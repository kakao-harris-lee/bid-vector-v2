package bidvector.archfixture.allowed.settlement

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.EnumMap
import java.util.Objects
import java.util.TreeMap
import java.util.TreeSet
import kotlin.math.roundToLong

/**
 * **양성 corpus** — 1B~1E 가 쓸 형태가 게이트를 통과하는지 잰다.
 *
 * 이것이 없으면 게이트는 **도메인이 비어 있는 동안만** 초록이고, 첫 도메인 커밋이 `check` 를
 * 깨는 자리에서 게이트를 느슨하게 하려는 압력이 생긴다(`ADR 0007` D-4 가 막으려는 경로).
 * 설계 검토가 이 형태들로 오탐 넷을 찾아냈다 — `kotlin.enums`(모든 enum) · `kotlin.math` ·
 * `java.time.temporal` 이 그때 전부 막혀 있었다.
 *
 * **툴체인 bump 가 새 좌표를 만들면 1B 가 아니라 여기서 먼저 깨진다.** 그것이 이 corpus 의 값이다.
 *
 * domain 모듈이 아니라 fixture 트리에 두는 이유: 도메인 코드는 이 slice 의 범위 밖이다.
 * production 을 지키는 **같은 규칙 값**을 이 루트에 걸어 재므로 판정은 같다.
 */
enum class Kind { GOODS, SERVICE, CONSTRUCTION }

@JvmInline
value class Rate(
    val fraction: BigDecimal,
) {
    fun applyTo(amount: BigDecimal): BigDecimal = amount.multiply(fraction).setScale(0, RoundingMode.HALF_UP)
}

data class Won(
    val amount: BigDecimal,
) : Comparable<Won> {
    operator fun plus(other: Won): Won = Won(amount + other.amount)

    override fun compareTo(other: Won): Int = amount.compareTo(other.amount)
}

sealed interface Verdict {
    data class Eligible(
        val on: LocalDate,
    ) : Verdict

    data class Ineligible(
        val reasons: List<String>,
    ) : Verdict

    data object Uncertain : Verdict
}

fun interface Scorer {
    fun score(row: Row): Long
}

data class Row(
    val kind: Kind,
    val bid: Won,
    val at: LocalDate,
)

@Suppress("TooManyFunctions")
class DomainShapes {
    private val cache: Map<Kind, Long> by lazy { Kind.entries.associateWith { it.ordinal.toLong() } }

    fun rounded(value: Double): Long = value.roundToLong()

    fun spanDays(
        from: LocalDate,
        to: LocalDate,
    ): Long = ChronoUnit.DAYS.between(from, to)

    fun byKind(rows: List<Row>): Map<Kind, List<Row>> = rows.groupBy { it.kind }

    fun indexed(rows: List<Row>): Map<LocalDate, Row> = rows.associateBy(Row::at)

    fun ordered(rows: List<Row>): List<Row> = rows.sortedWith(compareBy<Row> { it.kind }.thenByDescending { it.bid })

    fun total(rows: List<Row>): BigDecimal = rows.sumOf { it.bid.amount }

    fun widest(rows: List<Row>): Won? = rows.maxOfOrNull { it.bid }

    fun pairs(rows: List<Row>): List<List<Row>> = rows.windowed(size = 2, partialWindows = true)

    fun batched(rows: List<Row>): List<List<Row>> = rows.chunked(3)

    fun describe(verdict: Verdict): String =
        when (verdict) {
            is Verdict.Eligible -> "eligible ${verdict.on}"
            is Verdict.Ineligible -> verdict.reasons.joinToString(separator = ", ")
            Verdict.Uncertain -> "uncertain"
        }

    fun scored(
        rows: List<Row>,
        scorer: Scorer,
    ): List<Long> = rows.map(scorer::score)

    fun frozen(rows: List<Row>): List<Row> = Collections.unmodifiableList(rows.toList())

    fun sortedKeys(rows: List<Row>): Set<LocalDate> = TreeSet(rows.map { it.at })

    fun tallied(rows: List<Row>): Map<Kind, Int> =
        EnumMap<Kind, Int>(Kind::class.java).apply {
            rows.forEach { merge(it.kind, 1, Int::plus) }
        }

    fun ranked(rows: List<Row>): Map<Won, Row> = TreeMap(rows.associateBy { it.bid })

    fun required(row: Row?): Row = Objects.requireNonNull(row, "row")!!

    fun entries(rows: Map<Kind, Long>): List<String> = rows.map { (kind, n) -> "$kind=$n" }

    fun parsed(raw: String): Result<Long> = runCatching { raw.trim().toLong() }

    fun formatted(value: Long): String = String.format("%,d", value)

    fun span(count: Int): List<Int> = (1..count).toList()

    fun labels(rows: List<Row>): Array<String> = rows.map { it.kind.name }.toTypedArray()

    fun cached(): Map<Kind, Long> = cache

    fun built(rows: List<Row>): List<String> = buildList { rows.forEach { add(it.kind.name) } }
}
