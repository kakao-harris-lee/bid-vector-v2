package bidvector.adapters.snapshot

import bidvector.procurement.AxisConclusion
import bidvector.procurement.BusinessDivision
import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleList
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
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
        sample: SampleList,
        /**
         * 축의 완료와 **어느 걷기의 행을 쓸지**를 정하는 것은 시도 원장이다(D-6G-58·68) — raw 행의
         * 존재도, 그 행의 시각도 아니다. 페이지 중간에 끊긴 축도 행은 남기므로 존재로 판정하면 반쪽
         * 원문으로 행을 쓰고, 행의 시각으로 걷기를 고르면 빈 응답 재걷기·창 밖 재걷기·시계 역행
         * 셋에서 앞의 잘린 걷기가 마지막으로 보인다.
         */
        axisConclusions: Map<String, Map<SourceEndpoint, AxisConclusion>>,
    ): SnapshotExtraction {
        val observed = readObservations(sample, axisConclusions)
        val notices = readNotices(observed.byKey.keys)
        val rows = mutableListOf<SnapshotRow>()
        val withDetail = mutableSetOf<String>()
        var withoutNotice = 0
        var incomplete = 0
        for ((key, axes) in observed.byKey) {
            // 상세가 하나도 없는 표본은 세지 않고 지나간다 — 아래의 **차집합**이 센다. 여기서 세면
            // 관측이 아예 없는 표본(원문이 한 줄도 안 온 공고)을 놓친다.
            if (axes.keys.any { it in DETAIL_ENDPOINTS }) {
                val hash = NoticeKeyHash.of(key.number, key.round.value).value
                withDetail += hash
                val canonical = notices[key]
                when {
                    canonical == null -> withoutNotice++
                    !complete(hash, canonical.division, axisConclusions) -> incomplete++
                    else -> rows += assembleSnapshotRow(key, axes, canonical)
                }
            }
        }
        return SnapshotExtraction(
            rows,
            withoutNotice,
            sample.keys.size - withDetail.size,
            observed.outsideSample.size,
            incomplete,
        )
    }

    /**
     * 그 공고가 **부를 축을 전부 끝냈는가**. 부를 축은 업무가 정한다(공사는 A값까지 넷) — 부르지
     * 않는 축을 기다리면 공사 아닌 공고가 영영 행이 되지 않는다.
     */
    private fun complete(
        noticeKeyHash: String,
        division: String,
        axisConclusions: Map<String, Map<SourceEndpoint, AxisConclusion>>,
    ): Boolean {
        val known = axisConclusions[noticeKeyHash].orEmpty()
        return expectedAxesFor(division).all { known[it]?.settled == true }
    }

    private fun readObservations(
        sample: SampleList,
        axisConclusions: Map<String, Map<SourceEndpoint, AxisConclusion>>,
    ): ObservedRows =
        dataSource.connection.use { connection ->
            // **커서로 흘린다.** Postgres 드라이버는 autoCommit 이 켜진 채로는 `fetchSize` 를 무시하고
            // 결과 집합을 통째로 받는다 — 둘을 함께 두어야 표본이 커져도 메모리가 늘지 않는다.
            // 읽기만 하므로 커밋하지 않는다(커넥션이 닫히며 롤백된다).
            connection.autoCommit = false
            connection.prepareStatement(OBSERVATION_SQL).use { statement ->
                statement.fetchSize = OBSERVATION_FETCH_SIZE
                statement.executeQuery().use { rows -> groupObservations(rows, sample, axisConclusions) }
            }
        }

    /**
     * **표본 밖은 원문을 펴지 않는다**(code-review r2 LOW). 창 안의 모든 행을 파싱해 메모리에 올리면
     * 표본과 무관한 공고까지 통째로 적재된다 — 창이 넓어질수록 선형으로 는다. 키는 결과 집합에서
     * 바로 얻으므로, 표본 밖 행은 `payload_fields` 를 만지기 전에 버린다.
     */
    private fun groupObservations(
        rows: ResultSet,
        sample: SampleList,
        axisConclusions: Map<String, Map<SourceEndpoint, AxisConclusion>>,
    ): ObservedRows {
        val byKey = linkedMapOf<NoticeKey, MutableMap<SourceEndpoint, MutableList<RawRow>>>()
        val outside = mutableSetOf<NoticeKey>()
        while (rows.next()) {
            // 식별자나 엔드포인트 어휘가 서지 않는 행은 `null` 로 와서 조용히 지나간다.
            keyAndEndpointOf(rows)?.let { (key, endpoint) ->
                val hash = NoticeKeyHash.of(key.number, key.round.value)
                if (hash in sample.keys) {
                    collectWalkRow(byKey, key, endpoint, rows, axisConclusions[hash.value]?.get(endpoint))
                } else {
                    outside += key
                }
            }
        }
        return ObservedRows(byKey, outside)
    }

    /**
     * **(공고, 축)마다 원장이 가리키는 걷기의 행만 쓴다**(D-6G-68). 원문은 append-only 라(DB 트리거)
     * 잘린 걷기의 쪽이 그대로 남고, 다시 걸어 받은 전 쪽과 **합쳐지면** 그 공고의 참가자 수와 1위
     * 투찰가가 조용히 틀린다 — 행이 늘 뿐 오류가 없어 아무 데서도 붉어지지 않는다.
     *
     * 앞 판은 걷기를 **행의 시각**으로 골랐다(가장 늦은 `observed_at`). 그것은 세 자리에서 틀린다:
     * 빈 응답 재걷기는 행이 없어 보이지 않고, 추출 창 밖 재걷기도 보이지 않으며, 시계가 뒤로 가면
     * 순서가 뒤집힌다. 지금은 원장의 마지막 AXIS 줄이 걷기를 **가리킨다** — 짐작할 자리가 없다.
     *
     * 결말이 없는 축(원장 이전 원문)은 행을 모아만 둔다. 그 축은 완료로 서지 못해(D-6G-58) 그
     * 공고가 `incomplete_axis` 로 빠지므로 어느 행도 조립에 닿지 않는다.
     */
    private fun collectWalkRow(
        byKey: MutableMap<NoticeKey, MutableMap<SourceEndpoint, MutableList<RawRow>>>,
        key: NoticeKey,
        endpoint: SourceEndpoint,
        rows: ResultSet,
        conclusion: AxisConclusion?,
    ) {
        // 빈 응답으로 끝난 축은 **0 행**이다 — 앞의 잘린 걷기가 남긴 쪽을 쓰지 않는다.
        if (conclusion != null && conclusion.walk == null) return
        val observedAt = rows.getTimestamp("observed_at").toInstant()
        if (conclusion != null && observedAt != conclusion.walk) return
        byKey.getOrPut(key) { linkedMapOf() }.getOrPut(endpoint) { mutableListOf() } +=
            RawRow(parseFields(rows.getString("payload_fields")), policy)
    }

    private fun keyAndEndpointOf(rows: ResultSet): Pair<NoticeKey, SourceEndpoint>? {
        // **canonical 형태로 키를 맞춘다.** 원문 payload 는 수집 때 온 그대로이고 `notice` 표는
        // canonical 이라, 그대로 비교하면 같은 공고가 두 키로 갈린다(목록 축 행과 상세 축 행이
        // 서로 다른 키에 앉아 목록 축이 사라졌다 — 실측).
        val number = rows.getString("notice_number")?.let { NoticeNumber.of(it).value }
        val round = rows.getString("notice_round")?.let(::roundOrNull)
        val endpoint = runCatching { SourceEndpoint.valueOf(rows.getString("source_endpoint")) }.getOrNull()
        return if (number == null || round == null || endpoint == null) null else NoticeKey(number, round) to endpoint
    }

    /**
     * 표본으로 거른다 — 조건 없이 전건을 읽으면 `notice` 표 전체가 메모리에 온다(같은 지적).
     *
     * 여기 넘기는 번호는 **이미 canonical** 이고(`keyAndEndpointOf` 가 `NoticeNumber.of` 를 지났다)
     * `notice.notice_number` 도 canonical 이라 대소문자 함정이 없다 — 이어 돌기 조회가 원문과
     * canonical 을 맞대 조용히 빗나갔던 자리와 다르다(D-6G-40).
     */
    private fun readNotices(keys: Set<NoticeKey>): Map<NoticeKey, CanonicalNotice> {
        if (keys.isEmpty()) return emptyMap()
        return dataSource.connection.use { connection ->
            connection.prepareStatement(NOTICE_SQL).use { statement ->
                statement.setArray(1, connection.createArrayOf("text", keys.map { it.number }.toTypedArray()))
                statement.executeQuery().use(::collectNotices)
            }
        }
    }

    private fun collectNotices(rows: ResultSet): Map<NoticeKey, CanonicalNotice> {
        val out = linkedMapOf<NoticeKey, CanonicalNotice>()
        while (rows.next()) {
            val round = roundOrNull(rows.getString("notice_round"))
            val key = round?.let { NoticeKey(rows.getString("notice_number"), it) }
            if (key != null) canonicalNoticeOf(rows)?.let { out[key] = it }
        }
        return out
    }
}

