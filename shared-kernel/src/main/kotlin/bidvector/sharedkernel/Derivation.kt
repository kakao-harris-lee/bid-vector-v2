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

/** 파생값과 그 계산 근거를 함께 나르는 carrier. */
data class Derived<out T>(
    val value: T,
    val derivedFrom: DerivationRecord,
)
