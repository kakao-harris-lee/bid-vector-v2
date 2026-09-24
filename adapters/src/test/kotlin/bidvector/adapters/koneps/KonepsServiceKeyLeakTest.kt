package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * D-6F8-4(M6/6F-8) — 서비스 키는 요청 URI 에 실려 나간다. 전송이 어떤 모양으로 실패하든 그 키가 결과·예외
 * 메시지·로그·표준 출력 어디에도 남지 않는다(원문도 URL 인코딩 형태도). 「서버가 요청 URL 을 응답 본문에
 * 그대로 되돌리는」 경우까지 표본에 넣는다 — 응답 본문이 어디로든 새면 키가 새기 때문이다.
 */
class KonepsServiceKeyLeakTest {
    private val rawKey = "LEAK+SENTINEL/key=value"
    private val encodedKey = URLEncoder.encode(rawKey, StandardCharsets.UTF_8)
    private val referenceDate = CollectionReferenceDate(LocalDate.of(2026, 9, 7))
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC)

    private fun leaks(text: String): Boolean = rawKey in text || encodedKey in text

    /** 소스 하나를 돌리고 결과 문자열·로그·표준 출력 전부를 한 덩이로 모은다. */
    private fun surfacesOf(baseUri: URI): String {
        val logs = StringBuilder()
        val root = Logger.getLogger("")
        val handler =
            object : Handler() {
                override fun publish(record: LogRecord) {
                    logs.append(record.message).append(record.parameters?.joinToString().orEmpty())
                    record.thrown?.let { logs.append(it.toString()) }
                }

                override fun flush() = Unit

                override fun close() = Unit
            }
        val captured = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        root.addHandler(handler)
        System.setOut(PrintStream(captured, true, StandardCharsets.UTF_8))
        System.setErr(PrintStream(captured, true, StandardCharsets.UTF_8))
        try {
            val source =
                KonepsOpenApiNoticeSource(
                    httpClient = HttpClient.newHttpClient(),
                    baseUri = baseUri,
                    serviceKey = ServiceKey.of(rawKey),
                    httpPolicy = testKonepsHttpPolicy(maxAttempts = 2, requestTimeout = Duration.ofMillis(100)),
                    collectionPolicyProvider = ::resolvedCollectionPolicy,
                    clock = fixedClock,
                )
            val batch = source.fetchNotices(referenceDate, null)
            return "$batch|${batch.accounting}|${batch.next}|$logs|${captured.toString(StandardCharsets.UTF_8)}"
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            root.removeHandler(handler)
        }
    }

    private fun echoBody(prefix: String) = "$prefix serviceKey=$rawKey serviceKey=$encodedKey"

    @Test
    fun `연결이 거부돼도 키는 결과·로그·출력 어디에도 없다`() {
        val closedBase = MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, "{}"))).use { it.baseUri }

        leaks(surfacesOf(closedBase)) shouldBe false
    }

    @Test
    fun `서버가 요청 URL 을 응답 본문으로 되돌려도 키는 새지 않는다 — 오류 상태·쿼터·깨진 본문`() {
        listOf(
            MockKonepsResponse.Reply(500, echoBody("Internal Server Error")),
            MockKonepsResponse.Reply(429, echoBody("Too Many Requests")),
            MockKonepsResponse.Reply(400, echoBody("Bad Request")),
            MockKonepsResponse.Reply(200, echoBody("not-json")),
            MockKonepsResponse.Reply(
                200,
                "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"${echoBody("등록되지 않은 서비스 키")}\"}}}",
            ),
        ).forEach { reply ->
            MockKonepsServer.start(listOf(reply)).use { server ->
                leaks(surfacesOf(server.baseUri)) shouldBe false
            }
        }
    }

    @Test
    fun `응답이 지연돼 시간 초과가 나도 키는 새지 않는다`() {
        val slow = MockKonepsResponse.DelayThenReply(delayMillis = 400, status = 200, body = echoBody("slow"))
        MockKonepsServer.start(listOf(slow)).use { server ->
            leaks(surfacesOf(server.baseUri)) shouldBe false
        }
    }

    @Test
    fun `전송 계층의 실패 메시지에도 키가 없다 — 키를 담은 요청 URI 로 연결 실패`() {
        val closedBase = MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, "{}"))).use { it.baseUri }
        val uri = URI.create("$closedBase?serviceKey=$encodedKey&pageNo=1")

        val outcome = sendKonepsRequest(HttpClient.newHttpClient(), uri, Duration.ofMillis(200))

        outcome.toString() shouldNotContain rawKey
        outcome.toString() shouldNotContain encodedKey
    }

    @Test
    fun `전송 실패의 사유는 예외 클래스 이름뿐이다 — 예외 메시지가 결과로 나가는 채널이 없다`() {
        val closedBase = MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, "{}"))).use { it.baseUri }
        val uri = URI.create("$closedBase?serviceKey=$encodedKey&pageNo=1")

        val outcome = sendKonepsRequest(HttpClient.newHttpClient(), uri, Duration.ofMillis(200))

        outcome shouldBe KonepsTransportOutcome.TransportFailed(exceptionType = "ConnectException")
        describeTransport(outcome) shouldBe "ConnectException"
    }

    @Test
    fun `깨진 본문의 구조 실패 사유에는 응답 본문 조각이 실리지 않는다 — 서버가 요청 URL 을 되돌려도`() {
        val outcome =
            parseKonepsEnvelope(
                echoBody("not-json"),
                resolvedCollectionPolicy(referenceDate),
                testKonepsHttpPolicy().maxJsonDepth,
            )

        val reason = outcome.shouldBeInstanceOf<KonepsEnvelopeOutcome.StructureFailure>().reason
        reason shouldNotContain "serviceKey"
        reason shouldNotContain "not-json"
        leaks(reason) shouldBe false
    }

    @Test
    fun `표본이 실제로 키를 요청에 실어 보낸다 — 부재 단언이 공허하지 않다`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(500, "boom"))).use { server ->
            surfacesOf(server.baseUri)

            server.lastRequestQuery.orEmpty() shouldNotContain "serviceKey=$rawKey"
            server.lastRequestQuery.orEmpty().contains("serviceKey=$encodedKey") shouldBe true
        }
    }
}
