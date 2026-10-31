package bidvector.workflow.bidding

import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.strategy.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 훑기가 보는 건 하나 — 저장소가 **읽어 온 사실**이고 판정은 여기 없다(`NoticeOpeningFacts` 와
 * 같은 규율: 어댑터는 「무엇을 읽었는가」만 나르고, 그 덕에 창 판정 전수가 DB 없이 돈다).
 *
 * 공고 축 둘([noticeRevision] · [noticeUpdatedAt])은 **canonical 공고 행의 값**이고 행이 없으면
 * `null` 이다(6B-3 파기 또는 미수집). 둘을 어댑터에서 「개정됐는가」 한 불리언으로 접지 않는
 * 이유는 그 판정이 [openedAt] 과의 비교를 포함하기 때문이다 — 접으면 그 규칙이 SQL 에 숨는다.
 */
data class NotificationSweepCase(
    val account: AccountId,
    val notice: NoticeKey,
    val state: BidStateTag,
    /** 유효 마감 — 공고 행이 있으면 그 값, 없으면 담을 때의 사본([effectiveDeadline] 과 같은 규칙). */
    val deadlineAt: Instant?,
    val documentDueAt: Instant?,
    /** 건을 담은 시각 — 공고 개정이 **담은 뒤에** 일어났는지 가르는 축이다. */
    val openedAt: Instant,
    val noticeRevision: Long?,
    val noticeUpdatedAt: Instant?,
)

/**
 * 훑기의 후보 포트 — **상위집합을 돌려줘도 된다**([BidCaseStorePort.settleCandidates] 와 같은
 * 규율). 판정은 [sweepNotificationsFor] 가 건마다 다시 하므로, 이 질의가 하는 일은 「전 계정의
 * 전 건」을 훑지 않도록 시간 축으로 좁히는 것뿐이다.
 *
 * **주체를 인자로 받지 않는 유일한 건 질의다.** 훑기는 한 계정의 요청이 아니라 체계의 일이라
 * 모집단이 전 계정이다 — 그래서 [BidCaseStorePort] 를 넓히지 않고 **별 포트**로 둔다(그 포트의
 * 「첫 인자가 주체」 규율은 요청 경로의 소유 경계이고, 그 자리에 계정 없는 메서드가 생기면
 * 규율이 선언으로 내려앉는다). 계정은 **행에서 실려 온다** — 이 경로에 계정을 지어낼 자리가 없다.
 */
fun interface NotificationSweepCandidatePort {
    fun candidates(
        now: Instant,
        horizon: Instant,
    ): List<NotificationSweepCase>
}

/**
 * 훑기 한 번의 계수. **거짓 항등식을 만들지 않는다** — 건 하나가 알림 0~2 개를 낳으므로
 * `casesRead` 는 아래 세 계수의 합이 아니다. 각 계수가 자기 모집단을 말한다.
 */
data class NotificationSweepReport(
    val casesRead: Int,
    val created: Int,
    val updated: Int,
    /** 기록이 실패한 수 — 훑기를 멈추지 않는다(D-7C3B-9). 이 칸이 그 사실의 표지다. */
    val failed: Int,
)

/**
 * 시간이 지나서 생기는 알림을 한 번 훑는다(M7/7C③-b · D-7C3B-8) — 마감 D-1 · 마감 D-0 ·
 * 서류 기한 D-2 · 공고 개정 넷이다.
 *
 * **상주 스케줄러가 아니다.** 이 저장소에는 상주 스케줄러가 없고(`OPEN-6F10-SCHEDULER`) 주기는
 * 외부 cron 이 진다 — 이 클래스는 한 번 돌고 끝나는 일회 러너의 알맹이다(보존 파기 `PurgeExpiredData`
 * 와 같은 자리). **읽기 경로에 얹지 않았다**: 마감 정산처럼 「읽을 때 판정」으로 두면 읽기가 쓰기를
 * 낳고, 무엇보다 **순환이다** — 알림함은 「입찰함을 보라」고 말하는 장치인데 입찰함을 먼저 봐야
 * 알림이 생긴다.
 *
 * 멱등하다 — 같은 날 두 번 돌면 둘째 실행은 같은 키를 갱신할 뿐 항목을 늘리지 않는다(NOTI-10 의
 * PK). 그래서 이 러너는 자기 임대도, 실행 상태 파일도 들지 않는다.
 */
