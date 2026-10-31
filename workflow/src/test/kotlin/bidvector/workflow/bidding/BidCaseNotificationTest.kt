package bidvector.workflow.bidding

import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidOutcome
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.SkipReason
import bidvector.bidding.TransitionReason
import bidvector.bidding.tag
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.Period

/**
 * 운영자 기록의 **되비춤**(M7/7C③-b · D-7C3B-7) — 사상의 전수와, 그 사상이 전이 저장 **뒤에만**
 * 일어난다는 사실.
 *
 * 실 DB 위의 중복 억제·경합은 `adapters` 쪽 test 가 따로 잰다(여기는 fake 라 「무엇을 어떤
 * 사실로 부르는가」까지다 — 둘은 다른 축이고 둘 다 필요하다).
 */
class BidCaseNotificationTest {
    private val now = Instant.parse("2026-10-31T04:00:00Z")
    private val mine = AccountId(1L)
    private val notice = NoticeKey("R00TEST0000001", NoticeRound.of("000"))
    private val deadline = now.plusSeconds(1800)

    private fun workflow(
        store: FakeBidCaseStore,
        inbox: NotificationInboxPort,
    ) = BidCaseWorkflow(
        cases = store,
        deadlines = { keys -> keys.associateWith { NoticeDeadline(deadline) } },
        snapshots = { _, takenAt ->
            bidvector.bidding.NoticeSnapshot(
                noticeRevisionNumber = 1L,
                takenAt = takenAt,
                deadlineAt = deadline,
                floorRate = null,
                baseAmount = null,
            )
        },
        clock = { now },
        retentionPolicy =
            Resolution.Resolved(
                EvidenceRetentionPolicyData(Period.ofYears(3)),
                PolicyVersion(EffectiveFrom.Initial, "test"),
            ),
        notifications = inbox,
    )

    // --- 사상의 전수 -----------------------------------------------------------

    /**
     * **상태 전수**를 돌아 사실을 내는 상태 집합을 등식으로 고정한다. 상태를 더하면 `when` 이
     * 컴파일을 막고, 사상을 넓히면 이 등식이 붉다 — 두 방향이 모두 닫힌다.
     */
    @Test
    fun `되비추는 상태는 개찰 둘과 확정 둘뿐이다`() {
        val reflected =
            ALL_STATES
                .filter { reflectedNotificationFact(it) != null }
                .map { it.tag }
                .toSet()

        reflected shouldBe
            setOf(
                BidStateTag.OPENED_PROVISIONAL_FIRST,
                BidStateTag.OPENED_OUT_OF_RANK,
                BidStateTag.WON,
                BidStateTag.LOST,
            )
    }

    @Test
    fun `개찰과 확정의 detail 은 상태가 정한다`() {
        reflectedNotificationFact(BidState.OpenedProvisionalFirst) shouldBe
            NotificationFact.OpeningCompleted(provisionallyFirst = true)
        reflectedNotificationFact(BidState.OpenedOutOfRank) shouldBe
            NotificationFact.OpeningCompleted(provisionallyFirst = false)
        reflectedNotificationFact(BidState.Won) shouldBe NotificationFact.AwardDecided(awarded = true)
        reflectedNotificationFact(BidState.Lost) shouldBe NotificationFact.AwardDecided(awarded = false)
    }

    /**
     * **유찰은 기록하지 않는다**(D-7C3B-7 ⒝) — `NOT_AWARDED`(패찰)로 접으면 「내가 졌다」와
     * 「아무도 못 땄다」가 한 값이 된다. 어휘 확장은 CHECK 마이그레이션이라 별도 결정이다.
     */
    @Test
    fun `유찰은 패찰로 접히지 않고 알림이 서지 않는다`() {
        reflectedNotificationFact(BidState.FailedOrRebid) shouldBe null
    }

    // --- 전이 ↔ 기록 -----------------------------------------------------------

