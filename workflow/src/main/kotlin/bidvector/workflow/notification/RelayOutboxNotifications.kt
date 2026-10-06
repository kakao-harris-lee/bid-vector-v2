package bidvector.workflow.notification

import bidvector.workflow.event.ClaimedOutboxRow
import bidvector.workflow.event.ConsumerLeasePort
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.InboxDecision
import bidvector.workflow.event.InboxPort
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.LeaseGuard
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.event.OutboxCommand
import bidvector.workflow.event.OutboxConsumerKind
import bidvector.workflow.event.OutboxEntry
import bidvector.workflow.event.OutboxPort
import bidvector.workflow.event.OutboxTransition
import bidvector.workflow.event.OutboxTransitionOutcome
import bidvector.workflow.event.decideInbox
import bidvector.workflow.event.transitionOutbox
import bidvector.workflow.strategy.OperatorId

/**
 * relay 가 발송하는 **대상 축** — 「누구의 어느 채널로」. 값 하나로 묶는 이유는 둘이 항상
 * 함께 오고([RouteDirectory.routesFor] 가 owner 를, 그 결과 map 이 channel 을 받는다) 설정
 * 에서도 함께 오기 때문이다(`bidvector.relay.owner`·`channel`).
 */
data class RelayTarget(
    val owner: OperatorId,
    val channel: Channel,
)

/**
 * relay 가 **아무 행도 집지 않은** 사유(ADR 0005 D-10 ③ 「억제가 관측 가능한 결과」) — 둘은
 * 처방이 다르다: [LeaseBusy] 는 기다리면 풀리고, [EnvironmentSuppressed] 는 설정을 고쳐야
 * 풀린다. 한 값으로 접으면 「조금 뒤 다시 돌려 보라」가 끝나지 않는 조언이 된다(수집의
 * `ALREADY_RUNNING`/`UNLOCKABLE` 을 가른 것과 같은 축).
 */
enum class RelaySkipReason {
    LeaseBusy,
    EnvironmentSuppressed,
}

/**
 * relay run 하나의 결과 — **전부 값이고 읽기만 한다**(계수가 쓴 값을 그대로 나른다).
 *
 * [Completed.claimed] 와 처분 계수의 관계: 끝까지 돈 run 에서는 `claimed == delivered +
 * skippedDuplicates + failed + isolated` 다. [LeaseLost] 의 [LeaseLost.partial] 에서는
 * **처분 합이 claimed 보다 작다** — 남은 행을 건드리지 않고 멈췄기 때문이고, 그 차이가 곧
 * 「`CLAIMED` 에 남아 다음 run 이 받을 행 수」다. [Completed.unknownPayload] 는
 * **[Completed.isolated] 의 부분 계수**다(미지 payload 도 격리되므로 둘을 더하지 않는다).
 */
sealed interface RelayReport {
    data class Skipped(
        val reason: RelaySkipReason,
    ) : RelayReport

    /**
     * 본문 도중에 **임대를 잃었다**(R1-M-1) — 남은 행을 건드리지 않고 멈췄다. [partial] 은
     * 멈추기 전까지의 계수다(이미 발송한 것을 숨기지 않는다).
     *
     * 왜 `Skipped` 가 아닌가: `Skipped` 는 **아무것도 하지 않았다**는 뜻이고(claim 0), 이쪽은
     * 이미 집었고 일부를 발송했을 수 있다. 둘을 접으면 「행이 `CLAIMED` 에 남아 있다」는
     * 사실이 보고에서 사라진다. 종료 코드도 다르다 — 이쪽은 `FAILED` 다(기다리면 풀리는
     * 상황이 아니라 배타성이 깨진 상황이다).
     */
    data class LeaseLost(
        val partial: Completed,
    ) : RelayReport

    data class Completed(
        val orphansIsolated: Int,
        val claimed: Int,
        val delivered: Int,
        val skippedDuplicates: Int,
        val failed: Int,
        val isolated: Int,
        val unknownPayload: Int,
    ) : RelayReport
}