class SweepNotifications(
    private val candidates: NotificationSweepCandidatePort,
    private val notifications: NotificationInboxPort,
    private val clock: Clock,
    val policy: NotificationWindowPolicyData,
) {
    fun run(): NotificationSweepReport {
        val now = clock.now()
        val cases = candidates.candidates(now, horizonOf(now))
        val outcomes = cases.flatMap { sweepNotificationsFor(it, now, policy) }.map(notifications::record)
        return NotificationSweepReport(
            casesRead = cases.size,
            created = outcomes.count { it == NotificationInboxOutcome.Created },
            updated = outcomes.count { it == NotificationInboxOutcome.Updated },
            failed = outcomes.count { it == NotificationInboxOutcome.Failed },
        )
    }

    /**
     * 질의의 상한 — 창의 가장 먼 날에 **하루를 더한다**. 창은 KST 달력일로 세는데 이 상한은
     * 순간(instant)이라, 더하지 않으면 그 달력일의 끝자락이 상한 밖으로 떨어진다. 상위집합이
     * 허용되므로 넉넉한 쪽이 맞다.
     */
    private fun horizonOf(now: Instant): Instant = now.plus(Duration.ofDays(policy.horizonDays + 1))
}

/**
 * 건 하나가 **지금** 낳는 알림 전부(순수). 0~2 개이고, 둘이 되는 조합은 시간 기반 하나 + 공고
 * 개정 하나다(마감 축과 서류 기한 축은 상태가 서로 배타라 함께 설 수 없다).
 */
fun sweepNotificationsFor(
    case: NotificationSweepCase,
    now: Instant,
    policy: NotificationWindowPolicyData,
): List<NotificationInboxRecord> {
    val notice = case.notice.asNotificationNoticeId()
    val timed =
        listOfNotNull(
            deadlineFactOf(case, now, policy),
            documentFactOf(case, now, policy),
        ).map { fact -> NotificationInboxRecord(case.account, notice, fact, calendarDayStart(now)) }
    val revised =
        noticeRevisedAt(case, now)?.let { changedAt ->
            // **사실의 시각은 개정 시각이다**(훑은 시각이 아니다) — 호출자의 시계가 정하는 것은
            // 「언제 일어났나」이고, 공고 개정은 훑기보다 먼저 일어났다.
            NotificationInboxRecord(case.account, notice, NotificationFact.NoticeRevised, changedAt)
        }
    return timed + listOfNotNull(revised)
}

/**
 * 마감 축 — **투찰 미기록인 건만**이다. 그 모집단을 손으로 적지 않고 [DEADLINE_JUDGED_STATES]
 * (전이표에서 시스템의 마감 판정이 열린 출발 상태)를 쓴다: 「아직 투찰하지 않았다」와 「마감이
 * 지나면 마감 지남으로 간다」는 **같은 집합**이고, 표가 바뀌면 이 술어도 함께 바뀐다.
 *
 * 이미 지난 마감은 알리지 않는다(`isAfter(now)`) — 그 건은 마감 정산이 「마감 지남」으로 옮긴다.
 */
private fun deadlineFactOf(
    case: NotificationSweepCase,
    now: Instant,
    policy: NotificationWindowPolicyData,
): NotificationFact? {
    val unrecorded = case.state in DEADLINE_JUDGED_STATES
    // 이미 지난 마감은 알리지 않는다 — 그 건은 마감 정산이 「마감 지남」으로 옮긴다.
    val days = case.deadlineAt?.takeIf { unrecorded && it.isAfter(now) }?.let { daysBefore(now, it) }
    return when {
        days == null -> null
        policy.bidDeadlineToday.contains(days) -> NotificationFact.BidDeadlineToday
        policy.bidDeadlineImminent.contains(days) -> NotificationFact.BidDeadlineImminent
        else -> null
    }
}

