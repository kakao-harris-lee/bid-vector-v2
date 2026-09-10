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
    private val circuitBreaker = buildMlCircuitBreaker("ml-bid-prediction", resolvePolicy().data)

    override suspend fun predict(
        request: BidPredictionRequest,
        budget: CallBudget,
    ): BidPredictionOutcome {
        val resolved = resolvePolicy()
        val remaining = minOf(budget.remaining, resolved.data.deadlineCeiling)
        val requestId = UUID.randomUUID().toString()
        val protoRequest = mapRequest(request, requestId, resolved.data, resolved.versionLabel)
        val (stubWithDeadline, outcome) =
            callMlRpc(
                stub,
                protoRequest,
                circuitBreaker,
                resolved.data,
                remaining,
                ::isRetryableFailureResponse,
            ) { s, req -> s.calculateOptimalBid(req) }

        return when (outcome) {
            MlCallOutcome.BreakerOpen -> {
                BidPredictionOutcome.Unavailable(MlUnavailableReason.CircuitOpen)
            }

            // verifier r2 G-4 — 예산 소진은 서버를 한 번도 못 불렀거나 재시도를 포기한
            // 것이지 breaker 가 셀 transport 실패가 아니다(ResilientPredictionCall.kt).
            MlCallOutcome.BudgetExhausted -> {
                BidPredictionOutcome.Unavailable(MlUnavailableReason.DeadlineExceeded)
            }

            is MlCallOutcome.TransportFailed -> {
                mapTransportFailure(outcome.error)
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
        response: CalculateOptimalBidResponse,
        selector: DomainModelReleaseSelector,
        stub: PredictionStub,
        requestId: String,
        correlationId: String,
        expectedFeatureSchemaVersion: String,
    ): BidPredictionOutcome =
        when (response.resultCase) {
            CalculateOptimalBidResponse.ResultCase.SUCCESS -> {
                handleSuccess(response.success, selector, stub, requestId, correlationId, expectedFeatureSchemaVersion)
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
        expectedFeatureSchemaVersion: String,
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
        return mapSuccess(success, expectedFeatureSchemaVersion)
    }

    private suspend fun fetchPromoted(
        stub: PredictionStub,
        requestId: String,
        correlationId: String,
    ): ModelRelease? {
        val response = getMetadataOrNull(stub, requestId, correlationId) ?: return null
        val promoted =
            when (response.resultCase) {
                GetModelMetadataResponse.ResultCase.METADATA -> response.metadata.promoted
                else -> null
            }
        // verifier r1 F-2(high) (d) — promoted 가 공백(release_id·artifact_checksum 공백)이면
        // 「조회 성공」이 아니라 「대조 불가」로 접는다. 그래야 응답 release 도 공백일 때
        // `releaseSatisfiesSelector` 가 `""==""` 로 통과하는 경로가 막힌다(양쪽 공백이
        // ReleaseMismatch 대신 Predicted 로 새던 반례).
        return promoted?.takeIf { it.releaseId.isNotBlank() && it.artifactChecksum.isNotBlank() }
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

    /**
     * verifier r1 F-10(low) — `Resolution.NotApplicable` 가지의 `error(...)`는 scope.md ④
     * 「예외가 이 클래스 밖으로 새지 않는다」의 대상이 **아니다**. 그 규율은 ML 호출의
     * 업무 실패(transport·application·release 불일치 등)를 가리킨다 — 이 가지는 배선
     * 자체의 설정 오류(`ML_CALL_POLICY`에 [bidvector.sharedkernel.EffectiveFrom.Initial]
     * 항목이 없어 어떤 기준일도 못 푸는 상태)를 잡는 fail-fast 방어다. `ML_CALL_POLICY`가
     * `Initial` 하나만 갖는 한(운영 인스턴스 실측, `MlCallPolicyDataTest`) `predict` 경로는
     * 이 가지에 실질적으로 도달하지 않는다 — 제거하지 않는 이유는 정책이 시행일 기반
     * 다중 entry 로 확장될 미래(`OPEN-M2-DEADLINE-VALUES` 실측 갱신)에 이 방어가 실제
     * 배선 결함(예: 시행일이 전부 미래인 정책 배포)을 조용히 통과시키지 않게 하려는 것.
     */
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

/**
 * D-4D2-4 처방 2 — 응답 타입에 의존하는 재시도 판정 술어는 `ResilientPredictionCall.kt`가
 * 아니라 호출부가 갖는다(4D-1 에서 이 파일로 이동, 순수 위치 이동 — 판정 로직 자체는
 * 한 글자도 바뀌지 않았다). 임베딩 쪽 대응은 `GrpcEmbeddingGateway.kt`의
 * `isRetryableEmbedFailure`다.
 */
private fun isRetryableFailureResponse(response: CalculateOptimalBidResponse): Boolean =
    response.resultCase == CalculateOptimalBidResponse.ResultCase.FAILURE &&
        isRetryableApplicationFailure(response.failure.retryable)
