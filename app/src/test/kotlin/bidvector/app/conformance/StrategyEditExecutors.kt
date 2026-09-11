package bidvector.app.conformance

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.StrategyViolation
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import bidvector.strategy.isConfigured
import bidvector.strategy.validate
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionState
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.StrategyRepository
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/*
 * M4/4A — `strategy-edit` case → executor(D-4A-1 (a)). 신설 fixture 다섯이 편집 상태
 * 기계(`bidvector.workflow.strategy`)를 실제 domain 계약과 대조한다.
 * `StrategyExecutors.kt`(1E strategy-watch/strategy-validation)와 같은 관심사 분리 —
 * 이 파일은 편집 세션 축만 갖고, 다른 파일의 private helper 를 참조하지 않는다(관례).
 *
 * **verifier H-3/M-4 수정 — `EditStrategyWorkflow` 경유.** `beginSession`·`apply`가
 * `internal`로 내려가면서(우회 (3) 구조적 폐쇄) 이 파일이 커널을 직접 부르던 이전 형태가
 * 컴파일되지 않게 됐다. 지금은 이 파일 안의 fake port 넷(`FakeSessionRepository`·
 * `FakeStrategyRepository`·고정 `Clock`·`RecordingEventSink`)으로 `EditStrategyWorkflow`
 * 를 조립해 `begin`→`provideValue`→[`confirm`]을 실제로 몰아 최종 `EditSessionState`를
 * 읽는다 — ⑥「모든 편집 경로가 use case 를 지난다」를 corpus 자신이 밟는 형태라 이전보다
 * 더 정직한 실행자다. `testFixtures`는 쓰지 않는다(하네스 게이트 셋을 깬다, 1B-c 선례) —
 * fake 는 이 파일 안에 둔다.
 *
 * 다섯 case 전부 같은 형태(ProvideValue [+ Confirm]) 라 하나의 executor 를 공유한다.
 * `violations`·`isConfigured`·`watchRulesEmpty`는 상태 기계의 산출이 아니라 — 상태 기계는
 * `TransitionOutcome`에 그 값을 싣지 않는다(설계 검토 (3), 과잉 판정 회피) — 실제로 마지막
 * 단계에서 쓰인 draft·policy·revision 으로 `validate()`를 다시 불러 낸 값이다(1E
 * `strategyValidationExecutor`와 같은 투영 관례).
 */

private val NOW: Instant = Instant.EPOCH
private val SESSION_POLICY = EditSessionPolicyData(Duration.ofMinutes(15))
private val OPERATOR = Actor.Operator(OperatorId("corpus-operator"))

private class FakeStrategyRepository(
    var strategy: OperatorStrategy,
) : StrategyRepository {
    override fun load(): OperatorStrategy = strategy

    override fun save(applied: AppliedStrategy) {
        strategy = applied.strategy
    }
}

private class FakeSessionRepository : EditSessionRepository {
    private val sessions = mutableMapOf<EditSessionId, EditSession>()

    override fun load(id: EditSessionId): EditSession? = sessions[id]

    override fun save(session: EditSession) {
        sessions[session.id] = session
    }
}

/** M4/4C-1 좁은 예외(`EventSink.publish(event, actor)`) 배선 — corpus 는 actor 를 투영하지 않는다(범위 밖). */
private class RecordingEventSink : EventSink {
    val published = mutableListOf<StrategyEvent>()

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        published += event
    }
}

/**
 * case 002(apply 시점 재검증)는 두 스테이지에 서로 다른 정책을 쓴다 — `strategyPolicy` 가
 * `EditStrategyWorkflow` 생성자 필드라 스테이지마다 인스턴스를 새로 조립하되 [sessions]·
 * [strategies]·[events]는 공유해 세션·전략 상태가 그대로 이어지게 한다.
 */
private fun workflowFor(
    sessions: EditSessionRepository,
    strategies: StrategyRepository,
    events: EventSink,
    policy: Resolution.Resolved<StrategyPolicyData>,
): EditStrategyWorkflow = EditStrategyWorkflow(sessions, strategies, Clock { NOW }, events, policy, SESSION_POLICY)

