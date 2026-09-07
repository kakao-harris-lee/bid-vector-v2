package bidvector.procurement

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** ⑥ — 파생이 원본을 덮지 않는다. `isAuthoritative`는 데이터, `Direct`는 `Published`만 받는다. */
class ResolvedBaseAmountTest {
    @Test
    fun `Direct 는 Published 값으로만 조립된다 — 우회 (4) 예산 키 값은 이 자리에 컴파일되지 않는다`() {
        val direct =
            ResolvedBaseAmount.Direct.of(
                1_000_000L,
                Currency.KRW,
                VatTreatment.UNKNOWN,
                Provenance.Published(NoticeRound.of("000")),
            )

        direct.amount.provenance shouldBe Provenance.Published(NoticeRound.of("000"))
    }

    @Test
    fun `FallbackFromBudget 은 FilledFromBudgetKey 가 아니면 거부한다`() {
        val wrongProvenanceAmount = BaseAmount(1L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.Undeclared)

        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            ResolvedBaseAmount.FallbackFromBudget(RawKey("bdgtAmt"), wrongProvenanceAmount)
        }
    }

    @Test
    fun `Published 와 OperatorDeclared 만 authoritative 다 — 표에 없으면 기본 false`() {
        isAuthoritative(ProvenanceKind.PUBLISHED) shouldBe true
        isAuthoritative(ProvenanceKind.OPERATOR_DECLARED) shouldBe true
        isAuthoritative(ProvenanceKind.DERIVED_FROM_OPENING) shouldBe false
        isAuthoritative(ProvenanceKind.FILLED_FROM_BUDGET_KEY) shouldBe false
        isAuthoritative(ProvenanceKind.COPIED_FROM_BASE_AMOUNT) shouldBe false
        isAuthoritative(ProvenanceKind.UNDECLARED) shouldBe false
    }

    @Test
    fun `mayOverwrite 는 빈 자리는 항상 채우고, 채워진 자리는 권위값만 덮는다 — §5-1 규율 1`() {
        mayOverwrite(existingProvenance = null, incomingProvenance = Provenance.Undeclared) shouldBe true
        mayOverwrite(
            existingProvenance = Provenance.Published(NoticeRound.of("000")),
            incomingProvenance = Provenance.DerivedFromOpening,
        ) shouldBe false
        mayOverwrite(
            existingProvenance = Provenance.FilledFromBudgetKey("bdgtAmt"),
            incomingProvenance = Provenance.Published(NoticeRound.of("000")),
        ) shouldBe true
    }
}
