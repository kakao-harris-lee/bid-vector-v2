package bidvector.adapters.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.tag
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * **같은 건을 동시에 여러 번 읽어도 마감 판정 전이는 한 줄이다**(검토 r2 V2-W3 — verifier probe R3b 를 회귀 test 로 커밋).
 *
 * 읽기 셋(목록 · 건 하나 · 타임라인)이 모두 판정을 지나므로, 마감이 막 지난 건을 여러 요청이 동시에 읽으면 판정 쓰기가
 * 경합한다. 조건부 갱신(`case_version = ?`)이 그 경합을 **아무것도 쓰지 않는 쪽**(`VersionConflict`)으로 접고, 진 쪽은
 * 다시 읽어 이미 선 판정을 본다. 그 조건을 빼면 같은 판에 전이 둘이 들어가 타임라인 UNIQUE 위반(500)이 난다(변이로 잰다).
 */
class BidCaseConcurrencyTest : BidCaseDbFixture() {
    @Test
    fun `마감이 지난 건을 동시에 읽어도 시스템 판정은 건마다 한 줄이고 오류가 없다`() {
        val pool = Executors.newFixedThreadPool(THREADS)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val states = Collections.synchronizedSet(mutableSetOf<BidStateTag>())
        val systemCounts = mutableListOf<Int>()
        try {
            repeat(ROUNDS) { round ->
                val mine = account("conc-$round")
                val key = notice("conc-$round")
                seedNotice(key, now.plusSeconds(HOUR))
                val caseId = workflow().open(mine, key, BidKind.REAL).view.case.id
                now = now.plusSeconds(2 * HOUR)
                val start = CountDownLatch(1)
                val done = CountDownLatch(THREADS)
                repeat(THREADS) { thread ->
                    pool.submit {
                        runCatching {
                            start.await()
                            val reader = workflow()
                            when (thread % READ_KINDS) {
                                0 -> reader.list(mine, BidKind.REAL, null).single().case
                                1 -> reader.read(mine, caseId)!!.case
                                else -> reader.timeline(mine, caseId).let { reader.read(mine, caseId)!!.case }
                            }
                        }.onSuccess { states += it.state.tag }
                            .onFailure { errors += "${it::class.simpleName}: ${it.message}" }
                        done.countDown()
                    }
                }
                start.countDown()
                done.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true
                systemCounts += workflow().timeline(mine, caseId)!!.count { it.actorKind == ActorKind.SYSTEM }
            }
        } finally {
            pool.shutdown()
        }

        errors shouldBe emptyList()
        systemCounts shouldBe List(ROUNDS) { 1 }
        states shouldBe setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED)
    }

    private companion object {
        const val THREADS = 8
        const val ROUNDS = 12
        const val READ_KINDS = 3
        const val HOUR = 3_600L
        const val WAIT_SECONDS = 60L
    }
}
