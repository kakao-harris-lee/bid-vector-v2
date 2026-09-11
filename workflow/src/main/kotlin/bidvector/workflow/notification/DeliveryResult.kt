package bidvector.workflow.notification

import java.time.Instant

/**
 * 배달 시도 1건의 결과(scope.md ⑤) — [Delivered]는 sender 구현(어댑터)만 만든다. dispatch
 * 는 [Suppressed][PlanOutcome.Suppressed]를 이 타입으로 바꿀 방법이 없다([DeliveryOutcome]
 * 이 둘을 분리해 위로 올린다) — 미전달을 완료로 마킹하는 경로가 타입에 없다.
 */
sealed interface DeliveryResult {
    data class Delivered(
        val at: Instant,
        val target: MaskedTarget,
    ) : DeliveryResult

    data class Rejected(
        val reason: RejectionReason,
    ) : DeliveryResult

    data class Unknown(
        val observedAt: Instant,
    ) : DeliveryResult
}

/** 거부 사유(scope.md ⑤) — 자유 문자열 필드 0(설계 검토 (3) ii, provider 오류 문자열 미탑재). */
sealed interface RejectionReason {
    data object InvalidTarget : RejectionReason

    data object ProviderDeclined : RejectionReason
}
