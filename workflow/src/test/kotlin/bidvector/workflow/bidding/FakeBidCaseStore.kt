package bidvector.workflow.bidding

import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.tag
import bidvector.workflow.access.AccountId
import java.time.Instant

/** 소유 필터를 **포트 안에서** 지는 fake — 실 DB 축은 `adapters` 쪽이 따로 잰다. */
internal class FakeBidCaseStore : BidCaseStorePort {
    private data class Row(
        val account: AccountId,
        var case: BidCase,
        val timeline: MutableList<TimelineEntry> = mutableListOf(),
    )

    private val rows = mutableMapOf<Long, Row>()
    private var nextId = 1L

    fun seed(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        state: BidState,
        deadlineAt: Instant? = null,
    ): BidCase {
        val id = BidCaseId(nextId++)
        val case =
            BidCase(id, notice, kind, state, caseVersion = 0, outcomeNoticeExpectedAt = null, deadlineAt = deadlineAt)
        rows[id.value] = Row(account, case)
        return case
    }

    fun stateOf(id: BidCaseId): BidState = rows.getValue(id.value).case.state

    /** 읽기와 저장 사이에 다른 쓰기가 끼어든 상황을 만든다. */
    var interleaveBeforeApply: Boolean = false

    /**
     * 읽기와 저장 사이에 **건이 사라진** 상황을 만든다 — 저장이 `CaseNotFound` 로 떨어지는
     * 유일한 길이다(소유 확인은 읽기에서 이미 지났으므로). 그 갈래가 이중체 없이는 닿지 않아
     * **변이가 초록이었다**(M4) — 전이가 저장되지 않았는데 알림을 적는 변형이 지나갔다.
     */
    var vanishBeforeApply: Boolean = false

    fun countOf(account: AccountId): Int = rows.values.count { it.account.value == account.value }

    override fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
        deadlineAt: Instant?,
    ): OpenCaseResult {
        val existing =
            rows.values.firstOrNull {
                it.account.value == account.value && it.case.notice == notice && it.case.kind == kind
            }
        if (existing != null) return OpenCaseResult.AlreadyOpen(existing.case)
        return OpenCaseResult.Opened(seed(account, notice, kind, BidState.Watching, deadlineAt))
    }

    override fun settleCandidates(
        account: AccountId,
        kind: BidKind,
        judgedStates: Set<BidStateTag>,
        now: Instant,
    ): List<BidCase> =
        rows.values
            .filter { it.account.value == account.value && it.case.kind == kind }
            .filter { it.case.state.tag in judgedStates || it.case.state.tag == BidStateTag.DEADLINE_PASSED_UNRECORDED }
            .map { it.case }

    override fun load(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase? = rows[caseId.value]?.takeIf { it.account.value == account.value }?.case

    override fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCase> =
        rows.values
            .filter { it.account.value == account.value && it.case.kind == kind }
            .filter { state == null || it.case.state.tag == state }
            .map { it.case }

    override fun timeline(
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry> = rows[caseId.value]?.takeIf { it.account.value == account.value }?.timeline.orEmpty()

    override fun applyTransition(
        account: AccountId,
        applied: AppliedTransition,
    ): TransitionWrite {
        if (vanishBeforeApply) {
            rows.remove(applied.caseId.value)
            vanishBeforeApply = false
        }
        val row = rows[applied.caseId.value]?.takeIf { it.account.value == account.value }
        if (row != null && interleaveBeforeApply) {
            row.case = row.case.copy(caseVersion = row.case.caseVersion + 1)
            interleaveBeforeApply = false
        }
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
