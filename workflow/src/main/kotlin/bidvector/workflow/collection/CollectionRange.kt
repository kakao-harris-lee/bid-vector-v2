package bidvector.workflow.collection

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 수집 범위 상한 정책(D-6F8-3) — `to - from` 이 이 일수를 넘는 범위는 기동 단계에서 거부한다.
 * 값은 운영자 지시(2026-09-24, 「최근 한 달」)의 안전 상한이고 도메인 규칙이 아니다.
 * **생성자가 `internal` 이다** — 상한을 키운 정책 값을 다른 모듈(배선·test)이 지어 [CollectionRange.of]
 * 에 넘기는 경로가 없고, 운영 인스턴스는 [COLLECTION_RANGE_POLICY] 하나다.
 */
@ConsistentCopyVisibility
data class CollectionRangePolicyData internal constructor(
    val maxSpanDays: Int,
) {
    init {
        require(maxSpanDays >= 1) { "maxSpanDays 는 1 이상이어야 한다: $maxSpanDays" }
    }
}

val COLLECTION_RANGE_POLICY: EffectiveDatedPolicy<CollectionRangePolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m6/6f8/scope.md D-6F8-3 — 운영자 지시 2026-09-24, to - from <= 31일",
        entries = listOf(EffectiveFrom.Initial to CollectionRangePolicyData(maxSpanDays = 31)),
    )

/** 범위가 유효하지 않은 이유 — 설정 오류를 조용히 자르지 않고 그대로 드러낸다. */
enum class CollectionRangeViolation {
    FROM_AFTER_TO,
    TO_IN_FUTURE,
    SPAN_TOO_LONG,
}

sealed interface CollectionRangeOutcome {
    data class Valid(
        val range: CollectionRange,
    ) : CollectionRangeOutcome

    data class Rejected(
        val reason: CollectionRangeViolation,
    ) : CollectionRangeOutcome
}

/**
 * 수집할 조회일(KST 달력일) 구간 — 양 끝을 포함한다. 생성은 [of] 하나뿐이라 상한·미래 검사를 거치지
 * 않은 범위는 표현할 수 없다(use case 는 이 타입만 받는다). `today` 는 호출부가 KST 달력일로 넘긴다.
 */
class CollectionRange private constructor(
    val from: LocalDate,
    val to: LocalDate,
) {
    val dates: List<LocalDate>
        get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    companion object {
        fun of(
            from: LocalDate,
            to: LocalDate,
            today: LocalDate,
            policy: CollectionRangePolicyData,
        ): CollectionRangeOutcome =
            when {
                to.isAfter(today) -> {
                    CollectionRangeOutcome.Rejected(CollectionRangeViolation.TO_IN_FUTURE)
                }

                from.isAfter(to) -> {
                    CollectionRangeOutcome.Rejected(CollectionRangeViolation.FROM_AFTER_TO)
                }

                ChronoUnit.DAYS.between(from, to) > policy.maxSpanDays -> {
                    CollectionRangeOutcome.Rejected(CollectionRangeViolation.SPAN_TOO_LONG)
                }

                else -> {
                    CollectionRangeOutcome.Valid(CollectionRange(from, to))
                }
            }
    }
}
