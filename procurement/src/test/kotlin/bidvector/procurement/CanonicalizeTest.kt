package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private fun testContract(
    rawName: String,
    concept: FieldConcept,
    scale: FieldScale,
    provenanceTemplate: FieldProvenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
): KonepsFieldContract =
    KonepsFieldContract(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = scale,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        authoritative = true,
        presentIn = setOf(SourceEndpoint.NOTICE_LIST),
        provenanceTemplate = provenanceTemplate,
        effectiveFrom = EffectiveFrom.Initial,
        expectedRange = null,
        sourceZone = null,
    )

/** legacy-behavior — test 전용 정책 인스턴스(조사 b-4). main `KONEPS_COLLECTION_POLICY`는 형태만 갖는다. */
private val TEST_REGISTRY =
    KonepsFieldContractRegistry(
        listOf(
            testContract("bidNtceNo", FieldConcept.NOTICE_NUMBER, FieldScale.IDENTIFIER),
            testContract("bidNtceOrd", FieldConcept.NOTICE_ROUND, FieldScale.IDENTIFIER),
            testContract("bssAmt", FieldConcept.BASE_AMOUNT, FieldScale.WON_INTEGER, FieldProvenanceTemplate.PUBLISHED),
            testContract(
                "bdgtAmt",
                FieldConcept.ALLOCATED_BUDGET,
                FieldScale.WON_INTEGER,
                FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY,
            ),
            testContract(
                "presmptPrce",
                FieldConcept.ESTIMATED_AMOUNT,
                FieldScale.WON_INTEGER,
                FieldProvenanceTemplate.PUBLISHED,
            ),
            testContract(
                "wrongScaleAmt",
                FieldConcept.BASE_AMOUNT,
                FieldScale.PERCENT,
                FieldProvenanceTemplate.PUBLISHED,
            ),
        ),
    )

private val TEST_POLICY =
    KonepsCollectionPolicyData(
        fieldContracts = TEST_REGISTRY,
        resultCodeCategories = emptyList(),
        baseAmountResolutionOrder = listOf(RawKey("bssAmt"), RawKey("bdgtAmt")),
        estimatedPriceResolutionOrder = listOf(RawKey("presmptPrce")),
        dateInterpretation = SourceZoneRuleId.ASSUME_KST,
        detailFetchGates = DetailFetchGates(24, 48),
    )

private fun observationOf(fields: Map<String, String>): RawNoticeObservation =
    RawNoticeObservation.of(fields.mapKeys { RawKey(it.key) }, SourceEndpoint.NOTICE_LIST, Instant.EPOCH)

/** ⑤ — provenance 해석 지점 하나. */
class ResolveAmountTest {
    @Test
    fun `첫 후보가 값을 가지면 Published 로 해석한다`() {
        val observation = observationOf(mapOf("bssAmt" to "1,000,000", "bdgtAmt" to "500000"))

        val outcome =
            resolveAmount(observation, NoticeRound.of("000"), TEST_POLICY.baseAmountResolutionOrder, TEST_REGISTRY)

        outcome shouldBe
            AmountResolutionOutcome.Resolved(RawKey("bssAmt"), 1_000_000L, Provenance.Published(NoticeRound.of("000")))
    }

    @Test
    fun `0 이나 결측 후보는 건너뛰고 다음 후보로 폴백한다 — §5-2`() {
        val observation = observationOf(mapOf("bssAmt" to "0", "bdgtAmt" to "500000"))

        val outcome =
            resolveAmount(observation, NoticeRound.of("000"), TEST_POLICY.baseAmountResolutionOrder, TEST_REGISTRY)

        outcome shouldBe
            AmountResolutionOutcome.Resolved(RawKey("bdgtAmt"), 500_000L, Provenance.FilledFromBudgetKey("bdgtAmt"))
    }

    @Test
    fun `모든 후보가 없거나 0 이면 Unresolved 다`() {
        val observation = observationOf(mapOf("bssAmt" to "0"))

        val outcome =
            resolveAmount(observation, NoticeRound.of("000"), TEST_POLICY.baseAmountResolutionOrder, TEST_REGISTRY)

        outcome shouldBe AmountResolutionOutcome.Unresolved
    }

