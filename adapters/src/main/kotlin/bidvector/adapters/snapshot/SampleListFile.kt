package bidvector.adapters.snapshot

import bidvector.adapters.koneps.JsonValue
import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.adapters.koneps.asArray
import bidvector.adapters.koneps.asIntOrNull
import bidvector.adapters.koneps.asObject
import bidvector.adapters.koneps.asStringOrNull
import bidvector.procurement.BusinessDivision
import bidvector.workflow.collection.NOTICE_KEY_ORDER
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleConfirmation
import bidvector.workflow.collection.SampleList
import bidvector.workflow.collection.SampleListLedger
import bidvector.workflow.collection.SampleOutcome
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.collection.SampleStratum
import bidvector.workflow.collection.StratumOutcome
import bidvector.workflow.collection.sha256Hex
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate

private const val COLUMN_SEPARATOR = '\t'
private const val COLUMNS = 3

/**
 * 표본 목록 파일(D-6G-39) — **표본은 한 번 확정하고 영속한다.**
 *
 * r1 은 실행마다 표본틀을 다시 걷고 다시 뽑았다. 늦게 개찰된 공고가 창에 들어오거나 한 슬롯이 실패하면
 * **표본 자체가 달라져서**, 「결과를 보기 전에 확정한다」(우회 ⑦)가 실행 단위로만 성립했다. 3~4일에
 * 걸친 수집에서 그것은 성립하지 않는 것과 같다.
 *
 * 형태는 줄 단위 텍스트다: `공고키해시 <TAB> 업무 <TAB> 공고주`, **해시 오름차순**. 같은 표본이면 같은
 * 바이트이고, `manifest.sample_list_sha256` 은 **이 파일의 sha256** 이다(행에서 역산한 값이 아니다 —
 * 역산은 「파일이 곧 표본」이라는 성질을 검사하지 못한다).
 */
object SampleListFile {
    fun render(sample: SampleOutcome): String {
        val stratumOf = sample.strataByKey
        return sample.selected
            .sortedWith(NOTICE_KEY_ORDER)
            .joinToString("") { key ->
                val stratum = stratumOf.getValue(key)
                "${key.value}$COLUMN_SEPARATOR${stratum.division.name}$COLUMN_SEPARATOR${stratum.noticeWeek}\n"
            }
    }

    /** 줄이 형태를 어기면 **읽지 않는다** — 표본을 반쯤 읽는 것이 다시 뽑는 것보다 나쁘다. */
    fun parse(
        text: String,
        scope: SampleScope,
    ): SampleList {
        val entries =
            text
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(COLUMN_SEPARATOR)
                    require(parts.size == COLUMNS) { "표본 목록 줄의 칸 수가 ${COLUMNS}이 아니다" }
                    val division =
                        requireNotNull(BusinessDivision.entries.firstOrNull { it.name == parts[1] }) {
                            "표본 목록의 업무 어휘가 아니다"
                        }
                    NoticeKeyHash.ofHex(parts[0]) to SampleStratum(division, parts[2])
                }.toList()
        require(entries.isNotEmpty()) { "표본 목록이 비어 있다" }
        return SampleList(entries.toMap(), scope = scope)
    }
}

/**
 * 확정된 표본의 파일 표현 — 목록과 **바이트**를 함께 나른다. `manifest.sample_list_sha256` 은 이
 * 바이트의 해시이고, 추출은 이 바이트를 그대로 스냅숏 곁에 복사한다(다시 렌더링하면 해시 동일성이
 * 렌더러의 성질에 기대게 된다).
 */
class ConfirmedSampleList(
    val text: String,
    val list: SampleList,
) {
    val sha256: String = sha256Hex(text)
    val size: Int = list.keys.size
}

/**
 * 표본 목록 파일 원장(D-6G-39) — 저장소 **밖** 경로 하나에 한 번 쓰고, 그 뒤로는 읽기만 한다.
 *
 * 확정은 `CREATE_NEW` 다. 「있으면 쓰지 않는다」를 먼저 존재를 묻고 나중에 쓰는 두 걸음으로 두면 두
 * 실행이 동시에 들어올 때 뒤엣것이 앞엣것을 덮는다 — 파일시스템의 원자적 생성에 맡기고, 졌으면 이미
 * 놓인 것을 읽는다.
 */
class FileSampleListLedger(
    private val file: Path,
    /** 확정 바이트를 실행 상태에 못 박는다(D-6G-45) — 바깥에서 목록을 바꿔치우면 드러난다. */
    private val onConfirmed: (String) -> Unit = {},
) : SampleListLedger {
    private val scopeFile: Path = file.resolveSibling(SAMPLE_SCOPE_NAME)

    override fun confirmed(): SampleList? = read()?.list

    override fun confirm(confirmation: SampleConfirmation): SampleList {
        val text = SampleListFile.render(confirmation.sample)
        val won =
            try {
                Files.writeString(file, text, StandardOpenOption.CREATE_NEW)
                true
            } catch (_: FileAlreadyExistsException) {
                // 경쟁에서 졌다 — 제어 흐름이 아니라 원자적 생성의 결과 판독이다.
                false
            }
        if (!won) return requireNotNull(confirmed()) { "표본 목록 파일을 읽지 못했다" }
        Files.writeString(scopeFile, SampleScopeFile.render(confirmation))
        onConfirmed(text)
        val facts = SampleScopeFile.parse(Files.readString(scopeFile))
        return SampleListFile.parse(text, facts.scope).withFacts(facts)
    }

    /** 확정된 파일의 바이트까지 — 추출이 manifest 해시와 곁파일 복사에 쓴다. */
    fun read(): ConfirmedSampleList? {
        if (!Files.isRegularFile(file)) return null
        val text = Files.readString(file)
        // 목록이 있는데 범위가 없으면 거부한다(D-6G-50) — 범위 없는 목록은 대조가 조용히 꺼진다.
        require(Files.isRegularFile(scopeFile)) { "확정 표본의 표본틀 범위 파일이 없다" }
        val facts = SampleScopeFile.parse(Files.readString(scopeFile))
        return ConfirmedSampleList(text, SampleListFile.parse(text, facts.scope).withFacts(facts))
    }
}

