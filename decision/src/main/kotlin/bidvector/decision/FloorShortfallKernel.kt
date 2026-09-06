package bidvector.decision

import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.Resolution

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
 */
fun measureFloorShortfall(
    tally: ShortfallTally,
    critical: AssessmentRate,
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
                biasDirection = biasDirectionOf(critical, policy.value.biasIndeterminateBand),
                policyVersion = policy.version,
            )
        }
    return FloorShortfallJudgement(tally, critical, result)
}
