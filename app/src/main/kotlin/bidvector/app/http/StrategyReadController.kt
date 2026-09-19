package bidvector.app.http

import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.export
import bidvector.strategy.CategoryCode
import bidvector.strategy.OperatorStrategy
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 전략 조회(D-6A1-4 — 이 slice의 **유일한** endpoint, 읽기 전용). [StrategyRepository]가
 * 이미 낸 [OperatorStrategy]를 [StrategyReadResponse]로 그대로 옮긴다 — 새 계산값을
 * 만들지 않는다((2b) 「닫는다」).
 */
@RestController
class StrategyReadController(
    private val strategyRepository: StrategyRepository,
) {
    @GetMapping("/api/strategy")
    fun read(): StrategyReadResponse = StrategyReadResponse.from(strategyRepository.load())
}

/**
 * 전략 조회 응답 — **평탄하다**(D-6A1-20 ⓑ, `StrategyReadResponseIsFlatTest`가 이 평탄함을
 * 단언으로 잠근다). 모든 필드가 문자열·숫자·`List<String>` 중 하나이고 중첩 object가 없다.
 */
data class StrategyReadResponse(
    val revision: Int,
    val focusCategories: List<String>,
    val focusRegionTerms: List<String>,
    val excludeRegionTerms: List<String>,
    val requiredKeywordTerms: List<String>,
    val excludeKeywordTerms: List<String>,
    val minBudgetWon: Long?,
    val minBudgetCurrency: String?,
    val minBudgetVatTreatment: String?,
    val minBudgetProvenance: String?,
    val maxBudgetWon: Long?,
    val maxBudgetCurrency: String?,
    val maxBudgetVatTreatment: String?,
    val maxBudgetProvenance: String?,
    val minimumMatchScore: BigDecimal?,
    val minimumProbabilityScore: BigDecimal?,
    val bidNowThreshold: BigDecimal?,
    val reviewThreshold: BigDecimal?,
    val candidateLimit: Int?,
) {
    companion object {
        fun from(strategy: OperatorStrategy): StrategyReadResponse {
            val min = strategy.watchRules.budget.min?.export()
            val max = strategy.watchRules.budget.max?.export()
            return StrategyReadResponse(
                revision = strategy.revision.value,
                focusCategories = strategy.watchRules.focusCategories.map(CategoryCode::value),
                focusRegionTerms = strategy.watchRules.focusRegionTerms,
                excludeRegionTerms = strategy.watchRules.excludeRegionTerms,
                requiredKeywordTerms = strategy.watchRules.requiredKeywordTerms,
                excludeKeywordTerms = strategy.watchRules.excludeKeywordTerms,
                minBudgetWon = min?.won,
                minBudgetCurrency = min?.currency?.name,
                minBudgetVatTreatment = min?.vatTreatment?.name,
                minBudgetProvenance = min?.provenance?.let(::provenanceLabel),
                maxBudgetWon = max?.won,
                maxBudgetCurrency = max?.currency?.name,
                maxBudgetVatTreatment = max?.vatTreatment?.name,
                maxBudgetProvenance = max?.provenance?.let(::provenanceLabel),
                minimumMatchScore = strategy.actionThresholds.minimumMatchScore?.score?.value,
                minimumProbabilityScore = strategy.actionThresholds.minimumProbabilityScore?.score?.value,
                bidNowThreshold = strategy.actionThresholds.bidNowThreshold?.score?.value,
                reviewThreshold = strategy.actionThresholds.reviewThreshold?.score?.value,
                candidateLimit = strategy.candidateLimit?.value,
            )
        }
    }
}

/**
 * [Provenance] 여섯 갈래를 고정 라벨로 옮긴다(`when` 소진 — 분기 도배가 아니라 sealed
 * type 전수, 새 variant가 생기면 컴파일이 이 함수를 막는다). `ProvenanceCodec`
 * (`adapters.persistence`, Kotlin `internal`은 모듈 범위라 `app`에서 보이지 않는다)를
 * 재사용할 수 없어 이 slice가 자체 라벨을 갖는다 — 알려진 제한(중복, 재사용 후보였으나
 * 모듈 경계가 막았다).
 */
private fun provenanceLabel(provenance: Provenance): String =
    when (provenance) {
        is Provenance.Published -> "PUBLISHED"
        Provenance.DerivedFromOpening -> "DERIVED_FROM_OPENING"
        is Provenance.FilledFromBudgetKey -> "FILLED_FROM_BUDGET_KEY"
        Provenance.CopiedFromBaseAmount -> "COPIED_FROM_BASE_AMOUNT"
        Provenance.OperatorDeclared -> "OPERATOR_DECLARED"
        Provenance.Undeclared -> "UNDECLARED"
    }
