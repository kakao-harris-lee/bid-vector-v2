package bidvector.app.http

import tools.jackson.databind.JsonNode

/**
 * JSON 원시 값 읽기 — **명시** 검증만 한다(D-6A3-19 와 같은 규율). Jackson 의 기본 강제
 * 변환(`"3"` → 3, `1.7` → 1)이 값을 조용히 지어내지 못하게, 표기 자체를 먼저 판정하고
 * 어긋나면 [InvalidEditRequestException](400)으로 옮긴다.
 *
 * `StrategyEditRequests.kt` 에서 갈라 나왔다 — 파일당 함수 한도(11) 때문이고, 경계는
 * 「요청 모양을 아는 읽기」와 「JSON 타입만 아는 읽기」다(기능 변경 없음).
 */

internal fun JsonNode.requiredNode(key: String): JsonNode {
    val node = get(key)
    if (node == null || node.isNull) throw InvalidEditRequestException("$key 가 없다")
    return node
}

internal fun JsonNode.requiredNonBlankText(key: String): String {
    val node = requiredNode(key)
    if (!node.isString) throw InvalidEditRequestException("$key 는 문자열이어야 한다")
    val value = node.stringValue()
    if (value.isBlank()) throw InvalidEditRequestException("$key 는 빈 문자열일 수 없다")
    return value
}

internal fun JsonNode.requiredInt(
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

internal fun JsonNode.requiredLong(
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

/**
 * 척도를 깎지 않는다 — 문자열 표기 그대로 `BigDecimal` 로 읽는다(`EditSessionRow` 코덱과 같은
 * 규율). 다만 **무한히 큰 표기는 받지 않는다**(D-6A2b-25, code-review r1 L-6): 지수와 척도에
 * 상한을 둔다. 임계 값은 어차피 0..1 범위 검증을 지나지만, 900자리 소수는 그 검증을 통과한 채
 * 저장·직렬화 비용으로만 남는다 — 값을 지어내지 않되 **받지도 않는다**.
 */
internal fun JsonNode.requiredDecimal(key: String): java.math.BigDecimal {
    val node = requiredNode(key)
    if (!node.isNumber) throw InvalidEditRequestException("$key 는 JSON 수여야 한다")
    val value = node.decimalValue()
    if (value.scale() > MAX_DECIMAL_SCALE || value.precision() > MAX_DECIMAL_PRECISION) {
        throw InvalidEditRequestException("$key 의 자릿수가 상한을 넘는다(척도 $MAX_DECIMAL_SCALE · 유효숫자 $MAX_DECIMAL_PRECISION)")
    }
    return value
}

/**
 * 임계는 0..1 의 비율이라 소수 열 자리면 넘친다 — 이 값 자체는 정책이 아니라 **형식 상한**
 * 이다(어떤 업무 판단도 이 숫자에 걸려 있지 않다).
 */
private const val MAX_DECIMAL_SCALE = 10
private const val MAX_DECIMAL_PRECISION = 20

internal fun JsonNode.requiredStringArray(key: String): List<String> {
    val node = requiredNode(key)
    if (!node.isArray) throw InvalidEditRequestException("$key 는 배열이어야 한다")
    return node.values().map { element ->
        if (!element.isString) throw InvalidEditRequestException("$key 의 원소는 문자열이어야 한다")
        element.stringValue()
    }
}
