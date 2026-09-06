package bidvector.decision

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val VERSION_A = PolicyVersion(EffectiveFrom.Initial, "test-policy-a")
private val VERSION_B = PolicyVersion(EffectiveFrom.Initial, "test-policy-b")

private val RULE_ORDER_SUSPECT_FIRST =
    listOf(ProvenanceRuleId.SuspectRatio, ProvenanceRuleId.CleanInteger, ProvenanceRuleId.DerivedVat)
private val RULE_ORDER_CLEAN_FIRST =
    listOf(ProvenanceRuleId.CleanInteger, ProvenanceRuleId.SuspectRatio, ProvenanceRuleId.DerivedVat)

private fun testBaseAmount(won: Long): BaseAmount =
    BaseAmount(won, Currency.KRW, VatTreatment.UNKNOWN, Provenance.Undeclared)

private fun policyOf(
    ruleOrder: List<ProvenanceRuleId>,
    version: PolicyVersion = VERSION_A,
    trustRatioMax: Rate? = Rate.ofFraction(BigDecimal("1.15")),
): Resolution.Resolved<ProvenancePolicyData> =
    Resolution.Resolved(
        ProvenancePolicyData(
            ruleOrder = ruleOrder,
            trustRatioMax = trustRatioMax,
            cleanIntegerTolerance = BigDecimal("0.000001"),
            vatMultiplier = BigDecimal("1.1"),
            vatTolerance = BigDecimal("0.01"),
            yegaTolerance = BigDecimal.ONE,
        ),
        version,
    )

/** M1/1D ①②③ — provenance first-match 커널 회귀. */
class ProvenanceRulesTest {
    @Test
    fun `기대값 재현 — base-amount-provenance-001 hits 는 순서상 첫 매치 suspect-ratio 를 낸다`() {
        val original = testBaseAmount(912345678)
        val hits = setOf(ProvenanceRuleId.SuspectRatio)
        val policy = policyOf(RULE_ORDER_SUSPECT_FIRST)
        val recovery: Fact<Money> = Fact.Known(testBaseAmount(900000000))

        val judgement = ProvenanceRules.judge(original, hits, recovery, policy)

        judgement.classification shouldBe BaseAmountProvenance.SuspectRatio
        judgement.evidence.firstMatchedRule shouldBe ProvenanceRuleId.SuspectRatio
        judgement.original shouldBe original
        judgement.recoveryEstimate shouldBe recovery
    }

    @Test
    fun `② 같은 매치 집합이라도 정책 순서가 다르면 다른 라벨을 낸다 — 002 vs 003`() {
        val hits = setOf(ProvenanceRuleId.SuspectRatio, ProvenanceRuleId.CleanInteger)
        val policyA = policyOf(RULE_ORDER_SUSPECT_FIRST, VERSION_A)
        val policyB = policyOf(RULE_ORDER_CLEAN_FIRST, VERSION_B)

        val fromA = ProvenanceRules.firstMatchedRule(hits, policyA)
        val fromB = ProvenanceRules.firstMatchedRule(hits, policyB)

        fromA shouldBe ProvenanceRuleId.SuspectRatio
        fromB shouldBe ProvenanceRuleId.CleanInteger
    }

    @Test
    fun `매치 없음은 Unknown 이다 — D-2, 신뢰로 접히지 않는다`() {
        val policy = policyOf(listOf(ProvenanceRuleId.CleanInteger))

        val firstMatchedRule = ProvenanceRules.firstMatchedRule(emptySet(), policy)

        classificationFor(firstMatchedRule) shouldBe BaseAmountProvenance.Unknown
    }

    @Test
    fun `③ 원본 참조는 판정을 거쳐도 그대로 보존된다 — 복구 추정치와 분리`() {
        val original = testBaseAmount(912345678)
        val policy = policyOf(listOf(ProvenanceRuleId.SuspectRatio))
        val recovery: Fact<Money> = Fact.Known(testBaseAmount(900000000))

        val judgement = ProvenanceRules.judge(original, setOf(ProvenanceRuleId.SuspectRatio), recovery, policy)

        (judgement.original === original) shouldBe true
        judgement.recoveryEstimate shouldBe recovery
    }

    @Test
    fun `ruleOrder 에 중복 규칙이 있으면 정책 구성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            policyOf(listOf(ProvenanceRuleId.SuspectRatio, ProvenanceRuleId.SuspectRatio))
        }
    }

    @Test
    fun `ruleOrder 가 비어 있으면 정책 구성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            policyOf(emptyList())
        }
    }

    @Test
    fun `D-5 SuspectRatio 가 ruleOrder 에 있는데 trustRatioMax 가 없으면 정책 구성 자체가 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            policyOf(listOf(ProvenanceRuleId.SuspectRatio), trustRatioMax = null)
        }
    }

    @Test
    fun `trustRatioMax 부재는 SuspectRatio 를 쓰지 않는 정책에는 영향이 없다`() {
        val policy = policyOf(listOf(ProvenanceRuleId.CleanInteger), trustRatioMax = null)

        policy.value.trustRatioMax shouldBe null
    }

    @Test
    fun `술어 — suspect-ratio 는 엄격 초과만 매치한다`() {
        val row = ProvenanceRow(BigDecimal("115"), BigDecimal("100"), null, null)
        val exact = ProvenanceRow(BigDecimal("115.0000001"), BigDecimal("100"), null, null)

        isSuspectRatio(row, BigDecimal("1.15")) shouldBe false
        isSuspectRatio(exact, BigDecimal("1.15")) shouldBe true
    }

    @Test
    fun `술어 — clean-integer 는 허용 오차 미만이면 매치한다`() {
        val clean = ProvenanceRow(BigDecimal("1000000"), null, null, null)

        isCleanInteger(clean, BigDecimal("0.000001")) shouldBe true
    }

    @Test
    fun `술어 — derived-yega 는 base 곱하기 낙찰률이 낙찰가와 근사할 때만 매치한다`() {
        val row = ProvenanceRow(BigDecimal("1000000"), null, BigDecimal("900000"), Rate.ofFraction(BigDecimal("0.9")))

        isDerivedYega(row, BigDecimal.ONE) shouldBe true
    }

    @Test
    fun `술어 — derived-vat 는 base 곱하기 배수가 정수에 근사할 때만 매치한다`() {
        val row = ProvenanceRow(BigDecimal("1000000"), null, null, null)

        isDerivedVat(row, BigDecimal("1.1"), BigDecimal("0.01")) shouldBe true
    }

    @Test
    fun `단위 test 층 진입점 — 행에서 술어를 돌려 first-match 를 낸다`() {
        val original = testBaseAmount(1000000)
        val row = ProvenanceRow(BigDecimal("1000000"), null, null, null)
        val policy = policyOf(listOf(ProvenanceRuleId.CleanInteger, ProvenanceRuleId.DerivedVat))

        val judgement = ProvenanceRules.judgeRow(original, row, Fact.Absent(ReasonCode.EMPTY_INPUT), policy)

        judgement.classification shouldBe BaseAmountProvenance.Clean
    }
}
