package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * scope.md ③, 우회 (7) — breaker open 이면 호출 없이 `Unavailable(CircuitOpen)`. 캐시된
 * 옛 답을 돌려주는 경로가 없다(설계 검토 「닫히지 않는 자리」).
 */
class BreakerTest {
    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private fun gatewayOn(
        servicer: BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase,
        policyData: MlCallPolicyData,
    ): GrpcBidPredictionGateway {
        val serverName = "bidvector-4d1-breaker-${System.nanoTime()}"
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
    fun `연속 실패로 breaker 가 열리면 이후 호출은 servicer 를 부르지 않고 CircuitOpen 이다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val servicer =
                object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                    override suspend fun calculateOptimalBid(
                        request: CalculateOptimalBidRequest,
                    ): CalculateOptimalBidResponse {
                        calls.incrementAndGet()
                        throw StatusException(Status.UNAVAILABLE)
                    }

                    override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                        error("이 test 는 GetModelMetadata 를 부르지 않는다")
                }
            val policy =
                testMlCallPolicy(
                    maxAttempts = 1,
                    breakerSlidingWindowSize = 2,
                    breakerFailureRateThresholdPercent = 50,
                    breakerWaitDurationInOpenState = Duration.ofSeconds(60),
                )
            val gateway = gatewayOn(servicer, policy)

            val first = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))
            val second = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))
            first.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            first.reason shouldBe MlUnavailableReason.RetryBudgetExhausted
            second.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            calls.get() shouldBe 2

            val third = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(1)))

            third.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            third.reason shouldBe MlUnavailableReason.CircuitOpen
            calls.get() shouldBe 2
        }
    }

    /**
     * verifier r3 H-1(high) — 예산 소진(`PredictionCallOutcome.BudgetExhausted`)이 permit 을
     * 반납하지 않던 결함의 회귀 방지. CLOSED 에서는 permit 이 사실상 무제한이라 무해했지만,
     * OPEN→HALF_OPEN 전이 뒤에는 `permittedNumberOfCallsInHalfOpenState`(기본 10)가 유한해
     * 예산 소진 호출을 그 permit 수보다 많이 반복하면(여기선 15회) 수정 전 코드는 11번째
     * 부터 `CircuitOpen`(permit 고갈, 서버 호출 없음)으로 떨어지고 breaker 가 회복 불가능한
     * HALF_OPEN 에 영구히 갇힌다. 수정 뒤(`settlePermit`)에는 매 호출이 permit 을 얻고 즉시
     * 반납해 15회 전부 서버에 닿는다(`DeadlineExceeded`, `CircuitOpen` 아님) — 그 뒤 서버가
     * 건강을 회복하면 넉넉한 예산 호출도 `CircuitOpen` 없이 서버에 닿아 `Predicted`를 받는다.
     */
    @Test
    fun `HALF_OPEN 에서 예산 소진 호출이 permit 을 반납해 이후 호출이 서버에 닿는다(H-1)`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val healthy = AtomicBoolean(false)
            val servicer = untilHealthyServicer(calls, healthy)
            val policy =
                testMlCallPolicy(
                    maxAttempts = 2,
                    backoff = listOf(Duration.ofMillis(400)),
                    breakerSlidingWindowSize = 2,
                    breakerFailureRateThresholdPercent = 50,
                    breakerWaitDurationInOpenState = Duration.ofMillis(200),
                )
            val gateway = gatewayOn(servicer, policy)

            tripBreakerOpen(gateway)
            delay(400) // OPEN → HALF_OPEN 전이 대기(waitDurationInOpenState=200ms 보다 넉넉히).
            val servedInHalfOpen = drainHalfOpenBudgetExhaustedCalls(gateway, calls)
            servedInHalfOpen shouldBe 15

            healthy.set(true)
            assertHealthyCallReachesServer(gateway, calls)
        }
    }

    private fun untilHealthyServicer(
        calls: AtomicInteger,
        healthy: AtomicBoolean,
    ): BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase =
        object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
            override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
                calls.incrementAndGet()
                if (!healthy.get()) throw StatusException(Status.UNAVAILABLE)
                return protoResponse(testSuccessResponse())
            }

            override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                error("이 test 는 GetModelMetadata 를 부르지 않는다")
        }

    /** CLOSED → OPEN — 넉넉한 예산 실패 2회(window=2, threshold=50%). */
    private suspend fun tripBreakerOpen(gateway: GrpcBidPredictionGateway) {
        repeat(2) {
            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(5)))
            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
        }
    }

    /**
     * HALF_OPEN 에서 permit 수(기본 10)보다 많은 예산 소진 호출 15회 — 전부 서버에 닿아야
     * 한다(수정 전이면 11번째부터 `CircuitOpen`, 서버 호출 없음). 실제로 서버에 닿은 횟수를 낸다.
     */
    private suspend fun drainHalfOpenBudgetExhaustedCalls(
        gateway: GrpcBidPredictionGateway,
        calls: AtomicInteger,
    ): Int {
        val before = calls.get()
        repeat(15) {
            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofMillis(80)))
            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.DeadlineExceeded
        }
        return calls.get() - before
    }

    /** 서버 건강 회복 — 넉넉한 예산 호출이 CircuitOpen 없이 서버에 닿아 Predicted 다. */
    private suspend fun assertHealthyCallReachesServer(
        gateway: GrpcBidPredictionGateway,
        calls: AtomicInteger,
    ) {
        val before = calls.get()
        val outcome =
            gateway.predict(
                testBidPredictionRequest(
                    releaseSelector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test"),
                ),
                CallBudget(Duration.ofSeconds(5)),
            )

        outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        (calls.get() > before) shouldBe true
    }
}
