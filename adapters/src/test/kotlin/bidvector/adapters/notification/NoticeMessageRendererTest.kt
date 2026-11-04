package bidvector.adapters.notification

import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ContentRef
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * D-6 렌더 문면의 **골든**. 금액은 원 단위 콤마, 시각은 KST — 웹 화면의 규칙과 같은 축이고
 * (`web/src/display/noticeDisplay.ts` 의 `toLocaleString('ko-KR')`·`원`), 없는 칸은 `값 없음`
 * 이다(가짜 기본값 금지 — `0원`·`미정`을 지어내지 않는다).
 *
 * **렌더 실패는 발송 결과 어휘로 옮기지 않는다**(설계 검토 D-6 결정). 공고 행이 없으면 던지지도
 * 않고 금액·마감 칸을 지어내지도 않는다 — 그 사실을 말하는 **다른 모양의 본문**을 만든다.
 */
class NoticeMessageRendererTest {
    private fun renderer(facts: NoticeNotificationFacts?): NoticeMessageRenderer =
        NoticeMessageRenderer(
            facts = FixedNoticeFacts(facts),
            settings = NoticeMessageSettings(SITE_BASE_URL),
        )

    private fun bodyOf(
        facts: NoticeNotificationFacts?,
        ref: String = "$NUMBER-$ROUND",
    ): String = renderer(facts).render(ContentRef(ref), Channel.Telegram).body

    @Test
    fun `채널이 요청된 채널 그대로 실린다`() {
        renderer(fullFacts()).render(ContentRef("$NUMBER-$ROUND"), Channel.Telegram).channel shouldBe Channel.Telegram
    }

    @Test
    fun `칸이 다 찬 공고의 문면 — 골든`() {
        bodyOf(fullFacts()) shouldBe
            """
            [입찰 추천]
            공고명 통합 유지관리 용역
            공고번호 A-2026-11-000
            수요기관 한국테스트공사
            마감 2026-12-31 10:00 KST
            기초금액 1,234,567,890원
            https://bidvector.example/notices/A-2026-11/000
            """.trimIndent()
    }

    /** 행은 있고 칸이 빈 경우 — 칸마다 `값 없음` 이고 **구조는 같다**(아래 「행 없음」과 다른 모양이다). */
    @Test
    fun `행은 있고 칸이 비면 칸마다 값 없음 — 골든`() {
        bodyOf(emptyCellFacts()) shouldBe
            """
            [입찰 추천]
            공고명 값 없음
            공고번호 A-2026-11-000
            수요기관 값 없음
            마감 값 없음
            기초금액 값 없음
            https://bidvector.example/notices/A-2026-11/000
            """.trimIndent()
    }

    /** 통화가 원이 아니면 숫자만 적지 않는다 — 단위를 아는 척하지 않는다(웹 `amountDisplayOf` 와 같은 규칙). */
    @Test
    fun `원이 아닌 통화는 통화 코드를 함께 적는다`() {
        bodyOf(fullFacts(currency = "USD")) shouldBe
            """
            [입찰 추천]
            공고명 통합 유지관리 용역
            공고번호 A-2026-11-000
            수요기관 한국테스트공사
            마감 2026-12-31 10:00 KST
            기초금액 1,234,567,890 USD
            https://bidvector.example/notices/A-2026-11/000
            """.trimIndent()
    }

    /**
     * **공고 행이 없을 때** — 던지지 않고(run 이 멈추면 남은 행이 좌초한다) `Rejected` 로도 옮기지
     * 않는다(그 어휘는 「Telegram 이 거부했다」는 뜻이다). 금액·마감 칸 자체를 쓰지 않는다.
     */
    @Test
    fun `공고 행이 없으면 사실을 주장하지 않는 축약 본문이다 — 골든`() {
        bodyOf(null) shouldBe
            """
            [입찰 추천]
            공고번호 A-2026-11-000
            저장된 공고 정보가 없습니다 — 보존 파기 또는 미수집입니다.
            https://bidvector.example/notices/A-2026-11/000
            """.trimIndent()
    }

    /** 내용 참조가 공고 식별자 모양이 아니면 링크를 지어내지 않는다 — 지을 수 없기 때문이다. */
    @Test
    fun `내용 참조를 해독하지 못하면 링크 없는 본문이다 — 골든`() {
        bodyOf(fullFacts(), ref = "해독불가") shouldBe
            """
            [입찰 추천]
            공고 식별자를 해독하지 못했습니다 — 내용 참조 '해독불가'
            알림함에서 확인하십시오.
            """.trimIndent()
    }

    /** 해독 실패면 공고 표를 **묻지도 않는다** — 없는 좌표로 질의를 돌리지 않는다. */
    @Test
    fun `해독 실패는 공고 표를 묻지 않는다`() {
        val facts = FixedNoticeFacts(fullFacts())

        NoticeMessageRenderer(facts, NoticeMessageSettings(SITE_BASE_URL)).render(ContentRef("x"), Channel.Telegram)

        facts.askedFor shouldBe emptyList()
    }

    @Test
    fun `내용 참조 해독 — 번호에 하이픈이 있어도 마지막 세 자리가 차수다`() {
        NoticeReference.parse("A-2026-11-000") shouldBe NoticeReference("A-2026-11", "000")
        NoticeReference.parse("X-007") shouldBe NoticeReference("X", "007")
    }

    @Test
    fun `차수 모양이 아니거나 번호가 비면 해독 실패다`() {
        NoticeReference.parse("A-2026-11-00") shouldBe null
        NoticeReference.parse("A-2026-11-0000") shouldBe null
        NoticeReference.parse("A-2026-11-abc") shouldBe null
        NoticeReference.parse("-000") shouldBe null
        NoticeReference.parse("000") shouldBe null
        NoticeReference.parse("") shouldBe null
    }

    private fun fullFacts(currency: String? = "KRW"): NoticeNotificationFacts =
        NoticeNotificationFacts(
            title = "통합 유지관리 용역",
            demandAgencyName = "한국테스트공사",
            deadlineAt = DEADLINE,
            baseAmountWon = BigDecimal("1234567890"),
            baseAmountCurrency = currency,
        )

    private fun emptyCellFacts(): NoticeNotificationFacts =
        NoticeNotificationFacts(
            title = null,
            demandAgencyName = null,
            deadlineAt = null,
            baseAmountWon = null,
            baseAmountCurrency = null,
        )

    /** 공고 사실 port 의 대역 — **무엇을 물었는지도** 기록한다(묻지 않음을 잴 수 있어야 한다). */
    private class FixedNoticeFacts(
        private val facts: NoticeNotificationFacts?,
    ) : NoticeNotificationFactsPort {
        val askedFor = mutableListOf<NoticeReference>()

        override fun factsFor(reference: NoticeReference): NoticeNotificationFacts? {
            askedFor += reference
            return facts
        }
    }

    private companion object {
        const val SITE_BASE_URL = "https://bidvector.example"
        const val NUMBER = "A-2026-11"
        const val ROUND = "000"

        /** 2026-12-31 10:00 KST — UTC 로는 전날 01:00 이다(시간대 경계를 실제로 지나는 값). */
        val DEADLINE: Instant = Instant.parse("2026-12-31T01:00:00Z")
    }
}
