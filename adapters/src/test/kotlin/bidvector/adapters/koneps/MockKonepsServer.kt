package bidvector.adapters.koneps

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** 서버가 낼 응답 하나 — 즉시 응답 또는 「지연 뒤 응답」(timeout 시나리오 전용). */
internal sealed interface MockKonepsResponse {
    data class Reply(
        val status: Int,
        val body: String,
    ) : MockKonepsResponse

    data class DelayThenReply(
        val delayMillis: Long,
        val status: Int,
        val body: String,
    ) : MockKonepsResponse
}

/**
 * KONEPS mock server(⑦, D-3B-4) — JDK `com.sun.net.httpserver.HttpServer` loopback
 * in-process(네트워크 0, 소켓은 127.0.0.1 뿐). 요청마다 `script` 의 다음 응답을 순서대로
 * 낸다(소진되면 마지막 것을 반복 — 재시도 시나리오가 스크립트보다 더 부르는 경우를 방어).
 * [requestCount]가 호출 횟수를 서버 스스로 세는 정본이다(재시도 상한·이중화 없음의 단언
 * 근거, scope.md ②).
 */
internal class MockKonepsServer private constructor(
    private val server: HttpServer,
    private val script: List<MockKonepsResponse>,
    private val executor: ExecutorService,
) : AutoCloseable {
    private val counter = AtomicInteger(0)

    val requestCount: Int get() = counter.get()
    val baseUri: URI get() = URI.create("http://127.0.0.1:${server.address.port}/getBidPblancListInfoServc")

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun respond(exchange: HttpExchange) {
        val index = counter.getAndIncrement()
        val response = script.getOrElse(index) { script.last() }
        when (response) {
            is MockKonepsResponse.Reply -> {
                writeReply(exchange, response.status, response.body)
            }

            is MockKonepsResponse.DelayThenReply -> {
                Thread.sleep(response.delayMillis)
                writeReply(exchange, response.status, response.body)
            }
        }
    }

    private fun writeReply(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        /**
         * timeout 시나리오는 응답 하나를 일부러 지연시킨다 — 기본(단일 스레드) executor 라면
         * 그 지연이 서버 자체를 막아 **재시도 요청조차 dispatch 되지 못한다**(실측: retry
         * 2회를 걸어도 서버 카운터가 1에서 멈춤). 각 요청을 별 스레드로 받는 executor 를
         * 명시해 지연 중인 첫 요청과 무관하게 재시도 요청이 즉시 도착하게 한다.
         */
        fun start(script: List<MockKonepsResponse>): MockKonepsServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val executor = Executors.newCachedThreadPool()
            val instance = MockKonepsServer(server, script, executor)
            server.createContext("/getBidPblancListInfoServc", HttpHandler { exchange -> instance.respond(exchange) })
            server.executor = executor
            server.start()
            return instance
        }
    }
}
