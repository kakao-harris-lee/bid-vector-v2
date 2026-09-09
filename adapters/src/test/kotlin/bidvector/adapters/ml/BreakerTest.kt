package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.CallBudget
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
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
            val serverName = "bidvector-4d1-breaker-${System.nanoTime()}"
            server =
                InProcessServerBuilder
                    .forName(serverName)
                    .directExecutor()
                    .addService(servicer)
                    .build()
                    .start()
            val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
            this@BreakerTest.channel = channel
            val policy =
                testMlCallPolicy(
                    maxAttempts = 1,
                    breakerSlidingWindowSize = 2,
                    breakerFailureRateThresholdPercent = 50,
                    breakerWaitDurationInOpenState = Duration.ofSeconds(60),
                )
            val gateway = GrpcBidPredictionGateway(channel, testMlCallEffectivePolicy(policy), Clock.systemUTC())

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
}
