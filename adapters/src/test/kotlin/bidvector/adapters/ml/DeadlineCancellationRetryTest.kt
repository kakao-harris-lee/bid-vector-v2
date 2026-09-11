package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.CallBudget
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.FailureCode
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * scope.md ②, 설계 검토 우회 (12) — 2D `ContractDeadlineCancellationRetryTest`(fake 위)와
 * 같은 시나리오를 **실 어댑터 경로**(`GrpcBidPredictionGateway.predict`)로 재실행한다.
 * D-2D-7 관례를 따른다 — 취소 검증은 `cancelled()` 폴링이 아니라 servicer 가 실제로
 * `CancellationException`을 받았다는 카운터로 한다.
 */
class DeadlineCancellationRetryTest {
    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private class DelayedServicer(
        private val delayMillis: Long,
    ) : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
        val started = AtomicInteger(0)
        val cancelled = AtomicInteger(0)

        override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
            started.incrementAndGet()
            try {
                delay(delayMillis)
            } catch (cancellation: CancellationException) {
                cancelled.incrementAndGet()
                throw cancellation
            }
            return CalculateOptimalBidResponse
                .newBuilder()
                .also { it.failureBuilder.code = FailureCode.FAILURE_CODE_INVALID_REQUEST }
                .build()
        }

        override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
            error("이 test 는 GetModelMetadata 를 부르지 않는다")
    }

    private class FlakyServicer(
        private val failuresBeforeSuccess: Int,
    ) : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
        val attempts = AtomicInteger(0)

        override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
            val attempt = attempts.incrementAndGet()
            if (attempt <= failuresBeforeSuccess) {
                throw StatusException(Status.UNAVAILABLE.withDescription("attempt $attempt"))
            }
            return CalculateOptimalBidResponse
                .newBuilder()
                .also {
                    it.failureBuilder.code = FailureCode.FAILURE_CODE_INVALID_REQUEST
                }.build()
        }

        override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
            error("이 test 는 GetModelMetadata 를 부르지 않는다")
    }

    private fun gatewayOn(
        servicer: BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase,
        policyData: MlCallPolicyData,
    ): GrpcBidPredictionGateway {
        val serverName = "bidvector-4d1-deadline-${System.nanoTime()}"
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
    fun `짧은 예산은 DeadlineExceeded 로 어댑터를 통해 도달한다`() {
        runBlocking {
            val servicer = DelayedServicer(delayMillis = 2_000)
            val policy = testMlCallPolicy(deadlineCeiling = Duration.ofSeconds(5), maxAttempts = 1)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofMillis(50)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.DeadlineExceeded
        }
    }

    @Test
    fun `coroutine 취소가 predict 를 통해 servicer 의 계산 중단으로 이어진다`() {
        runBlocking {
            val servicer = DelayedServicer(delayMillis = 5_000)
            val policy = testMlCallPolicy(deadlineCeiling = Duration.ofSeconds(10), maxAttempts = 1)
            val gateway = gatewayOn(servicer, policy)

            val job =
                launch {
                    runCatching { gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(9))) }
                }
            while (servicer.started.get() == 0) {
                delay(5)
            }
            job.cancel()
            job.join()

            withTimeout(2_000) {
                while (servicer.cancelled.get() == 0) {
                    delay(5)
                }
            }
            servicer.cancelled.get() shouldBe 1
        }
    }

    @Test
    fun `UNAVAILABLE 은 상한 안에서 재시도해 InvalidRequest 로 끝난다(어댑터 경로)`() {
        runBlocking {
            val policy = testMlCallPolicy(maxAttempts = 3)
            val servicer = FlakyServicer(failuresBeforeSuccess = 2)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofSeconds(2)))

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.InvalidRequest
            servicer.attempts.get() shouldBe 3
        }
    }
}
