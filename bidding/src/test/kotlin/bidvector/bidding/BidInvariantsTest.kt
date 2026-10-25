package bidvector.bidding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 소급 표시 · 페이퍼 분리 · 스냅샷 불변 · 값 불변식 — 계약의 「미달 금지」 목록을 각각 한 자리에서
 * 잰다.
 */
class BidInvariantsTest {
    // --- 소급 확인(Q-11) ------------------------------------------------------

    @Test
    fun `소급 표시는 출발 상태가 정한다 — 호출부가 주장할 자리가 없다`() {
        val command = BidCommand.RecordBid(submission(), null)

        val retro = applyCommand(BidState.DeadlinePassedUnrecorded, command, ActorKind.USER, T0)
        retro.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        retro.retroactive shouldBe true

        val normal = applyCommand(BidState.BidPlanned, command, ActorKind.USER, T0)
        normal.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        normal.retroactive shouldBe false
    }

    @Test
    fun `소급 표시가 기록에도 실린다`() {
        val outcome =
            applyCommand(
                BidState.DeadlinePassedUnrecorded,
                BidCommand.RecordBid(submission(), null),
                ActorKind.USER,
                T0,
            )
        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val effect = outcome.effect
        effect.shouldBeInstanceOf<TransitionEffect.RecordWritten>()
        effect.record.retroactive shouldBe true
    }

    @Test
    fun `확인 시각은 서버의 시계이고 제출 시각과 따로 남는다`() {
        val later = T0.plusSeconds(86_400)
        val outcome =
            applyCommand(
                BidState.BidPlanned,
                BidCommand.RecordBid(submission(), null),
                ActorKind.USER,
                later,
            )
        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val effect = outcome.effect
        effect.shouldBeInstanceOf<TransitionEffect.RecordWritten>()
        effect.record.confirmedAt shouldBe later
        effect.record.submission.submittedAt shouldBe T0
    }

    // --- 페이퍼 / 실 분리 ------------------------------------------------------

    @Test
    fun `페이퍼 건은 실투찰로 좁혀지지 않는다`() {
        case(BidState.BidRecorded, BidKind.PAPER).asReal() shouldBe null
        case(BidState.BidRecorded, BidKind.REAL).asReal() shouldBe
            RealBidCase(case(BidState.BidRecorded, BidKind.REAL))
    }

    @Test
    fun `대사 후보는 투찰 기록됨 상태의 실투찰 건뿐이다`() {
        for (state in ALL_STATES) {
            val real = case(state, BidKind.REAL).asReal()
            requireNotNull(real)
            isReconciliationCandidate(real) shouldBe (state == BidState.BidRecorded)
        }
    }

    // --- 스냅샷 불변 -----------------------------------------------------------

    @Test
    fun `초안 수정은 새 초안을 내고 앞 초안을 바꾸지 않는다`() {
        val first = draft()
        val revised = draft().copy(amount = submittedAmount().copy(won = 999_000_000L))

        val outcome = applyCommand(BidState.BidPlanned, BidCommand.DraftBid(revised), ActorKind.USER, T0)
        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val effect = outcome.effect
        effect.shouldBeInstanceOf<TransitionEffect.DraftWritten>()
        effect.draft shouldBe revised
        // 앞 초안의 스냅샷은 그대로다 — 값 타입이라 공유 변경 자체가 없다.
        first.snapshot shouldBe snapshot()
        first.amount.won shouldBe 1_234_000_000L
    }

    // --- 금액 출처(메뉴 값 위장 금지) ------------------------------------------

    @Test
    fun `메뉴에서 고른 금액은 고른 안의 참조를 반드시 든다`() {
        val menu = submittedAmount(BidAmountOrigin.PriceMenu(PriceMenuRef("menu-7")))
        val origin = menu.origin
        origin.shouldBeInstanceOf<BidAmountOrigin.PriceMenu>()
        origin.chosenOption.value shouldBe "menu-7"
        // 운영자 입력에는 그 자리가 아예 없다 — 참조 없는 메뉴 값이 표현되지 않는다.
        submittedAmount().origin shouldBe BidAmountOrigin.OperatorEntered
    }

    // --- 값 불변식 -------------------------------------------------------------

    @Test
    fun `사유 없는 건너뜀과 빈 전이 사유는 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> { SkipReason("  ") }
        shouldThrow<IllegalArgumentException> { TransitionReason("") }
    }

    @Test
    fun `음수 투찰 금액과 음수 추첨번호는 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> { submittedAmount().copy(won = -1L) }
        shouldThrow<IllegalArgumentException> { submission().copy(drawNumbers = listOf(0)) }
    }

    @Test
    fun `증빙 참조와 보관 기한은 함께 있거나 함께 없다`() {
        shouldThrow<IllegalArgumentException> {
            BidRecordEntry(submission(), T0, retroactive = false, evidenceRetainUntil = RETAIN_UNTIL)
        }
        shouldThrow<IllegalArgumentException> {
            BidRecordEntry(
                submission(EvidenceRef("doc-1")),
                T0,
                retroactive = false,
                evidenceRetainUntil = null,
            )
        }
    }

    @Test
    fun `증빙 참조와 메모는 문면에 값을 싣지 않는다`() {
        EvidenceRef("제출확인서-2026-10-21").toString() shouldNotContain "2026"
        BidMemo("경쟁사가 붙을 것 같다").toString() shouldNotContain "경쟁사"
    }
}
