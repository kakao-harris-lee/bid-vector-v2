package bidvector.workflow.embedding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * scope.md ①, 설계 검토 (1) — 이 패키지가 여는 값 타입의 생성 불변식. 벡터 값 타입의 위조
 * 차단(`internal constructor`)은 이 test 가 아니라 임시 clone 컴파일 거부 실측
 * (commands.md, 4D-1 `PredictionValueTest` 관례)이 진다 — 이 test 는 같은 모듈 안에서
 * 여전히 서는 불변식(차원·정규화 하한)만 잰다.
 */
class EmbeddingValueTest {
    @Test
    fun `EmbeddingVector 는 values 가 빈 리스트면 거부한다`() {
        shouldThrow<IllegalArgumentException> { EmbeddingVector(emptyList()) }
    }

    @Test
    fun `EmbeddingVector 는 L2 정규화(norm 1)가 아니면 거부한다`() {
        // norm = 2 (정규화 안 됨)
        shouldThrow<IllegalArgumentException> { EmbeddingVector(listOf(2.0f, 0.0f)) }
    }

    @Test
    fun `EmbeddingVector 는 정규화된 값으로 정상 생성되고 dimension 은 values 크기에서 파생된다`() {
        val unit = (1.0 / Math.sqrt(2.0)).toFloat()
        val vector = EmbeddingVector(listOf(unit, unit))

        vector.dimension shouldBe 2
        vector.values shouldBe listOf(unit, unit)
    }

    @Test
    fun `단일 성분 1_0 은 정상 생성된다`() {
        val vector = EmbeddingVector(listOf(1.0f))

        vector.dimension shouldBe 1
    }

    @Test
    fun `EmbedTextRequest 의 text 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            testEmbedTextRequest(text = "")
        }
        shouldThrow<IllegalArgumentException> {
            testEmbedTextRequest(text = "   ")
        }
    }

    @Test
    fun `EmbedTextRequest 는 정상 값으로 생성된다`() {
        val request = testEmbedTextRequest(text = "공고 원문 합성 텍스트")

        request.text shouldBe "공고 원문 합성 텍스트"
        request.kind shouldBe TextKind.NOTICE
    }
}
