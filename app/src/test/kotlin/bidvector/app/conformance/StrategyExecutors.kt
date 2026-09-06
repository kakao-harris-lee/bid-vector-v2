package bidvector.app.conformance

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.BudgetBound
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.CategoryCode
import bidvector.strategy.FullScopeText
import bidvector.strategy.KeywordScopeText
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.StrategyViolation
import bidvector.strategy.WatchRuleId
import bidvector.strategy.WatchRules
import bidvector.strategy.WatchSubject
import bidvector.strategy.WatchUndeterminableReason
import bidvector.strategy.WatchVerdict
import bidvector.strategy.evaluate
import bidvector.strategy.isConfigured
import bidvector.strategy.validate
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

/*
 * M1/1E ⑪ — strategy-watch·strategy-validation·money-basis-003(D-2) case → executor
 * dispatch. `CorpusExecutors.kt`(1B-c·1C)·`ProvenanceFloorExecutors.kt`(1D)와 같은 관심사
 * 분리 — `SharedKernelCorpusConformanceTest.kt` 의 dispatch 표(`VALUE_EXECUTORS`)가 이
 * 파일의 [STRATEGY_EXECUTORS] 를 합친다. 이 파일도 `strategy` 공개 API 만 부른다 — 이전
 * 슬라이스들과 같은 이유로 다른 파일의 `private` helper 를 참조하지 않고 스스로 조립한다.
 */

// ---- 공용 JSON → strategy 값 조립 ----

private fun stringListFrom(node: JsonNode): List<String> =
    if (node.isArray) node.values().map { it.asString() } else emptyList()

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

private fun inclusivityFromToken(token: String): BudgetBoundInclusivity =
    when (token) {
        "Inclusive" -> BudgetBoundInclusivity.Inclusive
        "Exclusive" -> BudgetBoundInclusivity.Exclusive
        else -> error("이 corpus 가 다루지 않는 inclusivity 토큰: $token")
    }

/** 이 corpus의 provenance 토큰은 `OperatorDeclared`·`Published` 둘뿐이다(D-6·money-basis-003). */
private fun strategyProvenanceFrom(node: JsonNode): Provenance {
    val provenanceNode = node.path("provenance")
    require(!provenanceNode.isMissingNode && !provenanceNode.isNull) {
        "provenance 는 명시 선언이어야 한다 — 이 corpus 는 누락을 지어내지 않는다"
    }
    return when (val name = provenanceNode.asString()) {
        "OperatorDeclared" -> Provenance.OperatorDeclared
        "Published" -> Provenance.Published(node.path("noticeRevision").asString("0").toIntOrNull() ?: 0)
        else -> error("이 corpus 가 다루지 않는 provenance 토큰: $name")
    }
}

/** 이 corpus의 예산·공고 금액은 전부 `BASE_AMOUNT` basis 다(D-6, R-BASIS-01). */
private fun baseAmountFrom(node: JsonNode): BaseAmount {
    require(node.path("basis").asString() == "BASE_AMOUNT") { "이 corpus 는 BASE_AMOUNT 만 다룬다: $node" }
    val won = node.path("amount").asLong()
    val currency = Currency.valueOf(node.path("currency").asString())
    val vatTreatment = VatTreatment.valueOf(node.path("vatTreatment").asString())
    return BaseAmount(won, currency, vatTreatment, strategyProvenanceFrom(node))
}

/** `{"absent": "EMPTY_INPUT"}` 형태와 금액 객체 형태를 가른다 — §1.3 부재 1급. */
private fun factBaseAmountFrom(node: JsonNode): Fact<BaseAmount> {
    val absentNode = node.path("absent")
    if (!absentNode.isMissingNode && !absentNode.isNull) {
        return Fact.Absent(ReasonCode.valueOf(absentNode.asString()))
    }
    return Fact.Known(baseAmountFrom(node))
}

private fun budgetBoundFrom(node: JsonNode): BudgetBound {
    val minNode = node.path("min")
    val maxNode = node.path("max")
    val min = if (minNode.isMissingNode || minNode.isNull) null else baseAmountFrom(minNode)
    val max = if (maxNode.isMissingNode || maxNode.isNull) null else baseAmountFrom(maxNode)
    return BudgetBound(min, max, inclusivityFromToken(node.path("inclusivity").asString()))
}

private fun watchRulesFrom(node: JsonNode): WatchRules =
    WatchRules(
        focusCategories = stringListFrom(node.path("focusCategories")).map(::CategoryCode).toSet(),
        focusRegionTerms = stringListFrom(node.path("focusRegionTerms")),
        excludeRegionTerms = stringListFrom(node.path("excludeRegionTerms")),
        requiredKeywordTerms = stringListFrom(node.path("requiredKeywordTerms")),
        excludeKeywordTerms = stringListFrom(node.path("excludeKeywordTerms")),
        budget = budgetBoundFrom(node.path("budget")),
    )

