package bidvector.app.http

import bidvector.decision.BidNowReason
import bidvector.decision.MlUnavailableReason
import bidvector.decision.ReviewReason
import bidvector.decision.SkipReason
import bidvector.decision.Verdict
import bidvector.procurement.ResolvedBaseAmount
import bidvector.qualification.LicenseJudgement
import bidvector.qualification.LicenseValidity
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.WatchUndeterminableReason
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.EvaluationDropReason
import bidvector.workflow.evaluation.EvaluationStage
import bidvector.workflow.evaluation.MlAnalysisOutcome

/**
 * 투찰가 요청 응답의 **어휘와 사상**(M7/7C ②) — 도메인 sealed 타입을 wire 코드로 옮기는 자리.
 * `BidPriceRequestResponse.kt` 에서 파일 크기 한도로 갈라 나왔고 경계는 「응답 **모양**」과
 * 「응답 **어휘**」다(기능 변경 없음).
 *
 * 모든 사상은 **소진 `when`** 이다 — 도메인에 갈래가 생기면 이 자리가 컴파일로 드러난다.
 * `else` 를 쓰지 않는 것이 그 성질을 만든다.
 */

/**
 * 고지 — **무엇을 고지해야 하는가**만 나르고 한국어 문장은 표면이 짓는다(DEC-12 A1: legacy 는
 * 문구를 영속해 계산이 바뀐 뒤에도 옛 분모를 말했다). 앞 둘은 `decision.menu.BidMenuDisclosure`
 * 의 variant 와 이름이 맞물린다 — 그 커널이 머지되는 slice 가 이 둘을 그 enum 매핑으로 바꾸고
 * 집합 등식 test 를 더한다(어휘 이중화를 그때 닫는다).
 *
 * **전수를 늘 싣는다.** 메뉴가 서지 않아도 「자동 제출이 아니다」·「예정가격은 비공개 추첨」은
 * 투찰가 요청 자체에 대한 사실이고, 그 고지를 빼면 가격 화면이 안내 없이 뜬다.
 */
enum class BidPriceDisclosure {
    YEGA_DRAWN_PRIVATELY,
    NOT_AUTO_SUBMITTED,
    STATUTORY_FLOOR_RATE_NOT_INTERPRETED,
    PRICE_FITNESS_IS_NOT_PROBABILITY,
}

/** 게시 하한율이 근거로 설 수 있는가 — 「없다」와 「게시값이 아니다」를 가른다(DEC-03). */
enum class FloorRateGroundsState {
    USABLE,
    ABSENT,
    ORIGIN_NOT_PUBLISHED,
}

/**
 * 미보유 면허를 평탄하게 낼 수 있는가(QUAL-03 그룹 간 OR) — **합집합으로 접지 않는다.**
 * 그룹이 둘 이상이면 「이 면허들이 다 필요하다」로 읽히므로 목록을 비우고 이 상태로 말한다.
 */
enum class LicenseMissingDetailState {
    NOT_APPLICABLE,
    SINGLE_GROUP,
    MULTIPLE_GROUPS_NOT_FLATTENED,
}

/** 이 slice 가 산출하지 않는 축의 상태 — 값 하나다. 값이 서는 slice 가 갈래를 더한다. */
enum class NotComputedState {
    NOT_COMPUTED,
}

/** 신뢰도 상태 — 값 하나다. 값이 서면 그것은 가격 적합도이고 확률이 아니다(ML-03). */
enum class ConfidenceState {
    NOT_AVAILABLE,
}

/**
 * 하한 미달 빈도를 재지 못한 사유 — **`FloorUnmeasurableReason` 을 쓰지 않는다.** 그 어휘는
 * 「재려 했으나 재지 못했다」이고 여기는 「잴 기계가 배선되지 않았다」다. 표본이 0 이라고 적으면
 * 보지 않은 것을 봤다고 말하는 것이 된다.
 */
enum class FloorShortfallNotComputedReason {
    SHORTFALL_POLICY_NOT_PUBLISHED,
}

/**
 * 3안을 산출하지 않은 사유 — **이 배선에 산출 경로가 없다**는 사실 하나만 말한다. 커널을 불러
 * 「출처가 없었다」를 받은 것이 아니다(부르지 않았다). 상류 사실 둘(DEC-05 커널 미머지 · 후보
 * 비율의 출처가 미가용 ML)은 계약 문서의 operation description 이 적는다.
 */
