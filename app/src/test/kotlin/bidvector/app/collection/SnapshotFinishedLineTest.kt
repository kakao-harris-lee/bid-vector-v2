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
     * **D-6G2c-20 — 줄의 합계 칸은 줄의 원인 세 칸의 합이다**(cr r1 K-4 정정). 앞 판은 값 쪽에서
     * `total` 과 열거 합을 맞댔는데, 지도가 열거 키 전용이라 두 식이 **모든 입력에서 같았다** — 어떤
     * 변이도 가르지 못하는 단언이었다. 재야 할 것은 값이 아니라 **렌더한 줄**이다: 합계를 원인과 다른
     * 자리에서 길어 오는 편집이 그때 붉어진다.
     */
    @Test
    fun `렌더한 줄의 합계 칸은 되읽은 원인 세 칸의 합이다`() {
        val fields = fieldsOf(snapshotFinishedLine(sampleSize = 11, extraction = distinctCounts(), bytes = 97))

        fields.getValue("unusableRawRows") shouldBe
            CAUSE_FIELDS.sumOf { fields.getValue(it) }
    }

    /** 줄을 칸으로 되읽는다 — 재는 쪽이 줄을 짓는 코드를 다시 부르지 않는다. */
    private fun fieldsOf(line: String): Map<String, Int> =
        Regex("(\\w+)=(\\d+)")
            .findAll(line)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

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

/** 줄에 서는 원인 칸 셋 — 이름이 바뀌면 되읽기가 멈춘다(`getValue` 가 던진다). */
private val CAUSE_FIELDS = listOf("blankNoticeNumber", "malformedRound", "unknownEndpoint")
