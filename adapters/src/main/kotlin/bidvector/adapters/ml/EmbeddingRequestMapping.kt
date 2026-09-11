package bidvector.adapters.ml

import bidvector.workflow.embedding.EmbedTextRequest
import bidvector.workflow.embedding.TextKind
import contract.bidvector.ml.v1.EmbedTextRequest as ProtoEmbedTextRequest
import contract.bidvector.ml.v1.TextKind as ProtoTextKind

/**
 * M4/4D-2(scope.md ①) — 도메인 [EmbedTextRequest] → `embedding.proto` 계약 DTO. 자유
 * `String`은 만들지 않는다(요청 `text` 자체가 이미 도메인 값이다 — 4B-6 이 합성한다).
 * `PredictionEnvelope`를 재사용한다(D-4D2-1 — release 선택자·feature_schema_version·
 * deadline_policy_version 축은 예측 전용이 아니라 `PredictionEnvelope` 전체가 두 RPC 가
 * 공유하는 계약이다). 조립 자체는 `buildPredictionEnvelope`(`PredictionEnvelopeMapping.kt`,
 * cpd 블록 1)를 쓴다 — `toProto()`(`ModelReleaseSelector`)는 그 함수를 거쳐 4D-1
 * `RequestMapping.kt`의 `internal` 확장 함수를 재사용한다(중복 금지).
 */
internal fun mapEmbedRequest(
    request: EmbedTextRequest,
    requestId: String,
    policy: MlCallPolicyData,
    deadlinePolicyVersion: String,
): ProtoEmbedTextRequest {
    val envelope =
        buildPredictionEnvelope(
            requestId = requestId,
            correlationId = request.correlationId.value,
            releaseSelector = request.releaseSelector,
            featureSchemaVersion = policy.featureSchemaVersion,
            deadlinePolicyVersion = deadlinePolicyVersion,
        )

    return ProtoEmbedTextRequest
        .newBuilder()
        .setEnvelope(envelope)
        .setText(request.text)
        .setKind(request.kind.toProto())
        .build()
}

private fun TextKind.toProto(): ProtoTextKind =
    when (this) {
        TextKind.NOTICE -> ProtoTextKind.TEXT_KIND_NOTICE
        TextKind.OPERATOR_PROFILE -> ProtoTextKind.TEXT_KIND_OPERATOR_PROFILE
    }
