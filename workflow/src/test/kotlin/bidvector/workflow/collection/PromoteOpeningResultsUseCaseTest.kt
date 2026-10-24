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
    fun `원장 줄이 하나도 없는 상세 축의 행은 승격하지 않는다`() {
        // verifier r2 R2-1 회귀(VP3 모양). 원장에 이 공고의 줄이 **하나도** 없다 — 원장 이전에 적재된
        // 원문이거나 다른 실행 상태 디렉터리의 걷기다. 앞 판은 「열린 라운드가 있는 축만」 비웠으므로
        // 이 갈래가 행 시각으로 물러서서 승격됐다. 상세 축은 결말이 없으면 쓰지 않는다.
        val id = noticeId("PROMOTE-018")
        val repository = RecordingResultRepository()
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    listOf(groupOf(id, RowSpec(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))),
                ),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository, FakeAttemptLedger()).promote())

        report.promoted shouldBe 0
        report.noUsableAxis shouldBe 1
        repository.persistCalls.size shouldBe 0
    }

    @Test
    fun `g1c03 이 끊긴 라운드를 닫아도 그 상세 축은 승격되지 않는다`() {
        // 닫는 줄은 AXIS 줄이라 원장 판독이 그 축의 **결말을 낸다**(`axisConclusions` 는 갈래만 보고
        // 어휘를 거르지 않는다). 그 결말이 `Failed("INTERRUPTED")` 라 `usesRows` 가 false 이고,
        // 승격은 **실패 결말 갈래**로 그 행을 쓰지 않는다 — r1 부터 있던 분기다.
        //
        // 그래서 이 사례는 축 분기(D-7C5P-11)의 회귀가 **아니다** — 축 분기를 지워도 이 결말은 그대로
        // 실패 갈래에 걸린다. 그 분기의 회귀는 결말이 **없는** 두 사례(`PROMOTE-016` 열린 라운드 줄만 ·
        // `PROMOTE-018` 원장 줄 0)가 진다.
        //
        // 이 사례가 재는 것은 **합성**이다 — 닫히기 **전**(AXIS 줄 없음)은 축 갈래가, 닫힌 **뒤**는
        // 실패 갈래가 받아, 승격이 g1c03 의 어휘를 따로 읽지 않아도 두 레인이 겹치지 않는다.
        val id = noticeId("PROMOTE-019")
        val repository = RecordingResultRepository()
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    listOf(groupOf(id, RowSpec(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))),
                ),
            )
        val attempts =
            FakeAttemptLedger(
                openRoundLines(id, SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A) +
                    axisLine(id, SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, INTERRUPTED_ROUND),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository, attempts).promote())

        report.promoted shouldBe 0
        report.noUsableAxis shouldBe 1
        repository.persistCalls.size shouldBe 0
    }

    @Test
    fun `결말 없이 끊긴 상세 축의 행은 승격하지 않는다`() {
        // verifier r1 F1 회귀. 원장에 의도·호출 줄만 있고 AXIS 결말이 없다 — 반쪽 원문이다. 지금은
        // 축 기준 규칙(D-7C5P-11)이 같은 판정을 내므로 열린 라운드 유무는 결과를 바꾸지 않는다.
        val id = noticeId("PROMOTE-016")
        val repository = RecordingResultRepository()
        val truncated =
            RowSpec(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100", "compnoRsrvtnPrceSno" to "1"))
        val source = ScriptedRawAxisSource(OpeningRawAxisRead(listOf(groupOf(id, truncated))))
        val attempts = FakeAttemptLedger(openRoundLines(id, SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A))

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository, attempts).promote())

        report.promoted shouldBe 0
        report.noUsableAxis shouldBe 1
        repository.persistCalls.size shouldBe 0
    }

    @Test
    fun `원장 걷기의 행이 창 밖이고 끊긴 재걷기 행만 남아도 실행이 던지지 않는다`() {
        // verifier r1 F2 회귀. 결말은 WALK_A 를 가리키는데 읽기 집합에는 WALK_B 행만 있다.
        val id = noticeId("PROMOTE-017")
        val repository = RecordingResultRepository()
        val source =
            ScriptedRawAxisSource(
                OpeningRawAxisRead(
                    listOf(groupOf(id, RowSpec(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B, mapOf("plnprc" to "100")))),
                ),
            )
        val attempts =
            FakeAttemptLedger(
                listOf(axisLine(id, SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, AttemptOutcome.Succeeded)) +
                    openRoundLines(id, SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B),
            )

        val report = reportOf(useCaseOf(source, sampleListOf(id), repository, attempts).promote())

        report.noUsableAxis shouldBe 1
        repository.persistCalls.size shouldBe 0
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
