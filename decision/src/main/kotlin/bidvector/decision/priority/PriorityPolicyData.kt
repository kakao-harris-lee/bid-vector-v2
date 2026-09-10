package bidvector.decision.priority

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.math.BigDecimal
import java.math.RoundingMode

private const val WEIGHT_SUM_SCALE = 4
private val UNIT_MIN: BigDecimal = BigDecimal.ZERO
private val UNIT_MAX: BigDecimal = BigDecimal.ONE
private val WEIGHT_SUM_TARGET: BigDecimal = UNIT_MAX.setScale(WEIGHT_SUM_SCALE)

/** 용량 penalty 항 둘의 가중치(scope.md ②, 조사 §1.1 `allocation.py:505-517`). */
data class LoadPenaltyPolicy(
    val ratioWeight: BigDecimal,
    val workloadWeight: BigDecimal,
) {
    init {
        require(ratioWeight >= UNIT_MIN) { "ratioWeight 는 음수일 수 없다: $ratioWeight" }
        require(workloadWeight >= UNIT_MIN) { "workloadWeight 는 음수일 수 없다: $workloadWeight" }
    }
}

/** 복잡도 penalty 항(scope.md ②, 조사 §1.1 `allocation.py:528-532`). */
data class ComplexityPenaltyPolicy(
    val threshold: BigDecimal,
    val slope: BigDecimal,
    val cap: BigDecimal,
) {
    init {
        require(threshold >= UNIT_MIN && threshold <= UNIT_MAX) { "threshold 는 [0,1] 범위여야 한다: $threshold" }
        require(slope >= UNIT_MIN) { "slope 는 음수일 수 없다: $slope" }
        require(cap >= UNIT_MIN) { "cap 은 음수일 수 없다: $cap" }
    }
}

/**
 * priority 조합 정책(scope.md ②) — [weights] 는 [Component] 전 값을 덮는 전사상이고
 * 합은 1이다(scale 4 비교 — 4E `NotificationDeliveryPolicyData` 전사상 관례와 같은
 * 「빠진 축 = 생성 실패」 규율, 부동소수 반올림 잔차를 흡수하는 재정규화 값이라 정확한
 * 4자리 비교로 잰다). 확률 축을 뺀 legacy 가중치(조사 §1.1 `allocation.py:38-43`)를
 * 재정규화한 값이다(D-4B4-1·D-4B4-4) — 값 자체는 `policy-values.md`(legacy-behavior 층,
 * `OPEN-4B4-POLICY-VALUES` 승인 대기).
 *
 * [categoryOffsetMin]·[categoryOffsetMax] 는 카테고리 offset 의 허용 범위(조사 §1.2
 * `operator_strategy_tuning.py:16-17` `[-0.2, 0.2]`) — 범위 밖은 [SemanticMatch.of] 가
 * `require` 가 아니라 [MatchOutcome.OffsetOutOfRange] 로 낸다(4D-1 G-1).
 *
 * [normEpsilon] 은 [UnitVector] 의 norm 불변식 허용 오차 — 코드 리터럴 대신 이 슬롯에서
 * 온다(NOTI-01 「임계값이 코드 상수에 있는 형태는 채택하지 않는다」).
 */
data class PriorityPolicyData(
    val weights: Map<Component, BigDecimal>,
    val loadPenalty: LoadPenaltyPolicy,
    val complexityPenalty: ComplexityPenaltyPolicy,
    val categoryOffsetMin: BigDecimal,
    val categoryOffsetMax: BigDecimal,
    val normEpsilon: BigDecimal,
) {
    init {
        require(weights.keys == Component.entries.toSet()) {
            "weights 는 Component 전 값을 덮어야 한다: ${weights.keys}"
        }
        weights.forEach { (component, weight) ->
            require(weight > UNIT_MIN && weight <= UNIT_MAX) {
                "$component 가중치는 (0,1] 범위여야 한다: $weight"
            }
        }
        val sum = weights.values.fold(BigDecimal.ZERO, BigDecimal::add)
        require(sum.setScale(WEIGHT_SUM_SCALE, RoundingMode.HALF_UP).compareTo(WEIGHT_SUM_TARGET) == 0) {
            "weights 합은 1(scale $WEIGHT_SUM_SCALE)이어야 한다: $sum"
        }
        require(categoryOffsetMin <= categoryOffsetMax) {
            "categoryOffsetMin 은 categoryOffsetMax 이하여야 한다: min=$categoryOffsetMin max=$categoryOffsetMax"
        }
        require(normEpsilon > UNIT_MIN) { "normEpsilon 은 0보다 커야 한다: $normEpsilon" }
    }
}

/**
 * 운영 정책 인스턴스(D-4B4-4) — **착수 시 placeholder**(legacy-behavior 재정규화 값,
 * 승인 대기 `OPEN-4B4-POLICY-VALUES`). 정본은
 * `reports/evidence/m4/4b4/policy-values.md` — 값을 바꾸려면 그 문서를 먼저 갱신한다
 * (3A `KONEPS_COLLECTION_POLICY`·4D-1 `ML_CALL_POLICY` 관례). 가중치 0.3834/0.2333/
 * 0.1333/0.1000/0.1500 의 유도는 `policy-values.md` §1 참고 — legacy 0.23/0.14/0.08/
 * 0.06/0.09 를 0.60 으로 나눈 값을 scale 4 로 반올림한 뒤(0.3833/0.2333/0.1333/0.1000/
 * 0.1500, 합 0.9999) 잔차 0.0001 을 최대 가중치(`Match`)에 흡수해 합을 정확히 1.0000
 * 으로 고정했다.
 */
val PRIORITY_POLICY: EffectiveDatedPolicy<PriorityPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4b4/policy-values.md §1~§4 — 착수 placeholder, 승인 대기 OPEN-4B4-POLICY-VALUES",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    PriorityPolicyData(
                        weights =
                            mapOf(
                                Component.Match to BigDecimal("0.3834"),
                                Component.Urgency to BigDecimal("0.2333"),
                                Component.Competitiveness to BigDecimal("0.1333"),
                                Component.BudgetCapture to BigDecimal("0.1000"),
                                Component.ExpectedMargin to BigDecimal("0.1500"),
                            ),
                        loadPenalty =
                            LoadPenaltyPolicy(
                                ratioWeight = BigDecimal("0.18"),
                                workloadWeight = BigDecimal("0.12"),
                            ),
                        complexityPenalty =
                            ComplexityPenaltyPolicy(
                                threshold = BigDecimal("0.55"),
                                slope = BigDecimal("0.18"),
                                cap = BigDecimal("0.12"),
                            ),
                        categoryOffsetMin = BigDecimal("-0.20"),
                        categoryOffsetMax = BigDecimal("0.20"),
                        normEpsilon = BigDecimal("0.0001"),
                    ),
            ),
    )
