package bidvector.adapters.e2e

import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import contract.bidvector.ml.v1.ModelMetadata
import contract.bidvector.ml.v1.ModelRelease
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import contract.bidvector.ml.v1.EmbedTextRequest as ProtoEmbedTextRequest

/**
 * E2E 가 쓰는 in-process ML 대역(운영자 결정 C-1 (a)) — **실 컨테이너를 띄우지 않는다**
 * (`RealServerIntegrationTest` 가 그 축을 이미 갖는다, 설계 검토 (3) 과잉 후보). 예측·임베딩
 * 두 서비스를 **같은 서버**에 등록한다(ADR 0010 D-8 배선, `MultiServiceContractTest` 선례).
 *
 * 이 대역이 가리는 것은 **ML 서버**뿐이다 — `GrpcBidPredictionGateway`·`GrpcEmbeddingGateway`
 * 는 production 클래스 그대로 이 채널 위에 선다. 그래서 deadline·재시도·release 대조·
 * 응답 모양 검사가 전부 실제 코드 경로를 지난다.
 *
 * [MlFakeScript.predictionDelay] 는 **정책에서 도출한 값**을 받는다(설계 검토 (2) 우회 1 —
 * 손 상수로 「지연이 예산을 넘는다」를 주장하지 않는다). 지연 0 인 대조 run 이 같은 배선에서
 * 성공하는지를 같은 test 가 함께 잰다.
 */
internal class MlFakeScript(
    val predictionResponse: CalculateOptimalBidResponse,
    val predictionMetadata: GetModelMetadataResponse,
    val embeddingResponse: EmbedTextResponse,
    val embeddingMetadata: GetEmbeddingMetadataResponse,
    val predictionDelay: Duration = Duration.ZERO,
)

/** 예측 응답을 요청마다 고르는 자리 — rollback 축(EXACT 선택자)이 release 별로 다른 응답을 낸다. */
internal fun interface PredictionResponder {
    fun responseFor(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse
}

/**
 * 임베딩 응답을 요청 텍스트마다 고르는 자리 — 사다리 음성 대조가 **특정 공고에만** 직교 벡터를
 * 돌려줘 그 후보의 match 점수를 떨어뜨린다(공고별 priority 를 가르는 유일한 축).
 */
internal fun interface EmbeddingResponder {
    fun responseFor(request: ProtoEmbedTextRequest): EmbedTextResponse
}

internal class MlFakeServer private constructor(
    private val server: Server,
    val channel: ManagedChannel,
    private val predictionCalls: AtomicInteger,
) : AutoCloseable {
    /** 서버가 스스로 센 호출 횟수 — 「실제로 불렸는가」의 정본(`MockKonepsServer.requestCount` 관례). */
    fun predictionCallCount(): Int = predictionCalls.get()

    /** 종료를 기다린다(review L-4) — 닫지 않은 서버가 다음 test 로 새지 않게 한다. */
    override fun close() {
        channel.shutdownNow()
        server.shutdownNow()
        server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        fun start(
            script: MlFakeScript,
            responder: PredictionResponder = PredictionResponder { script.predictionResponse },
            embeddingResponder: EmbeddingResponder = EmbeddingResponder { script.embeddingResponse },
        ): MlFakeServer {
            val predictionCalls = AtomicInteger(0)
            val name = "bidvector-6d1-ml-${System.nanoTime()}"
            val server =
                InProcessServerBuilder
                    .forName(name)
                    .addService(ScriptedPredictionServicer(script, responder, predictionCalls))
                    .addService(ScriptedEmbeddingServicer(script, embeddingResponder))
                    .build()
                    .start()
            val channel = InProcessChannelBuilder.forName(name).build()
            return MlFakeServer(server, channel, predictionCalls)
        }
    }
}

private class ScriptedPredictionServicer(
    private val script: MlFakeScript,
    private val responder: PredictionResponder,
    private val calls: AtomicInteger,
) : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
    override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
        calls.incrementAndGet()
        delayFor(script.predictionDelay)
        return responder.responseFor(request)
    }

    override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
        script.predictionMetadata
}

private class ScriptedEmbeddingServicer(
    private val script: MlFakeScript,
    private val responder: EmbeddingResponder,
) : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse = responder.responseFor(request)

    override suspend fun getEmbeddingMetadata(request: GetEmbeddingMetadataRequest): GetEmbeddingMetadataResponse =
        script.embeddingMetadata
}

/** 지연은 스레드를 막지 않는다 — `delay` 가 코루틴만 멈추므로 gRPC deadline 이 정상 동작한다. */
private suspend fun delayFor(duration: Duration) {
    if (!duration.isZero) delay(duration.toMillis())
}

private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

internal fun predictionMetadataOf(promoted: ModelRelease): GetModelMetadataResponse =
    GetModelMetadataResponse
        .newBuilder()
        .setMetadata(ModelMetadata.newBuilder().setPromoted(promoted).build())
        .build()
