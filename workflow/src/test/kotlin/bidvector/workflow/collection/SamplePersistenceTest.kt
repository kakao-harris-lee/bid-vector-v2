package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 표본 영속(D-6G-39) — **표본은 첫 실행에서 확정되고 그 뒤로 달라지지 않는다.**
 *
 * r1 은 실행마다 표본틀을 다시 걷고 다시 뽑았다. 수집이 3~4일에 걸치면 늦게 개찰된 공고가 창에
 * 들어오거나 한 슬롯이 실패하는 것만으로 **표본 자체가 달라진다** — 「결과를 보기 전에 확정한다」가
 * 실행 단위로만 성립했고, 그것은 성립하지 않는 것과 같다.
 */
class SamplePersistenceTest {
    @Test
    fun `창이 넓어져도 표본은 첫 실행이 확정한 그대로다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-03-02", count = 5)
        val first = fixture.run()

        // 늦게 개찰된 공고가 다음 실행의 표본틀에 들어온다 — 같은 층(같은 주)이라 다시 뽑으면 섞인다.
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-03-03", count = 5)
        val second = fixture.run()

        second.sample.selected shouldContainExactly first.sample.selected
        second.frameSize shouldBeGreaterThan first.frameSize
        fixture.sampleList.confirmCount shouldBe 1
    }

    @Test
    fun `확정 표본인데 이번 표본틀에서 안 보이면 부르지 않고 센다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-03-02", count = 5)
        val first = fixture.run()
        val calledOnce = fixture.construction.reservePriceCalls.size

        // 그 슬롯이 이번엔 아무것도 내지 않는다(목록 축 실패). 표본을 다시 뽑아 메우지 않는다.
        fixture.dropListRows(BusinessDivision.CONSTRUCTION, "2026-03-02")
        val second = fixture.run()

        second.sample.selected.shouldBeEmpty()
        second.sampleUnseen shouldBe first.sample.selected.size
        fixture.construction.reservePriceCalls shouldHaveSize calledOnce
    }

    @Test
    fun `확정된 목록이 있으면 계획도 그것을 보여준다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-03-02", count = 5)
        val confirmed = fixture.run().sample.selected

        fixture.listRows(BusinessDivision.SERVICE, "2026-03-03", count = 5)

        fixture.plan().selected shouldContainExactly confirmed
        fixture.sampleList.confirmCount shouldBe 1
    }

    @Test
    fun `계획은 표본을 확정하지 않는다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-03-02", count = 5)

        fixture.plan()

        fixture.sampleList.confirmed() shouldBe null
        fixture.sampleList.confirmCount shouldBe 0
    }

    @Test
    fun `확정된 목록은 층을 함께 싣는다`() {
        val fixture = OpeningFixture(targetPerStratum = 2)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-03-02", count = 5)
        fixture.listRows(BusinessDivision.SERVICE, "2026-03-02", count = 5)
        val report = fixture.run()

        val strata = requireNotNull(fixture.sampleList.confirmed()).strataByKey
        strata.keys shouldContainExactlyInAnyOrder report.sample.selected
        strata.values.map { it.division }.toSet() shouldBe
            setOf(BusinessDivision.CONSTRUCTION, BusinessDivision.SERVICE)
    }
}
