package bidvector.adapters.e2e

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.adapters.relay.RelayWorkerDied
import bidvector.adapters.relay.inboxKeyCount
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
 * 관측 가능한 사건(발송 기록 수 · 이 run 이 집은 `CLAIMED` 행 · inbox 행 수)이다.
 *
 * **사건 술어는 넷이고 전부 이 파일에 있다**(PR #64 F5). 앞 판은 R-2 만 `adapters.relay` 의
 * `CrashAfterDispatch` 를 손으로 배선해 같은 축의 주입이 두 모양으로 갈렸다 — 그 클래스는
 * in_scope 밖이라 그대로 두고, e2e 쪽을 [crashAfterFirstDispatch] 로 통일했다.
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

/**
 * `PipelineAssembly(relayTransactionsFor = …)` 에 넘길 모양 — production 경계를 감싸기만 한다.
 *
 * [hookFor] 가 **조립의 sender 를 받는다**(PR #64 F4). 사건 술어는 발송 기록 수를 읽어야 하고
 * 그 sender 는 조립이 만든다 — 앞 판은 호출부에서 `lateinit var` 로 조립 자신을 되잡아 그
 * 순환을 넘겼다(네 자리). seam 이 sender 를 건네면 그 자기참조가 사라진다.
 */
internal fun relayBoundaryHook(
    hookFor: (RecordingNotificationSender) -> () -> Unit,
): (TransactionBoundary, RecordingNotificationSender) -> ConsumerTransactionPort =
    { boundary, sender -> EventTriggeredTransactions(ConsumerTransactions(boundary), hookFor(sender)) }

/** 아무것도 하지 않는 hook — 주입의 **존재**만 필요한 자리(협력자 그래프 단언)가 쓴다. */
internal val NO_OP_BOUNDARY_HOOK: () -> Unit = { }

/**
 * **이 run 이 집은 행이 있고 아직 아무것도 발송되지 않았다** — R-1 의 사건. 그 조건이 참인 첫
 * 경계 호출은 첫 행의 inbox 조회이고, 그래서 발송은 일어나지 않는다.
 *
 * **「`CLAIMED` 가 하나라도 있다」로는 안 된다**(PR #64 F1). 그 술어는 선재 고아가 있는 DB 에서
 * **첫 경계 호출**(고아 목록 조회)에서 발화해 claim 이 일어나기도 전에 run 을 죽인다 — 사건의
 * 이름과 실제 자리가 어긋난다. 그래서 hook 을 짓는 시점의 `CLAIMED` **entry id 집합**을 기억하고
 * **그 밖의 id** 가 나타났을 때만 터진다.
 *
 * 계수가 아니라 집합인 이유: 고아 격리가 선재 `CLAIMED` 를 종단으로 태우므로 **수는 줄어들 수도
 * 있다**(선재 1 + 이 run 이 1 을 집으면 수는 그대로 1 이다). id 는 그 상쇄에 속지 않는다.
 *
 * 집합을 **별도 커넥션으로** 읽는다는 것이 곧 「claim 트랜잭션이 커밋됐다」다 — 커밋 전이면 다른
 * 커넥션에 보이지 않는다.
 */
internal fun crashAfterClaimBeforeDispatch(
    dataSource: DataSource,
    sentCount: () -> Int,
): () -> Unit {
    val knownClaimed = claimedEntryIds(dataSource)
    return {
        if (sentCount() == 0 && (claimedEntryIds(dataSource) - knownClaimed).isNotEmpty()) throw RelayWorkerDied()
    }
}

/**
 * **발송은 일어났고 그 행의 T2 는 아직 커밋되지 않았다** — R-2 의 사건. 발송 기록이 생긴 **뒤**
 * 첫 경계 호출이 바로 그 T2 다.
 */
internal fun crashAfterFirstDispatch(sentCount: () -> Int): () -> Unit =
    {
        if (sentCount() > 0) throw RelayWorkerDied()
    }

/**
 * **[rows] 행이 종단까지 갔다** — R-3 의 사건. 발송 기록이 [rows] 이고 inbox 행도 [rows] 이면
 * 그 행들의 T2 가 커밋된 것이고(둘은 한 커밋이다), 그 뒤 첫 경계 호출은 **다음 행**의 것이다.
 *
 * 발송 수만 보면 T2 **앞**에서 걸려 [crashAfterFirstDispatch] 의 사건이 된다 — 두 사건을 가르는
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
            releasedInTime.set(release.await(E2E_HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }
}

/** 지금 `CLAIMED` 인 행의 entry id 전수 — 술어가 「이 run 이 집은 행」을 가리려면 수가 아니라 id 다. */
private fun claimedEntryIds(dataSource: DataSource): Set<String> =
    dataSource.connection.use { connection ->
        connection.prepareStatement(CLAIMED_ENTRY_IDS_SQL).use { statement ->
            statement.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toSet()
            }
        }
    }

private const val CLAIMED_ENTRY_IDS_SQL = "SELECT entry_id FROM outbox WHERE state = '" + CLAIMED_STATE + "'"
