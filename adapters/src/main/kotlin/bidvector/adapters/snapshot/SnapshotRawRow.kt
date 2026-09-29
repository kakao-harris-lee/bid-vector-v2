package bidvector.adapters.snapshot

import bidvector.adapters.koneps.JsonValue
import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.procurement.BusinessDivision
import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.RawKey
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 원문 일시의 해석 구역 — 계약이 `ASSUME_KST` 를 선언한다(`OPEN-3A-SOURCE-TZ` 는 열려 있다). */
private val SOURCE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * SQL 리터럴 자리에 놓을 수 있는 원문 키의 형태 — **아래 SQL 상수보다 먼저 서야 한다.** 최상위
 * 프로퍼티는 파일 순서로 초기화되므로, SQL 뒤에 두면 그 초기화가 `null` 정규식을 만난다(실측).
 */
private val SQL_SAFE_RAW_KEY = Regex("[A-Za-z][A-Za-z0-9_]*")

private const val PERCENT_DIVISOR = 100

/**
 * `raw_observation.payload_fields` 한 행 — **개념으로만 읽는다.** raw 키 문자열이 이 파일에 없고
 * 계약 레지스트리가 개념→키를 정한다(키를 아는 것은 계약의 몫이다).
 */
internal class RawRow(
    private val fields: Map<String, String?>,
    private val policy: KonepsCollectionPolicyData,
) {
    fun textOf(concept: FieldConcept): String? =
        policy.fieldContracts
            .contractsFor(concept)
            .firstNotNullOfOrNull { contract -> fields[contract.rawName.name]?.takeIf { it.isNotBlank() } }

    fun amountOf(concept: FieldConcept): BigDecimal? = textOf(concept)?.let { parseAmount(it) }

    /**
     * 한 개념을 **여러 raw 키**가 나르는 축의 값 전부(추첨번호 `drwtNo1`·`drwtNo2`). [textOf] 는 첫
     * 값만 내므로 이 축에는 쓸 수 없다 — 둘째 추첨번호가 조용히 사라진다.
     */
    fun allTextOf(concept: FieldConcept): List<String> =
        policy.fieldContracts
            .contractsFor(concept)
            .mapNotNull { contract -> fields[contract.rawName.name]?.takeIf { it.isNotBlank() } }

    /** 원문 단위 %(부호가 문자열 안에 있다: `-3`/`+3`) → fraction. 선행 `+` 를 허용한다(P-5 §3.2). */
    fun rateOf(concept: FieldConcept): BigDecimal? =
        textOf(concept)
            ?.trim()
            ?.removePrefix("+")
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?.divide(BigDecimal(PERCENT_DIVISOR))

    fun countOf(concept: FieldConcept): Int? = textOf(concept)?.trim()?.toIntOrNull()

    /** `Y`/`N` 만 참·거짓이다 — 그 밖의 값과 부재는 `null`(판정 불가). 지어내지 않는다. */
    fun predicateOf(concept: FieldConcept): Boolean? =
        when (textOf(concept)?.trim()?.uppercase()) {
            "Y" -> true
            "N" -> false
            else -> null
        }

    /** 일시 원문의 **날짜 부분**(KST 해석) — 개찰일·공고일처럼 날짜만 쓰는 축. */
    fun localDateOf(concept: FieldConcept): LocalDate? = instantOf(concept)?.atZone(SOURCE_ZONE)?.toLocalDate()

    fun instantOf(concept: FieldConcept): Instant? =
        textOf(concept)?.let { raw ->
            runCatching { LocalDateTime.parse(raw.trim().replace(' ', 'T')).atZone(SOURCE_ZONE).toInstant() }
                .getOrNull()
        }
}

private fun parseAmount(raw: String): BigDecimal? = runCatching { BigDecimal(raw.trim().replace(",", "")) }.getOrNull()

/**
 * canonical `notice` 한 행 — 개찰 축 응답에 **없는** 축(대분류·하한율·마감)만 여기서 온다.
 * 낙찰방법·분류·수요기관·공고일은 `notice` 표에 칸이 없어 **공고 목록 원문 관측**에서 읽는다
 * (같은 조회가 이미 가져오므로 조인이 필요 없다 — LATERAL 을 없앴다).
 */
internal class CanonicalNotice(
    val division: String,
    val bidCloseAt: Instant?,
    val floorRate: BigDecimal?,
)

/** 어휘 밖 라벨은 `null` — 대분류를 지어내지 않는다(그 공고는 행이 만들어지지 않는다). */
internal fun canonicalNoticeOf(rows: ResultSet): CanonicalNotice? {
    val division = BusinessDivision.fromLabel(rows.getString("business_division").orEmpty()) ?: return null
    return CanonicalNotice(
        division = division.name,
        bidCloseAt = rows.getTimestamp("deadline_at")?.toInstant(),
        floorRate = rows.getBigDecimal("floor_rate_fraction"),
    )
}

