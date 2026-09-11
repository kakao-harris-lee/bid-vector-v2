package bidvector.decision.priority.derive

import java.math.BigDecimal

/** 사다리 한 단(scope.md ①④) — [bound] 는 이 단이 적용되는 경계, [score] 는 그 단의 값. */
data class Band<T : Comparable<T>>(
    val bound: T,
    val score: BigDecimal,
)

/**
 * 밴드 사다리(설계 검토 (1)) — [bands] 에서 경계를 포함해 첫 매치를 낸다. 아무 것도
 * 매치하지 않으면 [beyondBandsScore]([bands] 밖의 legacy `∞`/`-∞` 폴백 rung, scope.md
 * ①④) — 별도 "무한대" `bound` 값을 짓지 않고 이 필드로 표현한다(매직 넘버가 아니라
 * 부재 rung — 실제 값은 [bidvector.decision.priority.derive.DerivationPolicyData] 가
 * 준다). [Ascending] 은 `value <= bound`(마감까지 남은 시간 — 작을수록 급하다),
 * [Descending] 은 `value >= bound`(예산 — 클수록 크다), D-4B5-3. [bands] 는 그 방향으로
 * 엄격 단조여야 한다(우회 (3)(4) — 가중치 합 오류·밴드 역순/중복 정책의 생성 자체를
 * 막는다).
 */
sealed interface Ladder<T : Comparable<T>> {
    val bands: List<Band<T>>
    val beyondBandsScore: BigDecimal

    data class Ascending<T : Comparable<T>>(
        override val bands: List<Band<T>>,
        override val beyondBandsScore: BigDecimal,
    ) : Ladder<T> {
        init {
            requireStrictlyOrdered(bands) { a, b -> a < b }
        }
    }

    data class Descending<T : Comparable<T>>(
        override val bands: List<Band<T>>,
        override val beyondBandsScore: BigDecimal,
    ) : Ladder<T> {
        init {
            requireStrictlyOrdered(bands) { a, b -> a > b }
        }
    }
}

/** [bands] 의 [Band.bound] 가 [ordered] 관계로 엄격 단조인지 검사(우회 (4) — 역순·중복 둘 다 여기서 막는다). */
private fun <T : Comparable<T>> requireStrictlyOrdered(
    bands: List<Band<T>>,
    ordered: (T, T) -> Boolean,
) {
    require(bands.isNotEmpty()) { "bands 는 최소 하나 이상이어야 한다" }
    bands.zipWithNext().forEach { (left, right) ->
        require(ordered(left.bound, right.bound)) {
            "bands 의 bound 는 엄격 단조여야 한다: ${left.bound} -> ${right.bound}"
        }
    }
}

/** [ladder] 에서 [value] 의 첫 매치 [Band.score] — 매치가 없으면 [Ladder.beyondBandsScore]. */
fun <T : Comparable<T>> resolveBand(
    value: T,
    ladder: Ladder<T>,
): BigDecimal {
    val matched =
        when (ladder) {
            is Ladder.Ascending -> ladder.bands.firstOrNull { value <= it.bound }
            is Ladder.Descending -> ladder.bands.firstOrNull { value >= it.bound }
        }
    return matched?.score ?: ladder.beyondBandsScore
}