private fun watchSubjectFrom(node: JsonNode): WatchSubject =
    WatchSubject(
        categories = stringListFrom(node.path("categories")).map(::CategoryCode).toSet(),
        keywordText = KeywordScopeText(node.path("keywordText").asString()),
        fullText = FullScopeText(node.path("fullText").asString()),
        baseAmount = factBaseAmountFrom(node.path("baseAmount")),
    )

// ---- strategy-watch projection ----

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

private fun watchUndeterminableReasonProjection(reason: WatchUndeterminableReason): Map<String, Any?> =
    when (reason) {
        is WatchUndeterminableReason.BaseAmountAbsent -> {
            mapOf("kind" to "BaseAmountAbsent", "reasonCode" to reason.reason.name)
        }

        is WatchUndeterminableReason.BudgetNotComparable -> {
            mapOf("kind" to "BudgetNotComparable", "reasonCode" to reason.reason.name)
        }
    }

/** `verdict`·`matched`·`failed`·`reason` 넷을 항상 낸다 — 부재는 `null` 이지 빈 배열이 아니다(§1.3). */
private fun watchVerdictProjection(verdict: WatchVerdict): Map<String, Any?> {
    val projection = mutableMapOf<String, Any?>("matched" to null, "failed" to null, "reason" to null)
    when (verdict) {
        is WatchVerdict.Passed -> {
            projection["verdict"] = "Passed"
            projection["matched"] = verdict.matched.map(::watchRuleIdName)
        }

        is WatchVerdict.Rejected -> {
            projection["verdict"] = "Rejected"
            projection["failed"] = verdict.failed.map(::watchRuleIdName)
        }

        WatchVerdict.NoGate -> {
            projection["verdict"] = "NoGate"
        }

        is WatchVerdict.Undeterminable -> {
            projection["verdict"] = "Undeterminable"
            projection["reason"] = watchUndeterminableReasonProjection(verdict.reason)
        }
    }
    return projection
}

private fun strategyWatchExecutor(input: JsonNode): Map<String, Any?> {
    val subject = watchSubjectFrom(input.atDollarPath("$.subject"))
    val rules = watchRulesFrom(input.atDollarPath("$.watchRules"))
    return watchVerdictProjection(rules.evaluate(subject))
}

// ---- strategy-validation projection ----

private fun scoreRangeFrom(node: JsonNode): ScoreRange =
    ScoreRange(node.path("min").decimalValue(), node.path("max").decimalValue())

private fun strategyPolicyFrom(node: JsonNode): Resolution.Resolved<StrategyPolicyData> {
    val data =
        StrategyPolicyData(
            matchScoreRange = scoreRangeFrom(node.path("matchScoreRange")),
            probabilityScoreRange = scoreRangeFrom(node.path("probabilityScoreRange")),
            priorityScoreRange = scoreRangeFrom(node.path("priorityScoreRange")),
            budgetBoundInclusivity = inclusivityFromToken(node.path("budgetBoundInclusivity").asString()),
        )
    return Resolution.Resolved(data, PolicyVersion(EffectiveFrom.Initial, node.path("policyVersion").asString()))
}

