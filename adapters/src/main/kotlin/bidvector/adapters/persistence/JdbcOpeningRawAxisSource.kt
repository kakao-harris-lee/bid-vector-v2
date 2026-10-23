package bidvector.adapters.persistence

import bidvector.adapters.snapshot.jsonbKeyOf
import bidvector.adapters.snapshot.parseFields
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.OPENING_PROMOTION_AXES
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningAxisGroup
import bidvector.procurement.OpeningAxisObservation
import bidvector.procurement.OpeningRawAxisRead
import bidvector.procurement.OpeningRawAxisSource
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawValue
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * [OpeningRawAxisSource] JDBC 구현(M7/7C⑤-0) — **읽기만** 한다. DML 이 없고, 원문 표는 DB 트리거로
 * 추가 전용이라 이 어댑터가 고쳐 쓸 길이 애초에 없다.
 *
 * 질의 조건 셋.
 * - **축**: [OPENING_PROMOTION_AXES] — 사상이 선언한 축 집합 그대로다(두 번째 목록을 두지 않는다).
 * - **마스킹 표지**: 표지가 찍힌 행은 **제외**한다(D-7C5P-3). 그 행을 승격하면 업체 식별 칸만 빈
 *   반쪽 canonical 이 생기고, 마스킹 전에 승격된 행과 구별되지 않는다.
 * - **적재 시각 하한**: 보존 창이다(D-7C5P-3). 승격 지연이 그 창 이내로 묶여, 업체 상호의 canonical
 *   잔존 상한이 「최초 수집 + 보존 창 + 승격 지연」 이하가 된다.
 *
 * 제외한 행은 **수를 공시한다** — 조용히 지나가면 「왜 승격이 비었나」를 물을 자리가 없다. 계수는
 * 원문을 가져오지 않는 집계 질의에서 나온다(제외 대상의 payload 를 메모리에 올리지 않는다).
 *
 * SQL 에 남는 원문 키는 **공고 식별자 둘**뿐이고 그 이름은 이 파일이 짓지 않는다
 * ([NOTICE_NUMBER_RAW_KEY]·[NOTICE_ROUND_RAW_KEY]) — 나머지 값은 계약을 아는 사상 함수가 읽는다.
 */
class JdbcOpeningRawAxisSource(
    private val dataSource: DataSource,
) : OpeningRawAxisSource {
    override fun readOpeningAxes(collectedSince: Instant): OpeningRawAxisRead =
        dataSource.connection.use { connection ->
            // 커서로 흘린다 — Postgres 드라이버는 autoCommit 이 켜진 채로는 fetchSize 를 무시한다.
            // 읽기만 하므로 커밋하지 않는다(커넥션이 닫히며 롤백된다).
            connection.autoCommit = false
            val excluded = connection.countExcluded(collectedSince)
            val eligible = connection.readEligible(collectedSince)
            OpeningRawAxisRead(
                groups = eligible.groups,
                unkeyedRows = eligible.unkeyedRows,
                maskedRows = excluded.masked,
                expiredRows = excluded.expired,
            )
        }

    private fun Connection.countExcluded(collectedSince: Instant): ExcludedCounts =
        prepareStatement(COUNT_EXCLUDED_SQL).use { statement ->
            statement.setTimestamp(1, Timestamp.from(collectedSince))
            statement.setArray(2, openingAxisArray())
            statement.executeQuery().use { rows ->
                if (!rows.next()) ExcludedCounts(0, 0) else ExcludedCounts(rows.getInt("masked"), rows.getInt("expired"))
            }
        }

    private fun Connection.readEligible(collectedSince: Instant): EligibleRows =
        prepareStatement(ELIGIBLE_SQL).use { statement ->
            statement.fetchSize = OBSERVATION_FETCH_SIZE
            statement.setArray(1, openingAxisArray())
            statement.setTimestamp(2, Timestamp.from(collectedSince))
            statement.executeQuery().use(::groupRows)
        }

    private fun Connection.openingAxisArray() =
        createArrayOf("text", OPENING_PROMOTION_AXES.map { it.name }.toTypedArray())
}

