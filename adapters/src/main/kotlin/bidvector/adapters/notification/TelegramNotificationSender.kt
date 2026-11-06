package bidvector.adapters.notification

import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.notification.DeliveryRequest
import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.NotificationSender
import bidvector.workflow.notification.RejectionReason
import bidvector.workflow.notification.RenderedContent
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Telegram Bot API `sendMessage` 로 **한 방향** 발송하는 실 sender(`OPEN-STR-12` 발송 축).
 *
 * **멱등(D-5).** 같은 [DeliveryRequest.idempotencyKey] 재호출은 앞 결과를 그대로 돌려주고 네트워크를
 * 다시 때리지 않는다 — `computeIfAbsent` 가 키마다 계산을 한 번만 돌린다. 기억은 **프로세스 안**이고
 * 영속이 아니다: 재기동을 건너는 재발송 창은 ADR 0005 §7.6 의 알려진 B-2 창이고 이 slice 가 넓히지도
 * 좁히지도 않는다(`TelegramSenderContractTest` 가 그 경계를 명시로 잰다).
 *
 * **이 성질이 약속하는 범위는 「한 run 안의 순차 재호출」이다(cr L-1).** 동시 호출을 성질로 내걸지
 * 않는다 — 매핑 함수가 최대 15초(연결 5s + 응답 10s)의 네트워크 호출을 돌고, `ConcurrentHashMap`
 * 계약은 그 동안 같은 bin 의 **무관한 키**까지 막힐 수 있다고 못박는다. 오늘 relay 는 단일 스레드라
 * 실해가 0 이고(`mode=once` 일회 러너), 동시 호출이 필요해지면 계산을 map 밖에서 돌고 `putIfAbsent`
 * 로 들이는 쪽으로 바꾼다. 기억의 **상한도 그 전제에서 온다** — 한 프로세스가 claim 하는 수
 * (`bidvector.relay.claim-limit`)만큼이고 프로세스가 끝나면 사라진다. 상주 조립에 들어가면
 * (이 클래스는 싱글턴 빈에 쥐어진다) 키마다 영구히 쌓이므로 그때 run 당 비움이나 캡이 필요하다.
 *
 * **모르는 결과를 전달로 접지 않는다(D-4).** 모호(timeout·5xx·429·해독 불가·송신 뒤 끊김)는
 * [DeliveryResult.Unknown] 이고 relay 가 그것을 `ISOLATED` 로 보낸다 — 재시도 0, at-most-once.
 *
 * **예외를 밖으로 내지 않는다.** 업무 분기를 예외로 흘리지 않는다는 규율이기도 하고, 누출 방어이기도
 * 하다: 자격이 요청 URI 에 들어가므로 `HttpRequest`·`HttpResponse`·전송 예외가 담을 수 있는 문자열을
 * 메시지로 옮기는 자리를 하나도 두지 않는다. 분류는 예외의 **타입**만 본다(`message` 를 읽지 않는다).
 */
class TelegramNotificationSender(
    private val settings: TelegramSenderSettings,
    private val httpClient: HttpClient,
    private val policy: NotificationDeliveryPolicyData,
    private val clock: Clock,
) : NotificationSender {
    private val remembered = ConcurrentHashMap<IdempotencyKey, DeliveryResult>()

    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult = remembered.computeIfAbsent(request.idempotencyKey) { attemptOnce(request, content) }

    /**
     * 설정된 route 가 아니면 **네트워크 호출 0** 에 대상 거부다 — 「기본 chat 으로 메운다」 경로가
     * 없다(설계 검토 우회 6). 이 거부는 chat 축의 사실이라 `InvalidTarget` 이 맞다.
     */
    private fun attemptOnce(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult {
        if (request.route != settings.routeKey) return DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        return telegramDeliveryResult(call(content), clock.instant(), settings.chatId.masked(policy))
    }

    private fun call(content: RenderedContent): TelegramCallOutcome =
        try {
            val response = httpClient.send(sendMessageRequest(content), HttpResponse.BodyHandlers.ofString())
            TelegramCallOutcome.Responded(response.statusCode(), TelegramResponseBody.verdictOf(response.body()))
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            // 인터럽트 상태를 되살린다 — 삼키면 상위 러너가 취소 신호를 잃는다.
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            telegramFailureOutcome(failure)
        }

    /**
     * **자격이 경로에 실리는 유일한 자리.** 지역 값으로만 쓰이고 필드에 보관되지 않는다. 리다이렉트를
     * 따르지 않는 것은 [telegramHttpClient] 가 진다 — 따라가면 자격이 남의 host 로 간다.
     */
    private fun sendMessageRequest(content: RenderedContent): HttpRequest =
        HttpRequest
            .newBuilder(URI.create("${settings.baseUrl}/bot${settings.botCredential.pathSegment()}/$SEND_MESSAGE"))
            .timeout(settings.responseTimeout)
            .header("Content-Type", FORM_CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(formBody(content), StandardCharsets.UTF_8))
            .build()

    /**
     * 칸은 **둘뿐이다** — `parse_mode` 를 보내지 않는다(D-6): 켜면 공고명의 마크업 문자가 Telegram
     * 파서를 깨거나 다른 뜻이 된다. 이스케이프 규칙을 손으로 짜는 대신 그 축을 아예 쓰지 않는다.
     */
    private fun formBody(content: RenderedContent): String =
        "$CHAT_ID_FIELD=${encoded(settings.chatId.formValue())}&$TEXT_FIELD=${encoded(capped(content.body))}"

    /**
     * Bot API `sendMessage` 의 `text` 상한은 **4096 자**다(cr M-6) — 넘기면 `400` 이고 그것은
     * 「확정 거부」로 분류되어 그 행이 `FAILED` 로 탄다. **내용이 길다는 이유로 알림을 잃지
     * 않는다**: 자르고 **잘렸다는 표지**를 남긴다(조용히 자르면 운영자가 뒷부분을 못 봤다는
     * 사실 자체를 모른다).
     *
     * 세는 단위는 `String.length`(UTF-16 코드 단위)이고 Bot API 가 재는 단위와 같다. 표지도
     * 상한 **안에** 들어가야 하므로 본문은 `상한 − 표지 길이` 까지만 남긴다.
     *
     * **서러게이트 쌍을 가르지 않는다**(v N-5 = cr L-2). 코드 **단위** 경계에서 자르면 BMP 밖
     * 문자(이모지 등)가 그 자리에 걸릴 때 짝 없는 반쪽이 남고, `URLEncoder` 가 그 고아를 `?` 로
     * 바꿔 보낸다(실측: 표지 앞 글자가 `0x3F`). 전달 실패는 아니지만 **운영자가 받는 메시지에
     * 깨진 글자가 남는다.** 자르는 지점 직전이 high surrogate 면 한 단위 더 물러난다 — 그러면
     * 본문이 한 글자 짧아질 뿐이고 상한은 그대로 지켜진다.
     *
     * 이 자리가 renderer 가 아니라 sender 인 이유: 4096 은 **Bot API 의 제약**이다. 렌더러는
     * 채널 무관한 문장을 만들고 채널의 상한은 그 채널 어댑터가 안다.
     */
    private fun capped(body: String): String =
        if (body.length <= MAX_TEXT_LENGTH) {
            body
        } else {
            body.take(wholeCharBoundary(body, MAX_TEXT_LENGTH - TRUNCATION_MARK.length)) + TRUNCATION_MARK
        }

    /** 짝 없는 서러게이트를 남기지 않는 절단 길이 — 직전이 high surrogate 면 한 단위 물러난다. */
    private fun wholeCharBoundary(
        body: String,
        limit: Int,
    ): Int = if (limit in 1..body.length && body[limit - 1].isHighSurrogate()) limit - 1 else limit

    private fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val SEND_MESSAGE = "sendMessage"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"
        const val CHAT_ID_FIELD = "chat_id"
        const val TEXT_FIELD = "text"

        /** Bot API `sendMessage` 의 `text` 상한 — 프로토콜 사실이고 정책 값이 아니다. */
        const val MAX_TEXT_LENGTH = 4096
        const val TRUNCATION_MARK = "\n…(이하 생략 — 알림함에서 확인하십시오)"
    }
}