private fun strategyDraftFrom(node: JsonNode): StrategyDraft {
    val minBudgetNode = node.path("minBudget")
    val maxBudgetNode = node.path("maxBudget")
    return StrategyDraft(
        focusCategories = stringListFrom(node.path("focusCategories")),
        focusRegionTerms = stringListFrom(node.path("focusRegionTerms")),
        excludeRegionTerms = stringListFrom(node.path("excludeRegionTerms")),
        requiredKeywordTerms = stringListFrom(node.path("requiredKeywordTerms")),
        excludeKeywordTerms = stringListFrom(node.path("excludeKeywordTerms")),
        minBudget = if (minBudgetNode.isMissingNode || minBudgetNode.isNull) null else baseAmountFrom(minBudgetNode),
        maxBudget = if (maxBudgetNode.isMissingNode || maxBudgetNode.isNull) null else baseAmountFrom(maxBudgetNode),
        minimumMatchScore = optionalDecimal(node, "minimumMatchScore"),
        minimumProbabilityScore = optionalDecimal(node, "minimumProbabilityScore"),
        bidNowThreshold = optionalDecimal(node, "bidNowThreshold"),
        reviewThreshold = optionalDecimal(node, "reviewThreshold"),
        candidateLimit = optionalInt(node, "candidateLimit"),
    )
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

/**
 * `isConfigured`·`watchRulesEmpty` 는 `Invalid` 에는 설 자리가 없다(D-10 — 거부된 편집에는
 * 전략 값이 없다, manifest strategy-validation-001 `not_covered` ①) — `null` 로 낸다.
 */
private fun strategyValidationProjection(result: StrategyValidation): Map<String, Any?> =
    when (result) {
        is StrategyValidation.Valid -> {
            mapOf(
                "validation" to "Valid",
                "violations" to emptyList<String>(),
                "policyVersion" to result.policyVersion.source,
                "isConfigured" to result.strategy.isConfigured(),
                "watchRulesEmpty" to result.strategy.watchRules.isEmpty(),
            )
        }

        is StrategyValidation.Invalid -> {
            mapOf(
                "validation" to "Invalid",
                "violations" to result.violations.map(::strategyViolationName),
                "policyVersion" to result.policyVersion.source,
                "isConfigured" to null,
                "watchRulesEmpty" to null,
            )
        }
    }

private fun strategyValidationExecutor(input: JsonNode): Map<String, Any?> {
    val draft = strategyDraftFrom(input.atDollarPath("$.draft"))
    val revision = StrategyRevision(input.atDollarPath("$.revision").asInt())
    val policy = strategyPolicyFrom(input.atDollarPath("$.policy"))
    return strategyValidationProjection(validate(draft, revision, policy))
}

// ---- money-basis-003(D-2) — 감시 경로와 검색 경로가 같은 predicate 를 쓴다 ----

private fun budgetBoundForField(
    field: String,
    amount: BaseAmount,
): BudgetBound =
    when (field) {
        "minBudget" -> BudgetBound(amount, null, BudgetBoundInclusivity.Inclusive)
        "maxBudget" -> BudgetBound(null, amount, BudgetBoundInclusivity.Inclusive)
        else -> error("이 corpus 는 field='$field' 를 다루지 않는다 — 지원: minBudget, maxBudget")
    }

private fun watchVerdictPassed(verdict: WatchVerdict): Boolean =
    when (verdict) {
        is WatchVerdict.Passed -> {
            true
        }

        is WatchVerdict.Rejected -> {
            false
        }

        WatchVerdict.NoGate -> {
            error("이 case 는 예산 규칙이 있는 입력만 다룬다 — NoGate 는 예상 밖이다")
        }

        is WatchVerdict.Undeterminable -> {
            error("이 case 는 비교가 성립하는 입력만 다룬다 — Undeterminable 은 예상 밖이다")
        }
    }

/**
 * D-11 — `EvaluationPath` 는 predicate 의 입력이 아니라 이 runner 투영의 축이다. 커널
 * 함수(`WatchRules.evaluate`)는 경로를 모른다 — 「두 경로가 같은 답」은 이 함수를
 * `evaluatedVia` 각 경로 이름 아래에서 **그대로 다시** 부른다는 구조 자체가 보장한다.
 * `outcome`(`Comparable`)은 manifest verified_paths 밖이다(curator 판단 — 미승인 토큰,
 * change_history) — 이 executor 는 그 자리를 내지 않는다.
 */
private fun moneyBasis003Executor(input: JsonNode): Map<String, Any?> {
    val operatorFilterNode = input.atDollarPath("$.operatorFilter")
    val operatorAmount = baseAmountFrom(operatorFilterNode)
    val noticeAmount = baseAmountFrom(input.atDollarPath("$.noticeAmount"))
    val bound = budgetBoundForField(operatorFilterNode.path("field").asString(), operatorAmount)
    val rules = WatchRules(emptySet(), emptyList(), emptyList(), emptyList(), emptyList(), bound)
    val subject = WatchSubject(emptySet(), KeywordScopeText(""), FullScopeText(""), Fact.Known(noticeAmount))
    val paths = stringListFrom(input.atDollarPath("$.evaluatedVia"))
    val perPath =
        paths.associateWith { _ ->
            val verdict = rules.evaluate(subject)
            mapOf("result" to watchVerdictPassed(verdict), "basisUsed" to noticeAmount.basis.name)
        }
    val results = perPath.values.map { it["result"] }.distinct()
    return mapOf("perPath" to perPath, "pathsAgree" to (results.size <= 1))
}

internal val STRATEGY_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "strategy-watch-001" to ::strategyWatchExecutor,
        "strategy-watch-002" to ::strategyWatchExecutor,
        "strategy-watch-003" to ::strategyWatchExecutor,
        "strategy-watch-004" to ::strategyWatchExecutor,
        "strategy-watch-005" to ::strategyWatchExecutor,
        "strategy-watch-006" to ::strategyWatchExecutor,
        "strategy-watch-007" to ::strategyWatchExecutor,
        "strategy-watch-008" to ::strategyWatchExecutor,
        "strategy-validation-001" to ::strategyValidationExecutor,
        "strategy-validation-002" to ::strategyValidationExecutor,
        "strategy-validation-003" to ::strategyValidationExecutor,
        "money-basis-003" to ::moneyBasis003Executor,
    )
