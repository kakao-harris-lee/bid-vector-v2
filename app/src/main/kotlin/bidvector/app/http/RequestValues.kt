package bidvector.app.http

import tools.jackson.databind.JsonNode

/**
 * 도메인 값 객체의 생성 규칙(`require`)을 **400 으로** 옮긴다. 형식이 틀린 요청 값(길이 상한 · 부호 ·
 * 표기 규칙)이 `IllegalArgumentException` 으로 새면 기본 처리기가 **500** 을 낸다 — 요청 탓을 서버
 * 탓으로 말하는 것이다. 그 예외 **하나만** 옮기고 나머지는 그대로 던진다(`JsonValueReaders` 의 시각
 * 판독과 같은 관례). 메시지는 응답에 실리지 않는다.
 *
 * 이 파일은 **요청 값 → 도메인 값 변환**을 모은 자리다. `BidCaseRequests.kt` 에서 갈라 나왔고
 * (M7/7C④ 2b), 프로필 요청도 같은 수단이 필요해 복사 대신 공용화했다 — 복사하면 「형식 오류를
 * 400 으로」라는 규율이 두 자리에 선다. `JsonValueReaders.kt` 가 아닌 이유는 그 파일이 **JSON 표기만
 * 아는 읽기**이고 파일당 함수 한도(11)에 닿아 있어서다.
 */
internal fun <T> requestValue(
    key: String,
    build: () -> T,
): T =
    runCatching(build).getOrElse { failure ->
        if (failure is IllegalArgumentException) throw InvalidEditRequestException("$key 가 값 규칙을 어긴다")
        throw failure
    }

/**
 * 알려진 어휘 하나 — 값이 enum 밖이면 400 이다. 「모르는 값을 기본값으로 접는」 경로를 두지
 * 않는다(그것이 곧 어휘를 지어내는 길이다).
 */
internal fun <T : Enum<T>> JsonNode.enumOf(
    key: String,
    values: List<T>,
): T {
    val raw = requiredNonBlankText(key)
    return values.firstOrNull { it.name == raw }
        ?: throw InvalidEditRequestException("$key 는 ${values.joinToString("·") { it.name }} 중 하나다")
}

/**
 * 닫힌 어휘의 **집합** — 중복 원소는 거부한다(V22 의 cardinality CHECK 와 같은 술어). 원소 비교가
 * [enumOf] 와 같은 자리에 있는 이유: 어휘 이름 비교를 HTTP 층의 **한 파일**에만 두기 위해서다
 * (`app.http` 는 비밀값 비교를 구조로 막는 패키지라 비교 자리가 흩어지면 그 판정이 흐려진다).
 */
internal fun <T : Enum<T>> JsonNode.enumSet(
    key: String,
    values: List<T>,
): Set<T> {
    val parsed =
        requiredStringArray(key).map { name ->
            values.firstOrNull { it.name == name }
                ?: throw InvalidEditRequestException("$key 는 ${values.joinToString("·") { it.name }} 의 부분집합이다")
        }
    if (parsed.size != parsed.toSet().size) throw InvalidEditRequestException("$key 에 같은 값이 두 번 있다")
    return parsed.toSet()
}

/** 없거나 `null` 이면 「미설정」. 값이 있으면 어휘 안이어야 한다(모르는 값을 미설정으로 접지 않는다). */
internal fun <T : Enum<T>> JsonNode.optionalEnum(
    key: String,
    values: List<T>,
): T? {
    val node = get(key)
    if (node == null || node.isNull) return null
    return enumOf(key, values)
}

/** 불리언 — Jackson 의 강제 변환(`"true"` · `1`)을 받지 않는다(표기 자체를 판정한다). */
internal fun JsonNode.requiredBoolean(key: String): Boolean {
    val node = requiredNode(key)
    if (!node.isBoolean) throw InvalidEditRequestException("$key 는 JSON 불리언이어야 한다")
    return node.booleanValue()
}
