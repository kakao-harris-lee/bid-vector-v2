package bidvector.adapters.e2e

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.adapters.relay.CrashAfterDispatch
import bidvector.adapters.relay.RelayWorkerDied
import bidvector.adapters.relay.outboxStateCounts
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.notification.RelayAborted
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelaySkipReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 6D 축 ③ — **restart 뒤 outbox/inbox 수렴**. 파이프라인(수집 → 평가 → outbox)이 만든 행
 * 위에서 production relay 가 T1 과 T2 사이에 죽고, **새 조립**이 그 뒤를 받는다.
 *
 * **「재기동」의 뜻**(B-1 (a)) — 새 [PipelineAssembly] 인스턴스다: 새 임대 세션 · 새 relay ·
 * 새 port · 새 sender. 같은 relay 객체를 다시 부르는 것은 재기동이 아니다(임대 연결이 같다).
 * 프로세스 사망의 in-JVM 등가이고, 죽은 조립의 임대가 실제로 풀렸다는 사실은 둘째 relay 가
 * `Busy` 를 받지 **않는다**는 것으로 잰다 — 그 반대(살아 있는 홀더 → `Busy`)를 R-5 가 잰다.
 *
 * **크래시는 순번이 아니라 사건에 걸린다**([PipelineRelayInjection] 의 술어들). 발송 계수는
 * 조립마다 sender 가 다르므로 **두 조립의 합**으로 센다 — 「발송 0 또는 1」은 그 합이다.
 *
 * **격리는 production 이 한다** — 이 파일에는 상태 강제(`forceOutboxState` 류)가 없다. 고아는
 * 죽은 run 이 남긴 행이고, 격리 여부는 새 relay 의 보고와 DB 상태 분포로만 읽는다. 재기동
 * **전**의 중간 상태도 함께 단언한다(주입이 들어가지 않은 초록을 가른다).
 */
