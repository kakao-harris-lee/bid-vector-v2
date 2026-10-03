package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.KONEPS_COLLECTION_POLICY
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
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CollectNoticesUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeOutcome
import bidvector.workflow.collection.CollectionSource
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.nio.file.Files
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate

/** 대역 배치의 걷기 이름(D-6G2d-4 ⓓ) — 빈 배치도 걷기는 돌았다. */
private val RUNNER_WALK: Instant = Instant.parse("2026-06-17T02:00:00Z")

/**
 * D-6F8-3·4 — 러너는 use case 를 한 번 돌리고 건수·조회일·업종·원인 코드만 로그로 남긴 뒤 프로세스를
 * 끝낸다. 실패는 원 예외를 잇지 않는 정제된 예외로만 나간다(SQL 상세·요청 URI 가 로그로 새지 않는다).
 */
class CollectionRunnerTest {
    private val day = LocalDate.of(2026, 9, 1)
    private val constructionName = requireNotNull(CollectionSourceName.of("construction"))
    private val secretTitle = "SECRET-TITLE-원문-공고명"
    private val secretKey = "SECRET-SERVICE-KEY-VALUE"

    private class Exits {
        val codes = mutableListOf<Int>()
        val termination = CollectionTermination { codes += it }
    }

    private class Lines {
        val written = mutableListOf<String>()
        val log = CollectionLog { written += it }
    }

    private fun range(): CollectionRange {
        val policy = (COLLECTION_RANGE_POLICY.resolve(day) as Resolution.Resolved).value
        return (CollectionRange.of(day, day, day, policy) as CollectionRangeOutcome.Valid).range
    }

