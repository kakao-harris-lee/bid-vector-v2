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
    vatTreatment: VatTreatment = VatTreatment.UNKNOWN,
    sourceZone: SourceZoneRuleId? = null,
    expectedRange: ExpectedRangeKey? = null,
): KonepsFieldContract =
    KonepsFieldContract.of(
        rawName = RawKey(rawName),
        concept = concept,
        basis = basis,
        scale = scale,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = vatTreatment,
        authoritative = true,
        presentIn = setOf(SourceEndpoint.NOTICE_LIST),
        provenanceTemplate = provenanceTemplate,
        effectiveFrom = EffectiveFrom.Initial,
        sourceZone = sourceZone,
        expectedRange = expectedRange,
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
                // N-2 회귀 가드 — INCLUSIVE 는 코드의 VatTreatment.UNKNOWN 리터럴과 값이 달라야
                // "계약에서 읽는다" test 가 실제로 그 리터럴 회귀를 잡는다(verifier r2 실측).
                VatTreatment.INCLUSIVE,
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
                // curator 표(policy-values.md §1.1)의 실제 값과 같다 — EXCLUSIVE. N-2 와 같은
                // 이유로 UNKNOWN 리터럴 회귀를 이 값이 잡는다.
                VatTreatment.EXCLUSIVE,
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
            // v2-defect 002 수정 — expectedRange 를 실제로 강제하는지 재는 합성 계약.
            // TEST_POLICY 는 이 id 로 밴드를 두지 않으므로(정책의 rangeBands 는 기본 빈
            // 표) 개별 test 가 `.copy(rangeBands = ...)`로 밴드를 얹는다.
            testContract(
                "boundedAmt",
                FieldConcept.BASE_AMOUNT,
                FieldScale.WON_INTEGER,
                Basis.BASE_AMOUNT,
                FieldProvenanceTemplate.PUBLISHED,
                expectedRange = ExpectedRangeKey("TEST_BAND"),
            ),
            testContract("bsnsDivCd", FieldConcept.BUSINESS_CATEGORY_CODE, FieldScale.OPAQUE_TEXT),
            testContract("bsnsDivNm", FieldConcept.BUSINESS_CATEGORY_LABEL, FieldScale.OPAQUE_TEXT),
            testContract("sucsfbidLwltRate", FieldConcept.FLOOR_RATE, FieldScale.PERCENT),
            testContract(
                "bidClseDt",
                FieldConcept.DEADLINE_AT,
                FieldScale.DATETIME_NO_ZONE,
                sourceZone = SourceZoneRuleId.ASSUME_KST,
            ),
            testContract(
                "opengDt",
                FieldConcept.OPENING_SCHEDULED_AT,
                FieldScale.DATETIME_NO_ZONE,
                sourceZone = SourceZoneRuleId.ASSUME_KST,
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
        // policy-values.md §1.4 authoritative 형식과 같다 — main 정책과 같은 패턴을 test 도 써야
        // "실제 KONEPS wire 형식이 파싱된다"는 회귀 가드가 성립한다(v2-defect 026 재발 방지).
        dateTimePatterns = listOf(DateTimePatternId.KONEPS_SPACE_DELIMITED_19),
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

    // v2-defect 002 수정(3A 잔여 일괄 verifier r3 전) — expectedRange 가 참조하는 밴드를
    // resolveAmount 가 실제로 강제한다. 값 크기로 단위를 되짚지 않는다(ADR 0002 D-4) —
    // 범위 밖이면 그대로 거부이지, 다른 scale 로 재해석하지 않는다.
    @Test
    fun `expectedRange 밴드 밖 값은 RANGE 위반으로 거부된다 — v2-defect 002 회귀 가드`() {
        val observation = observationOf(mapOf("boundedAmt" to "2000000"))
        val policy =
            TEST_POLICY.copy(
                baseAmountResolutionOrder = listOf(RawKey("boundedAmt")),
                rangeBands = mapOf("TEST_BAND" to RangeBand(BigDecimal.ZERO, BigDecimal("1000000"))),
            )

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, policy)

        outcome shouldBe
            AmountResolutionOutcome.Rejected(
                RawKey("boundedAmt"),
                CollectionDropReason.CollectionContractViolation(ContractViolationAxis.RANGE),
            )
    }

    @Test
    fun `expectedRange 밴드 안 값은 정상 해석된다`() {
        val observation = observationOf(mapOf("boundedAmt" to "500000"))
        val policy =
            TEST_POLICY.copy(
                baseAmountResolutionOrder = listOf(RawKey("boundedAmt")),
                rangeBands = mapOf("TEST_BAND" to RangeBand(BigDecimal.ZERO, BigDecimal("1000000"))),
            )

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, policy)

        outcome.shouldBeInstanceOf<AmountResolutionOutcome.Resolved>()
        outcome.won shouldBe 500_000L
    }

    @Test
    fun `정책에 밴드가 없으면(운영 정책 기본) expectedRange 계약이 있어도 거부하지 않는다`() {
        val observation = observationOf(mapOf("boundedAmt" to "999999999"))
        val policy = TEST_POLICY.copy(baseAmountResolutionOrder = listOf(RawKey("boundedAmt")))

        val outcome = resolveAmount(observation, NoticeRound.of("000"), AmountAxis.BASE, policy)

        outcome.shouldBeInstanceOf<AmountResolutionOutcome.Resolved>()
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
    fun `기초금액 통화·과세는 계약에서 읽는다 — F-2 리터럴 금지, N-2 회귀 가드`() {
        val observation =
            observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "bssAmt" to "1000000"))

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized
        val direct = outcome.command.baseAmount as ResolvedBaseAmount.Direct

        // 계약의 값(INCLUSIVE)은 코드가 예전에 쓰던 리터럴(UNKNOWN)과 달라야 한다 — 리터럴로
        // 되돌리는 변이가 이 test 를 실제로 실패시킨다(verifier r2 N-2, 재발 방지).
        direct.amount.vatTreatment shouldBe VatTreatment.INCLUSIVE
    }

    @Test
    fun `추정가격 통화·과세도 계약에서 읽는다 — N-2 회귀 가드(두 번째 표본)`() {
        val observation =
            observationOf(
                mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000", "presmptPrce" to "1000000"),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.estimatedAmount
            ?.amount
            ?.vatTreatment shouldBe VatTreatment.EXCLUSIVE
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

    @Test
    fun `마감·개찰예정 일시는 계약의 sourceZone 규칙으로 Instant 로 해석된다 — N-3`() {
        // v2-defect 026 재발 방지 — KONEPS 실제 wire 형식(공백 구분자, policy-values.md §1.4)을
        // 쓴다. ISO `T` 표기(이전 판 test 데이터)는 이제 이 형식 목록 밖이라 파싱되지 않는다.
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    "bidClseDt" to "2026-09-10 14:00:00",
                    "opengDt" to "2026-09-11 10:00:00",
                ),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.deadlineAt shouldBe
            parseSourceZonedInstant("2026-09-10 14:00:00", SourceZoneRuleId.ASSUME_KST, TEST_POLICY.dateTimePatterns)
        outcome.command.openingScheduledAt shouldBe
            parseSourceZonedInstant("2026-09-11 10:00:00", SourceZoneRuleId.ASSUME_KST, TEST_POLICY.dateTimePatterns)
        // 원문은 raw 에서 그대로 확인 가능하다 — 해석은 별도 필드일 뿐 원문을 지우지 않는다.
        outcome.command.raw.valueOf(TEST_REGISTRY.contractFor(RawKey("bidClseDt"))!!) shouldBe "2026-09-10 14:00:00"
    }

    @Test
    fun `마감일시 필드가 없으면 deadlineAt 은 null 이다 — 지어내지 않는다`() {
        val observation = observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000"))

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.deadlineAt shouldBe null
        outcome.command.openingScheduledAt shouldBe null
    }

    @Test
    fun `마감일시가 정책 패턴 어디로도 파싱되지 않으면 항목이 DATE_TIME 파싱 실패로 탈락한다 — v2-defect 026 회귀 가드`() {
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    // ISO `T` 표기는 이제 정책 형식 목록(공백 구분자) 밖이다 — 조용한 null 이
                    // 아니라 관측 가능한 파싱 실패여야 한다(음성 test).
                    "bidClseDt" to "2026-09-10T14:00:00",
                ),
            )

        val outcome = canonicalize(observation, TEST_POLICY)

        outcome.shouldBeInstanceOf<CanonicalizationOutcome.Dropped>()
        outcome.reason shouldBe CollectionDropReason.CollectionParseFailure(ParseFailureKind.DATE_TIME)
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
