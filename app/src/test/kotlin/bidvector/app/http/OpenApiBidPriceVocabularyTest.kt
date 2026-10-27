package bidvector.app.http

import bidvector.decision.MlUnavailableReason
import bidvector.decision.Verdict
import bidvector.procurement.ResolvedBaseAmount
import bidvector.qualification.LicenseName
import bidvector.qualification.LicenseValidity
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.UnjudgedQualificationAxis
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * M7/7C ② — **문서 enum ↔ 구현 어휘 등식**. 축이 넷이다.
 *
 * 1. **문서 ↔ 타입**: enum 클래스로 어휘가 사는 칸은 `entries` 가 전수다.
 * 2. **문서 ↔ 출하 사상**(`verifier r2 R2-M1`): sealed 계층에서 오는 칸은 응답에 실리는 값이
 *    사상 함수 안의 **손 문자열**이다. 타입 이름에서 만든 기대값과 문서가 맞아도 그 문자열이
 *    따로 틀어질 수 있다 — r2 가 판정 불가 사유 하나를 개명하고 초록을 받은 자리가 그것이다.
 *    그래서 하위 타입마다 **출하 사상 함수를 실제로 통과시켜** 나온 집합을 문서와 맞춘다.
 * 3. **덮개**(`cr r2 M-2`): 문서에 enum 이 있는 칸만 세면, 닫힌 어휘를 `string` 으로 둔 칸은
 *    아무 등식에도 걸리지 않는다. 그래서 등식을 뒤집는다 — **응답의 문자열 칸 전수**가
 *    「문서 enum 칸 ∪ 열린 어휘 allowlist」와 같아야 한다. 새 문자열 칸은 둘 중 하나로
 *    분류되지 않으면 붉어지므로 **잊는 길이 없다**.
 * 4. **nullable 양방향**: `null` 은 어휘가 아니라 표지다. 문서 enum 에 `null` 이 있으면 그 칸은
 *    `nullable` 이어야 하고(정방향), `nullable` 이고 enum 이 있으면 그 enum 에 `null` 이 있어야
 *    한다(역방향 — OpenAPI 3.0 의 `enum` 은 값 전수 제약이라 `nullable: true` 만으로는 `null` 이
 *    허용되지 않는다, `cr r2 M-1`).
 *
 * **전제(`cr r2 L3`)**: 이 등식은 스키마의 **평탄한 속성**만 읽는다 — `$ref`·`oneOf`·중첩 object
 * 는 보지 않는다. 이 응답이 평탄하다는 것은 별 게이트(D-6A1-38 평탄 술어)가 들고 있고, 그
 * 게이트가 깨지면 이 파일의 전제도 함께 무너진다.
 */
class OpenApiBidPriceVocabularyTest {
    private val properties: Map<String, Any?> by lazy {
        @Suppress("UNCHECKED_CAST")
        loadOpenApiSpec().schema("BidPriceRequestResponse")["properties"] as Map<String, Any?>
    }

    @Test
    fun `enum 클래스로 사는 칸의 문서 어휘가 entries 전수와 같다`() {
        enumClassFields().forEach { (field, expected) ->
            withClue(field) { docEnumOf(field) shouldBe expected }
        }
    }

    /**
     * 출하 사상 함수를 **실제로 통과시킨** 결과가 문서와 같다. 입력은 하위 타입 전수에서 만들고,
     * 그 표본이 하위 타입을 다 덮는지는 아래 표본 덮개 test 가 따로 잰다.
     */
    @Test
    fun `sealed 칸의 출하 사상 결과가 문서 어휘와 같다`() {
        emittedFields().forEach { (field, emitted) ->
            withClue(field) { emitted shouldBe docEnumOf(field) }
        }
    }

    /**
     * 표본이 하위 타입 전수를 덮는다 — 새 하위 타입이 생기면 표본이 모자라 붉어진다. 이것이
     * 없으면 위 등식은 「내가 고른 몇 개만」을 재는 것이 된다.
     */
    @Test
    fun `출하 사상 표본이 sealed 하위 타입 전수를 덮는다`() {
        sampleCoverage().forEach { (type, covered) ->
            withClue(type.simpleName.orEmpty()) { covered shouldBe type.sealedSubclasses.toSet() }
        }
    }

    /**
     * 생성자가 커널에 닫혀 있어 app test 가 입력을 만들 수 없는 칸은 **타입 이름**에서 기대값을
     * 만든다(알려진 제한 — evidence 에 적는다). 새 하위 타입이 생기면 이 등식이 붉어지므로
     * 「어휘가 늘었는데 문서가 낡는」 상태는 여전히 막힌다. 막히지 않는 것은 **사상 함수의
     * 손 문자열이 따로 틀어지는** 경우뿐이고, 그 둘은 오늘 같다.
     */
    @Test
    fun `생성자가 닫힌 칸은 하위 타입 이름 전수와 같다`() {
        nameDerivedFields().forEach { (field, expected) ->
            withClue(field) { docEnumOf(field) shouldBe expected }
        }
    }

