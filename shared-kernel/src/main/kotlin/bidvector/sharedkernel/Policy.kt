package bidvector.sharedkernel

import java.math.RoundingMode
import java.time.LocalDate

/**
 * 정책 version의 유효 시작일. `date.min` sentinel 대신 이 sealed type을 쓴다
 * (`data-dictionary.md` §4.1 계승 규율 ②).
 */
sealed interface EffectiveFrom {
    data object Initial : EffectiveFrom

    data class On(
        val date: LocalDate,
    ) : EffectiveFrom
}

/**
 * 정책 version은 날짜로 식별한다(`data-dictionary.md` §4.1, U-6b). 정책 식별 축은
 * `ruleVersion` 한 축이다 — `RoundingPolicy`처럼 입력 모집단과 무관한 정책은 `corpusScope`를
 * 걸지 않는다(운영자 결정 2026-09-04, A6).
 */
data class PolicyVersion(
    val effectiveFrom: EffectiveFrom,
    val source: String,
)

/** 정책 조회 결과. 기준일을 모르면 호출부가 이 함수를 부르지 않고 그 자체를 `NotApplicable`로 다룬다. */
sealed interface Resolution<out T> {
    data class Resolved<out T>(
        val value: T,
        val version: PolicyVersion,
    ) : Resolution<T>

    data class NotApplicable(
        val reason: ReasonCode,
    ) : Resolution<Nothing>
}

private val EFFECTIVE_FROM_ORDER =
    Comparator<EffectiveFrom> { left, right ->
        when {
            left is EffectiveFrom.Initial && right is EffectiveFrom.Initial -> 0
            left is EffectiveFrom.Initial -> -1
            right is EffectiveFrom.Initial -> 1
            left is EffectiveFrom.On && right is EffectiveFrom.On -> left.date.compareTo(right.date)
            else -> 0
        }
    }

private fun EffectiveFrom.appliesOnOrBefore(referenceDate: LocalDate): Boolean =
    when (this) {
        EffectiveFrom.Initial -> true
        is EffectiveFrom.On -> !date.isAfter(referenceDate)
    }

/**
 * effective-date 정책 표. 기준일을 모르면 표를 적용하지 않는다(소급 금지) —
 * `data-dictionary.md` §4.1 계승 규율 ①·②.
 */
data class EffectiveDatedPolicy<T>(
    val source: String,
    val entries: List<Pair<EffectiveFrom, T>>,
) {
    fun resolve(referenceDate: LocalDate): Resolution<T> {
        var best: Pair<EffectiveFrom, T>? = null
        for (entry in entries) {
            if (!entry.first.appliesOnOrBefore(referenceDate)) continue
            val current = best
            if (current == null || EFFECTIVE_FROM_ORDER.compare(entry.first, current.first) > 0) {
                best = entry
            }
        }
        val applicable = best
        return if (applicable == null) {
            Resolution.NotApplicable(ReasonCode.POLICY_NOT_APPLICABLE)
        } else {
            Resolution.Resolved(applicable.second, PolicyVersion(applicable.first, source))
        }
    }
}

/**
 * 반올림 정책. 금액 축의 `scaleDigits`는 이미 원 단위 정수(0)로 닫혔다
 * (`data-dictionary.md` §1.1 정의 ①) — `mode` 값은 `OPEN-DIC-10` 미결이라 이 타입은 형태만
 * 두고 값을 이 모듈이 지어내지 않는다. 호출부가 주입한다.
 *
 * `scaleDigits`는 하한(0 이상)만 이 타입이 건다 — 상한(금액 축 밖 자리수)은 `OPEN-DIC-10`
 * 미결이라 여기서 지어내지 않는다. 음수는 검사 없이 방치하면 조용히 성공해 백 원 단위
 * 반올림 같은 값 오염이 통과한다(verifier r2 M-6) — `Rate.init`·`BaseAmount.init`이 이미
 * 쓰는 construction-time invariant 관례(`require`로 던진다)를 그대로 따른다.
 */
data class RoundingPolicy(
    val scaleDigits: Int,
    val mode: RoundingMode,
) {
    init {
        require(scaleDigits >= 0) { "scaleDigits는 음수일 수 없다: $scaleDigits" }
    }
}
