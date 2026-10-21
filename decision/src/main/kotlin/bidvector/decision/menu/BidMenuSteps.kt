package bidvector.decision.menu

import bidvector.decision.FloorShortfallJudgement
import bidvector.decision.criticalAssessmentRateFor
import bidvector.decision.measureFloorShortfall
import bidvector.decision.tallyOf
import bidvector.sharedkernel.AppliedFloor
import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BidAmount
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Derived
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.appliedFloorFrom
import bidvector.sharedkernel.times

/** 세 단계 함수가 공유하는 문맥 — 기계적 묶음이 아니라 **같은 호출의 입력 전부**다. */
internal data class OptionContext(
    val input: BidMenuInput,
    val floorRate: FloorRate,
    val policy: Resolution.Resolved<BidMenuPolicyData>,
)

/** 효력 있는 투찰율과 **그 값을 정한 제약**(DEC-02) — 둘은 같은 자리에서 함께 정해진다. */
internal data class EffectiveRate(
    val rate: Rate,
    val binding: BindingConstraint,
)

/** [composeBidMenu] 의 단계 체인 — 실패를 값으로 옮긴다(`OpportunityAnalysis.Step` 관례). */
internal sealed interface Step<out T> {
    data class Ok<T>(
        val value: T,
    ) : Step<T>

    data class Halt(
        val outcome: BidMenuOutcome,
    ) : Step<Nothing>
}

internal fun <T, R> Step<T>.andThen(next: (T) -> Step<R>): Step<R> =
    when (this) {
        is Step.Ok -> next(value)
        is Step.Halt -> this
    }

private fun halt(reason: MenuUnmeasurableReason): Step<Nothing> = Step.Halt(BidMenuOutcome.Unmeasurable(reason))

internal fun optionFor(
    stance: BidStance,
    ctx: OptionContext,
): Step<BidMenuOption> =
    effectiveStep(stance, ctx).andThen { effective ->
        amountStep(stance, ctx, effective).andThen { amount ->
            riskStep(stance, ctx, effective).andThen { risk ->
                Step.Ok(optionOf(stance, ctx, effective, amount, risk))
            }
        }
    }

private fun effectiveStep(
    stance: BidStance,
    ctx: OptionContext,
): Step<EffectiveRate> {
    val candidate = ctx.input.rates.rateOf(stance)
    val effective =
        when (val reading = ctx.policy.value.floorRateReading) {
            FloorRateReading.Undecided -> {
                EffectiveRate(candidate, BindingConstraint.FloorRateReadingUndecided)
            }

            is FloorRateReading.AsBidRateLowerBound -> {
                boundedRate(candidate, ctx.floorRate.rate, reading.comparison)
            }
        }
    return effective?.let { Step.Ok(it) } ?: halt(MenuUnmeasurableReason.FloorBoundNotRepresentable(stance))
}

/**
 * 하한 bound 아래 후보의 처분 — 올릴 자리가 있으면 올리고(그 사실이 binding 이다), 허용
 * 구간이 열려 있어 최소 원소가 없으면 `null` 이다(엡실론을 지어내지 않는다).
 *
 * 후보가 bound 와 **정확히 같을 때**도 binding 은 [BindingConstraint.PublishedFloorBound] 다 —
 * 값이 제약 위에 올라앉아 있으므로 「제약이 안 걸렸다」가 아니다.
 */
private fun boundedRate(
    candidate: Rate,
    bound: Rate,
    comparison: FloorBoundComparison,
): EffectiveRate? =
    when {
        candidate > bound -> EffectiveRate(candidate, BindingConstraint.CandidateRate)
        admits(candidate, bound, comparison) -> EffectiveRate(candidate, BindingConstraint.PublishedFloorBound)
        else -> clampTargetOf(bound, comparison)?.let { EffectiveRate(it, BindingConstraint.PublishedFloorBound) }
    }

/**
 * 적용 하한은 **bound 에서** 나온다(효력 투찰율이 아니다) — 하한 위에 있는 값이 반올림으로
 * 조금 내려가는 것은 위반이 아니고, 하한 **아래로** 내려가는 것만 위반이다.
 * 읽기가 미정이면 하한이 없다 — 없는 제약을 금액 축에서 흉내내지 않는다.
 */
private fun appliedFloorOf(
    base: BaseAmount,
    bound: Rate,
    reading: FloorRateReading,
): AppliedFloor? =
    when (reading) {
        FloorRateReading.Undecided -> null
        is FloorRateReading.AsBidRateLowerBound -> base.appliedFloorFrom(BidRate.recommended(bound))
    }

private fun amountStep(
    stance: BidStance,
    ctx: OptionContext,
    effective: EffectiveRate,
): Step<Derived<BidAmount>> {
    val rounding = Resolution.Resolved(ctx.policy.value.amountRounding, ctx.policy.version)
    val floor = appliedFloorOf(ctx.input.base, ctx.floorRate.rate, ctx.policy.value.floorRateReading)
    val rounded = (ctx.input.base * BidRate.recommended(effective.rate)).roundedWith(rounding, floor)
    return when (rounded) {
        is Measurement.Measured -> Step.Ok(rounded.value)
        is Measurement.Unmeasurable -> halt(MenuUnmeasurableReason.AmountUnmeasurable(stance, rounded.reason))
    }
}

/**
 * 리스크 노트 — DEC-04 커널의 판정 봉투를 그대로 쓴다. 임계 사정률이 서지 않으면 빈도를
 * 지어내지 않고 사유 있는 실패를 낸다(정직 명세 2 — 「표본 부족은 0 이 아니라 부재다」와
 * 같은 규율).
 */
private fun riskStep(
    stance: BidStance,
    ctx: OptionContext,
    effective: EffectiveRate,
): Step<FloorShortfallJudgement> {
    val bidRate = BidRate.recommended(effective.rate)
    return when (val critical = criticalAssessmentRateFor(bidRate, ctx.floorRate, ctx.input.shortfallPolicy)) {
        is Measurement.Unmeasurable -> halt(MenuUnmeasurableReason.CriticalRateUnmeasurable(stance, critical.reason))
        is Measurement.Measured -> Step.Ok(judgementOf(ctx, critical.value))
    }
}

private fun judgementOf(
    ctx: OptionContext,
    critical: Derived<AssessmentRate>,
): FloorShortfallJudgement {
    val shortfall = ctx.input.shortfallPolicy.value
    val tally =
        tallyOf(
            samples = ctx.input.assessmentSamples,
            critical = critical.value,
            band = shortfall.denominatorBand,
            comparison = shortfall.shortfallComparison,
        )
    return measureFloorShortfall(tally, critical, ctx.input.shortfallPolicy)
}

private fun optionOf(
    stance: BidStance,
    ctx: OptionContext,
    effective: EffectiveRate,
    amount: Derived<BidAmount>,
    risk: FloorShortfallJudgement,
): BidMenuOption =
    BidMenuOption(
        stance = stance,
        amount = amount,
        binding = effective.binding,
        risk = risk,
        grounds =
            BidMenuGrounds(
                bidRate = BidRate.recommended(effective.rate),
                publishedFloorRate = ctx.floorRate,
                baseProvenance = ctx.input.base.provenance,
                policyVersion = ctx.policy.version,
            ),
    )
