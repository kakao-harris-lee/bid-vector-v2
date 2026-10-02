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
     * 덧붙임 채널의 출처 — 출하는 파일 채널이고, **순서를 재는 test 가 대역을 끼운다**(D-6G2d-41).
     * 기본값을 두는 이유는 이 인자가 배선의 선택이 아니라 test 의 관측 자리이기 때문이다.
     */
    private val channels: (Path) -> DurableAppend = ::appendChannel,
    /**
     * 줄을 쓸 때마다 무결성 장부를 갱신한다(D-6G-48) — 기록과 장부가 갈리는 창을 남기지 않는다.
     * **기본값이 없다**(vr r4 L-10): 장부를 갱신하지 않는 append 는 그 디렉터리를 다음 기동에서
     * 막아 버리고, 그것을 쉽게 만드는 기본값은 이 타입이 주는 편의가 아니라 함정이다.
     */
    private val onAppended: (String) -> Unit,
) : AttemptLedger {
    /**
     * 한 줄을 덧붙이고 **그 바이트를 굳힌 뒤** 장부 갱신을 부른다(D-6G2d-41).
     *
     * 순서가 뒤집히면 정전 뒤에 장부는 이 줄을 세는데 원장에는 없다 — 다음 기동이 「줄 수가 장부보다
     * 적다」로 **영구 거부**하고 출구는 디렉터리 폐기(= 상한 0 재시작)다. 반대 순서의 손해는 「굳은
     * 줄을 장부가 아직 모른다」이고, 그것은 다음 기동이 장부를 원장 쪽으로 재동기해 흡수한다.
     */
    override fun append(attempt: CollectionAttempt) {
        val line = lineOf(attempt)
        channels(file).use { channel ->
            channel.append(line)
            channel.force()
        }
        onAppended(line)
    }

    /**
     * 줄 순서를 **그대로** 지킨다 — 열린 라운드와 재호출 상한은 덧붙인 순서로 읽는다(D-6G-58).
     * 그 밖의 줄이 형태를 어기면 여전히 멈춘다: 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고,
     * 그것은 상한이 없는 것보다 나쁘다.
     */
    override fun read(): AttemptHistory {
        if (!Files.isRegularFile(file)) return AttemptHistory(emptyList())
        val attempts = mutableListOf<CollectionAttempt>()
        var lostCalls = 0
        Files
            .readString(file)
            .lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val fragment = tornFragmentOf(line)
                when (val recovered = fragment?.let(::recoveredCall)) {
                    null -> if (fragment == null) attempts += parseLine(line) else lostCalls++
                    else -> attempts += recovered
                }
            }
        return AttemptHistory(attempts, tornLines = lostCalls)
    }

    /** 표식이면 그 조각(값이 문자열이 아니면 빈 조각), 표식이 아니면 `null`. */
    private fun tornFragmentOf(line: String): String? {
        val fields = runCatching { fieldsOf(line) }.getOrNull()
        return fields?.get(TORN_KEY).asStringOrNull().orEmpty().takeIf { fields?.containsKey(TORN_KEY) == true }
    }

    /**
     * **조각도 열린 라운드의 증거다**(D-6G2e-3) — 조각에서 (공고, 축, 시각)이 읽히면 그 라운드를
     * **의도 줄**로 되살린다. 그러면 세 물음이 한 자리에서 맞는다: 예산은 이미 조각을 호출 하나로
     * 셌고([AttemptHistory.spend] 의 `tornLines`), 열린 라운드는 꼬리의 의도 줄로 닫히고, 재호출
     * 상한이 그 라운드를 하나로 센다. 앞 판은 조각을 **세기만** 해서 같은 축의 반복 크래시가
     * 재호출 상한을 올리지 못했다(예산 상한만이 막았다 — 두 장부의 가정이 갈렸다).
     *
     * **결말로 되살리지 않는다.** 조각이 결말 줄의 접두사였더라도 그 결말은 굳지 않았고, 「끝났다」로
     * 읽으면 받지 못한 축이 완료가 된다. 의도 줄은 **상한이 줄지 않는 쪽**이다: 라운드가 열린 채
     * 남아 재개하는 쪽이 `AXIS Failed` 로 닫는다.
     *
     * 읽히지 않는 조각은 `null` 이다 — **축을 지어내지 않는다**(D-6G-70 그대로, 호출 하나로만 센다).
     */
    private fun recoveredCall(fragment: String): CollectionAttempt? {
        val fields = truncatedFieldsOf(fragment).orEmpty()
        val axis = SourceEndpoint.entries.firstOrNull { it.name == fields["axis"].asStringOrNull() }
        val at = fields["at"].asStringOrNull()?.let { runCatching { Instant.parse(it) }.getOrNull() }
        // 공고 키가 읽히지 않으면 되살리지 않는다 — `null` 로 두면 공고 축 라운드가 목록 축 줄로
        // 보이고(공고 단위 묶기에서 빠진다) 그 라운드는 열린 채로 남지 못한다.
        val hash =
            fields["notice_key_hash"]
                .asStringOrNull()
                ?.let { runCatching { NoticeKeyHash.ofHex(it).value }.getOrNull() }
        return if (axis == null || at == null || hash == null) {
            null
        } else {
            CollectionAttempt(
                noticeKey = hash,
                axis = axis,
                // 결말 없는 의도 줄의 자리표시다 — 상한은 `kind` 로 센다([parseLine] 과 같은 규율).
                outcome = AttemptOutcome.Succeeded,
                at = at,
                kind = AttemptKind.PENDING,
                walk = null,
            )
        }
    }

    /**
     * 조각을 **마지막 온전한 칸까지** 닫아 읽는다. 줄 형태가 `at`·`axis`·`notice_key_hash` 를 앞에
     * 싣기 때문에([lineOf]) 조각이 거기까지 닿았으면 그 셋이 들어 있다. 쉼표 자리를 뒤에서부터 끊어
     * **원장의 판독기 그대로** 파싱한다 — 문자열을 긁어 값을 짓지 않는다(값 어휘·형태 검사가 한
     * 자리에 남는다). 어느 자리에서도 객체가 서지 않으면 읽히지 않는 조각이다.
     */
    private fun truncatedFieldsOf(fragment: String): Map<String, JsonValue>? =
        closedCandidatesOf(fragment).firstNotNullOfOrNull { runCatching { fieldsOf(it) }.getOrNull() }

    /**
     * 객체로 **닫은 후보들** — 조각 그대로(개행만 빠진 온전한 줄), 그리고 쉼표 자리를 뒤에서부터
     * 하나씩 끊은 것들. 값 안에 쉼표가 든 자리에서는 파싱이 서지 않고 다음(더 앞) 자리로 물러선다.
     */
    private fun closedCandidatesOf(fragment: String): Sequence<String> =
        if (!fragment.startsWith("{")) {
            emptySequence()
        } else {
            sequenceOf(fragment) +
                generateSequence(fragment.length) { fragment.lastIndexOf(',', it - 1).takeIf { cut -> cut > 0 } }
                    .drop(1)
                    .map { fragment.take(it) + "}" }
        }

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
                    // 걷기 식별자(D-6G-68) — AXIS 줄만 갖는다. 키는 **언제나** 싣고 값만 `null` 이다
                    // (cr r5-t L-2 — 앞 주석은 사실이 아니었다). 그 덕에 판독이 「이 코드가 쓴 줄」과
                    // 「칸이 생기기 전에 쓰인 줄」을 가릴 수 있다: AXIS 줄의 값이 `null` 이면 옛 줄이다.
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
        val kind =
            requireNotNull(AttemptKind.entries.firstOrNull { it.name == fields["kind"].asStringOrNull() }) {
                "시도 원장의 줄 갈래 어휘가 아니다"
            }
        val walk = fields["walk"]?.asStringOrNull()?.let(Instant::parse)
        // **옛 줄은 형식으로 거부한다**(D-6G2d-44). 걷기 없는 AXIS 줄은 이 칸이 생기기 전의 줄이고,
        // 값 타입의 generic `require` 로 죽으면 운영자 출력에서 「손상」과 구별되지 않는다. 그 구별이
        // 처방을 가른다: 옛 디렉터리는 폐기해도 되고, 손상은 사람이 봐야 한다.
        if (kind == AttemptKind.AXIS && walk == null) {
            throw RunStateFormatRefusedException(RunStateFormatFault.LEGACY_LINE)
        }
        return CollectionAttempt(
            // 형태 검사는 값 타입이 진다 — 원장에는 이미 지어진 hex 가 실린다.
            noticeKey = fields["notice_key_hash"]?.asStringOrNull()?.let { NoticeKeyHash.ofHex(it).value },
            axis = axis,
            // 결말 없는 의도 줄은 값이 아니라 **자리표시**다 — 상한은 `kind` 로 센다.
            outcome = fields["outcome"].asStringOrNull()?.let(::outcomeOf) ?: AttemptOutcome.Succeeded,
            at = Instant.parse(requireNotNull(fields["at"].asStringOrNull()) { "시도 원장에 시각이 없다" }),
            kind = kind,
            // 걷기를 **실은** 호출 줄은 옛 형식이 아니라 형태 위반이다 — 이 코드는 그런 줄을 쓴 적이
            // 없으므로 값 타입의 양방향 `require`(D-6G2d-4 ⓒ)가 그 자리에서 막는다.
            walk = walk,
        )
    }
}

