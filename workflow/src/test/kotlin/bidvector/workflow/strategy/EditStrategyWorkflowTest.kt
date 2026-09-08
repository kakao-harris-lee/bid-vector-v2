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

    override fun save(strategy: OperatorStrategy) {
        this.strategy = strategy
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

    override fun publish(event: StrategyEvent) {
        published += event
    }
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
        val session = workflow.begin(EditSessionId("s-1"), OPERATOR, FIELD)

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
    }

    @Test
    fun `Accepted 로 끝나는 경로는 전략을 저장하지도 이벤트를 발행하지도 않는다`() {
        val (workflow, strategies, events) = newWorkflow()
        val before = strategies.strategy
        val session = workflow.begin(EditSessionId("s-2"), OPERATOR, FIELD)

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
        val session = workflow.begin(EditSessionId("s-3"), OPERATOR, FIELD)
        clock.instant = session.expiresAt.plusSeconds(1)

        val state = workflow.expire(session.id)

        state shouldBe EditSessionState.Expired
    }

    @Test
    fun `expire 는 존재하지 않는 세션에 null 을 낸다`() {
        val (workflow, _, _) = newWorkflow()

        workflow.expire(EditSessionId("no-such-session")) shouldBe null
    }
}