enum class BidPriceMenuNotComputedReason {
    MENU_PATH_NOT_WIRED,
}

/** 알림함 기록 상태 — 쓰기 포트가 머지되기 전의 사실이다(R-4 공백을 계약 문면으로 둔다). */
enum class InboxRecordState {
    NOT_RECORDED_YET,
}

enum class InboxRecordNotRecordedReason {
    INBOX_WRITE_PORT_NOT_MERGED,
}

/** 평가가 판정에 이르렀는가 — `CandidateEvaluation` 두 갈래. */
enum class EvaluationOutcomeLabel {
    REACHED,
    NOT_REACHED,
}

/** ML 단계에서 무엇이 일어났는가 — 「이르지 못했다」와 「미가용」은 다른 사실이다. */
enum class MlAnalysisStateLabel {
    ANALYZED,
    UNAVAILABLE,
    SIMILARITY_PROJECTION_NOT_READY,
    NOT_REACHED,
}

/** 신뢰도가 없는 사유 — ML 상태에서 기계적으로 따라온다(두 자리에 다른 값을 두지 않는다). */
enum class ConfidenceNotAvailableReason {
    ML_SCORE_NOT_PROVIDED,
    ML_UNAVAILABLE,
    SIMILARITY_PROJECTION_NOT_READY,
    EVALUATION_NOT_REACHED_ML_STAGE,
}

internal fun outcomeLabelOf(evaluation: CandidateEvaluation): EvaluationOutcomeLabel =
    when (evaluation) {
        is CandidateEvaluation.Reached -> EvaluationOutcomeLabel.REACHED
        is CandidateEvaluation.NotReached -> EvaluationOutcomeLabel.NOT_REACHED
    }

internal fun verdictLabelOf(evaluation: CandidateEvaluation): String? =
    when (evaluation) {
        is CandidateEvaluation.NotReached -> null
        is CandidateEvaluation.Reached ->
            when (evaluation.verdict) {
                is Verdict.BidNow -> "BID_NOW"
                is Verdict.Review -> "REVIEW"
                is Verdict.Skip -> "SKIP"
            }
    }

internal fun verdictReasonCodesOf(evaluation: CandidateEvaluation): List<String> =
    when (evaluation) {
        is CandidateEvaluation.NotReached -> emptyList()
        is CandidateEvaluation.Reached ->
            when (val verdict = evaluation.verdict) {
                is Verdict.BidNow -> verdict.reasons.map(::bidNowReasonCode)
                is Verdict.Review -> verdict.reasons.map(::reviewReasonCode)
                is Verdict.Skip -> listOf(skipReasonCode(verdict.reason))
            }
    }

private fun bidNowReasonCode(reason: BidNowReason): String =
    when (reason) {
        is BidNowReason.PriorityAboveBidNowThreshold -> "PRIORITY_ABOVE_BID_NOW_THRESHOLD"
        is BidNowReason.ForceBidOverride -> "FORCE_BID_OVERRIDE"
    }

private fun reviewReasonCode(reason: ReviewReason): String =
    when (reason) {
        is ReviewReason.PriorityInReviewBand -> "PRIORITY_IN_REVIEW_BAND"
        is ReviewReason.MlUnavailable -> "ML_UNAVAILABLE"
    }

private fun skipReasonCode(reason: SkipReason): String =
    when (reason) {
        SkipReason.CapacityHold -> "CAPACITY_HOLD"
        SkipReason.LowPriority -> "LOW_PRIORITY"
    }

internal fun licenseValidityLabelOf(judgement: LicenseJudgement): String =
    when (judgement.validity) {
        LicenseValidity.NotVerified -> "NOT_VERIFIED"
    }

/**
 * 정책 version 의 시행일 — **소진 `when`** 이다. `Initial`(정책의 최초 판, 시행일이라는 값
 * 자체가 없다)은 `null` 이고, 그 둘을 한 문자열로 합치지 않는다(합치면 구분자를 지어내야 하고
 * 호출부가 그것을 다시 쪼갠다).
 */
internal fun effectiveFromLabelOf(version: PolicyVersion): String? =
    when (val effectiveFrom = version.effectiveFrom) {
        EffectiveFrom.Initial -> null
        is EffectiveFrom.On -> effectiveFrom.date.toString()
    }

