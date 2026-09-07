package bidvector.adapters.koneps

import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.ResultCodeCategory

/**
 * KONEPS 응답 envelope 판정(③, D-3B-7) — `resultCode` 분류에 따라 갈린다. **범주표는 3A
 * `KONEPS_COLLECTION_POLICY.resultCodeCategories`(§3, 운영자 승인 2026-09-07 P-4)를 읽기만
 * 한다 — 3B 가 재선언하지 않는다.**
 */
internal sealed interface KonepsEnvelopeOutcome {
    data class Success(
        val items: List<JsonValue.JsonObject>,
        val totalCount: Int?,
        val pageNo: Int?,
        val numOfRows: Int?,
    ) : KonepsEnvelopeOutcome

    /** `resultCode 03`(No Data) — 성공도 실패도 아닌 세 번째 상태(P-4 ③). */
    data object NoData : KonepsEnvelopeOutcome

    /** 3A 표에 있는 알려진 실패 범주(재시도 가능/불가·quota·입력 오류). */
    data class Classified(
        val category: ResultCodeCategory,
        val code: String,
    ) : KonepsEnvelopeOutcome

    /** 부재·미지 코드 — fail-safe 비재시도(P-4 ③, R-COL-01이 겨눈 「조용한 00」 금지). */
    data class Unclassified(
        val code: String?,
    ) : KonepsEnvelopeOutcome

    /** JSON 구조 자체가 무너짐(파싱 실패, 필수 봉투 형태 위반) — 항목이 아니라 페이지 단위 실패. */
    data class StructureFailure(
        val reason: String,
    ) : KonepsEnvelopeOutcome
}

private const val SUCCESS_CODE = "00"
private const val NO_DATA_CODE = "03"

/**
 * 응답 원문(JSON 문자열)을 envelope outcome 으로 판정한다(③, D-3B-7). `policy`는 3A
 * `KONEPS_COLLECTION_POLICY`를 조회일 기준으로 resolve 한 값이다.
 */
internal fun parseKonepsEnvelope(
    body: String,
    policy: KonepsCollectionPolicyData,
): KonepsEnvelopeOutcome =
    runCatching { KonepsJsonParser.parse(body) }
        .fold(
            onSuccess = { root -> classifyRoot(root, policy) },
            onFailure = { failure -> KonepsEnvelopeOutcome.StructureFailure("JSON 파싱 실패: ${failure.message}") },
        )

private fun classifyRoot(
    root: JsonValue,
    policy: KonepsCollectionPolicyData,
): KonepsEnvelopeOutcome {
    val response = root.asObject()?.member("response").asObject()
    val header = response?.member("header").asObject()
    return when {
        response == null -> KonepsEnvelopeOutcome.StructureFailure("최상위 'response' 객체가 없다")
        header == null -> KonepsEnvelopeOutcome.StructureFailure("'response.header' 객체가 없다")
        else -> classify(header.member("resultCode").asStringOrNull(), response, policy)
    }
}

private fun classify(
    code: String?,
    response: JsonValue.JsonObject,
    policy: KonepsCollectionPolicyData,
): KonepsEnvelopeOutcome =
    when (code) {
        SUCCESS_CODE -> successBody(response)
        NO_DATA_CODE -> KonepsEnvelopeOutcome.NoData
        null -> KonepsEnvelopeOutcome.Unclassified(null)
        else -> classifyKnownFailure(code, policy)
    }

private fun classifyKnownFailure(
    code: String,
    policy: KonepsCollectionPolicyData,
): KonepsEnvelopeOutcome {
    val category = policy.resultCodeCategories.firstOrNull { it.code == code }?.category
    return if (category != null) {
        KonepsEnvelopeOutcome.Classified(category, code)
    } else {
        KonepsEnvelopeOutcome.Unclassified(code)
    }
}

private fun successBody(response: JsonValue.JsonObject): KonepsEnvelopeOutcome {
    val body = response.member("body").asObject()
    val items = body?.let { readItems(it.member("items")) }
    return when {
        body == null -> {
            KonepsEnvelopeOutcome.StructureFailure("resultCode 00 인데 'response.body' 객체가 없다")
        }

        items == null -> {
            KonepsEnvelopeOutcome.StructureFailure("'response.body.items' 형태가 예상 밖이다")
        }

        else -> {
            KonepsEnvelopeOutcome.Success(
                items = items,
                totalCount = body.member("totalCount").asIntOrNull(),
                pageNo = body.member("pageNo").asIntOrNull(),
                numOfRows = body.member("numOfRows").asIntOrNull(),
            )
        }
    }
}

/** `items`가 배열이거나(간이 형태) `{"item": [...]}`/`{"item": {...}}` 로 감싼 형태(KONEPS 실물)를 받는다. */
private fun readItems(node: JsonValue?): List<JsonValue.JsonObject>? =
    when (node) {
        null -> emptyList()
        is JsonValue.JsonArray -> node.items.mapNotNull { it.asObject() }
        is JsonValue.JsonObject -> readWrappedItem(node.member("item"))
        else -> null
    }

private fun readWrappedItem(inner: JsonValue?): List<JsonValue.JsonObject>? =
    when (inner) {
        null -> emptyList()
        is JsonValue.JsonArray -> inner.items.mapNotNull { it.asObject() }
        is JsonValue.JsonObject -> listOf(inner)
        else -> null
    }
