package bidvector.decision

import bidvector.sharedkernel.RoundingPolicy

/**
 * 하한 미달 판정 정책(§3.3, D-8) — `minAssessmentSamples`는 근거 문자열을 동반해야 한다
 * (`OPEN-DEC-01` 해소, 운영자 승인 2026-08-26 — "통계적 편의"). `denominatorBand`(분모
 * 필터)·`biasIndeterminateBand`(편향 불확정 구간)·`criticalRateRounding`(임계 사정률
 * 나눗셈 자리수·모드, D-10)은 이 타입이 값을 지어내지 않는다 — 호출부가 준다.
 *
 * **verifier r2 N-1**: 자리수만(`criticalRateScale: Int`) 갖고 반올림 모드는 호출부
 * (`criticalAssessmentRateFor`)가 리터럴로 주입하던 이전 판은 `v2-지침서.md` §5의
 * 매직 넘버 금지("반올림 규칙"도 명시 대상)와 1B `RoundingPolicy` KDoc의 "값을 이 모듈이
 * 지어내지 않는다 — 호출부가 주입한다" 처방을 어겼다. `RoundingPolicy`(1B, 자리수+모드)
 * 를 그대로 슬롯으로 삼아 그 이탈을 없앤다 — `OPEN-DIC-10`(모드의 legacy 값 자체)은
 * 여전히 미결이고, 이 슬롯은 값을 정하지 않는다(test 정책 인스턴스에만 실제 값이 있다).
 */
data class FloorShortfallPolicyData(
    val minAssessmentSamples: Int,
    val minAssessmentSamplesRationale: String,
    val denominatorBand: AssessmentBand,
    val shortfallComparison: ShortfallComparison,
    val biasIndeterminateBand: AssessmentBand,
    val criticalRateRounding: RoundingPolicy,
) {
    init {
        require(minAssessmentSamples > 0) { "minAssessmentSamples는 양수여야 한다: $minAssessmentSamples" }
        require(minAssessmentSamplesRationale.isNotBlank()) { "minAssessmentSamplesRationale은 비어 있을 수 없다" }
    }
}
