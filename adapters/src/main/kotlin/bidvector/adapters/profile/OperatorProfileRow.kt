package bidvector.adapters.profile

import bidvector.adapters.persistence.getTextList
import bidvector.adapters.persistence.setTextArray
import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.strategy.CategoryCode
import bidvector.workflow.evaluation.ProfileFacts
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * `operator_profile` 행 하나의 원시 컬럼 값(M6/6F-6 D-6F6-2) — [ProfileFacts]·
 * [OperatorLicenses] 는 이미 평범한 public 생성자를 갖는 값 타입이라(전략과 달리 `validate()`
 * 같은 유일 통로가 없다), 이 행이 하는 일은 열 ↔ 필드 변환뿐이다. `licensesDeclared`가
 * `OperatorLicenses`의 sealed 두 상태를 열로 나른다 — 이 필드 없이 `licenseNames`만 보면
 * `NotDeclared`와 `Declared(emptyList())`가 똑같이 빈 배열이 되어 구분이 사라진다.
 */
internal data class OperatorProfileRow(
    val businessTypes: List<String>,
    val licensesDeclared: Boolean,
    val licenseNames: List<String>,
    val regionTerms: List<String>,
)

internal fun ResultSet.toOperatorProfileRow(): OperatorProfileRow =
    OperatorProfileRow(
        businessTypes = getTextList("business_types"),
        licensesDeclared = getBoolean("licenses_declared"),
        licenseNames = getTextList("license_names"),
        regionTerms = getTextList("region_terms"),
    )

/**
 * 행 → [ProfileFacts](D-6F6-2 — 도메인 타입을 직접 만들지 않는다는 결정은 여기서 「기존
 * public 생성 경로만 쓴다」로 구현된다: [ProfileFacts]·[OperatorLicenses.Declared]·
 * [OperatorLicenses.NotDeclared]·[CategoryCode]·[LicenseName] 전부 평범한 public
 * 생성자다 — 이 함수는 그 생성자들을 그대로 부르고 새 값 표현을 지어내지 않는다).
 */
internal fun OperatorProfileRow.toProfileFacts(): ProfileFacts =
    ProfileFacts(
        businessTypes = businessTypes.map(::CategoryCode).toSet(),
        licenses =
            if (licensesDeclared) {
                OperatorLicenses.Declared(licenseNames.map(::LicenseName))
            } else {
                OperatorLicenses.NotDeclared
            },
        regionTerms = regionTerms,
    )

/** [toProfileFacts]의 역함수 — [ProfileFacts]의 필드를 그대로 옮긴다(자기 값을 지어 쓰지 않는다). */
internal fun ProfileFacts.toRow(): OperatorProfileRow {
    val (declared, names) =
        when (val current = licenses) {
            is OperatorLicenses.Declared -> true to current.licenseNames.map(LicenseName::value)
            is OperatorLicenses.NotDeclared -> false to emptyList()
        }
    return OperatorProfileRow(
        businessTypes = businessTypes.map(CategoryCode::value),
        licensesDeclared = declared,
        licenseNames = names,
        regionTerms = regionTerms,
    )
}

/** [OperatorProfileRow]를 바인딩 순서대로 인자에 싣는다(`Sql.UPSERT_PROFILE`과 같은 순서). */
internal fun PreparedStatement.bindOperatorProfileRow(
    startIndex: Int,
    row: OperatorProfileRow,
): Int {
    var index = startIndex
    setTextArray(index++, row.businessTypes)
    setBoolean(index++, row.licensesDeclared)
    setTextArray(index++, row.licenseNames)
    setTextArray(index++, row.regionTerms)
    return index
}
