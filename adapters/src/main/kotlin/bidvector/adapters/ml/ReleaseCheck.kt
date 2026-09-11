package bidvector.adapters.ml

import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.ModelReleaseSelector
import contract.bidvector.ml.v1.RequestEnvelope
import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/**
 * M4/4D-1(D-4D-3·D-4D-4, `OPEN-2A-RELEASE-CHECK-4D` 종결) — M2/2B `PredictionContractTest`의
 * `releaseSatisfiesSelector`를 main 으로 승격한 실물. 제3 변환 금지(ADR 0010 D-3)의 **client
 * 집행**이다 — `GrpcBidPredictionGateway.predict`가 `latest_promoted` 요청일 때 같은 호출
 * 안에서 `GetModelMetadata`를 불러 이 함수로 대조한다(D-4D-4, 대조를 별도 port 메서드가
 * 아니라 어댑터 내부 단계로 둬 호출부가 잊을 수 없게 한다). `PredictionContractTest.kt`는
 * 이제 이 함수를 import 해서 쓴다(단언·case 무변경, scope.md S-3).
 */
internal fun releaseSatisfiesSelector(
    selector: ModelReleaseSelector,
    responseRelease: ModelRelease,
    promoted: ModelRelease?,
): Boolean =
    when (selector.selectorCase) {
        ModelReleaseSelector.SelectorCase.EXACT_RELEASE -> {
            responseRelease.releaseId == selector.exactRelease.releaseId &&
                responseRelease.artifactChecksum == selector.exactRelease.artifactChecksum
        }

        ModelReleaseSelector.SelectorCase.LATEST_PROMOTED -> {
            promoted != null &&
                responseRelease.releaseId == promoted.releaseId &&
                responseRelease.artifactChecksum == promoted.artifactChecksum
        }

        ModelReleaseSelector.SelectorCase.SELECTOR_NOT_SET, null -> {
            false
        }
    }

/**
 * 리뷰 F-E(medium) 처방 — `GrpcBidPredictionGateway.fetchPromoted`+`getMetadataOrNull`과
 * `GrpcEmbeddingGateway.fetchPromoted`+`getEmbeddingMetadataOrNull`이 함수 이름과 빈 줄
 * 하나만 다른 채 37줄을 그대로 복제하고 있었다(cpd 미검출 — 식별자만 다름). 두 RPC
 * (`GetModelMetadata`/`GetEmbeddingMetadata`)의 요청·응답 타입이 다르므로 그 차이만
 * [invoke]·[promotedOf] 인자로 뽑고 나머지(envelope 조립, 조회 실패 처리, promoted 공백
 * 거부)는 여기 하나로 합쳤다. metadata 조회 실패(status 무관)는 별도 사유가 아니라
 * 「대조 불가」로 접는다 — `releaseSatisfiesSelector(promoted=null)`이 그대로
 * `ReleaseMismatch`를 낸다. coroutine 취소(`CancellationException`)는 이 둘 중 어느
 * 타입도 아니라 그대로 전파된다(잡지 않는다, `RetryRules.kt`와 같은 이유).
 */
internal suspend fun <S, Resp> fetchPromotedRelease(
    stub: S,
    requestId: String,
    correlationId: String,
    invoke: suspend (S, RequestEnvelope) -> Resp,
    promotedOf: (Resp) -> ModelRelease?,
): ModelRelease? {
    val envelope =
        RequestEnvelope
            .newBuilder()
            .setRequestId(requestId)
            .setCorrelationId(correlationId)
            .build()
    val response = metadataOrNull(stub, envelope, invoke) ?: return null
    val promoted = promotedOf(response)
    // verifier r1 F-2(high) (d) — promoted 가 공백(release_id·artifact_checksum 공백)이면
    // 「조회 성공」이 아니라 「대조 불가」로 접는다. 그래야 응답 release 도 공백일 때
    // `releaseSatisfiesSelector` 가 `""==""` 로 통과하는 경로가 막힌다(양쪽 공백이
    // ReleaseMismatch 대신 Predicted/Embedded 로 새던 반례).
    return promoted?.takeIf { it.releaseId.isNotBlank() && it.artifactChecksum.isNotBlank() }
}

private suspend fun <S, Resp> metadataOrNull(
    stub: S,
    envelope: RequestEnvelope,
    invoke: suspend (S, RequestEnvelope) -> Resp,
): Resp? =
    try {
        invoke(stub, envelope)
    } catch (
        @Suppress("SwallowedException") statusError: StatusException,
    ) {
        null
    } catch (
        @Suppress("SwallowedException") statusRuntimeError: StatusRuntimeException,
    ) {
        null
    }
