package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
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
 * 「정확히 1 회」를 둘로 나눠 잰다: 게이트 밑에서 **0**, 게이트를 넘긴 다음 스윕에서 **1**. 앞만
 * 재면 「영영 부르지 않는다」가 통과하고, 뒤만 재면 「매 스윕 부른다」가 통과한다.
 */
class OpeningDetailFetchGateTest {
    private val axis = SourceEndpoint.RESERVE_PRICE_DETAIL

    /** 용역 한 공고 — 축 셋(예비가격·개찰완료·기초금액)이 같은 판정을 받는다. */
    private fun fixture(): OpeningFixture =
        OpeningFixture(sampleSize = 1).also { it.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 3) }

    /** 일시 실패로 끝난 스윕 하나 — 정착하지 않았으므로 이어 돌기는 그 축을 다시 부르려 한다. */
    private fun sweepThatLeavesAxesUnsettled(fixture: OpeningFixture): Int {
        fixture.service.detailTruncation = TruncationCause.Timeout
        fixture.run()
        fixture.service.detailTruncation = null
        return fixture.service.reservePriceCalls.size
    }

    @Test
    fun `재조회 게이트 미만이면 그 축을 다시 부르지 않는다`() {
        val fixture = fixture()
        val afterFirst = sweepThatLeavesAxesUnsettled(fixture)

        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(POLICY_RECHECK_GATE_HOURS - 1))
        fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize afterFirst
    }

    @Test
    fun `재조회 게이트를 넘긴 다음 스윕은 그 축을 정확히 1회 부른다`() {
        val fixture = fixture()
        val afterFirst = sweepThatLeavesAxesUnsettled(fixture)

        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(POLICY_RECHECK_GATE_HOURS + 1))
        fixture.run()

        fixture.service.reservePriceCalls shouldHaveSize afterFirst + 1
    }

    /**
     * **창의 길이가 거동을 정한다**(변이 ②의 측정점). 여기 적은 47·49 는 정책 값의 **복사가 아니다** —
     * 거동이 그 값에 매여 있음을 보이는 자리이고, 값이 바뀌면 이 test 가 붉어져 「그 변경은 거동 변경」
     * 임이 드러난다. 값 **자체**의 고정은 `bidvector.procurement.CollectionPolicyTest` 가 진다(두 자리의
     * 일은 다르다: 저쪽은 「승인된 값인가」, 이쪽은 「그 값대로 부르는가」).
     */
    @Test
    fun `창 안 47시간 뒤에는 0회 창 밖 49시간 뒤에는 1회 부른다`() {
        val inside = fixture()
        val insideFirst = sweepThatLeavesAxesUnsettled(inside)
        inside.now = COLLECTION_NOW.plus(Duration.ofHours(47))
        inside.run()

        val outside = fixture()
        val outsideFirst = sweepThatLeavesAxesUnsettled(outside)
        outside.now = COLLECTION_NOW.plus(Duration.ofHours(49))
        outside.run()

        inside.service.reservePriceCalls shouldHaveSize insideFirst
        outside.service.reservePriceCalls shouldHaveSize outsideFirst + 1
    }

    /**
     * 건너뛴 축은 **회계에 남는다**(capability 문면의 `backoff_skipped`) — 남지 않으면 「수집이
     * 줄었는데 오류는 0」이 되어 조용한 결측과 구별되지 않는다. 사유는 수집 보고가 따로 나른다.
     */
    @Test
    fun `재조회 게이트가 건너뛴 축은 건너뜀으로 센다`() {
        val fixture = fixture()
        sweepThatLeavesAxesUnsettled(fixture)
        val before = fixture.backoffSkipped()

        fixture.now = COLLECTION_NOW.plus(Duration.ofHours(POLICY_RECHECK_GATE_HOURS - 1))
        val report = fixture.run()

        fixture.backoffSkipped() - before shouldBe SERVICE_AXES
        report.skipped.recheckGate shouldBe SERVICE_AXES
        report.skipped.ageGate shouldBe 0
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
