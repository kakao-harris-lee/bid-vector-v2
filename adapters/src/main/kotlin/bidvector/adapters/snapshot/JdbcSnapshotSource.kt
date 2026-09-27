package bidvector.adapters.snapshot

import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleList
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate
import javax.sql.DataSource

/**
 * dev DB → 실험 스냅숏 행(D-6G-2) — **두 출처를 잇는다.**
 *
 * - `raw_observation`: 6G 수집 갈래가 적재한 개찰 축 원문(예비가격 상세·개찰완료·기초금액·A값).
 *   이 갈래는 canonical 승격을 하지 않으므로 그 값들은 여기에만 있다(D-6G-1).
 * - `notice`: 공고 목록 갈래가 세운 canonical fact. **업무 대분류·낙찰하한율·마감일시**가 여기에만
 *   있다 — 개찰 축 응답에는 그 셋이 없다.
 *
 * 그래서 **두 갈래가 다 돌아야 스냅숏이 선다.** 공고 목록 관측이 없는 공고는 대분류를 알 수 없어
 * 행이 만들어지지 않는다(지어내지 않는다) — 그 수는 [SnapshotExtraction.skippedWithoutNotice] 가 센다.
 *
 * 값은 **계약 경유**로만 읽는다(raw 키 리터럴이 이 파일에 없다) — 키를 아는 것은 계약이고, 이 어댑터는
 * 개념 이름만 안다.
 */
class JdbcSnapshotSource(
    private val dataSource: DataSource,
    private val policy: KonepsCollectionPolicyData,
) {
    /**
     * **표본은 [sample] 이 정한다**(D-6G-39). v3 까지는 「상세 관측이 있으면 표본이었다」로 역산했고,
     * 그것은 수집이 도중에 무엇을 불렀는가에 기댄 정의였다 — 확정 목록과 어긋나도 드러나지 않았다.
     * 이제 표본 밖은 세기만 하고, 표본인데 행이 되지 못한 공고는 **사유별로** 계수된다.
     */
    fun extract(
        from: LocalDate,
        to: LocalDate,
        sample: SampleList,
    ): SnapshotExtraction {
        val observations = readObservations(from, to)
        val notices = readNotices()
        val rows = mutableListOf<SnapshotRow>()
        val withDetail = mutableSetOf<NoticeKeyHash>()
        var withoutNotice = 0
        var outsideSample = 0
        for ((key, axes) in observations) {
            val hash = NoticeKeyHash.of(key.number, key.round)
            // 상세가 하나도 없는 표본은 세지 않고 지나간다 — 아래의 **차집합**이 센다. 여기서 세면
            // 관측이 아예 없는 표본(원문이 한 줄도 안 온 공고)을 놓친다.
            if (hash !in sample.keys) {
                outsideSample++
            } else if (axes.keys.any { it in DETAIL_ENDPOINTS }) {
                withDetail += hash
                val canonical = notices[key]
                if (canonical == null) withoutNotice++ else rows += assembleSnapshotRow(key, axes, canonical)
            }
        }
        return SnapshotExtraction(rows, withoutNotice, sample.keys.size - withDetail.size, outsideSample)
    }

    private fun readObservations(
        from: LocalDate,
        to: LocalDate,
    ): Map<NoticeKey, Map<SourceEndpoint, List<RawRow>>> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(OBSERVATION_SQL).use { statement ->
                statement.setObject(1, from)
                statement.setObject(2, to.plusDays(1))
                statement.executeQuery().use { rows -> groupObservations(rows) }
            }
        }

    private fun groupObservations(rows: ResultSet): Map<NoticeKey, Map<SourceEndpoint, List<RawRow>>> {
        val byKey = linkedMapOf<NoticeKey, MutableMap<SourceEndpoint, MutableList<RawRow>>>()
        while (rows.next()) {
            keyAndEndpointOf(rows)?.let { (key, endpoint) ->
                byKey
                    .getOrPut(key) { linkedMapOf() }
                    .getOrPut(endpoint) { mutableListOf() }
                    .add(RawRow(parseFields(rows.getString("payload_fields")), policy))
            }
        }
        return byKey
    }

    /** 식별자나 엔드포인트 어휘가 서지 않는 행은 조용히 지나간다 — 지어내지 않는다. */
    private fun keyAndEndpointOf(rows: ResultSet): Pair<NoticeKey, SourceEndpoint>? {
        // **canonical 형태로 키를 맞춘다.** 원문 payload 는 수집 때 온 그대로이고 `notice` 표는
        // canonical 이라, 그대로 비교하면 같은 공고가 두 키로 갈린다(목록 축 행과 상세 축 행이
        // 서로 다른 키에 앉아 목록 축이 사라졌다 — 실측).
        val number = rows.getString("notice_number")?.let { NoticeNumber.of(it).value }
        val round = rows.getString("notice_round")
        val endpoint = runCatching { SourceEndpoint.valueOf(rows.getString("source_endpoint")) }.getOrNull()
        return if (number == null || round == null || endpoint == null) null else NoticeKey(number, round) to endpoint
    }

    private fun readNotices(): Map<NoticeKey, CanonicalNotice> =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(NOTICE_SQL).use(::collectNotices)
            }
        }

    private fun collectNotices(rows: ResultSet): Map<NoticeKey, CanonicalNotice> {
        val out = linkedMapOf<NoticeKey, CanonicalNotice>()
        while (rows.next()) {
            val key = NoticeKey(rows.getString("notice_number"), rows.getString("notice_round"))
            canonicalNoticeOf(rows)?.let { out[key] = it }
        }
        return out
    }
}

internal const val RESERVE_PRICE_SLOTS = 15

/**
 * 추출 결과 — 행과 계수 셋. **닫힌 항등식**(스키마 §2)이 성립한다:
 * `표본 크기 == rows.size + sampledWithoutDetail + skippedWithoutNotice`. 표본 하나하나가 행이
 * 되었거나 되지 못한 사유로 계수된다 — 어느 쪽도 아닌 공고는 없다.
 *
 * [observedOutsideSample] 은 항등식 밖이다. 표본이 아닌 공고의 관측 수이므로 정상 값이 크다(표본틀
 * 전체가 여기 든다). 0 이 아닌 것이 문제가 아니라, 표본 쪽 계수가 전부 0 인데 이 값만 큰 것이
 * 문제다 — 엉뚱한 표본 목록 파일을 가리켰다는 뜻이다.
 */
data class SnapshotExtraction(
    val rows: List<SnapshotRow>,
    val skippedWithoutNotice: Int,
    val sampledWithoutDetail: Int,
    val observedOutsideSample: Int,
)

private val DETAIL_ENDPOINTS =
    setOf(
        SourceEndpoint.RESERVE_PRICE_DETAIL,
        SourceEndpoint.OPENING_COMPLETE,
        SourceEndpoint.BASE_AMOUNT_DETAIL,
        SourceEndpoint.BID_PRICE_FORMULA_A,
    )

internal data class NoticeKey(
    val number: String,
    val round: String,
)
