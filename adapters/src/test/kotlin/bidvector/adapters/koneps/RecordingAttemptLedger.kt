package bidvector.adapters.koneps

import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptLedger
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CallSpend
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionCallBudget
import java.net.http.HttpClient
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
        now = { now },
    )
