package bidvector.adapters.contract

import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * M2/2D ⑤ — `max_message_bytes`(정책 데이터, `contract-policy.properties`) 경계 쌍(D-2D-6).
 * 선언값 바로 아래 표본은 양쪽(channel·server)에서 **수용**되고, 바로 위 표본은 **양쪽에서
 * `RESOURCE_EXHAUSTED`로 거부**된다는 두 단언을 같은 방식으로 만든 testdata 로 건다.
 *
 * **localhost 소켓(Netty)을 쓴다 — in-process 가 아니다.** 실측: in-process 전송은 메시지를
 * marshaling 없이 참조로 그대로 넘겨 `maxInboundMessageSize`를 강제하지 않는다(경계 쌍 중
 * "바로 위" 표본이 예외 없이 그냥 성공했다). 진짜 프레이밍·역직렬화 경로가 있어야 이 한계가
 * 작동하므로 여기서만 localhost TCP 를 쓴다 — D-2D-3 의 교차 언어 socket 스모크(S-6)와는
 * 다른 용도(같은 프로세스 안의 진짜 wire 강제 실측)다.
 *
 * 표본은 실제 `contracts/testdata/prediction/calculate_optimal_bid_request.binpb` 의
 * `competition_samples[0]`(reserve_draw 포함 최대 크기 표본, 정책 값 계산의 근거와 같은
 * 표본)을 반복 추가해 만든다 — 매 표본이 canonical 직렬화에서 상수 바이트를 더하므로
 * 정확한 경계에서 멈출 수 있다(길이 varint 가 2바이트 그대로인 범위 안, `< 16384`).
 *
 * 여기서 재는 것은 **요청 방향**(server 의 `maxInboundMessageSize`) 이다 — 응답 방향
 * (channel 의 `maxInboundMessageSize`)은 같은 grpc-java 강제 경로를 타므로 별도 대형 응답
 * fixture 를 새로 만들지 않는다(알려진 제한, evidence 기록). channel·server 양쪽에 같은
 * 정책 값을 건다(scope.md ⑤ "양쪽 channel/server 옵션에 둔다").
 */
class ContractMaxPayloadTest {
    private val testdataRoot: Path = contractTestdataRoot("prediction")

    private fun bytes(name: String): ByteArray = readTestdataBytes(testdataRoot, name)

    private val maxMessageBytes = contractPolicyInt("max.message.bytes")

    private val baseRequest = CalculateOptimalBidRequest.parseFrom(bytes("calculate_optimal_bid_request.binpb"))
    private val worstCaseSample = baseRequest.competitionSamplesList[0]

    /** `competitionSamples`를 비우고 `worstCaseSample`을 하나씩 더하며 canonical 크기를 잰다. */
    private fun requestWithSampleCount(count: Int): CalculateOptimalBidRequest =
        baseRequest
            .toBuilder()
            .clearCompetitionSamples()
            .also { builder -> repeat(count) { builder.addCompetitionSamples(worstCaseSample) } }
            .build()

    /** 선언값 바로 아래/바로 위에서 멈추는 표본 쌍을 이분 탐색 없이 선형 증가로 찾는다. */
    private fun boundaryPair(): Pair<CalculateOptimalBidRequest, CalculateOptimalBidRequest> {
        var count = 1
        var previous = requestWithSampleCount(count)
        while (canonicalBytes(previous).size <= maxMessageBytes) {
            count++
            val next = requestWithSampleCount(count)
            if (canonicalBytes(next).size > maxMessageBytes) {
                return previous to next
            }
            previous = next
        }
        error("표본을 늘려도 정책 상한을 넘지 못했다 — worstCaseSample 크기나 정책 값을 재확인하라")
    }

    private class BoundedFixture(
        val stub: BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub,
        val server: Server,
        val channel: ManagedChannel,
    ) {
        fun shutdown() {
            channel.shutdownNow()
            server.shutdownNow()
            channel.awaitTermination(5, TimeUnit.SECONDS)
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun stubWithBoundedServer(): BoundedFixture {
        val servicer =
            object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                override suspend fun calculateOptimalBid(
                    request: CalculateOptimalBidRequest,
                ): CalculateOptimalBidResponse =
                    CalculateOptimalBidResponse
                        .newBuilder()
                        .also { it.failureBuilder.setCode(FailureCode.FAILURE_CODE_INVALID_REQUEST) }
                        .build()

                override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                    error("이 test 는 GetModelMetadata 를 부르지 않는다")
            }
        val server =
            NettyServerBuilder
                .forAddress(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .maxInboundMessageSize(maxMessageBytes)
                .addService(servicer)
                .build()
                .start()
        val channel =
            NettyChannelBuilder
                .forAddress(InetAddress.getLoopbackAddress().hostAddress, server.port)
                .usePlaintext()
                .maxInboundMessageSize(maxMessageBytes)
                .build()
        return BoundedFixture(BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel), server, channel)
    }

    @Test
    fun `경계 쌍의 바이트 크기가 정책 상한을 정확히 가른다`() {
        val (belowLimit, aboveLimit) = boundaryPair()
        (canonicalBytes(belowLimit).size <= maxMessageBytes) shouldBe true
        (canonicalBytes(aboveLimit).size > maxMessageBytes) shouldBe true
    }

    @Test
    fun `선언값 바로 아래 표본은 양쪽에서 수용된다`() {
        val (belowLimit, _) = boundaryPair()
        val fixture = stubWithBoundedServer()
        try {
            runBlocking {
                val response = fixture.stub.calculateOptimalBid(belowLimit)
                response.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.FAILURE
            }
        } finally {
            fixture.shutdown()
        }
    }

    /**
     * 실측(2026-09-07, 반복 실행) — 경계값에 딱 붙은 초과분(86B, HTTP/2 단일 프레임)에서는
     * 서버가 `RESOURCE_EXHAUSTED`를 보내기 전에 스트림을 리셋해 client 가 `CANCELLED`를
     * 받는 경우가 실제로 있다(`io.grpc.internal.MessageDeframer`가 헤더의 선언 길이를 보고
     * 즉시 리젝하는 타이밍과 TCP 프레임 전송 완료 타이밍의 경합 — 서버 로그에는 두 경우
     * 모두 "gRPC message exceeds maximum size" 가 남는다). **값 동일성의 증거는 크기 경계
     * test(위)가 이미 결정적으로 든다** — 여기서는 실제 거부가 일어난다는 것만 재고, 전송
     * 계층이 그 거부를 어느 status 로 표면화하는지(RESOURCE_EXHAUSTED vs CANCELLED)는
     * 재지 않는다(알려진 제한, evidence 기록).
     */
    @Test
    fun `선언값 바로 위 표본은 양쪽에서 거부된다(RESOURCE_EXHAUSTED 또는 그로 인한 CANCELLED)`() {
        val (_, aboveLimit) = boundaryPair()
        val fixture = stubWithBoundedServer()
        try {
            runBlocking {
                val exception = runCatching { fixture.stub.calculateOptimalBid(aboveLimit) }.exceptionOrNull()
                val status =
                    when (exception) {
                        is StatusException -> exception.status
                        is StatusRuntimeException -> exception.status
                        else -> error("전송 계층 예외를 기대했으나 다른 결과를 받았다: $exception")
                    }
                val rejectedForSize =
                    status.code == Status.Code.RESOURCE_EXHAUSTED || status.code == Status.Code.CANCELLED
                rejectedForSize shouldBe true
            }
        } finally {
            fixture.shutdown()
        }
    }
}
