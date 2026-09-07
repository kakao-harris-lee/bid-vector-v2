package bidvector.procurement

import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment

/** [resolveAmount] 한 지점의 결과(⑤) — 매치 없음(`Unresolved`)과 계약 위반(`Rejected`)을 구분한다. */
sealed interface AmountResolutionOutcome {
    /**
     * `unit`·`vatTreatment`는 후보를 낸 계약에서 그대로 옮긴 것이다(verifier r1 F-2) —
     * [canonicalize]가 통화·과세를 리터럴로 짓지 않고 이 값을 읽는다.
     */
    data class Resolved(
        val sourceKey: RawKey,
        val won: Long,
        val unit: FieldUnit,
        val vatTreatment: VatTreatment,
        val provenance: Provenance,
    ) : AmountResolutionOutcome

    data object Unresolved : AmountResolutionOutcome

    data class Rejected(
        val sourceKey: RawKey,
        val reason: CollectionDropReason,
    ) : AmountResolutionOutcome
}

/** 금액 해석의 축 — 정책의 어느 순서 목록을 쓸지 [resolveAmount] 호출부가 아니라 정책 자신이 정한다(F-4). */
enum class AmountAxis {
    BASE,
    ESTIMATED,
}

private fun parseWonInteger(raw: String): Long? = raw.replace(",", "").trim().toLongOrNull()

private fun provenanceFor(
    contract: KonepsFieldContract,
    rawKey: RawKey,
    noticeRound: NoticeRound,
): Provenance =
    when (contract.provenanceTemplate) {
        FieldProvenanceTemplate.PUBLISHED -> {
            Provenance.Published(noticeRound)
        }

        FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY -> {
            Provenance.FilledFromBudgetKey(rawKey.name)
        }

        FieldProvenanceTemplate.NOT_APPLICABLE -> {
            error("금액 해석 순서에 provenanceTemplate=NOT_APPLICABLE 키가 있다(정책 구성 오류): $rawKey")
        }
    }

private fun resolvedOrParseFailure(
    raw: String,
    rawKey: RawKey,
    contract: KonepsFieldContract,
    noticeRound: NoticeRound,
): AmountResolutionOutcome? {
    val won = parseWonInteger(raw)
    return when {
        won == null -> {
            val reason = CollectionDropReason.CollectionParseFailure(ParseFailureKind.NUMERIC)
            AmountResolutionOutcome.Rejected(rawKey, reason)
        }

        won == 0L -> {
            null
        }

        else -> {
            val provenance = provenanceFor(contract, rawKey, noticeRound)
            AmountResolutionOutcome.Resolved(rawKey, won, contract.unit, contract.vatTreatment, provenance)
        }
    }
}

private fun contractViolationOrNull(contract: KonepsFieldContract): ContractViolationAxis? =
    when {
        contract.scale != FieldScale.WON_INTEGER -> ContractViolationAxis.SCALE
        basisMismatch(contract) -> ContractViolationAxis.BASIS
        else -> null
    }

/**
 * 후보 하나를 평가한다 — `null`은 「이 후보는 건너뛴다」(계약 없음·값 없음·0·미상, §5.2),
 * 그 외에는 이 항목의 최종 결과([resolveAmount]가 순서를 멈춘다). `scale`·`basis` 어긋남은
 * 둘 다 계약 위반이다(F-3 — legacy `KEY_BASIS`가 넷을 한 basis 로 접은 것을 되돌린다).
 */
private fun evaluateCandidate(
    observation: RawNoticeObservation,
    noticeRound: NoticeRound,
    registry: KonepsFieldContractRegistry,
    rawKey: RawKey,
): AmountResolutionOutcome? {
    val contract = registry.contractFor(rawKey)
    val raw = contract?.let { observation.valueOf(it) }
    if (contract == null || raw == null) return null
    val violation = contractViolationOrNull(contract)
    return if (violation != null) {
        AmountResolutionOutcome.Rejected(rawKey, CollectionDropReason.CollectionContractViolation(violation))
    } else {
        resolvedOrParseFailure(raw, rawKey, contract, noticeRound)
    }
}

/**
 * `Provenance` 해석 지점 하나(⑤) — KONEPS 수집 경로의 first-match 커널. **정책 값 하나만
 * 받는다**(`policy`, F-4) — 호출부가 임의의 `order`·`registry`를 낱개로 조합할 수 없다.
 * `axis`가 그 정책 안에서 어느 순서 목록을 쓸지 고른다. `0`·결측 후보는 건너뛴다. 계약
 * 위반(scale·basis)이나 파싱 실패는 그 항목 전체를 거부한다(COL-07 acceptance — 다음
 * 후보로 넘어가지 않는다).
 */
fun resolveAmount(
    observation: RawNoticeObservation,
    noticeRound: NoticeRound,
    axis: AmountAxis,
    policy: KonepsCollectionPolicyData,
): AmountResolutionOutcome {
    val order =
        when (axis) {
            AmountAxis.BASE -> policy.baseAmountResolutionOrder
            AmountAxis.ESTIMATED -> policy.estimatedPriceResolutionOrder
        }
    return order.firstNotNullOfOrNull { rawKey ->
        evaluateCandidate(observation, noticeRound, policy.fieldContracts, rawKey)
    }
        ?: AmountResolutionOutcome.Unresolved
}
