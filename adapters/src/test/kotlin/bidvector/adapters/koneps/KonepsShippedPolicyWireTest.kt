package bidvector.adapters.koneps

import bidvector.procurement.AttemptKind
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 7))
private val WIRE_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC)

/** 움직이는 시계의 출발점 — 관문 대역의 예산 날짜와 **같은 값**이다(날 경계가 끼어들지 않는다). */
private val WIRE_START: Instant = GATE_NOW

/** 3A 표의 **재시도 가능** 범주 코드 하나 — 차단 응답 본문에 이것을 싣는 것이 아래 403 test 의 요점이다. */
private const val RETRYABLE_RESULT_CODE = "01"

/**
 * **출하 전송 정책을 resolve 한 그 값** — 시나리오 test 가 쓰는 좁힌 정책([testKonepsHttpPolicy])이
 * 아니다. 이 구별이 이 파일의 존재 이유다(v H-1).
 */
private val SHIPPED: KonepsHttpPolicyData =
    when (val resolution = KONEPS_HTTP_POLICY.resolve(REFERENCE_DATE.date)) {
        is Resolution.Resolved -> resolution.value
        else -> error("출하 전송 정책이 ${REFERENCE_DATE.date} 에 적용되지 않는다")
    }

/**
 * 주입 시계 + 기다림 기록기 — **기다린 만큼 시계가 간다**. 출하 간격은 1 초 단위라 실제로 잠들면
 * 이 test 가 벽시계를 태운다(그래서 `KonepsCallPacer` 가 시계와 기다림을 둘 다 주입받는다).
 */
private class WireWaiter(
    var now: Instant = WIRE_START,
) {
    val waits = mutableListOf<Duration>()

    fun waitFor(duration: Duration) {
        waits += duration
        now = now.plus(duration)
    }
}

private fun shippedPacer(waiter: WireWaiter): KonepsCallPacer =
    KonepsCallPacer(SHIPPED.minimumCallInterval, { waiter.now }, waiter::waitFor)

/**
 * **출하 정책 값이 실제 호출 경로에 닿는가**(M9/ops-code 수정 라운드 1 — v H-1 · cr MEDIUM).
 *
 * 앞 판의 구멍은 거동이 아니라 **측정의 자리**였다: 처분 표와 간격을 재는 test 가 모두
 * [testKonepsHttpPolicy] 로 만든 좁힌 정책 위에서 돌아, 출하 정책의 `statusRules` 에서 403 행을
 * 지우거나 `minimumCallInterval` 을 `ZERO` 로 바꾸는 변이가 **전 test 초록**이었다. 값의 비용은
 * 매 PR 에 내면서 그 값이 wire 에 닿는지는 아무도 묻지 않는 상태다.
 *
 * 그래서 이 파일은 [SHIPPED] 위에서만 돈다 — 좁힌 정책을 쓰는 시나리오 test 로 갈음하지 않는다.
 */
class KonepsShippedPolicyWireTest {
    /**
     * 출하 표의 **직접 단언** — 간격을 끈 값(`ZERO`)이 출하되면 여기가 붉어진다. 거동 test 와
     * 따로 두는 이유: 거동은 「벌어졌는가」를 재고 이 줄은 「출하 값이 무엇인가」를 잰다.
     */
    @Test
    fun `출하 전송 정책의 최소 간격은 0 보다 크다`() {
        (SHIPPED.minimumCallInterval > Duration.ZERO) shouldBe true
    }

    /**
     * **이 파일이 재는 것이 「지금 출하되는 값」이어야 한다**(cr r2 low). 위 [SHIPPED] 는 날짜 하나로
     * resolve 한 값이라, 날짜 붙은 엔트리가 하나 생기는 날 이 측정은 **은퇴한 행**을 가리키고
     * production 은 새 행으로 돈다. 그 어긋남을 한 줄로 막는다.
     */
    @Test
    fun `resolve 한 값이 정책 표의 마지막 엔트리와 같다`() {
        SHIPPED shouldBe KONEPS_HTTP_POLICY.entries.last().second
    }

    @Test
    fun `출하 표는 401·403 을 차단으로, 429 를 속도 한도로 가른다`() {
        SHIPPED.dispositionFor(401) shouldBe KonepsStatusDisposition.ACCESS_BLOCKED
        SHIPPED.dispositionFor(403) shouldBe KonepsStatusDisposition.ACCESS_BLOCKED
        SHIPPED.dispositionFor(429) shouldBe KonepsStatusDisposition.RATE_LIMITED
    }

