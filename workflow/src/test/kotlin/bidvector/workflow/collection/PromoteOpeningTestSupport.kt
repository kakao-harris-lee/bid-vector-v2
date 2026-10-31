package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.INTERRUPTED_ROUND
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningAxisGroup
import bidvector.procurement.OpeningAxisObservation
import bidvector.procurement.OpeningRawAxisRead
import bidvector.procurement.OpeningRawAxisSource
import bidvector.procurement.OpeningResult
import bidvector.procurement.OpeningResultRepository
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RejectionReason
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionPolicyData
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * M7/7C⑤-0 승격 use case test 의 **대역과 값**. 이 패키지의 수집 갈래가 이미 쓰는 모양이다
 * (`OpeningCollectionTestSupport`) — 승격은 포트가 달라 자기 대역을 따로 둔다.
 *
 * 자리를 가른 기준은 **책임**이다: 이 파일은 「무엇을 넣고 무엇을 받아 적는가」(대역·고정 값·
 * 원장 줄 짓기)이고, test 파일은 「그래서 무엇이 참인가」다.
 */
internal val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 10, 23))

internal val NOW: Instant = Instant.parse("2026-10-23T00:00:00Z")

internal val WALK_A: Instant = Instant.parse("2026-10-20T01:00:00Z")
internal val WALK_B: Instant = Instant.parse("2026-10-21T02:00:00Z")

internal val POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE.date)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("정책이 해석되지 않는다: ${resolution.reason}")
    }

internal val RETENTION: RetentionPolicyData =
    when (val resolution = RETENTION_POLICY.resolve(REFERENCE_DATE.date)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("보존 정책이 해석되지 않는다: ${resolution.reason}")
    }

internal fun noticeId(number: String): NoticeId = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

/** 원문 행 하나의 명세 — 묶음을 지을 때만 쓰고, 지어진 묶음의 행은 다시 열 수 없다(설계). */
internal class RowSpec(
    val endpoint: SourceEndpoint,
    val observedAt: Instant,
    val fields: Map<String, String>,
)

internal fun groupOf(
    id: NoticeId,
    vararg rows: RowSpec,
): OpeningAxisGroup =
    OpeningAxisGroup(
        id,
        rows.map { row ->
            OpeningAxisObservation(
                RawNoticeObservation.of(
                    fields =
                        row.fields.mapKeys { (name, _) -> RawKey(name) } +
                            mapOf(
                                NOTICE_NUMBER_RAW_KEY to id.number.value,
                                NOTICE_ROUND_RAW_KEY to id.round.value,
                            ),
                    sourceEndpoint = row.endpoint,
                    observedAt = row.observedAt,
                    sourceDivision = null,
                ),
                ObservationKey("key-${id.number.value}-${row.endpoint.name}-${row.observedAt}"),
            )
        },
    )

internal fun participantRow(
    observedAt: Instant,
    count: String,
): RowSpec = RowSpec(SourceEndpoint.OPENING_RESULT_LIST, observedAt, mapOf("prtcptCnum" to count))

/** 읽기 포트 대역 — 값과 호출 기록뿐이다(mock framework 없음). */
internal class ScriptedRawAxisSource(
    private val read: OpeningRawAxisRead,
) : OpeningRawAxisSource {
    val collectedSinceCalls = mutableListOf<Instant>()

    override fun readOpeningAxes(collectedSince: Instant): OpeningRawAxisRead {
        collectedSinceCalls += collectedSince
        return read
    }
}

/** canonical 저장 대역 — 「더 늦은 관측만 쓴다」는 실제 upsert 의 조건을 흉내 내지 않고 받은 대로 담는다. */
internal class RecordingResultRepository(
    private val rejectFor: NoticeId? = null,
) : OpeningResultRepository {
    val stored = mutableMapOf<NoticeId, OpeningResult>()
    val persistCalls = mutableListOf<NoticeId>()

    override fun persist(
        result: OpeningResult,
        observationKey: ObservationKey,
    ): PersistOutcome {
        persistCalls += result.noticeId
        if (result.noticeId == rejectFor) {
            return PersistOutcome.Rejected(RejectionReason.EMPTY_OVERWRITE_OF_EXISTING)
        }
        val previous = stored.put(result.noticeId, result)
        return if (previous == null) PersistOutcome.Inserted else PersistOutcome.Updated(2L)
    }

    override fun find(id: NoticeId): OpeningResult? = stored[id]
}

internal class FixedClock(
    private val now: Instant,
) : Clock {
    override fun now(): Instant = now
}

internal fun sampleListOf(vararg ids: NoticeId): SampleList {
    val stratum = SampleStratum(BusinessDivision.CONSTRUCTION, "2026-W42")
    return SampleList(
        strataByKey = ids.associate { NoticeKeyHash.of(it.number.value, it.round.value) to stratum },
        strata = emptyMap(),
        requested = ids.size,
        scope = SampleScope(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), setOf(BusinessDivision.CONSTRUCTION)),
    )
}

internal class StubSampleListLedger(
    private val sample: SampleList?,
) : SampleListLedger {
    override fun confirmed(): SampleList? = sample

    override fun confirm(confirmation: SampleConfirmation): SampleList = error("승격은 표본을 확정하지 않는다")
}

internal fun axisLine(
    id: NoticeId,
    endpoint: SourceEndpoint,
    walk: Instant,
    outcome: AttemptOutcome,
): CollectionAttempt =
    CollectionAttempt(
        noticeKey = NoticeKeyHash.of(id.number.value, id.round.value).value,
        axis = endpoint,
        outcome = outcome,
        at = walk,
        kind = AttemptKind.AXIS,
        walk = walk,
    )

/** 결말을 적지 못한 라운드의 줄들 — 관문이 호출 **전에** 의도를, 호출 뒤에 결말을 적는다(D-6G-61). */
internal fun openRoundLines(
    id: NoticeId,
    endpoint: SourceEndpoint,
    at: Instant,
): List<CollectionAttempt> {
    val key = NoticeKeyHash.of(id.number.value, id.round.value).value
    return listOf(
        CollectionAttempt(key, endpoint, AttemptOutcome.Succeeded, at, AttemptKind.PENDING, null),
        CollectionAttempt(key, endpoint, AttemptOutcome.Succeeded, at, AttemptKind.HTTP, null),
    )
}
