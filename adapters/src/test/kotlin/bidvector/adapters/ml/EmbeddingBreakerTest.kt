package bidvector.adapters.ml

import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.FailureCode
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
 * scope.md ③, breaker open 축(리뷰 F-D 정정 — 이전엔 「우회 (7)」로 적었으나 이 slice의
 * 우회 목록에 (7)은 없다. 번호 대신 문구로 가리킨다) — breaker open 이면 호출 없이
 * `Unavailable(CircuitOpen)`. permit
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
     *
     * **리뷰 F-A(high) 처방** — 이전 형태는 서버가 **성공**을 돌려주고 `BudgetExhausted`
     * 자체는 **transport deadline 이 벽시계 안에 실제로 만료되는지**에 기댔다(cold JVM 에서
     * in-process RPC 가 50ms 안에 못 닿으면 `calls=1`로 끝나 단언이 깨졌다 — 통과하는
     * 분기에서는 반대로 예산 소진 경로 자체를 지나지 않았다). 처방은 **결과 내용으로**
     * 재시도 가능 경로에 진입시키는 것이다 — 첫 호출에 `retryable=true` application failure
     * 를 응답으로 심으면 `isRetryableApplicationFailure`(순수 판정, 시각 무관)가 즉시 backoff
     * 구간을 연다. 예산(1초)은 backoff(10초)를 감당 못 하므로 **재시도 없이**
     * `BudgetExhausted`로 접힌다 — 이 판단은 실제 RPC 완료 여부에 좌우되지 않는다(RPC 자체는
     * in-process direct executor라 1초 예산 안에서 항상 끝난다, cold JVM 실측 3연속 확인).
     */
    @Test
    fun `예산 소진 호출 뒤 permit 이 반납돼 이후 호출이 서버에 닿는다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
                        val callNumber = calls.incrementAndGet()
                        return if (callNumber == 1) {
                            embeddingFailureResponse(FailureCode.FAILURE_CODE_MODEL_NOT_READY, retryable = true)
                        } else {
                            protoEmbedResponse(testEmbeddingSuccess())
                        }
                    }

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
                }
            val policy = testEmbeddingCallPolicy(maxAttempts = 2, backoff = listOf(Duration.ofSeconds(10)))
            val gateway = gatewayOn(servicer, policy)

            val budgetExhausted = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))
            budgetExhausted.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            budgetExhausted.reason shouldBe EmbeddingUnavailableReason.DeadlineExceeded
            calls.get() shouldBe 1

            val exactSelector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test")
            val healthy =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(5)),
                )

            healthy.shouldBeInstanceOf<EmbeddingOutcome.Embedded>()
            calls.get() shouldBe 2
        }
    }
}
