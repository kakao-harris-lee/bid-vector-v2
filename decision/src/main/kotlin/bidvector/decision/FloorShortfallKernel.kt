package bidvector.decision

import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Derived
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.criticalAssessmentRate

/**
 * 표본 하나의 미달 술어(②, D-4 — `floor-threshold` 축의 유일한 진입점). 경계 비교 방향은
 * 정책값이다(D-3, decision 28 — 초기값 [ShortfallComparison.StrictlyGreater]).
 */
fun isShortfall(
    realized: AssessmentRate,
    critical: AssessmentRate,
    comparison: ShortfallComparison,
): Boolean =
    when (comparison) {
        ShortfallComparison.StrictlyGreater -> realized.rate > critical.rate
        ShortfallComparison.GreaterOrEqual -> realized.rate >= critical.rate
    }

/**
 * 표본 목록에서 집계를 낸다(D-4 — 목록 입력을 가진 호출부의 진입점, 예: property test).
 * 밴드 밖 표본은 분모에서 제외되고(`outsideBand`), 밴드 안 표본만 [isShortfall]을 돈다.
 */
fun tallyOf(
    samples: List<AssessmentRate>,
    critical: AssessmentRate,
    band: AssessmentBand,
    comparison: ShortfallComparison,
): ShortfallTally {
    val (insideBand, outsideBand) = samples.partition { band.contains(it.rate.fraction) }
    val shortfallCount = insideBand.count { isShortfall(it, critical, comparison) }
    return ShortfallTally(rawCount = samples.size, outsideBand = outsideBand.size, shortfallNumerator = shortfallCount)
}

private fun biasDirectionOf(
    critical: AssessmentRate,
    indeterminateBand: AssessmentBand,
): BiasDirection =
    when {
        indeterminateBand.contains(critical.rate.fraction) -> BiasDirection.Indeterminate
        critical.rate.fraction > indeterminateBand.max -> BiasDirection.Overestimates
        else -> BiasDirection.Underestimates
    }

/**
 * 집계에서 판정을 낸다(⑥, D-4 — `floor-shortfall` 축의 유일한 진입점). 자격 있는 분모가
 * 최소 표본 수 미만이면 값 대신 사유 있는 실패를 낸다(§3.3 정직 명세 2·3) — 밴드 필터로
 * 분모가 문턱 아래로 줄어드는 경우도 같은 가지로 떨어진다(`floor-shortfall-005` 전이,
 * runner projection이 그 사실을 감사 필드로 낸다).
 *
 * `critical`은 `Derived<AssessmentRate>`다 — 봉투가 계산 정책 version을
 * 그대로 나른다. `decision`은 `Derived`를 새로 만들 수 없으므로(shared-kernel `internal`
 * 생성자) 호출부가 [criticalAssessmentRateFor] 또는 shared-kernel의
 * [bidvector.sharedkernel.criticalAssessmentRate]로 만든 값을 그대로 넘긴다.
 */
fun measureFloorShortfall(
    tally: ShortfallTally,
    critical: Derived<AssessmentRate>,
    policy: Resolution.Resolved<FloorShortfallPolicyData>,
): FloorShortfallJudgement {
    val minSamples = policy.value.minAssessmentSamples
    val result =
        if (tally.qualifiedDenominator < minSamples) {
            val reason = FloorUnmeasurableReason.SampleInsufficient(minSamples, tally.qualifiedDenominator)
            FloorShortfall.Unmeasurable(reason)
        } else {
            FloorShortfall.Measured(
                frequency = Frequency(tally.shortfallNumerator, tally.qualifiedDenominator),
                criticalAssessmentRate = critical,
                band = policy.value.denominatorBand,
                biasDirection = biasDirectionOf(critical.value, policy.value.biasIndeterminateBand),
                policyVersion = policy.version,
            )
        }
    return FloorShortfallJudgement(tally, critical, result)
}

/**
 * `FloorShortfallPolicyData.criticalRateRounding`(D-10)에서 나눗셈 정책을 만들어 임계
 * 사정률을 계산한다 — 이 함수가 그 정책 슬롯의 유일한 소비자다. 반올림 모드를 리터럴로
 * 짓지 않는다 — 자리수·모드 둘 다 정책 슬롯에서 그대로 온다. main 에 `RoundingMode` 리터럴이 없다.
 *
 * **하한율은 게이트 통과 값만 받는다**(DEC-10, D-DEC10-3 ②) — 임계 사정률 = 추천 투찰율 ÷
 * 낙찰하한율이므로 개연 불가 하한율이 들어오면 임계가 조용히 다른 수가 되고, 그 임계가 하한
 * 미달 판정의 분자를 정한다. shared-kernel 의 [bidvector.sharedkernel.criticalAssessmentRate]
 * 는 원값 서명을 유지한다(의존 방향상 게이트 타입을 볼 수 없다) — **그 함수의 전건을 이
 * 함수가 진다**. 이 자리가 `PlausibleFloorRate.value` 를 읽는 유일한 가격 경로이고, 그래서
 * 허용 목록의 가격 쪽 항목도 이 쌍 하나다.
 */
fun criticalAssessmentRateFor(
    bid: BidRate,
    floor: PlausibleFloorRate,
    policy: Resolution.Resolved<FloorShortfallPolicyData>,
): Measurement<Derived<AssessmentRate>> {
    val scalePolicy = Resolution.Resolved(policy.value.criticalRateRounding, policy.version)
    return criticalAssessmentRate(bid, floor.value, scalePolicy)
}
