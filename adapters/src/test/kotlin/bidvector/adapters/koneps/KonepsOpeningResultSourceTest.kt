package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.procurement.decideDetailFetch
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
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
        openingCompleteBaseUri = server.baseUri,
        config =
            KonepsSourceConfig(
                httpClient = HttpClient.newHttpClient(),
                serviceKey = ServiceKey.of("test-service-key"),
                httpPolicy = policy,
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                clock = FIXED_CLOCK,
            ),
    )

// F-6(verifier r1 재검토) — opengCorpInfo·progrsDivCdNm 는 presentIn 이 OPENING_RESULT_LIST
// 뿐이다. presentIn 강제 뒤로는 그 엔드포인트로 구성한 source 로만 이 필드들을 검증할 수 있다.
private fun newResultListSource(
    server: MockKonepsServer,
    policy: KonepsHttpPolicyData = testKonepsHttpPolicy(),
): KonepsOpeningResultSource =
    KonepsOpeningResultSource(
        listBaseUri = server.baseUri,
        listOperation = KonepsOperationPolicy.OPENING_RESULT_LIST,
        listSourceEndpoint = SourceEndpoint.OPENING_RESULT_LIST,
        reserveDetailBaseUri = server.baseUri,
        openingCompleteBaseUri = server.baseUri,
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
    fun `G-1(a) — compnoRsrvtnPrceSno 가 15행 전부에서 부재해도 15행이 모두 살아남는다`() {
        val rows =
            (1..15).map {
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "plnprc" to "900000000",
                    "drwtYn" to "N",
                )
            }
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 15, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchReservePrices(fetchEvidence())

            batch.items.size shouldBe 15
            batch.accounting.duplicate shouldBe 0
            batch.accounting.rowIdentifierIndeterminate shouldBe 15
        }
    }

    @Test
    fun `G-1(b) — 일부 행만 compnoRsrvtnPrceSno 가 부재해도 그 행들만 rowIdentifierIndeterminate 로 계수된다`() {
        val determined =
            (1..3).map { sno ->
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "compnoRsrvtnPrceSno" to sno.toString(),
                    "plnprc" to "900000000",
                )
            }
        val indeterminate =
            (1..2).map {
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "plnprc" to "900000000",
                )
            }
        val body =
            KonepsEnvelopeFixtures.success(determined + indeterminate, totalCount = 5, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchReservePrices(fetchEvidence())

            batch.items.size shouldBe 5
            batch.accounting.duplicate shouldBe 0
            batch.accounting.rowIdentifierIndeterminate shouldBe 2
        }
    }

    @Test
    fun `G-1(c) — compnoRsrvtnPrceSno 가 빈 문자열이어도 부재와 같이 취급된다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "compnoRsrvtnPrceSno" to "",
                    "plnprc" to "900000000",
                ),
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "compnoRsrvtnPrceSno" to "   ",
                    "plnprc" to "900000001",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 2, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchReservePrices(fetchEvidence())

            batch.items.size shouldBe 2
            batch.accounting.duplicate shouldBe 0
            batch.accounting.rowIdentifierIndeterminate shouldBe 2
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

    @Test
    fun `F-3 — 계약 밖 키(bidwinnrBizno)가 낙찰 목록에서 unknownFields 로 계수된다`() {
        val items =
            listOf(
                mapOf(
                    "bidNtceNo" to "SYN-OPEN-0030",
                    "bidNtceOrd" to "000",
                    "bidwinnrNm" to "SYN-A",
                    "bidwinnrBizno" to "9999999999",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.accounting.unknownFields shouldBe 1
            batch.accounting.maskingFailures shouldBe 0
            batch.accounting.dropped shouldBe 0
        }
    }

    @Test
    fun `F-8 — opengCorpInfo masking 실패(협상 계약형)가 개찰결과 목록에서 maskingFailures 로 계수된다`() {
        val items =
            listOf(
                mapOf(
                    "bidNtceNo" to "SYN-OPEN-0031",
                    "bidNtceOrd" to "000",
                    "opengCorpInfo" to "SYN-B^8888888888^SYN-REP",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newResultListSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.accounting.maskingFailures shouldBe 1
            batch.accounting.unknownFields shouldBe 0
            batch.accounting.dropped shouldBe 0
        }
    }

    @Test
    fun `F-6 — bssamt 는 예비가격 상세로 넓어진 presentIn 덕에 RESERVE_PRICE_DETAIL 관측에도 남는다`() {
        val items =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "bssamt" to "700000000",
                    "plnprc" to "900000000",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchReservePrices(fetchEvidence())

            val contract = resolvedCollectionPolicy(REFERENCE_DATE).fieldContracts.contractFor(RawKey("bssamt"))!!
            batch.items.single().valueOf(contract) shouldBe "700000000"
            batch.accounting.unknownFields shouldBe 0
        }
    }

    @Test
    fun `F-6 — 낙찰 목록 엔드포인트로 개찰결과 전용 필드(opengCorpInfo)를 받으면 presentIn 불일치로 제외된다`() {
        val items =
            listOf(
                mapOf(
                    "bidNtceNo" to "SYN-OPEN-0032",
                    "bidNtceOrd" to "000",
                    "bidwinnrNm" to "SYN-C",
                    "opengCorpInfo" to "SYN-D^7777777777^SYN-REP^100^95.0",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchOpeningResults(REFERENCE_DATE, null)

            batch.items.single().keys shouldNotContain RawKey("opengCorpInfo")
            // presentIn 불일치는 Excluded 로 접혀 unknownFields 로 계수된다(F-3 과 같은 축) —
            // 5성분(단일 낙찰자, 정상 masking 대상)인데도 masking 실패가 아니라 endpoint 불일치로
            // 제외됐다는 것을 maskingFailures=0 로 구별한다.
            batch.accounting.unknownFields shouldBe 1
            batch.accounting.maskingFailures shouldBe 0
        }
    }
}
