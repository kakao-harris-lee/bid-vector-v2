package bidvector.workflow.notification

import bidvector.workflow.event.OutboxEntryId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * **임대를 잃었을 때의 처분** — `RelayOutboxNotificationsTest` 에서 갈라낸 파일(파일 500줄 한도).
 *
 * 기계적 분할이 아니다: 여기 모인 test 들은 모두 `LosingLease` 한 fake 를 입력으로 **네 지점**
 * (획득 직후 · 고아마다 · claim 전 · 행마다)에서의 멈춤을 잰다. 남는 파일은 처분 어휘·경계
 * 계수·예외 집계를 본다.
 *
 * 서수↔지점 대응은 `LosingLease` KDoc 이 정본이다 — 고아 수에 따라 밀리므로 각 test 가 자기
 * 입력의 고아 수를 주석에 적는다.
 */
class RelayLeaseLossTest {
    /**
     * R1-M-1 — 본문 **도중에** 임대를 잃으면 남은 행을 건드리지 않고 멈춘다. verifier probe
     * V6b 가 실측한 것: 임대 연결만 끊겨도(프로세스는 살아 있다) 다음 relay 가 임대를 쥐고
     * 첫째의 in-flight `CLAIMED` 를 고아로 읽어 격리한다. 중복 발송은 0 이지만 발송된 행이
     * `ISOLATED` 로 표기되고 첫째 배치의 미발송 행은 놓친다.
     *
     * 멈춤의 증거는 **계수 차이**다 — `claimed` 는 2 인데 처분은 1 개다(남은 하나는 건드리지
     * 않았다). 그 차이가 곧 「`CLAIMED` 에 남아 다음 run 이 받을 행 수」다.
     */
    @Test
    fun `본문 도중 임대를 잃으면 남은 행을 건드리지 않고 멈춘다 — LeaseLost`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("keep"), notificationRow("drop")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report =
            relay(outbox, inbox, sender, leases = LosingLease(heldFor = 3))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.claimed shouldBe 2
        report.partial.delivered shouldBe 1
        outbox.delivered shouldBe listOf(OutboxEntryId("keep"))
        // 둘째 행은 어느 종단으로도 가지 않았다 — 건드리지 않은 것이 처분이다.
        outbox.failed.shouldBeEmpty()
        outbox.isolated.shouldBeEmpty()
        sender.requests.map { it.idempotencyKey.value } shouldBe listOf("key-keep")
    }

    /**
     * 집은 뒤 **첫 행 앞**에서 잃으면 한 행도 발송하지 않는다 — `claimed` 는 1 인데 처분이
     * 0 이다(그 행은 `CLAIMED` 에 남아 다음 run 의 고아가 된다).
     */
    @Test
    fun `첫 행 앞에서 임대를 잃으면 발송이 0 이다`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("none")))
        val sender = ScriptedSender(delivered())

        val report =
            relay(outbox, FakeInboxPort(), sender, leases = LosingLease(heldFor = 2))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.claimed shouldBe 1
        report.partial.delivered shouldBe 0
        sender.requests.shouldBeEmpty()
        outbox.delivered.shouldBeEmpty()
    }

    /**
     * D-6F10-31 ② — **획득 직후**에 잃으면 고아 목록을 읽지도 않는다. 앞 판은 이 지점에
     * 검사가 없어, 임대를 이미 잃은 relay 가 새 홀더의 in-flight 행을 고아로 격리했다
     * (verifier r2 probe V6c). 고아와 집을 행을 **둘 다 심어** 「아무것도 안 했다」가
     * 값으로 보이게 한다.
     */
    @Test
    fun `획득 직후에 임대를 잃으면 격리도 claim 도 하지 않는다`() {
        val outbox =
            FakeOutboxPort(
                pending = listOf(notificationRow("send")),
                orphans = listOf(notificationRow("orphan")),
            )
        val sender = ScriptedSender(delivered())
        val leases = LosingLease(heldFor = 0)

        val report =
            relay(outbox, FakeInboxPort(), sender, leases = leases)
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        // **첫 물음에서 멈췄다** — 더 물었다면 그 사이에 질의가 돌았다는 뜻이다(cr R-13 ⓐ).
        leases.asked shouldBe 1

        report.partial.orphansIsolated shouldBe 0
        report.partial.claimed shouldBe 0
        outbox.isolated.shouldBeEmpty()
        outbox.claimedEntriesKinds.shouldBeEmpty()
        outbox.claimedKinds.shouldBeEmpty()
        sender.requests.shouldBeEmpty()
    }

    /**
     * **첫 고아 앞** 지점 — 목록은 읽혔지만 **하나도 태우지 않는다**.
     *
     * 보증의 범위가 PR #63 finding 7 로 좁아졌다: 앞 판은 「목록도 읽지 않는다」였는데 그
     * 보호를 하던 검사가 획득 직후 검사와 **연속 중복**이었다. 목록 읽기는 **읽기 전용 질의**
     * 라 되돌릴 것이 없고, 되돌릴 수 없는 것은 격리다 — 그 자리는 고아마다 검사가 지킨다.
     */
    @Test
    fun `첫 고아 앞에서 임대를 잃으면 하나도 태우지 않는다`() {
        val outbox = FakeOutboxPort(orphans = listOf(notificationRow("orphan")))

        val report =
            relay(outbox, FakeInboxPort(), ScriptedSender(delivered()), leases = LosingLease(heldFor = 1))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.orphansIsolated shouldBe 0
        outbox.isolated.shouldBeEmpty()
        outbox.claimedKinds.shouldBeEmpty()
    }

    /**
     * **claim 전** 지점 — 격리는 이미 끝났으므로 계수에 남고(숨기지 않는다), claim 은 돌지
     * 않는다. 두 relay 가 같은 종류를 동시에 소비하는 것을 막는 자리다.
     */
    @Test
    fun `claim 전에 임대를 잃으면 격리 계수만 남고 집지 않는다`() {
        val outbox =
            FakeOutboxPort(
                pending = listOf(notificationRow("send")),
                orphans = listOf(notificationRow("orphan")),
            )

        // 고아가 하나라 서수 2 는 그 고아, 3 이 claim 전이다.
        val report =
            relay(outbox, FakeInboxPort(), ScriptedSender(delivered()), leases = LosingLease(heldFor = 2))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.orphansIsolated shouldBe 1
        report.partial.claimed shouldBe 0
        outbox.isolated shouldBe listOf(OutboxEntryId("orphan"))
        outbox.claimedKinds.shouldBeEmpty()
    }

    /**
     * **cr T-1** — 고아를 태우는 **도중**에 임대를 잃으면 멈춘다. 격리는 단방향 종단이라
     * (`ISOLATED` 에서 나가는 간선 0) 임대 없이 태운 행은 애플리케이션 경로로 되살릴 수 없다 —
     * 그래서 되돌릴 수 있는 발송 루프보다 **더** 촘촘해야 한다. 앞 판은 목록을 읽기 전 한 번만
     * 물어서, 첫째를 태우는 사이에 잃으면 남은 전부를 임대 없이 태웠다.
     *
     * 고아 둘 가운데 **하나만** 태워지고 둘째가 손대지지 않은 것이 증거다 — 계수와 port 기록
     * 둘 다로 잰다(계수만 보면 「둘 다 태우고 1 을 보고하는」 구현도 초록이다).
     */
    @Test
    fun `고아를 태우는 도중에 임대를 잃으면 남은 고아를 건드리지 않는다`() {
        val outbox =
            FakeOutboxPort(
                pending = listOf(notificationRow("send")),
                orphans = listOf(notificationRow("orphan-1"), notificationRow("orphan-2")),
            )

        // 1 획득(목록 전) · 2 첫 고아(참) · 3 둘째 고아(거짓).
        val report =
            relay(outbox, FakeInboxPort(), ScriptedSender(delivered()), leases = LosingLease(heldFor = 2))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.orphansIsolated shouldBe 1
        report.partial.claimed shouldBe 0
        outbox.isolated shouldBe listOf(OutboxEntryId("orphan-1"))
        outbox.claimedKinds.shouldBeEmpty()
    }

    /**
     * **R3-L-1 / cr T-2** — 획득 직후 검사가 **억제 판정보다 앞**이라는 것을 잠근다.
     *
     * 왜 이 한 건이 필요했나: 다른 임대 test 는 전부 Live 환경에서 돌아, 두 갈래의 순서를
     * 뒤바꾸는 변이가 상설 test 전부 초록이었다(verifier r3 probe P1s). 억제 환경에서만 둘이
     * 갈린다 — 순서가 지금대로면 `LeaseLost`(종료 코드 1), 뒤바뀌면 `Skipped`(종료 코드 4).
     *
     * 어느 쪽이 맞는가: 「임대를 잃었다」가 더 센 신호다. 억제는 설정을 고치면 풀리는 상태인데,
     * 임대 상실은 **배타성이 깨진** 상태라 같은 run 을 다시 돌리는 것이 안전하지 않다.
     */
    @Test
    fun `억제 환경에서도 획득 직후 임대 상실이 억제보다 앞이다`() {
        val outbox = FakeOutboxPort(orphans = listOf(notificationRow("orphan")))

        val report =
            relay(
                outbox,
                FakeInboxPort(),
                ScriptedSender(delivered()),
                leases = LosingLease(heldFor = 0),
                policy = relayPolicy(DeliveryMode.DryRun),
            ).relay(RELAY_LIMIT)

        report.shouldBeInstanceOf<RelayReport.LeaseLost>()
        report.partial.orphansIsolated shouldBe 0
        outbox.claimedEntriesKinds.shouldBeEmpty()
    }

    /**
     * **집을 행이 0 이어도 guard 를 본다**(probe V6c 의 핵심). 앞 판은 검사가 행 루프 안에만
     * 있어 빈 배치에서 guard 가 한 번도 불리지 않고 `Completed` + 종료 코드 0 이 났다 —
     * 「임대를 잃었는데 성공으로 보고한다」. 이 test 는 그 자리가 `LeaseLost` 임을 잠근다.
     */
    @Test
    fun `집을 행이 0 이어도 임대 상실은 LeaseLost 다`() {
        val report =
            relay(FakeOutboxPort(), FakeInboxPort(), ScriptedSender(delivered()), leases = LosingLease(heldFor = 1))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.claimed shouldBe 0
        report.partial.delivered shouldBe 0
    }
}
