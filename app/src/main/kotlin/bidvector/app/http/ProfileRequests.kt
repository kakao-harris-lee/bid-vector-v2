package bidvector.app.http

import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.strategy.CategoryCode
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.HeadquartersRegion
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.NaraRegistration
import bidvector.workflow.profile.NaraRegistrationField
import bidvector.workflow.profile.ProfileInputPolicy
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
        val names = payload.freeTerms("licenseNames")
        if (!declared && names.isNotEmpty()) {
            throw InvalidEditRequestException("licenseNames 는 licensesDeclared 가 참일 때만 실을 수 있다")
        }
        return ProfileFacts(
            businessTypes = payload.freeTerms("businessTypes").map(::CategoryCode).toSet(),
            licenses =
                if (declared) OperatorLicenses.Declared(names.map(::LicenseName)) else OperatorLicenses.NotDeclared,
            regionTerms = payload.freeTerms("regionTerms"),
        )
    }

    /** 사업 정보 절 — **통째로** 받는다(부분 갱신이 다른 필드를 지우지 않게, D-7C4-1 P5). */
    fun businessDetails(body: JsonNode): BusinessDetails {
        val payload = body.requireObject().requireKnownKeys(DETAILS_KEYS)
        return BusinessDetails(
            tradeName =
                payload
                    .declaredText("tradeName", ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS)
                    ?.let { requestValue("tradeName") { TradeName(it) } },
            headquartersRegion = payload.optionalEnum("headquartersRegion", HeadquartersRegion.entries),
            naraReadiness = payload.naraReadiness(),
        )
    }

    fun constructionCapacity(body: JsonNode): ConstructionCapacity {
        val payload = body.requireObject().requireKnownKeys(CAPACITY_KEYS)
        val licenseName = payload.requiredLicenseName()
        val amountWon = payload.requiredLong("amountWon", minimum = 1L)
        val assessmentYear = payload.requiredInt("assessmentYear", minimum = 0)
        return requestValue("constructionCapacity") {
            ConstructionCapacity(LicenseName(licenseName), amountWon, assessmentYear)
        }
    }

    fun licenseName(body: JsonNode): LicenseName =
        LicenseName(body.requireObject().requireKnownKeys(setOf("licenseName")).requiredLicenseName())
}

/**
 * 면허명 — 공백뿐 · 길이 초과 · 제어 문자를 **요청 판독에서** 거부한다. 길이는 값 타입
 * ([ConstructionCapacity])도 보지만, 삭제 요청에는 그 값 타입이 없어 여기 한 자리가 두 경로를 덮는다.
 */
private fun JsonNode.requiredLicenseName(): String =
    declaredText("licenseName", ProfileInputPolicy.LICENSE_NAME_MAX_CODE_POINTS)
        ?: throw InvalidEditRequestException("licenseName 이 없다")

/**
 * V12 자유 어휘 배열(업종 코드 · 면허명 · 지역어) — **원소마다** 공백 · 길이 · 제어 문자를 보고,
 * 배열 크기와 중복도 본다(M7/7C④ 2b r2, v F4).
 *
 * 왜 여기인가: V12 는 이 축에 표 제약이 없고(짝 CHECK 하나뿐), 값 타입(`CategoryCode` · `LicenseName`)도
 * 불변식을 두지 않는다. 2b 가 **처음 HTTP 로 연** 쓰기 경로라 경계가 이 자리밖에 없다. V22 쪽은 같은
 * 성격의 규칙을 값 타입과 표 CHECK 두 겹으로 갖는다 — 그 비대칭을 좁힌다.
 *
 * 중복을 **거부한다**: `businessTypes` 는 도메인이 `Set` 이라 조용히 접히고(그 접힘은 운영자에게
 * 보이지 않는다), 나머지 둘은 그대로 저장된다. 접거나 담는 대신 거부가 한 가지 답이다.
 */
private fun JsonNode.freeTerms(key: String): List<String> {
    // **원소 규칙을 먼저** 본다(r2 CR L-6) — 나중에 보면 `["", ""]` 가 「공백뿐」이 아니라 「같은 값이
    // 두 번」으로 답해, 운영자가 고칠 자리를 잘못 가리킨다.
    val raw =
        requiredStringArray(key).map {
            it.requiringName(key, ProfileInputPolicy.QUALIFICATION_TERM_MAX_CODE_POINTS)
        }
    val rejection =
        when {
            raw.size > ProfileInputPolicy.QUALIFICATION_TERMS_MAX -> {
                "$key 는 ${ProfileInputPolicy.QUALIFICATION_TERMS_MAX} 개를 넘을 수 없다"
            }

            raw.size != raw.toSet().size -> {
                "$key 에 같은 값이 두 번 있다"
            }

            else -> {
                null
            }
        }
    if (rejection != null) throw InvalidEditRequestException(rejection)
    return raw
}

/** 등록 상태에서만 의미가 있는 두 키 — 다른 답의 요청에 실려 있으면 **거부한다**. */
private val REGISTERED_ONLY_KEYS = listOf("naraRegisteredFields", "businessCertificateExpiresOn")

/**
 * 나라장터 준비 — 답 하나가 다른 두 필드의 **유무**를 정한다.
 *
 * 「미응답 · 미등록인데 분야 · 만료일이 실린」 요청은 받아 두고 버리지 않고 **거부한다**: 받고서 쓰지
 * 않는 입력은 계약 거짓말이고, 운영자에게는 「저장됐다」로 보인다. V22 의 짝 CHECK 가 같은 모순을
 * 표 층에서 막는다(두 겹).
 *
 * 답을 **열거로** 읽고 `when` 으로 소진한다 — 이름 비교는 어휘 조회 한 자리(`RequestValues.kt`)에만
 * 있다. 그래야 이 패키지의 「빠른 문자열 비교 0」 구조 게이트가 흐려지지 않는다.
 */
private fun JsonNode.naraReadiness(): NaraReadiness =
    when (enumOf("naraRegistration", NaraRegistration.entries)) {
        NaraRegistration.UNANSWERED -> {
            rejectRegisteredOnlyKeys()
            NaraReadiness.Unanswered
        }

        NaraRegistration.NOT_REGISTERED -> {
            rejectRegisteredOnlyKeys()
            NaraReadiness.NotRegistered
        }

        NaraRegistration.REGISTERED -> {
            NaraReadiness.Registered(
                fields = enumSet("naraRegisteredFields", NaraRegistrationField.entries),
                businessCertificateExpiresOn = optionalDate("businessCertificateExpiresOn"),
            )
        }
    }

private fun JsonNode.rejectRegisteredOnlyKeys() {
    REGISTERED_ONLY_KEYS.forEach { key ->
        val node = get(key)
        if (node != null && !node.isNull) {
            throw InvalidEditRequestException(
                "$key 는 naraRegistration 이 ${NaraRegistration.REGISTERED} 일 때만 실을 수 있다",
            )
        }
    }
}

/**
 * ISO-8601 날짜만 받는다(시각 판독과 같은 관례 — 표기 둘을 함께 받지 않는다). **있으면 날짜여야
 * 한다** — 공백뿐인 값을 「미설정」으로 접지 않는다(상호와 같은 규율, v F6).
 */
private fun JsonNode.optionalDate(key: String): LocalDate? {
    val node = get(key)
    if (node == null || node.isNull) return null
    val raw = requiredNonBlankText(key)
    return runCatching { LocalDate.parse(raw) }
        .getOrElse { throw InvalidEditRequestException("$key 는 ISO-8601 날짜여야 한다") }
}
