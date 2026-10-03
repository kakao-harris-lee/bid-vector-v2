package bidvector.app.collection

import bidvector.adapters.snapshot.SnapshotExtraction
import bidvector.adapters.snapshot.SnapshotNotice
import bidvector.adapters.snapshot.SnapshotOutcome
import bidvector.adapters.snapshot.SnapshotRow
import bidvector.adapters.snapshot.UnusableRawRowCause
import bidvector.adapters.snapshot.UnusableRawRows
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * D-6G2d-34 항목 4 — 추출 로그 줄의 **칸↔계수 배선**을 잠근다. 이 줄이 계수들의 유일한 공시 자리라
 * (manifest 어휘를 늘리지 않는다) 배선이 어긋나면 운영자가 읽는 수가 딴 것을 가리킨다.
 *
 * 값을 **서로 겹치지 않는 수**로 준다. 그래야 두 계수를 서로 바꾼 변이가 붉어진다 — 앞 판은 E2E
 * fixture 로 세 계수의 **존재만** 재서(셋 다 0) 칸을 리터럴 `0` 으로 바꾼 변이가 초록이었다(vr r3 L-2).
 */
class SnapshotFinishedLineTest {
    @Test
    fun `계수마다 다른 수를 주면 줄의 칸이 그 수를 그대로 싣는다`() {
        val line = snapshotFinishedLine(sampleSize = 11, extraction = distinctCounts(), bytes = 97)

        line shouldBe
            "snapshot-extract finished rows=3 sampleSize=11 sampledWithoutDetail=5 " +
            "skippedWithoutNotice=7 incompleteAxis=13 unusableRawRows=17 " +
            "blankNoticeNumber=2 malformedRound=3 unknownEndpoint=12 " +
            "fractionalAmounts=19 incompleteAValues=23 outsideSample=29 bytes=97"
    }

    /**
     * **D-6G2c-20 — 합계 칸은 원인 셋의 합이다.** 합계를 따로 들면 그 둘이 갈리는 날이 오고, 갈린
     * 뒤에는 어느 쪽이 참인지 줄만 보고 알 수 없다. 합계는 **파생**이므로 여기서 그것을 못 박는다.
     */
    @Test
    fun `원인 셋의 합이 곧 합계 칸이다`() {
        val causes = distinctCounts().unusableRawRows

        causes.total shouldBe
            UnusableRawRowCause.entries.sumOf { causes[it] }
    }

    /** 등재되지 않은 원인은 0 이다 — 빈 지도를 받은 추출의 줄에서 칸이 사라지지 않는다. */
    @Test
    fun `원인 지도가 비면 세 칸이 모두 0 으로 선다`() {
        val line = snapshotFinishedLine(sampleSize = 1, extraction = noUnusableRows(), bytes = 1)

        line shouldContain "unusableRawRows=0 blankNoticeNumber=0 malformedRound=0 unknownEndpoint=0 "
    }

    private fun noUnusableRows() = distinctCounts().copy(unusableRawRows = UnusableRawRows.NONE)

    private fun distinctCounts() =
        SnapshotExtraction(
            rows = List(3) { placeholderRow() },
            skippedWithoutNotice = 7,
            sampledWithoutDetail = 5,
            observedOutsideSample = 29,
            incompleteAxis = 13,
            unusableRawRows =
                UnusableRawRows(
                    mapOf(
                        UnusableRawRowCause.BLANK_NOTICE_NUMBER to 2,
                        UnusableRawRowCause.MALFORMED_ROUND to 3,
                        UnusableRawRowCause.UNKNOWN_ENDPOINT to 12,
                    ),
                ),
            fractionalAmounts = 19,
            incompleteAValues = 23,
        )

    /** 줄은 행의 **수**만 읽는다 — 내용은 이 test 의 물음이 아니라서 값 하나로 채운다. */
    private fun placeholderRow() = SnapshotRow(placeholderNotice(), placeholderOutcome())

    private fun placeholderNotice() =
        SnapshotNotice(
            noticeKeyHash = "0".repeat(64),
            category = "공사",
            noticedOn = LocalDate.of(2026, 1, 2),
            bidCloseAt = Instant.parse("2026-01-03T05:00:00Z"),
            baseAmount = null,
            baseAmountDisclosedAt = null,
            floorRate = null,
            reserveRangeBeginRate = null,
            reserveRangeEndRate = null,
            aValueTotal = null,
            aValueOpenAt = null,
            standardMarketPriceApplicable = null,
            bidPriceFormulaAApplicable = null,
            successfulBidMethodCode = null,
            successfulBidMethodName = null,
            prearrangedPriceDecisionMethod = null,
            hasAwardMethodApplicationStandard = false,
            hasApplicationBasisContent = false,
            noticeOrdinal = 1,
            procurementClassCode = null,
            demandAgencyCode = null,
            pureConstructionCost = null,
        )

    private fun placeholderOutcome() =
        SnapshotOutcome(
            openedOn = null,
            progressDivision = null,
            plannedPrice = null,
            openingBaseAmount = null,
            reservePrices = null,
            drawnSerialNumbers = null,
            participantCount = null,
            bidderRows = emptyList(),
        )
}
