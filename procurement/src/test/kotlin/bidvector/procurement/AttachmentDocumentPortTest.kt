package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val CONTRACT =
    KonepsFieldContract.of(
        rawName = RawKey("ntceSpecDocUrl1"),
        concept = FieldConcept.NOTICE_NUMBER,
        basis = null,
        scale = FieldScale.OPAQUE_TEXT,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        authoritative = false,
        presentIn = setOf(SourceEndpoint.NOTICE_DETAIL),
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        effectiveFrom = EffectiveFrom.Initial,
    )

/**
 * M3/3C ① — [AttachmentUrl]은 등재된 [KonepsFieldContract]가 없으면 만들 수 없다(위협
 * 모델 우회 (7)). 실제 `ntceSpecDocUrl1` 계약 등재는 `OPEN-3C-ATTACHMENT-FIELD-CONTRACT`
 * (CollectionPolicy.kt 편집은 이 slice 의 in_scope 밖) — 이 test 는 계약의 **형태**(생성
 * 함수가 [RawNoticeObservation.valueOf]로만 값을 읽는다는 메커니즘)만 증명한다.
 */
class AttachmentDocumentPortTest {
    @Test
    fun `등재된 계약과 값이 있으면 AttachmentUrl 을 만든다`() {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("ntceSpecDocUrl1") to "https://example.test/spec.pdf"),
                SourceEndpoint.NOTICE_DETAIL,
                Instant.EPOCH,
            )

        val url = AttachmentUrl.from(observation, CONTRACT)

        url.shouldNotBeNull()
        url.value shouldBe "https://example.test/spec.pdf"
    }

    @Test
    fun `계약이 있어도 관측에 값이 없으면 null 이다 — 미등재 키를 임의로 읽지 않는다`() {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to "20260101001"),
                SourceEndpoint.NOTICE_LIST,
                Instant.EPOCH,
            )

        AttachmentUrl.from(observation, CONTRACT).shouldBeNull()
    }

    @Test
    fun `AttachmentFetchLimits 는 0 이하 maxBytes 를 거부한다`() {
        shouldThrow<IllegalArgumentException> { AttachmentFetchLimits(0, Duration.ofSeconds(1)) }
    }

    @Test
    fun `FetchedDocument 의 동일성은 bytes 참조가 아니라 sha256 을 본다`() {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("ntceSpecDocUrl1") to "u"),
                SourceEndpoint.NOTICE_DETAIL,
                Instant.EPOCH,
            )
        val url = AttachmentUrl.from(observation, CONTRACT)!!
        val a = FetchedDocument("x".toByteArray(), "text/plain", "same", Instant.EPOCH, url)
        val b = FetchedDocument("y".toByteArray(), "text/plain", "same", Instant.EPOCH, url)

        a shouldBe b
    }
}
