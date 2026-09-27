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
    private val attemptFile = root.resolve(ATTEMPT_LEDGER_NAME)

    init {
        require(Files.isDirectory(root)) {
            "실행 상태 디렉터리가 없다 — 만들지 않는다(경로를 잘못 대면 상한이 0 에서 시작한다)"
        }
        verifyIntegrity()
    }

    private var directoryId: String =
        readFacts()?.directoryId ?: java.util.UUID
            .randomUUID()
            .toString()

    val sampleList: FileSampleListLedger = FileSampleListLedger(sampleFile) { recordState() }

    val attempts: AttemptLedger = FileAttemptLedger(attemptFile) { recordState() }

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
                    "attempts_sha256" to SnapshotJson.Text(digestOf(attemptFile)),
                    "attempt_lines" to SnapshotJson.Number(lineCountOf(attemptFile).toString()),
                ),
            )
        val staged = root.resolve("$STATE_NAME.staged")
        Files.writeString(staged, facts.render() + "\n")
        Files.move(staged, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
        require(facts.attemptsSha256 == digestOf(attemptFile)) {
            "시도 원장이 바뀌었다 — 승인 상한이 센 것과 파일이 말하는 것이 다르다"
        }
        require(facts.attemptLines == lineCountOf(attemptFile)) {
            "시도 원장의 줄 수가 장부와 다르다 — 지워졌거나 잘렸다"
        }
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
            attemptsSha256 = requireNotNull(fields["attempts_sha256"].asStringOrNull()) { "장부에 원장 해시가 없다" },
            attemptLines = requireNotNull(fields["attempt_lines"].asIntOrNull()) { "장부에 원장 줄 수가 없다" },
        )
    }
}

/** 없는 파일은 빈 바이트로 본다 — 「아직 없다」와 「비었다」를 장부가 같게 다룬다. */
private fun digestOf(file: Path): String = sha256Hex(runCatching { Files.readString(file) }.getOrDefault(""))

private fun lineCountOf(file: Path): Int =
    runCatching { Files.readString(file) }
        .getOrDefault("")
        .lineSequence()
        .count { it.isNotBlank() }

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
                    "http_attempts" to SnapshotJson.Number(attempt.httpAttempts.toString()),
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
            httpAttempts = requireNotNull(fields["http_attempts"].asIntOrNull()) { "시도 원장에 시도 수가 없다" },
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
