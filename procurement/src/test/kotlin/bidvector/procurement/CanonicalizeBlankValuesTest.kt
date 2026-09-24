package bidvector.procurement

import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 빈 값·공백뿐인 값은 **없는 값**이다(D-6F4-8 「값이 없으면 없다」, D-6F8-11) — 실수집이 처음 드러냈다: KONEPS 는 옵션
 * 일시를 키 부재나 `null` 이 아니라 **빈 문자열**로 낼 때가 많고, 그 항목 전체가 「일시 해석 실패」로 탈락했다.
 * 합성 fixture 는 빈 일시를 담지 않았고 `CanonicalizeNeverThrowsTest` 의 전수 표는 **던지지 않는지만** 단언한다 —
 * 빈 값이 탈락을 내도 던지지는 않아 초록이었다. 이 표는 그 빈칸을 **결과 종류**로 잠근다: 식별자 둘을 뺀 어느 계약
 * 필드도 빈 값·공백 값으로 항목을 탈락시키지 못한다. 형태가 있는데 해석이 안 되는 값은 여전히 탈락이다.
 */
class CanonicalizeBlankValuesTest {
    private val operationalPolicy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value

    private val identity = mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000")
    private val identityConcepts = setOf(FieldConcept.NOTICE_NUMBER, FieldConcept.NOTICE_ROUND)
    private val dateTimeFailure = CollectionDropReason.CollectionParseFailure(ParseFailureKind.DATE_TIME)

    private fun outcomeOf(
        fields: Map<String, String>,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): CanonicalizationOutcome = canonicalize(observationOf(identity + fields), policy)

    private fun normalizedOf(
        fields: Map<String, String>,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): NoticeCollected = outcomeOf(fields, policy).shouldBeInstanceOf<CanonicalizationOutcome.Normalized>().command

    private fun instantOf(raw: String): Instant =
        requireNotNull(
            parseSourceZonedInstant(raw, SourceZoneRuleId.ASSUME_KST, operationalPolicy.dateTimePatterns),
        ) { "test 표본이 해석되지 않는다: $raw" }

    @Test
    fun `빈 값·공백 값은 식별자를 뺀 어느 계약 필드에서도 항목을 탈락시키지 않는다 — 계약 전수 × 공백 값`() {
        val offenders =
            listOf(operationalPolicy, TEST_POLICY).flatMap { policy ->
                policy.fieldContracts.contracts
                    .filterNot { it.concept in identityConcepts }
                    .flatMap { contract ->
                        BLANK_VALUES.mapNotNull { blank ->
                            val outcome = outcomeOf(mapOf(contract.rawName.name to blank), policy)
                            (outcome as? CanonicalizationOutcome.Dropped)?.let {
                                "${contract.rawName.name}=${blank.escaped()} → ${it.reason}"
                            }
                        }
                    }
            }

        withClue(offenders.joinToString(separator = "\n")) { offenders.shouldBeEmpty() }
    }

    @Test
    fun `식별자는 빈 값이면 번호 없음 탈락이다 — 필수 식별자 부재의 유일한 사유`() {
        listOf(operationalPolicy, TEST_POLICY).forEach { policy ->
            policy.fieldContracts.contracts.filter { it.concept in identityConcepts }.forEach { contract ->
                BLANK_VALUES.forEach { blank ->
                    outcomeOf(mapOf(contract.rawName.name to blank), policy) shouldBe
                        CanonicalizationOutcome.Dropped(
                            CollectionDropReason.CollectionMissingNoticeNumber,
                            unknownFieldCount = 0,
                        )
                }
            }
        }
    }

    @Test
    fun `빈 마감·개찰 일시는 그 필드만 null 이다 — 다른 일시는 그대로 해석된다`() {
        BLANK_VALUES.forEach { blank ->
            val blankDeadline = normalizedOf(mapOf("bidClseDt" to blank, "opengDt" to "2026-10-06 10:00:00"))
            blankDeadline.deadlineAt shouldBe null
            blankDeadline.openingScheduledAt shouldBe instantOf("2026-10-06 10:00:00")

            val blankOpening = normalizedOf(mapOf("bidClseDt" to "2026-10-05 10:00:00", "opengDt" to blank))
            blankOpening.deadlineAt shouldBe instantOf("2026-10-05 10:00:00")
            blankOpening.openingScheduledAt shouldBe null
        }
    }