    private fun observation(
        number: String,
        title: String = secretTitle,
    ) = RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000", RawKey("bidNtceNm") to title),
        SourceEndpoint.NOTICE_LIST,
        Instant.EPOCH,
    )

    private fun accounting(
        normalized: Int,
        truncationCause: TruncationCause? = null,
    ) = CollectionAccounting(
        received = normalized,
        normalized = normalized,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = normalized,
        pagesFetched = 1,
        truncated = truncationCause != null,
        unknownFields = 0,
        truncationCause = truncationCause,
    )

    private fun source(
        items: List<RawNoticeObservation>,
        cause: TruncationCause? = null,
    ): CollectionSource =
        CollectionSource(
            constructionName,
            object : NoticeSourcePort {
                override fun fetchNotices(
                    referenceDate: CollectionReferenceDate,
                    cursor: PageCursor?,
                ) = SourceBatch(items, accounting(items.size, cause), next = null, observedAt = RUNNER_WALK)
            },
        )

    private fun useCase(
        rawStore: RawObservationStore = FixedKeyRawStore(),
        notices: NoticeRepository = InsertingNotices(),
    ): CollectNoticesUseCase {
        val policy = (KONEPS_COLLECTION_POLICY.resolve(day) as Resolution.Resolved).value
        return CollectNoticesUseCase(
            rawObservations = rawStore,
            notices = notices,
            runs = NoopRuns(),
            policyFor = { policy },
            clock = Clock { Instant.EPOCH },
        )
    }

    private class NoopRuns : CollectionRunStore {
        override fun record(
            accounting: CollectionAccounting,
            meta: CollectionRunMeta,
        ) = Unit
    }

    private class FixedKeyRawStore : RawObservationStore {
        override fun append(
            observation: RawNoticeObservation,
            rowDiscriminator: RowDiscriminator?,
        ) = ObservationKey("k")
    }

    private class ThrowingRawStore(
        private val failure: SQLException,
    ) : RawObservationStore {
        override fun append(
            observation: RawNoticeObservation,
            rowDiscriminator: RowDiscriminator?,
        ): ObservationKey = throw failure
    }

    private class InsertingNotices : NoticeRepository {
        override fun persist(
            command: NoticeCollected,
            observationKey: ObservationKey,
        ) = PersistOutcome.Inserted

        override fun find(id: NoticeId) = error("사용하지 않는다")
    }

    private fun runner(
        useCase: CollectNoticesUseCase,
        sources: List<CollectionSource>,
        lines: Lines,
        exits: Exits,
    ) = CollectionRunner(useCase, range(), sources, heldRunState(), lines.log, exits.termination)

    /** 러너 단위 test 는 잠금 거동이 아니라 로그·종료 코드를 잰다 — 실제로 잡은 잠금을 준다. */
    private fun heldRunState(): RunStateDirectory = RunStateDirectory(runStateDirectory())

    private fun runStateDirectory() = Files.createTempDirectory("6g-runner-lock")

    /**
     * **D-6G2c-21 ⑦ — 잠금을 못 든 실행은 판독 불가 원장에도 멈추지 않고 물러난다.** 앞 판은 Busy
     * 에서도 누적 해시를 지어 원장 전체를 읽었고, 그 읽기가 던지면(비UTF-8 바이트) 조용히 끝나야 할
     * 실행이 예외로 죽었다 — 운영자는 사유 토큰이 아니라 스택 트레이스를 본다.
     *
     * 든 실행을 먼저 세운 **뒤** 원장을 깨뜨린다: 든 실행의 판독은 이미 끝났고, 둘째 실행이 그
     * 바이트를 만난다.
     */
    @Test
    fun `원장이 판독 불가여도 잠금을 못 든 실행은 사유만 남기고 끝난다`() {
        val directory = runStateDirectory()
        val held = RunStateDirectory(directory)
        held.attempts.append(runnerAttempt())
        Files.write(directory.resolve("attempts.jsonl"), byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x0A))
        val lines = Lines()
        val exits = Exits()
        val sources = listOf(source(listOf(observation("N-1"))))

        try {
            CollectionRunner(useCase(), range(), sources, RunStateDirectory(directory), lines.log, exits.termination)
                .run(DefaultApplicationArguments())
        } finally {
            held.close()
        }

        lines.written shouldContainExactly listOf("collection skipped reason=ALREADY_RUNNING")
        exits.codes shouldContainExactly listOf(CollectionExitCode.ALREADY_RUNNING.value)
    }

    /**
     * **D-6G2c-2 — 자물쇠를 걸 수 없는 자리는 「다른 실행 중」이 아니다.** 둘 다 0 호출로 멈추지만
     * 처방이 다르다: 기다리면 풀리는 쪽과 영영 풀리지 않는 쪽이다. 사유 토큰과 종료 코드가 갈린다.
     */
    @Test
    fun `자물쇠를 걸 수 없으면 다른 사유와 다른 종료 코드로 끝난다`() {
        val directory = runStateDirectory()
        Files.createDirectory(directory.resolve("run.lock"))
        val lines = Lines()
        val exits = Exits()

        CollectionRunner(
            useCase(),
            range(),
            listOf(source(listOf(observation("N-1")))),
            RunStateDirectory(directory),
            lines.log,
            exits.termination,
        ).run(DefaultApplicationArguments())

        lines.written shouldContainExactly listOf("collection skipped reason=UNLOCKABLE")
        exits.codes shouldContainExactly listOf(CollectionExitCode.UNLOCKABLE.value)
        exits.codes shouldNotBe listOf(CollectionExitCode.ALREADY_RUNNING.value)
    }

    private fun runnerAttempt() =
        CollectionAttempt(
            noticeKey = null,
            axis = SourceEndpoint.NOTICE_LIST,
            outcome = AttemptOutcome.Succeeded,
            at = RUNNER_WALK,
            kind = AttemptKind.PENDING,
            walk = null,
        )

    @Test
    fun `끝까지 읽히면 시작·슬롯·종료 줄을 남기고 종료 코드 0 으로 끝낸다`() {
        val lines = Lines()
        val exits = Exits()

        runner(useCase(), listOf(source(listOf(observation("N-1"), observation("N-2")))), lines, exits)
            .run(DefaultApplicationArguments())

        lines.written shouldContainExactly
            listOf(
                "collection start from=2026-09-01 to=2026-09-01 sources=construction",
                "collection slot date=2026-09-01 source=construction received=2 normalized=2 duplicate=0 " +
                    "dropped=0 dropReasons={} sourceTotal=2 pages=1 truncated=none unknownFields=0 " +
                    "quotaExceeded=0 backoffSkipped=0 inserted=2 updated=0 unchanged=0 rejected=0",
                "collection finished slots=1 truncatedSlots=0 halted=false exit=0",
            )
        exits.codes shouldContainExactly listOf(0)
    }

    @Test
    fun `절단이 있으면 실행이 끝나도 종료 코드는 2 이다 — 쿼터 소진은 멈춤 줄을 더한다`() {
        val truncated = Lines()
        val truncatedExits = Exits()
        runner(useCase(), listOf(source(emptyList(), TruncationCause.Timeout)), truncated, truncatedExits)
            .run(DefaultApplicationArguments())

        val quota = Lines()
        val quotaExits = Exits()
        runner(useCase(), listOf(source(emptyList(), TruncationCause.QuotaExhausted)), quota, quotaExits)
            .run(DefaultApplicationArguments())

        truncatedExits.codes shouldContainExactly listOf(2)
        truncated.written.last() shouldBe "collection finished slots=1 truncatedSlots=1 halted=false exit=2"
        quotaExits.codes shouldContainExactly listOf(2)
        quota.written.any {
            it.startsWith("collection halted cause=QuotaExhausted date=2026-09-01 source=construction")
        } shouldBe
            true
    }

    @Test
    fun `로그 줄에는 공고명 원문도 서비스 키도 없다 — 건수와 코드만`() {
        val lines = Lines()

        runner(useCase(), listOf(source(listOf(observation("N-1")))), lines, Exits()).run(DefaultApplicationArguments())

        lines.written.joinToString("\n") shouldNotContain secretTitle
        lines.written.joinToString("\n") shouldNotContain secretKey
    }

    @Test
    fun `저장소 예외는 정제된 실패로만 나간다 — 예외 메시지·원인 체인·로그에 SQL 상세가 없다`() {
        val lines = Lines()
        val exits = Exits()
        val leakingFailure =
            ThrowingRawStore(
                SQLException(
                    "ERROR: new row for relation \"notice\" violates check constraint; Detail: $secretTitle $secretKey",
                    "23514",
                ),
            )

        val failure =
            shouldThrow<CollectionRunFailedException> {
                runner(useCase(rawStore = leakingFailure), listOf(source(listOf(observation("N-1")))), lines, exits)
                    .run(DefaultApplicationArguments())
            }

        failure.cause shouldBe null
        failure.causeCode shouldBe "java.sql.SQLException:sqlState=23514"
        listOf(failure.message.orEmpty(), lines.written.joinToString("\n")).forEach {
            it shouldNotContain secretTitle
            it shouldNotContain secretKey
        }
        lines.written.last() shouldBe "collection failed cause=java.sql.SQLException:sqlState=23514"
        exits.codes shouldBe emptyList()
    }

    @Test
    fun `전송 계층 예외의 메시지에 요청 URI 가 실려 있어도 원인 코드는 클래스 이름뿐이다`() {
        val lines = Lines()
        val uriCarrying =
            object : NoticeRepository {
                override fun persist(
                    command: NoticeCollected,
                    observationKey: ObservationKey,
                ): PersistOutcome = throw IllegalStateException("GET https://host/op?serviceKey=$secretKey failed")

                override fun find(id: NoticeId) = error("사용하지 않는다")
            }

        val failure =
            shouldThrow<CollectionRunFailedException> {
                runner(useCase(notices = uriCarrying), listOf(source(listOf(observation("N-1")))), lines, Exits())
                    .run(DefaultApplicationArguments())
            }

        failure.causeCode shouldBe "java.lang.IllegalStateException"
        failure.message.orEmpty() shouldNotContain secretKey
        lines.written.joinToString("\n") shouldNotContain secretKey
    }
}
