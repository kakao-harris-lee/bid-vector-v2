package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.wiring.RecordedExitCodes
import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptLedger
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningRawAxisRead
import bidvector.procurement.OpeningRawAxisSource
import bidvector.procurement.OpeningResult
import bidvector.procurement.OpeningResultRepository
import bidvector.procurement.PersistOutcome
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.PromoteOpeningResultsUseCase
import bidvector.workflow.collection.SampleConfirmation
import bidvector.workflow.collection.SampleList
import bidvector.workflow.collection.SampleListLedger
import bidvector.workflow.collection.SampleOutcome
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.collection.SampleStratum
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionPolicyData
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 10, 23))

private val POLICY: KonepsCollectionPolicyData =
    (KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE.date) as Resolution.Resolved).value

private val RETENTION: RetentionPolicyData =
    (RETENTION_POLICY.resolve(REFERENCE_DATE.date) as Resolution.Resolved).value

/** 읽기 포트 대역 — **불렸는지**가 이 test 의 증거다(잠금을 못 잡으면 0 이어야 한다). */
private class CountingRawAxisSource : OpeningRawAxisSource {
    var reads: Int = 0
        private set

    override fun readOpeningAxes(collectedSince: Instant): OpeningRawAxisRead {
        reads++
        return OpeningRawAxisRead(emptyList())
    }
}

private class CountingResultRepository : OpeningResultRepository {
    var persists: Int = 0
        private set

    override fun persist(
        result: OpeningResult,
        observationKey: ObservationKey,
    ): PersistOutcome {
        persists++
        return PersistOutcome.Inserted
    }

    override fun find(id: NoticeId): OpeningResult? = null
}

private class StubSampleLedger(
    private val sample: SampleList?,
) : SampleListLedger {
    override fun confirmed(): SampleList? = sample

    override fun confirm(confirmation: SampleConfirmation): SampleList = error("승격은 표본을 확정하지 않는다")
}

private class StubAttemptLedger : AttemptLedger {
    override fun append(attempt: CollectionAttempt) = error("승격은 원장에 쓰지 않는다")

    override fun read(): AttemptHistory = AttemptHistory(emptyList())
}

private fun confirmedSample(): SampleList {
    val hash = NoticeKeyHash.of("LOCK-TEST-001", "000")
    val stratum = SampleStratum(BusinessDivision.CONSTRUCTION, "2026-W42")
    return SampleList(
        strataByKey = mapOf(hash to stratum),
        strata = emptyMap(),
        requested = 1,
        scope =
            SampleScope(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 7),
                setOf(BusinessDivision.CONSTRUCTION),
            ),
    )
}

/**
 * M7/7C⑤-0 — **러너의 거동**(verifier r1 F3 · code-review r1 HIGH). 앞 판의 러너 test 는 줄 함수만
 * 재고 `run()` 을 한 번도 부르지 않아, 잠금 래퍼를 벗기는 변이가 `:app:test` 전체에서 초록이었다.
 *
 * 잠그는 것 셋: ① 다른 **프로세스**가 자물쇠를 들고 있으면 **거부**한다(읽기 0 · 쓰기 0) ② 확정 표본이
 * 없으면 건너뜀 줄 + 미완 종료 ③ 정상 실행은 마침 줄 + 완료 종료.
 *
 * 보유자는 **자식 JVM** 이다([RunStateLockHolder]) — 같은 JVM 의 겹친 잠금은 두 프로세스를 재지 못한다.
 * 종료 자리는 대역([RecordedExitCodes])이다 — production 자리는 `exitProcess` 라 test JVM 이 죽는다.
 */
class OpeningPromotionRunnerLockTest {
    private class Harness(
        val runner: OpeningPromotionRunner,
        val source: CountingRawAxisSource,
        val repository: CountingResultRepository,
        val exitCodes: RecordedExitCodes,
        val lines: MutableList<String>,
        val runState: RunStateDirectory,
    )

    private fun harness(
        directory: Path,
        sample: SampleList?,
    ): Harness {
        val source = CountingRawAxisSource()
        val repository = CountingResultRepository()
        val exitCodes = RecordedExitCodes()
        val lines = mutableListOf<String>()
        val runState = RunStateDirectory(directory)
        val useCase =
            PromoteOpeningResultsUseCase(
                rawAxes = source,
                results = repository,
                attempts = StubAttemptLedger(),
                sampleList = StubSampleLedger(sample),
                policyFor = { POLICY },
                retention = RETENTION,
                referenceDate = REFERENCE_DATE,
                clock = Clock { Instant.parse("2026-10-23T00:00:00Z") },
            )
        val runner = OpeningPromotionRunner(useCase, runState, CollectionLog { lines += it }, exitCodes)
        return Harness(runner, source, repository, exitCodes, lines, runState)
    }

    private fun run(harness: Harness) {
        try {
            harness.runner.run(DefaultApplicationArguments())
        } finally {
            harness.runState.close()
        }
    }

    @Test
    fun `다른 프로세스가 자물쇠를 들고 있으면 승격을 거부한다 — 읽기도 쓰기도 0`() {
        val directory = Files.createTempDirectory("7c5p-promote-locked")
        RunStateLockHolder.hold(directory).use {
            val harness = harness(directory, confirmedSample())

            run(harness)

            harness.exitCodes.recorded() shouldContainExactly listOf(CollectionExitCode.ALREADY_RUNNING.value)
            harness.source.reads shouldBe 0
            harness.repository.persists shouldBe 0
        }
    }

    @Test
    fun `확정 표본이 없으면 건너뜀 줄과 미완 종료다`() {
        val harness = harness(Files.createTempDirectory("7c5p-promote-no-sample"), sample = null)

        run(harness)

        harness.exitCodes.recorded() shouldContainExactly listOf(CollectionExitCode.INCOMPLETE.value)
        harness.lines shouldContainExactly listOf(PROMOTION_SKIPPED_LINE)
        harness.repository.persists shouldBe 0
    }

    @Test
    fun `정상 실행은 마침 줄과 완료 종료다`() {
        val harness = harness(Files.createTempDirectory("7c5p-promote-ok"), confirmedSample())

        run(harness)

        harness.exitCodes.recorded() shouldContainExactly listOf(CollectionExitCode.COMPLETE.value)
        harness.lines.single() shouldContain "opening-promote finished groupsRead=0 promoted=0"
        harness.source.reads shouldBe 1
    }

    @Test
    fun `거부한 실행은 마침 줄도 건너뜀 줄도 남기지 않는다`() {
        val directory = Files.createTempDirectory("7c5p-promote-locked-lines")
        RunStateLockHolder.hold(directory).use {
            val harness = harness(directory, confirmedSample())

            run(harness)

            harness.lines.filter { it.startsWith("opening-promote finished") }.shouldBeEmpty()
            harness.lines.none { it == PROMOTION_SKIPPED_LINE } shouldBe true
        }
    }
}