/**
 * 결말 어휘 — 오류는 코드를 뒤에 붙인다(`FAILED:<코드>` · `FINAL:<코드>`). 값이 아니라 분류만 싣는다.
 *
 * **다시 불러 볼 값이 있는 실패와 없는 실패를 원장이 가른다**(D-6G2d-8 ⓒ). 코드에서 되읽어 분류하지
 * 않는다: 그러면 사유 어휘가 늘 때마다 판독 쪽에 같은 표가 한 벌 더 생기고 두 표가 갈린다. 분류는
 * 절단 사유를 손에 든 자리(`attemptOutcomeOf`)가 한 번 하고, 줄이 그 답을 나른다.
 */
private fun labelOf(outcome: AttemptOutcome): String =
    when (outcome) {
        AttemptOutcome.Succeeded -> "SUCCEEDED"
        AttemptOutcome.Empty -> "EMPTY"
        is AttemptOutcome.Failed -> "$RETRYABLE_PREFIX${outcome.code}"
        is AttemptOutcome.FinalFailure -> "$FINAL_PREFIX${outcome.code}"
        is AttemptOutcome.Refused -> "$REFUSED_PREFIX${outcome.code}"
    }

private fun outcomeOf(label: String): AttemptOutcome =
    when {
        label == "SUCCEEDED" -> AttemptOutcome.Succeeded
        label == "EMPTY" -> AttemptOutcome.Empty
        label.startsWith(RETRYABLE_PREFIX) -> AttemptOutcome.Failed(label.removePrefix(RETRYABLE_PREFIX))
        label.startsWith(FINAL_PREFIX) -> AttemptOutcome.FinalFailure(label.removePrefix(FINAL_PREFIX))
        label.startsWith(REFUSED_PREFIX) -> AttemptOutcome.Refused(label.removePrefix(REFUSED_PREFIX))
        else -> throw IllegalArgumentException("시도 원장의 결말 어휘가 아니다")
    }

private const val RETRYABLE_PREFIX = "FAILED:"

private const val FINAL_PREFIX = "FINAL:"

/** 관문 거부 — 호출이 나가지 않았다(D-6G2d-16). 상한 셈 밖이라는 것을 줄이 스스로 말한다. */
private const val REFUSED_PREFIX = "REFUSED:"
