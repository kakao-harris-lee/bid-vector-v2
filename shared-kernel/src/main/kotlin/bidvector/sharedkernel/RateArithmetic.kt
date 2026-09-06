package bidvector.sharedkernel

/**
 * 임계 사정률 = 추천 투찰율 ÷ 낙찰하한율(`v2-지침서.md` §4.4 · `data-dictionary.md` §3.3
 * 「핵심 관계」, M1/1D D-1(a)). **파생값이다**(decision 17, `data-dictionary.md` §9·§11.1 —
 * verifier r1 F-1) — 형제 파생(`assessmentRateAgainst`·`awardRateAgainst`·`bidRateAgainst`)
 * 과 같은 형태로 계산에 쓴 정책 version을 `DerivationRecord`에 실어 `Derived<AssessmentRate>`
 * 로 낸다. `MoneyArithmetic.kt`의 `Money ÷ Money` 파생과 형태가 다른 것은 전건뿐이다 — `Rate`는
 * VAT·provenance를 나르지 않으므로 `sameKnownVat`·`hasDeclaredProvenance` 전건이 없다.
 * 자리수·반올림 모드는 호출부가 정책으로 준다(D-10 — legacy 6자리는 test 정책에만, 이
 * 함수는 값을 지어내지 않는다). 하한율이 0이면 임계 자체가 정의되지 않는다.
 *
 * 같은 모듈이므로 `AssessmentRate`의 `internal` 생성자를 직접 쓴다 — [AssessmentRate.observed]
 * 는 **관측 표본의 재구성**(저장·전송에서 돌아온 값을 그대로 감싸는 것) 전용이고, 이 함수처럼
 * 새로 계산해 내는 파생값의 생성 경로가 아니다.
 */
fun criticalAssessmentRate(
    bid: BidRate,
    floor: FloorRate,
    scale: Resolution.Resolved<RoundingPolicy>,
): Measurement<Derived<AssessmentRate>> {
    if (floor.rate.fraction.signum() == 0) return Measurement.Unmeasurable(ReasonCode.EMPTY_INPUT)
    val quotient = bid.rate.fraction.divide(floor.rate.fraction, scale.value.scaleDigits, scale.value.mode)
    val value = AssessmentRate(Rate.ofFraction(quotient))
    val derivation = DerivationRecord(scale.version)
    return Measurement.Measured(
        value = Derived(value, derivation),
        sampleSize = 1,
        policyVersion = scale.version,
    )
}
