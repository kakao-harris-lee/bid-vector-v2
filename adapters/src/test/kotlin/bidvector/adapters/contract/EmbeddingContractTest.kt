package bidvector.adapters.contract

import contract.bidvector.ml.v1.EmbedTextRequest
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.Embedding
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.TextKind
import contract.bidvector.ml.v1.VectorNormalization
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.MathContext
import java.nio.file.Path

/**
 * M2/2E — `EmbeddingService` 계약 consumer test. Kotlin in-process fake servicer
 * (`EmbeddingServiceCoroutineImplBase`) 위에서 `contracts/testdata/embedding/` 의
 * canonical 바이트(`buf convert`로 생성 — 재현 절차는 `reports/evidence/m2/2e/commands.md`)를
 * 답으로 낸다. 실 servicer(M5)·실 client(4D-2)는 이 slice 밖이다(scope.md 「만들지 않는 것」).
 *
 * **거부 규칙은 이 test 안의 순수 함수다**(2B `PredictionContractTest`와 같은 관례) —
 * `isAcceptableEmbedding`이 D-2E-1의 형태 불변식(차원 일치·L2 정규화)을 문서화·고정한다.
 * 실제 Kotlin validation 구현은 4D-2 몫이다.
 */
class EmbeddingContractTest {
    private val testdataRoot: Path = contractTestdataRoot("embedding")

    private fun bytes(name: String): ByteArray = readTestdataBytes(testdataRoot, name)

    private val requestBytes = bytes("embed_text_request.binpb")
    private val successBytes = bytes("embed_text_response_success.binpb")
    private val failureUnsupportedSchemaBytes = bytes("embed_text_response_failure_unsupported_schema.binpb")
    private val failureInvalidRequestEmptyTextBytes =
        bytes("embed_text_response_failure_invalid_request_empty_text.binpb")
    private val metadataBytes = bytes("get_embedding_metadata_response.binpb")

