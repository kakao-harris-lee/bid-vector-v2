package bidvector.workflow.embedding

import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.ModelReleaseSelector

/** workflow/embedding test 전용 fixture(4D-1 `testBidPredictionRequest` 관례). */
internal fun testEmbedTextRequest(
    text: String = "공고 원문 합성 텍스트",
    kind: TextKind = TextKind.NOTICE,
    releaseSelector: ModelReleaseSelector = ModelReleaseSelector.LatestPromoted,
    correlationId: CorrelationId = CorrelationId("test-correlation-id"),
): EmbedTextRequest = EmbedTextRequest(text, kind, releaseSelector, correlationId)
