package bidvector.adapters.extraction

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** 스크립트 응답 하나(⑧) — 3B `MockKonepsServer`(`bidvector.adapters.koneps`)와 같은 형태. */
sealed interface FakeLlmResponse {
    data class Reply(
        val status: Int,
        val body: String,
    ) : FakeLlmResponse

    data class DelayThenReply(
        val delayMillis: Long,
        val status: Int,
        val body: String,
    ) : FakeLlmResponse
}

/**
 * loopback in-process LLM provider mock(⑧, S-2~S-4) — 네트워크 0, 소켓은 127.0.0.1 뿐.
 * [requestCount]가 호출 횟수를 서버 스스로 세는 정본이다(S-3 「호출 0회/1회」 실행 증거).
 * 3B `MockKonepsServer`와 형태가 같지만 파일을 공유하지 않는다(패키지 경계, CPD 주의는
 * evidence 에 기록).
 */
class FakeLlmServer private constructor(
    private val server: HttpServer,
    private val script: List<FakeLlmResponse>,
    private val executor: ExecutorService,
) : AutoCloseable {
    private val counter = AtomicInteger(0)

    val requestCount: Int get() = counter.get()
    val endpointUri: URI get() = URI.create("http://127.0.0.1:${server.address.port}/v1/complete")

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun respond(exchange: HttpExchange) {
        val index = counter.getAndIncrement()
        when (val response = script.getOrElse(index) { script.last() }) {
            is FakeLlmResponse.Reply -> {
                writeReply(exchange, response.status, response.body)
            }

            is FakeLlmResponse.DelayThenReply -> {
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
        fun start(script: List<FakeLlmResponse>): FakeLlmServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val executor = Executors.newCachedThreadPool()
            val instance = FakeLlmServer(server, script, executor)
            server.createContext("/v1/complete", HttpHandler { exchange -> instance.respond(exchange) })
            server.executor = executor
            server.start()
            return instance
        }
    }
}
