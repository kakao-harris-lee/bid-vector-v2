package bidvector.adapters.koneps

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.TruncationCause
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 7))
private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC)

/** 3A 표의 **재시도 가능** 범주 코드 하나 — 차단 응답 본문에 이것을 싣는 것이 이 test 의 요점이다. */
private const val RETRYABLE_RESULT_CODE = "01"

private fun walk(
    script: List<MockKonepsResponse>,
    maxAttempts: Int = 3,
): Pair<Int, CollectionAccounting> =
    MockKonepsServer.start(script).use { server ->
        val source =
            KonepsOpenApiNoticeSource(
                gate = testCallGate(),
                baseUri = server.baseUri,
                serviceKey = ServiceKey.of("test-service-key"),
                httpPolicy = testKonepsHttpPolicy(maxAttempts = maxAttempts),
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                businessDivision = BusinessDivision.SERVICE,
                clock = FIXED_CLOCK,
            )
        val batch = source.fetchNotices(REFERENCE_DATE, null)
        server.requestCount to batch.accounting
    }

/**
 * **OPS-09 — 상태 코드 축의 재시도 처분**(M9/ops-code).
 *
 * 앞 판의 구멍: 429 가 아닌 수신 응답은 **상태 코드와 무관하게** 봉투 해석으로 갔고 재시도
 * 여부를 봉투 분류가 정했다. 그래서 차단(403)이 재시도 가능 코드 봉투를 싣고 오면 **재시도됐다** —
 * 차단당한 상대에게 재시도는 상황을 악화시키는 방향이다. 그 구멍을 재는 자리가 이 test 다:
 * 본문은 재시도 가능 코드([RETRYABLE_RESULT_CODE])를 싣고 상태 코드만 차단이다.
 */
class KonepsStatusDispositionTest {
    @Test
    fun `403 은 재시도 가능 봉투를 싣고 와도 재시도되지 않는다`() {
        val body = KonepsEnvelopeFixtures.failure(RETRYABLE_RESULT_CODE, "서비스 접근 거부")
        val (requests, accounting) = walk(listOf(MockKonepsResponse.Reply(403, body)))

        requests shouldBe 1
        accounting.truncated shouldBe true
        accounting.truncationCause shouldBe TruncationCause.AccessBlocked
    }

    @Test
    fun `401 도 같은 차단 축이다 — 자격이 거부된 호출을 다시 내지 않는다`() {
        val body = KonepsEnvelopeFixtures.failure(RETRYABLE_RESULT_CODE, "인증 실패")
        val (requests, accounting) = walk(listOf(MockKonepsResponse.Reply(401, body)))

        requests shouldBe 1
        accounting.truncationCause shouldBe TruncationCause.AccessBlocked
    }

    /**
     * 차단은 **쿼터 신호가 아니다** — 회계의 두 축을 겹쳐 세면 「차단당함」이 「한도를 다 썼음」으로
     * 읽히고 운영자의 다음 할 일이 달라진다(OPS-09 의 사용자 가치 문장이 가르는 바로 그 둘).
     */
    @Test
    fun `차단은 quota 신호로 세지 않는다`() {
        val body = KonepsEnvelopeFixtures.failure(RETRYABLE_RESULT_CODE, "서비스 접근 거부")
        val (_, accounting) = walk(listOf(MockKonepsResponse.Reply(403, body)))

        accounting.quotaExceeded shouldBe 0
    }

    /**
     * **통제군** — 같은 경로에서 429 는 그대로 재시도된다(조사 a-4 의 「~2분 안에 회복, 원인은
     * 동시성」 축). 이 test 가 없으면 「상태 코드를 봤더니 전부 비재시도」라는 과잉 처방도 초록이다.
     */
    @Test
    fun `429 는 여전히 재시도되고 회복된다`() {
        val success =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-M9-0001", "bidNtceOrd" to "000")),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        val script =
            listOf(
                MockKonepsResponse.Reply(429, ""),
                MockKonepsResponse.Reply(429, ""),
                MockKonepsResponse.Reply(200, success),
            )
        val (requests, accounting) = walk(script)

        requests shouldBe 3
        accounting.truncated shouldBe false
        accounting.quotaExceeded shouldBe 2
    }

    /**
     * 처분 표의 **집합 등식**. 「401·403 이 차단인가」만 물으면 표가 넓어지는 것을 보지 못하고,
     * 넓어진 한 줄이 곧 「봉투를 읽어야 할 응답을 읽지 않는다」다(HTTP 200 + 에러 봉투 검출이
     * OPS-09 의 첫 결함이다). 그래서 **봉투로 가지 않는 상태 전수**를 잰다.
     */
    @Test
    fun `봉투 해석을 건너뛰는 상태는 정확히 셋이다`() {
        val declared =
            KONEPS_STATUS_RULES
                .filterNot { it.disposition == KonepsStatusDisposition.INTERPRET_ENVELOPE }
                .map { it.status to it.disposition }

        declared.sortedBy { it.first } shouldContainExactly
            listOf(
                401 to KonepsStatusDisposition.ACCESS_BLOCKED,
                403 to KonepsStatusDisposition.ACCESS_BLOCKED,
                429 to KonepsStatusDisposition.RATE_LIMITED,
            )
    }

    /** 표에 없는 상태는 봉투로 간다 — 그 기본 방향이 HTTP 200 + 에러 봉투 검출을 살린다. */
    @Test
    fun `표에 없는 상태는 봉투 해석으로 간다`() {
        testKonepsHttpPolicy().dispositionFor(500) shouldBe KonepsStatusDisposition.INTERPRET_ENVELOPE
        testKonepsHttpPolicy().dispositionFor(200) shouldBe KonepsStatusDisposition.INTERPRET_ENVELOPE
    }
}
