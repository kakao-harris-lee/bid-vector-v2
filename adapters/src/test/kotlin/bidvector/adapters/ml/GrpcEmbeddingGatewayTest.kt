package bidvector.adapters.ml

import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import contract.bidvector.ml.v1.EmbedTextResponse
import contract.bidvector.ml.v1.EmbeddingServiceGrpcKt
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.GetEmbeddingMetadataRequest
import contract.bidvector.ml.v1.GetEmbeddingMetadataResponse
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
import contract.bidvector.ml.v1.EmbedTextRequest as ProtoEmbedTextRequest

/**
 * scope.md ①~⑤, 설계 검토 「구현 지침」 — fake servicer 위 `GrpcEmbeddingGateway` consumer
 * test(4D-1 `GrpcBidPredictionGatewayTest` 관례). 매핑·release 대조·application failure
 * 재시도가 **실 gateway 경로**로 증명된다.
 */
class GrpcEmbeddingGatewayTest {
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
        val serverName = "bidvector-4d2-gateway-${System.nanoTime()}"
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

    @Test
    fun `정상 응답과 release 일치는 Embedded 를 낸다`() {
        runBlocking {
            val release = testEmbeddingModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val servicer =
                fixedEmbeddingServicer(
                    embedText = protoEmbedResponse(testEmbeddingSuccess().toBuilder().setRelease(release).build()),
                    metadata = embeddingMetadataResponse(release),
                )
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Embedded>()
            outcome.vector.dimension shouldBe 4
        }
    }

    @Test
    fun `latest_promoted 인데 응답 release 가 promoted 와 다르면 ReleaseMismatch 다`() {
        runBlocking {
            val responseRelease = testEmbeddingModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val promotedRelease = testEmbeddingModelRelease(releaseId = "other", artifactChecksum = "c1")
            val responseSuccess = testEmbeddingSuccess().toBuilder().setRelease(responseRelease).build()
            val servicer =
                fixedEmbeddingServicer(
                    embedText = protoEmbedResponse(responseSuccess),
                    metadata = embeddingMetadataResponse(promotedRelease),
                )
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ReleaseMismatch
        }
    }

    // ---- PR #5 게이트 시정(D-2E ② 미구현, contract-keeper 차단) — dimension 대조를 실
    // gateway 경로로 고정한다. 변이 — 처방 전 코드로 되돌리면(`promotedMetadata.dimension`
    // 대조를 지우면) 이 test 가 유일하게 잡는다: release 는 일치하지만 metadata 의
    // dimension 만 응답과 다른 fixture라 `releaseSatisfiesSelector`는 통과하고
    // dimension 대조만 단독으로 걸린다.
    @Test
    fun `latest_promoted 인데 GetEmbeddingMetadata 의 dimension 이 응답과 다르면 ReleaseMismatch 다`() {
        runBlocking {
            val release = testEmbeddingModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val servicer =
                fixedEmbeddingServicer(
                    // 응답 Embedding 은 testEmbeddingSuccess() 기본값(dimension=4, values 4개,
                    // release 일치)이라 release 대조·구조 검증 모두 통과한다.
                    embedText = protoEmbedResponse(testEmbeddingSuccess().toBuilder().setRelease(release).build()),
                    // metadata 는 release 는 같지만 dimension 만 5(응답의 4와 다르다)를 낸다.
                    metadata = embeddingMetadataResponse(release, dimension = 5),
                )
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ReleaseMismatch
        }
    }

