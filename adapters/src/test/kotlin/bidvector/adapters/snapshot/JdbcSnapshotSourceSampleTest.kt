package bidvector.adapters.snapshot

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.BusinessDivision
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleList
import bidvector.workflow.collection.SampleStratum
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private val OBSERVED_AT: Instant = Instant.parse("2026-06-17T02:00:00Z")
private val WINDOW_FROM: LocalDate = LocalDate.of(2026, 6, 16)
private val WINDOW_TO: LocalDate = LocalDate.of(2026, 6, 18)

private fun policy(): KonepsCollectionPolicyData =
    (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 7)) as Resolution.Resolved).value

private fun sampleOf(vararg numbers: String): SampleList =
    SampleList(
        numbers.associate { NoticeKeyHash.of(it, "000") to SampleStratum(BusinessDivision.SERVICE, "2026-W25") },
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
    ) = appendRawObservation(
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to round),
            endpoint,
            OBSERVED_AT,
        ),
    )

    private fun extract(sample: SampleList): SnapshotExtraction =
        JdbcSnapshotSource(dataSource(), policy()).extract(WINDOW_FROM, WINDOW_TO, sample)

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
                .extract(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), sampleOf("20260617001-00"))

        extraction.sampledWithoutDetail shouldBe 1
        extraction.skippedWithoutNotice shouldBe 0
    }
}
