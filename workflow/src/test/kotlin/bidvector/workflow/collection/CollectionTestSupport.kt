package bidvector.workflow.collection

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeRepository
import bidvector.procurement.NoticeSourcePort
import bidvector.procurement.ObservationKey
import bidvector.procurement.PageCursor
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.RowDiscriminator
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.sharedkernel.Resolution
import bidvector.workflow.strategy.Clock
import java.time.Instant
import java.time.LocalDate

/** 수집 use case test 가 함께 쓰는 fake port·조립 헬퍼 — 전부 값·호출 기록이고 mock framework 는 없다. */
internal val COLLECTION_NOW: Instant = Instant.parse("2026-09-24T03:00:00Z")

internal val COLLECTION_POLICY: KonepsCollectionPolicyData =
    (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value

internal fun sourceName(raw: String): CollectionSourceName =
    requireNotNull(CollectionSourceName.of(raw)) { "test 표본이 유효한 업종 이름이 아니다: $raw" }

/** 공고 목록 항목 하나 — 계약이 등재한 키(`bidNtceNo`·`bidNtceOrd`·`bidNtceNm`)로 원문을 만든다. */
internal fun observation(
    number: String,
    title: String? = "공고 $number",
    extra: Map<String, String> = emptyMap(),
): RawNoticeObservation {
    val fields =
        mapOf("bidNtceNo" to number, "bidNtceOrd" to "000") +
            (title?.let { mapOf("bidNtceNm" to it) } ?: emptyMap()) +
            extra
    return RawNoticeObservation.of(fields.mapKeys { RawKey(it.key) }, SourceEndpoint.NOTICE_LIST, COLLECTION_NOW)
}

/** 소스 쪽 회계 — 항등식(`received = normalized + duplicate + dropped`)을 호출부가 맞춰 넘긴다. */
internal fun sourceAccounting(
    normalized: Int,
    duplicate: Int = 0,
    dropped: Int = 0,
    dropReasons: Map<CollectionDropReason, Int> = emptyMap(),
    pagesFetched: Int = 1,
    truncationCause: TruncationCause? = null,
    quotaExceeded: Int = 0,
    unknownFields: Int = 0,
): CollectionAccounting =
    CollectionAccounting(
        received = normalized + duplicate + dropped,
        normalized = normalized,
        duplicate = duplicate,
        dropped = dropped,
        dropReasons = dropReasons,
        sourceTotal = normalized + duplicate + dropped,
        pagesFetched = pagesFetched,
        truncated = truncationCause != null,
        unknownFields = unknownFields,
        truncationCause = truncationCause,
        quotaExceeded = quotaExceeded,
    )

internal fun batchOf(
    items: List<RawNoticeObservation>,
    accounting: CollectionAccounting = sourceAccounting(normalized = items.size),
    next: PageCursor? = null,
): SourceBatch<RawNoticeObservation> = SourceBatch(items, accounting, next)

/** 호출 기록을 남기는 소스 — `script` 가 (조회일, 커서)마다 배치를 낸다. */
internal class ScriptedSource(
    private val script: (LocalDate, PageCursor?) -> SourceBatch<RawNoticeObservation>,
) : NoticeSourcePort {
    val calls = mutableListOf<Pair<LocalDate, PageCursor?>>()

    override fun fetchNotices(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        calls += referenceDate.date to cursor
        return script(referenceDate.date, cursor)
    }
}

internal class RecordingRawStore : RawObservationStore {
    val appended = mutableListOf<RawNoticeObservation>()

    override fun append(
        observation: RawNoticeObservation,
        rowDiscriminator: RowDiscriminator?,
    ): ObservationKey {
        appended += observation
        return ObservationKey("raw-${appended.size}")
    }
}

/** `outcomeFor` 가 항목마다 저장 결과를 낸다(기본 `Inserted`) — `throwOn` 이 참이면 인프라 실패를 흉내낸다. */
internal class RecordingNoticeRepository(
    private val outcomeFor: (NoticeCollected) -> PersistOutcome = { PersistOutcome.Inserted },
    private val throwOn: (NoticeCollected) -> Boolean = { false },
) : NoticeRepository {
    val persisted = mutableListOf<Pair<NoticeCollected, ObservationKey>>()

    override fun persist(
        command: NoticeCollected,
        observationKey: ObservationKey,
    ): PersistOutcome {
        check(!throwOn(command)) { "저장소 장애 표본" }
        persisted += command to observationKey
        return outcomeFor(command)
    }

    override fun find(id: NoticeId) = error("수집 use case 는 조회하지 않는다")
}

internal class RecordingRunStore : CollectionRunStore {
    val recorded = mutableListOf<Pair<CollectionAccounting, CollectionRunMeta>>()

    override fun record(
        accounting: CollectionAccounting,
        meta: CollectionRunMeta,
    ) {
        recorded += accounting to meta
    }
}

internal class CollectionFixture(
    val raw: RecordingRawStore = RecordingRawStore(),
    val notices: RecordingNoticeRepository = RecordingNoticeRepository(),
    val runs: RecordingRunStore = RecordingRunStore(),
) {
    val useCase =
        CollectNoticesUseCase(
            rawObservations = raw,
            notices = notices,
            runs = runs,
            policyFor = { COLLECTION_POLICY },
            clock = Clock { COLLECTION_NOW },
        )
}

internal fun rangeOf(
    from: String,
    to: String,
): CollectionRange {
    val outcome =
        CollectionRange.of(
            LocalDate.parse(from),
            LocalDate.parse(to),
            LocalDate.parse(to),
            COLLECTION_RANGE_POLICY_DATA,
        )
    return (outcome as CollectionRangeOutcome.Valid).range
}

internal val COLLECTION_RANGE_POLICY_DATA: CollectionRangePolicyData =
    (COLLECTION_RANGE_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value
