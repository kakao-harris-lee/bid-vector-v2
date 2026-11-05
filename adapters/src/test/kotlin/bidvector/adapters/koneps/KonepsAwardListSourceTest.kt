package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.RawKey
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val AWARD_REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 10, 20))
private val AWARD_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)

private fun awardSource(server: MockKonepsServer) =
    konepsAwardListSource(
        listBaseUri = server.baseUri,
        config =
            KonepsSourceConfig(
                gate = testCallGate(),
                serviceKey = ServiceKey.of("test-service-key"),
                httpPolicy = testKonepsHttpPolicy(),
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                clock = AWARD_CLOCK,
            ),
    )

/**
 * M9/award-ingest — 낙찰 목록 포트. 잠그는 것 셋: ① 요청이 **개찰일시 축 + 하루 창**으로 나간다
 * ② 항목이 `OPENING_AWARD_LIST` 로 관측된다 ③ 계약 미등재 식별자(사업자번호·대표자명)는 담기지 않는다.
 */
class KonepsAwardListSourceTest {
    @Test
    fun `낙찰 목록은 개찰일시 축과 하루 창으로 요청하고 OPENING_AWARD_LIST 로 관측한다`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(
                    mapOf(
                        "bidNtceNo" to "SYN-AWARD-0001",
                        "bidNtceOrd" to "000",
                        "bidwinnrNm" to "SYN-WINNER",
                        "sucsfbidAmt" to "1200000000",
                    ),
                ),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = awardSource(server).fetchAwardList(AWARD_REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.items.single().sourceEndpoint shouldBe SourceEndpoint.OPENING_AWARD_LIST
            // 개찰 축 오퍼레이션은 업무 대분류를 정하지 않는다(응답에서 추측하지 않는다).
            batch.items.single().sourceDivision shouldBe null
            val query = requireNotNull(server.lastRequestQuery)
            query.contains("inqryDiv=3") shouldBe true
            query.contains("inqryBgnDt=202610200000") shouldBe true
            query.contains("inqryEndDt=202610202359") shouldBe true
        }
    }

    @Test
    fun `계약에 없는 낙찰자 식별자는 관측에 담기지 않는다 — 금액·상호는 담긴다`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(
                    mapOf(
                        "bidNtceNo" to "SYN-AWARD-0002",
                        "bidNtceOrd" to "000",
                        "bidwinnrNm" to "SYN-WINNER",
                        "sucsfbidAmt" to "1200000000",
                        "bidwinnrBizno" to "SYN-BIZNO",
                        "bidwinnrCeoNm" to "SYN-CEO",
                    ),
                ),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val observation = awardSource(server).fetchAwardList(AWARD_REFERENCE_DATE, null).items.single()

            val keys = observation.keys
            // 양성 대조 — 계약 등재 키는 남는다(부재 단언이 빈 관측을 보고 초록이 되지 않는다).
            keys.contains(RawKey("bidwinnrNm")) shouldBe true
            keys.contains(RawKey("sucsfbidAmt")) shouldBe true
            keys.contains(RawKey("bidwinnrBizno")) shouldBe false
            keys.contains(RawKey("bidwinnrCeoNm")) shouldBe false
            // 원문 보존 텍스트에도 남지 않는다 — 감사 통로로 새는 길이 없다.
            val sourceText = requireNotNull(observation.sourceText)
            sourceText.contains("SYN-BIZNO") shouldBe false
            sourceText.contains("SYN-CEO") shouldBe false
            sourceText.contains("SYN-WINNER") shouldBe true
        }
    }
}
