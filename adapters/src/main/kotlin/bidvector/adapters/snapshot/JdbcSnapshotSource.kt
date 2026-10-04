package bidvector.adapters.snapshot

import bidvector.procurement.AttemptOutcome
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
        val tally = AssemblyTally()
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
                    else -> rows += assembleSnapshotRow(key, axes.mapValues { it.value.rows }, canonical, tally)
                }
            }
        }
        // **명명 인자**다(cr r4 ⑧) — 같은 타입의 계수 일곱을 위치로 넘기면 두 칸을 맞바꾼 편집이
        // 컴파일을 지나고, 그 뒤 판독은 「사유가 바뀐 스냅숏」을 받는다. 배선은 아래 조립 test 가 잰다.
        return SnapshotExtraction(
            rows = rows,
            skippedWithoutNotice = withoutNotice,
            sampledWithoutDetail = sample.keys.size - withDetail.size,
            observedOutsideSample = observed.outsideSample.size,
            incompleteAxis = incomplete,
            unusableRawRows = observed.unusableRows,
            fractionalAmounts = tally.fractionalAmounts,
            incompleteAValues = tally.incompleteAValues,
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
        // **성공 또는 빈 응답**만 완료다(D-6G2d-4 · D-6G2d-8 ⓒ). 실패로 정착한 축은 다시 부르지
        // 않지만 그 행으로 스냅숏을 쓸 수도 없다 — 그 공고는 `incomplete_axis` 로 정직하게 빠진다.
        return expectedAxesFor(division).all { axis -> known[axis]?.let(::usableAxis) == true }
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
        val byKey = linkedMapOf<NoticeKey, MutableMap<SourceEndpoint, WalkRows>>()
        val outside = mutableSetOf<NoticeKey>()
        val unusable = mutableMapOf<UnusableRawRowCause, Int>()
        while (rows.next()) {
            // 식별자나 엔드포인트 어휘가 서지 않는 행은 키를 갖지 못한다(D-6G2d-8 ⓐ) — 그 행만
            // 버리고 **수를 공시한다**. 조용히 지나가면 「왜 표본이 비었나」를 물을 자리가 없다.
            // **원인까지 공시한다**(D-6G2c-20) — 셋이 한 수에 접히면 0 이 아닌 값에서 무엇이 틀렸는지
            // 물을 자리가 없고, 셋의 처방이 다르다(적재 결함 · 원천 형태 · 코드 변경).
            when (val keyed = keyAndEndpointOf(rows)) {
                is RawRowKey.Unusable -> {
                    unusable.merge(keyed.cause, 1, Int::plus)
                }

                is RawRowKey.Keyed -> {
                    val hash = NoticeKeyHash.of(keyed.key.number, keyed.key.round.value)
                    if (hash in sample.keys) {
                        val conclusion = axisConclusions[hash.value]?.get(keyed.endpoint)
                        collectWalkRow(byKey, keyed.key, keyed.endpoint, rows, conclusion)
                    } else {
                        outside += keyed.key
                    }
                }
            }
        }
        return ObservedRows(byKey, outside, UnusableRawRows(unusable))
    }

    /**
     * **(공고, 축)마다 한 걷기의 행만 쓴다**(D-6G-68 · D-6G2d-3). 원문은 append-only 라(DB 트리거)
     * 잘린 걷기의 쪽이 그대로 남고, 다시 걸어 받은 전 쪽과 **합쳐지면** 그 공고의 참가자 수와 1위
     * 투찰가가 조용히 틀린다 — 행이 늘 뿐 오류가 없어 아무 데서도 붉어지지 않는다.
     *
     * 어느 걷기인가를 정하는 것은 **둘**이다. 원장에 AXIS 결말 줄이 있으면 그 줄이 가리킨다 —
     * 짐작할 자리가 없다(앞 판은 행의 시각으로 골라, 빈 응답 재걷기·창 밖 재걷기·시계 역행 셋에서
     * 앞의 잘린 걷기를 마지막으로 봤다). 결말 줄이 **없는** 축(목록 축 둘)에는 원장이 줄 답이 없어
     * **가장 늦은 `observed_at`** 만 남긴다(D-6G-58 r4-d 복원). 적재 순서로 고르면 backfill 이
     * 뒤집고, 선별을 아예 안 하면 여러 걷기가 합쳐져 **가장 오래된 관측**이 실린다(vr r5-t W6).
     */
    private fun collectWalkRow(
        byKey: MutableMap<NoticeKey, MutableMap<SourceEndpoint, WalkRows>>,
        key: NoticeKey,
        endpoint: SourceEndpoint,
        rows: ResultSet,
        conclusion: AxisConclusion?,
    ) {
        val observedAt = rows.getTimestamp("observed_at").toInstant()
        if (!fromLedgeredWalk(conclusion, observedAt)) return
        val kept = byKey.getOrPut(key) { linkedMapOf() }.getOrPut(endpoint) { WalkRows(observedAt) }
        // 결말 줄이 있는 축은 위에서 그 걷기의 행만 통과했으므로 여기서 걷기가 갈리지 않는다.
        if (observedAt >= kept.walk) {
            if (observedAt > kept.walk) kept.replaceWalk(observedAt)
            kept.rows += RawRow(parseFields(rows.getString("payload_fields")), policy)
        }
    }

    private fun keyAndEndpointOf(rows: ResultSet): RawRowKey {
        // **canonical 형태로 키를 맞춘다.** 원문 payload 는 수집 때 온 그대로이고 `notice` 표는
        // canonical 이라, 그대로 비교하면 같은 공고가 두 키로 갈린다(목록 축 행과 상세 축 행이
        // 서로 다른 키에 앉아 목록 축이 사라졌다 — 실측).
        // **무방비로 정규화하지 않는다**(D-6G2d-8 ⓐ) — 공고번호 칸이 빈 문자열로 온 원문 행이 있으면
        // `NoticeNumber.of` 가 던지고 그 한 행이 추출 전체를 멈춘다. 원문은 append-only 라 지울 수도
        // 없다. 차수와 같은 규율이다: 형태를 어긴 행은 키를 갖지 못한다. 오늘의 수집 경로는 그런
        // 항목을 적재 전에 떨어뜨리므로 이것은 **방어 심화**다(cr r1 L-1) — 판독은 적재 경로의
        // 전제에 기대지 않는다.
        val number = rows.getString("notice_number")?.let { NoticeNumber.ofOrNull(it)?.value }
        val round = rows.getString("notice_round")?.let(::roundOrNull)
        val endpoint = runCatching { SourceEndpoint.valueOf(rows.getString("source_endpoint")) }.getOrNull()
        // **원인은 하나다** — 셋이 겹친 행도 한 칸에만 센다(겹치면 합계가 행 수를 넘어 「행마다 하나」가
        // 깨진다). 순서는 키를 짓는 순서 그대로다: 번호 → 차수 → 축.
        return when {
            number == null -> RawRowKey.Unusable(UnusableRawRowCause.BLANK_NOTICE_NUMBER)
            round == null -> RawRowKey.Unusable(UnusableRawRowCause.MALFORMED_ROUND)
            endpoint == null -> RawRowKey.Unusable(UnusableRawRowCause.UNKNOWN_ENDPOINT)
            else -> RawRowKey.Keyed(NoticeKey(number, round), endpoint)
        }
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

/**
 * 그 축이 **완료**인가 — 성공과 빈 응답만이다(D-6G2d-4 · D-6G2d-8 ⓒ). 실패로 정착한 축은 다시
 * 부르지 않지만 그 행으로 스냅숏을 쓸 수도 없다.
 */
private fun usableAxis(conclusion: AxisConclusion): Boolean =
    conclusion.usesRows || conclusion.outcome == AttemptOutcome.Empty

/**
 * 이 행이 **쓸 걷기의 것인가**. 결말 줄이 있으면 그 줄이 가리킨 걷기의 행만이고, 빈 응답으로 끝난
 * 축은 0 행이다(결말 어휘가 그것을 말한다 — 걷기 부재로 읽으면 옛 형식 줄이 같은 값이 된다,
 * D-6G2d-4 ⓑ). 결말 줄이 없으면 전부 후보이고 선별은 부르는 쪽이 한다.
 */
private fun fromLedgeredWalk(
    conclusion: AxisConclusion?,
    observedAt: Instant,
): Boolean = conclusion == null || (conclusion.outcome != AttemptOutcome.Empty && observedAt == conclusion.walk)

/** 이 추출이 본 관측 — 표본 안은 편 채로, 표본 밖은 **키만** 센다(관측 창은 없다, D-6G-68). */
private class ObservedRows(
    val byKey: Map<NoticeKey, Map<SourceEndpoint, WalkRows>>,
    val outsideSample: Set<NoticeKey>,
    /** 키를 갖지 못한 원문 행(D-6G2d-8 ⓐ) — 어느 표본에도 속하지 않아 항등식 밖이다. */
    val unusableRows: UnusableRawRows,
)

/**
 * 원문 행 하나의 판독 결과 — 키가 서거나, 서지 않은 **원인**이다(D-6G2c-20). `null` 하나로 돌려주던
 * 앞 판은 세 원인을 한 값에 접었고, 그래서 계수가 0 이 아닐 때 무엇이 틀렸는지 물을 자리가 없었다.
 */
private sealed interface RawRowKey {
    class Keyed(
        val key: NoticeKey,
        val endpoint: SourceEndpoint,
    ) : RawRowKey

    class Unusable(
        val cause: UnusableRawRowCause,
    ) : RawRowKey
}

/**
 * 한 (공고, 축)에서 **쓰기로 정한 걷기**와 그 걷기의 행(D-6G2d-3). 걷기를 값으로 들고 있어야
 * 결말 줄이 없는 축에서 「더 늦은 걷기가 오면 앞 걷기를 버린다」가 표현된다 — 행만 모으면 여러
 * 걷기가 합쳐지고, 그 합쳐짐은 계수에 드러나지 않는다.
 */
private class WalkRows(
    private var walkValue: Instant,
) {
    val rows: MutableList<RawRow> = mutableListOf()

    val walk: Instant get() = walkValue

    /** 더 늦은 걷기가 왔다 — 앞 걷기의 행은 **버린다**(합치지 않는다). */
    fun replaceWalk(later: Instant) {
        walkValue = later
        rows.clear()
    }
}

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
    /**
     * **공고 키 또는 축 어휘가 서지 않아 버린** 원문 행(D-6G2d-8 ⓐ · 18 · D-6G2c-20) — **항등식 밖**이다
     * ([observedOutsideSample] 과 같은 자리). 그 행은 어느 표본 공고에도 속하지 않으므로 네 항 어디에도
     * 들지 않는다. 합계(`total`)의 이름은 그대로이고 **원인별 계수가 그 안에 선다** — 앞 판은 셋을 한
     * 수로 접어, 0 이 아닌 값을 받은 사람이 어느 원인인지 물을 자리가 없었다(처방이 셋 다 다르다).
     */
    val unusableRawRows: UnusableRawRows,
    /**
     * 소수부 때문에 **없는 값이 된 금액 칸 수**(D-6G2d-15) — 집계(`a_value`·`reserve_prices`)는 통째로
     * 하나로 센다. 역시 항등식 밖이다: 그 행은 버려지지 않고 그 칸만 빈다. 0 이 아니면 원천이 원 단위
     * 정수를 낸다는 조사 문서의 관측이 깨졌다는 뜻이고, 그 사실은 로그로 공시된다.
     */
    val fractionalAmounts: Int,
    /**
     * A 묶음이 **전부 아니면 무**의 규율로 사라진 수(D-6G2d-21) — 공개일시 부재 또는 구성 항목 결측이다.
     * 소수부 계수와 칸이 다르다: 하나는 「원천이 소수를 냈다」이고 이것은 「원문이 반쪽이다」다. 역시
     * 항등식 밖이다 — 그 행은 버려지지 않고 A 칸만 빈다.
     */
    val incompleteAValues: Int,
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

/**
 * 키가 서지 않은 원문 행의 **원인**(D-6G2c-20) — 어휘를 열거로 두면 계수를 나르는 자리가 칸 순서가
 * 아니라 **이름**으로 선다(같은 타입 셋을 위치로 넘기는 자리를 만들지 않는다).
 */
enum class UnusableRawRowCause {
    /** 공고번호가 비었거나 형태를 어겼다 — 적재 경로가 그런 항목을 떨어뜨리므로 **방어 심화**다. */
    BLANK_NOTICE_NUMBER,

    /** 차수가 제로패딩 세 자리가 아니다 — 기본값을 쓰지 않는다(파싱 실패가 첫 차수로 둔갑하지 않게). */
    MALFORMED_ROUND,

    /** `source_endpoint` 가 열거 어휘 밖이다 — **코드 변경으로만** 생긴다(축 개명·제거 뒤 옛 행). */
    UNKNOWN_ENDPOINT,
}

/**
 * 원인별 계수(D-6G2c-20) — 합계는 [total] 이고 그 이름이 러너 로그에서 유지된다.
 *
 * **manifest 에는 싣지 않는다**: 칸을 늘리면 스냅숏 스키마가 바뀌고, 진행 중인 실행 상태 디렉터리가
 * 있는 동안 그 변경은 머지할 수 없다(D-6G2c-18). 공시 자리는 러너 로그 한 줄이다.
 */
data class UnusableRawRows(
    val byCause: Map<UnusableRawRowCause, Int>,
) {
    init {
        require(byCause.values.all { it >= 0 }) { "원인별 계수는 음수일 수 없다: $byCause" }
    }

    val total: Int get() = byCause.values.sum()

    operator fun get(cause: UnusableRawRowCause): Int = byCause[cause] ?: 0

    companion object {
        /** 하나도 버리지 않은 추출 — 세 칸이 모두 0 이다. */
        val NONE = UnusableRawRows(emptyMap())
    }
}
