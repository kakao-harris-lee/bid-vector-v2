package bidvector.adapters.contract

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Message
import contract.bidvector.ml.v1.AgencyIdFact
import contract.bidvector.ml.v1.BaseAmountFact
import contract.bidvector.ml.v1.BaseAmountProvenanceLabelFact
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.Candidate
import contract.bidvector.ml.v1.CandidateLabel
import contract.bidvector.ml.v1.CategoryCodeFact
import contract.bidvector.ml.v1.FeatureInputs
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import contract.bidvector.ml.v1.LatestPromoted
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.ModelReleaseSelector
import contract.bidvector.ml.v1.OptimizationObjective
import contract.bidvector.ml.v1.Success
import contract.bidvector.ml.v1.UnmeasurableReason
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/**
 * M2/2B — `BidPredictionService` 계약 consumer test. Kotlin in-process fake servicer
 * (`BidPredictionServiceCoroutineImplBase`) 위에서 `contracts/testdata/prediction/` 의
 * canonical 바이트(`buf convert`로 생성 — 재현 절차는 `reports/evidence/m2/2b/commands.md`)를
 * 답으로 낸다. 실제 socket 배선·client 매핑은 2D·M4 몫이다(scope.md 「구현 순서」 4).
 *
 * **거부 규칙은 이 test 안의 순수 함수다** — 2A `ContractRoundTripTest`와 같은 관례
 * (Kotlin 쪽 실제 validation 구현은 M4 몫). 여기서는 계약이 요구하는 불변식을 test가
 * 문서화하고 고정한다.
 */
class PredictionContractTest {
    private val testdataRoot: Path =
        Path
            .of(
                System.getProperty("bidvector.contracts.testdata")
                    ?: error("시스템 속성 'bidvector.contracts.testdata' 가 없다 — 빌드가 넘긴다"),
            ).resolve("prediction")

    private fun bytes(name: String): ByteArray = Files.readAllBytes(testdataRoot.resolve(name))

    private fun canonicalBytes(message: Message): ByteArray {
        val buffer = ByteArrayOutputStream()
        val coded = CodedOutputStream.newInstance(buffer)
        coded.useDeterministicSerialization()
        message.writeTo(coded)
        coded.flush()
        return buffer.toByteArray()
    }