    @Test
    fun `exact_release 요청은 GetEmbeddingMetadata 를 부르지 않는다`() {
        runBlocking {
            val release = testEmbeddingModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val servicer =
                fixedEmbeddingServicer(
                    embedText = protoEmbedResponse(testEmbeddingSuccess().toBuilder().setRelease(release).build()),
                    metadata = null,
                )
            val gateway = gatewayOn(servicer)
            val selector = ModelReleaseSelector.Exact("r1", "c1")

            val outcome =
                gateway.embed(testEmbedTextRequest(releaseSelector = selector), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Embedded>()
        }
    }

    @Test
    fun `비재시도 ApplicationFailure 는 한 번만 호출되고 매핑된 사유로 Unavailable 이다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val failure = embeddingFailureResponse(FailureCode.FAILURE_CODE_INVALID_REQUEST, retryable = false)
            val servicer = countingEmbeddingServicer(calls, failure)
            val gateway = gatewayOn(servicer)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.InvalidRequest
            calls.get() shouldBe 1
        }
    }

    @Test
    fun `MODEL_NOT_READY 가 지속되면 재시도 상한만큼 호출되고 ModelNotReady 로 끝난다`() {
        runBlocking {
            val policy = testEmbeddingCallPolicy(maxAttempts = 3)
            val calls = AtomicInteger(0)
            val failure = embeddingFailureResponse(FailureCode.FAILURE_CODE_MODEL_NOT_READY, retryable = true)
            val servicer = countingEmbeddingServicer(calls, failure)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ModelNotReady
            calls.get() shouldBe 3
        }
    }

    @Test
    fun `재시도 불가 transport status(INVALID_ARGUMENT)는 한 번만 시도한다`() {
        runBlocking {
            val calls = AtomicInteger(0)
            val servicer = throwingEmbeddingServicer(calls, Status.INVALID_ARGUMENT)
            val gateway = gatewayOn(servicer)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.TransportFailed
            calls.get() shouldBe 1
        }
    }

    @Test
    fun `UNAVAILABLE 이 재시도 상한을 넘으면 RetryBudgetExhausted 다`() {
        runBlocking {
            val policy = testEmbeddingCallPolicy(maxAttempts = 3)
            val calls = AtomicInteger(0)
            val servicer = throwingEmbeddingServicer(calls, Status.UNAVAILABLE)
            val gateway = gatewayOn(servicer, policy)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.RetryBudgetExhausted
            calls.get() shouldBe 3
        }
    }

    @Test
    fun `재시도는 같은 request_id 를 재사용한다(D-4 멱등)`() {
        runBlocking {
            val seenRequestIds = mutableListOf<String>()
            val policy = testEmbeddingCallPolicy(maxAttempts = 3)
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
                        seenRequestIds.add(request.envelope.base.requestId)
                        throw StatusException(Status.UNAVAILABLE)
                    }

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
                }
            val gateway = gatewayOn(servicer, policy)

            gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            seenRequestIds.size shouldBe 3
            seenRequestIds.toSet().size shouldBe 1
        }
    }

    // ---- verifier F-4(medium) — probe 로만 확인됐던 경로를 test 로 고정한다 ----

    @Test
    fun `응답 feature_schema_version 이 요청 값과 다르면 UnsupportedSchema 다(client 집행)`() {
        runBlocking {
            val mismatchedRelease =
                testEmbeddingModelRelease()
                    .toBuilder()
                    .setFeatureSchemaVersion(
                        "other-schema",
                    ).build()
            val servicer =
                fixedEmbeddingServicer(
                    embedText =
                        protoEmbedResponse(
                            testEmbeddingSuccess().toBuilder().setRelease(mismatchedRelease).build(),
                        ),
                )
            val gateway = gatewayOn(servicer)
            val selector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test")

            val outcome =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = selector),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.UnsupportedSchema
        }
    }

    @Test
    fun `서버 UNSUPPORTED_SCHEMA 실패는 UnsupportedSchema 다`() {
        runBlocking {
            val servicer =
                countingEmbeddingServicer(
                    AtomicInteger(0),
                    embeddingFailureResponse(FailureCode.FAILURE_CODE_UNSUPPORTED_SCHEMA, retryable = false),
                )
            val gateway = gatewayOn(servicer)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.UnsupportedSchema
        }
    }

    @Test
    fun `서버 UNSUPPORTED_RELEASE 실패는 UnsupportedRelease 다`() {
        runBlocking {
            val servicer =
                countingEmbeddingServicer(
                    AtomicInteger(0),
                    embeddingFailureResponse(FailureCode.FAILURE_CODE_UNSUPPORTED_RELEASE, retryable = false),
                )
            val gateway = gatewayOn(servicer)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.UnsupportedRelease
        }
    }

    @Test
    fun `RESULT_NOT_SET 응답은 예외 없이 ContractViolation 이다`() {
        runBlocking {
            val servicer = countingEmbeddingServicer(AtomicInteger(0), EmbedTextResponse.getDefaultInstance())
            val gateway = gatewayOn(servicer)

            val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ContractViolation
        }
    }

    @Test
    fun `training 전용 FailureCode(embedding proto 미표현)는 전부 ContractViolation 이다(table-driven)`() {
        runBlocking {
            val trainingOnlyCodes =
                listOf(
                    FailureCode.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC,
                    FailureCode.FAILURE_CODE_IDEMPOTENCY_CONFLICT,
                    FailureCode.FAILURE_CODE_JOB_NOT_FOUND,
                )
            trainingOnlyCodes.forEach { code ->
                val servicer =
                    countingEmbeddingServicer(AtomicInteger(0), embeddingFailureResponse(code, retryable = false))
                val gateway = gatewayOn(servicer)

                val outcome = gateway.embed(testEmbedTextRequest(), CallBudget(Duration.ofSeconds(1)))

                outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
                outcome.reason shouldBe EmbeddingUnavailableReason.ContractViolation
            }
        }
    }

    @Test
    fun `latest_promoted 인데 GetEmbeddingMetadata 가 예외를 던지면 예외 없이 ReleaseMismatch 다`() {
        runBlocking {
            val release = testEmbeddingModelRelease(releaseId = "r1", artifactChecksum = "c1")
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse =
                        protoEmbedResponse(testEmbeddingSuccess().toBuilder().setRelease(release).build())

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = throw StatusException(Status.UNAVAILABLE)
                }
            val gateway = gatewayOn(servicer)

            val outcome =
                gateway.embed(
                    testEmbedTextRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    CallBudget(Duration.ofSeconds(1)),
                )

            outcome.shouldBeInstanceOf<EmbeddingOutcome.Unavailable>()
            outcome.reason shouldBe EmbeddingUnavailableReason.ReleaseMismatch
        }
    }

    @Test
    fun `text 는 요청 그대로 servicer 에 전달된다(제3 변환 금지)`() {
        runBlocking {
            var seenText: String? = null
            val release = testEmbeddingModelRelease()
            val servicer =
                object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
                    override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
                        seenText = request.text
                        return protoEmbedResponse(testEmbeddingSuccess().toBuilder().setRelease(release).build())
                    }

                    override suspend fun getEmbeddingMetadata(
                        request: GetEmbeddingMetadataRequest,
                    ): GetEmbeddingMetadataResponse = error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
                }
            val gateway = gatewayOn(servicer)
            val selector = ModelReleaseSelector.Exact("release-2026-09-01", "sha256:test")

            gateway.embed(
                testEmbedTextRequest(text = "고유한 합성 텍스트 표본", releaseSelector = selector),
                CallBudget(Duration.ofSeconds(1)),
            )

            seenText shouldBe "고유한 합성 텍스트 표본"
        }
    }
}

private fun countingEmbeddingServicer(
    calls: AtomicInteger,
    embedText: EmbedTextResponse,
): EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase =
    object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
        override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
            calls.incrementAndGet()
            return embedText
        }

        override suspend fun getEmbeddingMetadata(request: GetEmbeddingMetadataRequest): GetEmbeddingMetadataResponse =
            error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
    }

private fun throwingEmbeddingServicer(
    calls: AtomicInteger,
    status: Status,
): EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase =
    object : EmbeddingServiceGrpcKt.EmbeddingServiceCoroutineImplBase() {
        override suspend fun embedText(request: ProtoEmbedTextRequest): EmbedTextResponse {
            calls.incrementAndGet()
            throw StatusException(status)
        }

        override suspend fun getEmbeddingMetadata(request: GetEmbeddingMetadataRequest): GetEmbeddingMetadataResponse =
            error("이 test 는 GetEmbeddingMetadata 를 부르지 않는다")
    }