/**
 * 개찰 축 원문 — 공고 식별자 둘을 JSONB 에서 꺼내 묶는다. 기간은 **관측 시각**으로 자른다(이 갈래가
 * 적재한 창을 그대로 뽑는다).
 *
 * 존재 판정에 JSONB `?` 연산자를 **쓰지 않는다** — JDBC 가 그것을 바인드 자리로 읽어 「매개 변수 3 에
 * 값이 없다」로 떨어진다(실측). `->> … IS NOT NULL` 이 같은 일을 하면서 그 충돌이 없다.
 *
 * **관측 창을 걸지 않는다**(D-6G-68). 범위를 정하는 것은 표본 목록과 시도 원장이다 — 표본 밖은
 * 아래에서 버려지고, 표본 안에서 어느 걷기를 쓸지는 원장의 AXIS 줄이 가리킨다. 창을 함께 걸면
 * **원장에는 걸리지 않는 창**이 되어, 창 밖에서 다시 걸은 축의 행이 보이지 않고 그 앞의 잘린 걷기가
 * 마지막으로 보인다(vr r5 H-1 probe W5). 앞 판이 목록 축만 창에서 뺀 이유(D-6G-54 — 목록 적재가
 * 개찰보다 앞서는 것은 정상이다)는 상세 축에도 같은 힘으로 적용된다.
 *
 * 공고 식별자 둘의 **키 이름은 여기서 짓지 않는다**(vr r4 L-11) — [NOTICE_NUMBER_RAW_KEY]·
 * [NOTICE_ROUND_RAW_KEY] 가 필드 계약과 같은 정의를 준다. 리터럴로 적으면 계약이 바뀌어도 이 문은
 * 옛 키를 읽고, 두 쪽의 공고 키가 조용히 갈린다.
 */
internal val OBSERVATION_SQL =
    """
    SELECT source_endpoint,
           payload_fields ->> '${jsonbKeyOf(NOTICE_NUMBER_RAW_KEY)}' AS notice_number,
           payload_fields ->> '${jsonbKeyOf(NOTICE_ROUND_RAW_KEY)}' AS notice_round,
           payload_fields::text AS payload_fields,
           observed_at
      FROM raw_observation
     WHERE payload_fields ->> '${jsonbKeyOf(NOTICE_NUMBER_RAW_KEY)}' IS NOT NULL
     ORDER BY inserted_at
    """

/**
 * 원문 키를 SQL 리터럴 자리에 놓기 전에 형태를 요구한다 — 따옴표나 공백이 든 키는 문을 갈라 놓는다.
 * 오늘 두 키는 상수라 이 검사는 기동 시 한 번 돌고, 새 키가 계약에 들어오는 날 그 자리에서 멈춘다.
 */
internal fun jsonbKeyOf(key: RawKey): String =
    key.name.also { require(SQL_SAFE_RAW_KEY.matches(it)) { "SQL 에 놓을 수 없는 원문 키다" } }

/**
 * canonical 축 — 대분류·하한율·마감. 낙찰방법·적용기준·분류·수요기관은 `notice` 표에 칸이 없어
 * 공고 목록 **원문 관측**에서 읽는다(마이그레이션을 만들지 않는다, D-6G-1).
 *
 * 그 값들을 SQL 이 `->>` 로 꺼내지 **않는다** — 그러면 raw 키 문자열이 이 파일에 박히고, 키를 아는 것은
 * 계약의 몫이라는 규율이 깨진다(6F-9 키 리터럴 게이트가 실제로 잡았다). 원문 객체를 통째로 가져와
 * [RawRow] 가 개념으로 읽는다. SQL 에 남는 raw 키는 **공고 식별자 둘**뿐이고, 그것은 조인 축이라
 * 달리 표현할 자리가 없다 — 다만 그 둘의 **이름은 이 파일이 짓지 않는다**(vr r4 L-11,
 * [jsonbKeyOf] · [bidvector.procurement.NOTICE_NUMBER_RAW_KEY]).
 */
internal const val NOTICE_SQL =
    """
    SELECT n.notice_number,
           n.notice_round,
           n.business_division,
           n.deadline_at,
           n.floor_rate_fraction
      FROM notice n
     WHERE n.business_division IS NOT NULL
       AND n.notice_number = ANY (?)
    """

/**
 * `payload_fields` 는 우리가 쓴 **평평한** 객체다(계약 등재 키 → 문자열 또는 null). 판독은 이 모듈에
 * 이미 있는 파서를 그대로 쓴다 — 같은 모듈의 `internal` 이라 새로 짜지 않는다(중복 금지). 깊이 상한은
 * 우리가 쓴 값이라 평평하지만, 상한을 끄지 않고 정책 기본값을 그대로 넘긴다.
 */
internal fun parseFields(json: String?): Map<String, String?> {
    val parsed =
        json?.takeIf { it.isNotBlank() }?.let { text ->
            runCatching { KonepsJsonParser.parse(text, PAYLOAD_MAX_DEPTH) }.getOrNull()
        }
    val fields = (parsed as? JsonValue.JsonObject)?.fields.orEmpty()
    return fields.mapValues { (_, value) -> (value as? JsonValue.JsonString)?.value }
}

private const val PAYLOAD_MAX_DEPTH = 8
