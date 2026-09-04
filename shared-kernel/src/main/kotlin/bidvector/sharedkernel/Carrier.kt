package bidvector.sharedkernel

/**
 * 판정이 방출하는 사유 코드. 어휘의 단일 소유는 DEC-06이다(`data-dictionary.md` §3.1) — 1B는
 * 자기가 내는 코드만 더하고 전체 목록을 선점하지 않는다.
 */
enum class ReasonCode {
    AMOUNT_OVERFLOW,
    VAT_TREATMENT_MISMATCH,
    EMPTY_INPUT,
    POLICY_NOT_APPLICABLE,
    UNIT_NOT_DECLARED,

    /**
     * `RoundingPolicy.mode = RoundingMode.UNNECESSARY`인데 반올림 없이 정확히 표현할 수
     * 없는 값이 들어온 경우 — overflow(표현 범위 초과)와는 다른 실패다(verifier r1 M-1).
     */
    ROUNDING_NOT_REPRESENTABLE,

    /** 반올림된 결과가 음수다 — 금액 불변식(음수 금지) 위반. overflow와는 다른 실패다. */
    NEGATIVE_AMOUNT,
}

/**
 * 값이 아직 없다 — `data-dictionary.md` §1.1. `0`은 "0원"이지 "모름"이 아니고, 어떤 계산도
 * 이 둘을 `0`으로 접을 수 없다(`orElse`·`getOrDefault`·`orZero`·`getOrThrow`를 선언하지
 * 않는다 — 값을 꺼내는 유일한 수단이 소진 `when`이다).
 */
sealed interface Fact<out T> {
    data class Known<out T>(
        val value: T,
    ) : Fact<T>

    data class Absent(
        val reason: ReasonCode,
    ) : Fact<Nothing>
}

/**
 * 재려 했으나 재지 못했다 — `data-dictionary.md` §1.6. `Fact`와 다른 어휘다(§1.1 vs §1.6,
 * 문면이 두 쌍을 다른 자리에서 정의한다).
 */
sealed interface Measurement<out T> {
    data class Measured<out T>(
        val value: T,
        val sampleSize: Int,
        val policyVersion: PolicyVersion,
    ) : Measurement<T>

    data class Unmeasurable(
        val reason: ReasonCode,
    ) : Measurement<Nothing>
}
