package bidvector.workflow.event

/**
 * outbox 행의 상태 어휘(scope.md ②, D-M4-5 (a), `OPEN-OPS-10` 종결) — `data-dictionary.md`
 * §2.2.5. 유일한 전이 경로는 [transitionOutbox]다. `Delivered`·`Failed`·`Isolated`는 종단 —
 * 이 셋에서 나가는 전이는 표에 없다(재시도 API 없음, 설계 검토 (4) 4).
 */
sealed interface OutboxEntryState {
    data object Pending : OutboxEntryState

    data object Claimed : OutboxEntryState

    /** 종단 — 정상 배달 완료. */
    data object Delivered : OutboxEntryState

    /** 종단 — 최대 시도 소진(D-M4-5 (a)). */
    data object Failed : OutboxEntryState

    /**
     * 종단 — `Claimed`에서 워커가 죽어 격리됐다(D-M4-5 (a), at-most-once·`OPEN-NOTI-02`).
     * 수동 검토는 앱 알림함 회수 경로가 진다(ADR 0005 D-4) — 이 상태 자체는 재실행 경로를
     * 갖지 않는다.
     */
    data object Isolated : OutboxEntryState
}

/** outbox 전이를 요청하는 명령(scope.md ②) — [transitionOutbox]의 두 번째 인자. */
sealed interface OutboxCommand {
    data object Claim : OutboxCommand

    data object Deliver : OutboxCommand

    data object Fail : OutboxCommand

    data object Isolate : OutboxCommand
}

/**
 * [transitionOutbox]가 낸 통로 타입(scope.md ③, 설계 검토 (2) 표 넷째 행) — 4A
 * `AppliedStrategy`와 같은 관례. [OutboxPort.markDelivered]/[markFailed]/[markIsolated]는
 * [OutboxEntryState]를 직접 받지 않고 이 타입만 받는다 — 전이표를 거치지 않은 임의 상태
 * 점프를 막는다.
 */
sealed interface OutboxTransition {
    val entryId: OutboxEntryId

    @ConsistentCopyVisibility
    data class ToClaimed internal constructor(
        override val entryId: OutboxEntryId,
    ) : OutboxTransition

    @ConsistentCopyVisibility
    data class ToDelivered internal constructor(
        override val entryId: OutboxEntryId,
    ) : OutboxTransition

    @ConsistentCopyVisibility
    data class ToFailed internal constructor(
        override val entryId: OutboxEntryId,
    ) : OutboxTransition

    @ConsistentCopyVisibility
    data class ToIsolated internal constructor(
        override val entryId: OutboxEntryId,
    ) : OutboxTransition
}

/** [transitionOutbox]의 결과. */
sealed interface OutboxTransitionOutcome {
    @ConsistentCopyVisibility
    data class Accepted internal constructor(
        val transition: OutboxTransition,
    ) : OutboxTransitionOutcome

    data class Rejected(
        val entryId: OutboxEntryId,
        val from: OutboxEntryState,
        val command: OutboxCommand,
    ) : OutboxTransitionOutcome
}

/**
 * outbox의 유일한 전이 문(scope.md ②, D-M4-5 (a)) — 허용 쌍만 갖는 소진 `when`(설계 검토
 * (1)). `internal`이다(4A H-3 교훈 선적용 — 설계 검토 (2) 마지막 행): 커널 함수 자체가
 * public이면 `workflow` 밖에서 이 함수를 직접 몰아 정당한 [OutboxTransition]을 얻고 실
 * outbox 구현의 `mark*`에 바로 넘길 수 있다. `internal`로 닫아 그 획득 경로 자체를 컴파일
 * 층에서 막는다 — 실 배달 오케스트레이션(4C-2)이 이 함수의 유일한 정당한 호출부가 된다.
 */
internal fun transitionOutbox(
    entryId: OutboxEntryId,
    current: OutboxEntryState,
    command: OutboxCommand,
): OutboxTransitionOutcome =
    when {
        current is OutboxEntryState.Pending && command is OutboxCommand.Claim -> {
            OutboxTransitionOutcome.Accepted(OutboxTransition.ToClaimed(entryId))
        }

        current is OutboxEntryState.Claimed && command is OutboxCommand.Deliver -> {
            OutboxTransitionOutcome.Accepted(OutboxTransition.ToDelivered(entryId))
        }

        current is OutboxEntryState.Claimed && command is OutboxCommand.Fail -> {
            OutboxTransitionOutcome.Accepted(OutboxTransition.ToFailed(entryId))
        }

        current is OutboxEntryState.Claimed && command is OutboxCommand.Isolate -> {
            OutboxTransitionOutcome.Accepted(OutboxTransition.ToIsolated(entryId))
        }

        else -> {
            OutboxTransitionOutcome.Rejected(entryId, current, command)
        }
    }
