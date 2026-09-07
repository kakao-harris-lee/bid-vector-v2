package bidvector.adapters.contract

import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
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
 * `PredictionContractTest`·`TrainingContractTest`가 이미 상세히 지킨다.
 */
class MultiServiceContractTest {
    private val predictionTestdataRoot: Path = contractTestdataRoot("prediction")
    private val trainingTestdataRoot: Path = contractTestdataRoot("training")

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    private fun startSharedServer(): Pair<
        BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub,
        TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub,
    > {
        val serverName = "bidvector-2d-multi-service-${System.nanoTime()}"
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
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(predictionServicer)
                .addService(FakeTrainingJobServicer())
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel!!) to
            TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub(channel!!)
    }

    @Test
    fun `한 in-process 서버 위에서 두 서비스가 각자 정상 응답한다`() {
        runBlocking {
            val (predictionStub, trainingStub) = startSharedServer()

            val predictionRequest =
                CalculateOptimalBidRequest.parseFrom(
                    readTestdataBytes(predictionTestdataRoot, "calculate_optimal_bid_request.binpb"),
                )
            val predictionResponse = predictionStub.calculateOptimalBid(predictionRequest)
            predictionResponse.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.SUCCESS

            val trainingRequest =
                StartTrainingRequest.parseFrom(readTestdataBytes(trainingTestdataRoot, "start_training_request.binpb"))
            val trainingResponse = trainingStub.startTraining(trainingRequest)
            trainingResponse.resultCase shouldBe StartTrainingResponse.ResultCase.HANDLE
            trainingResponse.handle.state shouldBe JobState.JOB_STATE_ACCEPTED
        }
    }
}
