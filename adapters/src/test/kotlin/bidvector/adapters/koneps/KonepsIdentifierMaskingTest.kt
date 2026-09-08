package bidvector.adapters.koneps

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.RawKey
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 9, 8))
private val OBSERVED_AT = Instant.parse("2026-09-08T00:00:00Z")
private val POLICY = resolvedCollectionPolicy(REFERENCE_DATE)

private fun item(json: String): JsonValue.JsonObject = KonepsJsonParser.parse(json, 32).asObject()!!

private const val SINGLE_WINNER_ITEM =
    """{"bidNtceNo":"SYN-MASK-0001","bidNtceOrd":"000","opengCorpInfo":"SYN-CORP^1234567890^SYN-REP^100000000^95.5"}"""
private const val MULTI_WINNER_ITEM =
    """{"bidNtceNo":"SYN-MASK-0002","bidNtceOrd":"000","opengCorpInfo":"낙찰예정자 다수^100000000^95.5"}"""
private const val NEGOTIATED_CONTRACT_ITEM =
    """{"bidNtceNo":"SYN-MASK-0003","bidNtceOrd":"000","opengCorpInfo":"SYN-CORP^1234567890^SYN-REP"}"""
private const val UNREGISTERED_IDENTIFIER_ITEM =
    """{"bidNtceNo":"SYN-MASK-0004","bidNtceOrd":"000","bidwinnrNm":"SYN-CORP","bidwinnrBizno":"1234567890"}"""

/**
 * 게이트 ① — P-10 (a) 식별자 치환. `opengCorpInfo` 세 변형(단일·다수·협상)과 allow-list
 * 반전(계약 없는 키가 `fields`·`sourceText` 둘 다에 없다)을 고정한다(설계 검토 「구현 레인에
 * 주는 결론」①).
 */
class KonepsIdentifierMaskingTest {
    @Test
    fun `단일 낙찰자 5성분은 업체명·투찰금액·투찰율만 남기고 사업자번호·대표자명을 버린다`() {
        val raw = item(SINGLE_WINNER_ITEM)

        val outcome =
            mapMaskedOpeningItem(raw, POLICY, SourceEndpoint.OPENING_RESULT_LIST, OBSERVED_AT, emptyList())

        val mapped = outcome.shouldBeInstanceOf<RawItemOutcome.Mapped>()
        val contract = POLICY.fieldContracts.contractFor(RawKey("opengCorpInfo"))!!
        mapped.observation.valueOf(contract) shouldBe "SYN-CORP^100000000^95.5"
        mapped.observation.sourceText!! shouldNotContain "1234567890"
        mapped.observation.sourceText!! shouldNotContain "SYN-REP"
        mapped.unknownFieldCount shouldBe 0
        mapped.maskingFailureCount shouldBe 0
    }

    @Test
    fun `낙찰예정자 다수(3성분, 이름 자리가 안내문)는 위치 추측 없이 값 전체를 폐기한다`() {
        val raw = item(MULTI_WINNER_ITEM)

        val outcome =
            mapMaskedOpeningItem(raw, POLICY, SourceEndpoint.OPENING_RESULT_LIST, OBSERVED_AT, emptyList())

        val mapped = outcome.shouldBeInstanceOf<RawItemOutcome.Mapped>()
        mapped.observation.keys shouldNotContain RawKey("opengCorpInfo")
        mapped.observation.sourceText!! shouldNotContain "opengCorpInfo"
        // F-3·F-8(verifier r1) 수정 — masking 실패는 별도 축(maskingFailureCount)이고,
        // unknownFieldCount(계약 밖 키 수)는 이 항목에 그런 키가 없으므로 0 이다.
        mapped.unknownFieldCount shouldBe 0
        mapped.maskingFailureCount shouldBe 1
    }

    @Test
    fun `협상 계약(3성분, 투찰금액·투찰율 없음)도 같은 이유로 값 전체를 폐기한다`() {
        val raw = item(NEGOTIATED_CONTRACT_ITEM)

        val outcome =
            mapMaskedOpeningItem(raw, POLICY, SourceEndpoint.OPENING_RESULT_LIST, OBSERVED_AT, emptyList())

        val mapped = outcome.shouldBeInstanceOf<RawItemOutcome.Mapped>()
        mapped.observation.keys shouldNotContain RawKey("opengCorpInfo")
        mapped.observation.sourceText!! shouldNotContain "1234567890"
        mapped.unknownFieldCount shouldBe 0
        mapped.maskingFailureCount shouldBe 1
    }

    @Test
    fun `allow-list 반전 — 계약 없는 키(사업자등록번호)는 fields 와 sourceText 둘 다에 없고 unknownFieldCount 로 계수된다`() {
        val raw = item(UNREGISTERED_IDENTIFIER_ITEM)

        val outcome =
            mapMaskedOpeningItem(raw, POLICY, SourceEndpoint.OPENING_AWARD_LIST, OBSERVED_AT, emptyList())

        val mapped = outcome.shouldBeInstanceOf<RawItemOutcome.Mapped>()
        mapped.observation.keys shouldNotContain RawKey("bidwinnrBizno")
        mapped.observation.sourceText!! shouldNotContain "1234567890"
        val nameContract = POLICY.fieldContracts.contractFor(RawKey("bidwinnrNm"))!!
        mapped.observation.valueOf(nameContract) shouldBe "SYN-CORP"
        // F-3(verifier r1) 수정 — allow-list 가 떨어뜨린 계약 밖 키(bidwinnrBizno) 1개가
        // 이제 unknownFieldCount 로 관측된다(이전 판은 0 이었다 — §5.3 규율 1 이 무력).
        mapped.unknownFieldCount shouldBe 1
        mapped.maskingFailureCount shouldBe 0
    }

    @Test
    fun `공고번호가 없으면 공고 축과 같은 사유로 떨어진다`() {
        val raw = item("""{"bidNtceOrd":"000","bidwinnrNm":"SYN-CORP"}""")

        val outcome =
            mapMaskedOpeningItem(raw, POLICY, SourceEndpoint.OPENING_AWARD_LIST, OBSERVED_AT, emptyList())

        val dropped = outcome.shouldBeInstanceOf<RawItemOutcome.Dropped>()
        dropped.reason shouldBe CollectionDropReason.CollectionMissingNoticeNumber
    }

    @Test
    fun `F-5 — 외자 대문자 FnlSucsfDate 는 이제 관측에 남는다(verifier r1 PROBE2 재현)`() {
        val json =
            """{"bidNtceNo":"SYN-MASK-0005","bidNtceOrd":"000","sucsfbidAmt":"5000","FnlSucsfDate":"2026-09-08"}"""
        val raw = item(json)

        val outcome = mapMaskedOpeningItem(raw, POLICY, SourceEndpoint.OPENING_AWARD_LIST, OBSERVED_AT, emptyList())

        val mapped = outcome.shouldBeInstanceOf<RawItemOutcome.Mapped>()
        mapped.observation.keys shouldContain RawKey("FnlSucsfDate")
    }

    @Test
    fun `maskOpengCorpInfo 는 5성분이 아니면 null 이다`() {
        maskOpengCorpInfo("SYN-CORP^1234567890^SYN-REP^100000000^95.5") shouldBe "SYN-CORP^100000000^95.5"
        maskOpengCorpInfo("낙찰예정자 다수^100000000^95.5") shouldBe null
        maskOpengCorpInfo("SYN-CORP^1234567890^SYN-REP") shouldBe null
    }
}
