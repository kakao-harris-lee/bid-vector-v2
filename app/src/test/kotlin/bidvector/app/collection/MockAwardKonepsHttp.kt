package bidvector.app.collection

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 낙찰 목록 KONEPS mock(M9/award-ingest) — loopback in-process(네트워크 0 · 실 조달청 호출 0).
 *
 * 개찰 축 mock([MockOpeningKonepsHttp])과 **따로 둔다**: 그쪽은 표본틀·상세 넷·투찰 행을 짓는 판이고
 * 이쪽은 목록 한 오퍼레이션뿐이다. 한 벌로 묶으면 낙찰 축 test 가 그 판의 분기(쪽 크기·2쪽 실패·
 * 참가 규모)를 함께 지나게 되고, 그 어긋남은 조용하다.
 *
 * **계약 미등재 식별자를 일부러 싣는다**(`bidwinnrBizno`·`bidwinnrCeoNm`) — 실 응답이 그렇기 때문이고,
 * 어댑터 경계의 allow-list 반전이 그 둘을 떨어뜨리는지는 그것을 보내 봐야만 측정이 된다.
 */
internal class MockAwardKonepsHttp(
    private val noticesPerSlot: Int,
    private val nonce: String,
    private val winnerName: String,
    /** 0 이면 한 쪽에 전부 — 양수면 그 수로 쪽을 나눈다(여러 쪽 걷기를 만드는 자리). */
    private val pageSize: Int = 0,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val received = AtomicInteger()

    /** 받은 낙찰 목록 호출의 오퍼레이션 이름 — 업무마다 몇 번 불렸는가. */
    val listCalls = CopyOnWriteArrayList<String>()

    /** 요청 URI 의 `inqryDiv` 전수 — 배선이 선언한 조회 축이 wire 에 닿았는가(응답 에코가 아니다). */
    val requestedInquiryDivisions = CopyOnWriteArrayList<String>()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/mock"

    fun requestCount(): Int = received.get()

    init {
        server.createContext("/mock") { exchange -> respond(exchange) }
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    private fun respond(exchange: HttpExchange) {
        received.incrementAndGet()
        val query = exchange.requestURI.rawQuery.orEmpty()
        val operation = exchange.requestURI.path.substringAfterLast('/')
        require(operation.startsWith(AWARD_LIST_OPERATION_PREFIX)) { "낙찰 목록 외의 오퍼레이션: $operation" }
        // 기간 조건 없이 목록을 걷는 요청은 창 전체를 받는다 — 그 요청이 나가면 mock 이 거부한다.
        require(query.contains("inqryBgnDt=") && query.contains("inqryEndDt=")) { "낙찰 목록은 기간창으로 걷는다" }
        paramOf(query, "inqryDiv")?.let { requestedInquiryDivisions += it }
        listCalls += operation
        val page = paramOf(query, "pageNo")?.toIntOrNull() ?: 1
        val all = (1..noticesPerSlot).map { index -> awardRow(operation, index) }
        val bytes = envelope(pageOf(all, page), all.size, page).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /**
     * 낙찰 목록 한 행 — 계약 등재 키 다섯과 **미등재 식별자 둘**이다. 금액·낙찰률은 공고마다 달라
     * 「행이 섞이면 붉어진다」가 성립한다.
     */
    private fun awardRow(
        operation: String,
        index: Int,
    ): Map<String, String> =
        mapOf(
            "bidNtceNo" to awardNoticeNumber(nonce, operation.removePrefix(AWARD_LIST_OPERATION_PREFIX), index),
            "bidNtceOrd" to AWARD_NOTICE_ROUND,
            "bidwinnrNm" to winnerName,
            "sucsfbidAmt" to (AWARD_BASE_AMOUNT + index * AWARD_AMOUNT_STEP).toString(),
            "sucsfbidRate" to "%.5f".format(AWARD_RATE_PERCENT + index),
            "prtcptCnum" to (AWARD_PARTICIPANTS + index).toString(),
            "rlOpengDt" to "2026-10-20 11:00:00",
            "fnlSucsfDate" to "2026-10-21",
            // 계약에 없는 식별자 둘 — 적재되면 안 된다.
            "bidwinnrBizno" to MOCK_WINNER_BUSINESS_NUMBER,
            "bidwinnrCeoNm" to MOCK_WINNER_REPRESENTATIVE,
        )

    private fun pageOf(
        all: List<Map<String, String>>,
        page: Int,
    ): List<Map<String, String>> = if (pageSize > 0) all.drop((page - 1) * pageSize).take(pageSize) else all

    private fun envelope(
        items: List<Map<String, String>>,
        totalCount: Int,
        page: Int,
    ): String {
        val rows = if (pageSize > 0) pageSize else ECHOED_ROWS
        val itemsJson = items.joinToString(",", "[", "]") { fields -> objectJson(fields) }
        val body = "\"items\":$itemsJson,\"numOfRows\":$rows,\"pageNo\":$page,\"totalCount\":$totalCount"
        return """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{$body}}}"""
    }

    private fun objectJson(fields: Map<String, String>): String =
        fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":\"$value\"" }

    private fun paramOf(
        query: String,
        name: String,
    ): String? =
        query
            .split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
}

/**
 * 낙찰 목록이 내는 공고번호 — test 가 **미리** 알아야 한다(수집 전에 그 공고의 개찰 축 원문과 확정
 * 표본을 심는 것이 「낙찰자 상호만 비어 있던 상태」를 짓는 길이다). 그래서 mock 이 아니라 이 함수가
 * 번호 규칙의 단일 출처다.
 */
internal fun awardNoticeNumber(
    nonce: String,
    suffix: String,
    index: Int,
): String = "AWARD-$nonce-${suffix.uppercase()}-%03d".format(index)

internal const val AWARD_NOTICE_ROUND = "000"

/** 실 응답이 상호와 함께 싣는 미등재 식별자 둘 — 원문 어디에도 **없어야** 한다(D-5). */
internal const val MOCK_WINNER_BUSINESS_NUMBER = "SYN-BIZNO-1234567890"

internal const val MOCK_WINNER_REPRESENTATIVE = "SYN-낙찰-대표자"

private const val AWARD_LIST_OPERATION_PREFIX = "getScsbidListSttus"
private const val HTTP_OK = 200
private const val ECHOED_ROWS = 100
private const val AWARD_BASE_AMOUNT = 1_200_000_000L
private const val AWARD_AMOUNT_STEP = 1_000_000L
private const val AWARD_RATE_PERCENT = 87.0
private const val AWARD_PARTICIPANTS = 4
