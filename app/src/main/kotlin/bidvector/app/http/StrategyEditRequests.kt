package bidvector.app.http

import bidvector.app.wiring.EditValue
import bidvector.app.wiring.EditValueSlot
import bidvector.app.wiring.valueSlot
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.EditableField
import tools.jackson.databind.JsonNode

/**
 * 요청 본문 형식 오류(M6/6A-2b D-6A2b-7) — 400 `INVALID_REQUEST`. 메시지는 응답에 실리지
 * 않는다(`ErrorMapping` 이 고정 문구로만 옮긴다) — 진단용이다.
 */
class InvalidEditRequestException(
    message: String,
) : RuntimeException(message)

/**
 * 요청 본문의 **명시** 형식 검증(D-6A3-19 와 같은 규율) — Jackson 의 기본 강제 변환
 * (`"3"` → 3, `1.7` → 1)이 값을 조용히 지어내지 못하게 원시 트리로 받아 여기서 판정한다.
 * 이 파일이 편집 endpoint 의 유일한 파싱 자리다 — 컨트롤러는 타입이 확정된 값만 받는다.
 */
internal fun JsonNode.requireObject(): JsonNode {
    if (!isObject) throw InvalidEditRequestException("요청 본문이 JSON object 가 아니다")
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

private fun JsonNode.requiredNode(key: String): JsonNode {
    val node = get(key)
    if (node == null || node.isNull) throw InvalidEditRequestException("$key 가 없다")
    return node
}

private fun JsonNode.requiredNonBlankText(key: String): String {
    val node = requiredNode(key)
    if (!node.isString) throw InvalidEditRequestException("$key 는 문자열이어야 한다")
    val value = node.stringValue()
    if (value.isBlank()) throw InvalidEditRequestException("$key 는 빈 문자열일 수 없다")
    return value
}

private fun JsonNode.requiredInt(
    key: String,
    minimum: Int,
): Int {
    val node = requiredNode(key)
    if (!node.isIntegralNumber || !node.canConvertToInt()) {
        throw InvalidEditRequestException("$key 는 Int 범위의 JSON 정수여야 한다")
    }
    val value = node.intValue()
    if (value < minimum) throw InvalidEditRequestException("$key 는 $minimum 이상이어야 한다")
    return value
}

private fun JsonNode.requiredLong(
    key: String,
    minimum: Long,
): Long {
    val node = requiredNode(key)
    if (!node.isIntegralNumber || !node.canConvertToLong()) {
        throw InvalidEditRequestException("$key 는 Long 범위의 JSON 정수여야 한다")
    }
    val value = node.longValue()
    if (value < minimum) throw InvalidEditRequestException("$key 는 $minimum 이상이어야 한다")
    return value
}

/** 척도를 깎지 않는다 — 문자열 표기 그대로 `BigDecimal` 로 읽는다(`EditSessionRow` 코덱과 같은 규율). */
private fun JsonNode.requiredDecimal(key: String): java.math.BigDecimal {
    val node = requiredNode(key)
    if (!node.isNumber) throw InvalidEditRequestException("$key 는 JSON 수여야 한다")
    return node.decimalValue()
}

private fun JsonNode.requiredStringArray(key: String): List<String> {
    val node = requiredNode(key)
    if (!node.isArray) throw InvalidEditRequestException("$key 는 배열이어야 한다")
    return node.values().map { element ->
        if (!element.isString) throw InvalidEditRequestException("$key 의 원소는 문자열이어야 한다")
        element.stringValue()
    }
}
