package bidvector.workflow.collection

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 수집 범위 상한 정책(D-6F8-3) — `to - from` 이 이 일수를 넘는 범위는 기동 단계에서 거부한다.
 * 값은 운영자 지시의 안전 상한이고 도메인 규칙이 아니다.
 * **생성자가 `internal` 이다** — 상한을 키운 정책 값을 다른 모듈(배선·test)이 지어 [CollectionRange.of]
 * 에 넘기는 경로가 없다. 운영 인스턴스는 **갈래마다 하나**다(D-6G2e-1): 공고 목록 갈래는
 * [COLLECTION_RANGE_POLICY], 개찰결과 갈래는 [OPENING_COLLECTION_RANGE_POLICY].
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

/**
 * 개찰결과 갈래의 범위 상한(D-6G2e-1, 운영자 승인 A-1 2026-10-02) — **인스턴스가 따로다.**
 *
 * 상한이 막는 것이 갈래마다 다르다. 공고 목록 갈래는 조회일마다 쪽을 걷어 호출 수가 **창 길이에
 * 비례**하고, 개찰 갈래의 호출 수는 **확정 표본 크기 × 축 + 개찰결과 목록 쪽 수**다 — 창은 표본틀의
 * 모집단을 정할 뿐이고 호출 수는 승인 상한(일 20,000 · 총 80,000)이 따로 묶는다. 그래서 한 인스턴스로
 * 묶으면 둘 중 하나가 반드시 틀린다: 31일이면 A-1 승인 기간(업무별 하한율 변경일 ~ 현재, 최대 16주)이
 * `SPAN_TOO_LONG` 으로 기동 거부되고, 120일이면 공고 목록 갈래의 호출 폭주 방지가 함께 열린다.
 *
 * 상한을 **없애지 않는다** — 설정 실수(연도 오타 한 글자)는 실 호출이 나가기 전에 기동 단계에서
 * 잡혀야 한다. 16주(112일)에 여유 8일이 승인된 값이다.
 */
val OPENING_COLLECTION_RANGE_POLICY: EffectiveDatedPolicy<CollectionRangePolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m6/6g2e/scope.md D-6G2e-1 — 운영자 승인 A-1 2026-10-02, to - from <= 120일",
        entries = listOf(EffectiveFrom.Initial to CollectionRangePolicyData(maxSpanDays = 120)),
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
