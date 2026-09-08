package bidvector.workflow.strategy

import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import java.time.Instant

/** 새 편집 세션을 연다 — [apply]를 거치지 않는 유일한 생성 경로다(선행 세션이 없다). */
fun beginSession(
    id: EditSessionId,
    operator: OperatorId,
    field: EditableField,
    now: Instant,
    policy: EditSessionPolicyData,
): EditSession =
    EditSession(
        id = id,
        actor = Actor.Operator(operator),
        state = EditSessionState.WaitingForValue(field),
        expiresAt = now + policy.timeoutWindow,
        sessionVersion = 0,
        lastCommand = null,
    )

private fun isTerminal(state: EditSessionState): Boolean =
    when (state) {
        is EditSessionState.Applied, is EditSessionState.Cancelled, EditSessionState.Expired -> true
        is EditSessionState.WaitingForValue, is EditSessionState.WaitingForConfirmation -> false
    }

/**
 * 비종단 상태이고 [now]가 만료 시각을 넘겼으면 `Expired`로 접는다(판정 순서 ①). 이미
 * 종단 상태면 그대로 돌려준다 — 종단 뒤에는 시각과 무관하게 상태가 굳는다.
 */
fun expireIfDue(
    session: EditSession,
    now: Instant,
): EditSession {
    val dueToExpire = !isTerminal(session.state) && !now.isBefore(session.expiresAt)
    return if (dueToExpire) {
        session.copy(state = EditSessionState.Expired, sessionVersion = session.sessionVersion + 1)
    } else {
        session
    }
}

/** 판정 순서 ①(설계 검토 (4) 2) — 이미 `Expired`인 세션, 또는 방금 만료로 접힌 세션. */
private fun expiryRejection(
    session: EditSession,
    command: EditCommand,
): TransitionOutcome.Rejected? =
    if (session.state is EditSessionState.Expired) {
        TransitionOutcome.Rejected(session, command, RejectionReason.SessionExpired)
    } else {
        null
    }

/** 판정 순서 ②(우회 (5)) — 직전 command 재전달은 효과 0, 다른 내용이면 conflict. */
private fun duplicateOutcome(
    session: EditSession,
    command: EditCommand,
): TransitionOutcome? {
    val last = session.lastCommand
    return when {
        last == null || last.commandId != command.commandId -> null
        last == command -> TransitionOutcome.Accepted(session)
        else -> TransitionOutcome.Rejected(session, command, RejectionReason.IdempotencyConflict)
    }
}

/** 판정 순서 ③ — `System` actor 는 전이표 자체가 없고, 다른 operator 는 소유권 위반. */
private fun actorRejection(
    session: EditSession,
    command: EditCommand,
): TransitionOutcome.Rejected? {
    val actor = command.actor
    return when {
        actor !is Actor.Operator -> {
            TransitionOutcome.Rejected(
                session,
                command,
                RejectionReason.SystemActorNotPermitted,
            )
        }

        actor != session.actor -> {
            TransitionOutcome.Rejected(session, command, RejectionReason.ActorMismatch)
        }

        else -> {
            null
        }
    }
}

/**
 * 편집 세션의 유일한 전이 문(scope.md ①, 우회 (1)) — 판정 순서(설계 검토 (4) 2):
 * ① 만료 → ② 직전 command 재전달 → ③ actor → ④ 전이표. [current]·[policy]는 매 호출마다
 * 신선하게 주입된다(호출부가 fresh read 를 보장 — apply 시점 재검증의 메커니즘).
 */
fun apply(
    session: EditSession,
    command: EditCommand,
    now: Instant,
    current: OperatorStrategy,
    policy: Resolution.Resolved<StrategyPolicyData>,
): TransitionOutcome {
    val checked = if (session.state is EditSessionState.Expired) session else expireIfDue(session, now)
    return expiryRejection(checked, command)
        ?: duplicateOutcome(checked, command)
        ?: actorRejection(checked, command)
        ?: dispatch(checked, command, current, policy)
}

private fun dispatch(
    session: EditSession,
    command: EditCommand,
    current: OperatorStrategy,
    policy: Resolution.Resolved<StrategyPolicyData>,
): TransitionOutcome {
    val state = session.state
    return when {
        state is EditSessionState.WaitingForValue && command is EditCommand.ProvideValue -> {
            onProvideValue(session, state, command, current, policy)
        }

        state is EditSessionState.WaitingForConfirmation && command is EditCommand.Confirm -> {
            onConfirm(session, state, command, current, policy)
        }

        state is EditSessionState.WaitingForConfirmation && command is EditCommand.RequestEdit -> {
            accept(session, EditSessionState.WaitingForValue(command.field), command)
        }

        !isTerminal(state) && command is EditCommand.Cancel -> {
            accept(session, EditSessionState.Cancelled(command.reason), command)
        }

        else -> {
            TransitionOutcome.Rejected(session, command, RejectionReason.InvalidTransition)
        }
    }
}

private fun accept(
    session: EditSession,
    next: EditSessionState,
    command: EditCommand,
): TransitionOutcome.Accepted =
    TransitionOutcome.Accepted(
        session.copy(state = next, sessionVersion = session.sessionVersion + 1, lastCommand = command),
    )

/** `ValueProvided`(scope.md ①②) — invalid 는 상태를 바꾸지 않는다(같은 field 로 accepted). */
private fun onProvideValue(
    session: EditSession,
    state: EditSessionState.WaitingForValue,
    command: EditCommand.ProvideValue,
    current: OperatorStrategy,
    policy: Resolution.Resolved<StrategyPolicyData>,
): TransitionOutcome =
    when (validate(command.draft, current.revision, policy)) {
        is StrategyValidation.Valid -> {
            accept(session, EditSessionState.WaitingForConfirmation(command.field, command.draft), command)
        }

        is StrategyValidation.Invalid -> {
            accept(session, EditSessionState.WaitingForValue(state.field), command)
        }
    }

/**
 * `Confirmed`(설계 검토 (4) 3) — (a) stale `seenRevision` 은 거부. (b) 재검증이 `Invalid`면
 * `WaitingForValue`로 되돌아가는 accepted 전이(거부가 아니다). (c) `Valid`면 `Applied`.
 */
private fun onConfirm(
    session: EditSession,
    state: EditSessionState.WaitingForConfirmation,
    command: EditCommand.Confirm,
    current: OperatorStrategy,
    policy: Resolution.Resolved<StrategyPolicyData>,
): TransitionOutcome {
    if (command.seenRevision != current.revision) {
        return TransitionOutcome.Rejected(session, command, RejectionReason.StaleRevision)
    }
    val nextRevision = StrategyRevision(current.revision.value + 1)
    return when (val result = validate(state.draft, nextRevision, policy)) {
        is StrategyValidation.Invalid -> {
            accept(session, EditSessionState.WaitingForValue(state.field), command)
        }

        is StrategyValidation.Valid -> {
            val next =
                session.copy(
                    state = EditSessionState.Applied(nextRevision),
                    sessionVersion = session.sessionVersion + 1,
                    lastCommand = command,
                )
            TransitionOutcome.Applied(
                next,
                result.strategy,
                StrategyEvent.StrategyUpdated(nextRevision, result.policyVersion),
            )
        }
    }
}