internal fun baseAmountResolutionLabel(resolved: ResolvedBaseAmount): String =
    when (resolved) {
        is ResolvedBaseAmount.Direct -> "DIRECT"
        is ResolvedBaseAmount.FallbackFromBudget -> "FALLBACK_FROM_BUDGET"
        is ResolvedBaseAmount.DerivedFromOpeningAmount -> "DERIVED_FROM_OPENING"
    }

internal fun floorRateOriginLabel(floor: FloorRate): String =
    when (floor.origin) {
        is FloorRateOrigin.NoticeValue -> "NOTICE_VALUE"
        is FloorRateOrigin.StatutoryTable -> "STATUTORY_TABLE"
    }

/**
 * **게시값만 근거다**(운영자 결정 2026-10-15) — 법정 하한율 표에서 온 값은 근거 칸에 싣지
 * 않는다. V2 에 법정 하한율 해석기가 없어 그런 값이 들어올 경로는 오늘 없지만, 타입이 그 값을
 * 표현할 수 있는 한 이 분기가 그 자리를 막는다.
 */
internal fun usableFloorRateOf(floor: FloorRate?): FloorRate? =
    floor?.takeIf { it.origin is FloorRateOrigin.NoticeValue }

/** 게시값 출처가 나르는 공고 차수 — 법정 표 출처에는 그 값이 없다(소진 `when`). */
internal fun publishedOriginRoundOf(floor: FloorRate?): String? =
    when (val origin = floor?.origin) {
        null -> null
        is FloorRateOrigin.NoticeValue -> origin.noticeRevision.value
        is FloorRateOrigin.StatutoryTable -> null
    }

internal fun floorRateGroundsStateOf(floor: FloorRate?): FloorRateGroundsState =
    when {
        floor == null -> FloorRateGroundsState.ABSENT
        floor.origin is FloorRateOrigin.NoticeValue -> FloorRateGroundsState.USABLE
        else -> FloorRateGroundsState.ORIGIN_NOT_PUBLISHED
    }

internal fun mlAnalysisStateOf(outcome: MlAnalysisOutcome?): MlAnalysisStateLabel =
    when (outcome) {
        null -> MlAnalysisStateLabel.NOT_REACHED
        is MlAnalysisOutcome.Analyzed -> MlAnalysisStateLabel.ANALYZED
        is MlAnalysisOutcome.Unavailable -> MlAnalysisStateLabel.UNAVAILABLE
        is MlAnalysisOutcome.SimilarityProjectionNotReady ->
            MlAnalysisStateLabel.SIMILARITY_PROJECTION_NOT_READY
    }

internal fun mlUnavailableReasonLabelOf(outcome: MlAnalysisOutcome?): String? =
    (outcome as? MlAnalysisOutcome.Unavailable)
        ?.let { mlUnavailableReasonCode(it.reason) }

private fun mlUnavailableReasonCode(reason: MlUnavailableReason): String =
    when (reason) {
        MlUnavailableReason.ScoreNotProvided -> "SCORE_NOT_PROVIDED"
        MlUnavailableReason.DeadlineExceeded -> "DEADLINE_EXCEEDED"
        MlUnavailableReason.CircuitOpen -> "CIRCUIT_OPEN"
        MlUnavailableReason.RetryBudgetExhausted -> "RETRY_BUDGET_EXHAUSTED"
        MlUnavailableReason.TransportFailed -> "TRANSPORT_FAILED"
        MlUnavailableReason.ModelNotReady -> "MODEL_NOT_READY"
        MlUnavailableReason.ReleaseMismatch -> "RELEASE_MISMATCH"
        MlUnavailableReason.ContractViolation -> "CONTRACT_VIOLATION"
        MlUnavailableReason.UnsupportedSchema -> "UNSUPPORTED_SCHEMA"
        MlUnavailableReason.UnsupportedRelease -> "UNSUPPORTED_RELEASE"
        MlUnavailableReason.InvalidRequest -> "INVALID_REQUEST"
    }

internal fun confidenceNotAvailableReasonOf(state: MlAnalysisStateLabel): ConfidenceNotAvailableReason =
    when (state) {
        MlAnalysisStateLabel.NOT_REACHED -> ConfidenceNotAvailableReason.EVALUATION_NOT_REACHED_ML_STAGE
        MlAnalysisStateLabel.UNAVAILABLE -> ConfidenceNotAvailableReason.ML_UNAVAILABLE
        MlAnalysisStateLabel.SIMILARITY_PROJECTION_NOT_READY ->
            ConfidenceNotAvailableReason.SIMILARITY_PROJECTION_NOT_READY
        // 분석됐어도 이 slice 는 가격 적합도를 싣지 않는다 — 점수 자리를 만들지 않았다.
        MlAnalysisStateLabel.ANALYZED -> ConfidenceNotAvailableReason.ML_SCORE_NOT_PROVIDED
    }

