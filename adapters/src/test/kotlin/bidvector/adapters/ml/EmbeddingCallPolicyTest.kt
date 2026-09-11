package bidvector.adapters.ml

import bidvector.adapters.contract.contractPolicyValue
import bidvector.adapters.contract.contractTestdataRoot
import bidvector.adapters.contract.readTestdataBytes
import bidvector.sharedkernel.Resolution
import bidvector.workflow.embedding.EmbeddingVector
import contract.bidvector.ml.v1.EmbedTextResponse
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate

/** D-4D2-2 — 임베딩 전용 정책 슬롯의 생성·출하 값 확인(4D-1 `MlCallPolicyDataTest` 관례). */
class EmbeddingCallPolicyTest {
    @Test
    fun `EMBEDDING_CALL_POLICY 운영 인스턴스는 정상 생성된다`() {
        EMBEDDING_CALL_POLICY.entries.shouldNotBeEmpty()
    }

    @Test
    fun `EMBEDDING_CALL_POLICY 의 deadline·재시도·backoff·breaker 는 착수 placeholder(4D-1 과 같은 값)와 일치한다`() {
        val resolution = EMBEDDING_CALL_POLICY.resolve(LocalDate.now())
        resolution.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()
        val data = resolution.value

        data.deadlineCeiling shouldBe Duration.ofSeconds(5)
        data.maxAttempts shouldBe 3
        data.backoff shouldBe listOf(Duration.ofMillis(200), Duration.ofMillis(800))
        data.breakerFailureRateThresholdPercent shouldBe 50
        data.breakerSlidingWindowSize shouldBe 10
        data.breakerWaitDurationInOpenState shouldBe Duration.ofSeconds(30)
    }

    /**
     * 리뷰 F-B(medium) — `featureSchemaVersion`은 deadline·재시도·backoff·breaker 와 다른
     * 축(텍스트 합성 규약 version, `OPEN-2E-TEXT-SYNTHESIS` 소유)이라 4D-1 과 같을 이유가
     * 없다. 이 slice는 그 값의 **의미를 정하지 않고** 2E 가 이미 승인한 testdata(provider
     * fixture servicer 가 실제로 받아들이는 값)와 직접 대조한다 — 어긋나면 실 provider와
     * 양방향 `UNSUPPORTED_SCHEMA`가 나는데 그것을 지금까지 아무 test 도 못 잡았다.
     */
    @Test
    fun `EMBEDDING_CALL_POLICY 의 featureSchemaVersion 은 2E 승인 testdata 의 값과 일치한다`() {
        val resolution = EMBEDDING_CALL_POLICY.resolve(LocalDate.now())
        resolution.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()

        val root = contractTestdataRoot("embedding")
        val golden = EmbedTextResponse.parseFrom(readTestdataBytes(root, "embed_text_response_success.binpb"))

        resolution.value.featureSchemaVersion shouldBe golden.success.release.featureSchemaVersion
    }

    @Test
    fun `EMBEDDING_CALL_POLICY 와 ML_CALL_POLICY 는 서로 다른 인스턴스다(D-4D2-2, 값 슬롯 분리)`() {
        val embeddingSchema = EMBEDDING_CALL_POLICY.resolve(LocalDate.now())
        val predictionSchema = ML_CALL_POLICY.resolve(LocalDate.now())
        embeddingSchema.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()
        predictionSchema.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()

        (embeddingSchema.value.featureSchemaVersion == predictionSchema.value.featureSchemaVersion) shouldBe false
    }

    /**
     * verifier F-3(medium) — `EMBEDDING_NORM_EPSILON`(`EmbeddingCallPolicy.kt`) KDoc 은
     * `config/quality/contract-policy.properties`의 `embedding.norm.epsilon`을 미러한다고
     * 적지만 그 어긋남을 잡는 test 가 없었다. `ContractPolicySupport.kt`(2D)가 이미 여는
     * 유일 판독 지점(`contractPolicyValue`)으로 두 값을 직접 대조한다 — 어느 한쪽만 바뀌면
     * 이 test 가 빨개진다.
     */
    @Test
    fun `EMBEDDING_NORM_EPSILON 은 contract-policy_properties 의 embedding_norm_epsilon 과 일치한다`() {
        EMBEDDING_NORM_EPSILON shouldBe BigDecimal(contractPolicyValue("embedding.norm.epsilon"))
    }

    /**
     * 리뷰 F-C(medium) — 「구조 검증층(정밀 임계)이 값 타입 `init`(거친 안전판)보다 먼저
     * 걸린다」는 이 slice 의 핵심 불변식이 **오직** `EMBEDDING_NORM_EPSILON <
     * EmbeddingVector.COARSE_NORM_EPSILON` 라는 대소 관계에만 기댄다. 정책 파일의
     * `embedding.norm.epsilon`이 갱신돼 이 관계가 뒤집히면 검증층을 통과한 벡터가
     * `EmbeddingVector.init`에서 예외를 던져 F-1과 같은 클래스로 샌다 — 그 관계를 여기서
     * 기계로 강제한다.
     */
    @Test
    fun `EMBEDDING_NORM_EPSILON 은 EmbeddingVector 의 거친 안전판보다 좁다(검증층 선행 불변식)`() {
        (EMBEDDING_NORM_EPSILON < BigDecimal(EmbeddingVector.COARSE_NORM_EPSILON)) shouldBe true
    }
}
