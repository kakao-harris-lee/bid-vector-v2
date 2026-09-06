package bidvector.qualification

import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution

/**
 * 면허 자격 판정 커널 — U-8(그룹 간 OR·그룹 내 AND) 폴딩을 커널 하나로 낸다(R-QUAL-01·02,
 * QUAL-03). 두 번째 fold 구현을 두지 않는다 — 다른 축(기술부문·협회 등)이 같은 규칙을
 * 필요로 하면 이 객체를 재사용한다. 그룹 폴딩 자체(그룹 나누기·평가·OR 결합)는
 * `LicenseGroupFolding.kt` 가 갖는다 — 이 파일은 판정 봉투 조립(요약·분기·`LicenseJudgement`
 * 구성)만 갖는다(detekt `TooManyFunctions`, 크기 한도 회피가 아니라 관심사 분리).
 *
 * verifier r1 F-5 — `policy` 는 값과 그 값을 해석한 `PolicyVersion` 을 함께 나르는
 * `Resolution.Resolved` 하나다. 값과 version 을 독립 인자로 받으면 호출부가 서로 다른
 * 정책 시점을 섞어 부를 수 있어 decision 23(「판정에 쓴 정책의 version 을 싣는다」)이
 * 관례에 머문다 — 하나로 받으면 그 사실이 타입으로 강제된다.
 */
object LicenseEligibility {
    fun judge(
        collection: RequirementCollection,
        operatorLicenses: OperatorLicenses,
        policy: Resolution.Resolved<LicenseQualificationPolicyData>,
    ): LicenseJudgement =
        when (collection) {
            RequirementCollection.DataAbsent -> {
                absentJudgement(UncertainReason.RequirementDataAbsent, policy.version)
            }

            RequirementCollection.CollectionFailed -> {
                absentJudgement(UncertainReason.CollectionFailed, policy.version)
            }

            is RequirementCollection.Collected -> {
                judgeCollected(collection.rows, operatorLicenses, policy)
            }
        }
}

private fun absentJudgement(
    reason: UncertainReason,
    policyVersion: PolicyVersion,
): LicenseJudgement =
    LicenseJudgement(
        verdict = LicenseVerdict.Uncertain(reason),
        requiredLicenses = null,
        missingByGroup = emptyMap(),
        unparsableRowCount = 0,
        requirementSourceFields = emptySet(),
        policyVersion = policyVersion,
    )

/** [judgeCollected] 가 매 분기에서 되풀이해 계산하지 않도록 행 요약을 한 번만 낸다. */
private data class RowSummary(
    val parsedRows: List<RequirementRow.Parsed>,
    val unparsableCount: Int,
    val sourceFields: Set<RequirementSourceField>,
    val requiredLicenses: List<LicenseName>,
)

/**
 * `sortedBy` 는 인라인 비교자 합성 class 를 내는데 그 SourceFile 이 우리 소스가 아닌 stdlib
 * `Comparisons.kt` 로 찍혀 `jarContentGate` 를 깬다(실측) — `String`(Comparable) 자연 순서를
 * 직접 쓰는 `sorted()` 로 같은 결과를 낸다(합성 class 를 만들지 않는다).
 */
private fun sortedRequiredLicenses(parsedRows: List<RequirementRow.Parsed>): List<LicenseName> =
    parsedRows
        .flatMap { it.licenseNames }
        .map { it.value }
        .distinct()
        .sorted()
        .map(::LicenseName)

private fun summarize(rows: List<RequirementRow>): RowSummary {
    val parsedRows = rows.filterIsInstance<RequirementRow.Parsed>()
    return RowSummary(
        parsedRows = parsedRows,
        unparsableCount = rows.count { it is RequirementRow.Unparsable },
        sourceFields = parsedRows.map { it.sourceField }.toSet(),
        requiredLicenses = sortedRequiredLicenses(parsedRows),
    )
}

/** 행은 있었으나(§3.2.1) 폴딩까지 가지 못하고 멈추는 두 분기(전부 파싱 실패·보유 선언 부재)가 쓴다. */
private fun uncertainJudgement(
    reason: UncertainReason,
    summary: RowSummary,
    policyVersion: PolicyVersion,
): LicenseJudgement =
    LicenseJudgement(
        verdict = LicenseVerdict.Uncertain(reason),
        requiredLicenses = summary.requiredLicenses,
        missingByGroup = emptyMap(),
        unparsableRowCount = summary.unparsableCount,
        requirementSourceFields = summary.sourceFields,
        policyVersion = policyVersion,
    )

private fun judgeCollected(
    rows: List<RequirementRow>,
    operatorLicenses: OperatorLicenses,
    policy: Resolution.Resolved<LicenseQualificationPolicyData>,
): LicenseJudgement {
    if (rows.isEmpty()) return absentJudgement(UncertainReason.RequirementDataAbsent, policy.version)

    val summary = summarize(rows)
    val groups = groupRows(summary.parsedRows)
    return when {
        groups.isEmpty() -> {
            uncertainJudgement(UncertainReason.RequirementUnparsable, summary, policy.version)
        }

        operatorLicenses is OperatorLicenses.NotDeclared -> {
            uncertainJudgement(UncertainReason.OperatorLicensesNotDeclared, summary, policy.version)
        }

        else -> {
            judgeWithHeldLicenses(operatorLicenses as OperatorLicenses.Declared, groups, summary, policy)
        }
    }
}

private fun judgeWithHeldLicenses(
    operatorLicenses: OperatorLicenses.Declared,
    groups: Map<RequirementGroupId, List<RequirementRow.Parsed>>,
    summary: RowSummary,
    policy: Resolution.Resolved<LicenseQualificationPolicyData>,
): LicenseJudgement {
    val aliasTable = policy.value.aliasTable
    val heldKeys = operatorLicenses.licenseNames.map { licenseComparisonKey(it, aliasTable) }.toSet()
    val verdict = foldGroups(groups, heldKeys, aliasTable)
    return LicenseJudgement(
        verdict = verdict,
        requiredLicenses = summary.requiredLicenses,
        missingByGroup = (verdict as? LicenseVerdict.Ineligible)?.missingByGroup.orEmpty(),
        unparsableRowCount = summary.unparsableCount,
        requirementSourceFields = summary.sourceFields,
        policyVersion = policy.version,
    )
}
