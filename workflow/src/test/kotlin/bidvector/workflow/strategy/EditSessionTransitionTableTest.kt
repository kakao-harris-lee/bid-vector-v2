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
import io.kotest.assertions.withClue
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
        outcome.applied.strategy.revision shouldBe StrategyRevision(2)
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

    /**
     * verifier r4 MEDIUM-8 — `EditStrategyWorkflow.process`(D-6B1-10)가 저장 여부를 가르는
     * 판별자는 `outcome.session !== session`(인스턴스 동일성)이지 「버전이 올랐다」가
     * 아니다. 둘의 등가는 **현재 전이표에서만** 참이고 코드 어디에도 잠겨 있지 않다 —
     * verifier 실측: `StaleRevision` 거부 갈래에 "새 인스턴스인데 버전 그대로"를 심어도
     * `:workflow:test`·어댑터 test 전건이 초록이었다. 판별자 자체(`!==`)는 바꾸지 않는다
     * (`begin()`/`expire()`와 일관되고 code-reviewer가 현 전이표 위에서 구조적으로 옳음을
     * 확인했다) — 대신 **등가 자체를 불변식으로 잠근다**: `apply()`가 새 인스턴스를 낼 때는
     * 반드시 `sessionVersion`이 입력보다 정확히 1 크다. 전이표가 바뀌어(새 command·새 분기)
     * 이 등가가 깨지면 이 test 가 먼저 붉어져야 한다 — `process`의 저장 판별자가 아니라
     * `apply()`가 지키는 계약이므로 이 table test 에 둔다(같은 파일이 이미 전이표 전수를
     * 다룬다). case 구성 근거는 [saveDiscriminatorInvariantCases] KDoc 참고 — 함수당
     * 50줄 한도로 분리했을 뿐 설계 변경은 아니다.
     */
    @Test
    fun `apply 가 새 인스턴스를 내면 언제나 sessionVersion 이 정확히 1 오른다 — 저장 판별자 불변식`() {
        val strategy = currentStrategy(1)

        saveDiscriminatorInvariantCases().forEach { (label, session, command) ->
            val outcome = apply(session, command, NOW, strategy, policyOf())

            withClue(label) {
                if (outcome.session !== session) {
                    outcome.session.sessionVersion shouldBe session.sessionVersion + 1
                }
            }
        }
    }
}

/** verifier r4 MEDIUM-8 위 test 전용 — `EditSessionTransitionTableTest`의 50줄 함수 한도로 분리(설계 변경 아님). */
private data class SaveDiscriminatorCase(
    val label: String,
    val session: EditSession,
    val command: EditCommand,
)

/**
 * [RejectionReason] 여섯 전부(`SessionAlreadyActive`는 `begin()` 전용이라 `apply()`가 만들
 * 수 없다 — 제외)와 `Accepted`·`Applied` 양쪽을 모두 돈다. 같은 인스턴스를 내는 갈래(만료
 * 상태 유지·중복 거부·actor 불일치·SystemActor·StaleRevision·InvalidTransition)는 불변식이
 * 공허하게 성립하지만, 새 인스턴스를 내는 갈래(만료 fold·모든 accepted 전이·Applied)에서
 * 실제로 `+1`인지를 잰다 — 그게 이 test 의 값이다.
 */
private fun saveDiscriminatorInvariantCases(): List<SaveDiscriminatorCase> {
    val expiredButNotFolded =
        sessionAt(EditSessionState.WaitingForValue(FIELD), expiresAt = NOW.minusSeconds(1), sessionVersion = 2)
    val withDuplicateLastCommand =
        sessionAt(
            EditSessionState.WaitingForValue(FIELD),
            sessionVersion = 1,
            lastCommand = provideValue(id = "cmd-dup", draft = VALID_DRAFT),
        )
    val withSameLastCommand =
        sessionAt(
            EditSessionState.WaitingForValue(FIELD),
            sessionVersion = 1,
            lastCommand = provideValue(id = "cmd-same", draft = VALID_DRAFT),
        )

    return listOf(
        SaveDiscriminatorCase(
            "이미 Expired — 판정 순서 ①, 같은 인스턴스",
            sessionAt(EditSessionState.Expired, sessionVersion = 3),
            provideValue(),
        ),
        SaveDiscriminatorCase(
            "비종단인데 시각상 만료 — 판정 순서 ① fold, 새 인스턴스",
            expiredButNotFolded,
            provideValue(),
        ),
        SaveDiscriminatorCase(
            "같은 command 재전달(다른 내용) — 판정 순서 ② IdempotencyConflict, 같은 인스턴스",
            withDuplicateLastCommand,
            provideValue(id = "cmd-dup", draft = INVALID_DRAFT),
        ),
        SaveDiscriminatorCase(
            "같은 command 재전달(같은 내용) — 판정 순서 ② Accepted 중복, 같은 인스턴스",
            withSameLastCommand,
            provideValue(id = "cmd-same", draft = VALID_DRAFT),
        ),
        SaveDiscriminatorCase(
            "다른 operator — 판정 순서 ③ ActorMismatch, 같은 인스턴스",
            sessionAt(EditSessionState.WaitingForValue(FIELD), sessionVersion = 1),
            provideValue(actor = Actor.Operator(OperatorId("other-operator"))),
        ),
        SaveDiscriminatorCase(
            "System actor — 판정 순서 ③ SystemActorNotPermitted, 같은 인스턴스",
            sessionAt(EditSessionState.WaitingForValue(FIELD), sessionVersion = 1),
            provideValue(actor = Actor.System("sweep")),
        ),
        SaveDiscriminatorCase(
            "seenRevision 불일치 — 판정 순서 ④ StaleRevision, 같은 인스턴스",
            sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT), sessionVersion = 1),
            confirm(seenRevision = StrategyRevision(99)),
        ),
        SaveDiscriminatorCase(
            "전이표 밖 — 판정 순서 ④ InvalidTransition, 같은 인스턴스",
            sessionAt(EditSessionState.Applied(StrategyRevision(2)), sessionVersion = 2),
            cancel(),
        ),
        SaveDiscriminatorCase(
            "유효한 ValueProvided — Accepted, 새 인스턴스",
            sessionAt(EditSessionState.WaitingForValue(FIELD), sessionVersion = 1),
            provideValue(draft = VALID_DRAFT),
        ),
        SaveDiscriminatorCase(
            "무효한 ValueProvided — Accepted(상태 불변), 새 인스턴스",
            sessionAt(EditSessionState.WaitingForValue(FIELD), sessionVersion = 1),
            provideValue(draft = INVALID_DRAFT),
        ),
        SaveDiscriminatorCase(
            "RequestEdit — Accepted, 새 인스턴스",
            sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT), sessionVersion = 1),
            requestEdit(),
        ),
        SaveDiscriminatorCase(
            "비종단 Cancel — Accepted, 새 인스턴스",
            sessionAt(EditSessionState.WaitingForValue(FIELD), sessionVersion = 1),
            cancel(),
        ),
        SaveDiscriminatorCase(
            "유효한 Confirmed — Applied, 새 인스턴스",
            sessionAt(EditSessionState.WaitingForConfirmation(FIELD, VALID_DRAFT), sessionVersion = 1),
            confirm(seenRevision = StrategyRevision(1)),
        ),
    )
}
