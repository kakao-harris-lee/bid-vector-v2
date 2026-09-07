package bidvector.adapters.extraction

import bidvector.procurement.AttachmentFetchFailure
import bidvector.procurement.AttachmentFetchLimits
import bidvector.procurement.FetchOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private fun source() =
    HttpAttachmentDocumentSource(HttpClient.newHttpClient(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

/** ① — 크기 상한·timeout·전송 실패가 각각 관측된다(procurement `AttachmentFetchFailure`). */
class HttpAttachmentDocumentSourceTest {
    @Test
    fun `정상 응답은 sha256 과 함께 취득된다`() {
        val body = "spec".toByteArray()
        FakeAttachmentServer.start(status = 200, body = body, contentType = "text/plain").use { server ->
            val url = sampleAttachmentUrl(server.uri.toString())

            val outcome = source().fetch(url, AttachmentFetchLimits(1024, Duration.ofSeconds(2)))

            outcome.shouldBeInstanceOf<FetchOutcome.Fetched>()
            outcome.document.bytes.contentEquals(body) shouldBe true
        }
    }

    @Test
    fun `크기 상한을 넘으면 TooLarge 다`() {
        FakeAttachmentServer.start(status = 200, body = ByteArray(100)).use { server ->
            val url = sampleAttachmentUrl(server.uri.toString())

            val outcome = source().fetch(url, AttachmentFetchLimits(10, Duration.ofSeconds(2)))

            outcome.shouldBeInstanceOf<FetchOutcome.Failed>()
            outcome.reason shouldBe AttachmentFetchFailure.TooLarge
        }
    }

    @Test
    fun `느린 응답은 Timeout 이다`() {
        FakeAttachmentServer.start(status = 200, body = ByteArray(1), delayMillis = 500).use { server ->
            val url = sampleAttachmentUrl(server.uri.toString())

            val outcome = source().fetch(url, AttachmentFetchLimits(1024, Duration.ofMillis(100)))

            outcome.shouldBeInstanceOf<FetchOutcome.Failed>()
            outcome.reason shouldBe AttachmentFetchFailure.Timeout
        }
    }

    @Test
    fun `HTTP 오류 상태는 TransportFailed 다`() {
        FakeAttachmentServer.start(status = 404, body = ByteArray(0)).use { server ->
            val url = sampleAttachmentUrl(server.uri.toString())

            val outcome = source().fetch(url, AttachmentFetchLimits(1024, Duration.ofSeconds(2)))

            outcome.shouldBeInstanceOf<FetchOutcome.Failed>()
            outcome.reason.shouldBeInstanceOf<AttachmentFetchFailure.TransportFailed>()
        }
    }
}
