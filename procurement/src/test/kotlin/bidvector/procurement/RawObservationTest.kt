package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val NOW = Instant.parse("2026-09-07T00:00:00Z")

private fun contract(rawName: String): KonepsFieldContract =
    KonepsFieldContract.of(
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

    // D-6F9-1 — 대분류는 응답 필드가 아니라 수집 오퍼레이션이 정한다. 관측이 구조로 나른다(URL 을 파싱하지 않는다).
    @Test
    fun `관측은 수집 오퍼레이션의 대분류를 나른다 — 기본은 null 이고 두 생성 경로 모두 실을 수 있다`() {
        val fields = mapOf(RawKey("k") to "v")

        RawNoticeObservation.of(fields, SourceEndpoint.NOTICE_LIST, NOW).sourceDivision shouldBe null
        RawNoticeObservation
            .of(fields, SourceEndpoint.NOTICE_LIST, NOW, sourceDivision = BusinessDivision.SERVICE)
            .sourceDivision shouldBe BusinessDivision.SERVICE
        RawNoticeObservation
            .ofRawValues(
                mapOf(RawKey("k") to RawValue.ExplicitNull),
                SourceEndpoint.NOTICE_LIST,
                NOW,
                sourceText = null,
                sourceDivision = BusinessDivision.CONSTRUCTION,
            ).sourceDivision shouldBe BusinessDivision.CONSTRUCTION
    }

    @Test
    fun `대분류가 다른 관측은 같은 원문이어도 동등하지 않다`() {
        val fields = mapOf(RawKey("k") to "v")

        fun observationOf(division: BusinessDivision?) =
            RawNoticeObservation.of(fields, SourceEndpoint.NOTICE_LIST, NOW, sourceDivision = division)

        (observationOf(BusinessDivision.SERVICE) == observationOf(BusinessDivision.CONSTRUCTION)) shouldBe false
        (observationOf(BusinessDivision.SERVICE) == observationOf(null)) shouldBe false
        observationOf(BusinessDivision.SERVICE) shouldBe observationOf(BusinessDivision.SERVICE)
        observationOf(BusinessDivision.SERVICE).hashCode() shouldBe observationOf(BusinessDivision.SERVICE).hashCode()
    }

    @Test
    fun `RawKey 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { RawKey("") }
    }

    // v2-defect(3A 잔여 일괄 verifier r3 전 수정, koneps-collection-003·004) — 명시 null 과
    // 키 부재를 presenceOf 로 구분한다. valueOf 는 하위호환으로 둘 다 null 로 접는다.
    @Test
    fun `presenceOf 는 명시 null 과 키 부재를 구분한다`() {
        val observation =
            RawNoticeObservation.ofRawValues(
                mapOf(RawKey("presmptPrce") to RawValue.ExplicitNull),
                SourceEndpoint.NOTICE_LIST,
                NOW,
                sourceText = null,
                sourceDivision = null,
            )

        observation.presenceOf(contract("presmptPrce")) shouldBe FieldPresence.ExplicitNull
        observation.presenceOf(contract("otherKey")) shouldBe FieldPresence.Missing
        observation.valueOf(contract("presmptPrce")) shouldBe null
    }

    @Test
    fun `presenceOf 는 문자열 값이 있으면 Present 를 낸다`() {
        val observation =
            RawNoticeObservation.ofRawValues(
                mapOf(RawKey("presmptPrce") to RawValue.Present("900000000")),
                SourceEndpoint.NOTICE_LIST,
                NOW,
                sourceText = null,
                sourceDivision = null,
            )

        observation.presenceOf(contract("presmptPrce")) shouldBe FieldPresence.Present("900000000")
    }

    @Test
    fun `ofRawValues 로 만든 관측도 keys 에 명시 null 키를 포함한다`() {
        val observation =
            RawNoticeObservation.ofRawValues(
                mapOf(RawKey("presmptPrce") to RawValue.ExplicitNull),
                SourceEndpoint.NOTICE_LIST,
                NOW,
                sourceText = null,
                sourceDivision = null,
            )

        observation.keys shouldBe setOf(RawKey("presmptPrce"))
    }
}