    @Test
    fun `scale 이 WON_INTEGER 가 아니면 항목을 거부한다 — 다음 후보로 넘어가지 않는다`() {
        val observation = observationOf(mapOf("wrongScaleAmt" to "87.995"))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), listOf(RawKey("wrongScaleAmt")), TEST_REGISTRY)

        outcome shouldBe
            AmountResolutionOutcome.Rejected(
                RawKey("wrongScaleAmt"),
                CollectionDropReason.CollectionContractViolation(ContractViolationAxis.SCALE),
            )
    }

    @Test
    fun `숫자로 파싱되지 않는 값은 ParseFailure 다`() {
        val observation = observationOf(mapOf("bssAmt" to "not-a-number"))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), listOf(RawKey("bssAmt")), TEST_REGISTRY)

        outcome shouldBe
            AmountResolutionOutcome.Rejected(
                RawKey("bssAmt"),
                CollectionDropReason.CollectionParseFailure(ParseFailureKind.NUMERIC),
            )
    }

    @Test
    fun `등재되지 않은 순서 항목은 건너뛴다 — 정책 구성 오류를 방어적으로 흡수`() {
        val observation = observationOf(mapOf("bdgtAmt" to "500000"))

        val outcome =
            resolveAmount(
                observation,
                NoticeRound.of("000"),
                listOf(RawKey("neverRegistered"), RawKey("bdgtAmt")),
                TEST_REGISTRY,
            )

        outcome.shouldBeInstanceOf<AmountResolutionOutcome.Resolved>()
    }
}

/** ② — 원문↔command 변환은 [canonicalize] 한 함수다. */
class CanonicalizeTest {
    @Test
    fun `공고번호와 차수가 있으면 정규화 성공을 낸다`() {
        val observation =
            observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "bssAmt" to "1000000"))

        val outcome = canonicalize(observation, TEST_POLICY)

        outcome.shouldBeInstanceOf<CanonicalizationOutcome.Normalized>()
        outcome.command.id shouldBe NoticeId(NoticeNumber.of("20260101001"), NoticeRound.of("000"))
        outcome.command.baseAmount.shouldBeInstanceOf<ResolvedBaseAmount.Direct>()
        outcome.command.raw shouldBe observation
    }

    @Test
    fun `공고번호가 없으면 MissingNoticeNumber 로 탈락한다`() {
        val observation = observationOf(mapOf("bidNtceOrd" to "000"))

        val outcome = canonicalize(observation, TEST_POLICY)

        outcome shouldBe CanonicalizationOutcome.Dropped(CollectionDropReason.CollectionMissingNoticeNumber, 0)
    }

    @Test
    fun `미지 키는 unknownFieldCount 로 계수되고 canonical 값으로 소비되지 않는다 — COL-07`() {
        val observation =
            observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "mysteryField" to "?"))

        val outcome = canonicalize(observation, TEST_POLICY)

        outcome.unknownFieldCount shouldBe 1
    }

    @Test
    fun `기초금액이 계약 위반이면 항목 전체가 탈락한다`() {
        val observation =
            observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "bssAmt" to "not-a-number"))

        val outcome = canonicalize(observation, TEST_POLICY)

        outcome.shouldBeInstanceOf<CanonicalizationOutcome.Dropped>()
        outcome.reason shouldBe CollectionDropReason.CollectionParseFailure(ParseFailureKind.NUMERIC)
    }

    @Test
    fun `추정가격 예산 폴백은 예산 키로 해석된다`() {
        val observation =
            observationOf(
                mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "bssAmt" to "0", "bdgtAmt" to "500000"),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.baseAmount.shouldBeInstanceOf<ResolvedBaseAmount.FallbackFromBudget>()
    }
}

/** 우회 후보 (11) — 정책 구성이 스스로 규율을 어기면 거부한다. */
class KonepsCollectionPolicyDataTest {
    @Test
    fun `추정가격 해석 순서에 기초금액 키를 넣으면 정책 구성이 실패한다 — §5-2`() {
        shouldThrow<IllegalArgumentException> {
            TEST_POLICY.copy(estimatedPriceResolutionOrder = listOf(RawKey("bssAmt")))
        }
    }

    @Test
    fun `해석 순서에 등재되지 않은 키가 있으면 정책 구성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            TEST_POLICY.copy(baseAmountResolutionOrder = listOf(RawKey("neverRegistered")))
        }
    }

    @Test
    fun `resultCodeCategories 중복 코드는 정책 구성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            TEST_POLICY.copy(
                resultCodeCategories =
                    listOf(
                        ResultCodeCategoryEntry("08", ResultCodeCategory.INPUT_ERROR),
                        ResultCodeCategoryEntry("08", ResultCodeCategory.RETRYABLE),
                    ),
            )
        }
    }
}
