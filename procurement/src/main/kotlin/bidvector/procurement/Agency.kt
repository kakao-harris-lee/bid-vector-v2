package bidvector.procurement

/**
 * 발주기관 코드(D-3H-3, `OPEN-2B-AGENCY-ID` 1/2) — [CategoryCode]와 **같은 정규화 함수**
 * ([normalizeCategoryKey])를 재사용한다(`docs/discovery/data-dictionary.md` §6.3.1 「발주기관
 * 키의 Kotlin 정본은 기관 fact 가 생길 때 같은 함수로 둔다」). 생성은 [of] 하나뿐이고
 * `@ConsistentCopyVisibility`(4B-8 `CategoryCode` 관례)로 `copy()`도 그 경계 밖으로 나가지
 * 않는다.
 *
 * **빈/공백 원문은 예외가 아니라 `null`이다** — `CategoryCode.of`(항상 존재해야 하는 필수
 * 개념)와 달리 기관 코드는 KONEPS 문서상 옵션 필드라 결측이 정상 형태다(D-3H-2 「코드 결측 =
 * 기관 fact 결측(정직)」). `canonicalize`(business control flow)가 이 축에서 예외를 던지지
 * 않게 하는 구조적 장치다(CLAUDE.md 「business control flow에 exception을 쓰지 않는다」).
 */
@ConsistentCopyVisibility
data class AgencyCode private constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AgencyCode는 빈 문자열일 수 없다" }
    }

    companion object {
        /** 정규화 뒤 빈 문자열이면(원문이 공백뿐이었으면) `null` — 우회 (8). */
        fun of(raw: String): AgencyCode? {
            val normalized = normalizeCategoryKey(raw)
            return if (normalized.isBlank()) null else AgencyCode(normalized)
        }
    }
}

/**
 * 기관명(D-3H-3) — 표시·감사용. 원문 **trim만** 하고 정규화하지 않는다(별칭 사전을 두지
 * 않는 §6.3.1 과 같은 결 — 표기 흔들림을 그대로 보존한다, 우회 (7)). 동일성 판정에는
 * 쓰지 않는다([AgencyCode]가 그 자리를 진다) — 이름 키를 조회·매칭에 쓰지 않는다(D-3H-2).
 */
@ConsistentCopyVisibility
data class AgencyName private constructor(
    val value: String,
) {
    companion object {
        /** trim 뒤 빈 문자열이면 `null`(우회 (8)) — 예외로 흐르지 않는다. */
        fun of(raw: String): AgencyName? {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) null else AgencyName(trimmed)
        }
    }
}

/**
 * 발주기관 fact(D-3H-3) — 코드·이름 **각자 자기 필드**. [Notice.demandAgency]·
 * [Notice.noticeAgency]가 역할별로 각자 하나씩 갖는다 — 이 타입 자체는 역할을 모르고,
 * 폴백(수요기관 결측 시 공고기관 값을 빌리는 것)은 이 타입도 조립부([canonicalize])도
 * 하지 않는다(우회 (3), legacy 누수 재현 금지). 코드·이름이 둘 다 없으면 fact 자체가
 * 없다는 뜻이라 `Agency` 인스턴스를 만들지 않는다(호출부가 `null`을 낸다) — 그래서
 * `init`은 둘 다 `null`인 조합만 거부한다.
 */
data class Agency(
    val code: AgencyCode?,
    val name: AgencyName?,
) {
    init {
        require(code != null || name != null) { "Agency는 code·name 이 둘 다 null 일 수 없다" }
    }
}

/**
 * 발주기관 조립(D-3H-3, M3/3H-1) — `businessCategoryFrom`(`BusinessClassification.kt`)과 같은 관례로
 * `registry.contractsFor(concept)` 경유만 읽는다(scope.md 우회 (1)). [codeConcept]·
 * [nameConcept]는 호출부가 역할별로 고정해 넘긴다 — 이 함수 자신은 "수요"·"공고" 어느
 * 쪽인지 모른다(그래서 폴백이 구조적으로 불가능하다, 우회 (3)). 코드·이름이 둘 다 없으면
 * fact 자체가 없다(`null`). `Canonicalize.kt`가 아니라 여기 사는 이유는 detekt
 * `TooManyFunctions`(파일당 함수 수 상한, v2-지침서 §5) — Agency 조립은 Agency 타입과
 * 같은 파일이 자연스러운 경계이기도 하다.
 */
private fun agencyFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    codeConcept: FieldConcept,
    nameConcept: FieldConcept,
): Agency? {
    val code =
        registry
            .contractsFor(codeConcept)
            .firstOrNull()
            ?.let(observation::valueOf)
            ?.let(AgencyCode::of)
    val name =
        registry
            .contractsFor(nameConcept)
            .firstOrNull()
            ?.let(observation::valueOf)
            ?.let(AgencyName::of)
    return if (code == null && name == null) null else Agency(code, name)
}

/** 수요기관(`dminsttCd`·`dminsttNm`) — 엔진 `agency_id` 정본 축(D-3H-2). */
internal fun demandAgencyFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): Agency? = agencyFrom(observation, registry, FieldConcept.DEMAND_AGENCY_CODE, FieldConcept.DEMAND_AGENCY_NAME)

/** 공고기관(`ntceInsttCd`·`ntceInsttNm`) — [demandAgencyFrom]과 다른 축(자기 필드만). */
internal fun noticeAgencyFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): Agency? = agencyFrom(observation, registry, FieldConcept.NOTICE_AGENCY_CODE, FieldConcept.NOTICE_AGENCY_NAME)
