package bidvector.workflow.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId

// ---------------------------------------------------------------------------
// 복원 — 원시 값([EditSessionSnapshot]) → EditSession(D-6B1-6·D-6B1-7). `internal`이라
// `workflow` 밖에서 호출 자체가 컴파일되지 않는다. 미지·불량 값은 지어내지 않고
// 거부한다(팀장 판정). 인코딩 쪽([EditSession.toSnapshot] 등)은 EditSessionSnapshot.kt —
// 이 파일은 detekt TooManyFunctions(11) 축을 넘지 않도록 나눈 분할이다(sizeGate 500 관례,
// CleanMigrationTest 계열과 같은 이유).
// ---------------------------------------------------------------------------

internal fun restoreEditSession(snapshot: EditSessionSnapshot): EditSession =
    EditSession(
        id = snapshot.id,
        actor = snapshot.operator,
        state = restoreState(snapshot),
        expiresAt = snapshot.expiresAt,
        sessionVersion = snapshot.sessionVersion,
        lastCommand = snapshot.lastCommand?.let { restoreCommand(snapshot.id, snapshot.operator, it) },
    )

private fun restoreState(snapshot: EditSessionSnapshot): EditSessionState =
    when (snapshot.stateKind) {
        "WAITING_FOR_VALUE" -> {
            EditSessionState.WaitingForValue(restoreRequiredField(snapshot.stateField))
        }

        "WAITING_FOR_CONFIRMATION" -> {
            EditSessionState.WaitingForConfirmation(
                restoreRequiredField(snapshot.stateField),
                restoreStrategyDraft(
                    requireNotNull(snapshot.stateDraft) { "WaitingForConfirmation 은 stateDraft 가 필요하다" },
                ),
            )
        }

        "APPLIED" -> {
            EditSessionState.Applied(
                StrategyRevision(requireNotNull(snapshot.stateRevision) { "Applied 는 stateRevision 이 필요하다" }),
            )
        }

        "CANCELLED" -> {
            EditSessionState.Cancelled(restoreCancellationReason(snapshot))
        }

        "EXPIRED" -> {
            EditSessionState.Expired
        }

        else -> {
            error("알 수 없는 EditSessionState kind: ${snapshot.stateKind}")
        }
    }

private fun restoreRequiredField(field: EditableFieldSnapshot?): EditableField =
    restoreEditableField(requireNotNull(field) { "이 상태는 stateField 가 필요하다" })

private fun restoreCancellationReason(snapshot: EditSessionSnapshot): CancellationReason {
    val kind = requireNotNull(snapshot.stateCancelReasonKind) { "Cancelled 는 stateCancelReasonKind 가 필요하다" }
    return when (kind) {
        "OPERATOR_REQUESTED" -> {
            CancellationReason.OperatorRequested
        }

        "OTHER" -> {
            CancellationReason.Other(
                requireNotNull(snapshot.stateCancelReasonNote) { "CancellationReason.Other 는 note 가 필요하다" },
            )
        }

        else -> {
            error("알 수 없는 CancellationReason kind: $kind")
        }
    }
}

private fun restoreEditableField(snapshot: EditableFieldSnapshot): EditableField =
    when (snapshot.kind) {
        "WATCH" -> {
            EditableField.Watch(
                watchRuleIdFromToken(requireNotNull(snapshot.detail) { "WATCH 는 detail 이 필요하다" }),
            )
        }

        "THRESHOLD" -> {
            EditableField.Threshold(
                thresholdFieldFromToken(requireNotNull(snapshot.detail) { "THRESHOLD 는 detail 이 필요하다" }),
            )
        }

        "CANDIDATE_LIMIT" -> {
            EditableField.CandidateLimit
        }

        else -> {
            error("알 수 없는 EditableField kind: ${snapshot.kind}")
        }
    }

private fun watchRuleIdFromToken(token: String): WatchRuleId =
    when (token) {
        "FocusCategory" -> WatchRuleId.FocusCategory
        "FocusRegion" -> WatchRuleId.FocusRegion
        "ExcludeRegion" -> WatchRuleId.ExcludeRegion
        "RequiredKeyword" -> WatchRuleId.RequiredKeyword
        "ExcludeKeyword" -> WatchRuleId.ExcludeKeyword
        "MinBudget" -> WatchRuleId.MinBudget
        "MaxBudget" -> WatchRuleId.MaxBudget
        else -> error("알 수 없는 WatchRuleId 토큰: $token")
    }

