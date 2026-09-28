package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.sha256Hex
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * 표본 추첨의 **용도 구분자**(vr r4 L-14) — 스키마 §2.2 가 두 레인에 대해 정본이다. 값을 바꾸면
 * 표본이 바뀌므로 그것은 새 실험이다.
 */
const val SAMPLE_DRAW_DOMAIN: String = "sample-draw"

/** 표본 seed — 정책 파일이 나르는 값이고 코드 리터럴이 아니다(사후에 바꾸면 새 version 이다). */
data class SamplingSeed(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "표본 seed 는 빈 문자열일 수 없다" }
    }
}

/** 층(D-6G-11) — 업무 대분류 × 공고 주. 공고 주는 ISO 주(`YYYY-Www`)다. */
data class SampleStratum(
    val division: BusinessDivision,
    val noticeWeek: String,
)

/**
 * 표본 후보 하나 — **결과를 나르는 칸이 없다**(우회 ⑦ 표본 쇼핑). 개찰 결과·투찰가·낙찰
 * 여부를 이 타입에 실을 수 없으므로 「결과를 보고 표본을 고르는」 경로가 구조적으로 닫힌다.
 * 주석이 아니라 타입이 그 닫힘이다.
 */
data class SampleCandidate(
    val key: NoticeKeyHash,
    val division: BusinessDivision,
    val noticeDate: LocalDate,
) {
    val stratum: SampleStratum
        get() =
            SampleStratum(
                division,
                "%d-W%02d".format(
                    noticeDate.get(IsoFields.WEEK_BASED_YEAR),
                    noticeDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                ),
            )
}

/**
 * 층 하나의 표집 결과 — [short] 는 「후보가 목표에 모자랐다」는 사실이다. 모자란 층을 조용히
 * 다른 층에서 채우지 않는다(층화 표본이 아니게 된다) — 사실로 나르고 계수는 보고가 한다.
 */
data class StratumOutcome(
    val target: Int,
    val available: Int,
    val taken: Int,
) {
    val short: Boolean get() = available < target
}

/**
 * 표집 전체 결과 — [sampleListSha256] 은 뽑힌 키의 **정렬 목록**에서만 나온다(후보 순서와
 * 무관). 판정 JSON 이 이 값을 실어 「결과를 보기 전에 확정된 목록」임을 고정한다.
 */
data class SampleOutcome(
    val selected: List<NoticeKeyHash>,
    val strata: Map<SampleStratum, StratumOutcome>,
    /** 뽑힌 키가 어느 층에서 왔는가 — 표본 목록 파일이 층을 함께 싣기 위해 필요하다(D-6G-39). */
    val strataByKey: Map<NoticeKeyHash, SampleStratum> = emptyMap(),
    /** 정책이 정한 전체 목표 — 뽑힌 수가 여기 못 미치면 후보가 모자랐다는 뜻이다(D-6G-20). */
    val requested: Int = 0,
) {
    /** 후보가 목표에 모자랐다 — 모자란 층을 다른 층에서 채우지 않으므로 전체가 그만큼 작다. */
    val short: Boolean get() = requested > 0 && selected.size < requested

    val sampleListSha256: String = sha256Hex(selected.map { it.value }.sorted().joinToString("\n"))
}

/**
 * 층화 무작위 표본(D-6G-11) — 무작위성의 출처는 난수 발생기가 아니라 **`sha256(용도 | seed | 공고
 * 키 해시)`** 다. 그래서 같은 seed·같은 후보 집합이면 언제 돌려도 같은 표본이 나오고, 뽑는 값이
 * 수집 결과와 무관하다는 것이 계산 그 자체로 선다.
 *
 * **용도 구분자**(vr r4 L-14, 스키마 §2.2): 같은 `seed|key` 형태를 Python S0 의 밴드 내 난수도
 * 쓴다. 두 seed 값이 같으면 「어떤 공고가 뽑혔나」와 「S0 가 그 공고에 낸 값」이 **같은 digest** 에서
 * 나와 두 무작위가 상관된다 — 표본 선택과 전략 값이 붙으면 판정이 그만큼 덜 독립이다. 용도를 앞에
 * 붙여 두 영역을 가른다.
 *
 * 난수 발생기를 쓰지 않는 이유: 상태가 있는 발생기는 후보를 **주는 순서**가 표본을 바꾼다 —
 * 수집 순서(응답이 온 순서)가 표본의 입력이 되면 「결과와 무관」이 깨진다.
 */
