package bidvector.procurement

// 업무구분 세부 분류 조립(M6/6F-9 D-6F9-2) — `Canonicalize.kt` 에서 분리한 파일이다(detekt `TooManyFunctions`, 파일당 함수
// 수 — `Agency.kt` 의 기관 조립과 같은 전례). 전부 `registry.valueIn(concept)` 경유로만 읽는다(계약 없는 키는 이 경로에
// 들어올 수 없다, `businessCategoryFrom` 관례). 대분류는 여기 없다 — 필드가 아니라 관측의 `sourceDivision` 이다.

/**
 * 업무구분 코드·라벨이 **한 쌍으로** 나오는 원천 — 쌍 안에서만 코드와 라벨을 함께 읽고 다른 쌍의 라벨을 빌리지 않는다
 * (코드 `81111500` 에 라벨 `용역` 이 붙는 식의 축 섞임 방지, P-7). 앞 쌍이 코드를 내면 그 쌍이 이기고, 코드가 없으면
 * 다음 쌍으로 넘어간다. 레거시 쌍(`bsnsDivCd`·`bsnsDivNm` 축)은 운영 정책에 코드 행이 없어 test 정책에서만 서고,
 * 운영에서는 공공조달분류 쌍(D-6F9-2)이 서는 자리다.
 */
private data class CategorySource(
    val code: FieldConcept,
    val label: FieldConcept,
)

private val CATEGORY_SOURCES: List<CategorySource> =
    listOf(
        CategorySource(FieldConcept.BUSINESS_CATEGORY_CODE, FieldConcept.BUSINESS_CATEGORY_LABEL),
        CategorySource(FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE, FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME),
    )

/** 「공백뿐이면 없다」 규칙 하나 — 코드·라벨 두 자리가 같은 관용구를 쓴다(code-review r1 L6). */
private fun String?.presentTrimmed(): String? = this?.takeIf(String::isNotBlank)?.trim()

private fun categoryFrom(
    source: CategorySource,
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): BusinessCategory? =
    registry
        .valueIn(observation, source.code)
        .presentTrimmed()
        ?.let { code ->
            val label = registry.valueIn(observation, source.label).presentTrimmed()
            BusinessCategory(CategoryCode.of(code), label?.let(::CategoryLabel))
        }

/**
 * 업무구분(⑤ D-3A-5, COL-08) — 코드·라벨 두 값. 라벨은 원문 trim(다른 이름 칸과 같은 관례), 공백뿐이면 `null`(임의 라벨 금지).
 * **M6/6F-9 부터 라벨을 trim 한다**(code-review r1 L6 — 기존 `business_category_label` 칸의 거동 변경): 6F-9 전까지 이 자리는
 * 공백뿐인 값만 떨어뜨리고 양끝 공백은 그대로 저장했다. 코드 쪽 trim 은 [CategoryCode.of] 정규화 안에 있었고 라벨만 예외였다.
 */
internal fun businessCategoryFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): BusinessCategory? = CATEGORY_SOURCES.firstNotNullOfOrNull { categoryFrom(it, observation, registry) }

/** 용역구분(D-6F9-2) — 원문 trim, 빈 값은 `null`. 업무구분 라벨 칸과 다른 자기 칸이다. */
internal fun serviceDivisionFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): ServiceDivision? = registry.valueIn(observation, FieldConcept.SERVICE_DIVISION)?.let(ServiceDivision::of)

/** 주공종(D-6F9-2) — 이름만, 원문 trim·빈 값은 `null`. 코드를 만들지 않는다. */
internal fun mainConstructionTypeFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): MainConstructionType? =
    registry.valueIn(observation, FieldConcept.MAIN_CONSTRUCTION_TYPE)?.let(MainConstructionType::of)
