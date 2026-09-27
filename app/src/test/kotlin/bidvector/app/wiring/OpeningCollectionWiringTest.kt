package bidvector.app.wiring

import bidvector.app.collection.OpeningCollectionRunner
import bidvector.app.collection.SnapshotExtractionRunner
import bidvector.procurement.CallSpend
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionCallLedgerStore
import bidvector.procurement.NoticeId
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.CallBudgetLedger
import bidvector.workflow.collection.StratifiedSampler
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * M6/6G D-6G-1·11 — 개찰 축 배선의 **켜짐 조건과 기동 실패**. 이 갈래는 `mode=once` 일 때만 올라오고,
 * 켜졌을 때 **승인 값이 하나라도 없으면 기동하지 않는다**: 호출 상한·표본 seed·층당 목표에 기본값을
 * 두지 않았다는 것이 실제로 기동을 막는지를 여기서 잰다(기본값 부재는 선언이 아니라 거동이어야 한다).
 */
private object StubLedgerStore : CollectionCallLedgerStore {
    override fun spentSince(
        since: java.time.Instant,
        dayStart: java.time.Instant,
    ): CallSpend = CallSpend(total = 0, today = 0)
}

private object StubAxisStore : CollectedAxisStore {
    override fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<NoticeId>,
    ): Set<NoticeId> = emptySet()
}

/** 저장소 밖 — 배선은 경로를 검사할 뿐 파일을 만들지 않는다(표본 확정은 수집이 한다). */
private val WIRING_SAMPLE_LIST: Path = Files.createTempDirectory("6g-wiring-sample").resolve("sample-list.tsv")

class OpeningCollectionWiringTest {
    private val fixedNow = Instant.parse("2026-09-24T03:00:00Z")

    private val approved =
        arrayOf(
            "bidvector.opening-collection.mode=once",
            "bidvector.opening-collection.from=2026-09-20",
            "bidvector.opening-collection.to=2026-09-22",
            "bidvector.opening-collection.categories=construction,service",
            "bidvector.opening-collection.sampling-seed=6g-wiring-seed",
            "bidvector.opening-collection.target-per-stratum=2",
            "bidvector.opening-collection.calls-per-day=100",
            "bidvector.opening-collection.calls-total=1000",
            "bidvector.opening-collection.budget-since=2026-01-01T00:00:00Z",
            "bidvector.opening-collection.sample-list-file=$WIRING_SAMPLE_LIST",
            "bidvector.koneps.service-key=WIRING-TEST-KEY",
        )

    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(vararg properties: String): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        // DB 없이 기동 **조건**만 잰다 — 저장소 둘을 대체 빈으로 먼저 세운다(출하에서는 JDBC 구현).
        context.registerBean(CollectionCallLedgerStore::class.java, Supplier { StubLedgerStore })
        context.registerBean(CollectedAxisStore::class.java, Supplier { StubAxisStore })
        context.register(OpeningCollectionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { fixedNow } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        return Booted(context, failure)
    }

    private fun bootWithout(key: String): Booted = boot(*approved.filterNot { it.startsWith("$key=") }.toTypedArray())

    /** 승인 설정에서 한 항목만 바꿔 넣는다 — 하나만 넘기면 나머지가 없어 조건 자체가 서지 않는다. */
    private fun bootWith(property: String): Booted =
        boot(*approved.filterNot { it.startsWith(property.substringBefore('=') + "=") }.toTypedArray(), property)

    private fun <T> use(
        booted: Booted,
        read: (AnnotationConfigApplicationContext) -> T,
    ): T = booted.context.use(read)

    @Test
    fun `mode 가 없으면 이 갈래는 아예 올라오지 않는다 — 서비스 키가 없어도 뜬다`() {
        val booted = boot("bidvector.opening-collection.from=2026-09-20")

        booted.failure shouldBe null
        use(booted) { it.getBeanNamesForType(ApplicationRunner::class.java).toList() }.shouldBeEmpty()
    }

    @Test
    fun `승인 값이 다 있으면 러너·예산·표본기가 선다`() {
        val booted = boot(*approved)

        booted.failure shouldBe null
        use(booted) { context ->
            context.getBean(OpeningCollectionRunner::class.java) shouldNotBe null
            context.getBean(CallBudgetLedger::class.java) shouldNotBe null
            context.getBean(StratifiedSampler::class.java) shouldNotBe null
        }
    }

    @Test
    fun `호출 상한이 없으면 기동하지 않는다 — 기본값을 지어내지 않는다`() {
        bootWithout("bidvector.opening-collection.calls-per-day").failure shouldNotBe null
        bootWithout("bidvector.opening-collection.calls-total").failure shouldNotBe null
    }

