package bidvector.archfixture.allowed.settlement

import java.math.BigDecimal
import java.time.LocalDate

/**
 * **양성** fixture — 실제 도메인이 쓸 형태가 게이트를 통과하는지 잰다.
 *
 * 이것이 없으면 게이트는 **도메인이 비어 있는 동안만** 초록이다. 그 상태로 넘어가면 1B 의 첫
 * 커밋이 `check` 를 깨고, 그때 게이트를 느슨하게 하려는 압력이 생긴다 — `ADR 0007` D-4 가
 * 막으려는 경로다. `data class` 하나가 컴파일러 삽입 `@NotNull` 로 실제로 걸렸었다.
 *
 * domain 모듈이 아니라 fixture 트리에 두는 이유: 도메인 코드는 이 slice 의 범위 밖이다
 * (`scope.md` out_of_scope). production 을 지키는 **같은 규칙 값**을 이 루트에 걸어 재므로
 * 판정은 같다.
 */
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

class Assessment(
    private val floor: Won?,
) {
    fun describe(verdict: Verdict): String =
        when (verdict) {
            is Verdict.Eligible -> "eligible ${verdict.on}"
            is Verdict.Ineligible -> verdict.reasons.joinToString()
            Verdict.Uncertain -> "uncertain"
        }

    fun clamp(bids: List<Won>): List<Won> = bids.filter { floor == null || it >= floor }.sorted()
}
