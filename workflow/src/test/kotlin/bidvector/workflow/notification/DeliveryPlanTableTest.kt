package bidvector.workflow.notification

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private val ENABLED_WITH_ROUTE = ChannelPolicyFacts(channelEnabled = true, routeConfigured = true)
private val DISABLED = ChannelPolicyFacts(channelEnabled = false, routeConfigured = true)
private val ENABLED_NO_ROUTE = ChannelPolicyFacts(channelEnabled = true, routeConfigured = false)

private val LIVE = EnvironmentFacts(RuntimeEnvironment.Production, DeliveryMode.Live)
private val DRY_RUN = EnvironmentFacts(RuntimeEnvironment.Staging, DeliveryMode.DryRun)
private val BLOCKED = EnvironmentFacts(RuntimeEnvironment.Test, DeliveryMode.Blocked)

/**
 * scope.md ②, 설계 검토 (5)1 — 정책 3({Enabled+route, Disabled, Enabled+route 없음}) ×
 * 환경 3({Live, DryRun, Blocked}) = 9행 전수. 첫 위반이 이긴다: 정책(비활성 → route 없음)
 * 다음 환경(차단 → dry-run). 비활성+dry-run 은 비활성(우회 (6)(8)의 값 증거).
 */
class DeliveryPlanTableTest {
    @Test
    fun `정책 3 곱 환경 3 전수 아홉 행이 첫 위반 순서대로 판정된다`() {
        val cases =
            listOf(
                Triple(ENABLED_WITH_ROUTE, LIVE, PlanOutcome.Send),
                Triple(ENABLED_WITH_ROUTE, DRY_RUN, PlanOutcome.Suppressed(SuppressionReason.DryRun)),
                Triple(
                    ENABLED_WITH_ROUTE,
                    BLOCKED,
                    PlanOutcome.Suppressed(SuppressionReason.EnvironmentBlocked(RuntimeEnvironment.Test)),
                ),
                Triple(DISABLED, LIVE, PlanOutcome.Suppressed(SuppressionReason.ChannelDisabled)),
                Triple(DISABLED, DRY_RUN, PlanOutcome.Suppressed(SuppressionReason.ChannelDisabled)),
                Triple(DISABLED, BLOCKED, PlanOutcome.Suppressed(SuppressionReason.ChannelDisabled)),
                Triple(ENABLED_NO_ROUTE, LIVE, PlanOutcome.Suppressed(SuppressionReason.TargetMissing)),
                Triple(ENABLED_NO_ROUTE, DRY_RUN, PlanOutcome.Suppressed(SuppressionReason.TargetMissing)),
                Triple(ENABLED_NO_ROUTE, BLOCKED, PlanOutcome.Suppressed(SuppressionReason.TargetMissing)),
            )

        cases.forEach { (policy, environment, expected) ->
            resolveDeliveryPlan(policy, environment).outcome shouldBe expected
        }
    }

    @Test
    fun `정책 판정과 환경 판정은 서로 다른 필드로 남는다 — 합쳐지지 않는다`() {
        val plan = resolveDeliveryPlan(DISABLED, DRY_RUN)

        plan.policy shouldBe PolicyVerdict.ChannelDisabled
        plan.environment shouldBe EnvironmentVerdict.DryRun
        plan.outcome shouldBe PlanOutcome.Suppressed(SuppressionReason.ChannelDisabled)
    }
}
