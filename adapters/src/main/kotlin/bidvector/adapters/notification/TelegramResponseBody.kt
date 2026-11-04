package bidvector.adapters.notification

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * 응답 본문에서 **`ok` 한 칸만** 읽는다(D-4). 손으로 짠 파서를 두지 않는다 — `adapters` 가 이미
 * `com.fasterxml.jackson.databind` 를 쓰고(`EditSessionRow`·`SchemaValidation`), 새 의존성이 0 이다.
 *
 * **본문의 나머지를 어디로도 옮기지 않는다.** Telegram 의 실제 `sendMessage` 응답은
 * `result.chat.id` 를 되비추므로, 본문을 로그·예외·결과에 실으면 chat id 가 그대로 새어 나간다.
 * 해독 실패도 **사유 없는 하나의 값**으로 접는다 — Jackson 의 예외 메시지는 원문 조각을 담는다.
 *
 * 모르는 모양(배열·스칼라·빈 문자열·`ok` 가 boolean 이 아님)은 전부 [TelegramBodyVerdict.Unreadable]
 * 이고 그것은 모호로 이어진다 — 「읽지 못했다」가 전달로 접히는 길이 없다.
 */
internal object TelegramResponseBody {
    private const val OK_FIELD = "ok"

    /** 읽기 전용 사용이라 인스턴스를 공유한다(Jackson `ObjectMapper` 는 그 쓰임에서 thread-safe 다). */
    private val mapper = ObjectMapper()

    fun verdictOf(body: String): TelegramBodyVerdict =
        try {
            readVerdict(body)
        } catch (
            @Suppress("SwallowedException") parseFailure: JacksonException,
        ) {
            // 예외를 **읽지 않는다** — 그 메시지는 원문 조각(`[Source: (String)"…"]`)을 담는다.
            TelegramBodyVerdict.Unreadable
        }

    private fun readVerdict(body: String): TelegramBodyVerdict {
        val root = mapper.readTree(body)
        if (!root.isObject) return TelegramBodyVerdict.Unreadable
        val ok = root.get(OK_FIELD) ?: return TelegramBodyVerdict.Unreadable
        if (!ok.isBoolean) return TelegramBodyVerdict.Unreadable
        return if (ok.booleanValue()) TelegramBodyVerdict.Ok else TelegramBodyVerdict.NotOk
    }
}
