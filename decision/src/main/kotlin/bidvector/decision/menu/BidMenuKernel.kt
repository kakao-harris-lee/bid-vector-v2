package bidvector.decision.menu

import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution

/**
 * 투찰가 메뉴 커널(DEC-05 + DEC-02) — 입력 → [BidMenuOutcome] 의 **순수 함수**다.
 * 단계 함수(효력 투찰율·금액·리스크 노트)는 `BidMenuSteps.kt` 가 든다.
 *
 * 금액은 승인된 산술(`BaseAmount × BidRate` → `roundedWith`)로만 만든다. `decision` 은
 * `Derived`·`Measurement.Measured` 의 생성자를 볼 수 없으므로(`shared-kernel` `internal`),
 * 메뉴에 실린 금액이 그 산술을 **거치지 않았을 수 없다** — 타입이 그 사실을 보증한다.
 *
 * 하한 제약은 **율 축**에서만 건다. 게시 하한율은 예정가격 기준이라 기초금액에 곱하면 basis
 * 가 섞이고(DEC-12 A5), 그 해석 자체가 미결이다(`OPEN-DEC05-FLOOR-RATE-READING`) — 미결일
 * 때 커널은 제약을 적용한 「척」하지 않고 [BindingConstraint.FloorRateReadingUndecided] 로
 * 그 사실을 옵션마다 싣는다(fail-closed).
 */
fun composeBidMenu(
    input: BidMenuInput,
    policy: Resolution.Resolved<BidMenuPolicyData>,
): BidMenuOutcome {
    val floorRate = input.publishedFloorRate
    return when {
        floorRate == null -> BidMenuOutcome.SourceAbsent(MenuSourceAbsence.PublishedFloorRateAbsent)
        else -> collapsedOrBuilt(OptionContext(input, floorRate, policy))
    }
}

private fun collapsedOrBuilt(ctx: OptionContext): BidMenuOutcome =
    collapseOf(ctx.input.rates, ctx.floorRate.rate, ctx.policy.value.floorRateReading) ?: buildAll(ctx)

/**
 * 밴드 붕괴 판정(DEC-05 무조건 acceptance 둘째) — 후보 셋의 꼭대기조차 하한 bound 를
 * 만족하지 못하면 셋이 한 점으로 접힌다.
 *
 * 읽기가 미정이면 **붕괴를 판정할 수 없다** — 비교할 bound 가 서지 않기 때문이고, 동시에
 * 제약이 하나도 걸리지 않으므로 붕괴가 구조적으로 불가능하다([StanceRates] 의 순서 불변식이
 * `safe <= aggressive` 를 이미 보장한다). 「판정 안 함」이 「붕괴 아님」으로 위장되지 않도록
 * 그 경우의 binding 이 [BindingConstraint.FloorRateReadingUndecided] 다.
 */
private fun collapseOf(
    rates: StanceRates,
    bound: Rate,
    reading: FloorRateReading,
): BidMenuOutcome.BandCollapsed? =
    when (reading) {
        FloorRateReading.Undecided -> {
            null
        }

        is FloorRateReading.AsBidRateLowerBound -> {
            if (admits(rates.aggressive, bound, reading.comparison)) {
                null
            } else {
                BidMenuOutcome.BandCollapsed(lowerBound = bound, upperBound = rates.aggressive)
            }
        }
    }

private fun buildAll(ctx: OptionContext): BidMenuOutcome {
    val byStance = mutableMapOf<BidStance, BidMenuOption>()
    for (stance in BidStance.entries) {
        when (val step = optionFor(stance, ctx)) {
            is Step.Halt -> return step.outcome
            is Step.Ok -> byStance[stance] = step.value
        }
    }
    return offered(byStance, ctx.policy.version)
}

/** [buildAll] 이 태도 전수를 돌아 세 자리를 모두 채웠음을 보장한다 — 그 보장을 문장으로 적는다. */
private fun offered(
    byStance: Map<BidStance, BidMenuOption>,
    version: PolicyVersion,
): BidMenuOutcome.Offered =
    BidMenuOutcome.Offered(
        safe = requireNotNull(byStance[BidStance.Safe]) { "buildAll 이 태도 전수를 채운다" },
        recommended = requireNotNull(byStance[BidStance.Recommended]) { "buildAll 이 태도 전수를 채운다" },
        aggressive = requireNotNull(byStance[BidStance.Aggressive]) { "buildAll 이 태도 전수를 채운다" },
        disclosures = BidMenuDisclosure.entries.toSet(),
        policyVersion = version,
    )
