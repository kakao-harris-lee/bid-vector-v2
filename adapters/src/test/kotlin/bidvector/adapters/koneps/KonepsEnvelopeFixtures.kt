package bidvector.adapters.koneps

/**
 * 시나리오 test 가 쓰는 envelope JSON — **골든 사본이 아니다**(D-3B-1 (a)). `fixtures/input`
 * 아래 `koneps` 하위 디렉터리(위 협의된 정본 자리)가 아직 없고 `fixtures` 디렉터리는 이
 * slice 범위 밖이라 만들 수 없다(scope.md out_of_scope). 여기 담은 값은 authoritative
 * 필드명(`policy-values.md` §1.1·§1.3·§1.6 — `bidNtceNo`·`bidNtceOrd`·`resultCode`·
 * `totalCount` 등)과 `koneps-collection-027`이 고정한 resultCode 표만 근거로 이 레인이 직접
 * 작성한 것이다(원문 기존 파일의 바이트를 복제하지 않았다) — checklist.md 「알려진 제한」에
 * 이 자리를 등재한다.
 */
internal object KonepsEnvelopeFixtures {
    fun success(
        items: List<Map<String, String?>>,
        totalCount: Int?,
        pageNo: Int,
        numOfRows: Int,
    ): String {
        val itemsJson = items.joinToString(",", "[", "]") { fields -> objectJson(fields) }
        val bodyFields =
            listOfNotNull(
                "\"items\":$itemsJson",
                "\"numOfRows\":$numOfRows",
                "\"pageNo\":$pageNo",
                totalCount?.let { "\"totalCount\":$it" },
            ).joinToString(",")
        return """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{$bodyFields}}}"""
    }

    const val NO_DATA: String =
        """{"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},""" +
            """"body":{"items":[],"numOfRows":0,"pageNo":1,"totalCount":0}}}"""

    fun failure(
        code: String,
        msg: String,
    ): String = """{"response":{"header":{"resultCode":"$code","resultMsg":"$msg"}}}"""

    const val ABSENT_RESULT_CODE: String = """{"response":{"header":{},"body":{}}}"""

    private fun objectJson(fields: Map<String, String?>): String =
        fields.entries.joinToString(",", "{", "}") { (key, value) ->
            if (value == null) "\"$key\":null" else "\"$key\":\"${value.replace("\"", "\\\"")}\""
        }
}