private fun optionalDecimal(
    node: JsonNode,
    field: String,
): BigDecimal? {
    val fieldNode = node.path(field)
    return if (fieldNode.isMissingNode || fieldNode.isNull) null else fieldNode.decimalValue()
}

private fun optionalInt(
    node: JsonNode,
    field: String,
): Int? {
    val fieldNode = node.path(field)
    return if (fieldNode.isMissingNode || fieldNode.isNull) null else fieldNode.asInt()
}

private fun scoreRangeFrom(node: JsonNode): ScoreRange =
    ScoreRange(node.path("min").decimalValue(), node.path("max").decimalValue())

private fun inclusivityFromToken(token: String): BudgetBoundInclusivity =
    when (token) {
        "Inclusive" -> BudgetBoundInclusivity.Inclusive
        "Exclusive" -> BudgetBoundInclusivity.Exclusive
        else -> error("이 corpus 가 다루지 않는 inclusivity 토큰: $token")
    }

private fun policyFrom(node: JsonNode): Resolution.Resolved<StrategyPolicyData> {
    val data =
        StrategyPolicyData(
            matchScoreRange = scoreRangeFrom(node.path("matchScoreRange")),
            probabilityScoreRange = scoreRangeFrom(node.path("probabilityScoreRange")),
            priorityScoreRange = scoreRangeFrom(node.path("priorityScoreRange")),
            budgetBoundInclusivity = inclusivityFromToken(node.path("budgetBoundInclusivity").asString()),
        )
    return Resolution.Resolved(data, PolicyVersion(EffectiveFrom.Initial, node.path("policyVersion").asString()))
}

/** 이 corpus 의 예산 값은 전부 `OperatorDeclared`·`INCLUSIVE`다(D-6 비교 성립 요건) — 다섯 case 가 그 축을 재지 않는다. */
private fun baseAmountFrom(node: JsonNode): BaseAmount =
    BaseAmount(
        node.path("amount").asLong(),
        Currency.valueOf(node.path("currency").asString()),
        VatTreatment.INCLUSIVE,
        Provenance.OperatorDeclared,
    )

private fun draftFrom(node: JsonNode): StrategyDraft {
    val minNode = node.path("minBudget")
    val maxNode = node.path("maxBudget")
    return StrategyDraft(
        minBudget = if (minNode.isMissingNode || minNode.isNull) null else baseAmountFrom(minNode),
        maxBudget = if (maxNode.isMissingNode || maxNode.isNull) null else baseAmountFrom(maxNode),
        minimumMatchScore = optionalDecimal(node, "minimumMatchScore"),
        minimumProbabilityScore = optionalDecimal(node, "minimumProbabilityScore"),
        bidNowThreshold = optionalDecimal(node, "bidNowThreshold"),
        reviewThreshold = optionalDecimal(node, "reviewThreshold"),
        candidateLimit = optionalInt(node, "candidateLimit"),
    )
}

private fun thresholdFieldFromToken(token: String): ThresholdField =
    when (token) {
        "MinimumMatchScore" -> ThresholdField.MinimumMatchScore
        "MinimumProbabilityScore" -> ThresholdField.MinimumProbabilityScore
        "BidNowThreshold" -> ThresholdField.BidNowThreshold
        "ReviewThreshold" -> ThresholdField.ReviewThreshold
        else -> error("이 corpus 가 다루지 않는 ThresholdField 토큰: $token")
    }

private fun thresholdFieldName(field: ThresholdField): String =
    when (field) {
        ThresholdField.MinimumMatchScore -> "MinimumMatchScore"
        ThresholdField.MinimumProbabilityScore -> "MinimumProbabilityScore"
        ThresholdField.BidNowThreshold -> "BidNowThreshold"
        ThresholdField.ReviewThreshold -> "ReviewThreshold"
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
        else -> error("이 corpus 가 다루지 않는 WatchRuleId 토큰: $token")
    }

