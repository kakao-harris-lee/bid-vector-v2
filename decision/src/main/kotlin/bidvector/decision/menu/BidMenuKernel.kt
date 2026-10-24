package bidvector.decision.menu

import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution

/**
 * 투찰가 메뉴 커널(DEC-05 + DEC-02) — 입력 → [BidMenuOutcome] 의 **순수 함수**다.
 * 태도별 단계 함수(효력 투찰율·금액·리스크 노트)는 `BidMenuSteps.kt` 가 든다.
 *
 * 금액은 승인된 산술(`BaseAmount × BidRate` → `roundedWith`)로만 만든다. `decision` 은
 * `Derived`·`Measurement.Measured` 의 생성자를 볼 수 없으므로(`shared-kernel` `internal`),
 * 메뉴에 실린 금액이 그 산술을 **거치지 않았을 수 없다** — 타입이 그 사실을 보증한다.
 *
 * 하한 제약은 **율 축**에서만 건다. 게시 하한율은 예정가격 기준이라 기초금액에 곱하면 basis
 * 가 섞이고(DEC-12 A5), 그 해석 자체가 미결이다(`OPEN-DEC05-FLOOR-RATE-READING`).
 *
 * **순서**: ① 출처 부재 → ② 태도별 효력 투찰율 → ③ 밴드 붕괴 둘(하한이 밴드 위 · 효력율 중복)
 * → ④ 태도별 금액·리스크 → ⑤ 조립(셋 전부 / 일부 / 전부 보류).
 */
fun composeBidMenu(
    input: BidMenuInput,
    policy: Resolution.Resolved<BidMenuPolicyData>,
): BidMenuOutcome {
    val floorRate = input.publishedFloorRate
    return when {
        floorRate == null -> BidMenuOutcome.SourceAbsent(MenuSourceAbsence.PublishedFloorRateAbsent)
        else -> withFloorRate(OptionContext(input, floorRate, policy))
    }
}

private fun withFloorRate(ctx: OptionContext): BidMenuOutcome {
    val effective = effectiveRates(ctx)
    return collapseOf(ctx, effective) ?: assemble(ctx, effective)
}

/** 태도별 효력 투찰율 — 낼 수 없는 태도는 사유로 남는다(그 자체가 붕괴는 아니다). */
private fun effectiveRates(ctx: OptionContext): Map<BidStance, Step<EffectiveRate>> =
    BidStance.entries.associateWith { stance -> effectiveRateOf(stance, ctx) }

/**
 * 밴드 붕괴 판정 둘(DEC-05 무조건 acceptance 둘째).
 *
 * 하한 유래([CollapseCause.FloorAboveBand])는 bound 가 설 때만 판정할 수 있지만, **효력율
 * 중복**([CollapseCause.DuplicateEffectiveRates])은 읽기가 미정이어도 판정한다 — ML 이 같은
 * 값을 둘 이상 내면 그것만으로 선택지가 아니기 때문이다(라운드 2 HIGH-2).
 */
private fun collapseOf(
    ctx: OptionContext,
    effective: Map<BidStance, Step<EffectiveRate>>,
): BidMenuOutcome.BandCollapsed? = floorAboveBand(ctx) ?: duplicateRates(effective)

/**
 * 후보 셋의 **꼭대기**가 하한 bound 를 못 넘으면 셋이 한 점으로 접힌다.
 *
 * 꼭대기는 [StanceRates.safe] 다 — 안전이 가장 높은 투찰율이기 때문이다(라운드 2 HIGH-1).
 * 라운드 1 은 이 자리에서 `aggressive` 를 읽었는데, 라벨을 바로잡은 뒤 그것은 **가장 낮은**
 * 율이라 그대로 두면 붕괴를 놓치거나 반대로 오탐한다.
 */
