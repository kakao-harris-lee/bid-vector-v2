package bidvector.workflow.event

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

private val ENTRY_ID = OutboxEntryId("entry-1")

/**
 * scope.md ②, D-M4-5 (a) — outbox 전이표 전수 test. 허용 쌍 넷
 * (`Pending→Claimed`, `Claimed→Delivered`, `Claimed→Failed`, `Claimed→Isolated`)과
 * 표 밖 쌍 전부의 거부를 관측한다(설계 검토 (1) — 소진 `when`, 종단 셋에서 나가는
 * 화살표 없음).
 */
class OutboxTransitionTableTest {
    @Test
    fun `Pending 에 Claim 은 Claimed 로 accepted 전이한다`() {
        val outcome = transitionOutbox(ENTRY_ID, OutboxEntryState.Pending, OutboxCommand.Claim)

        outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Accepted>()
        outcome.transition.shouldBeInstanceOf<OutboxTransition.ToClaimed>()
        outcome.transition.entryId shouldBe ENTRY_ID
    }

    @Test
    fun `Claimed 에 Deliver 는 Delivered 로 accepted 전이한다`() {
        val outcome = transitionOutbox(ENTRY_ID, OutboxEntryState.Claimed, OutboxCommand.Deliver)

        outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Accepted>()
        outcome.transition.shouldBeInstanceOf<OutboxTransition.ToDelivered>()
    }

    @Test
    fun `Claimed 에 Fail 은 Failed 로 accepted 전이한다`() {
        val outcome = transitionOutbox(ENTRY_ID, OutboxEntryState.Claimed, OutboxCommand.Fail)

        outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Accepted>()
        outcome.transition.shouldBeInstanceOf<OutboxTransition.ToFailed>()
    }

    @Test
    fun `Claimed 에 Isolate 는 Isolated 로 accepted 전이한다`() {
        val outcome = transitionOutbox(ENTRY_ID, OutboxEntryState.Claimed, OutboxCommand.Isolate)

        outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Accepted>()
        outcome.transition.shouldBeInstanceOf<OutboxTransition.ToIsolated>()
    }

    @Test
    fun `표 밖의 (state, command) 쌍은 전부 거부되고 관측 가능하다`() {
        val allStates =
            listOf(
                OutboxEntryState.Pending,
                OutboxEntryState.Claimed,
                OutboxEntryState.Delivered,
                OutboxEntryState.Failed,
                OutboxEntryState.Isolated,
            )
        val allCommands =
            listOf(OutboxCommand.Claim, OutboxCommand.Deliver, OutboxCommand.Fail, OutboxCommand.Isolate)
        val allowedPairs =
            setOf(
                OutboxEntryState.Pending to OutboxCommand.Claim,
                OutboxEntryState.Claimed to OutboxCommand.Deliver,
                OutboxEntryState.Claimed to OutboxCommand.Fail,
                OutboxEntryState.Claimed to OutboxCommand.Isolate,
            )

        val offTablePairs =
            allStates.flatMap { state -> allCommands.map { command -> state to command } } - allowedPairs

        offTablePairs.forEach { (state, command) ->
            val outcome = transitionOutbox(ENTRY_ID, state, command)
            outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Rejected>()
            outcome.from shouldBe state
            outcome.command shouldBe command
        }
    }

    @Test
    fun `종단 셋 Delivered·Failed·Isolated 는 어느 command 로도 나가는 전이가 없다`() {
        val terminals = listOf(OutboxEntryState.Delivered, OutboxEntryState.Failed, OutboxEntryState.Isolated)
        val commands = listOf(OutboxCommand.Claim, OutboxCommand.Deliver, OutboxCommand.Fail, OutboxCommand.Isolate)

        terminals.forEach { terminal ->
            commands.forEach { command ->
                transitionOutbox(ENTRY_ID, terminal, command).shouldBeInstanceOf<OutboxTransitionOutcome.Rejected>()
            }
        }
    }
}
