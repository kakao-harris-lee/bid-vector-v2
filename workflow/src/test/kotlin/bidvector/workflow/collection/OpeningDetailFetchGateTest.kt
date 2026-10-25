package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetLimit
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.DetailFetchSkipReason
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * **조회 가치 술어가 출하 경로에서 발화하는가**(COL-03 둘째 bullet, M6/G1-C03) — 술어 자신의 단위
 * test(`bidvector.procurement.DetailFetchTest`)와 재는 것이 다르다: 저쪽은 네 결과의 **값**이고
 * 이쪽은 6G 수집 use case 가 그 결과대로 **부르거나 부르지 않는가**다. G1-T 의 판정이 거짓이었던
 * 자리가 정확히 이 간극이었다 — 술어는 옳았고 호출부가 상수를 넘겼다.
 *
 * **두 판을 나눠 잰다.** 출하 정책의 재조회 창은 **0** 이므로(D-G1C03-2) 출하 거동은 「재조회로
 * 건너뛰는 일이 없다」이고, 「창이 서면 건너뛴다」는 **합성 정책**으로 잰다 — 배선은 값과 무관하게
 * 같은 자리이고, **값이 거동을 정한다는 것 자체**가 재는 대상이다.
 */
class OpeningDetailFetchGateTest {
    private val axis = SourceEndpoint.RESERVE_PRICE_DETAIL

