package bidvector.adapters.relay

import bidvector.adapters.event.outboxStateOf
import bidvector.adapters.notification.FakeTelegramServer
import bidvector.adapters.notification.TelegramScript
import bidvector.adapters.notification.channelSettings
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RuntimeEnvironment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * 어휘 해석표(D-6F10-12)의 네 갈래와 **production 조립 자신**을 실 DB 상태로 잰다(R1-L-3·cr L-1).
 *
 * 왜 따로 두는가: `RelayOutboxNotificationsTest` 는 fake port 의 **목록**으로 처분을 재고,
 * `RelayDatabaseTest` 는 계수 계약·T1/T2·kind·억제를 잰다. 여기서 재는 것은 「그 처분이 **DB
 * 행의 상태**로 어떻게 보이는가」다 — verifier 가 일회성 probe(V5a~d)로 확인한 것을 상설로
 * 올린 것이고, 고아 격리를 임대 밖으로 옮기는 변이가 기존 DB test 전부 초록이었던 사각을 닫는다.
 */
class RelayVocabularyDatabaseTest : PersistenceTestSupport() {
    @Test
    fun `이미 처리된 키는 발송 없이 DELIVERED 가 된다 — SkipDuplicate`() {
        insertPendingNotificationRow(dataSource(), "dup-1")
        seedInboxKey(dataSource(), "notification-dup-1")

        val harness = RelayHarness(dataSource())
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.skippedDuplicates shouldBe 1
        report.delivered shouldBe 0
        outboxStateOf(dataSource(), "dup-1") shouldBe "DELIVERED"
        harness.sender.sentKeys() shouldBe emptyList()
        // 키가 더 생기지 않는다 — 이미 있던 하나뿐이다.
        inboxKeyCount(dataSource()) shouldBe 1
    }

    @Test
    fun `채널이 비활성이면 route Suppressed 가 FAILED 가 된다`() {
        insertPendingNotificationRow(dataSource(), "suppressed-route-1")

        val harness = RelayHarness(dataSource(), channelEnabled = false)
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.failed shouldBe 1
        outboxStateOf(dataSource(), "suppressed-route-1") shouldBe "FAILED"
        harness.sender.sentKeys() shouldBe emptyList()
        inboxKeyCount(dataSource()) shouldBe 0
    }

    @Test
    fun `sender 가 거부하면 FAILED 가 되고 inbox 키는 생기지 않는다 — 키 소진 방지`() {
        insertPendingNotificationRow(dataSource(), "rejected-1")

        val harness = RelayHarness(dataSource(), sendOutcome = SendOutcome.REJECTED)
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.failed shouldBe 1
        outboxStateOf(dataSource(), "rejected-1") shouldBe "FAILED"
        harness.sender.sentKeys().size shouldBe 1
        inboxKeyCount(dataSource()) shouldBe 0
    }

    @Test
    fun `sender 결과가 모호하면 ISOLATED 가 된다 — 재시도 없음`() {
        insertPendingNotificationRow(dataSource(), "unknown-1")

        val harness = RelayHarness(dataSource(), sendOutcome = SendOutcome.UNKNOWN)
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.isolated shouldBe 1
        report.unknownPayload shouldBe 0
        outboxStateOf(dataSource(), "unknown-1") shouldBe "ISOLATED"
        inboxKeyCount(dataSource()) shouldBe 0
    }

    /**
     * R1-M-3 — **다른 종류의 고아는 건드리지 않는다.** 오늘 `StrategyUpdated` 소비자가 없어 그
     * 종류의 `CLAIMED` 는 생길 수 없지만, 소비자가 생기면 임대 키가 kind 별이라 두 relay 가
     * 동시에 돌고 알림 relay 가 살아 있는 전략 소비자의 in-flight 행을 격리한다 — 위협 ⑦ 의
     * kind 간 판이다. 고아 조회의 kind 필터를 지우는 변이가 이 test 에서만 RED 다.
     */
    @Test
    fun `다른 종류의 CLAIMED 고아는 격리되지 않는다`() {
        insertClaimedStrategyRow(dataSource(), "their-orphan")
        insertPendingNotificationRow(dataSource(), "mine-1")

        val report =
            RelayHarness(dataSource()).relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.orphansIsolated shouldBe 0
        report.delivered shouldBe 1
        outboxStateOf(dataSource(), "their-orphan") shouldBe "CLAIMED"
        outboxStateOf(dataSource(), "mine-1") shouldBe "DELIVERED"
    }

    /**
     * cr L-1 — **production 조립 자신**(`NotificationRelayRun`)을 그대로 돌린다. 앞 판은 그
     * 조립을 어느 test 도 거동으로 돌리지 않았고, 그 KDoc 이 스스로 예고한 실패(경계 인스턴스
     * 분리)는 컴파일·배선 모두 통과하고 **첫 질의에서만** 터지는 종류였다.
     *
     * **M9/STR12 로 단언이 바뀌었다.** 자리지킴 셋이 사라져 조립이 끝까지 간다 — claim → 렌더 →
     * 가짜 Telegram 서버 → `DELIVERED` + inbox 한 줄. 앞 판은 「던지기 **전에** claim 이 커밋됐다」
     * 로 경계 공유를 전이적으로 잠갔고, 지금은 **T1 과 T2 가 둘 다 섰다**는 사실이 같은 것을 더
     * 세게 잠근다(경계가 갈라지면 둘 중 하나가 실행 시점에 터진다).
     *
     * 공고 행이 없으므로 본문은 **사실을 주장하지 않는 축약 판**이다(설계 검토 D-6 결정) — 그
     * 갈래가 던지지도, 결과를 거부로 옮기지도 않음을 이 자리가 실 DB 위에서 함께 잰다.
     */
    @Test
    fun `production 조립이 가짜 서버로 끝까지 발송한다 — 경계 공유와 렌더 실패 처분`() {
        insertPendingNotificationRow(dataSource(), "assembly-1", noticeId = "ASM-001")

        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            val run =
                NotificationRelayRun(
                    dataSource = dataSource(),
                    target = RelayTarget(RELAY_DB_OWNER, RELAY_DB_CHANNEL),
                    environment = RuntimeEnvironment.Production,
                    policy = relayNotificationPolicy(),
                    channels = channelSettings(server.baseUrl),
                    clock = Clock.fixed(RELAY_DB_NOW, ZoneOffset.UTC),
                )

            val report = run.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

            report.claimed shouldBe 1
            report.delivered shouldBe 1
            // T1 과 T2 가 둘 다 같은 경계에서 섰다.
            outboxStateOf(dataSource(), "assembly-1") shouldBe "DELIVERED"
            inboxKeyCount(dataSource()) shouldBe 1
            server.received().single().form.getValue("text") shouldContain "저장된 공고 정보가 없습니다"
        }
    }
}
