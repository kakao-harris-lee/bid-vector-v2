package bidvector.adapters.notification

import bidvector.workflow.notification.MaskedTarget
import bidvector.workflow.notification.NotificationDeliveryPolicyData

/**
 * 봇 자격 — Telegram 이 「bot token」이라 부르는 값이다(runbook 이 그 이름을 든다). 원문이 나가는
 * 자리를 **하나로** 좁힌다(`LlmCredential`·`ServiceKey` 와 같은 관례).
 *
 * `data class` 가 **아니다** — 합성 `toString`·`copy`·`component1` 이 원문을 내보낸다.
 *
 * 이 값은 Bot API 의 **경로 조각**이라 요청 URI 에 들어간다. 그래서 그 URI 를 담은
 * `HttpRequest`·`HttpResponse` 의 `toString()` 은 자격을 싣는다 — 이 패키지의 어느 메시지 조립도
 * 그 두 객체를 읽지 않고, 로거를 쥐지도 않는다(`NotificationAdapterDependencyTest` 가 구조로 잰다).
 */
class TelegramBotCredential private constructor(
    private val raw: String,
) {
    /** 원문의 **유일한 출구** — 호출부는 URI 를 짓는 한 자리뿐이고 그 산출물은 지역 값이다. */
    internal fun pathSegment(): String = raw

    override fun toString(): String = MASKED

    override fun equals(other: Any?): Boolean = other is TelegramBotCredential && raw == other.raw

    override fun hashCode(): Int = raw.hashCode()

    companion object {
        private const val MASKED = "TelegramBotCredential(***)"

        /** 거부 메시지는 길이조차 담지 않는다 — 「비었다」는 사실만으로 충분하다. */
        fun of(raw: String): TelegramBotCredential {
            require(raw.isNotBlank()) { "봇 자격은 빈 문자열일 수 없다 — 값을 지어내지 않는다" }
            return TelegramBotCredential(raw)
        }
    }
}

/**
 * 발송 대상 chat id — 자격은 아니지만 **식별자**라 같은 취급을 받는다(NOTI-03: 원문이 로그·예외·
 * 장부에 남지 않는다). 결과가 나르는 표시는 [masked] 가 만드는 [MaskedTarget] 뿐이다.
 */
class TelegramChatId private constructor(
    private val raw: String,
) {
    /** 원문의 **유일한 출구** — form 본문을 짓는 한 자리뿐이다. */
    internal fun formValue(): String = raw

    /** 장부·결과가 나르는 표시 — 끝 `maskedSuffixLength` 자 외 전부를 덮는다(정책 값). */
    internal fun masked(policy: NotificationDeliveryPolicyData): MaskedTarget = MaskedTarget.mask(raw, policy)

    override fun toString(): String = MASKED

    override fun equals(other: Any?): Boolean = other is TelegramChatId && raw == other.raw

    override fun hashCode(): Int = raw.hashCode()

    companion object {
        private const val MASKED = "TelegramChatId(***)"

        fun of(raw: String): TelegramChatId {
            require(raw.isNotBlank()) { "chat id 는 빈 문자열일 수 없다 — 값을 지어내지 않는다" }
            return TelegramChatId(raw)
        }
    }
}
