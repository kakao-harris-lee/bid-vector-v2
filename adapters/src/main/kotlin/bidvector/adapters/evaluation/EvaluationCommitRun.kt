package bidvector.adapters.evaluation

import bidvector.sharedkernel.AccountId
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CapacityPort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.InboxRecordingNotifications
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.NotificationRequestPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.notificationinbox.NotificationAudience
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository

/**
 * 평가 **커밋 경로**의 조립 경계(6F-10 ⓒ, `OPEN-6A3-EVALUATION-COMMIT` 종결) — dry-run 과
 * 같은 use case 를 돌리되 알림 요청 port 가 `RecordingNotificationRequestPort` 가 아니라
 * 조립이 꽂아 주는 production 채널이다([channel]). 그 채널이 outbox 쓰기일 때 `BidNow` 판정이
 * **실제 outbox 행**으로 남고, 그 행이 relay 의 입력이 된다.
 *
 * **M15/15A — 그 채널을 쥐는 run 은 하나다.** 전역 경로의 수신자가 운영자 한 사람이므로 계정이
 * 여럿이 된 뒤에도 거기 쓰는 run 은 **가장 작은 계정**의 것 하나이고, 나머지 계정의 run 은
 * `SuppressedNotificationChannel` 을 받아 알림함에만 남는다. 그 선택에는 계정 전수가 필요하므로
 * 판단은 [EvaluationCommitRuns] 가 하고 이 클래스는 **받은 채널을 쓴다**.
 *
 * **왜 `app` 이 아니라 여기인가**(선례 `JdbcStrategyEditTransaction`, D-6A2b-3) — 이 경로의 조립은
 * `JdbcOutboxPort`·`OutboxNotificationRequestPort` 를 이름으로 부르고 `app` production 은
 * outbox 쓰기 타입을 참조하지 못한다(D-6A3-17(a)③). 그 이름을 부르는 자리는 M15/15A 이후
 * [EvaluationCommitRuns] 이고, 둘 다 `adapters` 라 그 경계는 그대로다. `app` 은 이 둘만 든다.
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
 * 인자를 **묶지 않는 이유**(cr r1 low): 전부 성질이 다른 협력자이고 꽂는 자리는 배선 한 곳이다.
 * 묶는 타입을 만들면 그 타입의 유일한 일이 서명을 짧게 하는 것이 되고, dry-run·단건 경로는
 * **다른 부분집합**을 쓰므로 그 묶음을 공유하지도 못한다 — 자리만 하나 늘어난다. 기본값이 없다는
 * 성질(빠뜨리면 컴파일이 깨진다)이 이 slice 가 지키려는 축이고 그것은 그대로다. (수를 문면에 적지
 * 않는다 — 앞 판은 「열셋」을 적었다가 병합마다 틀린 수가 됐고, 세어 고치는 일만 남았다.)
 */
@Suppress("LongParameterList")
class EvaluationCommitRun(
    /**
     * **M15/15A — run 하나는 계정 하나다.** 전략·자격·기회 분석이 전부 이 계정의 것이고, 알림함
     * 기록도 이 계정으로만 간다(아래 [notifications] 의 청중).
     */
    private val account: AccountId,
    /**
     * **전역 발송 채널을 쥐는가는 이 run 이 정하지 않는다**(M15/15A, 계약 「Telegram」). 조립이
     * 정해서 꽂는다 — 채널은 하나뿐이고 그것을 받을 계정은 **가장 작은 계정** 하나이므로, 그
     * 판단에는 **계정 전수**가 필요하고 그 목록을 아는 자리는 [EvaluationCommitRuns] 다.
     * run 하나가 자기만 보고 정하면 「내가 그 하나인가」를 알 길이 없다.
     */
    private val channel: NotificationRequestPort,
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
) {
    /**
     * **알림함 기록이 outbox 등록을 감싼다**(NOTI-01 「억제는 채널 억제이지 기록 억제가 아니다」).
     * 기록이 먼저이므로 outbox 쓰기가 실패해도 알림함 항목은 남는다 — 그 순서가
     * `InboxRecordingNotifications` 의 계약이고 거기 test 가 양방향으로 잠근다.
     */
    private val notifications =
        InboxRecordingNotifications(
            channel = channel,
            inbox = notificationInbox,
            // **M15/15A — 청중은 이 run 의 계정 하나로 고정한다**(`PinnedStrategyRepository` 와 같은
            // 꼴). 앞 판은 `JdbcNotificationAudience` 를 그대로 받아 **모든 계정**의 알림함에 같은 새
            // 후보를 적었다 — 그것이 이 slice 가 닫는 누출이다. 계정 전수를 읽는 자리는 이제 러너
            // 하나이고(`EvaluationCommitRuns.audience`), 그 목록의 계정마다 run 이 하나씩 선다.
            audience = { NotificationAudience.Accounts(listOf(account)) },
            clock = clock,
        )

    suspend fun evaluate(): List<CandidateEvaluation> =
        EvaluateCandidatesUseCase(
            account = account,
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
