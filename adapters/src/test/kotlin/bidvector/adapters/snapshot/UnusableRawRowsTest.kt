package bidvector.adapters.snapshot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * M6/6G-2c — 버린 원문 행의 **계수 값**(vr r1 F-5 · cr r1 K-5). 재는 것은 셈이 아니라 **값의 성질**
 * 이다: 넘긴 지도를 뒤에서 바꿔도 변하지 않는가, 같은 계수가 같은 값인가, 음수를 거부하는가.
 */
class UnusableRawRowsTest {
    /**
     * **vr r1 F-5 — 별칭으로 불변식을 우회할 수 없다.** 앞 판은 넘긴 지도를 그대로 들었고, 호출부가
     * 그 지도를 뒤에서 바꾸면 생성자의 비음수 검사를 지난 값이 음수 합계를 냈다(실측 `total=-5`).
     */
    @Test
    fun `넘긴 지도를 뒤에서 바꿔도 계수가 변하지 않는다`() {
        val counts = mutableMapOf(UnusableRawRowCause.BLANK_NOTICE_NUMBER to 2)
        val rows = UnusableRawRows(counts)

        counts[UnusableRawRowCause.BLANK_NOTICE_NUMBER] = -5
        counts[UnusableRawRowCause.MALFORMED_ROUND] = 7

        rows.total shouldBe 2
        rows[UnusableRawRowCause.BLANK_NOTICE_NUMBER] shouldBe 2
        rows[UnusableRawRowCause.MALFORMED_ROUND] shouldBe 0
    }

    /**
     * **cr r1 K-5 — 같은 계수는 같은 값이다.** 빈 지도와 「0 셋을 적은 지도」는 `total` 도 `get` 도
     * 같은데 `equals` 가 달랐다. `SnapshotExtraction` 도 값 비교를 하므로, 계수가 완전히 같은 두
     * 추출이 불일치로 읽혔다.
     */
    @Test
    fun `빈 지도와 0 셋을 적은 지도는 같은 값이다`() {
        val dense =
            UnusableRawRows(UnusableRawRowCause.entries.associateWith { 0 })

        dense shouldBe UnusableRawRows.NONE
        dense.hashCode() shouldBe UnusableRawRows.NONE.hashCode()
        // 음성 대조 — 한 칸이라도 다르면 다른 값이다.
        UnusableRawRows(mapOf(UnusableRawRowCause.MALFORMED_ROUND to 1)) shouldNotBe UnusableRawRows.NONE
    }

    /** 칸이 빠진 지도도 **조밀**하게 선다 — 줄에서 칸이 사라지지 않는다. */
    @Test
    fun `적히지 않은 원인은 0 으로 선다`() {
        val rows = UnusableRawRows(mapOf(UnusableRawRowCause.UNKNOWN_ENDPOINT to 4))

        rows.byCause.keys shouldBe UnusableRawRowCause.entries.toSet()
        rows[UnusableRawRowCause.BLANK_NOTICE_NUMBER] shouldBe 0
        rows.total shouldBe 4
    }

    @Test
    fun `음수 계수는 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            UnusableRawRows(mapOf(UnusableRawRowCause.BLANK_NOTICE_NUMBER to -1))
        }
    }
}
