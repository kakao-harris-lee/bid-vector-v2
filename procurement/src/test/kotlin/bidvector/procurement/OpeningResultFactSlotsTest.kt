package bidvector.procurement

import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.YegaAmount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * ③ `OpeningResult` fact 슬롯 확장(D-3E-4 (a)) — 3B-2가 실제로 수집하는 것만(최종낙찰금액·
 * 최종낙찰업체명·참가업체수·진행구분·복수예비가격 자식 목록). 기존 4-positional 생성자
 * 호출(`OpeningQualificationRepositoryTest` 등 3D 기존 test)이 그대로 컴파일·통과하는지가
 * 「추가만」의 회귀 방지 test다.
 */
class OpeningResultFactSlotsTest {
    private val id = NoticeId(NoticeNumber.of("SLOT-20260908-001"), bidvector.sharedkernel.NoticeRound.of("000"))

    @Test
    fun `기존 4-positional 생성 호출은 새 슬롯이 전부 기본값(null 또는 빈 목록)이다`() {
        val result = OpeningResult(id, null, null, Instant.EPOCH)

        result.finalAwardAmount shouldBe null
        result.finalAwardCompanyName shouldBe null
        result.participantCount shouldBe null
        result.progressDivision shouldBe null
        result.reservePrices shouldBe emptyList()
    }

    @Test
    fun `새 슬롯을 이름 인자로 채울 수 있다`() {
        val award = AwardAmount(1_000_000L, Currency.KRW, Provenance.OperatorDeclared)
        val result =
            OpeningResult(
                noticeId = id,
                winningRate = null,
                derivedBaseAmount = null,
                observedAt = Instant.EPOCH,
                finalAwardAmount = award,
                finalAwardCompanyName = "테스트상사",
                participantCount = 3,
                progressDivision = "개찰완료",
            )

        result.finalAwardAmount shouldBe award
        result.finalAwardCompanyName shouldBe "테스트상사"
        result.participantCount shouldBe 3
        result.progressDivision shouldBe "개찰완료"
    }

    @Test
    fun `participantCount 는 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningResult(id, null, null, Instant.EPOCH, participantCount = -1)
        }
    }

    @Test
    fun `OpeningReservePriceRow 는 sequenceNumber 부재를 타입으로 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningReservePriceRow("", null, null, null, null)
        }
        shouldThrow<IllegalArgumentException> {
            OpeningReservePriceRow("   ", null, null, null, null)
        }
    }

    @Test
    fun `OpeningResult 는 자식 목록을 안는다 — 부모+자식 한 aggregate`() {
        val row =
            OpeningReservePriceRow(
                sequenceNumber = "001",
                plannedPrice = YegaAmount(500_000_000L, Currency.KRW, Provenance.OperatorDeclared),
                baseReservePrice =
                    ReservePriceCandidateAmount(
                        500_000_000L,
                        Currency.KRW,
                        VatTreatment.UNKNOWN,
                        Provenance.OperatorDeclared,
                    ),
                isDrawn = false,
                actualOpeningAt = Instant.parse("2026-09-08T01:00:00Z"),
            )
        val result = OpeningResult(id, null, null, Instant.EPOCH, reservePrices = listOf(row))

        result.reservePrices shouldBe listOf(row)
    }

    @Test
    fun `ReservePriceCandidateAmount 는 음수 금액을 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            ReservePriceCandidateAmount(-1L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.OperatorDeclared)
        }
    }
}
