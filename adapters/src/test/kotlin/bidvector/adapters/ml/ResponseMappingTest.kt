package bidvector.adapters.ml

import bidvector.adapters.contract.contractTestdataRoot
import bidvector.adapters.contract.readTestdataBytes
import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.PredictionDiagnostics
import bidvector.workflow.prediction.SegmentSupport
import bidvector.workflow.prediction.UnmeasurableReason
import bidvector.workflow.prediction.Weight
import contract.bidvector.ml.v1.ApplicationFailure
import contract.bidvector.ml.v1.BidRateOrigin
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.CandidateLabel
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.ReleaseKind
import contract.bidvector.ml.v1.Unmeasurable
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import contract.bidvector.ml.v1.UnmeasurableReason as ProtoUnmeasurableReason

/**
 * scope.md ④⑤⑦, 우회 (3)(6) — `Unmeasurable`은 값·기본값으로 접지 않는다(사유 셋 구분),
 * 계약 위반(정의 밖 enum·후보 개수/순서/origin 위반·정규형 위반·`sample_size==0`)은 전부
 * `Unavailable(ContractViolation)`이다(fail-closed, else 없음).
 */
class ResponseMappingTest {
    @Test
    fun `Unmeasurable 세 사유는 서로 다른 도메인 값으로 매핑된다`() {
        mapUnmeasurable(unmeasurableOf(ProtoUnmeasurableReason.UNMEASURABLE_REASON_INSUFFICIENT_SAMPLES)) shouldBe
            BidPredictionOutcome.Unmeasurable(UnmeasurableReason.InsufficientSamples)
        mapUnmeasurable(unmeasurableOf(ProtoUnmeasurableReason.UNMEASURABLE_REASON_UNTRAINED_SEGMENT)) shouldBe
            BidPredictionOutcome.Unmeasurable(UnmeasurableReason.UntrainedSegment)
        mapUnmeasurable(unmeasurableOf(ProtoUnmeasurableReason.UNMEASURABLE_REASON_FEATURE_ABSENT)) shouldBe
            BidPredictionOutcome.Unmeasurable(UnmeasurableReason.FeatureAbsent)
    }

