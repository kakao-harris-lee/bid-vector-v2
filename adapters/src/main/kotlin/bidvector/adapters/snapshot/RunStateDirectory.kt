package bidvector.adapters.snapshot

import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.adapters.koneps.asIntOrNull
import bidvector.adapters.koneps.asObject
import bidvector.adapters.koneps.asStringOrNull
import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptLedger
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.NoticeKeyHash
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

internal const val SAMPLE_LIST_NAME = "sample-list.tsv"
internal const val ATTEMPT_LEDGER_NAME = "attempts.jsonl"
internal const val STATE_NAME = "state.json"

private const val ATTEMPT_MAX_DEPTH = 4

/**
 * 실행 상태의 무결성 장부(D-6G-48) — 이 넷이 맞아야 기동한다.
 *
 * 앞 판은 표본 해시 하나였고, 그래서 **원장만 지우거나 잘라도** 거부 없이 상한이 0 에서 다시
 * 시작했다(vr M-7). 원장의 해시와 줄 수를 함께 적으면 삭제·절삭·부분 복사가 전부 어긋난다.
 * [directoryId] 는 첫 확정이 지은 표식이다 — 파일 셋이 한 실행의 것임을 말한다.
 *
 * **세 파일을 통째로 복사한 것은 구별되지 않는다**(넷이 서로 맞으므로). 그것은 운영자 행위이고
 * 경계 밖이다 — 이 장부가 겨누는 것은 사고와 오조작이다.
 */
private class RunStateFacts(
    val directoryId: String,
    val sampleListSha256: String,
    val sampleScopeSha256: String,
    val attemptsSha256: String,
    val attemptLines: Int,
)

/**
 * 실험 실행 상태 디렉터리(D-6G-45·48) — 저장소 **밖**에 있고 파일 셋을 담는다.
 *
 * | 파일 | 무엇 |
 * |---|---|
 * | `sample-list.tsv` | 확정된 표본(D-6G-39) |
 * | `attempts.jsonl` | 시도 원장 — append-only |
 * | `state.json` | 위 둘의 무결성 장부([RunStateFacts]) |
 *
 * **이 클래스는 디렉터리를 만들지 않는다.** 없으면 거부한다. 경로 오타 하나로 빈 디렉터리가 생기면
 * 승인 상한이 조용히 0 에서 시작하고 표본이 다시 뽑힌다 — 둘 다 실 호출이 나간 뒤에야 드러난다.
 * 디렉터리를 만드는 것은 운영자의 명시 행위여야 한다.
 */
