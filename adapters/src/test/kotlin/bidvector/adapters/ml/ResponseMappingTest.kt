package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.UnmeasurableReason
import contract.bidvector.ml.v1.ApplicationFailure
import contract.bidvector.ml.v1.BidRateOrigin
import contract.bidvector.ml.v1.CandidateLabel
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.Unmeasurable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
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
        val outcome = mapSuccess(testSuccessResponse())
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
    }

    @Test
    fun `sample_size 0 은 Predicted 가 아니라 ContractViolation 이다(우회 3)`() {
        val outcome = mapSuccess(testSuccessResponse(sampleSize = 0))
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        outcome.reason shouldBe MlUnavailableReason.ContractViolation
    }

    @Test
    fun `후보가 2개면 ContractViolation 이다(우회 6)`() {
        val mutated = testSuccessResponse().toBuilder().also { it.removeCandidates(2) }.build()
        val outcome = mapSuccess(mutated)
        outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    @Test
    fun `후보 라벨 순서가 어긋나면 ContractViolation 이다(우회 6)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.getCandidatesBuilder(0).label = CandidateLabel.CANDIDATE_LABEL_AGGRESSIVE }
                .build()
        mapSuccess(mutated).shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    @Test
    fun `후보 origin 이 RECOMMENDED 가 아니면 ContractViolation 이다`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.getCandidatesBuilder(0).origin = BidRateOrigin.BID_RATE_ORIGIN_OBSERVED }
                .build()
        mapSuccess(mutated).shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }

    @Test
    fun `fraction 이 지수 표기면 ContractViolation 이다(정규형 위반)`() {
        val mutated =
            testSuccessResponse()
                .toBuilder()
                .also { it.getCandidatesBuilder(0).bidRateBuilder.fraction = "9.2E-1" }
                .build()
        mapSuccess(mutated).shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
    }
}

private fun unmeasurableOf(reason: ProtoUnmeasurableReason): Unmeasurable =
    Unmeasurable.newBuilder().setReason(reason).build()

private fun failureOf(code: FailureCode): ApplicationFailure =
    ApplicationFailure
        .newBuilder()
        .setCode(code)
        .setRetryable(false)
        .build()
