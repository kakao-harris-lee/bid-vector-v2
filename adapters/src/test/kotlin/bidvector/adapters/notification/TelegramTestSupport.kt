package bidvector.adapters.notification

import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ContentRef
import bidvector.workflow.notification.ContentRenderer
import bidvector.workflow.notification.DeliveryMode
import bidvector.workflow.notification.DeliveryOutcome
import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.DispatchNotification
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.NotificationIntent
import bidvector.workflow.notification.RenderedContent
import bidvector.workflow.notification.RouteKey
import bidvector.workflow.notification.RuntimeEnvironment
import bidvector.workflow.strategy.OperatorId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * **가짜 자격이다** — 실 Telegram 호출은 0 이고(D-9) 이 값은 가짜 서버만 받는다. 누출 test 의
 * 바늘(needle)이기도 하므로 다른 어휘와 겹치지 않는 모양으로 둔다.
 */
internal const val FAKE_BOT_CREDENTIAL = "811122233:FAKEbotcredentialZZQ"

/** 가짜 chat id — 끝 네 자리(`3210`)가 마스킹 뒤에도 남는 유일한 조각이다(정책 `maskedSuffixLength=4`). */
internal const val FAKE_CHAT_ID = "-1009876543210"

internal val FAKE_ROUTE = RouteKey("telegram-primary")
internal val FAKE_OWNER = OperatorId("str12-operator")
internal val SENT_AT: Instant = Instant.parse("2026-11-05T02:03:04Z")

/** 공고 상세 링크의 base URL — 예시용 도메인(RFC 2606 `example`), 바깥 질의 0. */
internal const val FAKE_SITE_BASE_URL = "https://bidvector.example"

private const val MASKED_SUFFIX_LENGTH = 4
private const val FAST_TIMEOUT_MILLIS = 300L

/**
 * 응답 timeout 보다 **확실히 길고** 그만큼만 길다 — 가짜 서버의 핸들러는 `HttpServer` 의 기본
 * 실행자(디스패처 스레드)에서 돌므로, 이 시간 동안 그 스레드가 잠든다. 길게 두면 test 가 끝난 뒤에도
 * 스레드가 남는다.
 */
private const val SLOW_SERVER_MILLIS = 1_500L

/** 전 환경을 `Live` 로 덮는다 — 발송 축을 재려면 dispatch 가 억제하지 않아야 한다(`SenderContractTest` 관례). */
internal val LIVE_POLICY =
    NotificationDeliveryPolicyData(
        environmentModes = RuntimeEnvironment.entries.associateWith { DeliveryMode.Live },
        maskedSuffixLength = MASKED_SUFFIX_LENGTH,
    )

internal fun telegramSettings(
    baseUrl: String,
    routeKey: RouteKey = FAKE_ROUTE,
    responseTimeout: Duration = Duration.ofSeconds(10),
): TelegramSenderSettings =
    TelegramSenderSettings(
        baseUrl = baseUrl,
        botCredential = TelegramBotCredential.of(FAKE_BOT_CREDENTIAL),
        routeKey = routeKey,
        chatId = TelegramChatId.of(FAKE_CHAT_ID),
        connectTimeout = Duration.ofSeconds(5),
        responseTimeout = responseTimeout,
    )

/** 응답을 기다리지 않는 설정 — 가짜 서버의 [TelegramScript.Stall] 과 짝이다. */
internal fun impatientSettings(baseUrl: String): TelegramSenderSettings =
    telegramSettings(baseUrl, responseTimeout = Duration.ofMillis(FAST_TIMEOUT_MILLIS))

/**
 * 발송 축 설정 묶음 — 조립 경계([bidvector.adapters.relay.NotificationRelayRun])와 E2E 가 함께
 * 쓴다(같은 묶음을 두 자리에 또 짓지 않는다).
 */
internal fun channelSettings(baseUrl: String): NotificationChannelSettings =
    NotificationChannelSettings(
        telegram = telegramSettings(baseUrl),
        message = NoticeMessageSettings(FAKE_SITE_BASE_URL),
    )

internal fun stallLongerThanTimeout(): TelegramScript = TelegramScript.Stall(SLOW_SERVER_MILLIS)

internal fun telegramSender(settings: TelegramSenderSettings): TelegramNotificationSender =
    TelegramNotificationSender(
        settings = settings,
        httpClient = telegramHttpClient(settings.connectTimeout),
        policy = LIVE_POLICY,
        clock = Clock.fixed(SENT_AT, ZoneOffset.UTC),
    )

/** 렌더러 자리의 고정 문장 — 이 묶음이 재는 것은 발송 축이다(렌더 문면은 자기 test 가 든다). */
internal class FixedContentRenderer(
    private val body: String = "발송 본문",
) : ContentRenderer {
    override fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent = RenderedContent(channel, body)
}

/**
 * [bidvector.workflow.notification.DeliveryRequest] 는 `workflow` 밖에서 지을 수 없다
 * (`internal constructor`) — 그래서 sender 를 **production dispatch 를 지나** 부른다. 덧값:
 * route 획득([ConfiguredRouteDirectory])과 억제 판정까지 같은 경로로 실측된다.
 */
internal fun dispatchThrough(
    sender: TelegramNotificationSender,
    routeKey: RouteKey = FAKE_ROUTE,
    renderer: ContentRenderer = FixedContentRenderer(),
    key: String = "noti-1",
): DeliveryResult {
    val dispatcher =
        DispatchNotification(
            routes = ConfiguredRouteDirectory(FAKE_OWNER, routeKey),
            renderer = renderer,
            sender = sender,
            policyData = LIVE_POLICY,
            environment = RuntimeEnvironment.Production,
        )
    val outcome =
        dispatcher.dispatch(
            NotificationIntent(IdempotencyKey(key), FAKE_OWNER, Channel.Telegram, ContentRef("A-1-000")),
        )
    check(outcome is DeliveryOutcome.Attempted) { "발송이 억제됐다 — 이 묶음은 발송 축을 잰다: $outcome" }
    return outcome.result
}
