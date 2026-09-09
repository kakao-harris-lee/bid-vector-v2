package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.ModelReleaseRef
import contract.bidvector.ml.v1.ApplicationFailure
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.Success
import contract.bidvector.ml.v1.Unmeasurable
import bidvector.workflow.prediction.UnmeasurableReason as DomainUnmeasurableReason
import contract.bidvector.ml.v1.UnmeasurableReason as ProtoUnmeasurableReason

/**
 * M4/4D-1(scope.md ④~⑦) — 계약 DTO → 도메인 [BidPredictionOutcome]. **fail-closed** — 정의
 * 밖 enum·후보 개수/순서/origin 위반·정규형 위반·`sample_size==0`은 전부
 * `Unavailable(ContractViolation)`(⑦, 우회 (5)(6)). `Unmeasurable`은 값·기본값으로 접지
 * 않는다(⑤, ADR 0010 D-3). `Success` 형태 검증·필드 파싱은 `ParsedSuccessFields.kt`에 있다
 * (detekt `TooManyFunctions` — 한 파일에 열셋을 몰아두지 않는다).
 */
internal fun mapUnmeasurable(message: Unmeasurable): BidPredictionOutcome =
    when (message.reason) {
        ProtoUnmeasurableReason.UNMEASURABLE_REASON_INSUFFICIENT_SAMPLES -> {
            BidPredictionOutcome.Unmeasurable(DomainUnmeasurableReason.InsufficientSamples)
        }

        ProtoUnmeasurableReason.UNMEASURABLE_REASON_UNTRAINED_SEGMENT -> {
            BidPredictionOutcome.Unmeasurable(DomainUnmeasurableReason.UntrainedSegment)
        }

        ProtoUnmeasurableReason.UNMEASURABLE_REASON_FEATURE_ABSENT -> {
            BidPredictionOutcome.Unmeasurable(DomainUnmeasurableReason.FeatureAbsent)
        }

        ProtoUnmeasurableReason.UNMEASURABLE_REASON_UNSPECIFIED, ProtoUnmeasurableReason.UNRECOGNIZED -> {
            BidPredictionOutcome.Unavailable(MlUnavailableReason.ContractViolation)
        }
    }

/**
 * application failure 층(ADR 0010 D-3) — `CalculateOptimalBid`에 나타나지 않는 training
 * 전용 코드(`UNSUPPORTED_TRAINING_SPEC`·`IDEMPOTENCY_CONFLICT`·`JOB_NOT_FOUND`)는 이 RPC
 * 맥락에서 계약 위반으로 접는다(else 없음, `when` 전수).
 */
internal fun mapApplicationFailure(failure: ApplicationFailure): BidPredictionOutcome.Unavailable =
    BidPredictionOutcome.Unavailable(
        when (failure.code) {
            FailureCode.FAILURE_CODE_UNSUPPORTED_SCHEMA -> MlUnavailableReason.UnsupportedSchema

            FailureCode.FAILURE_CODE_UNSUPPORTED_RELEASE -> MlUnavailableReason.UnsupportedRelease

            FailureCode.FAILURE_CODE_INVALID_REQUEST -> MlUnavailableReason.InvalidRequest

            FailureCode.FAILURE_CODE_MODEL_NOT_READY -> MlUnavailableReason.ModelNotReady

            FailureCode.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC,
            FailureCode.FAILURE_CODE_IDEMPOTENCY_CONFLICT,
            FailureCode.FAILURE_CODE_JOB_NOT_FOUND,
            FailureCode.FAILURE_CODE_UNSPECIFIED,
            FailureCode.UNRECOGNIZED,
            -> MlUnavailableReason.ContractViolation
        },
    )

/**
 * release **대조**(selector 일치)는 호출부(`GrpcBidPredictionGateway`)가 이 함수 호출 전에
 * 마친다(D-4D-4) — 이 함수는 이미 selector 와 맞다고 확인된 `Success`만 받는다.
 *
 * 검사 순서: 구조 검증(형태·정규형·후보·**release 다섯 성분 비공백**, `ContractViolation`)이
 * **먼저**다 — 공백 release 는 그 자체로 계약 위반이라 「어떤 schema 냐」를 묻기 전에
 * 걸러진다. 구조가 유효한 뒤에야 **release schema 축의 client 집행**(ADR 0010 D-7 「다른
 * 축」)을 한다 — 응답 `feature_schema_version` 이 이 호출이 실제로 보낸 요청의
 * 값(`expectedFeatureSchemaVersion`)과 다르면 `Unavailable(UnsupportedSchema)`다(verifier
 * r1 F-2 (c)).
 */
internal fun mapSuccess(
    success: Success,
    expectedFeatureSchemaVersion: String,
): BidPredictionOutcome {
    val fields = validatedSuccessFields(success) ?: return contractViolation()
    return if (success.release.featureSchemaVersion != expectedFeatureSchemaVersion) {
        BidPredictionOutcome.Unavailable(MlUnavailableReason.UnsupportedSchema)
    } else {
        BidPredictionOutcome.Predicted(
            candidates = fields.toCandidates(),
            fitness = fields.toFitness(),
            uncertainty = fields.toUncertainty(success),
            release = success.release.toDomain(),
        )
    }
}

internal fun contractViolation(): BidPredictionOutcome.Unavailable =
    BidPredictionOutcome.Unavailable(MlUnavailableReason.ContractViolation)

private fun ModelRelease.toDomain(): ModelReleaseRef =
    ModelReleaseRef(releaseId, artifactChecksum, featureSchemaVersion, codeVersion, datasetId)
