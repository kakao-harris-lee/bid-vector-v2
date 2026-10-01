package bidvector.app.collection

import bidvector.adapters.snapshot.SnapshotExtraction
import bidvector.adapters.snapshot.SnapshotNotice
import bidvector.adapters.snapshot.SnapshotOutcome
import bidvector.adapters.snapshot.SnapshotRow
import io.kotest.matchers.shouldBe
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
            "fractionalAmounts=19 incompleteAValues=23 outsideSample=29 bytes=97"
    }

    private fun distinctCounts() =
        SnapshotExtraction(
            rows = List(3) { placeholderRow() },
            skippedWithoutNotice = 7,
            sampledWithoutDetail = 5,
            observedOutsideSample = 29,
            incompleteAxis = 13,
            unusableRawRows = 17,
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
