package bidvector.adapters.snapshot

import bidvector.adapters.koneps.JsonValue
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
import bidvector.workflow.collection.hexOf
import bidvector.workflow.collection.sha256Hex
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant

internal const val SAMPLE_LIST_NAME = "sample-list.tsv"
internal const val ATTEMPT_LEDGER_NAME = "attempts.jsonl"
internal const val STATE_NAME = "state.json"

/** 원자적 교체의 중간 이름 — 장부 집합 등식에서 빼는 **유일한** 이름이다(cr r5 L-2). */
internal const val STAGED_STATE_NAME = "$STATE_NAME.staged"

/**
 * 원장 복구의 원자적 교안 중간 이름(D-6G2d-2) — 장부 교안과 같은 모양이다. 장부 대상
 * 집합에서 빼는 이름은 이제 둘이고, 둘 다 이 클래스가 직접 쓰는 이름이다(`*.staged` 통째 제외 아니다).
 */
internal const val STAGED_ATTEMPT_NAME = "$ATTEMPT_LEDGER_NAME.staged"

/** 원장·장부 줄의 JSON 깊이 상한 — 두 파일이 같은 값을 쓴다(줄 형태가 같다). */
internal const val ATTEMPT_MAX_DEPTH = 4

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

    /**
     * **이 자리의 표식**(cr r4 M-3) — 디렉터리의 절대 경로 해시다. 앞 판은 임의의 UUID 를 적고 아무
     * 데서도 대조하지 않아 죽은 칸이었고, 「다른 디렉터리로의 복사가 모두 거부된다」는 참이 아니었다.
     * 자리에 묶으면 파일 셋을 통째로 복사해 상한을 0 에서 다시 시작하는 길이 닫힌다(넷이 서로 맞아도
     * 표식이 그 자리의 것이 아니다).
     *
     * **실경로**로 짓는다(vr r5 L-5) — `toAbsolutePath().normalize()` 는 심링크를 풀지 않아, 같은
     * 디렉터리를 심링크로 가리킨 **정직한** 기동이 「다른 자리의 표식」으로 거부됐다.
     */
    private val directoryId: String = sha256Hex(realPathOf(root))

    /**
     * 장부 파일의 판독([RunStateFactsFile]) — **누적 해시 초기화식보다 앞에 선다.** 복구·되돌림이 그
     * 식 안에서 돌고 되돌림이 장부를 읽으므로, 뒤에 두면 그 읽기가 `null` 을 부른다(D-6G2d-1 과 같은
     * 계열의 함정 — 프로퍼티 초기화는 선언 순서다).
     */
    private val factsFile = RunStateFactsFile(stateFile)

    /**
     * 원장의 **누적** 해시와 줄 수 — append 마다 파일 전체를 다시 읽지 않는다(cr r4 M-6).
     *
     * **복구가 끝난 바이트로 짓는다**(D-6G2d-1). 프로퍼티 초기화는 선언 순서라, 이 자리에서 곧바로
     * `LedgerDigest(attemptFile)` 을 부르면 [healTornTail] 보다 **앞서** 돈다 — 장부에 조각까지 포함한
     * 해시가 굳고, 그 뒤의 교체가 다음 기동에서 「앞부분이 다르다」로 읽힌다(vr r5-t probe C2: 기동 A
     * 만 수락하고 B·C 는 영구 거부). 복구를 이 초기화식 자신 안에 두어, 새 상태가 이 식 앞에 끼어들
     * 자리를 남기지 않는다.
     */
    private val ledger: LedgerDigest = healedLedgerDigest()

    init {
        // **형식 판별은 잠금과 무관하다**(D-6G2d-44) — 잠금을 못 잡아도 옛 디렉터리는 「형식」으로
        // 거부돼야 한다. 잠금 뒤로 미루면 다른 실행이 도는 동안 열린 옛 디렉터리가 generic 파싱
        // 오류로 죽고, 운영자는 무엇이 틀렸는지 출력에서 읽을 수 없다.
        factsFile.requireReadableFormat()
        if (lock is RunStateLock.Held) heldOrRelease { verifyIntegrity() }
    }

    /**
     * 복구·되돌림을 먼저 끝낸 뒤 **그 바이트**로 누적 해시를 짓는다. 무결성 대조는 그다음이다 —
     * 장부를 재동기하는 해시([requireLedgerAheadOrEqual])가 디스크에 없는 바이트를 가리킬 수 없다.
     */
    private fun healedLedgerDigest(): LedgerDigest {
        if (lock is RunStateLock.Held) {
            heldOrRelease {
                rollBackInterruptedConfirmation()
                healTornTail()
            }
        }
        return LedgerDigest(attemptFile)
    }

    /** 기동 거부로 끝나도 **잠금은 놓는다** — 들고 죽은 잠금은 다음 실행을 막는다. */
    private fun heldOrRelease(body: () -> Unit) {
        runCatching(body).onFailure {
            lock.release()
            throw it
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
            is RunStateLock.Held -> {
                FileAttemptLedger(attemptFile) { line ->
                    ledger.append(line)
                    recordState()
                }
            }

            RunStateLock.Busy -> {
                LockedOutAttemptLedger(FileAttemptLedger(attemptFile) {})
            }
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
                    "format_version" to SnapshotJson.Number(RUN_STATE_FORMAT_VERSION.toString()),
                    "directory_id" to SnapshotJson.Text(directoryId),
                    "sample_list_sha256" to SnapshotJson.Text(digestOf(sampleFile)),
                    "sample_scope_sha256" to SnapshotJson.Text(digestOf(scopeFile)),
                    "attempts_sha256" to SnapshotJson.Text(ledger.hex()),
                    "attempt_lines" to SnapshotJson.Number(ledger.lines.toString()),
                ),
            )
        replaceDurably(root.resolve(STAGED_STATE_NAME), stateFile, facts.render() + "\n", root)
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
        val facts = factsFile.read() ?: return
        if (facts.sampleListSha256 != EMPTY_DIGEST) return
        Files.deleteIfExists(sampleFile)
        Files.deleteIfExists(scopeFile)
    }

    /**
     * 기동 시 대조 — 넷 중 하나라도 어긋나면 거부한다. 장부가 아직 없으면(첫 실행) 검사할 것이
     * 없지만, **파일이 있는데 장부가 없으면** 거부다: 그것이 「장부만 지웠다」의 모양이다.
     */
    private fun verifyIntegrity() {
        val facts = factsFile.read()
        val hasFiles = Files.isRegularFile(sampleFile) || Files.isRegularFile(attemptFile)
        require(facts != null || !hasFiles) {
            "실행 상태 장부가 없는데 파일이 있다 — 무엇이 지워졌는지 알 수 없다"
        }
        if (facts == null) return
        require(facts.directoryId == directoryId) {
            "실행 상태 장부가 다른 자리에서 왔다 — 디렉터리가 복사되었거나 옮겨졌다"
        }
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
                // `*.staged` 를 통째로 빼면 `anything.staged` 가 장부 검사에 보이지 않는다
                // (cr r5 L-2). 이 클래스가 **직접 쓰는 이름**만 뺀다 — 장부 교체와 원장 복구 교체
                // 둘(D-6G2d-2). 복구 도중 죽어 남은 중간 파일이 「모르는 파일」로 기동을 막으면,
                // 그 사고를 만든 것은 복구 자신이다.
                .filterNot { it in EXCLUDED_FROM_LEDGERED_SET }
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

    /**
     * **찢어진 끝 줄을 닫는다**(D-6G-70). append 도중에 죽으면 원장은 개행 없이 끝난다. 그대로 두면
     * 다음 append 가 조각에 이어 붙어 **두 시도가 한 줄**이 되고, 그 줄은 영영 읽히지 않는다.
     *
     * 조각을 **버리지 않는다** — 그 호출은 실제로 나갔을 수 있다. 조각을 제 줄에서 떼어 `torn`
     * 표식 줄로 감싼다: 형태가 선 JSON 이라 뒤에 줄이 더 붙어도 읽기가 멈추지 않고, 원문 조각은
     * 사람이 볼 수 있게 남으며, 읽는 쪽이 그것을 **호출 하나**로 센다(상한이 줄지 않는 쪽).
     * 축을 **지어내지 않는다** — 조각이 무엇이었는지 모르는 채로 시도 줄을 만들면 이어 돌기가
     * 있지도 않은 축을 완료로 읽는다.
     *
     * 누적 해시보다 **먼저** 돈다 — 그 순서는 [healedLedgerDigest] 가 구조로 든다.
     *
     * 교체는 **원자적이고 내구적이다**(D-6G2d-2, cr r5-t M-3 · cr r4 ③). 제자리 truncate+rewrite 는
     * 8 만 줄짜리 원장을 다시 쓰는 도중에 또 죽으면 파일을 짧게 만들고, 다음 기동은 「줄 수가 장부보다
     * 적다」로 영구 거부한다 — 복구가 도는 순간은 방금 죽은 기계 위다. [replaceDurably] 가 그 순서를
     * 든다([recordState] 와 같은 형태다).
     */
    private fun healTornTail() {
        if (!Files.isRegularFile(attemptFile)) return
        val text = Files.readString(attemptFile)
        if (text.isEmpty() || text.endsWith("\n")) return
        val fragment = text.substringAfterLast('\n')
        val healed = text.removeSuffix(fragment) + tornMarkerOf(fragment)
        replaceDurably(root.resolve(STAGED_ATTEMPT_NAME), attemptFile, healed, root)
    }
}

