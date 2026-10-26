package bidvector.app.http

import tools.jackson.databind.JsonNode

/**
 * 요청 값 → 도메인 값 변환의 **공용 두 수단**. `BidCaseRequests.kt` 에서 갈라 나왔다(M7/7C④ 2b) —
 * 프로필 요청도 같은 두 가지가 필요했고, 복사하면 「형식 오류를 400 으로 옮긴다」는 규율이 두 자리에
 * 서게 된다. 거동은 바뀌지 않는다.
 *
 * `JsonValueReaders.kt` 가 아니라 새 파일인 이유: 그 파일은 **JSON 표기만 아는 읽기**이고 파일당 함수
 * 한도(11)에 닿아 있다. 여기 둘은 읽은 값을 **도메인 어휘로 옮기는** 변환이라 경계가 다르다.
 */

/**
 * 도메인 값 객체의 생성 규칙(`require`)을 **400 으로** 옮긴다. 형식이 틀린 요청 값(길이 상한 · 부호 ·
 * 표기 규칙)이 `IllegalArgumentException` 으로 새면 기본 처리기가 **500** 을 낸다 — 요청 탓을 서버
 * 탓으로 말하는 것이다. 그 예외 **하나만** 옮기고 나머지는 그대로 던진다(`JsonValueReaders` 의 시각
 * 판독과 같은 관례). 메시지는 응답에 실리지 않는다.
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

/** 불리언 — Jackson 의 강제 변환(`"true"` · `1`)을 받지 않는다(표기 자체를 판정한다). */
internal fun JsonNode.requiredBoolean(key: String): Boolean {
    val node = requiredNode(key)
    if (!node.isBoolean) throw InvalidEditRequestException("$key 는 JSON 불리언이어야 한다")
    return node.booleanValue()
}