    /**
     * **출하 간격이 관문을 지난 호출들을 실제로 벌린다** — 증거를 원장에서 읽는다(관문이 호출
     * 전에 자기 시계로 `PENDING` 줄을 적는다). 기다림 계수도 함께 잰다: 간격이 `ZERO` 면 기다림이
     * 아예 일어나지 않으므로 「벌어짐 ≥ 0」 같은 공허한 단언이 되지 않는다.
     */
    @Test
    fun `출하 간격이 관문을 지난 연속 호출의 원장 시각을 벌린다`() {
        val waiter = WireWaiter()
        val ledger = RecordingAttemptLedger()
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.NO_DATA))).use { server ->
            val gate =
                testCallGate(
                    ledger = ledger,
                    nowSource = { waiter.now },
                    pacer = shippedPacer(waiter),
                )
            repeat(3) {
                gate.send(KonepsCallContext(SourceEndpoint.NOTICE_LIST), server.baseUri, SHIPPED.requestTimeout)
            }

            server.requestCount shouldBe 3
        }

        val interval = SHIPPED.minimumCallInterval
        waiter.waits shouldContainExactly listOf(interval, interval)
        val sentAt = ledger.appended.filter { it.kind == AttemptKind.PENDING }.map { it.at }
        sentAt shouldHaveSize 3
        sentAt.zipWithNext { a, b -> Duration.between(a, b) } shouldContainExactly listOf(interval, interval)
    }

    /**
     * **상한이 거부한 호출은 기다리지 않는다** — 간격은 상한 **뒤**, 전송 **앞**이다(「예산 소모는
     * 실제 부작용 발생에만」의 짝: 나가지 않은 호출이 벽시계를 태우지 않는다). 이 단언이 없으면
     * `awaitTurn()` 을 상한 검사 앞으로 옮기는 변이가 초록으로 지나간다(v L-1 ②).
     */
    @Test
    fun `상한이 막은 호출은 출하 간격을 기다리지 않는다`() {
        val waiter = WireWaiter()
        val gate =
            testCallGate(
                perDay = 1,
                total = 1,
                nowSource = { waiter.now },
                pacer = shippedPacer(waiter),
            )
        val call = KonepsCallContext(SourceEndpoint.NOTICE_LIST)

        // 첫 호출은 상한을 받아 나가고(도달 불가 주소라 전송은 실패한다 — 재는 것은 그 결말이 아니다)
        // 그 자리에서 다음 하한이 선다. 둘째 호출은 상한이 막으므로 기다릴 이유가 없다.
        gate.send(call, UNREACHABLE, SHIPPED.requestTimeout)
        val denied = gate.send(call, UNREACHABLE, SHIPPED.requestTimeout)

        (denied is KonepsGateOutcome.Denied) shouldBe true
        waiter.waits.shouldHaveSize(0)
    }

    /**
     * **출하 표 위에서 403 은 호출 1회다** — 본문은 재시도 가능 코드([RETRYABLE_RESULT_CODE])를
     * 싣고 상태 코드만 차단이다. 출하 표에서 403 행을 지우면 봉투 분류가 재시도를 얻어 이 계수가
     * 4 가 된다(출하 `maxAttempts`).
     */
    @Test
    fun `출하 표의 403 은 재시도 가능 봉투를 싣고 와도 호출 1회다`() {
        val waiter = WireWaiter()
        val body = KonepsEnvelopeFixtures.failure(RETRYABLE_RESULT_CODE, "서비스 접근 거부")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(403, body))).use { server ->
            val source =
                KonepsOpenApiNoticeSource(
                    gate = testCallGate(nowSource = { waiter.now }, pacer = shippedPacer(waiter)),
                    baseUri = server.baseUri,
                    serviceKey = ServiceKey.of("test-service-key"),
                    httpPolicy = SHIPPED,
                    collectionPolicyProvider = ::resolvedCollectionPolicy,
                    businessDivision = BusinessDivision.SERVICE,
                    clock = WIRE_CLOCK,
                )

            val batch = source.fetchNotices(REFERENCE_DATE, null)

            server.requestCount shouldBe 1
            batch.accounting.truncationCause shouldBe TruncationCause.AccessBlocked
            // **이어 갈 자리를 내지 않는다**(v L-1 ③) — 차단은 「입력·구성이 틀렸다」 축이라 같은
            // 자격으로 같은 쪽을 다시 걸으면 같은 403 이 온다. 이 단언이 없으면 차단을 재개 가능
            // 축으로 되돌리는 변이가 초록으로 지나간다.
            batch.next shouldBe null
        }
    }
}

/** 서버를 띄우지 않는 판의 주소 — 재는 것이 응답이 아니라 **상한·간격의 순서**라는 사실의 표기다. */
private val UNREACHABLE: URI = URI.create("http://127.0.0.1:1/unreachable")
