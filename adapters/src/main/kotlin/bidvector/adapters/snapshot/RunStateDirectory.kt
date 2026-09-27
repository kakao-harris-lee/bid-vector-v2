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
import java.nio.file.StandardOpenOption
import java.time.Instant

internal const val SAMPLE_LIST_NAME = "sample-list.tsv"
internal const val ATTEMPT_LEDGER_NAME = "attempts.jsonl"
internal const val STATE_NAME = "state.json"

private const val ATTEMPT_MAX_DEPTH = 4

/**
 * 실험 실행 상태 디렉터리(D-6G-45) — 저장소 **밖**에 있고 파일 셋을 담는다.
 *
 * | 파일 | 무엇 |
 * |---|---|
 * | `sample-list.tsv` | 확정된 표본(D-6G-39) |
 * | `attempts.jsonl` | 시도 원장 — append-only |
 * | `state.json` | 표본 목록 바이트의 sha256 |
 *
 * **이 클래스는 디렉터리를 만들지 않는다.** 없으면 거부한다. 경로 오타 하나로 빈 디렉터리가 생기면
 * 승인 상한이 조용히 0 에서 시작하고 표본이 다시 뽑힌다 — 둘 다 실 호출이 나간 뒤에야 드러난다.
 * 디렉터리를 만드는 것은 운영자의 명시 행위여야 한다.
 *
 * 표본 목록이 있는데 `state.json` 이 없거나 해시가 어긋나면 **기동을 거부**한다. 바깥에서 목록을
 * 바꿔치우면 「결과를 보기 전에 확정했다」가 거짓이 되는데, 그것은 파일 안을 봐서는 알 수 없다.
 */
class RunStateDirectory(
    private val root: Path,
) {
    init {
        require(Files.isDirectory(root)) {
            "실행 상태 디렉터리가 없다 — 만들지 않는다(경로를 잘못 대면 상한이 0 에서 시작한다)"
        }
        verifySampleListIntegrity()
    }

    val sampleList: FileSampleListLedger = FileSampleListLedger(root.resolve(SAMPLE_LIST_NAME), ::recordState)

    val attempts: AttemptLedger = FileAttemptLedger(root.resolve(ATTEMPT_LEDGER_NAME))

    /** 추출이 읽는다 — 목록과 그 **바이트**(manifest 해시·곁파일 복사). */
    fun confirmedSampleList(): ConfirmedSampleList? = sampleList.read()

    private fun recordState(text: String) {
        val state = SnapshotJson.Obj(listOf("sample_list_sha256" to SnapshotJson.Text(sha256Hex(text))))
        Files.writeString(root.resolve(STATE_NAME), state.render() + "\n")
    }

    private fun declaredSha256(state: String): String? =
        KonepsJsonParser
            .parse(state, ATTEMPT_MAX_DEPTH)
            .asObject()
            ?.fields
            ?.get("sample_list_sha256")
            .asStringOrNull()

    private fun verifySampleListIntegrity() {
        val listFile = root.resolve(SAMPLE_LIST_NAME)
        if (!Files.isRegularFile(listFile)) return
        val declared =
            runCatching { Files.readString(root.resolve(STATE_NAME)) }
                .getOrNull()
                ?.let(::declaredSha256)
        require(declared == sha256Hex(Files.readString(listFile))) {
            "표본 목록이 확정된 뒤에 바뀌었다 — 결과를 보기 전에 확정했다는 것이 더는 참이 아니다"
        }
    }
}

/**
 * 시도 원장의 파일 구현 — 줄마다 한 시도, JSON 하나. **덧붙이기만** 한다(고쳐 쓰지 않는다).
 *
 * 줄이 형태를 어기면 읽지 않는다 — 시도 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고, 그것은
 * 상한이 없는 것보다 나쁘다(있다고 믿게 된다).
 */
class FileAttemptLedger(
    private val file: Path,
) : AttemptLedger {
    override fun append(attempt: CollectionAttempt) {
        Files.writeString(file, lineOf(attempt), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
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