/** 자격 축의 평탄한 칸들 — 한 판정에서 기계적으로 따라온다. */
internal class LicenseFields(
    val verdict: String,
    val uncertainReason: String?,
    val requiredLicenses: List<String>?,
    val sourceFields: List<String>,
    val groupsSatisfied: List<String>,
    val groupsMissing: List<String>,
    val missingLicenses: List<String>,
    val missingDetailState: String,
)

internal fun licenseFieldsOf(judgement: LicenseJudgement): LicenseFields {
    val verdict = judgement.verdict
    val satisfied =
        (verdict as? LicenseVerdict.Eligible)
            ?.satisfiedGroups
            ?.map(::requirementGroupLabel)
            .orEmpty()
    val missingByGroup = judgement.missingByGroup
    val singleGroup = missingByGroup.entries.singleOrNull()
    return LicenseFields(
        verdict = licenseVerdictLabel(verdict),
        uncertainReason = (verdict as? LicenseVerdict.Uncertain)?.reason?.let(::uncertainReasonCode),
        requiredLicenses = judgement.requiredLicenses?.map { it.value },
        sourceFields = judgement.requirementSourceFields.map { it.name }.sorted(),
        groupsSatisfied = satisfied.sorted(),
        groupsMissing = missingByGroup.keys.map(::requirementGroupLabel).sorted(),
        // 그룹이 둘 이상이면 **비운다** — 합집합은 그룹 간 OR 을 AND 로 오독시킨다.
        missingLicenses = singleGroup?.value?.map { it.value }?.sorted().orEmpty(),
        missingDetailState = missingDetailStateOf(missingByGroup.size).name,
    )
}

private fun missingDetailStateOf(missingGroupCount: Int): LicenseMissingDetailState =
    when (missingGroupCount) {
        0 -> LicenseMissingDetailState.NOT_APPLICABLE
        1 -> LicenseMissingDetailState.SINGLE_GROUP
        else -> LicenseMissingDetailState.MULTIPLE_GROUPS_NOT_FLATTENED
    }

private fun licenseVerdictLabel(verdict: LicenseVerdict): String =
    when (verdict) {
        is LicenseVerdict.Eligible -> "ELIGIBLE"
        is LicenseVerdict.Ineligible -> "INELIGIBLE"
        is LicenseVerdict.Uncertain -> "UNCERTAIN"
    }

private fun uncertainReasonCode(reason: UncertainReason): String =
    when (reason) {
        UncertainReason.RequirementDataAbsent -> "REQUIREMENT_DATA_ABSENT"
        UncertainReason.RequirementUnparsable -> "REQUIREMENT_UNPARSABLE"
        UncertainReason.OperatorLicensesNotDeclared -> "OPERATOR_LICENSES_NOT_DECLARED"
        UncertainReason.CollectionFailed -> "COLLECTION_FAILED"
        UncertainReason.PermittedIndustryCombinationRuleUndecided ->
            "PERMITTED_INDUSTRY_COMBINATION_RULE_UNDECIDED"
    }

/** 그룹 라벨 — 번호 있는 그룹과 번호 없는 묶음을 구분한다(U-8). */
private fun requirementGroupLabel(group: RequirementGroupId): String =
    when (group) {
        is RequirementGroupId.Numbered -> "G${group.groupNo.value}"
        RequirementGroupId.Ungrouped -> "UNGROUPED"
    }

/** 탈락 축의 평탄한 칸들 — 판정에 이르면 전부 `null`·빈 목록이다. */
internal class DropFields(
    val stage: String?,
    val reasonCode: String?,
    val failedWatchRuleIds: List<String>,
    val watchUndeterminableReason: String?,
    val scoreThreshold: String?,
    val scoreActual: String?,
)

internal fun dropFieldsOf(evaluation: CandidateEvaluation): DropFields =
    when (evaluation) {
        is CandidateEvaluation.Reached -> DropFields(null, null, emptyList(), null, null, null)
        is CandidateEvaluation.NotReached -> dropFieldsOfReason(evaluation)
    }

