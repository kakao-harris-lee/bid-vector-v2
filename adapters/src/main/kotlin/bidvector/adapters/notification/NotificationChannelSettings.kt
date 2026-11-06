package bidvector.adapters.notification

import bidvector.workflow.notification.RouteKey
import java.net.URI
import java.time.Duration

/**
 * Telegram 발송에 필요한 설정 전부(D-2·D-3·D-8·D-9) — **값은 전부 밖에서 온다**(기본 인자 0).
 * 자격·chat id 가 마스킹 값 타입이라 이 `data class` 의 합성 `toString` 도 원문을 담지 않는다.
 *
 * 경로는 **하나**다(`OPEN-STR-07` 단일 회사·단일 운영자). 그래서 `RouteKey -> chat id` 를 map 으로
 * 두지 않는다 — 두면 「슬러그는 있고 chat id 는 없는」 어긋난 상태가 표현 가능해진다. [routeKey] 와
 * 다른 route 로 들어온 요청은 **호출 없이** 대상 거부다.
 */
data class TelegramSenderSettings(
    val baseUrl: String,
    val botCredential: TelegramBotCredential,
    val routeKey: RouteKey,
    val chatId: TelegramChatId,
    val connectTimeout: Duration,
    val responseTimeout: Duration,
) {
    init {
        require(BASE_URL_SHAPE.matches(baseUrl)) {
            "Telegram base URL 은 질의·fragment·userinfo 없는 http(s) 주소여야 한다 — 원문은 담지 않는다"
        }
        require(!baseUrl.endsWith("/")) { "Telegram base URL 은 `/` 로 끝나지 않는다(경로를 두 번 잇지 않는다)" }
        require(uriAccepts(baseUrl)) {
            "Telegram base URL 이 URI 로 해독되지 않는다 — 원문은 담지 않는다(runbook §13.6)"
        }
        require(connectTimeout.isPositive()) { "연결 timeout 은 유한 양수여야 한다: $connectTimeout" }
        require(responseTimeout.isPositive()) { "응답 timeout 은 유한 양수여야 한다: $responseTimeout" }
    }

    private companion object {
        /**
         * **조용한 오설정을 막는 선행 검사**다 — 유일한 URI 조립 자리가 자격을 경로에 싣기 때문에,
         * 그 앞에 `@`(userinfo)·`?`·`#` 가 섞인 주소를 받으면 자격이 질의 문자열로 흘러갈 수 있다.
         * 방어의 본체는 「URI 를 짓는 자리가 하나」라는 구조이고 이것은 그 위의 얕은 층이다.
         */
        val BASE_URL_SHAPE = Regex("""^https?://[^\s@?#]+$""")

        const val MAX_PORT = 65535

        /**
         * **URI 가 받는지는 URI 만 안다**(v N-1 = cr M-2). 위 정규식은 **문자열 술어**라
         * `java.net.URI` 가 거부하는 문자(`| \ " { } ^ < > ` ` 와 16진 두 자리가 따르지 않는 `%`)를
         * 통과시킨다. 그 주소를 받으면 기동은 초록이고, **첫 발송에서** `URI.create` 가 던지고, 그
         * 예외가 발송 경로의 포괄 `catch` 에 걸려 **`Unknown`(모호)** 으로 기록된다 — 집은 행이 전부
         * `ISOLATED` 로 탄다(at-most-once 라 재시도도 0). 자격 축을 모양으로 막은 것과 **같은
         * 처분**(기동 실패)으로 정렬한다.
         *
         * 정규식을 늘리는 쪽은 같은 함정을 한 칸 뒤로 미루는 것뿐이라 쓰지 않았다 — 술어를
         * **문자열이 아니라 구조로** 닫는다.
         *
         * `URI` 왕복만으로는 둘이 빠져나간다(실측): `…:abc` 는 **받아들여지지만** authority 가
         * registry 기반이 되어 `host == null` 이고, `…:99999` 는 포트에 16비트 검사가 없다. 둘 다
         * 발송 때 요청 조립·연결에서 깨져 같은 `ISOLATED` 처분으로 흘러가므로 함께 막는다.
         */
        fun uriAccepts(raw: String): Boolean {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return false
            return uri.host != null && uri.port <= MAX_PORT
        }
    }
}

/** 알림 본문 설정 — 공고 상세 링크의 도메인(D-6). 채널 무관이라 Telegram 설정과 다른 값이다. */
data class NoticeMessageSettings(
    val siteBaseUrl: String,
) {
    init {
        require(SITE_SHAPE.matches(siteBaseUrl)) {
            "공고 상세 링크의 base URL 은 질의·fragment·userinfo 없는 http(s) 주소여야 한다 — 원문은 담지 않는다"
        }
        require(!siteBaseUrl.endsWith("/")) { "base URL 은 `/` 로 끝나지 않는다(경로를 두 번 잇지 않는다)" }
    }

    private companion object {
        val SITE_SHAPE = Regex("""^https?://[^\s@?#]+$""")
    }
}

/** 발송 축 설정 묶음 — 조립 경계([bidvector.adapters.relay.NotificationRelayRun])가 이 하나를 받는다. */
data class NotificationChannelSettings(
    val telegram: TelegramSenderSettings,
    val message: NoticeMessageSettings,
)
