package bidvector.procurement

import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import java.time.Instant

/**
 * `canonicalize`가 원문에서 실제로 낸 canonical 값(②) — 원문(`raw`)은 감사를 위해 그대로
 * 함께 나른다(버리지 않는다). 판정 결과(`BaseAmountProvenance` 라벨)는 여기 없다 — 3A는
 * `Provenance` 해석까지만 낸다(⑤ 경계).
 */
data class NoticeCollected(
    val id: NoticeId,
    val businessCategory: BusinessCategory?,
    val baseAmount: ResolvedBaseAmount?,
    val estimatedAmount: ResolvedEstimatedAmount?,
    val allocatedBudget: AllocatedBudget?,
    val floorRate: FloorRate?,
    val deadlineAt: Instant?,
    val openingScheduledAt: Instant?,
    val raw: RawNoticeObservation,
)

/** 추정가격도 기초금액과 같은 형태 규율(파생이 원본을 덮지 않는다)을 받는다 — `Published`만 직접값이다. */
data class ResolvedEstimatedAmount(
    val sourceKey: RawKey,
    val amount: EstimatedAmount,
)

/** 항목 하나의 canonicalize 결과 — 성공(정규화됨) 또는 탈락(사유 있음), 미지 필드 수는 둘 다 싣는다. */
sealed interface CanonicalizationOutcome {
    val unknownFieldCount: Int

    data class Normalized(
        val command: NoticeCollected,
        override val unknownFieldCount: Int,
    ) : CanonicalizationOutcome

    data class Dropped(
        val reason: CollectionDropReason,
        override val unknownFieldCount: Int,
    ) : CanonicalizationOutcome
}

private fun currencyFor(unit: FieldUnit): Currency =
    when (unit) {
        FieldUnit.WON -> Currency.KRW
        else -> error("금액 축 계약의 unit은 WON이어야 한다: $unit")
    }

private fun identifierValue(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    concept: FieldConcept,
): String? = registry.contractsFor(concept).firstOrNull()?.let(observation::valueOf)

private fun resolvedNoticeId(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): NoticeId? {
    val numberRaw = identifierValue(observation, registry, FieldConcept.NOTICE_NUMBER)
    val roundRaw = identifierValue(observation, registry, FieldConcept.NOTICE_ROUND)
    return if (numberRaw != null && roundRaw != null) {
        NoticeId(NoticeNumber.of(numberRaw), NoticeRound.of(roundRaw))
    } else {
        null
    }
}

/** 통화·과세는 계약에서 읽는다(verifier r1 F-2) — 리터럴로 짓지 않는다. */
private fun baseAmountAsResolved(outcome: AmountResolutionOutcome): ResolvedBaseAmount? {
    if (outcome !is AmountResolutionOutcome.Resolved) return null
    val currency = currencyFor(outcome.unit)
    return when (val provenance = outcome.provenance) {
        is Provenance.Published -> {
            ResolvedBaseAmount.Direct.of(outcome.won, currency, outcome.vatTreatment, provenance)
        }

        is Provenance.FilledFromBudgetKey -> {
            ResolvedBaseAmount.FallbackFromBudget(
                outcome.sourceKey,
                BaseAmount(outcome.won, currency, outcome.vatTreatment, provenance),
            )
        }

        else -> {
            null
        }
    }
}

private fun estimatedAmountAsResolved(outcome: AmountResolutionOutcome): ResolvedEstimatedAmount? {
    if (outcome !is AmountResolutionOutcome.Resolved) return null
    return ResolvedEstimatedAmount(
        outcome.sourceKey,
        EstimatedAmount(outcome.won, currencyFor(outcome.unit), outcome.vatTreatment, outcome.provenance),
    )
}

/** 업무구분(⑤ D-3A-5, COL-08) — 코드·라벨 두 값. 매핑 없는 라벨은 `null`(임의 라벨 금지). */
private fun businessCategoryFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): BusinessCategory? =
    registry
        .contractsFor(FieldConcept.BUSINESS_CATEGORY_CODE)
        .firstOrNull()
        ?.let(observation::valueOf)
        ?.let { code ->
            val label =
                registry.contractsFor(FieldConcept.BUSINESS_CATEGORY_LABEL).firstOrNull()?.let(observation::valueOf)
            BusinessCategory(CategoryCode(code), label?.let(::CategoryLabel))
        }

