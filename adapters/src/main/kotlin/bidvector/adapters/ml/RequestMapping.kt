package bidvector.adapters.ml

import bidvector.procurement.BusinessCategory
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Rate
import bidvector.workflow.prediction.AgencyId
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.CompetitionSample
import bidvector.workflow.prediction.OptimizationObjective
import contract.bidvector.ml.v1.AgencyIdFact
import contract.bidvector.ml.v1.BaseAmountFact
import contract.bidvector.ml.v1.BaseAmountProvenanceLabel
import contract.bidvector.ml.v1.BaseAmountProvenanceLabelFact
import contract.bidvector.ml.v1.BidRateOrigin
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CategoryCodeFact
import contract.bidvector.ml.v1.ExactRelease
import contract.bidvector.ml.v1.FeatureInputs
import contract.bidvector.ml.v1.LatestPromoted
import contract.bidvector.ml.v1.MissingReason
import contract.bidvector.ml.v1.ModelReleaseSelector
import contract.bidvector.ml.v1.PredictionEnvelope
import contract.bidvector.ml.v1.RequestEnvelope
import bidvector.workflow.prediction.ModelReleaseSelector as DomainModelReleaseSelector
import contract.bidvector.ml.v1.CompetitionSample as ProtoCompetitionSample
import contract.bidvector.ml.v1.OptimizationObjective as ProtoOptimizationObjective
import contract.bidvector.ml.v1.Rate as ProtoRate

/**
 * M4/4D-1(scope.md ①) — 도메인 [BidPredictionRequest] → `CalculateOptimalBidRequest`(2B 계약
 * DTO). 자유 `String`은 만들지 않는다 — 값이 없으면 `MissingReason.UNKNOWN`으로 「모른다」를
 * 정직하게 싣는다(fact 가 결측 사유 없이 접히지 않는다, features.proto 관례). `Money`(공통)
 * 축 변환(`Currency`·`Basis`·`VatTreatment`·`Provenance`)은 `MoneyMapping.kt`에 있다
 * (detekt `TooManyFunctions` — 한 파일에 몰아두지 않는다).
 */
internal fun mapRequest(
    request: BidPredictionRequest,
    requestId: String,
    policy: MlCallPolicyData,
    deadlinePolicyVersion: String,
): CalculateOptimalBidRequest {
    val envelope =
        PredictionEnvelope
            .newBuilder()
            .setBase(
                RequestEnvelope
                    .newBuilder()
                    .setRequestId(requestId)
                    .setCorrelationId(request.correlationId.value)
                    .build(),
            ).setFeatureSchemaVersion(policy.featureSchemaVersion)
            .setModelReleaseSelector(request.releaseSelector.toProto())
            .setDeadlinePolicyVersion(deadlinePolicyVersion)
            .build()

    return CalculateOptimalBidRequest
        .newBuilder()
        .setEnvelope(envelope)
        .setFeatures(request.toFeatureInputs())
        .addAllCompetitionSamples(request.competitionSamples.map { it.toProto() })
        .setObjective(request.objective.toProto())
        .build()
}

private fun BidPredictionRequest.toFeatureInputs(): FeatureInputs =
    FeatureInputs
        .newBuilder()
        .setBaseAmount(BaseAmountFact.newBuilder().setValue(baseAmount.amount.toProtoMoney()).build())
        .setCategoryCode(businessCategory.toCategoryCodeFact())
        .setAgencyId(agencyId.toAgencyIdFact())
        .setBaseAmountProvenanceLabel(
            BaseAmountProvenanceLabelFact.newBuilder().setValue(baseAmountProvenanceLabel.toProto()).build(),
        ).build()

private fun BusinessCategory?.toCategoryCodeFact(): CategoryCodeFact =
    if (this == null) {
        CategoryCodeFact.newBuilder().setMissing(MissingReason.MISSING_REASON_UNKNOWN).build()
    } else {
        CategoryCodeFact.newBuilder().setValue(code.value).build()
    }

private fun AgencyId?.toAgencyIdFact(): AgencyIdFact =
    if (this == null) {
        AgencyIdFact.newBuilder().setMissing(MissingReason.MISSING_REASON_UNKNOWN).build()
    } else {
        AgencyIdFact.newBuilder().setValue(value).build()
    }

private fun CompetitionSample.toProto(): ProtoCompetitionSample {
    val builder =
        ProtoCompetitionSample
            .newBuilder()
            .setObservedBidRate(observedBidRate.toProtoRate())
            .setOrigin(BidRateOrigin.BID_RATE_ORIGIN_OBSERVED)
            .setBaseAmount(baseAmount.toProtoMoney())
            .setBaseAmountProvenanceLabel(baseAmountProvenanceLabel.toProto())
            .setOpenedOn(openedOn.toString())
    awardRate?.let { builder.setAwardRate(it.toProtoRate()) }
    return builder.build()
}

/**
 * `internal` — 요청 매핑(`mapRequest`)뿐 아니라 `GrpcBidPredictionGateway`의 release 대조
 * 단계(D-4D-4)도 같은 매핑을 쓴다(중복 금지).
 */
internal fun DomainModelReleaseSelector.toProto(): ModelReleaseSelector =
    when (this) {
        DomainModelReleaseSelector.LatestPromoted -> {
            ModelReleaseSelector.newBuilder().setLatestPromoted(LatestPromoted.getDefaultInstance()).build()
        }

        is DomainModelReleaseSelector.Exact -> {
            ModelReleaseSelector
                .newBuilder()
                .setExactRelease(
                    ExactRelease
                        .newBuilder()
                        .setReleaseId(releaseId)
                        .setArtifactChecksum(artifactChecksum)
                        .build(),
                ).build()
        }
    }

private fun OptimizationObjective.toProto(): ProtoOptimizationObjective =
    when (this) {
        OptimizationObjective.SCENARIO_TRIPLE -> ProtoOptimizationObjective.OPTIMIZATION_OBJECTIVE_SCENARIO_TRIPLE
    }

private fun Rate.toProtoRate(): ProtoRate = ProtoRate.newBuilder().setFraction(fraction.toPlainString()).build()

private fun BaseAmountProvenance.toProto(): BaseAmountProvenanceLabel =
    when (this) {
        BaseAmountProvenance.Clean -> BaseAmountProvenanceLabel.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
        BaseAmountProvenance.DerivedYega -> BaseAmountProvenanceLabel.BASE_AMOUNT_PROVENANCE_LABEL_DERIVED_YEGA
        BaseAmountProvenance.DerivedVat -> BaseAmountProvenanceLabel.BASE_AMOUNT_PROVENANCE_LABEL_DERIVED_VAT
        BaseAmountProvenance.SuspectRatio -> BaseAmountProvenanceLabel.BASE_AMOUNT_PROVENANCE_LABEL_SUSPECT_RATIO
        BaseAmountProvenance.Unknown -> BaseAmountProvenanceLabel.BASE_AMOUNT_PROVENANCE_LABEL_UNKNOWN
    }
