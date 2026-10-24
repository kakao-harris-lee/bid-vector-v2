package bidvector.adapters.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.SkipReason
import bidvector.bidding.tag
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCommandResult
import bidvector.workflow.bidding.TimelineEntry
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * **마감 판정은 되돌릴 수 있다**(계약 D-7B-4 결정 ⑤ ⑥ · 검토 r2 C2-N1 high · C2-N7) — 출하 조립 · 실 DB.
 *
 * 앞 판에서 마감 판정은 커밋된 전이라 비가역이었다: 마감이 한때 과거로 보여 판정이 선 뒤 **마감을 미룬 정정 공고**가 오면,
 * 건은 「마감 지남」에 남아 초안 · 건너뜀 · 나중에가 영구히 409 이고 유일한 출구가 하지 않은 투찰의 소급 기록이었다.
 * 정정 공고 test 둘은 판정이 **서기 전에** 정정이 오는 순서라 그 축을 비껴갔다 — 여기는 **판정이 선 뒤** 정정이 오는 순서다.
 *
 * 그리고 「행 없음」과 「값 없음」(마감을 지운 정정)을 가른다 — 앞 판은 둘을 같은 값으로 접어 낡은 사본이 판정을 지배했다.
 */
class BidCaseDeadlineRevocationTest : BidCaseDbFixture() {
    /** 검토 r2 C2-N1 의 재현 그대로: 담기 → 읽기(판정이 섬) → 연장 정정 → 읽기 → 원래 상태. */
    @Test
    fun `판정 뒤 마감을 미룬 정정이 오면 시스템 되돌림으로 원래 상태가 되고 다시 움직인다`() {
        val mine = account("revoke-a")
        val key = notice("revoke-a")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id
        workflow.draft(mine, caseId, amount(1_000_000L), now, BidActor.User(mine))

        now = now.plusSeconds(2 * HOUR)
        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.DEADLINE_PASSED_UNRECORDED
        correctNoticeDeadline(key, now.plusSeconds(3 * HOUR))

        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.BID_PLANNED
        val tail = workflow.timeline(mine, caseId)!!.takeLast(2)
        tail.map { it.command } shouldBe listOf("MARK_DEADLINE_PASSED", "REVOKE_DEADLINE_PASSED")
        tail.map { it.actorKind } shouldBe listOf(ActorKind.SYSTEM, ActorKind.SYSTEM)
        tail.map { it.actorDetail } shouldBe listOf("deadline-check", "deadline-check")
        tail.last().fromState shouldBe BidStateTag.DEADLINE_PASSED_UNRECORDED
        tail.last().toState shouldBe BidStateTag.BID_PLANNED
        // 다시 움직인다 — 초안 수정과 건너뜀이 받아들여진다(앞 판에서는 영구히 409).
        workflow
            .draft(mine, caseId, amount(2_000_000L), now, BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()
        accepted(workflow.command(mine, caseId, BidCommand.Skip(SkipReason("연장 뒤 포기")), BidActor.User(mine)))
    }

    /** 「나중에」에서 판정된 건은 리마인드 시각을 판정이 지웠으므로 관심으로 돌아온다(§6). */
    @Test
    fun `나중에에서 판정된 건은 되돌릴 때 관심으로 돌아온다`() {
        val mine = account("revoke-b")
        val key = notice("revoke-b")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id
        workflow.command(mine, caseId, BidCommand.Defer(now.plusSeconds(HOUR / 2)), BidActor.User(mine))

        now = now.plusSeconds(2 * HOUR)
        workflow.read(mine, caseId)
        correctNoticeDeadline(key, now.plusSeconds(HOUR))

        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.WATCHING
        lastSystem(workflow.timeline(mine, caseId)!!).command shouldBe "REVOKE_DEADLINE_PASSED"
    }

    /** 기록으로 이미 「마감 지남」을 떠난 건은 되돌림 대상이 아니다 — 기록은 사실이다. */
    @Test
    fun `소급 기록으로 떠난 건은 연장 정정이 와도 그대로다`() {
        val mine = account("revoke-c")
        val key = notice("revoke-c")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id

        now = now.plusSeconds(2 * HOUR)
        workflow.command(mine, caseId, BidCommand.RecordBid(submission(), null), BidActor.User(mine))
        correctNoticeDeadline(key, now.plusSeconds(HOUR))

        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.BID_RECORDED
        workflow.timeline(mine, caseId)!!.count { it.command == "REVOKE_DEADLINE_PASSED" } shouldBe 0
    }

    /** 결정 ⑥ — 공고 행이 있고 마감이 미정(NULL)이면 판정하지 않는다(낡은 사본으로 판정하지 않는다). */
    @Test
    fun `마감을 지운 정정이면 사본이 지났어도 판정하지 않는다`() {
        val mine = account("revoke-d")
        val key = notice("revoke-d")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id
        correctNoticeDeadline(key, null)

        now = now.plusSeconds(2 * HOUR)

        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.WATCHING
        workflow.timeline(mine, caseId)!!.filter { it.actorKind == ActorKind.SYSTEM }.shouldBeEmpty()
    }

    /** 결정 ⑥ — 판정이 선 뒤 마감이 지워지면 미정이므로 판정을 거둔다. */
    @Test
    fun `판정 뒤 마감을 지운 정정이 오면 되돌린다`() {
        val mine = account("revoke-e")
        val key = notice("revoke-e")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id

        now = now.plusSeconds(2 * HOUR)
        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.DEADLINE_PASSED_UNRECORDED
        correctNoticeDeadline(key, null)

        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.WATCHING
    }

    /** 검토 r2 V2-W1 · C2-N9 — 이미 마감이 지난 공고를 담으면 응답이 곧 「마감 지남」이다(담기 → 읽기가 두 답을 주지 않는다). */
    @Test
    fun `이미 마감이 지난 공고를 담으면 담기 응답부터 마감 지남이다`() {
        val mine = account("revoke-f")
        val key = notice("revoke-f")
        seedNotice(key, now.minusSeconds(HOUR))

        val opened = workflow().open(mine, key, BidKind.REAL)

        opened.created shouldBe true
        opened.view.case.state.tag shouldBe BidStateTag.DEADLINE_PASSED_UNRECORDED
    }

    /** 목록도 같은 판정을 지난다 — 되돌린 건이 상태 탭에 원래 상태로 보인다(판정 후보를 SQL 이 좁히는 경로). */
    @Test
    fun `목록의 판정 후보 경로도 되돌림을 적용한다`() {
        val mine = account("revoke-g")
        val key = notice("revoke-g")
        seedNotice(key, now.plusSeconds(HOUR))
        val workflow = workflow()
        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id

        now = now.plusSeconds(2 * HOUR)
        val passed = workflow.list(mine, BidKind.REAL, BidStateTag.DEADLINE_PASSED_UNRECORDED)
        passed.map { it.case.id } shouldBe listOf(caseId)
        correctNoticeDeadline(key, now.plusSeconds(HOUR))

        workflow.list(mine, BidKind.REAL, BidStateTag.WATCHING).map { it.case.id } shouldBe listOf(caseId)
        workflow.list(mine, BidKind.REAL, BidStateTag.DEADLINE_PASSED_UNRECORDED).shouldBeEmpty()
    }

    private fun accepted(result: BidCommandResult) {
        result
            .shouldBeInstanceOf<BidCommandResult.Processed>()
            .outcome
            .shouldBeInstanceOf<bidvector.bidding.TransitionOutcome.Accepted>()
    }

    private fun lastSystem(timeline: List<TimelineEntry>): TimelineEntry =
        timeline.last { it.actorKind == ActorKind.SYSTEM }

    private companion object {
        const val HOUR = 3_600L
    }
}
