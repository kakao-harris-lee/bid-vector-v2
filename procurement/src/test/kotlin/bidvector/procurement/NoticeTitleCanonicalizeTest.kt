package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D-6F8-2(M6/6F-8) — 공고명은 **필드 계약 데이터**가 나른다(D-6F4-6). `canonicalize` 는 키 문자열을
 * 모르고 `FieldConcept.NOTICE_TITLE` 계약이 가리키는 키에서만 읽는다. 값이 없으면 `null` 이다
 * (D-6F4-8 — 센티넬 금지).
 */
class NoticeTitleCanonicalizeTest {
    private val operationalPolicy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value

    private fun titleOf(
        fields: Map<String, String>,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): NoticeTitle? {
        val identified = mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000") + fields
        val outcome = canonicalize(observationOf(identified), policy)
        return outcome.shouldBeInstanceOf<CanonicalizationOutcome.Normalized>().command.title
    }

    @Test
    fun `운영 정책 계약이 공고명을 나른다 — 원문은 trim 만 한다`() {
        titleOf(mapOf("bidNtceNm" to "  2026년 정보시스템 유지보수 용역 ")) shouldBe
            NoticeTitle.of("2026년 정보시스템 유지보수 용역")
    }

    @Test
    fun `공고명 키가 부재하면 null 이다`() {
        titleOf(emptyMap()) shouldBe null
    }

    @Test
    fun `공고명이 공백뿐이면 null 이다 — 센티넬을 만들지 않는다`() {
        titleOf(mapOf("bidNtceNm" to " 　\t ")) shouldBe null
    }

    @Test
    fun `NOTICE_TITLE 계약이 없는 정책은 키가 원문에 있어도 공고명을 낳지 않는다`() {
        val withoutTitle =
            operationalPolicy.copy(
                fieldContracts =
                    KonepsFieldContractRegistry.of(
                        operationalPolicy.fieldContracts.contracts.filterNot {
                            it.concept == FieldConcept.NOTICE_TITLE
                        },
                    ),
            )

        titleOf(mapOf("bidNtceNm" to "계약 없는 공고명"), withoutTitle) shouldBe null
    }

    @Test
    fun `공고명은 계약이 가리키는 키에서만 읽는다 — canonicalize 에 키 리터럴이 없다`() {
        val isTitle = { contract: KonepsFieldContract -> contract.concept == FieldConcept.NOTICE_TITLE }
        val retargeted =
            operationalPolicy.fieldContracts.contracts.map { contract ->
                if (isTitle(contract)) contract.copy(rawName = RawKey("otherTitleKey")) else contract
            }
        val policy = operationalPolicy.copy(fieldContracts = KonepsFieldContractRegistry.of(retargeted))

        titleOf(mapOf("bidNtceNm" to "옛 키", "otherTitleKey" to "새 키"), policy) shouldBe NoticeTitle.of("새 키")
        titleOf(mapOf("bidNtceNm" to "옛 키"), policy) shouldBe null
    }

    @Test
    fun `운영 정책의 NOTICE_TITLE 계약은 공고 목록 오퍼레이션의 선택 필드 하나다`() {
        val contracts = operationalPolicy.fieldContracts.contractsFor(FieldConcept.NOTICE_TITLE)

        contracts.map { it.rawName } shouldContainExactly listOf(RawKey("bidNtceNm"))
        contracts.single().presentIn shouldBe setOf(SourceEndpoint.NOTICE_LIST)
        contracts.single().scale shouldBe FieldScale.OPAQUE_TEXT
        contracts.single().nullability shouldBe FieldNullability.OPTIONAL
        contracts.single().authoritative shouldBe true
        contracts.single().effectiveFrom shouldBe EffectiveFrom.Initial
    }
}
