package bidvector.adapters.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.TransitionOutcome
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCommandResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant

/**
 * 일정 축 두 결정(계약 D-7B-3)을 **출하 조립 · 실 DB** 위에서 잰다.
 *
 * - **결정 ① — 마감은 서버가 판정한다(Q-11).** 마감 전 상태의 건을 읽거나 그 건에 명령이 오면, 마감이
 *   지났을 때 시스템 주체로 `MARK_DEADLINE_PASSED` 를 먼저 적용하고 요청을 이어서 판정한다. 그래서
 *   마감 뒤의 「제출함」은 **소급 기록**이 되고, 공고가 파기된 건도 담을 때 적은 마감 사본으로 판정된다.
 *   앞 판에는 이 전이를 부르는 자리가 출하 경로에 없어 소급 확인이 도달 불가였다(verifier r1 H1).
 * - **결정 ② — 통보 예정일을 전이가 지우지 않는다.** 앞 판의 갱신 문장은 그 열에 늘 NULL 을 썼다(검토 H-1).
 */
class BidCaseScheduleTest : BidCaseDbFixture() {
    @Test
    fun `마감 뒤의 제출함은 시스템 전이 뒤 소급 기록이 된다 — 타임라인 순서까지`() {
        val mine = account("sched-a")
        val key = notice("sched-a")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId = workflow.open(mine, key, BidKind.REAL).view.case.id
        workflow.draft(mine, caseId, amount(1_000_000L), now, BidActor.User(mine))

        now = now.plusSeconds(2 * HOUR)
        val result = workflow.command(mine, caseId, BidCommand.RecordBid(submission(), null), BidActor.User(mine))

        val accepted = result.shouldBeInstanceOf<BidCommandResult.Processed>().outcome
        accepted.shouldBeInstanceOf<TransitionOutcome.Accepted>().retroactive shouldBe true
        val tail = workflow.timeline(mine, caseId)!!.takeLast(2)
        tail.map { it.command } shouldBe listOf("MARK_DEADLINE_PASSED", "RECORD_BID")
        tail.map { it.actorKind } shouldBe listOf(ActorKind.SYSTEM, ActorKind.USER)
        tail.first().fromState shouldBe BidStateTag.BID_PLANNED
        tail.first().actorDetail shouldBe "deadline-check"
        tail.last().fromState shouldBe BidStateTag.DEADLINE_PASSED_UNRECORDED
        tail.map { it.retroactive } shouldBe listOf(false, true)
        recordRetroactive(caseId.value) shouldBe true
    }

    /** 공고가 6B-3 으로 파기돼도 담을 때 적은 사본으로 판정된다 — 초안 없이(관심 상태에서) 소급 기록이 된다. */
    @Test
    fun `공고가 파기된 관심 건도 마감 사본으로 판정되어 소급 기록이 된다`() {
        val mine = account("sched-b")
        val key = notice("sched-b")
        val deadline = now.plusSeconds(HOUR)
        seedNotice(key, deadline)
        val workflow = workflow()
        val caseId = workflow.open(mine, key, BidKind.REAL).view.case.id
        deadlineCopy(caseId.value) shouldBe deadline
        purgeNotice(key)

        now = now.plusSeconds(2 * HOUR)
        workflow.command(mine, caseId, BidCommand.RecordBid(submission(), null), BidActor.User(mine))

        workflow.read(mine, caseId)!!.case.state.tag shouldBe BidStateTag.BID_RECORDED
        recordRetroactive(caseId.value) shouldBe true
    }

    @Test
    fun `마감 전에는 읽어도 명령해도 시스템 전이가 없다`() {
        val mine = account("sched-c")
        val key = notice("sched-c")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId = workflow.open(mine, key, BidKind.REAL).view.case.id

        workflow.read(mine, caseId)
        workflow.list(mine, BidKind.REAL, null)
        workflow.command(mine, caseId, BidCommand.Defer(now.plusSeconds(HOUR / 2)), BidActor.User(mine))

        workflow.timeline(mine, caseId)!!.filter { it.actorKind == ActorKind.SYSTEM }.shouldBeEmpty()
        workflow.read(mine, caseId)!!.case.state.tag shouldBe BidStateTag.DEFERRED
    }

    /** 읽기만으로도 판정된다 — 목록의 상태 탭은 **판정 뒤에** 거른다(관심 탭에 마감 지난 건이 남지 않는다). */
    @Test
    fun `마감이 지나면 읽기만 해도 마감 지남이 되고 상태 탭이 그 판정을 따른다`() {
        val mine = account("sched-d")
        val key = notice("sched-d")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId = workflow.open(mine, key, BidKind.REAL).view.case.id

        now = now.plusSeconds(2 * HOUR)

        workflow.list(mine, BidKind.REAL, BidStateTag.WATCHING).shouldBeEmpty()
        val passed = workflow.list(mine, BidKind.REAL, BidStateTag.DEADLINE_PASSED_UNRECORDED)
        passed.map { it.case.id } shouldBe listOf(caseId)
        workflow.timeline(mine, caseId)!!.count { it.actorKind == ActorKind.SYSTEM } shouldBe 1
    }

    @Test
    fun `통보 예정일 값은 전이가 지우지 않는다`() {
        val mine = account("sched-e")
        val key = notice("sched-e")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId = workflow.open(mine, key, BidKind.REAL).view.case.id
        val expected = Instant.parse("2026-11-02T01:00:00Z")
        // 쓰는 경로가 아직 없으므로(OPEN-7B-NOTICE-EXPECTED-WRITER) 소유자 연결로 심는다.
        adminExecute("UPDATE bid_case SET outcome_notice_expected_at = '$expected' WHERE id = ${caseId.value}")

        workflow.command(mine, caseId, BidCommand.Defer(now.plusSeconds(HOUR / 2)), BidActor.User(mine))

        workflow.read(mine, caseId)!!.case.outcomeNoticeExpectedAt shouldBe expected
    }

    private fun recordRetroactive(caseId: Long): Boolean =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT retroactive FROM bid_record WHERE case_id = ?").use { statement ->
                statement.setLong(1, caseId)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }

    private fun deadlineCopy(caseId: Long): Instant? =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT deadline_at FROM bid_case WHERE id = ?").use { statement ->
                statement.setLong(1, caseId)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getTimestamp(1)?.let(Timestamp::toInstant)
                }
            }
        }

    private companion object {
        const val HOUR = 3_600L
    }
}
