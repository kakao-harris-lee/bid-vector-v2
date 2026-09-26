package bidvector.app.http

import bidvector.app.wiring.EditValue
import bidvector.app.wiring.EditValueSlot
import bidvector.app.wiring.valueSlot
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.EditableField
import tools.jackson.databind.JsonNode

/**
 * 요청 본문의 **명시** 형식 검증(D-6A3-19 와 같은 규율) — Jackson 의 기본 강제 변환
 * (`"3"` → 3, `1.7` → 1)이 값을 조용히 지어내지 못하게 원시 트리로 받아 여기서 판정한다.
 * 이 파일이 편집 endpoint 의 유일한 파싱 자리다 — 컨트롤러는 타입이 확정된 값만 받는다.
 */
internal fun JsonNode.requireObject(): JsonNode {
    if (!isObject) throw InvalidEditRequestException("요청 본문이 JSON object 가 아니다")
    return this
}

/**
 * 알려진 키 밖은 **거부한다**(D-6A2b-25, code-review r1 L-5). 값 칸만 걸러내고 나머지 키를
 * 조용히 무시하면 「보낸 것과 저장된 것이 다르다」가 되고, 그것이 이 slice 가 닫으려는
 * 「값을 지어내지 않는다」의 반대다. 오탈자(`commandID`)도 성공으로 보이지 않는다.
 */
internal fun JsonNode.requireKnownKeys(allowed: Set<String>): JsonNode {
    val unknown = propertyNames().filterNot { it in allowed }.sorted()
    if (unknown.isNotEmpty()) throw InvalidEditRequestException("알 수 없는 본문 키: $unknown")
    return this
}

internal fun JsonNode.commandId(): CommandId = CommandId(requiredNonBlankText("commandId"))

internal fun JsonNode.field(): EditableField {
    val token = requiredNonBlankText("field")
    return editableFieldOfToken(token) ?: throw InvalidEditRequestException("알 수 없는 field 토큰: $token")
}

internal fun JsonNode.seenRevision(): StrategyRevision = StrategyRevision(requiredInt("seenRevision", minimum = 0))

/**
 * 값 칸 넷 가운데 **필드가 이름한 칸 하나만** 읽는다(D-6A2b-1) — 다른 칸이 함께 오면
 * 형식 오류다. 조용히 무시하면 「보낸 값과 저장된 값이 다르다」가 되고, 그것이 이 slice 가
 * 닫으려는 「값을 지어내지 않는다」의 반대다.
 */
internal fun JsonNode.editValue(field: EditableField): EditValue {
    val slot = field.valueSlot()
    requireKnownKeys(setOf("commandId", "field", slot.jsonKey))
    EditValueSlot.entries
        .filter { it != slot && has(it.jsonKey) }
        .forEach { throw InvalidEditRequestException("${field.token()} 는 ${it.jsonKey} 칸을 읽지 않는다") }
    return when (slot) {
        EditValueSlot.TERMS -> EditValue.Terms(requiredStringArray(slot.jsonKey))
        EditValueSlot.AMOUNT_WON -> EditValue.AmountWon(requiredLong(slot.jsonKey, minimum = 0))
        EditValueSlot.NUMBER -> EditValue.Number(requiredDecimal(slot.jsonKey))
        EditValueSlot.COUNT -> EditValue.Count(requiredInt(slot.jsonKey, minimum = Int.MIN_VALUE))
    }
}
