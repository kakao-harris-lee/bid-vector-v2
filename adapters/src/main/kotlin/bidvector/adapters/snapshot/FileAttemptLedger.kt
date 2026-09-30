package bidvector.adapters.snapshot

import bidvector.adapters.koneps.JsonValue
import bidvector.adapters.koneps.KonepsJsonParser
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

/**
 * 시도 원장의 **파일 구현** — 줄마다 한 시도, JSON 하나. **덧붙이기만** 한다(고쳐 쓰지 않는다).
 *
 * `RunStateDirectory` 에서 갈라낸 파일이다(sizeGate 500). 디렉터리가 지는 것은 잠금·무결성 장부·
 * 표본 확정이고, 여기가 지는 것은 **줄의 형태와 그 줄을 읽고 쓰는 규칙**이다 — 두 축은 따로 바뀐다.
 *
 * 줄이 형태를 어기면 읽지 않는다 — 시도 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고, 그것은
 * 상한이 없는 것보다 나쁘다(있다고 믿게 된다). 관용은 **찢어진 끝 줄** 하나뿐이다(D-6G-70).
 */
internal class FileAttemptLedger(
    private val file: Path,
    /**
     * 줄을 쓸 때마다 무결성 장부를 갱신한다(D-6G-48) — 기록과 장부가 갈리는 창을 남기지 않는다.
     * **기본값이 없다**(vr r4 L-10): 장부를 갱신하지 않는 append 는 그 디렉터리를 다음 기동에서
     * 막아 버리고, 그것을 쉽게 만드는 기본값은 이 타입이 주는 편의가 아니라 함정이다.
     */
    private val onAppended: (String) -> Unit,
) : AttemptLedger {
    override fun append(attempt: CollectionAttempt) {
        val line = lineOf(attempt)
        Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        onAppended(line)
    }

    override fun read(): AttemptHistory {
        if (!Files.isRegularFile(file)) return AttemptHistory(emptyList())
        val lines =
            Files
                .readString(file)
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
        // `torn` 표식 줄은 시도가 아니라 **잃어버린 호출**이다(D-6G-70) — 세기만 한다. 그 밖의
        // 줄이 형태를 어기면 여전히 멈춘다: 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고,
        // 그것은 상한이 없는 것보다 나쁘다.
        val (tornLines, attemptLines) = lines.partition(::isTornMarker)
        return AttemptHistory(attemptLines.map(::parseLine), tornLines = tornLines.size)
    }

    private fun isTornMarker(line: String): Boolean =
        runCatching { fieldsOf(line)?.containsKey(TORN_KEY) }.getOrNull() == true

    private fun fieldsOf(line: String): Map<String, JsonValue>? =
        KonepsJsonParser
            .parse(line, ATTEMPT_MAX_DEPTH)
            .asObject()
            ?.fields

    private fun lineOf(attempt: CollectionAttempt): String =
        SnapshotJson
            .Obj(
                listOf(
                    "at" to SnapshotJson.Text(attempt.at.toString()),
                    "axis" to SnapshotJson.Text(attempt.axis.name),
                    "notice_key_hash" to (attempt.noticeKey?.let { SnapshotJson.Text(it) } ?: SnapshotJson.Null),
                    // 의도 줄에는 결말이 **없다**(cr r5 L-3). 앞 판은 `SUCCEEDED` 를 적었고,
                    // `kind` 를 함께 보지 않는 판독자에게는 나가지도 않은 호출이 성공으로 보였다.
                    "outcome" to
                        if (attempt.kind == AttemptKind.PENDING) {
                            SnapshotJson.Null
                        } else {
                            SnapshotJson.Text(labelOf(attempt.outcome))
                        },
                    "kind" to SnapshotJson.Text(attempt.kind.name),
                    // 걷기 식별자(D-6G-68) — AXIS 줄만 갖는다. 없는 줄은 키 자체를 싣지 않는다.
                    "walk" to (attempt.walk?.let { SnapshotJson.Text(it.toString()) } ?: SnapshotJson.Null),
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
            // 결말 없는 의도 줄은 값이 아니라 **자리표시**다 — 상한은 `kind` 로 센다.
            outcome = fields["outcome"].asStringOrNull()?.let(::outcomeOf) ?: AttemptOutcome.Succeeded,
            at = Instant.parse(requireNotNull(fields["at"].asStringOrNull()) { "시도 원장에 시각이 없다" }),
            kind =
                requireNotNull(AttemptKind.entries.firstOrNull { it.name == fields["kind"].asStringOrNull() }) {
                    "시도 원장의 줄 갈래 어휘가 아니다"
                },
            // 이 칸 이전에 쓰인 원장은 값이 없다 — 그 축은 걷기를 모르므로 추출이 미완으로 센다.
            walk = fields["walk"]?.asStringOrNull()?.let(Instant::parse),
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
