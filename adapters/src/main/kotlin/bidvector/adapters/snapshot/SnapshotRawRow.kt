package bidvector.adapters.snapshot

import bidvector.adapters.koneps.JsonValue
import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.procurement.BusinessDivision
import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.RawKey
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 원문 일시의 해석 구역 — 계약이 `ASSUME_KST` 를 선언한다(`OPEN-3A-SOURCE-TZ` 는 열려 있다). */
private val SOURCE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

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

    /** 원문 단위 %(부호가 문자열 안에 있다: `-3`/`+3`) → fraction. 선행 `+` 를 허용한다(P-5 §3.2). */
    fun rateOf(concept: FieldConcept): BigDecimal? =
        textOf(concept)
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

    fun instantOf(concept: FieldConcept): Instant? =
        textOf(concept)?.let { raw ->
            runCatching { LocalDateTime.parse(raw.trim().replace(' ', 'T')).atZone(SOURCE_ZONE).toInstant() }
                .getOrNull()
        }
}

private fun parseAmount(raw: String): BigDecimal? = runCatching { BigDecimal(raw.trim().replace(",", "")) }.getOrNull()

/** canonical `notice` 한 행 — 개찰 축 응답에 **없는** 축(대분류·하한율·마감·낙찰방법)이 여기서 온다. */
internal class CanonicalNotice(
    /** 스냅숏의 닫힌 셋 값(`SERVICE` …) — DB 는 **문서 라벨**(`용역`)을 담으므로 어휘 변환이 필요하다. */
    val division: String,
    val noticedOn: LocalDate?,
    val openedOn: LocalDate,
    val bidCloseAt: Instant?,
    val floorRate: BigDecimal?,
    val awardMethodCode: String?,
    val awardMethodName: String?,
    val awardMethodStandard: String?,
    val applicationBasis: String?,
    val procurementClassCode: String?,
    val demandAgencyCode: String?,
)

/** 어휘 밖 라벨은 `null` — 대분류를 지어내지 않는다(그 공고는 행이 만들어지지 않는다). */
internal fun canonicalNoticeOf(
    rows: ResultSet,
    policy: KonepsCollectionPolicyData,
): CanonicalNotice? {
    val division = BusinessDivision.fromLabel(rows.getString("business_division").orEmpty()) ?: return null
    val listRow = RawRow(parseFields(rows.getString("notice_list_fields")), policy)
    return CanonicalNotice(
        division = division.name,
        noticedOn = rows.getObject("noticed_on", LocalDate::class.java),
        openedOn = rows.getObject("opened_on", LocalDate::class.java) ?: LocalDate.EPOCH,
        bidCloseAt = rows.getTimestamp("deadline_at")?.toInstant(),
        floorRate = rows.getBigDecimal("floor_rate_fraction"),
        awardMethodCode = listRow.textOf(FieldConcept.AWARD_METHOD_CODE),
        awardMethodName = listRow.textOf(FieldConcept.AWARD_METHOD_NAME),
        awardMethodStandard = listRow.textOf(FieldConcept.AWARD_METHOD_APPLICATION_STANDARD),
        applicationBasis = listRow.textOf(FieldConcept.APPLICATION_BASIS_CONTENT),
        procurementClassCode = listRow.textOf(FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE),
        demandAgencyCode = listRow.textOf(FieldConcept.DEMAND_AGENCY_CODE),
    )
}

/**
 * 개찰 축 원문 — 공고 식별자 둘을 JSONB 에서 꺼내 묶는다. 기간은 **관측 시각**으로 자른다(이 갈래가
 * 적재한 창을 그대로 뽑는다).
 *
 * 존재 판정에 JSONB `?` 연산자를 **쓰지 않는다** — JDBC 가 그것을 바인드 자리로 읽어 「매개 변수 3 에
 * 값이 없다」로 떨어진다(실측). `->> … IS NOT NULL` 이 같은 일을 하면서 그 충돌이 없다.
 */
internal const val OBSERVATION_SQL =
    """
    SELECT source_endpoint,
           payload_fields ->> 'bidNtceNo' AS notice_number,
           payload_fields ->> 'bidNtceOrd' AS notice_round,
           payload_fields::text AS payload_fields
      FROM raw_observation
     WHERE observed_at >= ?::date AND observed_at < ?::date
       AND payload_fields ->> 'bidNtceNo' IS NOT NULL
     ORDER BY inserted_at
    """

/**
 * canonical 축 — 대분류·하한율·마감. 낙찰방법·적용기준·분류·수요기관은 `notice` 표에 칸이 없어
 * 공고 목록 **원문 관측**에서 읽는다(마이그레이션을 만들지 않는다, D-6G-1).
 *
 * 그 값들을 SQL 이 `->>` 로 꺼내지 **않는다** — 그러면 raw 키 문자열이 이 파일에 박히고, 키를 아는 것은
 * 계약의 몫이라는 규율이 깨진다(6F-9 키 리터럴 게이트가 실제로 잡았다). 원문 객체를 통째로 가져와
 * [RawRow] 가 개념으로 읽는다. SQL 에 남는 raw 키는 **공고 식별자 둘**뿐이고, 그것은 조인 축이라
 * 달리 표현할 자리가 없다(알려진 제한).
 */
internal const val NOTICE_SQL =
    """
    SELECT n.notice_number,
           n.notice_round,
           n.business_division,
           n.deadline_at,
           n.floor_rate_fraction,
           NULL::date  AS noticed_on,
           o.opened_on AS opened_on,
           r.payload_fields::text AS notice_list_fields
      FROM notice n
      LEFT JOIN LATERAL (
            SELECT payload_fields
              FROM raw_observation
             WHERE source_endpoint = 'NOTICE_LIST'
               AND payload_fields ->> 'bidNtceNo' = n.notice_number
               AND payload_fields ->> 'bidNtceOrd' = n.notice_round
             ORDER BY observed_at DESC
             LIMIT 1) r ON TRUE
      LEFT JOIN LATERAL (
            SELECT (actual_opening_at AT TIME ZONE 'Asia/Seoul')::date AS opened_on
              FROM opening_result
             WHERE notice_number = n.notice_number
               AND notice_round = n.notice_round) o ON TRUE
     WHERE n.business_division IS NOT NULL
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
