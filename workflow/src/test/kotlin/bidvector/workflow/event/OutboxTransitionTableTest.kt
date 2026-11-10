package bidvector.workflow.event

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

private val ENTRY_ID = OutboxEntryId("entry-1")

/**
 * scope.md ②, D-M4-5 (a) — outbox 전이표 전수 test. 허용 쌍은 `Pending→Claimed` 와
 * `Claimed→종단`이고, 표 밖 쌍 전부의 거부를 관측한다(설계 검토 (1) — 소진 `when`, 종단
 * 셋에서 나가는 화살표 없음).
 *
 * **종단 명령은 사유를 갖는다(M9/OPS-05)** — `Deliver`·`Fail`·`Isolate` 셋이
 * [OutboxCommand.Terminate] 하나로 모였고 **목표 상태를 사유가 정한다.** 명령과 사유를 따로
 * 받으면 둘이 어긋난 조합(`Fail` + `ORPHANED_CLAIM`)이 성립하므로 그 자리를 없앴다.
 */
class OutboxTransitionTableTest {
    @Test
    fun `Pending 에 Claim 은 Claimed 로 accepted 전이한다`() {
        val outcome = transitionOutbox(ENTRY_ID, OutboxEntryState.Pending, OutboxCommand.Claim)

        outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Accepted>()
        outcome.transition.shouldBeInstanceOf<OutboxTransition.ToClaimed>()
        outcome.transition.entryId shouldBe ENTRY_ID
    }

    /**
     * 사유 일곱 전수 — 통로가 **사유를 그대로 나르고**(저장 층이 적을 값이다), 어느 종단
     * 상태로 가는지는 사유가 정한다. 기대 사상은 이 test 가 따로 진술하고(아래 `when`),
     * production 은 `OutboxTerminalReason.terminalState` 로 그 사상을 노출한다.
     */
    @Test
    fun `Claimed 에 Terminate 는 사유를 나르고 사유가 목표 상태를 정한다 — 일곱 전수`() {
        OutboxTerminalReason.entries.forEach { reason ->
            val outcome = transitionOutbox(ENTRY_ID, OutboxEntryState.Claimed, OutboxCommand.Terminate(reason))

            outcome.shouldBeInstanceOf<OutboxTransitionOutcome.Accepted>()
            val transition = outcome.transition.shouldBeInstanceOf<OutboxTransition.Terminal>()
            transition.entryId shouldBe ENTRY_ID
            transition.reason shouldBe reason
            stateOf(transition) shouldBe reason.terminalState
        }
    }

    /** 어휘가 조용히 넓어지거나 줄어들면 붉어진다 — 저장 층 CHECK 와 맞추는 집합이다. */
    @Test
    fun `사유 어휘는 일곱이고 종단 상태 셋에만 사상된다`() {
        OutboxTerminalReason.entries.map { it.name }.toSet() shouldBe
            setOf(
                "DELIVERED",
                "DUPLICATE_SKIPPED",
                "DELIVERY_REJECTED",
                "DELIVERY_SUPPRESSED",
                "DELIVERY_OUTCOME_UNKNOWN",
                "UNKNOWN_PAYLOAD",
                "ORPHANED_CLAIM",
            )
        OutboxTerminalReason.entries.map { it.terminalState }.toSet() shouldBe
            setOf(OutboxEntryState.Delivered, OutboxEntryState.Failed, OutboxEntryState.Isolated)
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
            listOf(OutboxCommand.Claim) +
                OutboxTerminalReason.entries.map { OutboxCommand.Terminate(it) }
        val allowedPairs =
            buildSet<Pair<OutboxEntryState, OutboxCommand>> {
                add(OutboxEntryState.Pending to OutboxCommand.Claim)
                OutboxTerminalReason.entries.forEach { add(OutboxEntryState.Claimed to OutboxCommand.Terminate(it)) }
            }

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
        val commands =
            listOf(OutboxCommand.Claim) + OutboxTerminalReason.entries.map { OutboxCommand.Terminate(it) }

        terminals.forEach { terminal ->
            commands.forEach { command ->
                transitionOutbox(ENTRY_ID, terminal, command).shouldBeInstanceOf<OutboxTransitionOutcome.Rejected>()
            }
        }
    }
}

/** 기대 사상의 test 쪽 진술 — production 의 `terminalState` 와 같아야 한다. */
private fun stateOf(transition: OutboxTransition.Terminal): OutboxEntryState =
    when (transition) {
        is OutboxTransition.ToDelivered -> OutboxEntryState.Delivered
        is OutboxTransition.ToFailed -> OutboxEntryState.Failed
        is OutboxTransition.ToIsolated -> OutboxEntryState.Isolated
    }
