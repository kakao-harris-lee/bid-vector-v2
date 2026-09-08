package bidvector.workflow.strategy

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import bidvector.strategy.validate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-09-08T00:00:00Z")
private val TEST_POLICY_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-strategy-policy")
private val TEST_SESSION_POLICY = EditSessionPolicyData(Duration.ofMinutes(15))
private val OPERATOR = Actor.Operator(OperatorId("op-1"))
private val FIELD = EditableField.Threshold(ThresholdField.BidNowThreshold)

private fun policyOf(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        TEST_POLICY_VERSION,
    )

private fun currentStrategy(revision: Int = 1): OperatorStrategy {
    val result = validate(StrategyDraft(), StrategyRevision(revision), policyOf())
    return (result as StrategyValidation.Valid).strategy
}

private fun sessionAt(
    state: EditSessionState,
    expiresAt: Instant = NOW.plus(Duration.ofMinutes(15)),
    sessionVersion: Int = 0,
    lastCommand: EditCommand? = null,
    actor: Actor.Operator = OPERATOR,
): EditSession =
    beginSession(EditSessionId("s-1"), actor.id, FIELD, NOW.minusSeconds(1), TEST_SESSION_POLICY)
        .copy(state = state, expiresAt = expiresAt, sessionVersion = sessionVersion, lastCommand = lastCommand)

private val VALID_DRAFT = StrategyDraft(bidNowThreshold = BigDecimal("0.7"))
private val INVALID_DRAFT = StrategyDraft(bidNowThreshold = BigDecimal("0.5"), reviewThreshold = BigDecimal("0.6"))

private fun provideValue(
    id: String = "cmd-1",
    actor: Actor = OPERATOR,
    field: EditableField = FIELD,
    draft: StrategyDraft = VALID_DRAFT,
): EditCommand.ProvideValue = EditCommand.ProvideValue(CommandId(id), EditSessionId("s-1"), actor, field, draft)

private fun confirm(
    id: String = "cmd-2",
    actor: Actor = OPERATOR,
    seenRevision: StrategyRevision = StrategyRevision(1),
): EditCommand.Confirm = EditCommand.Confirm(CommandId(id), EditSessionId("s-1"), actor, seenRevision)

private fun requestEdit(
    id: String = "cmd-3",
    actor: Actor = OPERATOR,
    field: EditableField = FIELD,
): EditCommand.RequestEdit = EditCommand.RequestEdit(CommandId(id), EditSessionId("s-1"), actor, field)

private fun cancel(
    id: String = "cmd-4",
    actor: Actor = OPERATOR,
): EditCommand.Cancel =
    EditCommand.Cancel(CommandId(id), EditSessionId("s-1"), actor, CancellationReason.OperatorRequested)

/**
 * scope.md ①③④ — 전이표 전수 test. 허용 쌍 다섯(설계 검토 (1) 「허용 쌍만 갖는 전수
 * when」)과 표 밖 쌍의 거부를 관측한다. actor·timeout·revision 은 판정 순서(설계 검토
 * (4) 2)대로 별도 test 로 나눈다 — 표 자체는 actor 검사를 통과한 뒤의 자리다.
 */
