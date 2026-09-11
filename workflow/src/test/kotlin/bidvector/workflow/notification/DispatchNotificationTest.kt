package bidvector.workflow.notification

import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.strategy.OperatorId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private val OWNER_A = OperatorId("owner-a")
private val OWNER_B = OperatorId("owner-b")
private val ROUTE_A = RouteKey("route-a")
private val ROUTE_B = RouteKey("route-b")
private val ENABLED_A = ChannelRoute(enabled = true, key = ROUTE_A)
private val DISABLED_A = ChannelRoute(enabled = false, key = ROUTE_A)
private val ENABLED_B = ChannelRoute(enabled = true, key = ROUTE_B)

private val TEST_POLICY_DATA =
    NotificationDeliveryPolicyData(
        environmentModes =
            mapOf(
                RuntimeEnvironment.Production to DeliveryMode.Live,
                RuntimeEnvironment.Staging to DeliveryMode.DryRun,
                RuntimeEnvironment.Development to DeliveryMode.DryRun,
                RuntimeEnvironment.Test to DeliveryMode.Blocked,
            ),
        maskedSuffixLength = 4,
    )

private fun intentFor(owner: OperatorId): NotificationIntent =
    NotificationIntent(IdempotencyKey("noti-${owner.value}"), owner, Channel.Telegram, ContentRef("content-1"))

/** [FakeRouteDirectory] 조립 축약 — 채널은 이 test 전부 Telegram 고정, route가 없는 owner는 `null`로 표기. */
private fun routesWith(vararg entries: Pair<OperatorId, ChannelRoute?>): FakeRouteDirectory =
    FakeRouteDirectory(
        entries.associate { (owner, route) -> owner to (route?.let { mapOf(Channel.Telegram to it) } ?: emptyMap()) },
    )

private fun deliveredSender(): RecordingSender =
    RecordingSender { DeliveryResult.Delivered(Instant.EPOCH, MaskedTarget.mask("chat", TEST_POLICY_DATA)) }

/**
 * scope.md ②③④⑤⑥, 설계 검토 (5)1·(4) — dispatch use case 배선 test. route 획득 경로가
 * [RouteDirectory.routesFor]뿐임(owner isolation, 우회 (없음 — API 표면))과 억제 시
 * [NotificationSender]를 읽지도 않음(dry-run 이 sender 를 감싸지 않는다, 우회 (5))을
 * 실측한다. [ThrowingSender]가 호출되면 즉시 실패하므로 억제 경로 넷(dry-run·blocked·
 * disabled·missing) 전부가 무호출임을 증명한다.
 */
class DispatchNotificationTest {
    @Test
    fun `Live 환경 정상 경로는 sender 를 정확히 한 번 부르고 Attempted(Delivered) 를 낸다`() {
        val sender = deliveredSender()
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to ENABLED_A),
                renderer = FakeContentRenderer(),
                sender = sender,
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Production,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        sender.callCount shouldBe 1
        outcome.shouldBeInstanceOf<DeliveryOutcome.Attempted>()
        outcome.result.shouldBeInstanceOf<DeliveryResult.Delivered>()
    }

    @Test
    fun `dry-run 환경은 sender 를 호출하지 않고 Suppressed(DryRun) 을 낸다`() {
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to ENABLED_A),
                renderer = FakeContentRenderer(),
                sender = ThrowingSender(),
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Staging,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        outcome.shouldBeInstanceOf<DeliveryOutcome.Suppressed>()
        outcome.plan.outcome shouldBe PlanOutcome.Suppressed(SuppressionReason.DryRun)
    }

    @Test
    fun `차단 환경은 sender 를 호출하지 않고 Suppressed(EnvironmentBlocked) 를 낸다`() {
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to ENABLED_A),
                renderer = FakeContentRenderer(),
                sender = ThrowingSender(),
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Test,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        outcome.shouldBeInstanceOf<DeliveryOutcome.Suppressed>()
        outcome.plan.outcome shouldBe
            PlanOutcome.Suppressed(SuppressionReason.EnvironmentBlocked(RuntimeEnvironment.Test))
    }

    @Test
    fun `채널 비활성이면 sender 를 호출하지 않고 Suppressed(ChannelDisabled) 를 낸다`() {
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to DISABLED_A),
                renderer = FakeContentRenderer(),
                sender = ThrowingSender(),
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Production,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        outcome.shouldBeInstanceOf<DeliveryOutcome.Suppressed>()
        outcome.plan.outcome shouldBe PlanOutcome.Suppressed(SuppressionReason.ChannelDisabled)
    }

    @Test
    fun `route 가 없으면 sender 를 호출하지 않고 Suppressed(TargetMissing) 을 낸다`() {
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to null),
                renderer = FakeContentRenderer(),
                sender = ThrowingSender(),
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Production,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        outcome.shouldBeInstanceOf<DeliveryOutcome.Suppressed>()
        outcome.plan.outcome shouldBe PlanOutcome.Suppressed(SuppressionReason.TargetMissing)
    }

    @Test
    fun `Unknown 결과는 재시도 없이 호출 1회로 그대로 위로 올라간다`() {
        val fixedInstant = Instant.parse("2026-09-09T00:00:00Z")
        val sender = RecordingSender { DeliveryResult.Unknown(fixedInstant) }
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to ENABLED_A),
                renderer = FakeContentRenderer(),
                sender = sender,
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Production,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        sender.callCount shouldBe 1
        outcome.shouldBeInstanceOf<DeliveryOutcome.Attempted>()
        outcome.result shouldBe DeliveryResult.Unknown(fixedInstant)
    }

    @Test
    fun `owner A 의 intent 는 owner B 의 route 로 가지 않는다 — owner isolation`() {
        val sender = deliveredSender()
        val dispatch =
            DispatchNotification(
                routes = routesWith(OWNER_A to null, OWNER_B to ENABLED_B),
                renderer = FakeContentRenderer(),
                sender = sender,
                policyData = TEST_POLICY_DATA,
                environment = RuntimeEnvironment.Production,
            )

        val outcome = dispatch.dispatch(intentFor(OWNER_A))

        // A는 B의 route(ROUTE_B)를 빌리지 않고 TargetMissing으로 억제된다.
        outcome.shouldBeInstanceOf<DeliveryOutcome.Suppressed>()
        outcome.plan.outcome shouldBe PlanOutcome.Suppressed(SuppressionReason.TargetMissing)
        sender.callCount shouldBe 0
    }
}

private class FakeRouteDirectory(
    private val routes: Map<OperatorId, Map<Channel, ChannelRoute>>,
) : RouteDirectory {
    override fun routesFor(owner: OperatorId): Map<Channel, ChannelRoute> = routes[owner] ?: emptyMap()
}

private class FakeContentRenderer : ContentRenderer {
    override fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent = RenderedContent(channel, "rendered:${contentRef.value}")
}

/** 호출되면 즉시 실패한다 — 억제 경로가 sender 를 읽지 않음을 실측한다(우회 (5)). */
private class ThrowingSender : NotificationSender {
    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult = error("Suppressed 경로에서 sender 가 호출됐다 — 억제가 sender 를 감싸지 않아야 한다")
}

private class RecordingSender(
    private val nextResult: () -> DeliveryResult,
) : NotificationSender {
    var callCount: Int = 0
        private set

    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult {
        callCount++
        return nextResult()
    }
}