class RunStateDirectory(
    private val root: Path,
) {
    private val stateFile = root.resolve(STATE_NAME)
    private val sampleFile = root.resolve(SAMPLE_LIST_NAME)
    private val scopeFile = root.resolve(SAMPLE_SCOPE_NAME)
    private val attemptFile = root.resolve(ATTEMPT_LEDGER_NAME)

    /**
     * **여는 자리가 곧 잠금 자리**(D-6G-57) — 원장을 얻는 길은 이 객체 하나이고 이 객체를 지으면
     * 잠금을 시도한다. 잠그지 않고 원장에 쓰는 경로를 배선이 만들 수 없다. 얻지 못한 것은 오류가
     * 아니라 값이다: 호출부가 [RunStateLock.Busy] 를 보고 조용히 끝낸다.
     */
    val lock: RunStateLock = RunStateLock.tryAcquire(requireStateDirectory(root))

    /** 첫 확정이 지은 표식 — 재동기가 [init] 안에서 장부를 다시 쓰므로 그 전에 값이 있어야 한다. */
    private val directoryId: String =
        readFacts()?.directoryId ?: java.util.UUID
            .randomUUID()
            .toString()

    init {
        if (lock is RunStateLock.Held) {
            // 기동 거부로 끝나도 **잠금은 놓는다** — 들고 죽은 잠금은 다음 실행을 막는다.
            runCatching {
                rollBackInterruptedConfirmation()
                verifyIntegrity()
            }.onFailure {
                lock.release()
                throw it
            }
        }
    }

    val sampleList: FileSampleListLedger = FileSampleListLedger(sampleFile) { recordState() }

    /**
     * 잠금을 들었을 때만 쓸 수 있다 — 들지 않았으면 읽기만 되고 [AttemptLedger.append] 가 거부한다.
     * 관문은 호출 **전에** 의도 줄을 적으므로(D-6G-61 ①), 잠금 없는 실행은 첫 호출이 나가기 전에
     * 멈춘다. 임차 검사를 잊은 배선이 있어도 호출은 나가지 못한다.
     */
    val attempts: AttemptLedger =
        when (lock) {
            is RunStateLock.Held -> FileAttemptLedger(attemptFile) { recordState() }
            RunStateLock.Busy -> LockedOutAttemptLedger(FileAttemptLedger(attemptFile))
        }

    /**
     * 잠금을 놓는다 — Spring 이 컨텍스트를 닫을 때 이름으로 찾아 부른다(`destroyMethod` 추론).
     * 프로세스가 죽으면 OS 가 놓지만, 한 프로세스가 여러 번 기동하는 test 는 여기서 놓아야 한다.
     */
    fun close() = lock.release()

    /** 추출이 읽는다 — 목록과 그 **바이트**(manifest 해시·곁파일 복사). */
    fun confirmedSampleList(): ConfirmedSampleList? = sampleList.read()

    /**
     * 장부를 **원자적으로 갈아 끼운다** — 줄을 쓸 때마다다. 덮어쓰다 죽으면 반쯤 쓰인 장부가 남아
     * 다음 기동이 거부되는데, 그것은 「사고를 잡았다」가 아니라 이 장부 자신이 만든 사고다.
     */
    private fun recordState() {
        val facts =
            SnapshotJson.Obj(
                listOf(
                    "directory_id" to SnapshotJson.Text(directoryId),
                    "sample_list_sha256" to SnapshotJson.Text(digestOf(sampleFile)),
                    "sample_scope_sha256" to SnapshotJson.Text(digestOf(scopeFile)),
                    "attempts_sha256" to SnapshotJson.Text(digestOf(attemptFile)),
                    "attempt_lines" to SnapshotJson.Number(lineCountOf(attemptFile).toString()),
                ),
            )
        val staged = root.resolve("$STATE_NAME.staged")
        Files.writeString(staged, facts.render() + "\n")
        Files.move(staged, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * 표본 확정은 세 걸음이다(목록 파일 · 범위 파일 · 무결성 장부, D-6G-61 ③). 중간에 죽으면 장부는
     * 아직 「확정된 목록 없음」인데 파일이 남고, 다음 기동은 그 불일치를 보고 거부한다 — 사람이 손대기
     * 전까지 풀리지 않는 사고를, 그 사고를 막으려던 장부가 만든다.
     *
     * **확정 전으로 되돌린다**: 끝나지 않은 확정은 확정이 아니다. 잠금 안이라 그 목록을 보고 있는 다른
     * 실행이 없고, 결과를 보기 전에 확정한다는 성질도 그대로다(다시 뽑아 다시 확정한다).
     *
     * 장부가 **아예 없는** 모양은 확정 창이 아니다 — 그것은 「장부만 지웠다」이고 [verifyIntegrity] 가
     * 거부한다(되돌리면 지워진 것이 무엇인지 모른 채 상한이 0 에서 시작한다). 확정은 목록 축 조회
     * 뒤에 오므로 그 시점에는 장부가 이미 서 있다.
     */
    private fun rollBackInterruptedConfirmation() {
        val facts = readFacts() ?: return
        if (facts.sampleListSha256 != EMPTY_DIGEST) return
        Files.deleteIfExists(sampleFile)
        Files.deleteIfExists(scopeFile)
    }

    /**
     * 기동 시 대조 — 넷 중 하나라도 어긋나면 거부한다. 장부가 아직 없으면(첫 실행) 검사할 것이
     * 없지만, **파일이 있는데 장부가 없으면** 거부다: 그것이 「장부만 지웠다」의 모양이다.
     */
    private fun verifyIntegrity() {
        val facts = readFacts()
        val hasFiles = Files.isRegularFile(sampleFile) || Files.isRegularFile(attemptFile)
        require(facts != null || !hasFiles) {
            "실행 상태 장부가 없는데 파일이 있다 — 무엇이 지워졌는지 알 수 없다"
        }
        if (facts == null) return
        require(facts.sampleListSha256 == digestOf(sampleFile)) {
            "표본 목록이 확정된 뒤에 바뀌었다 — 결과를 보기 전에 확정했다는 것이 더는 참이 아니다"
        }
        require(facts.sampleScopeSha256 == digestOf(scopeFile)) {
            "표본틀 범위가 확정된 뒤에 바뀌었다 — 그 표본이 무엇의 표본인지가 달라진다"
        }
        // **장부 대상 집합을 디렉터리 목록에서 도출한다**(D-6G-60) — 다음 파일이 생겨도 장부 밖에
        // 놓이지 않는다. 자물쇠(`run.lock`)와 장부 자신은 상태가 아니라 뺀다.
        val present =
            Files
                .list(root)
                .use { paths -> paths.map { it.fileName.toString() }.toList() }
                .filterNot { it == STATE_NAME || it == RUN_LOCK_NAME || it.endsWith(".staged") }
                .toSet()
        require(present == LEDGERED_FILES.filter { Files.isRegularFile(root.resolve(it)) }.toSet()) {
            "실행 상태 디렉터리에 장부가 모르는 파일이 있다 — 무엇이 정본인지 알 수 없다"
        }
        requireLedgerAheadOrEqual(facts)
    }

    /**
     * 원장은 **append-only** 다(D-6G-61 ②). 그래서 「장부가 아는 앞부분이 그대로이고 뒤에 줄이
     * 더 있다」는 변조가 아니라 **크래시 흔적**이다 — 마지막 append 와 장부 교체 사이에서 죽으면
     * 반드시 이 모양이 된다. 거부하면 그 실행 상태는 사람이 손대기 전까지 영영 막히고, 그 사고를
     * 만든 것은 장부 자신이다. 이 경우는 **원장을 정본으로 재동기**한다.
     *
     * 앞부분이 다르거나 줄이 **줄었으면** 여전히 거부다 — 그것은 append-only 로 설명되지 않는다.
     */
    private fun requireLedgerAheadOrEqual(facts: RunStateFacts) {
        val lines = linesOf(attemptFile)
        require(lines.size >= facts.attemptLines) {
            "시도 원장의 줄 수가 장부보다 적다 — 지워졌거나 잘렸다"
        }
        val known = lines.take(facts.attemptLines).joinToString("") { it + "\n" }
        require(sha256Hex(known) == facts.attemptsSha256) {
            "시도 원장의 앞부분이 장부와 다르다 — append-only 로 설명되지 않는 변경이다"
        }
        if (lines.size > facts.attemptLines) recordState()
    }

    private fun readFacts(): RunStateFacts? {
        val fields =
            runCatching { Files.readString(stateFile) }
                .getOrNull()
                ?.let { KonepsJsonParser.parse(it, ATTEMPT_MAX_DEPTH).asObject()?.fields }
                ?: return null
        return RunStateFacts(
            directoryId = requireNotNull(fields["directory_id"].asStringOrNull()) { "장부에 디렉터리 식별자가 없다" },
            sampleListSha256 = requireNotNull(fields["sample_list_sha256"].asStringOrNull()) { "장부에 표본 해시가 없다" },
            sampleScopeSha256 =
                requireNotNull(fields["sample_scope_sha256"].asStringOrNull()) { "장부에 범위 해시가 없다" },
            attemptsSha256 = requireNotNull(fields["attempts_sha256"].asStringOrNull()) { "장부에 원장 해시가 없다" },
            attemptLines = requireNotNull(fields["attempt_lines"].asIntOrNull()) { "장부에 원장 줄 수가 없다" },
        )
    }
}

private fun requireStateDirectory(root: Path): Path {
    require(Files.isDirectory(root)) {
        "실행 상태 디렉터리가 없다 — 만들지 않는다(경로를 잘못 대면 상한이 0 에서 시작한다)"
    }
    return root
}

/** 없는 파일은 빈 바이트로 본다 — 「아직 없다」와 「비었다」를 장부가 같게 다룬다. */
private fun digestOf(file: Path): String = sha256Hex(runCatching { Files.readString(file) }.getOrDefault(""))

/** 아직 아무것도 확정되지 않은 자리의 해시 — 「없다」와 「비었다」가 같으므로 값 하나다. */
private val EMPTY_DIGEST: String = sha256Hex("")

/**
 * 잠금을 들지 않은 실행의 시도 원장 — 읽기는 되고 **쓰기는 거부**한다(D-6G-57). 두 실행이 나란히
 * 원장에 쓰면 무결성 장부가 서로의 줄에 어긋나고, 그보다 먼저 두 상한 회계가 서로의 호출을 못 본다.
 */
private class LockedOutAttemptLedger(
    private val reads: AttemptLedger,
) : AttemptLedger {
    override fun append(attempt: CollectionAttempt): Unit = error("실행 상태 잠금을 들지 않았다 — 다른 실행이 돌고 있다")

    override fun read(): AttemptHistory = reads.read()
}

private fun lineCountOf(file: Path): Int = linesOf(file).size

private fun linesOf(file: Path): List<String> =
    runCatching { Files.readString(file) }
        .getOrDefault("")
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()

/**
 * 시도 원장의 파일 구현 — 줄마다 한 시도, JSON 하나. **덧붙이기만** 한다(고쳐 쓰지 않는다).
 *
 * 줄이 형태를 어기면 읽지 않는다 — 시도 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고, 그것은
 * 상한이 없는 것보다 나쁘다(있다고 믿게 된다).
 */
class FileAttemptLedger(
    private val file: Path,
    /** 줄을 쓸 때마다 무결성 장부를 갱신한다(D-6G-48) — 기록과 장부가 갈리는 창을 남기지 않는다. */
    private val onAppended: () -> Unit = {},
) : AttemptLedger {
    override fun append(attempt: CollectionAttempt) {
        Files.writeString(file, lineOf(attempt), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        onAppended()
    }

    override fun read(): AttemptHistory {
        if (!Files.isRegularFile(file)) return AttemptHistory(emptyList())
        return AttemptHistory(
            Files
                .readString(file)
                .lineSequence()
                .filter { it.isNotBlank() }
                .map(::parseLine)
                .toList(),
        )
    }

    private fun lineOf(attempt: CollectionAttempt): String =
        SnapshotJson
            .Obj(
                listOf(
                    "at" to SnapshotJson.Text(attempt.at.toString()),
                    "axis" to SnapshotJson.Text(attempt.axis.name),
                    "notice_key_hash" to (attempt.noticeKey?.let { SnapshotJson.Text(it) } ?: SnapshotJson.Null),
                    "outcome" to SnapshotJson.Text(labelOf(attempt.outcome)),
                    "kind" to SnapshotJson.Text(attempt.kind.name),
                ),
            ).render() + "\n"

    private fun parseLine(line: String): CollectionAttempt {
        val fields =
            requireNotNull(KonepsJsonParser.parse(line, ATTEMPT_MAX_DEPTH).asObject()?.fields) {
                "시도 원장 줄이 JSON 객체가 아니다"
            }
        val axis =
            requireNotNull(SourceEndpoint.entries.firstOrNull { it.name == fields["axis"].asStringOrNull() }) {
                "시도 원장의 축 어휘가 아니다"
            }
        return CollectionAttempt(
            // 형태 검사는 값 타입이 진다 — 원장에는 이미 지어진 hex 가 실린다.
            noticeKey = fields["notice_key_hash"]?.asStringOrNull()?.let { NoticeKeyHash.ofHex(it).value },
            axis = axis,
            outcome = outcomeOf(requireNotNull(fields["outcome"].asStringOrNull()) { "시도 원장에 결말이 없다" }),
            at = Instant.parse(requireNotNull(fields["at"].asStringOrNull()) { "시도 원장에 시각이 없다" }),
            kind =
                requireNotNull(AttemptKind.entries.firstOrNull { it.name == fields["kind"].asStringOrNull() }) {
                    "시도 원장의 줄 갈래 어휘가 아니다"
                },
        )
    }
}

/** 결말 어휘 — 오류는 코드를 뒤에 붙인다(`FAILED:<코드>`). 값이 아니라 분류만 싣는다. */
private fun labelOf(outcome: AttemptOutcome): String =
    when (outcome) {
        AttemptOutcome.Succeeded -> "SUCCEEDED"
        AttemptOutcome.Empty -> "EMPTY"
        is AttemptOutcome.Failed -> "FAILED:${outcome.code}"
    }

private fun outcomeOf(label: String): AttemptOutcome =
    when {
        label == "SUCCEEDED" -> AttemptOutcome.Succeeded
        label == "EMPTY" -> AttemptOutcome.Empty
        label.startsWith("FAILED:") -> AttemptOutcome.Failed(label.removePrefix("FAILED:"))
        else -> throw IllegalArgumentException("시도 원장의 결말 어휘가 아니다")
    }

internal const val RUN_LOCK_NAME = "run.lock"

/** 장부가 지키는 정본 파일 — 디렉터리에 이 밖의 파일이 있으면 기동이 거부된다(D-6G-60). */
private val LEDGERED_FILES = listOf(SAMPLE_LIST_NAME, SAMPLE_SCOPE_NAME, ATTEMPT_LEDGER_NAME)

/**
 * 실행 상태 디렉터리의 **잠금**(D-6G-57) — 상태가 파일 범위이므로 잠금도 파일 범위다.
 *
 * r3 은 DB advisory lock 을 썼고 개찰 갈래에만 걸었다. 공고 목록 갈래가 같은 원장을 쓰게 된 뒤로는
 * 두 갈래가 나란히 seed 한 뒤 **남은 상한을 각자 다 쓰는** 길이 열려 있었다(vr r4 H-2, 일 상한 10 에
 * HTTP 줄 20). 범위가 다른 두 자물쇠는 같은 것을 지키지 못한다 — 상태가 있는 자리에 건다.
 *
 * `FileChannel.tryLock` 은 **프로세스 범위**다: 프로세스가 죽으면 OS 가 놓는다(잠금 행을 표에 두면
 * 죽은 실행이 그것을 들고 남는다). 얻지 못하는 것은 오류가 아니라 정상적인 답이다.
 */
sealed interface RunStateLock {
    /** 놓는다 — 얻지 못한 잠금을 놓는 것은 아무 일도 아니다(호출부가 갈래를 나누지 않게). */
    fun release()

    class Held(
        private val channel: FileChannel,
        private val lock: FileLock,
    ) : RunStateLock {
        override fun release() {
            if (lock.isValid) lock.release()
            channel.close()
        }
    }

    data object Busy : RunStateLock {
        override fun release() = Unit
    }

    companion object {
        /**
         * 디렉터리를 **여는 자리가 곧 잠금 자리**다 — 잠그지 않고 원장을 얻는 길을 두지 않는다.
         * 잠금 파일은 상태가 아니라 자물쇠라 무결성 장부의 대상이 아니다(§장부 집합에서 뺀다).
         */
        fun tryAcquire(root: Path): RunStateLock {
            val channel =
                FileChannel.open(
                    root.resolve(RUN_LOCK_NAME),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            val lock = runCatching { channel.tryLock() }.getOrNull()
            return if (lock == null) {
                channel.close()
                Busy
            } else {
                Held(channel, lock)
            }
        }
    }
}
