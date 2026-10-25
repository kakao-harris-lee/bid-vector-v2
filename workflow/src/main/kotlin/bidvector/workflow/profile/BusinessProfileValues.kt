package bidvector.workflow.profile

import bidvector.qualification.LicenseName
import bidvector.workflow.evaluation.UnjudgedQualificationAxis
import java.time.LocalDate

/**
 * 운영자 상호(SET-01 대사의 운영자 쪽 입력) — **입력한 원문 그대로**다.
 *
 * 앞뒤 공백도 자르지 않는다: 자르는 것도 정규화이고, 정규화는 대사 시점의 커널이 한 번만 한다
 * (이중 정규화 금지 — scope 산출물 5, g1set01 의 `OperatorTradeName.Declared(rawName)` 와 맞물린다).
 * 공백뿐인 값은 상호가 아니다 — 「없음」은 이 타입의 `null` 이다.
 *
 * `toString()` 을 가린다 — 상호는 운영자 사업 정보이고 예외 문면 · 로그에 실리면 안 된다(P4).
 */
data class TradeName(
    val raw: String,
) {
    init {
        require(raw.isNotBlank()) { "상호는 공백뿐일 수 없다 — 없으면 null 이다" }
        require(raw.codePointLength() <= ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS) {
            "상호는 ${ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS} 자를 넘을 수 없다"
        }
    }

    override fun toString(): String = "TradeName(***)"
}

/**
 * 본사 소재지 시·도(QUAL-08 의 **입력**) — 닫힌 어휘 17. 판정은 아직 없다([UnjudgedQualificationAxis]).
 *
 * V12 `region_terms` 를 재사용하지 않는 이유: 그 값은 텍스트 합성용 자유 어휘이고, QUAL-08 의
 * acceptance 는 「텍스트가 아니라 구조화 지역제한 필드로 판정」이다(capability-map QUAL-08).
 * 자유 텍스트를 이 축의 입력으로 두면 나중의 판정이 금지된 쪽으로 기운다(checklist §0.2).
 */
enum class HeadquartersRegion(
    val label: String,
) {
    SEOUL("서울특별시"),
    BUSAN("부산광역시"),
    DAEGU("대구광역시"),
    INCHEON("인천광역시"),
    GWANGJU("광주광역시"),
    DAEJEON("대전광역시"),
    ULSAN("울산광역시"),
    SEJONG("세종특별자치시"),
    GYEONGGI("경기도"),
    GANGWON("강원특별자치도"),
    CHUNGBUK("충청북도"),
    CHUNGNAM("충청남도"),
    JEONBUK("전북특별자치도"),
    JEONNAM("전라남도"),
    GYEONGBUK("경상북도"),
    GYEONGNAM("경상남도"),
    JEJU("제주특별자치도"),
}

/** 나라장터 입찰참가자격 등록분야 — 닫힌 어휘 셋(자기 신고, 진위 확인은 범위 밖). */
enum class NaraRegistrationField(
    val label: String,
) {
    GOODS("물품"),
    SERVICE("용역"),
    CONSTRUCTION("공사"),
}

/**
 * 나라장터 준비 상태(UX §6.2 A — 자기 신고). **「미응답」이 값이다** — `null` 이나 「미등록」으로
 * 접지 않는다: 운영자가 아직 답하지 않은 것과 「등록하지 않았다」고 답한 것은 화면 ③ 의 경고가
 * 다르다.
 *
 * 등록분야 · 인증서 만료일은 [Registered] 에만 있다 — 「미등록인데 분야가 있다」는 모순이 타입으로
 * 표현되지 않는다(V22 의 짝 CHECK 가 표 층의 둘째 겹이다).
 */
sealed interface NaraReadiness {
    data object Unanswered : NaraReadiness

    data object NotRegistered : NaraReadiness

    /**
     * 등록했다고 답했다. 분야가 비었거나 만료일이 없을 수 있다 — 입력 도중의 상태이고, 체크리스트가
     * 그것을 「미완」으로 판정한다(지어 채우지 않는다).
     *
     * 만료일은 **사업자용**(전자서명) 인증서 하나다 — 투찰 서명에 쓰는 쪽이다(UX §2.1, D-7C4-1 Q3).
     * `toString()` 은 만료일을 가린다(P4 — 운영자 사업 정보).
     */
    data class Registered(
        val fields: Set<NaraRegistrationField>,
        val businessCertificateExpiresOn: LocalDate?,
    ) : NaraReadiness {
        override fun toString(): String = "Registered(***)"
    }
}

/**
 * 계정의 사업 정보 한 절 — 상호 · 본사 소재지 · 나라장터 준비. 이 절은 **통째로 교체**된다
 * (D-7C4-1 P5): 다른 절(V12 자격 사실 · 시평액)에 닿는 쓰기 경로가 이 타입에 없다.
 *
 * `null` 은 각각 「미설정」이다. 행이 없는 계정의 값은 [EMPTY] 이고, 그 값은 「아무것도 신고하지
 * 않았다」를 필드마다 명시한 것이다(빈 값을 지어낸 것이 아니다 — 어느 필드도 기본값으로 「신고됨」
 * 이 되지 않는다).
 */
data class BusinessDetails(
    val tradeName: TradeName?,
    val headquartersRegion: HeadquartersRegion?,
    val naraReadiness: NaraReadiness,
) {
    companion object {
        val EMPTY: BusinessDetails = BusinessDetails(null, null, NaraReadiness.Unanswered)
    }
}

/**
 * 면허(업종)별 시공능력평가액 한 행(QUAL-11 의 **입력**) — 판정은 아직 없다([UnjudgedQualificationAxis]).
 *
 * 금액은 원 단위 정수이고 0 을 받지 않는다. 기준 연도가 필수다(`OPEN-QUAL-10` 의 운영자 쪽 절반 —
 * 어느 해 평가액인지 모르는 값은 받지 않는다). 시평액은 가격이 아니므로 공유 커널의 `Basis` ·
 * `VatTreatment` 어휘를 빌려 오지 않는다 — 그 어휘에는 이 값에 맞는 칸이 없다(checklist §0.2).
 *
 * 수행실적(QUAL-10)은 이 타입에 없다 — capability 가 `근거 부족` 이라 받지 않는다(D-7C4-1 Q2).
 * `toString()` 은 금액을 가린다(P4 — 실적 규모는 민감 사업정보다).
 */
data class ConstructionCapacity(
    val licenseName: LicenseName,
    val amountWon: Long,
    val assessmentYear: Int,
) {
    init {
        require(licenseName.value.isNotBlank()) { "면허명은 공백뿐일 수 없다" }
        require(licenseName.value.codePointLength() <= ProfileInputPolicy.LICENSE_NAME_MAX_CODE_POINTS) {
            "면허명은 ${ProfileInputPolicy.LICENSE_NAME_MAX_CODE_POINTS} 자를 넘을 수 없다"
        }
        require(amountWon > 0 && amountWon <= ProfileInputPolicy.CAPACITY_AMOUNT_MAX_WON) {
            "시평액은 0 보다 크고 ${ProfileInputPolicy.CAPACITY_AMOUNT_MAX_WON} 원 이하여야 한다"
        }
        require(assessmentYear in ProfileInputPolicy.ASSESSMENT_YEAR_MIN..ProfileInputPolicy.ASSESSMENT_YEAR_MAX) {
            "기준 연도는 ${ProfileInputPolicy.ASSESSMENT_YEAR_MIN}~${ProfileInputPolicy.ASSESSMENT_YEAR_MAX} 이어야 한다"
        }
    }

    override fun toString(): String = "ConstructionCapacity(***)"
}
