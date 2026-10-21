package bidvector.workflow.bidding

import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.NextCheckpoint
import bidvector.bidding.NoticeKey
import bidvector.bidding.RejectionReason
import bidvector.bidding.TransitionOutcome
import bidvector.bidding.applyCommand
import bidvector.bidding.asReal
import bidvector.bidding.isOverdue
import bidvector.bidding.isReconciliationCandidate
import bidvector.bidding.nextCheckpoint
import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.AccountId
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

/** [BidCaseWorkflow.command] 의 결과 — 건 부재는 [TransitionOutcome] 밖의 사유다. */
sealed interface BidCommandResult {
    data class Processed(
        val outcome: TransitionOutcome,
    ) : BidCommandResult

    /** 없거나 **남의 건이다** — 둘을 구별하지 않는다. */
    data object CaseNotFound : BidCommandResult

    /** 그 사이에 다른 전이가 끼었다. */
    data object VersionConflict : BidCommandResult
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
 */
class BidCaseWorkflow(
    private val cases: BidCaseStorePort,
    private val deadlines: NoticeDeadlineQueryPort,
    private val clock: Clock,
    private val retentionPolicy: Resolution.Resolved<EvidenceRetentionPolicyData>,
) {
    /** 관심 담기 — 이미 있으면 그 건을 돌려준다(같은 공고에 건은 하나다). */
    fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
    ): BidCaseView {
        val now = clock.now()
        val result = cases.open(account, notice, kind, now)
        val case =
            when (result) {
                is OpenCaseResult.Opened -> result.case
                is OpenCaseResult.AlreadyOpen -> result.case
            }
        return view(case, deadlineOf(case), now)
    }

    fun read(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCaseView? {
        val case = cases.load(account, caseId) ?: return null
        return view(case, deadlineOf(case), clock.now())
    }

    /** 내 입찰함 — [kind] 가 **필수**라 페이퍼와 실투찰이 한 목록에 들 수 없다. */
    fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCaseView> {
        val now = clock.now()
        val found = cases.list(account, kind, state)
        val byNotice = deadlines.deadlines(found.map { it.notice }.toSet())
        return found.map { view(it, byNotice[it.notice], now) }
    }

    fun timeline(
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry>? {
        cases.load(account, caseId) ?: return null
        return cases.timeline(account, caseId)
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
        val case = cases.load(account, caseId) ?: return BidCommandResult.CaseNotFound
        val now = clock.now()
        val resolved = resolveRetention(command)
        val outcome = applyCommand(case.state, resolved, actor.kind, now)
        if (outcome is TransitionOutcome.Rejected) return BidCommandResult.Processed(outcome)

        val accepted = outcome as TransitionOutcome.Accepted
        val applied =
            AppliedTransition(
                caseId = case.id,
                expectedVersion = case.caseVersion,
                command = resolved.tag,
                fromState = case.state,
                next = accepted.next,
                effect = accepted.effect,
                retroactive = accepted.retroactive,
                reason = accepted.reason,
                actorKind = actor.kind,
                actorDetail = (actor as? BidActor.System)?.detail,
                occurredAt = now,
            )
        return when (cases.applyTransition(account, applied)) {
            TransitionWrite.Applied -> BidCommandResult.Processed(accepted)
            TransitionWrite.CaseNotFound -> BidCommandResult.CaseNotFound
            TransitionWrite.VersionConflict -> BidCommandResult.VersionConflict
        }
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

    private fun deadlineOf(case: BidCase): Instant? = deadlines.deadlines(setOf(case.notice))[case.notice]

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
