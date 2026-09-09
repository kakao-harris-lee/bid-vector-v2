package bidvector.decision

import java.math.BigDecimal

/**
 * ML 미가용 세부 사유(D-M4-6, ADR 0010 D-6) — 4B-1 은 사다리에 필요한 점수가 없다는
 * 사실 하나만 안다. 재시도 소진·circuit open·`MODEL_NOT_READY` 같은 실 실패 원인 어휘는
 * ADR 0010 D-6 이 「M4 4D 착수 시 사전 등재」로 인계했다 — 이 sealed 는 4D 가 넓힌다.
 */
sealed interface MlUnavailableReason {
    /** 사다리가 요구하는 점수 입력([LadderInput])이 결측이었다. */
    data object ScoreNotProvided : MlUnavailableReason
}

/**
 * `Review`의 사유(scope.md ④) — 생성자는 전부 `internal`(설계 검토 (2), [BidNowReason]과
 * 같은 이유).
 */
sealed interface ReviewReason {
    /** priority 가 `[reviewThreshold, bidNowThreshold)` 구간이다(조사 §2.2 분기 3). */
    @ConsistentCopyVisibility
    data class PriorityInReviewBand internal constructor(
        val priority: BigDecimal,
        val reviewThreshold: BigDecimal,
        val bidNowThreshold: BigDecimal,
    ) : ReviewReason

    /**
     * ML 점수 부재(D-M4-6 (a)) — 「추천 없음」이지 「낮은 추천」이 아니다. legacy 는 이
     * 상태 자체가 없어 fail-open 이었다(조사 §7.2) — 이 타입이 그 상태에 이름을 준다.
     * **부재에서 `BidNow`로 가는 경로가 타입에 없다**([VerdictLadder.judge] 구현·
     * `VerdictLadderPropertyTest` negative property 참고).
     */
    @ConsistentCopyVisibility
    data class MlUnavailable internal constructor(
        val reason: MlUnavailableReason,
    ) : ReviewReason
}
