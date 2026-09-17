package bidvector.workflow.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import java.math.BigDecimal
import java.time.Instant

/**
 * [EditSessionRepository.load]의 반환 타입(D-6B1-7, 계약 갱신 (2)) — `EditSession`은
 * `internal constructor`(M4가 위조를 막았다)라 어댑터가 직접 만들 수 없다. 이 타입은
 * **원시 필드만** 담고(도메인 불변식 0) 어댑터가 만드는 유일한 타입이다.
 * `EditSession`으로의 유일한 복원 경로는 [restoreEditSession]("internal", `workflow`
 * 밖에서 호출 자체가 컴파일되지 않는다) — 통로 타입(`AppliedStrategy`)이 쓰기 주체를
 * 가르듯, 이 타입은 읽기 주체가 얻는 것을 "원시 값"으로 한정한다(M4/4C-1
 * `OutboxPort.claim() -> ClaimedOutboxRow` 패턴, D-6B1-7 근거).
 */
data class EditSessionSnapshot(
    val id: EditSessionId,
    val operator: Actor.Operator,
    val expiresAt: Instant,
    val sessionVersion: Int,
    val stateKind: String,
    val stateField: EditableFieldSnapshot?,
    val stateDraft: StrategyDraftSnapshot?,
    val stateRevision: Int?,
    val stateCancelReasonKind: String?,
    val stateCancelReasonNote: String?,
    val lastCommand: EditCommandSnapshot?,
)

/** [EditableField]의 원시 표현 — kind 는 닫힌 어휘 셋(WATCH·THRESHOLD·CANDIDATE_LIMIT). */
data class EditableFieldSnapshot(
    val kind: String,
    val detail: String?,
)

/**
 * [BaseAmount]의 원시 표현 — `won`은 `shared-kernel` 밖으로 내지 않는 `internal` 필드라
 * (`R-BASIS-01`) [BaseAmount.export]로만 꺼낸다. `StrategyDraft.minBudget`·`maxBudget`은
 * [bidvector.strategy.validate]를 통과해야만 [EditSessionState.WaitingForConfirmation]에
 * 실리므로(D-10 `checkBudgetLimitDeclaration`) `provenanceKind`는 항상
 * `OPERATOR_DECLARED`·`vatTreatment`는 항상 `INCLUSIVE`다 — [restoreBaseAmount]가 그
 * 전제를 검사해 다른 값은 거부한다(지어내지 않는다).
 */
data class MoneySnapshot(
    val won: Long,
    val currency: String,
    val vatTreatment: String,
    val provenanceKind: String,
    val provenanceDetail: String?,
)

/** [StrategyDraft]의 원시 표현 — [StrategyDraft] 자신이 불변식 0(전부 optional)이라 필드가 1:1 대응한다. */
data class StrategyDraftSnapshot(
    val focusCategories: List<String> = emptyList(),
    val focusRegionTerms: List<String> = emptyList(),
    val excludeRegionTerms: List<String> = emptyList(),
    val requiredKeywordTerms: List<String> = emptyList(),
    val excludeKeywordTerms: List<String> = emptyList(),
    val minBudget: MoneySnapshot? = null,
    val maxBudget: MoneySnapshot? = null,
    val minimumMatchScore: BigDecimal? = null,
    val minimumProbabilityScore: BigDecimal? = null,
    val bidNowThreshold: BigDecimal? = null,
    val reviewThreshold: BigDecimal? = null,
    val candidateLimit: Int? = null,
)

/**
 * [EditCommand]의 원시 표현 — `sessionId`·`actor`는 담지 않는다. [EditSession.lastCommand]는
 * [Transition.accept]를 거친 것만 실리고, 그 경로는 `actorRejection`이
 * `command.actor == session.actor`를 이미 강제해 두 값이 항상 세션 자신과 같다
 * (구조적으로 중복이라 싣지 않는다 — v2-지침서.md §5 「중복 금지」).
 */
data class EditCommandSnapshot(
    val commandId: String,
    val kind: String,
    val field: EditableFieldSnapshot?,
    val draft: StrategyDraftSnapshot?,
    val seenRevision: Int?,
    val cancelReasonKind: String?,
    val cancelReasonNote: String?,
)

// ---------------------------------------------------------------------------
// 인코딩 — EditSession → 원시 값(안전한 방향, 획득이 아니다). 이미 정당한 값에서
// 값을 꺼낼 뿐이라 위조 위험이 없다 — 어댑터의 save()·test fake 가 함께 쓴다.
// ---------------------------------------------------------------------------

fun EditSession.toSnapshot(): EditSessionSnapshot =
    EditSessionSnapshot(
        id = id,
        operator = actor,
        expiresAt = expiresAt,
        sessionVersion = sessionVersion,
        stateKind = state.stateKindName(),
        stateField = state.toFieldSnapshot(),
        stateDraft = state.toDraftSnapshot(),
        stateRevision = (state as? EditSessionState.Applied)?.revision?.value,
        stateCancelReasonKind = (state as? EditSessionState.Cancelled)?.reason?.kindName(),
        stateCancelReasonNote = ((state as? EditSessionState.Cancelled)?.reason as? CancellationReason.Other)?.note,
        lastCommand = lastCommand?.toSnapshot(),
    )

