package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.SourceEndpoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test

/**
 * **재호출 상한의 회계**(D-6G2d-8 ⓒ · 16 · 19 · cr r4 ②) — 「몇 번까지 다시 부르는가」는 수집 계약
 * (`CollectOpeningResultsUseCaseTest`)과 재는 것이 다르다: 저쪽은 무엇을 부르는가이고 이쪽은 **같은
 * 축을 언제 그만 부르는가**다. 파일 500 줄 한도가 이 경계에서 갈라졌다.
 */
class OpeningRetryCapTest {
    /**
     * **cr r4 ② — 결말 없이 끝난 라운드도 상한에 센다.** 적재가 구조적으로 실패하는 축(제약 위반·연결
     * 끊김)은 걷기마다 원문만 남기고 던진다 — 일시 실패 결말이 **하나도 없어** 앞 판은 그 축을 매 실행
     * 다시 불렀다. 승인 호출을 영영 태우는 모양이고, 그것이 D-6G-65 의 「나간 호출과 상한의 어긋남」이다.
     *
     * 기준은 **대역 포트가 받은 요청 수**다: 상한만큼의 기동이 각각 한 번 부르고, 그다음 기동은 **0 번**
     * 부른다. 대역이 관문의 두 줄을 남기므로(출하 경로가 그렇다) 원장에 그 호출들이 보인다.
     */
    @Test
    fun `적재가 던져 결말이 없는 라운드도 재호출 상한에 센다`() {
        val fixture = OpeningFixture(sampleSize = 1, rawFailsOn = SourceEndpoint.RESERVE_PRICE_DETAIL)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)
        repeat(POLICY_RETRY_LIMIT) { shouldThrow<IllegalStateException> { fixture.run() } }
        fixture.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT

        fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize POLICY_RETRY_LIMIT
    }
}
