package bidvector.adapters.koneps

import bidvector.procurement.AttemptKind
import bidvector.procurement.SourceEndpoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val PACER_START: Instant = Instant.parse("2026-09-24T03:00:00Z")
private val INTERVAL: Duration = Duration.ofMillis(500)
private val TIMEOUT: Duration = Duration.ofSeconds(2)

/**
 * 주입 시계 + 기다림 기록기 — `Thread.sleep` 자리를 값으로 바꾼다. **기다린 만큼 시계가 간다**:
 * 그래야 「기다렸는데 시계가 멈춰 있어 다음 호출이 또 꽉 기다린다」는 거짓 초록이 안 생긴다.
 */
private class FakeWaiter(
    var now: Instant = PACER_START,
) {
    val waits = mutableListOf<Duration>()

    fun waitFor(duration: Duration) {
        waits += duration
        now = now.plus(duration)
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

private fun pacerOf(
    waiter: FakeWaiter,
    interval: Duration = INTERVAL,
): KonepsCallPacer = KonepsCallPacer(interval, { waiter.now }, waiter::waitFor)

/**
 * **OPS-08 (a) — 연속 호출 사이의 최소 간격**(M9/ops-code).
 *
 * 계수 기반 상한(rate limiter 의 `permits`/`period`)은 **창 안의 호출 수**를 센다 — 창 경계에서
 * 두 호출이 붙어 나갈 수 있고, legacy 에서 429 를 부른 원인이 바로 그 동시성이었다(조사 a-4).
 * 이 축이 그 붙음을 시간으로 막는다. 기다림이 실제로 벽시계를 태우는 자리라 시계와 기다림을
 * 둘 다 주입해 잰다(test 가 잠들지 않는다).
 */
class KonepsCallPacerTest {
    @Test
    fun `첫 호출은 기다리지 않는다`() {
        val waiter = FakeWaiter()

        pacerOf(waiter).awaitTurn()

        waiter.waits.shouldHaveSize(0)
    }

    @Test
    fun `간격 안에 온 둘째 호출은 남은 만큼만 기다린다`() {
        val waiter = FakeWaiter()
        val pacer = pacerOf(waiter)

        pacer.awaitTurn()
        waiter.advance(Duration.ofMillis(200))
        pacer.awaitTurn()

        waiter.waits shouldContainExactly listOf(Duration.ofMillis(300))
    }

    @Test
    fun `간격이 이미 지났으면 기다리지 않는다`() {
        val waiter = FakeWaiter()
        val pacer = pacerOf(waiter)

        pacer.awaitTurn()
        waiter.advance(Duration.ofMillis(800))
        pacer.awaitTurn()

        waiter.waits.shouldHaveSize(0)
    }

    /**
     * 연속 호출 셋이 **쉼 없이** 올 때가 이 축의 본론이다 — 기다린 뒤의 시각에서 다음 하한을
     * 세므로 기다림이 겹쳐 쌓이지 않고(매번 간격 하나), 실제 간격은 정확히 선언값이 된다.
     */
    @Test
    fun `쉼 없는 연속 호출은 매번 간격 하나만 기다리고 실제 간격이 선언값과 같다`() {
        val waiter = FakeWaiter()
        val pacer = pacerOf(waiter)
        val sentAt = mutableListOf<Instant>()

        repeat(3) {
            pacer.awaitTurn()
            sentAt += waiter.now
        }

        waiter.waits shouldContainExactly listOf(INTERVAL, INTERVAL)
        sentAt.zipWithNext { a, b -> Duration.between(a, b) } shouldContainExactly listOf(INTERVAL, INTERVAL)
    }

    /**
     * **한 번 길어진 기다림이 다음 간격을 줄이지 않는다**(cr r2 low).
     *
     * 실제 `Thread.sleep` 은 ms 를 올리고 스케줄러가 더 늦히므로 호출이 하한보다 **뒤에** 나간다.
     * 그 늦음이 매번 같으면 티가 안 나지만 **한 번만 늦고 다음이 제때면** 두 호출이 서로 가까워진다:
     * 다음 하한을 「기다리기로 한 시각」에서 세는 판은 그 하한이 이미 지난 과거라 둘째 간격이
     * 선언값보다 짧아진다(선언이 **최소** 간격이므로 그 방향은 보증 위반이다). 기다림 뒤의 시각에서
     * 세면 그 오차가 「조금 느리다」로만 남는다.
     */
    @Test
    fun `한 번 길어진 기다림 뒤에도 다음 간격은 선언값 이상이다`() {
        val waiter = FakeWaiter()
        var overshoot = Duration.ofMillis(120)
        val pacer =
            KonepsCallPacer(INTERVAL, { waiter.now }, { requested ->
                waiter.waitFor(requested + overshoot)
                // 늦음은 **한 번뿐**이다 — 다음 기다림은 제때 끝난다.
                overshoot = Duration.ZERO
            })
        val sentAt = mutableListOf<Instant>()

        repeat(3) {
            pacer.awaitTurn()
            sentAt += waiter.now
        }

        sentAt.zipWithNext { a, b -> Duration.between(a, b) }.forEach { gap ->
            withClue("실측 간격 $gap · 선언 $INTERVAL") { (gap >= INTERVAL) shouldBe true }
        }
    }

    /** 간격 0 은 **끄는 자리**다 — 기다림이 아예 일어나지 않는다(시나리오 test 가 쓰는 값). */
    @Test
    fun `간격 0 이면 기다림이 0 회다`() {
        val waiter = FakeWaiter()
        val pacer = pacerOf(waiter, Duration.ZERO)

        repeat(3) { pacer.awaitTurn() }

        waiter.waits.shouldHaveSize(0)
    }

    @Test
    fun `음수 간격은 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            KonepsCallPacer(Duration.ofMillis(-1), { PACER_START }, {})
        }
    }

    /**
     * **관문을 지나는 모든 호출이 간격을 지난다**(OPS-08 의 구조적 닫힘) — 관문이 `HttpClient` 를
     * 쥔 유일한 자리라(D-6G-47) 이 단언이 곧 「우회 경로가 없다」다. 간격의 증거를 **원장**에서
     * 읽는다: 관문은 호출 전에 자기 시계로 `PENDING` 줄을 적으므로 그 줄들의 시각 간격이 곧
     * 나간 호출들의 간격이다.
     */
    @Test
    fun `관문을 지난 호출들의 원장 시각이 선언된 간격 이상으로 벌어진다`() {
        val waiter = FakeWaiter()
        val ledger = RecordingAttemptLedger()
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.NO_DATA))).use { server ->
            val gate =
                testCallGate(
                    ledger = ledger,
                    nowSource = { waiter.now },
                    pacer = pacerOf(waiter),
                )
            repeat(3) { gate.send(KonepsCallContext(SourceEndpoint.NOTICE_LIST), server.baseUri, TIMEOUT) }

            server.requestCount shouldBe 3
        }

        val sentAt = ledger.appended.filter { it.kind == AttemptKind.PENDING }.map { it.at }
        sentAt shouldHaveSize 3
        sentAt.zipWithNext { a, b -> Duration.between(a, b) }.forEach { gap ->
            (gap >= INTERVAL) shouldBe true
        }
    }
}