/**
 * outbox 알림 행의 **production relay**(6F-10 ⓐ) — claim → inbox 판정 → [DispatchNotification]
 * → 종단 전이. 6D-1 이 test 소스셋에 모양으로 보여 준 자리의 production 판이고,
 * `OPEN-4C2-MARK-UNEXERCISED`(종단 전이의 port 호출부 부재)를 **통로를 열지 않고** 닫는다 —
 * 이 클래스가 `workflow` 안에 살아서 `transitionOutbox`·`OutboxEntry.restore`(둘 다
 * `internal`)를 부를 수 있기 때문이다. `OutboxEntry.restore` 의 KDoc 이 예고한 「미래 배달
 * 오케스트레이션 use case」가 이것이다.
 *
 * **왜 `workflow.notification` 인가(D-6F10-18 ②).** `workflow.event` 에 두면 이 클래스가
 * [DispatchNotification](`workflow.notification`)을 참조하는 순간 `event -> notification`
 * 간선이 새로 생기고, 반대 방향(`notification -> event`, `DeliveryRequest.idempotencyKey:
 * IdempotencyKey`)이 **이미 있어** 패키지 순환이 닫힌다(`packagesMustBeFreeOfCycles` 는
 * 하위 패키지 단위로 잰다 — D-6F7-1 이 `workflow.evaluation` 에서 겪은 같은 함정). 이
 * 패키지는 `workflow.event` 를 이미 보고 있어 새 간선이 없다.
 *
 * **순서가 at-most-once 다(D-6F10-3).** T1(claim 커밋) → 발송 → T2(종단 전이 + inbox 기록)
 * 이고, inbox 는 **`Delivered` 일 때만, 발송 뒤에** 기록한다. 선기록하면 발송 실패 시 키가
 * 소진되고 행이 `CLAIMED` 에 좌초한다(6D-1 이 인계한 자리). T1 과 T2 사이의 크래시가 남기는
 * `CLAIMED` 는 다음 run 의 고아이고, 그 처분은 **격리**다(ADR 0005 D-3 — `running` 에서 죽은
 * 행은 재실행하지 않는다; 놓침을 감수하는 것이 운영자 결정 `OPEN-NOTI-02` 다).
 *
 * **고아 판정에 시각이 없다(D-6F10-11).** [leases] 를 **새로** 쥔 relay 가 **첫 claim 전에**
 * 보는 `CLAIMED` 는 **그 홀더가 더 이상 임대를 쥐지 않은** 행이다 — TTL·`claimed_at` 열이
 * 없는 이유가 그것이다(관측용 열은 `OPEN-6F10-CLAIM-OBSERVABILITY`).
 *
 * **「전부 죽은 홀더의 것」은 아니다**(R1-M-1 이 반증, R2-L-4 로 문면 정정). advisory lock 을
 * 놓게 하는 것은 프로세스 사망이 아니라 **연결 단절**이므로, 프로세스가 살아 있는 홀더도
 * 임대를 잃는다 — 그 홀더가 아직 발송 중이면 그 행은 「살아 있는 남의 in-flight」다. 그래서
 * 이쪽은 [LeaseGuard] 로 네 지점에서 자기 임대를 되묻고, 그 확인 **밖에 남는 창**이 알려진
 * 제한 11 ⓐ·ⓑ 다.
 *
 * **환경 억제는 claim 자체를 하지 않는다**(ADR 0005 D-4 「억제는 기록 억제가 아니다」) — 행은
 * `PENDING` 으로 보존되고 상태 분포가 전후로 불변이다. claim 한 뒤 억제하면 어휘 밖 종단이
 * 없어 그 행이 `CLAIMED` 에 좌초한다.
 */
