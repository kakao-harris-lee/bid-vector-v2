package bidvector.adapters.e2e

import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.persistence.TransactionBoundary
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        outboxStates() shouldContainExactly listOf("DELIVERED", "CLAIMED")
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
        val policy = e2eMlCallPolicy(deadlineCeiling = SHORT_DEADLINE)
        val delay = effectivePredictionDeadline(policy).plus(DELAY_MARGIN)

        val assembly = assembly(successfulMlScript(predictionDelay = delay), policy)
        runBlocking { assembly.evaluate() }

        val payload = outboxPayloads().single()
        payload shouldContain "NOT_PREDICTED"
        payload shouldContain "DeadlineExceeded"
        payload shouldNotContain "DIAGNOSED"
    }

    /** 대조 run — 같은 배선에서 지연만 0 이면 예측이 성공해 근거가 `DIAGNOSED` 로 남는다. */
    @Test
    fun `지연 0 대조 run 은 같은 배선에서 DIAGNOSED 근거를 남긴다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        val policy = e2eMlCallPolicy(deadlineCeiling = SHORT_DEADLINE)

        val assembly = assembly(successfulMlScript(predictionDelay = Duration.ZERO), policy)
        runBlocking { assembly.evaluate() }

        val payload = outboxPayloads().single()
        payload shouldContain "DIAGNOSED"
        payload shouldContain E2E_RELEASE_ID
        payload shouldNotContain "DeadlineExceeded"
    }

    /**
     * DB conflict — relay 두 벌이 같은 outbox 행을 동시에 집으려 하면 하나만 집는다
     * (`FOR UPDATE SKIP LOCKED`). 순서는 래치로 고정한다(sleep 금지, `OutboxClaimConcurrencyTest`
     * 와 같은 모양). 충돌이 실제로 없었다면 둘째 워커가 같은 행을 집어 `shouldBeEmpty` 가 깨진다.
     */
    @Test
    fun `두 relay 가 같은 outbox 행을 동시에 집으려 하면 하나만 집는다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        runBlocking { assembly(successfulMlScript()).evaluate() }
        outboxIdempotencyKeys() shouldHaveSize 1

        val claims = concurrentClaims()

        claims.first shouldHaveSize 1
        claims.second.shouldBeEmpty()
        outboxStates() shouldContainExactly listOf("CLAIMED")
    }

    private fun concurrentClaims(): Pair<List<String>, List<String>> {
        val boundary = TransactionBoundary(dataSource())
        val firstClaimed = CountDownLatch(1)
        val secondDone = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val worker =
            Callable {
                boundary.inTransaction {
                    val claimed = JdbcOutboxPort(boundary).claim(1).map { it.entryId.value }
                    firstClaimed.countDown()
                    secondDone.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    claimed
                }
            }
        val future = executor.submit(worker)
        firstClaimed.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val second = boundary.inTransaction { JdbcOutboxPort(boundary).claim(1) }.map { it.entryId.value }
        secondDone.countDown()
        val first = future.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        executor.shutdown()
        return first to second
    }

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
        const val LATCH_TIMEOUT_SECONDS = 5L
        val SHORT_DEADLINE: Duration = Duration.ofMillis(200)
        val DELAY_MARGIN: Duration = Duration.ofMillis(100)
    }
}
