package bidvector.app.collection

import bidvector.procurement.BusinessDivision
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

/**
 * 개찰결과 수집 갈래의 입력(M6/6G D-6G-1·11·20) — `bidvector.opening-collection.mode=once` 일 때만
 * 바인딩된다. **기본값이 하나도 없다**: seed·층당 목표·호출 상한 둘은 전부 설정이 주어야 한다.
 * 코드가 「안전한 기본값」을 지어내면 운영자 승인 범위 밖의 실행이 조용히 가능해진다(D-6G-11).
 *
 * [from]·[to] 는 **공고일** 범위다(개찰일이 아니다) — 개찰결과 목록을 공고일 축으로 걷기 때문이고,
 * 그래야 층(업무 × 공고 주)이 응답 필드 없이 선다.
 */
@ConfigurationProperties(prefix = "bidvector.opening-collection")
data class OpeningCollectionProperties(
    val from: LocalDate,
    val to: LocalDate,
    val categories: List<String>,
    val samplingSeed: String,
    /**
     * 표본 **전체** 크기(D-6G-20·42 M-6) — 층마다 같은 수가 아니라 층 크기에 비례해 나뉜다.
     * 층당 고정 수로 두면 층 크기가 다를 때 작은 층이 과대표집돼, 층화가 막으려던 편향을
     * 층화가 만든다. 크기 결정식(상한·최소 필요 표본)은 정책이 정하고 배선은 그 값을 받는다.
     */
    val sampleSize: Int,
    val callsPerDay: Int,
    val callsTotal: Int,
    /**
     * 실행 상태 디렉터리(D-6G-39·45) — 저장소 **밖** 경로. 확정 표본·시도 원장·표본 해시가 여기 있다.
     * **이 디렉터리를 만들지 않는다**: 경로 오타 하나로 빈 디렉터리가 생기면 승인 상한이 0 에서
     * 시작하고 표본이 다시 뽑힌다. 기본값도 두지 않는다 — 어느 실행의 상태인지를 실행자가 매번 댄다.
     */
    val runStateDir: String,
    /** 원문 저장 행의 출처 표식 — 빌드 식별자를 모르는 로컬 실행이 값을 지어내지 않고 「모름」을 적는다. */
    val releaseSha: String = "unversioned",
)

private const val DEFAULT_SCSBID_BASE_URL = "https://apis.data.go.kr/1230000/as/ScsbidInfoService"

/**
 * 게이트웨이가 한 호출에 주는 행 수의 상한(D-6G2f-1) — **실측값이다**(2026-10-02 개찰결과 목록
 * 공사: `numOfRows=999` 요청에 `resultCode 00` · 항목 999 · `numOfRows` 999 에코, 같은 창의
 * `totalCount` 1522. 나머지 여섯 operation 은 2026-10-03, 개찰완료는 배포 전 사전 확인 몫이다).
 * `1000` 도 200 을 내지만 문서화된 상한을 모르므로 그것을 근거로 올리지 않는다.
 *
 * 기본값과 [KonepsOpeningEndpointProperties] 의 상한 검사가 **이 한 자리**에서 나온다 — 같은 수가
 * 두 자리에 있으면 한쪽만 바뀌는 날이 온다.
 */
internal const val KONEPS_MAX_ROWS_PER_PAGE = 999

/**
 * 개찰 축 오퍼레이션 경로(D-6G-19) — 업무 대분류마다 **다른 오퍼레이션**인 것 셋(개찰결과 목록·예비가격
 * 상세·기초금액)을 한 행에 묶는다. 한쪽만 바꾸고 다른 쪽을 잊는 표류를 행 형태가 막는다(D-6F9-1 관례).
 */
data class KonepsOpeningOperationProperties(
    val openingResultListPath: String,
    val reservePriceDetailPath: String,
    val baseAmountPath: String,
    val division: BusinessDivision,
) {
    init {
        require(openingResultListPath.isNotBlank()) { "개찰결과 목록 경로가 비어 있다" }
        require(reservePriceDetailPath.isNotBlank()) { "예비가격 상세 경로가 비어 있다" }
        require(baseAmountPath.isNotBlank()) { "기초금액 조회 경로가 비어 있다" }
    }
}

