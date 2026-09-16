package bidvector.adapters.contract

import bidvector.adapters.ml.GrpcBidPredictionGateway
import bidvector.adapters.ml.testBidPredictionRequest
import bidvector.adapters.ml.testMlCallEffectivePolicy
import bidvector.adapters.ml.testMlCallPolicy
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.CompetitionSample
import bidvector.workflow.prediction.ModelReleaseSelector
import bidvector.workflow.prediction.OptimizationObjective
import bidvector.workflow.prediction.ReserveDrawObservation
import contract.bidvector.ml.v1.BidPredictionServiceGrpcKt
import contract.bidvector.ml.v1.GetModelMetadataRequest
import contract.bidvector.ml.v1.GetModelMetadataResponse
import contract.bidvector.ml.v1.Readiness
import contract.bidvector.ml.v1.RequestEnvelope
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * M6/6C ④(scope.md, `OPEN-5E2-CROSSLANG-REAL-SERVER` 종결) — **실 Kotlin gateway**
 * (`GrpcBidPredictionGateway`, fake 아님)로 (1) `docker/ml-serving.Dockerfile`이 만든
 * 실 Python 서버 컨테이너에 붙는다. 2B/2D/4D-1 이 fake servicer 위에서 확인해 온 매핑·
 * release 대조·재시도 규칙이 실 서버 응답에서도 성립함을 여기서 처음 증명한다.
 *
 * D-6C-4 — 기본 `check`에서 빠진다(`@EnabledIfSystemProperty`, JVM 이 이 조건을 평가하는
 * 시점에 `@BeforeAll`이 아직 호출되지 않았으므로 조건이 거짓이면 컨테이너 자체가
 * 뜨지 않는다 — Docker 부재 환경에서도 plain `./gradlew check`가 이 클래스를 건드리지
 * 않는다). CI 에서는 `-PrealServer=true`로 켠다(`adapters/build.gradle.kts`가
 * `bidvector.realServer.enabled` system property 로 project property 를 그대로
 * 전달한다).
 *
 * **패키지가 `bidvector.adapters.contract`다(D-6C-8, 계약 갱신 2026-09-16 (2))** —
 * `bidvector.adapters.ml`에는 두지 않는다. 그 패키지에는 `MlGateRegistrationTest`
 * (디렉터리의 모든 `*Test.kt` 전수를 `gate-tests.properties` 등재와 대조)와
 * `gateExecutionGate`(등재된 클래스의 skip 0 요구)가 함께 있어, 환경 조건부로 항상
 * skip 될 수 있는 이 test 를 그 자리에 두면 두 게이트가 동시에 만족 불가능해진다
 * (등재하면 skip 위반, 빼면 등재 위반 — 구현 레인 실측, 정지·보고 뒤 팀장 결정).
 * 같은 축의 기존 전례(`CrossLangSmokeTest` — 교차 언어 스모크, 컨테이너 없이 도는
 * Python 서버 대상)가 이미 이 패키지에 있다 — 이 test 는 그 컨테이너 판이다. 게이트
 * 술어(두 파일 모두 build-logic·기존 test)는 건드리지 않는다(`OPEN-6C-CONDITIONAL-
 * GATE-TEST` — `adapters.ml` 안에 조건부 test 가 실제로 필요해지면 그때 설계 검토로
 * 게이트 술어 개정을 받는다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "bidvector.realServer.enabled", matches = "true")
class RealServerIntegrationTest {
    private lateinit var container: RealMlServingContainer
    private lateinit var channel: ManagedChannel

    @BeforeAll
    fun startContainer() {
        container =
            RealMlServingContainer(DockerImageName.parse(IMAGE_REF))
                .withExposedPorts(GRPC_PORT)
                .withEnv("ML_ENGINE_BIND", "0.0.0.0:$GRPC_PORT")
                .withEnv("ML_ENGINE_INFERENCE_POLICY", "/app/policy/inference-v1.yaml")
                .withEnv("ML_ENGINE_TRAINING_POLICY", "/app/policy/training-v1.yaml")
                .withEnv("ML_ENGINE_EVALUATION_POLICY", "/app/policy/evaluation-v1.yaml")
                .withEnv("ML_ENGINE_SERVING_POLICY", "/app/policy/serving-v1.yaml")
                .withEnv("ML_ENGINE_ARTIFACT_OUT_DIR", "/app/artifacts")
                .withEnv("ML_ENGINE_CODE_VERSION", "6c-real-server-integration-test")
                .waitingFor(Wait.forLogMessage(".*readiness=ReadinessSnapshot.*\\n", 1))
        container.start()
        channel =
            ManagedChannelBuilder
                .forAddress(container.host, container.getMappedPort(GRPC_PORT))
                .usePlaintext()
                .build()
    }

    @AfterAll
    fun stopContainer() {
        if (this::channel.isInitialized) channel.shutdownNow()
        if (this::container.isInitialized) container.stop()
    }

