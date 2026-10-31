package bidvector.app.notificationinbox

import bidvector.app.collection.CollectionLog
import bidvector.app.wiring.RecordedExitCodes
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.DaysBeforeWindow
import bidvector.workflow.bidding.NotificationSweepCandidatePort
import bidvector.workflow.bidding.NotificationSweepCase
import bidvector.workflow.bidding.NotificationWindowPolicyData
import bidvector.workflow.bidding.SweepNotifications
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.sql.SQLException
import java.time.Instant

/**
 * 훑기 러너의 **출구** — 종료 코드와 로그 줄. 창 판정·질의는 각자의 test 가 재고, 여기서 재는
 * 것은 「cron 이 무엇을 받는가」와 「운영자가 무엇을 읽는가」다.
 *
 * 종료 자리는 [RecordedExitCodes] 대역이다 — production 은 `exitProcess` 라 그대로 쓰면 test
 * JVM 이 죽고 Gradle 이 그것을 초록으로 보고한다(그 함정의 사유는 그 대역의 KDoc).
 */
class NotificationSweepRunnerTest {
    private val now = Instant.parse("2026-10-31T06:00:00Z")
    private val policy =
        NotificationWindowPolicyData(
            bidDeadlineImminent = DaysBeforeWindow(1, 1),
            bidDeadlineToday = DaysBeforeWindow(0, 0),
            documentDeadline = DaysBeforeWindow(0, 2),
        )

    private class Lines : CollectionLog {
        val written = mutableListOf<String>()

        override fun write(line: String) {
            written += line
        }
    }

    private fun runner(
        candidates: NotificationSweepCandidatePort,
        inbox: NotificationInboxPort,
        exits: RecordedExitCodes,
        lines: Lines,
    ) = NotificationSweepRunner(SweepNotifications(candidates, inbox, { now }, policy), lines, exits)

    @Test
    fun `적을 것이 없어도 0 으로 끝난다 — 대상 0 은 실패가 아니다`() {
        val exits = RecordedExitCodes()
        val lines = Lines()

        runner({ _, _ -> emptyList() }, { NotificationInboxOutcome.Created }, exits, lines)
            .run(DefaultApplicationArguments())

        exits.recorded() shouldBe listOf(NotificationSweepExitCode.COMPLETE.value)
        lines.written.last() shouldContain "casesRead=0"
        lines.written.last() shouldContain "exit=COMPLETE(0)"
    }

    /**
     * **기록 실패가 있으면 cron 이 0 을 받지 않는다**(D-7C3B-9). 훑기는 끝까지 돌지만 그 run 은
     * 알림을 **잃은** run 이다 — 0 으로 끝내면 그 사실이 운영 쪽에서 보이지 않는다.
     */
    @Test
    fun `기록이 하나라도 실패하면 미완으로 끝난다`() {
        val exits = RecordedExitCodes()
        val lines = Lines()

        runner(
            { _, _ -> listOf(sweepCaseDueTomorrow(now)) },
            { NotificationInboxOutcome.Failed },
            exits,
            lines,
        ).run(DefaultApplicationArguments())

        exits.recorded() shouldBe listOf(NotificationSweepExitCode.INCOMPLETE.value)
        lines.written.last() shouldContain "failed=1"
    }

    /** 예외는 Spring 에 넘기지 않고 **종료 코드로** 옮긴다 — 원인은 정제된 코드만 싣는다. */
    @Test
    fun `질의가 터지면 정제된 원인 코드와 함께 실패로 끝난다`() {
        val exits = RecordedExitCodes()
        val lines = Lines()
        val leaky = SQLException("ERROR: relation \"bid_case\" ... 담당자 홍길동 010-0000-0000")

        runner({ _, _ -> throw leaky }, { NotificationInboxOutcome.Created }, exits, lines)
            .run(DefaultApplicationArguments())

        exits.recorded() shouldBe listOf(NotificationSweepExitCode.FAILED.value)
        val failure = lines.written.last()
        failure shouldContain "exit=FAILED(1)"
        // **원 예외 메시지를 잇지 않는다** — 저장소 예외에는 행 값이 실릴 수 있다.
        failure shouldNotContain "홍길동"
        failure shouldNotContain "010-0000-0000"
    }

    /** 시작 줄은 **창 폭**을 적는다 — 어떤 정책으로 돈 run 인지가 로그에서 갈린다. */
    @Test
    fun `시작 줄이 창 셋을 적고 계정 주체는 어디에도 없다`() {
        val exits = RecordedExitCodes()
        val lines = Lines()

        runner(
            { _, _ -> listOf(sweepCaseDueTomorrow(now)) },
            { NotificationInboxOutcome.Created },
            exits,
            lines,
        ).run(DefaultApplicationArguments())

        lines.written.first() shouldContain "bidDeadlineImminent=1..1"
        lines.written.first() shouldContain "documentDeadline=0..2"
        // 주체 라벨을 만드는 자리는 저장소에 하나다 — 러너가 같은 접두를 적으면 그 등식이 깨진다.
        lines.written.forEach { it shouldNotContain "account:" }
    }
}

/** 창 한가운데의 건 하나 — 마감이 KST 로 내일이고 아직 투찰을 기록하지 않았다. */
private fun sweepCaseDueTomorrow(now: Instant): NotificationSweepCase =
    NotificationSweepCase(
        account = AccountId(1L),
        notice = NoticeKey("R7C3B-RUNNER-1", NoticeRound.of("000")),
        state = BidStateTag.WATCHING,
        deadlineAt = now.plusSeconds(86_400),
        documentDueAt = null,
        openedAt = now.minusSeconds(86_400),
        noticeRevision = null,
        noticeUpdatedAt = null,
    )
