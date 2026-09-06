package bidvector.qualification

import bidvector.sharedkernel.PolicyVersion

/** `lmtGrpNo` 결측 행 전부가 접히는 자리 — 번호가 아니라 sealed 로 표현한다(U-8). */
sealed interface RequirementGroupId {
    data class Numbered(
        val groupNo: LmtGrpNo,
    ) : RequirementGroupId

    data object Ungrouped : RequirementGroupId
}

/**
 * 미결 처리 사유 — U-5 확정 넷 + [PermittedIndustryCombinationRuleUndecided](decision 25,
 * `OPEN-QUAL-11` 임시 처리). sealed 이므로 필요해지면 variant 를 더한다(명세 변경).
 */
sealed interface UncertainReason {
    data object RequirementDataAbsent : UncertainReason

    data object RequirementUnparsable : UncertainReason

    data object OperatorLicensesNotDeclared : UncertainReason

    data object CollectionFailed : UncertainReason

    data object PermittedIndustryCombinationRuleUndecided : UncertainReason
}

/**
 * 면허 자격 판정 — boolean 하나로 접히지 않는다. `Eligible`·`Ineligible`·`Uncertain` 은 서로
 * 다른 개념이고(`Uncertain ≠ Ineligible`, U-5), 이 타입에는 `orElse`·`isEligible(): Boolean`
 * 류의 접는 API 를 두지 않는다 — 소비자는 소진 `when` 만 쓴다(위협 모델 우회 (1)).
 */
sealed interface LicenseVerdict {
    data class Eligible(
        val satisfiedGroups: Set<RequirementGroupId>,
    ) : LicenseVerdict

    data class Ineligible(
        val missingByGroup: Map<RequirementGroupId, Set<LicenseName>>,
    ) : LicenseVerdict

    data class Uncertain(
        val reason: UncertainReason,
    ) : LicenseVerdict
}

/**
 * 유효기간 표시(U-7) — 기본값 없는 sealed 단일 variant. 검증 variant 는 만들지 않는다
 * (위협 모델 우회 (6) — 조용히 "검증됨"처럼 보이는 기본값 필드를 두지 않는다).
 */
sealed interface LicenseValidity {
    data object NotVerified : LicenseValidity
}

/**
 * 판정 봉투 — `verdict` 외에 동반 산출을 함께 나른다(§3.2.1). `missingByGroup` 은 `verdict`
 * 가 `Ineligible` 일 때 그 값을 그대로 투영한 것이고, 그 밖의 verdict 에서는 빈 맵이다 —
 * 두 자리에 서로 다른 값을 두지 않는다(단일 진실 공급원, [LicenseEligibility] 이 만든다).
 */
data class LicenseJudgement(
    val verdict: LicenseVerdict,
    val requiredLicenses: List<LicenseName>?,
    val missingByGroup: Map<RequirementGroupId, Set<LicenseName>>,
    val unparsableRowCount: Int,
    val requirementSourceFields: Set<RequirementSourceField>,
    val policyVersion: PolicyVersion,
    val validity: LicenseValidity = LicenseValidity.NotVerified,
)
