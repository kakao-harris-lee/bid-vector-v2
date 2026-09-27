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
import bidvector.strategy.toDraft
import bidvector.strategy.validate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val OPERATOR = OperatorId("op-base-revision")
private val SESSION_A = EditSessionId("session-a")
private val SESSION_B = EditSessionId("session-b")
private val NOW: Instant = Instant.parse("2026-09-27T00:00:00Z")

private fun policy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        PolicyVersion(EffectiveFrom.Initial, "test-base-revision"),
    )

private fun initialStrategy(): OperatorStrategy =
    (validate(StrategyDraft(), StrategyRevision(1), policy()) as StrategyValidation.Valid).strategy

private class BaseRevisionClock : Clock {
    override fun now(): Instant = NOW
}

private class BaseRevisionStrategyRepository : StrategyRepository {
    var strategy: OperatorStrategy = initialStrategy()

    override fun load(): OperatorStrategy = strategy

    override fun save(applied: AppliedStrategy) {
        strategy = applied.strategy
    }
}

/** 저장 형태가 **원시 스냅숏**이라 「이 필드 이전에 저장된 행」을 그대로 흉내낼 수 있다. */
private class BaseRevisionSessionRepository : EditSessionRepository {
    private val stored = mutableMapOf<EditSessionId, EditSessionSnapshot>()

    override fun load(id: EditSessionId): EditSessionSnapshot? = stored[id]

    override fun save(session: EditSession) {
        stored[session.id] = session.toSnapshot()
    }

    fun put(snapshot: EditSessionSnapshot) {
        stored[snapshot.id] = snapshot
    }
}

private class BaseRevisionEventSink : EventSink {
    val published = mutableListOf<StrategyEvent>()

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        published += event
    }
}

/**
 * D-6A2b-18 — **draft 를 뜬 기준 revision** 이 confirm 의 둘째 신선도 축이다.
 *
 * verifier r1 F-2 · code-review r1 HIGH-1 의 재현을 그대로 회귀 test 로 고정한다: 세션 둘이
 * 같은 revision 에서 각자 전체 draft 를 뜨고, 앞 세션이 적용한 뒤 뒤 세션이 **확인 직전에
 * 조회한** 최신 revision 으로 확인하면 — 이전 판에서는 앞의 변경이 409 없이 사라졌다.
 * 이제 기준 대조가 그것을 `StaleRevision` 으로 막는다.
 */
class EditSessionBaseRevisionTest {
    private val sessions = BaseRevisionSessionRepository()
    private val strategies = BaseRevisionStrategyRepository()
    private val events = BaseRevisionEventSink()
    private val workflow =
        EditStrategyWorkflow(
            sessions,
            strategies,
            BaseRevisionClock(),
            events,
            policy(),
            EditSessionPolicyData(Duration.ofMinutes(15)),
        )

    /** 어댑터가 하는 일과 같다 — 현재 전략을 초안으로 내보내 그 필드 하나만 바꾼다(D-6A2b-2). */
    private fun draftWith(patch: StrategyDraft.() -> StrategyDraft): StrategyDraft = strategies.load().toDraft().patch()

    private fun provideValue(
        session: EditSessionId,
        commandId: String,
        field: EditableField,
        draft: StrategyDraft,
        baseRevision: StrategyRevision? = null,
    ): CommandResult =
        workflow.provideValue(
            EditCommand.ProvideValue(
                CommandId(commandId),
                session,
                Actor.Operator(OPERATOR),
                field,
                draft,
                baseRevision ?: strategies.load().revision,
            ),
        )

    private fun confirm(
        session: EditSessionId,
        commandId: String,
        seenRevision: Int,
    ): TransitionOutcome =
        (
            workflow.confirm(
                EditCommand.Confirm(
                    CommandId(commandId),
                    session,
                    Actor.Operator(OPERATOR),
                    StrategyRevision(seenRevision),
                ),
            ) as CommandResult.Processed
        ).outcome

