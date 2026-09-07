package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val NOW = Instant.parse("2026-09-07T00:00:00Z")

private fun contract(rawName: String): KonepsFieldContract =
    KonepsFieldContract(
        rawName = RawKey(rawName),
        concept = FieldConcept.BASE_AMOUNT,
        basis = null,
        scale = FieldScale.WON_INTEGER,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        authoritative = true,
        presentIn = setOf(SourceEndpoint.NOTICE_LIST),
        provenanceTemplate = FieldProvenanceTemplate.PUBLISHED,
        effectiveFrom = EffectiveFrom.Initial,
        expectedRange = null,
        sourceZone = null,
    )

/** ②④ — 원문은 감사를 위해 보존하되, 값 취득은 계약을 인자로 요구하는 [RawNoticeObservation.valueOf] 하나뿐이다. */
class RawObservationTest {
    @Test
    fun `keys 는 관측이 가진 raw 키 전체를 낸다`() {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bssAmt") to "1,000,000", RawKey("unknownKey") to "x"),
                SourceEndpoint.NOTICE_LIST,
                NOW,
            )

        observation.keys shouldBe setOf(RawKey("bssAmt"), RawKey("unknownKey"))
    }

    @Test
    fun `valueOf 는 계약의 rawName 으로만 값을 낸다 — 우회 (1)(10) Map 을 직접 인덱싱할 수 없다`() {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bssAmt") to "1,000,000"),
                SourceEndpoint.NOTICE_LIST,
                NOW,
            )

        observation.valueOf(contract("bssAmt")) shouldBe "1,000,000"
        observation.valueOf(contract("otherKey")) shouldBe null
    }

    @Test
    fun `같은 fields·sourceEndpoint·observedAt 은 값으로 동등하다`() {
        val a = RawNoticeObservation.of(mapOf(RawKey("k") to "v"), SourceEndpoint.NOTICE_LIST, NOW)
        val b = RawNoticeObservation.of(mapOf(RawKey("k") to "v"), SourceEndpoint.NOTICE_LIST, NOW)

        a shouldBe b
    }

    @Test
    fun `RawKey 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { RawKey("") }
    }
}
