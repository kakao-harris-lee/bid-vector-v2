package bidvector.adapters.e2e

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.adapters.relay.RelayWorkerDied
import bidvector.adapters.relay.outboxStateCounts
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.notification.RelayAborted
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelaySkipReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
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
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 6D 축 ③ — **restart 뒤 outbox/inbox 수렴**. 파이프라인(수집 → 평가 → outbox)이 만든 행
 * 위에서 production relay 가 T1 과 T2 사이에 죽고, **새 조립**이 그 뒤를 받는다.
 *
 * **「재기동」의 뜻**(B-1 (a)) — 새 [PipelineAssembly] 인스턴스이고, 프로세스 사망의 in-JVM
 * 등가다. 새 조립은 새 relay · 새 port · 새 sender 를 준다.
 *
 * **임대 세션은 인스턴스 단위가 아니다**(verifier r1 R1-M-2 가 반증). `PostgresAdvisoryLockLease`
 * 는 `withLease` **호출마다** `dataSource.connection` 을 새로 열므로, 같은 조립의 relay 를 다시
 * 불러도 그 호출은 새 세션에서 잠금을 다툰다 — 막혀 있는 홀더가 있으면 **같은 조립의 재호출도
 * `Busy` 를 받는다**(탐침 실측). 그래서 「새 인스턴스라서 새 세션이다」라는 논증은 서지 않는다.
 * relay 는 호출 사이에 상태가 없어 **새 인스턴스와 재호출이 동치**이고, 새 인스턴스를 쓰는 실익은
 * sender 계수를 조립별로 가르는 것(발송 합)과 production 재기동의 모양을 따르는 것뿐이다.
 *
 * 「죽은 조립의 임대가 실제로 풀렸다」의 증거는 **재기동 relay 가 `Busy` 가 아니라는 사실뿐**이다 —
 * 죽은 run 의 예외가 `withLease` 의 `finally` 를 지나 연결을 반납했기 때문이다. 그 반대(살아
 * 있는 홀더가 막혀 있으면 `Busy`)를 R-5 가 잰다.
 *
 * **크래시는 순번이 아니라 사건에 걸린다**([EventTriggeredTransactions] 의 술어들). 발송 계수는
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
        val keys = outboxIdempotencyKeys()
        keys shouldHaveSize TWO_ENTRIES
        keys.toSet() shouldHaveSize 1

        val redelivery = reevaluated.relay().shouldBeInstanceOf<RelayReport.Completed>()

        redelivery.claimed shouldBe 1
        redelivery.delivered shouldBe 1
        redelivery.skippedDuplicates shouldBe 0
        // 이미 `ISOLATED` 인 행은 `claimedEntries` 에 들지 않는다 — 셋째 run 은 고아를 보지 않는다.
        redelivery.orphansIsolated shouldBe 0
        sentTotal(dying, restarted, reevaluated) shouldBe REDISPATCHED_TOTAL
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
        val dying =
            assembly(
                relayBoundaryHook { sender ->
                    crashAfterSettledRows(dataSource(), SETTLED_BEFORE_CRASH, sender::callCount)
                },
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
     * R-4 — **수렴은 고정점이고, 그 고정점이 종단이어야 한다.** 한 번 더 재기동해도 보고의 **계수
     * 전부**가 0 이고 상태 **분포 Map** 이 같다. 보고를 값 하나로 대조하므로
     * [RelayReport.Completed] 에 계수가 늘면 컴파일이 깨진다.
     *
     * 「고정점」만으로는 부족하다(verifier r1 R1-M-1 실측) — production 의 고아 격리를 no-op 로
     * 바꾸면 `CLAIMED` 1 이 좌초한 채 **그것도 고정점이 되어** 앞 판의 R-4 가 초록이었다. 그래서
     * 둘을 더했다: 첫 재기동 보고의 `orphansIsolated == 1` 과, 수렴한 분포가 **종단뿐**이라는
     * Map 전체 등식(PENDING·CLAIMED 0). 둘 다 그 변이에서 붉어진다.
     *
     * 「`CLAIMED` 0」 하나로 수렴을 정의하지 않는 이유는 그대로다 — PENDING 이 남아도 `CLAIMED`
     * 는 0 이다.
     */
    @Test
    fun `격리 뒤 한 번 더 재기동하면 보고가 전부 0 이고 알림 행이 전부 종단으로 남는다`() {
        val dying = crashedBeforeFirstDispatch()
        val restarted = assembly()
        val isolation = restarted.relay().shouldBeInstanceOf<RelayReport.Completed>()

        // 수렴의 전제 — 첫 재기동이 **실제로 격리했다**. 이 줄이 없으면 격리가 없어도 고정점이다.
        isolation.orphansIsolated shouldBe 1
        val converged = outboxStateCounts(dataSource())
        // 알림 종류 행이 전부 종단 — Map 전체 등식이라 PENDING·CLAIMED 는 0 이다.
        converged shouldBe mapOf(ISOLATED_STATE to 1)

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
     * **주입의 자리 자체를 잠근다**(PR #64 F1). 앞 판의 술어는 「`CLAIMED` 가 하나라도 있고 발송
     * 0」이라, 선재 고아가 있는 DB 에서 재사용하면 **첫 경계 호출**(고아 목록 조회)에서 터졌다 —
     * 사건의 이름(「claim 커밋 뒤·발송 전」)과 실제 자리가 어긋났고, 그래도 `RelayAborted` 는
     * 떴으므로 R-1 류 단언은 그 어긋남을 보지 못했다.
     *
     * 입력을 그 모양으로 만든다 — **상태 강제 없이**: R-1 의 죽은 run 이 남긴 `CLAIMED` 한 행이
     * 선재 고아이고, 그 뒤 재평가가 같은 키의 둘째 entry 를 `PENDING` 으로 놓는다. 주입이 자리를
     * 지키면 그 run 은 고아를 **격리하고 claim 까지 해낸 뒤** 죽는다(`orphansIsolated 1` ·
     * `claimed 1`). 자리를 안 지키면 둘 다 0 이다.
     *
     * 이 test 가 「수가 아니라 id 집합」인 이유도 함께 잠근다 — 격리가 선재 `CLAIMED` 를 태우므로
     * 이 run 이 하나를 집은 뒤에도 **수는 그대로 1** 이다.
     */
    @Test
    fun `선재 고아가 있어도 주입은 이 run 의 claim 뒤에만 터진다`() {
        val first = crashedBeforeFirstDispatch()

        val reevaluated = assembly()
        runBlocking { reevaluated.evaluate() }
        outboxStateCounts(dataSource()) shouldBe mapOf(CLAIMED_STATE to 1, PENDING_STATE to 1)

        val second = dyingBeforeFirstDispatch()
        val aborted = shouldThrow<RelayAborted> { second.relay() }

        aborted.cause.shouldBeInstanceOf<RelayWorkerDied>()
        aborted.partial.orphansIsolated shouldBe 1
        aborted.partial.claimed shouldBe 1
        aborted.partial.delivered shouldBe 0
        sentTotal(first, reevaluated, second) shouldBe 0
        outboxStateCounts(dataSource()) shouldBe mapOf(ISOLATED_STATE to 1, CLAIMED_STATE to 1)
        inboxKeys().shouldBeEmpty()
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
        val holder =
            assembly(
                relayBoundaryHook { sender ->
                    pauseAfterFirstDispatch(blocked, release, releasedInTime, sender::callCount)
                },
            )
        runBlocking { holder.evaluate() }
        val executor = Executors.newSingleThreadExecutor()
        val running = executor.submit(Callable { holder.relay() })
        var terminated = false
        try {
            holdThenRelease(holder, blocked, release, releasedInTime, running)
        } finally {
            // 단언이 깨져 빠져나가도 스레드를 남기지 않는다 — 남은 스레드가 쥔 임대·트랜잭션은
            // 다음 test 의 TRUNCATE 를 막는다(6D-1 review PR62 J). `shutdownNow` 는 **중단을
            // 요청할 뿐**이라 돌아온 시점에 스레드가 아직 살아 있을 수 있어 합류를 기다린다
            // (PR #64 F2). 여기서 단언하지 않는다 — finally 가 던지면 본문의 실패 원인을 덮는다.
            running.cancel(true)
            executor.shutdownNow()
            terminated = executor.awaitTermination(E2E_JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        withClue("홀더 스레드가 합류 시한 안에 끝나지 않았다 — 다음 test 와 임대를 다툰다") {
            terminated shouldBe true
        }
    }

    private fun holdThenRelease(
        holder: PipelineAssembly,
        blocked: CountDownLatch,
        release: CountDownLatch,
        releasedInTime: AtomicBoolean,
        running: Future<RelayReport>,
    ) {
        val signalled = blocked.await(E2E_SIGNAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 홀더가 hook 에 닿기 전에 끝났으면 그 사실을 단언에 싣는다(cr G-2 · PR #64 F3).
        // 예외로 끝났으면 `get` 이 그 예외를 올리고, **값으로** 끝났으면 그 보고가 원인이므로
        // clue 에 담는다 — 앞 판은 그 보고를 버려 `expected true but was false` 만 남았다.
        val earlyReport = if (!signalled && running.isDone) running.get(0, TimeUnit.SECONDS) else null
        withClue("홀더 relay 가 막히기 전에 끝났다면 그 보고: $earlyReport") { signalled shouldBe true }
        busyWhileHeld(holder, release, releasedInTime, running)
    }

    private fun busyWhileHeld(
        holder: PipelineAssembly,
        release: CountDownLatch,
        releasedInTime: AtomicBoolean,
        running: Future<RelayReport>,
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
                .get(E2E_JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
     * 단언에 **이름은 적지 않는다** — 람다의 합성 클래스 이름은 표기 하나로 낡는다. 그러나 **개수는
     * 이름이 아니다**(cr G-1): `leaks` 가 둘이라는 단언이 위 실측 ① 자체를 회귀 test 로 만든다.
     *
     * 그리고 **건너뛴 가지 셋을 0 으로 잠근다**(cr G-1). hook 람다는 깊이 2 이므로, 그 필드 읽기가
     * 실패하거나 깊이 상한에 걸리면 `leaks` 에서 람다만 조용히 사라진다 — 그때 「wrapper 가 거기
     * 있다」와 「전부 test 출력이다」는 **둘 다 그대로 통과한다**. 잠그는 것은 오늘의 거동이 아니라
     * 그 거동이 유지된다는 사실이다.
     *
     * 그래서 이 test 가 붉어지는 길은 둘이다 — 주입이 그래프 밖으로 새거나, 순회가 주입 아래에서
     * 잘리는 것.
     */
    @Test
    fun `주입을 꽂은 조립은 협력자 그래프에서 비-MAIN 으로 잡힌다`() {
        seedStrategy()
        seedProfile()

        val honest = assembly().wiredCollaborators()
        val injected = assembly(relayBoundaryHook { NO_OP_BOUNDARY_HOOK }).wiredCollaborators()

        honest.collected.filter {
            originOf(it) != ClassOrigin.MAIN && portBoundariesOf(it).isEmpty()
        } shouldBe emptyList()
        honest.collected.map { it.javaClass } shouldContain ConsumerTransactions::class.java
        injected.depthLimitHits shouldBe 0
        injected.traversalFailures shouldBe emptyList()
        injected.skippedHolders shouldBe emptyList()
        val leaks = injected.collected.filter { originOf(it) != ClassOrigin.MAIN && portBoundariesOf(it).isEmpty() }
        leaks shouldHaveSize INJECTED_NON_MAIN_COUNT
        leaks.map { it.javaClass } shouldContain EventTriggeredTransactions::class.java
        leaks.map { originOf(it) }.toSet() shouldBe setOf(ClassOrigin.TEST)
        injected.collected.map { it.javaClass } shouldContain ConsumerTransactions::class.java
    }

    /** R-1 의 주입을 꽂은 조립 — 선재 고아가 있는 DB 에서도 **이 run 의 claim 뒤**에만 터진다. */
    private fun dyingBeforeFirstDispatch(): PipelineAssembly =
        assembly(
            relayBoundaryHook { sender ->
                crashAfterClaimBeforeDispatch(dataSource(), sender::callCount)
            },
        )

    /** R-1 의 죽은 run — 중간 상태까지 단언하고 그 조립을 돌려준다(발송 계수의 한쪽). */
    private fun crashedBeforeFirstDispatch(): PipelineAssembly {
        seedPipeline(listOf(NOTICE))
        val dying = dyingBeforeFirstDispatch()
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

    /** R-2 의 죽은 run — 발송은 일어났고 T2 는 커밋되지 않았다. */
    private fun crashedAfterFirstDispatch(): PipelineAssembly {
        seedPipeline(listOf(NOTICE))
        val dying = assembly(relayBoundaryHook { sender -> crashAfterFirstDispatch(sender::callCount) })
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
        relayTransactionsFor: (TransactionBoundary, RecordingNotificationSender) -> ConsumerTransactionPort =
            { boundary, _ -> ConsumerTransactions(boundary) },
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

        /** 도출값이다(cr G-4) — 배치 크기를 손보면 이 값이 함께 움직인다. */
        const val ORPHANS_AFTER_CRASH = BATCH_SIZE - SETTLED_BEFORE_CRASH
        const val HOLDER_BATCH_SIZE = 2

        /** 같은 멱등 키의 **행 수** — 재평가가 새 entry 를 낳는다. */
        const val TWO_ENTRIES = 2

        /**
         * B-2 가 「사실로 고정한다」고 적은 **발송 합** — 행 수와 우연히 같을 뿐 축이 다르다
         * (cr G-9). 이 값이 바뀌면 at-most-once 설계가 바뀐 것이다.
         */
        const val REDISPATCHED_TOTAL = 2

        /** 주입 조립의 비-MAIN 협력자 수 — wrapper 와 그 hook 람다 둘이다(실측 ①). */
        const val INJECTED_NON_MAIN_COUNT = 2
        val BATCH_NOTICES = listOf("E2E-RESTART-B001", "E2E-RESTART-B002", "E2E-RESTART-B003")
        val HOLDER_NOTICES = listOf("E2E-RESTART-H001", "E2E-RESTART-H002")
    }
}
