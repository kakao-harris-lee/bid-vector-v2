package bidvector.app.http

import bidvector.qualification.LicenseJudgement
import bidvector.qualification.LicenseValidity
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.workflow.evaluation.UnjudgedQualificationAxis

/**
 * **자격 축의 어휘**(M7/7C ②, QUAL-01 · 03 · 05) — 한 판정 봉투에서 [QualificationFields] 를
 * 짓는다. 그룹 간 OR 을 합집합으로 접지 않는 규칙이 이 파일에 있다.
 *
 * 중간 보유 타입을 두지 않는다(탈락 축 파일과 같은 사유) — DTO 를 바로 짓는다.
 */
internal fun qualificationFieldsOf(judgement: LicenseJudgement): QualificationFields {
    val verdict = judgement.verdict
    val missingByGroup = judgement.missingByGroup
    val singleGroup = missingByGroup.entries.singleOrNull()
    return QualificationFields(
        licenseVerdict = licenseVerdictLabel(verdict),
        licenseUncertainReason = (verdict as? LicenseVerdict.Uncertain)?.reason?.let(::uncertainReasonCode),
        licenseRequiredLicenses = judgement.requiredLicenses?.map { it.value },
        licenseRequirementSourceFields = judgement.requirementSourceFields.map { it.name }.sorted(),
        licenseGroupsSatisfied = satisfiedGroupLabelsOf(verdict),
        licenseGroupsMissing = missingByGroup.keys.map(::requirementGroupLabel).sorted(),
        // 그룹이 둘 이상이면 **비운다** — 합집합은 그룹 간 OR 을 AND 로 오독시킨다.
        licenseMissingLicenses =
            singleGroup
                ?.value
                ?.map { it.value }
                ?.sorted()
                .orEmpty(),
        licenseMissingDetailState = missingDetailStateOf(missingByGroup.size).name,
        licenseUnparsableRowCount = judgement.unparsableRowCount,
        licenseValidity = licenseValidityLabelOf(judgement),
        licensePolicySource = judgement.policyVersion.source,
        licensePolicyEffectiveFrom = effectiveFromLabelOf(judgement.policyVersion),
        unjudgedAxes = UnjudgedQualificationAxis.entries.map { it.name },
    )
}

private fun satisfiedGroupLabelsOf(verdict: LicenseVerdict): List<String> =
    (verdict as? LicenseVerdict.Eligible)
        ?.satisfiedGroups
        ?.map(::requirementGroupLabel)
        ?.sorted()
        .orEmpty()

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
        UncertainReason.RequirementDataAbsent -> {
            "REQUIREMENT_DATA_ABSENT"
        }

        UncertainReason.RequirementUnparsable -> {
            "REQUIREMENT_UNPARSABLE"
        }

        UncertainReason.OperatorLicensesNotDeclared -> {
            "OPERATOR_LICENSES_NOT_DECLARED"
        }

        UncertainReason.CollectionFailed -> {
            "COLLECTION_FAILED"
        }

        UncertainReason.PermittedIndustryCombinationRuleUndecided -> {
            "PERMITTED_INDUSTRY_COMBINATION_RULE_UNDECIDED"
        }
    }

/** 그룹 라벨 — 번호 있는 그룹과 번호 없는 묶음을 구분한다(U-8). */
private fun requirementGroupLabel(group: RequirementGroupId): String =
    when (group) {
        is RequirementGroupId.Numbered -> "G${group.groupNo.value}"
        RequirementGroupId.Ungrouped -> "UNGROUPED"
    }

/** 면허 **유효기간 미검증**(U-7) — V2 는 유효기간을 보지 않고, 그 사실이 판정 결과에 드러난다. */
private fun licenseValidityLabelOf(judgement: LicenseJudgement): String =
    when (judgement.validity) {
        LicenseValidity.NotVerified -> "NOT_VERIFIED"
    }

/**
 * 정책 version 의 시행일 — **소진 `when`** 이다. `Initial`(정책의 최초 판, 시행일이라는 값
 * 자체가 없다)은 `null` 이고, 그 둘을 한 문자열로 합치지 않는다(합치면 구분자를 지어내야 하고
 * 호출부가 그것을 다시 쪼갠다).
 */
private fun effectiveFromLabelOf(version: PolicyVersion): String? =
    when (val effectiveFrom = version.effectiveFrom) {
        EffectiveFrom.Initial -> null
        is EffectiveFrom.On -> effectiveFrom.date.toString()
    }
