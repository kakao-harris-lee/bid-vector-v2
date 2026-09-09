package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.Resolution
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionPort
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.CallBudget
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.RequestEnvelope
import contract.bidvector.ml.v1.Success
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import bidvector.workflow.prediction.ModelReleaseSelector as DomainModelReleaseSelector

private typealias PredictionStub = BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub

/**
 * M4/4D-1(scope.md ①~⑩) — `BidPredictionPort`의 gRPC 구현. `ManagedChannel`·정책·`Clock`을
 * 생성자로 받는다(DI, 채널 생성 자체는 M6 배선 소관). 실패는 전부 [BidPredictionOutcome]
 * 안으로 접힌다 — 예외가 이 클래스 밖으로 새지 않는다(`CancellationException`은 예외 —
 * coroutine 취소는 그대로 전파한다, ADR 0010 D-2).
 */
class GrpcBidPredictionGateway(
    channel: ManagedChannel,
    private val policy: EffectiveDatedPolicy<MlCallPolicyData>,
    private val clock: Clock,
) : BidPredictionPort {
    private val stub: PredictionStub = BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel)
    private val circuitBreaker = buildPredictionCircuitBreaker("ml-bid-prediction", resolvePolicy().data)

    override suspend fun predict(
        request: BidPredictionRequest,
        budget: CallBudget,
    ): BidPredictionOutcome {
        val resolved = resolvePolicy()
        val remaining = minOf(budget.remaining, resolved.data.deadlineCeiling)
        val requestId = UUID.randomUUID().toString()
        val protoRequest = mapRequest(request, requestId, resolved.data, resolved.versionLabel)
        val stubWithDeadline = stub.withDeadlineAfter(remaining.toMillis(), TimeUnit.MILLISECONDS)
        val outcome =
            callResilient(circuitBreaker, resolved.data.maxAttempts) {
                stubWithDeadline.calculateOptimalBid(protoRequest)
            }

        return when (outcome) {
            PredictionCallOutcome.BreakerOpen -> {
                BidPredictionOutcome.Unavailable(MlUnavailableReason.CircuitOpen)
            }

            is PredictionCallOutcome.TransportFailed -> {
                mapTransportFailure(outcome.error)
            }

            is PredictionCallOutcome.Responded -> {
                handleResponse(
                    outcome.response,
                    request.releaseSelector,
                    stubWithDeadline,
                    requestId,
                    request.correlationId.value,
                )
            }
        }
    }

    private suspend fun handleResponse(
        response: CalculateOptimalBidResponse,
        selector: DomainModelReleaseSelector,
        stub: PredictionStub,
        requestId: String,
        correlationId: String,
    ): BidPredictionOutcome =
        when (response.resultCase) {
            CalculateOptimalBidResponse.ResultCase.SUCCESS -> {
                handleSuccess(response.success, selector, stub, requestId, correlationId)
            }

            CalculateOptimalBidResponse.ResultCase.UNMEASURABLE -> {
                mapUnmeasurable(response.unmeasurable)
            }

            CalculateOptimalBidResponse.ResultCase.FAILURE -> {
                mapApplicationFailure(response.failure)
            }

            CalculateOptimalBidResponse.ResultCase.RESULT_NOT_SET, null -> {
                BidPredictionOutcome.Unavailable(MlUnavailableReason.ContractViolation)
            }
        }

    /**
     * release 대조는 여기서 마친다(D-4D-4) — `latest_promoted`면 **같은 predict 호출 안에서**
     * `GetModelMetadata`를 부른다. 대조에 실패(불일치·조회 불가)하면 `Predicted`로 가지 않고
     * `ReleaseMismatch`다(제3 변환 금지, ADR 0010 D-3).
     */
    private suspend fun handleSuccess(
        success: Success,
        selector: DomainModelReleaseSelector,
        stub: PredictionStub,
        requestId: String,
        correlationId: String,
    ): BidPredictionOutcome {
        val promoted: ModelRelease? =
            if (selector is DomainModelReleaseSelector.LatestPromoted) {
                fetchPromoted(stub, requestId, correlationId)
            } else {
                null
            }
        if (!releaseSatisfiesSelector(selector.toProto(), success.release, promoted)) {
            return BidPredictionOutcome.Unavailable(MlUnavailableReason.ReleaseMismatch)
        }
        return mapSuccess(success)
    }

    private suspend fun fetchPromoted(
        stub: PredictionStub,
        requestId: String,
        correlationId: String,
    ): ModelRelease? {
        val response = getMetadataOrNull(stub, requestId, correlationId) ?: return null
        return when (response.resultCase) {
            GetModelMetadataResponse.ResultCase.METADATA -> response.metadata.promoted
            else -> null
        }
    }

    // metadata 조회 실패(status 무관)는 별도 Unavailable 사유가 아니라 「대조 불가」로 접는다
    // — releaseSatisfiesSelector(promoted=null)이 그대로 ReleaseMismatch를 낸다. coroutine
    // 취소(`CancellationException`)는 이 둘 중 어느 타입도 아니라 그대로 전파된다(잡지
    // 않는다, `RetryRules.kt`와 같은 이유).
    private suspend fun getMetadataOrNull(
        stub: PredictionStub,
        requestId: String,
        correlationId: String,
    ): GetModelMetadataResponse? {
        val envelope =
            RequestEnvelope
                .newBuilder()
                .setRequestId(requestId)
                .setCorrelationId(correlationId)
                .build()
        val metadataRequest = GetModelMetadataRequest.newBuilder().setEnvelope(envelope).build()
        return try {
            stub.getModelMetadata(metadataRequest)
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

    private fun mapTransportFailure(error: Throwable): BidPredictionOutcome.Unavailable {
        val status = grpcStatusOf(error)
        val reason =
            when (status?.code) {
                Status.Code.DEADLINE_EXCEEDED -> MlUnavailableReason.DeadlineExceeded
                Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED -> MlUnavailableReason.RetryBudgetExhausted
                else -> MlUnavailableReason.TransportFailed
            }
        return BidPredictionOutcome.Unavailable(reason)
    }

    private fun resolvePolicy(): ResolvedMlCallPolicy {
        val referenceDate = LocalDate.now(clock)
        return when (val resolution = policy.resolve(referenceDate)) {
            is Resolution.Resolved -> {
                val versionLabel = "${resolution.version.source} @ $referenceDate"
                ResolvedMlCallPolicy(resolution.value, versionLabel)
            }

            is Resolution.NotApplicable -> {
                error("ML_CALL_POLICY 가 $referenceDate 에 적용되지 않는다: ${resolution.reason}")
            }
        }
    }
}

private data class ResolvedMlCallPolicy(
    val data: MlCallPolicyData,
    val versionLabel: String,
)