/** 제로패딩 세 자리가 아니면 **기본값을 쓰지 않는다** — 차수를 모르는 행은 키를 갖지 못한다. */
private fun roundOrNull(raw: String?): NoticeRound? = raw?.let { runCatching { NoticeRound.of(it) }.getOrNull() }

/** 창 안의 관측 — 표본 안은 편 채로, 표본 밖은 **키만** 센다. */
private class ObservedRows(
    val byKey: Map<NoticeKey, Map<SourceEndpoint, List<RawRow>>>,
    val outsideSample: Set<NoticeKey>,
)

private const val OBSERVATION_FETCH_SIZE = 500

internal const val RESERVE_PRICE_SLOTS = 15

/**
 * 추출 결과 — 행과 계수 넷. **닫힌 항등식**(스키마 §2)이 성립한다:
 * `표본 크기 == rows.size + sampledWithoutDetail + skippedWithoutNotice + incompleteAxis`. 표본
 * 하나하나가 행이 되었거나 되지 못한 사유로 계수된다 — 어느 쪽도 아닌 공고는 없다.
 *
 * 이 항등식은 여기서 **구성상 참**이다(`sampledWithoutDetail` 이 나머지 셋의 차집합으로 나온다) —
 * 그래서 이 자리의 검사는 방어가 아니라 **표기**다. 실제로 깨질 수 있는 대조는 판독 쪽(표본 파일의
 * 키 수 ↔ manifest 계수)이고, 여기서 잡히는 것은 계수 하나를 상수로 바꾸는 변이뿐이다.
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
    /** 완료되지 않은 축이 있는 표본 수(D-6G-58) — 반쪽 원문으로 행을 쓰지 않는다. */
    val incompleteAxis: Int,
)

