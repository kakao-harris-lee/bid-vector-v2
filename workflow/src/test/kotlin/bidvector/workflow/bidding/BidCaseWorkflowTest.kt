package bidvector.workflow.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BID_TRANSITIONS
import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.BidCommandTag
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.BidSubmission
import bidvector.bidding.CheckpointKind
import bidvector.bidding.EvidenceRef
import bidvector.bidding.NextCheckpoint
import bidvector.bidding.NoticeKey
import bidvector.bidding.RejectionReason
import bidvector.bidding.SubmittedBidAmount
import bidvector.bidding.TransitionEffect
import bidvector.bidding.TransitionKey
import bidvector.bidding.TransitionOutcome
import bidvector.bidding.tag
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * use case 축 — **소유 경계**(포트가 주체를 받는다) · 증빙 보관 기한(R-5) · 다음 확인 시점의
 * 응답 반영 · 낙관적 잠금.
 *
 * 실 DB 위의 소유 경계는 `adapters` 쪽 test 가 따로 잰다(여기는 fake 라 「포트가 주체를
 * 받는다」까지만 증명한다 — 그 둘은 다른 축이고 둘 다 필요하다).
 */
class BidCaseWorkflowTest {
    private val now = Instant.parse("2026-10-21T04:00:00Z")
    private val mine = AccountId(1L)
    private val other = AccountId(2L)
    private val notice = NoticeKey("R00TEST0000001", NoticeRound.of("000"))
    private val deadline = now.plusSeconds(1800)

    private fun policy() =
        Resolution.Resolved(
            EvidenceRetentionPolicyData(Period.ofYears(3)),
            PolicyVersion(EffectiveFrom.Initial, "test"),
        )

    private fun workflow(store: FakeBidCaseStore) =
        BidCaseWorkflow(
            cases = store,
            deadlines = { keys -> keys.associateWith { NoticeDeadline(deadline) } },
            // 스냅샷은 **서버가 뜬다** — 이 fake 가 그 자리를 메운다(요청이 실어 오지 않는다).
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
            retentionPolicy = policy(),
            // 되비춤 자체는 `BidCaseNotificationTest` 가 잰다 — 여기서는 전이가 알림 포트에
            // 기대지 않는다는 사실만 유지한다(무엇을 돌려주든 이 파일의 단언이 흔들리지 않는다).
            notifications = { NotificationInboxOutcome.Created },
        )

    private fun submission(evidence: EvidenceRef? = null) =
        BidSubmission(
            amount =
                SubmittedBidAmount(
                    won = 1_000_000L,
                    currency = Currency.KRW,
                    vatTreatment = VatTreatment.INCLUSIVE,
                    origin = BidAmountOrigin.OperatorEntered,
                ),
            submittedAt = now,
            drawNumbers = emptyList(),
            evidence = evidence,
            memo = null,
        )

    // --- 마감 판정 되돌림 도착표(D-7B-5 C3-M3) ----------------------------------

    @Test
    fun `되돌림 도착표의 키는 마감 판정을 세우는 상태 전수와 같고 도착은 전이표가 허락한 집합 안이다`() {
        REVOKE_TARGETS.keys shouldBe DEADLINE_JUDGED_STATES
        val revoke =
            requireNotNull(
                BID_TRANSITIONS[
                    TransitionKey(
                        BidStateTag.DEADLINE_PASSED_UNRECORDED,
                        BidCommandTag.REVOKE_DEADLINE_PASSED,
                    ),
                ],
            )
        revoke.allowedTargets.containsAll(REVOKE_TARGETS.values.map { it.tag }) shouldBe true
        // 의도한 항목 — 「나중에」는 리마인드 시각을 잃었으므로 관심으로 돌아간다(D-7B-4 결정 ⑤ 보충).
        REVOKE_TARGETS[BidStateTag.DEFERRED] shouldBe BidState.Watching
    }

    // --- 소유 경계 -------------------------------------------------------------

