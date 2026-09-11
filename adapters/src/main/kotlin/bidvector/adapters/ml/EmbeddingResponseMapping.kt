package bidvector.adapters.ml

import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.embedding.EmbeddingVector
import bidvector.workflow.prediction.ModelReleaseRef
import contract.bidvector.ml.v1.ApplicationFailure
import contract.bidvector.ml.v1.Embedding
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.ModelRelease

/**
 * M4/4D-2(scope.md ③~⑤) — 계약 DTO → 도메인 [EmbeddingOutcome]. **fail-closed** —
 * `isAcceptableEmbeddingShape`를 통과하지 못한 `Embedding`은 전부
 * `Unavailable(ContractViolation)`(우회 (1) 차원 불일치·(2) UNSPECIFIED 정규화·(3)
 * release 공백 — **(6)이 아니다, 리뷰 F-D 정정**: (6)은 이 slice가 열어 둔 값 타입 위조
 * 축이다). `embedding.proto`에 나타나지 않는
 * training 전용 코드(`UNSUPPORTED_TRAINING_SPEC`·`IDEMPOTENCY_CONFLICT`·`JOB_NOT_FOUND`)는
 * 이 RPC 맥락에서 계약 위반으로 접는다(else 없음, `when` 전수 — `ResponseMapping.kt`의
 * `mapApplicationFailure`와 동형).
 */
internal fun mapEmbedApplicationFailure(failure: ApplicationFailure): EmbeddingOutcome.Unavailable =
    EmbeddingOutcome.Unavailable(
        when (failure.code) {
            FailureCode.FAILURE_CODE_UNSUPPORTED_SCHEMA -> EmbeddingUnavailableReason.UnsupportedSchema

            FailureCode.FAILURE_CODE_UNSUPPORTED_RELEASE -> EmbeddingUnavailableReason.UnsupportedRelease

            FailureCode.FAILURE_CODE_INVALID_REQUEST -> EmbeddingUnavailableReason.InvalidRequest

            FailureCode.FAILURE_CODE_MODEL_NOT_READY -> EmbeddingUnavailableReason.ModelNotReady

            FailureCode.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC,
            FailureCode.FAILURE_CODE_IDEMPOTENCY_CONFLICT,
            FailureCode.FAILURE_CODE_JOB_NOT_FOUND,
            FailureCode.FAILURE_CODE_UNSPECIFIED,
            FailureCode.UNRECOGNIZED,
            -> EmbeddingUnavailableReason.ContractViolation
        },
    )

/**
 * release **대조**(selector 일치)는 호출부(`GrpcEmbeddingGateway`)가 이 함수 호출 전에
 * 마친다(**D-4D-4**, 4D-1 `mapSuccess`와 같은 순서 — 리뷰 F-D 정정: 이전엔 D-4D2-4
 * (`callResilient` 제네릭화 결정, 무관한 ID)로 잘못 인용했다) — 이 함수는 이미 selector 와 맞다고
 * 확인된 `Embedding`만 받는다. 검사 순서: 구조 검증(`isAcceptableEmbeddingShape` — 형태·
 * 정규형·release 다섯 성분 비공백, `ContractViolation`)이 **먼저**다. 구조가 유효한
 * 뒤에야 schema 축의 client 집행(ADR 0010 D-7 「다른 축」)을 한다 — 응답
 * `feature_schema_version`이 이 호출이 실제로 보낸 요청 값(`expectedFeatureSchemaVersion`)과
 * 다르면 `Unavailable(UnsupportedSchema)`다(4D-1 verifier r1 F-2 (c) 관례).
 */
internal fun mapEmbedSuccess(
    success: Embedding,
    expectedFeatureSchemaVersion: String,
): EmbeddingOutcome {
    if (!isAcceptableEmbeddingShape(success)) return embeddingContractViolation()
    return if (success.release.featureSchemaVersion != expectedFeatureSchemaVersion) {
        EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.UnsupportedSchema)
    } else {
        EmbeddingOutcome.Embedded(
            vector = EmbeddingVector(success.valuesList.toList()),
            release = success.release.toDomainEmbeddingRelease(),
        )
    }
}

internal fun embeddingContractViolation(): EmbeddingOutcome.Unavailable =
    EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.ContractViolation)

private fun ModelRelease.toDomainEmbeddingRelease(): ModelReleaseRef =
    ModelReleaseRef(releaseId, artifactChecksum, featureSchemaVersion, codeVersion, datasetId)
