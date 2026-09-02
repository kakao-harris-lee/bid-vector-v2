package bidvector.buildlogic

/**
 * 도출된 후보의 **사람 분류**. 값은 `forbidden` 이거나 `reviewed:<사유>` 다.
 *
 * 「효과 표면에 닿는다」와 「도메인이 쓰면 안 된다」는 다르다 — `ArrayList#add` 는
 * `System#arraycopy` 를 부르지만 효과를 밖으로 내지 않는다. 그 판단은 도출이 할 수 없어
 * 사람이 한 번 하고, **판단하지 않은 후보가 남으면 게이트가 실패한다.** 그것이 이 층을
 * 열거가 아니라 래칫으로 만드는 성질이다.
 */
internal class MemberEffectClassification private constructor(
    private val verdicts: Map<String, String>,
) {
    val forbidden: Set<String> get() = verdicts.filterValues { it == FORBIDDEN }.keys

    val reviewed: Set<String> get() = verdicts.keys - forbidden

    /** 후보 집합과 분류 집합이 어긋난 자리. 양방향으로 본다 — 낡은 분류도 거짓말이다. */
    fun mismatches(candidates: Set<String>): List<String> =
        (candidates - verdicts.keys).sorted().map { "분류되지 않은 후보 — $it" } +
            (verdicts.keys - candidates).sorted().map { "후보에 없는 분류(낡았다) — $it" }

    companion object {
        const val FORBIDDEN = "forbidden"
        private const val REVIEWED = "reviewed:"
        private val METADATA_KEYS = setOf("policy.version")

        fun parse(entries: Map<String, String>): MemberEffectClassification {
            val verdicts = entries.filterKeys { it !in METADATA_KEYS }
            val malformed = verdicts.filterValues { !it.isWellFormed() }.keys.sorted()
            check(malformed.isEmpty()) {
                malformed.joinToString(
                    prefix = "분류 값은 '$FORBIDDEN' 이거나 'reviewed:<사유>' 여야 한다:\n  ",
                    separator = "\n  ",
                )
            }
            return MemberEffectClassification(verdicts)
        }

        private fun String.isWellFormed(): Boolean =
            this == FORBIDDEN || (startsWith(REVIEWED) && removePrefix(REVIEWED).isNotBlank())
    }
}