private fun floorAboveBand(ctx: OptionContext): BidMenuOutcome.BandCollapsed? =
    when (val reading = ctx.policy.value.floorRateReading) {
        FloorRateReading.Undecided -> {
            null
        }

        is FloorRateReading.AsBidRateLowerBound -> {
            val top = ctx.input.rates.safe
            val bound = ctx.floorRate.rate
            if (admits(top, bound, reading.comparison)) {
                null
            } else {
                BidMenuOutcome.BandCollapsed(
                    cause = CollapseCause.FloorAboveBand(lowerBound = bound, upperBound = top),
                    collapsedStances = BidStance.entries.toSet(),
                )
            }
        }
    }

/** 효력 투찰율이 겹친 태도 묶음 — 부분 붕괴(둘만 겹침)도 「정상 3안」이 아니다. */
private fun duplicateRates(effective: Map<BidStance, Step<EffectiveRate>>): BidMenuOutcome.BandCollapsed? {
    val rates = effective.mapNotNull { (stance, step) -> (step as? Step.Ok)?.let { stance to it.value.rate } }
    val duplicated = rates.groupBy { it.second }.entries.firstOrNull { it.value.size > 1 }
    return duplicated?.let { (rate, pairs) ->
        BidMenuOutcome.BandCollapsed(
            cause = CollapseCause.DuplicateEffectiveRates(rate),
            collapsedStances = pairs.mapTo(mutableSetOf()) { it.first },
        )
    }
}

private fun assemble(
    ctx: OptionContext,
    effective: Map<BidStance, Step<EffectiveRate>>,
): BidMenuOutcome {
    val offered = mutableMapOf<BidStance, BidMenuOption>()
    val withheld = mutableMapOf<BidStance, MenuUnmeasurableReason>()
    for (stance in BidStance.entries) {
        when (val step = optionFor(stance, ctx, effective.getValue(stance))) {
            is Step.Ok -> offered[stance] = step.value
            is Step.Halt -> withheld[stance] = step.reason
        }
    }
    return outcomeOf(offered, withheld, ctx.policy.version)
}

private fun optionFor(
    stance: BidStance,
    ctx: OptionContext,
    effectiveStep: Step<EffectiveRate>,
): Step<BidMenuOption> =
    effectiveStep.andThen { effective ->
        amountStep(stance, ctx, effective).andThen { decision ->
            riskStep(stance, ctx, effective).andThen { risk ->
                Step.Ok(optionOf(stance, ctx, effective, decision, risk))
            }
        }
    }

/** 셋 전부 → [BidMenuOutcome.Offered] · 일부 → `PartiallyOffered` · 전부 보류 → `Unmeasurable`. */
private fun outcomeOf(
    offered: Map<BidStance, BidMenuOption>,
    withheld: Map<BidStance, MenuUnmeasurableReason>,
    version: PolicyVersion,
): BidMenuOutcome =
    when {
        offered.isEmpty() -> BidMenuOutcome.Unmeasurable(withheld)
        withheld.isEmpty() -> fullMenu(offered, version)
        else ->
            BidMenuOutcome.PartiallyOffered(
                offered = offered,
                withheld = withheld,
                disclosures = BidMenuDisclosure.entries.toSet(),
                policyVersion = version,
            )
    }

/** 셋 전부가 섰을 때만 지어진다 — 「정상 3안」의 이름이 그 사실을 말한다. */
private fun fullMenu(
    offered: Map<BidStance, BidMenuOption>,
    version: PolicyVersion,
): BidMenuOutcome.Offered =
    BidMenuOutcome.Offered(
        safe = requireNotNull(offered[BidStance.Safe]) { "withheld 가 비었으면 태도 전수가 채워져 있다" },
        recommended = requireNotNull(offered[BidStance.Recommended]) { "withheld 가 비었으면 태도 전수가 채워져 있다" },
        aggressive = requireNotNull(offered[BidStance.Aggressive]) { "withheld 가 비었으면 태도 전수가 채워져 있다" },
        disclosures = BidMenuDisclosure.entries.toSet(),
        policyVersion = version,
    )
