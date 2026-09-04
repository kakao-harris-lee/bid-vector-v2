package bidvector.sharedkernel

/**
 * 파생 `Money`(`BidAmount`)와 파생 율(`AssessmentRate`·`AwardRate`·`BidRate`)이 자기 값에
 * 싣는 계산 근거 — **⚠ 조정, decision 17**(운영자 결정 2026-09-04, Codex M1/1B 1차 리뷰
 * #4). 원 결정(B11, 선택지 ②)은 "입력 fact의 안정적 참조와 계산 정책 version을 함께
 * 싣는다"였으나, 그 참조가 가리킬 identity가 이 문서 안에 없었다(`data-dictionary.md`
 * §12.2의 식별자 아홉은 전부 다른 capability 소유, `inputSnapshotHash`는 불채택된 선택지
 * ①의 carrier 소유) — 그래서 **①의 변형으로 조정**한다. 값은 **계산에 쓴 정책 version만**
 * 싣는다. **입력 fact로의 되짚기는 이 값의 책임이 아니다** — 그 되짚기는 그 값을 낸
 * 판정의 `DecisionProvenance`(§4.1, M2~ 판정 레이어 — `policyVersion`·`inputSnapshotHash`)가
 * 소유한다(`data-dictionary.md` §9·§11.1 해소 블록).
 */
@ConsistentCopyVisibility
data class DerivationRecord internal constructor(
    val policyVersion: PolicyVersion,
)

/**
 * 파생값과 그 계산 근거를 함께 나르는 carrier — 값과 계산에 쓴 정책 version의 **결속**을
 * 보증하는 자리다(decision 17). `DerivationRecord` 의 생성자만 닫고 이 타입을 열어
 * 두면 `a.copy(derivedFrom = b.derivedFrom)`(기록 교체)·`Derived(a.value, b.derivedFrom)`
 * (위조 wrapper)·`Derived(x, d.derivedFrom)`(임의 타입 포장)가 모듈 밖에서 컴파일된다
 * (verifier r2 H-3) — `value`와 `derivedFrom`의 **결속**이 지켜지지 않는다. `Rate`·파생
 * `Money`가 이미 쓴 처방(`internal constructor` + `@ConsistentCopyVisibility`)을 그대로
 * 적용한다 — 생성 경로는 `MoneyArithmetic.kt`의 파생 함수(`roundedWith`·`assessmentRateAgainst`
 * 등)만이다. **입력 fact로의 되짚기는 이 carrier의 책임이 아니다** — `DecisionProvenance`
 * (§4.1)가 소유한다(decision 17).
 */
@ConsistentCopyVisibility
data class Derived<out T> internal constructor(
    val value: T,
    val derivedFrom: DerivationRecord,
)
