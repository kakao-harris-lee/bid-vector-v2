package bidvector.buildlogic

import java.security.MessageDigest

/*
 * `LeakPatternGateTask`의 판정 로직 — 전부 순수 함수다(`ContractGateChecks.kt`와 같은 관례).
 * Task 는 파일을 읽어 값으로만 넘기고, 매치·baseline 비교·보고서 문구는 여기서 결정한다.
 * 이 분리가 `LeakPatternGateChecksTest`를 Gradle Task 인스턴스화 없이 돌게 한다.
 *
 * baseline 키(leak-baseline-coord, D-LBC-1) = `경로` + `#` + `정규화한 줄 내용의 SHA-256 hex`.
 * 줄 번호는 키에 없다 — 위쪽 편집으로 좌표가 밀려도 승인이 무효화되지 않는다(S-3). 경로는
 * 키에 남는다 — 내용만으로 키를 잡으면 한 파일에서 승인한 문자열이 모든 파일에서 승인된다.
 */

/** 정책 파일 줄 파싱 — 빈 줄·`#` 시작 줄은 무시하고 나머지는 대소문자 무시 정규식으로. */
internal fun parseLeakPatterns(lines: List<String>): List<Regex> =
    lines
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { Regex(it, RegexOption.IGNORE_CASE) }

/** baseline 파일 줄 파싱 — 정책 파일과 같은 필터(빈 줄·`#` 시작 줄 무시), 집합으로. */
internal fun parseLeakBaseline(lines: List<String>): Set<String> =
    lines
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()

/** 패턴에 매치한 한 줄 — 키 산출에 원문 내용이 필요해 좌표뿐 아니라 내용까지 들고 다닌다. */
internal data class LeakMatch(
    val path: String,
    val lineNumber: Int,
    val content: String,
) {
    /** 사람이 보러 갈 좌표 — baseline 에는 저장하지 않는다(D-LBC-3). */
    val coordinate: String get() = "$path:$lineNumber"
}

/** 한 파일에서 패턴에 매치하는 줄을 낸다 — 매치 판정 자체(어떤 줄이 매치인가)는 그대로다. */
internal fun leakMatchesInFile(
    relativePath: String,
    lines: List<String>,
    patterns: List<Regex>,
): List<LeakMatch> =
    lines
        .withIndex()
        .filter { (_, line) -> patterns.any { it.containsMatchIn(line) } }
        .map { (index, line) -> LeakMatch(relativePath, index + 1, line) }

private val INTERNAL_WHITESPACE = Regex("\\s+")

/** trim + 내부 공백 접기까지만 — 소문자화·구두점 제거는 서로 다른 줄을 같은 키로 접어 하지 않는다. */
internal fun normalizeLeakLineContent(content: String): String = content.trim().replace(INTERNAL_WHITESPACE, " ")

private const val LEAK_BASELINE_KEY_SEPARATOR = "#"

/** baseline 키 — `경로` + `#` + 정규화한 줄 내용의 SHA-256 hex. */
internal fun leakBaselineKey(
    path: String,
    content: String,
): String {
    val hash =
        MessageDigest
            .getInstance("SHA-256")
            .digest(normalizeLeakLineContent(content).toByteArray())
            .joinToString("") { "%02x".format(it) }
    return "$path$LEAK_BASELINE_KEY_SEPARATOR$hash"
}

private val LEGACY_BASELINE_KEY_PATTERN = Regex("^.+:\\d+$")

/** 옛 형식(`경로:줄번호`) 판별 — 새 키는 `#` 구분자를 쓰므로 이 패턴에 매치할 수 없다. */
internal fun isLegacyLeakBaselineKey(key: String): Boolean = LEGACY_BASELINE_KEY_PATTERN.matches(key)

/**
 * baseline 에 옛 형식 항목이 남아 있으면 사유를 담은 실패 메시지, 없으면 null(D-LBC-2 —
 * 조용히 stale 로 죽지 않고 게이트를 명시적으로 실패시킨다).
 */
