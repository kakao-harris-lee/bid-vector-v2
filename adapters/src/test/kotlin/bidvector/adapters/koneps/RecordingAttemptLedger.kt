package bidvector.adapters.koneps

import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptLedger
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CallSpend
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionCallBudget
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 메모리 시도 원장 — 어댑터 test 는 무엇이 적혔는지만 본다(파일은 `FileAttemptLedger` test 가 잰다). */
internal class RecordingAttemptLedger : AttemptLedger {
    val appended = mutableListOf<CollectionAttempt>()

    override fun append(attempt: CollectionAttempt) {
        appended += attempt
    }

    override fun read(): AttemptHistory = AttemptHistory(appended.toList())
}

internal val GATE_NOW: Instant = Instant.parse("2026-09-24T03:00:00Z")

/**
 * 어댑터 test 용 관문 — 상한을 넉넉히 두어 **상한이 아니라 전송 거동**을 잰다. 상한 자체는
 * `KonepsCallGateTest` 와 E2E 가 잰다.
 */
internal fun testCallGate(
    httpClient: HttpClient = HttpClient.newHttpClient(),
    perDay: Int = 10_000,
    total: Int = 10_000,
    ledger: AttemptLedger = RecordingAttemptLedger(),
    now: Instant = GATE_NOW,
    startDay: LocalDate = LocalDate.ofInstant(now, COLLECTION_BUDGET_ZONE),
    alreadySpent: CallSpend = CallSpend(total = 0, today = 0),
    // 움직이는 시계가 필요한 test(간격 축)는 함수를 준다 — 기본은 고정 시각 그대로다.
    nowSource: () -> Instant = { now },
    // 간격은 **끈 상태가 기본**이다(간격 0): 전송 시나리오 test 는 고정 시계를 쓰므로 간격을 두면
    // 둘째 호출부터 실제로 잠든다. 간격 축은 `KonepsCallPacerTest` 가 주입 시계로 잰다.
    pacer: KonepsCallPacer = KonepsCallPacer(Duration.ZERO, nowSource, {}),
): KonepsCallGate =
    KonepsCallGate(
        httpClient = httpClient,
        budget =
            CallBudgetLedger(
                CollectionCallBudget(perDay, total),
                startDay,
                alreadySpent,
            ),
        attempts = ledger,
        now = nowSource,
        pacer = pacer,
    )
