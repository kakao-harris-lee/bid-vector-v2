package bidvector.qualification

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M1/1C 예제 test — legacy 회귀 사례(`_workspace/m1-1c/01_scout_legacy-qualification.md` §4)를
 * V2 타입으로 옮긴다. 기대값의 정본은 `data-dictionary.md` §3.2 · `capability-map.md`
 * QUAL-01/03/05 다 — fixture JSON 은 여기서 읽지 않는다(그건 runner 몫).
 */
class LicenseEligibilityTest {
    private val version = PolicyVersion(EffectiveFrom.Initial, "test-policy")
    private val emptyPolicy = LicenseQualificationPolicyData(LicenseAliasTable(emptyList()), emptyList())

    private fun row(
        group: String?,
        serial: String,
        vararg names: String,
        source: RequirementSourceField = RequirementSourceField.LcnsLmtNm,
    ): RequirementRow.Parsed =
        RequirementRow.Parsed(
            groupNo = group?.let(::LmtGrpNo),
            serialNo = LmtSno(serial),
            sourceField = source,
            licenseNames = names.map(::LicenseName),
        )

    private fun declared(vararg names: String): OperatorLicenses = OperatorLicenses.Declared(names.map(::LicenseName))

    private fun judge(
        rows: List<RequirementRow>,
        held: OperatorLicenses,
    ): LicenseJudgement = LicenseEligibility.judge(RequirementCollection.Collected(rows), held, emptyPolicy, version)

