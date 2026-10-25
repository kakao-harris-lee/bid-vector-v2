package bidvector.adapters.bidding

import bidvector.bidding.BidStateTag
import bidvector.bidding.tag
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * **상태 복원의 전수**(계약 D-7B-3 · 검토 H-3) — `BidCaseRow.toState` 의 KDoc 이 가리키던 test 다(앞 판에는
 * 이 class 가 없었다).
 *
 * 복원은 둘로 나뉜다: payload 를 요구하는 넷은 `when` 이, 나머지는 룩업표가 든다. `when` 에 `else` 가 있어
 * 컴파일러가 전수를 보장하지 않으므로, 한쪽에서 꼬리표가 빠지면 그 상태의 건을 **읽을 때** 터진다(조회 500).
 * 여기서 꼬리표 **전수**를 돌려 「복원한 상태의 꼬리표가 입력과 같다」를 잰다.
 */
class BidCaseRowTest {
    private val at = Instant.parse("2026-10-21T04:00:00Z")

    @Test
    fun `상태 꼬리표 열넷이 전부 복원되고 꼬리표가 보존된다`() {
        BidStateTag.entries.size shouldBe STATE_COUNT

        BidStateTag.entries.filterNot { tag ->
            BidCaseRow.toState(tag, remindAt = at, openingAt = at, documentDueAt = at, skipReason = "사유").tag == tag
        } shouldBe emptyList()
    }

    /** payload 를 요구하는 상태는 열이 비면 **조용한 기본값으로 접지 않는다** — CHECK 를 지나오지 못했어야 할 행이다. */
    @Test
    fun `payload 를 요구하는 상태 넷은 열이 비면 복원을 거부한다`() {
        PAYLOAD_TAGS.forEach { tag ->
            shouldThrow<IllegalArgumentException> {
                BidCaseRow.toState(tag, remindAt = null, openingAt = null, documentDueAt = null, skipReason = null)
            }
        }
    }

    private companion object {
        const val STATE_COUNT = 14
        val PAYLOAD_TAGS =
            listOf(
                BidStateTag.DEFERRED,
                BidStateTag.AWAITING_OPENING,
                BidStateTag.UNDER_QUALIFICATION_REVIEW,
                BidStateTag.SKIPPED,
            )
    }
}
