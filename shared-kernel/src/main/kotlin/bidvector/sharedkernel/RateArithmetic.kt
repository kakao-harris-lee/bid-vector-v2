package bidvector.sharedkernel

/**
 * 임계 사정률 = 추천 투찰율 ÷ 낙찰하한율(`v2-지침서.md` §4.4 · `data-dictionary.md` §3.3
 * 「핵심 관계」, M1/1D D-1(a)). `MoneyArithmetic.kt`의 `Money ÷ Money` 파생과 형태가 다르다 —
 * `Rate`는 VAT·provenance를 나르지 않으므로 `sameKnownVat`·`hasDeclaredProvenance` 전건이
 * 없다. 자리수·반올림 모드는 호출부가 정책으로 준다(D-10 — legacy 6자리는 test 정책에만,
 * 이 함수는 값을 지어내지 않는다). 하한율이 0이면 임계 자체가 정의되지 않는다.
 */
fun criticalAssessmentRate(
    bid: BidRate,
    floor: FloorRate,
    scale: Resolution.Resolved<RoundingPolicy>,
): Measurement<AssessmentRate> {
    if (floor.rate.fraction.signum() == 0) return Measurement.Unmeasurable(ReasonCode.EMPTY_INPUT)
    val quotient = bid.rate.fraction.divide(floor.rate.fraction, scale.value.scaleDigits, scale.value.mode)
    return Measurement.Measured(
        value = AssessmentRate.observed(Rate.ofFraction(quotient)),
        sampleSize = 1,
        policyVersion = scale.version,
    )
}