/**
 * 그 업무가 부르는 상세 축(D-6G-20) — 예비가격 상세·개찰완료·기초금액은 모든 업무, A값은 공사만.
 * 수집 use case 의 `detailAxesFor` 와 **같은 규칙**이고, 그 규칙이 갈리면 추출이 영영 오지 않을
 * 축을 기다리거나 덜 기다린다.
 */
internal fun expectedAxesFor(division: String): Set<SourceEndpoint> =
    buildSet {
        add(SourceEndpoint.RESERVE_PRICE_DETAIL)
        add(SourceEndpoint.OPENING_COMPLETE)
        add(SourceEndpoint.BASE_AMOUNT_DETAIL)
        if (division == BusinessDivision.CONSTRUCTION.name) add(SourceEndpoint.BID_PRICE_FORMULA_A)
    }

private val DETAIL_ENDPOINTS =
    setOf(
        SourceEndpoint.RESERVE_PRICE_DETAIL,
        SourceEndpoint.OPENING_COMPLETE,
        SourceEndpoint.BASE_AMOUNT_DETAIL,
        SourceEndpoint.BID_PRICE_FORMULA_A,
    )

/**
 * 공고 키 — **차수는 파싱된 값**이다(D-6G-42 M-9). 문자열로 들고 뒤에서 `toIntOrNull() ?: 0` 하면
 * 파싱 실패가 **첫 차수(`000` = 0)** 로 둔갑해 제외 ③(재입찰·정정)을 통과한다. 파싱을 키 만드는
 * 자리로 올려, 차수가 서지 않는 행은 키를 갖지 못하게 한다.
 */
internal data class NoticeKey(
    val number: String,
    val round: NoticeRound,
)
