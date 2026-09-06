package bidvector.qualification

/** 외부 식별자 — `lmtGrpNo`. 제로패딩 보존을 위해 문자열로만 나른다(R-QUAL-05). */
data class LmtGrpNo(
    val value: String,
)

/** 외부 식별자 — `lmtSno`(R-QUAL-05). 커널은 소비하지 않으나 행 추적을 위해 나른다. */
data class LmtSno(
    val value: String,
)

/** 면허 이름 — 원문 표기(정규화 전). 비교 키 산출은 [licenseComparisonKey] 하나뿐이다(R-QUAL-03). */
data class LicenseName(
    val value: String,
)

/**
 * 요건이 공고의 어느 필드에서 왔는가 — 판정 결과에 남아야 하는 동반 산출이다(`data-dictionary.md`
 * §3.2.1). `PermsnIndstrytyList` 단독 충족 여부는 [UncertainReason.PermittedIndustryCombinationRuleUndecided]
 * 로 미결 처리한다(§3.2.5, `OPEN-QUAL-11`).
 */
enum class RequirementSourceField {
    LcnsLmtNm,
    PermsnIndstrytyList,
}

/**
 * 구조화 요건 행 — 요건 원문 파싱은 어댑터 소관이다(D-3, M3 3B). 커널은 이미 분해된 행만
 * 받는다. `lmtGrpNo` 결측은 [Parsed.groupNo] `null` 로 표현하고, 결측 행 전부는 U-8 에 따라
 * 하나의 그룹으로 AND 폴딩된다([RequirementGroupId.Ungrouped]).
 */
sealed interface RequirementRow {
    /**
     * `licenseNames` 는 비어 있을 수 없다(verifier r1 F-2) — 이름을 하나도 못 읽었으면
     * [Unparsable] 을 쓴다(D-3). 빈 목록을 허용하면 그 행의 요구 키 집합이 공집합이 되어
     * `heldKeys.containsAll(emptySet())` 가 보유 0에서도 참이 되는 공허한 충족을 만든다.
     */
    data class Parsed(
        val groupNo: LmtGrpNo?,
        val serialNo: LmtSno,
        val sourceField: RequirementSourceField,
        val licenseNames: List<LicenseName>,
    ) : RequirementRow {
        init {
            require(licenseNames.isNotEmpty()) {
                "RequirementRow.Parsed.licenseNames 는 비어 있을 수 없다(serialNo=$serialNo) — " +
                    "이름을 못 읽었으면 RequirementRow.Unparsable 을 쓴다"
            }
        }
    }

    /** 파싱 실패 표시 — 이름 목록을 지어내지 않는다. [serialNo] 만 원문 추적용으로 남긴다. */
    data class Unparsable(
        val serialNo: LmtSno,
    ) : RequirementRow
}

/**
 * 공고의 요건 수집 결과 — 「행이 없다」와 「수집이 실패했다」는 다른 사유다
 * ([UncertainReason.RequirementDataAbsent] vs [UncertainReason.CollectionFailed]).
 */
sealed interface RequirementCollection {
    data class Collected(
        val rows: List<RequirementRow>,
    ) : RequirementCollection

    data object DataAbsent : RequirementCollection

    data object CollectionFailed : RequirementCollection
}

/** 운영자 보유 면허 선언 — 선언 부재는 미보유가 아니다(U-5, `Uncertain ≠ Ineligible`). */
sealed interface OperatorLicenses {
    data class Declared(
        val licenseNames: List<LicenseName>,
    ) : OperatorLicenses

    data object NotDeclared : OperatorLicenses
}
