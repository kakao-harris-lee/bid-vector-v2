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
import bidvector.workflow.collection.sha256Hex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

private val KEY = NoticeKeyHash.of("SYN-6G-0001", "000")
private val STRATUM = SampleStratum(BusinessDivision.SERVICE, "2026-W23")
private val AT: Instant = Instant.parse("2026-09-24T01:00:00Z")

private fun sample() =
    SampleConfirmation(
        SampleOutcome(listOf(KEY), mapOf(STRATUM to StratumOutcome(1, 1, 1)), mapOf(KEY to STRATUM), 1),
        SampleScope(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 3), setOf(BusinessDivision.SERVICE)),
    )

private fun httpAttempt() =
    CollectionAttempt(KEY.value, SourceEndpoint.RESERVE_PRICE_DETAIL, AttemptOutcome.Succeeded, AT, AttemptKind.PENDING)

/**
 * D-6G-45 — 실행 상태는 저장소 밖 디렉터리 하나다. 이 test 가 재는 것은 **거부**다: 디렉터리가
 * 없거나 표본 목록이 뒤에 바뀌면, 조용히 0 에서 시작하지 않고 기동이 실패해야 한다.
 */
class RunStateDirectoryTest {
    @TempDir
    lateinit var temp: Path

    private val opened = mutableListOf<RunStateDirectory>()

    /**
     * 연 디렉터리는 **잠금을 들고 있다**(D-6G-57). 한 JVM 안에서 두 번 열면 둘째가 「이미 도는
     * 실행」이 되므로, test 마다 놓아 준다(출하에서는 프로세스가 끝나며 OS 가 놓는다).
     */
    @AfterEach
    fun releaseLocks() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun open(at: Path = root()): RunStateDirectory = RunStateDirectory(at).also { opened += it }

    /** 같은 자리를 **다시 기동**한다 — 앞 실행이 끝난 뒤이므로 잠금을 놓고 다시 잡는다. */
    private fun reopen(at: Path = root()): RunStateDirectory {
        releaseLocks()
        return open(at)
    }

    private fun root(): Path = Files.createDirectories(temp.resolve("run-state"))

    @Test
    fun `디렉터리가 없으면 거부한다 — 만들지 않는다`() {
        shouldThrow<IllegalArgumentException> { open(temp.resolve("없는-자리")) }
        Files.exists(temp.resolve("없는-자리")) shouldBe false
    }

    @Test
    fun `첫 확정이 무결성 장부 넷을 남긴다`() {
        val state = open()

        state.sampleList.confirm(sample())

        val declared = Files.readString(root().resolve(STATE_NAME))
        declared shouldContain "\"directory_id\""
        val sampleDigest = sha256Hex(Files.readString(root().resolve(SAMPLE_LIST_NAME)))
        declared shouldContain "\"sample_list_sha256\":\"$sampleDigest\""
        declared shouldContain "\"attempts_sha256\""
        declared shouldContain "\"attempt_lines\":0"
    }

