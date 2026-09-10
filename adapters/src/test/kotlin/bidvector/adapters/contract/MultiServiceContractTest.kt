package bidvector.adapters.contract

import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.EmbedTextRequest
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import contract.bidvector.ml.v1.JobState
import contract.bidvector.ml.v1.StartTrainingRequest
import contract.bidvector.ml.v1.StartTrainingResponse
import contract.bidvector.ml.v1.TrainingJobServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * M2/2D ⑧ — 2B(`BidPredictionService`)·2C(`TrainingJobService`) fake servicer 를 **같은
 * in-process 서버**에 함께 얹어 한 suite 로 묶는다(ADR 0010 D-8 "별도 job API, 같은 gRPC
 * 서버"의 실제 배선 가능성 증명 — 서비스 이름 충돌·등록 순서 문제가 있었다면 이 test 의
 * 서버 기동 자체가 실패했을 것이다). 각 서비스 RPC 를 한 번씩 부른다 — 개별 계약 불변식은
 * `PredictionContractTest`·`TrainingContractTest`·`EmbeddingContractTest`가 이미 상세히
 * 지킨다.
 *
 * **M2/2E — 넷째 서비스(`EmbeddingService`)를 더한다**(설계 검토 (4) 우회 (8) — 이 test 가
 * 넷째를 안 넣으면 stub 이름 충돌이 안 잡힌다). D-2E-2(별도 서비스, release 축·readiness가
 * `BidPredictionService`와 다르다)의 배선 가능성 증명이기도 하다.
 */
class MultiServiceContractTest {
    private val predictionTestdataRoot: Path = contractTestdataRoot("prediction")
    private val trainingTestdataRoot: Path = contractTestdataRoot("training")
    private val embeddingTestdataRoot: Path = contractTestdataRoot("embedding")

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private data class Stubs(
        val prediction: BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub,
        val training: TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub,
        val embedding: EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineStub,
    )

    private fun startSharedServer(): Stubs {
        val serverName = "bidvector-2e-multi-service-${System.nanoTime()}"
        val predictionServicer =
            object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                override suspend fun calculateOptimalBid(
                    request: CalculateOptimalBidRequest,
                ): CalculateOptimalBidResponse =
                    CalculateOptimalBidResponse.parseFrom(
                        readTestdataBytes(predictionTestdataRoot, "calculate_optimal_bid_response_success.binpb"),
                    )

                override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                    error("이 test 는 GetModelMetadata 를 부르지 않는다")
            }
        val embeddingServicer =
            object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                override suspend fun embedText(request: EmbedTextRequest): EmbedTextResponse =
                    EmbedTextResponse.parseFrom(
                        readTestdataBytes(embeddingTestdataRoot, "embed_text_response_success.binpb"),
                    )

                override suspend fun getEmbeddingMetadata(
                    request: GetEmbeddingMetadataRequest,
                ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
            }
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(predictionServicer)
                .addService(FakeTrainingJobServicer())
                .addService(embeddingServicer)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return Stubs(
            prediction = BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel!!),
            training = TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub(channel!!),
            embedding = EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineStub(channel!!),
        )
    }

    @Test
    fun `한 in-process 서버 위에서 세 서비스가 각자 정상 응답한다`() {
        runBlocking {
            val stubs = startSharedServer()

            val predictionRequest =
                CalculateOptimalBidRequest.parseFrom(
                    readTestdataBytes(predictionTestdataRoot, "calculate_optimal_bid_request.binpb"),
                )
            val predictionResponse = stubs.prediction.calculateOptimalBid(predictionRequest)
            predictionResponse.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.SUCCESS

            val trainingRequest =
                StartTrainingRequest.parseFrom(readTestdataBytes(trainingTestdataRoot, "start_training_request.binpb"))
            val trainingResponse = stubs.training.startTraining(trainingRequest)
            trainingResponse.resultCase shouldBe StartTrainingResponse.ResultCase.HANDLE
            trainingResponse.handle.state shouldBe JobState.JOB_STATE_ACCEPTED

            val embeddingRequest =
                EmbedTextRequest.parseFrom(readTestdataBytes(embeddingTestdataRoot, "embed_text_request.binpb"))
            val embeddingResponse = stubs.embedding.embedText(embeddingRequest)
            embeddingResponse.resultCase shouldBe EmbedTextResponse.ResultCase.SUCCESS
        }
    }
}
