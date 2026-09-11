package bidvector.workflow.notification

import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.strategy.OperatorId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val TEST_POLICY =
    NotificationDeliveryPolicyData(
        environmentModes = RuntimeEnvironment.entries.associateWith { DeliveryMode.Live },
        maskedSuffixLength = 4,
    )

private fun testRequest(key: IdempotencyKey): DeliveryRequest =
    planDelivery(
        NotificationIntent(key, OperatorId("op-1"), Channel.Telegram, ContentRef("content-1")),
        RouteKey("owner-route"),
    )

private fun testContent(): RenderedContent = RenderedContent(Channel.Telegram, "hello")

/**
 * scope.md ④, `OPEN-NOTI-02` 해소 — 같은 `idempotencyKey` 재호출은 효과 0(앞 결과를 그대로
 * 돌려준다). [FakeNotificationSender]는 호출 횟수([FakeNotificationSender.callCount])와
 * 효과 횟수([FakeNotificationSender.effectCount])를 분리 계수해 이 계약을 증명한다
 * (우회 (2)(3)의 test 증거 — 실 어댑터는 [NotificationSender] KDoc 이 요구하는 이 계약을
 * 같은 골격의 test로 계승해야 한다, 알려진 제한).
 */
class SenderContractTest {
    @Test
    fun `같은 idempotencyKey 를 두 번 호출하면 호출은 2회, 효과는 1회, 두 결과가 같다`() {
        val sender = FakeNotificationSender()
        val request = testRequest(IdempotencyKey("noti-1"))
        val content = testContent()

        val first = sender.send(request, content)
        val second = sender.send(request, content)

        sender.callCount shouldBe 2
        sender.effectCount shouldBe 1
        second shouldBe first
    }

    @Test
    fun `실패 주입 뒤에도 같은 키 재호출은 그 Rejected 결과를 그대로 돌려준다`() {
        val sender =
            FakeNotificationSender(nextResultOverride = DeliveryResult.Rejected(RejectionReason.ProviderDeclined))
        val request = testRequest(IdempotencyKey("noti-2"))
        val content = testContent()

        val first = sender.send(request, content)
        val second = sender.send(request, content)

        first shouldBe DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
        second shouldBe first
        sender.callCount shouldBe 2
        sender.effectCount shouldBe 1
    }

    @Test
    fun `Unknown 주입도 같은 키 재호출에서 같은 값을 돌려준다`() {
        val fixedInstant = Instant.parse("2026-09-09T00:00:00Z")
        val sender = FakeNotificationSender(nextResultOverride = DeliveryResult.Unknown(fixedInstant))
        val request = testRequest(IdempotencyKey("noti-3"))
        val content = testContent()

        val first = sender.send(request, content)
        val second = sender.send(request, content)

        first shouldBe DeliveryResult.Unknown(fixedInstant)
        second shouldBe first
        sender.callCount shouldBe 2
        sender.effectCount shouldBe 1
    }

    @Test
    fun `다른 idempotencyKey 는 각각 별도 효과를 낸다`() {
        val sender = FakeNotificationSender()

        sender.send(testRequest(IdempotencyKey("noti-a")), testContent())
        sender.send(testRequest(IdempotencyKey("noti-b")), testContent())

        sender.callCount shouldBe 2
        sender.effectCount shouldBe 2
    }
}

/**
 * 계약 증명용 fake(scope.md ④) — [DeliveryResult.Delivered]가 기본이고 [nextResultOverride]
 * 로 실패·Unknown을 주입할 수 있다. [callCount](모든 호출)와 [effectCount](고유 키 수)를
 * 분리 계수한다 — 같은 키 재호출은 호출만 늘고 효과는 늘지 않는다.
 */
private class FakeNotificationSender(
    private val nextResultOverride: DeliveryResult? = null,
) : NotificationSender {
    private val effects = mutableMapOf<IdempotencyKey, DeliveryResult>()
    var callCount: Int = 0
        private set
    val effectCount: Int
        get() = effects.size

    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult {
        callCount++
        return effects.getOrPut(request.idempotencyKey) {
            nextResultOverride ?: DeliveryResult.Delivered(Instant.EPOCH, MaskedTarget.mask("chat-target", TEST_POLICY))
        }
    }
}
