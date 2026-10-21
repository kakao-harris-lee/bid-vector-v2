package bidvector.bidding

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 「다음 확인 시점」 — §6.1 *"어느 상태에서도 비어 있지 않다(없으면 「없음」이 명시된다)"*.
 *
 * 타입이 nullable 이 아니므로 「빈 값」은 이미 표현 불가다. 그래서 이 test 가 재는 것은 그
 * 다음 질문이다 — **`None` 이 정직한 자리에서만 나오는가.** `None` 이 조용히 넓어지면
 * 「없음」 표시가 「아직 안 정했다」를 덮는다.
 */
class NextCheckpointTest {
    private val deadline = T0.plusSeconds(1800)
    private val expected = T0.plusSeconds(259_200)

    /** 상태 꼬리표 → 기대 종류(입력이 전부 있을 때). `null` 은 「없음」이 정답인 자리다. */
    private val expectedKind: Map<BidStateTag, CheckpointKind?> =
        mapOf(
            BidStateTag.WATCHING to CheckpointKind.DEADLINE,
            BidStateTag.DEFERRED to CheckpointKind.REMINDER,
            BidStateTag.PRICE_RECEIVED to CheckpointKind.DEADLINE,
            BidStateTag.BID_PLANNED to CheckpointKind.DEADLINE,
            BidStateTag.BID_RECORDED to null,
            BidStateTag.AWAITING_OPENING to CheckpointKind.OPENING,
            BidStateTag.OPENED_PROVISIONAL_FIRST to CheckpointKind.NOTICE_EXPECTED,
            BidStateTag.OPENED_OUT_OF_RANK to CheckpointKind.NOTICE_EXPECTED,
            BidStateTag.UNDER_QUALIFICATION_REVIEW to CheckpointKind.DOCUMENT_DUE,
            BidStateTag.WON to null,
            BidStateTag.LOST to null,
            BidStateTag.FAILED_OR_REBID to null,
            BidStateTag.SKIPPED to null,
            BidStateTag.DEADLINE_PASSED_UNRECORDED to null,
        )

    @Test
    fun `기대표가 상태 전수를 덮는다`() {
        expectedKind.keys shouldContainExactlyInAnyOrder BidStateTag.entries
    }

    @Test
    fun `전 상태에서 답이 나오고 종류가 기대와 같다`() {
        for (state in ALL_STATES) {
            val checkpoint = nextCheckpoint(state, deadline, expected)
            when (val kind = expectedKind.getValue(state.tag)) {
                null -> checkpoint shouldBe NextCheckpoint.None
                else -> {
                    checkpoint.shouldBeInstanceOf<NextCheckpoint.At>()
                    checkpoint.kind shouldBe kind
                }
            }
        }
    }

    @Test
    fun `필수 입력은 상태가 실어 공고 마감이 없어도 사라지지 않는다`() {
        // 마감·통보 예정일을 모르는 상태에서도, 상태가 싣는 축(리마인드·개찰·서류 기한)은 답이 있다.
        nextCheckpoint(BidState.Deferred(T0), null, null)
            .shouldBeInstanceOf<NextCheckpoint.At>()
        nextCheckpoint(BidState.AwaitingOpening(T0), null, null)
            .shouldBeInstanceOf<NextCheckpoint.At>()
        nextCheckpoint(BidState.UnderQualificationReview(T0), null, null)
            .shouldBeInstanceOf<NextCheckpoint.At>()
    }

    @Test
    fun `마감을 모르면 없음이 명시된다 — 빈 값이 아니다`() {
        nextCheckpoint(BidState.Watching, null, null) shouldBe NextCheckpoint.None
    }

    @Test
    fun `지난 일정 경고는 서버의 한 시각이 판정한다`() {
        val past = NextCheckpoint.At(T0.minusSeconds(1), CheckpointKind.DEADLINE)
        val future = NextCheckpoint.At(T0.plusSeconds(1), CheckpointKind.DEADLINE)
        isOverdue(past, T0) shouldBe true
        isOverdue(future, T0) shouldBe false
        // 「없음」은 지나지 않는다 — 경고가 붙을 시각 자체가 없다.
        isOverdue(NextCheckpoint.None, T0) shouldBe false
    }
}
