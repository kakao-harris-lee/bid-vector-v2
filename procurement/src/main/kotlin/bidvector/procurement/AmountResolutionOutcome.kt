package bidvector.procurement

import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance

/** [resolveAmount] 한 지점의 결과(⑤) — 매치 없음(`Unresolved`)과 계약 위반(`Rejected`)을 구분한다. */
sealed interface AmountResolutionOutcome {
    data class Resolved(
        val sourceKey: RawKey,
        val won: Long,
        val provenance: Provenance,
    ) : AmountResolutionOutcome

    data object Unresolved : AmountResolutionOutcome

    data class Rejected(
        val sourceKey: RawKey,
        val reason: CollectionDropReason,
    ) : AmountResolutionOutcome
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
            AmountResolutionOutcome.Resolved(rawKey, won, provenanceFor(contract, rawKey, noticeRound))
        }
    }
}

/**
 * 후보 하나를 평가한다 — `null`은 「이 후보는 건너뛴다」(계약 없음·값 없음·0·미상, §5.2),
 * 그 외에는 이 항목의 최종 결과([resolveAmount]가 순서를 멈춘다).
 */
private fun evaluateCandidate(
    observation: RawNoticeObservation,
    noticeRound: NoticeRound,
    registry: KonepsFieldContractRegistry,
    rawKey: RawKey,
): AmountResolutionOutcome? {
    val contract = registry.contractFor(rawKey)
    val raw = contract?.let { observation.valueOf(it) }
    return when {
        contract == null || raw == null -> {
            null
        }

        contract.scale != FieldScale.WON_INTEGER -> {
            val reason = CollectionDropReason.CollectionContractViolation(ContractViolationAxis.SCALE)
            AmountResolutionOutcome.Rejected(rawKey, reason)
        }

        else -> {
            resolvedOrParseFailure(raw, rawKey, contract, noticeRound)
        }
    }
}

/**
 * `Provenance` 해석 지점 하나(⑤) — KONEPS 수집 경로의 first-match 커널. `order`가 정책이
 * 선언한 순서다(§5.2 「해석 순서는 정책 데이터」). `0`·결측 후보는 건너뛴다. `scale`이
 * `WON_INTEGER`가 아니거나 파싱에 실패하면 그 항목 전체를 거부한다(COL-07 acceptance —
 * 다음 후보로 넘어가지 않는다, 계약 위반은 값이 아니라 사고이기 때문이다).
 */
fun resolveAmount(
    observation: RawNoticeObservation,
    noticeRound: NoticeRound,
    order: List<RawKey>,
    registry: KonepsFieldContractRegistry,
): AmountResolutionOutcome =
    order.firstNotNullOfOrNull { rawKey -> evaluateCandidate(observation, noticeRound, registry, rawKey) }
        ?: AmountResolutionOutcome.Unresolved
