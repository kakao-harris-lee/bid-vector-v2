package bidvector.app.http

import bidvector.qualification.OperatorLicenses
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.ProfileView
import bidvector.workflow.profile.UnjudgedQualificationAxis

/**
 * 프로필 조회 응답 — **평탄하다**(D-6A1-20 ⓑ): 모든 필드가 스칼라이거나 스칼라 배열이다. 그래서
 * 시평액 목록은 이 응답에 없고 **자기 경로의 최상위 배열**로 나간다.
 *
 * **면허의 세 상태가 응답에서도 보존된다**(D-7C4-2 와 같은 규율 — 세 상태를 두 상태로 접지 않는다):
 *  · [qualificationDeclared] `false` → 「미설정」(V12 행이 없다). 이때 나머지 자격 필드는 비어 있고,
 *    그 빈 값은 **신고된 빈 값이 아니다**.
 *  · `true` + [licensesDeclared] `false` → 「미신고」.
 *  · `true` + `true` → 「신고」. 그때만 [licenseNames] 가 운영자의 답이다(빈 목록도 답이다).
 *
 * [unjudgedAxes] 는 선언 표를 그대로 싣는다 — 입력이 있든 없든 **판정이 없는 축**을 응답이 말한다
 * (M7 완료 조건 G2). 화면이 이 목록에서 「아직 판정하지 않음」을 그린다(클라이언트가 추론하지 않는다).
 */
data class ProfileResponse(
    val qualificationDeclared: Boolean,
    val businessTypes: List<String>,
    val licensesDeclared: Boolean,
    val licenseNames: List<String>,
    val regionTerms: List<String>,
    val tradeName: String?,
    val headquartersRegion: String?,
    val naraRegistration: String,
    val naraRegisteredFields: List<String>,
    val businessCertificateExpiresOn: String?,
    val unjudgedAxes: List<String>,
) {
    companion object {
        fun from(view: ProfileView): ProfileResponse {
            val facts = view.qualification
            val declaredLicenses = facts?.licenses as? OperatorLicenses.Declared
            val readiness = view.details.naraReadiness
            val registered = readiness as? NaraReadiness.Registered
            return ProfileResponse(
                qualificationDeclared = facts != null,
                businessTypes =
                    facts
                        ?.businessTypes
                        .orEmpty()
                        .map { it.value }
                        .sorted(),
                licensesDeclared = declaredLicenses != null,
                licenseNames = declaredLicenses?.licenseNames.orEmpty().map { it.value },
                regionTerms = facts?.regionTerms.orEmpty(),
                // 상호는 **입력 원문 그대로** 나간다(앞뒤 공백도 자르지 않는다 — 이중 정규화 금지).
                tradeName = view.details.tradeName?.raw,
                headquartersRegion = view.details.headquartersRegion?.name,
                naraRegistration = readiness.registration.name,
                naraRegisteredFields =
                    registered
                        ?.fields
                        .orEmpty()
                        .map { it.name }
                        .sorted(),
                businessCertificateExpiresOn = registered?.businessCertificateExpiresOn?.toString(),
                unjudgedAxes = UnjudgedQualificationAxis.entries.map { it.name },
            )
        }
    }
}

/** 시평액 한 행 — 평탄하다. 목록은 이 타입의 **최상위 배열**로 나간다(감싸는 object 가 없다). */
data class ConstructionCapacityResponse(
    val licenseName: String,
    val amountWon: Long,
    val assessmentYear: Int,
) {
    companion object {
        fun from(capacity: ConstructionCapacity): ConstructionCapacityResponse =
            ConstructionCapacityResponse(
                licenseName = capacity.licenseName.value,
                amountWon = capacity.amountWon,
                assessmentYear = capacity.assessmentYear,
            )
    }
}
