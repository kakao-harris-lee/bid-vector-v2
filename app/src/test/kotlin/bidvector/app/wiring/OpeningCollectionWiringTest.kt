package bidvector.app.wiring

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.OpeningCollectionRunner
import bidvector.app.collection.SnapshotExtractionRunner
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.NoticeId
import bidvector.procurement.SourceEndpoint
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
private object StubAxisStore : CollectedAxisStore {
    override fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<NoticeId>,
    ): Set<NoticeId> = emptySet()
}

/**
 * 저장소 밖 — 배선은 디렉터리가 **있는지**만 본다(표본 확정과 시도 기록은 수집이 한다).
 * test 마다 새 자리를 준다: 무결성 장부가 파일 셋의 일관성을 요구하므로(D-6G-48) 한 자리를
 * 여럿이 나눠 쓰면 앞 test 가 남긴 상태가 뒤 test 의 기동을 막는다.
 */
private fun newRunState(): Path = Files.createTempDirectory("6g-wiring-run-state")

private val WIRING_RUN_STATE: Path = newRunState()

class OpeningCollectionWiringTest {
    private val fixedNow = Instant.parse("2026-09-24T03:00:00Z")

    private val approved =
        arrayOf(
            "bidvector.opening-collection.mode=once",
            "bidvector.opening-collection.from=2026-09-20",
            "bidvector.opening-collection.to=2026-09-22",
            "bidvector.opening-collection.categories=construction,service",
            "bidvector.opening-collection.sampling-seed=6g-wiring-seed",
            "bidvector.opening-collection.sample-size=2",
            "bidvector.opening-collection.calls-per-day=100",
            "bidvector.opening-collection.calls-total=1000",
            "bidvector.opening-collection.run-state-dir=$WIRING_RUN_STATE",
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
        context.registerBean(CollectedAxisStore::class.java, Supplier { StubAxisStore })
        context.register(OpeningCollectionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { fixedNow } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        return Booted(context, failure)
    }

    private fun bootWithout(key: String): Booted = boot(*approved.filterNot { it.startsWith("$key=") }.toTypedArray())

    /**
     * 시도 원장을 미리 깐다 — 앞 실행이 남긴 상태를 재현한다. **출하 경로로 쓴다**: 손으로 줄만
     * 쓰면 무결성 장부와 어긋나 기동이 거부되고(D-6G-48), 그 거부는 이 test 가 재려는 것이 아니다.
     */
    private fun seedAttempts(vararg lines: Pair<String, Int>) {
        val runState = RunStateDirectory(WIRING_RUN_STATE)
        lines.forEach { (at, calls) ->
            repeat(calls) {
                runState.attempts.append(
                    CollectionAttempt(
                        noticeKey = null,
                        axis = SourceEndpoint.OPENING_RESULT_LIST,
                        outcome = AttemptOutcome.Succeeded,
                        at = Instant.parse(at),
                        kind = AttemptKind.PENDING,
                    ),
                )
            }
        }
        // 잠금을 놓는다 — 놓지 않으면 뒤이은 기동이 스스로를 「이미 도는 실행」으로 본다.
        runState.close()
    }

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

    /**
     * 상한은 **시도 원장에서 seed** 된다(D-6G-45). 매 기동 0 에서 시작하면 승인 총 상한이 3~4일에
     * 걸친 여러 실행을 덮지 못하고, 하루 경계를 UTC 로 잡으면 KST 자정과 그 사이 아홉 시간의 호출이
     * 오늘치에서 빠진다. 둘 다 조용한 실패라 **원장 파일을 미리 깔고** 잰다.
     *
     * 고정 시계는 `2026-09-24T03:00Z` = KST 12:00 → 그날 KST 자정은 전날 `15:00Z` 다. 두 줄을 그
     * 경계 양쪽에 두어 오늘치가 한 줄만 세는지 본다.
     */
    @Test
    fun `호출 상한은 시도 원장의 HTTP 시도 합에서 시작한다 — KST 경계로`() {
        seedAttempts(
            "2026-09-23T16:00:00Z" to 6,
            "2026-09-23T14:00:00Z" to 1,
        )

        use(boot(*approved)) { context ->
            val ledger = context.getBean(CallBudgetLedger::class.java)
            ledger.spentTotal shouldBe 7
            ledger.spentToday shouldBe 6
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
        bootWithout("bidvector.opening-collection.sample-size").failure shouldNotBe null
    }

    /**
     * 실행 상태 자리를 지어내지 않는다(D-6G-39·45) — 기본 경로를 두면 다른 수집의 표본과 시도를
     * 조용히 이어받는다. 저장소 **안**을 가리키면 기동이 실패한다(D-6G-43).
     */
    @Test
    fun `실행 상태 디렉터리가 없거나 저장소 안이면 기동하지 않는다`() {
        bootWithout("bidvector.opening-collection.run-state-dir").failure shouldNotBe null
        bootWith("bidvector.opening-collection.run-state-dir=reports/evidence")
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
 * 파일이 없으면 기동하지 않는다. **KONEPS 서비스 키를 요구하지 않는다**(DB 만 읽는다).
 */
private val EXTRACT_RUN_STATE: Path = newRunState()

class SnapshotExtractionWiringTest {
    private val approved =
        arrayOf(
            "bidvector.snapshot-extract.mode=once",
            "bidvector.snapshot-extract.from=2026-06-01",
            "bidvector.snapshot-extract.to=2026-06-30",
            "bidvector.snapshot-extract.output-dir=/tmp/bidvector-snapshot-wiring-test",
            "bidvector.snapshot-extract.snapshot-id=wiring-test",
            "bidvector.snapshot-extract.run-state-dir=$EXTRACT_RUN_STATE",
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

    /**
     * 해시를 설정으로 받지 않는다(D-6G-39) — 받으면 실행자가 적어 넣은 문자열이 「결과를 보기 전에
     * 확정됐다」의 증거 행세를 한다. 수집의 **실행 상태**를 가리키게 하고 해시는 그 바이트에서 낸다.
     */
    @Test
    fun `실행 상태 디렉터리가 없으면 기동하지 않는다 — 해시를 설정으로 받지 않는다`() {
        val (_, failure) =
            boot(
                *approved
                    .filterNot { it.startsWith("bidvector.snapshot-extract.run-state-dir=") }
                    .toTypedArray(),
            )

        failure shouldNotBe null
    }

    /** 실행 상태도 저장소 밖이다(D-6G-43) — 실험 입력은 커밋되지 않는다. */
    @Test
    fun `실행 상태 디렉터리가 저장소 안이면 기동하지 않는다`() {
        val (_, failure) =
            boot(
                *approved
                    .filterNot { it.startsWith("bidvector.snapshot-extract.run-state-dir=") }
                    .toTypedArray(),
                "bidvector.snapshot-extract.run-state-dir=reports/evidence",
            )

        failure shouldNotBe null
    }
}
