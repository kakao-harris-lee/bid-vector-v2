package bidvector.adapters.e2e

import bidvector.adapters.relay.outboxStateCounts
import bidvector.workflow.notification.RelayReport
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * 6D 축 ② 의 **redelivery** 자리 — 계약 표의 문면(「같은 entry 두 번 claim」)을 바로잡아 잰다.
 *
 * **같은 entry 는 두 번 claim 될 수 없다**(구조): 전이표에 `Claimed → Pending` 간선이 없고 claim
 * SQL 이 `WHERE state = 'PENDING'` 이다. 그래서 broker 재전달에 해당하는 것은 「같은 멱등 키의
 * **다른 entry**」가 **재기동을 건너** 둘째 relay 에 닿는 것이다 — run 마다 행 하나이므로
 * (D-6F7-6) 같은 입력을 다시 평가하면 그 모양이 된다.
 *
 * `PipelineFailureInjectionE2ETest` 가 재는 것과 다른 축이다: 그쪽은 같은 키 2행을 **한 relay
 * run** 이 집는다(그러면 둘째 행의 inbox 조회가 첫 행의 T2 **뒤**에 일어난다). 여기서는 두 행이
 * 서로 다른 조립의 서로 다른 run 에 걸쳐 있고, 첫 행은 이미 종단에 있다.
 */
internal class PipelineRedeliveryE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    /**
     * D-1 — 둘째 relay 는 그 행을 **집고**(claimed 1) 발송 없이 종단으로 보낸다(skippedDuplicates
     * 1 · delivered 0). 발송 횟수만 보면 「애초에 집지 않았다」와 구별되지 않으므로 세 계수와
     * sender 기록과 상태 분포를 함께 단언한다.
     */
    @Test
    fun `재기동을 건너 같은 키의 다음 entry 가 와도 inbox 가 발송을 막는다`() {
        val first = deliverFirstEntry()

        val second = assembly()
        runBlocking { second.evaluate() }
        outboxIdempotencyKeys() shouldHaveSize TWO_ENTRIES
        outboxIdempotencyKeys().toSet() shouldHaveSize 1

        val report = second.relay().shouldBeInstanceOf<RelayReport.Completed>()

        report.claimed shouldBe 1
        report.skippedDuplicates shouldBe 1
        report.delivered shouldBe 0
        report.orphansIsolated shouldBe 0
        second.sender.callCount() shouldBe 0
        first.sender.callCount() shouldBe 1
        outboxStateCounts(dataSource()) shouldBe mapOf(DELIVERED_STATE to TWO_ENTRIES)
        inboxKeys() shouldHaveSize 1
    }

    /**
     * D-2 — 종단 행만 남은 상태에서는 새 relay 가 **아무 행도 집지 않는다**. 고아 격리도 0 이다
     * (`DELIVERED` 는 `CLAIMED` 가 아니다). 분포가 전후로 같다는 것이 이 test 의 단언이다.
     */
    @Test
    fun `종단 행만 있을 때 새 relay 는 아무 행도 집지 않고 분포가 그대로다`() {
        val first = deliverFirstEntry()
        val terminal = outboxStateCounts(dataSource())

        val third = assembly()
        val report = third.relay().shouldBeInstanceOf<RelayReport.Completed>()

        report.claimed shouldBe 0
        report.orphansIsolated shouldBe 0
        report.delivered shouldBe 0
        report.skippedDuplicates shouldBe 0
        third.sender.callCount() shouldBe 0
        first.sender.callCount() shouldBe 1
        outboxStateCounts(dataSource()) shouldBe terminal
        inboxKeys() shouldHaveSize 1
    }

    /** 조립 1 — 키 K 를 전달해 `DELIVERED` + inbox 한 행을 만든다(두 축의 공통 전제). */
    private fun deliverFirstEntry(): PipelineAssembly {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        val first = assembly()
        runBlocking { first.evaluate() }

        val report = first.relay().shouldBeInstanceOf<RelayReport.Completed>()

        report.claimed shouldBe 1
        report.delivered shouldBe 1
        report.skippedDuplicates shouldBe 0
        outboxStateCounts(dataSource()) shouldBe mapOf(DELIVERED_STATE to 1)
        inboxKeys() shouldHaveSize 1
        return first
    }

    private fun assembly(): PipelineAssembly {
        val server = MlFakeServer.start(successfulMlScript())
        servers += server
        return PipelineAssembly(
            dataSource = dataSource(),
            mlChannel = server.channel,
            at = E2E_NOW,
            correlationPrefix = CORRELATION_PREFIX,
            mlPolicy = e2eMlCallPolicy(),
        )
    }

    private companion object {
        const val NOTICE = "E2E-REDELIVER-0001"
        const val CORRELATION_PREFIX = "redelivery"
        const val TWO_ENTRIES = 2
    }
}