private fun watchRuleIdName(id: WatchRuleId): String =
    when (id) {
        WatchRuleId.FocusCategory -> "FocusCategory"
        WatchRuleId.FocusRegion -> "FocusRegion"
        WatchRuleId.ExcludeRegion -> "ExcludeRegion"
        WatchRuleId.RequiredKeyword -> "RequiredKeyword"
        WatchRuleId.ExcludeKeyword -> "ExcludeKeyword"
        WatchRuleId.MinBudget -> "MinBudget"
        WatchRuleId.MaxBudget -> "MaxBudget"
    }

private fun editableFieldFrom(node: JsonNode): EditableField =
    when (val kind = node.path("kind").asString()) {
        "Threshold" -> EditableField.Threshold(thresholdFieldFromToken(node.path("field").asString()))
        "Watch" -> EditableField.Watch(watchRuleIdFromToken(node.path("field").asString()))
        "CandidateLimit" -> EditableField.CandidateLimit
        else -> error("이 corpus 가 다루지 않는 EditableField kind: $kind")
    }

private fun editableFieldName(field: EditableField): String =
    when (field) {
        is EditableField.Threshold -> thresholdFieldName(field.field)
        is EditableField.Watch -> watchRuleIdName(field.id)
        EditableField.CandidateLimit -> "CandidateLimit"
    }

private fun editSessionStateName(state: EditSessionState): String =
    when (state) {
        is EditSessionState.WaitingForValue -> "WaitingForValue"
        is EditSessionState.WaitingForConfirmation -> "WaitingForConfirmation"
        is EditSessionState.Applied -> "Applied"
        is EditSessionState.Cancelled -> "Cancelled"
        EditSessionState.Expired -> "Expired"
    }

private fun editSessionStateFieldName(state: EditSessionState): String? =
    when (state) {
        is EditSessionState.WaitingForValue -> editableFieldName(state.field)
        is EditSessionState.WaitingForConfirmation -> editableFieldName(state.field)
        is EditSessionState.Applied, is EditSessionState.Cancelled, EditSessionState.Expired -> null
    }

private fun strategyViolationName(violation: StrategyViolation): String =
    when (violation) {
        StrategyViolation.ReviewAboveBidNow -> "ReviewAboveBidNow"
        StrategyViolation.MinBudgetAboveMaxBudget -> "MinBudgetAboveMaxBudget"
        is StrategyViolation.ScoreOutOfRange -> "ScoreOutOfRange"
        StrategyViolation.CandidateLimitNotPositive -> "CandidateLimitNotPositive"
        is StrategyViolation.BlankTerm -> "BlankTerm"
        is StrategyViolation.BudgetLimitNotComparable -> "BudgetLimitNotComparable"
    }

/** 이 executor 가 재구성하는 입력 한 묶음 — `strategyEditExecutor` 를 50줄 아래로 유지하려는 분해다. */
private data class StrategyEditCase(
    val policy: Resolution.Resolved<StrategyPolicyData>,
    val confirmPolicy: Resolution.Resolved<StrategyPolicyData>?,
    val current: OperatorStrategy,
    val field: EditableField,
    val draft: StrategyDraft,
    val doConfirm: Boolean,
)

private fun strategyEditCaseFrom(input: JsonNode): StrategyEditCase {
    val policy = policyFrom(input.atDollarPath("$.policy"))
    val confirmPolicyNode = input.atDollarPath("$.confirmPolicy")
    val confirmPolicy =
        if (confirmPolicyNode.isMissingNode ||
            confirmPolicyNode.isNull
        ) {
            null
        } else {
            policyFrom(confirmPolicyNode)
        }
    val currentRevision = StrategyRevision(input.atDollarPath("$.currentRevision").asInt())
    val current = (validate(StrategyDraft(), currentRevision, policy) as StrategyValidation.Valid).strategy
    return StrategyEditCase(
        policy = policy,
        confirmPolicy = confirmPolicy,
        current = current,
        field = editableFieldFrom(input.atDollarPath("$.field")),
        draft = draftFrom(input.atDollarPath("$.draft")),
        doConfirm = input.atDollarPath("$.confirm").let { it.isBoolean && it.booleanValue() },
    )
}

