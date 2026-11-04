package bidvector.adapters.notification

import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.MaskedTarget
import bidvector.workflow.notification.RejectionReason
import bidvector.workflow.notification.RouteKey
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test

/**
 * 실 sender 의 **거동** — 가짜 Telegram 서버(D-9)를 상대로 D-4 전 갈래를 민다. 순수 판정은
 * [TelegramDeliveryMappingTest] 가 들고, 이쪽은 **그 판정에 닿는 입력을 실제 HTTP 왕복에서
 * 뽑아내는가**를 잰다(상태 코드 읽기·본문 읽기·예외 분류·form 조립).
 */
class TelegramNotificationSenderTest {
    private fun sendTo(
        script: List<TelegramScript>,
        settingsFor: (String) -> TelegramSenderSettings = ::telegramSettings,
        routeKey: RouteKey = FAKE_ROUTE,
    ): Pair<DeliveryResult, List<TelegramCallRecord>> =
        FakeTelegramServer.start(script).use { server ->
            val result = dispatchThrough(telegramSender(settingsFor(server.baseUrl)), routeKey = routeKey)
            result to server.received()
        }

    private fun resultOf(status: Int, body: String): DeliveryResult =
        sendTo(listOf(TelegramScript.Reply(status, body))).first

    @Test
    fun `2xx 와 ok true 면 전달이고 target 은 마스킹된 값이다`() {
        val (result, received) = sendTo(listOf(TelegramScript.ok()))

        result shouldBe DeliveryResult.Delivered(SENT_AT, MaskedTarget.mask(FAKE_CHAT_ID, LIVE_POLICY))
        received shouldHaveSize 1
    }

    /**
     * **보내는 form 칸이 둘뿐이다** — `parse_mode` 를 켜면 공고명의 마크업 문자가 Telegram 파서를
     * 깨거나 다른 뜻이 된다(설계 검토 우회 8). 「안 보낸다」는 키 **집합 등식**으로만 측정된다.
     */
    @Test
    fun `보내는 form 은 chat_id 와 text 둘뿐이고 경로는 sendMessage 다`() {
        val (_, received) = sendTo(listOf(TelegramScript.ok()))

        val call = received.single()
        call.form.keys shouldBe setOf("chat_id", "text")
        call.form.getValue("chat_id") shouldBe FAKE_CHAT_ID
        call.form.getValue("text") shouldBe "발송 본문"
        call.path shouldEndWith "/sendMessage"
        (call.contentType ?: "") shouldContain "application/x-www-form-urlencoded"
    }

    /** 본문에 줄바꿈·`&`·`=`·한글이 섞여도 서버가 **원문 그대로** 되살린다(인코딩 왕복). */
    @Test
    fun `본문의 구분자와 줄바꿈이 인코딩 왕복을 지난다`() {
        val body = "첫 줄\n두 번째 & 세 번째=끝 100%"
        val received =
            FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
                dispatchThrough(
                    telegramSender(telegramSettings(server.baseUrl)),
                    renderer = FixedContentRenderer(body),
                )
                server.received()
            }

        received.single().form.getValue("text") shouldBe body
    }

    @Test
    fun `확정 거부 셋이 Rejected 로 온다`() {
        resultOf(BAD_REQUEST, CHAT_NOT_FOUND_BODY) shouldBe
            DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        resultOf(FORBIDDEN, BLOCKED_BODY) shouldBe
            DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        resultOf(UNAUTHORIZED, UNAUTHORIZED_BODY) shouldBe
            DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
    }

    @Test
    fun `429 와 5xx 는 Unknown 으로 온다 — 재시도하지 않는다`() {
        resultOf(TOO_MANY_REQUESTS, RETRY_AFTER_BODY) shouldBe DeliveryResult.Unknown(SENT_AT)
        resultOf(SERVER_ERROR, "") shouldBe DeliveryResult.Unknown(SENT_AT)
    }

    @Test
    fun `2xx 에 ok false 또는 해독 불가 본문이면 Unknown 이다`() {
        resultOf(OK, """{"ok":false,"description":"x"}""") shouldBe DeliveryResult.Unknown(SENT_AT)
        resultOf(OK, "not json at all") shouldBe DeliveryResult.Unknown(SENT_AT)
        resultOf(OK, "") shouldBe DeliveryResult.Unknown(SENT_AT)
        resultOf(OK, """{"ok":"true"}""") shouldBe DeliveryResult.Unknown(SENT_AT)
        resultOf(OK, """[{"ok":true}]""") shouldBe DeliveryResult.Unknown(SENT_AT)
    }

    /** 연결 거부 — 요청이 Telegram 에 **닿지 않았음이 확정**이라 Rejected 다(D-4). */
    @Test
    fun `연결이 거부되면 Rejected 이고 호출은 일어나지 않는다`() {
        val sender = telegramSender(telegramSettings(FakeTelegramServer.closedPortBaseUrl()))

        dispatchThrough(sender) shouldBe DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
    }

    /** 이름 해소 실패(DNS)도 같은 갈래다 — 예약된 미등록 TLD 를 쓴다(바깥 질의 0). */
    @Test
    fun `이름이 해소되지 않으면 Rejected 다`() {
        val sender = telegramSender(telegramSettings("http://str12-no-such-host.invalid"))

        dispatchThrough(sender) shouldBe DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
    }

    @Test
    fun `응답 timeout 은 Unknown 이다`() {
        val (result, received) = sendTo(listOf(stallLongerThanTimeout()), settingsFor = ::impatientSettings)

        result shouldBe DeliveryResult.Unknown(SENT_AT)
        received shouldHaveSize 1
    }

    /** 송신 뒤 연결이 끊기면 Unknown 이다 — 받았는지 모른다. */
    @Test
    fun `송신 뒤 끊김은 Unknown 이다`() {
        val (result, received) = sendTo(listOf(TelegramScript.Truncate))

        result shouldBe DeliveryResult.Unknown(SENT_AT)
        received shouldHaveSize 1
    }

    /**
     * 설정에 없는 route 는 **네트워크 호출 0** 에 `InvalidTarget` 이다 — 「기본 chat 으로 메운다」
     * 경로가 없다(설계 검토 우회 6).
     */
    @Test
    fun `설정과 다른 route 는 호출 없이 대상 거부다`() {
        val (result, received) = sendTo(listOf(TelegramScript.ok()), routeKey = RouteKey("other-route"))

        result shouldBe DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        received.shouldBeEmpty()
    }

    private companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500

        const val CHAT_NOT_FOUND_BODY = """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}"""
        const val BLOCKED_BODY = """{"ok":false,"error_code":403,"description":"Forbidden: bot was blocked"}"""
        const val UNAUTHORIZED_BODY = """{"ok":false,"error_code":401,"description":"Unauthorized"}"""
        const val RETRY_AFTER_BODY = """{"ok":false,"error_code":429,"parameters":{"retry_after":30}}"""
    }
}
