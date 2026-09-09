package bidvector.workflow.notification

/**
 * dispatch의 반환(scope.md ⑤) — [Suppressed]가 [Attempted]([DeliveryResult.Delivered])로
 * 바뀌는 문이 타입에 없다. [Suppressed]는 [DeliveryPlan] 전체(정책·환경 두 판정)를 실어
 * NOTI-03 「두 개의 서로 다른 값으로 구분 기록」을 위로 전달한다(설계 검토 (3)).
 */
sealed interface DeliveryOutcome {
    data class Suppressed(
        val plan: DeliveryPlan,
    ) : DeliveryOutcome

    data class Attempted(
        val result: DeliveryResult,
    ) : DeliveryOutcome
}

/**
 * 배달 dispatch use case(scope.md ②③④⑤⑥, 설계 검토 (5)2) — 생성자 주입(DI 규율). route는
 * [RouteDirectory.routesFor]로만 얻는다(owner isolation, 우회 없음 — API 표면 자체가
 * 방어선). 억제([DeliveryOutcome.Suppressed])면 [sender]를 읽지도 않는다(dry-run이
 * sender를 감싸지 않음, 우회 (5)). [DeliveryResult.Unknown]은 재시도하지 않는다(호출
 * 최대 1, D-4E-2 — 격리 판단은 4C 소유).
 */
class DispatchNotification(
    private val routes: RouteDirectory,
    private val renderer: ContentRenderer,
    private val sender: NotificationSender,
    private val policyData: NotificationDeliveryPolicyData,
    private val environment: RuntimeEnvironment,
) {
    fun dispatch(intent: NotificationIntent): DeliveryOutcome {
        val channelRoute = routes.routesFor(intent.owner)[intent.channel]
        val plan =
            resolveDeliveryPlan(
                policy = channelPolicyFactsOf(channelRoute),
                environment = EnvironmentFacts(environment, policyData.environmentModes.getValue(environment)),
            )

        return when (plan.outcome) {
            is PlanOutcome.Suppressed -> DeliveryOutcome.Suppressed(plan)
            PlanOutcome.Send -> attemptSend(intent, channelRoute, plan)
        }
    }

    private fun attemptSend(
        intent: NotificationIntent,
        channelRoute: ChannelRoute?,
        plan: DeliveryPlan,
    ): DeliveryOutcome.Attempted {
        val route =
            checkNotNull(channelRoute?.key) {
                "정책 판정이 Send(${plan.policy})인데 route가 없다 — resolveDeliveryPlan 불변식 위반"
            }
        val request = planDelivery(intent, route)
        val content = renderer.render(intent.contentRef, intent.channel)
        return DeliveryOutcome.Attempted(sender.send(request, content))
    }
}

private fun channelPolicyFactsOf(route: ChannelRoute?): ChannelPolicyFacts =
    ChannelPolicyFacts(
        channelEnabled = route?.enabled ?: true,
        routeConfigured = route?.key != null,
    )

/**
 * [DeliveryRequest] 신규 생성의 유일한 경로(scope.md ①, 설계 검토 (2)) — `internal`이라
 * `workflow` 밖에서 임의 필드로 요청을 조립하는 위조를 컴파일 층에서 닫는다.
 */
internal fun planDelivery(
    intent: NotificationIntent,
    route: RouteKey,
): DeliveryRequest =
    DeliveryRequest(
        idempotencyKey = intent.idempotencyKey,
        owner = intent.owner,
        channel = intent.channel,
        route = route,
        contentRef = intent.contentRef,
    )
