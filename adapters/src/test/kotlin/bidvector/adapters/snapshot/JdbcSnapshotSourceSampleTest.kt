package bidvector.adapters.snapshot

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.BusinessDivision
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleList
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.collection.SampleStratum
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private val OBSERVED_AT: Instant = Instant.parse("2026-06-17T02:00:00Z")

/** 다시 걷기는 다른 시각에 온다 — 그 시각이 걷기의 이름이다(D-6G-58). */
private const val RE_WALK_GAP_SECONDS = 3600L
private val WINDOW_FROM: LocalDate = LocalDate.of(2026, 6, 16)
private val WINDOW_TO: LocalDate = LocalDate.of(2026, 6, 18)

private fun policy(): KonepsCollectionPolicyData =
    (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 7)) as Resolution.Resolved).value

private fun sampleOf(vararg numbers: String): SampleList =
    SampleList(
        numbers.associate { NoticeKeyHash.of(it, "000") to SampleStratum(BusinessDivision.SERVICE, "2026-W25") },
        scope = SampleScope(WINDOW_FROM, WINDOW_TO, setOf(BusinessDivision.SERVICE)),
    )

/**
 * D-6G-40 — 추출이 **확정 표본만** 싣는지(D-6G-39), 그리고 표본인데 행이 되지 못한 공고가
 * **사유별로** 계수되는지. dev DB 를 읽는 자리라 실 Postgres 로 잰다.
 *
 * canonical `notice` 행을 세우지 않는다 — 여기서 재는 것은 「무엇이 행이 되는가」의 **문턱**이고,
 * 문턱을 넘지 못한 공고는 canonical 이 있든 없든 행이 되지 않아야 한다.
 */
