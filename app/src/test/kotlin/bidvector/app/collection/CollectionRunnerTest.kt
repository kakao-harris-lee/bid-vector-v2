package bidvector.app.collection

import bidvector.procurement.CollectionAccounting
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
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate

/**
 * D-6F8-3·4(M6/6F-8) — 러너는 use case 를 한 번 돌리고 건수·조회일·업종·원인 코드만 로그로 남긴 뒤 프로세스를
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
                ) = SourceBatch(items, accounting(items.size, cause), next = null)
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
    ) = CollectionRunner(useCase, range(), sources, lines.log, exits.termination)

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
