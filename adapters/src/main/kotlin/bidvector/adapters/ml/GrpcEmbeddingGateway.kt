package bidvector.adapters.ml

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.Resolution
import bidvector.workflow.embedding.EmbedTextPort
import bidvector.workflow.embedding.EmbedTextRequest
import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.prediction.CallBudget
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.Embedding
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.RequestEnvelope
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import bidvector.workflow.prediction.ModelReleaseSelector as DomainModelReleaseSelector

private typealias EmbeddingStub = EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineStub

/**
 * M4/4D-2(scope.md ②~⑤) — `EmbedTextPort`의 gRPC 구현. `ManagedChannel`·정책·`Clock`을
 * 생성자로 받는다(4D-1 `GrpcBidPredictionGateway`와 같은 DI 관례, 채널 생성 자체는 M6
 * 배선 소관). resilience 골격(breaker·bounded retry)은 `ResilientPredictionCall.kt`(D-4D2-4
 * 제네릭화)를 **재사용**한다 — 임베딩 고유는 매핑·구조 검증·release 대조뿐이다. 실패는
 * 전부 [EmbeddingOutcome] 안으로 접힌다 — 예외가 이 클래스 밖으로 새지 않는다
 * (`CancellationException`은 예외 — coroutine 취소는 그대로 전파한다, ADR 0010 D-2).
 */