    @Test
    fun `Unmeasurable UNSPECIFIED 는 값으로 접히지 않고 ContractViolation 이다`() {
        val outcome = mapUnmeasurable(unmeasurableOf(ProtoUnmeasurableReason.UNMEASURABLE_REASON_UNSPECIFIED))
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `ApplicationFailure 는 코드별로 서로 다른 MlUnavailableReason 을 낸다`() {
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_UNSUPPORTED_SCHEMA)).reason shouldBe
            MlUnavailableReason.UnsupportedSchema
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_UNSUPPORTED_RELEASE)).reason shouldBe
            MlUnavailableReason.UnsupportedRelease
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_INVALID_REQUEST)).reason shouldBe
            MlUnavailableReason.InvalidRequest
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_MODEL_NOT_READY)).reason shouldBe
            MlUnavailableReason.ModelNotReady
    }

    @Test
    fun `이 RPC 밖의 FailureCode 는 계약 위반으로 접힌다`() {
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_JOB_NOT_FOUND)).reason shouldBe
            MlUnavailableReason.ContractViolation
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_IDEMPOTENCY_CONFLICT)).reason shouldBe
            MlUnavailableReason.ContractViolation
        mapApplicationFailure(failureOf(FailureCode.FAILURE_CODE_UNSPECIFIED)).reason shouldBe
            MlUnavailableReason.ContractViolation
    }

    @Test
    fun `정상 Success 는 Predicted 로 매핑된다`() {
        val outcome = mapSuccess(testSuccessResponse(), expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
    }

    @Test
    fun `sample_size 0 은 Predicted 가 아니라 ContractViolation 이다(우회 3)`() {
        val outcome =
            mapSuccess(testSuccessResponse(sampleSize = 0), expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `후보가 2개면 ContractViolation 이다(우회 6)`() {
        val mutated = testSuccessResponse().toBuilder().also { it.removeCandidates(2) }.build()
        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    @Test
    fun `후보 라벨 순서가 어긋나면 ContractViolation 이다(우회 6)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.getCandidatesBuilder(0).label = CandidateLabel.CANDIDATE_LABEL_AGGRESSIVE }
                .build()
        mapSuccess(
            mutated,
            expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION,
        ).shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    @Test
    fun `후보 origin 이 RECOMMENDED 가 아니면 ContractViolation 이다`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.getCandidatesBuilder(0).origin = BidRateOrigin.BID_RATE_ORIGIN_OBSERVED }
                .build()
        mapSuccess(
            mutated,
            expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION,
        ).shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    @Test
    fun `fraction 이 지수 표기면 ContractViolation 이다(정규형 위반)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.getCandidatesBuilder(0).bidRateBuilder.fraction = "9.2E-1" }
                .build()
        mapSuccess(
            mutated,
            expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION,
        ).shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    // ---- verifier r1 F-2(high) — release provenance 공백 유출 회귀 방지(probe P4) ----

    @Test
    fun `release 성분 셋(schema code_version dataset_id)이 공백이면 ContractViolation 이다(F-2 P4a)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also {
                    it.releaseBuilder.featureSchemaVersion = ""
                    it.releaseBuilder.codeVersion = ""
                    it.releaseBuilder.datasetId = ""
                }.build()

        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `release 전 성분이 공백(default instance)이면 ContractViolation 이다(F-2 P4b)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also {
                    it.release =
                        contract.bidvector.ml.v1.ModelRelease
                            .getDefaultInstance()
                }.build()

        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `응답 feature_schema_version 이 요청과 다르면 UnsupportedSchema 다(D-7)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.releaseBuilder.featureSchemaVersion = "bidvector.ml.v2-unexpected" }
                .build()

        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.UnsupportedSchema
    }

    // ---- M4/4D-3(scope.md D-4D3-2, 위협 모델 우회 (1)~(5)) — 진단 fail-closed ----

    @Test
    fun `shrinkage_weight fraction 이 범위 밖 형태면 ContractViolation 이다(우회 1)`() {
        listOf("1.5000", "-0.1000", "", "1e-1").forEach { badFraction ->
            val mutated =
                testSuccessResponse()
                    .toBuilder()
                    .also { it.diagnosticsBuilder.shrinkageWeightBuilder.fraction = badFraction }
                    .build()

            val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

            withClue(badFraction) {
                outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
                outcome.reason shouldBe MlUnavailableReason.ContractViolation
            }
        }
    }

    @Test
    fun `segment_support 가 미설정이면 ContractViolation 이다(우회 2)`() {
        val unset =
            testSuccessResponse()
                .toBuilder()
                .also { it.diagnosticsBuilder.clearSegmentSupport() }
                .build()

        val outcome = mapSuccess(unset, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `uint32 성분이 오버플로(Kotlin Int 음수)면 ContractViolation 이다(우회 3)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.diagnosticsBuilder.excludedObservations = -1 }
                .build()

        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `DERIVED release 인데 training_row_count 가 0보다 크면 ContractViolation 이다(우회 4)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also {
                    it.releaseBuilder.releaseKind = ReleaseKind.RELEASE_KIND_DERIVED
                    it.releaseBuilder.releaseId = "distribution/reserve-draw-distribution-v1"
                    it.diagnosticsBuilder.trainingRowCount = 10
                }.build()

        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `진단 없는 응답(proto 기본 인스턴스)은 ContractViolation 이다(우회 5, 알려진 제한 — 2F 이전 서버)`() {
        val mutated = testSuccessResponse().toBuilder().clearDiagnostics().build()

        val outcome = mapSuccess(mutated, expectedFeatureSchemaVersion = TEST_SCHEMA_VERSION)

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    // ---- testdata ARTIFACT/DERIVED 두 응답의 진단 값이 wire 와 같음(scope.md 종결 조건) ----

    @Test
    fun `ARTIFACT testdata 는 wire 진단 값 그대로 Predicted 로 매핑된다`() {
        val bytes =
            readTestdataBytes(
                contractTestdataRoot("prediction"),
                "calculate_optimal_bid_response_success.binpb",
            )
        val success = CalculateOptimalBidResponse.parseFrom(bytes).success

        val outcome = mapSuccess(success, expectedFeatureSchemaVersion = "award-rate-v1")

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        outcome.diagnostics shouldBe
            PredictionDiagnostics(
                trainingRowCount = 4120,
                segmentSupport = SegmentSupport.Direct,
                shrinkageWeight = Weight(BigDecimal("0.1500")),
                excludedObservations = 12,
                agencySampleCount = 38,
                agencySampleBelowThreshold = false,
            )
    }

    @Test
    fun `DERIVED testdata 는 wire 진단 값 그대로 Predicted 로 매핑된다`() {
        val bytes =
            readTestdataBytes(
                contractTestdataRoot("prediction"),
                "calculate_optimal_bid_response_success_posterior_predictive.binpb",
            )
        val success = CalculateOptimalBidResponse.parseFrom(bytes).success

        val outcome = mapSuccess(success, expectedFeatureSchemaVersion = "award-rate-v1")

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        outcome.diagnostics shouldBe
            PredictionDiagnostics(
                trainingRowCount = 0,
                segmentSupport = SegmentSupport.Global,
                shrinkageWeight = Weight(BigDecimal("0.0000")),
                excludedObservations = 0,
                agencySampleCount = 0,
                agencySampleBelowThreshold = false,
            )
    }
}

private const val TEST_SCHEMA_VERSION = "bidvector.ml.v1-test"

private fun unmeasurableOf(reason: ProtoUnmeasurableReason): Unmeasurable =
    Unmeasurable.newBuilder().setReason(reason).build()

private fun failureOf(code: FailureCode): ApplicationFailure =
    ApplicationFailure
        .newBuilder()
        .setCode(code)
        .setRetryable(false)
        .build()
