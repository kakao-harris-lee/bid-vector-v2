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
import bidvector.sharedkernel.MONEY_AXIS_SCALE_DIGITS
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.RoundingPolicy
import bidvector.sharedkernel.UnroundedBidAmount
import bidvector.sharedkernel.appliedFloorFrom
import bidvector.sharedkernel.times
import java.math.RoundingMode

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

/** 금액과 **그 금액을 정한 제약** — 하한으로 올라가면 binding 이 하한으로 바뀐다. */
internal data class AmountDecision(
    val amount: Derived<BidAmount>,
    val binding: BindingConstraint,
)

/**
 * 한 태도의 단계 체인 — 실패를 값으로 옮긴다. [Step.Halt] 가 **메뉴 전체가 아니라 그 태도
 * 하나**를 보류시킨다(라운드 2 HIGH-3) — 한 태도를 못 낸다고 나머지 둘을 버리지 않는다.
 */
internal sealed interface Step<out T> {
    data class Ok<T>(
        val value: T,
    ) : Step<T>

    data class Halt(
        val reason: MenuUnmeasurableReason,
    ) : Step<Nothing>
}

internal fun <T, R> Step<T>.andThen(next: (T) -> Step<R>): Step<R> =
    when (this) {
        is Step.Ok -> next(value)
        is Step.Halt -> this
    }

/**
 * 하한을 만족시키는 **최소 정수**로 올릴 때 쓰는 자리수·모드.
 *
 * **정책값이 아니다.** 제출 금액은 원 단위 정수이고 제약은 「하한 **이상**」이므로, 그 제약을
 * 만족하는 값 가운데 가장 작은 것이 곧 올림이다 — 정수 도메인에서의 **제약의 정의**이지
 * 운영자가 고를 수 있는 튜닝이 아니다. 그래서 [BidMenuPolicyData] 에 슬롯을 두지 않는다
 * (`OPEN-DIC-10` 은 **자연 반올림**의 모드를 소유하고 이 자리와 다른 축이다).
 */
private val FLOOR_PROMOTION_ROUNDING = RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, RoundingMode.CEILING)

internal fun effectiveRateOf(
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
    return effective?.let { Step.Ok(it) } ?: Step.Halt(MenuUnmeasurableReason.FloorBoundNotRepresentable(stance))
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

/**
 * 금액 파생 — 자연 반올림이 적용 하한을 밑돌면 **보류하지 않고 올린다**(라운드 2 HIGH-3).
 *
 * 라운드 1 은 이 자리에서 `ROUNDED_BELOW_FLOOR` 를 그대로 보류로 흘려 **하한에 걸린 옵션이 늘
 * 사라지는** 결함이 있었다(`DOWN`·`HALF_*` 가 경계에서 1원 아래로 떨어진다). 그것은 Phase 1
 * 설계(「하한 bound 자체를 채택한 값으로 올라간다」)를 **선언 없이 뒤집은 것**이기도 하다.
 *
 * 올림의 대상은 **그 옵션 자신의 반올림 전 값**이다. clamp 된 옵션은 그 값이 곧
 * `bound × 기초금액` 이라 결과가 `ceil(bound × 기초금액)` 과 같고, clamp 되지 않은 옵션은 자기
 * 값에서 올라가 후보 비율에 더 가깝게 남는다 — 둘 다 「제약을 만족하는 최소 정수」다.
 */
internal fun amountStep(
    stance: BidStance,
    ctx: OptionContext,
    effective: EffectiveRate,
): Step<AmountDecision> {
    val rounding = Resolution.Resolved(ctx.policy.value.amountRounding, ctx.policy.version)
    val floor = appliedFloorOf(ctx.input.base, ctx.floorRate.rate, ctx.policy.value.floorRateReading)
    val unrounded = ctx.input.base * BidRate.recommended(effective.rate)
    return when (val natural = unrounded.roundedWith(rounding, floor)) {
        is Measurement.Measured -> Step.Ok(AmountDecision(natural.value, effective.binding))
        is Measurement.Unmeasurable -> promoteOrHalt(stance, ctx, unrounded, floor, natural.reason)
    }
}

/** 하한 미달만 올림 대상이다 — overflow·출처 미선언 등 다른 사유는 올려도 해결되지 않는다. */
private fun promoteOrHalt(
    stance: BidStance,
    ctx: OptionContext,
    unrounded: UnroundedBidAmount,
    floor: AppliedFloor?,
    reason: ReasonCode,
): Step<AmountDecision> =
    if (reason != ReasonCode.ROUNDED_BELOW_FLOOR) {
        Step.Halt(MenuUnmeasurableReason.AmountUnmeasurable(stance, reason))
    } else {
        promoteToFloor(stance, ctx, unrounded, floor)
    }

private fun promoteToFloor(
    stance: BidStance,
    ctx: OptionContext,
    unrounded: UnroundedBidAmount,
    floor: AppliedFloor?,
): Step<AmountDecision> {
    val promotion = Resolution.Resolved(FLOOR_PROMOTION_ROUNDING, ctx.policy.version)
    return when (val promoted = unrounded.roundedWith(promotion, floor)) {
        is Measurement.Measured -> Step.Ok(AmountDecision(promoted.value, BindingConstraint.PublishedFloorBound))
        is Measurement.Unmeasurable -> Step.Halt(MenuUnmeasurableReason.AmountUnmeasurable(stance, promoted.reason))
    }
}

/**
 * 리스크 노트 — DEC-04 커널의 판정 봉투를 그대로 쓴다. 임계 사정률이 서지 않으면 빈도를
 * 지어내지 않고 사유 있는 실패를 낸다(정직 명세 2 — 「표본 부족은 0 이 아니라 부재다」와
 * 같은 규율).
 */
internal fun riskStep(
    stance: BidStance,
    ctx: OptionContext,
    effective: EffectiveRate,
): Step<FloorShortfallJudgement> {
    val bidRate = BidRate.recommended(effective.rate)
    return when (val critical = criticalAssessmentRateFor(bidRate, ctx.floorRate, ctx.input.shortfallPolicy)) {
        is Measurement.Unmeasurable -> {
            Step.Halt(MenuUnmeasurableReason.CriticalRateUnmeasurable(stance, critical.reason))
        }

        is Measurement.Measured -> {
            Step.Ok(judgementOf(ctx, critical.value))
        }
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

internal fun optionOf(
    stance: BidStance,
    ctx: OptionContext,
    effective: EffectiveRate,
    decision: AmountDecision,
    risk: FloorShortfallJudgement,
): BidMenuOption =
    BidMenuOption(
        stance = stance,
        amount = decision.amount,
        binding = decision.binding,
        risk = risk,
        grounds =
            BidMenuGrounds(
                bidRate = BidRate.recommended(effective.rate),
                publishedFloorRate = ctx.floorRate,
                baseProvenance = ctx.input.base.provenance,
                policyVersion = ctx.policy.version,
            ),
    )
