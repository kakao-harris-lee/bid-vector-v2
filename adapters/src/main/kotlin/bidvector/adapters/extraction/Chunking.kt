package bidvector.adapters.extraction

/** chunk 하나 — 원문 문자 구간을 그대로 보존해 provenance 가 가리킬 수 있게 한다. */
data class DocumentChunk(
    val index: Int,
    val text: String,
    val charStart: Int,
    val charEnd: Int,
)

/**
 * chunk 분할 결과(⑤) — 문서가 정책 상한(`maxChunksPerDocument`)을 넘는 chunk 수를
 * 요구하면 [TooManyChunks]로 낸다(부분 chunk 만 처리해 부분 결과를 승격하지 않는다,
 * 위협 모델 방어 (c)).
 */
sealed interface ChunkingOutcome {
    data class Chunks(
        val chunks: List<DocumentChunk>,
    ) : ChunkingOutcome

    data object TooManyChunks : ChunkingOutcome
}

/**
 * 문서 원문을 `chunkChars` 단위로 나눈다(⑤ 「chunk 를 나누지 않고 문서 전체를 한 호출에」
 * 우회 (6) 방어 — chunk **수** 예산은 이 함수가 강제한다). **정정(verifier r1 F-4(b))**:
 * 호출 수 상한은 [ResilientLlmCall]이 아니라 `HttpLlmRequirementExtractor.prepareChunks`
 * 의 별도 검사(`chunks.size > maxCallsPerDocument`)가 강제한다 — `ResilientLlmCall`(breaker
 * + time limiter)에는 호출 계수기가 없다. `maxChunksPerDocument`와 `maxCallsPerDocument`가
 * 같은 값이면 그 별도 검사는 이 함수가 이미 보장한 조건이라 도달 불가가 된다(둘을 다르게
 * 두는 정책 인스턴스에서만 실제로 걸린다, `ExtractionFailOpenTest` 실행 증거).
 * 문자 경계로만 나누고 문장·토큰 경계는 보지 않는다(측정된 필요 없음, 최소 구현).
 */
fun chunkDocumentText(
    text: String,
    chunkChars: Int,
    maxChunks: Int,
): ChunkingOutcome {
    if (text.isEmpty()) return ChunkingOutcome.Chunks(emptyList())
    val chunks =
        text
            .indices
            .filter { it % chunkChars == 0 }
            .mapIndexed { index, start ->
                val end = minOf(start + chunkChars, text.length)
                DocumentChunk(index, text.substring(start, end), start, end)
            }
    return if (chunks.size > maxChunks) ChunkingOutcome.TooManyChunks else ChunkingOutcome.Chunks(chunks)
}
