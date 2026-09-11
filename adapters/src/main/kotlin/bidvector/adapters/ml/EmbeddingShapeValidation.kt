package bidvector.adapters.ml

import contract.bidvector.ml.v1.Embedding
import contract.bidvector.ml.v1.VectorNormalization
import java.math.BigDecimal
import java.math.MathContext

/**
 * 설계 검토 (3) — 검증층이 값 타입 `init` 보다 먼저 걸린다(4D-1 r2 G-1·G-2 의 교훈,
 * `isAcceptableSuccessShape`와 동형). `values.size == dimension`(우회 (1) 차원 불일치 축)·
 * L2 정규화 지정(우회 (2) UNSPECIFIED 정규화 축)·**유한성**(verifier F-1, 아래)·norm 이
 * `1 ± embedding.norm.epsilon` 안(2E `isAcceptableEmbedding` 관례, `EmbeddingContractTest`
 * 가 test 쪽에서 이미 고정한 규칙)·release 다섯 성분 비공백(우회 (3) release 공백
 * 축 — **우회 (6)이 아니다, 리뷰 F-D 정정**: (6)은 이 slice가 열어 둔 값 타입 위조
 * 축이다, `OPEN-4D2-VECTOR-FORGERY-AT-WIRING`)을 모두 통과해야 `EmbeddingVector`·
 * `ModelReleaseRef`를 짓는다. `dimension`은 계약이 수치를 정하지 않는다(모델이 바뀌면
 * 값이 바뀐다) — `GetEmbeddingMetadata.dimension`과의 대조는 하지 않는다(알려진 제한,
 * `exact_release`요청은 metadata 를 부르지 않는 4D-1 관례를 깨지 않기 위해서다 — evidence
 * 에 근거를 남긴다).
 *
 * **verifier F-1(high) — 비유한 값이 예외로 새던 것을 구조로 닫는다.** `BigDecimal(Double)`
 * 은 NaN·Infinity에 `NumberFormatException`을 던진다. 이전 형태(`listOf(...).all { it }`)는
 * 네 검사를 **전부 즉시 평가**했으므로 앞 항이 이미 false여도 `isL2Normalized`가 호출되어
 * NaN·Infinity가 그 변환에 도달했다(proto3 `repeated float`는 이 값을 정상 wire 값으로
 * 나른다). 처방은 가지를 더 얹는 것이 아니라 **`&&` 단락 평가로 순서 자체를 관문으로
 * 세우는 것**이다 — `values.all { it.isFinite() }`를 `isL2Normalized` 호출 **앞에** 두어,
 * 그 함수가 실행되는 시점에는 이미 전 성분이 유한함이 구조로 보장된다(비유한 값은 이
 * 항에서 `false`로 걸려 뒤 항을 아예 평가하지 않는다 — `try`/`catch`로 잡는 것이 아니라
 * 도달 자체를 막는다).
 */
internal fun isAcceptableEmbeddingShape(embedding: Embedding): Boolean =
    embedding.valuesCount == embedding.dimension &&
        embedding.normalization == VectorNormalization.VECTOR_NORMALIZATION_L2 &&
        embedding.valuesList.all { it.isFinite() } &&
        isL2Normalized(embedding.valuesList) &&
        hasNonBlankRelease(embedding.release)

/** 호출 시점에는 [isAcceptableEmbeddingShape]의 유한성 관문을 이미 통과한 값만 들어온다. */
private fun isL2Normalized(values: List<Float>): Boolean {
    val sumOfSquares =
        values.fold(BigDecimal.ZERO) { acc, value ->
            val component = BigDecimal(value.toDouble(), MathContext.DECIMAL64)
            acc.add(component.multiply(component))
        }
    val norm = sumOfSquares.sqrt(MathContext.DECIMAL64)
    return norm.subtract(BigDecimal.ONE).abs() <= EMBEDDING_NORM_EPSILON
}