/**
 * 배정예산(F-5) — `FilledFromBudgetKey` 폴백과는 **다른 자리**다. 자기 개념(`ALLOCATED_BUDGET`)
 * 필드에 값이 있으면 그 자체로 게시값이라 `Provenance.Published`를 받는다(폴백에 쓰였는지
 * 여부와 무관 — 축이 다르다, §5.2).
 */
private fun allocatedBudgetFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    noticeRound: NoticeRound,
): AllocatedBudget? =
    registry
        .contractsFor(FieldConcept.ALLOCATED_BUDGET)
        .firstOrNull()
        ?.takeIf { it.scale == FieldScale.WON_INTEGER && !basisMismatch(it) }
        ?.let { contract ->
            observation
                .valueOf(contract)
                ?.replace(",", "")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { won -> AllocatedBudget(won, currencyFor(contract.unit), Provenance.Published(noticeRound)) }
        }

/** 게시 낙찰하한율(F-5) — 원문 percent → canonical fraction 은 계약 `scale` 지시로만 연다(ADR 0002 D-4). */
private fun floorRateFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    noticeRound: NoticeRound,
): FloorRate? =
    registry
        .contractsFor(FieldConcept.FLOOR_RATE)
        .firstOrNull()
        ?.takeIf { it.scale == FieldScale.PERCENT }
        ?.let(observation::valueOf)
        ?.toBigDecimalOrNull()
        ?.let { numeric -> FloorRate(Rate.ofPercent(numeric), FloorRateOrigin.NoticeValue(noticeRound)) }

/** 식별자가 선 뒤의 나머지 canonicalize — 금액 해석 둘 중 하나라도 계약 위반이면 항목 전체가 탈락한다(④·⑦). */
private fun normalizedCommand(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
    noticeId: NoticeId,
    unknownFieldCount: Int,
): CanonicalizationOutcome {
    val baseAmountResolution = resolveAmount(observation, noticeId.round, AmountAxis.BASE, policy)
    val estimatedResolution = resolveAmount(observation, noticeId.round, AmountAxis.ESTIMATED, policy)
    return when {
        baseAmountResolution is AmountResolutionOutcome.Rejected -> {
            CanonicalizationOutcome.Dropped(baseAmountResolution.reason, unknownFieldCount)
        }

        estimatedResolution is AmountResolutionOutcome.Rejected -> {
            CanonicalizationOutcome.Dropped(estimatedResolution.reason, unknownFieldCount)
        }

        else -> {
            CanonicalizationOutcome.Normalized(
                NoticeCollected(
                    id = noticeId,
                    businessCategory = businessCategoryFrom(observation, policy.fieldContracts),
                    baseAmount = baseAmountAsResolved(baseAmountResolution),
                    estimatedAmount = estimatedAmountAsResolved(estimatedResolution),
                    allocatedBudget = allocatedBudgetFrom(observation, policy.fieldContracts, noticeId.round),
                    floorRate = floorRateFrom(observation, policy.fieldContracts, noticeId.round),
                    deadlineAt = instantFrom(observation, policy.fieldContracts, FieldConcept.DEADLINE_AT),
                    openingScheduledAt =
                        instantFrom(
                            observation,
                            policy.fieldContracts,
                            FieldConcept.OPENING_SCHEDULED_AT,
                        ),
                    raw = observation,
                ),
                unknownFieldCount,
            )
        }
    }
}

/**
 * 원문 raw 관측 ↔ canonical command 변환(②) — 유일한 변환 지점. 원문은 [NoticeCollected.raw]
 * 로 보존된다(감사). 공고번호·차수 중 하나라도 없으면 이 항목은 탈락한다(④·⑦).
 */
fun canonicalize(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
): CanonicalizationOutcome {
    val noticeId = resolvedNoticeId(observation, policy.fieldContracts)
    val unknownFieldCount = policy.fieldContracts.unknownKeysIn(observation).size
    return if (noticeId == null) {
        CanonicalizationOutcome.Dropped(CollectionDropReason.CollectionMissingNoticeNumber, unknownFieldCount)
    } else {
        normalizedCommand(observation, policy, noticeId, unknownFieldCount)
    }
}
