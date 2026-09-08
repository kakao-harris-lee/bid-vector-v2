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
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
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

private fun freshSession(): EditSession =
    beginSession(EditSessionId("s-1"), OPERATOR.id, FIELD, NOW.minusSeconds(1), TEST_SESSION_POLICY)
        .copy(expiresAt = NOW.plus(Duration.ofMinutes(15)))

/**
 * scope.md ④ — timeout 과 중복 command. 설계 검토 (4) 2의 불변식을 직접 단언한다: 재전달의
 * 불변식은 「첫 결과와 같은 값」이 아니라 「효과 0 · 상태 불변」이다.
 */
class EditSessionIdempotencyPropertyTest {
    @Test
    fun `④ 같은 commandId·같은 내용의 재전달은 효과 0 이고 상태가 불변이다`() {
        runBlocking {
            checkAll(Arb.int(1, 1_000_000)) { rawId ->
                val session = freshSession()
                val command =
                    EditCommand.ProvideValue(
                        CommandId("cmd-$rawId"),
                        session.id,
                        OPERATOR,
                        FIELD,
                        StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                    )

                val first = apply(session, command, NOW, currentStrategy(), policyOf())
                val replay = apply(first.session, command, NOW, currentStrategy(), policyOf())

                replay shouldBe TransitionOutcome.Accepted(first.session)
                replay.session.sessionVersion shouldBe first.session.sessionVersion
            }
        }
    }

    @Test
    fun `④ 같은 commandId·다른 내용의 재전달은 IdempotencyConflict 로 거부되고 상태가 불변이다`() {
        runBlocking {
            checkAll(Arb.int(1, 1_000_000)) { rawId ->
                val session = freshSession()
                val commandId = CommandId("cmd-$rawId")
                val original =
                    EditCommand.ProvideValue(
                        commandId,
                        session.id,
                        OPERATOR,
                        FIELD,
                        StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                    )
                val conflicting = original.copy(draft = StrategyDraft(bidNowThreshold = BigDecimal("0.8")))

                val first = apply(session, original, NOW, currentStrategy(), policyOf())
                val replay = apply(first.session, conflicting, NOW, currentStrategy(), policyOf())

                replay.shouldBeRejectedWith(RejectionReason.IdempotencyConflict)
                replay.session.state shouldBe first.session.state
                replay.session.sessionVersion shouldBe first.session.sessionVersion
            }
        }
    }

    @Test
    fun `④ Applied 로 이미 종단된 세션에 같은 Confirm 재전달은 효과 0 이다 — 두 번째 적용이 없다`() {
        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.7"))
        val session = freshSession().copy(state = EditSessionState.WaitingForConfirmation(FIELD, draft))
        val confirmCommand = EditCommand.Confirm(CommandId("confirm-1"), session.id, OPERATOR, StrategyRevision(1))

        val first = apply(session, confirmCommand, NOW, currentStrategy(1), policyOf())
        first.shouldBeInstanceOfApplied()

        val replay = apply(first.session, confirmCommand, NOW, currentStrategy(2), policyOf())

        replay shouldBe TransitionOutcome.Accepted(first.session)
    }

    @Test
    fun `④ 만료 뒤 같은 command 를 재전달해도 정직하게 SessionExpired 를 낸다 — 첫 결과와 같은 값이 아니다`() {
        val session = freshSession().copy(expiresAt = NOW.minusSeconds(1))
        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.7"))
        val command = EditCommand.ProvideValue(CommandId("cmd-1"), session.id, OPERATOR, FIELD, draft)

        val first = apply(session, command, NOW, currentStrategy(), policyOf())
        val replay = apply(first.session, command, NOW, currentStrategy(), policyOf())

        first.shouldBeRejectedWith(RejectionReason.SessionExpired)
        replay.shouldBeRejectedWith(RejectionReason.SessionExpired)
    }
}

private fun TransitionOutcome.shouldBeRejectedWith(reason: RejectionReason) {
    check(this is TransitionOutcome.Rejected) { "Rejected 를 기대했으나 $this" }
    this.reason shouldBe reason
}

private fun TransitionOutcome.shouldBeInstanceOfApplied() {
    check(this is TransitionOutcome.Applied) { "Applied 를 기대했으나 $this" }
}
