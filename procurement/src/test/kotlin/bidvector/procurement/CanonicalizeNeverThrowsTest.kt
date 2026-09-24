package bidvector.procurement

import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * `canonicalize` 는 원문 값이 무엇이든 던지지 않는다(D-6F8-7, M6/6F-8). 실데이터를 처음 붙이는 변환 지점이라
 * 값 유래 실패는 전부 **탈락 사유**이거나 **필드 부재**로 접힌다 — 던지면 수집 러너가 통째로 죽는다(재실행해도
 * 같은 항목에서). 접는 자리 하나하나는 아래 개별 test 가, 「새 필드가 던진다」는 회귀는 전수 표가 잠근다.
 */
class CanonicalizeNeverThrowsTest {
    private val operationalPolicy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value

    private val identity = mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000")

    private fun outcomeOf(
        fields: Map<String, String>,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): CanonicalizationOutcome = canonicalize(observationOf(identity + fields), policy)

    private fun normalizedOf(
        fields: Map<String, String>,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): NoticeCollected = outcomeOf(fields, policy).shouldBeInstanceOf<CanonicalizationOutcome.Normalized>().command

    private fun droppedReasonOf(
        fields: Map<String, String>,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): CollectionDropReason = outcomeOf(fields, policy).shouldBeInstanceOf<CanonicalizationOutcome.Dropped>().reason

    private val identifierViolation = CollectionDropReason.CollectionParseFailure(ParseFailureKind.IDENTIFIER)
    private val numericFailure = CollectionDropReason.CollectionParseFailure(ParseFailureKind.NUMERIC)

    @Test
    fun `차수가 비어 있지 않지만 세 자리 숫자가 아니면 IDENTIFIER 탈락이다 — 던지지 않는다`() {
        val malformedRounds = listOf("1", "01", "00A", " 000", "000 ", "0000", "０００", "-01", "000\n")

        malformedRounds.forEach { round ->
            val outcome =
                canonicalize(
                    observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to round)),
                    operationalPolicy,
                )

