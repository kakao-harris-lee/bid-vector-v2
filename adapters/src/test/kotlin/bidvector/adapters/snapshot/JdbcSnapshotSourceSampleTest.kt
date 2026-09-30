package bidvector.adapters.snapshot

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.AxisConclusion
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
import java.time.temporal.ChronoUnit

/**
 * D-6G-40 — 추출이 **확정 표본만** 싣는지(D-6G-39), 그리고 표본인데 행이 되지 못한 공고가
 * **사유별로** 계수되는지. dev DB 를 읽는 자리라 실 Postgres 로 잰다.
 *
 * canonical `notice` 행을 세우지 않는다 — 여기서 재는 것은 「무엇이 행이 되는가」의 **문턱**이고,
 * 문턱을 넘지 못한 공고는 canonical 이 있든 없든 행이 되지 않아야 한다.
 */
class JdbcSnapshotSourceSampleTest : SnapshotSourceTestBase() {
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

        // 원문은 있지만 개찰완료 축이 원장에서 끝나지 않았다(예: 2쪽 중 1쪽에서 끊겼다) —
        // 그 축의 결말은 **미완**이고, 걷기를 가리켜도 그 행은 쓰이지 않는다.
        val partial =
            allAxesSettled(sample) +
                (
                    sample.keys.single().value to
                        allAxesSettled(sample).getValue(sample.keys.single().value) +
                        (SourceEndpoint.OPENING_COMPLETE to AxisConclusion(shortWalk, observedAt))
                )
        val extraction = extract(sample, partial)

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
        val again = observedAt.plusSeconds(reWalkGapSeconds)
        repeat(3) { observe(number, SourceEndpoint.OPENING_COMPLETE, at = again, marker = "다시-걷기-$it") }

        val row = extract(sampleOf(number), allAxesSettled(sampleOf(number), again)).rows.single()

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

        // **항이 넷이다**(cr r5 L-5) — 앞 판은 `incompleteAxis` 를 빼고 셋만 더했고, 그 판의 값이
        // 0 이라 성립했다. 계수를 축 미완으로 잘못 귀속하는 변이가 그 문턱을 그대로 지났다.
        val accounted =
            extraction.rows.size + extraction.sampledWithoutDetail +
                extraction.skippedWithoutNotice + extraction.incompleteAxis
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

    /**
     * **D-6G-68 (vr r5 H-1 probe W1) — 빈 응답으로 끝난 축은 0 행이다.** 다시 걸었더니 KONEPS 가
     * `NODATA` 를 돌려주면 그 걷기는 행을 남기지 않는다. 앞 판은 걷기를 **행의 시각**으로 골랐으므로
     * 그 재걷기가 아예 보이지 않았고, 그 앞의 **잘린 걷기**가 마지막으로 보여 투찰 둘짜리 행이
     * 완성돼 실렸다 — 계수도 오류도 없이. 지금은 원장이 「빈 응답」을 가리키고, 그 축은 0 행이다.
     */
    @Test
    fun `빈 응답으로 끝난 축은 앞의 잘린 걷기를 쓰지 않는다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        // 첫 걷기는 2쪽 중 1쪽에서 끊겨 투찰 행 둘을 남겼다. 재걷기는 NODATA — 원문 0 행.
        repeat(2) { observe(number, SourceEndpoint.OPENING_COMPLETE, marker = "끊긴-걷기-$it") }

        val extraction = extract(sampleOf(number), allAxesSettled(sampleOf(number), outcome = AttemptOutcome.Empty))

        extraction.rows.shouldBeEmpty()
        extraction.sampledWithoutDetail shouldBe 1
    }

    /**
     * **D-6G-68 (vr r5 H-1 probe W2) — 걷기의 순서를 벽시계가 정하지 않는다.** 실행 사이에 시계가
     * 뒤로 가면(이 호스트는 WSL2, 시각 보정이 있다) 나중에 받은 **전 쪽 걷기**가 앞의 잘린 걷기보다
     * 이른 시각을 단다. 「가장 늦은 `observed_at`」은 그때 잘린 걷기를 고른다. 원장이 가리키면
     * 시각의 대소는 상관이 없다.
     */
    @Test
    fun `시계가 뒤로 가도 원장이 가리킨 걷기를 쓴다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        // 끊긴 걷기가 **더 늦은** 시각을 달았다(시계가 앞서 있던 실행).
        val skewed = observedAt.plusSeconds(reWalkGapSeconds)
        repeat(2) { observe(number, SourceEndpoint.OPENING_COMPLETE, at = skewed, marker = "끊긴-걷기-$it") }
        // 다시 걸어 전 쪽을 받았지만 시각은 뒤로 간 시계의 것이다.
        repeat(3) { observe(number, SourceEndpoint.OPENING_COMPLETE, marker = "전-쪽-$it") }

        val row = extract(sampleOf(number), allAxesSettled(sampleOf(number), observedAt)).rows.single()

