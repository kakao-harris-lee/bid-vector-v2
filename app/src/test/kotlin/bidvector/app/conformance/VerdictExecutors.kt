package bidvector.app.conformance

import bidvector.decision.BidNowReason
import bidvector.decision.FloorOverrideOutcome
import bidvector.decision.FloorOverrideValidation
import bidvector.decision.LadderInput
import bidvector.decision.MlUnavailableReason
import bidvector.decision.PlausibilityBand
import bidvector.decision.ReviewReason
import bidvector.decision.SkipReason
import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.decision.VerdictLadder
import bidvector.decision.VerdictLadderPolicyData
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import tools.jackson.databind.JsonNode

/*
 * M4/4B-1 — verdict case → executor(scope.md ⑧). `decision`은 도메인 모듈이라 이 파일이
 * `VerdictLadder.judge`·`FloorOverrideValidation.validate`(둘 다 public — 설계 검토 (2))를
 * 직접 부른다. `Verdict`·reason 하위 타입 생성자가 `internal`이라도 이 실행자는 그 값을
 * 조립하지 않는다 — [VerdictLadder.judge]가 낸 값을 **읽기만** 해서 projection 을 만든다.
 *
 * `verdict-001~004`는 수정 라운드 1(2026-09-09, 운영자 결정 (a))에서 `authoritative`로
 * 승격됐다 — M0 저작 시점(커널 이전)의 입력을 이 커널의 입력 계약(`$.input`/`$.policy`,
 * `$.override`/`$.band`)으로 재구성해 이 표에 넣었다(각 case가 단언하는 규칙은 불변,
 * manifest.yaml 의 `change_history` 참고). 열둘 전부 이제 이 표에 있다.
 */

private fun optionalUnitScore(
    node: JsonNode,
    field: String,
): UnitScore? {
    val fieldNode = node.path(field)
    return if (fieldNode.isMissingNode || fieldNode.isNull) null else UnitScore(fieldNode.decimalValue())
}

private fun ladderInputFrom(input: JsonNode): LadderInput {
    val node = input.atDollarPath("$.input")
    return LadderInput(
        priorityScore = optionalUnitScore(node, "priorityScore"),
        probabilityScore = optionalUnitScore(node, "probabilityScore"),
        matchedScore = optionalUnitScore(node, "matchedScore"),
        currentActiveBids = node.path("currentActiveBids").asInt(),
        maxActiveBids = node.path("maxActiveBids").asInt(),
    )
}

private fun ladderPolicyFrom(input: JsonNode): Resolution.Resolved<VerdictLadderPolicyData> {
    val policyNode = input.atDollarPath("$.policy")
    val data =
        VerdictLadderPolicyData(
            capacityHoldPriorityThreshold = policyNode.path("capacityHoldPriorityThreshold").decimalValue(),
            bidNowThreshold = policyNode.path("bidNowThreshold").decimalValue(),
            reviewThreshold = policyNode.path("reviewThreshold").decimalValue(),
            forceBidProbabilityThreshold = policyNode.path("forceBidProbabilityThreshold").decimalValue(),
            forceBidMatchedThreshold = policyNode.path("forceBidMatchedThreshold").decimalValue(),
        )
    return Resolution.Resolved(data, PolicyVersion(EffectiveFrom.Initial, policyNode.path("policyVersion").asString()))
}

private fun skipReasonName(reason: SkipReason): String =
    when (reason) {
        SkipReason.CapacityHold -> "CapacityHold"
        SkipReason.LowPriority -> "LowPriority"
    }

private fun mlUnavailableReasonName(reason: MlUnavailableReason): String =
    when (reason) {
        MlUnavailableReason.ScoreNotProvided -> "ScoreNotProvided"

        // M4/4D-1(D-4D-6) 이 넓힌 값 아홉 — conformance harness projection 은 이름만 낸다,
        // 이 커널 판단은 이 값들을 만들지 않는다(4D-1 gateway 만 짓는다).
        MlUnavailableReason.DeadlineExceeded -> "DeadlineExceeded"

        MlUnavailableReason.CircuitOpen -> "CircuitOpen"

        MlUnavailableReason.RetryBudgetExhausted -> "RetryBudgetExhausted"

        MlUnavailableReason.TransportFailed -> "TransportFailed"

        MlUnavailableReason.ModelNotReady -> "ModelNotReady"

        MlUnavailableReason.ReleaseMismatch -> "ReleaseMismatch"

        MlUnavailableReason.ContractViolation -> "ContractViolation"

        MlUnavailableReason.UnsupportedSchema -> "UnsupportedSchema"

        MlUnavailableReason.UnsupportedRelease -> "UnsupportedRelease"

        MlUnavailableReason.InvalidRequest -> "InvalidRequest"
    }