/**
 * 키가 서지 않는 행은 **그 행만 버리고 수를 공시한다**(D-6G2d-8 ⓐ 와 같은 규율) — 원문은 추가
 * 전용이라 지울 수도 없고, 한 행의 형태 때문에 승격 전체가 멈추면 안 된다. **무방비로 정규화하지
 * 않는다**: 공고번호가 빈 문자열인 행에 `NoticeNumber.of` 를 쓰면 그 한 행이 던진다.
 */
private fun groupRows(rows: ResultSet): EligibleRows {
    val byNotice = linkedMapOf<NoticeId, MutableList<OpeningAxisObservation>>()
    var unkeyed = 0
    while (rows.next()) {
        val id = noticeIdOf(rows)
        val endpoint = endpointOf(rows)
        if (id == null || endpoint == null) {
            unkeyed++
            continue
        }
        byNotice.getOrPut(id) { mutableListOf() } += observationOf(rows, endpoint)
    }
    return EligibleRows(byNotice.map { (id, observations) -> OpeningAxisGroup(id, observations) }, unkeyed)
}

private fun noticeIdOf(rows: ResultSet): NoticeId? {
    val number = rows.getString("notice_number")?.let(NoticeNumber::ofOrNull) ?: return null
    val round = rows.getString("notice_round")?.let { raw -> runCatching { NoticeRound.of(raw) }.getOrNull() }
    return round?.let { NoticeId(number, it) }
}

private fun endpointOf(rows: ResultSet): SourceEndpoint? =
    runCatching { SourceEndpoint.valueOf(rows.getString("source_endpoint")) }.getOrNull()

/**
 * 투영을 원문 관측으로 되살린다 — **값 열람은 하지 않는다.** 명시 `null` 과 키 부재를 구분해 싣고
 * (사상이 그 구분을 쓴다), `sourceText` 는 싣지 않는다(이 경로는 원문 정본을 읽지 않는다).
 * `sourceDivision` 은 **명시적으로 `null`** 이다 — 개찰 축 오퍼레이션은 업무 대분류를 정하지 않는다.
 */
private fun observationOf(
    rows: ResultSet,
    endpoint: SourceEndpoint,
): OpeningAxisObservation =
    OpeningAxisObservation(
        RawNoticeObservation.ofRawValues(
            fields = rawValuesOf(rows.getString("payload_fields")),
            sourceEndpoint = endpoint,
            observedAt = rows.getTimestamp("observed_at").toInstant(),
            sourceText = null,
            sourceDivision = null,
        ),
        ObservationKey(rows.getString("observation_key")),
    )

/** 명시 `null` 과 키 부재를 구분해 싣는다 — 사상이 그 구분을 쓴다. */
private fun rawValuesOf(payloadFields: String?): Map<RawKey, RawValue> =
    parseFields(payloadFields).entries.associate { (name, value) ->
        RawKey(name) to (value?.let(RawValue::Present) ?: RawValue.ExplicitNull)
    }

private class EligibleRows(
    val groups: List<OpeningAxisGroup>,
    val unkeyedRows: Int,
)

private class ExcludedCounts(
    val masked: Int,
    val expired: Int,
)

private const val OBSERVATION_FETCH_SIZE = 500

/**
 * 제외한 행의 계수 — **원문을 가져오지 않는다**(집계뿐이다). 마스킹 표지가 있는 행과, 표지는 없으나
 * 보존 창보다 오래된 행을 따로 센다: 처방이 다르다(앞은 영구 제외, 뒤는 창을 지난 적재다).
 */
private val COUNT_EXCLUDED_SQL =
    """
    SELECT count(*) FILTER (WHERE pii_masked_at IS NOT NULL) AS masked,
           count(*) FILTER (WHERE pii_masked_at IS NULL AND inserted_at < ?) AS expired
      FROM raw_observation
     WHERE source_endpoint = ANY(?)
    """

private val ELIGIBLE_SQL =
    """
    SELECT observation_key,
           source_endpoint,
           payload_fields ->> '${jsonbKeyOf(NOTICE_NUMBER_RAW_KEY)}' AS notice_number,
           payload_fields ->> '${jsonbKeyOf(NOTICE_ROUND_RAW_KEY)}' AS notice_round,
           payload_fields::text AS payload_fields,
           observed_at
      FROM raw_observation
     WHERE source_endpoint = ANY(?)
       AND pii_masked_at IS NULL
       AND inserted_at >= ?
     ORDER BY observation_key
    """
