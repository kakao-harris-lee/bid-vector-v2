package bidvector.procurement

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

private val OBSERVED_AT = Instant.parse("2026-09-09T00:00:00Z")

/**
 * M3/3F D-3F-4 — 개찰완료 축 부모 슬롯 셋. D-3F-3 해소로 투찰자별 canonical 표를 만들지
 * 않는다 — 부모는 ① 개찰 1위 축([OpeningRankOneOutcome]) ② 관측된 추첨번호 집합
 * ([DrawNumberObservation]) 만 는다(③ 개찰결과구분명은 기존 `progressDivision` 재사용,
 * 3E 슬롯 편집 없음).
 *
 * 「1위를 특정할 수 없으면(순위 1 부재·중복) 그 축을 비우고 명시적 회계 — 투찰금액으로
 * 순위를 재계산하지 않는다」(scope.md ②)와 「범위 검사와 검사 불가」(scope.md ④)를
 * 타입으로 고정한다. **verifier r1 F-2 뒤** — 두 sealed 타입 모두 `observedAt`(축 자신의
 * 관측 시각, `NotObserved`만 `null`)을 나른다. 저장 경로의 왕복·낡음 판정 test 는
 * `OpeningCompleteAxisRepositoryTest`(adapters/persistence) 소관이다.
 */
class OpeningCompleteAxisTest {
    private val id = NoticeId(NoticeNumber.of("OPENG-20260909-001"), bidvector.sharedkernel.NoticeRound.of("000"))

    // =========================================================================
    // OpeningResult 새 슬롯 — 추가만, 기본값 회귀 방지
    // =========================================================================
    @Test
    fun `기존 4-positional 생성 호출은 새 슬롯이 NotObserved 다`() {
        val result = OpeningResult(id, null, null, Instant.EPOCH)

        result.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
        result.drawNumbers shouldBe DrawNumberObservation.NotObserved
        result.openingRankOne.observedAt shouldBe null
        result.drawNumbers.observedAt shouldBe null
    }

