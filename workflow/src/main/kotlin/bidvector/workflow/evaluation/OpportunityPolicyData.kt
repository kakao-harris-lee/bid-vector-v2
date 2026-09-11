package bidvector.workflow.evaluation

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.RoundingPolicy
import bidvector.workflow.prediction.ModelReleaseSelector
import bidvector.workflow.prediction.OptimizationObjective
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * 4B-6 정책 데이터(4B-6a scope.md ⑥ + 4B-6b D-4B6B-5) — 합성 규약 version·정책 키워드·
 * 텍스트 상한(4B-6a)에 조합기 예산·release 선택자·목적함수·카테고리 offset(4B-6b)이
 * 더해졌다. `textMaxChars`는 2E `contract-policy.properties`의 `embedding.text.max-chars`와
 * **같아야 한다**(D-4B6A-4, 두 자리가 어긋나면 4D-2 gateway가 거부한다) —
 * `OpportunityPolicyDataTest`가 그 파일 값과 직접 대조한다.
 */
data class OpportunityPolicyData(
    val synthesisVersion: SynthesisVersion,
    val keywords: List<String>,
    val textMaxChars: Int,
    /** 임베딩 호출 예산(D-4B6B-5) — 조합기가 `embed` 호출마다 만드는 `CallBudget`의 근거. */
    val embeddingBudget: Duration,
    /** 예측 호출 예산(D-4B6B-5) — 조합기가 `predict` 호출마다 만드는 `CallBudget`의 근거. */
    val predictionBudget: Duration,
    /** 임베딩·예측 두 호출에 같은 값을 넘긴다(D-4B6B-3 — release 일관성 전제). */
    val releaseSelector: ModelReleaseSelector,
    /** `CalculateOptimalBid`의 목적함수(D-4B6B-5) — 계약이 지원하는 값은 지금 하나뿐이다. */
    val objective: OptimizationObjective,
    /** `SemanticMatch.of`에 넘기는 카테고리 offset(D-4B6B-5) — STR-05 산식은 이 slice 밖(값 0 고정). */
    val categoryOffset: BigDecimal,
    /**
     * D-4B6B-6 구현에 필요한 추가 슬롯 — `baseAmount × BidRate → BidAmount`(shared-kernel
     * `MoneyArithmetic.roundedWith`)에 넘기는 [RoundingPolicy]. `scaleDigits`는 금액 축이
     * 이미 원 단위 정수(0)로 닫힌 구조적 사실(`data-dictionary.md` §1.1 정의 ①)이라 값이
     * 아니지만, `mode`는 `RoundingPolicy` KDoc이 명시한 대로 열려 있는 정책값(`OPEN-DIC-10`)
     * 이라 호출부가 주입해야 한다 — `DerivationPolicyData.budgetCaptureRounding`·
     * `ProvenancePolicyData.integerRoundingMode`와 같은 규율(매직넘버 금지, 코드 리터럴로
     * 두지 않는다). `budgetCaptureRounding`(scale 6)은 이 자리와 다른 축이다 — `divideForRate`
     * 내부에서 실제로는 쓰이지 않고(고정 `MathContext(20)`) `policyVersion` 라벨링에만
     * 쓰인다(조사 실측) — 재사용하면 의미가 어긋난다.
     */
    val recommendedAmountRounding: RoundingPolicy,
) {
    init {
        require(keywords.isNotEmpty()) { "keywords는 비어 있을 수 없다" }
        require(keywords.all { it.isNotBlank() }) { "keywords는 공백 항목을 가질 수 없다: $keywords" }
        require(keywords.size == keywords.distinct().size) { "keywords는 중복을 가질 수 없다: $keywords" }
        require(keywords.all { it == it.lowercase() }) { "keywords는 소문자여야 한다: $keywords" }
        require(textMaxChars > 0) { "textMaxChars는 양수여야 한다: $textMaxChars" }
        require(!embeddingBudget.isNegative && !embeddingBudget.isZero) {
            "embeddingBudget는 0보다 커야 한다: $embeddingBudget"
        }
        require(!predictionBudget.isNegative && !predictionBudget.isZero) {
            "predictionBudget는 0보다 커야 한다: $predictionBudget"
        }
    }
}

/**
 * **승인 대기 — `OPEN-4B6A-POLICY-VALUES`(4B-6a 슬롯)·`OPEN-4B6B-POLICY-VALUES`(4B-6b
 * 슬롯).** 키워드 14는 legacy-behavior(`opportunity_analysis/base.py`
 * `EXECUTION_COMPLEXITY_KEYWORDS`, 4B-5 D-4B5-5 인계)이고 `textMaxChars`는 2E
 * `contract-policy.properties`의 `embedding.text.max-chars=4000`과 같은 값이다(D-4B6A-4).
 * `embeddingBudget`(2s)·`predictionBudget`(3s)·`releaseSelector`(`LatestPromoted`)·
 * `objective`(`SCENARIO_TRIPLE`)·`categoryOffset`(0)은 D-4B6B-5 추천값 — 정본은
 * `reports/evidence/m4/4b6b/policy-values.md`. 값을 바꾸려면 그 문서를 먼저 갱신한다.
 */
val OPPORTUNITY_POLICY: EffectiveDatedPolicy<OpportunityPolicyData> =
    EffectiveDatedPolicy(
        source =
            "reports/evidence/m4/4b6a/policy-values.md §1(4B-6a 슬롯) · " +
                "reports/evidence/m4/4b6b/policy-values.md(4B-6b 슬롯) — 승인 대기",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    OpportunityPolicyData(
                        synthesisVersion = SynthesisVersion("v1"),
                        keywords =
                            listOf(
                                "통합",
                                "고도화",
                                "운영",
                                "유지관리",
                                "24시간",
                                "대규모",
                                "다기관",
                                "클라우드",
                                "센터",
                                "실시간",
                                "연계",
                                "보안",
                                "이관",
                                "플랫폼",
                            ),
                        textMaxChars = 4000,
                        embeddingBudget = Duration.ofSeconds(2),
                        predictionBudget = Duration.ofSeconds(3),
                        releaseSelector = ModelReleaseSelector.LatestPromoted,
                        objective = OptimizationObjective.SCENARIO_TRIPLE,
                        categoryOffset = BigDecimal.ZERO,
                        recommendedAmountRounding = RoundingPolicy(scaleDigits = 0, mode = RoundingMode.HALF_UP),
                    ),
            ),
    )
