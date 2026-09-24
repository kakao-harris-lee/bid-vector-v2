package bidvector.app.collection

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
 * 업종 → 공고 목록 오퍼레이션 기본 매핑표(D-6F8-3) — 코드 리터럴이 아니라 설정 데이터다. 환경변수·속성으로
 * 덮어쓸 수 있다(mock server test 가 [baseUrl] 을 갈아 끼우는 자리이기도 하다). 표에 없는 업종 이름은 기동
 * 실패다.
 */
@ConfigurationProperties(prefix = "bidvector.koneps")
data class KonepsEndpointProperties(
    val baseUrl: String = DEFAULT_KONEPS_BASE_URL,
    val operations: Map<String, String> =
        mapOf(
            "construction" to "getBidPblancListInfoCnstwk",
            "service" to "getBidPblancListInfoServc",
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
