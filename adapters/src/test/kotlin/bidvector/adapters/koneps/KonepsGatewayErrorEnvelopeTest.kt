package bidvector.adapters.koneps

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.ResultCodeCategory
import bidvector.procurement.TruncationCause
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val GATEWAY_REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 27))
private val GATEWAY_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneOffset.UTC)

/**
 * data.go.kr 게이트웨이가 한도 초과·키 오류를 낼 때의 XML 오류 봉투 — `type=json` 을 보내도
 * **게이트웨이 층**(서비스 앞단)이 거절하면 XML 로 온다. 봉투는 `cmmMsgHeader` 안에
 * `returnReasonCode` 를 싣는다(코드 어휘는 서비스의 `resultCode` 와 같은 표를 쓴다).
 */
private fun gatewayErrorXml(
    reasonCode: String,
    authMsg: String,
): String =
    """<?xml version="1.0" encoding="UTF-8"?>
<OpenAPI_ServiceResponse>
  <cmmMsgHeader>
    <errMsg>SERVICE ERROR</errMsg>
    <returnAuthMsg>$authMsg</returnAuthMsg>
    <returnReasonCode>$reasonCode</returnReasonCode>
  </cmmMsgHeader>
</OpenAPI_ServiceResponse>
"""

private fun gatewaySource(server: MockKonepsServer): KonepsOpenApiNoticeSource =
    KonepsOpenApiNoticeSource(
        httpClient = HttpClient.newHttpClient(),
        baseUri = server.baseUri,
        serviceKey = ServiceKey.of("test-service-key"),
        httpPolicy = testKonepsHttpPolicy(maxAttempts = 3),
        collectionPolicyProvider = ::resolvedCollectionPolicy,
        businessDivision = BusinessDivision.SERVICE,
        clock = GATEWAY_CLOCK,
    )

/**
 * `OPEN-6F8-QUOTA-XML-ENVELOPE` 폐쇄(M6/6G D-6G-11) — 게이트웨이 XML 오류 봉투가
 * `StructureFailure` 로 접히면 use case 가 「다음 슬롯」으로 넘어가 남은 슬롯마다 거부된
 * 호출을 한 번씩 더 낸다. 한도 초과는 **멈춤**이어야 한다.
 */
class KonepsGatewayErrorEnvelopeTest {
    @Test
    fun `한도 초과 XML 봉투는 구조 실패가 아니라 quota 범주로 판정된다`() {
        val policy = resolvedCollectionPolicy(GATEWAY_REFERENCE_DATE)

        val outcome =
            parseKonepsEnvelope(
                gatewayErrorXml("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                policy,
                maxJsonDepth = 32,
            )

        val classified = outcome.shouldBeInstanceOf<KonepsEnvelopeOutcome.Classified>()
        classified.category shouldBe ResultCodeCategory.QUOTA_EXCEEDED
        classified.code shouldBe "22"
    }

    @Test
    fun `게이트웨이 XML 봉투의 코드 어휘는 JSON 봉투와 같은 표를 쓴다 — 키 오류는 비재시도`() {
        val policy = resolvedCollectionPolicy(GATEWAY_REFERENCE_DATE)

        val outcome = parseKonepsEnvelope(gatewayErrorXml("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"), policy, 32)

        val classified = outcome.shouldBeInstanceOf<KonepsEnvelopeOutcome.Classified>()
        classified.category shouldBe ResultCodeCategory.NOT_RETRYABLE
    }

    @Test
    fun `XML 이지만 코드 원소가 없으면 여전히 구조 실패다 — 지어내지 않는다`() {
        val policy = resolvedCollectionPolicy(GATEWAY_REFERENCE_DATE)

        val outcome = parseKonepsEnvelope("<html><body>502 Bad Gateway</body></html>", policy, 32)

        outcome.shouldBeInstanceOf<KonepsEnvelopeOutcome.StructureFailure>()
    }

    @Test
    fun `XML 봉투 판독 실패의 사유 문자열에 응답 본문 조각이 실리지 않는다`() {
        val policy = resolvedCollectionPolicy(GATEWAY_REFERENCE_DATE)
        val leaky = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>serviceKey=SECRET-KEY-VALUE"

        val outcome = parseKonepsEnvelope(leaky, policy, 32)

        val reason = outcome.shouldBeInstanceOf<KonepsEnvelopeOutcome.StructureFailure>().reason
        reason shouldNotContain "SECRET-KEY-VALUE"
        reason shouldNotContain "serviceKey"
    }

    @Test
    fun `외부 엔티티를 참조하는 XML 은 해석되지 않고 구조 실패로 접힌다`() {
        val policy = resolvedCollectionPolicy(GATEWAY_REFERENCE_DATE)
        val xxe =
            """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" +
                """<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>&x;</returnReasonCode>""" +
                """</cmmMsgHeader></OpenAPI_ServiceResponse>"""

        val outcome = parseKonepsEnvelope(xxe, policy, 32)

        outcome.shouldBeInstanceOf<KonepsEnvelopeOutcome.StructureFailure>()
    }

    @Test
    fun `일 한도 초과는 즉시 멈춘다 — 재시도로 거부된 호출을 더 내지 않는다`() {
        val script = List(3) { MockKonepsResponse.Reply(200, gatewayErrorXml("22", "LIMITED_NUMBER")) }

        MockKonepsServer.start(script).use { server ->
            val batch = gatewaySource(server).fetchNotices(GATEWAY_REFERENCE_DATE, null)

            batch.accounting.truncationCause shouldBe TruncationCause.QuotaExhausted
            // 재시도 상한이 3 이어도 호출은 하나다 — 한도 초과는 백오프로 회복되지 않는다.
            server.requestCount shouldBe 1
            batch.accounting.quotaExceeded shouldBe 1
            // 멈춤 사유라 이어 읽을 커서를 내지 않는다.
            batch.next shouldBe null
        }
    }

    @Test
    fun `429 속도 한도는 그대로 재시도된다 — 일 한도와 다른 축이다`() {
        val successBody =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-6G-0001", "bidNtceOrd" to "000")),
                totalCount = 1,
                pageNo = 1,
                numOfRows = 100,
            )
        val script =
            listOf(
                MockKonepsResponse.Reply(429, ""),
                MockKonepsResponse.Reply(200, successBody),
            )

        MockKonepsServer.start(script).use { server ->
            val batch = gatewaySource(server).fetchNotices(GATEWAY_REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            server.requestCount shouldBe 2
            batch.accounting.quotaExceeded shouldBe 1
        }
    }
}
