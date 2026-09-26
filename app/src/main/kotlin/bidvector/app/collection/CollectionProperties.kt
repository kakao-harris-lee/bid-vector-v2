package bidvector.app.collection

import bidvector.procurement.BusinessDivision
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

/** 원문 저장 행의 출처 표식 기본값 — 빌드 식별자를 모르는 로컬 실행이 값을 지어내지 않고 「모름」을 그대로 적는다. */
private const val UNVERSIONED_RELEASE = "unversioned"

/**
 * 일회성 수집 러너의 입력(D-6F8-3) — `bidvector.collection.mode=once` 일 때만 바인딩된다(러너 배선이
 * 꺼져 있으면 이 값들은 아무도 읽지 않는다). 범위 상한·미래 금지는 여기서 자르지 않고 배선이 기동 실패로
 * 드러낸다(설정 오류를 조용히 고치지 않는다).
 */
@ConfigurationProperties(prefix = "bidvector.collection")
data class CollectionProperties(
    val from: LocalDate,
    val to: LocalDate,
    val categories: List<String>,
    val releaseSha: String = UNVERSIONED_RELEASE,
)

private const val DEFAULT_KONEPS_BASE_URL = "https://apis.data.go.kr/1230000/ad/BidPublicInfoService"

/**
 * 업종 하나가 부르는 공고 목록 오퍼레이션 — **경로와 업무 대분류를 한 행에 묶는다**(D-6F9-1, M6/6F-9). 응답에 대분류 필드가
 * 없어 「어느 오퍼레이션으로 받았는가」가 곧 대분류이므로, 행 형태가 막는 것은 **한쪽만 바꾸고 다른 쪽을 잊는 표류**다
 * (code-review r1 L9 — 행 안에서 서로 어긋난 값은 이 형태가 막지 못한다. 기본 표의 값 정합은 `CollectionWiringTest` 가
 * 고정하고, 어긋난 행을 주면 **설정한 대분류가 이긴다**는 것도 같은 test 가 잰다 — 경로에서 짓지 않는다).
 * 두 칸 다 필수다: [division] 은 누락·어휘 밖이 바인딩 실패고, [path] 는 공백이 기동 실패다(빈 경로가 HTTP 시점까지
 * 미뤄지지 않는다). 배선은 이 값을 어댑터 생성 인자로 넘길 뿐이고 어댑터가 URL 을 파싱하지 않는다.
 */
data class KonepsOperationProperties(
    val path: String,
    val division: BusinessDivision,
) {
    init {
        require(path.isNotBlank()) { "bidvector.koneps.operations.<업종>.path 가 비어 있다" }
    }
}

/**
 * 업종 → 공고 목록 오퍼레이션 기본 매핑표(D-6F8-3) — 코드 리터럴이 아니라 설정 데이터다. 환경변수·속성으로 덮어쓸 수 있다
 * (mock server test 가 [baseUrl] 을 갈아 끼우는 자리이기도 하다). 표에 없는 업종 이름은 기동 실패다. 기본 표는 공사·용역 둘이다 —
 * 물품·외자 오퍼레이션 경로는 이 slice 가 검증하지 않아 짓지 않는다(`OPEN-6F9-GOODS-FOREIGN-COLLECTION`).
 */
@ConfigurationProperties(prefix = "bidvector.koneps")
data class KonepsEndpointProperties(
    val baseUrl: String = DEFAULT_KONEPS_BASE_URL,
    val operations: Map<String, KonepsOperationProperties> =
        mapOf(
            "construction" to KonepsOperationProperties("getBidPblancListInfoCnstwk", BusinessDivision.CONSTRUCTION),
            "service" to KonepsOperationProperties("getBidPblancListInfoServc", BusinessDivision.SERVICE),
        ),
)

/**
 * KONEPS 서비스 키 설정(D-6F8-3·4) — 값은 환경변수 주입, **기본값 없음**. `data class` 가 아니다: 합성
 * `toString()`·`copy()` 가 원문을 로그·오류 화면으로 내보내는 경로가 되기 때문이다(`OperatorCredentialProperties`
 * 와 같은 근거). 원문은 배선이 [bidvector.adapters.koneps.ServiceKey] 로 감싸는 한 곳에서만 읽힌다.
 * 공개 설정([KonepsEndpointProperties])과 같은 접두를 쓰지만 클래스를 갈라 비밀이 공개 값 옆에 놓이지 않는다.
 */
@ConfigurationProperties(prefix = "bidvector.koneps")
class KonepsCredentialProperties(
    val serviceKey: String,
)
