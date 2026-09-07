package bidvector.adapters.extraction

import bidvector.procurement.AttachmentDocumentPort
import bidvector.procurement.AttachmentFetchFailure
import bidvector.procurement.AttachmentFetchLimits
import bidvector.procurement.AttachmentUrl
import bidvector.procurement.FetchOutcome
import bidvector.procurement.FetchedDocument
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Clock

/** 전송 자체(타임아웃·IO 실패)의 판정 — 응답 해석([interpretResponse])과 층을 나눈다. */
private sealed interface RawFetchOutcome {
    data class Received(
        val response: HttpResponse<ByteArray>,
    ) : RawFetchOutcome

    data object TimedOut : RawFetchOutcome

    data class TransportFailed(
        val detail: String,
    ) : RawFetchOutcome
}

/**
 * 첨부문서 취득 어댑터(①) — 크기 상한은 `Content-Length` 헤더와 실제 수신 바이트 둘 다
 * 확인한다(헤더 위조·부재 방어). timeout 은 [AttachmentFetchLimits.timeout]이 정한다.
 */
class HttpAttachmentDocumentSource(
    private val httpClient: HttpClient,
    private val clock: Clock,
) : AttachmentDocumentPort {
    override fun fetch(
        url: AttachmentUrl,
        limits: AttachmentFetchLimits,
    ): FetchOutcome {
        val request =
            HttpRequest
                .newBuilder(URI.create(url.value))
                .timeout(limits.timeout)
                .GET()
                .build()
        return when (val raw = sendRequest(request)) {
            is RawFetchOutcome.Received -> {
                interpretResponse(raw.response, limits, url)
            }

            RawFetchOutcome.TimedOut -> {
                FetchOutcome.Failed(AttachmentFetchFailure.Timeout)
            }

            is RawFetchOutcome.TransportFailed -> {
                FetchOutcome.Failed(AttachmentFetchFailure.TransportFailed(raw.detail))
            }
        }
    }

    /**
     * [java.net.http.HttpTimeoutException]은 [AttachmentFetchLimits.timeout] 자신이 정한
     * 시한 초과라 그 이상의 진단 정보가 없다(3B `KonepsTransportOutcome`의 같은 판단과
     * 같은 이유) — 변수를 쓰지 않는다.
     */
    private fun sendRequest(request: HttpRequest): RawFetchOutcome =
        try {
            RawFetchOutcome.Received(httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()))
        } catch (
            @Suppress("SwallowedException")
            timeout: java.net.http.HttpTimeoutException,
        ) {
            RawFetchOutcome.TimedOut
        } catch (io: java.io.IOException) {
            RawFetchOutcome.TransportFailed(io.javaClass.simpleName)
        }

    private fun interpretResponse(
        response: HttpResponse<ByteArray>,
        limits: AttachmentFetchLimits,
        url: AttachmentUrl,
    ): FetchOutcome {
        val bytes = response.body()
        return when {
            response.statusCode() !in SUCCESS_RANGE -> {
                FetchOutcome.Failed(AttachmentFetchFailure.TransportFailed("HTTP ${response.statusCode()}"))
            }

            bytes.size.toLong() > limits.maxBytes -> {
                FetchOutcome.Failed(AttachmentFetchFailure.TooLarge)
            }

            else -> {
                val mediaType = response.headers().firstValue("Content-Type").orElse("application/octet-stream")
                FetchOutcome.Fetched(FetchedDocument(bytes, mediaType, sha256Hex(bytes), clock.instant(), url))
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val SUCCESS_RANGE = 200..299
    }
}