    @Test
    fun `응답의 문자열 칸 전수가 문서 enum 칸과 열린 어휘 allowlist 의 합집합과 같다`() {
        stringFields() shouldBe (docFieldsWithEnum() + OPEN_VOCABULARY.keys)
    }

    /**
     * **스칼라 칸만 본다**(`cr r2 L1`). 배열 칸에서 `nullable` 은 **배열 자신**의 성질이고 enum 은
     * 원소의 성질이라, 둘을 한 칸으로 묶으면 「nullable 배열이면 원소 enum 에 `null` 이 있어야
     * 한다」는 틀린 요구가 된다. 오늘 그런 칸은 없고, 그 전제는 바로 아래 test 가 잠근다.
     */
    @Test
    fun `문서 enum 에 null 을 실은 스칼라 칸은 nullable 이다`() {
        scalarEnumFields()
            .filter { null in rawEnumOf(it) }
            .forEach { withClue(it) { propertyOf(it)["nullable"] shouldBe true } }
    }

    @Test
    fun `nullable 이고 enum 이 있는 스칼라 칸은 그 enum 에 null 이 있다`() {
        scalarEnumFields()
            .filter { propertyOf(it)["nullable"] == true }
            .forEach { withClue(it) { (null in rawEnumOf(it)) shouldBe true } }
    }

    /** 위 두 test 의 전제 — enum 을 가진 **배열** 칸은 nullable 이 아니다(그 축이 아직 없다). */
    @Test
    fun `enum 을 가진 배열 칸은 nullable 이 아니다`() {
        (docFieldsWithEnum() - scalarEnumFields())
            .forEach { withClue(it) { propertyOf(it)["nullable"] shouldBe null } }
    }

    // --- 문서 읽기 ---------------------------------------------------------

    private fun propertyOf(field: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return properties[field] as? Map<String, Any?> ?: error("문서에 칸이 없다: $field")
    }

