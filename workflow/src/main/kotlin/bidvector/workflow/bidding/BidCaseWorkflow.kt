package bidvector.workflow.bidding

import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.BidDraft
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NextCheckpoint
import bidvector.bidding.NoticeKey
import bidvector.bidding.RejectionReason
import bidvector.bidding.SubmittedBidAmount
import bidvector.bidding.TransitionOutcome
import bidvector.bidding.applyCommand
import bidvector.bidding.asReal
import bidvector.bidding.isOverdue
import bidvector.bidding.isReconciliationCandidate
import bidvector.bidding.nextCheckpoint
import bidvector.bidding.tag
import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.strategy.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 건 하나를 화면이 쓰는 모양으로 — 상태에 더해 **서버가 판정한 둘**(다음 확인 시점 · 지난
 * 일정 경고)과 대사 후보 여부.
 *
 * 다음 확인 시점을 응답에 싣는 이유는 화면이 자기 시계로 다시 계산하지 않게 하기 위해서다
 * (계약 산출물 2 「서버가 한 시각 기준으로 판정한다」).
 */
data class BidCaseView(
    val case: BidCase,
    val nextCheckpoint: NextCheckpoint,
    val overdue: Boolean,
    val reconciliationCandidate: Boolean,
)

/**
 * 담기의 결과 — 새로 담았는가([created])를 버리지 않는다(계약 D-7B-3). 화면은 「담겼습니다」와
 * 「이미 담긴 건입니다」를 가르고, HTTP 는 201 과 200 으로 가른다.
 */
data class OpenedCase(
    val view: BidCaseView,
    val created: Boolean,
)

/** [BidCaseWorkflow.command] 의 결과 — 건 부재는 [TransitionOutcome] 밖의 사유다. */
sealed interface BidCommandResult {
    data class Processed(
        val outcome: TransitionOutcome,
    ) : BidCommandResult

    /** 없거나 **남의 건이다** — 둘을 구별하지 않는다. */
    data object CaseNotFound : BidCommandResult

    /** 그 사이에 다른 전이가 끼었다. */
    data object VersionConflict : BidCommandResult

    /**
     * 초안을 만들려는데 공고를 뜰 수 없다(없거나 이미 파기됐다). **스냅샷 없는 초안을 만들지
     * 않는다** — §6.1 의 acceptance 가 초안에 공고 스냅샷을 요구하고, 비워 두면 「그때 그
     * 금액을 정한 근거」가 빈 채로 남는다.
     */
    data object NoticeSnapshotUnavailable : BidCommandResult
}

/**
 * 입찰 건 use case(M7/7B, `BID-01`) — **모든 건 write 가 이 클래스를 지난다.**
 *
 * **주체를 인자로 받는다**(R-2, 계약 산출물 4). 전역 · 스레드 로컬에서 꺼내지 않으므로
 * 「주체 없이 부르는 경로」가 서명에 없고, 소유하지 않은 건의 조회 · 전이는 포트가 `null` ·
 * [TransitionWrite.CaseNotFound] 로 돌려준다 — 거부가 코드의 `if` 가 아니라 **질의의 필터**다.
 *
 * 증빙 보관 기한은 여기서 **정책으로 푼다**(R-5 3년) — 도메인이 날짜 산술의 기준일을 지어내지
 * 않는다. 기한은 **제출 시각의 KST 달력일**에서 센다(확인 시각이 아니다 — 소급 확인이면 확인이
 * 몇 달 뒤일 수 있고, 그러면 같은 투찰의 증빙이 언제 확인했느냐에 따라 다른 날 만료된다).
 *
 * **마감은 서버가 판정한다**(계약 D-7B-3 결정 ①) — 건을 읽거나 명령을 걸 때 [DeadlineSettlement] 를
 * 먼저 지난다. 그래서 마감 뒤의 「제출함」은 소급 기록이 된다.
 *
 * **결과 전이는 알림함에 되비친다**(M7/7C③-b · D-7C3B-7) — [notifications] 가 그 자리다. 기록은
 * 전이가 저장된 **뒤**이고 실패해도 전이를 되돌리지 않는다([reflect]).
 */
