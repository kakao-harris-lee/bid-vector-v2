package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.QualificationFetchDecision
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.procurement.decideQualificationFetch
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
private val NOTICE_ID = NoticeId(NoticeNumber.of("SYN-LIC-0001"), NoticeRound.of("000"))

private fun newSource(
    server: MockKonepsServer,
    policy: KonepsHttpPolicyData = testKonepsHttpPolicy(),
): KonepsLicenseLimitDocumentSource =
    KonepsLicenseLimitDocumentSource(
        baseUri = server.baseUri,
        config =
            KonepsSourceConfig(
                httpClient = HttpClient.newHttpClient(),
                serviceKey = ServiceKey.of("test-service-key"),
                httpPolicy = policy,
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                clock = FIXED_CLOCK,
            ),
    )

private fun fetchEvidence(): QualificationFetchDecision.Fetch {
    val decision = decideQualificationFetch(NOTICE_ID, industryRestricted = true)
    check(decision is QualificationFetchDecision.Fetch) { "술어가 Fetch 를 내지 않았다: $decision" }
    return decision
}

/** ③④, D-3B2-5 (a), D-3B2-6 (a) — `DocumentSourcePort` 구현(license-limit). */
class KonepsLicenseLimitDocumentSourceTest {
    @Test
    fun `업종제한이 있으면 정확히 1회 조회하고 LICENSE_LIMIT_DETAIL 로 낸다`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(
                    mapOf(
                        "bidNtceNo" to NOTICE_ID.number.value,
                        "bidNtceOrd" to "000",
                        "lcnsLmtNm" to "SYN-001/전기공사업",
                        "permsnIndstrytyList" to "[SYN-업종/0001]",
                    ),
                ),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchQualificationText(fetchEvidence())

            batch.items.size shouldBe 1
            batch.items.single().sourceEndpoint shouldBe SourceEndpoint.LICENSE_LIMIT_DETAIL
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `미등재 응답 키(indstrytyMfrcFldList)는 COL-07 대로 unknownFields 로 계수된다 — 전체 원문 보존(masking 없음)`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(
                    mapOf(
                        "bidNtceNo" to NOTICE_ID.number.value,
                        "bidNtceOrd" to "000",
                        "indstrytyMfrcFldList" to "SYN-주력업종",
                    ),
                ),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchQualificationText(fetchEvidence())

            batch.accounting.unknownFields shouldBe 1
        }
    }

    @Test
    fun `제한 없음(resultCode 00 + totalCount 0)은 성공 + 빈 항목이다 — COL-04 첫째 acceptance`() {
        val body = KonepsEnvelopeFixtures.success(emptyList(), totalCount = 0, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchQualificationText(fetchEvidence())

            batch.items.shouldBeEmpty()
            batch.accounting.sourceTotal shouldBe 0
            batch.accounting.truncated shouldBe false
        }
    }

    @Test
    fun `resultCode 03 은 데이터 없음이다`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.NO_DATA))).use { server ->
            val batch = newSource(server).fetchQualificationText(fetchEvidence())

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe false
        }
    }

    @Test
    fun `resultCode 08 은 필수값 입력 에러 — bidNtceOrd 누락 시나리오, 재시도 아님`() {
        val body = KonepsEnvelopeFixtures.failure("08", "필수값 입력 에러")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchQualificationText(fetchEvidence())

            batch.accounting.truncated shouldBe true
            batch.accounting.truncationCause shouldBe TruncationCause.InputError
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `F-1 — 제한그룹 3행이 모두 살아남는다(같은 공고, lmtGrpNo x lmtSno 축)`() {
        val rows =
            (1..3).map { sno ->
                mapOf(
                    "bidNtceNo" to NOTICE_ID.number.value,
                    "bidNtceOrd" to "000",
                    "lmtGrpNo" to "1",
                    "lmtSno" to sno.toString(),
                    "lcnsLmtNm" to "SYN-00$sno/전기공사업",
                )
            }
        val body = KonepsEnvelopeFixtures.success(rows, totalCount = 3, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server).fetchQualificationText(fetchEvidence())

            batch.items.size shouldBe 3
            batch.accounting.received shouldBe 3
            batch.accounting.duplicate shouldBe 0
        }
    }

    @Test
    fun `F-2 — resultCode 22 는 quota 초과로 재시도 대상이다(bounded retry)`() {
        val row =
            mapOf("bidNtceNo" to NOTICE_ID.number.value, "bidNtceOrd" to "000", "lmtGrpNo" to "1", "lmtSno" to "1")
        val successBody =
            KonepsEnvelopeFixtures.success(
                listOf(row),
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
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchQualificationText(fetchEvidence())

            batch.items.size shouldBe 1
            batch.accounting.quotaExceeded shouldBe 1
            server.requestCount shouldBe 2
        }
    }
}
