package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.TruncationCause
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * D-6G-1·11 — 6G 수집 갈래의 계약. 개찰결과 목록을 **공고일 축**으로 걸어 표본틀을 만들고,
 * 결과를 보기 전에 표본을 확정한 뒤, 표본 공고마다 상세 셋(예비가격 상세 · 개찰완료 ·
 * 공사면 A값)을 부른다. 호출 예산과 쿼터가 각각 실행을 멈춘다.
 */
class CollectOpeningResultsUseCaseTest {
    @Test
    fun `표본에 뽑힌 공고만 상세를 부른다 — 표본틀 전체를 부르지 않는다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 10)

        val report = fixture.run()

        report.sample.selected.size shouldBe 2
        fixture.service.reservePriceCalls.size shouldBe 2
        fixture.service.openingCompleteCalls.size shouldBe 2
        // 상세를 부른 공고 집합 == 표본 집합(표본틀 10 가운데 2).
        fixture.service.reservePriceCalls
            .map { fixture.hashOf(it) }
            .toSet() shouldBe
            report.sample.selected
                .map { it.value }
                .toSet()
    }

    @Test
    fun `기초금액 조회는 모든 업무에서 부른다 — 예가 범위율이 그 오퍼레이션에서만 온다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 2)

        fixture.run()

        fixture.construction.baseAmountCalls.size shouldBe 2
        fixture.service.baseAmountCalls.size shouldBe 2
    }

    @Test
    fun `A값은 공사에서만 부른다 — 용역·물품은 그 오퍼레이션을 부르지 않는다`() {
        val fixture = OpeningFixture(targetPerStratum = 3)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 3)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)

        fixture.run()

        fixture.construction.formulaACalls.size shouldBe 3
        fixture.service.formulaACalls.shouldBeEmpty()
    }

    @Test
    fun `표본은 결과를 보기 전에 확정된다 — 상세를 한 번도 부르지 않아도 같은 표본이 나온다`() {
        val planOnly = OpeningFixture(targetPerStratum = 2)
        planOnly.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 8)
        val full = OpeningFixture(targetPerStratum = 2)
        full.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 8)

        val planned = planOnly.plan()
        val collected = full.run()

        planned.selected shouldContainExactly collected.sample.selected
    }

    @Test
    fun `층은 공고일 슬롯이 정한다 — 같은 업무의 다른 주가 각자 목표만큼 뽑힌다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-10", count = 5)

        val report = fixture.run()

        report.sample.strata.size shouldBe 2
        report.sample.selected.size shouldBe 4
    }

    @Test
    fun `일 호출 상한에 닿으면 멈춘다 — 남은 표본은 부르지 않는다`() {
        val fixture = OpeningFixture(targetPerStratum = 10, budget = CollectionCallBudget(perDay = 6, total = 100))
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 10)

        val report = fixture.run()

        report.halted shouldNotBe null
        report.halted!!.budgetLimit shouldBe BudgetLimit.DAILY
        // 목록 슬롯 둘(업무 둘 × 공고일 하나) = 2 + 용역 공고마다 셋(예비가격·개찰완료·기초금액) →
        // 상한 6 은 첫 공고 셋(3·4·5)과 둘째의 예비가격(6)까지 허가하고 둘째의 개찰완료에서 끊긴다.
        fixture.service.reservePriceCalls.size shouldBe 2
        fixture.service.openingCompleteCalls.size shouldBe 1
        fixture.service.baseAmountCalls.size shouldBe 1
        report.detailCalls shouldBe 4
    }

    @Test
    fun `쿼터 소진은 표본틀 단계에서도 실행을 멈춘다 — 표본을 뽑지 않는다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.listTruncation = TruncationCause.QuotaExhausted

        val report = fixture.run()

        report.halted shouldNotBe null
        report.halted!!.truncationCause shouldBe TruncationCause.QuotaExhausted
        report.sample.selected.shouldBeEmpty()
        fixture.service.reservePriceCalls.shouldBeEmpty()
    }

    @Test
    fun `쿼터가 아닌 절단은 그 슬롯만 접고 표본틀을 이어 만든다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.listTruncation = TruncationCause.Timeout

        val report = fixture.run()

        report.halted shouldBe null
        report.sample.selected.size shouldBe 2
    }

    @Test
    fun `원문은 부른 응답마다 남는다 — 목록·상세 어느 축이든 관측이 저장된다`() {
        val fixture = OpeningFixture(targetPerStratum = 1)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 4)

        fixture.run()

        // 목록 4행 + 표본 하나의 상세 넷(공사라 A값까지).
        fixture.raw.appended.size shouldBe 8
    }
}
