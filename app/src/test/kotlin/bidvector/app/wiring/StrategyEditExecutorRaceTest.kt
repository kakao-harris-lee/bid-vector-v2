package bidvector.app.wiring

import bidvector.adapters.strategy.StrategyEditTransaction
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
import bidvector.strategy.validate
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.RejectionReason
import bidvector.workflow.strategy.StrategyRepository
import bidvector.workflow.strategy.TransitionOutcome
import bidvector.workflow.strategy.toSnapshot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val OPERATOR_ID = bidvector.workflow.strategy.OperatorId("operator")

private fun racePolicy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        PolicyVersion(EffectiveFrom.Initial, "test-race"),
    )

private fun strategyOf(
    draft: StrategyDraft,
    revision: Int,
): OperatorStrategy = (validate(draft, StrategyRevision(revision), racePolicy()) as StrategyValidation.Valid).strategy

/**
 * **읽기 사이에 다른 커밋을 끼우는** 저장소 — verifier r2 F-r2-3 의 결정적 재현이다.
 * 실 DB 의 READ COMMITTED 창을 흉내내기 위해 「N 번째 `load()` 직후 값이 바뀐다」로 고정한다
 * (시간에 기대지 않는다 — 경합 test 가 flaky 해지는 흔한 이유를 피한다).
 */
private class RacingStrategyRepository(
    var strategy: OperatorStrategy,
    private val mutateAfterLoad: Int,
    private val mutation: () -> OperatorStrategy,
) : StrategyRepository {
    var loads: Int = 0
        private set

    override fun load(): OperatorStrategy {
        val current = strategy
        loads += 1
        if (loads == mutateAfterLoad) strategy = mutation()
        return current
    }

    override fun save(applied: AppliedStrategy) {
        strategy = applied.strategy
    }
}

private class RaceSessionRepository : EditSessionRepository {
    private val stored = mutableMapOf<EditSessionId, EditSessionSnapshot>()

    override fun load(id: EditSessionId): EditSessionSnapshot? = stored[id]

    override fun save(session: EditSession) {
        stored[session.id] = session.toSnapshot()
    }
}

private class RaceEventSink : EventSink {
    val published = mutableListOf<StrategyEvent>()

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        published += event
    }
}

private class RaceClock : Clock {
    override fun now(): Instant = Instant.parse("2026-09-27T00:00:00Z")
}

/** 실행기와 use case 는 production 클래스 그대로다 — 바뀌는 것은 저장소뿐이다. */
private class DirectStrategyEditTransaction(
    private val strategies: StrategyRepository,
    private val sessions: EditSessionRepository,
    private val events: EventSink,
) : StrategyEditTransaction {
    override fun <T> inTransaction(action: (EditStrategyWorkflow) -> T): T =
        action(
            EditStrategyWorkflow(
                sessions,
                strategies,
                RaceClock(),
                events,
                racePolicy(),
                EditSessionPolicyData(Duration.ofMinutes(15)),
            ),
        )
}

/**
 * D-6A2b-28 회귀 — **기준 revision 은 draft 를 만든 그 읽기에서 온다.**
 *
 * verifier r2 F-r2-3: 실행기가 전략을 한 번 읽어 draft 를 만들고, use case 가 **다시** 읽어
 * 그 값의 revision 을 기준으로 심었다. 두 읽기 사이에 다른 커밋이 끼면 기준은 새 revision 이
 * 되고 draft 는 낡은 값이라, 확인이 통과하며 앞 세션의 변경이 409 없이 사라졌다.
 */
class StrategyEditExecutorRaceTest {
    private val sessions = RaceSessionRepository()
    private val events = RaceEventSink()

    private fun executorOver(strategies: StrategyRepository): StrategyEditExecutor =
        StrategyEditExecutor(DirectStrategyEditTransaction(strategies, sessions, events), racePolicy())

    @Test
    fun `draft 를 만든 읽기와 커널의 읽기 사이에 커밋이 끼면 값 제출이 거부된다`() {
        // 첫 `load()`(실행기가 draft 를 만드는 읽기) 직후, 다른 세션이 적용한 것처럼 값을 민다.
        val strategies =
            RacingStrategyRepository(
                strategy = strategyOf(StrategyDraft(), revision = 0),
                mutateAfterLoad = 1,
                mutation = { strategyOf(StrategyDraft(candidateLimit = 13), revision = 1) },
            )
        val executor = executorOver(strategies)
        val started = executor.begin(EditableField.MaxActiveBids) as BeginOutcome.Started

        val outcome =
            executor.provideValue(
                started.session.id,
                CommandId("race-value"),
                EditableField.MaxActiveBids,
                EditValue.Count(4),
            )

        val processed = outcome.shouldBeInstanceOf<ProvideValueOutcome.Processed>()
        val rejected =
            (processed.result as CommandResult.Processed)
                .outcome
                .shouldBeInstanceOf<TransitionOutcome.Rejected>()
        rejected.reason shouldBe RejectionReason.StaleRevision
        // 앞 세션의 값이 그대로다 — 낡은 스냅숏이 세션에 들어가지도 않았다.
        strategies.strategy.candidateLimit?.value shouldBe 13
        strategies.strategy.maxActiveBids shouldBe null
        events.published.size shouldBe 0
    }

    @Test
    fun `끼어드는 커밋이 없으면 값 제출이 그대로 통과한다 — 양성 대조`() {
        val strategies =
            RacingStrategyRepository(
                strategy = strategyOf(StrategyDraft(), revision = 0),
                mutateAfterLoad = 0,
                mutation = { error("이 대조에서는 값이 바뀌지 않는다") },
            )
        val executor = executorOver(strategies)
        val started = executor.begin(EditableField.MaxActiveBids) as BeginOutcome.Started

        val outcome =
            executor.provideValue(
                started.session.id,
                CommandId("plain-value"),
                EditableField.MaxActiveBids,
                EditValue.Count(4),
            )

        val processed = outcome.shouldBeInstanceOf<ProvideValueOutcome.Processed>()
        (processed.result as CommandResult.Processed).outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
    }
}
