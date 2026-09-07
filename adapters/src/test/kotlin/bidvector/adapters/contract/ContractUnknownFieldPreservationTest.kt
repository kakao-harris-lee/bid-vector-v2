package bidvector.adapters.contract

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path

/**
 * M2/2D ④(a) — 정의 밖 필드 번호가 든 바이트를 **실제 in-process RPC 왕복**(client stub 의
 * serialize → wire → server 의 parse) 뒤에도 파싱 결과가 unknown field 를 보존하는지, 그리고
 * 그 unknown field 가 **응답(다른 메시지 타입)으로 되돌아오지 않는지** 증명한다. ④(b)(정의
 * 밖 enum 정수 거부)는 2A~2C 의 기존 test(`ContractRoundTripTest`·`PredictionContractTest`·
 * `TrainingContractTest`)가 이미 여러 필드에서 증명했으므로 여기서 되풀이하지 않는다.
 *
 * client 쪽이 `CalculateOptimalBidRequest.parseFrom(bytesWithUnknown)`로 만든 객체를
 * 그대로 stub 에 넘기면, coroutine stub 이 그 객체를 다시 직렬화해 in-process 채널로 보내고
 * server 가 그 바이트를 또 파싱한다 — 이 두 번째 파싱(진짜 RPC 경계를 넘은 뒤)에서도
 * unknown field 가 살아있어야 proto3 전방 호환이 성립한다(fake servicer 가 그 사실을
 * 응답에 실어 증명한다).
 */
class ContractUnknownFieldPreservationTest {
    private val testdataRoot: Path = contractTestdataRoot("prediction")

    private fun bytes(name: String): ByteArray = readTestdataBytes(testdataRoot, name)

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    /** 정의 밖 필드 번호 999(varint wire type)를 유효한 메시지 바이트 뒤에 이어 붙인다. */
    private fun appendUnknownVarintField(
        original: ByteArray,
        fieldNumber: Int,
        value: Long,
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        buffer.write(original)
        val coded = CodedOutputStream.newInstance(buffer)
        coded.writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT)
        coded.writeUInt64NoTag(value)
        coded.flush()
        return buffer.toByteArray()
    }

    /** unknown field 개수를 응답 `detail_code`에 실어 증명하는 fake servicer(테스트 전용). */
    private fun stubObservingUnknownFields(): BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub {
        val serverName = "bidvector-2d-unknown-field-${System.nanoTime()}"
        val servicer =
            object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                override suspend fun calculateOptimalBid(
                    request: CalculateOptimalBidRequest,
                ): CalculateOptimalBidResponse {
                    val unknownCount = request.unknownFields.asMap().size
                    return CalculateOptimalBidResponse
                        .newBuilder()
                        .also {
                            it.failureBuilder
                                .setCode(contract.bidvector.ml.v1.FailureCode.FAILURE_CODE_INVALID_REQUEST)
                                .setDetailCode("observed-unknown-field-count=$unknownCount")
                        }.build()
                }

                override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                    error("이 test 는 GetModelMetadata 를 부르지 않는다")
            }
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(servicer)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel!!)
    }

    @Test
    fun `정의 밖 필드는 parse 후 unknownFields 에 보존된다`() {
        val original = bytes("calculate_optimal_bid_request.binpb")
        val withUnknown = appendUnknownVarintField(original, fieldNumber = 999, value = 42L)

        val parsed = CalculateOptimalBidRequest.parseFrom(withUnknown)

        parsed.unknownFields.asMap().containsKey(999) shouldBe true
        parsed.unknownFields
            .asMap()
            .getValue(999)
            .varintList shouldBe listOf(42L)
    }

    @Test
    fun `unknown field 를 실은 요청을 재직렬화해도 값이 살아있다(canonicalization 안정)`() {
        val original = bytes("calculate_optimal_bid_request.binpb")
        val withUnknown = appendUnknownVarintField(original, fieldNumber = 999, value = 42L)
        val parsed = CalculateOptimalBidRequest.parseFrom(withUnknown)

        val reparsed = CalculateOptimalBidRequest.parseFrom(canonicalBytes(parsed))

        reparsed.unknownFields.asMap().containsKey(999) shouldBe true
        reparsed.unknownFields
            .asMap()
            .getValue(999)
            .varintList shouldBe listOf(42L)
    }

    @Test
    fun `unknown field 는 실제 in-process RPC 경계를 넘어도 보존되고 응답에는 없다`() {
        runBlocking {
            val original = bytes("calculate_optimal_bid_request.binpb")
            val withUnknown = appendUnknownVarintField(original, fieldNumber = 999, value = 42L)
            val requestWithUnknownField = CalculateOptimalBidRequest.parseFrom(withUnknown)

            val stub = stubObservingUnknownFields()
            val response = stub.calculateOptimalBid(requestWithUnknownField)

            // server 가 client 의 재직렬화 → wire → 재파싱을 거친 뒤에도 unknown field 를 봤다.
            response.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.FAILURE
            response.failure.detailCode shouldBe "observed-unknown-field-count=1"
            // 응답(다른 메시지 타입)에는 그 필드가 실리지 않는다 — 새로 만든 객체이므로 구조적으로 당연하지만
            // 명시적으로 고정한다(scope.md ④ "응답으로 되돌아오지 않는다").
            response.unknownFields.asMap().isEmpty() shouldBe true
        }
    }
}
