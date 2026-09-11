package bidvector.adapters.ml

import contract.bidvector.ml.v1.PredictionEnvelope
import contract.bidvector.ml.v1.RequestEnvelope
import bidvector.workflow.prediction.ModelReleaseSelector as DomainModelReleaseSelector

/**
 * M4/4D-2(cpd 블록 1) — 예측(`RequestMapping.kt`)과 임베딩(`EmbeddingRequestMapping.kt`)이
 * 각자 조립하던 `PredictionEnvelope` 구성을 하나로 묶는다. 두 RPC 가 같은 계약 타입
 * (`common.proto`의 `PredictionEnvelope`)을 공유한다는 사실 자체가 D-4D2-1 근거였다 —
 * 조립 코드가 둘로 남아 있는 것은 그 근거와 어긋난다. `toProto()`(`ModelReleaseSelector`)는
 * `RequestMapping.kt`의 `internal` 확장 함수를 그대로 쓴다(같은 패키지, 중복 금지).
 */
internal fun buildPredictionEnvelope(
    requestId: String,
    correlationId: String,
    releaseSelector: DomainModelReleaseSelector,
    featureSchemaVersion: String,
    deadlinePolicyVersion: String,
): PredictionEnvelope =
    PredictionEnvelope
        .newBuilder()
        .setBase(
            RequestEnvelope
                .newBuilder()
                .setRequestId(requestId)
                .setCorrelationId(correlationId)
                .build(),
        ).setFeatureSchemaVersion(featureSchemaVersion)
        .setModelReleaseSelector(releaseSelector.toProto())
        .setDeadlinePolicyVersion(deadlinePolicyVersion)
        .build()
