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

        /**
         * Bot API 자격의 모양 — `<봇 id>:<비밀 조각>`(비밀 조각은 URL-safe 어휘다).
         *
         * **모양을 생성 시점에 막는 이유(v M-1)**: 이 값은 요청 URI 의 **경로 조각**이 된다.
         * URI 에 쓸 수 없는 문자(공백·`{`·`[` 등)가 섞이면 `URI.create` 가
         * `IllegalArgumentException` 을 던지고, 그것이 발송 경로의 포괄 `catch` 에 걸려
         * **`Unknown`(모호)으로 오분류**된다 — 설정 오류가 「받았는지 모른다」로 기록되고 그 행이
         * `ISOLATED` 로 탄다. 여기서 막으면 같은 오류가 **기동 실패**로 나온다(러너가 돌기 전).
         */
        private val SHAPE = Regex("^[0-9]+:[A-Za-z0-9_-]+$")

        /**
         * 거부 메시지는 **원문도 길이도 담지 않는다**(v N-6) — 앞 판은 길이를 남겼는데, 그것은
         * 설정 `toString` 의 KDoc 이 「**길이도 비밀 값의 단서다**」라고 적는 것과 어긋났다. 두 자리의
         * 문면을 하나로 맞춘다: 거부는 **어느 칸이 어떤 모양을 어겼는지**만 말한다. 운영자가 고치는 데
         * 필요한 것은 그것이고(§13.6 의 행 키), 길이는 거기에 보탬이 없다.
         */
        fun of(raw: String): TelegramBotCredential {
            require(raw.isNotBlank()) { "봇 자격은 빈 문자열일 수 없다 — 값을 지어내지 않는다" }
            require(SHAPE.matches(raw)) {
                "봇 자격이 Bot API 모양이 아니다(<봇 id>:<비밀 조각>) — 원문도 길이도 담지 않는다"
            }
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