/**
 * 서류 기한 축 — 적격심사 중인 건만이다. 그 상태만이 서류 기한을 **타입으로** 싣는다
 * (`BidState.UnderQualificationReview`), 그래서 다른 상태에서 이 값을 읽는 조합이 없다.
 */
private fun documentFactOf(
    case: NotificationSweepCase,
    now: Instant,
    policy: NotificationWindowPolicyData,
): NotificationFact? {
    val reviewing = case.state == BidStateTag.UNDER_QUALIFICATION_REVIEW
    val days = case.documentDueAt?.takeIf { reviewing && it.isAfter(now) }?.let { daysBefore(now, it) }
    return NotificationFact.DocumentDeadlineImminent.takeIf { days != null && policy.documentDeadline.contains(days) }
}

/**
 * 공고 개정 축(COL-01) — 개정 시각을 돌려주고, 알릴 것이 없으면 `null`.
 *
 * 조건 넷이 **전부** 필요하다: ① canonical 공고 행이 있고 ② 개정이 첫 적재 이후이며
 * ③ 그 개정이 **건을 담은 뒤**에 일어났고(담기 전의 개정은 담을 때 이미 본 값이다)
 * ④ 유효 마감이 아직 지나지 않았다(지난 공고의 개정은 할 일을 바꾸지 않는다).
 *
 * **무엇이 바뀌었는지는 말하지 않는다** — 우리가 아는 것은 canonical 행의 개정 번호가 올랐다는
 * 사실뿐이고, 이전 값은 `notice_audit` 에 남는다. 관련성은 「이 계정이 그 건을 담았다」가 진다
 * (7C③ 이 보류한 사유가 그 관련성의 부재였고, 7B 머지로 `bid_case` 가 그 자리를 채웠다).
 */
private fun noticeRevisedAt(
    case: NotificationSweepCase,
    now: Instant,
): Instant? {
    val everRevised = (case.noticeRevision ?: FIRST_NOTICE_REVISION) > FIRST_NOTICE_REVISION
    val stillOpen = case.deadlineAt?.isAfter(now) == true
    // 담기 **전**의 개정은 담을 때 이미 본 값이다 — 새 소식이 아니다.
    return case.noticeUpdatedAt?.takeIf { everRevised && stillOpen && it.isAfter(case.openedAt) }
}

/**
 * 시간 기반 사실의 시각 — **그 판정이 선 KST 달력일의 시작**이다(v L-4 · CR L-2).
 *
 * 훑은 시각(`now`)을 쓰지 않는 이유: 창은 달력일로 세므로 같은 날 두 번 돌아도 **같은 판정**인데,
 * `now` 를 실으면 재기록마다 `occurred_at` 이 움직여 그 항목이 목록 맨 위로 다시 올라온다(목록은
 * 그 열의 역순이다). 운영자에게는 「새 소식이 왔다」로 보이지만 바뀐 것이 없다.
 *
 * 마감 시각 자체를 쓰지 않는 이유: 그것은 **미래**라 사실의 시각이 아니고, 역순 목록에서 아직
 * 일어나지 않은 일이 이미 일어난 일 위에 선다.
 */
private fun calendarDayStart(now: Instant): Instant =
    LocalDate.ofInstant(now, NOTIFICATION_CALENDAR_ZONE).atStartOfDay(NOTIFICATION_CALENDAR_ZONE).toInstant()

/** 며칠 **뒤**인가 — KST 달력일 차이다(0 = 오늘, 1 = 내일). */
private fun daysBefore(
    now: Instant,
    at: Instant,
): Long =
    LocalDate.ofInstant(at, NOTIFICATION_CALENDAR_ZONE).toEpochDay() -
        LocalDate.ofInstant(now, NOTIFICATION_CALENDAR_ZONE).toEpochDay()

/** 첫 적재 때의 개정 번호(V1 `notice.revision` 의 기본값) — 이 값이면 아직 개정된 적이 없다. */
private const val FIRST_NOTICE_REVISION = 1L
