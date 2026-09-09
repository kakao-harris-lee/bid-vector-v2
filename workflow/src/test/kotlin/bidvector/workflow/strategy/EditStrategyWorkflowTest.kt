package bidvector.workflow.strategy

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.ThresholdField
import bidvector.strategy.validate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val TEST_POLICY_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-strategy-policy")
private val OPERATOR = OperatorId("op-1")
private val FIELD = EditableField.Threshold(ThresholdField.BidNowThreshold)

private fun strategyPolicy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        TEST_POLICY_VERSION,
    )

private fun initialStrategy(): OperatorStrategy {
    val result = validate(StrategyDraft(), StrategyRevision(1), strategyPolicy())
    return (result as StrategyValidation.Valid).strategy
}

private class FixedClock(
    var instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class InMemoryStrategyRepository(
    var strategy: OperatorStrategy,
) : StrategyRepository {
    override fun load(): OperatorStrategy = strategy

    override fun save(applied: AppliedStrategy) {
        this.strategy = applied.strategy
    }
}

private class InMemorySessionRepository : EditSessionRepository {
    private val sessions = mutableMapOf<EditSessionId, EditSession>()

    override fun load(id: EditSessionId): EditSession? = sessions[id]

    override fun save(session: EditSession) {
        sessions[session.id] = session
    }
}

private class RecordingEventSink : EventSink {
    val published = mutableListOf<StrategyEvent>()
    val publishedActors = mutableListOf<Actor>()

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        published += event
        publishedActors += actor
    }
}

/** verifier M-3 — 저장 실패를 한 번 흉내내는 fake. */
private class FlakyStrategyRepository(
    private val delegate: InMemoryStrategyRepository,
) : StrategyRepository {
    var failNextSave = false

    override fun load(): OperatorStrategy = delegate.load()

    override fun save(applied: AppliedStrategy) {
        if (failNextSave) {
            failNextSave = false
            error("simulated strategy save failure")
        }
        delegate.save(applied)
    }
}

/** verifier M-3 — 발행 실패를 한 번 흉내내는 fake. */
private class FlakyEventSink : EventSink {
    val published = mutableListOf<StrategyEvent>()
    var failNextPublish = false

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        if (failNextPublish) {
            failNextPublish = false
            error("simulated publish failure")
        }
        published += event
    }
}

/** `begin()`이 `Started`를 낼 것으로 기대하는 호출부의 공용 unwrap(verifier N-1 — begin 이 이제 `BeginOutcome`을 낸다). */
private fun EditStrategyWorkflow.beginStarted(
    sessionId: EditSessionId,
    operator: OperatorId,
    field: EditableField,
): EditSession {
    val outcome = begin(sessionId, operator, field)
    check(outcome is BeginOutcome.Started) { "Started 를 기대했으나 $outcome" }
    return outcome.session
}

/**
 * scope.md ⑥⑦ — use case 배선. 세 ports(fake)만으로 begin→provideValue→confirm 전 과정과
 * `strategies.save`·`events.publish`가 `Applied`에서만 일어남을(⑥ 「모든 편집 경로가 이
 * use case 를 지난다」의 실행 증거)을 확인한다.
 */
class EditStrategyWorkflowTest {
    private fun newWorkflow(
        sessions: EditSessionRepository = InMemorySessionRepository(),
        strategies: InMemoryStrategyRepository = InMemoryStrategyRepository(initialStrategy()),
        clock: FixedClock = FixedClock(Instant.parse("2026-09-08T00:00:00Z")),
        events: RecordingEventSink = RecordingEventSink(),
    ): Triple<EditStrategyWorkflow, InMemoryStrategyRepository, RecordingEventSink> {
        val workflow =
            EditStrategyWorkflow(
                sessions,
                strategies,
                clock,
                events,
                strategyPolicy(),
                EditSessionPolicyData(Duration.ofMinutes(15)),
            )
        return Triple(workflow, strategies, events)
    }