    /** 용역 한 공고 — 축 셋(예비가격·개찰완료·기초금액)이 같은 판정을 받는다. */
    private fun fixture(gates: DetailFetchGates = COLLECTION_POLICY.detailFetchGates) =
        OpeningFixture(sampleSize = 1, gates = gates).also {
            it.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)
        }

    /** 일시 실패로 끝난 스윕 하나 — 정착하지 않았으므로 이어 돌기는 그 축을 다시 부르려 한다. */
    private fun sweepThatLeavesAxesUnsettled(fixture: OpeningFixture): Int {
        fixture.service.detailTruncation = TruncationCause.Timeout
        fixture.run()
        fixture.service.detailTruncation = null
        return fixture.service.reservePriceCalls.size
    }

    /**
     * **출하 거동**(D-G1C03-2) — 창이 0 이므로 **다음 스윕이 바로** 그 축을 다시 부른다. 창을 0 이
     * 아닌 값으로 올리면 이 test 가 붉어져 「그 값 변경은 이어 돌기 거동 변경」임을 그 자리에서
     * 알린다(변이 ②).
     *
     * 쪽 중간에 끊긴 축이 다음 기동에 재개되지 않으면 결손이 **참가자 많은 공고에 몰린다** — 끊길
     * 확률이 쪽 수에 비례하기 때문이다. 같은 보증을 어댑터·app 층에서 재는 것이
     * `bidvector.app.collection.OpeningBudgetE2ETest` 다.
     */
    @Test
    fun `출하 값에서는 다음 스윕이 미완 축을 바로 다시 부른다`() {
        val fixture = fixture()
        val afterFirst = sweepThatLeavesAxesUnsettled(fixture)

        fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize afterFirst + 1
        fixture.backoffSkipped() shouldBe 0
    }

    /**
     * **창이 서면 건너뛴다**(합성 정책) — 배선이 값을 **해석만** 한다는 것을 보인다. 창 밑에서 0 회,
     * 넘긴 뒤 1 회를 두 걸음으로 잰다: 앞만 재면 「영영 안 부른다」가 통과하고, 뒤만 재면 「매 스윕
     * 부른다」가 통과한다.
     */
    @Test
    fun `합성 창 밑에서는 다시 부르지 않고 넘긴 뒤에는 정확히 1회 부른다`() {
        val inside = fixture(SYNTHETIC_RECHECK_GATES)
        val insideFirst = sweepThatLeavesAxesUnsettled(inside)
        inside.now = COLLECTION_NOW.plus(Duration.ofHours(SYNTHETIC_RECHECK_GATE_HOURS - 1))
        inside.run()

        val outside = fixture(SYNTHETIC_RECHECK_GATES)
        val outsideFirst = sweepThatLeavesAxesUnsettled(outside)
        outside.now = COLLECTION_NOW.plus(Duration.ofHours(SYNTHETIC_RECHECK_GATE_HOURS + 1))
        outside.run()

        inside.service.reservePriceCalls shouldHaveSize insideFirst
        outside.service.reservePriceCalls shouldHaveSize outsideFirst + 1
    }

    /**
     * 건너뛴 축은 **회계에 남는다**(capability 문면의 `backoff_skipped`) — 남지 않으면 「수집이
     * 줄었는데 오류는 0」이 되어 조용한 결측과 구별되지 않는다. 사유는 수집 보고가 따로 나른다.
     */
    @Test
    fun `창이 건너뛴 축은 사유와 함께 건너뜀으로 센다`() {
        val fixture = fixture(SYNTHETIC_RECHECK_GATES)
        sweepThatLeavesAxesUnsettled(fixture)
        val before = fixture.backoffSkipped()

        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(SYNTHETIC_RECHECK_GATE_HOURS - 1))
        val report = fixture.run()

        fixture.backoffSkipped() - before shouldBe SERVICE_AXES
        report.skipped.recheckGate shouldBe SERVICE_AXES
        report.skipped.ageGate shouldBe 0
    }

    /**
     * **기준 시각을 싣지 않는 경로에서는 age 판정이 서지 않는다**(D-G1C03-1, 운영자 결정 (나)) —
     * 건너뛰는 것은 **판정**이지 호출이 아니다. 표본틀이 기준 시각을 실었다면(개찰이 방금이었다면)
     * age 창이 이 스윕의 전 축을 건너뛰게 했을 자리에서, 지금 경로는 전 축을 부르고 age 계수는 0 이다.
     *
     * 이 test 를 붉히는 변이는 **선언을 관측값으로 바꿔치기**(④)다. 별 slice 가 그 한 줄을 실값으로
     * 바꾸면 이 test 가 **의도대로** 붉어져, 그 변경이 거동 변경임을 그 slice 에 알린다.
     */
    @Test
    fun `표본틀이 기준 시각을 싣지 않으므로 age 판정이 서지 않는다`() {
        val fixture = fixture()

        val report = fixture.run()

        report.skipped.ageGate shouldBe 0
        fixture.service.reservePriceCalls shouldHaveSize 1
    }

    /**
     * **이미 받은 축은 백오프가 아니다**(첫째 bullet 과의 경계) — 재수집 0 회는 같지만 사유가 다르고,
     * 이것을 같은 칸에 세면 그 신호가 매 스윕 표본 크기만큼 울려 둔해진다.
     */
    @Test
    fun `이미 받은 축은 부르지 않고 건너뜀으로도 세지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 1, collectedAxes = FakeCollectedAxisStore(setOf(axis)))
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3)

        val report = fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize 0
        report.skipped.ageGate shouldBe 0
        report.skipped.recheckGate shouldBe 0
        fixture.backoffSkipped() shouldBe 0
    }

    /**
     * **이번 실행이 닫은 끊긴 라운드는 창을 열지 않는다** — 닫는 줄의 시각은 「닫은 때」라 관측이 아니다.
     * 그 줄을 관측으로 읽으면 닫자마자 창 안이 되어 끊긴 축이 재개되지 않는다.
     */
    @Test
    fun `이번 실행이 닫은 끊긴 라운드는 창을 열지 않는다`() {
        val fixture = fixture(SYNTHETIC_RECHECK_GATES)
        crashOnIntent(fixture)
        val afterCrash = fixture.service.reservePriceCalls.size

        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(1))
        val report = fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize afterCrash + 1
        report.skipped.recheckGate shouldBe 0
    }

    /**
     * **앞 실행이 닫고 그 축을 관측하지 못한 채 멈춰도** 다음 실행은 그 닫는 줄로 창을 열지 않는다.
     * 둘째 기동은 라운드를 닫은 뒤 그 축의 관문에서 거부되어 멈춘다(거부는 관측이 아니다) — 셋째 기동이
     * 그 축을 바로 다시 부른다.
     */
    @Test
    fun `앞 실행이 닫고 그 축을 관측하지 못한 채 멈춰도 창을 열지 않는다`() {
        val fixture = fixture(SYNTHETIC_RECHECK_GATES)
        crashOnIntent(fixture)
        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(1))
        fixture.service.detailTruncation = TruncationCause.BudgetExhausted(BudgetLimit.DAILY)
        fixture.run().halted.shouldNotBeNull()
        fixture.service.detailTruncation = null
        val beforeThird = fixture.service.reservePriceCalls.size

        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(2))
        val report = fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize beforeThird + 1
        report.skipped.recheckGate shouldBe 0
    }

    /**
     * **멈춘 실행도 그때까지의 건너뜀을 회계에 싣는다** — 멈춘 실행에서 건너뜀이 사라지면 그날의
     * `backoff_skipped` 가 실제보다 작다. 예비가격 축만 한 시간 전에 관측돼 이번 스윕은 창 안이고, 다음
     * 축이 승인 상한에서 멈춘다.
     */
    @Test
    fun `멈춘 실행도 그때까지의 건너뜀을 회계에 싣는다`() {
        val fixture = fixture(SYNTHETIC_RECHECK_GATES)
        val sample = fixture.plan().selected
        val key = sample.single().value
        val at = COLLECTION_NOW
        val timedOut = CollectionAttempt(key, axis, AttemptOutcome.Failed("TIMEOUT"), at, AttemptKind.AXIS, walk = at)
        fixture.attempts.append(timedOut)
        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(1))
        fixture.service.detailTruncation = TruncationCause.BudgetExhausted(BudgetLimit.DAILY)

        val report = fixture.run()

        report.halted.shouldNotBeNull()
        report.skipped.recheckGate shouldBe 1
        fixture.backoffSkipped() shouldBe 1
    }

    /**
     * **기준 시각은 후보가 선언째 술어에 나른다**(D-G1C03-1) — 출하 표본틀은 [OpeningAnchor.Observed] 를
     * 싣지 않으므로 use case 층에서는 이 배선을 잴 수 없다. 그래서 그 배선이 사는 유일한 자리
     * ([detailFetchDecision])를 관측값을 실은 후보로 잰다: age 창 밑이면 age 로 건너뛰고, 넘기면 부른다.
     */
    @Test
    fun `관측한 개찰 일시는 age 창 밑에서 건너뛰고 넘긴 뒤 부른다`() {
        val gates = COLLECTION_POLICY.detailFetchGates
        val openedAt = COLLECTION_NOW
        val observed = candidate(OpeningAnchor.Observed(openedAt))

        val inside = observed.decideAt(openedAt.plus(Duration.ofHours(gates.ageGateHours - 1)), gates)
        val outside = observed.decideAt(openedAt.plus(Duration.ofHours(gates.ageGateHours + 1)), gates)

        inside
            .shouldBeInstanceOf<DetailFetchDecision.Skip>()
            .reason
            .shouldBeInstanceOf<DetailFetchSkipReason.AgeGateNotPassed>()
        outside.shouldBeInstanceOf<DetailFetchDecision.Fetch>()
        candidate(OpeningAnchor.NotCarriedByFrame)
            .decideAt(openedAt.plus(Duration.ofHours(1)), gates)
            .shouldBeInstanceOf<DetailFetchDecision.Fetch>()
    }

    /**
     * 사유별 수는 **사유마다 따로** 세고 보유는 세지 않는다 — age 건너뜀은 출하 경로에서 아직 생기지 않으므로
     * (기준 시각을 싣지 않는다) 이 자리에서 잰다. 별 slice 가 기준 시각을 실을 때 age 건너뜀이 조용히
     * 안 세어지는 일을 막는다.
     */
    @Test
    fun `건너뜀은 사유마다 따로 세고 보유는 세지 않는다`() {
        val tally = DetailSkipTally()
        tally.record(axis, DetailFetchSkipReason.AgeGateNotPassed(COLLECTION_NOW))
        repeat(2) { tally.record(axis, DetailFetchSkipReason.RecheckGateNotPassed(COLLECTION_NOW)) }
        tally.record(axis, DetailFetchSkipReason.AlreadyHeld)

        tally.counts() shouldBe DetailSkipCounts(ageGate = 1, recheckGate = 2)
    }

    /** 의도 줄만 남기고 죽는 기동 하나 — 예비가격 축에 끊긴 라운드가 남는다. */
    private fun crashOnIntent(fixture: OpeningFixture) {
        fixture.service.crashAfterIntentOn = axis
        shouldThrow<IllegalStateException> { fixture.run() }
        fixture.service.crashAfterIntentOn = null
    }

    private fun candidate(anchor: OpeningAnchor): Candidate {
        val id = NoticeId(NoticeNumber.of("SYN-ANCHOR-0001"), NoticeRound.of("000"))
        val source =
            OpeningCollectionSource(
                sourceName("service"),
                BusinessDivision.SERVICE,
                ScriptedOpeningPort(BusinessDivision.SERVICE),
            )
        val sampled = SampleCandidate(keyOf(id), BusinessDivision.SERVICE, LocalDate.parse("2026-06-03"))
        return Candidate(id, sampled, source, anchor)
    }

    private fun Candidate.decideAt(
        now: Instant,
        gates: DetailFetchGates,
    ): DetailFetchDecision = detailFetchDecision(alreadyHeld = false, lastCheckedAt = null, now = now, gates = gates)
}

/** 용역 공고 하나가 가진 상세 축 수(예비가격·개찰완료·기초금액) — 공사만 A값이 하나 더 붙는다. */
private const val SERVICE_AXES = 3