    @Test
    fun `개찰 결과를 기록하면 그 사실이 내 알림함에 적힌다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.AwaitingOpening(now.plusSeconds(60)))

        workflow(store, inbox).command(mine, case.id, BidCommand.RecordOpeningFirst, BidActor.User(mine))

        inbox.records.size shouldBe 1
        val record = inbox.records.single()
        record.account.value shouldBe mine.value
        record.notice.number shouldBe NoticeNumber.of(notice.noticeNumber)
        record.notice.round shouldBe notice.noticeRound
        record.fact shouldBe NotificationFact.OpeningCompleted(provisionallyFirst = true)
        record.occurredAt shouldBe now
    }

    /** 정정은 **같은 키의 detail 을 바꾼다** — 두 항목이 되지 않는다(키가 종류 + 공고라서). */
    @Test
    fun `낙찰을 패찰로 정정하면 같은 키에 바뀐 detail 이 적힌다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Won)

        workflow(store, inbox).command(
            mine,
            case.id,
            BidCommand.CorrectOutcome(BidOutcome.LOST, TransitionReason("개찰 결과 정정")),
            BidActor.User(mine),
        )

        val record = inbox.records.single()
        record.fact shouldBe NotificationFact.AwardDecided(awarded = false)
        // 같은 항목이라는 것은 키가 말한다 — 종류와 공고가 앞 기록과 같다.
        record.fact.kind shouldBe NotificationFact.AwardDecided(awarded = true).kind
    }

    /**
     * **페이퍼는 되비추지 않는다** — 알림 키에 투찰 종류 축이 없어서, 같은 공고의 페이퍼 건과
     * 실투찰 건이 한 항목을 덮어쓴다. 가상 연습이 실제 결과를 가리지 않게 한다.
     */
    @Test
    fun `페이퍼 건의 결과는 알림함에 서지 않는다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        val case = store.seed(mine, notice, BidKind.PAPER, BidState.AwaitingOpening(now.plusSeconds(60)))

        val result = workflow(store, inbox).command(mine, case.id, BidCommand.RecordOpeningFirst, BidActor.User(mine))

        result.shouldBeProcessed()
        store.stateOf(case.id) shouldBe BidState.OpenedProvisionalFirst
        inbox.records.shouldBeEmpty()
    }

    @Test
    fun `전이표가 거부한 명령은 알림을 남기지 않는다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        // 관심 상태에서 개찰 결과를 기록할 수 없다(전이표).
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Watching)

        workflow(store, inbox).command(mine, case.id, BidCommand.RecordOpeningFirst, BidActor.User(mine))

        inbox.records.shouldBeEmpty()
    }

    @Test
    fun `남의 건에 건 명령은 알림을 남기지 않는다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        val case = store.seed(AccountId(2L), notice, BidKind.REAL, BidState.AwaitingOpening(now.plusSeconds(60)))

        workflow(store, inbox).command(mine, case.id, BidCommand.RecordOpeningFirst, BidActor.User(mine))

        inbox.records.shouldBeEmpty()
    }

    @Test
    fun `저장 경합으로 진 전이는 알림을 남기지 않는다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.AwaitingOpening(now.plusSeconds(60)))
        store.interleaveBeforeApply = true

        val result = workflow(store, inbox).command(mine, case.id, BidCommand.RecordOpeningFirst, BidActor.User(mine))

        result shouldBe BidCommandResult.VersionConflict
        inbox.records.shouldBeEmpty()
    }

    /**
     * **알림 기록 실패가 원천을 되돌리지 않는다**(D-7C3B-9) — 전이는 남고 응답도 성공이다.
     * 잃은 사실은 포트의 값으로 남고 출하 조립이 그것을 로그 표지로 적는다.
     */
    @Test
    fun `알림 기록이 실패해도 전이는 남는다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox(outcome = NotificationInboxOutcome.Failed)
        val case = store.seed(mine, notice, BidKind.REAL, BidState.AwaitingOpening(now.plusSeconds(60)))

        val result = workflow(store, inbox).command(mine, case.id, BidCommand.RecordOpeningFirst, BidActor.User(mine))

        result.shouldBeProcessed()
        store.stateOf(case.id) shouldBe BidState.OpenedProvisionalFirst
        inbox.records.size shouldBe 1
    }

    /**
     * **마감 판정은 되비추지 않는다** — 시스템 판정은 저장 함수를 직접 부르고 이 경로를 지나지
     * 않는다. 「마감 지남」은 알림 종류가 아니고(마감 D-1/D-0 은 훑기의 종류다) 운영자 명령도 아니다.
     */
    @Test
    fun `마감 판정이 상태를 옮겨도 알림은 서지 않는다`() {
        val store = FakeBidCaseStore()
        val inbox = RecordingInbox()
        val passed = now.minusSeconds(60)
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Watching, deadlineAt = passed)

        val workflow =
            BidCaseWorkflow(
                cases = store,
                deadlines = { keys -> keys.associateWith { NoticeDeadline(passed) } },
                snapshots = { _, _ -> null },
                clock = { now },
                retentionPolicy =
                    Resolution.Resolved(
                        EvidenceRetentionPolicyData(Period.ofYears(3)),
                        PolicyVersion(EffectiveFrom.Initial, "test"),
                    ),
                notifications = inbox,
            )
        workflow.read(mine, case.id)

        store.stateOf(case.id) shouldBe BidState.DeadlinePassedUnrecorded
        inbox.records.shouldBeEmpty()
    }

    private fun BidCommandResult.shouldBeProcessed() {
        (this is BidCommandResult.Processed) shouldBe true
    }

    /** 포트의 이중체 — 부른 기록을 그대로 모은다(mock framework 를 쓰지 않는다). */
    private class RecordingInbox(
        private val outcome: NotificationInboxOutcome = NotificationInboxOutcome.Created,
    ) : NotificationInboxPort {
        val records = mutableListOf<NotificationInboxRecord>()

        override fun record(record: NotificationInboxRecord): NotificationInboxOutcome {
            records += record
            return outcome
        }
    }

    private companion object {
        /**
         * 상태 **전수**. 꼬리표 전수와 같은 크기라는 것을 함께 단언해 「새 상태를 여기 빠뜨리는」
         * 길을 막는다 — 빠뜨리면 사상 등식이 모집단을 덜 보고도 초록일 수 있다.
         */
        val ALL_STATES: List<BidState> =
            listOf(
                BidState.Watching,
                BidState.Deferred(Instant.parse("2026-11-01T00:00:00Z")),
                BidState.PriceReceived,
                BidState.BidPlanned,
                BidState.BidRecorded,
                BidState.AwaitingOpening(Instant.parse("2026-11-02T00:00:00Z")),
                BidState.OpenedProvisionalFirst,
                BidState.OpenedOutOfRank,
                BidState.UnderQualificationReview(Instant.parse("2026-11-03T00:00:00Z")),
                BidState.Won,
                BidState.Lost,
                BidState.FailedOrRebid,
                BidState.Skipped(SkipReason("다른 건에 집중")),
                BidState.DeadlinePassedUnrecorded,
            )
    }

    @Test
    fun `상태 전수 목록이 꼬리표 전수를 덮는다`() {
        ALL_STATES.map { it.tag }.toSet() shouldBe BidStateTag.entries.toSet()
    }
}