    @Test
    fun `begin→provideValue→confirm 전 과정이 전략을 저장하고 이벤트를 정확히 한 번 발행한다`() {
        val (workflow, strategies, events) = newWorkflow()
        val session = workflow.beginStarted(EditSessionId("s-1"), OPERATOR, FIELD)

        val provided =
            workflow.provideValue(
                EditCommand.ProvideValue(
                    CommandId("cmd-1"),
                    session.id,
                    Actor.Operator(OPERATOR),
                    FIELD,
                    StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                ),
            )
        provided.shouldBeInstanceOf<CommandResult.Processed>()
        provided.outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()

        val confirmCommand =
            EditCommand.Confirm(
                CommandId("cmd-2"),
                session.id,
                Actor.Operator(OPERATOR),
                StrategyRevision(1),
            )
        val confirmed = workflow.confirm(confirmCommand)

        confirmed.shouldBeInstanceOf<CommandResult.Processed>()
        confirmed.outcome.shouldBeInstanceOf<TransitionOutcome.Applied>()
        strategies.strategy.revision shouldBe StrategyRevision(2)
        events.published shouldBe listOf(StrategyEvent.StrategyUpdated(StrategyRevision(2), TEST_POLICY_VERSION))
        // 4C-1 좁은 예외(EventSink.publish(event, actor)) — 발행이 나르는 actor 는
        // 그 세션의 소유 operator 다(session.actor), command 의 actor 재확인이 아니다.
        events.publishedActors shouldBe listOf(Actor.Operator(OPERATOR))
    }

    @Test
    fun `Accepted 로 끝나는 경로는 전략을 저장하지도 이벤트를 발행하지도 않는다`() {
        val (workflow, strategies, events) = newWorkflow()
        val before = strategies.strategy
        val session = workflow.beginStarted(EditSessionId("s-2"), OPERATOR, FIELD)

        workflow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                session.id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )

