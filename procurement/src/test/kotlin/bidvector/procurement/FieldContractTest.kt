package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private fun wonContract(
    rawName: String,
    concept: FieldConcept = FieldConcept.BASE_AMOUNT,
    provenanceTemplate: FieldProvenanceTemplate = FieldProvenanceTemplate.PUBLISHED,
): KonepsFieldContract =
    KonepsFieldContract(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = FieldScale.WON_INTEGER,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        authoritative = true,
        presentIn = setOf(SourceEndpoint.NOTICE_LIST),
        provenanceTemplate = provenanceTemplate,
        effectiveFrom = EffectiveFrom.Initial,
        expectedRange = null,
        sourceZone = null,
    )

/** ④ — 소비되는 모든 키에 필수, 등재율 낮음의 재발을 막는 타입 구조. */
class FieldContractTest {
    @Test
    fun `presentIn 이 비어 있으면 계약 구성 자체가 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            wonContract("bssAmt").copy(presentIn = emptySet())
        }
    }

    @Test
    fun `sourceZone 은 DATETIME_NO_ZONE 계약에만 있어야 한다`() {
        shouldThrow<IllegalArgumentException> {
            wonContract("bssAmt").copy(sourceZone = SourceZoneRuleId.ASSUME_KST)
        }
        shouldThrow<IllegalArgumentException> {
            wonContract("rgstDt").copy(scale = FieldScale.DATETIME_NO_ZONE, sourceZone = null)
        }
    }

    @Test
    fun `레지스트리는 중복 rawName 등재를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            KonepsFieldContractRegistry(listOf(wonContract("bssAmt"), wonContract("bssAmt")))
        }
    }

    @Test
    fun `contractFor 는 등재되지 않은 키에 null 을 낸다 — 미지 필드는 조용히 소비되지 않는다`() {
        val registry = KonepsFieldContractRegistry(listOf(wonContract("bssAmt")))

        registry.contractFor(RawKey("unknownKey")) shouldBe null
    }

    @Test
    fun `unknownKeysIn 은 레지스트리에 없는 raw 키를 낸다 — COL-07 acceptance`() {
        val registry = KonepsFieldContractRegistry(listOf(wonContract("bssAmt")))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bssAmt") to "1000", RawKey("mysteryField") to "?"),
                SourceEndpoint.NOTICE_LIST,
                java.time.Instant.EPOCH,
            )

        registry.unknownKeysIn(observation) shouldBe setOf(RawKey("mysteryField"))
    }

    @Test
    fun `contractsFor 는 개념별 계약만 낸다`() {
        val registry =
            KonepsFieldContractRegistry(
                listOf(
                    wonContract("bssAmt", concept = FieldConcept.BASE_AMOUNT),
                    wonContract("presmptPrce", concept = FieldConcept.ESTIMATED_AMOUNT),
                ),
            )

        registry.contractsFor(FieldConcept.BASE_AMOUNT).map { it.rawName } shouldBe listOf(RawKey("bssAmt"))
    }
}
