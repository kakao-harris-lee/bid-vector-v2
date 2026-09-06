package bidvector.qualification

import bidvector.sharedkernel.PolicyVersion

/**
 * 면허 자격 판정 커널 — U-8(그룹 간 OR·그룹 내 AND) 폴딩을 커널 하나로 낸다(R-QUAL-01·02,
 * QUAL-03). 두 번째 fold 구현을 두지 않는다 — 다른 축(기술부문·협회 등)이 같은 규칙을
 * 필요로 하면 이 객체를 재사용한다.
 */
object LicenseEligibility {
    fun judge(
        collection: RequirementCollection,
        operatorLicenses: OperatorLicenses,
        policyData: LicenseQualificationPolicyData,
        policyVersion: PolicyVersion,
    ): LicenseJudgement =
        when (collection) {
            RequirementCollection.DataAbsent -> {
                absentJudgement(UncertainReason.RequirementDataAbsent, policyVersion)
            }

            RequirementCollection.CollectionFailed -> {
                absentJudgement(UncertainReason.CollectionFailed, policyVersion)
            }

            is RequirementCollection.Collected -> {
                judgeCollected(collection.rows, operatorLicenses, policyData, policyVersion)
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
    policyData: LicenseQualificationPolicyData,
    policyVersion: PolicyVersion,
): LicenseJudgement {
    if (rows.isEmpty()) return absentJudgement(UncertainReason.RequirementDataAbsent, policyVersion)

    val summary = summarize(rows)
    val groups = groupRows(summary.parsedRows)
    return when {
        groups.isEmpty() -> {
            uncertainJudgement(UncertainReason.RequirementUnparsable, summary, policyVersion)
        }

        operatorLicenses is OperatorLicenses.NotDeclared -> {
            uncertainJudgement(UncertainReason.OperatorLicensesNotDeclared, summary, policyVersion)
        }

        else -> {
            judgeWithHeldLicenses(
                operatorLicenses as OperatorLicenses.Declared,
                groups,
                summary,
                policyData,
                policyVersion,
            )
        }
    }
}

private fun judgeWithHeldLicenses(
    operatorLicenses: OperatorLicenses.Declared,
    groups: Map<RequirementGroupId, List<RequirementRow.Parsed>>,
    summary: RowSummary,
    policyData: LicenseQualificationPolicyData,
    policyVersion: PolicyVersion,
): LicenseJudgement {
    val heldKeys = operatorLicenses.licenseNames.map { licenseComparisonKey(it, policyData.aliasTable) }.toSet()
    val verdict = foldGroups(groups, heldKeys, policyData.aliasTable)
    return LicenseJudgement(
        verdict = verdict,
        requiredLicenses = summary.requiredLicenses,
        missingByGroup = (verdict as? LicenseVerdict.Ineligible)?.missingByGroup.orEmpty(),
        unparsableRowCount = summary.unparsableCount,
        requirementSourceFields = summary.sourceFields,
        policyVersion = policyVersion,
    )
}

private fun groupRows(parsedRows: List<RequirementRow.Parsed>): Map<RequirementGroupId, List<RequirementRow.Parsed>> =
    parsedRows.groupBy { row -> row.groupNo?.let { RequirementGroupId.Numbered(it) } ?: RequirementGroupId.Ungrouped }

/**
 * 그룹 하나의 평가 — 제한 면허(`LcnsLmtNm`)만으로 충족되는지와, 허용업종(`PermsnIndstrytyList`)
 * 단독 보유로 인해 결합 규칙이 갈리는지(ambiguous)를 함께 낸다(§3.2.5, `OPEN-QUAL-11`).
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

private fun evaluateGroup(
    id: RequirementGroupId,
    rows: List<RequirementRow.Parsed>,
    heldKeys: Set<LicenseName>,
    aliasTable: LicenseAliasTable,
): GroupEvaluation {
    val restrictedRows = rows.filter { it.sourceField == RequirementSourceField.LcnsLmtNm }
    val permsnRows = rows.filter { it.sourceField == RequirementSourceField.PermsnIndstrytyList }
    val restrictedSatisfied = heldKeys.containsAll(requiredKeys(restrictedRows, aliasTable))
    val permsnSatisfied = permsnRows.isNotEmpty() && heldKeys.containsAll(requiredKeys(permsnRows, aliasTable))
    val ambiguous = !restrictedSatisfied && permsnSatisfied
    val missing =
        rows
            .flatMap { it.licenseNames }
            .distinct()
            .filterNot { licenseComparisonKey(it, aliasTable) in heldKeys }
            .toSet()
    return GroupEvaluation(id, restrictedSatisfied, ambiguous, missing)
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
