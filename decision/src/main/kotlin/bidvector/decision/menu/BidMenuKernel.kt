package bidvector.decision.menu

import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.export

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
    floorAboveBand(ctx)?.let { return it }
    return assemble(ctx, effective)
}

/** 태도별 효력 투찰율 — 낼 수 없는 태도는 사유로 남는다(그 자체가 붕괴는 아니다). */
private fun effectiveRates(ctx: OptionContext): Map<BidStance, Step<EffectiveRate>> =
    BidStance.entries.associateWith { stance -> effectiveRateOf(stance, ctx) }

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

/**
 * 제출 **금액**이 겹친 태도 묶음(r2 M-3) — 판정 축이 율이 아니라 금액인 이유는
 * [CollapseCause.DuplicateEffectiveAmounts] 에 적었다. 금액은 옵션을 다 지은 **뒤에야**
 * 알 수 있으므로 이 판정은 조립 뒤에 온다.
 *
 * 태도가 셋이므로 겹침 묶음은 **많아야 하나**다(둘이 겹치면 남은 하나는 그 둘과 다르다).
 */
private fun collidingStances(built: Map<BidStance, BidMenuOption>): Map<BidStance, Long> {
    val stancesByAmount: Map<Long, List<BidStance>> =
        built.entries.groupBy({ entry -> entry.value.amount.value.export().won }, { entry -> entry.key })
    val collided = stancesByAmount.entries.firstOrNull { (_, stances) -> stances.size > 1 } ?: return emptyMap()
    return collided.value.associateWith { collided.key }
}

/**
 * 조립 — 태도별로 옵션을 짓고, **금액이 겹친 태도만** 보류로 옮긴다.
 *
 * 겹치지 않은 유효한 옵션은 **그대로 제시한다**(r2 M-1). 라운드 2 초판은 겹침을 발견하면
 * 메뉴 전체를 `BandCollapsed` 로 돌려 **멀쩡한 세 번째 옵션까지 버렸는데**, 그것은
 * [BidMenuOutcome.PartiallyOffered] 를 만든 원칙(한 태도를 못 낸다고 나머지를 버리지 않는다)과
 * 정반대다. 전부 겹쳤을 때만 밴드 붕괴다.
 */
private fun assemble(
    ctx: OptionContext,
    effective: Map<BidStance, Step<EffectiveRate>>,
): BidMenuOutcome {
    val built = mutableMapOf<BidStance, BidMenuOption>()
    val withheld = mutableMapOf<BidStance, MenuUnmeasurableReason>()
    for (stance in BidStance.entries) {
        when (val step = optionFor(stance, ctx, effective.getValue(stance))) {
            is Step.Ok -> built[stance] = step.value
            is Step.Halt -> withheld[stance] = step.reason
        }
    }

    val collided = collidingStances(built)
    for ((stance, won) in collided) {
        withheld[stance] =
            MenuUnmeasurableReason.CollapsedWithOtherStances(
                stance = stance,
                amountWon = won,
                otherStances = collided.keys - stance,
            )
    }
    val offered = built.filterKeys { it !in collided }

    return when {
        collided.keys == BidStance.entries.toSet() ->
            BidMenuOutcome.BandCollapsed(
                cause = CollapseCause.DuplicateEffectiveAmounts(collided.values.first()),
                collapsedStances = collided.keys,
            )

        else -> outcomeOf(offered, withheld, ctx.policy.version)
    }
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
        offered.isEmpty() -> {
            BidMenuOutcome.Unmeasurable(withheld)
        }

        withheld.isEmpty() -> {
            fullMenu(offered, version)
        }

        else -> {
            BidMenuOutcome.PartiallyOffered(
                offered = offered,
                withheld = withheld,
                disclosures = BidMenuDisclosure.entries.toSet(),
                policyVersion = version,
            )
        }
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
