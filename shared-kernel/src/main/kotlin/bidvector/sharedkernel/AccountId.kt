package bidvector.sharedkernel

/**
 * 계정 한 행의 식별자 — 저장 층이 발급한다(애플리케이션이 지어내지 않는다).
 *
 * **왜 공유 커널인가**(M15/15A). 이 값은 `bidvector.workflow.access` 에 있었다. 거기 두면
 * **계정별 행을 가진 모든 문맥이 인증 문맥을 참조**해야 하고, 15A 가 전략·프로필·편집 세션의
 * 포트 서명에 계정을 넣는 순간 그 참조가 `workflow.strategy -> workflow.access` 로 섰다.
 * `workflow.access` 는 이미 `workflow.strategy`(시계)와 `workflow.collection`(해시)을
 * 반대 방향으로 보고 있었으므로 그 한 변이 패키지 순환 넷을 닫았다(ArchUnit 슬라이스 규칙,
 * v2-지침서 §5).
 *
 * 순환을 푸는 변은 하나다 — 식별자를 **아무것도 참조하지 않는 자리**로 옮기는 것. 계정 식별자는
 * 인증의 개념이 아니라 여러 문맥이 공유하는 **신원**이고, 그 자리가 공유 커널이다. 인증 문맥은
 * 이제 이 값을 쓰는 여러 문맥 가운데 하나일 뿐이다.
 *
 * 값 비교를 제공하지 않는다(`equals`/`hashCode` 미정의) — 옮기기 전과 같다. 두 식별자가 같은
 * 계정인지는 [value] 로 묻는다.
 */
class AccountId(
    val value: Long,
)
