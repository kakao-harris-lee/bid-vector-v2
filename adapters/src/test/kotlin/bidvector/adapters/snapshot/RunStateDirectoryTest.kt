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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

    private fun root(): Path = Files.createDirectories(temp.resolve("run-state"))

    @Test
    fun `디렉터리가 없으면 거부한다 — 만들지 않는다`() {
        shouldThrow<IllegalArgumentException> { RunStateDirectory(temp.resolve("없는-자리")) }
        Files.exists(temp.resolve("없는-자리")) shouldBe false
    }

    @Test
    fun `첫 확정이 무결성 장부 넷을 남긴다`() {
        val state = RunStateDirectory(root())

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
        val state = RunStateDirectory(root())
        state.sampleList.confirm(sample())
        state.attempts.append(httpAttempt())
        Files.delete(root().resolve(ATTEMPT_LEDGER_NAME))

        shouldThrow<IllegalArgumentException> { RunStateDirectory(root()) }
    }

    @Test
    fun `시도 원장을 자르면 기동을 거부한다`() {
        val state = RunStateDirectory(root())
        state.sampleList.confirm(sample())
        state.attempts.append(httpAttempt())
        state.attempts.append(httpAttempt())
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(file, Files.readString(file).lines().first() + "\n")

        shouldThrow<IllegalArgumentException> { RunStateDirectory(root()) }
    }

    /** 표본과 장부만 새 디렉터리로 옮기면 원장이 비어 장부와 어긋난다 — 「새로 시작」이 막힌다. */
    @Test
    fun `원장 없이 표본과 장부만 복사하면 거부한다`() {
        val source = RunStateDirectory(root())
        source.sampleList.confirm(sample())
        source.attempts.append(httpAttempt())
        val copy = Files.createDirectories(temp.resolve("copied"))
        listOf(SAMPLE_LIST_NAME, STATE_NAME).forEach {
            Files.copy(root().resolve(it), copy.resolve(it))
        }

        shouldThrow<IllegalArgumentException> { RunStateDirectory(copy) }
    }

    /** 파일이 있는데 장부만 지운 것도 거부다 — 무엇이 지워졌는지 알 수 없다. */
    @Test
    fun `장부만 지워도 거부한다`() {
        val state = RunStateDirectory(root())
        state.sampleList.confirm(sample())
        Files.delete(root().resolve(STATE_NAME))

        shouldThrow<IllegalArgumentException> { RunStateDirectory(root()) }
    }

    @Test
    fun `줄을 쓸 때마다 장부가 따라온다`() {
        val state = RunStateDirectory(root())
        state.sampleList.confirm(sample())

        state.attempts.append(httpAttempt())
        state.attempts.append(httpAttempt())

        Files.readString(root().resolve(STATE_NAME)) shouldContain "\"attempt_lines\":2"
        // 같은 디렉터리로 다시 기동해도 넷이 맞는다.
        RunStateDirectory(root()).attempts.read().size shouldBe 2
    }

    /** 바깥에서 목록을 바꿔치우면 「결과를 보기 전에 확정했다」가 거짓이 된다 — 안을 봐서는 모른다. */
    @Test
    fun `확정된 뒤 표본 목록이 바뀌면 기동을 거부한다`() {
        RunStateDirectory(root()).sampleList.confirm(sample())
        val swapped = SampleListFile.render(SampleOutcome(listOf(OTHER_KEY), emptyMap(), mapOf(OTHER_KEY to STRATUM)))
        Files.writeString(root().resolve(SAMPLE_LIST_NAME), swapped)

        shouldThrow<IllegalArgumentException> { RunStateDirectory(root()) }
    }

    @Test
    fun `해시 파일이 사라져도 거부한다`() {
        RunStateDirectory(root()).sampleList.confirm(sample())
        Files.delete(root().resolve(STATE_NAME))

        shouldThrow<IllegalArgumentException> { RunStateDirectory(root()) }
    }

    @Test
    fun `표본 확정 전에는 아무 조건도 걸지 않는다`() {
        RunStateDirectory(root()).sampleList.confirmed() shouldBe null
    }
}

private val OTHER_KEY = NoticeKeyHash.of("SYN-6G-9999", "000")

class FileAttemptLedgerTest {
    @TempDir
    lateinit var temp: Path

    private fun ledger(): FileAttemptLedger =
        FileAttemptLedger(Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME))

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
        history.spend(Instant.parse("2026-09-20T00:00:00Z"), AT).total shouldBe 2
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