    private val requestBytes = bytes("calculate_optimal_bid_request.binpb")
    private val successBytes = bytes("calculate_optimal_bid_response_success.binpb")
    private val unmeasurableUntrainedBytes = bytes("calculate_optimal_bid_response_unmeasurable_untrained_segment.binpb")
    private val unmeasurableInsufficientBytes = bytes("calculate_optimal_bid_response_unmeasurable_insufficient_samples.binpb")
    private val failureBytes = bytes("calculate_optimal_bid_response_failure_unsupported_schema.binpb")
    private val metadataBytes = bytes("get_model_metadata_response.binpb")

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    /** 고정 응답을 내는 fake servicer 위에 in-process stub 을 배선한다(scope.md ⑨). */
    private fun stubAnswering(
        calculate: CalculateOptimalBidResponse? = null,
        metadata: GetModelMetadataResponse? = null,
    ): BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub {
        val serverName = "bidvector-2b-fake-${System.nanoTime()}"
        val servicer =
            object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse =
                    calculate ?: error("이 test 는 CalculateOptimalBid 응답을 배선하지 않았다")

                override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                    metadata ?: error("이 test 는 GetModelMetadata 응답을 배선하지 않았다")
            }
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(servicer)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel!!)
    }

    // ---- ⑨ fake servicer 위 실제 RPC — 3후보·순서·라벨 고정 ----

    // **주의** — 이 두 test는 블록 본문(`{ }`)을 쓴다. `= runBlocking { ... }`(식 본문)로
    // 쓰면 kotest `shouldBe`가 수신자를 반환(체이닝용)하므로 함수의 추론 반환 타입이
    // `Unit`이 아니게 되고, 그 결과 JUnit Jupiter가 이 메서드를 test로 discover하지
    // 않는다(가짜 초록 — 실측: 식 본문일 때 `tests="25"`로 이 둘이 조용히 빠졌다). 블록
    // 본문은 반환 타입이 항상 `Unit`이라 이 함정이 없다.
    @Test
    fun `fake servicer 가 CONSERVATIVE BASE AGGRESSIVE 순서의 3후보를 낸다`() {
        runBlocking {
            val stub = stubAnswering(calculate = CalculateOptimalBidResponse.parseFrom(successBytes))
            val response = stub.calculateOptimalBid(CalculateOptimalBidRequest.parseFrom(requestBytes))

            response.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.SUCCESS
            response.success.candidatesList.map { it.label } shouldBe
                listOf(
                    CandidateLabel.CANDIDATE_LABEL_CONSERVATIVE,
                    CandidateLabel.CANDIDATE_LABEL_BASE,
                    CandidateLabel.CANDIDATE_LABEL_AGGRESSIVE,
                )
        }
    }

    @Test
    fun `fake servicer 의 GetModelMetadata 가 promoted release 를 낸다`() {
        runBlocking {
            val stub = stubAnswering(metadata = GetModelMetadataResponse.parseFrom(metadataBytes))
            // 요청은 봉투만 필요하다(scope.md ⑧) — testdata 에 별도 request 표본이 없어
            // `CalculateOptimalBidRequest` 표본의 base envelope 를 재사용해 최소 구성한다.
            val requestEnvelope = CalculateOptimalBidRequest.parseFrom(requestBytes).envelope.base
            val request = GetModelMetadataRequest.newBuilder().setEnvelope(requestEnvelope).build()

            val response = stub.getModelMetadata(request)

            response.resultCase shouldBe GetModelMetadataResponse.ResultCase.METADATA
            response.metadata.promoted.releaseId shouldBe "release-2026-09-01"
        }
    }

    // ---- 후보 3 고정 — 개수 위반은 거부 ----

    @Test
    fun `testdata 의 Success 는 정확히 3후보이고 순서가 맞다`() {
        val success = CalculateOptimalBidResponse.parseFrom(successBytes).success
        hasExactlyThreeOrderedCandidates(success) shouldBe true
    }

    @Test
    fun `후보가 2개면 계약 불변식 위반이다`() {
        val response = CalculateOptimalBidResponse.parseFrom(successBytes)
        val mutated =
            response
                .toBuilder()
                .also { it.successBuilder.removeCandidates(2) }
                .build()
        hasExactlyThreeOrderedCandidates(mutated.success) shouldBe false
    }

    // ---- Unmeasurable 두 사유 구분 ----

    @Test
    fun `Unmeasurable 두 사유는 다른 값이다`() {
        val untrained = CalculateOptimalBidResponse.parseFrom(unmeasurableUntrainedBytes)
        val insufficient = CalculateOptimalBidResponse.parseFrom(unmeasurableInsufficientBytes)

        untrained.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.UNMEASURABLE
        insufficient.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.UNMEASURABLE
        untrained.unmeasurable.reason shouldBe UnmeasurableReason.UNMEASURABLE_REASON_UNTRAINED_SEGMENT
        insufficient.unmeasurable.reason shouldBe UnmeasurableReason.UNMEASURABLE_REASON_INSUFFICIENT_SAMPLES
        (untrained.unmeasurable.reason == insufficient.unmeasurable.reason) shouldBe false
    }

    // ---- release 일치 규칙 — client 집행 규칙을 순수 함수로(2A ⑥ 인계) ----

    @Test
    fun `exact_release 요청이면 응답 release 가 정확히 같아야 한다`() {
        val request = CalculateOptimalBidRequest.parseFrom(requestBytes)
        val selector = request.envelope.modelReleaseSelector
        selector.selectorCase shouldBe ModelReleaseSelector.SelectorCase.EXACT_RELEASE

        val release = CalculateOptimalBidResponse.parseFrom(successBytes).success.release
        val mismatched = release.toBuilder().setReleaseId("other-release-id").build()

        releaseSatisfiesSelector(selector, release, promoted = null) shouldBe true
        releaseSatisfiesSelector(selector, mismatched, promoted = null) shouldBe false
    }

    @Test
    fun `latest_promoted 요청이면 응답 release 가 GetModelMetadata promoted 와 같아야 한다`() {
        val selector =
            ModelReleaseSelector
                .newBuilder()
                .setLatestPromoted(LatestPromoted.getDefaultInstance())
                .build()
        val promoted = GetModelMetadataResponse.parseFrom(metadataBytes).metadata.promoted
        val mismatched = promoted.toBuilder().setReleaseId("other-release-id").build()

        releaseSatisfiesSelector(selector, promoted, promoted) shouldBe true
        releaseSatisfiesSelector(selector, mismatched, promoted) shouldBe false
    }

    // ---- UNSPECIFIED·미지 enum 정수 거부 ----

    @Test
    fun `OptimizationObjective UNSPECIFIED 는 거부된다`() {
        isAcceptableObjective(OptimizationObjective.OPTIMIZATION_OBJECTIVE_UNSPECIFIED) shouldBe false
    }

    @Test
    fun `OptimizationObjective 정의 밖 정수(99)는 거부된다`() {
        val request = CalculateOptimalBidRequest.parseFrom(requestBytes).toBuilder().setObjectiveValue(99).build()
        request.objective shouldBe OptimizationObjective.UNRECOGNIZED
        isAcceptableObjective(request.objective) shouldBe false
    }

    @Test
    fun `유효한 objective 는 허용된다`() {
        val request = CalculateOptimalBidRequest.parseFrom(requestBytes)
        isAcceptableObjective(request.objective) shouldBe true
    }

    @Test
    fun `CandidateLabel UNSPECIFIED 는 거부된다`() {
        isAcceptableCandidateLabel(CandidateLabel.CANDIDATE_LABEL_UNSPECIFIED) shouldBe false
    }

    @Test
    fun `CandidateLabel 정의 밖 정수는 거부된다`() {
        val candidate = Candidate.newBuilder().setLabelValue(77).build()
        candidate.label shouldBe CandidateLabel.UNRECOGNIZED
        isAcceptableCandidateLabel(candidate.label) shouldBe false
    }

    // ---- Rate.fraction > 1 거부(D-2B-8, H-11 percent 관용 재유입 차단) ----

    @Test
    fun `bid_rate fraction 이 1 을 넘으면 거부된다`() {
        isValidBidRateFraction("0.9120") shouldBe true
        isValidBidRateFraction("1.0000") shouldBe true
        isValidBidRateFraction("1.0001") shouldBe false
        // legacy `legal_floor_bid_rate` 의 percent/fraction 겸용 반례(조사 01 H-11) — 값
        // 크기로 단위를 추측하지 않는다. "87.995"는 fraction 축에서 그대로 1 초과라 거부다.
        isValidBidRateFraction("87.995") shouldBe false
    }

    @Test
    fun `testdata 의 모든 후보 bid_rate 는 1 이하다`() {
        val success = CalculateOptimalBidResponse.parseFrom(successBytes).success
        success.candidatesList.forEach { candidate ->
            isValidBidRateFraction(candidate.bidRate.fraction) shouldBe true
        }
    }

    // ---- Uncertainty.sample_size >= 1(우회 후보 (3)) ----

    @Test
    fun `Success 이면서 sample_size 0 은 계약 불변식 위반이다`() {
        val response = CalculateOptimalBidResponse.parseFrom(successBytes)
        val zeroSample =
            response
                .toBuilder()
                .also { it.successBuilder.uncertaintyBuilder.sampleSize = 0 }
                .build()
        isAcceptableSuccess(zeroSample.success) shouldBe false
    }

    @Test
    fun `testdata 의 Success 는 sample_size 가 1 이상이다`() {
        val success = CalculateOptimalBidResponse.parseFrom(successBytes).success
        isAcceptableSuccess(success) shouldBe true
    }

    // ---- FeatureInputs fact oneof 미설정 거부 ----

    @Test
    fun `FeatureInputs 의 모든 fact 가 설정되면 허용된다`() {
        val features = CalculateOptimalBidRequest.parseFrom(requestBytes).features
        isAcceptableFeatureInputs(features) shouldBe true
    }

    @Test
    fun `FeatureInputs 는 base_amount fact 가 미설정이면 거부된다`() {
        val features = CalculateOptimalBidRequest.parseFrom(requestBytes).features
        val missing = features.toBuilder().clearBaseAmount().build()
        missing.baseAmount.factCase shouldBe BaseAmountFact.FactCase.FACT_NOT_SET
        isAcceptableFeatureInputs(missing) shouldBe false
    }

    @Test
    fun `FeatureInputs 는 category_code fact 가 미설정이면 거부된다`() {
        val features = CalculateOptimalBidRequest.parseFrom(requestBytes).features
        val missing = features.toBuilder().clearCategoryCode().build()
        missing.categoryCode.factCase shouldBe CategoryCodeFact.FactCase.FACT_NOT_SET
        isAcceptableFeatureInputs(missing) shouldBe false
    }

    @Test
    fun `FeatureInputs 는 agency_id fact 가 미설정이면 거부된다`() {
        val features = CalculateOptimalBidRequest.parseFrom(requestBytes).features
        val missing = features.toBuilder().clearAgencyId().build()
        missing.agencyId.factCase shouldBe AgencyIdFact.FactCase.FACT_NOT_SET
        isAcceptableFeatureInputs(missing) shouldBe false
    }

    @Test
    fun `FeatureInputs 는 base_amount_provenance_label fact 가 미설정이면 거부된다`() {
        val features = CalculateOptimalBidRequest.parseFrom(requestBytes).features
        val missing = features.toBuilder().clearBaseAmountProvenanceLabel().build()
        missing.baseAmountProvenanceLabel.factCase shouldBe BaseAmountProvenanceLabelFact.FactCase.FACT_NOT_SET
        isAcceptableFeatureInputs(missing) shouldBe false
    }

    // ---- round-trip(testdata 바이트 ↔ 생성 타입) ----

    @Test
    fun `CalculateOptimalBidRequest 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(CalculateOptimalBidRequest.parseFrom(requestBytes)).toList() shouldBe requestBytes.toList()
    }

    @Test
    fun `CalculateOptimalBidResponse Success 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(CalculateOptimalBidResponse.parseFrom(successBytes)).toList() shouldBe successBytes.toList()
    }

    @Test
    fun `CalculateOptimalBidResponse Unmeasurable UNTRAINED_SEGMENT 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(CalculateOptimalBidResponse.parseFrom(unmeasurableUntrainedBytes)).toList() shouldBe
            unmeasurableUntrainedBytes.toList()
    }

    @Test
    fun `CalculateOptimalBidResponse Unmeasurable INSUFFICIENT_SAMPLES 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(CalculateOptimalBidResponse.parseFrom(unmeasurableInsufficientBytes)).toList() shouldBe
            unmeasurableInsufficientBytes.toList()
    }

    @Test
    fun `CalculateOptimalBidResponse ApplicationFailure 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(CalculateOptimalBidResponse.parseFrom(failureBytes)).toList() shouldBe failureBytes.toList()
    }

    @Test
    fun `GetModelMetadataResponse 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetModelMetadataResponse.parseFrom(metadataBytes)).toList() shouldBe metadataBytes.toList()
    }

    // ---- 계약이 요구하는 거부 규칙 — 순수 함수. 실제 Kotlin validation 구현은 M4 몫이고,
    // 여기서는 test 가 그 규칙을 문서화·고정한다(scope.md 「구현 순서」 4). ----

    private fun hasExactlyThreeOrderedCandidates(success: Success): Boolean {
        val labels = success.candidatesList.map { it.label }
        return labels ==
            listOf(
                CandidateLabel.CANDIDATE_LABEL_CONSERVATIVE,
                CandidateLabel.CANDIDATE_LABEL_BASE,
                CandidateLabel.CANDIDATE_LABEL_AGGRESSIVE,
            )
    }

    private fun releaseSatisfiesSelector(
        selector: ModelReleaseSelector,
        responseRelease: ModelRelease,
        promoted: ModelRelease?,
    ): Boolean =
        when (selector.selectorCase) {
            ModelReleaseSelector.SelectorCase.EXACT_RELEASE ->
                responseRelease.releaseId == selector.exactRelease.releaseId &&
                    responseRelease.artifactChecksum == selector.exactRelease.artifactChecksum
            ModelReleaseSelector.SelectorCase.LATEST_PROMOTED ->
                promoted != null &&
                    responseRelease.releaseId == promoted.releaseId &&
                    responseRelease.artifactChecksum == promoted.artifactChecksum
            ModelReleaseSelector.SelectorCase.SELECTOR_NOT_SET, null -> false
        }

    private fun isAcceptableObjective(objective: OptimizationObjective): Boolean =
        objective != OptimizationObjective.OPTIMIZATION_OBJECTIVE_UNSPECIFIED &&
            objective != OptimizationObjective.UNRECOGNIZED

    private fun isAcceptableCandidateLabel(label: CandidateLabel): Boolean =
        label != CandidateLabel.CANDIDATE_LABEL_UNSPECIFIED && label != CandidateLabel.UNRECOGNIZED

    private fun isValidBidRateFraction(fraction: String): Boolean =
        runCatching {
            val value = BigDecimal(fraction)
            value.signum() >= 0 && value <= BigDecimal.ONE
        }.getOrDefault(false)

    private fun isAcceptableSuccess(success: Success): Boolean = success.uncertainty.sampleSize >= 1

    private fun isAcceptableFeatureInputs(features: FeatureInputs): Boolean =
        features.baseAmount.factCase != BaseAmountFact.FactCase.FACT_NOT_SET &&
            features.categoryCode.factCase != CategoryCodeFact.FactCase.FACT_NOT_SET &&
            features.agencyId.factCase != AgencyIdFact.FactCase.FACT_NOT_SET &&
            features.baseAmountProvenanceLabel.factCase != BaseAmountProvenanceLabelFact.FactCase.FACT_NOT_SET
}