/** 심링크를 푼 절대 경로 — 풀 수 없으면(경쟁 상태) 앞 규칙으로 물러선다. */
private fun realPathOf(root: Path): String =
    runCatching { root.toRealPath().toString() }.getOrElse { root.toAbsolutePath().normalize().toString() }

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
 * 원장의 누적 해시(cr r4 M-6) — append 마다 파일 전체를 다시 읽고 해시하면 호출 수의 제곱으로 는다.
 * 승인 상한이 8만 호출이면 그 비용이 수집 자체를 넘어선다. 원장은 append-only 라 누적이 성립한다.
 */
private class LedgerDigest(
    file: Path,
) {
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    var lines: Int = 0
        private set

    init {
        val text = runCatching { Files.readString(file) }.getOrDefault("")
        digest.update(text.toByteArray(Charsets.UTF_8))
        lines = text.lineSequence().count { it.isNotBlank() }
    }

    fun append(line: String) {
        digest.update(line.toByteArray(Charsets.UTF_8))
        lines++
    }

    /** 복제해서 뽑는다 — `digest()` 는 상태를 되돌리므로 원본을 쓰면 다음 줄부터 해시가 갈린다. */
    fun hex(): String = hexOf((digest.clone() as MessageDigest).digest())
}

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

/** 잃어버린 호출의 표식(D-6G-70) — 조각을 원문 그대로 담되 형태가 선 JSON 한 줄로. */
private fun tornMarkerOf(fragment: String): String =
    SnapshotJson.Obj(listOf(TORN_KEY to SnapshotJson.Text(fragment))).render() + "\n"

internal const val TORN_KEY = "torn"

private fun linesOf(file: Path): List<String> =
    runCatching { Files.readString(file) }
        .getOrDefault("")
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()

internal const val RUN_LOCK_NAME = "run.lock"

/** 장부가 지키는 정본 파일 — 디렉터리에 이 밖의 파일이 있으면 기동이 거부된다(D-6G-60). */
private val LEDGERED_FILES = listOf(SAMPLE_LIST_NAME, SAMPLE_SCOPE_NAME, ATTEMPT_LEDGER_NAME)

/**
 * 장부 대상 집합에서 빼는 이름 — 자물쇠와 장부 자신, 그리고 **원자 교체의 중간 이름 둘**이다
 * (D-6G2d-2). 이름을 여기 적지 않은 파일이 디렉터리에 생기면 기동이 거부된다(D-6G-60).
 */
private val EXCLUDED_FROM_LEDGERED_SET =
    setOf(STATE_NAME, RUN_LOCK_NAME, STAGED_STATE_NAME, STAGED_ATTEMPT_NAME)

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