/**
 * 개찰 축 엔드포인트(D-6G-19) — **서비스가 둘이다.** 개찰결과 목록·예비가격 상세·개찰완료는
 * 낙찰정보서비스(`as/ScsbidInfoService`)이고, 입찰가격산식 A·기초금액 조회는 입찰공고정보서비스
 * (`ad/BidPublicInfoService`, [KonepsEndpointProperties.baseUrl])다. 한 base 로 접으면 절반이 404 다.
 *
 * 개찰완료·A값은 업무 접미가 **없는 단일 오퍼레이션**이라 경로가 하나다(P-1 §1.5).
 * 기본 표는 공사·용역 둘이다 — 물품은 이 slice 가 검증하지 않아 짓지 않는다.
 */
@ConfigurationProperties(prefix = "bidvector.koneps.opening")
data class KonepsOpeningEndpointProperties(
    val scsbidBaseUrl: String = DEFAULT_SCSBID_BASE_URL,
    val openingCompletePath: String = "getOpengResultListInfoOpengCompt",
    val bidPriceFormulaAPath: String = "getBidPblancListBidPrceCalclAInfo",
    val operations: Map<String, KonepsOpeningOperationProperties> =
        mapOf(
            "construction" to
                KonepsOpeningOperationProperties(
                    openingResultListPath = "getOpengResultListInfoCnstwk",
                    reservePriceDetailPath = "getOpengResultListInfoCnstwkPreparPcDetail",
                    baseAmountPath = "getBidPblancListInfoCnstwkBsisAmount",
                    division = BusinessDivision.CONSTRUCTION,
                ),
            "service" to
                KonepsOpeningOperationProperties(
                    openingResultListPath = "getOpengResultListInfoServc",
                    reservePriceDetailPath = "getOpengResultListInfoServcPreparPcDetail",
                    baseAmountPath = "getBidPblancListInfoServcBsisAmount",
                    division = BusinessDivision.SERVICE,
                ),
        ),
    /**
     * 개찰 축 한 호출이 받을 행 수(D-6G2f-1) — **기본이 게이트웨이 상한**이다.
     *
     * 이 키의 한도는 키 × operation × 일 1,000 건이고, 개찰완료는 업무 공통 **단일 operation** 이라
     * 다섯 축에서 먼저 닫힌다(실수집 day1 실측: 476 공고에 1,000 호출). 한도 자체는 운영계정
     * 승인으로만 움직이므로, 같은 한도 안에서 공고 수를 늘리는 축은 **호출당 행 수**뿐이다.
     *
     * 되돌림은 재빌드 없이 이 값을 100 으로 주는 인자 하나다.
     */
    val rowsPerPage: Int = KONEPS_MAX_ROWS_PER_PAGE,
) {
    init {
        // 상한 밖 값은 **기동 거부**다 — 게이트웨이가 무엇을 하는지 모르는 수(**1,000 이상**: 1000 도
        // 실측된 적이 없다)로 실수집이 도는 길을 열지 않는다. 밖에 허락하는 것은 1..상한 안의 선택뿐이다.
        // 거부가 값싼 이유: 파라미터 거부는 분류된 코드(10·11 INPUT_ERROR · 12·20·30~32 NOT_RETRYABLE)로
        // 와서 그 축을 `FinalFailure` 로 **영구 정착**시킨다 — 기동 전에 막지 못하면 되돌릴 수 없다.
        require(rowsPerPage in 1..KONEPS_MAX_ROWS_PER_PAGE) {
            "bidvector.koneps.opening.rows-per-page 는 1..$KONEPS_MAX_ROWS_PER_PAGE 안이어야 한다: $rowsPerPage"
        }
    }
}
