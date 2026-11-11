package bidvector.workflow.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.BusinessDivision
import bidvector.procurement.TruncationCause
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **개찰 갈래도 차단에 멈춘다**(M9/ops-code 수정 라운드 1 — cr HIGH · v M-1).
 *
 * 앞 판은 `haltOf` 와 표본틀의 `when {}` 이 `else` 로 나머지를 전부 「계속」으로 접어, 403 을 받은
 * 뒤에도 표본 공고 × 축마다 거절될 호출을 한 번씩 더 냈다 — 공고 갈래와 **공유하는 승인 상한**을
 * 태우고(D-6G-47) 개찰 run 자신은 `halted == null` 이라 COMPLETE(0) 로 끝나 그 소진이 보이지도
 * 않았다. 그 두 자리를 소진 `when`([openingDirective])으로 바꾼 거동을 여기서 잰다.
 *
 * `CollectNoticesUseCaseTest` 의 「차단은 실행을 멈춘다」와 **짝**이다 — 두 갈래가 같은 답을 낸다.
 */
class OpeningAccessBlockedHaltTest {
    @Test
    fun `차단은 표본틀 단계에서 실행을 멈춘다 — 표본을 뽑지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.listTruncation = TruncationCause.AccessBlocked

        val report = fixture.run()

        report.halted?.truncationCause shouldBe TruncationCause.AccessBlocked
        report.sample.selected.shouldBeEmpty()
        fixture.service.reservePriceCalls.shouldBeEmpty()
    }

    /**
     * **남은 후보 호출 0** — 이 단언이 이 slice 가 닫은 구멍의 전부다. 앞 판은 첫 상세가 403 을
     * 물어도 남은 표본을 계속 불렀다.
     */
    @Test
    fun `차단은 첫 상세 호출에서 멈추고 남은 표본을 부르지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.detailTruncation = TruncationCause.AccessBlocked

        val report = fixture.run()

        val halt = requireNotNull(report.halted)
        halt.truncationCause shouldBe TruncationCause.AccessBlocked
        // 예산 한도가 아니라 **원천의 거절**이다 — 두 칸이 섞이지 않는다.
        halt.budgetLimit shouldBe null
        halt.partialNotice shouldBe false
        halt.notAttempted shouldBe 2
        report.detailCalls shouldBe 1
    }

    /**
     * **통제군** — 일시 사유는 그대로 계속 간다(앞 판의 거동). 이 test 가 없으면 「절단 사유를
     * 봤더니 전부 멈춘다」는 과잉 처방도 초록이다.
     */
    @Test
    fun `구조 실패는 멈추지 않는다 — 남은 표본을 계속 부른다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.detailTruncation = TruncationCause.StructureFailure

        val report = fixture.run()

        report.halted shouldBe null
        // **정확한 수**로 잰다(cr r2 low) — 표본 2 × 용역 축 셋이고 결정적이다. 「계속 가긴 하는데
        // 중간에 멈춘다」는 회귀가 부등식에서는 초록으로 지나간다.
        report.detailCalls shouldBe SERVICE_SAMPLE_CALLS
    }

    /**
     * 처분 표의 **집합 등식**. 「차단이 멈추는가」만 물으면 멈추는 집합이 넓어지는 것을 보지 못하고,
     * 넓어진 한 사유가 곧 「일시 장애 한 번에 개찰 run 을 통째로 버린다」다. 그래서 **전수**를 잰다 —
     * 목록은 손으로 적지 않고 sealed 계층에서 뽑는다.
     */
    @Test
    fun `원천 거절로 멈추는 사유는 정확히 쿼터와 차단 둘이다`() {
        val causes = allTruncationCauses()
        // **모집단을 먼저 잰다**(cr r2 MEDIUM) — 대표값을 짓는 자리가 값 나르는 사유를 다른 사유로
        // 대체하면 등식이 조용해진다. `AxisLastObservedAtTest` 의 선례와 같은 구조 등식이다.
        causes.map { it.javaClass }.toSet() shouldBe TruncationCause::class.java.permittedSubclasses.toSet()

        val haltingBySource =
            causes
                .filter { it.openingDirective() is OpeningWalkDirective.HaltSource }
                .map { it::class.simpleName }

        haltingBySource.sortedBy { it } shouldContainExactly listOf("AccessBlocked", "QuotaExhausted")
    }

    @Test
    fun `예산 한도는 자기 갈래이고 사유 부재는 계속이다`() {
        // 답이 **값을 나른다** — 한도를 다시 꺼내는 캐스트가 소비 자리에 없다(cr r2 MEDIUM).
        TruncationCause.BudgetExhausted(BudgetLimit.DAILY).openingDirective() shouldBe
            OpeningWalkDirective.HaltBudget(BudgetLimit.DAILY)
        TruncationCause.AccessBlocked.openingDirective() shouldBe
            OpeningWalkDirective.HaltSource(TruncationCause.AccessBlocked)
        null.openingDirective() shouldBe OpeningWalkDirective.Continue
    }
}

/** 표본 2 × 용역 상세 축 셋(예비가격 · 개찰완료 · 기초금액) — A값은 공사만이다(D-6G-12). */
private const val SERVICE_SAMPLE_CALLS = 6

/**
 * sealed 계층에서 뽑은 전수 — 손으로 적은 목록은 새 사유가 생겨도 조용히 초록이다(그 사유가 목록에
 * 없으니 등식이 **측정하지 않는다**). 값을 나르는 갈래는 `INSTANCE` 가 없어 대표값을 짓는데, 그
 * 대체가 모집단을 바꿔치울 수 있으므로 **쓰는 자리가 클래스 집합 등식을 먼저 잰다**(cr r2 MEDIUM).
 */
private fun allTruncationCauses(): List<TruncationCause> =
    requireNotNull(TruncationCause::class.java.permittedSubclasses) { "TruncationCause 는 sealed 여야 한다" }
        .map { subclass ->
            runCatching { subclass.getField("INSTANCE").get(null) as TruncationCause }
                .getOrElse { TruncationCause.BudgetExhausted(BudgetLimit.TOTAL) }
        }
