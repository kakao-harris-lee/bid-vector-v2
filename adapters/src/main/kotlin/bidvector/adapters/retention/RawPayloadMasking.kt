package bidvector.adapters.retention

import bidvector.adapters.koneps.JsonParseException
import bidvector.adapters.koneps.JsonValue
import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.adapters.koneps.render

/**
 * 판독할 수 없는 원문의 **대체 값**(fail-closed) — 부분 치환이나 위치 추측을 하지 않는다
 * (`maskOpengCorpInfo` 의 「값 전체 폐기」 관례, 설계 검토 우회 10). `payload` 열은
 * `CHECK (payload <> '')` 이라 빈 문자열이 아니어야 한다.
 */
internal const val PAYLOAD_MASK_FAIL_CLOSED = """{"pii_masked":"fail_closed"}"""

/**
 * `payload` 는 KONEPS 항목 **하나**의 평평한 객체다 — 상한을 끄지 않고 저장된 원문을 읽는
 * 같은 자리(`adapters.snapshot` 의 `payload_fields` 판독)와 같은 값을 쓴다.
 */
private const val PAYLOAD_MAX_DEPTH = 8

/** [maskRawPayload] 의 결과 — 「마스킹했다」와 「판독할 수 없어 값을 버렸다」를 접지 않는다. */
internal sealed interface PayloadMaskOutcome {
    data class Masked(
        val payload: String,
    ) : PayloadMaskOutcome

    /** 형태가 다르다 — 값 전체를 [PAYLOAD_MASK_FAIL_CLOSED] 로 바꾸고 따로 센다. */
    data object FailClosed : PayloadMaskOutcome
}

/**
 * 원문 `payload` 에서 **남길 키만** 남긴다(M6/6B-3 P-4) — deny-list 가 아니라 **allow-list
 * 반전**이다. 담당자 키 이름을 열거하면 상류가 키를 더할 때 조용히 열린다
 * (`OPEN-6F8-RAW-PII-RETENTION` 이 가리킨 자리가 바로 상류가 더하는 키였다).
 *
 * **allow-list 의 출처는 그 행 자신의 `payload_fields` 키 집합**이다 — 「우리가 실제로 읽은
 * 키」가 행에 이미 적혀 있다. 그 투영은 `JdbcRawObservationStore` 가 필드 계약 레지스트리로
 * 쓰고(`ObservationPayloadCodec.encode`), 그 레지스트리에는 담당자 키가 **등재되지 않는다**
 * (D-3H-7 — `FieldContractTest` 가 어떤 rawName 에도 `Ofcl` 가 없음을 단언). 그래서 남김
 * 집합에 담당자 키가 들어올 경로가 없고, 집합이 어긋날 **두 벌**(여기 또 한 벌의 키 목록)도
 * 두지 않는다.
 *
 * 값이 객체·배열인 키는 **남기지 않는다**: 계약 경유 판독은 문자열 하나를 읽으므로
 * (`RawValue`) 중첩 값은 아무도 읽지 않고, 그 안에 무엇이 들었는지 이 함수가 알 수 없다.
 *
 * 순수 함수다 — DB·시계·설정을 모른다(설계 검토 (2b) 「마스킹 함수 … 순수 함수 — 무해」).
 */
internal fun maskRawPayload(
    payload: String,
    keptKeys: Set<String>,
): PayloadMaskOutcome {
    val fields = flatObjectOrNull(payload) ?: return PayloadMaskOutcome.FailClosed
    val kept = fields.filter { (name, value) -> name in keptKeys && value.isScalar() }
    return PayloadMaskOutcome.Masked(JsonValue.JsonObject(kept, sourceText = "").render())
}

/**
 * 판독 실패의 **상세를 잃는다** — 그 문면에는 원문(= PII)이 실린다. 잃지 않는 것은 「이 행은
 * 판독할 수 없다」는 사실이고, 호출부가 그것을 fail-closed 계수로 올린다.
 */
private fun flatObjectOrNull(payload: String): Map<String, JsonValue>? =
    try {
        (KonepsJsonParser.parse(payload, PAYLOAD_MAX_DEPTH) as? JsonValue.JsonObject)?.fields
    } catch (
        @Suppress("SwallowedException") malformed: JsonParseException,
    ) {
        null
    }

private fun JsonValue.isScalar(): Boolean =
    when (this) {
        is JsonValue.JsonString, is JsonValue.JsonNumber, is JsonValue.JsonBool, JsonValue.JsonNull -> true
        is JsonValue.JsonObject, is JsonValue.JsonArray -> false
    }
