package bidvector.app.wiring

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.AwardResult
import bidvector.workflow.notificationinbox.NotificationBidKind
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationGroupKey
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 잃은 알림 한 건의 **로그 표지**(M7/7C③ 의 조립 · M9/paper-bid 가 축 한 칸을 더했다).
 *
 * 왜 test 가 필요한가: 그 표지가 **무엇을 담고 무엇을 담지 않는가**가 둘 다 주장이다. 담는 쪽은
 * 계약 ⓑ 의 사유(「없으면 두 축의 실패가 로그에서 같은 줄이다」)이고, 담지 않는 쪽은 그 함수
 * KDoc 의 사유(계정 주체도 저장소 예외 메시지도 싣지 않는다 — 후자에는 행 값이 실릴 수 있다).
 * 두 주장이 모두 잠겨 있지 않았다(cr LOW — 이 slice 전에도 test 0).
 *
 * **이 class 가 받는 것은 그 둘 중 앞의 것뿐이다**(cr L-2). 「저장소 예외 메시지를 싣지 않는다」는
 * 이 함수의 **서명**이 진다 — 인자가 record 하나고 예외 인자가 없어 여기서는 **구조적으로
 * 불가능**하다. 그 주장이 실제로 깨질 자리는 **호출 모양**이다: `notificationInboxPort` 의
 * `logger.warn(inboxWriteFailedLine(record))` 가 `logger.warn(line, e)` 로 바뀌면 예외의
 * 메시지·행 값이 그 줄에 붙는다. 그 자리는 조립 빈이라 이 순수 함수 test 가 닿지 않고 오늘
 * test 0 이다 — 축을 또 더할 때 함께 본다.
 *
 * 순수 함수라 조립을 세우지 않고 값만 쓴다.
 */
internal class NotificationInboxWiringTest {
    @Test
    fun `잃은 알림 표지가 종류·공고·투찰 종류를 담는다`() {
        val line = inboxWriteFailedLine(record(NotificationBidKind.PAPER))

        line shouldContain "kind=AWARD_DECIDED"
        line shouldContain "notice=R26BK0000001/007"
        line shouldContain "bidKind=PAPER"
    }

    /**
     * **같은 공고·같은 종류의 두 축이 다른 줄이다** — 축이 빠지면 서면의 실패와 실투찰의 실패를
     * 로그에서 가를 수 없다(그것이 그 칸을 더한 사유다).
     */
    @Test
    fun `투찰 종류만 다른 두 기록은 다른 줄을 낸다`() {
        val paper = inboxWriteFailedLine(record(NotificationBidKind.PAPER))
        val real = inboxWriteFailedLine(record(NotificationBidKind.REAL))

        paper shouldNotBe real
    }

    /**
     * **음성 대조 — 계정 주체를 담지 않는다.** 담는 축만 재면 「전부 담는」 구현도 초록이고,
     * 그러면 그 KDoc 의 둘째 주장이 선언으로 내려앉는다.
     */
    @Test
    fun `잃은 알림 표지가 계정 주체를 담지 않는다`() {
        val line = inboxWriteFailedLine(record(NotificationBidKind.REAL))

        line shouldNotContain ACCOUNT.value.toString()
        // 하중은 **값** 쪽이 진다. 이름 쪽은 열거형 술어라 꼴을 넓혀 둔다 — 대소문자를 접으면
        // `AccountId(`·`Account=` 도 걸린다(cr L-3: 축어 `"account"` 는 그것들을 지나보냈다).
        line.lowercase() shouldNotContain "account"
    }

    private fun record(bidKind: NotificationBidKind): NotificationInboxRecord =
        NotificationInboxRecord(
            account = ACCOUNT,
            group = NotificationGroupKey(NOTICE, bidKind),
            fact = NotificationFact.AwardDecided(AwardResult.AWARDED),
            occurredAt = Instant.parse("2026-11-06T00:00:00Z"),
        )

    private companion object {
        /** 로그 줄에 나타나면 안 되는 값이라 **눈에 띄는 수**로 둔다(부분 문자열 오탐을 피한다). */
        val ACCOUNT = AccountId(987_654_321L)
        val NOTICE = NoticeId(NoticeNumber.of("R26BK0000001"), NoticeRound.of("007"))
    }
}