class RelayOutboxNotifications(
    private val outbox: OutboxPort,
    private val inbox: InboxPort,
    private val dispatcher: DispatchNotification,
    private val leases: ConsumerLeasePort,
    private val transactions: ConsumerTransactionPort,
    private val target: RelayTarget,
    private val environment: RuntimeEnvironment,
    private val policy: NotificationDeliveryPolicyData,
) {
    fun relay(limit: Int): RelayReport =
        when (
            val attempt =
                leases.withLease(OutboxConsumerKind.NotificationRequested) { guard ->
                    relayUnderLease(limit, guard)
                }
        ) {
            is LeaseAttempt.Held -> attempt.result
            LeaseAttempt.Busy -> RelayReport.Skipped(RelaySkipReason.LeaseBusy)
        }

    /**
     * 억제 판정이 **고아 격리보다도 먼저**다 — 억제 환경의 relay 는 행을 하나도 만지지
     * 않는다(격리도 하지 않는다: 격리는 단방향 종단이고, 보낼 수 없는 환경에서 남의 run 이
     * 남긴 행을 태울 이유가 없다).
     *
     * **임대를 묻는 자리가 넷이다(D-6F10-31 ②).** 획득 직후 · 고아 격리 전 · claim 전 ·
     * 행마다 발송 전. 어느 지점에서 잃어도 그 뒤 질의를 돌리지 않고 [RelayReport.LeaseLost]
     * 로 멈춘다. 앞 판은 **행 루프 안 하나**였고 그때 열려 있던 것(verifier r2 probe V6c
     * 실측): 임대를 이미 잃은 relay 가 **새 홀더가 막 집은** `CLAIMED` 행을 고아로 읽어
     * 영구 격리하고, 집을 행이 0 이면 루프가 돌지 않아 guard 가 **한 번도 불리지 않고**
     * `Completed` + 종료 코드 **0** 으로 끝났다. 그래서 claim 이 0 건인 경로도 반드시
     * guard 를 지난다.
     *
     * 획득 직후 검사는 **억제 판정보다 앞**이다 — 둘 다 「아무것도 하지 않았다」이지만
     * 「임대를 잃었다」가 더 센 신호다(종료 코드 1 대 4).
     */
    private fun relayUnderLease(
        limit: Int,
        guard: LeaseGuard,
    ): RelayReport =
        when {
            !guard.stillHeld() -> {
                leaseLostBefore(orphansIsolated = 0)
            }

            policy.environmentModes.getValue(environment) != DeliveryMode.Live -> {
                RelayReport.Skipped(RelaySkipReason.EnvironmentSuppressed)
            }

            else -> {
                relayHoldingLease(limit, guard)
            }
        }

    private fun relayHoldingLease(
        limit: Int,
        guard: LeaseGuard,
    ): RelayReport {
        // 고아 격리 전 — 잃은 뒤 격리하면 새 홀더의 in-flight 행을 태운다(되돌릴 간선 없음).
        if (!guard.stillHeld()) return leaseLostBefore(orphansIsolated = 0)
        val orphansIsolated = isolateOrphans()
        // claim 전 — 잃은 뒤 집으면 두 relay 가 같은 종류를 동시에 소비한다.
        return if (guard.stillHeld()) {
            claimAndSettle(limit, guard, orphansIsolated)
        } else {
            leaseLostBefore(orphansIsolated)
        }
    }

    private fun claimAndSettle(
        limit: Int,
        guard: LeaseGuard,
        orphansIsolated: Int,
    ): RelayReport {
        val rows = transactions.inTransaction { outbox.claim(limit, OutboxConsumerKind.NotificationRequested) }
        val dispositions = mutableListOf<RowDisposition>()
        // 행마다 발송 **전에** 임대를 다시 묻는다(R1-M-1) — 거짓이면 남은 행을 건드리지
        // 않는다. 그 행들은 `CLAIMED` 에 남아 다음 run 의 고아 격리가 받는다(놓침).
        var leaseLost = false
        for (row in rows) {
            if (!guard.stillHeld()) {
                leaseLost = true
                break
            }
            dispositions += settleRow(row)
        }
        val report = reportOf(orphansIsolated, rows.size, dispositions)
        return if (leaseLost) RelayReport.LeaseLost(report) else report
    }

    private fun isolateOrphans(): Int {
        val orphans = transactions.inTransaction { outbox.claimedEntries(OutboxConsumerKind.NotificationRequested) }
        orphans.forEach { row ->
            transactions.inTransaction { transition(OutboxEntry.restore(row), OutboxCommand.Isolate) }
        }
        return orphans.size
    }

    /**
     * 미지 payload 는 **조용히 건너뛰지 않는다** — 건너뛰면 그 행이 `CLAIMED` 에 좌초하고
     * 계수만 줄어 사유가 사라진다(6D-1 review L-3 과 같은 처분). 전달 여부가 모호한 것이
     * 아니라 **전달을 시도조차 못 한 것**이지만, 어휘에 그 칸이 없고 V6 CHECK 를 바꾸는 것은
     * 범위 밖이라 `ISOLATED`(모호) 로 보내고 [RelayReport.Completed.unknownPayload] 가 사유를
     * 가른다(D-6F10-12 어휘 해석표).
     */
    private fun settleRow(row: ClaimedOutboxRow<*>): RowDisposition {
        val entry = OutboxEntry.restore(row)
        val payload =
            entry.envelope.payload as? NotificationRequestedPayload
                ?: return isolateUnknownPayload(entry)
        val key = entry.envelope.idempotencyKey
        return skipDuplicate(entry, key)
            ?: settleDispatch(entry, key, dispatcher.dispatch(intentFor(key, payload, target)))
    }

    private fun isolateUnknownPayload(entry: OutboxEntry): RowDisposition =
        transactions.inTransaction {
            transition(entry, OutboxCommand.Isolate)
            RowDisposition.ISOLATED_UNKNOWN_PAYLOAD
        }

    /**
     * 이미 처리된 키면 **발송 없이** `DELIVERED` 로 보낸다(D-6F10-12 — 「같은 멱등 키가 이미
     * 전달됨」도 전달이다). `null` 은 「중복이 아니다」이고 호출부의 `?:` 가 발송으로 잇는다 —
     * `evaluateOne` 의 guard 체인과 같은 관례다.
     */
    private fun skipDuplicate(
        entry: OutboxEntry,
        key: IdempotencyKey,
    ): RowDisposition? {
        val alreadyProcessed = transactions.inTransaction { inbox.hasProcessed(key) }
        if (decideInbox(alreadyProcessed) != InboxDecision.SkipDuplicate) return null
        return transactions.inTransaction {
            transition(entry, OutboxCommand.Deliver)
            RowDisposition.SKIPPED_DUPLICATE
        }
    }

    /**
     * T2 — 종단 전이와 inbox 기록이 **한 커밋**이다. 매핑은 D-6F10-12 어휘 해석표다:
     * `Delivered` → `DELIVERED` + inbox · `Rejected` → `FAILED` · `Unknown` → `ISOLATED`
     * (모호, 재시도 없음 D-4E-2) · `Suppressed` → `FAILED`.
     *
     * 여기 오는 [DeliveryOutcome.Suppressed] 는 **채널 비활성·route 부재**다 — 환경 억제는
     * [relayUnderLease] 가 claim 전에 걸렀다. 그 둘은 「이 entry 로는 전달이 일어나지 않았고
     * 일어나지 않을 것」이라 `FAILED` 가 맞다(설정을 고치면 **다음** 판정이 새 행을 낳는다 —
     * 이 행을 되살리는 간선은 표에 없다).
     */
    private fun settleDispatch(
        entry: OutboxEntry,
        key: IdempotencyKey,
        outcome: DeliveryOutcome,
    ): RowDisposition =
        transactions.inTransaction {
            when (outcome) {
                is DeliveryOutcome.Suppressed -> {
                    transition(entry, OutboxCommand.Fail)
                    RowDisposition.FAILED
                }

                is DeliveryOutcome.Attempted -> {
                    settleResult(entry, key, outcome.result)
                }
            }
        }

    private fun settleResult(
        entry: OutboxEntry,
        key: IdempotencyKey,
        result: DeliveryResult,
    ): RowDisposition =
        when (result) {
            is DeliveryResult.Delivered -> {
                inbox.markProcessed(key)
                transition(entry, OutboxCommand.Deliver)
                RowDisposition.DELIVERED
            }

            is DeliveryResult.Rejected -> {
                transition(entry, OutboxCommand.Fail)
                RowDisposition.FAILED
            }

            is DeliveryResult.Unknown -> {
                transition(entry, OutboxCommand.Isolate)
                RowDisposition.ISOLATED
            }
        }

    /**
     * **전이표를 지나 port 로 올리는 유일한 자리** — 명령과 통로 타입의 짝이 이 한 함수에만
     * 있다. 소진 `when` 이라 [OutboxTransition] 에 하위 타입이 생기면 컴파일이 깨진다(사본
     * SQL 0, `internal` 완화 0).
     *
     * 거부는 `error` 다. 호출부가 넘기는 상태는 늘 [OutboxEntry.restore] 가 고정한
     * `Claimed` 이고 명령 셋은 전부 그 상태에서 허용되므로, 거부가 돌아오면 그것은 전이표가
     * 바뀌었다는 뜻이다(업무 분기가 아니라 불변식 위반). 러너가 그 예외를 정제된 원인
     * 코드로 옮긴다(수집 러너 선례).
     */
    private fun transition(
        entry: OutboxEntry,
        command: OutboxCommand,
    ) {
        val accepted =
            when (val outcome = transitionOutbox(entry.id, entry.state, command)) {
                is OutboxTransitionOutcome.Accepted -> {
                    outcome.transition
                }

                is OutboxTransitionOutcome.Rejected -> {
                    error("outbox 전이표가 거부했다 — from=${outcome.from} command=${outcome.command}")
                }
            }
        when (accepted) {
            is OutboxTransition.ToDelivered -> outbox.markDelivered(accepted)
            is OutboxTransition.ToFailed -> outbox.markFailed(accepted)
            is OutboxTransition.ToIsolated -> outbox.markIsolated(accepted)
            is OutboxTransition.ToClaimed -> error("relay 는 claim 전이를 port 로 올리지 않는다")
        }
    }
}

