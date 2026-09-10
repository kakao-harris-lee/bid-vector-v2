package bidvector.adapters.ml

import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
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
import contract.bidvector.ml.v1.EmbedTextRequest as ProtoEmbedTextRequest

/**
 * scope.md ③, 우회 (7) — breaker open 이면 호출 없이 `Unavailable(CircuitOpen)`. permit
 * 결말 강제(`settlePermit`, verifier r3 H-1)는 `callResilient`가 예측·임베딩 공유로
 * 제네릭화됐으므로(D-4D2-4) 그 메커니즘 자체는 4D-1 `BreakerTest`가 이미 전건 증명한다 —
 * 이 파일은 **임베딩 경로가 같은 공유 메커니즘에 올바르게 배선됐는지**만 최소로 확인한다
 * (H-1 의 15회 HALF_OPEN 반복까지 재현하지 않는다 — 로직이 아니라 배선의 재확인이라
 * 중복 커버리지를 피한다, cpdCheck 의도와도 같은 방향).
 */
class EmbeddingBreakerTest {
    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private fun gatewayOn(
        servicer: EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase,
        policyData: MlCallPolicyData,
    ): GrpcEmbeddingGateway {
        val serverName = "bidvector-4d2-breaker-${System.nanoTime()}"
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(servicer)
                .build()
                .start()
        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        this.channel = channel
        return GrpcEmbeddingGateway(channel, testEmbeddingCallEffectivePolicy(policyData), Clock.systemUTC())
    }

    @Test
    fun `연속 실패로 breaker 가 열리면 이후 호출은 servicer 를 부르지 않고 CircuitOpen 이다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
                        calls.incrementAndGet()
                        throw StatusException(Status.UNAVAILABLE)
                    }

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
                }
            val policy =
                testEmbeddingCallPolicy(
                    maxAttempts = 1,
                    breakerSlidingWindowSize = 2,
                    breakerFailureRateThresholdPercent = 50,
                    breakerWaitDurationInOpenState = Duration.ofSeconds(60),
                )
            val gateway = gatewayOn(servicer, policy)

            val first = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))
            val second = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))
            first.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            first.reason shouldBe EmbeddingUnavailableReason.RetryBudgetExhausted
            second.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            calls.get() shouldBe 2

            val third = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            third.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            third.reason shouldBe EmbeddingUnavailableReason.CircuitOpen
            calls.get() shouldBe 2
        }
    }

    /**
     * 예산 소진(`MlCallOutcome.BudgetExhausted`)이 permit 을 반납하지 않으면 HALF_OPEN 에서
     * breaker 가 영구히 막힌다(4D-1 verifier r3 H-1) — `settlePermit`은 공유 코드이므로 이
     * test 는 **한 번**의 예산 소진 뒤 permit 이 반납돼 바로 다음 호출이 서버에 닿는지만
     * 확인한다(전건 재현은 4D-1 `BreakerTest`가 진다).
     */
    @Test
    fun `예산 소진 호출 뒤 permit 이 반납돼 이후 호출이 서버에 닿는다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
                        calls.incrementAndGet()
                        return protoEmbedResponse(testEmbeddingSuccess())
                    }

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
                }
            val policy = testEmbeddingCallPolicy(maxAttempts = 2, backoff = listOf(Duration.ofSeconds(10)))
            val gateway = gatewayOn(servicer, policy)

            // 예산이 backoff(10초)를 감당 못 해 1회 시도 뒤 DeadlineExceeded 로 끝난다(서버는
            // 응답했으므로 calls 는 1).
            val budgetExhausted = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofMillis(50)))
            budgetExhausted.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()

            val exactSelector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test")
            val healthy =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(5)),
                )

            healthy.shouldBeInstanceOf<EmbeddingOutcome.Embedded>()
            (calls.get() >= 2) shouldBe true
        }
    }
}
