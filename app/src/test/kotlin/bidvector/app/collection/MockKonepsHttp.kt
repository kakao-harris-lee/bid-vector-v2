package bidvector.app.collection

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * KONEPS 공고 목록 mock(D-6F8-3 E2E) — loopback in-process(네트워크 0). 경로의 마지막 조각이 오퍼레이션
 * 이름이고 질의 `inqryBgnDt` 앞 여덟 자리가 조회일이다. [itemsFor] 가 (오퍼레이션, 조회일)마다 항목을 낸다.
 * 받은 요청의 원시 질의를 [queries] 에 남겨 키가 실제로 실려 나갔는지 잴 수 있다.
 */
internal class MockKonepsHttp(
    private val itemsFor: (operation: String, day: String) -> List<Map<String, String>>,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val queries = CopyOnWriteArrayList<String>()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/mock"

    init {
        server.createContext("/mock") { exchange -> respond(exchange) }
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    private fun respond(exchange: HttpExchange) {
        val query = exchange.requestURI.rawQuery.orEmpty()
        queries += query
        val operation = exchange.requestURI.path.substringAfterLast('/')
        val day = query.substringAfter("inqryBgnDt=").take(DAY_DIGITS)
        val items = itemsFor(operation, day)
        val bytes = envelope(items).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun envelope(items: List<Map<String, String>>): String {
        val itemsJson = items.joinToString(",", "[", "]") { fields -> objectJson(fields) }
        val body = "\"items\":$itemsJson,\"numOfRows\":100,\"pageNo\":1,\"totalCount\":${items.size}"
        return """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{$body}}}"""
    }

    private fun objectJson(fields: Map<String, String>): String =
        fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":\"${value.replace("\"", "\\\"")}\"" }

    override fun close() {
        server.stop(0)
        (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    private companion object {
        /** `yyyyMMdd` 여덟 자리. */
        const val DAY_DIGITS = 8
    }
}
