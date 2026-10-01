package bidvector.adapters.snapshot

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleConfirmation
import bidvector.workflow.collection.SampleOutcome
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.collection.SampleStratum
import bidvector.workflow.collection.StratumOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

internal val RUN_STATE_KEY = NoticeKeyHash.of("SYN-6G-0001", "000")

internal val RUN_STATE_AT: Instant = Instant.parse("2026-09-24T01:00:00Z")

internal val RUN_STATE_STRATUM = SampleStratum(BusinessDivision.SERVICE, "2026-W23")

internal fun runStateSample() =
    SampleConfirmation(
        SampleOutcome(
            listOf(RUN_STATE_KEY),
            mapOf(RUN_STATE_STRATUM to StratumOutcome(1, 1, 1)),
            mapOf(RUN_STATE_KEY to RUN_STATE_STRATUM),
            1,
        ),
        SampleScope(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 3), setOf(BusinessDivision.SERVICE)),
    )

internal fun runStatePendingAttempt() =
    CollectionAttempt(
        RUN_STATE_KEY.value,
        SourceEndpoint.RESERVE_PRICE_DETAIL,
        AttemptOutcome.Succeeded,
        RUN_STATE_AT,
        AttemptKind.PENDING,
        walk = null,
    )

/**
 * 실행 상태 디렉터리 test 의 공통 하네스 — 여는 자리가 곧 잠금 자리라(D-6G-57) **test 마다 놓아
 * 주어야** 하고, 그 규율을 두 test 클래스가 공유한다(무결성·복구 축과 형식 version 축). 사본을 두지
 * 않는 이유는 그 규율이 갈리면 한쪽 클래스의 test 가 「이미 도는 실행」을 보며 조용히 붉어진다.
 */
abstract class RunStateDirectoryFixture {
    @TempDir
    lateinit var temp: Path

    /** 이 test 가 연 디렉터리 전부 — 같은 자리를 두 번 여는 test 도 여기 담아 놓아 준다. */
    protected val opened = mutableListOf<RunStateDirectory>()

    /**
     * 연 디렉터리는 **잠금을 들고 있다**(D-6G-57). 한 JVM 안에서 두 번 열면 둘째가 「이미 도는
     * 실행」이 되므로, test 마다 놓아 준다(출하에서는 프로세스가 끝나며 OS 가 놓는다).
     */
    @AfterEach
    fun releaseLocks() {
        opened.forEach { it.close() }
        opened.clear()
    }

    protected fun open(at: Path = root()): RunStateDirectory = RunStateDirectory(at).also { opened += it }

    /** 같은 자리를 **다시 기동**한다 — 앞 실행이 끝난 뒤이므로 잠금을 놓고 다시 잡는다. */
    protected fun reopen(at: Path = root()): RunStateDirectory {
        releaseLocks()
        return open(at)
    }

    protected fun root(): Path = Files.createDirectories(temp.resolve("run-state"))
}
