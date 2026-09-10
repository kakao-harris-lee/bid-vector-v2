package bidvector.adapters.ml

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.workflow.embedding.EmbedTextRequest
import bidvector.workflow.embedding.TextKind
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.Embedding
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.VectorNormalization
import java.time.Duration
import kotlin.math.sqrt
import contract.bidvector.ml.v1.EmbedTextRequest as ProtoEmbedTextRequest

/**
 * adapters/ml embedding test 전용 fixture(4D-1 `MlTestFixtures.kt` 관례) — 운영
 * `EMBEDDING_CALL_POLICY`를 쓰지 않는다.
 */
internal fun testEmbeddingCallPolicy(
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
        featureSchemaVersion = "bidvector.ml.v1-embedding-test",
    )

internal fun testEmbeddingCallEffectivePolicy(
    data: MlCallPolicyData = testEmbeddingCallPolicy(),
): EffectiveDatedPolicy<MlCallPolicyData> =
    EffectiveDatedPolicy(source = "test", entries = listOf(EffectiveFrom.Initial to data))

internal fun testEmbedTextRequest(
    text: String = "공고 원문 합성 텍스트",
    kind: TextKind = TextKind.NOTICE,
    releaseSelector: ModelReleaseSelector = ModelReleaseSelector.LatestPromoted,
): EmbedTextRequest =
    EmbedTextRequest(
        text = text,
        kind = kind,
        releaseSelector = releaseSelector,
        correlationId = CorrelationId("test-correlation-id"),
    )

internal fun testEmbeddingModelRelease(
    releaseId: String = "release-2026-09-01",
    artifactChecksum: String = "sha256:test",
): ModelRelease =
    ModelRelease
        .newBuilder()
        .setReleaseId(releaseId)
        .setArtifactChecksum(artifactChecksum)
        .setFeatureSchemaVersion("bidvector.ml.v1-embedding-test")
        .setCodeVersion("v-test")
        .setDatasetId("dataset-test")
        .build()

/** 단위 벡터(norm=1, L2) — 각 성분이 `1/sqrt(dimension)`인 정규화된 값. */
internal fun testUnitVector(dimension: Int): List<Float> {
    val component = (1.0 / sqrt(dimension.toDouble())).toFloat()
    return List(dimension) { component }
}

internal fun testEmbeddingSuccess(
    releaseId: String = "release-2026-09-01",
    artifactChecksum: String = "sha256:test",
    values: List<Float> = testUnitVector(4),
): Embedding =
    Embedding
        .newBuilder()
        .addAllValues(values)
        .setDimension(values.size)
        .setNormalization(VectorNormalization.VECTOR_NORMALIZATION_L2)
        .setRelease(testEmbeddingModelRelease(releaseId, artifactChecksum))
        .build()

/** `Embedding`을 `EmbedTextResponse.SUCCESS`로 감싸는 반복 배선(consumer test 공용). */
internal fun protoEmbedResponse(success: Embedding): EmbedTextResponse =
    EmbedTextResponse.newBuilder().setSuccess(success).build()

/** 고정 응답 servicer(consumer test 공용, 4D-1 `fixedServicer` 관례). */
internal fun fixedEmbeddingServicer(
    embedText: EmbedTextResponse? = null,
    metadata: GetEmbeddingMetadataResponse? = null,
): EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase =
    object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
        override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse =
            embedText ?: error("이 test 는 EmbedText 응답을 배선하지 않았다")

        override suspend fun getEmbeddingMetadata(request: GetEmbeddingMetadataRequest): GetEmbeddingMetadataResponse =
            metadata ?: error("이 test 는 GetEmbeddingMetadata 응답을 배선하지 않았다")
    }

internal fun embeddingMetadataResponse(promoted: ModelRelease): GetEmbeddingMetadataResponse =
    GetEmbeddingMetadataResponse
        .newBuilder()
        .setMetadata(
            contract.bidvector.ml.v1.EmbeddingMetadata
                .newBuilder()
                .setPromoted(promoted)
                .build(),
        ).build()
