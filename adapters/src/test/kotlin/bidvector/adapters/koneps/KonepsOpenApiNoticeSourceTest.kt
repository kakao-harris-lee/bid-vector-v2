package bidvector.adapters.koneps

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.CollectionReferenceDate
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 7))
private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC)

private fun newSource(
    server: MockKonepsServer,
    policy: KonepsHttpPolicyData,
): KonepsOpenApiNoticeSource =
    KonepsOpenApiNoticeSource(
        httpClient = HttpClient.newHttpClient(),
        baseUri = server.baseUri,
        serviceKey = ServiceKey.of("test-service-key"),
        httpPolicy = policy,
        collectionPolicyProvider = ::resolvedCollectionPolicy,
        clock = FIXED_CLOCK,
    )

/**
 * ⑦ mock server 시나리오 — scope.md 「이 slice 가 하는 일」 ⑦, D-3B-4(loopback in-process,
 * 네트워크 0). 각 test 가 scope ⑦ 목록의 한 항목에 대응한다(대응표는 checklist.md).
 */
class KonepsOpenApiNoticeSourceTest {
    @Test
    fun `COL-01 — 4건 중 1건 공고번호 없음이면 3건 수집 + dropped 1`() {
        val items =
            listOf(
                mapOf("bidNtceNo" to "SYN-NTC-0001", "bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "SYN-NTC-0002", "bidNtceOrd" to "000"),
                mapOf("bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "SYN-NTC-0004", "bidNtceOrd" to "000"),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 4, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 3
            batch.accounting.dropped shouldBe 1
            batch.accounting.dropReasons[CollectionDropReason.CollectionMissingNoticeNumber] shouldBe 1
            batch.accounting.truncated shouldBe false
        }
    }

    @Test
    fun `429 연속 실패 뒤 성공 — 재시도 상한 안에서 회복, 호출 횟수는 서버 카운터로 단언`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-NTC-0005", "bidNtceOrd" to "000")),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        val script =
            listOf(
                MockKonepsResponse.Reply(429, ""),
                MockKonepsResponse.Reply(429, ""),
                MockKonepsResponse.Reply(200, body),
            )
        MockKonepsServer.start(script).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            server.requestCount shouldBe 3
            batch.accounting.truncated shouldBe false
        }
    }

    @Test
    fun `timeout 이 반복되면 재시도 상한 뒤 truncated 로 종료된다`() {
        val script = listOf(MockKonepsResponse.DelayThenReply(delayMillis = 300, status = 200, body = "{}"))
        MockKonepsServer.start(script).use { server ->
            val policy = testKonepsHttpPolicy(maxAttempts = 2, requestTimeout = Duration.ofMillis(50))
            val batch = newSource(server, policy).fetchNotices(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe true
            server.requestCount shouldBe 2
        }
    }

    @Test
    fun `totalCount 없음 + 같은 페이지 반복이면 유한 페이지에서 truncated 로 끝난다`() {
        val items = listOf(mapOf("bidNtceNo" to "SYN-NTC-0006", "bidNtceOrd" to "000"))
        val body = KonepsEnvelopeFixtures.success(items, totalCount = null, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxPages = 5)).fetchNotices(REFERENCE_DATE, null)

            batch.accounting.truncated shouldBe true
            batch.items.size shouldBe 1
            server.requestCount shouldBe 2
        }
    }

    @Test
    fun `미지 resultCode 는 Unclassified 로 비재시도 실패한다`() {
        val body = KonepsEnvelopeFixtures.failure("99", "정의되지 않은 코드")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe true
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `resultCode 자체가 부재하면 Unclassified 로 비재시도 실패한다`() {
        val body = KonepsEnvelopeFixtures.ABSENT_RESULT_CODE
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe true
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `계약에 없는 raw 키는 항목을 살리되 unknownFields 로 계수한다 COL-07`() {
        val items =
            listOf(
                mapOf(
                    "bidNtceNo" to "SYN-NTC-0007",
                    "bidNtceOrd" to "000",
                    "synNewKey" to "1234",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.accounting.unknownFields shouldBe 1
            batch.accounting.dropped shouldBe 0
        }
    }

    @Test
    fun `resultCode 03 은 실패가 아니라 데이터 없음이다`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.NO_DATA))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.sourceTotal shouldBe 0
            batch.accounting.truncated shouldBe false
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `resultCode 22 는 quota 초과로 재시도 대상이다`() {
        val successBody =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-NTC-0008", "bidNtceOrd" to "000")),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        val script =
            listOf(
                MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.failure("22", "서비스 요청 제한 횟수 초과")),
                MockKonepsResponse.Reply(200, successBody),
            )
        MockKonepsServer.start(script).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            server.requestCount shouldBe 2
        }
    }

    @Test
    fun `resultCode 30 은 등록되지 않은 서비스 키 — 비재시도`() {
        val body = KonepsEnvelopeFixtures.failure("30", "등록되지 않은 서비스키")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.accounting.truncated shouldBe true
            server.requestCount shouldBe 1
        }
    }
}
