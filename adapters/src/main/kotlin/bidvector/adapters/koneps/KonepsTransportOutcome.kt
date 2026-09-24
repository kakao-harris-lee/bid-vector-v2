package bidvector.adapters.koneps

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** HTTP 전송 결과(①) — 예외를 값으로 접는다(경계 안에서 끝난다, port 밖은 값만 나간다). */
internal sealed interface KonepsTransportOutcome {
    data class Received(
        val status: Int,
        val body: String,
    ) : KonepsTransportOutcome

    data object TimedOut : KonepsTransportOutcome

    /**
     * 예외 **클래스 이름만** 싣는다 — JDK 전송 예외의 메시지에는 요청 URI(서비스 키가 쿼리에 실린다)가 실릴 수
     * 있어 메시지를 결과 채널로 내보내지 않는다(D-6F8-4, `HttpAttachmentDocumentSource` 와 같은 접기).
     */
    data class TransportFailed(
        val exceptionType: String,
    ) : KonepsTransportOutcome
}

/**
 * KONEPS HTTP 호출 — JDK `HttpClient.sendAsync` + `CompletableFuture.get(timeout)`(①,
 * D-M3-1, 착수 시 계약 정정 ①). `HttpRequest.timeout()`(요청 자체 시한)과 `future.get(timeout)`
 * (호출부 시한) 둘 다 건다 — 타임아웃 시 `future.cancel(true)`로 취소를 실제로 전파한다.
 * timeout·전송 실패가 이 함수 안에서 끝나고 [KonepsTransportOutcome] 값으로만 나간다
 * (port 밖으로 예외가 넘어가지 않는다).
 */
internal fun sendKonepsRequest(
    httpClient: HttpClient,
    uri: URI,
    timeout: Duration,
): KonepsTransportOutcome {
    val request =
        HttpRequest
            .newBuilder(uri)
            .GET()
            .timeout(timeout)
            .build()
    val future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    return try {
        val response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        KonepsTransportOutcome.Received(response.statusCode(), response.body())
    } catch (
        @Suppress("SwallowedException")
        timedOut: TimeoutException,
    ) {
        // `TimedOut`은 의도적으로 detail 이 없다 — `future.get(timeout)` 자신의 시한 초과는
        // 원인이 「이 함수가 스스로 정한 시한」 하나뿐이라 예외 종류가 그 이상의 진단
        // 정보를 주지 않는다(HttpTimeoutException 계열과 달리 서버·네트워크 원인 구분이
        // 없다) — ExecutionException·InterruptedException 분기는 원인이 다양해 예외
        // 클래스 이름을 보존한다.
        future.cancel(true)
        KonepsTransportOutcome.TimedOut
    } catch (failed: ExecutionException) {
        toOutcome(failed)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        future.cancel(true)
        KonepsTransportOutcome.TransportFailed(interrupted.javaClass.simpleName)
    }
}

private fun toOutcome(failed: ExecutionException): KonepsTransportOutcome {
    val cause = failed.cause
    return if (cause is HttpTimeoutException) {
        KonepsTransportOutcome.TimedOut
    } else {
        KonepsTransportOutcome.TransportFailed((cause ?: failed).javaClass.simpleName)
    }
}
