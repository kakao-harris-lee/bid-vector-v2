package bidvector.sharedkernel

/**
 * 파생 `Money`(`BidAmount`)와 파생 율(`AssessmentRate`·`AwardRate`·`BidRate`)이 자기 값에
 * 무엇을 실어 입력 fact를 되짚게 하는가 — 선택지 ②(운영자 결정 2026-09-04, B11). 입력 금액의
 * `AmountRecord`(다섯 성분 그대로)와 계산에 쓴 정책 version을 함께 싣는다. 「참조·version을
 * 나르는 정확한 필드 형태」는 그 결정이 정하지 않고 M1 1B 구현이 정한다
 * (`data-dictionary.md` §9·§11.1 해소 블록).
 */
@ConsistentCopyVisibility
data class DerivationRecord internal constructor(
    val inputs: List<AmountRecord>,
    val policyVersion: PolicyVersion,
)

/**
 * 파생값과 그 계산 근거를 함께 나르는 carrier — B11 보증(파생값이 자기 입력 fact 를
 * 되짚는다)을 실제로 나르는 자리다. `DerivationRecord` 의 생성자만 닫고 이 타입을 열어
 * 두면 `a.copy(derivedFrom = b.derivedFrom)`(기록 교체)·`Derived(a.value, b.derivedFrom)`
 * (위조 wrapper)·`Derived(x, d.derivedFrom)`(임의 타입 포장)가 모듈 밖에서 컴파일된다
 * (verifier r2 H-3) — `value`와 `derivedFrom`의 **결속**이 지켜지지 않는다. `Rate`·파생
 * `Money`가 이미 쓴 처방(`internal constructor` + `@ConsistentCopyVisibility`)을 그대로
 * 적용한다 — 생성 경로는 `MoneyArithmetic.kt`의 파생 함수(`roundedWith`·`assessmentRateAgainst`
 * 등)만이다.
 */
@ConsistentCopyVisibility
data class Derived<out T> internal constructor(
    val value: T,
    val derivedFrom: DerivationRecord,
)
