package bidvector.workflow.strategy

/**
 * [TransitionOutcome.Rejected]의 사유 — 구조화 코드다, 문장이 아니다(v2-지침서.md §4.2 관례).
 */
sealed interface RejectionReason {
    /** 판정 순서 ① — 비종단 상태에서 만료 시각을 넘긴 command, 또는 이미 `Expired`인 세션. */
    data object SessionExpired : RejectionReason

    /** 판정 순서 ② — 같은 commandId·다른 내용의 재전달(우회 (5)). */
    data object IdempotencyConflict : RejectionReason

    /** 판정 순서 ③ — 세션을 시작한 operator 와 다른 operator 의 command. */
    data object ActorMismatch : RejectionReason

    /** 판정 순서 ③ — `System` actor 는 전이표에 행이 없다(D-4A-5, 우회 (6)). */
    data object SystemActorNotPermitted : RejectionReason

    /** 판정 순서 ④(Confirm) — 확인 시점의 revision 이 지금 저장된 전략과 다르다(우회 (2)). */
    data object StaleRevision : RejectionReason

    /** 판정 순서 ④ — 전이표 밖의 (state, command) 쌍(설계 검토 (1)). */
    data object InvalidTransition : RejectionReason

    /**
     * `begin()` 전용(verifier N-1 수정) — 같은 [EditSessionId]에 이미 비종단 세션이 있으면
     * 새로 열지 않는다. `apply()`의 판정 순서 밖이다 — `begin()`은 세션이 아직 없을 때만
     * 서는 유일한 생성 경로라 전이표 자체가 적용되지 않는다(D-4A-4, 재개는 새 세션).
     */
    data object SessionAlreadyActive : RejectionReason
}
