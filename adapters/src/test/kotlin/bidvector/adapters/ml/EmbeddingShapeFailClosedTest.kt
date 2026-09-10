package bidvector.adapters.ml

import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
import contract.bidvector.ml.v1.VectorNormalization
import io.grpc.ManagedChannel
import io.grpc.Server
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
import java.util.concurrent.atomic.AtomicReference
import contract.bidvector.ml.v1.EmbedTextRequest as ProtoEmbedTextRequest

/**
 * 검증층을 통과하지 못한 `Embedding`은 예외가 아니라 `Unavailable`로 접힌다 —
 * `EmbeddingVector.init`은 마지막 안전판이지 게이트가 아니다(`isAcceptableEmbeddingShape`가
 * 게이트, 4D-1 `SuccessShapeFailClosedTest`와 동형 패턴). 이 파일이 증명하는 것 셋:
 *
 * - values 개수가 dimension 과 다르면 `Unavailable(ContractViolation)`이다(우회 (1)).
 * - normalization 이 L2 가 아니거나 norm 이 1 을 epsilon 이상 벗어나면
 *   `Unavailable(ContractViolation)`이다(우회 (2)).
 * - release 다섯 성분 중 하나라도 공백이면 `Unavailable(ContractViolation)`이다(우회 (6),
 *   성분별 table-driven).
 */
class EmbeddingShapeFailClosedTest {
    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private fun gatewayOn(
        servicer: EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase,
        policyData: MlCallPolicyData = testEmbeddingCallPolicy(),
    ): GrpcEmbeddingGateway {
        val serverName = "bidvector-4d2-shape-${System.nanoTime()}"
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

    private val exactSelector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test")

    @Test
    fun `values 개수가 dimension 과 다르면 예외 없이 ContractViolation 이다`() {
        runBlocking {
            val mutated =
                testEmbeddingSuccess()
                    .toBuilder()
                    .also {
                        it.clearValues()
                        it.addAllValues(testUnitVector(3))
                    } // dimension 은 4 그대로
                    .build()
            val gateway = gatewayOn(fixedEmbeddingServicer(embedText = protoEmbedResponse(mutated)))

            val outcome =
                gateway.embed(testEmbedTextRequest(releaseSelector = exactSelector), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ContractViolation
        }
    }

    @Test
    fun `normalization 이 UNSPECIFIED 면 예외 없이 ContractViolation 이다`() {
        runBlocking {
            val mutated =
                testEmbeddingSuccess()
                    .toBuilder()
                    .setNormalization(VectorNormalization.VECTOR_NORMALIZATION_UNSPECIFIED)
                    .build()
            val gateway = gatewayOn(fixedEmbeddingServicer(embedText = protoEmbedResponse(mutated)))

            val outcome =
                gateway.embed(testEmbedTextRequest(releaseSelector = exactSelector), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ContractViolation
        }
    }

    @Test
    fun `norm 이 1 을 epsilon 이상 벗어나면 예외 없이 ContractViolation 이다`() {
        runBlocking {
            val scaled = testUnitVector(4).map { it * 2.0f }
            val mutated =
                testEmbeddingSuccess()
                    .toBuilder()
                    .also {
                        it.clearValues()
                        it.addAllValues(scaled)
                    }.build()
            val gateway = gatewayOn(fixedEmbeddingServicer(embedText = protoEmbedResponse(mutated)))

            val outcome =
                gateway.embed(testEmbedTextRequest(releaseSelector = exactSelector), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ContractViolation
        }
    }

    @Test
    fun `정확히 L2 정규화된 응답은 정상 Embedded 다(대비 표본)`() {
        runBlocking {
            val gateway = gatewayOn(fixedEmbeddingServicer(embedText = protoEmbedResponse(testEmbeddingSuccess())))

            val outcome =
                gateway.embed(testEmbedTextRequest(releaseSelector = exactSelector), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Embedded>()
        }
    }

    // ---- release 다섯 성분 공백(4D-1 SuccessShapeFailClosedTest 관례, table-driven) ----

    @Test
    fun `release 다섯 성분 중 하나라도 공백이면 예외 없이 접힌다(table-driven)`() {
        runBlocking {
            val currentResponse = AtomicReference<EmbedTextResponse>()
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse =
                        currentResponse.get() ?: error("이 test 는 매 case 마다 응답을 미리 심는다")

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
                }
            val gateway = gatewayOn(servicer)

            releaseBlankCases.forEach { case ->
                val mutated = testEmbeddingSuccess().toBuilder().also(case.mutate).build()
                currentResponse.set(protoEmbedResponse(mutated))

                val outcome =
                    gateway.embed(
                        testEmbedTextRequest(releaseSelector = exactSelector),
                        CallBudget(Duration.ofSeconds(1)),
                    )

                withClue(case.description) {
                    outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
                    outcome.reason shouldBe case.expectedReason
                }
            }
        }
    }
}

private data class ReleaseBlankCase(
    val description: String,
    val mutate: (contract.bidvector.ml.v1.Embedding.Builder) -> Unit,
    val expectedReason: EmbeddingUnavailableReason,
)

/**
 * `releaseId`·`artifactChecksum`은 `exact_release` 대조(`releaseSatisfiesSelector`)가
 * `mapEmbedSuccess`보다 먼저 걸려 `ContractViolation`이 아니라 `ReleaseMismatch`다(4D-1
 * `SuccessShapeFailClosedTest` H-6 관례와 같은 순서 — 둘 다 `Unavailable`이고 예외가
 * 아니므로 짝이 없는 것은 아니다, 검증층이 다른 사유로 먼저 접었을 뿐).
 */
private val releaseBlankCases =
    listOf(
        ReleaseBlankCase(
            "releaseId 공백(exact_release 대조가 먼저 걸린다)",
            { b -> b.releaseBuilder.releaseId = "" },
            EmbeddingUnavailableReason.ReleaseMismatch,
        ),
        ReleaseBlankCase(
            "artifactChecksum 공백(exact_release 대조가 먼저 걸린다)",
            { b -> b.releaseBuilder.artifactChecksum = "" },
            EmbeddingUnavailableReason.ReleaseMismatch,
        ),
        ReleaseBlankCase(
            "featureSchemaVersion 공백",
            { b -> b.releaseBuilder.featureSchemaVersion = "" },
            EmbeddingUnavailableReason.ContractViolation,
        ),
        ReleaseBlankCase(
            "codeVersion 공백",
            { b -> b.releaseBuilder.codeVersion = "" },
            EmbeddingUnavailableReason.ContractViolation,
        ),
        ReleaseBlankCase(
            "datasetId 공백",
            { b -> b.releaseBuilder.datasetId = "" },
            EmbeddingUnavailableReason.ContractViolation,
        ),
    )
