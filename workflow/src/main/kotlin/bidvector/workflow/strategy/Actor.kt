package bidvector.workflow.strategy

/**
 * 편집 세션 소유자 식별자(scope.md ③) — 단일 회사 전제라 세션 소유권 검사에만 쓴다
 * (`OPEN-STR-07` 해소). 승인 게이트가 아니다.
 */
data class OperatorId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "OperatorId는 빈 문자열일 수 없다" }
    }
}

/**
 * command 의 actor(D-M4-2, scope.md ③) — 이벤트 봉투의 actor(4C 소유)와는 다른 자리다.
 * `System` 은 타입만 있고 전이표에 행이 없다(D-4A-5, STR-15 `후속`) — 어떤 command 도
 * `System` actor 로는 전이표에 닿지 못하고 [bidvector.workflow.strategy.apply]가
 * [RejectionReason.SystemActorNotPermitted]로 거부한다.
 */
sealed interface Actor {
    data class Operator(
        val id: OperatorId,
    ) : Actor

    data class System(
        val reason: String,
    ) : Actor
}
