package bidvector.adapters.evaluation

import bidvector.adapters.event.JdbcEventIdFactory
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.persistence.OwnTransactionConnectionSource
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CapacityPort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.InboxRecordingNotifications
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.OutboxNotificationRequestPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.notificationinbox.NotificationAudiencePort
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import javax.sql.DataSource

/**
 * 평가 **커밋 경로**의 조립 경계(6F-10 ⓒ, `OPEN-6A3-EVALUATION-COMMIT` 종결) — dry-run 과
 * 같은 use case 를 돌리되 알림 요청 port 가 `RecordingNotificationRequestPort` 가 아니라
 * production [OutboxNotificationRequestPort] 다. 그래서 `BidNow` 판정이 **실제 outbox 행**으로
 * 남고, 그 행이 relay 의 입력이 된다.
 *
 * **왜 `app` 이 아니라 여기인가**(선례 `JdbcStrategyEditTransaction`, D-6A2b-3) — 이 조립은
 * `JdbcOutboxPort`·[OutboxNotificationRequestPort] 를 이름으로 부르고 `app` production 은
 * outbox 쓰기 타입을 참조하지 못한다(D-6A3-17(a)③). `app` 은 이 클래스만 든다.
 *
 * **요청 하나 = 트랜잭션 하나**(D-6F10-4). `OwnTransactionConnectionSource` 는 호출마다 연결을
 * 열어 커밋하고 닫는다 — ThreadLocal·소유 스레드 검사가 없다. 그것이 `suspend evaluate()`
 * 아래에서 유일하게 서는 모양이다: `TransactionBoundary` 는 경계를 연 스레드에서만 쓸 수
 * 있어 코루틴이 다른 스레드에서 재개되면 던지고, run 전체를 한 트랜잭션으로 감는 설계는
 * 애초에 서지 않는다(판정 하나하나가 독립 커밋이라는 뜻이고, **run 전체의 원자성은 주장하지
 * 않는다**).
 *
 * **오늘 평가에는 outbox 밖 도메인 write 가 없다**(판정 기록 표 부재, D-6F7-2) — 그래서
 * D-6F7-11 이 지적한 「도메인 write 는 커밋됐는데 outbox 행이 없다」는 **성립하지 않는다**.
 * 그 사실은 문면이 아니라 test 가 든다(커밋 run 뒤 변한 표는 `outbox` 하나). 판정 기록 표가
 * 생기는 slice 가 이 축을 다시 받는다 — `OPEN-6F10-EVALUATION-DOMAIN-WRITE`.
 *
 * `Failed` 는 **버려지지 않는다** — `reach()` 가 그 값을 `CandidateEvaluation.Reached.
 * disposition` 에 실어 돌려주고, 러너가 그 계수로 종료 코드를 정한다.
 *
 * 인자가 열셋인 것을 **묶지 않는 이유**(cr r1 low): 전부 성질이 다른 협력자이고 꽂는 자리는 배선
 * 한 곳이다. 묶는 타입을 만들면 그 타입의 유일한 일이 서명을 짧게 하는 것이 되고, dry-run·단건
 * 경로는 **다른 부분집합**을 쓰므로 그 묶음을 공유하지도 못한다 — 자리만 하나 늘어난다. 기본값이
 * 없다는 성질(빠뜨리면 컴파일이 깨진다)이 이 slice 가 지키려는 축이고 그것은 그대로다.
 */
@Suppress("LongParameterList")
class EvaluationCommitRun(
    dataSource: DataSource,
    private val strategies: StrategyRepository,
    private val candidateSource: CandidateSourcePort,
    private val watchSubjects: WatchSubjectPort,
    private val licenseGate: LicenseGatePort,
    private val mlAnalysis: MlAnalysisPort,
    private val capacity: CapacityPort,
    private val correlationIds: CorrelationIdFactory,
    private val clock: Clock,
    private val analysisBudget: Int?,
    /**
     * NOTI-01 — 가치 게이트를 통과한 후보를 운영자 **알림함**에도 남긴다. 둘 다 **기본값이
     * 없다**: 이 경로가 `NEW_CANDIDATE` 의 유일한 생산자라, 조용히 빠지면 그 값이 production
     * 에서 다시 0 이 된다. 빠뜨리면 컴파일이 깨진다.
     *
     * **dry-run·단건 경로에는 이 짝이 없다.** 그 둘은 `RecordingNotificationRequestPort` 를 쓰고
     * 알림함에 닿지 않는다 — 「미리보기가 기록을 남긴다」가 술어가 아니라 구조로 불가능하다
     * (그 분리를 실제로 잡은 게이트는 `AppHttpDependencyGateTest` 와 `ArchitectureGateTest` 다 —
     * 단건 경로에 알림함 감싸기를 심은 변이를 그 둘이 RED 로 냈다).
     */
    private val notificationInbox: NotificationInboxPort,
    private val audience: NotificationAudiencePort,
) {
    /**
     * **알림함 기록이 outbox 등록을 감싼다**(NOTI-01 「억제는 채널 억제이지 기록 억제가 아니다」).
     * 기록이 먼저이므로 outbox 쓰기가 실패해도 알림함 항목은 남는다 — 그 순서가
     * `InboxRecordingNotifications` 의 계약이고 거기 test 가 양방향으로 잠근다.
     */
    private val notifications =
        InboxRecordingNotifications(
            channel =
                OutboxNotificationRequestPort(
                    JdbcOutboxPort(OwnTransactionConnectionSource(dataSource)),
                    JdbcEventIdFactory(),
                    clock,
                ),
            inbox = notificationInbox,
            audience = audience,
            clock = clock,
        )

    suspend fun evaluate(): List<CandidateEvaluation> =
        EvaluateCandidatesUseCase(
            strategies = strategies,
            candidateSource = candidateSource,
            watchSubjects = watchSubjects,
            licenseGate = licenseGate,
            mlAnalysis = mlAnalysis,
            capacity = capacity,
            notifications = notifications,
            correlationIds = correlationIds,
            clock = clock,
            analysisBudget = analysisBudget,
        ).evaluate()
}