    private fun openBoth() {
        workflow.begin(SESSION_A, OPERATOR, EditableField.CandidateLimit)
        workflow.begin(SESSION_B, OPERATOR, EditableField.MaxActiveBids)
        // 둘 다 revision 1 에서 전체 스냅숏을 뜬다 — 서버 생성 id 아래 정상 사용이다.
        provideValue(SESSION_A, "a-value", EditableField.CandidateLimit, draftWith { copy(candidateLimit = 13) })
        provideValue(SESSION_B, "b-value", EditableField.MaxActiveBids, draftWith { copy(maxActiveBids = 4) })
    }

    @Test
    fun `value 는 draft 를 뜬 기준 revision 을 세션에 남긴다`() {
        workflow.begin(SESSION_A, OPERATOR, EditableField.CandidateLimit)

        val provided =
            provideValue(SESSION_A, "a-value", EditableField.CandidateLimit, draftWith { copy(candidateLimit = 13) })

        val state = (provided as CommandResult.Processed).outcome.session.state
        state.shouldBeInstanceOf<EditSessionState.WaitingForConfirmation>()
        state.baseRevision shouldBe StrategyRevision(1)
    }

    @Test
    fun `앞 세션이 적용한 뒤 낡은 draft 의 확인은 최신 revision 을 보내도 StaleRevision 이다`() {
        openBoth()

        val applied = confirm(SESSION_A, "a-confirm", seenRevision = 1)
        applied.shouldBeInstanceOf<TransitionOutcome.Applied>()
        strategies.load().revision shouldBe StrategyRevision(2)

        // B 는 확인 직전에 조회해 최신 revision(2)을 보낸다 — ① 축은 통과한다.
        val rejected = confirm(SESSION_B, "b-confirm", seenRevision = 2)

        rejected.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        rejected.reason shouldBe RejectionReason.StaleRevision
        // A 의 값이 보존된다 — 이전 판에서는 여기서 null 이 됐다.
        strategies.load().candidateLimit?.value shouldBe 13
        strategies.load().maxActiveBids shouldBe null
        strategies.load().revision shouldBe StrategyRevision(2)
        events.published.size shouldBe 1
    }

    @Test
    fun `되돌아가 새 값을 제출하면 새 기준을 잡고 확인이 통과한다`() {
        openBoth()
        confirm(SESSION_A, "a-confirm", seenRevision = 1)

        workflow.requestEdit(
            EditCommand.RequestEdit(
                CommandId("b-edit"),
                SESSION_B,
                Actor.Operator(OPERATOR),
                EditableField.MaxActiveBids,
            ),
        )
        provideValue(SESSION_B, "b-value-2", EditableField.MaxActiveBids, draftWith { copy(maxActiveBids = 4) })

        val applied = confirm(SESSION_B, "b-confirm-2", seenRevision = 2)

        applied.shouldBeInstanceOf<TransitionOutcome.Applied>()
        strategies.load().candidateLimit?.value shouldBe 13
        strategies.load().maxActiveBids?.value shouldBe 4
        strategies.load().revision shouldBe StrategyRevision(3)
    }

    @Test
    fun `기준 revision 이 없는 저장 행은 확인에서 fail-closed 로 거부된다`() {
        workflow.begin(SESSION_A, OPERATOR, EditableField.CandidateLimit)
        provideValue(SESSION_A, "a-value", EditableField.CandidateLimit, draftWith { copy(candidateLimit = 13) })
        // 이 필드가 생기기 전에 저장된 행을 그대로 흉내낸다(마이그레이션 0 — 낡은 행이 실재할 수 있다).
        sessions.put(requireNotNull(sessions.load(SESSION_A)).copy(stateBaseRevision = null))

        val rejected = confirm(SESSION_A, "a-confirm", seenRevision = 1)

        rejected.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        rejected.reason shouldBe RejectionReason.StaleRevision
        strategies.load().revision shouldBe StrategyRevision(1)
        events.published.size shouldBe 0
    }
}
