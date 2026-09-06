package bidvector.qualification

/**
 * 그룹 나누기·평가·OR 결합 — U-8(그룹 간 OR·그룹 내 AND)의 실제 폴딩 로직.
 * `LicenseEligibility.kt` 에서 detekt `TooManyFunctions` 를 피하려고 갈랐다(관심사 분리 —
 * 「판정 봉투 조립」 대 「그룹 폴딩」, 파일 KDoc 참고).
 */
internal fun groupRows(parsedRows: List<RequirementRow.Parsed>): Map<RequirementGroupId, List<RequirementRow.Parsed>> =
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
internal fun foldGroups(
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
