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
 * 판독 깊이 상한 — **수집이 받아들인 깊이 이상**이어야 한다(cr M-1). 낮으면 「형태가 우리 것이
 * 아니다」와 「우리가 받아들인 뒤 임의로 낮춘 상한을 넘었다」가 같은 처분(값 전체 폐기)을 받고,
 * 그 폐기는 되돌릴 수 없다(트리거가 값→값 UPDATE 를 거부해 forward-fix 조차 없다).
 *
 * 두 상수의 **독립이 결함의 원인**이었으므로 그 독립을 test 가 센다(`RawPayloadMaskingTest` —
 * 수집 정책 **전 entry** 의 `maxJsonDepth` 이상임을 단언한다). 여기서 정책을 직접 읽지 않는
 * 이유는 이 함수가 **순수**라는 성질이다 — 기준일도 설정도 받지 않는다.
 */
internal const val PAYLOAD_MASK_MAX_DEPTH = 32

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
 * **allow-list 는 호출자가 준다** — 출처는 계약 레지스트리이고(`RetentionMaskedKeys.keptRawNames`)
 * 그 행의 데이터가 아니다. 앞 판은 그 행 자신의 `payload_fields` 키 집합을 썼고, 「담당자 키가
 * 남김 집합에 들어올 경로가 없다」고 단언했지만 **그 경로가 있었다**: 적재자가 투영에 그 키를 실으면
 * 들어왔다(vr R2-F2(a)). 담당자 키가 계약에 등재되지 않는다는 사실(D-3H-7)은 여전히 참이고, 이제는
 * 그 사실이 **계약에서 유도한 집합**을 통해 하중을 진다 — 키 목록을 두 벌 두지 않는 성질도 그대로다.
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
        (KonepsJsonParser.parse(payload, PAYLOAD_MASK_MAX_DEPTH) as? JsonValue.JsonObject)?.fields
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
