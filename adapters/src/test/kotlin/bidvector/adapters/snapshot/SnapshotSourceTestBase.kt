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
 * 추출 test 셋의 공통 대역(cr r4 ⑧ — 파일 500 줄 한도로 갈랐다). **문턱**을 재는 판과 **계수 배선**을
 * 재는 판이 같은 dev DB 대역을 쓰므로, 그 대역을 여기 한 자리에 둔다 — 두 파일에 복사하면 한쪽이 낡는다.
 *
 * 이름을 파일 수준에 두지 않고 **보호된 멤버**로 둔다: `internal` 파일 수준 이름은 패키지 전체에 보이고,
 * 같은 패키지의 다른 test 파일이 같은 이름(`policy` 같은)을 이미 쓴다.
 */
abstract class SnapshotSourceTestBase : PersistenceTestSupport() {
    protected val observedAt: Instant = Instant.parse("2026-06-17T02:00:00Z")

    /** 짧은 걷기 — 미완이다(다시 부른다). 원장이 그 축을 끝내지 않았다는 뜻이다. */
    protected val shortWalk = AttemptOutcome.Failed("SHORT_WALK")

    /** 다시 걷기는 다른 시각에 온다 — 그 시각이 걷기의 이름이다(D-6G-58). */
    protected val reWalkGapSeconds = 3600L

    /** 용역이 부르는 축 셋 — 업무가 정한다(`expectedAxesFor`). */
    protected val serviceAxes: List<SourceEndpoint> =
        listOf(
            SourceEndpoint.RESERVE_PRICE_DETAIL,
            SourceEndpoint.OPENING_COMPLETE,
            SourceEndpoint.BASE_AMOUNT_DETAIL,
        )

    protected val windowFrom: LocalDate = LocalDate.of(2026, 6, 16)
    protected val windowTo: LocalDate = LocalDate.of(2026, 6, 18)

    protected fun policy(): KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 7)) as Resolution.Resolved).value

    protected fun sampleOf(vararg numbers: String): SampleList =
        SampleList(
            numbers.associate { NoticeKeyHash.of(it, "000") to SampleStratum(BusinessDivision.SERVICE, "2026-W25") },
            scope = SampleScope(windowFrom, windowTo, setOf(BusinessDivision.SERVICE)),
        )

    protected fun observe(
        number: String,
        endpoint: SourceEndpoint,
        round: String = "000",
        at: Instant = observedAt,
        marker: String? = null,
        fields: Map<String, String> = emptyMap(),
    ) = appendRawObservation(
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to round) +
                (marker?.let { mapOf(RawKey("prcbdrNm") to it) } ?: emptyMap()) +
                fields.mapKeys { RawKey(it.key) },
            endpoint,
            at,
        ),
    )

    /** 기본은 **전 축 완료 · 걷기는 [observedAt]** — 이 test 들이 재는 것은 그 앞의 문턱들이다. */
    protected fun extract(
        sample: SampleList,
        conclusions: Map<String, Map<SourceEndpoint, AxisConclusion>> = allAxesSettled(sample),
    ): SnapshotExtraction = JdbcSnapshotSource(dataSource(), policy()).extract(sample, conclusions)

    /**
     * 원장이 가리키는 걷기(D-6G-68)와 그 걷기의 **결말**. 걷기는 언제나 있다(D-6G2d-4 ⓑ) — 0 행은
     * [AttemptOutcome.Empty] 가 말하고, 걷기 부재로 말하면 옛 형식 줄과 같은 값이 된다.
     */
    protected fun allAxesSettled(
        sample: SampleList,
        walk: Instant = observedAt,
        outcome: AttemptOutcome = AttemptOutcome.Succeeded,
    ): Map<String, Map<SourceEndpoint, AxisConclusion>> =
        sample.keys.associate { key ->
            key.value to
                expectedAxesFor(BusinessDivision.SERVICE.name).associateWith { AxisConclusion(outcome, walk) }
        }

    /** 개찰결과 목록 관측 — 참가자 수가 이 축에서만 온다(결말 줄이 없는 축이다). */
    protected fun openingListObservation(
        number: String,
        participants: String,
        at: Instant,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                RawKey("bidNtceNo") to number,
                RawKey("bidNtceOrd") to "000",
                RawKey("prtcptCnum") to participants,
            ),
            SourceEndpoint.OPENING_RESULT_LIST,
            at,
        )

    /** canonical 공고를 **출하 경로**(repository)로 세운다 — test 전용 SQL 사본을 두지 않는다. */
    protected fun listObservation(number: String): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            observedAt,
        )

    /** 합성 공고번호 — 같은 형태에 순번만 다르다(키 해시가 접히지 않게). */
    protected fun syntheticNumbers(range: IntRange): List<String> = range.map { "20260617%03d-00".format(it) }

    /** [unfinished] 의 개찰완료만 미완으로 — 그 공고는 행이 아니라 `incompleteAxis` 다. */
    protected fun conclusionsWithUnfinished(
        sample: SampleList,
        unfinished: List<String>,
    ): Map<String, Map<SourceEndpoint, AxisConclusion>> {
        val hashes = unfinished.map { NoticeKeyHash.of(it, "000").value }.toSet()
        val unusable = SourceEndpoint.OPENING_COMPLETE to AxisConclusion(shortWalk, observedAt)
        return allAxesSettled(sample).mapValues { (key, axes) ->
            if (key in hashes) axes + unusable else axes
        }
    }

    protected fun persistCanonical(
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