    @Test
    fun `남의 건은 읽히지 않는다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(other, notice, BidKind.REAL, BidState.Watching)
        workflow(store).read(mine, case.id) shouldBe null
    }

    @Test
    fun `남의 건에는 전이가 걸리지 않는다 — 건 부재와 같은 답이다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(other, notice, BidKind.REAL, BidState.Watching)
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.Skip(bidvector.bidding.SkipReason("아님")),
                BidActor.User(mine),
            )
        result shouldBe BidCommandResult.CaseNotFound
        // 남의 건의 상태가 그대로다 — 쓰기가 일어나지 않았다.
        store.stateOf(case.id) shouldBe BidState.Watching
    }

    @Test
    fun `남의 건의 타임라인도 보이지 않는다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(other, notice, BidKind.REAL, BidState.Watching)
        workflow(store).timeline(mine, case.id) shouldBe null
    }

    @Test
    fun `목록은 자기 계정의 그 종류만 낸다`() {
        val store = FakeBidCaseStore()
        store.seed(mine, notice, BidKind.REAL, BidState.Watching)
        store.seed(mine, NoticeKey("R26BK0000002", NoticeRound.of("000")), BidKind.PAPER, BidState.Watching)
        store.seed(other, NoticeKey("R26BK0000003", NoticeRound.of("000")), BidKind.REAL, BidState.Watching)

        val real = workflow(store).list(mine, BidKind.REAL, null)
        real.map { it.case.kind } shouldBe listOf(BidKind.REAL)
        real.single().case.notice shouldBe notice
    }

    // --- 증빙 보관 기한(R-5) ---------------------------------------------------

    @Test
    fun `증빙이 있으면 보관 기한이 제출일 기준 3년이다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.BidPlanned)
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.RecordBid(submission(EvidenceRef("doc-1")), evidenceRetainUntil = null),
                BidActor.User(mine),
            )
        result.shouldBeInstanceOf<BidCommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val effect = outcome.effect
        effect.shouldBeInstanceOf<TransitionEffect.RecordWritten>()
        // 제출 시각 2026-10-21T04:00Z = KST 2026-10-21 13:00 → 2029-10-21
        effect.record.evidenceRetainUntil shouldBe LocalDate.of(2029, 10, 21)
    }

    @Test
    fun `호출부가 보낸 보관 기한은 정책이 덮는다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.BidPlanned)
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.RecordBid(
                    submission(EvidenceRef("doc-1")),
                    evidenceRetainUntil = LocalDate.of(2026, 10, 22),
                ),
                BidActor.User(mine),
            )
        result.shouldBeInstanceOf<BidCommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val effect = outcome.effect
        effect.shouldBeInstanceOf<TransitionEffect.RecordWritten>()
        effect.record.evidenceRetainUntil shouldBe LocalDate.of(2029, 10, 21)
    }

    @Test
    fun `증빙이 없으면 보관 기한도 없다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.BidPlanned)
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.RecordBid(submission(), evidenceRetainUntil = LocalDate.of(2030, 1, 1)),
                BidActor.User(mine),
            )
        result.shouldBeInstanceOf<BidCommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        val effect = outcome.effect
        effect.shouldBeInstanceOf<TransitionEffect.RecordWritten>()
        effect.record.evidenceRetainUntil shouldBe null
    }

    // --- 응답이 서버 판정을 싣는다 ---------------------------------------------

    @Test
    fun `다음 확인 시점과 지난 일정 경고를 서버가 실어 보낸다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Watching)
        val view = workflow(store).read(mine, case.id)
        requireNotNull(view)
        view.nextCheckpoint shouldBe NextCheckpoint.At(deadline, CheckpointKind.DEADLINE)
        view.overdue shouldBe false
        view.reconciliationCandidate shouldBe false
    }

    @Test
    fun `대사 후보는 투찰 기록됨 실투찰 건에만 선다`() {
        val store = FakeBidCaseStore()
        val real = store.seed(mine, notice, BidKind.REAL, BidState.BidRecorded)
        val paper =
            store.seed(
                mine,
                NoticeKey("R26BK0000009", NoticeRound.of("000")),
                BidKind.PAPER,
                BidState.BidRecorded,
            )
        workflow(store).read(mine, real.id)!!.reconciliationCandidate shouldBe true
        workflow(store).read(mine, paper.id)!!.reconciliationCandidate shouldBe false
    }

    // --- 전이 거부와 경합 -------------------------------------------------------

    @Test
    fun `표에 없는 전이는 거부로 돌아온다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Won)
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.AwaitOpening(now),
                BidActor.User(mine),
            )
        result.shouldBeInstanceOf<BidCommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.InvalidTransition
        outcome.reason.isForbiddenTransition() shouldBe true
    }

    @Test
    fun `시스템 전용 전이는 사용자 주체로 거부된다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Watching)
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.ReceivePriceMenu(bidvector.bidding.PriceMenuRef("menu-1")),
                BidActor.User(mine),
            )
        result.shouldBeInstanceOf<BidCommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.ActorNotPermitted
    }

    @Test
    fun `읽기와 저장 사이에 다른 전이가 끼면 경합으로 떨어진다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Watching)
        // 이 use case 는 매번 신선하게 읽으므로, 경합은 **읽은 뒤 저장 전에** 끼어야 재현된다.
        store.interleaveBeforeApply = true
        val result =
            workflow(store).command(
                mine,
                case.id,
                BidCommand.Skip(bidvector.bidding.SkipReason("중복")),
                BidActor.User(mine),
            )
        result shouldBe BidCommandResult.VersionConflict
    }

    @Test
    fun `같은 공고에 두 번 담아도 건은 하나다`() {
        val store = FakeBidCaseStore()
        val first = workflow(store).open(mine, notice, BidKind.REAL)
        val second = workflow(store).open(mine, notice, BidKind.REAL)
        second.view.case.id shouldBe first.view.case.id
        first.created shouldBe true
        second.created shouldBe false
        store.countOf(mine) shouldBe 1
    }

    @Test
    fun `전이가 타임라인 한 줄을 남기고 주체 종류를 적는다`() {
        val store = FakeBidCaseStore()
        val case = store.seed(mine, notice, BidKind.REAL, BidState.Watching)
        workflow(store).command(
            mine,
            case.id,
            BidCommand.Skip(bidvector.bidding.SkipReason("면허 불일치")),
            BidActor.User(mine),
        )
        val entries = workflow(store).timeline(mine, case.id)
        requireNotNull(entries)
        entries.size shouldBe 1
        entries.single().toState shouldBe BidStateTag.SKIPPED
        entries.single().actorKind shouldBe ActorKind.USER
        entries.single().reason shouldBe "면허 불일치"
    }
}
