package bidvector.app.wiring

import bidvector.adapters.evaluation.InvalidEvaluationRequestException
import bidvector.adapters.evaluation.PinnedStrategyRepository
import bidvector.adapters.evaluation.RecordingNotificationRequestPort
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.strategy.OperatorStrategy
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository

/**
 * 요청 스코프 평가 실행 묶음(D-6A3-8) — `EvaluationDryRunController`가 이 안의 [useCase]만
 * 부른다. [strategy]·[notifications]를 함께 낸다 — 컨트롤러가 응답을 조립할 때 여력 상한·
 * 후보 상한·`wouldNotifyNoticeIds`를 이 값들에서 읽는다(`evaluate()`는 `List<
 * CandidateEvaluation>`만 낸다, D-6A3-6 — use case 자신은 그 값을 따로 내지 않는다).
 */
class EvaluationDryRunRun(
    val useCase: EvaluateCandidatesUseCase,
    val notifications: RecordingNotificationRequestPort,
    val strategy: OperatorStrategy,
)

/**
 * 전략에 여력 상한이 설정되지 않았다(D-6A3-4 fail-closed) — `app.http.ErrorMapping`이 이
 * 타입을 409 `MAX_ACTIVE_BIDS_NOT_CONFIGURED`로 매핑한다. 현재값을 0 으로도 상한을
 * 무한으로도 지어내지 않는다 — 결정 ③이 「현재값 0 고정」을 기각한 이유와 대칭이다.
 */
class MaxActiveBidsNotConfiguredException :
    RuntimeException("전략에 여력 상한(maxActiveBids)이 설정되지 않았다 — dry-run을 거부한다")

/**
 * 요청마다 [EvaluateCandidatesUseCase]를 짓는다(D-6A3-8, D-6A3-5) — 컨트롤러는 이 팩토리의
 * [forRequest]만 부른다(6A 완료 조건 「컨트롤러는 use case만 부른다」). `currentActiveBids`
 * **외 어떤 인자도 밖에서 받지 않는다**((2b) 「경계로 처리」) — 전략·후보 상한·판정 정책은
 * 전부 이 팩토리가 생성자로 받은 싱글턴 협력자에서 읽는다.
 *
 * **전략은 요청당 정확히 한 번 읽는다(D-6A3-5).** [strategyRepository].`load()`를 여기서
 * 한 번 부르고 [PinnedStrategyRepository]로 감싸 use case 에 넘긴다 — use case 자신의
 * `strategies.load()` 호출은 이 캐시값을 돌려받을 뿐 저장소를 다시 두드리지 않는다.
 */
class EvaluationDryRunFactory(
    private val strategyRepository: StrategyRepository,
    private val candidateSource: CandidateSourcePort,
    private val watchSubjects: WatchSubjectPort,
    private val licenseGate: LicenseGatePort,
    private val mlAnalysis: MlAnalysisPort,
    private val correlationIds: CorrelationIdFactory,
    private val clock: Clock,
) {
    fun forRequest(currentActiveBids: Int): EvaluationDryRunRun {
        // 요청 형태(400) 를 먼저 거부한다 — 저장소 상태(409) 보다 앞선다(입력 검증 우선
        // 원칙). RequestCapacityPort 생성자도 같은 조건을 다시 검사한다(심층 방어,
        // StrategyRow.toExactStrategyWon 과 같은 관례) — 이 자리에서 먼저 걸러 순서를 고정한다.
        if (currentActiveBids < 0) {
            throw InvalidEvaluationRequestException("currentActiveBids는 음수일 수 없다: $currentActiveBids")
        }
        val strategy = strategyRepository.load()
        val maxActiveBids = strategy.maxActiveBids?.value ?: throw MaxActiveBidsNotConfiguredException()
        val capacity = RequestCapacityPort(currentActiveBids, maxActiveBids)
        val notifications = RecordingNotificationRequestPort()
        val pinnedStrategies = PinnedStrategyRepository(strategy, strategyRepository)
        val useCase =
            EvaluateCandidatesUseCase(
                strategies = pinnedStrategies,
                candidateSource = candidateSource,
                watchSubjects = watchSubjects,
                licenseGate = licenseGate,
                mlAnalysis = mlAnalysis,
                capacity = capacity,
                notifications = notifications,
                correlationIds = correlationIds,
                clock = clock,
                analysisBudget = strategy.candidateLimit?.value,
            )
        return EvaluationDryRunRun(useCase, notifications, strategy)
    }
}
