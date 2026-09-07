package bidvector.adapters.extraction

import bidvector.procurement.ExtractedSourceField
import bidvector.procurement.FetchedDocument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private fun document(
    bytes: ByteArray,
    mediaType: String,
) = FetchedDocument(bytes, mediaType, "sha", Instant.EPOCH, sampleAttachmentUrl("https://example.test/spec"))

/** 정상 경로 — 요건 추출·요건 없음 주장·비지원 형식이 각각 옳게 갈린다. */
class HttpLlmRequirementExtractorTest {
    @Test
    fun `정상 응답은 Extracted 로 승격되고 provenance 를 남긴다`() {
        val script = listOf(FakeLlmResponse.Reply(200, validExtractionResponseJson(listOf("전기공사업"))))
        val server = FakeLlmServer.start(script)
        server.use {
            val attempt =
                buildExtractor(server, policy = testExtractionPolicy(chunkChars = 400)).extractDetailed(
                    document("면허: 전기공사업".toByteArray(), "text/plain"),
                )

            attempt.shouldBeInstanceOf<ExtractionAttempt.Extracted>()
            val extracted = attempt
            extracted.requirements.items
                .single()
                .licenseNames shouldBe listOf("전기공사업")
            extracted.requirements.items
                .single()
                .sourceField shouldBe ExtractedSourceField.LicenseLimitName
            extracted.provenance.single().modelId shouldBe "test-model"
            extracted.provenance.single().schemaVersion shouldBe REQUIREMENT_EXTRACTION_SCHEMA_VERSION
        }
    }

    @Test
    fun `모델이 요건 없음을 주장하면 assertedAbsent=true 로 승격된다`() {
        val server = FakeLlmServer.start(listOf(FakeLlmResponse.Reply(200, ASSERTED_ABSENT_RESPONSE_JSON)))
        server.use {
            val attempt =
                buildExtractor(server, policy = testExtractionPolicy(chunkChars = 400))
                    .extractDetailed(document("아무 요건 없음".toByteArray(), "text/plain"))

            attempt.shouldBeInstanceOf<ExtractionAttempt.Extracted>()
            attempt.requirements.assertedAbsent shouldBe true
            attempt.requirements.items shouldBe emptyList()
        }
    }

    @Test
    fun `지원하지 않는 문서 형식은 LLM 을 부르지 않고 Uncertain 이다`() {
        val server = FakeLlmServer.start(listOf(FakeLlmResponse.Reply(200, validExtractionResponseJson())))
        server.use {
            val attempt = buildExtractor(server).extractDetailed(document(byteArrayOf(1, 2, 3), "application/x-hwp"))

            attempt.shouldBeInstanceOf<ExtractionAttempt.Uncertain>()
            attempt.reason.shouldBeInstanceOf<ExtractionFailure.UnsupportedFormat>()
            server.requestCount shouldBe 0
        }
    }
}