private fun bidNowReasonProjection(reason: BidNowReason): Map<String, Any?> =
    when (reason) {
        is BidNowReason.PriorityAboveBidNowThreshold -> {
            mapOf(
                "type" to "PriorityAboveBidNowThreshold",
                "priority" to reason.priority,
                "threshold" to reason.threshold,
            )
        }

        is BidNowReason.ForceBidOverride -> {
            mapOf(
                "type" to "ForceBidOverride",
                "probability" to reason.probability,
                "matched" to reason.matched,
                "probabilityThreshold" to reason.probabilityThreshold,
                "matchedThreshold" to reason.matchedThreshold,
            )
        }
    }

private fun reviewReasonProjection(reason: ReviewReason): Map<String, Any?> =
    when (reason) {
        is ReviewReason.PriorityInReviewBand -> {
            mapOf(
                "type" to "PriorityInReviewBand",
                "priority" to reason.priority,
                "reviewThreshold" to reason.reviewThreshold,
                "bidNowThreshold" to reason.bidNowThreshold,
            )
        }

        is ReviewReason.MlUnavailable -> {
            mapOf("type" to "MlUnavailable", "reason" to mlUnavailableReasonName(reason.reason))
        }
    }

/** [Verdict] → projection(scope.md ①). 세 갈래 소진 `when` — 새 [Verdict] 하위 타입이 생기면 컴파일이 요구한다. */
private fun verdictProjection(verdict: Verdict): Map<String, Any?> =
    when (verdict) {
        is Verdict.BidNow -> {
            mapOf("verdict" to "BidNow", "reasons" to verdict.reasons.map(::bidNowReasonProjection))
        }

        is Verdict.Review -> {
            mapOf("verdict" to "Review", "reasons" to verdict.reasons.map(::reviewReasonProjection))
        }

        is Verdict.Skip -> {
            mapOf("verdict" to "Skip", "skipReason" to skipReasonName(verdict.reason))
        }
    }

/** 사다리 실행자(①②④⑤ — 네 분기·force-bid·ML 부재·순서 민감도). */
private fun verdictLadderExecutor(input: JsonNode): Map<String, Any?> {
    val ladderInput = ladderInputFrom(input)
    val policy = ladderPolicyFrom(input)
    val verdict = VerdictLadder.judge(ladderInput, policy)
    return verdictProjection(verdict)
}

/** override 검증 실행자(⑥, `OPEN-DEC-04`). */
private fun floorOverrideExecutor(input: JsonNode): Map<String, Any?> {
    val rate = input.atDollarPath("$.override.rate").decimalValue()
    val bandNode = input.atDollarPath("$.band")
    val band = PlausibilityBand(bandNode.path("min").decimalValue(), bandNode.path("max").decimalValue())

    return when (val outcome = FloorOverrideValidation.validate(rate, band)) {
        is FloorOverrideOutcome.Accepted -> {
            mapOf(
                "overrideOutcome" to "Accepted",
                "reasonCode" to null,
                "silentlyDropped" to false,
                "silentlyAccepted" to false,
                "observableInResult" to true,
            )
        }

        is FloorOverrideOutcome.Rejected -> {
            mapOf(
                "overrideOutcome" to "Rejected",
                // 이 corpus 유일 사유 — 값은 밴드 밖 하나뿐이라 리터럴로 낸다(runner 투영,
                // domain 타입 자체에 reasonCode enum 은 없다 — Rejected 가 rate·band 를
                // 그대로 나른다).
                "reasonCode" to "OverrideOutsidePlausibilityBand",
                "silentlyDropped" to false,
                "silentlyAccepted" to false,
                "observableInResult" to true,
            )
        }
    }
}

internal val VERDICT_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "verdict-001" to ::verdictLadderExecutor,
        "verdict-002" to ::verdictLadderExecutor,
        "verdict-003" to ::verdictLadderExecutor,
        "verdict-004" to ::floorOverrideExecutor,
        "verdict-005" to ::verdictLadderExecutor,
        "verdict-006" to ::verdictLadderExecutor,
        "verdict-007" to ::verdictLadderExecutor,
        "verdict-008" to ::verdictLadderExecutor,
        "verdict-009" to ::verdictLadderExecutor,
        "verdict-010" to ::verdictLadderExecutor,
        "verdict-011" to ::verdictLadderExecutor,
        "verdict-012" to ::floorOverrideExecutor,
    )
