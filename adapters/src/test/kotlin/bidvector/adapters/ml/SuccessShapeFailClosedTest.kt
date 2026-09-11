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
import contract.bidvector.ml.v1.Success
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 검증층을 통과하지 못한 `Success` 는 예외가 아니라 `Unavailable`로 접힌다 — 값 타입
 * `init`은 마지막 안전판이지 게이트가 아니다(`isAcceptableSuccessShape`가 게이트,
 * verifier r2 G-1·F-2 `hasNonBlankRelease`와 동형 패턴). `GrpcBidPredictionGatewayTest`
 * 에서 size ratchet(v2-지침서 §5, 500줄)으로 갈라낸 파일이다(`CandidateShapeValidation.kt`
 * 를 `ParsedSuccessFields.kt`에서 가른 것과 같은 사유). 이 파일이 증명하는 것 넷:
 *
 * - 후보 순서(conservative≤base≤aggressive) 위반은 `BidRateCandidates.init`이 아니라
 *   구조 검증층이 먼저 잡아 `Unavailable(ContractViolation)`을 낸다(r2 G-1).
 * - `PriceFitness` 부호는 계약 근거가 없다(proto 주석 「값의 산식은 이 계약이 규정하지
 *   않는다」) — 정직한 음수 적합도가 값으로 접히지 않고 `Predicted`로 통과한다(r2 G-2).
 * - 호출부 예산 부족(합성 DeadlineExceeded)은 breaker 계수 밖이다 — 서버는 건강한데
 *   예산만 짧은 호출을 반복해도 breaker 가 열리지 않고, 뒤이은 넉넉한 예산 호출이
 *   서버에 닿는다(r2 G-4 — HALF_OPEN 의 permit 계수는 `BreakerTest`가 잰다).
 * - release 불일치와 schema 불일치가 동시에 있으면 `releaseSatisfiesSelector`가
 *   `mapSuccess`보다 먼저 걸려 `ReleaseMismatch`가 이긴다(r2 G-5).
 * - 값 타입마다 `init`이 던지는 조건과 검증층 술어가 짝을 이룬다 — table-driven 으로
 *   전수 대조한다(r3 H-6, 짝 없는 조건이 생기면 이 test 가 떨어진다).
 */
class SuccessShapeFailClosedTest {
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

    // ---- verifier r3 H-6(low) — init 조건 ↔ 검증층 술어 짝을 손대조가 아니라 구조 test 로
    // 고정한다. 값 타입마다 init 이 던지는 조건을 표(입력 → 기대)로 열거하고, 같은 입력을
    // 실 gateway 경로에 넣어 예외 없이 기대한 Unavailable 사유로 접히는지 대조한다 — 새
    // init 조건이 검증층 술어 없이 추가되면(F-5→G-1 이 두 번째로 난 그 클래스) 이 test 가
    // 떨어진다. PriceFitness 는 G-2 로 init 자체가 없어져 이 표의 대상이 아니다. ----

    @Test
    fun `init 이 던지는 조건마다 검증층이 먼저 걸려 예외 없이 접힌다(H-6, table-driven)`() {
        runBlocking {
            val currentResponse = AtomicReference<CalculateOptimalBidResponse>()
            val servicer =
                object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                    override suspend fun calculateOptimalBid(
                        request: CalculateOptimalBidRequest,
                    ): CalculateOptimalBidResponse = currentResponse.get() ?: error("이 test 는 매 case 마다 응답을 미리 심는다")

                    override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                        error("이 test 는 GetModelMetadata 를 부르지 않는다")
                }
            val gateway = gatewayOn(servicer)

            shapeInvariantCases.forEach { case ->
                val mutated = testSuccessResponse().toBuilder().also(case.mutate).build()
                currentResponse.set(protoResponse(mutated))

                val outcome =
                    gateway.predict(
                        testBidPredictionRequest(releaseSelector = exactSelector),
                        CallBudget(Duration.ofSeconds(1)),
                    )

                withClue(case.description) {
                    outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
                    outcome.reason shouldBe case.expectedReason
                }
            }
        }
    }
}

private data class ShapeInvariantCase(
    val description: String,
    val mutate: (Success.Builder) -> Unit,
    val expectedReason: MlUnavailableReason,
)

/**
 * r3 verifier §3 의 「init 조건 ↔ 검증층 짝 대조」표를 그대로 옮긴 것 — `BidRateCandidates`
 * 두 부등식(각각 단독 위반)·`Uncertainty.sampleSize`·`ModelReleaseRef` 다섯 성분. release
 * 성분 중 `releaseId`·`artifactChecksum`은 `exact_release` 대조가 `mapSuccess`보다 먼저
 * 걸려(G-5 의 순서) `ContractViolation`이 아니라 `ReleaseMismatch`다 — 둘 다 `Unavailable`
 * 이고 예외가 아니므로 짝이 없는 것은 아니다(검증층이 다른 사유로 먼저 접었을 뿐).
 */
private val shapeInvariantCases =
    listOf(
        ShapeInvariantCase(
            "BidRateCandidates: conservative > base(첫 쌍만 역전)",
            { b -> b.getCandidatesBuilder(0).bidRateBuilder.fraction = "0.9300" },
            MlUnavailableReason.ContractViolation,
        ),
        ShapeInvariantCase(
            "BidRateCandidates: base > aggressive(끝 쌍만 역전)",
            { b -> b.getCandidatesBuilder(2).bidRateBuilder.fraction = "0.9000" },
            MlUnavailableReason.ContractViolation,
        ),
        ShapeInvariantCase(
            "Uncertainty: sampleSize=0",
            { b -> b.uncertaintyBuilder.sampleSize = 0 },
            MlUnavailableReason.ContractViolation,
        ),
        ShapeInvariantCase(
            "ModelReleaseRef: releaseId 공백(exact_release 대조가 먼저 걸린다)",
            { b -> b.releaseBuilder.releaseId = "" },
            MlUnavailableReason.ReleaseMismatch,
        ),
        ShapeInvariantCase(
            "ModelReleaseRef: artifactChecksum 공백(exact_release 대조가 먼저 걸린다)",
            { b -> b.releaseBuilder.artifactChecksum = "" },
            MlUnavailableReason.ReleaseMismatch,
        ),
        ShapeInvariantCase(
            "ModelReleaseRef: featureSchemaVersion 공백",
            { b -> b.releaseBuilder.featureSchemaVersion = "" },
            MlUnavailableReason.ContractViolation,
        ),
        ShapeInvariantCase(
            "ModelReleaseRef: codeVersion 공백",
            { b -> b.releaseBuilder.codeVersion = "" },
            MlUnavailableReason.ContractViolation,
        ),
        ShapeInvariantCase(
            "ModelReleaseRef: datasetId 공백",
            { b -> b.releaseBuilder.datasetId = "" },
            MlUnavailableReason.ContractViolation,
        ),
    )