    private val normEpsilon = BigDecimal(contractPolicyValue("embedding.norm.epsilon"))

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    /** 고정 응답을 내는 fake servicer 위에 in-process stub 을 배선한다(2B ⑨와 같은 관례). */
    private fun stubAnswering(
        embedText: EmbedTextResponse? = null,
        metadata: GetEmbeddingMetadataResponse? = null,
    ): EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineStub {
        val serverName = "bidvector-2e-fake-${System.nanoTime()}"
        val servicer =
            object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                override suspend fun embedText(request: EmbedTextRequest): EmbedTextResponse =
                    embedText ?: error("이 test 는 EmbedText 응답을 배선하지 않았다")

                override suspend fun getEmbeddingMetadata(
                    request: GetEmbeddingMetadataRequest,
                ): GetEmbeddingMetadataResponse = metadata ?: error("이 test 는 GetEmbeddingMetadata 응답을 배선하지 않았다")
            }
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(servicer)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineStub(channel!!)
    }

    // ---- fake servicer 위 실제 RPC ----

    // **주의** — 블록 본문(`{ }`)을 쓴다(2B `PredictionContractTest` 주석 — 식 본문이면
    // kotest `shouldBe`의 수신자 반환 탓에 추론 반환 타입이 `Unit`이 아니게 돼 JUnit이
    // discover 하지 않는다, `OPEN-2B-TEST-DISCOVERY-GUARD`).
    @Test
    fun `fake servicer 가 EmbedText 성공 응답을 낸다`() {
        runBlocking {
            val stub = stubAnswering(embedText = EmbedTextResponse.parseFrom(successBytes))
            val response = stub.embedText(EmbedTextRequest.parseFrom(requestBytes))

            response.resultCase shouldBe EmbedTextResponse.ResultCase.SUCCESS
            response.success.dimension shouldBe response.success.valuesCount
        }
    }

    @Test
    fun `fake servicer 의 GetEmbeddingMetadata 가 promoted release 와 dimension 을 낸다`() {
        runBlocking {
            val stub = stubAnswering(metadata = GetEmbeddingMetadataResponse.parseFrom(metadataBytes))
            val requestEnvelope = EmbedTextRequest.parseFrom(requestBytes).envelope.base
            val request = GetEmbeddingMetadataRequest.newBuilder().setEnvelope(requestEnvelope).build()

            val response = stub.getEmbeddingMetadata(request)

            response.resultCase shouldBe GetEmbeddingMetadataResponse.ResultCase.METADATA
            response.metadata.promoted.releaseId shouldBe "release-2026-09-01"
        }
    }

    // ---- D-2E-1 — 차원 불일치·정규화 위반(위협 모델 방어 (a), 우회 (1)(2)) ----

    @Test
    fun `testdata 의 Embedding 은 values 개수가 dimension 과 같고 L2 정규화다`() {
        val success = EmbedTextResponse.parseFrom(successBytes).success
        isAcceptableEmbedding(success, normEpsilon) shouldBe true
    }

    @Test
    fun `values 개수가 dimension 과 다르면 계약 불변식 위반이다`() {
        // 반복 스칼라 필드(`float`)는 protoc-gen-java 가 `removeXxx(index)` 를 생성하지
        // 않는다(메시지 타입 반복 필드에만 생성 — 2B `removeCandidates`와의 차이,
        // 실측). `clearValues` + `addAllValues(축소된 목록)`로 우회한다.
        val response = EmbedTextResponse.parseFrom(successBytes)
        val mutated =
            response
                .toBuilder()
                .also { builder ->
                    val truncated = builder.successBuilder.valuesList.drop(1)
                    builder.successBuilder.clearValues()
                    builder.successBuilder.addAllValues(truncated)
                }.build()
        isAcceptableEmbedding(mutated.success, normEpsilon) shouldBe false
    }

    @Test
    fun `norm 이 1 을 epsilon 이상 벗어나면 계약 불변식 위반이다`() {
        val response = EmbedTextResponse.parseFrom(successBytes)
        val mutated =
            response
                .toBuilder()
                .also { builder ->
                    val scaled = builder.successBuilder.valuesList.map { it * 2.0f }
                    builder.successBuilder.clearValues()
                    builder.successBuilder.addAllValues(scaled)
                }.build()
        isAcceptableEmbedding(mutated.success, normEpsilon) shouldBe false
    }

    // ---- UNSPECIFIED·정의 밖 enum 정수 거부(fail-closed, ④) ----

    @Test
    fun `TextKind UNSPECIFIED 는 거부된다`() {
        isAcceptableTextKind(TextKind.TEXT_KIND_UNSPECIFIED) shouldBe false
    }

    @Test
    fun `TextKind 정의 밖 정수는 거부된다`() {
        val request = EmbedTextRequest.parseFrom(requestBytes).toBuilder().setKindValue(99).build()
        request.kind shouldBe TextKind.UNRECOGNIZED
        isAcceptableTextKind(request.kind) shouldBe false
    }

    @Test
    fun `testdata 의 kind 는 허용된다`() {
        val request = EmbedTextRequest.parseFrom(requestBytes)
        isAcceptableTextKind(request.kind) shouldBe true
    }

    @Test
    fun `VectorNormalization UNSPECIFIED 는 거부된다`() {
        isAcceptableVectorNormalization(VectorNormalization.VECTOR_NORMALIZATION_UNSPECIFIED) shouldBe false
    }

    @Test
    fun `VectorNormalization 정의 밖 정수는 거부된다`() {
        val response = EmbedTextResponse.parseFrom(successBytes)
        val mutated = response.toBuilder().also { it.successBuilder.normalizationValue = 99 }.build()
        mutated.success.normalization shouldBe VectorNormalization.UNRECOGNIZED
        isAcceptableVectorNormalization(mutated.success.normalization) shouldBe false
    }

    // ---- release 공백 거부(4D-1 `hasNonBlankRelease` 와 같은 규칙, 우회 (6)) ----
    // 4D-1 `hasNonBlankRelease`(main, `bidvector.adapters.ml.ReleaseShapeValidation`)는
    // `Success`(prediction.proto)에 바인딩돼 있어 `Embedding`에는 시그니처가 맞지 않는다 —
    // 그 파일은 out_of_scope(Kotlin main 코드)라 여기서 고치지 않는다(설계와 다른 결정).
    // 같은 다섯 성분 비공백 규칙을 `ModelRelease`(두 메시지가 공유하는 실제 타입) 위에
    // 순수 함수로 다시 문서화한다 — 값·규칙은 4D-1과 동일하다.

    @Test
    fun `testdata 의 Success release 는 다섯 성분 모두 비공백이다`() {
        val success = EmbedTextResponse.parseFrom(successBytes).success
        isModelReleaseNonBlank(success.release) shouldBe true
    }

    @Test
    fun `release 성분 하나라도 공백이면 계약 불변식 위반이다`() {
        val response = EmbedTextResponse.parseFrom(successBytes)
        val mutated =
            response
                .toBuilder()
                .also { it.successBuilder.releaseBuilder.datasetId = "" }
                .build()
        isModelReleaseNonBlank(mutated.success.release) shouldBe false
    }

    // ---- 실패 표본 — UNSUPPORTED_SCHEMA · INVALID_REQUEST(빈 텍스트) ----

    @Test
    fun `testdata 의 UNSUPPORTED_SCHEMA 실패는 비재시도다`() {
        val response = EmbedTextResponse.parseFrom(failureUnsupportedSchemaBytes)
        response.resultCase shouldBe EmbedTextResponse.ResultCase.FAILURE
        response.failure.retryable shouldBe false
    }

    @Test
    fun `testdata 의 빈 텍스트 INVALID_REQUEST 실패는 비재시도다`() {
        val response = EmbedTextResponse.parseFrom(failureInvalidRequestEmptyTextBytes)
        response.resultCase shouldBe EmbedTextResponse.ResultCase.FAILURE
        response.failure.retryable shouldBe false
    }

    // ---- round-trip(testdata 바이트 ↔ 생성 타입) ----

    @Test
    fun `EmbedTextRequest 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(EmbedTextRequest.parseFrom(requestBytes)).toList() shouldBe requestBytes.toList()
    }

    @Test
    fun `EmbedTextResponse Success 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(EmbedTextResponse.parseFrom(successBytes)).toList() shouldBe successBytes.toList()
    }

    @Test
    fun `EmbedTextResponse UNSUPPORTED_SCHEMA 실패는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(EmbedTextResponse.parseFrom(failureUnsupportedSchemaBytes)).toList() shouldBe
            failureUnsupportedSchemaBytes.toList()
    }

    @Test
    fun `EmbedTextResponse INVALID_REQUEST 실패는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(EmbedTextResponse.parseFrom(failureInvalidRequestEmptyTextBytes)).toList() shouldBe
            failureInvalidRequestEmptyTextBytes.toList()
    }

    @Test
    fun `GetEmbeddingMetadataResponse 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetEmbeddingMetadataResponse.parseFrom(metadataBytes)).toList() shouldBe metadataBytes.toList()
    }

    // ---- 계약이 요구하는 거부 규칙 — 순수 함수. 실제 Kotlin validation 구현은 4D-2 몫이고,
    // 여기서는 test 가 그 규칙을 문서화·고정한다(D-2E-1). ----

    private fun isAcceptableTextKind(kind: TextKind): Boolean =
        kind != TextKind.TEXT_KIND_UNSPECIFIED && kind != TextKind.UNRECOGNIZED

    private fun isAcceptableVectorNormalization(normalization: VectorNormalization): Boolean =
        normalization != VectorNormalization.VECTOR_NORMALIZATION_UNSPECIFIED &&
            normalization != VectorNormalization.UNRECOGNIZED

    /** D-2E-1의 형태 불변식 — `values.size == dimension` 이고 L2 norm 이 `1 ± epsilon` 안. */
    private fun isAcceptableEmbedding(
        embedding: Embedding,
        epsilon: BigDecimal,
    ): Boolean {
        val dimensionMatches = embedding.valuesCount == embedding.dimension
        val normalizationAcceptable = isAcceptableVectorNormalization(embedding.normalization)
        val sumOfSquares =
            embedding.valuesList.fold(BigDecimal.ZERO) { acc, value ->
                val component = BigDecimal(value.toDouble(), MathContext.DECIMAL64)
                acc.add(component.multiply(component))
            }
        val norm = sumOfSquares.sqrt(MathContext.DECIMAL64)
        val normWithinEpsilon = norm.subtract(BigDecimal.ONE).abs() <= epsilon
        return dimensionMatches && normalizationAcceptable && normWithinEpsilon
    }

    private fun isModelReleaseNonBlank(release: ModelRelease): Boolean =
        with(release) {
            releaseId.isNotBlank() &&
                artifactChecksum.isNotBlank() &&
                featureSchemaVersion.isNotBlank() &&
                codeVersion.isNotBlank() &&
                datasetId.isNotBlank()
        }
}
