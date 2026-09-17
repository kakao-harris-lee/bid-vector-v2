package bidvector.adapters.persistence

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
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSessionConflictException
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionState
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.StrategyRepository
import bidvector.workflow.strategy.toSnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val OPERATOR = OperatorId("op-jdbc-1")
private val FIELD = EditableField.Threshold(ThresholdField.BidNowThreshold)
private val TEST_POLICY_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-jdbc-policy")

/**
 * S-30(scope.md) — 세션 왕복·낙관적 충돌(0행 → 실패)·상태 전이 보존·만료 시각 왕복. `adapters`
 * 는 `EditSession`을 만들 수 없다(`internal constructor` + `@ConsistentCopyVisibility`가
 * `copy()`도 internal로 내린다, D-6B1-6) — 그래서 이 test 는 [EditStrategyWorkflow]의 실제
 * 흐름으로 정당한 [bidvector.workflow.strategy.EditSession] 값을 얻고, 그 값으로
 * [JdbcEditSessionRepository]를 직접 몰아 write 경로를 실측한다.
 */
class JdbcEditSessionRepositoryTest : PersistenceTestSupport() {
    private fun repository(): JdbcEditSessionRepository = JdbcEditSessionRepository(dataSource())

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

    private fun initialStrategy(): OperatorStrategy =
        (validate(StrategyDraft(), StrategyRevision(1), strategyPolicy()) as StrategyValidation.Valid).strategy

    private class InMemoryStrategyRepository(
        var strategy: OperatorStrategy,
    ) : StrategyRepository {
        override fun load(): OperatorStrategy = strategy

        override fun save(applied: AppliedStrategy) {
            strategy = applied.strategy
        }
    }

    private class NoopEventSink : EventSink {
        val published = mutableListOf<StrategyEvent>()

        override fun publish(
            event: StrategyEvent,
            actor: Actor,
        ) {
            published += event
        }
    }

    private fun workflow(
        sessions: EditSessionRepository = repository(),
        clock: Clock = Clock { Instant.parse("2026-09-17T00:00:00Z") },
        strategies: InMemoryStrategyRepository = InMemoryStrategyRepository(initialStrategy()),
        timeout: Duration = Duration.ofMinutes(15),
    ): EditStrategyWorkflow =
        EditStrategyWorkflow(
            sessions,
            strategies,
            clock,
            NoopEventSink(),
            strategyPolicy(),
            EditSessionPolicyData(timeout),
        )

    @Test
    fun `begin 으로 만든 세션을 load 하면 원본과 같은 스냅숏이 나온다 — 왕복`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-round-trip")
        val begun = workflow(sessions).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)

        sessions.load(id) shouldBe begun.session.toSnapshot()
    }

    @Test
    fun `WaitingForConfirmation 전이(예산 한계 포함)가 저장 뒤 그대로 복원된다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-confirmation")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)

        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.7"), candidateLimit = 5)
        val provided =
            flow.provideValue(EditCommand.ProvideValue(CommandId("cmd-1"), id, Actor.Operator(OPERATOR), FIELD, draft))
        check(provided is CommandResult.Processed)

        sessions.load(id) shouldBe provided.outcome.session.toSnapshot()
        sessions.load(id)!!.stateKind shouldBe "WAITING_FOR_CONFIRMATION"
    }

    @Test
    fun `Applied 까지 전이한 세션이 revision 을 보존한 채 복원된다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-applied")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        flow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        val confirmed =
            flow.confirm(EditCommand.Confirm(CommandId("cmd-2"), id, Actor.Operator(OPERATOR), StrategyRevision(1)))
        check(confirmed is CommandResult.Processed)

        val loaded = sessions.load(id)
        loaded shouldBe confirmed.outcome.session.toSnapshot()
        loaded!!.stateKind shouldBe "APPLIED"
        loaded.stateRevision shouldBe 2
    }

    @Test
    fun `만료 시각을 넘긴 세션을 expire 하면 EXPIRED 로 저장되고 부가 데이터가 없다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-expired")
        val startClock = Clock { Instant.parse("2026-09-17T00:00:00Z") }
        workflow(sessions, clock = startClock, timeout = Duration.ofMinutes(1)).begin(id, OPERATOR, FIELD)

        val afterExpiry = Clock { Instant.parse("2026-09-17T00:10:00Z") }
        val state = workflow(sessions, clock = afterExpiry).expire(id)

        state shouldBe EditSessionState.Expired
        val loaded = sessions.load(id)
        loaded!!.stateKind shouldBe "EXPIRED"
        loaded.stateField shouldBe null
        loaded.stateDraft shouldBe null
        loaded.stateRevision shouldBe null
        loaded.stateCancelReasonKind shouldBe null
        loaded.expiresAt shouldBe Instant.parse("2026-09-17T00:01:00Z")
    }

    @Test
    fun `같은 세션 값을 두 번 save 하면 두 번째는 낙관적 충돌로 실패한다 — 0행`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-conflict")
        val begun = workflow(sessions).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)

        val flow = workflow(sessions)
        val provided =
            flow.provideValue(
                EditCommand.ProvideValue(
                    CommandId("cmd-1"),
                    id,
                    Actor.Operator(OPERATOR),
                    FIELD,
                    StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                ),
            )
        check(provided is CommandResult.Processed)
        val transitioned = provided.outcome.session

        // 이 시점에 DB 의 session_version 은 이미 transitioned.sessionVersion 과 같다(방금
        // workflow.provideValue 가 저장했다) — 같은 값을 다시 save 하면 전제조건
        // (저장소 버전 = 새 버전 - 1)이 거짓이라 0행 → 예외(동시 writer 가 같은 버전을
        // 다시 쓰려는 상황의 재현).
        val exception = shouldThrow<EditSessionConflictException> { sessions.save(transitioned) }
        exception.sessionId shouldBe id
        exception.expectedVersion shouldBe transitioned.sessionVersion - 1

        // 충돌 뒤에도 저장소의 값은 그대로다(덮이지 않았다 — 조용한 덮어쓰기가 아니다).
        sessions.load(id) shouldBe transitioned.toSnapshot()
    }

    @Test
    fun `같은 id 로 begin 이 경합하면(둘 다 session_version=0) 두 번째는 충돌한다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-begin-race")
        val begun = workflow(sessions).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)

        // 같은 begin() 을 몰아 만든 두 번째 세션(둘 다 sessionVersion=0)을 그대로 다시
        // save 한다 — 저장소의 state 는 아직 비종단(WaitingForValue)이라 "버전 0 이면
        // 무조건 통과" 규칙이 있었다면 조용히 덮였을 자리(D-6B1-3, 구현 레인 실측 수정).
        val exception = shouldThrow<EditSessionConflictException> { sessions.save(begun.session) }
        exception.sessionId shouldBe id
    }

    @Test
    fun `종단 상태 위에 새 begin 은 충돌 없이 새 episode 로 갈아 끼운다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-restart")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        flow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        flow.confirm(EditCommand.Confirm(CommandId("cmd-2"), id, Actor.Operator(OPERATOR), StrategyRevision(1)))
        sessions.load(id)!!.stateKind shouldBe "APPLIED"

        val restarted = workflow(sessions).begin(id, OPERATOR, EditableField.CandidateLimit)
        restarted.shouldBeInstanceOf<BeginOutcome.Started>()
        sessions.load(id)!!.stateKind shouldBe "WAITING_FOR_VALUE"
    }
}
