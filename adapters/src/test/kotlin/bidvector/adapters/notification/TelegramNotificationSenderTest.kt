package bidvector.adapters.notification

import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.MaskedTarget
import bidvector.workflow.notification.RejectionReason
import bidvector.workflow.notification.RouteKey
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
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

    private fun resultOf(
        status: Int,
        body: String,
    ): DeliveryResult = sendTo(listOf(TelegramScript.Reply(status, body))).first

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
     * cr M-6 — Bot API 의 `text` 상한은 **4096 자**다. 넘기면 `400`(확정 거부)이라 그 행이
     * `FAILED` 로 탄다 — **내용이 길다는 이유로 알림을 잃지 않는다.** 자르고 표지를 남긴다.
     */
    @Test
    fun `4096 자를 넘는 본문은 잘리고 표지가 남는다`() {
        val long = "가".repeat(5_000)

        val received =
            FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
                dispatchThrough(telegramSender(telegramSettings(server.baseUrl)), renderer = FixedContentRenderer(long))
                server.received()
            }

        val sent = received.single().form.getValue("text")
        sent.length shouldBe MAX_TELEGRAM_TEXT
        sent shouldContain "이하 생략"
        // 앞부분은 보존된다 — 자르기가 본문을 통째로 버리지 않는다.
        sent.startsWith("가".repeat(100)) shouldBe true
    }

    /**
     * **v N-5 = cr L-2** — 절단 지점에 **서러게이트 쌍**(BMP 밖 문자, 이모지 등)이 걸리면 쌍을
     * 가르지 않는다. 가르면 짝 없는 반쪽이 남고 `URLEncoder` 가 그것을 `?` 로 바꿔 보낸다 —
     * 전달 실패는 아니지만 운영자가 받는 메시지에 **깨진 글자**가 남는다.
     *
     * 경계를 **정확히 그 자리에** 맞춘다: 본문 앞부분을 「남길 길이 − 1」까지 BMP 문자로 채우고 그
     * 다음에 쌍 하나를 둔다. 그러면 코드 단위 절단이 바로 그 쌍의 가운데를 지난다.
     */
    @Test
    fun `절단 지점에 걸친 서러게이트 쌍을 가르지 않는다`() {
        val pair = "\uD83D\uDE80" // U+1F680, 코드 단위 둘
        val keep = MAX_TELEGRAM_TEXT - TRUNCATION_MARK_LENGTH
        val long = "가".repeat(keep - 1) + pair + "나".repeat(MAX_TELEGRAM_TEXT)

        val received =
            FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
                dispatchThrough(telegramSender(telegramSettings(server.baseUrl)), renderer = FixedContentRenderer(long))
                server.received()
            }

        val sent = received.single().form.getValue("text")
        val body = sent.removeSuffix(TRUNCATION_MARK_TEXT)

        withClue("표지가 붙어야 한다: $sent") { sent.endsWith(TRUNCATION_MARK_TEXT) shouldBe true }
        // 쌍을 통째로 버렸으므로 본문 끝은 BMP 문자이고 **고아 서러게이트가 없다**.
        withClue("본문 끝에 짝 없는 서러게이트가 남았다") { body.last().isSurrogate() shouldBe false }
        body.count { it.isSurrogate() } shouldBe 0
        // 한 단위 물러났으므로 전체 길이는 상한보다 1 짧다 — 상한을 넘지는 않는다.
        sent.length shouldBe MAX_TELEGRAM_TEXT - 1
    }

    /**
     * **음성 대조** — 쌍이 절단 지점에 걸치지 **않으면** 물러나지 않는다(길이가 상한 그대로다).
     * 이 칸이 없으면 위 test 가 「언제나 한 글자 버린다」는 구현으로도 통과한다.
     */
    @Test
    fun `절단 지점에 걸치지 않은 서러게이트 쌍은 길이를 줄이지 않는다`() {
        val pair = "\uD83D\uDE80"
        val keep = MAX_TELEGRAM_TEXT - TRUNCATION_MARK_LENGTH
        val long = pair + "가".repeat(keep) + "나".repeat(MAX_TELEGRAM_TEXT)

        val received =
            FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
                dispatchThrough(telegramSender(telegramSettings(server.baseUrl)), renderer = FixedContentRenderer(long))
                server.received()
            }

        val sent = received.single().form.getValue("text")
        sent.length shouldBe MAX_TELEGRAM_TEXT
    }

    /** 상한 **경계**: 정확히 4096 자는 그대로 간다(표지 없음). 한 칸 밀면 붉어진다. */
    @Test
    fun `정확히 4096 자는 자르지 않는다 — 경계`() {
        val exact = "나".repeat(MAX_TELEGRAM_TEXT)

        val received =
            FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
                dispatchThrough(
                    telegramSender(telegramSettings(server.baseUrl)),
                    renderer = FixedContentRenderer(exact),
                )
                server.received()
            }

        val sent = received.single().form.getValue("text")
        sent shouldBe exact
        sent shouldNotContain "이하 생략"
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

        /** Bot API `text` 상한 — production 상수의 사본이 아니라 **test 가 기대하는 값**이다. */
        const val MAX_TELEGRAM_TEXT = 4096

        /** 잘렸다는 표지 — 같은 이유로 **test 가 기대하는 문면**이다(구현 상수를 읽지 않는다). */
        const val TRUNCATION_MARK_TEXT = "\n…(이하 생략 — 알림함에서 확인하십시오)"
        const val TRUNCATION_MARK_LENGTH = 24

        const val CHAT_NOT_FOUND_BODY = """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}"""
        const val BLOCKED_BODY = """{"ok":false,"error_code":403,"description":"Forbidden: bot was blocked"}"""
        const val UNAUTHORIZED_BODY = """{"ok":false,"error_code":401,"description":"Unauthorized"}"""
        const val RETRY_AFTER_BODY = """{"ok":false,"error_code":429,"parameters":{"retry_after":30}}"""
    }
}
