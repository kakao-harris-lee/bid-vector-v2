package bidvector.adapters.koneps

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptLedger
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetLimit
import bidvector.procurement.BudgetOutcome
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 한 소스 호출이 무엇을 부르는지 — 원장 줄에 실린다. 목록 축은 공고 단위가 아니라 [noticeKey] 가
 * `null` 이다(슬롯 하나가 여러 공고를 낸다).
 */
internal data class KonepsCallContext(
    val axis: SourceEndpoint,
    val noticeKey: String? = null,
)

/** 관문의 답 — 보냈거나, 상한이 막았거나. 막힌 것은 오류가 아니라 **정상적인 답**이다. */
internal sealed interface KonepsGateOutcome {
    data class Sent(
        val transport: KonepsTransportOutcome,
    ) : KonepsGateOutcome

    data class Denied(
        val limit: BudgetLimit,
    ) : KonepsGateOutcome
}

/**
 * **6G 의 모든 KONEPS HTTP 시도가 지나는 단일 관문**(D-6G-47).
 *
 * 세 라운드 동안 상한이 조금씩만 닫힌 이유는 **세는 자리가 여럿**이었기 때문이다 — 공고 목록
 * 갈래는 아예 밖에 있었고, 실행 안 예산은 받은 페이지를 셌고, 재시도는 어느 쪽에도 안 잡혔다.
 * 그래서 셈을 한 자리로 옮긴다: 호출 **전에** 여기서 허가를 받고, 호출 **뒤 즉시** 여기서 원장에
 * 한 줄을 적는다. 재시도든 5xx 든 429 든 타임아웃이든 `send` 를 지나므로 전부 한 번씩 센다.
 *
 * 이 클래스가 [HttpClient] 를 쥔 **유일한** 자리다. 다른 곳에 클라이언트가 없으면 관문을 우회하는
 * 경로도 없다 — 규율이 아니라 의존 구조가 그것을 막고, architecture test 가 그 구조를 잰다.
 */
class KonepsCallGate(
    private val httpClient: HttpClient,
    private val budget: CallBudgetLedger,
    private val attempts: AttemptLedger,
    /** 시각의 출처 — 함수 하나다. 시계 타입을 받으면 어느 모듈의 시계인지가 경계 문제가 된다. */
    private val now: () -> Instant,
) {
    internal fun send(
        context: KonepsCallContext,
        uri: URI,
        timeout: Duration,
    ): KonepsGateOutcome {
        val today = LocalDate.ofInstant(now(), COLLECTION_BUDGET_ZONE)
        return when (val permit = budget.consume(today, 1)) {
            is BudgetOutcome.Exhausted -> KonepsGateOutcome.Denied(permit.limit)
            BudgetOutcome.Allowed -> KonepsGateOutcome.Sent(sendAndRecord(context, uri, timeout))
        }
    }

    /**
     * **선기록**(D-6G-61) — 호출 전에 의도를 적고 호출 뒤에 결말을 붙인다. 호출 뒤에만 적으면 그
     * 사이에 죽었을 때 나간 호출이 원장에 없어 다음 기동의 상한이 되감긴다.
     */
    private fun sendAndRecord(
        context: KonepsCallContext,
        uri: URI,
        timeout: Duration,
    ): KonepsTransportOutcome {
        attempts.append(lineOf(context, AttemptOutcome.Succeeded, AttemptKind.PENDING))
        val transport = sendKonepsRequest(httpClient, uri, timeout)
        attempts.append(lineOf(context, transportOutcomeOf(transport), AttemptKind.HTTP))
        return transport
    }

    private fun lineOf(
        context: KonepsCallContext,
        outcome: AttemptOutcome,
        kind: AttemptKind,
    ): CollectionAttempt =
        CollectionAttempt(
            noticeKey = context.noticeKey,
            axis = context.axis,
            outcome = outcome,
            at = now(),
            kind = kind,
            // 호출 단위 줄은 걷기를 모른다(D-6G2d-4 ⓒ) — 관문은 쪽 하나를 보내고, 걷기는 그 쪽들을
            // 묶는 자리의 이름이다. 기본값이 없어 이 자리가 그 사실을 **명시**한다.
            walk = null,
        )
}

/**
 * 한 시도의 결말 — transport 층이 아는 만큼만 적는다. 상태 코드는 **분류**만 남긴다(본문·URI 는
 * 서비스 키를 담을 수 있어 원장에 싣지 않는다).
 */
private fun transportOutcomeOf(transport: KonepsTransportOutcome): AttemptOutcome =
    when (transport) {
        is KonepsTransportOutcome.Received -> {
            if (transport.status == HTTP_OK) {
                AttemptOutcome.Succeeded
            } else {
                AttemptOutcome.Failed("HTTP_${transport.status}")
            }
        }

        KonepsTransportOutcome.TimedOut -> {
            AttemptOutcome.Failed("TIMEOUT")
        }

        is KonepsTransportOutcome.TransportFailed -> {
            AttemptOutcome.Failed("TRANSPORT")
        }
    }

private const val HTTP_OK = 200