private fun EditSessionState.stateKindName(): String =
    when (this) {
        is EditSessionState.WaitingForValue -> "WAITING_FOR_VALUE"
        is EditSessionState.WaitingForConfirmation -> "WAITING_FOR_CONFIRMATION"
        is EditSessionState.Applied -> "APPLIED"
        is EditSessionState.Cancelled -> "CANCELLED"
        EditSessionState.Expired -> "EXPIRED"
    }

private fun EditSessionState.toFieldSnapshot(): EditableFieldSnapshot? =
    when (this) {
        is EditSessionState.WaitingForValue -> field.toSnapshot()
        is EditSessionState.WaitingForConfirmation -> field.toSnapshot()
        is EditSessionState.Applied, is EditSessionState.Cancelled, EditSessionState.Expired -> null
    }

private fun EditSessionState.toDraftSnapshot(): StrategyDraftSnapshot? =
    (this as? EditSessionState.WaitingForConfirmation)?.draft?.toSnapshot()

private fun CancellationReason.kindName(): String =
    when (this) {
        CancellationReason.OperatorRequested -> "OPERATOR_REQUESTED"
        is CancellationReason.Other -> "OTHER"
    }

private fun EditableField.toSnapshot(): EditableFieldSnapshot =
    when (this) {
        is EditableField.Watch -> EditableFieldSnapshot("WATCH", id.name())
        is EditableField.Threshold -> EditableFieldSnapshot("THRESHOLD", field.name())
        EditableField.CandidateLimit -> EditableFieldSnapshot("CANDIDATE_LIMIT", null)
    }

private fun WatchRuleId.name(): String =
    when (this) {
        WatchRuleId.FocusCategory -> "FocusCategory"
        WatchRuleId.FocusRegion -> "FocusRegion"
        WatchRuleId.ExcludeRegion -> "ExcludeRegion"
        WatchRuleId.RequiredKeyword -> "RequiredKeyword"
        WatchRuleId.ExcludeKeyword -> "ExcludeKeyword"
        WatchRuleId.MinBudget -> "MinBudget"
        WatchRuleId.MaxBudget -> "MaxBudget"
    }

private fun ThresholdField.name(): String =
    when (this) {
        ThresholdField.MinimumMatchScore -> "MinimumMatchScore"
        ThresholdField.MinimumProbabilityScore -> "MinimumProbabilityScore"
        ThresholdField.BidNowThreshold -> "BidNowThreshold"
        ThresholdField.ReviewThreshold -> "ReviewThreshold"
    }

private fun StrategyDraft.toSnapshot(): StrategyDraftSnapshot =
    StrategyDraftSnapshot(
        focusCategories = focusCategories,
        focusRegionTerms = focusRegionTerms,
        excludeRegionTerms = excludeRegionTerms,
        requiredKeywordTerms = requiredKeywordTerms,
        excludeKeywordTerms = excludeKeywordTerms,
        minBudget = minBudget?.toMoneySnapshot(),
        maxBudget = maxBudget?.toMoneySnapshot(),
        minimumMatchScore = minimumMatchScore,
        minimumProbabilityScore = minimumProbabilityScore,
        bidNowThreshold = bidNowThreshold,
        reviewThreshold = reviewThreshold,
        candidateLimit = candidateLimit,
    )

/** D-10 전제(위 [MoneySnapshot] KDoc) — 인코딩 시점에도 확정해 낡은 가정을 조기에 드러낸다. */
private fun BaseAmount.toMoneySnapshot(): MoneySnapshot {
    val record = export()
    check(record.provenance == Provenance.OperatorDeclared) {
        "예산 한계 provenance 는 OperatorDeclared 여야 한다(D-10 validate 전제) — 실제: ${record.provenance}"
    }
    check(record.vatTreatment == VatTreatment.INCLUSIVE) {
        "예산 한계 vatTreatment 는 INCLUSIVE 여야 한다(D-10 validate 전제) — 실제: ${record.vatTreatment}"
    }
    return MoneySnapshot(record.won, record.currency.name, record.vatTreatment.name, "OPERATOR_DECLARED", null)
}

private fun EditCommand.toSnapshot(): EditCommandSnapshot =
    when (this) {
        is EditCommand.ProvideValue ->
            EditCommandSnapshot(commandId.value, "PROVIDE_VALUE", field.toSnapshot(), draft.toSnapshot(), null, null, null)

        is EditCommand.Confirm ->
            EditCommandSnapshot(commandId.value, "CONFIRM", null, null, seenRevision.value, null, null)

        is EditCommand.RequestEdit ->
            EditCommandSnapshot(commandId.value, "REQUEST_EDIT", field.toSnapshot(), null, null, null, null)

        is EditCommand.Cancel ->
            EditCommandSnapshot(
                commandId.value,
                "CANCEL",
                null,
                null,
                null,
                reason.kindName(),
                (reason as? CancellationReason.Other)?.note,
            )
    }

