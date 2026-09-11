package bidvector.workflow.embedding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * scope.md ①, 설계 검토 (1) — 이 패키지가 여는 값 타입의 생성 불변식.
 *
 * **verifier F-2(medium) — 정정.** 이전 문면은 「위조 차단은 `internal constructor` +
 * 임시 clone 컴파일 거부 실측이 진다」고 적었으나 `EmbeddingVector`의 생성자는
 * **`internal`이 아니라 public**이다(어댑터 모듈이 값을 지어야 해서 `internal`을 쓸 수
 * 없다 — `EmbeddingVector` KDoc, (2b) 표). 그러므로 위조 차단이라는 게이트 자체가
 * **없다** — 이 test 는 `init`이 실제로 강제하는 **형태 하한**만 잰다: 빈 리스트 거부·
 * L2 정규화(norm 1±0.01) 밖 거부·비유한(NaN/Infinity) 값 거부(IEEE754 비교가 항상
 * `false`가 되어 정규화 검사에 걸린다, verifier F-1 확증). **정규화된 임의 방향 벡터는
 * 통과한다** — 「위조 방어」로 셀 수 없다. 소비자가 배선되는 순간(4B-6) 타입만으로는
 * 진짜와 위조를 가를 수 없다는 사실은 `OPEN-4D2-VECTOR-FORGERY-AT-WIRING`으로 그
 * slice에 넘겼다(scope.md (2b) 표).
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
    fun `EmbeddingVector 는 비유한 값(NaN Infinity)을 거부한다`() {
        shouldThrow<IllegalArgumentException> { EmbeddingVector(listOf(Float.NaN, 0.0f)) }
        shouldThrow<IllegalArgumentException> { EmbeddingVector(listOf(Float.POSITIVE_INFINITY, 0.0f)) }
        shouldThrow<IllegalArgumentException> { EmbeddingVector(listOf(Float.NEGATIVE_INFINITY, 0.0f)) }
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
