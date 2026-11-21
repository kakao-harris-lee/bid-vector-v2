package bidvector.adapters.evaluation

import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.notificationinbox.NotificationAudience
import bidvector.workflow.notificationinbox.NotificationAudiencePort
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import javax.sql.DataSource

/**
 * 평가 커밋의 **계정별 run 공장**(M15/15A) — 앞 판은 run 이 하나뿐이었고 그 하나가 싱글턴 전략으로
 * 평가해 **모든 계정**의 알림함에 같은 새 후보를 적었다. 이 클래스가 그 자리를 가른다: 계정 전수를
 * 읽는 자리 하나와, 계정마다 서는 [EvaluationCommitRun] 들.
 *
 * **전략은 계정마다 run 당 정확히 한 번 읽는다**(R1-M-4 의 계정별 판). 여기서 한 번 읽어 여력
 * 상한·분석 예산을 뽑고 **그 값을** [PinnedStrategyRepository] 로 고정해 넘기므로, use case 의
 * `evaluate()` 진입 `load()` 는 저장소를 다시 두드리지 않는다.
 *
 * **전략이 쓸 수 없는 계정은 `null` 이다**([forAccount]) — 러너가 그 수를 센다. 앞 판은 여력 상한
 * 미설정이 **기동 실패**였는데, 계정이 여럿이면 한 계정의 미설정이 다른 계정의 평가를 멈춘다.
 * 평가하지 않는다는 결과는 같다(지어낸 상한으로 평가하지 않는다) — 달라지는 것은 **범위**이고,
 * 그 사실이 로그 계수로 관측된다.
 *
 * 왜 `app` 이 아니라 여기인가: 이 조립이 [EvaluationCommitRun] 을 짓고 그 생성자는 outbox 쓰기
 * 타입을 이름으로 부른다 — `app` production 은 그 타입을 참조하지 못한다(D-6A3-17(a)③).
 */
@Suppress("LongParameterList")
class EvaluationCommitRuns(
    private val dataSource: DataSource,
    /**
     * **읽기 port 로만 저장소를 본다**(D-6A2b-41) — 이 공장은 전략을 쓰지 않는다. 쓰기 가능한
     * port 를 쥐면 그것 하나로 편집 use case 를 스스로 조립할 수 있고, 그 조립은 트랜잭션·
     * outbox·세션을 전부 건너뛴다.
     */
    private val strategies: StrategyReader,
    private val candidateSource: CandidateSourcePort,
    private val watchSubjects: WatchSubjectPort,
    private val licenseGate: LicenseGatePort,
    private val mlAnalysis: MlAnalysisPort,
    private val correlationIds: CorrelationIdFactory,
    private val clock: Clock,
    private val notificationInbox: NotificationInboxPort,
    private val audience: NotificationAudiencePort,
    /**
     * 운영자가 설정으로 대는 **현재 진행 입찰 수**(D-6F10-20, `OPEN-6F10-CAPACITY-SOURCE`) —
     * 이 저장소에 실 계수기가 없다.
     *
     * **알려진 제한(M15/15A)**: 계정이 여럿이어도 값이 하나다. 계정별 진행 입찰 수를 셀 원천이
     * 생기기 전까지 모든 계정이 같은 현재값으로 용량을 판정한다 — `OPEN-M15-COMMIT-CAPACITY`.
     */
    private val currentActiveBids: Int,
    private val release: ReleaseIdentifier,
) {
    init {
        require(currentActiveBids >= 0) { "bidvector.evaluation.commit.current-active-bids 는 음수일 수 없다" }
    }

    /** 평가할 계정 전수 — 못 읽으면 [NotificationAudience.Unavailable] 이고 러너가 run 을 멈춘다. */
    fun accounts(): NotificationAudience = audience.accounts()

    /**
     * 그 계정의 run — **전략이 쓸 수 없으면 `null`** 이다. 술어는 여력 상한의 부재 하나이고, 그것이
     * 「전략 행이 없다」(빈 전략은 상한도 없다)와 「행은 있는데 상한을 안 정했다」를 함께 덮는다.
     * 둘을 가르지 않는 이유는 답이 같아서다 — 지어낸 상한으로 평가하지 않는다(D-6A3-4 fail-closed).
     */
    fun forAccount(account: AccountId): EvaluationCommitRun? {
        val strategy = strategies.load(account)
        val maxActiveBids = strategy.maxActiveBids?.value ?: return null
        return EvaluationCommitRun(
            account = account,
            dataSource = dataSource,
            strategies = PinnedStrategyRepository(account, strategy),
            candidateSource = candidateSource,
            watchSubjects = watchSubjects,
            licenseGate = licenseGate,
            mlAnalysis = mlAnalysis,
            capacity = RequestCapacityPort(currentActiveBids, maxActiveBids),
            correlationIds = correlationIds,
            clock = clock,
            analysisBudget = strategy.candidateLimit?.value,
            notificationInbox = notificationInbox,
            release = release,
        )
    }
}