private fun dropFieldsOfReason(notReached: CandidateEvaluation.NotReached): DropFields {
    val reason = notReached.reason
    val scores = scorePairOf(reason)
    return DropFields(
        stage = stageCode(notReached.stage),
        reasonCode = reason.reasonCode(),
        failedWatchRuleIds =
            (reason as? EvaluationDropReason.WatchGateRejected)
                ?.rejected
                ?.failed
                ?.map { it.token() }
                ?.sorted()
                .orEmpty(),
        watchUndeterminableReason =
            (reason as? EvaluationDropReason.WatchGateUndeterminable)
                ?.undeterminable
                ?.reason
                ?.let(::watchUndeterminableCode),
        scoreThreshold = scores?.first,
        scoreActual = scores?.second,
    )
}

/**
 * 점수 미달 두 사유가 싣는 (임계, 실제) — 10진 문자열로 낸다. 부동소수로 내면 `0.1` 같은 값이
 * 응답에서 다른 수가 된다. 그 밖의 사유는 이 칸이 없다(`null`).
 */
private fun scorePairOf(reason: EvaluationDropReason): Pair<String, String>? =
    when (reason) {
        is EvaluationDropReason.BelowMinimumMatchScore ->
            reason.threshold.toPlainString() to reason.actual.toPlainString()

        is EvaluationDropReason.BelowMinimumProbabilityScore ->
            reason.threshold.toPlainString() to reason.actual.toPlainString()

        else -> null
    }

private fun EvaluationDropReason.reasonCode(): String =
    when (this) {
        is EvaluationDropReason.NoticeNotBiddable -> "NOTICE_NOT_BIDDABLE"
        is EvaluationDropReason.WatchGateNotConfigured -> "WATCH_GATE_NOT_CONFIGURED"
        is EvaluationDropReason.WatchGateRejected -> "WATCH_GATE_REJECTED"
        is EvaluationDropReason.WatchGateUndeterminable -> "WATCH_GATE_UNDETERMINABLE"
        EvaluationDropReason.WatchSubjectUnavailable -> "WATCH_SUBJECT_UNAVAILABLE"
        is EvaluationDropReason.LicenseIneligible -> "LICENSE_INELIGIBLE"
        EvaluationDropReason.ActionThresholdsNotConfigured -> "ACTION_THRESHOLDS_NOT_CONFIGURED"
        EvaluationDropReason.AnalysisBudgetExhausted -> "ANALYSIS_BUDGET_EXHAUSTED"
        EvaluationDropReason.SimilarityProjectionNotReady -> "SIMILARITY_PROJECTION_NOT_READY"
        is EvaluationDropReason.BelowMinimumMatchScore -> "BELOW_MINIMUM_MATCH_SCORE"
        is EvaluationDropReason.BelowMinimumProbabilityScore -> "BELOW_MINIMUM_PROBABILITY_SCORE"
    }

private fun stageCode(stage: EvaluationStage): String =
    when (stage) {
        EvaluationStage.NoticeLifecycle -> "NOTICE_LIFECYCLE"
        EvaluationStage.WatchGate -> "WATCH_GATE"
        EvaluationStage.LicenseGate -> "LICENSE_GATE"
        EvaluationStage.ThresholdConfiguration -> "THRESHOLD_CONFIGURATION"
        EvaluationStage.AnalysisBudget -> "ANALYSIS_BUDGET"
        EvaluationStage.MlAvailability -> "ML_AVAILABILITY"
        EvaluationStage.ScoreThreshold -> "SCORE_THRESHOLD"
    }

/**
 * 감시 판정 불가 사유 — 이것도 sealed 이고 각 갈래가 `ReasonCode` 를 안고 있다. **축 수준의
 * 갈래만 낸다**(`BASE_AMOUNT_ABSENT` ↔ `BUDGET_NOT_COMPARABLE`) — 운영자에게 유의미한 구분이
 * 그 둘이고, 안쪽 `ReasonCode`(과세 불일치·provenance 미선언 등)는 이 응답에 칸을 두지 않았다.
 * 알려진 제한이며, 필요해지면 칸 하나를 더하는 것은 가산이다.
 */
private fun watchUndeterminableCode(reason: WatchUndeterminableReason): String =
    when (reason) {
        is WatchUndeterminableReason.BaseAmountAbsent -> "BASE_AMOUNT_ABSENT"
        is WatchUndeterminableReason.BudgetNotComparable -> "BUDGET_NOT_COMPARABLE"
    }
