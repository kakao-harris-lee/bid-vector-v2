package bidvector.app.collection

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

/**
 * 낙찰 목록 수집 갈래의 입력(M9/award-ingest D-1·D-3) — `bidvector.award-collection.mode=once` 일 때만
 * 바인딩된다. **기본값이 하나도 없다**: 개찰일 창·호출 상한 둘·실행 상태 디렉터리는 전부 설정이 줘야
 * 한다. 코드가 「안전한 기본값」을 지어내면 운영자 승인 범위 밖의 실 호출이 조용히 가능해진다.
 *
 * [from]·[to] 는 **개찰일** 범위다 — 낙찰 목록을 개찰일시 축으로 걷기 때문이다(그 군의 `inqryDiv` 가
 * 개찰일시다). 긴 기간은 조각으로 나눠 여러 번 돌린다(조각 상한은 `AWARD_COLLECTION_RANGE_POLICY`).
 */
@ConfigurationProperties(prefix = "bidvector.award-collection")
data class AwardCollectionProperties(
    val from: LocalDate,
    val to: LocalDate,
    val categories: List<String>,
    val callsPerDay: Int,
    val callsTotal: Int,
    /**
     * 실행 상태 디렉터리 — 저장소 **밖** 경로이고 **개찰결과 갈래와 다른 디렉터리**다. 상한 회계는 그
     * 디렉터리의 시도 원장에서 seed 되므로, 디렉터리를 공유하면 이 갈래의 총 상한이 남의 호출로 소진된다.
     * 디렉터리를 만들지 않는다(없으면 기동 거부) — 경로 오타 하나로 빈 디렉터리가 생기면 승인 상한이
     * 0 에서 다시 시작한다.
     */
    val runStateDir: String,
    /** 원문 저장 행의 출처 표식 — 빌드 식별자를 모르는 로컬 실행이 값을 지어내지 않고 「모름」을 적는다. */
    val releaseSha: String = "unversioned",
)

/**
 * 낙찰 목록 엔드포인트(M9/award-ingest D-1) — 낙찰정보서비스의 **목록 오퍼레이션만** 든다. 상세 축
 * 경로가 이 표에 없는 것이 이 갈래가 공고당 호출을 내지 않는다는 선언이다.
 *
 * 기본 표는 공사·용역 둘이다 — 물품·외자는 수집 범위 밖이라 짓지 않는다(scope.md D-1).
 */
@ConfigurationProperties(prefix = "bidvector.koneps.award")
data class KonepsAwardEndpointProperties(
    /**
     * 낙찰정보서비스의 base — 형제 갈래(`bidvector.koneps.opening`)와 **같은 서비스**이고 기본값도 같은
     * 문자열이다. 한 자리에서 공유하려면 그 갈래의 설정 타입을 고쳐야 하는데 그 파일은 다른 레인이
     * 쥐고 있어 이 slice 의 범위 밖이다(알려진 제한 — 뒤에 한 자리로 모은다).
     */
    val scsbidBaseUrl: String = AWARD_SCSBID_BASE_URL,
    /** 업종 키 → 낙찰 목록 오퍼레이션 경로. 업무 대분류는 **쓰지 않는다**(원문만 적재한다). */
    val operations: Map<String, String> =
        mapOf(
            "construction" to "getScsbidListSttusCnstwk",
            "service" to "getScsbidListSttusServc",
        ),
    /** 한 호출이 받을 행 수 — 기본이 게이트웨이 상한이다(형제 갈래와 같은 한 자리에서 온다). */
    val rowsPerPage: Int = KONEPS_MAX_ROWS_PER_PAGE,
) {
    init {
        require(operations.isNotEmpty()) { "bidvector.koneps.award.operations 가 비어 있다" }
        require(operations.values.none { it.isBlank() }) { "낙찰 목록 오퍼레이션 경로가 비어 있다" }
        // 상한 밖 값은 **기동 거부**다 — 게이트웨이가 무엇을 하는지 모르는 수로 실 수집이 도는 길을
        // 열지 않는다(거부는 분류된 코드로 와서 그 축을 영구 실패로 정착시킬 수 있다).
        require(rowsPerPage in 1..KONEPS_MAX_ROWS_PER_PAGE) {
            "bidvector.koneps.award.rows-per-page 는 1..$KONEPS_MAX_ROWS_PER_PAGE 안이어야 한다: $rowsPerPage"
        }
    }
}

private const val AWARD_SCSBID_BASE_URL = "https://apis.data.go.kr/1230000/as/ScsbidInfoService"
