package bidvector.procurement

import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmount
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
 * 최종낙찰업체명·참가업체수·진행구분·예정가격·기초금액·총예가건수·실개찰일시·복수예비가격
 * 자식 목록). 기존 4-positional 생성자 호출(`OpeningQualificationRepositoryTest` 등 3D 기존
 * test)이 그대로 컴파일·통과하는지가 「추가만」의 회귀 방지 test다.
 *
 * **§1.9.7 실측 정정(팀리드, 2026-09-08, 8건 23행 표본)** — `plnprc`(예정가격)·`bssamt`
 * (기초금액)·`totRsrvtnPrceNum`(총예가건수)·`rlOpengDt`(실개찰일시)는 응답이 행마다 반복해
 * 실어도 **공고 층**(부모)이다. 순번 공백은 총예가건수가 1(단수 예가)일 때만 관측됐다 —
 * 그때 자식 행은 0개이지만 부모의 예정가격·기초금액은 여전히 있어야 한다(잃지 않는다).
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
        result.plannedPrice shouldBe null
        result.baseAmount shouldBe null
        result.totalReservePriceCandidateCount shouldBe null
        result.actualOpeningAt shouldBe null
        result.reservePrices shouldBe emptyList()
    }

    @Test
    fun `새 슬롯을 이름 인자로 채울 수 있다`() {
        val award = AwardAmount(1_000_000L, Currency.KRW, Provenance.OperatorDeclared)
        val plannedPrice = YegaAmount(500_000_000L, Currency.KRW, Provenance.OperatorDeclared)
        val baseAmount = BaseAmount(480_000_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.OperatorDeclared)
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
                plannedPrice = plannedPrice,
                baseAmount = baseAmount,
                totalReservePriceCandidateCount = 1,
                actualOpeningAt = Instant.parse("2026-09-08T01:00:00Z"),
            )

        result.finalAwardAmount shouldBe award
        result.finalAwardCompanyName shouldBe "테스트상사"
        result.participantCount shouldBe 3
        result.progressDivision shouldBe "개찰완료"
        result.plannedPrice shouldBe plannedPrice
        result.baseAmount shouldBe baseAmount
        result.totalReservePriceCandidateCount shouldBe 1
    }

    @Test
    fun `participantCount 는 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningResult(id, null, null, Instant.EPOCH, participantCount = -1)
        }
    }

    @Test
    fun `totalReservePriceCandidateCount 는 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningResult(id, null, null, Instant.EPOCH, totalReservePriceCandidateCount = -1)
        }
    }

    @Test
    fun `OpeningReservePriceRow 는 sequenceNumber 부재를 타입으로 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningReservePriceRow("", null, null, Instant.EPOCH)
        }
        shouldThrow<IllegalArgumentException> {
            OpeningReservePriceRow("   ", null, null, Instant.EPOCH)
        }
    }

    @Test
    fun `OpeningReservePriceRow 는 drawCount 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningReservePriceRow("001", null, null, Instant.EPOCH, drawCount = -1)
        }
    }

    @Test
    fun `OpeningResult 는 자식 목록을 안는다 — 부모+자식 한 aggregate`() {
        val row =
            OpeningReservePriceRow(
                sequenceNumber = "001",
                baseReservePrice = ReservePriceCandidateAmount(500_000_000L, Currency.KRW),
                isDrawn = false,
                observedAt = Instant.EPOCH,
                drawCount = 0,
            )
        val result = OpeningResult(id, null, null, Instant.EPOCH, reservePrices = listOf(row))

        result.reservePrices shouldBe listOf(row)
    }

    /**
     * §1.9.7 실측이 겨눈 자리 — 총예가건수 1(단수 예가) + 순번 공백 응답은 **자식 행 0개**를
     * 낳지만, 예정가격·기초금액은 부모 슬롯이라 잃지 않는다(타입 층 증명). DB 왕복(부모에
     * 실제로 저장되는지)은 층 B(V4 마이그레이션, 별도 slice 계약 항목) 대상이다.
     */
    @Test
    fun `단수 예가(순번 공백)는 자식 행이 0개여도 부모의 예정가격 기초금액을 잃지 않는다`() {
        val plannedPrice = YegaAmount(300_000_000L, Currency.KRW, Provenance.OperatorDeclared)
        val baseAmount = BaseAmount(290_000_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.OperatorDeclared)
        val result =
            OpeningResult(
                id,
                null,
                null,
                Instant.EPOCH,
                plannedPrice = plannedPrice,
                baseAmount = baseAmount,
                totalReservePriceCandidateCount = 1,
                reservePrices = emptyList(),
            )

        result.reservePrices shouldBe emptyList()
        result.plannedPrice shouldBe plannedPrice
        result.baseAmount shouldBe baseAmount
        result.totalReservePriceCandidateCount shouldBe 1
    }

    @Test
    fun `ReservePriceCandidateAmount 는 음수 금액을 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            ReservePriceCandidateAmount(-1L, Currency.KRW)
        }
    }

    @Test
    fun `ReservePriceCandidateAmount 의 vatTreatment 는 항상 UNKNOWN 이다`() {
        ReservePriceCandidateAmount(500_000_000L, Currency.KRW).vatTreatment shouldBe VatTreatment.UNKNOWN
    }
}
