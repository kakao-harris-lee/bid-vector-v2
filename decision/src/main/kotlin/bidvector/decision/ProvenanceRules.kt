package bidvector.decision

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val RATIO_DIVISION_CONTEXT = MathContext(20)

/**
 * 술어 넷이 소비하는 행(D-12) — 판정 전 원시값이다. 아직 어느 basis 가 신뢰할 만한지
 * 정하는 판정 자체라 `BaseAmount`로 감싸지 않는다(판정 전에는 이 값이 `BaseAmount`가
 * 될지조차 모른다).
 */
data class ProvenanceRow(
    val rawBaseAmount: BigDecimal,
    val budgetEstimate: BigDecimal?,
    val winningAmount: BigDecimal?,
    val winningRate: Rate?,
)

/** `기초금액 / 사업금액 > trustRatioMax`(엄격 초과, 조사 §1.1). 두 값이 모두 양수여야 한다. */
internal fun isSuspectRatio(
    row: ProvenanceRow,
    trustRatioMax: BigDecimal,
): Boolean {
    val estimate = row.budgetEstimate
    return estimate != null &&
        row.rawBaseAmount.signum() > 0 &&
        estimate.signum() > 0 &&
        row.rawBaseAmount.divide(estimate, RATIO_DIVISION_CONTEXT) > trustRatioMax
}

/**
 * `|base - round(base)| < tolerance`(허용 오차 미만, 조사 §1.1). `roundingMode`는 정책
 * 슬롯에서 온다(verifier r2 N-1) — 이 함수가 값을 지어내지 않는다.
 */
internal fun isCleanInteger(
    row: ProvenanceRow,
    tolerance: BigDecimal,
    roundingMode: RoundingMode,
): Boolean {
    if (row.rawBaseAmount.signum() <= 0) return false
    val nearestInteger = row.rawBaseAmount.setScale(0, roundingMode)
    return row.rawBaseAmount.subtract(nearestInteger).abs() < tolerance
}

/** `|base × winningRate − winningAmount| < tolerance`(허용 오차 미만, 조사 §1.1). */
internal fun isDerivedYega(
    row: ProvenanceRow,
    tolerance: BigDecimal,
): Boolean {
    val winningAmount = row.winningAmount
    val winningRate = row.winningRate
    return winningAmount != null &&
        winningRate != null &&
        winningAmount.signum() > 0 &&
        winningRate.fraction.signum() > 0 &&
        yegaGap(row.rawBaseAmount, winningRate.fraction, winningAmount) < tolerance
}

private fun yegaGap(
    rawBaseAmount: BigDecimal,
    winningRateFraction: BigDecimal,
    winningAmount: BigDecimal,
): BigDecimal = rawBaseAmount.multiply(winningRateFraction).subtract(winningAmount).abs()

/**
 * `|base × vatMultiplier − round(base × vatMultiplier)| < tolerance`(허용 오차 미만,
 * 조사 §1.1). `roundingMode`는 정책 슬롯에서 온다(verifier r2 N-1).
 */
internal fun isDerivedVat(
    row: ProvenanceRow,
    multiplier: BigDecimal,
    tolerance: BigDecimal,
    roundingMode: RoundingMode,
): Boolean {
    if (row.rawBaseAmount.signum() <= 0) return false
    val withVat = row.rawBaseAmount.multiply(multiplier)
    val rounded = withVat.setScale(0, roundingMode)
    return withVat.subtract(rounded).abs() < tolerance
}

/**
 * `SuspectRatio` 활성 시 `trustRatioMax` 존재는 [ProvenancePolicyData.init]이 이미
 * 보증한다 — 이 함수는 그 불변식 위에서만 불린다.
 */
private fun matches(
    row: ProvenanceRow,
    ruleId: ProvenanceRuleId,
    policy: ProvenancePolicyData,
): Boolean =
    when (ruleId) {
        ProvenanceRuleId.SuspectRatio -> {
            isSuspectRatio(row, requireNotNull(policy.trustRatioMax).fraction)
        }

        ProvenanceRuleId.CleanInteger -> {
            isCleanInteger(row, policy.cleanIntegerTolerance, policy.integerRoundingMode)
        }

        ProvenanceRuleId.DerivedYega -> {
            isDerivedYega(row, policy.yegaTolerance)
        }

        ProvenanceRuleId.DerivedVat -> {
            isDerivedVat(row, policy.vatMultiplier, policy.vatTolerance, policy.integerRoundingMode)
        }
    }

/** 행에 정책의 술어 넷을 돌려 매치 집합을 낸다(단위 test 층 진입점, D-4). */
fun ProvenanceRow.evaluateHits(policy: ProvenancePolicyData): Set<ProvenanceRuleId> =
    policy.ruleOrder.filterTo(mutableSetOf()) { ruleId -> matches(this, ruleId, policy) }

/** rule id → 판정 라벨 결속(D-12, 커널 안 정의). 매치 없음(`null`) = `Unknown`(D-2). */
fun classificationFor(ruleId: ProvenanceRuleId?): BaseAmountProvenance =
    when (ruleId) {
        null -> BaseAmountProvenance.Unknown
        ProvenanceRuleId.SuspectRatio -> BaseAmountProvenance.SuspectRatio
        ProvenanceRuleId.CleanInteger -> BaseAmountProvenance.Clean
        ProvenanceRuleId.DerivedYega -> BaseAmountProvenance.DerivedYega
        ProvenanceRuleId.DerivedVat -> BaseAmountProvenance.DerivedVat
    }

/**
 * provenance first-match 커널(①, D-4) — 매치 집합을 받는 orderer(corpus 층)와 행을 받는
 * 판정(단위 test 층) 두 진입점이 모두 [judge] 하나로 모인다.
 */
object ProvenanceRules {
    /** 정책이 선언한 순서로 매치 집합을 훑어 첫 매치를 낸다. 매치 없으면 `null`. */
    fun firstMatchedRule(
        hits: Set<ProvenanceRuleId>,
        policy: Resolution.Resolved<ProvenancePolicyData>,
    ): ProvenanceRuleId? = policy.value.ruleOrder.firstOrNull { it in hits }

    /**
     * corpus 층 진입점 — 매치 집합이 이미 주어진 경우(D-4, 「입력이 가진 층」). `original`
     * 은 그대로 봉투에 실리고(D-6), 복구 추정치는 계산하지 않고 입력을 그대로 보존한다.
     */
    fun judge(
        original: BaseAmount,
        hits: Set<ProvenanceRuleId>,
        recoveryEstimate: Fact<Money>,
        policy: Resolution.Resolved<ProvenancePolicyData>,
    ): ProvenanceJudgement {
        val firstMatchedRule = firstMatchedRule(hits, policy)
        return ProvenanceJudgement(
            original = original,
            classification = classificationFor(firstMatchedRule),
            evidence = ProvenanceEvidence(firstMatchedRule, policy.value.ruleOrder, policy.version),
            recoveryEstimate = recoveryEstimate,
        )
    }

    /** 단위 test 층 진입점 — 행에서 술어를 돌려 매치 집합을 만든 뒤 [judge]로 위임한다. */
    fun judgeRow(
        original: BaseAmount,
        row: ProvenanceRow,
        recoveryEstimate: Fact<Money>,
        policy: Resolution.Resolved<ProvenancePolicyData>,
    ): ProvenanceJudgement = judge(original, row.evaluateHits(policy.value), recoveryEstimate, policy)
}
