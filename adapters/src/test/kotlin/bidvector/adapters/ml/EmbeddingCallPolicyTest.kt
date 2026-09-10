package bidvector.adapters.ml

import bidvector.sharedkernel.Resolution
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

/** D-4D2-2 — 임베딩 전용 정책 슬롯의 생성·출하 값 확인(4D-1 `MlCallPolicyDataTest` 관례). */
class EmbeddingCallPolicyTest {
    @Test
    fun `EMBEDDING_CALL_POLICY 운영 인스턴스는 정상 생성된다`() {
        EMBEDDING_CALL_POLICY.entries.shouldNotBeEmpty()
    }

    @Test
    fun `EMBEDDING_CALL_POLICY 값은 착수 placeholder(4D-1 과 같은 값)와 일치한다`() {
        val resolution = EMBEDDING_CALL_POLICY.resolve(LocalDate.now())
        resolution.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()
        val data = resolution.value

        data.deadlineCeiling shouldBe Duration.ofSeconds(5)
        data.maxAttempts shouldBe 3
        data.backoff shouldBe listOf(Duration.ofMillis(200), Duration.ofMillis(800))
        data.breakerFailureRateThresholdPercent shouldBe 50
        data.breakerSlidingWindowSize shouldBe 10
        data.breakerWaitDurationInOpenState shouldBe Duration.ofSeconds(30)
        data.featureSchemaVersion shouldBe "bidvector.ml.v1-embedding"
    }

    @Test
    fun `EMBEDDING_CALL_POLICY 와 ML_CALL_POLICY 는 서로 다른 인스턴스다(D-4D2-2, 값 슬롯 분리)`() {
        val embeddingSchema = EMBEDDING_CALL_POLICY.resolve(LocalDate.now())
        val predictionSchema = ML_CALL_POLICY.resolve(LocalDate.now())
        embeddingSchema.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()
        predictionSchema.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()

        (embeddingSchema.value.featureSchemaVersion == predictionSchema.value.featureSchemaVersion) shouldBe false
    }
}
