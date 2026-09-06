package bidvector.qualification

import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution

/**
 * 면허 자격 판정 커널 — U-8(그룹 간 OR·그룹 내 AND) 폴딩을 커널 하나로 낸다(R-QUAL-01·02,
 * QUAL-03). 두 번째 fold 구현을 두지 않는다 — 다른 축(기술부문·협회 등)이 같은 규칙을
 * 필요로 하면 이 객체를 재사용한다.
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

private fun groupRows(parsedRows: List<RequirementRow.Parsed>): Map<RequirementGroupId, List<RequirementRow.Parsed>> =
    parsedRows.groupBy { row -> row.groupNo?.let { RequirementGroupId.Numbered(it) } ?: RequirementGroupId.Ungrouped }

/**
 * 그룹 하나의 평가 — 제한 면허(`LcnsLmtNm`)만으로 충족되는지와, 허용업종(`PermsnIndstrytyList`)
 * 결합 규칙이 그룹의 운명을 갈라(ambiguous) `OPEN-QUAL-11` 로 미뤄야 하는지를 함께 낸다.
 */
private data class GroupEvaluation(
    val id: RequirementGroupId,
    val restrictedSatisfied: Boolean,
    val ambiguous: Boolean,
    val missing: Set<LicenseName>,
)

private fun requiredKeys(
    rows: List<RequirementRow.Parsed>,
    aliasTable: LicenseAliasTable,
): Set<LicenseName> = rows.flatMap { it.licenseNames }.map { licenseComparisonKey(it, aliasTable) }.toSet()

/**
 * verifier r1 F-1 — `restrictedRows` 가 비면 `containsAll(emptySet())` 이 공허하게 참이 되어
 * 허용업종 전용 그룹이 보유 0으로도 충족된 것처럼 보였다. **제한 면허 행이 실제로 있고 그
 * 요구를 전부 보유할 때만** 충족으로 센다.
 */
private fun restrictedSatisfied(
    restrictedRows: List<RequirementRow.Parsed>,
    heldKeys: Set<LicenseName>,
    aliasTable: LicenseAliasTable,
): Boolean = restrictedRows.isNotEmpty() && heldKeys.containsAll(requiredKeys(restrictedRows, aliasTable))

/**
 * verifier r1 재라운드 지침 — 그룹의 행이 **전부** `PermsnIndstrytyList` 면(제한 면허 행이
 * 하나도 없으면) 그 그룹의 운명 자체가 `OPEN-QUAL-11` (a)/(b) 그 질문이다. 보유 여부와
 * 무관하게 항상 결합 규칙 미결로 미룬다 — `Ineligible`(reading (b) 확정)·`Eligible`
 * (reading (a) 확정) 어느 쪽으로도 미결을 확정으로 쓰지 않는다. 제한 면허 행이 하나라도
 * 섞인 그룹(혼합 그룹)은 이 규칙 밖이다 — 제한 면허만으로 이미 갈릴 수 있고(§3.2.5 운영자
 * 판정: 제한 면허 충족이 이긴다), 갈리지 않을 때만(제한 미충족 + 허용업종 실제 보유)
 * 결합 규칙이 문제된다(license-011 대응).
 */
private fun isPermsnOnlyGroup(
    restrictedRows: List<RequirementRow.Parsed>,
    permsnRows: List<RequirementRow.Parsed>,
): Boolean = restrictedRows.isEmpty() && permsnRows.isNotEmpty()

private fun evaluateGroup(
    id: RequirementGroupId,
    rows: List<RequirementRow.Parsed>,
    heldKeys: Set<LicenseName>,
    aliasTable: LicenseAliasTable,
): GroupEvaluation {
    val restrictedRows = rows.filter { it.sourceField == RequirementSourceField.LcnsLmtNm }
    val permsnRows = rows.filter { it.sourceField == RequirementSourceField.PermsnIndstrytyList }
    val restricted = restrictedSatisfied(restrictedRows, heldKeys, aliasTable)
    val permsnSatisfied = permsnRows.isNotEmpty() && heldKeys.containsAll(requiredKeys(permsnRows, aliasTable))
    val ambiguous = !restricted && (isPermsnOnlyGroup(restrictedRows, permsnRows) || permsnSatisfied)
    val missing =
        rows
            .flatMap { it.licenseNames }
            .distinct()
            .filterNot { licenseComparisonKey(it, aliasTable) in heldKeys }
            .toSet()
    return GroupEvaluation(id, restricted, ambiguous, missing)
}

/**
 * 그룹 간 OR — 제한 면허로 충족되는 그룹이 하나라도 있으면 그쪽이 이긴다(§3.2.5 운영자 판정).
 * 그렇지 않고 허용업종 단독 보유로 결과가 갈리는 그룹이 있으면 결합 규칙 미결로 낸다.
 */
private fun foldGroups(
    groups: Map<RequirementGroupId, List<RequirementRow.Parsed>>,
    heldKeys: Set<LicenseName>,
    aliasTable: LicenseAliasTable,
): LicenseVerdict {
    val evaluations = groups.map { (id, rows) -> evaluateGroup(id, rows, heldKeys, aliasTable) }
    val satisfiedGroups = evaluations.filter { it.restrictedSatisfied }.map { it.id }.toSet()
    return when {
        satisfiedGroups.isNotEmpty() -> {
            LicenseVerdict.Eligible(satisfiedGroups)
        }

        evaluations.any { it.ambiguous } -> {
            LicenseVerdict.Uncertain(UncertainReason.PermittedIndustryCombinationRuleUndecided)
        }

        else -> {
            LicenseVerdict.Ineligible(evaluations.associate { it.id to it.missing })
        }
    }
}