class GrpcEmbeddingGateway(
    channel: ManagedChannel,
    private val policy: EffectiveDatedPolicy<MlCallPolicyData>,
    private val clock: Clock,
) : EmbedTextPort {
    private val stub: EmbeddingStub = EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineStub(channel)
    private val circuitBreaker = buildMlCircuitBreaker("ml-embedding", resolvePolicy().data)

    override suspend fun embed(
        request: EmbedTextRequest,
        budget: CallBudget,
    ): EmbeddingOutcome {
        val resolved = resolvePolicy()
        val remaining = minOf(budget.remaining, resolved.data.deadlineCeiling)
        val requestId = UUID.randomUUID().toString()
        val protoRequest = mapEmbedRequest(request, requestId, resolved.data, resolved.versionLabel)
        val (stubWithDeadline, outcome) =
            callMlRpc(
                stub,
                protoRequest,
                circuitBreaker,
                resolved.data,
                remaining,
                ::isRetryableEmbedFailure,
            ) { s, req -> s.embedText(req) }

        return when (outcome) {
            MlCallOutcome.BreakerOpen -> {
                EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.CircuitOpen)
            }

            // 4D-1 관례(verifier r2 G-4) — 예산 소진은 서버를 한 번도 못 불렀거나 재시도를
            // 포기한 것이지 breaker 가 셀 transport 실패가 아니다.
            MlCallOutcome.BudgetExhausted -> {
                EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.DeadlineExceeded)
            }

            is MlCallOutcome.TransportFailed -> {
                mapEmbedTransportFailure(outcome.error)
            }

            is MlCallOutcome.Responded -> {
                handleResponse(
                    outcome.response,
                    request.releaseSelector,
                    stubWithDeadline,
                    requestId,
                    request.correlationId.value,
                    resolved.data.featureSchemaVersion,
                )
            }
        }
    }

    private suspend fun handleResponse(
        response: EmbedTextResponse,
        selector: DomainModelReleaseSelector,
        stub: EmbeddingStub,
        requestId: String,
        correlationId: String,
        expectedFeatureSchemaVersion: String,
    ): EmbeddingOutcome =
        when (response.resultCase) {
            EmbedTextResponse.ResultCase.SUCCESS -> {
                handleSuccess(response.success, selector, stub, requestId, correlationId, expectedFeatureSchemaVersion)
            }

            EmbedTextResponse.ResultCase.FAILURE -> {
                mapEmbedApplicationFailure(response.failure)
            }

            EmbedTextResponse.ResultCase.RESULT_NOT_SET, null -> {
                EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.ContractViolation)
            }
        }

    /**
     * release 대조는 여기서 마친다(D-4D2-1, 4D-1 `handleSuccess`와 같은 자리) —
     * `latest_promoted`면 **같은 embed 호출 안에서** `GetEmbeddingMetadata`를 부른다. 대조에
     * 실패(불일치·조회 불가)하면 `Embedded`로 가지 않고 `ReleaseMismatch`다(제3 변환 금지,
     * ADR 0010 D-3).
     */
    private suspend fun handleSuccess(
        success: Embedding,
        selector: DomainModelReleaseSelector,
        stub: EmbeddingStub,
        requestId: String,
        correlationId: String,
        expectedFeatureSchemaVersion: String,
    ): EmbeddingOutcome {
        val promoted: ModelRelease? =
            if (selector is DomainModelReleaseSelector.LatestPromoted) {
                fetchPromoted(stub, requestId, correlationId)
            } else {
                null
            }
        if (!releaseSatisfiesSelector(selector.toProto(), success.release, promoted)) {
            return EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.ReleaseMismatch)
        }
        return mapEmbedSuccess(success, expectedFeatureSchemaVersion)
    }

    private suspend fun fetchPromoted(
        stub: EmbeddingStub,
        requestId: String,
        correlationId: String,
    ): ModelRelease? {
        val response = getEmbeddingMetadataOrNull(stub, requestId, correlationId) ?: return null
        val promoted =
            when (response.resultCase) {
                GetEmbeddingMetadataResponse.ResultCase.METADATA -> response.metadata.promoted
                else -> null
            }
        // 4D-1 관례(verifier r1 F-2 (d)) — promoted 가 공백이면 「조회 성공」이 아니라
        // 「대조 불가」로 접는다. 양쪽 공백이 releaseSatisfiesSelector 에서 "" == "" 로
        // 통과하는 경로를 막는다.
        return promoted?.takeIf { it.releaseId.isNotBlank() && it.artifactChecksum.isNotBlank() }
    }

    // metadata 조회 실패(status 무관)는 별도 Unavailable 사유가 아니라 「대조 불가」로 접는다
    // — releaseSatisfiesSelector(promoted=null)이 그대로 ReleaseMismatch 를 낸다. coroutine
    // 취소(CancellationException)는 이 둘 중 어느 타입도 아니라 그대로 전파된다(잡지
    // 않는다, `RetryRules.kt`와 같은 이유).
    private suspend fun getEmbeddingMetadataOrNull(
        stub: EmbeddingStub,
        requestId: String,
        correlationId: String,
    ): GetEmbeddingMetadataResponse? {
        val envelope =
            RequestEnvelope
                .newBuilder()
                .setRequestId(requestId)
                .setCorrelationId(correlationId)
                .build()
        val metadataRequest = GetEmbeddingMetadataRequest.newBuilder().setEnvelope(envelope).build()
        return try {
            stub.getEmbeddingMetadata(metadataRequest)
        } catch (
            @Suppress("SwallowedException") statusError: StatusException,
        ) {
            null
        } catch (
            @Suppress("SwallowedException") statusRuntimeError: StatusRuntimeException,
        ) {
            null
        }
    }

    private fun mapEmbedTransportFailure(error: Throwable): EmbeddingOutcome.Unavailable {
        val status = grpcStatusOf(error)
        val reason =
            when (status?.code) {
                Status.Code.DEADLINE_EXCEEDED -> {
                    EmbeddingUnavailableReason.DeadlineExceeded
                }

                Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED -> {
                    EmbeddingUnavailableReason.RetryBudgetExhausted
                }

                else -> {
                    EmbeddingUnavailableReason.TransportFailed
                }
            }
        return EmbeddingOutcome.Unavailable(reason)
    }

    /**
     * 4D-1 `resolvePolicy` r1 F-10(low) 관례와 같은 fail-fast 방어 — `EMBEDDING_CALL_POLICY`가
     * 어떤 기준일도 못 푸는 상태(시행일이 전부 미래)는 배선 자체의 설정 오류라 [error]로
     * 즉시 실패한다. `EMBEDDING_CALL_POLICY`가 `Initial` 하나만 갖는 한 `embed` 경로는 이
     * 가지에 실질적으로 도달하지 않는다.
     */
    private fun resolvePolicy(): ResolvedEmbeddingCallPolicy {
        val referenceDate = LocalDate.now(clock)
        return when (val resolution = policy.resolve(referenceDate)) {
            is Resolution.Resolved -> {
                val versionLabel = "${resolution.version.source} @ $referenceDate"
                ResolvedEmbeddingCallPolicy(resolution.value, versionLabel)
            }

            is Resolution.NotApplicable -> {
                error("EMBEDDING_CALL_POLICY 가 $referenceDate 에 적용되지 않는다: ${resolution.reason}")
            }
        }
    }
}

private data class ResolvedEmbeddingCallPolicy(
    val data: MlCallPolicyData,
    val versionLabel: String,
)

/**
 * D-4D2-4 처방 2 — `EmbedTextResponse`의 같은 모양(resultCase == FAILURE && retryable)을
 * 재시도 판정 술어로 호출부가 넘긴다. 4D-1 `isRetryableFailureResponse`
 * (`GrpcBidPredictionGateway.kt`)와 같은 논리를 다른 응답 타입에 적용한 것 — `retryable`
 * 필드 판독 자체는 `isRetryableApplicationFailure`(`RetryRules.kt`, 공유)를 쓴다.
 */
private fun isRetryableEmbedFailure(response: EmbedTextResponse): Boolean =
    response.resultCase == EmbedTextResponse.ResultCase.FAILURE &&
        isRetryableApplicationFailure(response.failure.retryable)
