package bidvector.workflow.embedding

import bidvector.workflow.prediction.ModelReleaseRef

/**
 * `EmbedTextPort.embed`의 결과(scope.md ①, ADR 0010 D-3) — 두 가지뿐이다(`embedding.proto`
 * `EmbedTextResponse` 주석 "결과 봉투 둘뿐 — 임베딩에는 측정 불가인 도메인 결과가 없다").
 * `Unmeasurable` 가지가 없다 — 4D-1 `BidPredictionOutcome`과 다른 축이다. `Embedded`는
 * 성공한 호출의 답, `Unavailable`은 어댑터가 답을 얻지 못했다는 것이다. 둘을 값·기본값으로
 * 접지 않는다 — 어댑터 밖으로 예외가 나가지 않는다(위협 모델 (c)).
 */
sealed interface EmbeddingOutcome {
    /**
     * `release`는 [bidvector.workflow.prediction.ModelReleaseRef]를 재사용한다 — `ModelRelease`
     * (prediction.proto)를 `Embedding`(embedding.proto)이 그대로 공유하는 같은 계약 타입이라
     * provenance 다섯 성분 불변식을 중복해서 열지 않는다.
     */
    data class Embedded(
        val vector: EmbeddingVector,
        val release: ModelReleaseRef,
    ) : EmbeddingOutcome

    data class Unavailable(
        val reason: EmbeddingUnavailableReason,
    ) : EmbeddingOutcome
}