    @Test
    fun `표본 정책이 없으면 기동하지 않는다 — seed 와 층당 목표 둘 다`() {
        bootWithout("bidvector.opening-collection.sampling-seed").failure shouldNotBe null
        bootWithout("bidvector.opening-collection.target-per-stratum").failure shouldNotBe null
    }

    @Test
    fun `예산 시작 시점이 없으면 기동하지 않는다 — 어느 시점부터 상한을 세는지를 지어내지 않는다`() {
        bootWithout("bidvector.opening-collection.budget-since").failure shouldNotBe null
    }

    /**
     * 표본 목록 자리를 지어내지 않는다(D-6G-39) — 기본 경로를 두면 다른 수집의 표본을 조용히
     * 이어받는다. 저장소 **안**을 가리키면 기동이 실패한다(D-6G-43): 표본 목록은 커밋되지 않는다.
     */
    @Test
    fun `표본 목록 파일이 없거나 저장소 안이면 기동하지 않는다`() {
        bootWithout("bidvector.opening-collection.sample-list-file").failure shouldNotBe null
        bootWith("bidvector.opening-collection.sample-list-file=reports/evidence/sample-list.tsv")
            .failure shouldNotBe null
    }

    @Test
    fun `서비스 키가 없으면 기동하지 않는다`() {
        bootWithout("bidvector.koneps.service-key").failure shouldNotBe null
    }

    @Test
    fun `총 상한이 일 상한보다 작으면 기동하지 않는다 — 값이 서로 어긋나는 구성도 막는다`() {
        val booted =
            boot(
                *approved.filterNot { it.startsWith("bidvector.opening-collection.calls-total=") }.toTypedArray(),
                "bidvector.opening-collection.calls-total=10",
            )

        booted.failure shouldNotBe null
    }

    @Test
    fun `등재되지 않은 업종이면 기동하지 않는다 — 경로를 지어내지 않는다`() {
        val booted =
            boot(
                *approved.filterNot { it.startsWith("bidvector.opening-collection.categories=") }.toTypedArray(),
                "bidvector.opening-collection.categories=goods",
            )

        booted.failure shouldNotBe null
    }

    @Test
    fun `평문 http 외부 호스트는 기동 실패다 — 서비스 키가 쿼리로 나간다`() {
        val booted = boot(*approved, "bidvector.koneps.opening.scsbid-base-url=http://koneps.example.com/api")

        booted.failure shouldNotBe null
    }
}

/**
 * M6/6G D-6G-2 — 추출 배선도 같은 성질이다: `mode=once` 일 때만 올라오고, 출력 경로·기간·표본 목록
 * 해시가 없으면 기동하지 않는다. **KONEPS 서비스 키를 요구하지 않는다**(DB 만 읽는다).
 */
class SnapshotExtractionWiringTest {
    private val approved =
        arrayOf(
            "bidvector.snapshot-extract.mode=once",
            "bidvector.snapshot-extract.from=2026-06-01",
            "bidvector.snapshot-extract.to=2026-06-30",
            "bidvector.snapshot-extract.output-dir=/tmp/bidvector-snapshot-wiring-test",
            "bidvector.snapshot-extract.snapshot-id=wiring-test",
            "bidvector.snapshot-extract.sample-list-sha256=feedface",
        )

    private fun boot(vararg properties: String): Pair<AnnotationConfigApplicationContext, Throwable?> {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(SnapshotExtractionWiring::class.java)
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        return context to runCatching { context.refresh() }.exceptionOrNull()
    }

    @Test
    fun `mode 가 없으면 추출 배선이 올라오지 않는다`() {
        val (context, failure) = boot("bidvector.snapshot-extract.from=2026-06-01")

        failure shouldBe null
        context.use { it.getBeanNamesForType(ApplicationRunner::class.java).toList() }.shouldBeEmpty()
    }

    @Test
    fun `승인 값이 다 있으면 추출 러너가 선다 — 서비스 키를 요구하지 않는다`() {
        val (context, failure) = boot(*approved)

        failure shouldBe null
        context.use { it.getBean(SnapshotExtractionRunner::class.java) shouldNotBe null }
    }

    @Test
    fun `출력 경로가 없으면 기동하지 않는다 — 어디에 쓰는지를 실행자가 매번 정한다`() {
        val (_, failure) =
            boot(*approved.filterNot { it.startsWith("bidvector.snapshot-extract.output-dir=") }.toTypedArray())

        failure shouldNotBe null
    }

    @Test
    fun `표본 목록 해시가 없으면 기동하지 않는다 — 결과에서 역산한 값을 싣지 않는다`() {
        val (_, failure) =
            boot(
                *approved
                    .filterNot { it.startsWith("bidvector.snapshot-extract.sample-list-sha256=") }
                    .toTypedArray(),
            )

        failure shouldNotBe null
    }
}
