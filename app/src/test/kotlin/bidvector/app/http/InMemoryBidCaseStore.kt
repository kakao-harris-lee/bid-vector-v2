package bidvector.app.http

import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.NoticeSnapshot
import bidvector.bidding.tag
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.AppliedTransition
import bidvector.workflow.bidding.BidCaseStorePort
import bidvector.workflow.bidding.OpenCaseResult
import bidvector.workflow.bidding.TimelineEntry
import bidvector.workflow.bidding.TransitionWrite
import java.time.Instant

/**
 * 입찰 건의 HTTP 층 이중체(M7/7B) — 실 DB 없이 **운반 층 거동만** 잰다.
 *
 * 소유 필터를 이 fake 도 스스로 진다. 그래야 운반 층 test 가 「주체가 전달되는가」를 볼 수
 * 있다 — 실제 질의가 계정으로 좁혀지는지는 `BidCaseOwnershipTest`(실 DB)가 따로 잰다.
 */
class InMemoryBidCaseStore : BidCaseStorePort {
    private class Row(
        val account: AccountId,
        var case: BidCase,
        val timeline: MutableList<TimelineEntry> = mutableListOf(),
    )

    private val rows = mutableMapOf<Long, Row>()
    private var nextId = 1L

    override fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
    ): OpenCaseResult {
        val existing =
            rows.values.firstOrNull {
                it.account == account && it.case.notice == notice && it.case.kind == kind
            }
        if (existing != null) return OpenCaseResult.AlreadyOpen(existing.case)
        val id = BidCaseId(nextId++)
        val case = BidCase(id, notice, kind, BidState.Watching, caseVersion = 0, outcomeNoticeExpectedAt = null)
        rows[id.value] = Row(account, case)
        return OpenCaseResult.Opened(case)
    }

    override fun load(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase? = rows[caseId.value]?.takeIf { it.account == account }?.case

    override fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCase> =
        rows.values
            .filter { it.account == account && it.case.kind == kind }
            .filter { state == null || it.case.state.tag == state }
            .map { it.case }

    override fun timeline(
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry> = rows[caseId.value]?.takeIf { it.account == account }?.timeline.orEmpty()

    override fun applyTransition(
        account: AccountId,
        applied: AppliedTransition,
    ): TransitionWrite {
        val row = rows[applied.caseId.value]?.takeIf { it.account == account }
        return if (row == null) {
            TransitionWrite.CaseNotFound
        } else if (row.case.caseVersion != applied.expectedVersion) {
            TransitionWrite.VersionConflict
        } else {
            append(row, applied)
        }
    }

    private fun append(
        row: Row,
        applied: AppliedTransition,
    ): TransitionWrite {
        val nextVersion = row.case.caseVersion + 1
        row.case = row.case.copy(state = applied.next, caseVersion = nextVersion)
        row.timeline +=
            TimelineEntry(
                caseVersion = nextVersion,
                fromState = applied.fromState.tag,
                toState = applied.next.tag,
                command = applied.command.name,
                actorKind = applied.actorKind,
                actorDetail = applied.actorDetail,
                reason = applied.reason?.value,
                retroactive = applied.retroactive,
                occurredAt = applied.occurredAt,
            )
        return TransitionWrite.Applied
    }
}

/** 공고 스냅샷 이중체 — 늘 같은 판을 낸다(운반 층은 스냅샷 **내용**을 보지 않는다). */
fun fixedNoticeSnapshot(takenAt: Instant): NoticeSnapshot =
    NoticeSnapshot(
        noticeRevisionNumber = 1L,
        takenAt = takenAt,
        deadlineAt = takenAt.plusSeconds(1800),
        floorRate = null,
        baseAmount = null,
    )
