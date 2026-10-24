package bidvector.app.http

import bidvector.app.wiring.BidPriceRequestRun
import bidvector.procurement.Notice
import bidvector.sharedkernel.export
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.MlAnalysisOutcome
import com.fasterxml.jackson.annotation.JsonUnwrapped

/**
 * 투찰가 요청 응답(M7/7C ②) — **JSON 은 평탄하다.** 축별 값을 접두사로 열거하고 중첩 object 를
 * 쓰지 않는다(계약 전체의 평탄 술어를 넓히지 않는다).
 *
 * **Kotlin 타입은 축별로 쪼개고 직렬화에서 편다(`D-7C2-3` R7).** 평탄하게 한 타입에 다 담으면
 * 필드가 51 이고 그것은 `limit.type.members`(30, ADR 0007)를 넘는다. 두 게이트가 **서로 다른
 * 대상**을 본다 — 멤버 상한은 Kotlin 타입에, 평탄 술어는 OpenAPI 스키마에 걸린다. 그래서 타입만
 * 쪼개면 둘 다 선다. [JsonUnwrapped] 가 각 묶음의 속성을 이 object 의 속성으로 펴므로 **wire
 * 형태는 평탄 하나**이고 계약 문서도 스칼라 속성 하나짜리 스키마 그대로다.
 *
 * 펴기가 꺼지면(애너테이션을 지우면) 응답이 중첩되고 **최상위 키 집합이 달라진다** —
 * `OpenApiBidPriceContractTest` 의 키 집합 등식이 **출하 직렬화**로 그것을 잡는다. 쪼갠 묶음끼리
 * 같은 키를 내면 조용히 덮이므로 그 축은 `BidPriceResponseShapeTest` 의 「키 충돌 0」이 잠근다.
 *
 * **값이 서지 않는 자리에는 값 필드가 없다** — 3안·신뢰도·하한 미달 빈도는 **상태와 사유**만 있다.
 * 숫자 칸을 미리 만들어 `null` 로 채우면 「없음」이 「0」으로 읽히는 문이 열린다.
 *
 * 공고명·기관명·요건 원문·사업자번호는 **어느 묶음에도 자리가 없다** — 「근거 원문」은 (출처 필드 +
 * 요건 면허명 + 정책 version)으로 낸다.
 */
data class BidPriceRequestResponse(
    val noticeNumber: String,
    val noticeRound: String,
    val correlationId: String,
    val strategyRevision: Int,
    val currentActiveBids: Int,
    val maxActiveBids: Int,
    @get:JsonUnwrapped val evaluation: EvaluationDetailFields,
    @get:JsonUnwrapped val notification: NotificationFields,
    @get:JsonUnwrapped val qualification: QualificationFields,
    @get:JsonUnwrapped val baseAmount: BaseAmountFields,
    @get:JsonUnwrapped val floorRate: FloorRateFields,
    @get:JsonUnwrapped val priceAxes: PriceAxesFields,
    val disclosures: List<String>,
)

/** 평가 상세(`OPEN-6A3-EVALUATION-DETAIL` 해소) — 단건이라 단계·사유가 스칼라다. */
data class EvaluationDetailFields(
    val outcome: String,
    val verdict: String?,
    val verdictReasonCodes: List<String>,
    val dropStage: String?,
    val dropReasonCode: String?,
    val noticeStatus: String,
    val failedWatchRuleIds: List<String>,
    val watchUndeterminableReason: String?,
    val scoreThreshold: String?,
    val scoreActual: String?,
)

/** 알림 요청·알림함 기록 — **요청까지**이고 배달·기록은 없다(R-4 공백을 상태로 드러낸다). */
data class NotificationFields(
    val wouldNotify: Boolean,
    val inboxRecordState: String,
    val inboxRecordNotRecordedReason: String?,
)

/** 자격 축(QUAL-01 · 03 · 05)과 **아직 판정하지 않는 축** 목록. */
data class QualificationFields(
    val licenseVerdict: String,
    val licenseUncertainReason: String?,
    val licenseRequiredLicenses: List<String>?,
    val licenseRequirementSourceFields: List<String>,
    val licenseGroupsSatisfied: List<String>,
    val licenseGroupsMissing: List<String>,
    val licenseMissingLicenses: List<String>,
    val licenseMissingDetailState: String,
    val licenseUnparsableRowCount: Int,
    val licenseValidity: String,
    val licensePolicySource: String,
    val licensePolicyEffectiveFrom: String?,
    val unjudgedAxes: List<String>,
)

/** 기초금액(DEC-01) — 값만 싣지 않고 basis·과세·provenance·해석 갈래와 함께 싣는다. */
data class BaseAmountFields(
    val baseAmountWon: Long?,
    val baseAmountCurrency: String?,
    val baseAmountBasis: String?,
    val baseAmountVatTreatment: String?,
    val baseAmountProvenance: String?,
    val baseAmountResolution: String?,
)

