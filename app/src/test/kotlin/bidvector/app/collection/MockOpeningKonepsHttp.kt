package bidvector.app.collection

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 개찰 축 KONEPS mock(M6/6G E2E) — loopback in-process(네트워크 0). 경로의 마지막 조각이 오퍼레이션
 * 이름이고, 오퍼레이션마다 어떤 축으로 불렸는지를 목록에 남겨 「표본에 뽑힌 공고만 상세를 불렀는가」를
 * 셀 수 있게 한다.
 *
 * **투찰 행에 상호를 싣는다**(`prcbdrNm`) — 실 응답이 그렇기 때문이고, 그래야 「상호가 로그에 새지
 * 않는가」를 실제로 잴 수 있다. 사업자번호·대표자명도 함께 실어 어댑터 경계의 허용 목록 반전이 그
 * 둘을 떨어뜨리는지 같은 자리에서 확인한다.
 */
internal class MockOpeningKonepsHttp(
    private val noticesPerSlot: Int,
    private val bidderName: String,
    /**
     * 공고번호를 **이 mock 인스턴스마다 다르게** 만드는 표식. 같은 컨테이너를 쓰는 앞 test 가 같은
     * 번호를 이미 적재하면 이어 돌기(D-6G-29 ③)가 전 축을 건너뛰어, 뒤 test 가 재려는 거동이
     * 사라진다 — 그 건너뜀 자체는 옳은 동작이라 번호를 갈라 둔다.
     */
    private val nonce: String = newNonce(),
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val listCalls = CopyOnWriteArrayList<String>()
    val noticeListCalls = CopyOnWriteArrayList<String>()
    val reservePriceNotices = CopyOnWriteArrayList<String>()
    val openingCompleteNotices = CopyOnWriteArrayList<String>()
    val baseAmountNotices = CopyOnWriteArrayList<String>()
    val formulaANotices = CopyOnWriteArrayList<String>()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/mock"

    fun detailCallCount(): Int =
        reservePriceNotices.size + openingCompleteNotices.size + baseAmountNotices.size + formulaANotices.size

    init {
        server.createContext("/mock") { exchange -> respond(exchange) }
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    private fun respond(exchange: HttpExchange) {
        val query = exchange.requestURI.rawQuery.orEmpty()
        val operation = exchange.requestURI.path.substringAfterLast('/')
        val noticeNumber = paramOf(query, "bidNtceNo")
        val items = itemsFor(operation, query, noticeNumber)
        val bytes = envelope(items).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun itemsFor(
        operation: String,
        query: String,
        noticeNumber: String?,
    ): List<Map<String, String>> =
        when {
            operation.endsWith("PreparPcDetail") -> {
                reservePriceNotices += noticeNumber.orEmpty()
                // **복수예가 15행 전부.** 하나만 주면 제외 ⑤(예비가격·추첨 결측)가 전 행을 걷어내
                // golden 에 승인되는 행이 하나도 남지 않는다(D-6G-37).
                (1..RESERVE_PRICE_ROWS).map { sequence -> reservePriceRow(noticeNumber.orEmpty(), sequence) }
            }

            operation.endsWith("OpengCompt") -> {
                openingCompleteNotices += noticeNumber.orEmpty()
                listOf(bidderRow(noticeNumber.orEmpty()))
            }

            operation.endsWith("BsisAmount") -> {
                baseAmountNotices += noticeNumber.orEmpty()
                listOf(baseAmountRow(noticeNumber.orEmpty()))
            }

            operation.endsWith("BidPrceCalclAInfo") -> {
                formulaANotices += noticeNumber.orEmpty()
                listOf(formulaARow(noticeNumber.orEmpty()))
            }

            // 공고 목록(다른 서비스·다른 오퍼레이션) — canonical `notice` 를 세우는 갈래가 부른다.
            // **같은 공고번호**를 낸다: 추출이 두 출처를 잇기 때문에 번호가 갈리면 아무것도 안 엮인다.
            operation.startsWith("getBidPblancListInfo") && !operation.endsWith("BsisAmount") -> {
                noticeListCalls += operation
                val suffix = operation.removePrefix("getBidPblancListInfo")
                (1..noticesPerSlot).map { index -> noticeListRow(suffix, index) }
            }

            else -> {
                listCalls += operation
                val suffix = operation.removePrefix("getOpengResultListInfo")
                (1..noticesPerSlot)
                    .map { index ->
                        mapOf(
                            "bidNtceNo" to noticeNumber(suffix, index),
                            "bidNtceOrd" to "000",
                            "prtcptCnum" to "7",
                            "progrsDivCdNm" to "개찰완료",
                        )
                    }.also { require(query.contains("inqryDiv=2")) { "표본틀은 공고일 축으로 걸어야 한다" } }
            }
        }

    /** 공고 목록 행 — 대분류·하한율·마감·낙찰방법·**공고일**이 여기서만 온다. */
    private fun noticeListRow(
        suffix: String,
        index: Int,
    ) = mapOf(
        "bidNtceNo" to noticeNumber(openingSuffixFor(suffix), index),
        "bidNtceOrd" to "000",
        "bidNtceNm" to "합성 공고 $index",
        "sucsfbidLwltRate" to "87.745",
        "sucsfbidMthdCd" to "낙030001",
        "sucsfbidMthdNm" to "적격심사제",
        "sucsfbidMthdAppStd" to "조달청 기준",
        "pubPrcrmntClsfcNo" to "81112200",
        "dminsttCd" to "6110000",
        // 공고일은 개찰일보다 **이르다** — 둘이 같으면 제외 ⑬ 의 결함이 드러나지 않는다.
        "bidNtceDt" to "2026-06-03 09:00:00",
        "bidClseDt" to "2026-06-16 10:00:00",
    )

    private fun noticeNumber(
        suffix: String,
        index: Int,
    ): String = "OPEN-E2E-$nonce-$suffix-%04d".format(index)

    /** 공고 목록 오퍼레이션 접미(`Cnstwk`)와 개찰결과 목록 접미가 같은 업무를 가리킨다. */
    private fun openingSuffixFor(noticeListSuffix: String): String = noticeListSuffix

    private fun reservePriceRow(
        noticeNumber: String,
        sequence: Int,
    ) = mapOf(
        "bidNtceNo" to noticeNumber,
        "bidNtceOrd" to "000",
        "compnoRsrvtnPrceSno" to "%02d".format(sequence),
        "bsisPlnprc" to (1_240_000_000L + sequence * 1_000_000L).toString(),
        "drwtYn" to if (sequence in DRAWN_SEQUENCES) "Y" else "N",
        "bssamt" to "1239999999",
        "plnprc" to "1250000000",
        // 개찰일 — 추출이 이 축에서 읽는다(canonical 이 아니라 원문에서).
        "rlOpengDt" to "2026-06-17 11:00:00",
    )

    /** 실 응답 그대로 개인정보 키 둘을 함께 싣는다 — 경계가 떨어뜨리는지 이 자리에서 잰다. */
    private fun bidderRow(noticeNumber: String) =
        mapOf(
            "bidNtceNo" to noticeNumber,
            "bidNtceOrd" to "000",
            "opengRank" to "1",
            "prcbdrNm" to bidderName,
            "prcbdrBizno" to "1234567890",
            "prcbdrCeoNm" to "SYN-대표자",
            "bidprcAmt" to "1100000000",
            "bidprcrt" to "88.000",
            // 추첨번호 — v2 는 추출이 이 축을 안 읽어 실 추출이면 전 행이 제외 ⑤ 에 걸렸다(H-1).
            "drwtNo1" to "3",
            "drwtNo2" to "7",
        )

    private fun baseAmountRow(noticeNumber: String) =
        mapOf(
            "bidNtceNo" to noticeNumber,
            "bidNtceOrd" to "000",
            "bssamt" to "1234567890",
            "bssamtOpenDt" to "2026-06-10 09:00:00",
            "rsrvtnPrceRngBgnRate" to "-3",
            "rsrvtnPrceRngEndRate" to "+3",
            "bidPrceCalclAYn" to "Y",
        )

    private fun formulaARow(noticeNumber: String) =
        mapOf(
            "bidNtceNo" to noticeNumber,
            "bidNtceOrd" to "000",
            "npnInsrprm" to "260853707",
            "qltyMngcstAObjYn" to "Y",
            "smkpAmtYn" to "N",
            "ntceNticeDt" to "2026-06-03 09:39:16",
            "bidPrceCalclAOpenDt" to "2026-06-16 16:10:19",
        )

    private fun paramOf(
        query: String,
        name: String,
    ): String? =
        query
            .split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

    private fun envelope(items: List<Map<String, String>>): String {
        val itemsJson = items.joinToString(",", "[", "]") { fields -> objectJson(fields) }
        val body = "\"items\":$itemsJson,\"numOfRows\":100,\"pageNo\":1,\"totalCount\":${items.size}"
        return """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{$body}}}"""
    }

    private fun objectJson(fields: Map<String, String>): String =
        fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":\"$value\"" }
}

private fun newNonce(): String =
    UUID
        .randomUUID()
        .toString()
        .take(NONCE_LENGTH)
        .uppercase()

private const val NONCE_LENGTH = 8

private const val RESERVE_PRICE_ROWS = 15

private val DRAWN_SEQUENCES = setOf(3, 7, 11, 14)
