package bidvector.adapters.notification

import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.RejectionReason
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-5 — `NotificationSender` KDoc 이 요구한 계약(「같은 key 재호출 효과 0」)을 **실 어댑터가**
 * 증명한다. `bidvector.workflow.notification.SenderContractTest` 와 **같은 골격**이고, 다른 점은
 * 효과 계수를 fake 의 필드가 아니라 **가짜 서버의 수신 수**로 센다는 것뿐이다 — 어댑터에서
 * 「효과」는 네트워크 호출이다.
 *
 * 영속 기억은 만들지 않는다(D-5) — 재기동을 건너는 창은 ADR 0005 §7.6 의 알려진 B-2 창이고 이
 * slice 가 넓히지도 좁히지도 않는다. 그래서 이 계약은 **한 sender 인스턴스 안**에서만 성립하고,
 * 아래 마지막 test 가 그 경계를 명시한다.
 */
class TelegramSenderContractTest {
    @Test
    fun `같은 키를 두 번 보내면 수신은 1, 두 결과가 같다`() {
        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            val sender = telegramSender(telegramSettings(server.baseUrl))

            val first = dispatchThrough(sender, key = "noti-1")
            val second = dispatchThrough(sender, key = "noti-1")

            server.received() shouldHaveSize 1
            second shouldBe first
        }
    }

    @Test
    fun `다른 키는 각각 효과를 낸다 — 수신 2`() {
        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            val sender = telegramSender(telegramSettings(server.baseUrl))

            dispatchThrough(sender, key = "noti-a")
            dispatchThrough(sender, key = "noti-b")

            server.received() shouldHaveSize 2
        }
    }

    /** 거부 결과도 기억한다 — 재호출이 **같은 거부**를 돌려주고 네트워크를 다시 때리지 않는다. */
    @Test
    fun `거부 뒤 같은 키 재호출은 그 거부를 그대로 돌려준다`() {
        FakeTelegramServer.start(listOf(TelegramScript.Reply(BAD_REQUEST, """{"ok":false}"""))).use { server ->
            val sender = telegramSender(telegramSettings(server.baseUrl))

            val first = dispatchThrough(sender, key = "noti-2")
            val second = dispatchThrough(sender, key = "noti-2")

            first shouldBe DeliveryResult.Rejected(RejectionReason.InvalidTarget)
            second shouldBe first
            server.received() shouldHaveSize 1
        }
    }

    /**
     * **모호한 결과도 기억한다** — 이 칸이 at-most-once 의 자리다. 기억하지 않으면 같은 run 안의
     * 재호출이 「받았을지도 모르는」 메시지를 한 번 더 보낸다.
     */
    @Test
    fun `모호 뒤 같은 키 재호출도 다시 보내지 않는다`() {
        FakeTelegramServer.start(listOf(TelegramScript.Reply(SERVER_ERROR, ""))).use { server ->
            val sender = telegramSender(telegramSettings(server.baseUrl))

            val first = dispatchThrough(sender, key = "noti-3")
            val second = dispatchThrough(sender, key = "noti-3")

            first shouldBe DeliveryResult.Unknown(SENT_AT)
            second shouldBe first
            server.received() shouldHaveSize 1
        }
    }

    /**
     * **기억의 경계를 명시한다**(알려진 제한 ①) — 새 sender 인스턴스는 같은 키를 다시 보낸다.
     * 이것이 ADR 0005 §7.6 B-2 창이고, 그 창을 relay 쪽 `inbox` dedup 이 **커밋된 뒤에만** 닫는다.
     * 이 test 가 없으면 「영속 기억을 만들지 않았다」가 문서 문면으로만 남는다.
     */
    @Test
    fun `새 인스턴스는 같은 키를 다시 보낸다 — 영속 기억이 없다`() {
        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            val settings = telegramSettings(server.baseUrl)

            dispatchThrough(telegramSender(settings), key = "noti-4")
            dispatchThrough(telegramSender(settings), key = "noti-4")

            server.received() shouldHaveSize 2
        }
    }

    private companion object {
        const val BAD_REQUEST = 400
        const val SERVER_ERROR = 500
    }
}
