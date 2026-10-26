package bidvector.app.http

import bidvector.decision.Verdict
import bidvector.procurement.ResolvedBaseAmount
import bidvector.qualification.LicenseValidity
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.workflow.evaluation.UnjudgedQualificationAxis
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * M7/7C ② (`verifier r1 M-1` · `cr r1 M3`) — **문서 enum ↔ 구현 어휘 등식**.
 *
 * 이 등식이 없으면 다음 slice 가 사상 함수에 갈래를 더하고 문서를 잊어도 전부 초록이다.
 * 생성 타입은 **문서에서** 만들어지므로 바이트 등식(W-5)이 서고, 응답 키 집합 등식은 키만 보며,
 * 소진 `when` 은 Kotlin 안쪽만 잠근다. 그 사이로 **화면이 새 값을 「불가능」으로 읽는** 상태가
 * 지나간다. verifier 가 문서의 enum 값 하나를 개명하고도 초록을 받은 자리가 여기다.
 *
 * 등식의 구현 쪽은 **어휘를 가진 타입 자신**에서 만든다 — 사상 함수의 `when` 을 베끼지 않는다.
 * 베끼면 같은 실수를 두 자리에 적는 것이라 등식이 공허해진다.
 */
class OpenApiBidPriceVocabularyTest {
    private val properties: Map<String, Any?> by lazy {
        @Suppress("UNCHECKED_CAST")
        loadOpenApiSpec().schema("BidPriceRequestResponse")["properties"] as Map<String, Any?>
    }

    /** 어휘가 enum 클래스에 사는 칸 — `entries` 가 곧 전수다. */
    @Test
    fun `enum 클래스로 사는 칸의 문서 어휘가 entries 전수와 같다`() {
        enumClassFields().forEach { (field, expected) ->
            withClue(field) { docEnumOf(field) shouldBe expected }
        }
    }

    /**
     * 어휘가 sealed 계층에서 오는 칸 — **하위 타입 전수**가 곧 전수다. 새 하위 타입이 생기면
     * 그 자리가 저절로 넓어지므로 손 목록이 아니다.
     */
    @Test
    fun `sealed 계층에서 오는 칸의 문서 어휘가 하위 타입 전수와 같다`() {
        sealedFields().forEach { (field, expected) ->
            withClue(field) { docEnumOf(field) shouldBe expected }
        }
    }

    /**
     * 덮개 — 문서가 enum 을 선언한 칸 전수가 위 두 표의 칸과 같다. 이것이 없으면 새 enum 칸을
     * 문서에만 더하고 이 파일을 잊는 길이 남는다(그 칸의 어휘가 무엇인지는 그때 사람이 정한다).
     */
    @Test
    fun `문서에서 enum 을 가진 칸 전수가 이 파일이 덮는 칸과 같다`() {
        docFieldsWithEnum() shouldBe (enumClassFields().keys + sealedFields().keys)
    }

    /**
     * `null` 은 어휘가 아니라 **nullable 표지**다. 문서가 `null` 을 enum 에 실으면 그 칸은
     * `nullable: true` 여야 한다 — 아니면 생성 타입이 `null` 을 값으로 읽는다.
     */
    @Test
    fun `문서 enum 에 null 을 실은 칸은 nullable 이다`() {
        docFieldsWithEnum()
            .filter { null in rawEnumOf(it) }
            .forEach { withClue(it) { propertyOf(it)["nullable"] shouldBe true } }
    }

    // --- 문서 읽기 ---------------------------------------------------------

    private fun propertyOf(field: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return properties[field] as? Map<String, Any?> ?: error("문서에 칸이 없다: $field")
    }

    private fun rawEnumOf(field: String): List<Any?> {
        val property = propertyOf(field)

        @Suppress("UNCHECKED_CAST")
        val items = property["items"] as? Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        return (property["enum"] ?: items?.get("enum")) as? List<Any?> ?: emptyList()
    }

    /** 어휘 비교용 — `null`(nullable 표지)은 빼고 본다. 그 축은 바로 위 test 가 든다. */
    private fun docEnumOf(field: String): Set<String> = rawEnumOf(field).filterNotNull().map { it.toString() }.toSet()

    private fun docFieldsWithEnum(): Set<String> = properties.keys.filter { rawEnumOf(it).isNotEmpty() }.toSet()

    // --- 구현 어휘 ---------------------------------------------------------

    private fun enumClassFields(): Map<String, Set<String>> =
        mapOf(
            "outcome" to EvaluationOutcomeLabel.entries.names(),
            "inboxRecordState" to InboxRecordState.entries.names(),
            "inboxRecordNotRecordedReason" to InboxRecordNotRecordedReason.entries.names(),
            "licenseMissingDetailState" to LicenseMissingDetailState.entries.names(),
            "floorRateGroundsState" to FloorRateGroundsState.entries.names(),
            "floorShortfallState" to NotComputedState.entries.names(),
            "floorShortfallNotComputedReason" to FloorShortfallNotComputedReason.entries.names(),
            "mlAnalysisState" to MlAnalysisStateLabel.entries.names(),
            "confidenceState" to ConfidenceState.entries.names(),
            "confidenceNotAvailableReason" to ConfidenceNotAvailableReason.entries.names(),
            "bidPriceMenuState" to NotComputedState.entries.names(),
            "bidPriceMenuNotComputedReason" to BidPriceMenuNotComputedReason.entries.names(),
            "disclosures" to BidPriceDisclosure.entries.names(),
            "unjudgedAxes" to UnjudgedQualificationAxis.entries.names(),
        )

    private fun sealedFields(): Map<String, Set<String>> =
        mapOf(
            "verdict" to tokensOf(Verdict::class),
            "licenseVerdict" to tokensOf(LicenseVerdict::class),
            "licenseUncertainReason" to tokensOf(UncertainReason::class),
            "licenseValidity" to tokensOf(LicenseValidity::class),
            "publishedFloorRateOrigin" to tokensOf(FloorRateOrigin::class),
            // 타입 이름과 wire 토큰이 **일부러 다른** 자리 하나. 표는 하위 타입 전수를 키로
            // 돌기 때문에 새 하위 타입이 생기면 모자라서 붉어진다(손 목록이 아니다).
            "baseAmountResolution" to
                tokensOf(ResolvedBaseAmount::class, mapOf("DerivedFromOpeningAmount" to "DERIVED_FROM_OPENING")),
        )

    private fun List<Enum<*>>.names(): Set<String> = map { it.name }.toSet()

    private fun tokensOf(
        type: KClass<*>,
        overrides: Map<String, String> = emptyMap(),
    ): Set<String> {
        val subtypes = type.sealedSubclasses
        check(subtypes.isNotEmpty()) { "sealed 하위 타입이 없다: ${type.simpleName}" }
        return subtypes
            .map { subtype ->
                val name = subtype.simpleName ?: error("익명 하위 타입: ${type.simpleName}")
                overrides[name] ?: name.screamingSnake()
            }.toSet()
    }

    /** `OperatorLicensesNotDeclared` → `OPERATOR_LICENSES_NOT_DECLARED`. */
    private fun String.screamingSnake(): String = replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").uppercase()
}
