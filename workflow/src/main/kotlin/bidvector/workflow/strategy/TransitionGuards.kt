package bidvector.workflow.strategy

import bidvector.strategy.OperatorStrategy

// ---------------------------------------------------------------------------
// 판정 순서의 술어들(설계 검토 (4) 2) — ① 만료 → ② 직전 command 재전달 → ③ actor, 그리고
// `Confirm` 전용 ④ 신선도. `Transition.kt` 의 `apply`/`dispatch` 가 이 순서대로 부른다.
//
// `Transition.kt` 에서 갈라 나왔다(파일당 함수 한도 11) — 경계는 「거부할 이유가 있는가」와
// 「전이를 실제로 만든다」다. `private` 이던 것이 `internal` 이 되지만 모듈 밖으로는 나가지
// 않는다(`EditSessionRestore.kt` 를 스냅숏 파일에서 나눈 것과 같은 종류의 분할이다).
// ---------------------------------------------------------------------------

/** 판정 순서 ①(설계 검토 (4) 2) — 이미 `Expired`인 세션, 또는 방금 만료로 접힌 세션. */
internal fun expiryRejection(
    session: EditSession,
    command: EditCommand,
): TransitionOutcome.Rejected? =
    if (session.state is EditSessionState.Expired) {
        TransitionOutcome.Rejected(session, command, RejectionReason.SessionExpired)
    } else {
        null
    }

/**
 * 판정 순서 ①-b(D-6A2b-28·38) — `ProvideValue` 의 기준 대조는 **재전달 판별보다 앞선다.**
 * 뒤에 두면 같은 본문의 재전달이 「기준이 달라졌다」는 이유로 중복 처리돼 조용히 통과한다.
 */
internal fun provideValueStaleness(
    session: EditSession,
    command: EditCommand,
    current: OperatorStrategy,
): TransitionOutcome.Rejected? =
    if (command is EditCommand.ProvideValue && command.baseRevision != current.revision) {
        TransitionOutcome.Rejected(session, command, RejectionReason.StaleRevision)
    } else {
        null
    }

/**
 * 재전달 판별용 지문 — **서버가 파생한 값을 뺀다**(M-r3-6). `baseRevision` 은 실행기가 호출마다
 * 새로 읽어 싣는 값이라, 그것을 비교에 넣으면 같은 본문의 재전달이 `IdempotencyConflict`
 * (「같은 commandId 다른 본문」)로 나온다 — 클라이언트는 같은 본문을 보냈고, 운영자가 고칠 곳이
 * 달라진다. 지문이 같고 서버 파생 값만 다르면 맞는 사유는 `StaleRevision` 이다.
 */
private fun EditCommand.idempotencyFingerprint(): EditCommand =
    if (this is EditCommand.ProvideValue) copy(baseRevision = null) else this

/** 판정 순서 ②(우회 (5)) — 직전 command 재전달은 효과 0, 다른 내용이면 conflict. */
internal fun duplicateOutcome(
    session: EditSession,
    command: EditCommand,
): TransitionOutcome? {
    val last = session.lastCommand
    return when {
        last == null || last.commandId != command.commandId -> {
            null
        }

        // 같은 본문이고 서버 파생 값까지 같다 — 진짜 재전달이다(효과 0).
        last == command -> {
            TransitionOutcome.Accepted(session)
        }

        // 본문은 같은데 서버 파생 기준만 다르다 — 세션이 든 draft 가 이미 낡았다는 뜻이다.
        // 조용히 수용하면 낡은 스냅숏이 확인 대기로 남는다(M-r3-6).
        last.idempotencyFingerprint() == command.idempotencyFingerprint() -> {
            TransitionOutcome.Rejected(session, command, RejectionReason.StaleRevision)
        }

        else -> {
            TransitionOutcome.Rejected(session, command, RejectionReason.IdempotencyConflict)
        }
    }
}

/** 판정 순서 ③ — `System` actor 는 전이표 자체가 없고, 다른 operator 는 소유권 위반. */
internal fun actorRejection(
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
 * 신선도는 **두 축**이다(M6/6A-2b D-6A2b-18) — 둘 다 통과해야 적용한다.
 * ① 클라이언트가 본 revision(`seenRevision`)이 지금 값과 같은가 ② 세션이 든 draft 를 **뜬**
 * 시점([EditSessionState.WaitingForConfirmation.baseRevision])이 지금 값과 같은가.
 *
 * ①만으로는 부족했다(verifier r1 F-2 실측): 확인 직전에 조회하면 ①은 늘 참이 되고, 그 사이
 * 다른 세션이 적용한 변경은 낡은 스냅숏에 **덮여 사라졌다**. ②는 「이 draft 가 만들어진 뒤
 * 전략이 움직였는가」를 직접 묻는다. 기준이 없는(= 이 필드 이전에 저장된) 세션은 `null` 이라
 * 항상 stale 로 떨어진다 — 지어내지 않고 거부한다.
 */
internal fun isStale(
    state: EditSessionState.WaitingForConfirmation,
    command: EditCommand.Confirm,
    current: OperatorStrategy,
): Boolean = command.seenRevision != current.revision || state.baseRevision != current.revision