    /** 원장을 지우면 상한이 0 에서 다시 시작한다 — 앞 판은 그것을 거부 없이 지나갔다(vr M-7). */
    @Test
    fun `시도 원장을 지우면 기동을 거부한다`() {
        val state = open()
        state.sampleList.confirm(sample())
        state.attempts.append(httpAttempt())
        Files.delete(root().resolve(ATTEMPT_LEDGER_NAME))

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    @Test
    fun `시도 원장을 자르면 기동을 거부한다`() {
        val state = open()
        state.sampleList.confirm(sample())
        state.attempts.append(httpAttempt())
        state.attempts.append(httpAttempt())
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(file, Files.readString(file).lines().first() + "\n")

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    /**
     * cr r4 M-3 — 파일 **셋을 통째로** 복사하면 넷이 서로 맞아 앞 판은 그대로 기동했다(상한이 0 에서
     * 다시 시작한다). 장부의 표식이 그 **자리**에 묶이면 복사본은 자기 자리의 것이 아니다.
     */
    @Test
    fun `디렉터리를 통째로 복사하면 거부한다`() {
        val source = open()
        source.sampleList.confirm(sample())
        source.attempts.append(httpAttempt())
        val copy = Files.createDirectories(temp.resolve("whole-copy"))
        listOf(SAMPLE_LIST_NAME, SAMPLE_SCOPE_NAME, ATTEMPT_LEDGER_NAME, STATE_NAME).forEach {
            Files.copy(root().resolve(it), copy.resolve(it))
        }

        shouldThrow<IllegalArgumentException> { open(copy) }
    }

    /** 표본과 장부만 새 디렉터리로 옮기면 원장이 비어 장부와 어긋난다 — 「새로 시작」이 막힌다. */
    @Test
    fun `원장 없이 표본과 장부만 복사하면 거부한다`() {
        val source = open()
        source.sampleList.confirm(sample())
        source.attempts.append(httpAttempt())
        val copy = Files.createDirectories(temp.resolve("copied"))
        listOf(SAMPLE_LIST_NAME, STATE_NAME).forEach {
            Files.copy(root().resolve(it), copy.resolve(it))
        }

        shouldThrow<IllegalArgumentException> { open(copy) }
    }

    /** 파일이 있는데 장부만 지운 것도 거부다 — 무엇이 지워졌는지 알 수 없다. */
    @Test
    fun `장부만 지워도 거부한다`() {
        val state = open()
        state.sampleList.confirm(sample())
        Files.delete(root().resolve(STATE_NAME))

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    @Test
    fun `줄을 쓸 때마다 장부가 따라온다`() {
        val state = open()
        state.sampleList.confirm(sample())

        state.attempts.append(httpAttempt())
        state.attempts.append(httpAttempt())

        Files.readString(root().resolve(STATE_NAME)) shouldContain "\"attempt_lines\":2"
        // 같은 디렉터리로 다시 기동해도 넷이 맞는다.
        open().attempts.read().size shouldBe 2
    }

    /** 바깥에서 목록을 바꿔치우면 「결과를 보기 전에 확정했다」가 거짓이 된다 — 안을 봐서는 모른다. */
    @Test
    fun `확정된 뒤 표본 목록이 바뀌면 기동을 거부한다`() {
        open().sampleList.confirm(sample())
        val swapped = SampleListFile.render(SampleOutcome(listOf(OTHER_KEY), emptyMap(), mapOf(OTHER_KEY to STRATUM)))
        Files.writeString(root().resolve(SAMPLE_LIST_NAME), swapped)

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    @Test
    fun `해시 파일이 사라져도 거부한다`() {
        open().sampleList.confirm(sample())
        Files.delete(root().resolve(STATE_NAME))

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    @Test
    fun `표본 확정 전에는 아무 조건도 걸지 않는다`() {
        open().sampleList.confirmed() shouldBe null
    }

    /**
     * D-6G-60 — 장부의 대상 집합은 **디렉터리 목록에서 도출**한다. 이름을 열거하면 다음 파일이
     * 장부 밖에 놓이고, 장부는 그것이 바뀌어도 초록이다.
     */
    @Test
    fun `장부가 모르는 파일이 디렉터리에 있으면 거부한다`() {
        val state = open()
        state.sampleList.confirm(sample())
        Files.writeString(root().resolve("옆에-둔-메모.txt"), "x")

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    /** 자물쇠는 상태가 아니다 — 잠금 파일이 있다고 기동이 막히면 두 번째 실행이 영영 못 돈다. */
    @Test
    fun `잠금 파일은 장부 대상이 아니다`() {
        open().sampleList.confirm(sample())

        Files.isRegularFile(root().resolve(RUN_LOCK_NAME)) shouldBe true
        reopen().sampleList.confirmed() shouldBe null.let { open().sampleList.confirmed() }
    }

    /** D-6G-60 — 표본틀 범위 파일도 장부 대상이다. 지우거나 고치면 그 표본이 무엇의 표본인지 달라진다. */
    @Test
    fun `표본틀 범위 파일을 지우면 거부한다`() {
        open().sampleList.confirm(sample())
        Files.delete(root().resolve(SAMPLE_SCOPE_NAME))

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    @Test
    fun `표본틀 범위 파일을 고치면 거부한다`() {
        open().sampleList.confirm(sample())
        val scope = root().resolve(SAMPLE_SCOPE_NAME)
        Files.writeString(scope, Files.readString(scope).replace("2026-06-03", "2026-06-10"))

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    /**
     * D-6G-61 ② — 마지막 append 와 장부 교체 사이에서 죽으면 원장이 장부보다 **한 줄 앞선다.**
     * append-only 원장에서 그것은 변조가 아니라 크래시 흔적이다. 거부하면 그 실행 상태는 사람이
     * 손대기 전까지 막히고, **상한도 되감기지 않는다**(그 줄이 세어져야 한다).
     */
    @Test
    fun `장부보다 한 줄 앞선 원장은 거부가 아니라 재동기다`() {
        val state = open()
        state.sampleList.confirm(sample())
        state.attempts.append(httpAttempt())
        // 마지막 append 직후 kill 을 흉내낸다 — 줄은 붙었고 장부는 아직 앞 상태다.
        val stale = Files.readString(root().resolve(STATE_NAME))
        state.attempts.append(httpAttempt())
        Files.writeString(root().resolve(STATE_NAME), stale)

        val reopened = reopen()

        reopened.attempts.read().size shouldBe 2
        reopened.attempts
            .read()
            .spend(Instant.EPOCH)
            .total shouldBe 2
        Files.readString(root().resolve(STATE_NAME)) shouldContain "\"attempt_lines\":2"
    }

    /** 앞부분이 다르면 append-only 로 설명되지 않는다 — 재동기가 아니라 거부다. */
    @Test
    fun `앞부분이 바뀐 원장은 줄이 늘어도 거부한다`() {
        val state = open()
        state.sampleList.confirm(sample())
        state.attempts.append(httpAttempt())
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        val rewritten = Files.readString(file).replace("\"kind\":\"PENDING\"", "\"kind\":\"AXIS\"")
        Files.writeString(file, rewritten + rewritten)

        shouldThrow<IllegalArgumentException> { reopen() }
    }

    /**
     * D-6G-61 ③ — 표본 확정은 세 걸음이다. 중간에 죽으면 장부는 「확정된 목록 없음」인데 파일이
     * 남고, 다음 기동은 그 불일치를 보고 거부한다. 끝나지 않은 확정은 확정이 아니므로 되돌린다.
     */
    @Test
    fun `확정 중간에 죽은 표본 파일은 되돌린다 — 기동이 막히지 않는다`() {
        val state = open()
        state.attempts.append(httpAttempt())
        // 첫 걸음만 끝난 모양 — 목록 파일은 있고 범위 파일과 장부 기록이 없다.
        Files.writeString(root().resolve(SAMPLE_LIST_NAME), SampleListFile.render(sample().sample))

        val reopened = reopen()

        reopened.sampleList.confirmed() shouldBe null
        Files.exists(root().resolve(SAMPLE_LIST_NAME)) shouldBe false
        // 되돌린 것은 확정뿐이다 — 원장은 그대로이고 상한이 되감기지 않는다.
        reopened.attempts.read().size shouldBe 1
    }

    /**
     * D-6G-57 — 잠금 없이 원장에 쓰는 길을 두지 않는다. 관문은 호출 **전에** 의도 줄을 적으므로,
     * 임차 검사를 잊은 배선이 있어도 첫 호출이 나가기 전에 멈춘다.
     */
    @Test
    fun `잠금을 들지 못한 실행은 원장에 쓰지 못한다`() {
        val held = open()
        held.sampleList.confirm(sample())
        held.attempts.append(httpAttempt())

        val second = RunStateDirectory(root()).also { opened += it }

        second.lock shouldBe RunStateLock.Busy
        // 읽기는 된다 — 막는 것은 쓰기다.
        second.attempts.read().size shouldBe 1
        shouldThrow<IllegalStateException> { second.attempts.append(httpAttempt()) }
    }
}

private val OTHER_KEY = NoticeKeyHash.of("SYN-6G-9999", "000")

class FileAttemptLedgerTest {
    @TempDir
    lateinit var temp: Path

    private fun ledger(): FileAttemptLedger =
        FileAttemptLedger(Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME)) {}

    private fun attemptOf(
        outcome: AttemptOutcome,
        key: String? = KEY.value,
        kind: AttemptKind = AttemptKind.PENDING,
    ) = CollectionAttempt(key, SourceEndpoint.RESERVE_PRICE_DETAIL, outcome, AT, kind)

    @Test
    fun `원장이 없으면 빈 이력이다 — 첫 실행이다`() {
        ledger().read().size shouldBe 0
    }

    @Test
    fun `쓴 것을 그대로 읽는다 — 결말 셋 전부`() {
        val ledger = ledger()
        val written =
            listOf(
                attemptOf(AttemptOutcome.Succeeded),
                attemptOf(AttemptOutcome.Failed("TIMEOUT"), kind = AttemptKind.HTTP),
                attemptOf(AttemptOutcome.Succeeded, key = null),
                attemptOf(AttemptOutcome.Empty, kind = AttemptKind.AXIS),
            )

        written.forEach(ledger::append)

        val history = ledger.read()
        history.size shouldBe written.size
        // 축 결말 줄만 이어 돌기에 든다. HTTP 줄은 상한만 센다(둘이 한 파일에 있어도 섞이지 않는다).
        history.settledAxes().getValue(KEY.value) shouldContainExactly listOf(SourceEndpoint.RESERVE_PRICE_DETAIL)
        history.spend(AT).total shouldBe 2
    }

    /** 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고, 그것은 상한이 없는 것보다 나쁘다. */
    @Test
    fun `형태를 어긴 줄은 읽지 않는다`() {
        val ledger = ledger()
        ledger.append(attemptOf(AttemptOutcome.Succeeded))
        val file = Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME)

        Files.writeString(file, Files.readString(file) + "{\"axis\":\"RESERVE_PRICE_DETAIL\"}\n")

        shouldThrow<IllegalArgumentException> { ledger.read() }
    }
}
