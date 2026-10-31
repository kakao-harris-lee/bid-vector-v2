package bidvector.workflow.suggestion

import bidvector.procurement.Notice
import bidvector.qualification.LicenseVerdict
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyRevision
import bidvector.workflow.evaluation.CandidateScreening
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.evaluation.UnjudgedQualificationAxis
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.evaluation.screenCandidate
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import java.time.Instant

/**
 * 제안 하나 — 값싼 게이트 셋을 통과한 공고와 그 공고의 **면허 판정 실제 값**. `Ineligible` 은
 * 게이트가 거르므로 여기 올 수 없다(생성 시 거부 — 조립 결함을 조용히 싣지 않는다).
 */
class Suggestion internal constructor(
    val notice: Notice,
    val license: LicenseVerdict,
) {
    init {
        require(license !is LicenseVerdict.Ineligible) { "면허 미달 공고는 제안이 아니다: ${notice.id}" }
    }
}

/**
 * 빈 결과의 뜻(STR-16 의 「이유 있는 빈 상태」를 제안 목록에 옮긴 것). 7C ① 의 `NOTHING_COLLECTED` 와
 * 이름이 다르다 — 이 목록의 원천은 수집된 전체가 아니라 **열린 후보**라 뜻이 다르다.
 */
enum class SuggestionState {
    MATCHED,
    NO_OPEN_NOTICES,
    WATCH_NOT_CONFIGURED,
    NO_MATCH,
}

/**
 * 오늘 판정하지 않는 축 전수 — 제안 카드의 면허 판정을 자격 전체로 읽지 않게 늘 싣는다.
 *
 * **선언 표는 `workflow.evaluation` 하나다**(`D-7C2-11`). 이 자리에 있던 사본은 지웠다 — 항목이
 * **둘**(QUAL-08 · QUAL-11)이라 **QUAL-10 이 빠져 있었고**, 그것은 의도가 아니라 시점 차이였다
 * (QUAL-10 은 이 slice 가 머지된 **뒤** `D-7C2-1` Q5 로 들어온 축이다). 빠진 채로 두면 면허 판정만
 * 있는 카드가 **수행실적까지 판정된 것처럼** 읽힌다.
 */
val SUGGESTION_UNJUDGED_AXES: List<UnjudgedQualificationAxis> = UnjudgedQualificationAxis.entries

/**
 * 제안 목록 한 번의 결과. [totalCount] 는 표시 상한을 적용하기 **전** 통과 건수이고
 * [suggestions] 는 그 앞에서부터 표시 상한만큼이다(STR-06).
 */
class SuggestionList internal constructor(
    val strategyRevision: StrategyRevision,
    val computedAt: Instant,
    val totalCount: Int,
    val suggestions: List<Suggestion>,
    val state: SuggestionState,
)

/** 계산하는 동안 전략·프로필이 두 번 연속 바뀌었다 — 낡은 결과도 지어낸 결과도 내지 않는다(STR-07). */
class StrategyChangedDuringComputationException : RuntimeException("계산하는 동안 전략·프로필이 두 번 연속 바뀌었다")

/**
 * 제안 목록 use case(M7/7C①b — STR-06·STR-07). **조회가 곧 계산이다** — 영속 스냅샷이 없다.
 *
 * **STR-07 은 소비자 쪽 값 비교로 닫는다.** 한 번의 시도는 첫머리에 전략·프로필을 읽어(`inputs`)
 * **전략만** 게이트에 고정해 넘긴다 — 프로필은 고정되지 않는다(면허 게이트가 후보마다 직접 읽는다).
 * 첫머리의 프로필은 비교의 기준값이다. 게이트를 다 돈 **뒤** 둘을 다시 읽어 값 등식으로 비교하고,
 * 다르면 그 결과를 버린다(A → B → A 처럼 돌아온 변경은 보지 못한다).
 * 판정이 쓰기 쪽이 무엇을 했는가(무효화 호출·이벤트·개정 +1)를 보지 않으므로, 편집 세션이든
 * 앞으로 생길 다른 경로든 수동 SQL 이든 **읽힌 값이 바뀌면** 같은 비교에 걸린다. 버린 뒤 한 번만
 * 다시 계산하고, 그것도 바뀌면 [StrategyChangedDuringComputationException](409)이다.
 *
 * **STR-06 — 표시 상한과 계산 예산이 다른 자리에서 온다.** 계산은 [candidateSource] 가 낸 열린 후보
 * **전부**(그 원천의 스캔 상한 = 운영 설정)에 게이트를 돈다. 전략의 `candidateLimit` 은 그 **뒤**
 * 결과를 자를 때만 읽는다 — 원천·게이트 어디에도 전달되지 않는다. 평가 use case 의 분석 예산처럼
 * 게이트 **앞**에서 원시 순번을 깎지 않는다(그 결합은 F-1 로 보고된 별 slice 소관이다).
 *
 * 순서는 원천의 결정적 순서(마감 임박 순, D-6F2-5)를 그대로 쓴다 — 이 use case 는 다시 정렬하지 않는다.
 * ML·사다리·용량·알림은 부르지 않는다(이 목록은 「감시 통과 × 자격 통과」까지다).
 */
class ListSuggestionsUseCase(
    private val strategies: StrategyReader,
    private val profiles: OperatorProfilePort,
    private val candidateSource: CandidateSourcePort,
    private val watchSubjects: WatchSubjectPort,
    private val licenseGate: LicenseGatePort,
    private val clock: Clock,
) {
    fun list(): SuggestionList = attempt() ?: attempt() ?: throw StrategyChangedDuringComputationException()

    /** 한 번의 계산 — 계산 중 입력이 바뀌었으면 `null`(그 결과는 채택하지 않는다). */
    private fun attempt(): SuggestionList? {
        val inputs = readInputs()
        val candidates = candidateSource.openCandidates()
        val passed = candidates.mapNotNull { notice -> passedSuggestion(notice, inputs.strategy) }
        if (readInputs() != inputs) return null
        return SuggestionList(
            strategyRevision = inputs.strategy.revision,
            computedAt = clock.now(),
            totalCount = passed.size,
            suggestions = passed.take(inputs.strategy.candidateLimit?.value ?: passed.size),
            state = stateOf(candidates, passed, inputs.strategy),
        )
    }

    private fun readInputs(): ComputationInputs = ComputationInputs(strategies.load(), profiles.current())

    private fun passedSuggestion(
        notice: Notice,
        strategy: OperatorStrategy,
    ): Suggestion? =
        when (val screening = screenCandidate(notice, strategy, clock, watchSubjects, licenseGate)) {
            is CandidateScreening.Passed -> Suggestion(notice, screening.license)
            is CandidateScreening.Dropped -> null
        }
}

/** 계산의 입력 — 비교의 단위다. 프로필은 면허 게이트가 읽으므로 전략과 함께 비교한다(고정은 전략만). */
private data class ComputationInputs(
    val strategy: OperatorStrategy,
    val profile: ProfileFacts?,
)

private fun stateOf(
    candidates: List<Notice>,
    passed: List<Suggestion>,
    strategy: OperatorStrategy,
): SuggestionState =
    when {
        candidates.isEmpty() -> SuggestionState.NO_OPEN_NOTICES
        passed.isNotEmpty() -> SuggestionState.MATCHED
        strategy.watchRules.isEmpty() -> SuggestionState.WATCH_NOT_CONFIGURED
        else -> SuggestionState.NO_MATCH
    }
