package bidvector.adapters.koneps

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.FieldConcept
import bidvector.procurement.PageCursor
import bidvector.procurement.TruncationCause
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

private fun truncationCauseFor(
    script: List<MockKonepsResponse>,
    maxAttempts: Int = 1,
): TruncationCause? =
    MockKonepsServer.start(script).use { server ->
        newSource(server, testKonepsHttpPolicy(maxAttempts = maxAttempts))
            .fetchNotices(REFERENCE_DATE, null)
            .accounting
            .truncationCause
    }

private fun fieldContractFor(concept: FieldConcept) =
    resolvedCollectionPolicy(REFERENCE_DATE).fieldContracts.contractsFor(concept).first()

/**
 * ⑦ mock server 시나리오 — scope.md 「이 slice 가 하는 일」 ⑦, D-3B-4(loopback in-process,
 * 네트워크 0). 각 test 가 scope ⑦ 목록의 한 항목에 대응한다(대응표는 checklist.md).
 */
class KonepsOpenApiNoticeSourceTest {
    @Test
    fun `COL-01 — 4건 중 1건 공고번호 없음이면 3건 수집 + dropped 1`() {
        val items =
            listOf(
                mapOf("bidNtceNo" to "SYN-3B-0001", "bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "SYN-3B-0002", "bidNtceOrd" to "000"),
                mapOf("bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "SYN-3B-0004", "bidNtceOrd" to "000"),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 4, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 3
            batch.accounting.dropped shouldBe 1
            batch.accounting.dropReasons[CollectionDropReason.CollectionMissingNoticeNumber] shouldBe 1
            batch.accounting.truncated shouldBe false
            batch.next shouldBe null
        }
    }

    @Test
    fun `H-2 — 빈 문자열·공백 공고번호도 COL-01 탈락에 걸린다`() {
        val items =
            listOf(
                mapOf("bidNtceNo" to "", "bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "   ", "bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "SYN-3B-0009", "bidNtceOrd" to "   "),
                mapOf("bidNtceNo" to "SYN-3B-0010", "bidNtceOrd" to "000"),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 4, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.accounting.dropped shouldBe 3
            batch.accounting.dropReasons[CollectionDropReason.CollectionMissingNoticeNumber] shouldBe 3
        }
    }

    @Test
    fun `429 연속 실패 뒤 성공 — 재시도 상한 안에서 회복, 호출 횟수는 서버 카운터로 단언`() {
        val body =
            KonepsEnvelopeFixtures.success(
                listOf(mapOf("bidNtceNo" to "SYN-3B-0005", "bidNtceOrd" to "000")),
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
            // H-3 — 최종 성공 이전에 관측된 429 두 번이 quotaExceeded 로 남는다(스스로 회복
            // 해도 quota 압력을 겪은 사실 자체는 사라지지 않는다).
            batch.accounting.quotaExceeded shouldBe 2
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
        val items = listOf(mapOf("bidNtceNo" to "SYN-3B-0006", "bidNtceOrd" to "000"))
        val body = KonepsEnvelopeFixtures.success(items, totalCount = null, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxPages = 5)).fetchNotices(REFERENCE_DATE, null)

            batch.accounting.truncated shouldBe true
            batch.items.size shouldBe 1
            server.requestCount shouldBe 2
            // L-8 — 백스톱을 발동시킨 반복 페이지도 HTTP 호출은 실제로 나갔으니 센다.
            batch.accounting.pagesFetched shouldBe 2
            batch.accounting.truncationCause shouldBe TruncationCause.RepeatedPage
            // M-3 — 완료를 거짓 진술하지 않는다: 다음 페이지가 있을 수 있으니 재개 커서를 낸다.
            batch.next shouldNotBe null
        }
    }

    @Test
    fun `미지 resultCode 는 Unclassified 로 비재시도 실패한다`() {
        val body = KonepsEnvelopeFixtures.failure("99", "정의되지 않은 코드")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe true
            batch.accounting.truncationCause shouldBe TruncationCause.Unclassified
            server.requestCount shouldBe 1
            // N-4 — 비재시도 사유는 next 를 안 낸다(같은 실패를 무한 재개하지 않는다).
            batch.next shouldBe null
        }
    }

