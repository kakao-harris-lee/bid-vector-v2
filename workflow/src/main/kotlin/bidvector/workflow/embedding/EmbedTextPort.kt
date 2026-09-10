package bidvector.workflow.embedding

import bidvector.workflow.prediction.CallBudget

/**
 * 임베딩 gateway port(scope.md ①, ADR 0010 D-2) — `suspend`다. coroutine stub 을 동기
 * 경계로 접으면 취소 전파(D-2)가 끊긴다(설계 검토 구성 표, 4D-1 `BidPredictionPort` 관례).
 * [CallBudget]은 필수 인자다 — deadline 없는 호출은 이 시그니처에 없다(우회 (1) 차단).
 * [CallBudget]을 재사용하는 이유는 [bidvector.workflow.prediction.ModelReleaseRef]와 같다
 * ([EmbeddingOutcome] KDoc) — ML 호출 예산이라는 개념은 예측 전용이 아니다.
 */
fun interface EmbedTextPort {
    suspend fun embed(
        request: EmbedTextRequest,
        budget: CallBudget,
    ): EmbeddingOutcome
}
