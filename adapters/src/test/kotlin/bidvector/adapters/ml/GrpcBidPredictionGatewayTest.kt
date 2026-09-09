package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import contract.bidvector.ml.v1.ModelMetadata
import contract.bidvector.ml.v1.UnmeasurableReason
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * scope.md ①~⑦, 설계 검토 「구현 지시」 — fake servicer 위 `GrpcBidPredictionGateway`
 * consumer test. 매핑·release 대조·application failure 재시도가 **실 gateway 경로**로
 * 증명된다(2B/2D fake 대역과 같은 관례).
 */
class GrpcBidPredictionGatewayTest {
    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private fun gatewayOn(
        servicer: BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase,
        policyData: MlCallPolicyData = testMlCallPolicy(),
    ): GrpcBidPredictionGateway {
        val serverName = "bidvector-4d1-gateway-${System.nanoTime()}"
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(servicer)
                .build()
                .start()
        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        this.channel = channel
        return GrpcBidPredictionGateway(channel, testMlCallEffectivePolicy(policyData), Clock.systemUTC())
    }

    @Test
    fun `정상 응답과 release 일치는 Predicted 를 낸다`() {
        runBlocking {
            val release = testModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val servicer =
                fixedServicer(
                    calculate = successResponse(release),
                    metadata = metadataResponse(release),
                )
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        }
    }

    @Test
    fun `latest_promoted 인데 응답 release 가 promoted 와 다르면 ReleaseMismatch 다`() {
        runBlocking {
            val responseRelease = testModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val promotedRelease = testModelRelease(releaseId = "other", artifactChecksum = "c1")
            val servicer =
                fixedServicer(
                    calculate = successResponse(responseRelease),
                    metadata = metadataResponse(promotedRelease),
                )
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.ReleaseMismatch
        }
    }

    @Test
    fun `latest_promoted 인데 응답 release 와 promoted 가 둘 다 공백이면 통과가 아니라 ReleaseMismatch 다(F-2 P4c)`() {
        runBlocking {
            val blankRelease =
                contract.bidvector.ml.v1.ModelRelease
                    .getDefaultInstance()
            val servicer =
                fixedServicer(
                    calculate = successResponse(blankRelease),
                    metadata = metadataResponse(blankRelease),
                )
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.ReleaseMismatch
        }
    }

