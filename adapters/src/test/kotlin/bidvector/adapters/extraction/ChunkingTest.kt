package bidvector.adapters.extraction

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/** ⑤ — chunk 예산이 chunk **수** 단위로 강제된다(우회 (6) — 문서 전체를 한 호출에 넣지 않는다). */
class ChunkingTest {
    @Test
    fun `빈 문서는 chunk 가 없다`() {
        val outcome = chunkDocumentText("", chunkChars = 10, maxChunks = 5)

        outcome shouldBe ChunkingOutcome.Chunks(emptyList())
    }

    @Test
    fun `chunkChars 단위로 문서를 나눈다`() {
        val outcome = chunkDocumentText("a".repeat(25), chunkChars = 10, maxChunks = 10)

        outcome.shouldBeInstanceOf<ChunkingOutcome.Chunks>()
        outcome.chunks.map { it.text.length } shouldBe listOf(10, 10, 5)
    }

    @Test
    fun `chunk 수가 상한을 넘으면 TooManyChunks 다`() {
        val outcome = chunkDocumentText("a".repeat(100), chunkChars = 10, maxChunks = 3)

        outcome shouldBe ChunkingOutcome.TooManyChunks
    }

    @Test
    fun `chunk 는 원문 구간을 보존한다`() {
        val outcome = chunkDocumentText("abcdefghij", chunkChars = 4, maxChunks = 10) as ChunkingOutcome.Chunks

        outcome.chunks[1].charStart shouldBe 4
        outcome.chunks[1].charEnd shouldBe 8
        outcome.chunks[1].text shouldBe "efgh"
    }
}