internal class PipelineRestartConvergenceE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    /** R-1 — claim 은 커밋됐고 발송은 아직 없다. 놓침 0 이 아니라 **발송 0** 이 계약 문면이다. */
    @Test
    fun `claim 뒤 발송 전에 죽으면 재기동이 고아를 격리하고 발송 합이 0 이다`() {
        val dying = crashedBeforeFirstDispatch()

        val restarted = assembly()
        val report = restarted.relay().shouldBeInstanceOf<RelayReport.Completed>()

        report.orphansIsolated shouldBe 1
        report.claimed shouldBe 0
        report.delivered shouldBe 0
        sentTotal(dying, restarted) shouldBe 0
        outboxStateCounts(dataSource()) shouldBe mapOf(ISOLATED_STATE to 1)
        inboxKeys().shouldBeEmpty()
    }

    /**
     * R-2 — **B-2 의 실측**(알려진 제한). inbox 는 전달 **뒤** T2 에서만 기록되므로(D-6F10-3)
     * 전달과 T2 사이의 크래시 창에서는 키 중복 제거가 서지 않는다. 그 창을 지나 같은 입력을
     * 재평가하면 같은 멱등 키의 **새 entry** 가 생기고 그것은 **다시 발송된다** — 발송 합 2.
     *
     * 계약의 「중복 0」은 **entry 단위**(한 entry 가 두 번 실행되지 않는다)이고 at-most-once 가
     * 막는 것은 entry 의 재실행이다. 키의 재발생은 그 문면 밖이다 — 이 값이 바뀌면 at-most-once
     * 설계가 바뀐 것이므로 여기서 **사실로 고정**한다.
     */
    @Test
    fun `발송 뒤 종단 전에 죽으면 재기동은 격리하고 재평가한 같은 키는 다시 발송된다`() {
        val dying = crashedAfterFirstDispatch()

        val restarted = assembly()
        val isolation = restarted.relay().shouldBeInstanceOf<RelayReport.Completed>()

        isolation.orphansIsolated shouldBe 1
        isolation.claimed shouldBe 0
        sentTotal(dying, restarted) shouldBe 1
        inboxKeys().shouldBeEmpty()
        outboxStateCounts(dataSource()) shouldBe mapOf(ISOLATED_STATE to 1)

        val reevaluated = assembly()
        runBlocking { reevaluated.evaluate() }
        outboxIdempotencyKeys() shouldHaveSize TWO_ENTRIES
        outboxIdempotencyKeys().toSet() shouldHaveSize 1

        val redelivery = reevaluated.relay().shouldBeInstanceOf<RelayReport.Completed>()

        redelivery.claimed shouldBe 1
        redelivery.delivered shouldBe 1
        redelivery.skippedDuplicates shouldBe 0
        sentTotal(dying, restarted, reevaluated) shouldBe TWO_ENTRIES
        outboxStateCounts(dataSource()) shouldBe mapOf(ISOLATED_STATE to 1, DELIVERED_STATE to 1)
        inboxKeys() shouldHaveSize 1
    }

    /**
     * R-3 — N 행 배치의 k 행만 **종단까지** 가고 죽는다. 주입 시점은 「발송 기록 k · inbox 행 k」
     * 가 참인 첫 경계 호출이라, 첫 행은 `DELIVERED`+inbox 까지 끝나고 **둘째 행에서** 죽는다.
     * 재기동 run 이 PENDING 을 새로 집어 우연히 「발송 ≤ 1」을 맞추는 길을 막으려고, 재기동
     * run 의 발송 0 과 격리 N−k 를 **따로** 단언한다(설계 검토 (2) 우회 11).
     */
    @Test
    fun `배치 중간에 죽으면 종단 행은 남고 나머지는 격리되며 재기동 발송은 0 이다`() {
        seedPipeline(BATCH_NOTICES)
        lateinit var dying: PipelineAssembly
        dying =
            assembly(
                relayBoundaryHook(
                    crashAfterSettledRows(dataSource(), SETTLED_BEFORE_CRASH) { dying.sender.callCount() },
                ),
            )
        runBlocking { dying.evaluate() }
        outboxStates() shouldContainExactly List(BATCH_SIZE) { PENDING_STATE }

        val aborted = shouldThrow<RelayAborted> { dying.relay() }

        aborted.cause.shouldBeInstanceOf<RelayWorkerDied>()
        aborted.partial.claimed shouldBe BATCH_SIZE
        aborted.partial.delivered shouldBe SETTLED_BEFORE_CRASH
        outboxStateCounts(dataSource()) shouldBe
            mapOf(DELIVERED_STATE to SETTLED_BEFORE_CRASH, CLAIMED_STATE to ORPHANS_AFTER_CRASH)
        inboxKeys() shouldHaveSize SETTLED_BEFORE_CRASH

        val restarted = assembly()
        val report = restarted.relay().shouldBeInstanceOf<RelayReport.Completed>()

        report.orphansIsolated shouldBe ORPHANS_AFTER_CRASH
        report.claimed shouldBe 0
        restarted.sender.callCount() shouldBe 0
        sentTotal(dying, restarted) shouldBe SETTLED_BEFORE_CRASH
        outboxStateCounts(dataSource()) shouldBe
            mapOf(DELIVERED_STATE to SETTLED_BEFORE_CRASH, ISOLATED_STATE to ORPHANS_AFTER_CRASH)
        inboxKeys() shouldHaveSize SETTLED_BEFORE_CRASH
    }

    /**
     * R-4 — **수렴은 고정점이다.** 「`CLAIMED` 0」으로 정의하지 않는다(PENDING 이 남아도 `CLAIMED`
     * 는 0 이다): 한 번 더 재기동해도 보고의 **계수 전부**가 0 이고 상태 **분포 Map** 이 같다.
     * 보고를 값 하나로 대조하므로 [RelayReport.Completed] 에 계수가 늘면 컴파일이 깨진다.
     */
    @Test
    fun `격리 뒤 한 번 더 재기동하면 보고가 전부 0 이고 분포가 그대로다`() {
        val dying = crashedBeforeFirstDispatch()
        val restarted = assembly()
        restarted.relay().shouldBeInstanceOf<RelayReport.Completed>()
        val converged = outboxStateCounts(dataSource())

        val again = assembly()
        val report = again.relay().shouldBeInstanceOf<RelayReport.Completed>()

        report shouldBe
            RelayReport.Completed(
                orphansIsolated = 0,
                claimed = 0,
                delivered = 0,
                skippedDuplicates = 0,
                failed = 0,
                isolated = 0,
                unknownPayload = 0,
            )
        outboxStateCounts(dataSource()) shouldBe converged
        inboxKeys().shouldBeEmpty()
        sentTotal(dying, restarted, again) shouldBe 0
    }

    /**
     * R-5 — **살아 있는 홀더**. 첫 relay 가 배치 중간에서 막혀 있는 동안(임대 연결은 살아 있다)
     * 둘째 조립의 relay 는 `Skipped(LeaseBusy)` 여야 하고 상태 분포는 **움직이지 않아야** 한다 —
     * 살아 있는 남의 in-flight `CLAIMED` 를 격리하면 발송된 행이 `ISOLATED` 로 표기된다.
     *
     * 래치 `await` 의 **반환값**을 단언한다 — 시한 만료로 풀린 교착이 초록으로 지나가지 않게
     * 한다. 막힌 홀더는 풀린 뒤 **끝까지** 전달한다(완주 단언).
     */
    @Test
    fun `살아 있는 홀더가 막혀 있는 동안 둘째 relay 는 Busy 이고 분포가 움직이지 않는다`() {
        seedPipeline(HOLDER_NOTICES)
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val releasedInTime = AtomicBoolean(false)
        lateinit var holder: PipelineAssembly
        holder =
            assembly(
                relayBoundaryHook(
                    pauseAfterFirstDispatch(blocked, release, releasedInTime) { holder.sender.callCount() },
                ),
            )
        runBlocking { holder.evaluate() }
        val executor = Executors.newSingleThreadExecutor()
        val running = executor.submit(Callable { holder.relay() })
        try {
            blocked.await(RELAY_BLOCK_SIGNAL_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            busyWhileHeld(holder, release, releasedInTime, running)
        } finally {
            // 단언이 깨져 빠져나가도 스레드를 남기지 않는다 — 남은 스레드가 쥔 임대·트랜잭션은
            // 다음 test 의 TRUNCATE 를 막는다(6D-1 review PR62 J).
            running.cancel(true)
            executor.shutdownNow()
        }
    }

    private fun busyWhileHeld(
        holder: PipelineAssembly,
        release: CountDownLatch,
        releasedInTime: AtomicBoolean,
        running: java.util.concurrent.Future<RelayReport>,
    ) {
        val held = outboxStateCounts(dataSource())
        held shouldBe mapOf(CLAIMED_STATE to HOLDER_BATCH_SIZE)

        val second = assembly()
        second.relay() shouldBe RelayReport.Skipped(RelaySkipReason.LeaseBusy)
        outboxStateCounts(dataSource()) shouldBe held
        second.sender.callCount() shouldBe 0

        release.countDown()
        val completed =
            running
                .get(RELAY_JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .shouldBeInstanceOf<RelayReport.Completed>()

        releasedInTime.get() shouldBe true
        completed.claimed shouldBe HOLDER_BATCH_SIZE
        completed.delivered shouldBe HOLDER_BATCH_SIZE
        completed.isolated shouldBe 0
        sentTotal(holder, second) shouldBe HOLDER_BATCH_SIZE
        outboxStateCounts(dataSource()) shouldBe mapOf(DELIVERED_STATE to HOLDER_BATCH_SIZE)
        inboxKeys() shouldHaveSize HOLDER_BATCH_SIZE
    }

    /**
     * (2b) 「경계로 처리」 행의 실측 — 주입을 꽂은 조립은 [PipelineAssembly.wiredCollaborators]
     * 에서 **비-MAIN 협력자**로 잡힌다. 정직한 조립에는 그런 객체가 **하나도 없다**(그 단언이
     * `PipelineOneLineE2ETest` 의 출처 단언이고 여기서 한 번 더 둔다). 이 test 가 붉어지는
     * 유일한 길은 주입이 그래프 밖으로 새는 것이다.
     *
     * **실측 둘, 둘 다 첫 실행에서 문면을 고쳤다.** ① 잡히는 것은 [EventTriggeredTransactions]
     * 하나가 아니라 그 **hook 람다**까지다 — 그래프가 데코레이터의 필드까지 내려간다. ②
     * production [ConsumerTransactions] 는 **사라지지 않는다**: 데코레이터가 그것을 `delegate`
     * 로 쥐고 있어 그래프가 둘 다 본다. 그러므로 주입은 「production 자리를 대체해 숨는」 모양이
     * 아니라 **앞에 덧대어 드러나는** 모양이다 — 위임으로는 숨지 못한다.
     *
     * 단언은 개수가 아니다(람다의 합성 클래스 이름을 적으면 표기 하나로 낡는다): 「wrapper 가
     * 거기 있다 · 비-MAIN 은 전부 test 출력이다 · 감싸인 production 경계도 함께 보인다」 셋이다.
     */
    @Test
    fun `주입을 꽂은 조립은 협력자 그래프에서 비-MAIN 으로 잡힌다`() {
        seedStrategy()
        seedProfile()

        val honest = assembly().wiredCollaborators()
        val injected = assembly(relayBoundaryHook { }).wiredCollaborators()

        honest.collected.filter {
            originOf(it) != ClassOrigin.MAIN && portBoundariesOf(it).isEmpty()
        } shouldBe emptyList()
        honest.collected.map { it.javaClass } shouldContain ConsumerTransactions::class.java
        val leaks = injected.collected.filter { originOf(it) != ClassOrigin.MAIN && portBoundariesOf(it).isEmpty() }
        leaks.map { it.javaClass } shouldContain EventTriggeredTransactions::class.java
        leaks.map { originOf(it) }.toSet() shouldBe setOf(ClassOrigin.TEST)
        injected.collected.map { it.javaClass } shouldContain ConsumerTransactions::class.java
    }

    /** R-1 의 죽은 run — 중간 상태까지 단언하고 그 조립을 돌려준다(발송 계수의 한쪽). */
    private fun crashedBeforeFirstDispatch(): PipelineAssembly {
        seedPipeline(listOf(NOTICE))
        lateinit var dying: PipelineAssembly
        dying =
            assembly(
                relayBoundaryHook(crashAfterClaimBeforeDispatch(dataSource()) { dying.sender.callCount() }),
            )
        runBlocking { dying.evaluate() }
        outboxStates() shouldContainExactly listOf(PENDING_STATE)

        val aborted = shouldThrow<RelayAborted> { dying.relay() }

        aborted.cause.shouldBeInstanceOf<RelayWorkerDied>()
        aborted.partial.claimed shouldBe 1
        aborted.partial.delivered shouldBe 0
        dying.sender.callCount() shouldBe 0
        outboxStateCounts(dataSource()) shouldBe mapOf(CLAIMED_STATE to 1)
        inboxKeys().shouldBeEmpty()
        return dying
    }

    /** R-2 의 죽은 run — 발송은 일어났고 T2 는 커밋되지 않았다(`CrashAfterDispatch` 재사용). */
    private fun crashedAfterFirstDispatch(): PipelineAssembly {
        seedPipeline(listOf(NOTICE))
        lateinit var dying: PipelineAssembly
        dying =
            assembly { boundary ->
                CrashAfterDispatch(ConsumerTransactions(boundary)) { dying.sender.callCount() > 0 }
            }
        runBlocking { dying.evaluate() }

        val aborted = shouldThrow<RelayAborted> { dying.relay() }

        aborted.cause.shouldBeInstanceOf<RelayWorkerDied>()
        aborted.partial.claimed shouldBe 1
        aborted.partial.delivered shouldBe 0
        dying.sender.callCount() shouldBe 1
        outboxStateCounts(dataSource()) shouldBe mapOf(CLAIMED_STATE to 1)
        inboxKeys().shouldBeEmpty()
        return dying
    }

    private fun seedPipeline(notices: List<String>) {
        seedStrategy()
        seedProfile()
        collectNotices(notices.map { e2eNoticeItem(it) })
    }

    private fun assembly(
        relayTransactionsFor: (TransactionBoundary) -> ConsumerTransactionPort = { ConsumerTransactions(it) },
    ): PipelineAssembly {
        val server = MlFakeServer.start(successfulMlScript())
        servers += server
        return PipelineAssembly(
            dataSource = dataSource(),
            mlChannel = server.channel,
            at = E2E_NOW,
            correlationPrefix = CORRELATION_PREFIX,
            mlPolicy = e2eMlCallPolicy(),
            relayTransactionsFor = relayTransactionsFor,
        )
    }

    /** 발송은 조립마다 다른 sender 가 기록한다 — 계약의 「발송 0 또는 1」은 그 **합**이다. */
    private fun sentTotal(vararg assemblies: PipelineAssembly): Int = assemblies.sumOf { it.sender.callCount() }

    private companion object {
        const val NOTICE = "E2E-RESTART-0001"
        const val CORRELATION_PREFIX = "restart"
        const val BATCH_SIZE = 3
        const val SETTLED_BEFORE_CRASH = 1
        const val ORPHANS_AFTER_CRASH = 2
        const val HOLDER_BATCH_SIZE = 2
        const val TWO_ENTRIES = 2
        val BATCH_NOTICES = listOf("E2E-RESTART-B001", "E2E-RESTART-B002", "E2E-RESTART-B003")
        val HOLDER_NOTICES = listOf("E2E-RESTART-H001", "E2E-RESTART-H002")
    }
}
