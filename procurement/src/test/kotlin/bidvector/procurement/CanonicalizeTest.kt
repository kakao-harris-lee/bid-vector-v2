package bidvector.procurement

import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

private fun testContract(
    rawName: String,
    concept: FieldConcept,
    scale: FieldScale,
    basis: Basis? = null,
    provenanceTemplate: FieldProvenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
): KonepsFieldContract =
    KonepsFieldContract.of(
        rawName = RawKey(rawName),
        concept = concept,
        basis = basis,
        scale = scale,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        authoritative = true,
        presentIn = setOf(SourceEndpoint.NOTICE_LIST),
        provenanceTemplate = provenanceTemplate,
        effectiveFrom = EffectiveFrom.Initial,
    )

/** legacy-behavior — test 전용 정책 인스턴스(조사 b-4). main `KONEPS_COLLECTION_POLICY`는 형태만 갖는다. */
private val TEST_REGISTRY =
    KonepsFieldContractRegistry.of(
        listOf(
            testContract("bidNtceNo", FieldConcept.NOTICE_NUMBER, FieldScale.IDENTIFIER),
            testContract("bidNtceOrd", FieldConcept.NOTICE_ROUND, FieldScale.IDENTIFIER),
            testContract(
                "bssAmt",
                FieldConcept.BASE_AMOUNT,
                FieldScale.WON_INTEGER,
                Basis.BASE_AMOUNT,
                FieldProvenanceTemplate.PUBLISHED,
            ),
            testContract(
                "bdgtAmt",
                FieldConcept.ALLOCATED_BUDGET,
                FieldScale.WON_INTEGER,
                Basis.ALLOCATED_BUDGET,
                FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY,
            ),
            testContract(
                "presmptPrce",
                FieldConcept.ESTIMATED_AMOUNT,
                FieldScale.WON_INTEGER,
                Basis.ESTIMATED,
                FieldProvenanceTemplate.PUBLISHED,
            ),
            testContract(
                "wrongScaleAmt",
                FieldConcept.BASE_AMOUNT,
                FieldScale.PERCENT,
                Basis.BASE_AMOUNT,
                FieldProvenanceTemplate.PUBLISHED,
            ),
            testContract(
                "wrongBasisAmt",
                FieldConcept.BASE_AMOUNT,
                FieldScale.WON_INTEGER,
                Basis.ESTIMATED,
                FieldProvenanceTemplate.PUBLISHED,
            ),
            testContract("bsnsDivCd", FieldConcept.BUSINESS_CATEGORY_CODE, FieldScale.OPAQUE_TEXT),
            testContract("bsnsDivNm", FieldConcept.BUSINESS_CATEGORY_LABEL, FieldScale.OPAQUE_TEXT),
            testContract("sucsfbidLwltRate", FieldConcept.FLOOR_RATE, FieldScale.PERCENT),
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

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, TEST_POLICY)

        outcome.shouldBeInstanceOf<AmountResolutionOutcome.Resolved>()
        outcome.sourceKey shouldBe RawKey("bssAmt")
        outcome.won shouldBe 1_000_000L
        outcome.unit shouldBe FieldUnit.WON
        outcome.provenance shouldBe Provenance.Published(NoticeRound.of("000"))
    }

    @Test
    fun `0 이나 결측 후보는 건너뛰고 다음 후보로 폴백한다 — §5-2`() {
        val observation = observationOf(mapOf("bssAmt" to "0", "bdgtAmt" to "500000"))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, TEST_POLICY)

        outcome.shouldBeInstanceOf<AmountResolutionOutcome.Resolved>()
        outcome.sourceKey shouldBe RawKey("bdgtAmt")
        outcome.won shouldBe 500_000L
        outcome.provenance shouldBe Provenance.FilledFromBudgetKey("bdgtAmt")
    }

    @Test
    fun `모든 후보가 없거나 0 이면 Unresolved 다`() {
        val observation = observationOf(mapOf("bssAmt" to "0"))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, TEST_POLICY)

        outcome shouldBe AmountResolutionOutcome.Unresolved
    }

    @Test
    fun `scale 이 WON_INTEGER 가 아니면 항목을 거부한다 — 다음 후보로 넘어가지 않는다`() {
        val observation = observationOf(mapOf("wrongScaleAmt" to "87.995"))
        val policy = TEST_POLICY.copy(baseAmountResolutionOrder = listOf(RawKey("wrongScaleAmt")))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, policy)

        outcome shouldBe
            AmountResolutionOutcome.Rejected(
                RawKey("wrongScaleAmt"),
                CollectionDropReason.CollectionContractViolation(ContractViolationAxis.SCALE),
            )
    }

    @Test
    fun `basis 가 concept 과 어긋나면 항목을 거부한다 — F-3`() {
        val observation = observationOf(mapOf("wrongBasisAmt" to "1000000"))
        val policy = TEST_POLICY.copy(baseAmountResolutionOrder = listOf(RawKey("wrongBasisAmt")))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, policy)

        outcome shouldBe
            AmountResolutionOutcome.Rejected(
                RawKey("wrongBasisAmt"),
                CollectionDropReason.CollectionContractViolation(ContractViolationAxis.BASIS),
            )
    }

    @Test
    fun `숫자로 파싱되지 않는 값은 ParseFailure 다`() {
        val observation = observationOf(mapOf("bssAmt" to "not-a-number"))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, TEST_POLICY)

        outcome shouldBe
            AmountResolutionOutcome.Rejected(
                RawKey("bssAmt"),
                CollectionDropReason.CollectionParseFailure(ParseFailureKind.NUMERIC),
            )
    }

    @Test
    fun `등재되지 않은 순서 항목은 건너뛴다 — 정책 구성 오류를 방어적으로 흡수`() {
        val observation = observationOf(mapOf("bdgtAmt" to "500000"))
        val policy = TEST_POLICY.copy(baseAmountResolutionOrder = listOf(RawKey("bdgtAmt")))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, policy)

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

    @Test
    fun `기초금액 통화·과세는 계약에서 읽는다 — F-2 리터럴 금지`() {
        val observation =
            observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "bssAmt" to "1000000"))

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized
        val direct = outcome.command.baseAmount as ResolvedBaseAmount.Direct

        direct.amount.vatTreatment shouldBe VatTreatment.UNKNOWN
    }

    @Test
    fun `업무구분 코드·라벨이 있으면 businessCategory 를 채운다 — F-5`() {
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    "bsnsDivCd" to "0411",
                    "bsnsDivNm" to "기술용역",
                ),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.businessCategory shouldBe BusinessCategory(CategoryCode("0411"), CategoryLabel("기술용역"))
    }

    @Test
    fun `배정예산 필드가 있으면 allocatedBudget 을 자기 자리로 채운다 — 예산 키 폴백과 다른 축`() {
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    "bssAmt" to "1000000",
                    "bdgtAmt" to "500000",
                ),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized
        val budget = outcome.command.allocatedBudget

        budget.shouldBeInstanceOf<AllocatedBudget>()
        budget.provenance shouldBe Provenance.Published(NoticeRound.of("000"))
        outcome.command.baseAmount.shouldBeInstanceOf<ResolvedBaseAmount.Direct>()
    }

    @Test
    fun `게시 낙찰하한율은 percent 원문을 fraction 으로 바꿔 FloorRateOrigin_NoticeValue 로 낸다 — F-5`() {
        val observation =
            observationOf(
                mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "sucsfbidLwltRate" to "87.995"),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.floorRate shouldBe
            FloorRate(Rate.ofPercent(BigDecimal("87.995")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
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