private fun thresholdFieldFromToken(token: String): ThresholdField =
    when (token) {
        "MinimumMatchScore" -> ThresholdField.MinimumMatchScore
        "MinimumProbabilityScore" -> ThresholdField.MinimumProbabilityScore
        "BidNowThreshold" -> ThresholdField.BidNowThreshold
        "ReviewThreshold" -> ThresholdField.ReviewThreshold
        else -> error("알 수 없는 ThresholdField 토큰: $token")
    }

private fun restoreStrategyDraft(snapshot: StrategyDraftSnapshot): StrategyDraft =
    StrategyDraft(
        focusCategories = snapshot.focusCategories,
        focusRegionTerms = snapshot.focusRegionTerms,
        excludeRegionTerms = snapshot.excludeRegionTerms,
        requiredKeywordTerms = snapshot.requiredKeywordTerms,
        excludeKeywordTerms = snapshot.excludeKeywordTerms,
        minBudget = snapshot.minBudget?.let(::restoreBaseAmount),
        maxBudget = snapshot.maxBudget?.let(::restoreBaseAmount),
        minimumMatchScore = snapshot.minimumMatchScore,
        minimumProbabilityScore = snapshot.minimumProbabilityScore,
        bidNowThreshold = snapshot.bidNowThreshold,
        reviewThreshold = snapshot.reviewThreshold,
        candidateLimit = snapshot.candidateLimit,
    )

/** 위 [MoneySnapshot] KDoc의 D-10 전제 — `OperatorDeclared`·`INCLUSIVE` 밖은 지어내지 않고 거부한다. */
private fun restoreBaseAmount(snapshot: MoneySnapshot): BaseAmount {
    require(snapshot.provenanceKind == "OPERATOR_DECLARED") {
        "예산 한계는 OperatorDeclared 만 허용한다(D-10 validate 전제) — 받은 provenanceKind=${snapshot.provenanceKind}"
    }
    require(snapshot.vatTreatment == "INCLUSIVE") {
        "예산 한계는 INCLUSIVE 만 허용한다(D-10 validate 전제) — 받은 vatTreatment=${snapshot.vatTreatment}"
    }
    return BaseAmount(
        snapshot.won,
        Currency.valueOf(snapshot.currency),
        VatTreatment.INCLUSIVE,
        Provenance.OperatorDeclared,
    )
}

private fun restoreCommand(
    sessionId: EditSessionId,
    operator: Actor.Operator,
    snapshot: EditCommandSnapshot,
): EditCommand {
    val commandId = CommandId(snapshot.commandId)
    return when (snapshot.kind) {
        "PROVIDE_VALUE" -> {
            EditCommand.ProvideValue(
                commandId,
                sessionId,
                operator,
                restoreRequiredField(snapshot.field),
                restoreStrategyDraft(requireNotNull(snapshot.draft) { "PROVIDE_VALUE 는 draft 가 필요하다" }),
            )
        }

        "CONFIRM" -> {
            EditCommand.Confirm(
                commandId,
                sessionId,
                operator,
                StrategyRevision(requireNotNull(snapshot.seenRevision) { "CONFIRM 은 seenRevision 이 필요하다" }),
            )
        }

        "REQUEST_EDIT" -> {
            EditCommand.RequestEdit(commandId, sessionId, operator, restoreRequiredField(snapshot.field))
        }

        "CANCEL" -> {
            EditCommand.Cancel(commandId, sessionId, operator, restoreCancelReasonFromCommand(snapshot))
        }

        else -> {
            error("알 수 없는 EditCommand kind: ${snapshot.kind}")
        }
    }
}

private fun restoreCancelReasonFromCommand(snapshot: EditCommandSnapshot): CancellationReason {
    val kind = requireNotNull(snapshot.cancelReasonKind) { "CANCEL 은 cancelReasonKind 가 필요하다" }
    return when (kind) {
        "OPERATOR_REQUESTED" -> {
            CancellationReason.OperatorRequested
        }

        "OTHER" -> {
            CancellationReason.Other(
                requireNotNull(snapshot.cancelReasonNote) { "CancellationReason.Other 는 note 가 필요하다" },
            )
        }

        else -> {
            error("알 수 없는 CancellationReason kind: $kind")
        }
    }
}
