package bidvector.adapters.contract

import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.JobState
import contract.bidvector.ml.v1.StartTrainingRequest
import contract.bidvector.ml.v1.StartTrainingResponse
import contract.bidvector.ml.v1.TrainingJobServiceGrpcKt
import io.grpc.ManagedChannelBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * M2/2D S-6 — 교차 언어 socket 스모크(D-2D-3 (a), **상시 게이트가 아니다** — `tools/
 * contract-crosslang-smoke.sh`가 Python 서버를 띄운 뒤 이 test class 만 골라 돈다).
 * `adapters/build.gradle.kts`의 일반 `test` task 는 이 class 를 이름으로 **제외**한다 —
 * Python 서버가 없는 보통의 `check` 실행에서는 절대 돌지 않는다.
 *
 * `bidvector.crosslang.address`(시스템 속성, 스크립트가 넘긴다)로 실제 TCP 소켓에 연결해
 * 2B·2C 를 한 번씩 부른다 — "요청만으로 Python 이 DB 조회 없이 계산 가능"의 형태 증명
 * (fake 는 DB 를 갖지 않는다, scope.md ⑧).
 */
class CrossLangSmokeTest {
    private val predictionTestdataRoot: Path = contractTestdataRoot("prediction")
    private val trainingTestdataRoot: Path = contractTestdataRoot("training")

    @Test
    fun `Python 서버에 실제 소켓으로 2B·2C 를 한 번씩 부른다`() {
        val address =
            System.getProperty("bidvector.crosslang.address")
                ?: error("시스템 속성 'bidvector.crosslang.address' 가 없다 — 스크립트가 넘긴다")
        val channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build()
        try {
            runBlocking {
                val predictionStub = BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel)
                val predictionRequest =
                    CalculateOptimalBidRequest.parseFrom(
                        readTestdataBytes(predictionTestdataRoot, "calculate_optimal_bid_request.binpb"),
                    )
                val predictionResponse = predictionStub.calculateOptimalBid(predictionRequest)
                predictionResponse.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.SUCCESS

                val trainingStub = TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub(channel)
                val trainingRequest =
                    StartTrainingRequest.parseFrom(
                        readTestdataBytes(trainingTestdataRoot, "start_training_request.binpb"),
                    )
                val trainingResponse = trainingStub.startTraining(trainingRequest)
                trainingResponse.resultCase shouldBe StartTrainingResponse.ResultCase.HANDLE
                trainingResponse.handle.state shouldBe JobState.JOB_STATE_ACCEPTED
            }
        } finally {
            channel.shutdownNow()
        }
    }
}
