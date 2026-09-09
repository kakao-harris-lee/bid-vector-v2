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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * verifier r2(`_workspace/m4-4d/05_verifier_report_r2.md`) G-1·G-2·G-4·G-5 재현·회귀 방지 —
 * `GrpcBidPredictionGatewayTest`에서 size ratchet(v2-지침서 §5, 500줄)으로 갈라낸 파일이다
 * (`CandidateShapeValidation.kt`를 `ParsedSuccessFields.kt`에서 가른 것과 같은 사유).
 *
 * **G-1(high)** — `BidRateCandidates.init`(conservative≤base≤aggressive) 위반이 예외로
 * `predict` 밖까지 새면 안 된다. `isAcceptableSuccessShape`(구조 검증층)가 먼저 잡아
 * `Unavailable(ContractViolation)`을 내야 한다(F-2 `hasNonBlankRelease`와 동형 패턴).
 * **G-2(high)** — `PriceFitness` 부호는 계약 근거가 없다(proto 주석 「값의 산식은 이
 * 계약이 규정하지 않는다」) — 정직한 음수 적합도가 값으로 접히지 않고 `Predicted`로
 * 통과해야 한다. **G-4(medium)** — 호출부 예산 부족(합성 DeadlineExceeded)은 breaker
 * 계수 밖이어야 한다. **G-5(low)** — release 불일치와 schema 불일치가 동시에 있으면
 * `releaseSatisfiesSelector`가 `mapSuccess`보다 먼저 걸려 `ReleaseMismatch`가 이긴다.
 */
class GrpcBidPredictionGatewayVerifierR2Test {
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
        val serverName = "bidvector-4d1-gateway-r2-${System.nanoTime()}"
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

    private val exactSelector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test")

    // ---- verifier r2 G-5(low) — release 불일치와 schema 불일치가 동시에 있으면
    // ReleaseMismatch 가 이긴다(handleSuccess 가 releaseSatisfiesSelector 를 mapSuccess 보다
    // 먼저 부르는 구조적 순서, 우회 후보 4/5 판정 실측 재확인). ----

    @Test
    fun `release 불일치와 schema 불일치가 동시에 있으면 ReleaseMismatch 가 이긴다(G-5)`() {
        runBlocking {
            val mismatchedRelease = testModelRelease(releaseId = "other-release", artifactChecksum = "sha256:test")
            val mutated =
                testSuccessResponse()
                    .toBuilder()
                    .setRelease(mismatchedRelease)
                    .also { it.releaseBuilder.featureSchemaVersion = "bidvector.ml.v2-unexpected" }
                    .build()
            val gateway = gatewayOn(fixedServicer(calculate = protoResponse(mutated)))

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.ReleaseMismatch
        }
    }

    // ---- verifier r2 G-1(high) — 값 타입 init 위반이 실 gateway 경로에서 예외로 새면
    // 안 된다. isAcceptableSuccessShape(구조 검증층)가 BidRateCandidates.init 조건
    // (conservative≤base≤aggressive)을 먼저 걸러야 한다(F-2 의 release 패턴과 동형). ----

    @Test
    fun `후보 순서가 내림차순이면 예외 없이 ContractViolation 이다(G-1)`() {
        runBlocking {
            val descending =
                testSuccessResponse()
                    .toBuilder()
                    .also {
                        it.getCandidatesBuilder(0).bidRateBuilder.fraction = "0.9800"
                        it.getCandidatesBuilder(2).bidRateBuilder.fraction = "0.9000"
                    }.build()
            val gateway = gatewayOn(fixedServicer(calculate = protoResponse(descending)))

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.ContractViolation
        }
    }

    @Test
    fun `base 가 aggressive 보다 크면 예외 없이 ContractViolation 이다(G-1)`() {
        runBlocking {
            val mutated =
                testSuccessResponse()
                    .toBuilder()
                    .also { it.getCandidatesBuilder(1).bidRateBuilder.fraction = "0.9700" }
                    .build()
            val gateway = gatewayOn(fixedServicer(calculate = protoResponse(mutated)))

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe MlUnavailableReason.ContractViolation
        }
    }

