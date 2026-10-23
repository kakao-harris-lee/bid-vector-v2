package bidvector.app.http

import bidvector.workflow.access.AccountId
import jakarta.servlet.http.HttpServletRequest

/**
 * **임시 — 7B(D-7B-2) 머지 뒤 rebase 에서 이 파일과 그 test 를 삭제하고 7B 의 접근자로
 * 교체한다**(운영자 결정 D-7C3-2 ⓐ·ⓑ). 7B 가 세션 인증이 요청 속성에 심은 **typed**
 * `AccountId` 를 접근자 하나로 읽게 만들므로, audit 주체 **문자열을 파싱하는** 이 방식은 그
 * 판과 정면으로 어긋난다. 동결 SHA 에 이 파일이 남아 있으면 `not-ready` 다.
 *
 * 지금 두는 이유는 하나뿐이다: 7B 가 아직 머지되지 않아 그 접근자가 main 에 없고, 알림함
 * controller 가 계정 주체 없이는 컴파일되지 않는다. 그래서 **파싱을 이 한 자리에 모아** 두어
 * 교체가 파일 삭제 하나로 끝나게 한다(controller 는 `SessionAccount.of(request)` 한 줄만
 * 부른다 — 그 줄을 7B 접근자 호출로 바꾸는 것이 교체의 전부다).
 *
 * ---
 * 인증을 지난 요청에서 **계정 주체**를 꺼낸다(M7/7C③).
 *
 * 7D 의 기본 거부 필터는 「인증됐는가」까지만 보고 **어떤 주체인가**는 판정에 쓰지 않는다 —
 * 통과한 요청의 audit 주체 라벨만 요청 속성에 남긴다. 인증 경로 둘 중 **계정을 내는 것은
 * 세션뿐이다**: 운영자 자격 헤더 경로의 주체는 `operator` 이고 계정 행이 없다. 그래서 계정
 * 소유 데이터를 다루는 표면은 세션 경로로만 닿고, 자격 헤더로 오면 여기서 `null` 이 된다.
 *
 * **라벨 형식을 이 파일이 정하지 않는다** — 짓는 자리는 세션 인증기 하나이고, 이 함수는 그
 * 역이다. 두 자리가 갈리면 `SessionAccountTest` 의 **왕복 등식**이 붉다(형식을 양쪽에 적는
 * 대신 왕복으로 잠근다 — 7D 의 파일을 이 slice 가 고치지 않고도 닫히는 자리다).
 *
 * `null` 을 내는 경우 넷: 속성이 없다(필터 밖) · 라벨이 `unauthenticated` 다 · 접두가 다르다
 * (자격 헤더 경로) · 접두 뒤가 양의 정수가 아니다. **넷을 가르지 않는다** — 호출자에게는 모두
 * 「이 요청에는 계정 주체가 없다」 하나이고, 거부의 사유를 나누지 않는 7D 의 관례와 같다.
 */
object SessionAccount {
    /** 세션 인증기가 audit 주체 라벨에 쓰는 접두. 여기서 **읽기만** 한다. */
    private const val ACCOUNT_SUBJECT_PREFIX = "account:"

    fun of(request: HttpServletRequest): AccountId? =
        (request.getAttribute(AUDIT_SUBJECT_ATTRIBUTE) as? String)?.let(::parse)

    /**
     * 라벨 한 줄 → 계정. 식별자는 `account.id`(IDENTITY)라 **양의 정수**다 — `0`·음수·선행
     * 부호·공백은 그 열의 값이 될 수 없으므로 받지 않는다(`toLongOrNull` 은 `+1` 과 `-1` 을
     * 받아들인다. 조용히 넓히지 않는다).
     */
    internal fun parse(subject: String): AccountId? {
        if (!subject.startsWith(ACCOUNT_SUBJECT_PREFIX)) return null
        val raw = subject.removePrefix(ACCOUNT_SUBJECT_PREFIX)
        if (raw.isEmpty() || !raw.all { it in '0'..'9' }) return null
        return raw.toLongOrNull()?.takeIf { it > 0L }?.let(::AccountId)
    }
}
