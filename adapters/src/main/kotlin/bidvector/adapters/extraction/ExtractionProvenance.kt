package bidvector.adapters.extraction

import java.time.Instant

/** 추출 요건이 원문 어느 chunk 의 어느 구간에서 왔는가 — 원문을 복사하지 않고 구간만 싣는다. */
data class ExtractionEvidenceLocation(
    val chunkIndex: Int,
    val charStart: Int,
    val charEnd: Int,
)

/**
 * 추출 provenance(⑥) — 원문 chunk 는 [ExtractionEvidenceLocation]이 가리킬 뿐 값에 복사되지
 * 않는다. `modelId`·`promptVersion`·`schemaVersion`이 어느 버전으로 이 결과가 났는지를
 * 고정한다(D-3C-7). 응답 원문([LlmResponse])은 이 타입에 담기지 않는다(방어 (e)).
 */
data class ExtractionProvenance(
    val documentSha256: String,
    val sourceUrl: String,
    val evidence: ExtractionEvidenceLocation,
    val schemaVersion: String,
    val modelId: String,
    val promptVersion: String,
    val extractedAt: Instant,
)