    @Test
    fun `세 후보가 동일한 값(클램프 포화)이면 정상 Predicted 다(G-1 대비 — 상한 아닌 하한 이하)`() {
        runBlocking {
            val clamped =
                testSuccessResponse()
                    .toBuilder()
                    .also {
                        it.getCandidatesBuilder(0).bidRateBuilder.fraction = "0.9200"
                        it.getCandidatesBuilder(2).bidRateBuilder.fraction = "0.9200"
                    }.build()
            val gateway = gatewayOn(fixedServicer(calculate = protoResponse(clamped)))

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        }
    }

    // ---- verifier r2 G-2(high) — PriceFitness 부호는 계약 근거가 없다. 정직한 음수
    // 적합도는 값으로 접지 않고 그대로 Predicted 로 통과해야 한다. ----

    @Test
    fun `음수 fitness 는 정직한 값으로 그대로 Predicted 가 된다(G-2)`() {
        runBlocking {
            val negativeFitness =
                testSuccessResponse()
                    .toBuilder()
                    .also { it.fitnessBuilder.score = "-0.3000" }
                    .build()
            val gateway = gatewayOn(fixedServicer(calculate = protoResponse(negativeFitness)))

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
            outcome.fitness.score.signum() shouldBe -1
        }
    }

    // ---- verifier r2 G-4(medium) — 호출부 예산 부족(합성 DeadlineExceeded)은 breaker 계수
    // 밖이어야 한다. 서버는 건강(1회 실패 뒤 재시도 성공)한데 예산만 짧은 호출을 반복해도
    // breaker 가 열리면 안 되고, 뒤이은 넉넉한 예산 호출이 서버에 닿아야 한다. ----

    @Test
    fun `예산 부족이 반복돼도 breaker 는 열리지 않고 이어진 넉넉한 예산 호출은 서버에 닿는다(G-4)`() {
        runBlocking {
            // calls 는 이 servicer 전체(budget 부족 predict 둘 + 마지막 healthy predict)를
            // 관통하는 전역 카운터다 — budget 부족 호출은 backoff 를 못 감당해 attempt 1회로
            // 끝나므로 처음 2회(calls 1~2)가 그 둘에 정확히 대응한다. breakerSlidingWindowSize=2
            // 라 이 둘이 breaker onError 로 잡히면 즉시 open 된다(minimumNumberOfCalls=창 크기).
            // 3번째 호출부터는 healthy predict 의 attempt 라 성공으로 고정한다.
            val calls = AtomicInteger(0)
            val servicer =
                object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                    override suspend fun calculateOptimalBid(
                        request: CalculateOptimalBidRequest,
                    ): CalculateOptimalBidResponse {
                        val attempt = calls.incrementAndGet()
                        if (attempt <= 2) throw StatusException(Status.UNAVAILABLE)
                        return protoResponse(testSuccessResponse())
                    }

                    override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                        error("이 test 는 GetModelMetadata 를 부르지 않는다")
                }
            val policy =
                testMlCallPolicy(
                    maxAttempts = 2,
                    backoff = listOf(Duration.ofMillis(300)),
                    breakerSlidingWindowSize = 2,
                )
            val gateway = gatewayOn(servicer, policy)

            // 예산이 짧아 backoff(300ms)를 못 감당 — 매번 1회 실패 뒤 재시도 없이 DeadlineExceeded.
            repeat(2) {
                val outcome = gateway.predict(testBidPredictionRequest(), CallBudget(Duration.ofMillis(80)))
                outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
                outcome.reason shouldBe MlUnavailableReason.DeadlineExceeded
            }

            // 넉넉한 예산 — 실패 뒤 재시도가 서버에 닿아 성공해야 한다. breaker 가 앞선
            // 예산 부족 둘을 오류로 셌다면(window 2 라 즉시 open) 여기서 CircuitOpen 이 난다.
            val healthyOutcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = exactSelector),
                    CallBudget(Duration.ofSeconds(5)),
                )

            healthyOutcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        }
    }
}