    /** 우회 (3) — 붙은 곳이 컨테이너임을 스스로 단언한다(로컬 `.venv` 서버가 아니다). */
    @Test
    fun `test 자신이 컨테이너 포트에 붙었음을 단언한다`() {
        container.isRunning shouldBe true
        container.dockerImageName shouldBe IMAGE_REF
        container.containerId.isNotBlank() shouldBe true
    }

    /** 설계 검토 구현순서 6-① — GetModelMetadata 가 READY·promoted 를 실 서버에서 낸다. */
    @Test
    fun `실 서버의 GetModelMetadata 는 READY 와 promoted release 를 낸다`() {
        runBlocking {
            val stub = BidPredictionServiceGrpcKt.BidPredictionServiceCoroutineStub(channel)
            val response =
                stub.getModelMetadata(
                    GetModelMetadataRequest
                        .newBuilder()
                        .setEnvelope(
                            RequestEnvelope
                                .newBuilder()
                                .setRequestId(UUID.randomUUID().toString())
                                .setCorrelationId("real-server-metadata-check")
                                .build(),
                        ).build(),
                )
            response.resultCase shouldBe GetModelMetadataResponse.ResultCase.METADATA
            val metadata = response.metadata
            metadata.readiness shouldBe Readiness.READINESS_READY
            val promotedReleaseId = metadata.promoted.releaseId
            promotedReleaseId.isNotBlank() shouldBe true
            val supported = metadata.supportedFeatureSchemaVersionsList
            supported.contains(SUPPORTED_FEATURE_SCHEMA_VERSION) shouldBe true
        }
    }

    /**
     * 설계 검토 구현순서 6-②③ — 실 gateway 경로로 `Predicted`를 받고(Kotlin 소비자 규칙
     * 다섯이 실 응답에서 성립한다는 것은 `handleResponse`→`mapSuccess`→`ResponseMapping`이
     * 예외 없이 `Predicted`를 낸다는 사실 자체가 증명한다 — fake 응답이 아니라 실 서버가
     * 만든 `Success`가 `ReleaseShapeValidation`·`ParsedSuccessFields`·`FractionRules`를
     * 실제로 통과했다는 뜻이다), 5F-2 `featureSchemaVersion`이 수용된다.
     */
    @Test
    fun `실 서버 예측 호출은 Predicted 를 낸다(Kotlin 소비자 규칙 다섯이 실 응답에서 성립)`() {
        runBlocking {
            val gateway = realGateway(SUPPORTED_FEATURE_SCHEMA_VERSION)

            val outcome =
                gateway.predict(
                    manySampleBidPredictionRequest(ModelReleaseSelector.LatestPromoted),
                    bidvector.workflow.prediction.CallBudget(Duration.ofSeconds(10)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        }
    }

    /**
     * 우회 (3) 의 짝 — 5F-2 가 맞춘 `featureSchemaVersion`이 **거부**되기도 함을 실 서버로
     * 확인한다(음성 대조, scope.md ④). 옛 스키마 버전을 보내면 서버가
     * `FAILURE_CODE_UNSUPPORTED_SCHEMA`(재시도 불가)로 거부하고, Kotlin 쪽은
     * `MlUnavailableReason.UnsupportedSchema`로 접는다 — `ResponseMapping.kt`가 실 서버
     * 응답에서도 같은 사유로 접힌다는 증명이다.
     */
    @Test
    fun `옛 featureSchemaVersion 은 실 서버에서 UNSUPPORTED_SCHEMA 로 거부된다(음성 대조)`() {
        runBlocking {
            val gateway = realGateway("bidvector.ml.v1-superseded")

            val outcome =
                gateway.predict(
                    testBidPredictionRequest(releaseSelector = ModelReleaseSelector.LatestPromoted),
                    bidvector.workflow.prediction.CallBudget(Duration.ofSeconds(10)),
                )

            outcome.shouldBeInstanceOf<BidPredictionOutcome.Unavailable>()
            outcome.reason shouldBe bidvector.decision.MlUnavailableReason.UnsupportedSchema
        }
    }

    /**
     * `testBidPredictionRequest()`(fake servicer 대역 공용, 표본 1건, `reserveDraw` 없음)와
     * 달리 실 엔진(`ml_engine.inference.observations.observe_sample`)은 표본마다
     * `reserve_draw`(예비가 15개, `reserve.expected_price_count`)가 **있어야** K6
     * draw 연산을 하고, 관측 행 수가 `reserve.min_reserve_records`(8)·비율 표본 수가
     * `bid_ratio.min_samples`(3) 미만이면 `Unmeasurable(InsufficientSamples)`로
     * 정직하게 거절한다(fake 는 응답을 고정으로 배선해 이 두 축을 재지 않는다) — **값
     * 계산 축이지 이 slice 가 잡는 소비자 규칙 축이 아니라서** 정책 하한을 채운 별도
     * fixture 를 쓴다(2026-09-16 실 서버 첫 실행 두 차례에서 실측 — ① `reserveDraw`
     * 없이 보내면 전 표본이 `NO_RESERVE_DRAW`로 제외돼 표본 수와 무관하게
     * `Unmeasurable` ② `reserveDraw`를 채우자 `Predicted`로 성립).
     */
    private fun manySampleBidPredictionRequest(releaseSelector: ModelReleaseSelector): BidPredictionRequest =
        BidPredictionRequest(
            baseAmount =
                ResolvedBaseAmount.Direct.of(
                    won = 1_000_000L,
                    currency = Currency.KRW,
                    vatTreatment = VatTreatment.INCLUSIVE,
                    provenance = Provenance.Published(NoticeRound("000")),
                ),
            businessCategory = null,
            agencyId = null,
            baseAmountProvenanceLabel = BaseAmountProvenance.Clean,
            competitionSamples =
                (0 until SAMPLE_COUNT).map { i ->
                    CompetitionSample(
                        observedBidRate = Rate.ofFraction(BigDecimal("0.${880 + i * 10}")),
                        baseAmount =
                            BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Undeclared),
                        baseAmountProvenanceLabel = BaseAmountProvenance.Clean,
                        openedOn = LocalDate.of(2026, 1, 1).plusDays(i.toLong()),
                        reserveDraw = reserveDrawObservation(i),
                    )
                },
            objective = OptimizationObjective.SCENARIO_TRIPLE,
            releaseSelector = releaseSelector,
            correlationId = CorrelationId("real-server-integration-test"),
        )

    /**
     * `ml_engine.inference.observations._resolve_reserve_draw`가 요구하는 형태 —
     * 정확히 `RESERVE_PRICE_COUNT`(15, `reserve.expected_price_count`)개의 양수 `Money`
     * (basis 는 `BaseAmount` 타입 자체가 고정한다). K6 `draw_mean_moments`가 표본 15개가
     * 전부 동일값이면 모분산 0 으로 `RESERVE_DRAW_UNMEASURABLE`을 내므로(관측 노트,
     * `observations.py`) `i`마다 값을 조금씩 벌려 분산을 만든다. 비율(가격/기초금액)이
     * `assessment.plausible_min/max`(0.8~1.2) 안에 들도록 90 만~104 만 사이로 둔다.
     * `selectedNumbers`는 빈 집합(관측되지 않음, 엔진이 거부하지 않는다 —
     * `ReserveDrawObservation` KDoc).
     */
    private fun reserveDrawObservation(sampleIndex: Int): ReserveDrawObservation =
        ReserveDrawObservation(
            reservePrices =
                (0 until RESERVE_PRICE_COUNT).map { j ->
                    val won = 900_000L + ((sampleIndex * 7 + j * 11) % 15) * 10_000L
                    BaseAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Undeclared)
                },
            selectedNumbers = emptySet(),
        )

