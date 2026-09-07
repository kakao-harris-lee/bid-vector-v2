package bidvector.adapters.extraction

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * loopback in-process 첨부문서 서버 — 3B `MockKonepsServer`·이 slice `FakeLlmServer`와
 * 같은 형태(3B 관례 재사용, CPD 는 test source set 관찰 전용이라 실패로 세지 않는다,
 * `duplicate-policy.properties` `fail.source-sets=main`).
 */
class FakeAttachmentServer private constructor(
    private val server: HttpServer,
    private val status: Int,
    private val body: ByteArray,
    private val contentType: String,
    private val delayMillis: Long,
    private val executor: ExecutorService,
) : AutoCloseable {
    val uri: URI get() = URI.create("http://127.0.0.1:${server.address.port}/spec.pdf")

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun respond(exchange: HttpExchange) {
        if (delayMillis > 0) Thread.sleep(delayMillis)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    companion object {
        fun start(
            status: Int = 200,
            body: ByteArray = ByteArray(0),
            contentType: String = "application/octet-stream",
            delayMillis: Long = 0,
        ): FakeAttachmentServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val executor = Executors.newCachedThreadPool()
            val instance = FakeAttachmentServer(server, status, body, contentType, delayMillis, executor)
            server.createContext("/spec.pdf", HttpHandler { exchange -> instance.respond(exchange) })
            server.executor = executor
            server.start()
            return instance
        }
    }
}