    private fun itemsOf(field: String): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST")
        return propertyOf(field)["items"] as? Map<String, Any?>
    }

    private fun rawEnumOf(field: String): List<Any?> {
        @Suppress("UNCHECKED_CAST")
        return (propertyOf(field)["enum"] ?: itemsOf(field)?.get("enum")) as? List<Any?> ?: emptyList()
    }

    /** 어휘 비교용 — `null`(표지)은 빼고 본다. 그 축은 nullable test 둘이 든다. */
    private fun docEnumOf(field: String): Set<String> = rawEnumOf(field).filterNotNull().map { it.toString() }.toSet()

    private fun docFieldsWithEnum(): Set<String> = properties.keys.filter { rawEnumOf(it).isNotEmpty() }.toSet()

    /** enum 을 가진 **스칼라** 칸 — nullable 축은 이쪽만 본다(배열은 성질의 주인이 다르다). */
    private fun scalarEnumFields(): Set<String> =
        docFieldsWithEnum().filter { propertyOf(it)["type"] != "array" }.toSet()

    /** 어휘를 나를 수 있는 칸 — 스칼라 `string` 과 `string` 배열이다(수·boolean 은 어휘가 없다). */
    private fun stringFields(): Set<String> =
        properties.keys
            .filter { propertyOf(it)["type"] == "string" || itemsOf(it)?.get("type") == "string" }
            .toSet()

    // --- 구현 어휘: enum 클래스 -------------------------------------------

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

    private fun List<Enum<*>>.names(): Set<String> = map { it.name }.toSet()

    // --- 구현 어휘: 출하 사상 함수를 통과시킨 결과 ------------------------

    private fun uncertainReasons(): List<UncertainReason> =
        UncertainReason::class.sealedSubclasses.map {
            it.objectInstance ?: error("판정 불가 사유는 data object 다: $it")
        }

    private fun mlUnavailableReasons(): List<MlUnavailableReason> =
        MlUnavailableReason::class.sealedSubclasses.map {
            it.objectInstance ?: error("ML 미가용 사유는 data object 다: $it")
        }

    private fun licenseVerdictSamples(): List<LicenseVerdict> =
        listOf(
            LicenseVerdict.Eligible(setOf(RequirementGroupId.Ungrouped)),
            LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to setOf(LicenseName("토목공사업")))),
        ) + uncertainReasons().map { LicenseVerdict.Uncertain(it) }

    private fun floorRateSamples() = listOf(publishedFloorRate(), statutoryFloorRate())

    private fun emittedFields(): Map<String, Set<String>> {
        val qualification =
            licenseVerdictSamples().map { qualificationFieldsOf(licenseJudgementFixture(verdict = it)) }
        return mapOf(
            "licenseVerdict" to qualification.map { it.licenseVerdict }.toSet(),
            "licenseUncertainReason" to qualification.mapNotNull { it.licenseUncertainReason }.toSet(),
            "licenseValidity" to qualification.map { it.licenseValidity }.toSet(),
            "mlUnavailableReason" to
                mlUnavailableReasons()
                    .mapNotNull { mlUnavailableReasonLabelOf(MlAnalysisOutcome.Unavailable(it)) }
                    .toSet(),
            "publishedFloorRateOrigin" to floorRateSamples().map(::floorRateOriginLabel).toSet(),
        )
    }

    private fun sampleCoverage(): Map<KClass<*>, Set<KClass<*>>> =
        mapOf(
            LicenseVerdict::class to licenseVerdictSamples().map { it::class }.toSet(),
            UncertainReason::class to uncertainReasons().map { it::class }.toSet(),
            MlUnavailableReason::class to mlUnavailableReasons().map { it::class }.toSet(),
            LicenseValidity::class to setOf(LicenseValidity.NotVerified::class),
            FloorRateOrigin::class to floorRateSamples().map { it.origin::class }.toSet(),
        )

    // --- 구현 어휘: 생성자가 닫혀 입력을 만들 수 없는 칸 ------------------

    private fun nameDerivedFields(): Map<String, Set<String>> =
        mapOf(
            // `Verdict` 의 세 하위 타입은 `internal constructor` 다 — 커널만 만든다(그 폐쇄가 설계다).
            "verdict" to tokensOf(Verdict::class),
            // `ResolvedBaseAmount` 는 provenance 별 사전 조건(`require`)이 붙어 세 입력을 app test 가
            // 짓는 비용이 크다. 이 칸의 사상은 `when` 세 갈래뿐이고 토큰 하나만 이름과 다르다.
            "baseAmountResolution" to
                tokensOf(ResolvedBaseAmount::class, mapOf("DerivedFromOpeningAmount" to "DERIVED_FROM_OPENING")),
        )

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

    /**
     * `OperatorLicensesNotDeclared` → `OPERATOR_LICENSES_NOT_DECLARED`.
     *
     * 연속 대문자 경계도 가른다(`cr r2 L2`) — `MLUnavailable` 같은 이름이 생기면 앞 판은
     * `MLUNAVAILABLE` 로 접어 가짜 RED 를 냈다.
     */
    private fun String.screamingSnake(): String =
        replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_")
            .replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), "_")
            .uppercase()

    private companion object {
        /**
         * **열린 어휘로 둔 문자열 칸**과 그 사유(`cr r2 M-2` 처방 (b)). 위 덮개 등식이 이 목록과
         * 문서 enum 칸의 합집합을 응답의 문자열 칸 전수와 맞추므로, 새 칸은 **반드시** 둘 중
         * 하나로 분류된다.
         */
        val OPEN_VOCABULARY: Map<String, String> =
            mapOf(
                // 자유 값 — 어휘가 아니다(원문·식별자·수의 문자열 표현).
                "noticeNumber" to "공고번호 원문",
                "noticeRound" to "차수 원문",
                "correlationId" to "요청 상관 id",
                "licenseRequiredLicenses" to "요구 면허명 — 공고 원문 어휘",
                "licenseMissingLicenses" to "미보유 면허명 — 공고 원문 어휘",
                "licenseGroupsSatisfied" to "요건 그룹 라벨 — 그룹 번호가 값에 들어간다",
                "licenseGroupsMissing" to "요건 그룹 라벨 — 같은 이유",
                "licensePolicySource" to "정책 version 의 출처 문자열",
                "licensePolicyEffectiveFrom" to "정책 시행일 — 날짜",
                "publishedFloorRateFraction" to "율의 소수 표기 — 수",
                "publishedFloorRateNoticeRound" to "게시값이 온 차수 — 수",
                "scoreThreshold" to "임계 점수 — 수",
                "scoreActual" to "실측 점수 — 수",
                // 닫힌 어휘이지만 **이 slice 가 소유하지 않은 축**이다(알려진 제한 R2-L2).
                // 그 축의 slice 가 enum 으로 올릴 때 이 목록에서 빠지고, 덮개가 그것을 요구한다.
                "verdictReasonCodes" to "판정 사유 코드 — DEC 축 소관(닫힌 어휘, 미enum)",
                "dropStage" to "탈락 단계 — 평가 커널 소관(닫힌 어휘, 미enum)",
                "dropReasonCode" to "탈락 사유 — 같은 축",
                "noticeStatus" to "공고 수명 상태 — COL 축 소관",
                "watchUndeterminableReason" to "감시 판정 불가 사유 — STR 축 소관",
                "failedWatchRuleIds" to "실패한 감시 규칙 id — STR 축 소관",
                "licenseRequirementSourceFields" to "요건 출처 필드명 — QUAL 축 소관",
                "baseAmountCurrency" to "통화 — shared-kernel 금액 축 소관",
                "baseAmountBasis" to "금액 basis — 같은 축",
                "baseAmountVatTreatment" to "과세 취급 — 같은 축",
                "baseAmountProvenance" to "금액 provenance — 같은 축",
            )
    }
}
