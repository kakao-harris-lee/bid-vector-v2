package bidvector.adapters.snapshot

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private const val ESCAPE_THRESHOLD = 0x20

/**
 * 스냅숏 JSON 의 최소 직렬화(M6/6G D-6G-2) — **쓰는 형태를 우리가 정한 자리**라 범용 라이브러리를
 * 들이지 않는다(이 저장소는 Jackson 을 전이 의존으로만 두고 직접 선언하지 않는다).
 *
 * 대신 **문자열 이스케이프는 대충 하지 않는다**: 자유텍스트 칸(낙찰방법적용기준·적용기준내용)에
 * 따옴표·역슬래시·개행·제어문자가 실려 올 수 있고, 하나라도 새면 그 줄이 JSON 이 아니게 되어 판독이
 * 스냅숏 전체를 거부한다(소비 쪽이 fail-closed 다).
 *
 * 값은 **선언 순서 그대로** 쓴다 — 키 순서가 계약이라 맵의 순회 순서에 맡기지 않는다.
 */
internal sealed interface SnapshotJson {
    data class Text(
        val value: String,
    ) : SnapshotJson

    data class Number(
        val literal: String,
    ) : SnapshotJson

    data class Bool(
        val value: Boolean,
    ) : SnapshotJson

    data object Null : SnapshotJson

    data class Arr(
        val items: List<SnapshotJson>,
    ) : SnapshotJson

    /** 키-값 **목록**이다(맵이 아니다) — 선언 순서가 출력 순서다. */
    data class Obj(
        val fields: List<Pair<String, SnapshotJson>>,
    ) : SnapshotJson
}

internal fun SnapshotJson.render(): String =
    when (this) {
        is SnapshotJson.Text -> quote(value)
        is SnapshotJson.Number -> literal
        is SnapshotJson.Bool -> value.toString()
        SnapshotJson.Null -> "null"
        is SnapshotJson.Arr -> items.joinToString(",", "[", "]") { it.render() }
        is SnapshotJson.Obj -> fields.joinToString(",", "{", "}") { (key, value) -> "${quote(key)}:${value.render()}" }
    }

/**
 * RFC 8259 문자열. 반드시 이스케이프해야 하는 것은 `"`·`\`·U+0000..U+001F 셋이고, 나머지는 원문 그대로
 * 둔다(UTF-8 로 쓴다). 제어문자에 짧은 표기가 있는 것은 그것을 쓰고 없으면 `\u00XX` 다 — 표기를 섞어
 * 쓰지 않으면 같은 입력이 같은 바이트를 낸다(재현성).
 */
private fun quote(raw: String): String {
    val out = StringBuilder(raw.length + 2)
    out.append('"')
    for (ch in raw) {
        when {
            ch == '"' -> out.append("\\\"")
            ch == '\\' -> out.append("\\\\")
            ch == '\n' -> out.append("\\n")
            ch == '\r' -> out.append("\\r")
            ch == '\t' -> out.append("\\t")
            ch == '\b' -> out.append("\\b")
            ch == '\u000C' -> out.append("\\f")
            ch.code < ESCAPE_THRESHOLD -> out.append("\\u%04x".format(ch.code))
            else -> out.append(ch)
        }
    }
    out.append('"')
    return out.toString()
}

/**
 * 스칼라 칸을 JSON 값으로 — `null` 은 전부 [SnapshotJson.Null] 이다(칸을 빼지 않는다). 소비 쪽 판독이
 * fail-closed 라 **키가 빠지면 거부**된다: 「값이 없다」와 「칸이 없다」는 다른 것이고, 스냅숏이 말하는
 * 것은 앞쪽이다.
 */
internal fun jsonText(value: String?): SnapshotJson = value?.let { SnapshotJson.Text(it) } ?: SnapshotJson.Null

internal fun jsonBool(value: Boolean?): SnapshotJson = value?.let { SnapshotJson.Bool(it) } ?: SnapshotJson.Null

internal fun jsonCount(value: Int?): SnapshotJson =
    value?.let { SnapshotJson.Number(it.toString()) } ?: SnapshotJson.Null

internal fun jsonDate(value: LocalDate): SnapshotJson = SnapshotJson.Text(value.toString())

/** 날짜 결측은 **행 단위 제외**의 입력이다(v3) — 대체값을 지어내지 않는다. */
internal fun jsonDateOrNull(value: LocalDate?): SnapshotJson =
    value?.let { SnapshotJson.Text(it.toString()) } ?: SnapshotJson.Null

internal fun jsonInstant(value: Instant?): SnapshotJson =
    value?.let { SnapshotJson.Text(it.toString()) } ?: SnapshotJson.Null

/**
 * 금액은 **정수 리터럴**이다. **여기 오는 값은 이미 정수다**(D-6G2d-15) — 소수부의 처분은 조립이
 * 정하고 그 자리가 계수한다.
 *
 * 소수부가 여기까지 왔다면 **조립을 거치지 않은 생산자**가 생겼다는 뜻이고, 그것을 조용한 `null` 로
 * 접지 않는다(cr r4 ⑥). 조용히 접으면 그 경로의 결측이 계수되지 않은 채 스냅숏에 실리고, 판독은
 * 「값이 없는 칸」과 「셈에서 빠진 칸」을 구별할 수 없다 — 이 slice 가 고치고 있는 결함 계열 그대로다.
 * 반올림도 하지 않는다: 지어낸 값이 채점에 들어가는 것이 결측보다 나쁘다.
 */
internal fun jsonAmount(value: BigDecimal?): SnapshotJson {
    if (value == null) return SnapshotJson.Null
    check(value.isWonInteger()) { AMOUNT_NOT_TALLIED }
    return SnapshotJson.Number(value.setScale(0).toPlainString())
}

/**
 * 계수되지 않은 소수 금액 — 조립([AssemblyTally])을 지나지 않은 생산자만 낼 수 있다. 사람이 읽을
 * 문장이고, 값은 싣지 않는다(금액은 공시 대상이 아니다).
 */
internal const val AMOUNT_NOT_TALLIED = "금액의 소수부는 조립에서 계수하고 비운다 — 렌더에 소수가 오면 그 경로가 조립을 거치지 않았다"

/** 비율은 지수 표기 없이 — `1E-2` 는 JSON 으로 유효하지만 같은 값이 두 표기를 갖게 된다(재현성). */
internal fun jsonRate(value: BigDecimal?): SnapshotJson =
    value?.let { SnapshotJson.Number(it.toPlainString()) } ?: SnapshotJson.Null

internal fun <T> List<T>?.jsonArrayOrNull(map: (List<T>) -> List<SnapshotJson>): SnapshotJson =
    this?.let { SnapshotJson.Arr(map(it)) } ?: SnapshotJson.Null
