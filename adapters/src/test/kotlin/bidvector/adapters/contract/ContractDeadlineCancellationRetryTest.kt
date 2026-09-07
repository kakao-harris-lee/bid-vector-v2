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
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * M2/2D ⑥ — deadline·cancellation·retry(ADR 0010 D-2·D-4, 조사 노트 02 grpc/grpc#36193).
 * fake servicer 는 in-process(grpc-testing 대역) 위에서 지연·취소·간헐 실패를 흉내낸다.
 * **취소 검증은 `cancelled()` 플래그 폴링이 아니라 `CancellationException` 을 실제로 받아
 * 계산을 중단했다는 카운터로 한다**(D-2D-7 — 조사 02 가 콜백 중 `cancelled()` False 버그를
 * 지적했으므로 폴링에 기대지 않는다).
 */
class ContractDeadlineCancellationRetryTest {
    private val testdataRoot: Path = contractTestdataRoot("prediction")

    private fun bytes(name: String): ByteArray = readTestdataBytes(testdataRoot, name)

    private val requestBytes = bytes("calculate_optimal_bid_request.binpb")
    private val retryMaxAttempts = contractPolicyInt("retry.sample.max-attempts")

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    /** 요청마다 `delayMillis` 만큼 지연하고, 중단되면 `cancelled`를 올리는 fake servicer. */
    private class DelayedServicer(
        private val delayMillis: Long,
    ) : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
        val started = AtomicInteger(0)
        val cancelled = AtomicInteger(0)

        override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
            started.incrementAndGet()
            try {
                delay(delayMillis)
            } catch (cancellation: CancellationException) {
                cancelled.incrementAndGet()
                throw cancellation
            }
            return CalculateOptimalBidResponse
                .newBuilder()
                .also { it.failureBuilder.setCode(FailureCode.FAILURE_CODE_INVALID_REQUEST) }
                .build()
        }

        override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
            error("이 test 는 GetModelMetadata 를 부르지 않는다")
    }

    /** 처음 `failuresBeforeSuccess`회는 UNAVAILABLE 을 던지고 그 뒤로는 성공하는 fake servicer. */
    private class FlakyServicer(
        private val failuresBeforeSuccess: Int,
    ) : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
        val attempts = AtomicInteger(0)

        override suspend fun calculateOptimalBid(request: CalculateOptimalBidRequest): CalculateOptimalBidResponse {
            val attempt = attempts.incrementAndGet()
            if (attempt <= failuresBeforeSuccess) {
                throw StatusException(Status.UNAVAILABLE.withDescription("attempt $attempt"))
            }
            return CalculateOptimalBidResponse
                .newBuilder()
                .also { it.failureBuilder.setCode(FailureCode.FAILURE_CODE_INVALID_REQUEST) }
                .build()
        }

        override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
            error("이 test 는 GetModelMetadata 를 부르지 않는다")
    }

    private fun stubOn(
        servicer: BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase,
    ): BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub {
        val serverName = "bidvector-2d-deadline-${System.nanoTime()}"
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

    // ---- (a) deadline ----

    @Test
    fun `짧은 deadline 은 DEADLINE_EXCEEDED 로 client 에 도달한다`() {
        runBlocking {
            val servicer = DelayedServicer(delayMillis = 2_000)
            val stub = stubOn(servicer).withDeadlineAfter(50, TimeUnit.MILLISECONDS)
            val request = CalculateOptimalBidRequest.parseFrom(requestBytes)

            val error = runCatching { stub.calculateOptimalBid(request) }.exceptionOrNull()

            val status =
                when (error) {
                    is StatusException -> error.status
                    is StatusRuntimeException -> error.status
                    else -> error("DEADLINE_EXCEEDED 대신 다른 예외: $error")
                }
            status.code shouldBe Status.Code.DEADLINE_EXCEEDED
        }
    }

    // ---- (b) cancellation — 자원 해제(카운터), 플래그 폴링 아님(D-2D-7) ----

    @Test
    fun `coroutine 취소가 servicer 의 계산 중단으로 이어진다(카운터 관측)`() {
        runBlocking {
            val servicer = DelayedServicer(delayMillis = 5_000)
            val stub = stubOn(servicer)
            val request = CalculateOptimalBidRequest.parseFrom(requestBytes)

            val job = launch { runCatching { stub.calculateOptimalBid(request) } }
            while (servicer.started.get() == 0) {
                delay(5)
            }
            job.cancel()
            job.join()

            // server 쪽 취소 처리는 client job 과 다른 coroutine(구조적 동기화 밖)이라
            // job.join() 만으로는 그 완료를 보장하지 않는다 — 값을 기다리는 것이지
            // `cancelled()` 플래그를 재는 것이 아니다(D-2D-7 은 servicer 의 취소 *판단*
            // 로직을 폴링하지 말라는 것이지, test 의 비동기 동기화 대기를 금지하지 않는다).
            withTimeout(2_000) {
                while (servicer.cancelled.get() == 0) {
                    delay(5)
                }
            }

            servicer.started.get() shouldBe 1
            servicer.cancelled.get() shouldBe 1
        }
    }

    // ---- (c) retry — retryable 인 경우만 정책 상한 안에서 ----

    @Test
    fun `UNAVAILABLE 은 상한 안에서 재시도하면 결국 성공한다`() {
        runBlocking {
            val servicer = FlakyServicer(failuresBeforeSuccess = retryMaxAttempts - 1)
            val stub = stubOn(servicer)
            val request = CalculateOptimalBidRequest.parseFrom(requestBytes)

            val response =
                callWithTransportRetry(retryMaxAttempts) { stub.calculateOptimalBid(request) }

            response.resultCase shouldBe CalculateOptimalBidResponse.ResultCase.FAILURE
            servicer.attempts.get() shouldBe retryMaxAttempts
        }
    }

    @Test
    fun `상한을 넘는 UNAVAILABLE 은 결국 예외로 올라온다`() {
        runBlocking {
            val servicer = FlakyServicer(failuresBeforeSuccess = retryMaxAttempts + 5)
            val stub = stubOn(servicer)
            val request = CalculateOptimalBidRequest.parseFrom(requestBytes)

            val error =
                runCatching { callWithTransportRetry(retryMaxAttempts) { stub.calculateOptimalBid(request) } }
                    .exceptionOrNull()

            error.shouldNotBeNullStatusException()
            servicer.attempts.get() shouldBe retryMaxAttempts
        }
    }

    @Test
    fun `재시도 불가 상태(INVALID_ARGUMENT)는 한 번만 시도하고 예외를 던진다`() {
        runBlocking {
            val servicer =
                object : BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineImplBase() {
                    val attempts = AtomicInteger(0)

                    override suspend fun calculateOptimalBid(
                        request: CalculateOptimalBidRequest,
                    ): CalculateOptimalBidResponse {
                        attempts.incrementAndGet()
                        throw StatusException(Status.INVALID_ARGUMENT)
                    }

                    override suspend fun getModelMetadata(request: GetModelMetadataRequest): GetModelMetadataResponse =
                        error("이 test 는 GetModelMetadata 를 부르지 않는다")
                }
            val stub = stubOn(servicer)
            val request = CalculateOptimalBidRequest.parseFrom(requestBytes)

            runCatching { callWithTransportRetry(retryMaxAttempts) { stub.calculateOptimalBid(request) } }

            servicer.attempts.get() shouldBe 1
        }
    }

    @Test
    fun `application failure 는 retryable 필드가 재시도 여부를 정한다(재시도 0)`() {
        isRetryableApplicationFailure(retryable = false) shouldBe false
        isRetryableApplicationFailure(retryable = true) shouldBe true
    }

    private fun Throwable?.shouldNotBeNullStatusException() {
        val status =
            when (this) {
                is StatusException -> status
                is StatusRuntimeException -> status
                else -> error("StatusException 을 기대했으나 $this")
            }
        status.code shouldBe Status.Code.UNAVAILABLE
    }
}
