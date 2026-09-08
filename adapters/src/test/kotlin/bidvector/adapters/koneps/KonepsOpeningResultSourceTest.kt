package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.procurement.decideDetailFetch
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 8))
private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
private val NOTICE_ID = NoticeId(NoticeNumber.of("SYN-OPEN-0001"), NoticeRound.of("000"))
private val GATES = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48)

private fun newSource(
    server: MockKonepsServer,
    policy: KonepsHttpPolicyData = testKonepsHttpPolicy(),
): KonepsOpeningResultSource =
    KonepsOpeningResultSource(
        listBaseUri = server.baseUri,
        listOperation = KonepsOperationPolicy.AWARD_LIST,
        listSourceEndpoint = SourceEndpoint.OPENING_AWARD_LIST,
        reserveDetailBaseUri = server.baseUri,
        config =
            KonepsSourceConfig(
                httpClient = HttpClient.newHttpClient(),
                serviceKey = ServiceKey.of("test-service-key"),
                httpPolicy = policy,
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                clock = FIXED_CLOCK,
            ),
    )

private fun fetchEvidence(): DetailFetchDecision.Fetch {
    val decision =
        decideDetailFetch(
            NOTICE_ID,
            alreadyHeld = false,
            openingObservedAt = null,
            lastCheckedAt = null,
            now = Instant.parse("2026-09-08T00:00:00Z"),
            gates = GATES,
        )
    check(decision is DetailFetchDecision.Fetch) { "술어가 Fetch 를 내지 않았다: $decision" }
    return decision
}

/** ①②⑥b — `OpeningResultSourcePort` 구현. */
class KonepsOpeningResultSourceTest {
    @Test
    fun `fetchOpeningResults 는 낙찰 목록 항목을 OPENING_AWARD_LIST sourceEndpoint 로 낸다`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-OPEN-0002", "bidNtceOrd" to "000", "bidwinnrNm" to "SYN-CORP")),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.items.single().sourceEndpoint shouldBe SourceEndpoint.OPENING_AWARD_LIST
        }
    }

    @Test
    fun `fetchReservePrices 는 evidence 의 noticeId 로 단건 조회하고 RESERVE_PRICE_DETAIL 로 낸다`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(
                    mapOf(
                        "bidNtceNo" to NOTICE_ID.number.value,
                        "bidNtceOrd" to "000",
                        "plnprc" to "900000000",
                        "drwtYn" to "N",
                    ),
                ),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchReservePrices(fetchEvidence())

            batch.items.size shouldBe 1
            batch.items.single().sourceEndpoint shouldBe SourceEndpoint.RESERVE_PRICE_DETAIL
        }
    }

    @Test
    fun `제한 없음(resultCode 00 + totalCount 0)은 성공 + 빈 항목이다 — COL-04 세 어휘 중 하나`() {
        val body = KonepsEnvelopeFixtures.success(emptyList(), totalCount = 0, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.sourceTotal shouldBe 0
            batch.accounting.truncated shouldBe false
        }
    }

    @Test
    fun `resultCode 03 은 데이터 없음이다 — COL-04 세 어휘 중 하나`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.NO_DATA))).use { server ->
            val batch = newSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe false
            batch.accounting.sourceTotal shouldBe 0
        }
    }

    @Test
    fun `미지 resultCode 는 비재시도 실패다 — 3B 공통 기반 재사용 확인`() {
        val body = KonepsEnvelopeFixtures.failure("99", "?")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch =
                newSource(server, testKonepsHttpPolicy(maxAttempts = 2))
                    .fetchOpeningResults(REFERENCE_DATE, null)

            batch.accounting.truncationCause shouldBe TruncationCause.Unclassified
        }
    }

    @Test
    fun `F-1 — 예비가격 상세 복수예가 15행이 모두 살아남는다(같은 공고, compnoRsrvtnPrceSno 1~15)`() {
        val rows =
            (1..15).map { sno ->
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "compnoRsrvtnPrceSno" to sno.toString(),
                    "plnprc" to (900_000_000 + sno).toString(),
                    "drwtYn" to "N",
                )
            }
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 15, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchReservePrices(fetchEvidence())

            batch.items.size shouldBe 15
            batch.accounting.received shouldBe 15
            batch.accounting.normalized shouldBe 15
            batch.accounting.duplicate shouldBe 0
        }
    }

    @Test
    fun `F-2 — 차수 없는 1건이 목록 정상 N건 사이에서 port 수준으로 drop 된다`() {
        val items =
            listOf(
                mapOf("bidNtceNo" to "SYN-OPEN-0010", "bidNtceOrd" to "000", "bidwinnrNm" to "SYN-A"),
                mapOf("bidNtceNo" to "SYN-OPEN-0011", "bidwinnrNm" to "SYN-B"),
                mapOf("bidNtceNo" to "SYN-OPEN-0012", "bidNtceOrd" to "000", "bidwinnrNm" to "SYN-C"),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 3, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.size shouldBe 2
            batch.accounting.dropped shouldBe 1
        }
    }

    @Test
    fun `F-2 — 429 연속 실패 뒤 성공(bounded retry)`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-OPEN-0020", "bidNtceOrd" to "000", "bidwinnrNm" to "SYN-CORP")),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        val script = listOf(MockKonepsResponse.Reply(429, ""), MockKonepsResponse.Reply(200, body))
        MockKonepsServer.start(script).use { server ->
            val batch =
                newSource(server, testKonepsHttpPolicy(maxAttempts = 3))
                    .fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.accounting.quotaExceeded shouldBe 1
            server.requestCount shouldBe 2
        }
    }
}
