package bidvector.workflow.embedding

import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.ModelReleaseSelector

/**
 * `EmbedText` 요청(scope.md ①) — **도메인 값만** 나른다. 호출부(4B-6)가 조립하고 어댑터가
 * 계약 DTO로만 매핑한다(제3 변환 금지, ADR 0010 D-3). `releaseSelector`는
 * [bidvector.workflow.prediction.ModelReleaseSelector]를 재사용한다 — release 선택자는
 * 예측 전용 개념이 아니라 `PredictionEnvelope`(common.proto)가 두 RPC 모두에 공유하는
 * 계약 축이다(D-4D2-1 「port 모양은 계약에서 나온다」).
 */
data class EmbedTextRequest(
    val text: String,
    val kind: TextKind,
    val releaseSelector: ModelReleaseSelector,
    val correlationId: CorrelationId,
) {
    init {
        require(text.isNotBlank()) { "EmbedTextRequest.text는 빈 문자열일 수 없다" }
    }
}
