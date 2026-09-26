package bidvector.adapters.koneps

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionReferenceDate
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 24))
private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

private fun sourceOf(
    server: MockKonepsServer,
    division: BusinessDivision,
): KonepsOpenApiNoticeSource =
    KonepsOpenApiNoticeSource(
        httpClient = HttpClient.newHttpClient(),
        baseUri = server.baseUri,
        serviceKey = ServiceKey.of("test-service-key"),
        httpPolicy = testKonepsHttpPolicy(),
        collectionPolicyProvider = ::resolvedCollectionPolicy,
        businessDivision = division,
        clock = FIXED_CLOCK,
    )

private fun twoItems(extra: Map<String, String> = emptyMap()): String =
    KonepsEnvelopeFixtures.success(
        listOf(
            mapOf("bidNtceNo" to "SYN-6F9-0001", "bidNtceOrd" to "000") + extra,
            mapOf("bidNtceNo" to "SYN-6F9-0002", "bidNtceOrd" to "000") + extra,
        ),
        totalCount = 2,
        pageNo = 1,
        numOfRows = 100,
    )

/**
 * D-6F9-1(M6/6F-9) — 업무 대분류는 응답 필드가 아니라 **수집 오퍼레이션**이 정한다. 어댑터는 호출부가 데이터(설정 표)에서
 * 넘긴 값을 관측에 **구조로** 싣고, URL 문자열이나 응답의 다른 필드에서 얻지 않는다(위협 모델 우회 1).
 */
class KonepsSourceDivisionTest {
    @Test
    fun `공고 목록 관측은 어댑터가 받은 대분류를 항목마다 싣는다 — 넷 모두`() {
        BusinessDivision.entries.forEach { division ->
            MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, twoItems()))).use { server ->
                val batch = sourceOf(server, division).fetchNotices(REFERENCE_DATE, null)

                withClue(division.name) {
                    batch.items.map { it.sourceDivision } shouldBe listOf(division, division)
                }
            }
        }
    }

    @Test
    fun `대분류는 URL 경로에서 파싱하지 않는다 — 경로가 용역 오퍼레이션이어도 받은 값이 이긴다`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, twoItems()))).use { server ->
            server.baseUri.path.contains("Servc") shouldBe true

            val batch = sourceOf(server, BusinessDivision.CONSTRUCTION).fetchNotices(REFERENCE_DATE, null)

            batch.items.map { it.sourceDivision }.toSet() shouldBe setOf(BusinessDivision.CONSTRUCTION)
        }
    }

    @Test
    fun `응답이 다른 업무구분 라벨을 실어도 관측의 대분류는 어댑터 값이다 — 응답에서 추측하지 않는다`() {
        val body = twoItems(mapOf("bsnsDivNm" to "공사"))
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = sourceOf(server, BusinessDivision.SERVICE).fetchNotices(REFERENCE_DATE, null)

            batch.items.map { it.sourceDivision }.toSet() shouldBe setOf(BusinessDivision.SERVICE)
        }
    }
}