        strategies.strategy shouldBe before
        events.published.shouldBeEmpty()
    }

    @Test
    fun `존재하지 않는 세션에 대한 command 는 SessionNotFound 를 낸다`() {
        val (workflow, _, _) = newWorkflow()

        val result =
            workflow.provideValue(
                EditCommand.ProvideValue(
                    CommandId("cmd-1"),
                    EditSessionId("no-such-session"),
                    Actor.Operator(OPERATOR),
                    FIELD,
                    StrategyDraft(),
                ),
            )

        result shouldBe CommandResult.SessionNotFound
    }

    @Test
    fun `expire 는 만료 시각을 넘긴 세션을 저장하고 Expired 상태를 낸다`() {
        val clock = FixedClock(Instant.parse("2026-09-08T00:00:00Z"))
        val (workflow, _, _) = newWorkflow(clock = clock)
        val session = workflow.beginStarted(EditSessionId("s-3"), OPERATOR, FIELD)
        clock.instant = session.expiresAt.plusSeconds(1)

        val state = workflow.expire(session.id)

        state shouldBe EditSessionState.Expired
    }

    @Test
    fun `expire 는 존재하지 않는 세션에 null 을 낸다`() {
        val (workflow, _, _) = newWorkflow()

        workflow.expire(EditSessionId("no-such-session")) shouldBe null
    }

    @Test
    fun `begin 은 같은 id 에 이미 비종단 세션이 있으면 덮어쓰지 않고 거부한다`() {
        val (workflow, _, _) = newWorkflow()
        val sessionId = EditSessionId("s-4")
        val original = workflow.beginStarted(sessionId, OPERATOR, FIELD)
        workflow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                sessionId,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )

        val reopened = workflow.begin(sessionId, OPERATOR, FIELD)

        reopened.shouldBeInstanceOf<BeginOutcome.Rejected>()
        reopened.reason shouldBe RejectionReason.SessionAlreadyActive
        reopened.existing.id shouldBe original.id
        reopened.existing.state.shouldBeInstanceOf<EditSessionState.WaitingForConfirmation>()
    }

    @Test
    fun `begin 은 같은 id 의 세션이 종단이면 새로 열 수 있다`() {
        val (workflow, _, _) = newWorkflow()
        val sessionId = EditSessionId("s-5")
        val original = workflow.beginStarted(sessionId, OPERATOR, FIELD)
        val cancelCommand =
            EditCommand.Cancel(
                CommandId("cmd-1"),
                sessionId,
                Actor.Operator(OPERATOR),
                CancellationReason.OperatorRequested,
            )
        workflow.cancel(cancelCommand)

        val reopened = workflow.begin(sessionId, OPERATOR, FIELD)

        reopened.shouldBeInstanceOf<BeginOutcome.Started>()
        reopened.session.id shouldBe original.id
        reopened.session.state shouldBe EditSessionState.WaitingForValue(FIELD)
    }

    @Test
    fun `M-3 전략 저장이 실패하면 세션이 전진하지 않아 재전달이 정상 재시도가 된다`() {
        val sessions = InMemorySessionRepository()
        val strategies = FlakyStrategyRepository(InMemoryStrategyRepository(initialStrategy()))
        val events = RecordingEventSink()
        val workflow =
            EditStrategyWorkflow(
                sessions,
                strategies,
                FixedClock(Instant.parse("2026-09-08T00:00:00Z")),
                events,
                strategyPolicy(),
                EditSessionPolicyData(Duration.ofMinutes(15)),
            )
        val sessionId = EditSessionId("s-6")
        workflow.beginStarted(sessionId, OPERATOR, FIELD)
        workflow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                sessionId,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        val confirmCommand =
            EditCommand.Confirm(
                CommandId("cmd-2"),
                sessionId,
                Actor.Operator(OPERATOR),
                StrategyRevision(1),
            )

        strategies.failNextSave = true
        shouldThrow<IllegalStateException> { workflow.confirm(confirmCommand) }

        sessions.load(sessionId)!!.state.shouldBeInstanceOf<EditSessionState.WaitingForConfirmation>()
        strategies.load().revision shouldBe StrategyRevision(1)
        events.published.shouldBeEmpty()

        val retried = workflow.confirm(confirmCommand)
        retried.shouldBeInstanceOf<CommandResult.Processed>()
        retried.outcome.shouldBeInstanceOf<TransitionOutcome.Applied>()
        strategies.load().revision shouldBe StrategyRevision(2)
        events.published.size shouldBe 1
    }

    @Test
    fun `M-3 발행이 실패하면 세션은 전진하지 않지만 전략은 저장돼 재전달이 StaleRevision 으로 거부된다`() {
        val sessions = InMemorySessionRepository()
        val strategies = InMemoryStrategyRepository(initialStrategy())
        val events = FlakyEventSink()
        val workflow =
            EditStrategyWorkflow(
                sessions,
                strategies,
                FixedClock(Instant.parse("2026-09-08T00:00:00Z")),
                events,
                strategyPolicy(),
                EditSessionPolicyData(Duration.ofMinutes(15)),
            )
        val sessionId = EditSessionId("s-7")
        workflow.beginStarted(sessionId, OPERATOR, FIELD)
        workflow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                sessionId,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        val confirmCommand =
            EditCommand.Confirm(
                CommandId("cmd-2"),
                sessionId,
                Actor.Operator(OPERATOR),
                StrategyRevision(1),
            )

        events.failNextPublish = true
        shouldThrow<IllegalStateException> { workflow.confirm(confirmCommand) }

        strategies.strategy.revision shouldBe StrategyRevision(2)
        sessions.load(sessionId)!!.state.shouldBeInstanceOf<EditSessionState.WaitingForConfirmation>()

        val retried = workflow.confirm(confirmCommand)
        retried.shouldBeInstanceOf<CommandResult.Processed>()
        val rejected = retried.outcome
        rejected.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        rejected.reason shouldBe RejectionReason.StaleRevision
    }

    @Test
    fun `M-5 begin 은 시각상 만료됐지만 아직 fold 되지 않은 세션 위에서도 Started 를 낸다`() {
        val clock = FixedClock(Instant.parse("2026-09-08T00:00:00Z"))
        val (workflow, _, _) = newWorkflow(clock = clock)
        val sessionId = EditSessionId("s-8")
        val original = workflow.beginStarted(sessionId, OPERATOR, FIELD)
        clock.instant = original.expiresAt.plusSeconds(1)

        val reopened = workflow.begin(sessionId, OPERATOR, FIELD)

        reopened.shouldBeInstanceOf<BeginOutcome.Started>()
        reopened.session.id shouldBe original.id
        reopened.session.state shouldBe EditSessionState.WaitingForValue(FIELD)
    }

    @Test
    fun `L-5 만료 fold 는 Rejected 로 끝나는 command 처리에서도 영속된다`() {
        val sessions = InMemorySessionRepository()
        val clock = FixedClock(Instant.parse("2026-09-08T00:00:00Z"))
        val (workflow, _, _) = newWorkflow(sessions = sessions, clock = clock)
        val sessionId = EditSessionId("s-9")
        val session = workflow.beginStarted(sessionId, OPERATOR, FIELD)
        clock.instant = session.expiresAt.plusSeconds(1)

        val result =
            workflow.provideValue(
                EditCommand.ProvideValue(
                    CommandId("cmd-1"),
                    sessionId,
                    Actor.Operator(OPERATOR),
                    FIELD,
                    StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                ),
            )

        result.shouldBeInstanceOf<CommandResult.Processed>()
        val rejected = result.outcome
        rejected.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        rejected.reason shouldBe RejectionReason.SessionExpired
        // process() 가 outcome.session 을 저장했는지(만료 fold 가 실제로 영속됐는지)를 독립된
        // sessions 참조로 확인한다 — outcome.session 자체가 Expired 라는 것만으로는
        // process() 의 저장 호출 여부를 재지 못한다(변이: sessions.save 를 조건부로 감싸도
        // outcome 필드는 그대로 Expired 라 통과했을 것이다, verifier L-5).
        sessions.load(sessionId)!!.state shouldBe EditSessionState.Expired
    }
}