/** 게시 하한율(DEC-03 · DEC-10 표시 축) — 게시값만 근거다. */
data class FloorRateFields(
    val publishedFloorRateFraction: String?,
    val publishedFloorRateOrigin: String?,
    val publishedFloorRateNoticeRound: String?,
    val floorRateGroundsState: String,
)

/**
 * 가격 세 축(DEC-04 · ML-03 · DEC-05) — **전부 상태와 사유다.** 값 칸이 없는 것이 이 타입의
 * 요점이다: 출하 ML 이 항상 미가용이고 하한 미달 정책의 출하 정본 표가 없다.
 */
data class PriceAxesFields(
    val floorShortfallState: String,
    val floorShortfallNotComputedReason: String,
    val mlAnalysisState: String,
    val mlUnavailableReason: String?,
    val confidenceState: String,
    val confidenceNotAvailableReason: String,
    val bidPriceMenuState: String,
    val bidPriceMenuNotComputedReason: String,
)

/**
 * 응답 조립 — 값은 전부 **이 요청의 한 번 실행**이 만든 것에서 온다(판정·근거를 다시 계산하지
 * 않는다). 순수 함수라 Spring 없이 test 가 직접 부른다.
 */
fun bidPriceRequestResponseOf(
    run: BidPriceRequestRun,
    evaluation: CandidateEvaluation,
    currentActiveBids: Int,
): BidPriceRequestResponse =
    BidPriceRequestResponse(
        noticeNumber = run.notice.id.number.value,
        noticeRound = run.notice.id.round.value,
        correlationId = evaluation.correlationId.value,
        strategyRevision = run.strategy.revision.value,
        currentActiveBids = currentActiveBids,
        maxActiveBids =
            requireNotNull(run.strategy.maxActiveBids) {
                "factory 가 이미 상한 존재를 보장한다(MaxActiveBidsNotConfiguredException)"
            }.value,
        evaluation = evaluationDetailFieldsOf(evaluation, run.notice),
        notification = notificationFieldsOf(run),
        qualification = qualificationFieldsOf(run.licenseJudgement),
        baseAmount = baseAmountFieldsOf(run.notice),
        floorRate = floorRateFieldsOf(run.notice),
        priceAxes = priceAxesFieldsOf(run.mlOutcome()),
        disclosures = BidPriceDisclosure.entries.map { it.name },
    )

private fun notificationFieldsOf(run: BidPriceRequestRun): NotificationFields =
    NotificationFields(
        wouldNotify = run.wouldNotify(),
        inboxRecordState = InboxRecordState.NOT_RECORDED_YET.name,
        inboxRecordNotRecordedReason = InboxRecordNotRecordedReason.INBOX_WRITE_PORT_NOT_MERGED.name,
    )

private fun priceAxesFieldsOf(mlOutcome: MlAnalysisOutcome?): PriceAxesFields {
    val mlState = mlAnalysisStateOf(mlOutcome)
    return PriceAxesFields(
        floorShortfallState = NotComputedState.NOT_COMPUTED.name,
        floorShortfallNotComputedReason = FloorShortfallNotComputedReason.SHORTFALL_POLICY_NOT_PUBLISHED.name,
        mlAnalysisState = mlState.name,
        mlUnavailableReason = mlUnavailableReasonLabelOf(mlOutcome),
        confidenceState = ConfidenceState.NOT_AVAILABLE.name,
        confidenceNotAvailableReason = confidenceNotAvailableReasonOf(mlState).name,
        bidPriceMenuState = NotComputedState.NOT_COMPUTED.name,
        bidPriceMenuNotComputedReason = BidPriceMenuNotComputedReason.MENU_PATH_NOT_WIRED.name,
    )
}

private fun baseAmountFieldsOf(notice: Notice): BaseAmountFields {
    val exported = notice.baseAmount?.amount?.export()
    return BaseAmountFields(
        baseAmountWon = exported?.won,
        baseAmountCurrency = exported?.currency?.name,
        baseAmountBasis = exported?.basis?.name,
        baseAmountVatTreatment = exported?.vatTreatment?.name,
        baseAmountProvenance = exported?.provenance?.let(::provenanceLabel),
        baseAmountResolution = notice.baseAmount?.let(::baseAmountResolutionLabel),
    )
}

private fun floorRateFieldsOf(notice: Notice): FloorRateFields {
    val floor = notice.floorRate
    return FloorRateFields(
        publishedFloorRateFraction = usableFloorRateOf(floor)?.rate?.fraction?.toPlainString(),
        publishedFloorRateOrigin = floor?.let(::floorRateOriginLabel),
        publishedFloorRateNoticeRound = publishedOriginRoundOf(floor),
        floorRateGroundsState = floorRateGroundsStateOf(floor).name,
    )
}
