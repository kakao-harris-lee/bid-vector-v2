package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * ② [deriveBudgetCapture](scope.md, legacy `allocation_core.py:104-` `budget_capture_score`).
 * legacy 「base 없으면 중립 0.5」sentinel 뒤집기(D-4B5-1, 우회 (1)) — 1B [bidRateAgainst]
 * 산술만 쓴다(D-4B5-4).
 */
class BudgetCaptureDerivationTest {
    private val policy = resolvedTestPolicy()

    @Test
    fun `base 가 null 이면 BaseAmountMissing`() {
        val outcome = deriveBudgetCapture(testBidAmount(500_000_000L), null, policy)
        outcome shouldBe DerivationOutcome.Absent(DerivationAbsence.BaseAmountMissing)
    }

    @Test
    fun `base 가 0 이하면 BaseAmountMissing(우회 1 — legacy 중립 0_5 sentinel 폐기)`() {
        val outcome = deriveBudgetCapture(testBidAmount(500_000_000L), testBaseAmount(0L), policy)
        outcome shouldBe DerivationOutcome.Absent(DerivationAbsence.BaseAmountMissing)
    }

    @Test
    fun `recommended 가 null 이면 RecommendationMissing`() {
        val outcome = deriveBudgetCapture(null, testBaseAmount(1_000_000_000L), policy)
        outcome shouldBe DerivationOutcome.Absent(DerivationAbsence.RecommendationMissing)
    }

    @Test
    fun `recommended 가 base 의 절반이면 0_5(1B 산술 왕복)`() {
        val outcome = deriveBudgetCapture(testBidAmount(500_000_000L), testBaseAmount(1_000_000_000L), policy)
        val present = outcome.shouldBeInstanceOf<DerivationOutcome.Present<UnitScore>>()
        present.value.value.compareTo(BigDecimal("0.5")) shouldBe 0
    }

    @Test
    fun `recommended 가 base 를 넘으면 1_0 으로 clamp`() {
        val outcome = deriveBudgetCapture(testBidAmount(1_500_000_000L), testBaseAmount(1_000_000_000L), policy)
        val present = outcome.shouldBeInstanceOf<DerivationOutcome.Present<UnitScore>>()
        present.value.value.compareTo(BigDecimal.ONE) shouldBe 0
    }

    @Test
    fun `recommended 가 base 와 정확히 같으면 1_0`() {
        val outcome = deriveBudgetCapture(testBidAmount(1_000_000_000L), testBaseAmount(1_000_000_000L), policy)
        val present = outcome.shouldBeInstanceOf<DerivationOutcome.Present<UnitScore>>()
        present.value.value.compareTo(BigDecimal.ONE) shouldBe 0
    }

    @Test
    fun `VAT 불일치는 MoneyArithmeticUnmeasurable(D-4B5-4 — bidRateAgainst 의 나머지 사유 흡수)`() {
        val recommended =
            testBidAmount(500_000_000L, vat = VatTreatment.INCLUSIVE, provenance = Provenance.OperatorDeclared)
        val base =
            testBaseAmount(1_000_000_000L, vat = VatTreatment.EXCLUSIVE, provenance = Provenance.OperatorDeclared)

        val outcome = deriveBudgetCapture(recommended, base, policy)

        outcome shouldBe
            DerivationOutcome.Absent(DerivationAbsence.MoneyArithmeticUnmeasurable(ReasonCode.VAT_TREATMENT_MISMATCH))
    }

    @Test
    fun `미선언 출처는 MoneyArithmeticUnmeasurable`() {
        // BidAmount 는 roundedWith 자체가 provenance 를 먼저 검사해(1B) Undeclared 로는
        // 애초에 만들 수 없다 — 그래서 base 쪽을 Undeclared 로 둔다(divideForRate 는 둘 중
        // 하나라도 Undeclared 면 UNDECLARED_PROVENANCE 를 낸다, 순서상 base 도 해당).
        val recommended = testBidAmount(500_000_000L)
        val base = testBaseAmount(1_000_000_000L, provenance = Provenance.Undeclared)

        val outcome = deriveBudgetCapture(recommended, base, policy)

        outcome shouldBe
            DerivationOutcome.Absent(DerivationAbsence.MoneyArithmeticUnmeasurable(ReasonCode.UNDECLARED_PROVENANCE))
    }
}
