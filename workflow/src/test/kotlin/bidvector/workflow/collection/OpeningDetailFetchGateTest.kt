package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

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
}

/** 용역 공고 하나가 가진 상세 축 수(예비가격·개찰완료·기초금액) — 공사만 A값이 하나 더 붙는다. */
private const val SERVICE_AXES = 3