class JdbcSnapshotSourceSampleTest : PersistenceTestSupport() {
    private fun observe(
        number: String,
        endpoint: SourceEndpoint,
        round: String = "000",
        at: Instant = OBSERVED_AT,
        marker: String? = null,
    ) = appendRawObservation(
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to round) +
                (marker?.let { mapOf(RawKey("prcbdrNm") to it) } ?: emptyMap()),
            endpoint,
            at,
        ),
    )

    /** 기본은 **전 축 완료** — 이 test 들이 재는 것은 완료 판정이 아니라 그 앞의 문턱들이다. */
    private fun extract(
        sample: SampleList,
        settled: Map<String, Set<SourceEndpoint>> = allAxesSettled(sample),
    ): SnapshotExtraction = JdbcSnapshotSource(dataSource(), policy()).extract(WINDOW_FROM, WINDOW_TO, sample, settled)

    private fun allAxesSettled(sample: SampleList): Map<String, Set<SourceEndpoint>> =
        sample.keys.associate { it.value to expectedAxesFor(BusinessDivision.SERVICE.name) }

    /** 표본틀에만 있던 공고(목록 축만)는 행이 되지 않는다 — 상세를 부르지 않았으므로 결과가 없다. */
    @Test
    fun `표본이어도 상세가 없으면 행이 아니라 사유다`() {
        observe("20260617001-00", SourceEndpoint.OPENING_RESULT_LIST)

        val extraction = extract(sampleOf("20260617001-00"))

        extraction.rows.shouldBeEmpty()
        extraction.sampledWithoutDetail shouldBe 1
        extraction.skippedWithoutNotice shouldBe 0
        extraction.observedOutsideSample shouldBe 0
    }

    /** 관측이 **한 줄도 없는** 표본도 같은 사유다 — 차집합으로 세지 않으면 이 공고가 사라진다. */
    @Test
    fun `관측이 아예 없는 표본도 사유로 남는다`() {
        val extraction = extract(sampleOf("20260617001-00", "20260617002-00"))

        extraction.sampledWithoutDetail shouldBe 2
    }

    @Test
    fun `표본 밖은 상세가 있어도 싣지 않는다 — 계수만 한다`() {
        observe("20260617009-00", SourceEndpoint.OPENING_RESULT_LIST)
        observe("20260617009-00", SourceEndpoint.OPENING_COMPLETE)

        val extraction = extract(sampleOf("20260617001-00"))

        extraction.rows.shouldBeEmpty()
        extraction.observedOutsideSample shouldBe 1
        extraction.sampledWithoutDetail shouldBe 1
    }

    /** 상세는 받았는데 공고 목록 canonical 이 없으면 대분류를 몰라 행을 만들 수 없다 — 다른 사유다. */
    @Test
    fun `상세는 있는데 canonical 공고가 없으면 또 다른 사유다`() {
        observe("20260617001-00", SourceEndpoint.OPENING_COMPLETE)

        val extraction = extract(sampleOf("20260617001-00"))

        extraction.rows.shouldBeEmpty()
        extraction.skippedWithoutNotice shouldBe 1
        extraction.sampledWithoutDetail shouldBe 0
    }

    /**
     * D-6G-58 — **완료되지 않은 축이 있으면 행을 쓰지 않는다.** 한 축이 페이지 중간에 끊기면 그
     * 축의 원문은 일부만 있고, 그 반쪽으로 쓴 행은 값이 조용히 틀리면서(받은 쪽까지만 센 투찰자
     * 수) 어느 제외 사유에도 걸리지 않는다. 완료는 시도 원장이 정한다 — raw 존재가 아니다.
     */
    @Test
    fun `축 하나가 끝나지 않았으면 행이 아니라 계수다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        observe(number, SourceEndpoint.OPENING_COMPLETE)
        val sample = sampleOf(number)

        // 원문은 있지만 개찰완료 축이 원장에서 끝나지 않았다(예: 2쪽 중 1쪽에서 끊겼다).
        val partial = expectedAxesFor(BusinessDivision.SERVICE.name) - SourceEndpoint.OPENING_COMPLETE
        val extraction = extract(sample, mapOf(sample.keys.single().value to partial))

        extraction.rows.shouldBeEmpty()
        extraction.incompleteAxis shouldBe 1
        extraction.skippedWithoutNotice shouldBe 0
        extraction.sampledWithoutDetail shouldBe 0
    }

    /**
     * D-6G-58 — 원문은 append-only 다(DB 트리거가 DELETE 를 막는다). 그래서 **잘린 걷기의 쪽이
     * 그대로 남고**, 다시 걸어 받은 전 쪽과 합쳐지면 그 공고의 투찰 행이 실제보다 많아진다 — 참가자
     * 수와 1위 투찰가가 조용히 틀리고, 행이 늘 뿐이라 어느 제외 사유에도 걸리지 않는다. 추출은
     * (공고, 축)마다 **마지막 걷기**의 행만 쓴다(한 걷기의 모든 쪽은 같은 관측 시각을 단다).
     */
    @Test
    fun `다시 걸은 축은 마지막 걷기의 행만 쓴다 — 앞 걷기와 합쳐지지 않는다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        // 첫 걷기 — 2쪽 중 1쪽에서 끊겨 투찰 행 둘만 남았다.
        repeat(2) { observe(number, SourceEndpoint.OPENING_COMPLETE, marker = "첫-걷기-$it") }
        // 다시 걷기 — 전 쪽을 받아 투찰 행 셋.
        val again = OBSERVED_AT.plusSeconds(RE_WALK_GAP_SECONDS)
        repeat(3) { observe(number, SourceEndpoint.OPENING_COMPLETE, at = again, marker = "다시-걷기-$it") }

        val row = extract(sampleOf(number)).rows.single()

        row.outcome.bidderRows shouldHaveSize 3
    }

    /** 공사는 A값까지 넷이다 — 부르지 않는 축을 기다리면 공사 아닌 공고가 영영 행이 되지 않는다. */
    @Test
    fun `부를 축은 업무가 정한다`() {
        expectedAxesFor(BusinessDivision.CONSTRUCTION.name) shouldHaveSize 4
        expectedAxesFor(BusinessDivision.SERVICE.name) shouldHaveSize 3
        expectedAxesFor(BusinessDivision.SERVICE.name).contains(SourceEndpoint.BID_PRICE_FORMULA_A) shouldBe false
    }

    /** 사유 셋의 합이 표본 크기를 덮는다(스키마 §2 닫힌 항등식) — 어느 쪽도 아닌 공고는 없다. */
    @Test
    fun `표본 하나하나가 행이거나 사유다`() {
        observe("20260617001-00", SourceEndpoint.OPENING_COMPLETE)
        observe("20260617002-00", SourceEndpoint.OPENING_RESULT_LIST)
        observe("20260617009-00", SourceEndpoint.OPENING_COMPLETE)
        val sample = sampleOf("20260617001-00", "20260617002-00", "20260617003-00")

        val extraction = extract(sample)

        val accounted =
            extraction.rows.size + extraction.sampledWithoutDetail + extraction.skippedWithoutNotice
        accounted shouldBe sample.keys.size
    }

    /**
     * D-6G-42 M-9 — 차수가 서지 않는 행은 **기본값을 받지 않는다.** `toIntOrNull() ?: 0` 이면 파싱
     * 실패가 첫 차수(`000` = 0)로 둔갑해 제외 ③(재입찰·정정)을 그대로 통과한다 — 몇 차 공고인지
     * 모르는 공고가 「첫 공고」로 실험에 든다.
     */
    @Test
    fun `차수가 제로패딩 세 자리가 아니면 행이 되지 않는다`() {
        observe("20260617001-00", SourceEndpoint.OPENING_COMPLETE, round = "1")

        val extraction = extract(sampleOf("20260617001-00"))

        extraction.rows.shouldBeEmpty()
        // 표본의 키 해시는 canonical 차수로 만들어졌으므로 이 행은 애초에 그 키가 아니다.
        extraction.sampledWithoutDetail shouldBe 1
    }

    /**
     * 표본 밖 공고의 원문은 **펴지 않는다**(code-review r2 LOW) — 창 안 전건을 파싱해 메모리에
     * 올리면 표본과 무관한 공고까지 통째로 적재된다. 결과는 계수로만 드러나므로, 표본 밖 키가
     * 여럿일 때 **키 수**로 세는지(행 수가 아니라) 확인한다.
     */
    @Test
    fun `표본 밖은 키 수로 센다 — 행 수가 아니다`() {
        // 한 공고에 축 둘·행 여럿 — 행으로 세면 이 값이 1 이 아니다.
        observe("20260617009-00", SourceEndpoint.OPENING_RESULT_LIST)
        observe("20260617009-00", SourceEndpoint.OPENING_COMPLETE)
        observe("20260617008-00", SourceEndpoint.OPENING_COMPLETE)

        val extraction = extract(sampleOf("20260617001-00"))

        extraction.observedOutsideSample shouldBe 2
    }

    /** 관측 창 밖의 원문은 보지 않는다 — 창은 `observed_at` 으로 자른다. */
    @Test
    fun `창 밖 관측은 상세로 치지 않는다`() {
        observe("20260617001-00", SourceEndpoint.OPENING_COMPLETE)

        val extraction =
            JdbcSnapshotSource(dataSource(), policy())
                .extract(
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 2),
                    sampleOf("20260617001-00"),
                    allAxesSettled(sampleOf("20260617001-00")),
                )

        extraction.sampledWithoutDetail shouldBe 1
        extraction.skippedWithoutNotice shouldBe 0
    }

    /**
     * D-6G-54(vr M-4) — **공고 목록 축은 창이 자르지 않는다.** 공고는 개찰보다 먼저 적재되므로
     * 추출 창이 목록 적재 시각을 덮지 않는 것이 정상인데, 그것까지 자르면 그 행의 공고일·낙찰방법·
     * 분류가 전부 사라진다(전 행 `NOTICE_DATE_ABSENT`). canonical 결합에도 시각 조건이 없다 —
     * 있으면 같은 구멍이 `notice` 표 쪽에 생긴다.
     *
     * 계수로는 이 회귀가 드러나지 않는다(어느 쪽이든 사유는 같다) — **행의 값**으로 잰다.
     */
    @Test
    fun `창 밖의 공고 목록 관측이 행의 공고일을 채운다`() {
        val number = "20260617001-00"
        // 목록 축은 창보다 **한 달 앞서** 적재됐다. 공고일·낙찰방법이 이 행에만 있다.
        val listObservation =
            RawNoticeObservation.of(
                mapOf(
                    RawKey("bidNtceNo") to number,
                    RawKey("bidNtceOrd") to "000",
                    RawKey("bidNtceDt") to "2026-05-17 09:00:00",
                    RawKey("sucsfbidMthdCd") to "낙030001",
                ),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-05-17T02:00:00Z"),
            )
        persistCanonical(number, listObservation)
        observe(number, SourceEndpoint.OPENING_COMPLETE)

        val row = extract(sampleOf(number)).rows.single()

        row.notice.noticedOn shouldBe LocalDate.of(2026, 5, 17)
        row.notice.successfulBidMethodCode shouldBe "낙030001"
    }

    /** canonical 공고를 **출하 경로**(repository)로 세운다 — test 전용 SQL 사본을 두지 않는다. */
    private fun listObservation(number: String): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            OBSERVED_AT,
        )

    private fun persistCanonical(
        number: String,
        observation: RawNoticeObservation,
    ) {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        JdbcNoticeRepository(dataSource()).persist(
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
                businessDivision = BusinessDivision.SERVICE,
                serviceDivision = null,
                mainConstructionType = null,
            ),
            appendRawObservation(observation),
        )
    }
}
