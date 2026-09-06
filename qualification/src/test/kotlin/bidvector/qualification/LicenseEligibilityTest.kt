package bidvector.qualification

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
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

    /** verifier r1 F-5 — `judge` 가 값과 version 을 하나로 묶은 `Resolution.Resolved` 하나만 받는다. */
    private val resolvedPolicy = Resolution.Resolved(emptyPolicy, version)

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
    ): LicenseJudgement = LicenseEligibility.judge(RequirementCollection.Collected(rows), held, resolvedPolicy)

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
        val result = LicenseEligibility.judge(RequirementCollection.DataAbsent, declared("토목공사업"), resolvedPolicy)
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.RequirementDataAbsent)
        result.requiredLicenses shouldBe null
    }

    @Test
    fun `수집이 실패하면 Uncertain CollectionFailed`() {
        val result =
            LicenseEligibility.judge(RequirementCollection.CollectionFailed, declared("토목공사업"), resolvedPolicy)
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

    /**
     * verifier r1 F-4·PROBE F2 — legacy `_KEY_NOISE_RE` 는 전각 괄호 `（）`도 잡음으로
     * 제거한다. 이전 판(반각 괄호만)은 이 문자를 품은 면허명을 거짓 `Ineligible` 로 냈다.
     */
    @Test
    fun `전각 괄호가 있어도 정규화 키가 같아지면 Eligible이다`() {
        val result = judge(listOf(row("1", "1", "토목공사업（전문）")), declared("토목공사업(전문)"))
        result.verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Numbered(LmtGrpNo("1"))))
    }

    /** verifier r1 F-4·PROBE F3 — 나카구로 `・` 도 legacy 잡음 집합에 있다. */
    @Test
    fun `나카구로가 있어도 정규화 키가 같아지면 Eligible이다`() {
        val result = judge(listOf(row("1", "1", "항만・해안공사업")), declared("항만해안공사업"))
        result.verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Numbered(LmtGrpNo("1"))))
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
                Resolution.Resolved(aliasPolicy, version),
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
     * verifier r1 F-1·PROBE A — 그룹의 행이 전부 `PermsnIndstrytyList` 면 보유가 전혀
     * 없어도 예전엔 `restrictedRows` 가 비어 `containsAll(emptySet())` 가 공허하게 참이었다
     * (공허한 충족). 이제 제한 면허 행이 아예 없는 그룹은 그 자체로 결합 규칙 미결이다 —
     * 보유 여부와 무관하게 `Uncertain`, 「과추천」(`Eligible`)도 「과차단」(`Ineligible`도
     * 아니라는 확정)도 아니다.
     */
    @Test
    fun `그룹의 행이 전부 permsnIndstrytyList면 보유가 없어도 공허하게 충족되지 않는다 — PROBE A`() {
        val rows = listOf(row("1", "1", "축조업", source = RequirementSourceField.PermsnIndstrytyList))
        val result = judge(rows, declared())
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.PermittedIndustryCombinationRuleUndecided)
    }

    /**
     * verifier r1 F-1·PROBE D — 그룹 간 OR 이라도 공허하게 충족된 그룹이 다른 그룹의 실제
     * 미충족을 덮어 공고 전체를 `Eligible` 로 만들면 안 된다. 그룹1(제한 면허, 미보유)·
     * 그룹2(허용업종 전용)에서 어느 쪽도 `Eligible` 을 내지 않고 전체가 `Uncertain` 이다.
     */
    @Test
    fun `허용업종 전용 그룹이 다른 그룹의 실제 미충족을 Eligible로 덮지 않는다 — PROBE D`() {
        val rows =
            listOf(
                row("1", "1", "토목공사업"),
                row("2", "1", "축조업", source = RequirementSourceField.PermsnIndstrytyList),
            )
        val result = judge(rows, declared())
        result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.PermittedIndustryCombinationRuleUndecided)
    }

    /** verifier r1 F-2 — 이름을 하나도 못 읽은 행은 `Unparsable` 이어야지 빈 목록 `Parsed` 가 아니다. */
    @Test
    fun `licenseNames가 빈 Parsed 행은 구성 시점에 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            RequirementRow.Parsed(
                groupNo = LmtGrpNo("1"),
                serialNo = LmtSno("1"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = emptyList(),
            )
        }
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
