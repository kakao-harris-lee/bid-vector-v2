package bidvector.adapters.relay

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelaySkipReason
import bidvector.workflow.notification.RuntimeEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 실 DB 위의 production relay — `workflow` 쪽 fake port test 가 잴 수 없는 넷을 잰다:
 * ① 종단 전이의 **갱신 계수 계약**(D-6F10-2) ② T1/T2 분리가 크래시에서 남기는 **상태**
 * (D-6F10-3) ③ 다른 종류 행의 **불변**(D-6F10-13) ④ 환경 억제에서 상태 분포 **불변**.
 *
 * 발송 축 셋만 fake 다([RelayHarness]) — outbox·inbox·lease·커밋 경계·use case 는 production.
 */
class RelayDatabaseTest : PersistenceTestSupport() {
    @Test
    fun `행을 집은 뒤 다른 상태로 바뀌면 종단 전이가 예외다 — 갱신 계수 계약`() {
        insertPendingNotificationRow(dataSource(), "count-1")
        // T2(네 번째 경계 호출) 직전에 행을 DELIVERED 로 밀어 둔다 — `WHERE state = 'CLAIMED'`
        // 가 거부해 영향 행이 0 이 되는 유일한 자리다. 앞 판은 그 0 을 버려 전이 실패가
        // 조용한 no-op 였다.
        val harness =
            RelayHarness(
                dataSource(),
                transactionsFor = { boundary ->
                    InterferingTransactions(ConsumerTransactions(boundary), atCall = T2_CALL_INDEX) {
                        forceOutboxState(dataSource(), "count-1", "DELIVERED")
                    }
                },
            )

        shouldThrow<IllegalStateException> { harness.relay.relay(RELAY_DB_LIMIT) }
    }

    /**
     * **발송 뒤·종단 전 크래시는 행을 `CLAIMED` 에 남긴다** — `PENDING` 이 아니다. T1 이 이미
     * 커밋했기 때문이고, 그것이 at-most-once 의 성립 근거다(되돌아가면 다음 run 이 **다시**
     * 발송한다). 발송은 이미 한 번 일어났다는 것도 함께 잰다.
     */
    @Test
    fun `발송 뒤 종단 전이 전에 죽으면 행은 CLAIMED 로 남는다 — T1·T2 분리`() {
        insertPendingNotificationRow(dataSource(), "crash-1")
        // R1-L-4 — 주입을 **사건**(발송 발생)에 건다. 순번에 걸면 경계 호출 수를 바꾸는 변이에서
        // 주입 자체가 사라져 아래 상태 단언이 돌지 않는다.
        lateinit var harness: RelayHarness
        harness =
            RelayHarness(
                dataSource(),
                transactionsFor = { boundary ->
                    CrashAfterDispatch(ConsumerTransactions(boundary)) { harness.sender.sentKeys().isNotEmpty() }
                },
            )

        shouldThrow<RelayWorkerDied> { harness.relay.relay(RELAY_DB_LIMIT) }

        harness.sender.sentKeys() shouldBe listOf("notification-crash-1")
        outboxStateCounts(dataSource()) shouldBe mapOf("CLAIMED" to 1)
        inboxKeyCount(dataSource()) shouldBe 0
    }

    /**
     * 앞 run 이 남긴 `CLAIMED` 를 **다음 run 이 격리한다** — lease 를 새로 쥔 relay 가 첫
     * claim 전에 보는 `CLAIMED` 는 정의상 죽은 홀더의 것이다(살아 있으면 lease 를 못 쥔다).
     * 재가시화(PENDING 복귀)가 **아니다**(ADR 0005 D-3, `OPEN-OPS-10` ③).
     */
    @Test
    fun `다음 run 은 좌초한 CLAIMED 를 ISOLATED 로 격리한다`() {
        insertPendingNotificationRow(dataSource(), "orphan-1")
        forceOutboxState(dataSource(), "orphan-1", "CLAIMED")

        val report =
            RelayHarness(dataSource()).relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.orphansIsolated shouldBe 1
        report.claimed shouldBe 0
        outboxStateCounts(dataSource()) shouldBe mapOf("ISOLATED" to 1)
    }

    /**
     * D-6F10-13 — relay 는 `StrategyUpdated` 행을 **건드리지 않는다**(그 종류의 소비자가 아직
     * 없고, 전이표에 `Claimed -> Pending` 간선이 없어 집으면 격리밖에 못 한다 =
     * 남의 행을 태운다).
     */
    @Test
    fun `relay 전후로 다른 종류 행의 상태 분포는 불변이다`() {
        insertPendingNotificationRow(dataSource(), "mine-1")
        insertPendingStrategyRow(dataSource(), "theirs-1")

        val harness = RelayHarness(dataSource())
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.delivered shouldBe 1
        // 내 행만 DELIVERED, 남의 행은 PENDING 그대로.
        outboxStateCounts(dataSource()) shouldBe mapOf("DELIVERED" to 1, "PENDING" to 1)
    }

    /**
     * ADR 0005 D-4 「억제는 기록 억제가 아니다」 — 억제 환경에서는 claim 0 이고 상태 분포가
     * 전후로 **같다**. claim 한 뒤 억제하면 그 행이 어휘 없는 종단을 찾지 못해 `CLAIMED` 에
     * 좌초한다.
     */
    @Test
    fun `억제 환경에서는 상태 분포가 전후로 같다 — claim 0`() {
        insertPendingNotificationRow(dataSource(), "suppressed-1")
        val before = outboxStateCounts(dataSource())

        val harness = RelayHarness(dataSource(), environment = RuntimeEnvironment.Development)
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Skipped>()

        report.reason shouldBe RelaySkipReason.EnvironmentSuppressed
        outboxStateCounts(dataSource()) shouldBe before
        harness.sender.sentKeys() shouldBe emptyList()
    }

    /** 정상 경로의 종단 — 발송 1, inbox 1, 행 DELIVERED. 위 변이들의 양성 대조다. */
    @Test
    fun `정상 경로는 DELIVERED 와 inbox 행 하나를 남긴다`() {
        insertPendingNotificationRow(dataSource(), "happy-1")

        val harness = RelayHarness(dataSource())
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.delivered shouldBe 1
        report.orphansIsolated shouldBe 0
        harness.sender.sentKeys() shouldBe listOf("notification-happy-1")
        outboxStateCounts(dataSource()) shouldBe mapOf("DELIVERED" to 1)
        inboxKeyCount(dataSource()) shouldBe 1
    }
}

/**
 * 행 하나짜리 run 의 경계 호출 순서 — ① 고아 조회 ② T1 claim ③ inbox 조회 ④ **T2**.
 * `RelayOutboxNotificationsTest` 의 경계 계수 단언(총 4)과 같은 사실을 가리킨다.
 *
 * 이 순번을 쓰는 것은 **계수 계약 test 하나**다(그쪽은 「T2 직전에 행을 밀어 둔다」는 주입
 * 지점이 순번으로만 표현된다). 크래시 주입은 순번을 쓰지 않는다(R1-L-4).
 */
private const val T2_CALL_INDEX = 4