class EditSessionTransitionTableTest {
    @Test
    fun `① WaitingForValue 에 유효한 ValueProvided 는 WaitingForConfirmation 으로 accepted 전이한다`() {
        val session = sessionAt(EditSessionState.WaitingForValue(FIELD))

        val outcome = apply(session, provideValue(draft = VALID_DRAFT), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val next = outcome.session.state
        next.shouldBeInstanceOf<EditSessionState.WaitingForConfirmation>()
        next.field shouldBe FIELD
        next.draft shouldBe VALID_DRAFT
        outcome.session.sessionVersion shouldBe 1
    }

    @Test
    fun `② WaitingForValue 에 무효한 ValueProvided 는 같은 field 로 accepted 전이한다 — 상태를 바꾸지 않는다`() {
        val session = sessionAt(EditSessionState.WaitingForValue(FIELD))

        val outcome = apply(session, provideValue(draft = INVALID_DRAFT), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        outcome.session.state shouldBe EditSessionState.WaitingForValue(FIELD)
    }

    @Test
    fun `① WaitingForConfirmation 에 유효한 Confirmed 는 Applied 전이하고 이벤트를 낸다`() {
        val session = sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT))

        val outcome = apply(session, confirm(seenRevision = StrategyRevision(1)), NOW, currentStrategy(1), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Applied>()
        outcome.session.state shouldBe EditSessionState.Applied(StrategyRevision(2))
        outcome.event.revision shouldBe StrategyRevision(2)
        outcome.strategy.revision shouldBe StrategyRevision(2)
    }

    @Test
    fun `③(a) Confirmed 의 seenRevision 이 현재와 다르면 StaleRevision 으로 거부된다`() {
        val session = sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT))

        val outcome = apply(session, confirm(seenRevision = StrategyRevision(1)), NOW, currentStrategy(2), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.StaleRevision
        outcome.session.state shouldBe EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT)
    }

    @Test
    fun `③(b) apply 시점 재검증이 Invalid 면 거부가 아니라 WaitingForValue 로 되돌아간다`() {
        val staleValidDraft = StrategyDraft(bidNowThreshold = BigDecimal("0.5"), reviewThreshold = BigDecimal("0.6"))
        val session = sessionAt(EditSessionState.WaitingForConfirmation(FIELD, staleValidDraft))

        val outcome = apply(session, confirm(seenRevision = StrategyRevision(1)), NOW, currentStrategy(1), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        outcome.session.state shouldBe EditSessionState.WaitingForValue(FIELD)
    }

    @Test
    fun `① WaitingForConfirmation 에 RequestEdit 는 지정한 field 로 WaitingForValue 전이한다`() {
        val session = sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT))
        val otherField = EditableField.Watch(WatchRuleId.FocusCategory)

        val outcome = apply(session, requestEdit(field = otherField), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        outcome.session.state shouldBe EditSessionState.WaitingForValue(otherField)
    }

    @Test
    fun `① 비종단 상태에서 Cancel 은 Cancelled 로 전이한다`() {
        val waiting = sessionAt(EditSessionState.WaitingForValue(FIELD))
        val confirming = sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT))

        val outcomeWaiting = apply(waiting, cancel(), NOW, currentStrategy(), policyOf())
        val outcomeConfirming = apply(confirming, cancel(), NOW, currentStrategy(), policyOf())

        outcomeWaiting.session.state shouldBe EditSessionState.Cancelled(CancellationReason.OperatorRequested)
        outcomeConfirming.session.state shouldBe EditSessionState.Cancelled(CancellationReason.OperatorRequested)
    }

    @Test
    fun `④ 표 밖의 (state, command) 쌍은 InvalidTransition 으로 거부되고 상태를 바꾸지 않는다`() {
        val waitingForValue = sessionAt(EditSessionState.WaitingForValue(FIELD))
        val waitingForConfirmation = sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT))
        val applied = sessionAt(EditSessionState.Applied(StrategyRevision(2)))
        val cancelled = sessionAt(EditSessionState.Cancelled(CancellationReason.OperatorRequested))

        val offTableCases =
            listOf(
                waitingForValue to confirm(),
                waitingForValue to requestEdit(),
                waitingForConfirmation to provideValue(),
                applied to provideValue(),
                applied to confirm(),
                applied to cancel(),
                cancelled to provideValue(),
                cancelled to cancel(),
            )

        offTableCases.forEach { (session, command) ->
            val outcome = apply(session, command, NOW, currentStrategy(), policyOf())
            outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
            outcome.reason shouldBe RejectionReason.InvalidTransition
            outcome.session.state shouldBe session.state
        }
    }
}
