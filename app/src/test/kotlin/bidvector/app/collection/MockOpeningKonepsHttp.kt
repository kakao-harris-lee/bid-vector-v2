package bidvector.app.collection

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    /**
     * 이 오퍼레이션들의 **첫 호출**만 HTTP 429 로 돌려준다 — 속도 한도는 재시도 대상이라
     * (`isRetryableStep`) 한 번 더 나간다. 「받은 페이지 1, 나간 호출 2」를 만드는 자리다(D-6G-45).
     */
    private val throttleOnce: Set<String> = emptySet(),
    /**
     * 개찰완료 축을 **쪽으로 나눈다**(D-6G-58) — 0 이면 한 쪽에 전부 싣는다(기본). 잘린 걷기가
     * 다음 실행에 다시 불리는지 재려면 한 축이 두 쪽이어야 한다.
     */
    private val openingCompletePageSize: Int = 0,
    /** 개찰완료 **2쪽의 첫 요청만** 5xx — 1쪽만 받고 끊긴 축을 만든다. */
    private val failOpeningCompleteSecondPageOnce: Boolean = false,
) : AutoCloseable {
    private val secondPageFailurePending = AtomicBoolean(failOpeningCompleteSecondPageOnce)
    private val throttled = CopyOnWriteArrayList<String>()
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

    /**
     * **이 서버가 실제로 받은 요청 수**(D-6G-56) — 429·5xx·본문을 끝까지 읽지 않은 것까지 전부다.
     * 원장이 세는 것을 원장의 합으로 재면 덜 센 것을 볼 수 없다. 기준은 원장 **밖**에 있어야 한다.
     */
    fun requestCount(): Int = received.get()

    private val received = AtomicInteger()

    private fun respond(exchange: HttpExchange) {
        received.incrementAndGet()
        val query = exchange.requestURI.rawQuery.orEmpty()
        val operation = exchange.requestURI.path.substringAfterLast('/')
        if (operation in throttleOnce && throttled.addIfAbsent(operation)) {
            exchange.sendResponseHeaders(HTTP_TOO_MANY_REQUESTS, -1)
            exchange.close()
            return
        }
        val page = paramOf(query, "pageNo")?.toIntOrNull() ?: 1
        if (isPagedOpeningComplete(operation) && page > 1 && secondPageFailurePending.compareAndSet(true, false)) {
            exchange.sendResponseHeaders(HTTP_SERVER_ERROR, -1)
            exchange.close()
            return
        }
        val noticeNumber = paramOf(query, "bidNtceNo")
        val all = itemsFor(operation, query, noticeNumber)
        val bytes =
            envelope(pageOf(operation, all, page), all.size, page, rowsPerPage(operation))
                .toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun itemsFor(
        operation: String,
        query: String,
        noticeNumber: String?,
    ): List<Map<String, String>> =
        // 상세를 **받지 못하는 표본**(D-6G-42) — 호출은 실제로 나가고(그래서 여기서도 센다)
        // 응답만 빈 항목이다. 원문이 한 줄도 적재되지 않아 표본인데 행이 되지 못한다.
        itemsOf(operation, query, noticeNumber).takeUnless {
            noticeNumber != null && isDetailOperation(operation) && isDetailless(noticeNumber)
        } ?: emptyList()

    private fun itemsOf(
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
                // **투찰자 셋.** 추첨번호의 출처는 예비가격 상세이고(D-6G-38) 이 행들의 선택은
                // 그것과 일부러 어긋나 있다 — 추출이 틀린 출처에서 읽으면 golden 이 붉어진다.
                // 셋을 두는 이유는 `prtcptCnum`(참가업체수)이 투찰 행 수와 같아야 하기 때문이다.
                BIDDERS.map { bidder -> bidderRow(noticeNumber.orEmpty(), bidder) }
            }

            operation.endsWith("BsisAmount") -> {
                baseAmountNotices += noticeNumber.orEmpty()
                listOf(baseAmountRow(noticeNumber.orEmpty()))
            }

            operation.endsWith("BidPrceCalclAInfo") -> {
                formulaANotices += noticeNumber.orEmpty()
                listOf(formulaARow(noticeNumber.orEmpty()))
            }

            operation.startsWith("getBidPblancListInfo") && !operation.endsWith("BsisAmount") -> {
                noticeListItems(operation)
            }

            else -> {
                listCalls += operation
                val suffix = operation.removePrefix("getOpengResultListInfo")
                (1..noticesPerSlot)
                    .map { index ->
                        mapOf(
                            "bidNtceNo" to noticeNumber(suffix, index),
                            "bidNtceOrd" to "000",
                            // 참가업체수는 **투찰 행 수와 같다** — 실물이 어긋나지 않는 자리다(D-6G-38).
                            "prtcptCnum" to BIDDERS.size.toString(),
                            "progrsDivCdNm" to "개찰완료",
                        )
                    }.also { require(query.contains("inqryDiv=2")) { "표본틀은 공고일 축으로 걸어야 한다" } }
            }
        }

    /**
     * 공고 목록(다른 서비스·다른 오퍼레이션) — canonical `notice` 를 세우는 갈래가 부른다.
     * **같은 공고번호**를 낸다: 추출이 두 출처를 잇기 때문에 번호가 갈리면 아무것도 안 엮인다.
     */
    private fun noticeListItems(operation: String): List<Map<String, String>> {
        noticeListCalls += operation
        val suffix = operation.removePrefix("getBidPblancListInfo")
        // canonical 이 **없는 표본**(D-6G-42) — 개찰 축 목록은 이 공고를 내지만 공고 목록 갈래는
        // 내지 않는다. 상세는 받지만 대분류를 몰라 행이 서지 않는 자리다.
        return (1..noticesPerSlot)
            .filterNot { index -> suffix == NO_CANONICAL_SUFFIX && index == MISSING_SAMPLE_INDEX }
            .map { index -> noticeListRow(suffix, index) }
    }

    /** 상세 축 넷 — 목록 축과 가른다(목록은 표본틀을 만든다). */
    private fun isDetailOperation(operation: String): Boolean =
        operation.endsWith("PreparPcDetail") ||
            operation.endsWith("OpengCompt") ||
            operation.endsWith("BsisAmount") ||
            operation.endsWith("BidPrceCalclAInfo")

    /**
     * 상세 요청의 공고번호는 **canonical**(ASCII 대문자화)이라 원문 접미와 그대로 맞대면 빗나간다 —
     * 이어 돌기 조회가 같은 이유로 조용히 빗나갔던 자리와 같은 함정이다(D-6G-40).
     */
    private fun isDetailless(noticeNumber: String): Boolean =
        indexOf(noticeNumber) == MISSING_SAMPLE_INDEX &&
            noticeNumber.uppercase().contains("-${DETAILLESS_SUFFIX.uppercase()}-")

    /**
     * 공고 목록 행 — 대분류·하한율·마감·낙찰방법·**공고일**이 여기서만 온다.
     *
     * 표본에 **값 결측 행을 일부러 섞는다**(D-6G-37): golden 이 v3 의 행 단위 제외를 실제로 밟아야
     * 「한 행의 null 이 전체를 거부하지 않는다」가 바이트로 확인된다. 결측은 칸마다 하나씩이다.
     */
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
    ) + omittableNoticeFields(index)

    /** 결측 표본 — `NO_NOTICE_DATE` 는 공고일을, `NO_BID_CLOSE` 는 마감을 뺀다. */
    private fun omittableNoticeFields(index: Int): Map<String, String> =
        buildMap {
            if (index != NO_NOTICE_DATE) put("bidNtceDt", "2026-06-03 09:00:00")
            if (index != NO_BID_CLOSE) put("bidClseDt", "2026-06-16 10:00:00")
        }

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
    ) + omittableOpeningFields(indexOf(noticeNumber))

    /**
     * 결측 표본(D-6G-37) — `NO_PLANNED_PRICE` 는 예정가격을, `NO_OPENING_DATE` 는 개찰일을 뺀다.
     * golden 이 v3 의 **행 단위** 제외를 실제로 밟아야 「한 행의 null 이 전체를 거부하지 않는다」가
     * 바이트로 확인된다.
     */
    private fun omittableOpeningFields(index: Int): Map<String, String> =
        buildMap {
            if (index != NO_PLANNED_PRICE) put("plnprc", "1250000000")
            // 개찰일 — 추출이 이 축에서 읽는다(canonical 이 아니라 원문에서).
            if (index != NO_OPENING_DATE) put("rlOpengDt", "2026-06-17 11:00:00")
        }

    /** 공고번호 끝 네 자리가 표본 안의 순번이다 — 어느 칸을 뺄지 그 값으로 고른다. */
    private fun indexOf(noticeNumber: String): Int = noticeNumber.takeLast(4).toIntOrNull() ?: 0

    /** 실 응답 그대로 개인정보 키 둘을 함께 싣는다 — 경계가 떨어뜨리는지 이 자리에서 잰다. */
    private fun bidderRow(
        noticeNumber: String,
        bidder: SyntheticBidder,
    ) = mapOf(
        "bidNtceNo" to noticeNumber,
        "bidNtceOrd" to "000",
        "opengRank" to bidder.rank.toString(),
        "prcbdrNm" to "$bidderName-${bidder.rank}",
        "prcbdrBizno" to "1234567890",
        "prcbdrCeoNm" to MOCK_REPRESENTATIVE_NAME,
        "bidprcAmt" to bidder.amount,
        "bidprcrt" to "88.000",
        // 추첨번호 — v2 는 추출이 이 축을 안 읽어 실 추출이면 전 행이 제외 ⑤ 에 걸렸다(H-1).
        "drwtNo1" to bidder.firstDraw,
        "drwtNo2" to bidder.secondDraw,
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
            // 순공사원가 — 공사 전용이고 제외 ⑨ 의 입력이다(없으면 공사가 전량 빠진다).
            "bssAmtPurcnstcst" to "900000000",
        )

    private fun formulaARow(noticeNumber: String) =
        mapOf(
            "bidNtceNo" to noticeNumber,
            "bidNtceOrd" to "000",
            "npnInsrprm" to "260853707",
            "qltyMngcstAObjYn" to "Y",
            "smkpAmtYn" to "N",
            "ntceNticeDt" to "2026-06-03 09:39:16",
            "bidPrceCalclAOpenDt" to "2026-06-11 09:00:00",
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

    private fun isPagedOpeningComplete(operation: String): Boolean =
        openingCompletePageSize > 0 && operation.endsWith("OpengCompt")

    /** 쪽으로 나눈 축은 그 쪽의 몫만 낸다 — `totalCount` 는 언제나 전수다(짧은 걷기를 볼 수 있게). */
    private fun pageOf(
        operation: String,
        all: List<Map<String, String>>,
        page: Int,
    ): List<Map<String, String>> =
        if (isPagedOpeningComplete(operation)) {
            all.drop((page - 1) * openingCompletePageSize).take(openingCompletePageSize)
        } else {
            all
        }

    /** 쪽 크기는 **쪽으로 나눈 오퍼레이션에만** 적용한다 — 다른 축까지 줄이면 그 축도 쪼개진다. */
    private fun rowsPerPage(operation: String): Int =
        if (isPagedOpeningComplete(operation)) openingCompletePageSize else DEFAULT_PAGE_ROWS

    private fun envelope(
        items: List<Map<String, String>>,
        totalCount: Int,
        page: Int,
        rows: Int,
    ): String {
        val itemsJson = items.joinToString(",", "[", "]") { fields -> objectJson(fields) }
        val body = "\"items\":$itemsJson,\"numOfRows\":$rows,\"pageNo\":$page,\"totalCount\":$totalCount"
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

/** 실 응답이 상호와 함께 싣는 대표자명 — 스냅숏에 **없어야** 한다(privacy r2 INFO-4). */
internal const val MOCK_REPRESENTATIVE_NAME = "SYN-대표자"

private const val HTTP_TOO_MANY_REQUESTS = 429

private const val RESERVE_PRICE_ROWS = 15
private const val DEFAULT_PAGE_ROWS = 100
private const val HTTP_SERVER_ERROR = 503

private val DRAWN_SEQUENCES = setOf(3, 7, 11, 14)

internal class SyntheticBidder(
    val rank: Int,
    val amount: String,
    val firstDraw: String,
    val secondDraw: String,
)

/**
 * 합성 투찰자 셋(D-6G-38) — 금액이 서로 달라 동가 1위(제외 ⑮)가 생기지 않는다.
 *
 * **선택(`drwtNo1`·`drwtNo2`)을 뽑힌 넷([DRAWN_SEQUENCES])과 일부러 다르게 둔다.** r1 의 mock 은 둘을
 * 같게 맞춰 두어, 추출이 **틀린 출처**(투찰자 선택)에서 읽고 있는데도 golden 이 초록이었다. 겹치지
 * 않게 두면 출처가 틀린 순간 golden 이 붉어진다.
 */
private val BIDDERS =
    listOf(
        SyntheticBidder(rank = 1, amount = "1100000000", firstDraw = "1", secondDraw = "2"),
        SyntheticBidder(rank = 2, amount = "1150000000", firstDraw = "4", secondDraw = "5"),
        SyntheticBidder(rank = 3, amount = "1200000000", firstDraw = "6", secondDraw = "8"),
    )

private const val NO_NOTICE_DATE = 2
private const val NO_BID_CLOSE = 3
private const val NO_OPENING_DATE = 4
private const val NO_PLANNED_PRICE = 5

/**
 * 표본인데 행이 되지 못하는 두 자리(D-6G-42) — 마지막 순번 하나를 업무마다 다른 사유로 쓴다.
 * 공사에서는 상세가 비고, 용역에서는 공고 목록 canonical 이 없다. 두 계수가 **각각 1** 이 되어야
 * 왕복이 「진부분집합」과 「결측 계수」를 실제로 지나간다 — 둘 다 0 이면 생산 쪽이 그 칸을 상수로
 * 적어도 golden 이 초록이다.
 */
private const val MISSING_SAMPLE_INDEX = 6
private const val DETAILLESS_SUFFIX = "Cnstwk"
private const val NO_CANONICAL_SUFFIX = "Servc"