    @Test
    fun `그룹 내 전부 보유하면 Eligible`() {
        val result = judge(listOf(row("1", "1", "토목공사업"), row("1", "2", "건축공사업")), declared("토목공사업", "건축공사업"))
        result.verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Numbered(LmtGrpNo("1"))))
    }

    @Test
    fun `그룹 내 하나라도 미보유면 Ineligible이고 missingByGroup은 실제 미보유만 담는다`() {
        val result = judge(listOf(row("1", "1", "토목공사업"), row("1", "2", "건축공사업")), declared("토목공사업"))
        result.verdict shouldBe
            LicenseVerdict.Ineligible(
                mapOf(RequirementGroupId.Numbered(LmtGrpNo("1")) to setOf(LicenseName("건축공사업"))),
            )
    }

    @Test
    fun `한 그룹만 충족해도 Eligible이고 다른 그룹 미충족은 사유가 아니다`() {
        val rows =
            listOf(
                row("1", "1", "토목공사업"),
                row("2", "1", "토목공사업"),
                row("2", "2", "건축공사업"),
            )
        val result = judge(rows, declared("토목공사업"))
        result.verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Numbered(LmtGrpNo("1"))))
    }

    @Test
    fun `lmtGrpNo 결측 행은 개수와 무관하게 하나의 AND 그룹으로 폴딩된다`() {
        val rows = listOf(row(null, "1", "토목공사업"), row(null, "2", "건축공사업"), row(null, "3", "조경공사업"))
        val result = judge(rows, declared("토목공사업", "건축공사업"))
        result.verdict shouldBe
            LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to setOf(LicenseName("조경공사업"))))
    }

    @Test
    fun `보유 면허 선언 부재는 Uncertain이지 Ineligible이 아니다`() {
        val result = judge(listOf(row("1", "1", "토목공사업")), OperatorLicenses.NotDeclared)
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared)
        result.requiredLicenses shouldBe listOf(LicenseName("토목공사업"))
    }

    @Test
    fun `요건 데이터 자체가 없으면 Uncertain RequirementDataAbsent이고 requiredLicenses는 null`() {
        val result = LicenseEligibility.judge(RequirementCollection.DataAbsent, declared("토목공사업"), emptyPolicy, version)
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.RequirementDataAbsent)
        result.requiredLicenses shouldBe null
    }

    @Test
    fun `수집이 실패하면 Uncertain CollectionFailed`() {
        val result =
            LicenseEligibility.judge(RequirementCollection.CollectionFailed, declared("토목공사업"), emptyPolicy, version)
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.CollectionFailed)
    }

    @Test
    fun `행이 전부 파싱 실패면 Uncertain RequirementUnparsable이고 건수가 노출된다`() {
        val rows = listOf(RequirementRow.Unparsable(LmtSno("1")), RequirementRow.Unparsable(LmtSno("2")))
        val result = judge(rows, declared("토목공사업"))
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.RequirementUnparsable)
        result.unparsableRowCount shouldBe 2
    }

    @Test
    fun `일부 행만 파싱 실패면 남은 유효 요건으로 판정하고 실패 건수를 함께 낸다`() {
        val rows = listOf(row("1", "1", "토목공사업"), RequirementRow.Unparsable(LmtSno("2")))
        val result = judge(rows, declared("토목공사업"))
        result.verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Numbered(LmtGrpNo("1"))))
        result.unparsableRowCount shouldBe 1
    }

    @Test
    fun `제한 면허로 충족되는 그룹이 있으면 permsnIndstrytyList 결합 미결과 무관하게 그쪽이 이긴다`() {
        val rows =
            listOf(
                row("1", "1", "토목공사업"),
                row("1", "2", "축조업", source = RequirementSourceField.PermsnIndstrytyList),
            )
        val result = judge(rows, declared("토목공사업", "축조업"))
        result.verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Numbered(LmtGrpNo("1"))))
    }

    @Test
    fun `허용업종만 보유하고 제한 면허는 미보유면 결합 규칙 미결로 Uncertain이다`() {
        val rows =
            listOf(
                row("1", "1", "토목공사업"),
                row("1", "2", "축조업", source = RequirementSourceField.PermsnIndstrytyList),
            )
        val result = judge(rows, declared("축조업"))
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.PermittedIndustryCombinationRuleUndecided)
    }

    @Test
    fun `허용업종조차 보유하지 않으면 결합 규칙과 무관하게 Ineligible이다`() {
        val rows =
            listOf(
                row("1", "1", "토목공사업"),
                row("1", "2", "축조업", source = RequirementSourceField.PermsnIndstrytyList),
            )
        val result = judge(rows, declared("전기공사업"))
        result.verdict shouldBe
            LicenseVerdict.Ineligible(
                mapOf(
                    RequirementGroupId.Numbered(LmtGrpNo("1")) to
                        setOf(LicenseName("토목공사업"), LicenseName("축조업")),
                ),
            )
    }

    @Test
    fun `별칭 미등재 면허는 원문 정규화 키로 보존되고 collapse되지 않는다`() {
        val aliasPolicy =
            LicenseQualificationPolicyData(
                LicenseAliasTable(listOf(LicenseAliasEntry(LicenseName("ENG001"), setOf(LicenseName("엔지니어링"))))),
                emptyList(),
            )
        val rows = listOf(row("1", "1", "정보시스템 감리법인"))
        val result =
            LicenseEligibility.judge(
                RequirementCollection.Collected(rows),
                declared("엔지니어링"),
                aliasPolicy,
                version,
            )
        result.verdict shouldBe
            LicenseVerdict.Ineligible(
                mapOf(RequirementGroupId.Numbered(LmtGrpNo("1")) to setOf(LicenseName("정보시스템 감리법인"))),
            )
    }

    @Test
    fun `유효기간은 항상 미검증으로 표시되고 검증 variant는 존재하지 않는다`() {
        val result = judge(listOf(row("1", "1", "토목공사업")), declared("토목공사업"))
        result.validity shouldBe LicenseValidity.NotVerified
    }

    @Test
    fun `정책 version이 판정 봉투에 그대로 실린다`() {
        val result = judge(listOf(row("1", "1", "토목공사업")), declared("토목공사업"))
        result.policyVersion shouldBe version
    }

    @Test
    fun `요건 소스 집합이 판정에 쓰인 필드를 그대로 드러낸다`() {
        val rows =
            listOf(
                row("1", "1", "토목공사업"),
                row("1", "2", "축조업", source = RequirementSourceField.PermsnIndstrytyList),
            )
        val result = judge(rows, declared("토목공사업", "축조업"))
        result.requirementSourceFields shouldBe
            setOf(RequirementSourceField.LcnsLmtNm, RequirementSourceField.PermsnIndstrytyList)
    }

    /**
     * 공개 API 표면 — 리플렉션 없이, [LicenseVerdict] 를 소진 `when` 만으로 소비하는 것이
     * 유일한 경로임을 컴파일이 고정한다. `orElse`·`isEligible(): Boolean` 류를 이 타입이
     * 두지 않으므로(위협 모델 우회 (1)) 이 `when` 은 `else` 없이 컴파일된다 — 새 variant 가
     * 생기면 `allWarningsAsErrors`(NO_ELSE_IN_WHEN)로 여기서 먼저 깨진다.
     */
    @Test
    fun `LicenseVerdict은 소진 when으로만 소비된다 — 접는 API가 없다`() {
        val samples =
            listOf(
                LicenseVerdict.Eligible(emptySet()),
                LicenseVerdict.Ineligible(emptyMap()),
                LicenseVerdict.Uncertain(UncertainReason.RequirementDataAbsent),
            )
        val labels =
            samples.map { verdict ->
                when (verdict) {
                    is LicenseVerdict.Eligible -> "Eligible"
                    is LicenseVerdict.Ineligible -> "Ineligible"
                    is LicenseVerdict.Uncertain -> "Uncertain"
                }
            }
        labels shouldBe listOf("Eligible", "Ineligible", "Uncertain")
    }
}