/**
 * 아직 아무 행도 집지 않은 상태의 임대 상실 — 처분 계수가 전부 0 이고 `orphansIsolated` 만
 * 그때까지 격리한 수다. top-level 인 이유는 수신자 상태가 필요 없기 때문이다(클래스당 함수
 * 11개 한도, detekt `TooManyFunctions`).
 */
private fun leaseLostBefore(orphansIsolated: Int): RelayReport.LeaseLost =
    RelayReport.LeaseLost(reportOf(orphansIsolated, claimed = 0, dispositions = emptyList()))

/** 행 하나의 처분 — [RelayReport.Completed] 의 계수가 이 값들을 센다. */
private enum class RowDisposition {
    DELIVERED,
    SKIPPED_DUPLICATE,
    FAILED,
    ISOLATED,
    ISOLATED_UNKNOWN_PAYLOAD,
}

private fun reportOf(
    orphansIsolated: Int,
    claimed: Int,
    dispositions: List<RowDisposition>,
): RelayReport.Completed =
    RelayReport.Completed(
        orphansIsolated = orphansIsolated,
        claimed = claimed,
        delivered = dispositions.count { it == RowDisposition.DELIVERED },
        skippedDuplicates = dispositions.count { it == RowDisposition.SKIPPED_DUPLICATE },
        failed = dispositions.count { it == RowDisposition.FAILED },
        isolated =
            dispositions.count {
                it == RowDisposition.ISOLATED || it == RowDisposition.ISOLATED_UNKNOWN_PAYLOAD
            },
        unknownPayload = dispositions.count { it == RowDisposition.ISOLATED_UNKNOWN_PAYLOAD },
    )

/**
 * payload 가 가리키는 **내용 참조**만 싣는다 — relay 는 문장을 만들지 않는다(렌더링은
 * [ContentRenderer] 몫, `OPEN-STR-12`). `idempotencyKey` 는 봉투가 등록 시점에 고정한 값을
 * 그대로 나른다(relay 가 새 키를 짓는 자리가 없다 — `decideInbox` 의 dedup 이 성립하는 전제).
 */
private fun intentFor(
    key: IdempotencyKey,
    payload: NotificationRequestedPayload,
    target: RelayTarget,
): NotificationIntent =
    NotificationIntent(
        idempotencyKey = key,
        owner = target.owner,
        channel = target.channel,
        contentRef = ContentRef(payload.noticeId),
    )
