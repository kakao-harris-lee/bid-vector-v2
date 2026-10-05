package bidvector.adapters.e2e

import bidvector.adapters.contract.contractTestdataRoot
import bidvector.adapters.contract.readTestdataBytes
import bidvector.adapters.ml.MlCallPolicyData
import bidvector.adapters.ml.embeddingMetadataResponse
import bidvector.adapters.ml.protoEmbedResponse
import bidvector.adapters.ml.testEmbeddingSuccess
import bidvector.adapters.ml.testUnitVector
import bidvector.adapters.ml.testMlCallPolicy
import bidvector.adapters.ml.testSuccessResponse
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.OPPORTUNITY_POLICY
import bidvector.workflow.evaluation.OpportunityPolicyData
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.ReleaseKind
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 6D-1 이 쓰는 ML 응답 대본. 성공 응답은 `adapters/ml` 의 기존 fixture 를 재사용하고,
 * **계약 위반 두 갈래**(unknown field · unsupported schema)는 `contracts/testdata` 의 골든
 * 바이트에서 만든다(설계 검토 (2) 우회 3 — test 안 리터럴로 짓지 않는다. 파일이 없으면
 * 로딩이 던져 RED 다).
 *
 * **release 다섯 성분은 한 자리에서 만든다**([e2eRelease]) — 임베딩과 예측이 서로 다른
 * `featureSchemaVersion` 을 쓰면 gateway 의 schema 대조가 먼저 걸려 파이프라인이 ML 단계에서
 * 멈춘다(실측). 호출 정책([e2eMlCallPolicy])도 같은 값을 쓴다.
 */

internal const val E2E_RELEASE_ID = "release-2026-09-01"
internal const val E2E_ROLLBACK_RELEASE_ID = "release-2026-08-01"
internal const val E2E_ARTIFACT_CHECKSUM = "sha256:e2e-6d1"
internal const val E2E_FEATURE_SCHEMA_VERSION = "bidvector.ml.v1-e2e"

internal fun e2eRelease(
    releaseId: String = E2E_RELEASE_ID,
    featureSchemaVersion: String = E2E_FEATURE_SCHEMA_VERSION,
    artifactChecksum: String = E2E_ARTIFACT_CHECKSUM,
): ModelRelease =
    ModelRelease
        .newBuilder()
        .setReleaseId(releaseId)
        .setArtifactChecksum(artifactChecksum)
        .setFeatureSchemaVersion(featureSchemaVersion)
        .setCodeVersion("v-e2e")
        .setDatasetId("dataset-e2e")
        .setReleaseKind(ReleaseKind.RELEASE_KIND_ARTIFACT)
        .build()

/** 예측·임베딩 공용 호출 정책 — 두 gateway 가 같은 `featureSchemaVersion` 을 기대하게 묶는다. */
internal fun e2eMlCallPolicy(
    deadlineCeiling: Duration = Duration.ofSeconds(5),
    maxAttempts: Int = 2,
    featureSchemaVersion: String = E2E_FEATURE_SCHEMA_VERSION,
): MlCallPolicyData =
    testMlCallPolicy(
        deadlineCeiling = deadlineCeiling,
        maxAttempts = maxAttempts,
        backoff = List(maxAttempts - 1) { Duration.ofMillis(1) },
    ).copy(featureSchemaVersion = featureSchemaVersion)

internal fun predictionSuccess(release: ModelRelease = e2eRelease()): CalculateOptimalBidResponse =
    CalculateOptimalBidResponse
        .newBuilder()
        .setSuccess(testSuccessResponse().toBuilder().setRelease(release).build())
        .build()

/**
 * 두 텍스트(공고·프로필)에 같은 단위 벡터를 돌려준다 — 코사인 유사도가 1 이라 match 점수가
 * 결측이 아니다(`composePriority` 는 match 가 없으면 즉시 `Unavailable` 이다).
 */
internal fun successfulMlScript(
    predictionDelay: Duration = Duration.ZERO,
    embeddingDelay: Duration = Duration.ZERO,
    release: ModelRelease = e2eRelease(),
    predictionResponse: CalculateOptimalBidResponse = predictionSuccess(release),
): MlFakeScript =
    MlFakeScript(
        predictionResponse = predictionResponse,
        predictionMetadata = predictionMetadataOf(release),
        embeddingResponse =
            protoEmbedResponse(testEmbeddingSuccess().toBuilder().setRelease(release).build()),
        embeddingMetadata = embeddingMetadataResponse(release),
        predictionDelay = predictionDelay,
        embeddingDelay = embeddingDelay,
    )

/**
 * **직교 벡터** 임베딩 — 공고 텍스트와 프로필 텍스트의 코사인 유사도를 0 으로 만든다. 그러면
 * match 점수가 바닥이라 그 후보의 priority 가 떨어진다(사다리 음성 대조의 유일한 축).
 * 기본 벡터가 전 성분 양수라, 부호를 번갈아 두면 내적이 정확히 0 이다.
 */
internal fun orthogonalEmbedding(release: ModelRelease): EmbedTextResponse {
    val base = testUnitVector(EMBEDDING_DIMENSION)
    val flipped = base.mapIndexed { index, value -> if (index % 2 == 0) value else -value }
    return protoEmbedResponse(
        testEmbeddingSuccess(values = flipped).toBuilder().setRelease(release).build(),
    )
}