            outcome shouldBe CanonicalizationOutcome.Dropped(identifierViolation, unknownFieldCount = 0)
        }
    }

    @Test
    fun `공백뿐인 공고번호·차수는 번호 없음 탈락이다 — 어댑터의 판단과 같다`() {
        listOf("", "  ", "\t").forEach { blank ->
            droppedReasonOf(mapOf("bidNtceNo" to blank)) shouldBe CollectionDropReason.CollectionMissingNoticeNumber
            droppedReasonOf(mapOf("bidNtceOrd" to blank)) shouldBe CollectionDropReason.CollectionMissingNoticeNumber
        }
    }

    @Test
    fun `금액이 음수면 NUMERIC 탈락이다 — 다음 후보로 넘어가지 않는다`() {
        droppedReasonOf(mapOf("bssamt" to "-1")) shouldBe numericFailure
        droppedReasonOf(mapOf("presmptPrce" to "-1000")) shouldBe numericFailure
        droppedReasonOf(mapOf("asignBdgtAmt" to "-5")) shouldBe numericFailure
        droppedReasonOf(mapOf("bssamt" to "-1", "asignBdgtAmt" to "500")) shouldBe numericFailure
    }

    @Test
    fun `금액이 Long 범위를 넘으면 NUMERIC 탈락이다`() {
        droppedReasonOf(mapOf("bssamt" to "99999999999999999999")) shouldBe numericFailure
    }

    @Test
    fun `낙찰하한율이 음수이거나 표현할 수 없으면 필드 부재다 — 항목은 살아남는다`() {
        listOf("-87.5", "1E-2147483647", "abc", "").forEach { hostile ->
            normalizedOf(mapOf("sucsfbidLwltRate" to hostile)).floorRate shouldBe null
        }
        normalizedOf(mapOf("sucsfbidLwltRate" to "87.995")).floorRate.shouldBeInstanceOf<FloorRate>()
    }

    @Test
    fun `업무구분 코드가 공백뿐이면 업무구분 부재다 — 항목은 살아남는다`() {
        val withCode = mapOf("bsnsDivCd" to "  ", "bsnsDivNm" to "기술용역")

        normalizedOf(withCode, TEST_POLICY).businessCategory shouldBe null
    }

    @Test
    fun `해석할 수 없는 일시는 DATE_TIME 탈락이다`() {
        val dateTimeFailure = CollectionDropReason.CollectionParseFailure(ParseFailureKind.DATE_TIME)

        listOf("2026-13-40 25:61:61", "0000-00-00 00:00:00", "2026-02-30 10:00:00").forEach { hostile ->
            droppedReasonOf(mapOf("bidClseDt" to hostile)) shouldBe dateTimeFailure
            droppedReasonOf(mapOf("opengDt" to hostile)) shouldBe dateTimeFailure
        }
    }

    @Test
    fun `어느 필드에 어떤 적대적 값이 와도 canonicalize 는 던지지 않는다 — 계약 전수 × 값 표`() {
        val failures =
            listOf(operationalPolicy, TEST_POLICY).flatMap { policy ->
                policy.fieldContracts.contracts.flatMap { contract ->
                    HOSTILE_VALUES.mapNotNull { value ->
                        val result = runCatching { outcomeOf(mapOf(contract.rawName.name to value), policy) }
                        result.exceptionOrNull()?.let {
                            "${contract.rawName.name}=${describe(
                                value,
                            )} → ${it::class.simpleName}"
                        }
                    }
                }
            }

        withClue(failures.joinToString(separator = "\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `명시 null 값도 던지지 않는다 — 계약 전수`() {
        val failures =
            listOf(operationalPolicy, TEST_POLICY).flatMap { policy ->
                policy.fieldContracts.contracts.mapNotNull { contract ->
                    val fields: Map<RawKey, RawValue> =
                        identity.entries.associate { (name, text) -> RawKey(name) to RawValue.Present(text) }
                    val observation =
                        RawNoticeObservation.ofRawValues(
                            fields + (contract.rawName to RawValue.ExplicitNull),
                            SourceEndpoint.NOTICE_LIST,
                            Instant.EPOCH,
                        )
                    runCatching {
                        canonicalize(observation, policy)
                    }.exceptionOrNull()?.let { "${contract.rawName.name} → ${it::class.simpleName}" }
                }
            }

        withClue(failures.joinToString(separator = "\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `금액 해석 순서의 키는 전부 원 단위 계약이고 출처 템플릿이 있다 — 통화·출처 error 는 입력이 아니라 정책 구성 오류다`() {
        val orderKeys = operationalPolicy.baseAmountResolutionOrder + operationalPolicy.estimatedPriceResolutionOrder

        orderKeys.forEach { key ->
            val contract = requireNotNull(operationalPolicy.fieldContracts.contractFor(key)) { "계약 없는 순서 키: $key" }
            contract.scale shouldBe FieldScale.WON_INTEGER
            contract.unit shouldBe FieldUnit.WON
            (contract.provenanceTemplate != FieldProvenanceTemplate.NOT_APPLICABLE) shouldBe true
        }
    }

    private fun describe(value: String): String =
        if (value.length >
            DESCRIBE_LIMIT
        ) {
            "${value.take(DESCRIBE_LIMIT)}…(${value.length}자)"
        } else {
            "'$value'"
        }

    private companion object {
        const val DESCRIBE_LIMIT = 24
        const val LONG_TEXT_LENGTH = 100_000

        val HOSTILE_VALUES: List<String> =
            listOf(
                "",
                " ",
                "\t\n",
                "0",
                "-0",
                "-1",
                "+5",
                "1,234",
                "1 234",
                "９９９",
                "NaN",
                "Infinity",
                "1e999999999",
                "1E-2147483647",
                "-1E-2147483647",
                "99999999999999999999",
                "-99999999999999999999",
                "\u0000",
                "가나다",
                "2026-13-40 25:61:61",
                "0000-00-00 00:00:00",
                "9999-12-31 23:59:59",
                "2026-09-24T10:00:00",
                "1",
                "01",
                "0000",
                "000",
                "a".repeat(LONG_TEXT_LENGTH),
            )
    }
}