// ---------------------------------------------------------------------------
// 복원 — 원시 값 → EditSession(D-6B1-6·D-6B1-7). `internal`이라 `workflow` 밖에서 호출
// 자체가 컴파일되지 않는다. 미지·불량 값은 지어내지 않고 거부한다(팀장 판정).
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
        "WAITING_FOR_VALUE" -> EditSessionState.WaitingForValue(restoreRequiredField(snapshot.stateField))

        "WAITING_FOR_CONFIRMATION" ->
            EditSessionState.WaitingForConfirmation(
                restoreRequiredField(snapshot.stateField),
                restoreStrategyDraft(requireNotNull(snapshot.stateDraft) { "WaitingForConfirmation 은 stateDraft 가 필요하다" }),
            )

        "APPLIED" ->
            EditSessionState.Applied(
                StrategyRevision(requireNotNull(snapshot.stateRevision) { "Applied 는 stateRevision 이 필요하다" }),
            )

        "CANCELLED" -> EditSessionState.Cancelled(restoreCancellationReason(snapshot))
        "EXPIRED" -> EditSessionState.Expired
        else -> error("알 수 없는 EditSessionState kind: ${snapshot.stateKind}")
    }

private fun restoreRequiredField(field: EditableFieldSnapshot?): EditableField =
    restoreEditableField(requireNotNull(field) { "이 상태는 stateField 가 필요하다" })

private fun restoreCancellationReason(snapshot: EditSessionSnapshot): CancellationReason {
    val kind = requireNotNull(snapshot.stateCancelReasonKind) { "Cancelled 는 stateCancelReasonKind 가 필요하다" }
    return when (kind) {
        "OPERATOR_REQUESTED" -> CancellationReason.OperatorRequested
        "OTHER" ->
            CancellationReason.Other(
                requireNotNull(snapshot.stateCancelReasonNote) { "CancellationReason.Other 는 note 가 필요하다" },
            )

        else -> error("알 수 없는 CancellationReason kind: $kind")
    }
}

private fun restoreEditableField(snapshot: EditableFieldSnapshot): EditableField =
    when (snapshot.kind) {
        "WATCH" -> EditableField.Watch(watchRuleIdFromToken(requireNotNull(snapshot.detail) { "WATCH 는 detail 이 필요하다" }))
        "THRESHOLD" ->
            EditableField.Threshold(
                thresholdFieldFromToken(requireNotNull(snapshot.detail) { "THRESHOLD 는 detail 이 필요하다" }),
            )

        "CANDIDATE_LIMIT" -> EditableField.CandidateLimit
        else -> error("알 수 없는 EditableField kind: ${snapshot.kind}")
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
    return BaseAmount(snapshot.won, Currency.valueOf(snapshot.currency), VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)
}

private fun restoreCommand(
    sessionId: EditSessionId,
    operator: Actor.Operator,
    snapshot: EditCommandSnapshot,
): EditCommand {
    val commandId = CommandId(snapshot.commandId)
    return when (snapshot.kind) {
        "PROVIDE_VALUE" ->
            EditCommand.ProvideValue(
                commandId,
                sessionId,
                operator,
                restoreRequiredField(snapshot.field),
                restoreStrategyDraft(requireNotNull(snapshot.draft) { "PROVIDE_VALUE 는 draft 가 필요하다" }),
            )

        "CONFIRM" ->
            EditCommand.Confirm(
                commandId,
                sessionId,
                operator,
                StrategyRevision(requireNotNull(snapshot.seenRevision) { "CONFIRM 은 seenRevision 이 필요하다" }),
            )

        "REQUEST_EDIT" -> EditCommand.RequestEdit(commandId, sessionId, operator, restoreRequiredField(snapshot.field))

        "CANCEL" -> EditCommand.Cancel(commandId, sessionId, operator, restoreCancelReasonFromCommand(snapshot))

        else -> error("알 수 없는 EditCommand kind: ${snapshot.kind}")
    }
}

private fun restoreCancelReasonFromCommand(snapshot: EditCommandSnapshot): CancellationReason {
    val kind = requireNotNull(snapshot.cancelReasonKind) { "CANCEL 은 cancelReasonKind 가 필요하다" }
    return when (kind) {
        "OPERATOR_REQUESTED" -> CancellationReason.OperatorRequested
        "OTHER" ->
            CancellationReason.Other(requireNotNull(snapshot.cancelReasonNote) { "CancellationReason.Other 는 note 가 필요하다" })

        else -> error("알 수 없는 CancellationReason kind: $kind")
    }
}
