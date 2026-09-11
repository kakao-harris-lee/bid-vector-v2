package bidvector.workflow.notification

/**
 * 배달 경로 슬러그(scope.md ③, 설계 검토 (1)) — 허용 모양 하나만 받는다. 원문 식별자
 * (채팅 id·봇 비밀값·메일 주소)는 이 shape 를 통과하지 못한다: 숫자로 시작·`:` 포함·
 * 대문자·`@`·공백·81자↑ 는 전부 생성 실패다. 무엇이 위험한지 몰라도 슬러그가 아니면
 * 못 들어온다(우회 (7)).
 *
 * **PR #5 게이트 시정(privacy-gate)** — 거부 메시지는 [value] 원문을 담지 않는다. 이
 * 타입이 막겠다고 선언한 바로 그 값(채팅 id·봇 비밀값·메일 주소)이 `require`의
 * `IllegalArgumentException` 메시지로 새면 타입의 방어가 예외 경로에서 무효화된다 —
 * 길이만 남긴다(형태 위반 사실을 알기엔 충분하고, 원문 복원에는 쓸모없다).
 */
data class RouteKey(
    val value: String,
) {
    init {
        require(SHAPE.matches(value)) {
            "RouteKey는 슬러그 모양(^[a-z][a-z0-9_-]{0,80}$)이어야 한다 — 원문 대신 길이만 남긴다: length=${value.length}"
        }
    }

    private companion object {
        val SHAPE = Regex("^[a-z][a-z0-9_-]{0,80}$")
    }
}