/**
 * 발송 전용 HTTP 클라이언트 — 연결 timeout 은 설정이고(D-8) **리다이렉트를 따르지 않는다**: 따라가면
 * 자격을 담은 경로가 응답이 지정한 host 로 다시 나간다.
 *
 * **닫는 자리를 두지 않은 것은 일회 러너 전제다(cr L-3).** JDK 21 의 `HttpClient` 는 `AutoCloseable`
 * 이지만 이 클라이언트는 `mode=once` 러너의 생애와 같아 프로세스 종료가 selector·worker 를 거둔다.
 * 상주 조립에 들어가면 **조립 경계가 소유권을 지고 닫아야** 한다 — 그러지 않으면 그 자원이 GC 까지
 * 남는다.
 */
internal fun telegramHttpClient(connectTimeout: Duration): HttpClient =
    HttpClient
        .newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

/**
 * 전송 실패의 분류(D-4) — **예외의 타입만** 본다(`message` 를 읽지 않는다).
 *
 * **원인 사슬을 본다.** JDK 는 같은 사실을 래핑해 올릴 수 있어(일반 `IOException` 안에
 * `ConnectException`) 겉 타입만 보면 「닿지 않았음이 확정」이 「모호」로 접힌다. 그래서 사슬 어디에든
 * 구체 타입이 있으면 그것이 이기고, 없을 때만 일반 `IOException` → 끊김, 그 밖 → 전송 실패다.
 *
 * timeout 은 연결 timeout 이든 응답 timeout 이든 **모호**다 — SYN 이 닿았는지 우리는 모른다.
 */
internal fun telegramFailureOutcome(failure: Throwable): TelegramCallOutcome {
    val chain = causeChainOf(failure)
    chain.firstNotNullOfOrNull(::specificOutcomeOf)?.let { return it }
    return if (chain.any { it is IOException }) {
        TelegramCallOutcome.Ambiguous(TelegramAmbiguityCause.ConnectionBroken)
    } else {
        TelegramCallOutcome.Ambiguous(TelegramAmbiguityCause.TransportFailure)
    }
}

private fun specificOutcomeOf(failure: Throwable): TelegramCallOutcome? =
    when (failure) {
        is HttpTimeoutException -> TelegramCallOutcome.Ambiguous(TelegramAmbiguityCause.Timeout)
        is UnknownHostException -> TelegramCallOutcome.NotSent(TelegramNotSentCause.NameNotResolved)
        is ConnectException -> TelegramCallOutcome.NotSent(TelegramNotSentCause.ConnectionRefused)
        is InterruptedException -> TelegramCallOutcome.Ambiguous(TelegramAmbiguityCause.Interrupted)
        else -> null
    }

private const val MAX_CAUSE_DEPTH = 8

private fun causeChainOf(failure: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = failure
    while (current != null && chain.size < MAX_CAUSE_DEPTH && chain.none { it === current }) {
        chain += current
        current = current.cause
    }
    return chain
}