    @Test
    fun `exact_release 요청은 GetModelMetadata 를 부르지 않는다`() {
        runBlocking {
            val release = testModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val servicer = fixedServicer(calculate = successResponse(release), metadata = null)
            val gateway = gatewayOn(servicer)
            val selector = ModelReleaseSelector.Exact("r1", "c1")

            val outcome =
                gateway.predict(testBidPredictionRequest(releaseSelector = selector), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        }
    }

    @Test
    fun `Unmeasurable 응답은 값으로 접히지 않고 그대로 도메인 사유가 된다`() {
        runBlocking {
            val response =
                CalculateOptimalBidResponse
                    .newBuilder()
                    .also {
                        it.unmeasurableBuilder.reason = UnmeasurableReason.UNMEASURABLE_REASON_UNTRAINED_SEGMENT
                    }.build()
            val gateway = gatewayOn(fixedServicer(calculate = response))

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unmeasurable>()
        }
    }

    @Test
    fun `비재시도 ApplicationFailure 는 한 번만 호출되고 매핑된 사유로 Unavailable 이다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val failure = failureResponse(FailureCode.FAILURE_CODE_INVALID_REQUEST, retryable = false)
            val servicer = countingServicer(calls, failure)
            val gateway = gatewayOn(servicer)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.InvalidRequest
            calls.get() shouldBe 1
        }
    }

    @Test
    fun `MODEL_NOT_READY 가 지속되면 재시도 상한만큼 호출되고 ModelNotReady 로 끝난다`() {
        runBlocking {
            val policy = testMlCallPolicy(maxAttempts = 3)
            val calls = AtomicInteger(0)
            val failure = failureResponse(FailureCode.FAILURE_CODE_MODEL_NOT_READY, retryable = true)
            val servicer = countingServicer(calls, failure)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.ModelNotReady
            calls.get() shouldBe 3
        }
    }

    @Test
    fun `재시도 불가 transport status(INVALID_ARGUMENT)는 한 번만 시도한다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val servicer = throwingServicer(calls, Status.INVALID_ARGUMENT)
            val gateway = gatewayOn(servicer)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.TransportFailed
            calls.get() shouldBe 1
        }
    }

    @Test
    fun `UNAVAILABLE 이 재시도 상한을 넘으면 RetryBudgetExhausted 다`() {
        runBlocking {
            val policy = testMlCallPolicy(maxAttempts = 3)
            val calls = AtomicInteger(0)
            val servicer = throwingServicer(calls, Status.UNAVAILABLE)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.RetryBudgetExhausted
            calls.get() shouldBe 3
        }
    }

    // ---- verifier r1 F-1(high) — 백오프 미구현 회귀 방지(probe P2) ----

    @Test
    fun `재시도 사이에 정책 백오프만큼 실제로 지연한다(경과시간 probe)`() {
        runBlocking {
            val policy =
                testMlCallPolicy(maxAttempts = 3, backoff = listOf(Duration.ofMillis(60), Duration.ofMillis(120)))
            val calls = AtomicInteger(0)
            val servicer = throwingServicer(calls, Status.RESOURCE_EXHAUSTED)
            val gateway = gatewayOn(servicer, policy)

            val start = System.nanoTime()
            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(5)))
            val elapsedMillis = (System.nanoTime() - start) / 1_000_000

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.RetryBudgetExhausted
            calls.get() shouldBe 3
            // 정책 배열 합(60+120=180ms) 이상 걸려야 한다 — 백오프가 지연 없이 즉시
            // 다음 attempt 로 가면(수정 전 실측 6ms) 이 하한을 못 채운다.
            (elapsedMillis >= 180) shouldBe true
        }
    }

    @Test
    fun `남은 예산이 백오프를 감당하지 못하면 재시도하지 않고 DeadlineExceeded 다`() {
        runBlocking {
            val policy =
                testMlCallPolicy(maxAttempts = 3, backoff = listOf(Duration.ofSeconds(10), Duration.ofSeconds(10)))
            val calls = AtomicInteger(0)
            val servicer = throwingServicer(calls, Status.RESOURCE_EXHAUSTED)
            val gateway = gatewayOn(servicer, policy)

            // 예산 200ms 인데 첫 backoff 가 10초 — 재시도할 시간이 없으니 1회 시도 뒤
            // 바로 DeadlineExceeded 로 끝나야 한다(재시도 폭풍 방지).
            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofMillis(200)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.DeadlineExceeded
            calls.get() shouldBe 1
        }
    }

    // ---- verifier r1 F-3(medium) — DEADLINE_EXCEEDED 재시도 예산 술어(F-1 과 같은 메커니즘) ----

    @Test
    fun `DEADLINE_EXCEEDED 도 예산이 없으면 재시도 없이 1회 호출로 끝난다(gRPC 절대 deadline 부작용에 기대지 않는다)`() {
        runBlocking {
            val policy =
                testMlCallPolicy(maxAttempts = 3, backoff = listOf(Duration.ofSeconds(10), Duration.ofSeconds(10)))
            val calls = AtomicInteger(0)
            val servicer = throwingServicer(calls, Status.DEADLINE_EXCEEDED)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofMillis(200)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.DeadlineExceeded
            calls.get() shouldBe 1
        }
    }

    @Test
    fun `재시도는 같은 request_id 를 재사용한다(D-4 멱등)`() {
        runBlocking {
            val seenRequestIds = mutableListOf<String>()
            val policy = testMlCallPolicy(maxAttempts = 3)
            val servicer =
                object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                    override suspend fun calculateOptimalBid(
                        request: CalculateOptimalBidRequest,
                    ): CalculateOptimalBidResponse {
                        seenRequestIds.add(request.envelope.base.requestId)
                        throw StatusException(Status.UNAVAILABLE)
                    }

                    override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                        error("이 test 는 GetModelMetadata 를 부르지 않는다")
                }
            val gateway = gatewayOn(servicer, policy)

            gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            seenRequestIds.size shouldBe 3
            seenRequestIds.toSet().size shouldBe 1
        }
    }

    // ---- verifier r2 G-1·G-2·G-4·G-5 의 실 gateway 경로 test는
    // `GrpcBidPredictionGatewayVerifierR2Test.kt`로 갈라졌다(size ratchet §5). ----
}

private fun countingServicer(
    calls: AtomicInteger,
    calculate: CalculateOptimalBidResponse,
): BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase =
    object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
        override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
            calls.incrementAndGet()
            return calculate
        }

        override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
            error("이 test 는 GetModelMetadata 를 부르지 않는다")
    }

private fun throwingServicer(
    calls: AtomicInteger,
    status: Status,
): BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase =
    object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
        override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
            calls.incrementAndGet()
            throw StatusException(status)
        }

        override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
            error("이 test 는 GetModelMetadata 를 부르지 않는다")
    }

private fun successResponse(release: contract.bidvector.ml.v1.ModelRelease): CalculateOptimalBidResponse =
    CalculateOptimalBidResponse
        .newBuilder()
        .setSuccess(testSuccessResponse().toBuilder().setRelease(release).build())
        .build()

private fun failureResponse(
    code: FailureCode,
    retryable: Boolean,
): CalculateOptimalBidResponse =
    CalculateOptimalBidResponse
        .newBuilder()
        .also {
            it.failureBuilder.code = code
            it.failureBuilder.retryable = retryable
        }.build()

private fun metadataResponse(promoted: contract.bidvector.ml.v1.ModelRelease): GetModelMetadataResponse =
    GetModelMetadataResponse
        .newBuilder()
        .setMetadata(ModelMetadata.newBuilder().setPromoted(promoted).build())
        .build()
