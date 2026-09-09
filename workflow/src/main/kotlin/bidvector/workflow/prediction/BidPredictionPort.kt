package bidvector.workflow.prediction

/**
 * ML 투찰율 후보 gateway port(scope.md ①, ADR 0010 D-2) — `suspend`다. coroutine stub 을
 * 동기 경계로 접으면 취소 전파(D-2)가 끊긴다(설계 검토 D-4D-2). [CallBudget]은 필수
 * 인자다 — deadline 없는 호출은 이 시그니처에 없다(우회 (1) 차단).
 */
fun interface BidPredictionPort {
    suspend fun predict(
        request: BidPredictionRequest,
        budget: CallBudget,
    ): BidPredictionOutcome
}
