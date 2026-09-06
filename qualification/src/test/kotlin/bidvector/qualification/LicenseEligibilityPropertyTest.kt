package bidvector.qualification

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * M1/1C property test — 위협 모델 우회 (2)(3) 방어. legacy 회귀
 * `test_required_keys_subset_equals_missing_empty` ·
 * `report_license_gate_impact._representative_missing` 의 V2 대응(R-QUAL-01·06).
 */
class LicenseEligibilityPropertyTest {
    private val version = PolicyVersion(EffectiveFrom.Initial, "test-policy")
    private val emptyPolicy = LicenseQualificationPolicyData(LicenseAliasTable(emptyList()), emptyList())
    private val licenseNameArb: Arb<String> = Arb.string(1..8, "abcde")

    private fun judgeSingleGroup(
        requiredNames: List<String>,
        heldNames: List<String>,
    ): LicenseJudgement {
        val row =
            RequirementRow.Parsed(
                groupNo = LmtGrpNo("1"),
                serialNo = LmtSno("1"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = requiredNames.map(::LicenseName),
            )
        return LicenseEligibility.judge(
            RequirementCollection.Collected(listOf(row)),
            OperatorLicenses.Declared(heldNames.map(::LicenseName)),
            emptyPolicy,
            version,
        )
    }

    @Test
    fun `missingByGroup은 항상 요구 minus 보유의 부분집합이다 — R-QUAL-06`() {
        runBlocking {
            checkAll(Arb.list(licenseNameArb, 1..4), Arb.list(licenseNameArb, 0..4)) { requiredNames, heldNames ->
                val verdict = judgeSingleGroup(requiredNames, heldNames).verdict
                if (verdict is LicenseVerdict.Ineligible) {
                    val requiredSet = requiredNames.toSet()
                    val heldSet = heldNames.toSet()
                    verdict.missingByGroup.values.flatten().forEach { name ->
                        withClue("missingByGroup 이 요구되지 않았거나 이미 보유한 면허를 담았다: $name") {
                            (name.value in requiredSet && name.value !in heldSet) shouldBe true
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `lmtGrpNo 결측 행은 몇 개가 오든 하나의 Ungrouped 그룹으로만 접힌다`() {
        runBlocking {
            checkAll(Arb.int(1..6)) { rowCount ->
                val rows =
                    (1..rowCount).map { index ->
                        RequirementRow.Parsed(
                            groupNo = null,
                            serialNo = LmtSno(index.toString()),
                            sourceField = RequirementSourceField.LcnsLmtNm,
                            licenseNames = listOf(LicenseName("license-$index")),
                        )
                    }
                val result =
                    LicenseEligibility.judge(
                        RequirementCollection.Collected(rows),
                        OperatorLicenses.Declared(emptyList()),
                        emptyPolicy,
                        version,
                    )
                val verdict = result.verdict
                withClue("결측 그룹 행이 있으면 미보유이므로 항상 Ineligible이어야 한다: $verdict") {
                    (verdict is LicenseVerdict.Ineligible) shouldBe true
                }
                if (verdict is LicenseVerdict.Ineligible) {
                    verdict.missingByGroup.keys shouldBe setOf(RequirementGroupId.Ungrouped)
                }
            }
        }
    }

    @Test
    fun `보유 선언 부재는 어떤 요건 조합에서도 Eligible이나 Ineligible로 접히지 않는다`() {
        runBlocking {
            checkAll(
                Arb.list(licenseNameArb, 1..4),
                Arb.list(Arb.int(1..3).map(Int::toString), 1..4),
            ) { names, groupNos ->
                val rows =
                    names.mapIndexed { index, name ->
                        RequirementRow.Parsed(
                            groupNo = LmtGrpNo(groupNos[index % groupNos.size]),
                            serialNo = LmtSno(index.toString()),
                            sourceField = RequirementSourceField.LcnsLmtNm,
                            licenseNames = listOf(LicenseName(name)),
                        )
                    }
                val result =
                    LicenseEligibility.judge(
                        RequirementCollection.Collected(rows),
                        OperatorLicenses.NotDeclared,
                        emptyPolicy,
                        version,
                    )
                result.verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared)
            }
        }
    }

    /**
     * verifier r1 F-1 — 그룹 유형(제한 면허·허용업종 전용·혼합)과 그룹 번호를 임의로 섞어도
     * 보유가 전혀 없으면 어떤 조합도 `Eligible` 을 내지 않는다. `restrictedRows` 가 빈 그룹의
     * `containsAll(emptySet())` 공허 참(PROBE A·D)이 회귀하면 이 property 가 잡는다.
     */
    @Test
    fun `보유 면허가 0이면 어떤 요건 조합에서도 Eligible이 나오지 않는다`() {
        runBlocking {
            checkAll(
                Arb.list(licenseNameArb, 1..5),
                Arb.list(Arb.int(1..3).map(Int::toString), 1..4),
                Arb.list(Arb.element(RequirementSourceField.entries), 1..5),
            ) { names, groupNos, sourceFields ->
                val rows =
                    names.mapIndexed { index, name ->
                        RequirementRow.Parsed(
                            groupNo = LmtGrpNo(groupNos[index % groupNos.size]),
                            serialNo = LmtSno(index.toString()),
                            sourceField = sourceFields[index % sourceFields.size],
                            licenseNames = listOf(LicenseName(name)),
                        )
                    }
                val result =
                    LicenseEligibility.judge(
                        RequirementCollection.Collected(rows),
                        OperatorLicenses.Declared(emptyList()),
                        emptyPolicy,
                        version,
                    )
                withClue("보유 0인데 Eligible이 나왔다: ${result.verdict}") {
                    (result.verdict is LicenseVerdict.Eligible) shouldBe false
                }
            }
        }
    }

    @Test
    fun `그룹 순서를 바꿔도 verdict는 그대로다`() {
        runBlocking {
            checkAll(Arb.list(Arb.int(1..3).map(Int::toString), 2..5)) { groupNos ->
                val distinctGroups = groupNos.toSet().toList()
                if (distinctGroups.size < 2) return@checkAll
                val rows =
                    distinctGroups.mapIndexed { index, groupNo ->
                        RequirementRow.Parsed(
                            groupNo = LmtGrpNo(groupNo),
                            serialNo = LmtSno(index.toString()),
                            sourceField = RequirementSourceField.LcnsLmtNm,
                            licenseNames = listOf(LicenseName("shared")),
                        )
                    }
                val held = OperatorLicenses.Declared(listOf(LicenseName("shared")))
                val forward =
                    LicenseEligibility.judge(RequirementCollection.Collected(rows), held, emptyPolicy, version).verdict
                val reversed =
                    LicenseEligibility
                        .judge(RequirementCollection.Collected(rows.reversed()), held, emptyPolicy, version)
                        .verdict
                forward shouldBe reversed
            }
        }
    }
}
