package bidvector.adapters.koneps

/**
 * KONEPS 응답 envelope 파싱 전용 최소 JSON 값·파서(⑥, D-3B-2) — 새 외부 좌표(Jackson·
 * kotlinx.serialization 등)를 이 slice 만을 위해 들이지 않는다. `architecture-policy.properties`
 * `group.forbidden` 이 jackson·kotlinx-serialization·gson·org.json 을 domain 층에서 금지하는
 * 결의를 이 adapters 패키지도 자체 적용한다 — JDK `java.base` 는 공개 JSON API 를 갖지 않고,
 * envelope 형태(object/array/string/number/boolean/null, 얕은 중첩)가 좁아 범용 파서를
 * 새로 도입할 측정된 필요가 없다(CLAUDE.md 「무거운 도구는 측정된 필요 없이 도입하지
 * 않는다」). 이 파서는 RFC 8259 완전 준수를 목표로 하지 않는다.
 */
internal sealed interface JsonValue {
    data class JsonObject(
        val fields: Map<String, JsonValue>,
    ) : JsonValue {
        fun member(name: String): JsonValue? = fields[name]
    }

    data class JsonArray(
        val items: List<JsonValue>,
    ) : JsonValue

    data class JsonString(
        val value: String,
    ) : JsonValue

    /** 원문 숫자 텍스트를 그대로 보존한다 — `Double` 로 접지 않는다(api-type-policy, ⑥). */
    data class JsonNumber(
        val raw: String,
    ) : JsonValue

    data class JsonBool(
        val value: Boolean,
    ) : JsonValue

    data object JsonNull : JsonValue
}

internal fun JsonValue?.asObject(): JsonValue.JsonObject? = this as? JsonValue.JsonObject

internal fun JsonValue?.asArray(): JsonValue.JsonArray? = this as? JsonValue.JsonArray

internal fun JsonValue?.asStringOrNull(): String? = (this as? JsonValue.JsonString)?.value

internal fun JsonValue?.asIntOrNull(): Int? =
    when (this) {
        is JsonValue.JsonNumber -> raw.toIntOrNull()
        is JsonValue.JsonString -> value.toIntOrNull()
        else -> null
    }

/** 값을 버리지 않는 원문 보존 재직렬화(감사용) — 표준 JSON 재현을 보장하지 않는다(escape 최소). */
internal fun JsonValue.render(): String =
    when (this) {
        is JsonValue.JsonString -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is JsonValue.JsonNumber -> raw
        is JsonValue.JsonBool -> value.toString()
        JsonValue.JsonNull -> "null"
        is JsonValue.JsonArray -> items.joinToString(",", "[", "]") { it.render() }
        is JsonValue.JsonObject -> fields.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${v.render()}" }
    }

internal class JsonParseException(
    message: String,
) : RuntimeException(message)

internal object KonepsJsonParser {
    /**
     * `maxDepth`(정책값, M-4) — object/array 중첩 상한. 상한 없이 깊게 중첩된 입력을
     * 그대로 재귀 판독하면 `StackOverflowError`(예외가 아니라 `Throwable`)가 나는데
     * `parseKonepsEnvelope` 의 `runCatching` 이 그것까지 삼켜 `StructureFailure` 로 접는다
     * — 오늘도 봉쇄는 되지만(예외가 port 밖으로 안 나간다) 실제 JVM 스택 한계 근처까지
     * 재귀를 태우는 것 자체가 취약하다. 상한을 미리 걸어 훨씬 이른 지점에서 통제된
     * `JsonParseException` 으로 끝낸다.
     */
    fun parse(
        text: String,
        maxDepth: Int,
    ): JsonValue {
        val reader = JsonReader(text, maxDepth)
        reader.cursor.skipWhitespace()
        val value = reader.readValue()
        reader.cursor.skipWhitespace()
        if (!reader.cursor.atEnd()) throw JsonParseException("JSON 뒤에 남은 문자가 있다 at ${reader.position}")
        return value
    }
}

/**
 * 문자 커서 — 위치 이동·1문자 판독만 안다(문법 규칙은 모른다). 문법 판독([JsonReader])과
 * 책임을 나눈 이유는 detekt `TooManyFunctions`(클래스당 함수 상한 11)다 — 한 클래스에
 * 다 두면 14개로 넘는다.
 */
private class CharCursor(
    private val text: String,
) {
    var position: Int = 0
        private set

    fun atEnd(): Boolean = position >= text.length

    fun peek(): Char {
        if (atEnd()) throw JsonParseException("예상치 못한 입력 끝")
        return text[position]
    }

    fun advance(): Char = peek().also { position++ }

    fun skipWhitespace() {
        while (!atEnd() && text[position].isWhitespace()) position++
    }

    fun expect(c: Char) {
        if (atEnd() || text[position] != c) {
            throw JsonParseException("'$c' 를 기대했으나 위치 $position 에서 다르다")
        }
        position++
    }

    fun substring(
        start: Int,
        end: Int,
    ): String = text.substring(start, end)
}

