package bidvector.app.http

import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.strategy.CategoryCode
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.HeadquartersRegion
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.NaraRegistrationField
import bidvector.workflow.profile.TradeName
import tools.jackson.databind.JsonNode
import java.time.LocalDate

/**
 * 프로필 요청 → 도메인 값(M7/7C④ 2b). **알려진 키 밖은 400** 이다(`BidCaseRequests` 와 같은 규율).
 *
 * **이 파일이 밖에 허락하지 않는 것**:
 *  · `accountId` — **자리가 없다.** 주체는 인증 필터가 적은 값에서만 온다(7B 가드가 타입 층에서도
 *    막는다: 요청 바인딩은 `AccountId` 를 짓지 못한다).
 *  · `updatedAt` — 자리가 없다. 시각은 use case 가 쥔 시계에서 온다.
 *  · 사업자번호 · 대표자명 · 인증서 원문 — 자리가 없다(V22 에 열이 없다, scope P4).
 *  · 「미등록인데 등록분야가 있다」 — **표현할 수 없다**: 분야 · 만료일은 등록 상태에서만 읽는다.
 */
internal object ProfileRequests {
    private val QUALIFICATION_KEYS = setOf("businessTypes", "licensesDeclared", "licenseNames", "regionTerms")
    private val DETAILS_KEYS =
        setOf(
            "tradeName",
            "headquartersRegion",
            "naraRegistration",
            "naraRegisteredFields",
            "businessCertificateExpiresOn",
        )
    private val CAPACITY_KEYS = setOf("licenseName", "amountWon", "assessmentYear")

    /**
     * 자격 사실(V12) — 면허의 **세 상태**가 요청에서도 보존된다: `licensesDeclared=false` 가 「미신고」,
     * `true` 에 빈 목록이 「신고했고 면허가 없다」다. 「미설정」(행 없음)은 요청으로 만들 수 없다 —
     * 지우는 표면을 이 slice 가 열지 않는다.
     */
    fun qualificationFacts(body: JsonNode): ProfileFacts {
        val payload = body.requireObject().requireKnownKeys(QUALIFICATION_KEYS)
        val declared = payload.requiredBoolean("licensesDeclared")
        val names = payload.requiredStringArray("licenseNames")
        if (!declared && names.isNotEmpty()) {
            throw InvalidEditRequestException("licenseNames 는 licensesDeclared 가 참일 때만 실을 수 있다")
        }
        return ProfileFacts(
            businessTypes = payload.requiredStringArray("businessTypes").map(::CategoryCode).toSet(),
            licenses =
                if (declared) OperatorLicenses.Declared(names.map(::LicenseName)) else OperatorLicenses.NotDeclared,
            regionTerms = payload.requiredStringArray("regionTerms"),
        )
    }

    /** 사업 정보 절 — **통째로** 받는다(부분 갱신이 다른 필드를 지우지 않게, D-7C4-1 P5). */
    fun businessDetails(body: JsonNode): BusinessDetails {
        val payload = body.requireObject().requireKnownKeys(DETAILS_KEYS)
        return BusinessDetails(
            tradeName = payload.optionalNonBlankText("tradeName")?.let { requestValue("tradeName") { TradeName(it) } },
            headquartersRegion = payload.optionalEnum("headquartersRegion", HeadquartersRegion.entries),
            naraReadiness = payload.naraReadiness(),
        )
    }

    fun constructionCapacity(body: JsonNode): ConstructionCapacity {
        val payload = body.requireObject().requireKnownKeys(CAPACITY_KEYS)
        val licenseName = payload.requiredNonBlankText("licenseName")
        val amountWon = payload.requiredLong("amountWon", minimum = 1L)
        val assessmentYear = payload.requiredInt("assessmentYear", minimum = 0)
        return requestValue("constructionCapacity") {
            ConstructionCapacity(LicenseName(licenseName), amountWon, assessmentYear)
        }
    }

    fun licenseName(body: JsonNode): LicenseName =
        LicenseName(body.requireObject().requireKnownKeys(setOf("licenseName")).requiredNonBlankText("licenseName"))
}

/** 등록 상태에서만 의미가 있는 두 키 — 다른 상태의 요청에 실려 있으면 **거부한다**. */
private val REGISTERED_ONLY_KEYS = listOf("naraRegisteredFields", "businessCertificateExpiresOn")

/**
 * 나라장터 준비 — 상태 이름 하나가 다른 두 필드의 **유무**를 정한다.
 *
 * 「미응답 · 미등록인데 분야 · 만료일이 실린」 요청은 받아 두고 버리지 않고 **거부한다**: 받고서 쓰지
 * 않는 입력은 계약 거짓말이고, 운영자에게는 「저장됐다」로 보인다. V22 의 짝 CHECK 가 같은 모순을
 * 표 층에서 막는다(두 겹).
 */
private fun JsonNode.naraReadiness(): NaraReadiness {
    val raw = requiredNonBlankText("naraRegistration")
    if (raw != NaraReadiness.REGISTERED_NAME) rejectRegisteredOnlyKeys()
    return when (raw) {
        NaraReadiness.UNANSWERED_NAME -> NaraReadiness.Unanswered
        NaraReadiness.NOT_REGISTERED_NAME -> NaraReadiness.NotRegistered
        NaraReadiness.REGISTERED_NAME ->
            NaraReadiness.Registered(
                fields = enumSet("naraRegisteredFields", NaraRegistrationField.entries),
                businessCertificateExpiresOn = optionalDate("businessCertificateExpiresOn"),
            )

        else -> throw InvalidEditRequestException(
            "naraRegistration 은 ${NaraReadiness.NAMES.joinToString("·")} 중 하나다(길이 ${raw.length})",
        )
    }
}

private fun JsonNode.rejectRegisteredOnlyKeys() {
    REGISTERED_ONLY_KEYS.forEach { key ->
        val node = get(key)
        if (node != null && !node.isNull) {
            throw InvalidEditRequestException(
                "$key 는 naraRegistration 이 ${NaraReadiness.REGISTERED_NAME} 일 때만 실을 수 있다",
            )
        }
    }
}

/** 없거나 `null` 이면 「미설정」. 값이 있으면 어휘 안이어야 한다(모르는 값을 미설정으로 접지 않는다). */
private fun <T : Enum<T>> JsonNode.optionalEnum(
    key: String,
    values: List<T>,
): T? {
    val node = get(key)
    if (node == null || node.isNull) return null
    return enumOf(key, values)
}

/** 닫힌 어휘의 **집합** — 중복 원소는 거부한다(표의 cardinality CHECK 와 같은 술어). */
private fun <T : Enum<T>> JsonNode.enumSet(
    key: String,
    values: List<T>,
): Set<T> {
    val raw = requiredStringArray(key)
    val parsed =
        raw.map { name ->
            values.firstOrNull { it.name == name }
                ?: throw InvalidEditRequestException("$key 는 ${values.joinToString("·") { it.name }} 의 부분집합이다")
        }
    if (parsed.size != parsed.toSet().size) throw InvalidEditRequestException("$key 에 같은 값이 두 번 있다")
    return parsed.toSet()
}

/** ISO-8601 날짜만 받는다(시각 판독과 같은 관례 — 표기 둘을 함께 받지 않는다). */
private fun JsonNode.optionalDate(key: String): LocalDate? {
    val raw = optionalNonBlankText(key) ?: return null
    return runCatching { LocalDate.parse(raw) }
        .getOrElse { throw InvalidEditRequestException("$key 는 ISO-8601 날짜여야 한다") }
}
