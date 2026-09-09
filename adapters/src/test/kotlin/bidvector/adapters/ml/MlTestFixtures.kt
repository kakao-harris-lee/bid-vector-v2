package bidvector.adapters.ml

import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.ModelReleaseSelector
import bidvector.workflow.prediction.OptimizationObjective
import contract.bidvector.ml.v1.BidRateOrigin
import contract.bidvector.ml.v1.Candidate
import contract.bidvector.ml.v1.CandidateLabel
import contract.bidvector.ml.v1.IntervalSource
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.PriceFitness
import contract.bidvector.ml.v1.Success
import contract.bidvector.ml.v1.Uncertainty
import contract.bidvector.ml.v1.Weight
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import contract.bidvector.ml.v1.Rate as ProtoRate

/**
 * adapters/ml test 전용 좁은 정책 인스턴스(3B `testKonepsHttpPolicy` 관례) — 운영
 * [ML_CALL_POLICY]를 쓰지 않는다.
 */
internal fun testMlCallPolicy(
    deadlineCeiling: Duration = Duration.ofSeconds(2),
    maxAttempts: Int = 3,
    backoff: List<Duration> = listOf(Duration.ofMillis(1), Duration.ofMillis(1)),
    breakerFailureRateThresholdPercent: Int = 50,
    breakerSlidingWindowSize: Int = 4,
    breakerWaitDurationInOpenState: Duration = Duration.ofMillis(200),
): MlCallPolicyData =
    MlCallPolicyData(
        deadlineCeiling = deadlineCeiling,
        maxAttempts = maxAttempts,
        backoff = backoff,
        breakerFailureRateThresholdPercent = breakerFailureRateThresholdPercent,
        breakerSlidingWindowSize = breakerSlidingWindowSize,
        breakerWaitDurationInOpenState = breakerWaitDurationInOpenState,
        featureSchemaVersion = "bidvector.ml.v1-test",
    )

internal fun testMlCallEffectivePolicy(
    data: MlCallPolicyData = testMlCallPolicy(),
): EffectiveDatedPolicy<MlCallPolicyData> =
    EffectiveDatedPolicy(source = "test", entries = listOf(EffectiveFrom.Initial to data))

internal fun testBidPredictionRequest(
    releaseSelector: ModelReleaseSelector = ModelReleaseSelector.LatestPromoted,
): BidPredictionRequest =
    BidPredictionRequest(
        baseAmount =
            ResolvedBaseAmount.Direct.of(
                won = 1_000_000L,
                currency = Currency.KRW,
                vatTreatment = VatTreatment.INCLUSIVE,
                provenance = Provenance.Published(NoticeRound("000")),
            ),
        businessCategory = null,
        agencyId = null,
        baseAmountProvenanceLabel = BaseAmountProvenance.Clean,
        competitionSamples =
            listOf(
                bidvector.workflow.prediction.CompetitionSample(
                    observedBidRate = Rate.ofFraction(BigDecimal("0.9200")),
                    baseAmount = BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Undeclared),
                    baseAmountProvenanceLabel = BaseAmountProvenance.Clean,
                    openedOn = LocalDate.of(2026, 1, 15),
                ),
            ),
        objective = OptimizationObjective.SCENARIO_TRIPLE,
        releaseSelector = releaseSelector,
        correlationId = CorrelationId("test-correlation-id"),
    )

internal fun testSuccessResponse(
    releaseId: String = "release-2026-09-01",
    artifactChecksum: String = "sha256:test",
    sampleSize: Int = 42,
): Success =
    Success
        .newBuilder()
        .addCandidates(testCandidate(CandidateLabel.CANDIDATE_LABEL_CONSERVATIVE, "0.9000"))
        .addCandidates(testCandidate(CandidateLabel.CANDIDATE_LABEL_BASE, "0.9200"))
        .addCandidates(testCandidate(CandidateLabel.CANDIDATE_LABEL_AGGRESSIVE, "0.9500"))
        .setFitness(PriceFitness.newBuilder().setScore("0.8100").build())
        .setUncertainty(
            Uncertainty
                .newBuilder()
                .setSampleSize(sampleSize)
                .setDispersion("0.0500")
                .setEstimateMargin("0.0200")
                .setIntervalSource(IntervalSource.INTERVAL_SOURCE_CROSS_VALIDATION_RESIDUAL)
                .build(),
        ).setRelease(testModelRelease(releaseId, artifactChecksum))
        .build()

internal fun testModelRelease(
    releaseId: String = "release-2026-09-01",
    artifactChecksum: String = "sha256:test",
): ModelRelease =
    ModelRelease
        .newBuilder()
        .setReleaseId(releaseId)
        .setArtifactChecksum(artifactChecksum)
        .setFeatureSchemaVersion("bidvector.ml.v1-test")
        .setCodeVersion("v-test")
        .setDatasetId("dataset-test")
        .build()

private fun testCandidate(
    label: CandidateLabel,
    fraction: String,
): Candidate =
    Candidate
        .newBuilder()
        .setLabel(label)
        .setBidRate(ProtoRate.newBuilder().setFraction(fraction).build())
        .setOrigin(BidRateOrigin.BID_RATE_ORIGIN_RECOMMENDED)
        .setWeight(Weight.newBuilder().setFraction("0.3300").build())
        .setWeightPolicyVersion("weight-policy-test")
        .build()