    @Test
    fun `실수집이 낸 형태 — 마감이 빈 문자열이고 계약 밖 일시 키 여럿이 비어 있어도 정규화된다`() {
        val fields =
            mapOf(
                "bidClseDt" to "",
                "opengDt" to "2026-10-06 10:00:00",
                // 계약이 읽는 일시 키는 위 둘뿐이다 — 아래는 응답에 실려 오지만 계약 밖이라 미지 필드로만 센다.
                "arsltReqstdocRcptDt" to "",
                "bidQlfctRgstDt" to "",
                "chgDt" to "",
                "cmmnSpldmdAgrmntClseDt" to "",
                "dcmtgOprtnDt" to "",
                "pqApplDocRcptDt" to "",
                "tpEvalApplClseDt" to "",
                "bidBeginDt" to "2026-09-24 09:00:00",
                "bidNtceDt" to "2026-09-24 09:00:00",
                "rbidOpengDt" to "2026-10-06 10:00:00",
                "rgstDt" to "2026-09-24 09:00:00",
            )

        val outcome = outcomeOf(fields).shouldBeInstanceOf<CanonicalizationOutcome.Normalized>()

        outcome.command.deadlineAt shouldBe null
        outcome.command.openingScheduledAt shouldBe instantOf("2026-10-06 10:00:00")
        outcome.unknownFieldCount shouldBe 11
    }

    @Test
    fun `공백뿐인 금액 후보는 건너뛰고 다음 후보가 이어받는다 — 없는 값이지 숫자 파싱 실패가 아니다`() {
        BLANK_VALUES.forEach { blank ->
            val fallback = normalizedOf(mapOf("bssamt" to blank, "asignBdgtAmt" to "500"))
            fallback.baseAmount
                .shouldBeInstanceOf<ResolvedBaseAmount.FallbackFromBudget>()
                .sourceKey shouldBe RawKey("asignBdgtAmt")

            val none = normalizedOf(mapOf("bssamt" to blank, "asignBdgtAmt" to blank, "bdgtAmt" to blank))
            none.baseAmount shouldBe null

            normalizedOf(mapOf("presmptPrce" to blank)).estimatedAmount shouldBe null
        }
        normalizedOf(mapOf("bssamt" to " 1,000 ")).baseAmount
            .shouldBeInstanceOf<ResolvedBaseAmount.Direct>()
            .amount.provenance
            .shouldBeInstanceOf<Provenance.Published>()
    }

    @Test
    fun `업무구분 라벨이 공백뿐이면 라벨만 없다 — 코드는 산다`() {
        BLANK_VALUES.forEach { blank ->
            val category = normalizedOf(mapOf("bsnsDivCd" to "0411", "bsnsDivNm" to blank), TEST_POLICY).businessCategory

            category?.code shouldBe CategoryCode.of("0411")
            category?.label shouldBe null
        }
    }

    @Test
    fun `형태가 있는데 해석되지 않는 일시는 여전히 DATE_TIME 탈락이다 — 빈 값 규칙이 그 문을 열지 않는다`() {
        listOf("2026-09-24 24:00:00", "2026-09-24 10:00", "20260924", "2026-00-10 10:00:00").forEach { unparsable ->
            (outcomeOf(mapOf("bidClseDt" to unparsable)) as CanonicalizationOutcome.Dropped).reason shouldBe
                dateTimeFailure
            (outcomeOf(mapOf("opengDt" to unparsable)) as CanonicalizationOutcome.Dropped).reason shouldBe
                dateTimeFailure
        }
    }

    private fun String.escaped(): String = map { if (it.isWhitespace()) "\\u%04x".format(it.code) else "$it" }.joinToString("")

    private companion object {
        val BLANK_VALUES = listOf("", " ", "\t", "\u3000", " \n ")
    }
}