class BidCaseWorkflow(
    private val cases: BidCaseStorePort,
    private val deadlines: NoticeDeadlineQueryPort,
    private val snapshots: NoticeSnapshotQueryPort,
    private val clock: Clock,
    private val retentionPolicy: Resolution.Resolved<EvidenceRetentionPolicyData>,
    private val notifications: NotificationInboxPort,
) {
    private val settlement = DeadlineSettlement(cases, deadlines)

    /** 관심 담기 — 이미 있으면 그 건을 돌려준다(같은 공고에 건은 하나다). 그 갈래를 버리지 않는다. */
    fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
    ): OpenedCase {
        val now = clock.now()
        val noticeDeadline = deadlines.deadlines(setOf(notice))[notice]
        val result = cases.open(account, notice, kind, now, noticeDeadline?.at)
        val opened =
            when (result) {
                is OpenCaseResult.Opened -> result.case
                is OpenCaseResult.AlreadyOpen -> result.case
            }
        // 새로 담은 건도 판정을 지난다(검토 r2 V2-W1) — 이미 마감이 지난 공고를 담으면 응답이 곧 「마감 지남」이다.
        // 같은 요청 쌍(담기 → 읽기)이 두 답을 주지 않는다.
        val case = settlement.settle(account, opened, noticeDeadline, now)
        return OpenedCase(
            view(case, effectiveDeadline(case, noticeDeadline), now),
            created = result is OpenCaseResult.Opened,
        )
    }

    fun read(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCaseView? {
        val now = clock.now()
        val settled = settlement.load(account, caseId, now) ?: return null
        return view(settled.case, effectiveDeadline(settled.case, settled.noticeDeadline), now)
    }

    /** 내 입찰함 — [kind] 가 **필수**라 페이퍼와 실투찰이 한 목록에 들 수 없다. */
    fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCaseView> {
        val now = clock.now()
        // ① 판정 후보만 저장소가 좁혀 판정한다(계약 D-7B-4 결정 ⑧) — 목록 전건을 건마다 판정하지 않는다.
        val candidates = cases.settleCandidates(account, kind, DEADLINE_JUDGED_STATES, now)
        val candidateDeadlines = deadlines.deadlines(candidates.map { it.notice }.toSet())
        candidates.forEach { settlement.settle(account, it, candidateDeadlines[it.notice], now) }
        // ② 판정 **뒤에** 상태 탭을 DB 가 거른다 — 판정이 상태를 바꿨으므로 그 다음에 묻는다.
        val found = cases.list(account, kind, state)
        val byNotice = deadlines.deadlines(found.map { it.notice }.toSet())
        return found.map { view(it, effectiveDeadline(it, byNotice[it.notice]), now) }
    }

    fun timeline(
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry>? {
        settlement.load(account, caseId, clock.now()) ?: return null
        return cases.timeline(account, caseId)
    }

    /**
     * 초안 만들기 · 수정 — **스냅샷은 서버가 뜬다**(요청이 실어 오지 않는다, 위협 ③).
     *
     * 순서가 중요하다: 소유 확인(load)이 **먼저**다. 그래야 남의 건의 공고를 스냅샷으로 떠
     * 보는 일조차 일어나지 않는다(존재 여부가 타이밍으로도 새지 않는다).
     */
    fun draft(
        account: AccountId,
        caseId: BidCaseId,
        amount: SubmittedBidAmount,
        presentedAt: Instant,
        actor: BidActor,
    ): BidCommandResult {
        val now = clock.now()
        // 마감 판정이 먼저다 — 마감이 지난 건에 초안을 만들지 않는다(전이표가 거부한다).
        val case = settlement.load(account, caseId, now)?.case ?: return BidCommandResult.CaseNotFound
        val snapshot = snapshots.snapshot(case.notice, now)
        return snapshot
            ?.let { command(account, caseId, BidCommand.DraftBid(BidDraft(amount, presentedAt, it)), actor) }
            ?: BidCommandResult.NoticeSnapshotUnavailable
    }

    /**
     * 건에 명령을 건다 — 순서는 ① 소유 확인(포트의 질의) ② 전이표 ③ 저장(낙관적 잠금)이다.
     *
     * 거부는 **도메인 사유 그대로** 돌려준다([TransitionOutcome.Rejected]) — HTTP 층이 그것을
     * 상태 코드로 옮긴다. 저장 단계의 경합은 별 갈래다(전이표가 허락한 것이 늦게 실패한 것이라
     * 「금지 전이」와 섞이면 안 된다).
     */
    fun command(
        account: AccountId,
        caseId: BidCaseId,
        command: BidCommand,
        actor: BidActor,
    ): BidCommandResult {
        val now = clock.now()
        val case = settlement.load(account, caseId, now)?.case ?: return BidCommandResult.CaseNotFound
        val resolved = resolveRetention(command)
        val outcome = applyCommand(case.state, resolved, actor.kind, now)
        return when (outcome) {
            is TransitionOutcome.Rejected -> BidCommandResult.Processed(outcome)
            is TransitionOutcome.Accepted -> persist(account, case, resolved, outcome, actor, now)
        }
    }

    /** 받아들여진 전이를 저장한다 — 저장 단계의 경합은 전이표 거부와 **다른 갈래**다. */
    @Suppress("LongParameterList")
    private fun persist(
        account: AccountId,
        case: BidCase,
        command: BidCommand,
        accepted: TransitionOutcome.Accepted,
        actor: BidActor,
        now: Instant,
    ): BidCommandResult =
        when (writeTransition(cases, account, case, command, accepted, actor, now)) {
            TransitionWrite.Applied -> {
                reflect(account, case, accepted.next, now)
                BidCommandResult.Processed(accepted)
            }

            TransitionWrite.CaseNotFound -> BidCommandResult.CaseNotFound
            TransitionWrite.VersionConflict -> BidCommandResult.VersionConflict
        }

    /**
     * 저장된 전이를 알림함에 되비춘다(D-7C3B-7) — **전이가 커밋된 뒤**이고 결과를 보지 않는다.
     *
     * 왜 같은 트랜잭션이 아닌가: 알림 포트는 자기 연결·자기 트랜잭션을 연다(`JdbcNotificationInbox`).
     * 넣을 수 있더라도 그렇게 하지 않는다 — 알림 때문에 투찰 결과 기록이 실패하는 쪽이 더 나쁘고,
     * 그 선택이 D-7C3B-9 다. 잃은 사실은 포트가 내는 `Failed` 값으로 남고 **출하 조립이 그것을
     * 로그 표지로 적는다**(`NotificationInboxWiring`) — 이 층은 로거를 들지 않는다.
     *
     * **마감 판정은 이 자리를 지나지 않는다.** 시스템 판정은 [DeadlineSettlement] 가 저장 함수를
     * 직접 부르므로, 되비추는 것은 **운영자 명령의 결과**뿐이다.
     *
     * **실투찰만 되비춘다**([asReal]). 알림함의 중복 억제 키에 투찰 종류 축이 없어서(7C③ 의
     * `(계정, 종류, 공고)` PK), 같은 공고에 페이퍼와 실투찰 건이 함께 있으면 둘의 결과가 **한
     * 항목을 덮어쓴다** — 페이퍼는 가상 연습이므로 실제 결과를 가릴 수 없게 한다. 페이퍼 건의
     * 결과가 알림함에 서지 않는 것은 그 선택의 대가이고 알려진 제한이다.
     */
    private fun reflect(
        account: AccountId,
        case: BidCase,
        next: BidState,
        now: Instant,
    ) {
        case.asReal() ?: return
        val fact = reflectedNotificationFact(next) ?: return
        notifications.record(NotificationInboxRecord(account, case.notice.asNotificationNoticeId(), fact, now))
    }

    /**
     * 증빙이 있는 기록 명령에만 보관 기한을 싣는다 — 호출부가 넘긴 값을 쓰지 않고 **여기서
     * 덮어쓴다**(HTTP 요청에 그 자리가 없고, 있더라도 정책이 이긴다).
     */
    private fun resolveRetention(command: BidCommand): BidCommand =
        if (command is BidCommand.RecordBid && command.submission.evidence != null) {
            command.copy(evidenceRetainUntil = retainUntil(command.submission.submittedAt))
        } else if (command is BidCommand.RecordBid) {
            command.copy(evidenceRetainUntil = null)
        } else {
            command
        }

    private fun retainUntil(submittedAt: Instant): LocalDate =
        LocalDate
            .ofInstant(submittedAt, EVIDENCE_RETENTION_ZONE)
            .plus(retentionPolicy.value.retainFor)

    private fun view(
        case: BidCase,
        deadline: Instant?,
        now: Instant,
    ): BidCaseView {
        val checkpoint = nextCheckpoint(case.state, deadline, case.outcomeNoticeExpectedAt)
        return BidCaseView(
            case = case,
            nextCheckpoint = checkpoint,
            overdue = isOverdue(checkpoint, now),
            reconciliationCandidate = case.asReal()?.let(::isReconciliationCandidate) ?: false,
        )
    }
}

/**
 * HTTP 층이 거부 사유를 상태 코드로 옮길 때 쓰는 분류 — 어휘를 한 자리에 둔다.
 * 「표에 없다」와 「도착이 범위 밖이다」는 사용자에게 같은 일(그 전이는 안 된다)이고,
 * 「주체 종류가 아니다」는 다른 일이다.
 */
fun RejectionReason.isForbiddenTransition(): Boolean =
    this == RejectionReason.InvalidTransition || this == RejectionReason.TargetNotAllowed
