package bidvector.adapters.koneps

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.URI

private val NOTICE_ID = NoticeId(NoticeNumber.of("20260908001"), NoticeRound.of("000"))
private val BASE = URI.create("http://127.0.0.1:0/op")
private val KEY = ServiceKey.of("test-key")

/**
 * 게이트 ③ — `inqryDiv`는 오퍼레이션 서술 값 객체([KonepsOperationDescriptor])가 필수
 * 생성자 인자로 갖고, [buildKonepsOperationUri]는 그 값을 해석만 한다(기본값·폴백 없음).
 */
class KonepsOperationDescriptorTest {
    @Test
    fun `AWARD_LIST 는 inqryDiv 3 개찰일시축 + 기간창을 낸다`() {
        val window = "202609080000" to "202609082359"
        val uri = buildKonepsOperationUri(BASE, KEY, KonepsOperationPolicy.AWARD_LIST, 1, 100, periodWindow = window)

        uri.toString() shouldContain "inqryDiv=3"
        uri.toString() shouldContain "inqryBgnDt=202609080000"
        uri.toString() shouldContain "inqryEndDt=202609082359"
        uri.toString() shouldNotContain "bidNtceNo"
    }

    @Test
    fun `RESERVE_PRICE_DETAIL 은 inqryDiv 2 입찰공고번호축 + 단건 조회다 — bidNtceOrd 는 없다`() {
        val operation = KonepsOperationPolicy.RESERVE_PRICE_DETAIL
        val uri = buildKonepsOperationUri(BASE, KEY, operation, 1, 100, noticeId = NOTICE_ID)

        uri.toString() shouldContain "inqryDiv=2"
        uri.toString() shouldContain "bidNtceNo=20260908001"
        uri.toString() shouldNotContain "bidNtceOrd"
        uri.toString() shouldNotContain "inqryBgnDt"
    }

    @Test
    fun `LICENSE_LIMIT_DETAIL 은 inqryDiv 2 이고 bidNtceNo·bidNtceOrd 둘 다 싣는다 — 1-9-5`() {
        val operation = KonepsOperationPolicy.LICENSE_LIMIT_DETAIL
        val uri = buildKonepsOperationUri(BASE, KEY, operation, 1, 100, noticeId = NOTICE_ID)

        uri.toString() shouldContain "inqryDiv=2"
        uri.toString() shouldContain "bidNtceNo=20260908001"
        uri.toString() shouldContain "bidNtceOrd=000"
    }

    @Test
    fun `정책 표를 바꾸면 URI 가 따라 바뀐다 — 리터럴이 아니라 정책 값을 읽는다는 회귀 test`() {
        val changed =
            KonepsOperationDescriptor(
                inquiryDivValue = "7",
                requiresPeriodWindow = true,
                requiresNoticeNumber = false,
                requiresNoticeRound = false,
                rowIdentifierRawKeys = emptyList(),
            )

        val uri = buildKonepsOperationUri(BASE, KEY, changed, 1, 100, periodWindow = "a" to "b")

        uri.toString() shouldContain "inqryDiv=7"
    }

    @Test
    fun `기간창과 단건 조회를 동시에 요구하는 서술은 구성 자체가 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            KonepsOperationDescriptor(
                inquiryDivValue = "1",
                requiresPeriodWindow = true,
                requiresNoticeNumber = true,
                requiresNoticeRound = false,
                rowIdentifierRawKeys = emptyList(),
            )
        }
    }

    @Test
    fun `bidNtceOrd 요구는 bidNtceNo 요구 없이 설 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            KonepsOperationDescriptor(
                inquiryDivValue = "1",
                requiresPeriodWindow = false,
                requiresNoticeNumber = false,
                requiresNoticeRound = true,
                rowIdentifierRawKeys = emptyList(),
            )
        }
    }

    @Test
    fun `기간창을 요구하는 오퍼레이션에 noticeId 만 주면 구성 전에 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            buildKonepsOperationUri(BASE, KEY, KonepsOperationPolicy.AWARD_LIST, 1, 100, noticeId = NOTICE_ID)
        }
    }

    // M3/3F — §1.9.2 「13-15: inqryDiv 자체가 없다」. OPENING_COMPLETE 는 그 사실을
    // inquiryDivValue = null 로 나른다.
    @Test
    fun `OPENING_COMPLETE 는 inqryDiv 파라미터 자체를 내지 않는다`() {
        val operation = KonepsOperationPolicy.OPENING_COMPLETE
        val uri = buildKonepsOperationUri(BASE, KEY, operation, 1, 100, noticeId = NOTICE_ID)

        uri.toString() shouldNotContain "inqryDiv"
    }

    @Test
    fun `OPENING_COMPLETE 는 bidNtceNo bidNtceOrd 둘 다 싣는다`() {
        val operation = KonepsOperationPolicy.OPENING_COMPLETE
        val uri = buildKonepsOperationUri(BASE, KEY, operation, 1, 100, noticeId = NOTICE_ID)

        uri.toString() shouldContain "bidNtceNo=20260908001"
        uri.toString() shouldContain "bidNtceOrd=000"
    }

    @Test
    fun `inquiryDivValue 가 null 인 서술은 구성 자체가 성립한다`() {
        val operation =
            KonepsOperationDescriptor(
                inquiryDivValue = null,
                requiresPeriodWindow = false,
                requiresNoticeNumber = true,
                requiresNoticeRound = false,
                rowIdentifierRawKeys = emptyList(),
            )

        operation.inquiryDivValue shouldBe null
    }

    @Test
    fun `빈 문자열 inquiryDivValue 는 축이 없으면 null 을 쓰라는 사유로 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            KonepsOperationDescriptor(
                inquiryDivValue = "",
                requiresPeriodWindow = false,
                requiresNoticeNumber = true,
                requiresNoticeRound = false,
                rowIdentifierRawKeys = emptyList(),
            )
        }
    }
}
