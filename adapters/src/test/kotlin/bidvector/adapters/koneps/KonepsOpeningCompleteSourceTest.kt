package bidvector.adapters.koneps

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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * M3/3F D-3F-1 (a) — `fetchOpeningCompleteResults`. `KonepsOpeningResultSourceTest.kt`에서
 * sizeGate(500줄, v2-지침서 §5)로 분리한 파일(3E `CleanMigrationCheckTest`와 같은 전례) — 위
 * 상수·helper 는 그 파일의 `private` 선언과 이름이 같지만 각자 파일 스코프라 부딪히지
 * 않는다(Kotlin top-level `private` 는 파일 단위 — 패키지 단위로 올리면 다른 형제 test
 * 파일의 동명 선언과 충돌해 오히려 재사용이 안 된다, 실측). scope.md ⑦ 시나리오: 정상 다수
 * 행·단일 낙찰자·협상 계약·추첨번호 부재·순위 동값·치환 세 변형·`bidNtceNo` 누락 → `08`
 * 비재시도.
 */
private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
private val NOTICE_ID = NoticeId(NoticeNumber.of("SYN-OPEN-0001"), NoticeRound.of("000"))
private val GATES = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48)

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

private fun newOpeningCompleteSource(
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

class KonepsOpeningCompleteSourceTest {
    @Test
    fun `정상 다수 행 — 투찰자별 행이 모두 OPENING_RESULT sourceEndpoint 로 살아남는다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-A",
                    "bidprcAmt" to "950000000",
                    "bidprcrt" to "87.995",
                ),
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "2",
                    "prcbdrNm" to "SYN-B",
                    "bidprcAmt" to "960000000",
                    "bidprcrt" to "88.5",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 2, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            batch.items.size shouldBe 2
            batch.items.map { it.sourceEndpoint }.toSet() shouldBe setOf(SourceEndpoint.OPENING_RESULT)
            batch.accounting.duplicate shouldBe 0
        }
    }

    @Test
    fun `단일 낙찰자 — 한 행뿐이어도 정상 수집된다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-ONLY",
                    "bidprcAmt" to "950000000",
                    "bidprcrt" to "87.995",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            batch.items.size shouldBe 1
        }
    }

    @Test
    fun `협상 계약 — 투찰금액 투찰율이 없어도 상호는 보존된다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-NEGOTIATED",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            batch.items.size shouldBe 1
            batch.items.single().sourceText!! shouldContain "SYN-NEGOTIATED"
        }
    }

    @Test
    fun `추첨번호 부재 — drwtNo1 drwtNo2 가 없어도 행은 정상 수집된다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-NO-DRAW",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            batch.items.size shouldBe 1
            batch.items.single().sourceText!! shouldNotContain "drwtNo"
        }
    }

    @Test
    fun `순위 동값 — opengRank 1 이 둘이어도 두 행 모두 살아남는다(rowIdentifier 는 상호)`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-TIE-A",
                ),
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-TIE-B",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 2, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            batch.items.size shouldBe 2
            batch.accounting.duplicate shouldBe 0
        }
    }

    @Test
    fun `치환 — 사업자등록번호(prcbdrBizno)가 와도 fields sourceText 어디에도 남지 않는다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-BIZNO",
                    "prcbdrBizno" to "1234567890",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            val item = batch.items.single()
            item.keys shouldNotContain RawKey("prcbdrBizno")
            item.sourceText!! shouldNotContain "1234567890"
        }
    }

    @Test
    fun `치환 — 대표자명(prcbdrCeoNm)이 와도 fields sourceText 어디에도 남지 않는다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-CEO",
                    "prcbdrCeoNm" to "SYN-REP",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            val item = batch.items.single()
            item.keys shouldNotContain RawKey("prcbdrCeoNm")
            item.sourceText!! shouldNotContain "SYN-REP"
        }
    }

    @Test
    fun `치환 — 사업자등록번호와 대표자명이 함께 와도 둘 다 제외되고 상호만 남는다`() {
        val rows =
            listOf(
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "opengRank" to "1",
                    "prcbdrNm" to "SYN-BOTH",
                    "prcbdrBizno" to "1234567890",
                    "prcbdrCeoNm" to "SYN-REP",
                ),
            )
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 1, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newOpeningCompleteSource(server).fetchOpeningCompleteResults(fetchEvidence())

            val item = batch.items.single()
            item.keys shouldNotContain RawKey("prcbdrBizno")
            item.keys shouldNotContain RawKey("prcbdrCeoNm")
            item.sourceText!! shouldNotContain "1234567890"
            item.sourceText!! shouldNotContain "SYN-REP"
            item.sourceText!! shouldContain "SYN-BOTH"
        }
    }

    @Test
    fun `bidNtceNo 누락은 resultCode 08 비재시도다`() {
        val body = KonepsEnvelopeFixtures.failure("08", "필수값 누락")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch =
                newOpeningCompleteSource(server, testKonepsHttpPolicy(maxAttempts = 3))
                    .fetchOpeningCompleteResults(fetchEvidence())

            batch.items.shouldBeEmpty()
            batch.accounting.truncationCause shouldBe TruncationCause.InputError
            server.requestCount shouldBe 1
        }
    }
}
