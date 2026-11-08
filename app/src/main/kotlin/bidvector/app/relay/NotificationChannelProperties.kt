package bidvector.app.relay

import bidvector.adapters.notification.NoticeMessageSettings
import bidvector.adapters.notification.NotificationChannelSettings
import bidvector.adapters.notification.TelegramBotCredential
import bidvector.adapters.notification.TelegramChatId
import bidvector.adapters.notification.TelegramSenderSettings
import bidvector.workflow.notification.RouteKey
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 알림 발송 채널 설정(D-3·D-8·D-9).
 *
 * **자격 셋은 기본값이 없다**(`null`) — env 로만 들어온다. 지어낸 기본값은 곧 발송 대상이 되고,
 * 「설정이 비었는가」를 기동 검사가 보지 못하게 만든다. 그 셋이 비어도 **Live 가 아닌 환경은
 * 그대로 뜬다**(D-7, relay 가 claim 조차 하지 않는다).
 *
 * **환경 기본값 넷은 기본 인자로 둔다** — base URL · timeout 둘 · route 슬러그. 이 저장소에는
 * `application.yml` 이 하나도 없고(설정은 전부 env) 선례는 `OpeningCollectionProperties` 의 경로
 * 기본값이다: 설정 파일을 새로 들이면 기동 경로의 전제가 하나 늘고 세 compose 판이 그것을 함께
 * 알아야 한다.
 */
@ConfigurationProperties(prefix = "bidvector.notification")
class NotificationChannelProperties(
    val siteBaseUrl: String? = null,
    val telegram: TelegramChannelProperties = TelegramChannelProperties(),
) {
    /**
     * **`data class` 가 아니다(v H-1).** 합성 `toString` 은 필드를 그대로 찍으므로, 원문 `String`
     * 을 쥔 설정 객체를 `data class` 로 두면 자격·chat id 가 그 한 줄로 새어 나간다 — Spring 의
     * 바인딩 실패 메시지·배선 로그·디버거가 모두 그 경로다. `copy`·`componentN` 도 같은 이유로
     * 만들지 않는다(원문을 꺼내는 합성 접근자를 늘리지 않는다).
     *
     * 마스킹 문자열은 **값이 아니라 「채워졌는가」**를 말한다 — 그것이 기동 실패를 읽는 데 필요한
     * 전부이고(어느 칸이 비었는가), 원문 복원에는 쓸모없다.
     */
    override fun toString(): String =
        "NotificationChannelProperties(siteBaseUrl=${siteBaseUrl.presence()}, telegram=$telegram)"

    /**
     * 기동 검사의 입력 — **값이 아니라 존재 여부**다. 공백은 「없다」로 센다: 빈 문자열을 자격으로
     * 받으면 그 발송은 반드시 실패하는데 기동은 통과한다(조용히 틀린 상태).
     */
    fun presence(): ChannelConfigPresence =
        ChannelConfigPresence(
            botCredential = !telegram.botCredential.isNullOrBlank(),
            chatId = !telegram.chatId.isNullOrBlank(),
            siteBaseUrl = !siteBaseUrl.isNullOrBlank(),
        )

    /**
     * 설정 묶음 — 자격 셋 가운데 하나라도 비면 `null` 이다(가짜 값을 만들지 않는다). `null` 은
     * 「이 환경은 발송하지 않는다」를 뜻하고, 그 상태로 `Live` 조립이 서지 않는 것은
     * [bidvector.adapters.relay.NotificationRelayRun] 의 `init` 불변식이 지킨다.
     */
    fun channelSettings(): NotificationChannelSettings? {
        val credential = telegram.botCredential?.takeIf { it.isNotBlank() }
        val chatId = telegram.chatId?.takeIf { it.isNotBlank() }
        val site = siteBaseUrl?.takeIf { it.isNotBlank() }
        return if (credential == null || chatId == null || site == null) {
            null
        } else {
            NotificationChannelSettings(
                telegram =
                    TelegramSenderSettings(
                        baseUrl = telegram.baseUrl,
                        botCredential = TelegramBotCredential.of(credential),
                        routeKey = RouteKey(telegram.routeKey),
                        chatId = TelegramChatId.of(chatId),
                        connectTimeout = telegram.connectTimeout,
                        responseTimeout = telegram.responseTimeout,
                    ),
                message = NoticeMessageSettings(site),
            )
        }
    }
}

/**
 * Telegram Bot API 축의 설정.
 *
 * [botCredential] 은 Telegram 이 「bot token」이라 부르는 값이다(runbook `m7-7f-production.md` §13 이 그 이름을 든다) —
 * 이 저장소의 자격 키 어휘(`*_CREDENTIAL`)를 따른다.
 *
 * [baseUrl]·[connectTimeout]·[responseTimeout]·[routeKey] 는 **환경 값**이고 기본이 있다(D-8·D-9).
 * timeout 둘은 유한 양수여야 하고 그 검사는 [TelegramSenderSettings] 의 `init` 이 진다 — 같은
 * 검사를 두 자리에 적지 않는다.
 */
class TelegramChannelProperties(
    val botCredential: String? = null,
    val chatId: String? = null,
    val routeKey: String = DEFAULT_ROUTE_KEY,
    val baseUrl: String = DEFAULT_BASE_URL,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val responseTimeout: Duration = Duration.ofSeconds(DEFAULT_RESPONSE_TIMEOUT_SECONDS),
) {
    /**
     * **`data class` 가 아니다(v H-1)** — 위 [NotificationChannelProperties] 와 같은 이유.
     * 자격·chat id 는 **있는지만** 말하고, 비밀이 아닌 값(route 슬러그·주소·timeout)은 그대로
     * 보인다(그것이 오설정을 읽는 데 필요한 것이고 비밀이 아니다).
     */
    override fun toString(): String =
        "TelegramChannelProperties(" +
            "botCredential=${botCredential.presence()}, " +
            "chatId=${chatId.presence()}, " +
            "routeKey=$routeKey, baseUrl=$baseUrl, " +
            "connectTimeout=$connectTimeout, responseTimeout=$responseTimeout)"
}

/** 「채워졌는가」만 말한다 — 원문도 길이도 담지 않는다(길이도 비밀 값의 단서다). */
private fun String?.presence(): String = if (isNullOrBlank()) "<비어 있음>" else "<채워짐>"

/** 경로 슬러그의 환경 기본값 — 경로가 하나인 배포(`OPEN-STR-07`)에서 운영자가 정할 이유가 없다. */
private const val DEFAULT_ROUTE_KEY = "telegram-primary"

/** Bot API 주소(D-9) — test 는 가짜 서버 주소로 덮어 쓴다. */
private const val DEFAULT_BASE_URL = "https://api.telegram.org"

/** D-8 「기본 예 연결 5s · 응답 10s」. 유한 양수 검사는 `TelegramSenderSettings.init` 이 진다. */
private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 5L
private const val DEFAULT_RESPONSE_TIMEOUT_SECONDS = 10L
