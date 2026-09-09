package bidvector.workflow.notification

/**
 * 배달 경로 슬러그(scope.md ③, 설계 검토 (1)) — 허용 모양 하나만 받는다. 원문 식별자
 * (채팅 id·봇 비밀값·메일 주소)는 이 shape 를 통과하지 못한다: 숫자로 시작·`:` 포함·
 * 대문자·`@`·공백·81자↑ 는 전부 생성 실패다. 무엇이 위험한지 몰라도 슬러그가 아니면
 * 못 들어온다(우회 (7)).
 */
data class RouteKey(
    val value: String,
) {
    init {
        require(SHAPE.matches(value)) { "RouteKey는 슬러그 모양이어야 한다: $value" }
    }

    private companion object {
        val SHAPE = Regex("^[a-z][a-z0-9_-]{0,80}$")
    }
}
