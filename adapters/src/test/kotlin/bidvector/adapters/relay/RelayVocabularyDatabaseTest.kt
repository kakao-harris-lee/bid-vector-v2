package bidvector.adapters.relay

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RuntimeEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

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
        stateOf(dataSource(), "dup-1") shouldBe "DELIVERED"
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
        stateOf(dataSource(), "suppressed-route-1") shouldBe "FAILED"
        harness.sender.sentKeys() shouldBe emptyList()
        inboxKeyCount(dataSource()) shouldBe 0
    }

    @Test
    fun `sender 가 거부하면 FAILED 가 되고 inbox 키는 생기지 않는다 — 키 소진 방지`() {
        insertPendingNotificationRow(dataSource(), "rejected-1")

        val harness = RelayHarness(dataSource(), sendOutcome = SendOutcome.REJECTED)
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.failed shouldBe 1
        stateOf(dataSource(), "rejected-1") shouldBe "FAILED"
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
        stateOf(dataSource(), "unknown-1") shouldBe "ISOLATED"
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
        stateOf(dataSource(), "their-orphan") shouldBe "CLAIMED"
        stateOf(dataSource(), "mine-1") shouldBe "DELIVERED"
    }

    /**
     * cr L-1 — **production 조립 자신**(`NotificationRelayRun`)을 그대로 돌린다. 앞 판은 그
     * 조립을 어느 test 도 거동으로 돌리지 않았고, 그 KDoc 이 스스로 예고한 실패(경계 인스턴스
     * 분리)는 컴파일·배선 모두 통과하고 **첫 질의에서만** 터지는 종류였다.
     *
     * 발송 축 셋이 자리지킴이라 `OPEN-STR-12` 로 던지는 것이 **정상 거동**이다. 중요한 것은
     * 던지기 **전에** claim 이 성공했다는 사실이다 — 그러려면 경계를 세 참여자가 공유해야
     * 하므로, 이 한 건이 경계 공유를 **전이적으로** 잠근다.
     */
    @Test
    fun `production 조립은 claim 까지 지나고 자리지킴 발송에서 던진다 — 경계 공유`() {
        insertPendingNotificationRow(dataSource(), "assembly-1")
        val run =
            NotificationRelayRun(
                dataSource = dataSource(),
                target = RelayTarget(RELAY_DB_OWNER, RELAY_DB_CHANNEL),
                environment = RuntimeEnvironment.Production,
                policy = relayNotificationPolicy(),
            )

        val thrown = shouldThrow<IllegalStateException> { run.relay(RELAY_DB_LIMIT) }

        thrown.message.orEmpty() shouldBe "배달 경로 구현이 없다 — OPEN-STR-12. relay 는 Live 환경에서만 이 자리에 닿는다"
        // 던지기 전에 claim 이 커밋됐다 = 경계를 세 참여자가 공유했다(T1 이 섰다).
        stateOf(dataSource(), "assembly-1") shouldBe "CLAIMED"
    }
}
