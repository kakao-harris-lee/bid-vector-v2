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

/**
 * **이름 값 하나의 거부 규칙 표**(M7/7C④ 2b r2) — 술어가 참이면 그 문구로 400 이다. 규칙을
 * `if … throw` 로 늘어놓지 않는 이유는 둘이다: 같은 규칙을 **두 표면**(단일 값 · 배열 원소)이
 * 공유하고, 규칙이 늘 때 늘어나는 것이 **표의 줄**이어야 한다(함수의 throw 가 아니라).
 *
 * 세 규칙의 근거:
 * - **공백뿐**: 「미설정」은 키 부재나 `null` 이다. 공백을 미설정으로 접으면 절 단위 교체에서 저장된
 *   값을 지우고 200 을 낸다(v F6 · CR M-2).
 * - **길이**: 값 타입 · 표 CHECK 와 같은 수를 쓴다(코드포인트 수).
 * - **쓸 수 없는 문자**: 유니코드 분류로 거부한다 — 제어(Cc) · 서식(Cf) · 대리(Cs) · 사용자 영역(Co) ·
 *   줄 구분(Zl) · 단락 구분(Zp). NUL 을 담은 텍스트는 PostgreSQL 이 SQLSTATE `22021` 로 거부하고 그
 *   예외는 어댑터의 class 23 번역 밖이라 **500** 이 된다 — 요청 탓을 서버 탓으로 말하는 자리다(v F5).
 *   제어만 보던 앞 판은 **보이지 않는 이름**(폭 0 문자만으로 된 상호) · `U+2028` 줄 구분 · 짝 없는
 *   대리를 통과시켰다(r2 v L-a). 할당되지 않은 코드포인트(Cn)는 **거부하지 않는다** — 유니코드가
 *   커지면 정상 입력을 막게 된다.
 */
internal fun String.requiringName(
    key: String,
    maxCodePoints: Int,
): String {
    val rejection =
        when {
            isBlank() -> "$key 는 공백뿐일 수 없다 — 미설정은 null 이다"
            codePointCount(0, length) > maxCodePoints -> "$key 는 $maxCodePoints 자를 넘을 수 없다"
            hasRejectedCharacter() -> "$key 에 쓸 수 없는 문자가 있다"
            else -> null
        }
    if (rejection != null) throw InvalidEditRequestException(rejection)
    return this
}

/**
 * 이름에 쓸 수 없는 유니코드 분류 — 눈에 보이지 않거나 줄을 끊거나 짝이 없는 코드포인트다. 분류로
 * 거부하므로 **문자를 열거하지 않는다**(열거에는 종점이 없다).
 */
private val REJECTED_CHARACTER_TYPES: Set<Int> =
    setOf(
        Character.CONTROL,
        Character.FORMAT,
        Character.SURROGATE,
        Character.PRIVATE_USE,
        Character.LINE_SEPARATOR,
        Character.PARAGRAPH_SEPARATOR,
    ).map(Byte::toInt).toSet()

private fun String.hasRejectedCharacter(): Boolean =
    codePoints().anyMatch { Character.getType(it) in REJECTED_CHARACTER_TYPES }

/**
 * 값이 **있으면** 위 규칙을 지켜야 한다 — 없거나 `null` 만 「미설정」이다.
 *
 * [optionalNonBlankText](7B)와 다른 점이 요지다: 그쪽은 공백뿐인 값을 `null` 로 접는데, **절 단위로
 * 교체하는** 표면에서 그 접힘은 저장돼 있던 값을 지우고 200 을 낸다(공백 입력이 조용히 「지우기」가
 * 된다). 같은 파일이 「받고서 쓰지 않는 입력은 계약 거짓말」이라며 등록 전용 키를 400 으로 거부하는
 * 규율과도 어긋난다.
 */
internal fun JsonNode.declaredText(
    key: String,
    maxCodePoints: Int,
): String? {
    val node = get(key)
    if (node == null || node.isNull) return null
    if (!node.isString) throw InvalidEditRequestException("$key 는 문자열이어야 한다")
    return node.stringValue().requiringName(key, maxCodePoints)
}

/** 불리언 — Jackson 의 강제 변환(`"true"` · `1`)을 받지 않는다(표기 자체를 판정한다). */
internal fun JsonNode.requiredBoolean(key: String): Boolean {
    val node = requiredNode(key)
    if (!node.isBoolean) throw InvalidEditRequestException("$key 는 JSON 불리언이어야 한다")
    return node.booleanValue()
}
