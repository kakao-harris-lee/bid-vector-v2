package bidvector.adapters.notification

import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.MaskedTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * D-3 — **원문 0**. 토큰이 URI 경로에 들어가므로(`/bot<자격>/sendMessage`) `HttpRequest`·
 * `HttpResponse` 의 `toString()` 은 그 자격을 싣는다. 이 묶음은 「JDK 가 URI 를 싣는지」에
 * 의존하지 않고 **그 두 객체가 어느 관측 가능한 문자열에도 닿지 않음**을 거동으로 잰다.
 *
 * 갈래마다 같은 바늘 둘(가짜 자격·가짜 chat id 원문)을 **관측 가능한 표면 전수**에서 찾는다:
 * 결과값 문자열 · 설정 문자열 · 자격 값 타입 문자열 · 발송이 던진 예외(있으면 그 stack trace).
 * 로그 축은 문자열이 아니라 **구조**로 닫는다 — 이 패키지는 로거를 쥐지 않고,
 * [NotificationAdapterDependencyTest] 가 바이트코드로 그것을 단언한다.
 */
class TelegramCredentialLeakTest {
    private val settings = telegramSettings("http://127.0.0.1:1")

    /**
     * **바늘이 진짜임을 먼저 잰다**(양성 대조). 자격이 실제로 경로에 실려 나가고 chat id 가 form
     * 에 실려 나간다 — 그러지 않으면 아래 부재 단언은 「보내지 않았다」와 구별되지 않는다.
     */
    @Test
    fun `가짜 서버는 자격과 chat id 를 실제로 받는다 — 바늘 양성 대조`() {
        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            dispatchThrough(telegramSender(telegramSettings(server.baseUrl)))

            val call = server.received().single()
            call.path shouldContain FAKE_BOT_CREDENTIAL
            call.form.getValue("chat_id") shouldBe FAKE_CHAT_ID
        }
    }

    @Test
    fun `자격 값 타입의 toString 은 원문을 담지 않는다`() {
        val credential = TelegramBotCredential.of(FAKE_BOT_CREDENTIAL)
        val chatId = TelegramChatId.of(FAKE_CHAT_ID)

        assertNoRaw("credential.toString", credential.toString())
        assertNoRaw("chatId.toString", chatId.toString())
        // data class 의 합성 toString 은 필드 타입의 toString 을 쓴다 — 그래서 설정도 함께 깨끗하다.
        assertNoRaw("settings.toString", settings.toString())
    }

    /** 전달 결과가 나르는 target 은 마스킹된 값이다 — 끝 네 자리만 남고 원문은 복원되지 않는다. */
    @Test
    fun `전달 결과의 target 은 마스킹 값이다`() {
        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            val result = dispatchThrough(telegramSender(telegramSettings(server.baseUrl)))

            val delivered = result as DeliveryResult.Delivered
            assertNoRaw("delivered.toString", delivered.toString())
            delivered.target.toString() shouldBe MaskedTarget.mask(FAKE_CHAT_ID, LIVE_POLICY).toString()
            delivered.target.toString() shouldContain CHAT_ID_SUFFIX
        }
    }

    /**
     * **응답 본문이 chat id 를 되비추어도** 새지 않는다 — Telegram 의 실제 `sendMessage` 응답은
     * `result.chat.id` 를 싣는다. 우리는 `ok` 한 칸만 읽고 본문을 어디에도 옮기지 않는다.
     */
    @Test
    fun `응답 본문이 chat id 를 되비추어도 결과에 남지 않는다`() {
        val echo = """{"ok":true,"result":{"message_id":7,"chat":{"id":$FAKE_CHAT_ID}}}"""

        FakeTelegramServer.start(listOf(TelegramScript.Reply(OK, echo))).use { server ->
            val result = dispatchThrough(telegramSender(telegramSettings(server.baseUrl)))

            assertNoRaw("echo 응답의 결과", result.toString())
        }
    }

    /**
     * **갈래 전수**. 어느 갈래에서도 발송은 예외를 밖으로 내지 않고(`runCatching` 이 성공이다),
     * 결과 문자열에 바늘이 없다. 갈래가 하나라도 예외로 빠져나가면 그 stack trace 가 함께 검사된다.
     */
    @Test
    fun `실패 갈래 전수에서 원문이 어느 관측 표면에도 없다`() {
        leakProbes().forEach { probe ->
            val attempt = runCatching { probe.run() }

            withClue("${probe.name} 은 예외를 밖으로 내지 않는다") { attempt.isSuccess shouldBe true }
            assertNoRaw("${probe.name} 결과", attempt.getOrNull().toString())
            attempt.exceptionOrNull()?.let { assertNoRaw("${probe.name} 예외", it.stackTraceToString()) }
        }
    }

    @Test
    fun `갈래 목록이 비어 있지 않다 — 공허한 전수 금지`() {
        leakProbes() shouldHaveSize EXPECTED_PROBE_COUNT
    }

    private fun assertNoRaw(
        where: String,
        observed: String,
    ) {
        withClue("$where 에 자격 원문이 있다") { observed shouldNotContain FAKE_BOT_CREDENTIAL }
        withClue("$where 에 chat id 원문이 있다") { observed shouldNotContain FAKE_CHAT_ID }
    }

    private fun leakProbes(): List<LeakProbe> =
        listOf(
            serverProbe("2xx ok", TelegramScript.ok()),
            serverProbe("2xx ok false", TelegramScript.Reply(OK, """{"ok":false,"description":"no"}""")),
            serverProbe("2xx 해독 불가", TelegramScript.Reply(OK, "<<<not json>>>")),
            serverProbe("400", TelegramScript.Reply(BAD_REQUEST, """{"ok":false}""")),
            serverProbe("401", TelegramScript.Reply(UNAUTHORIZED, """{"ok":false}""")),
            serverProbe("403", TelegramScript.Reply(FORBIDDEN, """{"ok":false}""")),
            serverProbe("429", TelegramScript.Reply(TOO_MANY_REQUESTS, """{"ok":false}""")),
            serverProbe("5xx", TelegramScript.Reply(SERVER_ERROR, "")),
            serverProbe("송신 뒤 끊김", TelegramScript.Truncate),
            LeakProbe("응답 timeout") {
                FakeTelegramServer.start(listOf(stallLongerThanTimeout())).use { server ->
                    dispatchThrough(telegramSender(impatientSettings(server.baseUrl)))
                }
            },
            LeakProbe("연결 거부") {
                dispatchThrough(telegramSender(telegramSettings(FakeTelegramServer.closedPortBaseUrl())))
            },
            LeakProbe("이름 해소 실패") {
                dispatchThrough(telegramSender(telegramSettings("http://str12-no-such-host.invalid")))
            },
        )

    private fun serverProbe(
        name: String,
        script: TelegramScript,
    ): LeakProbe =
        LeakProbe(name) {
            FakeTelegramServer.start(listOf(script)).use { server ->
                dispatchThrough(telegramSender(telegramSettings(server.baseUrl)))
            }
        }

    private class LeakProbe(
        val name: String,
        val run: () -> DeliveryResult,
    )

    private companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
        const val CHAT_ID_SUFFIX = "3210"
        const val EXPECTED_PROBE_COUNT = 12
    }
}
