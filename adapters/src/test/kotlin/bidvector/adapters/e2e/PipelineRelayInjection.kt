package bidvector.adapters.e2e

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.adapters.relay.RelayWorkerDied
import bidvector.adapters.relay.inboxKeyCount
import bidvector.adapters.relay.outboxStateCounts
import bidvector.workflow.event.ConsumerTransactionPort
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/*
 * relay 의 **커밋 경계에 사건을 거는** 주입 묶음(6D-2). 「재기동」을 재려면 run 이 T1 과 T2
 * 사이에서 죽어야 하고, 그 자리는 경계 호출 사이뿐이다.
 *
 * **순번이 아니라 사건이다**(설계 검토 (1), 6F-10 R1-L-4). `InterferingTransactions` 는 호출
 * 순번(`atCall`)에 걸어 경계 호출 수를 바꾸는 변이에서 주입 자체가 사라졌다 — 그러면 크래시
 * 뒤 상태 단언이 **돌지 않은 채** test 가 붉어지거나 초록이 된다. 여기서는 조건이 전부
 * 관측 가능한 사건(발송 기록 수 · DB 의 `CLAIMED` 행 · inbox 행 수)이다.
 *
 * `CrashAfterDispatch`(`adapters.relay`)는 이미 그 모양이라 **그대로 재사용한다** — 이 파일이
 * 더하는 것은 그것으로 표현할 수 없는 사건 둘(「claim 은 커밋됐고 아직 발송은 없다」·「k 행이
 * 종단까지 갔다」)과 **죽지 않고 멈추는** 쪽(살아 있는 홀더, R-5)이다.
 */

/**
 * 경계 호출 **직전에** [hook] 을 돌리는 [ConsumerTransactionPort] — hook 이 던지면 그 호출은
 * 일어나지 않는다(그것이 「T1 과 T2 사이의 사망」이다).
 *
 * hook 은 경계 **밖**에서 돈다. [TransactionBoundary] 는 중첩을 거부하므로 hook 이 DB 를 보려면
 * 별도 커넥션이어야 하고, 이 자리는 아직 경계가 열리지 않은 자리다.
 */
internal class EventTriggeredTransactions(
    private val delegate: ConsumerTransactionPort,
    private val hook: () -> Unit,
) : ConsumerTransactionPort {
    override fun <T> inTransaction(block: () -> T): T {
        hook()
        return delegate.inTransaction(block)
    }
}

/** `PipelineAssembly(relayTransactionsFor = …)` 에 넘길 모양 — production 경계를 감싸기만 한다. */
internal fun relayBoundaryHook(hook: () -> Unit): (TransactionBoundary) -> ConsumerTransactionPort =
    { boundary -> EventTriggeredTransactions(ConsumerTransactions(boundary), hook) }

/**
 * **claim 은 커밋됐고 아직 아무것도 발송되지 않았다** — R-1 의 사건. 그 조건이 참인 첫 경계
 * 호출은 첫 행의 inbox 조회이고, 그래서 발송은 일어나지 않는다.
 *
 * `CLAIMED` 행을 **별도 커넥션으로** 본다는 것이 곧 「claim 트랜잭션이 커밋됐다」다 — 커밋 전이면
 * 다른 커넥션에 보이지 않는다.
 */
internal fun crashAfterClaimBeforeDispatch(
    dataSource: DataSource,
    sentCount: () -> Int,
): () -> Unit =
    {
        if (sentCount() == 0 && (outboxStateCounts(dataSource)[CLAIMED_STATE] ?: 0) > 0) throw RelayWorkerDied()
    }

/**
 * **[rows] 행이 종단까지 갔다** — R-3 의 사건. 발송 기록이 [rows] 이고 inbox 행도 [rows] 이면
 * 그 행들의 T2 가 커밋된 것이고(둘은 한 커밋이다), 그 뒤 첫 경계 호출은 **다음 행**의 것이다.
 *
 * 발송 수만 보면 T2 **앞**에서 걸려 「발송 뒤·종단 전」(R-2 의 사건)이 된다 — 두 사건을 가르는
 * 것이 inbox 행 수다.
 */
internal fun crashAfterSettledRows(
    dataSource: DataSource,
    rows: Int,
    sentCount: () -> Int,
): () -> Unit =
    {
        if (sentCount() == rows && inboxKeyCount(dataSource) == rows) throw RelayWorkerDied()
    }

/**
 * **죽지 않고 멈춘다** — 살아 있는 홀더(R-5). 첫 발송 뒤 첫 경계 호출에서 [blocked] 를 내리고
 * [release] 를 기다린다. 임대 연결은 그동안 살아 있으므로 둘째 relay 는 `Busy` 를 받아야 한다.
 *
 * **한 번만 막는다**([AtomicBoolean]) — 풀린 뒤의 남은 행마다 다시 막히면 run 이 끝나지 않아
 * 「풀리면 끝까지 전달한다」를 잴 수 없다.
 *
 * [releasedInTime] 에 `await` 의 **반환값**을 담는다(6D-1 교훈) — 시한 만료로 풀린 것을 정상
 * 해제와 구별하지 못하면 교착이 초록으로 지나간다.
 */
internal fun pauseAfterFirstDispatch(
    blocked: CountDownLatch,
    release: CountDownLatch,
    releasedInTime: AtomicBoolean,
    sentCount: () -> Int,
): () -> Unit {
    val paused = AtomicBoolean(false)
    return {
        if (sentCount() > 0 && paused.compareAndSet(false, true)) {
            blocked.countDown()
            releasedInTime.set(release.await(RELAY_HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }
}

internal const val CLAIMED_STATE = "CLAIMED"
internal const val DELIVERED_STATE = "DELIVERED"
internal const val ISOLATED_STATE = "ISOLATED"
internal const val PENDING_STATE = "PENDING"

/** 홀더가 **쥐고 있는** 시한 — 둘째 relay 가 `Busy` 를 받고 돌아오면 즉시 풀리므로 정상 비용은 0 이다. */
internal const val RELAY_HOLD_TIMEOUT_SECONDS = 30L

/** 홀더가 막혔다는 신호를 기다리는 시한 — 막히지 않으면 그 자체가 결함이다. */
internal const val RELAY_BLOCK_SIGNAL_TIMEOUT_SECONDS = 5L

/** 홀더 합류 시한 — 쥠 시한보다 커야 그 만료가 합류 실패로 가려지지 않는다. */
internal const val RELAY_JOIN_TIMEOUT_SECONDS = 60L