internal const val SAMPLE_SCOPE_NAME = "sample-scope.json"

/**
 * 확정 표본의 곁 사실(D-6G-50) — 표본틀 **범위**와 **층별 목표**. TSV 는 레인 간 계약이라 칸을 늘릴
 * 수 없어(스키마 §2.1) 같은 디렉터리의 파일 하나로 뺀다. 이것이 없으면 범위 대조가 조용히 꺼지고
 * 「모자란 층」 보고가 사라지므로, 목록이 있는데 이 파일이 없으면 판독이 거부한다.
 */
internal object SampleScopeFile {
    fun render(confirmation: SampleConfirmation): String {
        val scope = confirmation.scope
        val strata =
            confirmation.sample.strata.entries.map { (stratum, outcome) ->
                SnapshotJson.Obj(
                    listOf(
                        "division" to SnapshotJson.Text(stratum.division.name),
                        "week" to SnapshotJson.Text(stratum.noticeWeek),
                        "target" to SnapshotJson.Number(outcome.target.toString()),
                        "available" to SnapshotJson.Number(outcome.available.toString()),
                        "taken" to SnapshotJson.Number(outcome.taken.toString()),
                    ),
                )
            }
        return SnapshotJson
            .Obj(
                listOf(
                    "from" to SnapshotJson.Text(scope.from.toString()),
                    "to" to SnapshotJson.Text(scope.to.toString()),
                    "divisions" to SnapshotJson.Arr(scope.divisions.map { SnapshotJson.Text(it.name) }),
                    "requested" to SnapshotJson.Number(confirmation.sample.requested.toString()),
                    "strata" to SnapshotJson.Arr(strata),
                ),
            ).render() + "\n"
    }

    fun parse(text: String): SampleList {
        val fields =
            requireNotNull(KonepsJsonParser.parse(text, SCOPE_MAX_DEPTH).asObject()?.fields) {
                "표본틀 범위 파일이 JSON 객체가 아니다"
            }
        val scope =
            SampleScope(
                from = LocalDate.parse(requireNotNull(fields["from"].asStringOrNull()) { "범위에 시작일이 없다" }),
                to = LocalDate.parse(requireNotNull(fields["to"].asStringOrNull()) { "범위에 종료일이 없다" }),
                divisions = divisionsOf(fields["divisions"]),
            )
        return SampleList(emptyMap(), strataOf(fields["strata"]), requestedOf(fields), scope)
    }

    private fun requestedOf(fields: Map<String, JsonValue>): Int =
        requireNotNull(fields["requested"].asIntOrNull()) { "범위에 목표 표본 수가 없다" }

    private fun divisionsOf(value: JsonValue?): Set<BusinessDivision> =
        requireNotNull(value.asArray()) { "범위의 업무 집합이 배열이 아니다" }
            .items
            .map { item ->
                val name = item.asStringOrNull()
                requireNotNull(BusinessDivision.entries.firstOrNull { it.name == name }) { "범위의 업무 어휘가 아니다" }
            }.toSet()

    private fun strataOf(value: JsonValue?): Map<SampleStratum, StratumOutcome> =
        requireNotNull(value.asArray()) { "범위의 층 목록이 배열이 아니다" }
            .items
            .associate { item ->
                val row = requireNotNull(item.asObject()?.fields) { "층 항목이 객체가 아니다" }
                val divisionName = row["division"].asStringOrNull()
                val division =
                    requireNotNull(BusinessDivision.entries.firstOrNull { it.name == divisionName }) {
                        "층의 업무 어휘가 아니다"
                    }
                val stratum = SampleStratum(division, requireNotNull(row["week"].asStringOrNull()) { "층에 주가 없다" })
                stratum to
                    StratumOutcome(
                        target = requireNotNull(row["target"].asIntOrNull()) { "층에 목표가 없다" },
                        available = requireNotNull(row["available"].asIntOrNull()) { "층에 후보 수가 없다" },
                        taken = requireNotNull(row["taken"].asIntOrNull()) { "층에 뽑힌 수가 없다" },
                    )
            }
}

private const val SCOPE_MAX_DEPTH = 6

/** 목록(키·층)에 곁 사실(범위·목표·층별 결과)을 얹는다 — 둘은 같은 확정의 두 조각이다. */
private fun SampleList.withFacts(facts: SampleList): SampleList =
    copy(strata = facts.strata, requested = facts.requested)
