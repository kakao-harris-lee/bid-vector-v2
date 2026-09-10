package bidvector.adapters.ml

import contract.bidvector.ml.v1.Embedding
import contract.bidvector.ml.v1.VectorNormalization
import java.math.BigDecimal
import java.math.MathContext

/**
 * 설계 검토 (3) — 검증층이 값 타입 `init` 보다 먼저 걸린다(4D-1 r2 G-1·G-2 의 교훈,
 * `isAcceptableSuccessShape`와 동형). `values.size == dimension`(우회 (1))·L2 정규화
 * 지정(우회 (2))·norm 이 `1 ± embedding.norm.epsilon` 안(2E `isAcceptableEmbedding` 관례,
 * `EmbeddingContractTest`가 test 쪽에서 이미 고정한 규칙)·release 다섯 성분 비공백(우회
 * (6))을 모두 통과해야 `EmbeddingVector`·`ModelReleaseRef`를 짓는다. `dimension`은 계약이
 * 수치를 정하지 않는다(모델이 바뀌면 값이 바뀐다) — `GetEmbeddingMetadata.dimension`과의
 * 대조는 하지 않는다(알려진 제한, `exact_release`요청은 metadata 를 부르지 않는 4D-1
 * 관례를 깨지 않기 위해서다 — evidence 에 근거를 남긴다).
 */
internal fun isAcceptableEmbeddingShape(embedding: Embedding): Boolean {
    val checks =
        listOf(
            embedding.valuesCount == embedding.dimension,
            embedding.normalization == VectorNormalization.VECTOR_NORMALIZATION_L2,
            isL2Normalized(embedding.valuesList),
            hasNonBlankRelease(embedding),
        )
    return checks.all { it }
}

private fun isL2Normalized(values: List<Float>): Boolean {
    val sumOfSquares =
        values.fold(BigDecimal.ZERO) { acc, value ->
            val component = BigDecimal(value.toDouble(), MathContext.DECIMAL64)
            acc.add(component.multiply(component))
        }
    val norm = sumOfSquares.sqrt(MathContext.DECIMAL64)
    return norm.subtract(BigDecimal.ONE).abs() <= EMBEDDING_NORM_EPSILON
}
