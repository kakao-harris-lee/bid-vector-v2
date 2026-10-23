package bidvector.app.http

import bidvector.qualification.LicenseJudgement
import bidvector.qualification.LicenseValidity
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion

/**
 * **자격 축의 어휘**(M7/7C ②, QUAL-01 · 03 · 05) — 판정·사유·그룹 라벨과 정책 version 표기.
 * 그룹 간 OR 을 합집합으로 접지 않는 규칙이 이 파일에 있다.
 */

internal fun licenseValidityLabelOf(judgement: LicenseJudgement): String =
    when (judgement.validity) {
        LicenseValidity.NotVerified -> "NOT_VERIFIED"
    }


/**
 * 정책 version 의 시행일 — **소진 `when`** 이다. `Initial`(정책의 최초 판, 시행일이라는 값
 * 자체가 없다)은 `null` 이고, 그 둘을 한 문자열로 합치지 않는다(합치면 구분자를 지어내야 하고
 * 호출부가 그것을 다시 쪼갠다).
 */
internal fun effectiveFromLabelOf(version: PolicyVersion): String? =
    when (val effectiveFrom = version.effectiveFrom) {
        EffectiveFrom.Initial -> null
        is EffectiveFrom.On -> effectiveFrom.date.toString()
    }


/** 자격 축의 평탄한 칸들 — 한 판정에서 기계적으로 따라온다. */
internal class LicenseFields(
    val verdict: String,
    val uncertainReason: String?,
    val requiredLicenses: List<String>?,
    val sourceFields: List<String>,
    val groupsSatisfied: List<String>,
    val groupsMissing: List<String>,
    val missingLicenses: List<String>,
    val missingDetailState: String,
)


internal fun licenseFieldsOf(judgement: LicenseJudgement): LicenseFields {
    val verdict = judgement.verdict
    val satisfied =
        (verdict as? LicenseVerdict.Eligible)
            ?.satisfiedGroups
            ?.map(::requirementGroupLabel)
            .orEmpty()
    val missingByGroup = judgement.missingByGroup
    val singleGroup = missingByGroup.entries.singleOrNull()
    return LicenseFields(
        verdict = licenseVerdictLabel(verdict),
        uncertainReason = (verdict as? LicenseVerdict.Uncertain)?.reason?.let(::uncertainReasonCode),
        requiredLicenses = judgement.requiredLicenses?.map { it.value },
        sourceFields = judgement.requirementSourceFields.map { it.name }.sorted(),
        groupsSatisfied = satisfied.sorted(),
        groupsMissing = missingByGroup.keys.map(::requirementGroupLabel).sorted(),
        // 그룹이 둘 이상이면 **비운다** — 합집합은 그룹 간 OR 을 AND 로 오독시킨다.
        missingLicenses = singleGroup?.value?.map { it.value }?.sorted().orEmpty(),
        missingDetailState = missingDetailStateOf(missingByGroup.size).name,
    )
}


private fun missingDetailStateOf(missingGroupCount: Int): LicenseMissingDetailState =
    when (missingGroupCount) {
        0 -> LicenseMissingDetailState.NOT_APPLICABLE
        1 -> LicenseMissingDetailState.SINGLE_GROUP
        else -> LicenseMissingDetailState.MULTIPLE_GROUPS_NOT_FLATTENED
    }


private fun licenseVerdictLabel(verdict: LicenseVerdict): String =
    when (verdict) {
        is LicenseVerdict.Eligible -> "ELIGIBLE"
        is LicenseVerdict.Ineligible -> "INELIGIBLE"
        is LicenseVerdict.Uncertain -> "UNCERTAIN"
    }


private fun uncertainReasonCode(reason: UncertainReason): String =
    when (reason) {
        UncertainReason.RequirementDataAbsent -> "REQUIREMENT_DATA_ABSENT"
        UncertainReason.RequirementUnparsable -> "REQUIREMENT_UNPARSABLE"
        UncertainReason.OperatorLicensesNotDeclared -> "OPERATOR_LICENSES_NOT_DECLARED"
        UncertainReason.CollectionFailed -> "COLLECTION_FAILED"
        UncertainReason.PermittedIndustryCombinationRuleUndecided ->
            "PERMITTED_INDUSTRY_COMBINATION_RULE_UNDECIDED"
    }


/** 그룹 라벨 — 번호 있는 그룹과 번호 없는 묶음을 구분한다(U-8). */
private fun requirementGroupLabel(group: RequirementGroupId): String =
    when (group) {
        is RequirementGroupId.Numbered -> "G${group.groupNo.value}"
        RequirementGroupId.Ungrouped -> "UNGROUPED"
    }
