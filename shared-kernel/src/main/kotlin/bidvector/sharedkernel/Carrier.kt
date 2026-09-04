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

    /**
     * 입력 `Money` 중 하나 이상의 `provenance`가 `Provenance.Undeclared`다 — "출처를
     * 모른다"를 산술·파생 계산에 쓰지 않는다(`v2-지침서.md` §4.1, Codex 1차 #1).
     * `OPEN-DIC-06`(어댑터 write 경로가 `Undeclared`를 거부하는가)과는 다른 축이다 — 그
     * 결정은 수집 시점 수용 여부이고, 이 사유는 이미 도메인에 들어온 값의 계산을 막는다.
     */
    UNDECLARED_PROVENANCE,
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
    /**
     * 공개 생성자를 열어 두면 이미 만들어진 [Derived] 값(그 자체는 `internal` 생성자로
     * 막혀 있어도, 함수 매개변수로 전달받은 기존 인스턴스는 여전히 읽을 수 있다)을 감싸
     * `Measurement.Measured(Derived(x, d.derivedFrom), 1, pv)` 형태로 임의 값이 진짜
     * 판정처럼 모듈 밖에서 조립된다(verifier r2 H-3). `Derived`·`DerivationRecord`와 같은
     * 처방을 적용한다 — 생성 경로는 `MoneyArithmetic.kt`의 파생 함수만이다.
     */
    @ConsistentCopyVisibility
    data class Measured<out T> internal constructor(
        val value: T,
        val sampleSize: Int,
        val policyVersion: PolicyVersion,
    ) : Measurement<T>

    data class Unmeasurable(
        val reason: ReasonCode,
    ) : Measurement<Nothing>
}
