package bidvector.app.collection

/**
 * mock KONEPS 응답의 **봉투와 쪽 나눔** — 「어느 축이 어떤 행을 내는가」(`MockOpeningKonepsHttp`)와
 * 「그 행들이 어떤 봉투에 몇 쪽으로 실리는가」는 따로 바뀐다. 봉투 문면은 실 게이트웨이의 것이고
 * (`resultCode 00` · `items` · `numOfRows` · `pageNo` · `totalCount`) 쪽 나눔은 걷기의 완료 판정이
 * 보는 축이라, 둘이 한 파일에 있으면 봉투를 손대는 편집이 쪽 나눔 test 를 함께 흔든다.
 */
internal fun mockKonepsEnvelope(
    items: List<Map<String, String>>,
    totalCount: Int,
    page: Int,
    rows: Int,
): String {
    val itemsJson = items.joinToString(",", "[", "]") { fields -> mockJsonObject(fields) }
    val body = "\"items\":$itemsJson,\"numOfRows\":$rows,\"pageNo\":$page,\"totalCount\":$totalCount"
    return """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{$body}}}"""
}

private fun mockJsonObject(fields: Map<String, String>): String =
    fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":\"$value\"" }

/** 쪽으로 나눈 축은 그 쪽의 몫만 낸다 — `totalCount` 는 언제나 전수다(짧은 걷기를 볼 수 있게). */
internal fun mockPageOf(
    all: List<Map<String, String>>,
    page: Int,
    pageSize: Int,
): List<Map<String, String>> = if (pageSize > 0) all.drop((page - 1) * pageSize).take(pageSize) else all

/**
 * 응답이 에코하는 `numOfRows` — 쪽을 나누지 않는 응답은 요청이 999 여도 **100 으로 에코**한다. 걷기의
 * 완료 판정은 `totalCount` 와 짧은 쪽으로만 서므로 그 에코는 거동에 영향이 없고, 실 게이트웨이와
 * 다른 자리라 여기 적어 둔다.
 */
internal fun mockEchoedRowsOf(pageSize: Int): Int = if (pageSize > 0) pageSize else MOCK_DEFAULT_PAGE_ROWS

internal const val MOCK_DEFAULT_PAGE_ROWS = 100

/**
 * 쪽 크기 판(D-6G2f-3) — 기본은 요청의 `numOfRows` 를 **무시하는** 기존 판이고, 기존 test 가 재는
 * 것이 그 판이다.
 *
 * [respectsRequestedRows] 를 켜면 요청이 쪽 크기를 정한다 — 배선이 올린 값이 호출 수를 실제로 줄이는지
 * 는 그렇게만 잴 수 있다. [biddersPerNotice] 는 참가가 많은 공고를 짓는다(`null` 이면 고정 셋).
 */
internal class MockPagingMode(
    val respectsRequestedRows: Boolean = false,
    val biddersPerNotice: Int? = null,
)

/** 받은 요청 한 건의 오퍼레이션과 `numOfRows` — 요청이 그 칸을 싣지 않았으면 `null` 이다. */
internal class RequestedRows(
    val operation: String,
    val numOfRows: Int?,
)