    private fun realGateway(featureSchemaVersion: String): GrpcBidPredictionGateway {
        val policy =
            testMlCallPolicy(
                deadlineCeiling = Duration.ofSeconds(10),
                maxAttempts = 1,
            ).let { data -> data.copy(featureSchemaVersion = featureSchemaVersion) }
        return GrpcBidPredictionGateway(channel, testMlCallEffectivePolicy(policy), Clock.systemUTC())
    }

    /** Kotlin 이 self-referencing 제네릭(`GenericContainer<SELF>`)을 raw type 없이 쓰기
     * 위한 표준 관용구(Testcontainers 공식 문서 권고) — 이 test 파일 전용, production
     * 표면 아님. */
    private class RealMlServingContainer(
        imageName: DockerImageName,
    ) : GenericContainer<RealMlServingContainer>(imageName)

    private companion object {
        // S-21(`docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .`)
        // 이 만드는 태그 — `PersistenceTestSupport.POSTGRES_IMAGE`와 같은 관례(test 상수,
        // 정책 데이터 아님).
        const val IMAGE_REF = "bidvector/ml-serving:local"
        const val GRPC_PORT = 50051
        const val SUPPORTED_FEATURE_SCHEMA_VERSION = "award-rate-features-v2"

        // `reserve.min_reserve_records`(8, `ml-engine/policy/inference-v1.yaml`) 이상 —
        // 실측 여유를 조금 둔다(정책 값 자체가 아니라 test fixture 크기라 policy 파일을
        // 참조하지 않는다, 값이 바뀌면 이 test 가 다시 Unmeasurable 로 실패해 드러난다).
        const val SAMPLE_COUNT = 10

        // `reserve.expected_price_count`(15, 같은 정책 파일) — 표본마다 정확히 이 개수의
        // 예비가를 보내야 `_resolve_reserve_draw`가 `PRICE_COUNT_MISMATCH`로 거절하지 않는다.
        const val RESERVE_PRICE_COUNT = 15
    }
}
