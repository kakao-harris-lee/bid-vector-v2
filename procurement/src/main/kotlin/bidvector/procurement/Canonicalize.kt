package bidvector.procurement

import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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

private fun baseAmountAsResolved(outcome: AmountResolutionOutcome): ResolvedBaseAmount? {
    if (outcome !is AmountResolutionOutcome.Resolved) return null
    return when (val provenance = outcome.provenance) {
        is Provenance.Published -> {
            ResolvedBaseAmount.Direct.of(outcome.won, Currency.KRW, VatTreatment.UNKNOWN, provenance)
        }

        is Provenance.FilledFromBudgetKey -> {
            ResolvedBaseAmount.FallbackFromBudget(
                outcome.sourceKey,
                BaseAmount(outcome.won, Currency.KRW, VatTreatment.UNKNOWN, provenance),
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
        EstimatedAmount(outcome.won, Currency.KRW, VatTreatment.UNKNOWN, outcome.provenance),
    )
}

/** 식별자가 선 뒤의 나머지 canonicalize — 금액 해석 둘 중 하나라도 계약 위반이면 항목 전체가 탈락한다(④·⑦). */
private fun normalizedCommand(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
    noticeId: NoticeId,
    unknownFieldCount: Int,
): CanonicalizationOutcome {
    val baseAmountResolution =
        resolveAmount(observation, noticeId.round, policy.baseAmountResolutionOrder, policy.fieldContracts)
    val estimatedResolution =
        resolveAmount(observation, noticeId.round, policy.estimatedPriceResolutionOrder, policy.fieldContracts)
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
                    businessCategory = null,
                    baseAmount = baseAmountAsResolved(baseAmountResolution),
                    estimatedAmount = estimatedAmountAsResolved(estimatedResolution),
                    allocatedBudget = null,
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

/** D-3A-4 — 타임존 없는 KONEPS 일시 문자열을 [SourceZoneRuleId]로 해석한다. */
fun parseSourceZonedInstant(
    raw: String,
    rule: SourceZoneRuleId,
): Instant? {
    val local = runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: return null
    val zone =
        when (rule) {
            SourceZoneRuleId.ASSUME_KST -> ZoneId.of("Asia/Seoul")
        }
    return local.atZone(zone).toInstant()
}