internal fun leakBaselineLegacyFormatViolation(baseline: Set<String>): String? {
    val legacyEntries = baseline.filter(::isLegacyLeakBaselineKey).sorted()
    if (legacyEntries.isEmpty()) return null
    return legacyEntries.joinToString(
        prefix =
            "config/quality/leak-pattern-baseline.txt 에 옛 형식(경로:줄번호) 항목이 남아 있다 — " +
                "새 형식(경로#정규화줄내용해시)으로 변환하라:\n  ",
        separator = "\n  ",
    )
}

/**
 * baseline 밖의 새 키 — 게이트가 실패시키는 유일한 축. baseline 에 있는 키는 통과하고,
 * baseline 밖의 새 키만 남긴다(방향이 반대로 뒤집히면 baseline 에 없는 것만 "정상"으로
 * 잘못 읽어 실제 유출을 놓친다).
 */
internal fun newLeakBaselineKeys(
    matchKeys: Set<String>,
    baseline: Set<String>,
): List<String> = (matchKeys - baseline).sorted()

/**
 * baseline 에는 있는데 지금 매치에는 없는 키 — **현재 동작을 그대로 고정**(게이트를
 * 실패시키지 않고 보고서에만 싣는다, 동작 변경 없음).
 */
internal fun staleLeakBaselineEntries(
    matchKeys: Set<String>,
    baseline: Set<String>,
): List<String> = (baseline - matchKeys).sorted()

/**
 * 게이트 실패 메시지 — 새 키가 있을 때만, 저장 가능한 키와 현재 좌표를 함께 낸다(D-LBC-3).
 * `coordinatesByKey`는 호출자(Task)가 한 번만 묶어 넘긴다 — 이 함수가 다시 groupBy 하면
 * `leakGateReportText`가 계산한 것과 어긋날 수 있고, 어긋나면 좌표가 조용히 빈 채로 나간다.
 */
internal fun leakGateViolation(
    newKeys: List<String>,
    coordinatesByKey: Map<String, List<LeakMatch>>,
): String? {
    if (newKeys.isEmpty()) return null
    return newKeys.joinToString(
        prefix =
            "leak-patterns.txt 매치가 baseline 밖에서 새로 나타났다 — 실제 유출이면 값을 제거하고, " +
                "검토된 오탐(패턴 어휘 인용 등)이면 config/quality/leak-pattern-baseline.txt 에 등재하라:\n  ",
        separator = "\n  ",
    ) { key ->
        val coordinates =
            coordinatesByKey[key]
                .orEmpty()
                .map { it.coordinate }
                .sorted()
                .joinToString(", ")
        "$key  ($coordinates)"
    }
}

/**
 * 보고서 본문 — 매치 줄 수(`matches`)와 접힌 키 수(`keys`)를 따로 싣고, new·stale 항목을
 * 나열한다. `coordinatesByKey`는 [leakGateViolation]과 같은 이유로 호출자가 한 번만 묶어 넘긴다.
 */
internal fun leakGateReportText(
    patternCount: Int,
    baselineCount: Int,
    matchCount: Int,
    coordinatesByKey: Map<String, List<LeakMatch>>,
    newKeys: List<String>,
    staleBaseline: List<String>,
): String {
    val lines =
        listOf(
            "patterns=$patternCount",
            "baseline=$baselineCount",
            "matches=$matchCount",
            "keys=${coordinatesByKey.size}",
            "new=${newKeys.size}",
        ) +
            newKeys.map { key ->
                val coordinates =
                    coordinatesByKey[key]
                        .orEmpty()
                        .map { it.coordinate }
                        .sorted()
                        .joinToString(", ")
                "new: $key  ($coordinates)"
            } +
            listOf("stale_baseline=${staleBaseline.size}") +
            staleBaseline.map { "stale: $it" }
    return lines.joinToString(separator = "\n", postfix = "\n")
}
