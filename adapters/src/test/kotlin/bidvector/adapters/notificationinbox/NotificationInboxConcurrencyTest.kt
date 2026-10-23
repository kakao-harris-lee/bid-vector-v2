package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * M7/7C③ — **경합에서 항목이 증식하지 않는다**(설계 검토 우회 ⑥). 중복 억제가 PK + 단일 문장
 * upsert 라는 주장의 유일한 실측이다.
 *
 * 왜 이 test 가 필요한가: 중복 억제를 select-then-insert 로 구현해도 **직렬 test 는 전부
 * 통과한다**. 두 연결이 같은 키로 동시에 들어올 때만 갈린다 — 그때 select 판은 둘 다 「없다」를
 * 보고 둘째가 PK 위반으로 떨어진다(계약은 거부가 아니라 **갱신**이다). 그 차이를 재는 것은
 * 동시성뿐이다.
 */
internal class NotificationInboxConcurrencyTest : NotificationInboxTestSupport() {
    /**
     * 행 수와 결과 값을 **함께** 잰다. 삽입이 둘이면 행 수가 잡고, **거부가 섞이면**(`Failed`)
     * 결과 집합이 잡는다 — 계약은 「둘째는 거부」가 아니라 「둘째는 갱신」이다.
     */
    @Test
    fun `두 연결이 같은 키를 동시에 기록해도 항목은 하나다 — 삽입 하나와 갱신 하나`() {
        val account = insertAccount("race-outcomes")
        val notice = unknownNotice("N-RACE2")
        val barrier = CyclicBarrier(WRITERS)
        val pool = Executors.newFixedThreadPool(WRITERS)

        val outcomes =
            try {
                (0 until WRITERS)
                    .map {
                        pool.submit<NotificationInboxOutcome> {
                            barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            inbox().record(record(account, notice, NotificationFact.NoticeRevised))
                        }
                    }.map { it.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

        outcomes shouldContainExactlyInAnyOrder
            listOf(NotificationInboxOutcome.Created, NotificationInboxOutcome.Updated)
        rowCount() shouldBe 1L
    }

    /**
     * **음성 대조** — 키가 다르면 동시 기록이 두 행을 만든다. 위 단언이 「언제나 행 하나」인
     * 술어 때문에 통과하는 것이 아님을 보인다.
     */
    @Test
    fun `키가 다른 동시 기록은 두 항목이 된다 — 음성 대조`() {
        val account = insertAccount("race-distinct")
        val barrier = CyclicBarrier(WRITERS)
        val pool = Executors.newFixedThreadPool(WRITERS)

        try {
            (0 until WRITERS)
                .map { index ->
                    pool.submit {
                        barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        inbox().record(
                            record(account, unknownNotice("N-D$index"), NotificationFact.NoticeRevised),
                        )
                    }
                }.forEach { it.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        rowCount() shouldBe WRITERS.toLong()
    }

    private companion object {
        const val WRITERS = 2
        const val BARRIER_TIMEOUT_SECONDS = 10L
        const val TASK_TIMEOUT_SECONDS = 30L
    }
}
