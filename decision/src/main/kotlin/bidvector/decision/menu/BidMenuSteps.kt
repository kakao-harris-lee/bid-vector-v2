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

/**
 * 금액과 **그 금액을 정한 제약** — 올림으로 하한에 맞춰 올라가면 binding 이 금액 축 값
 * ([BindingConstraint.PublishedFloorRounding])으로 바뀐다(율 축에서 이미 걸린 경우는 그대로).
 */
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
 * 올림의 대상은 그 옵션 자신의 반올림 전 값이지만, **결과는 언제나 `ceil(적용 하한)` 이다**
 * (r2 L-1 정정). 올림이 발화하는 조건이 `natural < F` 이고 자연 반올림의 성질상
 * `F <= U < natural + 1` 이므로 `ceil(U) = ceil(F)` 다 — 앞 판 KDoc 의 「clamp 되지 않은
 * 옵션은 후보 비율에 더 가깝게 남는다」는 **거짓**이다. 「제약을 만족하는 최소 정수」라는
 * 성질은 둘 다 참이고, 그 값이 하나로 정해진다는 것이 요점이다.
 *
 * **binding 은 어느 축에서 걸렸는지를 말한다**(r3 N-1). 올림이 발화하면 결과가 언제나
 * `ceil(적용 하한)` 이므로 **금액을 정한 것은 하한**이다 — 하한을 빼면 값이 달라진다. 그래서
 * 하한 **위** 후보가 원 단위 올림만 탄 경우는 [BindingConstraint.PublishedFloorRounding]
 * (금액 축)이고, 하한 **아래** 후보가 clamp 돼 올라왔으면 율 축에서 이미 걸린 것이라
 * [BindingConstraint.PublishedFloorBound] 를 유지한다.
 *
 * 앞 두 판이 양쪽으로 틀렸다 — r1 은 올림 경로에 무조건 `PublishedFloorBound` 를 달아 율 축의
 * 뜻을 넘겼고(r2 M-2), r2 는 그것을 `CandidateRate`(「걸린 제약이 없다」)로 되돌려 **하한이
 * 금액을 정한 사실을 지웠다**(r3 N-1). 축을 가르는 값이 하나 더 필요했던 것이다.
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
        is Measurement.Unmeasurable -> promoteOrHalt(stance, ctx, unrounded, floor, natural.reason, effective)
    }
}

/** 하한 미달만 올림 대상이다 — overflow·출처 미선언 등 다른 사유는 올려도 해결되지 않는다. */
private fun promoteOrHalt(
    stance: BidStance,
    ctx: OptionContext,
    unrounded: UnroundedBidAmount,
    floor: AppliedFloor?,
    reason: ReasonCode,
    effective: EffectiveRate,
): Step<AmountDecision> =
    if (reason != ReasonCode.ROUNDED_BELOW_FLOOR) {
        Step.Halt(MenuUnmeasurableReason.AmountUnmeasurable(stance, reason))
    } else {
        promoteToFloor(stance, ctx, unrounded, floor, effective)
    }

private fun promoteToFloor(
    stance: BidStance,
    ctx: OptionContext,
    unrounded: UnroundedBidAmount,
    floor: AppliedFloor?,
    effective: EffectiveRate,
): Step<AmountDecision> {
    val promotion = Resolution.Resolved(FLOOR_PROMOTION_ROUNDING, ctx.policy.version)
    return when (val promoted = unrounded.roundedWith(promotion, floor)) {
        is Measurement.Measured -> Step.Ok(AmountDecision(promoted.value, promotedBinding(effective)))
        is Measurement.Unmeasurable -> Step.Halt(MenuUnmeasurableReason.AmountUnmeasurable(stance, promoted.reason))
    }
}

/**
 * 올림이 발화한 옵션의 binding — **율 축에서 이미 걸렸으면 그대로 두고**, 율은 여유가 있었는데
 * 금액만 올라갔으면 금액 축 값으로 바꾼다(r3 N-1).
 *
 * `FloorRateReadingUndecided` 는 여기 닿지 않는다 — 읽기가 미정이면 적용 하한이 `null` 이라
 * `ROUNDED_BELOW_FLOOR` 자체가 나오지 않는다. 그래도 `when` 을 소진으로 두어 태도가 아니라
 * **binding 어휘가 늘 때** 이 자리가 컴파일로 드러나게 한다.
 */
private fun promotedBinding(effective: EffectiveRate): BindingConstraint =
    when (effective.binding) {
        BindingConstraint.CandidateRate -> BindingConstraint.PublishedFloorRounding
        BindingConstraint.PublishedFloorBound -> BindingConstraint.PublishedFloorBound
        BindingConstraint.PublishedFloorRounding -> BindingConstraint.PublishedFloorRounding
        BindingConstraint.FloorRateReadingUndecided -> BindingConstraint.FloorRateReadingUndecided
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
