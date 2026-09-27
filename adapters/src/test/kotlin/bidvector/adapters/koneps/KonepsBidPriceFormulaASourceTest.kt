package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.FieldConcept
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.decideDetailFetch
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneOffset.UTC)
private val NOTICE_ID = NoticeId(NoticeNumber.of("SYN-6G-A0001"), NoticeRound.of("001"))
private val GATES = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48)

private fun contractRegistry() =
    resolvedCollectionPolicy(CollectionReferenceDate(LocalDate.of(2026, 9, 27))).fieldContracts

private fun fetchEvidence(): DetailFetchDecision.Fetch {
    val decision =
        decideDetailFetch(
            NOTICE_ID,
            alreadyHeld = false,
            openingObservedAt = null,
            lastCheckedAt = null,
            now = Instant.parse("2026-09-27T00:00:00Z"),
            gates = GATES,
        )
    check(decision is DetailFetchDecision.Fetch) { "술어가 Fetch 를 내지 않았다: $decision" }
    return decision
}

private fun newFormulaASource(server: MockKonepsServer): KonepsOpeningResultSource =
    KonepsOpeningResultSource(
        listBaseUri = server.baseUri,
        listOperation = KonepsOperationPolicy.AWARD_LIST,
        listSourceEndpoint = SourceEndpoint.OPENING_AWARD_LIST,
        reserveDetailBaseUri = server.baseUri,
        openingCompleteBaseUri = server.baseUri,
        bidPriceFormulaABaseUri = server.baseUri,
        config =
            KonepsSourceConfig(
                httpClient = HttpClient.newHttpClient(),
                serviceKey = ServiceKey.of("test-service-key"),
                httpPolicy = testKonepsHttpPolicy(),
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                clock = FIXED_CLOCK,
            ),
    )

/** 문서 응답 항목 전부를 담은 한 행 — 실제 값은 합성이다(문서 샘플의 바이트를 옮기지 않는다). */
private val FULL_A_ROW =
    mapOf(
        "bidNtceNo" to NOTICE_ID.number.value,
        "bidNtceOrd" to "001",
        "npnInsrprm" to "260853707",
        "mrfnHealthInsrprm" to "205494753",
        "odsnLngtrmrcprInsrprm" to "26611570",
        "rtrfundNon" to "133325228",
        "sftyMngcst" to "314503783",
        "sftyChckMngcst" to "127430000",
        "qltyMngcst" to "95795075",
        "qltyMngcstAObjYn" to "Y",
        "smkpAmt" to "2354369710",
        "smkpAmtYn" to "N",
        "prearngPrceDcsnMthdNm" to "복수예가",
        "ntceNticeDt" to "2026-06-03 09:39:16",
        "bidPrceCalclAOpenDt" to "2026-06-16 16:10:19",
    )

/**
 * M6/6G D-6G-12 — 입찰가격산식 A 정보 오퍼레이션. 공사 하한가는 `(예정가격 − A) × r + A` 라
 * A 를 0 으로 두면 하한가를 낮게 잡고 적격 판정 자체가 틀린다.
 */
class KonepsBidPriceFormulaASourceTest {
    @Test
    fun `A 합산 항목 일곱과 술어 둘 공개일시 하나가 모두 관측으로 살아남는다`() {
        val body = KonepsEnvelopeFixtures.success(listOf(FULL_A_ROW), totalCount = 1, pageNo = 1, numOfRows = 100)

        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newFormulaASource(server).fetchBidPriceFormulaA(fetchEvidence())

            batch.items.size shouldBe 1
            val observation = batch.items.single()
            observation.sourceEndpoint shouldBe SourceEndpoint.BID_PRICE_FORMULA_A
            FULL_A_ROW.keys.forEach { key -> observation.keys shouldContain RawKey(key) }
            batch.accounting.unknownFields shouldBe 0
        }
    }

    @Test
    fun `A 합산 항목 일곱은 서로 다른 개념이다 — 하나가 나머지를 가리지 않는다`() {
        val registry = contractRegistry()
        val componentKeys =
            listOf(
                "npnInsrprm",
                "mrfnHealthInsrprm",
                "odsnLngtrmrcprInsrprm",
                "rtrfundNon",
                "sftyMngcst",
                "sftyChckMngcst",
                "qltyMngcst",
            )

        val concepts = componentKeys.map { key -> registry.contractFor(RawKey(key))?.concept }

        concepts shouldNotContain null
        concepts.toSet().size shouldBe componentKeys.size
        // 개념마다 계약이 하나뿐이어야 `valueIn`(개념의 첫 계약만 읽는다)이 조용히 다른 키를
        // 고르지 않는다.
        concepts.filterNotNull().forEach { concept -> registry.contractsFor(concept).size shouldBe 1 }
    }

    @Test
    fun `요청은 inqryDiv 2 와 공고번호를 싣고 차수 파라미터는 보내지 않는다`() {
        val body = KonepsEnvelopeFixtures.success(listOf(FULL_A_ROW), totalCount = 1, pageNo = 1, numOfRows = 100)

        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            newFormulaASource(server).fetchBidPriceFormulaA(fetchEvidence())

            val query = requireNotNull(server.lastRequestQuery)
            query shouldContain "inqryDiv=2"
            query shouldContain "bidNtceNo=${NOTICE_ID.number.value}"
            // 문서가 요청 항목으로 적지 않는 파라미터를 지어내 보내지 않는다.
            query shouldNotContain "bidNtceOrd="
            query shouldNotContain "inqryBgnDt"
        }
    }

    @Test
    fun `계약에 없는 키는 관측이 되기 전에 떨어진다 — allow-list 반전이 이 축에도 선다`() {
        val row = FULL_A_ROW + mapOf("prcbdrBizno" to "1234567890", "prcbdrCeoNm" to "SYN-CEO")
        val body = KonepsEnvelopeFixtures.success(listOf(row), totalCount = 1, pageNo = 1, numOfRows = 100)

        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newFormulaASource(server).fetchBidPriceFormulaA(fetchEvidence())

            val observation = batch.items.single()
            observation.keys shouldNotContain RawKey("prcbdrBizno")
            observation.keys shouldNotContain RawKey("prcbdrCeoNm")
            observation.sourceText shouldNotContain "SYN-CEO"
        }
    }

    @Test
    fun `공고게시일시와 A 공개일시는 다른 축이다 — 한 값으로 접히지 않는다`() {
        val body = KonepsEnvelopeFixtures.success(listOf(FULL_A_ROW), totalCount = 1, pageNo = 1, numOfRows = 100)

        MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            val batch = newFormulaASource(server).fetchBidPriceFormulaA(fetchEvidence())
            val registry = contractRegistry()
            val observation = batch.items.single()

            val posted = registry.contractsFor(FieldConcept.NOTICE_POSTED_AT).single()
            val disclosed = registry.contractsFor(FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT).single()

            posted.rawName shouldBe RawKey("ntceNticeDt")
            disclosed.rawName shouldBe RawKey("bidPrceCalclAOpenDt")
            observation.valueOf(posted) shouldBe "2026-06-03 09:39:16"
            observation.valueOf(disclosed) shouldBe "2026-06-16 16:10:19"
        }
    }
}
