package bidvector.adapters.e2e

import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.event.NotificationEvidencePayload
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 6D-1 축 ② — 장애 주입 셋(중복 공고 · ML timeout · DB conflict)을 **같은 조립**에 넣고,
 * 각 주입이 「정의된 자리에서 정의된 결과」를 내는지를 DB 상태와 sender 기록으로 잰다.
 * 계약 위반 두 갈래(unknown field · unsupported schema)와 rollback 은
 * [PipelineContractRejectionE2ETest] 가 갖는다.
 */
internal class PipelineFailureInjectionE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    /**
     * 중복 공고 — 같은 공고를 두 번 **수집**하면 canonical 은 1행이고, 두 번 **평가**하면
     * outbox 는 같은 idempotency 키로 2행이 된다(`outbox` 에 UNIQUE 가 없다, D-6F7-6).
     * 그 2행을 relay 가 집으면 **inbox 중복 제거가 실제로 호출돼** 발송은 1건이다 — 둘째 행은
     * `CLAIMED` 에서 멈춘다. 발송 횟수만 보면 「애초에 1행이었다」와 구별되지 않으므로
     * outbox 2행·inbox 1행을 함께 단언한다.
     */
    @Test
    fun `같은 공고가 두 번 들어와도 canonical 은 1행이고 발송은 inbox 중복 제거로 1건이다`() {
        seedStrategy()
        seedProfile()
        val first = collectNotices(listOf(e2eNoticeItem(NOTICE)))
        val second = collectNotices(listOf(e2eNoticeItem(NOTICE)))

        first.slots
            .single()
            .writes.inserted shouldBe 1
        second.slots
            .single()
            .writes.inserted shouldBe 0
        canonicalNoticeNumbers() shouldContainExactly listOf(NOTICE)

        val assembly = assembly(successfulMlScript())
        runBlocking {
            assembly.evaluate()
            assembly.evaluate()
        }

        outboxIdempotencyKeys() shouldHaveSize 2
        outboxIdempotencyKeys().toSet() shouldHaveSize 1
        assembly.relay() shouldBe 1
        inboxKeys() shouldHaveSize 1
        assembly.sender.callCount() shouldBe 1
        outboxStates() shouldContainExactlyInAnyOrder listOf("DELIVERED", "CLAIMED")
    }

    /**
     * ML timeout — 가짜 서버의 지연을 **정책에서 도출한 시한**([effectivePredictionDeadline])
     * 보다 길게 준다. 결과는 로그가 아니라 outbox payload 에 남는다: 예측 근거가
     * `NOT_PREDICTED` 이고 사유가 `DeadlineExceeded` 다.
     */
    @Test
    fun `예측 지연이 정책 시한을 넘기면 payload 에 DeadlineExceeded 가 남는다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        val budget = opportunityPolicy().predictionBudget
        budget shouldBe SHIPPED_PREDICTION_BUDGET
        val policy = timeoutMlCallPolicy()
        effectivePredictionDeadline(policy) shouldBe budget
        val delay = budget.plus(DELAY_MARGIN)

        val assembly = assembly(successfulMlScript(predictionDelay = delay), policy)
        runBlocking { assembly.evaluate() }

        // 서버가 실제로 불렸고 지연했다 — 이것이 없으면 「호출 자체가 없었다」와 구별되지 않는다.
        servers.last().predictionCallCount() shouldBe 1
        val payload = decodedOutboxPayload()
        payload.noticeId shouldBe "$NOTICE-000"
        payload.evidence shouldBe NotificationEvidencePayload.NotPredicted(reason = "DeadlineExceeded")
    }

    /** 대조 run — 같은 배선에서 지연만 0 이면 예측이 성공해 근거가 `DIAGNOSED` 로 남는다. */
    @Test
    fun `지연 0 대조 run 은 같은 배선에서 DIAGNOSED 근거를 남긴다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        val assembly = assembly(successfulMlScript(predictionDelay = Duration.ZERO), timeoutMlCallPolicy())
        runBlocking { assembly.evaluate() }

        val evidence = decodedOutboxPayload().evidence
        evidence.shouldBeInstanceOf<NotificationEvidencePayload.Diagnosed>()
        evidence.releaseId shouldBe E2E_RELEASE_ID
        evidence.featureSchemaVersion shouldBe E2E_FEATURE_SCHEMA_VERSION
    }

    /**
     * DB conflict — **첫 워커가 행을 쥔 동안** 둘째 relay 가 claim 한다(verifier r1 F-2 /
     * review G-1 의 시제품 모양). 앞 판은 첫 워커가 claim 을 **커밋한 뒤** 둘째가 집었고,
     * 그러면 「이미 `CLAIMED` 인 행은 다시 안 집힌다」만 재게 된다 — 그건 전이 UPDATE 의
     * `WHERE state` 이고 `OutboxTransitionSqlTest` 가 이미 잠근 축이라, 완전 순차에서도
     * production `SKIP LOCKED` 를 떼도 초록이었다(실측).
     *
     * 지금 재는 것은 셋이다 — ① 쥔 동안 둘째 claim 이 **막히지 않고** 빈 목록으로 돌아온다
     * (`SKIP LOCKED` 가 없으면 막혀서 [ClaimRace.releasedWithoutTimeout] 가 거짓이 된다)
     * ② 그 사이 둘째는 그 행을 못 집는다 ③ 첫 워커가 롤백하면 행이 `PENDING` 으로 돌아와
     * 다시 집힌다. 완전 순차로 바꾸면 ②가, `SKIP LOCKED` 를 떼면 ①이 깨진다.
     */
    @Test
    fun `첫 워커가 행을 쥔 동안 둘째는 못 집고 롤백 뒤에는 다시 집는다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        runBlocking { assembly(successfulMlScript()).evaluate() }
        outboxStates() shouldContainExactly listOf("PENDING")

        val race = claimWhileHeld()

        race.firstHeld shouldBe true
        race.releasedWithoutTimeout shouldBe true
        race.secondWhileHeld.shouldBeEmpty()
        outboxStates() shouldContainExactly listOf("CLAIMED")
        race.secondAfterRollback shouldHaveSize 1
    }

    /**
     * 첫 워커는 행을 claim 한 채 대기하다 **커밋 전 예외로 롤백**한다 — 그래서 쥐고 있던
     * 행이 `PENDING` 으로 돌아온다. `release` 래치는 둘째 claim 이 **돌아온 뒤에만** 내려가므로,
     * 둘째가 막히면 그 대기가 [HOLD_TIMEOUT_SECONDS] 만료로 풀리고 [ClaimRace.releasedWithoutTimeout] 가
     * 거짓이 된다.
     */
    private fun claimWhileHeld(): ClaimRace {
        val boundary = TransactionBoundary(dataSource())
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val releasedInTime = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit { holdRowThenRollback(boundary, held, release, releasedInTime) }
        try {
            val firstHeld = held.await(CLAIM_SIGNAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val whileHeld = claimEntryIds(boundary)
            release.countDown()
            worker.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return ClaimRace(firstHeld, releasedInTime.get(), whileHeld, claimEntryIds(boundary))
        } finally {
            // 단언이 깨져 빠져나가도 스레드를 남기지 않는다 — 남은 스레드가 쥔 트랜잭션은 다음
            // test 의 TRUNCATE 를 막는다(review PR62 J).
            worker.cancel(true)
            executor.shutdownNow()
        }
    }

    private fun holdRowThenRollback(
        boundary: TransactionBoundary,
        held: CountDownLatch,
        release: CountDownLatch,
        releasedInTime: AtomicBoolean,
    ) {
        try {
            boundary.inTransaction<Unit> {
                JdbcOutboxPort(boundary).claim(1) shouldHaveSize 1
                held.countDown()
                releasedInTime.set(release.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                throw WorkerDiedHoldingRow()
            }
        } catch (
            @Suppress("SwallowedException") expected: WorkerDiedHoldingRow,
        ) {
            // 의도한 sentinel 만 삼킨다 — 다른 예외는 그대로 올라가 워커 합류에서 드러난다
            // (review PR62 J: `runCatching` 은 무엇이든 삼켰다).
        }
    }

    private fun claimEntryIds(boundary: TransactionBoundary): List<String> =
        boundary.inTransaction { JdbcOutboxPort(boundary).claim(1) }.map { it.entryId.value }

    private fun assembly(
        script: MlFakeScript,
        policy: bidvector.adapters.ml.MlCallPolicyData = e2eMlCallPolicy(),
    ): PipelineAssembly {
        val server = MlFakeServer.start(script)
        servers += server
        return PipelineAssembly(
            dataSource = dataSource(),
            mlChannel = server.channel,
            at = E2E_NOW,
            correlationPrefix = "failure",
            mlPolicy = policy,
        )
    }

    private companion object {
        const val NOTICE = "E2E-INJECT-0001"

        /** 첫 워커가 행을 집었다는 신호를 기다리는 시한 — 집지 못하면 그 자체가 결함이다. */
        const val CLAIM_SIGNAL_TIMEOUT_SECONDS = 5L

        /**
         * 첫 워커가 행을 **쥐고 있는** 시한. 둘째 claim 이 돌아오면 즉시 풀리므로 정상 경로의
         * 비용은 0 이고, 이 값은 **거짓 RED 의 여유**로만 쓰인다 — 호스트가 느려 둘째 claim 하나가
         * 오래 걸리면 첫 워커가 조기 롤백해 막히지 않았는데도 막힌 것처럼 보인다. 넉넉히 둔다.
         */
        const val HOLD_TIMEOUT_SECONDS = 30L

        /** 워커 합류 시한 — 쥠 시한보다 커야 그 만료가 합류 실패로 가려지지 않는다. */
        const val WORKER_TIMEOUT_SECONDS = 60L

        val DELAY_MARGIN: Duration = Duration.ofMillis(100)
    }
}

/** 첫 워커가 행을 쥔 채 죽는 sentinel — 커밋 전 예외라 트랜잭션이 롤백된다. */
internal class WorkerDiedHoldingRow : RuntimeException("첫 워커가 행을 쥔 채 죽는다")

/** `claimWhileHeld` 의 관측 넷 — 셋째·넷째가 「쥔 동안 못 집음」과 「롤백 뒤 복귀」를 가른다. */
internal class ClaimRace(
    val firstHeld: Boolean,
    val releasedWithoutTimeout: Boolean,
    val secondWhileHeld: List<String>,
    val secondAfterRollback: List<String>,
)