class StratifiedSampler(
    private val seed: SamplingSeed,
    private val sampleSize: SampleSize,
) {
    fun select(candidates: List<SampleCandidate>): SampleOutcome {
        val byStratum = candidates.distinctBy { it.key }.groupBy { it.stratum }
        val allocation = proportionalAllocation(byStratum.mapValues { it.value.size }, sampleSize.total)
        val selected = mutableListOf<NoticeKeyHash>()
        val outcomes = mutableMapOf<SampleStratum, StratumOutcome>()
        val strataByKey = mutableMapOf<NoticeKeyHash, SampleStratum>()
        for ((stratum, pool) in byStratum) {
            val target = allocation.getValue(stratum)
            val tickets = pool.map { DrawTicket(drawOrderOf(it.key), it.key) }.sortedWith(DRAW_ORDER)
            val taken = tickets.take(target).map { it.key }
            selected += taken
            taken.forEach { strataByKey[it] = stratum }
            outcomes[stratum] = StratumOutcome(target, pool.size, taken.size)
        }
        return SampleOutcome(selected.sortedWith(NOTICE_KEY_ORDER), outcomes, strataByKey, sampleSize.total)
    }

    private fun drawOrderOf(key: NoticeKeyHash): String = sha256Hex("$SAMPLE_DRAW_DOMAIN|${seed.value}|${key.value}")
}

/**
 * 표본 **전체** 크기(D-6G-20) — 층마다 같은 수를 뽑으면 층 크기가 다를 때 층화 표본이 아니라
 * 균등 추출이다. 작은 층이 과대표집되고 큰 층이 과소표집된다. 크기 결정식(상한·최소 필요 표본)은
 * 정책이 정하고 여기는 그 값을 받는다.
 */
data class SampleSize(
    val total: Int,
) {
    init {
        require(total > 0) { "표본 크기는 양수여야 한다: $total" }
    }
}

/**
 * 층 크기에 비례해 전체를 나눈다 — **최대 잔여법**(Hare quota). 단순 반올림은 합이 목표와 어긋나
 * 표본이 상한을 넘거나 모자란다. 잔여 배분 순서는 나머지가 큰 층부터이고, 같으면 층 이름 순서로
 * 깬다 — 후보를 **주는 순서**가 배분을 바꾸면 수집 순서가 표본의 입력이 된다.
 *
 * 후보가 목표보다 적으면 전수다(있는 것보다 많이 뽑을 수는 없다).
 */
internal fun proportionalAllocation(
    pools: Map<SampleStratum, Int>,
    total: Int,
): Map<SampleStratum, Int> {
    val population = pools.values.sum()
    if (population <= total) return pools
    val allocation = pools.mapValues { (_, size) -> (total.toLong() * size / population).toInt() }.toMutableMap()
    var remaining = total - allocation.values.sum()
    for (stratum in pools.keys.sortedWith(remainderOrder(pools, total, population))) {
        if (remaining == 0) break
        if (allocation.getValue(stratum) < pools.getValue(stratum)) {
            allocation[stratum] = allocation.getValue(stratum) + 1
            remaining--
        }
    }
    return allocation
}

/** 나머지(= `total * n - population * floor`)가 큰 층 먼저, 같으면 층 이름 순서로. */
private fun remainderOrder(
    pools: Map<SampleStratum, Int>,
    total: Int,
    population: Int,
): Comparator<SampleStratum> {
    val remainderOf = { stratum: SampleStratum ->
        val size = pools.getValue(stratum).toLong()
        total.toLong() * size - population.toLong() * (total.toLong() * size / population)
    }
    return Comparator { left, right ->
        val byRemainder = remainderOf(right).compareTo(remainderOf(left))
        if (byRemainder != 0) byRemainder else STRATUM_ORDER.compare(left, right)
    }
}

private val STRATUM_ORDER =
    Comparator<SampleStratum> { left, right ->
        val byDivision = left.division.name.compareTo(right.division.name)
        if (byDivision != 0) byDivision else left.noticeWeek.compareTo(right.noticeWeek)
    }

/** 뽑기 순서와 그 키 — 순서를 미리 계산해 두면 비교마다 해시를 다시 돌리지 않는다. */
private class DrawTicket(
    val order: String,
    val key: NoticeKeyHash,
)

// 명시 Comparator 다 — `sortedBy` 는 stdlib 출처의 합성 비교자 클래스를 산출물에 남겨
// jarContentGate(게이트를 통과한 소스만 아카이브에 든다)가 거부한다.
private val DRAW_ORDER = Comparator<DrawTicket> { left, right -> left.order.compareTo(right.order) }
val NOTICE_KEY_ORDER = Comparator<NoticeKeyHash> { left, right -> left.value.compareTo(right.value) }
