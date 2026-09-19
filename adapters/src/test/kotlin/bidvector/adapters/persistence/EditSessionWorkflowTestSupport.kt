package bidvector.adapters.persistence

import bidvector.adapters.strategy.JdbcEditSessionRepository
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
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.StrategyRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

val EDIT_SESSION_TEST_OPERATOR = OperatorId("op-jdbc-1")
val EDIT_SESSION_TEST_FIELD = EditableField.Threshold(ThresholdField.BidNowThreshold)
private val EDIT_SESSION_TEST_POLICY_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-jdbc-policy")

/**
 * [JdbcEditSessionRepositoryTest]·[JdbcEditSessionSaveGuardTest] 공용 스캐폴딩(v2-지침서.md
 * §5 「중복 금지」 — 파일당 500줄 한도로 갈린 두 test 가 같은 fixture 를 각자 베끼지 않게
 * 추출했다, `EditSessionRestore.kt` 를 스냅숏 파일에서 기계적으로 분리한 것과 같은 종류의
 * 분할이지 설계 변경이 아니다). `adapters`는 `EditSession`을 만들 수 없다(`internal
 * constructor` + `@ConsistentCopyVisibility`가 `copy()`도 internal로 내린다, D-6B1-6) —
 * 그래서 이 fixture 는 [EditStrategyWorkflow]의 실제 흐름으로 정당한
 * [bidvector.workflow.strategy.EditSession] 값을 얻는다.
 */
abstract class EditSessionWorkflowTestSupport : PersistenceTestSupport() {
    protected fun repository(): JdbcEditSessionRepository = JdbcEditSessionRepository(dataSource())

    protected fun strategyPolicy(): Resolution.Resolved<StrategyPolicyData> =
        Resolution.Resolved(
            StrategyPolicyData(
                matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
            ),
            EDIT_SESSION_TEST_POLICY_VERSION,
        )

    protected fun initialStrategy(): OperatorStrategy =
        (validate(StrategyDraft(), StrategyRevision(1), strategyPolicy()) as StrategyValidation.Valid).strategy

    protected class InMemoryStrategyRepository(
        var strategy: OperatorStrategy,
    ) : StrategyRepository {
        override fun load(): OperatorStrategy = strategy

        override fun save(applied: AppliedStrategy) {
            strategy = applied.strategy
        }
    }

    protected class NoopEventSink : EventSink {
        val published = mutableListOf<StrategyEvent>()

        override fun publish(
            event: StrategyEvent,
            actor: Actor,
        ) {
            published += event
        }
    }

    protected fun workflow(
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
}