/**
 * `begin` → `provideValue` → [선택적 `confirm`]을 실제 [EditStrategyWorkflow]로 몰아
 * 최종 [EditSessionState]를 읽는다(verifier H-3/M-4 수정 — 커널 직접 호출 대신 use case
 * 경유). 두 스테이지가 같은 [FakeSessionRepository]·[FakeStrategyRepository]를 공유한다.
 */
private fun runStrategyEdit(case: StrategyEditCase): EditSessionState {
    val sessions = FakeSessionRepository()
    val strategies = FakeStrategyRepository(case.current)
    val events = RecordingEventSink()
    val sessionId = EditSessionId("corpus-session")

    val provideWorkflow = workflowFor(sessions, strategies, events, case.policy)
    val begun = provideWorkflow.begin(sessionId, OPERATOR.id, case.field)
    check(begun is BeginOutcome.Started) { "corpus 배선 오류 — begin() 이 거부됐다: $begun" }

    val provideCommand = EditCommand.ProvideValue(CommandId("cmd-1"), sessionId, OPERATOR, case.field, case.draft)
    val provided = provideWorkflow.provideValue(provideCommand)
    check(provided is CommandResult.Processed) { "corpus 배선 오류 — provideValue() 가 세션을 못 찾았다" }
    if (!case.doConfirm) return provided.outcome.session.state

    val confirmWorkflow = workflowFor(sessions, strategies, events, case.confirmPolicy ?: case.policy)
    val confirmCommand = EditCommand.Confirm(CommandId("cmd-2"), sessionId, OPERATOR, case.current.revision)
    val confirmed = confirmWorkflow.confirm(confirmCommand)
    check(confirmed is CommandResult.Processed) { "corpus 배선 오류 — confirm() 이 세션을 못 찾았다" }
    return confirmed.outcome.session.state
}

/**
 * `violations`·`isConfigured`·`watchRulesEmpty`는 상태 기계의 산출이 아니라 — 마지막으로
 * 쓰인 draft·policy·revision 으로 [validate]를 다시 불러 낸 투영이다(1E 관례).
 */
private fun projectionOf(case: StrategyEditCase): Map<String, Any?> {
    val effectivePolicy = if (case.doConfirm) case.confirmPolicy ?: case.policy else case.policy
    val effectiveRevision =
        if (case.doConfirm) {
            StrategyRevision(
                case.current.revision.value + 1,
            )
        } else {
            case.current.revision
        }
    val recomputed = validate(case.draft, effectiveRevision, effectivePolicy)
    return mapOf(
        "violations" to violationsOf(recomputed),
        "isConfigured" to (recomputed as? StrategyValidation.Valid)?.strategy?.isConfigured(),
        "watchRulesEmpty" to (recomputed as? StrategyValidation.Valid)?.strategy?.watchRules?.isEmpty(),
    )
}

private fun violationsOf(result: StrategyValidation): List<String> =
    when (result) {
        is StrategyValidation.Valid -> emptyList()
        is StrategyValidation.Invalid -> result.violations.map(::strategyViolationName)
    }

private fun strategyEditExecutor(input: JsonNode): Map<String, Any?> {
    val case = strategyEditCaseFrom(input)
    val finalState = runStrategyEdit(case)
    return mapOf(
        "state" to editSessionStateName(finalState),
        "field" to editSessionStateFieldName(finalState),
    ) + projectionOf(case)
}

internal val STRATEGY_EDIT_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "strategy-edit-001" to ::strategyEditExecutor,
        "strategy-edit-002" to ::strategyEditExecutor,
        "strategy-edit-003" to ::strategyEditExecutor,
        "strategy-edit-004" to ::strategyEditExecutor,
        "strategy-edit-005" to ::strategyEditExecutor,
    )
