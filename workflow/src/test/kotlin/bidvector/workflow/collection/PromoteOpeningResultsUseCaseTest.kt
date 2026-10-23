package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionReferenceDate
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

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 10, 23))

private val NOW: Instant = Instant.parse("2026-10-23T00:00:00Z")

private val WALK_A: Instant = Instant.parse("2026-10-20T01:00:00Z")
private val WALK_B: Instant = Instant.parse("2026-10-21T02:00:00Z")

private val POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE.date)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("정책이 해석되지 않는다: ${resolution.reason}")
    }

private val RETENTION: RetentionPolicyData =
    when (val resolution = RETENTION_POLICY.resolve(REFERENCE_DATE.date)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("보존 정책이 해석되지 않는다: ${resolution.reason}")
    }

private fun noticeId(number: String): NoticeId = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

/** 원문 행 하나의 명세 — 묶음을 지을 때만 쓰고, 지어진 묶음의 행은 다시 열 수 없다(설계). */
private class RowSpec(
    val endpoint: SourceEndpoint,
    val observedAt: Instant,
    val fields: Map<String, String>,
)

private fun groupOf(
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

private fun participantRow(
    observedAt: Instant,
    count: String,
): RowSpec = RowSpec(SourceEndpoint.OPENING_RESULT_LIST, observedAt, mapOf("prtcptCnum" to count))

/** 읽기 포트 대역 — 값과 호출 기록뿐이다(mock framework 없음). */
private class ScriptedRawAxisSource(
    private val read: OpeningRawAxisRead,
) : OpeningRawAxisSource {
    val collectedSinceCalls = mutableListOf<Instant>()

    override fun readOpeningAxes(collectedSince: Instant): OpeningRawAxisRead {
        collectedSinceCalls += collectedSince
        return read
    }
}

/** canonical 저장 대역 — 「더 늦은 관측만 쓴다」는 실제 upsert 의 조건을 흉내 내지 않고 받은 대로 담는다. */
private class RecordingResultRepository(
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

private class FixedClock(
    private val now: Instant,
) : Clock {
    override fun now(): Instant = now
}

private fun sampleListOf(vararg ids: NoticeId): SampleList {
    val stratum = SampleStratum(BusinessDivision.CONSTRUCTION, "2026-W42")
    return SampleList(
        strataByKey = ids.associate { NoticeKeyHash.of(it.number.value, it.round.value) to stratum },
        strata = emptyMap(),
        requested = ids.size,
        scope = SampleScope(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), setOf(BusinessDivision.CONSTRUCTION)),
    )
}

private class StubSampleListLedger(
    private val sample: SampleList?,
) : SampleListLedger {
    override fun confirmed(): SampleList? = sample

    override fun confirm(confirmation: SampleConfirmation): SampleList = error("승격은 표본을 확정하지 않는다")
}

private fun axisLine(
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

/**
 * M7/7C⑤-0 — 승격 use case. 수집 경로를 건드리지 않고(외부 호출 0 · 원문 쓰기 0 · 원장 쓰기 0),
 * 범위는 확정 표본이며(D-7C5P-6) 같은 입력 두 번에 행이 움직이지 않는다(D-7C5P-4).
 */
class PromoteOpeningResultsUseCaseTest {
    private fun useCaseOf(
        source: ScriptedRawAxisSource,
        sample: SampleList?,
        repository: OpeningResultRepository,
        attempts: FakeAttemptLedger = FakeAttemptLedger(),
    ): PromoteOpeningResultsUseCase =
        PromoteOpeningResultsUseCase(
            rawAxes = source,
            results = repository,
            attempts = attempts,
            sampleList = StubSampleListLedger(sample),
            policyFor = { POLICY },
            retention = RETENTION,
            referenceDate = REFERENCE_DATE,
            clock = FixedClock(NOW),
        )

    private fun reportOf(run: OpeningPromotionRun): OpeningPromotionReport =
        run.shouldBeInstanceOf<OpeningPromotionRun.Completed>().report

    @Test
    fun `표본 공고의 원문을 승격한다`() {
        val id = noticeId("PROMOTE-001")
        val repository = RecordingResultRepository()
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository).promote())

        report.promoted shouldBe 1
        report.groupsRead shouldBe 1
        repository.stored.getValue(id).participantCount shouldBe 5
    }

    @Test
    fun `같은 입력 두 번이면 둘째 실행은 쓰지 않는다`() {
        val id = noticeId("PROMOTE-002")
        val repository = RecordingResultRepository()
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))
        val useCase = useCaseOf(source, sampleListOf(id), repository)

        useCase.promote()
        val second = reportOf(useCase.promote())

        second.promoted shouldBe 0
        second.skippedUnchanged shouldBe 1
        repository.persistCalls.size shouldBe 1
    }

    @Test
    fun `새 걷기가 오면 다시 쓴다`() {
        val id = noticeId("PROMOTE-003")
        val repository = RecordingResultRepository()
        val first = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))
        val second = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_B, "8")))))

        useCaseOf(first, sampleListOf(id), repository).promote()
        val report = reportOf(useCaseOf(second, sampleListOf(id), repository).promote())

        report.promoted shouldBe 1
        repository.stored.getValue(id).participantCount shouldBe 8
    }

    @Test
    fun `표본 밖 공고는 승격하지 않는다`() {
        val sampled = noticeId("PROMOTE-004")
        val outside = noticeId("PROMOTE-005")
        val repository = RecordingResultRepository()
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    listOf(
                        groupOf(sampled, participantRow(WALK_A, "1")),
                        groupOf(outside, participantRow(WALK_A, "2")),
                    ),
                ),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(sampled), repository).promote())

        report.promoted shouldBe 1
        report.outsideSample shouldBe 1
        repository.persistCalls shouldBe listOf(sampled)
    }

    @Test
    fun `확정 표본이 없으면 읽지도 쓰지도 않는다`() {
        val id = noticeId("PROMOTE-006")
        val repository = RecordingResultRepository()
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))

        useCaseOf(source, sample = null, repository = repository).promote() shouldBe
            OpeningPromotionRun.SampleNotConfirmed

        repository.persistCalls.size shouldBe 0
        source.collectedSinceCalls.size shouldBe 0
    }

    @Test
    fun `원장이 축을 실패로 닫으면 그 공고는 승격되지 않는다`() {
        val id = noticeId("PROMOTE-007")
        val repository = RecordingResultRepository()
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))
        val attempts =
            FakeAttemptLedger(
                listOf(
                    axisLine(
                        id,
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        AttemptOutcome.Failed("SHORT_WALK"),
                    ),
                ),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository, attempts).promote())

        report.noUsableAxis shouldBe 1
        report.promoted shouldBe 0
        repository.persistCalls.size shouldBe 0
    }

    @Test
    fun `원장이 가리킨 걷기의 행만 승격한다`() {
        val id = noticeId("PROMOTE-008")
        val repository = RecordingResultRepository()
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    listOf(groupOf(id, participantRow(WALK_A, "3"), participantRow(WALK_B, "9"))),
                ),
            )
        val attempts =
            FakeAttemptLedger(
                listOf(axisLine(id, SourceEndpoint.OPENING_RESULT_LIST, WALK_A, AttemptOutcome.Succeeded)),
            )

        reportOf(useCaseOf(source, sampleListOf(id), repository, attempts).promote())

        // 더 늦은 행이 원문에 있어도 원장이 가리킨 걷기의 값이 실린다.
        repository.stored.getValue(id).participantCount shouldBe 3
        repository.stored.getValue(id).observedAt shouldBe WALK_A
    }

    @Test
    fun `저장 가드의 거부를 조용히 삼키지 않는다`() {
        val id = noticeId("PROMOTE-009")
        val repository = RecordingResultRepository(rejectFor = id)
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository).promote())

        report.rejected shouldBe 1
        report.promoted shouldBe 0
    }

    @Test
    fun `행 단위 제외 계수가 보고에 실린다`() {
        val id = noticeId("PROMOTE-010")
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    groups = listOf(groupOf(id, participantRow(WALK_A, "5"))),
                    unkeyedRows = 3,
                    maskedRows = 7,
                    expiredRows = 11,
                ),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(id), RecordingResultRepository()).promote())

        report.unkeyedRows shouldBe 3
        report.maskedRows shouldBe 7
        report.expiredRows shouldBe 11
    }

    @Test
    fun `읽기 하한은 보존 창이 정한다`() {
        val id = noticeId("PROMOTE-011")
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))

        useCaseOf(source, sampleListOf(id), RecordingResultRepository()).promote()

        // 창의 수를 test 가 다시 적지 않는다 — 보존 정책이 그 값의 정본이다.
        source.collectedSinceCalls shouldBe listOf(NOW.minus(RETENTION.retentionDays.toLong(), ChronoUnit.DAYS))
    }

    @Test
    fun `승격은 시도 원장에 쓰지 않는다`() {
        val id = noticeId("PROMOTE-012")
        val attempts = FakeAttemptLedger()
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, participantRow(WALK_A, "5")))))

        useCaseOf(source, sampleListOf(id), RecordingResultRepository(), attempts).promote()

        attempts.appended.size shouldBe 0
    }

    @Test
    fun `사유별 계수의 합은 읽은 묶음 수와 같다`() {
        val sampled = noticeId("PROMOTE-013")
        val outside = noticeId("PROMOTE-014")
        val failed = noticeId("PROMOTE-015")
        val repository = RecordingResultRepository()
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    listOf(
                        groupOf(sampled, participantRow(WALK_A, "1")),
                        groupOf(outside, participantRow(WALK_A, "2")),
                        groupOf(failed, participantRow(WALK_A, "3")),
                    ),
                ),
            )
        val attempts =
            FakeAttemptLedger(
                listOf(axisLine(failed, SourceEndpoint.OPENING_RESULT_LIST, WALK_A, AttemptOutcome.Failed("X"))),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(sampled, failed), repository, attempts).promote())

        report.groupsRead shouldBe 3
        report.promoted shouldBe 1
        report.outsideSample shouldBe 1
        report.noUsableAxis shouldBe 1
    }
}
