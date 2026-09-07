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

    data class TransportFailed(
        val message: String,
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
        // 원인이 「이 함수가 스스로 정한 시한」 하나뿐이라 예외 메시지가 그 이상의 진단
        // 정보를 주지 않는다(HttpTimeoutException 계열과 달리 서버·네트워크 원인 구분이
        // 없다) — ExecutionException·InterruptedException 분기는 원인이 다양해 message 를
        // 보존한다.
        future.cancel(true)
        KonepsTransportOutcome.TimedOut
    } catch (failed: ExecutionException) {
        toOutcome(failed)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        future.cancel(true)
        KonepsTransportOutcome.TransportFailed("호출이 중단됐다: ${interrupted.message}")
    }
}

private fun toOutcome(failed: ExecutionException): KonepsTransportOutcome {
    val cause = failed.cause
    return if (cause is HttpTimeoutException) {
        KonepsTransportOutcome.TimedOut
    } else {
        KonepsTransportOutcome.TransportFailed(cause?.message ?: failed.message ?: "전송 실패")
    }
}