/** 커서 기반 recursive-descent 판독기(문법 규칙) — 문자 이동은 [CharCursor]에 위임한다. */
private class JsonReader(
    text: String,
    private val maxDepth: Int,
) {
    // 비공개가 아니다 — 같은 파일의 KonepsJsonParser 가 직접 위임 호출한다(atEnd/
    // skipWhitespace 래퍼 함수 둘을 없애 detekt TooManyFunctions 상한 11을 지킨다).
    val cursor = CharCursor(text)
    private var depth = 0

    val position: Int get() = cursor.position

    fun readValue(): JsonValue {
        cursor.skipWhitespace()
        return when (cursor.peek()) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonValue.JsonString(readString())
            't' -> readLiteral("true", JsonValue.JsonBool(true))
            'f' -> readLiteral("false", JsonValue.JsonBool(false))
            'n' -> readLiteral("null", JsonValue.JsonNull)
            else -> readNumber()
        }
    }

    private fun readLiteral(
        literal: String,
        value: JsonValue,
    ): JsonValue {
        val start = cursor.position
        for (index in literal.indices) {
            if (cursor.atEnd() || cursor.advance() != literal[index]) {
                throw JsonParseException("'$literal' 리터럴을 기대했다 at $start")
            }
        }
        return value
    }

    /**
     * 객체 안 중복 키는 **마지막 값이 승리**한다(L-5, `LinkedHashMap` 대입이 자연히 그렇게
     * 한다) — `resultCode` 가 중복이어도 같은 규칙이라 마지막 값이 미지 코드면
     * fail-safe(비재시도) 방향으로 접힌다. RFC 8259 는 중복 키의 처리를 정하지 않는다 —
     * 이 선택을 명시로 남긴다(우연이 아니다).
     */
    private fun readObject(): JsonValue.JsonObject {
        enterNesting()
        try {
            cursor.expect('{')
            val fields = LinkedHashMap<String, JsonValue>()
            cursor.skipWhitespace()
            var more = cursor.peek() != '}'
            if (!more) cursor.advance()
            while (more) {
                cursor.skipWhitespace()
                val key = readString()
                cursor.skipWhitespace()
                cursor.expect(':')
                fields[key] = readValue()
                cursor.skipWhitespace()
                more =
                    when (cursor.advance()) {
                        ',' -> true
                        '}' -> false
                        else -> throw JsonParseException("객체 안에서 ',' 또는 '}' 를 기대했다 at ${cursor.position}")
                    }
            }
            return JsonValue.JsonObject(fields)
        } finally {
            depth--
        }
    }

    private fun readArray(): JsonValue.JsonArray {
        enterNesting()
        try {
            cursor.expect('[')
            val items = mutableListOf<JsonValue>()
            cursor.skipWhitespace()
            var more = cursor.peek() != ']'
            if (!more) cursor.advance()
            while (more) {
                items += readValue()
                cursor.skipWhitespace()
                more =
                    when (cursor.advance()) {
                        ',' -> true
                        ']' -> false
                        else -> throw JsonParseException("배열 안에서 ',' 또는 ']' 를 기대했다 at ${cursor.position}")
                    }
            }
            return JsonValue.JsonArray(items)
        } finally {
            depth--
        }
    }

    private fun enterNesting() {
        depth++
        if (depth > maxDepth) {
            throw JsonParseException("JSON 중첩 깊이가 정책 상한을 넘었다(M-4): depth=$depth max=$maxDepth")
        }
    }

    private fun readString(): String {
        cursor.expect('"')
        val builder = StringBuilder()
        var closed = false
        while (!closed) {
            when (val c = cursor.advance()) {
                '"' -> closed = true
                '\\' -> builder.append(readEscape())
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun readEscape(): Char =
        when (val esc = cursor.advance()) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> FORM_FEED
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> throw JsonParseException("알 수 없는 escape '\\$esc' at ${cursor.position}")
        }

    private fun readUnicodeEscape(): Char {
        val start = cursor.position
        repeat(UNICODE_ESCAPE_DIGITS) {
            if (cursor.atEnd()) throw JsonParseException("불완전한 \\u escape at $start")
            cursor.advance()
        }
        return cursor.substring(start, start + UNICODE_ESCAPE_DIGITS).toInt(RADIX_HEX).toChar()
    }

    private fun readNumber(): JsonValue.JsonNumber {
        val start = cursor.position
        if (!cursor.atEnd() && cursor.peek() == '-') cursor.advance()
        consumeDigits()
        if (!cursor.atEnd() && cursor.peek() == '.') {
            cursor.advance()
            consumeDigits()
        }
        if (!cursor.atEnd() && (cursor.peek() == 'e' || cursor.peek() == 'E')) {
            cursor.advance()
            if (!cursor.atEnd() && (cursor.peek() == '+' || cursor.peek() == '-')) cursor.advance()
            consumeDigits()
        }
        if (cursor.position == start) throw JsonParseException("숫자를 기대했다 at $start")
        return JsonValue.JsonNumber(cursor.substring(start, cursor.position))
    }

    private fun consumeDigits() {
        while (!cursor.atEnd() && cursor.peek().isDigit()) cursor.advance()
    }

    private companion object {
        const val UNICODE_ESCAPE_DIGITS = 4
        const val RADIX_HEX = 16
        const val FORM_FEED = '\u000C'
    }
}
