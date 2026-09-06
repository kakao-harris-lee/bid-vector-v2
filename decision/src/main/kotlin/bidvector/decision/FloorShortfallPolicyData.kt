package bidvector.decision

/**
 * 하한 미달 판정 정책(§3.3, D-8) — `minAssessmentSamples`는 근거 문자열을 동반해야 한다
 * (`OPEN-DEC-01` 해소, 운영자 승인 2026-08-26 — "통계적 편의"). `denominatorBand`(분모
 * 필터)·`biasIndeterminateBand`(편향 불확정 구간)·`criticalRateScale`(임계 사정률 나눗셈
 * 자리수, D-10)은 이 타입이 값을 지어내지 않는다 — 호출부가 준다.
 */
data class FloorShortfallPolicyData(
    val minAssessmentSamples: Int,
    val minAssessmentSamplesRationale: String,
    val denominatorBand: AssessmentBand,
    val shortfallComparison: ShortfallComparison,
    val biasIndeterminateBand: AssessmentBand,
    val criticalRateScale: Int,
) {
    init {
        require(minAssessmentSamples > 0) { "minAssessmentSamples는 양수여야 한다: $minAssessmentSamples" }
        require(minAssessmentSamplesRationale.isNotBlank()) { "minAssessmentSamplesRationale은 비어 있을 수 없다" }
        require(criticalRateScale >= 0) { "criticalRateScale은 음수일 수 없다: $criticalRateScale" }
    }
}
