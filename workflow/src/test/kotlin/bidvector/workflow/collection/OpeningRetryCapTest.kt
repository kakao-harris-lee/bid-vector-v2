package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetLimit
import bidvector.procurement.BusinessDivision
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **재호출 상한의 회계**(D-6G2d-8 ⓒ · 16 · 19 · 42) — 「몇 번까지 다시 부르는가」는 수집 계약
 * (`CollectOpeningResultsUseCaseTest`)과 재는 것이 다르다: 저쪽은 무엇을 부르는가이고 이쪽은 **같은
 * 축을 언제 그만 부르는가**다. 파일 500 줄 한도가 이 경계에서 갈라졌다.
 */
class OpeningRetryCapTest {
    private val axis = SourceEndpoint.RESERVE_PRICE_DETAIL

    private fun crashingFixture(pagesPerCall: Int): OpeningFixture {
        val fixture = OpeningFixture(sampleSize = 1, rawFailsOn = axis)
        fixture.service.detailPagesPerCall = pagesPerCall
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)
        return fixture
    }

    /**
     * **D-6G2d-42 (cr r5 ②) — 크래시 라운드는 라운드 하나로 센다.** 적재와 결말 사이에서 던진 걷기는
     * 결말 줄을 남기지 못한다. 재개하는 쪽이 그 라운드를 닫으므로(`AXIS Failed`) 상한은 라운드를 세고,
     * **쪽 수는 셈에 들어오지 않는다** — 앞 판은 꼬리의 호출 줄을 세어 쪽이 여럿인 축을 크래시 한 번에
     * 확정시켰고, 그러면 참가자가 많은 공고만 빠지는 **비랜덤 결측**이 된다.
     *
     * 기준은 대역 포트가 받은 요청 수다. 한 쪽 축과 여섯 쪽 축이 **같은 기동에서** 멈춘다.
     */
    @Test
    fun `쪽이 여럿인 축도 한 쪽 축과 같은 기동에서 멈춘다`() {
        val onePage = crashingFixture(pagesPerCall = 1)
        val sixPages = crashingFixture(pagesPerCall = 6)

        repeat(POLICY_RETRY_LIMIT) {
            shouldThrow<IllegalStateException> { onePage.run() }
            shouldThrow<IllegalStateException> { sixPages.run() }
        }
        onePage.run()
        sixPages.run()

        onePage.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT
        sixPages.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT
    }

    /** 상한 **미만**에서는 다시 부른다 — 닫힌 라운드가 상한보다 하나 적으면 그 축은 살아 있다. */
    @Test
    fun `상한 미만의 크래시 라운드 뒤에는 다시 부른다`() {
        val fixture = crashingFixture(pagesPerCall = 6)

        repeat(POLICY_RETRY_LIMIT - 1) { shouldThrow<IllegalStateException> { fixture.run() } }
        fixture.raw.failOn = null
        fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT
    }

    /**
     * **D-6G2d-48 ② — 의도 줄만 남은 크래시도 라운드 하나다.** 관문이 의도 줄을 적은 뒤·호출 줄을 적기
     * 전에 죽으면 꼬리에 HTTP 줄이 없다. 앞 판은 그 꼬리를 열린 라운드로 보지 않아 **상한 없이** 매 실행
     * 다시 불렀다 — 예산은 이미 그 의도 줄을 나간 호출로 세므로 두 장부의 가정이 갈렸다.
     */
    @Test
    fun `의도 줄만 남기고 죽은 라운드도 상한에 센다`() {
        val fixture = OpeningFixture(sampleSize = 1)
        fixture.service.crashAfterIntentOn = axis
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)
        repeat(POLICY_RETRY_LIMIT) { shouldThrow<IllegalStateException> { fixture.run() } }
        fixture.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT

        fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT
    }

    /**
     * **관문 거부는 셈을 끊지 않는다**(D-6G2d-16 · 42). 앞 판은 꼬리를 호출 줄로 셌고 그 꼬리가
     * `Refused` 결말에서 끊겨 **거부 뒤의 크래시가 사라졌다**. 지금은 닫힌 라운드가 세어지므로 거부가
     * 사이에 끼어도 앞의 둘이 남는다 — 크래시·크래시·거부 뒤의 기동에서 셈은 둘이고, 그 축은 상한
     * 셋 미만이라 다시 불린다.
     */
    @Test
    fun `크래시 둘 사이에 관문 거부가 끼어도 셈은 둘이다`() {
        val fixture = crashingFixture(pagesPerCall = 2)
        repeat(2) { shouldThrow<IllegalStateException> { fixture.run() } }

        // 거부는 호출 전에 접힌다 — 적재까지 가지 않으므로 이 기동은 던지지 않는다.
        fixture.raw.failOn = null
        fixture.service.detailTruncation = TruncationCause.BudgetExhausted(BudgetLimit.DAILY)
        fixture.run()
        fixture.service.detailTruncation = null
        fixture.run()

        // 닫힌 라운드 둘 + 거부 하나 — 상한이 세는 것은 일시 실패 결말 둘뿐이다.
        val conclusions = fixture.attempts.appended.filter { it.kind == AttemptKind.AXIS }
        conclusions.count { it.outcome is AttemptOutcome.Failed } shouldBe 2
        conclusions.count { it.outcome is AttemptOutcome.Refused } shouldBe 1
    }
}
