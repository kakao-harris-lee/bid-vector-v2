package bidvector.app.http

import bidvector.bidding.ActorKind
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.NoticeSnapshot
import bidvector.bidding.OPEN_CASE_COMMAND
import bidvector.bidding.tag
import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.AppliedTransition
import bidvector.workflow.bidding.BidCaseStorePort
import bidvector.workflow.bidding.BidCaseWorkflow
import bidvector.workflow.bidding.EVIDENCE_RETENTION_POLICY
import bidvector.workflow.bidding.EvidenceRetentionPolicyData
import bidvector.workflow.bidding.NoticeDeadline
import bidvector.workflow.bidding.NoticeDeadlineQueryPort
import bidvector.workflow.bidding.OpenCaseResult
import bidvector.workflow.bidding.TimelineEntry
import bidvector.workflow.bidding.TransitionWrite
import java.time.Instant
import java.time.LocalDate

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
        deadlineAt: Instant?,
    ): OpenCaseResult {
        val existing =
            rows.values.firstOrNull {
                it.account.value == account.value && it.case.notice == notice && it.case.kind == kind
            }
        if (existing != null) return OpenCaseResult.AlreadyOpen(existing.case)
        val id = BidCaseId(nextId++)
        val case =
            BidCase(
                id,
                notice,
                kind,
                BidState.Watching,
                caseVersion = 0,
                outcomeNoticeExpectedAt = null,
                deadlineAt = deadlineAt,
            )
        // 실 어댑터(`JdbcBidCaseStore.open`)처럼 **생성 전이 한 줄**을 남긴다(검토 C-M7) — 빠지면 이
        // 이중체 위의 「담은 직후 타임라인」이 실 DB 와 갈린다.
        rows[id.value] = Row(account, case, mutableListOf(creationEntry(at)))
        return OpenCaseResult.Opened(case)
    }

    private fun creationEntry(at: Instant): TimelineEntry =
        TimelineEntry(
            caseVersion = 0,
            fromState = null,
            toState = BidStateTag.WATCHING,
            command = OPEN_CASE_COMMAND,
            actorKind = ActorKind.USER,
            actorDetail = null,
            reason = null,
            retroactive = false,
            occurredAt = at,
        )

    /** 상위집합 — 마감을 모르므로 상태로만 좁힌다(판정은 use case 가 다시 한다, 포트 KDoc). */
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
        val row = rows[applied.caseId.value]?.takeIf { it.account.value == account.value }
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

/**
 * test 가 옮기는 공고 마감(검토 r2 C2-N6) — 앞 판의 이중체는 마감이 늘 「지금 + 30분」이라 운반 층에서 마감 판정의
 * 어느 축도 잴 수 없었다. [at] 을 정하면 모든 공고의 마감이 그 값이고, [rowPresent] 를 끄면 공고 행이 없는 것(파기)과
 * 같다. [reset] 이 기본값으로 돌린다.
 */
class MovableNoticeDeadlines(
    private val clock: FixedClock,
) : NoticeDeadlineQueryPort {
    @Volatile
    var at: Instant? = null

    @Volatile
    var rowPresent: Boolean = true

    @Volatile
    private var explicit: Boolean = false

    /** 마감을 정한다 — `null` 이면 공고 행은 있고 마감이 미정이다. */
    fun set(deadline: Instant?) {
        at = deadline
        explicit = true
    }

    fun reset() {
        at = null
        explicit = false
        rowPresent = true
    }

    override fun deadlines(notices: Set<NoticeKey>): Map<NoticeKey, NoticeDeadline> =
        if (!rowPresent) {
            emptyMap()
        } else {
            val deadline = if (explicit) at else clock.now().plusSeconds(DEFAULT_AHEAD_SECONDS)
            notices.associateWith { NoticeDeadline(deadline) }
        }

    private companion object {
        const val DEFAULT_AHEAD_SECONDS = 1800L
    }
}

/**
 * HTTP test 조립의 입찰 건 워크플로 — 출하 조립(`BidCaseWiring`)과 같은 형태(controller 가 받는 단 하나의 협력자, 저장소를 controller 에
 * 직접 주지 않는다). `HttpTestSupport.kt` 의 빈이 이것을 부른다(그 파일의 크기 상한 — 두 slice 병합 뒤 500 줄을 넘었다).
 */
@Suppress("UNCHECKED_CAST")
fun testBidCaseWorkflow(
    store: InMemoryBidCaseStore,
    deadlines: MovableNoticeDeadlines,
    clock: FixedClock,
    notifications: InMemoryNotificationInbox = InMemoryNotificationInbox(),
): BidCaseWorkflow =
    BidCaseWorkflow(
        cases = store,
        deadlines = deadlines,
        snapshots = { _, takenAt -> fixedNoticeSnapshot(takenAt) },
        clock = clock,
        retentionPolicy =
            EVIDENCE_RETENTION_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<EvidenceRetentionPolicyData>,
        notifications = notifications,
    )