    @Test
    fun `새 슬롯을 이름 인자로 채울 수 있다`() {
        val bid = OpeningRankOneBid(bidderName = "SYN-CORP", bidAmount = ObservedBidAmount(900_000_000L, Currency.KRW))
        val result =
            OpeningResult(
                id,
                null,
                null,
                Instant.EPOCH,
                openingRankOne = OpeningRankOneOutcome.Determined(bid, OBSERVED_AT),
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7), OBSERVED_AT),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.Determined(bid, OBSERVED_AT)
        result.drawNumbers shouldBe DrawNumberObservation.Verified(setOf(3, 7), OBSERVED_AT)
    }

    // =========================================================================
    // ObservedBidAmount — bidprcAmt 관측값(파생 BidAmount(Basis.BID)와 다른 축, 구성 자체가 다르다)
    // =========================================================================
    @Test
    fun `ObservedBidAmount 는 음수 금액을 거부한다`() {
        shouldThrow<IllegalArgumentException> { ObservedBidAmount(-1L, Currency.KRW) }
    }

    @Test
    fun `ObservedBidAmount 의 vatTreatment 는 항상 UNKNOWN 이다`() {
        ObservedBidAmount(900_000_000L, Currency.KRW).vatTreatment shouldBe VatTreatment.UNKNOWN
    }

    // =========================================================================
    // OpeningRankOneBid — 협상 계약(투찰금액·투찰율 부재)도 값으로 나른다(scope.md ⑦)
    // =========================================================================
    @Test
    fun `OpeningRankOneBid 는 bidderName 부재를 타입으로 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> { OpeningRankOneBid(bidderName = "") }
        shouldThrow<IllegalArgumentException> { OpeningRankOneBid(bidderName = "   ") }
    }

    @Test
    fun `협상 계약 — 투찰금액 투찰율이 부재여도 상호는 값으로 남는다`() {
        val bid = OpeningRankOneBid(bidderName = "SYN-NEGOTIATED", bidAmount = null, bidRate = null)

        bid.bidderName shouldBe "SYN-NEGOTIATED"
        bid.bidAmount shouldBe null
        bid.bidRate shouldBe null
    }

    @Test
    fun `OpeningRankOneBid 는 bidRate 를 plain Rate 로 나른다`() {
        val bid = OpeningRankOneBid(bidderName = "SYN-CORP", bidRate = Rate.ofPercent(BigDecimal("87.995")))

        bid.bidRate shouldBe Rate.ofPercent(BigDecimal("87.995"))
    }

    // =========================================================================
    // OpeningRankOneOutcome.resolve — 순위 1 부재·중복은 축을 비우고 명시적으로 회계한다
    // =========================================================================
    @Test
    fun `resolve — 후보가 없으면 NotObserved 다`() {
        OpeningRankOneOutcome.resolve(emptyList(), OBSERVED_AT) shouldBe OpeningRankOneOutcome.NotObserved
    }

    @Test
    fun `resolve — 순위 1 이 없으면 RankMissing 이다(투찰금액으로 재계산하지 않는다)`() {
        val candidates =
            listOf(
                2 to OpeningRankOneBid("SYN-A"),
                3 to OpeningRankOneBid("SYN-B"),
                null to OpeningRankOneBid("SYN-C"),
            )

        OpeningRankOneOutcome.resolve(candidates, OBSERVED_AT) shouldBe OpeningRankOneOutcome.RankMissing(OBSERVED_AT)
    }

    @Test
    fun `resolve — 순위 1 이 둘 이상이면 RankDuplicated 다(동값이 실재한다)`() {
        val candidates =
            listOf(
                1 to OpeningRankOneBid("SYN-A"),
                1 to OpeningRankOneBid("SYN-B"),
                2 to OpeningRankOneBid("SYN-C"),
            )

        val outcome = OpeningRankOneOutcome.resolve(candidates, OBSERVED_AT)
        val duplicated = outcome.shouldBeInstanceOf<OpeningRankOneOutcome.RankDuplicated>()
        duplicated.count shouldBe 2
        duplicated.observedAt shouldBe OBSERVED_AT
    }

    @Test
    fun `resolve — 순위 1 이 정확히 하나면 Determined 다`() {
        val winner = OpeningRankOneBid("SYN-WINNER", bidAmount = ObservedBidAmount(1_000L, Currency.KRW))
        val candidates = listOf(1 to winner, 2 to OpeningRankOneBid("SYN-B"))

        OpeningRankOneOutcome.resolve(candidates, OBSERVED_AT) shouldBe
            OpeningRankOneOutcome.Determined(winner, OBSERVED_AT)
    }

    @Test
    fun `RankDuplicated 는 count 2 미만을 타입으로 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> { OpeningRankOneOutcome.RankDuplicated(1, OBSERVED_AT) }
        shouldThrow<IllegalArgumentException> { OpeningRankOneOutcome.RankDuplicated(0, OBSERVED_AT) }
    }

    // =========================================================================
    // DrawNumberObservation.of — 범위 검사와 「검사 불가」를 명시적 결과로(scope.md ④)
    // =========================================================================
    @Test
    fun `of — 번호가 없으면 NotObserved 다(부재 7-15 형태, 정상)`() {
        DrawNumberObservation.of(emptySet(), 15, OBSERVED_AT) shouldBe DrawNumberObservation.NotObserved
        DrawNumberObservation.of(emptySet(), null, OBSERVED_AT) shouldBe DrawNumberObservation.NotObserved
    }

    @Test
    fun `of — 총예가건수를 모르면 번호가 있어도 RangeCheckUnavailable 이다(1-9-7 실측, 조용한 통과가 아니다)`() {
        DrawNumberObservation.of(setOf(3, 7), null, OBSERVED_AT) shouldBe
            DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7), OBSERVED_AT)
    }

    @Test
    fun `of — 1부터 총예가건수까지 안이면 Verified 다`() {
        DrawNumberObservation.of(setOf(1, 15), 15, OBSERVED_AT) shouldBe
            DrawNumberObservation.Verified(setOf(1, 15), OBSERVED_AT)
    }

    @Test
    fun `of — 범위 밖 번호가 섞이면 조용히 통과시키지 않고 OutOfRange 다`() {
        val outcome = DrawNumberObservation.of(setOf(3, 16), 15, OBSERVED_AT)

        outcome shouldBe DrawNumberObservation.OutOfRange(setOf(3, 16), 1..15, OBSERVED_AT)
    }

    @Test
    fun `of — 0 은 1-기반 인덱스 밖이라 OutOfRange 다`() {
        DrawNumberObservation.of(setOf(0), 15, OBSERVED_AT) shouldBe
            DrawNumberObservation.OutOfRange(setOf(0), 1..15, OBSERVED_AT)
    }
}
