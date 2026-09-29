package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetLimit
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import io.kotest.assertions.throwables.shouldThrow
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
        val fixture = OpeningFixture(sampleSize = 2)
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
        val fixture = OpeningFixture(sampleSize = 4)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 2)

        fixture.run()

        fixture.construction.baseAmountCalls.size shouldBe 2
        fixture.service.baseAmountCalls.size shouldBe 2
    }

    @Test
    fun `A값은 공사에서만 부른다 — 용역·물품은 그 오퍼레이션을 부르지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 6)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 3)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)

        fixture.run()

        fixture.construction.formulaACalls.size shouldBe 3
        fixture.service.formulaACalls.shouldBeEmpty()
    }

    @Test
    fun `표본은 결과를 보기 전에 확정된다 — 상세를 한 번도 부르지 않아도 같은 표본이 나온다`() {
        val planOnly = OpeningFixture(sampleSize = 2)
        planOnly.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 8)
        val full = OpeningFixture(sampleSize = 2)
        full.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 8)

        val planned = planOnly.plan()
        val collected = full.run()

        planned.selected shouldContainExactly collected.sample.selected
    }

    @Test
    fun `층은 공고일 슬롯이 정한다 — 같은 업무의 다른 주가 각자 목표만큼 뽑힌다`() {
        val fixture = OpeningFixture(sampleSize = 4)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-10", count = 5)

        val report = fixture.run()

        report.sample.strata.size shouldBe 2
        report.sample.selected.size shouldBe 4
    }

    /**
     * 상한 판정은 **관문**이 한다(D-6G-47) — 이 use case 는 그 거부를 절단 사유로 받아 멈추고
     * 어느 한도였는지 보고한다. 상한을 실제로 세는 거동은 관문 test 와 E2E 가 잰다(여기서 다시
     * 세면 셈의 출처가 둘이 된다).
     */
    @Test
    fun `목록 갈래가 상한에 걸리면 표본을 뽑지 않고 멈춘다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)
        fixture.service.listTruncation = TruncationCause.BudgetExhausted(BudgetLimit.TOTAL)

        val report = fixture.run()

        report.halted?.budgetLimit shouldBe BudgetLimit.TOTAL
        // 표본틀이 반만 선 채로 상세를 부르지 않는다 — 반쪽 표본틀에서 뽑으면 층이 비뚤어진다.
        report.detailCalls shouldBe 0
        report.sample.selected.shouldBeEmpty()
    }

    @Test
    fun `상세 단계가 상한에 걸리면 멈추고 어느 한도였는지 싣는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 10)
        fixture.service.detailTruncation = TruncationCause.BudgetExhausted(BudgetLimit.DAILY)

        val report = fixture.run()

        report.halted shouldNotBe null
        report.halted!!.budgetLimit shouldBe BudgetLimit.DAILY
        report.detailCalls shouldBe 1
    }

    @Test
    fun `이미 받은 축은 다시 부르지 않는다 — 이어 돌기`() {
        val fixture =
            OpeningFixture(
                sampleSize = 2,
                collectedAxes =
                    FakeCollectedAxisStore(setOf(bidvector.procurement.SourceEndpoint.RESERVE_PRICE_DETAIL)),
            )
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 6)

        val report = fixture.run()

        // 예비가격 상세는 이미 받았으므로 **한 번도 부르지 않는다**. 나머지 축은 표본 수만큼 나간다.
        report.halted shouldBe null
        fixture.service.reservePriceCalls.shouldBeEmpty()
        fixture.service.openingCompleteCalls.size shouldBe 2
        fixture.service.baseAmountCalls.size shouldBe 2
        // 건너뛴 축은 예산에서도 빠진다 — 걸음당 호출이 셋이 아니라 둘이다.
        report.detailCalls shouldBe 4
    }

    @Test
    fun `쿼터 소진은 표본틀 단계에서도 실행을 멈춘다 — 표본을 뽑지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.listTruncation = TruncationCause.QuotaExhausted

        val report = fixture.run()

        report.halted shouldNotBe null
        report.halted!!.truncationCause shouldBe TruncationCause.QuotaExhausted
        report.sample.selected.shouldBeEmpty()
        fixture.service.reservePriceCalls.shouldBeEmpty()
    }

    @Test
    fun `첫 페이지 throttle — 한 장도 못 받은 슬롯이 실행을 죽이지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 4)
        // rate limiter 가 첫 페이지에서 허가를 거부한 모양 — 페이지를 한 장도 못 받았다.
        fixture.service.listPagesFetched = 0
        fixture.service.listTruncation = TruncationCause.SelfThrottled

        // 한 장도 못 받은 슬롯은 **절단**이다 — 그 표본틀로 표본을 굳히지 않는다(D-6G-50).
        // 빈 표본을 확정하는 것도 막힌다: 그 파일이 이후 모든 실행을 막는다.
        shouldThrow<IllegalArgumentException> { fixture.run() }

        fixture.sampleList.confirmCount shouldBe 0
    }

    /**
     * D-6G-59 — `partialNotice` 는 **관측**이다: 이 공고의 축 중 하나라도 끝났는가. 앞 판은 「멈춘
     * 축이 마지막이 아니면 반쪽」이라고 **추측**했고, 그래서 첫 축에서 막혀 아무것도 적재되지 않은
     * 공고도 반쪽으로 세어 「손대지 않은」 수가 하나 모자랐다.
     */
    @Test
    fun `K6 — 첫 축에서 쿼터를 물면 그 공고는 반쪽이 아니다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.detailTruncation = TruncationCause.QuotaExhausted

        val report = fixture.run()

        // 첫 상세 호출이 쿼터를 물면 거기서 멈춘다 — 남은 표본을 계속 부르면 거부만 쌓인다.
        val halt = requireNotNull(report.halted)
        halt.truncationCause shouldBe TruncationCause.QuotaExhausted
        halt.partialNotice shouldBe false
        halt.notAttempted shouldBe 2
        report.detailCalls shouldBe 1
    }

    /** 앞 축을 받고 둘째 축에서 막히면 그 공고는 실제로 반쪽이다 — 「손대지 않은」 수에서 뺀다. */
    @Test
    fun `K6 — 둘째 축에서 쿼터를 물면 그 공고는 반쪽이다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.detailTruncation = TruncationCause.QuotaExhausted
        fixture.service.detailTruncationFromCall = 1

        val report = fixture.run()

        val halt = requireNotNull(report.halted)
        halt.partialNotice shouldBe true
        halt.notAttempted shouldBe 1
        report.detailCalls shouldBe 2
    }

    /** 상한 거부가 첫 축에서 오면 그 공고는 축 하나도 적재되지 않아 반쪽이 아니다. */
    @Test
    fun `K6 — 상한이 첫 축에서 물면 그 공고는 반쪽이 아니다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.detailTruncation = TruncationCause.BudgetExhausted(BudgetLimit.TOTAL)

        val halt = requireNotNull(fixture.run().halted)

        halt.budgetLimit shouldBe BudgetLimit.TOTAL
        halt.partialNotice shouldBe false
        halt.notAttempted shouldBe 2
    }

    /**
     * **D-6G-74 (vr r5 M-2) — 예산 멈춤의 반쪽도 잰다.** 쿼터 쪽에는 「둘째 축에서 물면 반쪽」이
     * 있었고 예산 쪽에는 「첫 축에서 물면 반쪽이 아니다」만 있었다. 출하 코드는 두 사유에 같은
     * 물음을 쓰지만(D-6G-59), 그 성질을 예산 갈래에서 잠그는 test 가 없어 갈래마다 다른 답을
     * 쓰게 되어도 초록이었다.
     */
    @Test
    fun `K6 — 상한이 둘째 축에서 물면 그 공고는 반쪽이다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.detailTruncation = TruncationCause.BudgetExhausted(BudgetLimit.TOTAL)
        fixture.service.detailTruncationFromCall = 1

        val halt = requireNotNull(fixture.run().halted)

        halt.budgetLimit shouldBe BudgetLimit.TOTAL
        halt.partialNotice shouldBe true
        // 반쯤 받은 그 공고는 「손대지 않은」 수에서 빠진다.
        halt.notAttempted shouldBe 1
    }

    /**
     * D-6G-58 — 축의 결말은 원장이 정한다. 짧게 걸었거나 실패한 축은 **원문 행이 있어도** 다시
     * 부른다. 앞 판은 raw 존재를 보고 완료로 읽어, 잘린 1쪽만 남은 축이 영영 다시 불리지 않았다.
     */
    @Test
    fun `원문이 있어도 원장이 미완이라고 하면 다시 부른다`() {
        val axis = SourceEndpoint.RESERVE_PRICE_DETAIL
        val fixture =
            OpeningFixture(
                sampleSize = 1,
                collectedAxes = FakeCollectedAxisStore(setOf(axis)),
                attemptSeed = syntheticServiceKeys().map { shortWalkOn(it, axis) },
            )
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)

        fixture.run()

        fixture.service.reservePriceCalls.size shouldBe 1
    }

    /** 반대 방향 — 원장이 「끝났다」고 하면 원문이 없어도 부르지 않는다(빈 응답이 영원히 불리지 않게). */
    @Test
    fun `원문이 없어도 원장이 끝났다고 하면 부르지 않는다`() {
        val axis = SourceEndpoint.RESERVE_PRICE_DETAIL
        val fixture =
            OpeningFixture(
                sampleSize = 1,
                attemptSeed = syntheticServiceKeys().map { settledOn(it, axis) },
            )
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)

        fixture.run()

        fixture.service.reservePriceCalls.shouldBeEmpty()
    }

    /**
     * D-6G-50(vr M-3) — 쿼터가 아닌 절단은 멈춤을 내지 않지만 **표본을 확정해서도 안 된다.**
     * 반쪽 표본틀에서 굳히면 실패한 슬롯의 공고는 다음 실행에 표본틀에 들어와도 영영 뽑히지
     * 않는다. 확정하지 않고 거부하면 다음 실행이 표본틀부터 다시 세운다.
     */
    @Test
    fun `절단된 슬롯이 있으면 표본을 확정하지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.service.listTruncation = TruncationCause.Timeout

        shouldThrow<IllegalArgumentException> { fixture.run() }

        fixture.sampleList.confirmCount shouldBe 0
    }

    /**
     * **D-6G-58 ⓑ — 축의 결말은 그 축의 원문이 적재된 뒤에 적는다.** 적재 전에 적으면, 적재가
     * 실패하거나 그 사이에 프로세스가 죽었을 때 다음 실행이 그 축을 「완료」로 읽고 영영 다시
     * 부르지 않는다 — 그 공고는 그 축 없이 스냅숏에 들어가고 어느 제외 사유에도 걸리지 않는다.
     *
     * 공사 표본 하나의 둘째 축(개찰완료)에서 적재를 실패시킨다: 첫 축의 결말은 남고 둘째 축의
     * 결말은 **없어야** 한다. 있으면 미완이 완료로 둔갑한 것이다.
     */
    @Test
    fun `적재가 실패한 축은 원장에 결말이 남지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 1, rawFailsOn = SourceEndpoint.OPENING_COMPLETE)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 4)

        shouldThrow<IllegalStateException> { fixture.run() }

        fixture.attempts.appended
            .filter { it.kind == AttemptKind.AXIS }
            .map { it.axis } shouldContainExactly listOf(SourceEndpoint.RESERVE_PRICE_DETAIL)
    }

    @Test
    fun `원문은 부른 응답마다 남는다 — 목록·상세 어느 축이든 관측이 저장된다`() {
        val fixture = OpeningFixture(sampleSize = 1)
        fixture.listRows(BusinessDivision.CONSTRUCTION, "2026-06-03", count = 4)

        fixture.run()

        // 목록 4행 + 표본 하나의 상세 넷(공사라 A값까지).
        fixture.raw.appended.size shouldBe 8
    }
}

/** 합성 목록이 내는 공고 키 해시 — 이어 돌기 원장을 그 공고들에 대고 심는다. */
private fun syntheticServiceKeys(): List<String> =
    (1..3).map { NoticeKeyHash.of("SYN-6G-SERVICE-2026-06-03-%04d".format(it), "000").value }

private fun shortWalkOn(
    key: String,
    axis: SourceEndpoint,
) = CollectionAttempt(key, axis, AttemptOutcome.Failed("SHORT_WALK"), COLLECTION_NOW, AttemptKind.AXIS)

private fun settledOn(
    key: String,
    axis: SourceEndpoint,
) = CollectionAttempt(key, axis, AttemptOutcome.Succeeded, COLLECTION_NOW, AttemptKind.AXIS)
