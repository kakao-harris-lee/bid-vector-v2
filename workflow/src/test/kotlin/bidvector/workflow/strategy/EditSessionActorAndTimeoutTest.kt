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
private val OTHER_OPERATOR = Actor.Operator(OperatorId("op-2"))
private val SYSTEM = Actor.System("scheduled-tuning")
private val FIELD = EditableField.Threshold(ThresholdField.BidNowThreshold)
private val VALID_DRAFT = StrategyDraft(bidNowThreshold = BigDecimal("0.7"))

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

private fun freshSession(expiresAt: Instant = NOW.plus(Duration.ofMinutes(15))): EditSession =
    beginSession(EditSessionId("s-1"), OPERATOR.id, FIELD, NOW.minusSeconds(1), TEST_SESSION_POLICY)
        .copy(expiresAt = expiresAt)

private fun provideValue(
    id: String = "cmd-1",
    actor: Actor = OPERATOR,
): EditCommand.ProvideValue = EditCommand.ProvideValue(CommandId(id), EditSessionId("s-1"), actor, FIELD, VALID_DRAFT)

/** scope.md ③ — actor/operator scope 와 timeout(④). 판정 순서(설계 검토 (4) 2) ①③을 잰다. */
class EditSessionActorAndTimeoutTest {
    @Test
    fun `③ 세션을 시작한 operator 와 다른 operator 의 command 는 ActorMismatch 로 거부된다`() {
        val session = freshSession()

        val outcome = apply(session, provideValue(actor = OTHER_OPERATOR), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.ActorMismatch
        outcome.session.state shouldBe session.state
    }

    @Test
    fun `D-4A-5 System actor 의 command 는 전이표에 행이 없어 SystemActorNotPermitted 로 거부된다`() {
        val session = freshSession()

        val outcome = apply(session, provideValue(actor = SYSTEM), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.SystemActorNotPermitted
    }

    @Test
    fun `④ 만료 시각을 넘긴 command 는 Expired 로 전이한 뒤 SessionExpired 로 거부된다`() {
        val session = freshSession(expiresAt = NOW.minusSeconds(1))

        val outcome = apply(session, provideValue(), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.SessionExpired
        outcome.session.state shouldBe EditSessionState.Expired
        outcome.session.sessionVersion shouldBe session.sessionVersion + 1
    }

    @Test
    fun `④ 만료 시각과 같은 순간(now == expiresAt)은 만료로 판정한다 — 배타적 상한`() {
        val expiresAt = NOW.plus(Duration.ofMinutes(15))
        val session = freshSession(expiresAt = expiresAt)

        val outcome = apply(session, provideValue(), expiresAt, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.SessionExpired
    }

    @Test
    fun `④ 이미 Expired 인 세션은 어떤 command 를 다시 보내도 정직하게 SessionExpired 를 낸다`() {
        val expired =
            freshSession(expiresAt = NOW.minusSeconds(1))
                .copy(state = EditSessionState.Expired)

        val outcome = apply(expired, provideValue(), NOW, currentStrategy(), policyOf())

        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.SessionExpired
        outcome.session.state shouldBe EditSessionState.Expired
    }

    @Test
    fun `expireIfDue 는 종단 상태를 시각과 무관하게 그대로 둔다`() {
        val applied =
            freshSession(expiresAt = NOW.minusSeconds(1))
                .copy(state = EditSessionState.Applied(StrategyRevision(2)))

        val result = expireIfDue(applied, NOW)

        result shouldBe applied
    }
}