    @Test
    fun `resultCode 자체가 부재하면 Unclassified 로 비재시도 실패한다`() {
        val body = KonepsEnvelopeFixtures.ABSENT_RESULT_CODE
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.items.shouldBeEmpty()
            batch.accounting.truncated shouldBe true
            batch.accounting.truncationCause shouldBe TruncationCause.Unclassified
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `계약에 없는 raw 키는 항목을 살리되 unknownFields 로 계수한다 COL-07`() {
        val items =
            listOf(
                mapOf(
                    "bidNtceNo" to "SYN-3B-0007",
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
                listOf(mapOf("bidNtceNo" to "SYN-3B-0008", "bidNtceOrd" to "000")),
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
            batch.accounting.quotaExceeded shouldBe 1
        }
    }

    @Test
    fun `resultCode 30 은 등록되지 않은 서비스 키 — 비재시도`() {
        val body = KonepsEnvelopeFixtures.failure("30", "등록되지 않은 서비스키")
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxAttempts = 3)).fetchNotices(REFERENCE_DATE, null)

            batch.accounting.truncated shouldBe true
            batch.accounting.truncationCause shouldBe TruncationCause.NotRetryable
            server.requestCount shouldBe 1
            // N-4 — 비재시도 사유는 next 를 안 낸다.
            batch.next shouldBe null
        }
    }

    @Test
    fun `N-4 — 재시도로 뚫릴 수 있는 사유(반복 페이지)는 next 를 낸다`() {
        val items = listOf(mapOf("bidNtceNo" to "SYN-3B-0018", "bidNtceOrd" to "000"))
        val body = KonepsEnvelopeFixtures.success(items, totalCount = null, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy(maxPages = 5)).fetchNotices(REFERENCE_DATE, null)

            batch.accounting.truncationCause shouldBe TruncationCause.RepeatedPage
            batch.next shouldNotBe null
        }
    }

    @Test
    fun `N-5 — cursor 토큰의 선행 0·부호 기호도 조용히 수용하지 않고 명시 실패를 낸다`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, "{}"))).use { server ->
            val source = newSource(server, testKonepsHttpPolicy())

            val leadingZero = source.fetchNotices(REFERENCE_DATE, PageCursor("007"))
            val plusSign = source.fetchNotices(REFERENCE_DATE, PageCursor("+4"))
            val leadingSpace = source.fetchNotices(REFERENCE_DATE, PageCursor(" 3"))

            leadingZero.accounting.truncationCause shouldBe TruncationCause.InputError
            plusSign.accounting.truncationCause shouldBe TruncationCause.InputError
            leadingSpace.accounting.truncationCause shouldBe TruncationCause.InputError
            server.requestCount shouldBe 0
        }
    }

    @Test
    fun `H-3 — 다섯 truncation 사유가 회계에서 바이트 동일하지 않고 서로 구별된다`() {
        val quota429 = truncationCauseFor(List(3) { MockKonepsResponse.Reply(429, "") }, maxAttempts = 2)
        val quota22 =
            truncationCauseFor(
                List(3) { MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.failure("22", "quota")) },
                maxAttempts = 2,
            )
        val unclassified =
            truncationCauseFor(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.failure("99", "?"))))
        val structureFailure = truncationCauseFor(listOf(MockKonepsResponse.Reply(200, "not-json")))
        val notRetryable =
            truncationCauseFor(listOf(MockKonepsResponse.Reply(200, KonepsEnvelopeFixtures.failure("30", "bad key"))))

        quota429 shouldBe TruncationCause.QuotaExhausted
        quota22 shouldBe TruncationCause.QuotaExhausted
        unclassified shouldBe TruncationCause.Unclassified
        structureFailure shouldBe TruncationCause.StructureFailure
        notRetryable shouldBe TruncationCause.NotRetryable
        setOf(quota429, quota22, unclassified, structureFailure, notRetryable).size shouldBe 4
    }

    @Test
    fun `M-1 — JSON boolean 은 Y N 으로 바뀌지 않고 원문 토큰 텍스트 그대로 옮겨진다`() {
        val body =
            """{"response":{"header":{"resultCode":"00","resultMsg":"ok"},"body":{"items":""" +
                """[{"bidNtceNo":"SYN-3B-0015","bidNtceOrd":"000","bsnsDivNm":true}],""" +
                """"numOfRows":100,"pageNo":1,"totalCount":1}}}"""
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.items.first().valueOf(fieldContractFor(FieldConcept.BUSINESS_CATEGORY_LABEL)) shouldBe "true"
        }
    }

    @Test
    fun `F-7 3B 몫 — sourceText 는 서빙한 항목 바이트와 정확히 같다(불규칙 공백·키 순서 포함)`() {
        // 표준 정렬(키 알파벳 순)과 다른 순서 + 불규칙 공백을 일부러 둔다 — 재직렬화를
        // 거치면(render() 등) 이 형태가 사라진다. 3D `RawObservationSourceTextRoundTripTest`
        // 가 같은 문자열로 저장 계층까지의 바이트 동일성을 이어서 잰다(F-7 운영자 결정 (a)).
        val servedItemBytes = "{\"bidNtceOrd\":  \"000\", \"bidNtceNo\":\"SRC-TEXT-001\"}"
        val body =
            """{"response":{"header":{"resultCode":"00","resultMsg":"ok"},"body":{"items":""" +
                """[$servedItemBytes],"numOfRows":100,"pageNo":1,"totalCount":1}}}"""
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.items.single().sourceText shouldBe servedItemBytes
        }
    }

    @Test
    fun `M-2 — canonical 공고번호가 같으면 원문 표기가 달라도 duplicate 로 계수된다`() {
        val items =
            listOf(
                mapOf("bidNtceNo" to "vp 0004", "bidNtceOrd" to "000"),
                mapOf("bidNtceNo" to "VP-0004", "bidNtceOrd" to "000"),
            )
        val body = KonepsEnvelopeFixtures.success(items, totalCount = 2, pageNo = 1, numOfRows = 100)
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.accounting.duplicate shouldBe 1
        }
    }

    @Test
    fun `M-5 — cursor 토큰이 숫자가 아니거나 0 이하면 조용히 page 1 로 접지 않고 명시 실패를 낸다`() {
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, "{}"))).use { server ->
            val source = newSource(server, testKonepsHttpPolicy())

            val opaque = source.fetchNotices(REFERENCE_DATE, PageCursor("opaque-token"))
            val zero = source.fetchNotices(REFERENCE_DATE, PageCursor("0"))
            val negative = source.fetchNotices(REFERENCE_DATE, PageCursor("-5"))

            opaque.accounting.truncationCause shouldBe TruncationCause.InputError
            zero.accounting.truncationCause shouldBe TruncationCause.InputError
            negative.accounting.truncationCause shouldBe TruncationCause.InputError
            opaque.items.shouldBeEmpty()
            server.requestCount shouldBe 0
        }
    }

    @Test
    fun `L-5 — 항목 안 중복 JSON 키는 마지막 값이 승리한다`() {
        val body =
            """{"response":{"header":{"resultCode":"00","resultMsg":"ok"},"body":{"items":""" +
                """[{"bidNtceNo":"SYN-3B-0016","bidNtceNo":"SYN-3B-0017","bidNtceOrd":"000"}],""" +
                """"numOfRows":100,"pageNo":1,"totalCount":1}}}"""
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newSource(server, testKonepsHttpPolicy()).fetchNotices(REFERENCE_DATE, null)

            batch.items.size shouldBe 1
            batch.items.first().valueOf(fieldContractFor(FieldConcept.NOTICE_NUMBER)) shouldBe "SYN-3B-0017"
        }
    }

    @Test
    fun `M-4 — JSON 중첩 깊이가 정책 상한을 넘으면 StructureFailure 로 접히고 예외가 안 샌다`() {
        val deepArray = "[".repeat(50) + "]".repeat(50)
        val body =
            """{"response":{"header":{"resultCode":"00"},"body":{"items":$deepArray,""" +
                """"numOfRows":1,"pageNo":1,"totalCount":0}}}"""
        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch =
                newSource(server, testKonepsHttpPolicy(maxAttempts = 1, maxJsonDepth = 10))
                    .fetchNotices(REFERENCE_DATE, null)

            batch.accounting.truncationCause shouldBe TruncationCause.StructureFailure
        }
    }
}