        row.outcome.bidderRows shouldHaveSize 3
    }

    /**
     * **D-6G-68 (vr r5 H-1 probe W5) — 추출에 관측 창이 없다.** 앞 판은 `observed_at` 으로 창을
     * 잘랐고, 그 창은 **원장에는 걸리지 않았다**: 창 밖에서 다시 걸은 축은 행이 보이지 않는데 원장은
     * 「완료」라고 말해, 그 공고가 **앞의 잘린 걷기의 행으로** 완성돼 실렸다. 범위를 정하는 것은
     * 표본 목록과 원장이다 — 창이 아니라.
     */
    @Test
    fun `창 밖에서 다시 걸은 축의 행도 원장이 가리키면 쓴다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        // 첫 걷기는 끊겨 투찰 행 둘, 다시 걷기는 **한 달 뒤**(옛 추출 창 밖)에 전 쪽 셋.
        repeat(2) { observe(number, SourceEndpoint.OPENING_COMPLETE, marker = "첫-걷기-$it") }
        val farLater = observedAt.plus(30, ChronoUnit.DAYS)
        repeat(3) { observe(number, SourceEndpoint.OPENING_COMPLETE, at = farLater, marker = "창-밖-$it") }

        val extraction = extract(sampleOf(number), allAxesSettled(sampleOf(number), farLater))

        extraction.sampledWithoutDetail shouldBe 0
        extraction.rows
            .single()
            .outcome.bidderRows shouldHaveSize 3
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

    /**
     * **D-6G2d-3 (vr r5-t probe W6) — 결말 줄이 없는 축은 가장 늦은 걷기의 행만 쓴다.** AXIS 결말
     * 줄을 쓰는 자리는 상세 축 넷뿐이라 목록 축 둘은 **언제나** 결말이 없다. 앞 판은 그 축의 행을
     * 아무 선별 없이 모으고 조립이 적재 순서의 첫 행을 취해 **가장 오래된 관측**을 썼다 — 발주처가
     * 정정해 다시 걸어도 낡은 값이 실린다. 계수로는 드러나지 않는다(어느 쪽이든 사유가 같다).
     */
    @Test
    fun `결말 줄이 없는 목록 축은 가장 늦은 관측을 쓴다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        observe(number, SourceEndpoint.OPENING_COMPLETE)
        appendRawObservation(openingListObservation(number, participants = "2", at = observedAt))
        val again = observedAt.plusSeconds(reWalkGapSeconds)
        appendRawObservation(openingListObservation(number, participants = "5", at = again))

        val row = extract(sampleOf(number)).rows.single()

        row.outcome.participantCount shouldBe 5
    }

    /**
     * **D-6G2d-3 (vr r5-t probe W6b) — 실수집에서 발화하는 모양.** 개발 DB 에는 6F-8·6F-9 수집이
     * 남긴 공고 목록 원문이 있고 그 행들은 6G 가 계약에 더한 공고일·낙찰방법 칸을 **싣지 않는다**
     * (`payload_fields` 는 적재 당시 등재 칸의 투영이다). 가장 오래된 관측을 쓰면 그 기간 표본의
     * 공고일이 null 이 되고 Python 이 공고일 결측으로 **통째로** 뺀다 — 날짜로 몰린 비랜덤 제외다.
     */
    @Test
    fun `6G 칸이 없는 옛 목록 관측 뒤의 새 관측이 공고일과 낙찰방법을 채운다`() {
        val number = "20260617001-00"
        // 6F-8 판 — 식별자 둘뿐이다.
        persistCanonical(number, listObservation(number))
        observe(number, SourceEndpoint.OPENING_COMPLETE)
        // 6G 판 — 같은 공고를 다시 걸어 새 칸이 실렸다.
        appendRawObservation(
            RawNoticeObservation.of(
                mapOf(
                    RawKey("bidNtceNo") to number,
                    RawKey("bidNtceOrd") to "000",
                    RawKey("bidNtceDt") to "2026-06-03 09:00:00",
                    RawKey("sucsfbidMthdCd") to "낙030001",
                ),
                SourceEndpoint.NOTICE_LIST,
                observedAt.plusSeconds(reWalkGapSeconds),
            ),
        )

        val row = extract(sampleOf(number)).rows.single()

        row.notice.noticedOn shouldBe LocalDate.of(2026, 6, 3)
        row.notice.successfulBidMethodCode shouldBe "낙030001"
    }

    /**
     * **D-6G2d-3 — 걷기의 순서는 적재 순서가 아니라 관측 시각이다.** 재걷기는 보통 뒤에 적재되므로
     * 두 기준이 같은 답처럼 보인다. backfill(이른 관측을 뒤늦게 적재)은 두 기준을 갈라놓는다 —
     * `inserted_at` 으로 고르면 나중에 들어온 **이른** 관측이 이긴다.
     */
    @Test
    fun `걷기 선별의 기준은 적재 시각이 아니다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        observe(number, SourceEndpoint.OPENING_COMPLETE)
        val again = observedAt.plusSeconds(reWalkGapSeconds)
        appendRawObservation(openingListObservation(number, participants = "5", at = again))
        // 이른 관측이 **뒤에** 적재된다.
        appendRawObservation(openingListObservation(number, participants = "2", at = observedAt))

        val row = extract(sampleOf(number)).rows.single()

        row.outcome.participantCount shouldBe 5
    }

    /**
     * **D-6G2d-8 ⓐ — 공고번호가 빈 원문 행 한 줄이 추출 전체를 멈추지 않는다.** 적재는 정규화
     * **전에** 일어나고 원문은 append-only 다(DB 트리거) — 번호 없는 항목이 한 번 들어오면 그 행은
     * 지울 수 없고, 관측 창도 없어져 추출은 매번 그 행을 만난다. 키를 갖지 못한 행은 버리고 **수를
     * 공시한다**: 어느 표본 공고에도 속하지 않으므로 네 항 항등식은 그대로다.
     */
    @Test
    fun `번호가 빈 원문 행은 추출을 멈추지 않고 계수된다`() {
        val number = "20260617001-00"
        persistCanonical(number, listObservation(number))
        observe(number, SourceEndpoint.OPENING_COMPLETE)
        appendRawObservation(
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to " ", RawKey("bidNtceOrd") to "000"),
                SourceEndpoint.OPENING_COMPLETE,
                observedAt,
            ),
        )

        val extraction = extract(sampleOf(number))

        extraction.rows shouldHaveSize 1
        extraction.unusableRawRows shouldBe 1
        extraction.observedOutsideSample shouldBe 0
    }
}
