package bidvector.adapters.notification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 가짜 Telegram Bot API(D-9 — 실제 호출 0). JDK `com.sun.net.httpserver` 하나로 짓는다(새 의존성 0).
 *
 * **수신을 기록한다** — 발송 횟수·경로·form 칸을 test 가 셀 수 있어야 멱등 계약(D-5)과 「`parse_mode`
 * 를 보내지 않는다」(설계 검토 우회 8)를 거동으로 잴 수 있다. 「보냈다」를 반환값으로만 보면 그 둘은
 * 측정되지 않는다.
 */
internal class FakeTelegramServer private constructor(
    private val server: HttpServer,
    private val recorded: CopyOnWriteArrayList<TelegramCallRecord>,
) : AutoCloseable {
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun received(): List<TelegramCallRecord> = recorded.toList()

    override fun close() = server.stop(0)

    companion object {
        private const val BACKLOG = 0
        private const val STOP_DELAY_SECONDS = 0

        /**
         * [script] 의 항목을 **순서대로** 하나씩 쓰고, 다 쓰면 마지막 항목을 되쓴다 — 한 항목만 준
         * script 가 「모든 호출에 같은 응답」을 뜻한다(가장 자주 쓰는 모양).
         */
        fun start(script: List<TelegramScript>): FakeTelegramServer {
            require(script.isNotEmpty()) { "script 가 비어 있으면 서버가 무엇을 낼지 정해지지 않는다" }
            val recorded = CopyOnWriteArrayList<TelegramCallRecord>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), BACKLOG)
            server.createContext("/") { exchange ->
                val index = recorded.size
                recorded += recordOf(exchange)
                respond(exchange, script[minOf(index, script.lastIndex)])
            }
            server.start()
            return FakeTelegramServer(server, recorded)
        }

        /**
         * 포트를 잡아 띄운 뒤 **닫는다** — 그 포트로의 연결은 거부된다(D-4 「송신 전 실패」 갈래).
         *
         * `start()` 없이 `create()` → `stop()` 하면 **리스너 소켓이 그대로 바인딩돼 있어** 연결이
         * 거부되지 않고 backlog 에 쌓인 채 응답 timeout 으로 흘러간다(실측 — 그 판에서 이 갈래가
         * `Rejected` 가 아니라 `Unknown` 으로 왔다). 그래서 띄운 뒤 멈춘다.
         */
        fun closedPortBaseUrl(): String {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), BACKLOG)
            val url = "http://127.0.0.1:${server.address.port}"
            server.start()
            server.stop(STOP_DELAY_SECONDS)
            return url
        }

        private fun recordOf(exchange: HttpExchange): TelegramCallRecord =
            TelegramCallRecord(
                path = exchange.requestURI.path,
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                form = formOf(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)),
            )

        private fun formOf(body: String): Map<String, String> =
            body
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { pair ->
                    val name = pair.substringBefore('=')
                    val value = pair.substringAfter('=', missingDelimiterValue = "")
                    decode(name) to decode(value)
                }

        private fun decode(raw: String): String = URLDecoder.decode(raw, StandardCharsets.UTF_8)

        private fun respond(
            exchange: HttpExchange,
            step: TelegramScript,
        ) {
            exchange.use {
                when (step) {
                    is TelegramScript.Reply -> reply(exchange, step.status, step.body)
                    is TelegramScript.Stall -> {
                        Thread.sleep(step.millis)
                        reply(exchange, TelegramScript.OK_STATUS, """{"ok":true}""")
                    }

                    TelegramScript.Truncate -> {
                        // 본문 길이를 약속하고 그만큼 쓰지 않는다 — 클라이언트는 **송신 뒤** 끊김을 본다.
                        exchange.sendResponseHeaders(TelegramScript.OK_STATUS, TelegramScript.PROMISED_BYTES)
                    }
                }
            }
        }

        private fun reply(
            exchange: HttpExchange,
            status: Int,
            body: String,
        ) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }
    }
}

/** 가짜 서버가 받은 호출 하나 — 경로·Content-Type·form 칸. 토큰은 [path] 에 들어 있다(test 가 그것을 쓴다). */
internal data class TelegramCallRecord(
    val path: String,
    val contentType: String?,
    val form: Map<String, String>,
)

/** 가짜 서버의 한 걸음. */
internal sealed interface TelegramScript {
    data class Reply(
        val status: Int,
        val body: String,
    ) : TelegramScript

    /** 응답을 [millis] 동안 붙든다 — 응답 timeout 갈래. */
    data class Stall(
        val millis: Long,
    ) : TelegramScript

    /** 약속한 본문을 보내지 않고 끊는다 — 「송신 뒤 연결 끊김」 갈래. */
    data object Truncate : TelegramScript

    companion object {
        const val OK_STATUS = 200
        const val PROMISED_BYTES = 64L

        fun ok(): Reply = Reply(OK_STATUS, """{"ok":true,"result":{"message_id":1}}""")
    }
}
