package bidvector.adapters.koneps

import bidvector.procurement.AttemptKind
import bidvector.procurement.BudgetLimit
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private val LIST_CALL = KonepsCallContext(SourceEndpoint.OPENING_RESULT_LIST)
private val TIMEOUT: Duration = Duration.ofSeconds(1)

/** 닿지 않는 주소 — 전송은 실패하지만 **호출은 나갔다**(그것이 이 test 가 세는 것이다). */
private val UNREACHABLE: URI = URI.create("http://127.0.0.1:1/mock/getOpengResultListInfoCnstwk")

/**
 * D-6G-47 — 관문이 지는 계약 셋: 호출 **전에** 허가, 호출 **뒤 즉시** 한 줄, 거부는 **값**이다.
 *
 * 상한이 세 라운드 동안 조금씩만 닫힌 이유는 세는 자리가 여럿이어서다. 여기서 재는 것은 「관문
 * 하나가 전부 센다」이고, 그 하나가 틀리면 다른 어디에도 보정이 없다.
 */
class KonepsCallGateTest {
    @Test
    fun `호출마다 의도 한 줄과 결말 한 줄 — 재시도도 각자 한 벌이다`() {
        val ledger = RecordingAttemptLedger()
        val gate = testCallGate(ledger = ledger)

        repeat(3) { gate.send(LIST_CALL, UNREACHABLE, TIMEOUT) }

        ledger.appended shouldHaveSizeOf 6
        // 의도 줄은 호출 **전에** 적힌다 — 그 사이에 죽어도 다음 기동의 상한이 되감기지 않는다.
        ledger.appended.filterIndexed { index, _ -> index % 2 == 0 }.all { it.kind == AttemptKind.PENDING } shouldBe
            true
        ledger.appended.filterIndexed { index, _ -> index % 2 == 1 }.all { it.kind == AttemptKind.HTTP } shouldBe true
        ledger.read().spend(Instant.EPOCH, Instant.EPOCH).total shouldBe 3
    }

    @Test
    fun `상한을 넘기면 보내지 않고 거부를 값으로 낸다`() {
        val ledger = RecordingAttemptLedger()
        val gate = testCallGate(perDay = 1, total = 1, ledger = ledger)

        gate.send(LIST_CALL, UNREACHABLE, TIMEOUT).shouldBeInstanceOf<KonepsGateOutcome.Sent>()
        val denied = gate.send(LIST_CALL, UNREACHABLE, TIMEOUT)

        denied.shouldBeInstanceOf<KonepsGateOutcome.Denied>().limit shouldBe BudgetLimit.TOTAL
        // 막힌 호출은 원장에 없다 — 나가지 않았으므로 「시도」가 아니다(나간 하나의 의도·결말 두 줄뿐).
        ledger.appended shouldHaveSizeOf 2
        ledger.read().spend(Instant.EPOCH, Instant.EPOCH).total shouldBe 1
    }

    /**
     * 하루의 경계는 **KST 자정**이다. 시계를 정오 근처에 두면 UTC 와 KST 의 날짜가 같아 이 회귀를
     * 잡지 못한다(vr H-2 KM4a 가 초록이던 이유) — 그래서 **KST 00:30**(= 전날 15:30Z)에 둔다.
     */
    @Test
    fun `일 상한의 하루는 KST 로 돈다 — 시계를 KST 자정 직후에 둔다`() {
        val justAfterKstMidnight = Instant.parse("2026-09-23T15:30:00Z")
        LocalDate.ofInstant(justAfterKstMidnight, COLLECTION_BUDGET_ZONE) shouldBe LocalDate.of(2026, 9, 24)
        // 같은 순간의 UTC 날짜는 하루 이르다 — 두 구역이 갈리는 자리다.
        LocalDate.ofInstant(justAfterKstMidnight, java.time.ZoneOffset.UTC) shouldBe LocalDate.of(2026, 9, 23)

        // 원장은 9-23 에서 **일 상한을 이미 다 쓴 채** 시작한다. 지금이 KST 로 9-24 면 하루가
        // 바뀌어 새로 차고, UTC 로 9-23 이면 그대로 막힌다 — 두 구역의 답이 갈리는 자리다.
        val gate =
            testCallGate(
                perDay = 1,
                total = 10,
                now = justAfterKstMidnight,
                startDay = LocalDate.of(2026, 9, 23),
                alreadySpent = bidvector.procurement.CallSpend(total = 1, today = 1),
            )

        gate.send(LIST_CALL, UNREACHABLE, TIMEOUT).shouldBeInstanceOf<KonepsGateOutcome.Sent>()
    }
}

private infix fun <T> Collection<T>.shouldHaveSizeOf(expected: Int) {
    size shouldBe expected
}