/**
 * [markerNoticeNumber] 가 든 텍스트에만 직교 벡터를 돌려준다 — 같은 run 의 다른 후보는 평소대로
 * 높은 match 를 받는다. 프로필 텍스트에는 공고 번호가 없어 영향받지 않는다.
 */
internal fun lowMatchEmbeddingResponder(
    markerNoticeNumber: String,
    release: ModelRelease,
    standard: EmbedTextResponse,
): EmbeddingResponder =
    EmbeddingResponder { request ->
        if (request.text.contains(markerNoticeNumber)) orthogonalEmbedding(release) else standard
    }

/** 계약이 정의하지 않은 필드 번호가 든 성공 응답 — 골든 바이트 뒤에 varint 하나를 잇는다. */
internal fun predictionResponseWithUnknownField(release: ModelRelease): CalculateOptimalBidResponse {
    val golden = predictionGolden("calculate_optimal_bid_response_success.binpb")
    val parsed = CalculateOptimalBidResponse.parseFrom(appendUnknownVarint(golden, UNKNOWN_FIELD_NUMBER, 1L))
    return parsed
        .toBuilder()
        .setSuccess(
            parsed.success
                .toBuilder()
                .setRelease(release)
                .build(),
        ).build()
}

/** 골든이 실제로 unknown field 를 실었는지 — 「붙였다」가 아니라 파싱 결과로 잰다. */
internal fun unknownFieldCountOf(response: CalculateOptimalBidResponse): Int = response.unknownFields.asMap().size

/** Python 서버가 내는 `FEATURE_SCHEMA_VERSION_UNSUPPORTED` 거부 골든 그대로. */
internal fun predictionUnsupportedSchemaResponse(): CalculateOptimalBidResponse =
    CalculateOptimalBidResponse.parseFrom(
        predictionGolden("calculate_optimal_bid_response_failure_unsupported_schema.binpb"),
    )

private fun predictionGolden(name: String): ByteArray = readTestdataBytes(contractTestdataRoot("prediction"), name)

private fun appendUnknownVarint(
    original: ByteArray,
    fieldNumber: Int,
    value: Long,
): ByteArray {
    val buffer = ByteArrayOutputStream()
    buffer.write(original)
    val coded = CodedOutputStream.newInstance(buffer)
    coded.writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT)
    coded.writeUInt64NoTag(value)
    coded.flush()
    return buffer.toByteArray()
}

/**
 * ML 호출에 실제로 걸리는 시한 — `OpportunityPolicyData` 의 호출 예산과 gateway 의
 * `deadlineCeiling` 중 **작은 쪽**이다(`GrpcBidPredictionGateway` 가 `minOf` 로 고른다).
 *
 * **앞 판은 이 값이 늘 test 상한이었다**(verifier r1 F-4 / review G-5): 상한 200ms 가 출하 예산
 * 3s 보다 작아 `minOf` 가 언제나 test 쪽을 골랐고, 그래서 출하 예산을 어떻게 바꿔도 timeout 축이
 * 초록이었다. 지금은 [timeoutMlCallPolicy] 가 상한을 **예산보다 크게** 두어 예산이 묶는 항이 되고,
 * 그 사실 자체를 test 가 `effectivePredictionDeadline(policy) == 예산` 으로 단언한다.
 */
internal fun effectivePredictionDeadline(policy: MlCallPolicyData): Duration =
    minOf(opportunityPolicy().predictionBudget, policy.deadlineCeiling)

/**
 * timeout 축 전용 호출 정책 — gateway 상한을 **출하 예산의 두 배**로 둔다. 상한이 예산보다 크므로
 * `minOf` 가 예산을 고르고, 지연을 그 예산에서 도출하면 실제로 출하 값이 재어진다.
 */
internal fun timeoutMlCallPolicy(): MlCallPolicyData =
    e2eMlCallPolicy(deadlineCeiling = opportunityPolicy().predictionBudget.multipliedBy(CEILING_HEADROOM))

/**
 * 출하 예측 예산의 **고정점**(`reports/evidence/m4/4b6b/policy-values.md`, 승인 대기). timeout 축의
 * 지연이 이 값에서 나오므로, 값이 바뀌면 지연도 함께 따라가 timeout 이 늘 발화하는 공허한 test 가
 * 된다 — 그래서 test 가 값 자체를 먼저 단언한다. 예산을 바꾸면 **서버를 부르기 전에** 붉어진다.
 */
internal val SHIPPED_PREDICTION_BUDGET: Duration = Duration.ofSeconds(3)

internal fun opportunityPolicy(): OpportunityPolicyData {
    val resolution = OPPORTUNITY_POLICY.resolve(LocalDate.ofInstant(E2E_NOW, ZoneOffset.UTC))
    check(resolution is Resolution.Resolved) { "OPPORTUNITY_POLICY 가 해소되지 않았다: $resolution" }
    return resolution.value
}

private const val UNKNOWN_FIELD_NUMBER = 999
private const val CEILING_HEADROOM = 2L

/** `testEmbeddingSuccess` 의 기본 차원 — metadata 대조가 같은 값을 요구한다. */
private const val EMBEDDING_DIMENSION = 4
