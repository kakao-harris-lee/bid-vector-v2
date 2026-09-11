package bidvector.buildlogic

/*
 * `LeakPatternGateTask`의 판정 로직 — 전부 순수 함수다(`ContractGateChecks.kt`와 같은 관례).
 * Task 는 파일을 읽어 값으로만 넘기고, 매치·baseline 비교·보고서 문구는 여기서 결정한다.
 * 이 분리가 `LeakPatternGateChecksTest`를 Gradle Task 인스턴스화 없이 돌게 한다.
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

/** 한 파일에서 패턴에 매치하는 줄을 `상대경로:줄번호`(1-based) 형태로 낸다. */
internal fun leakMatchesInFile(
    relativePath: String,
    lines: List<String>,
    patterns: List<Regex>,
): List<String> =
    lines
        .withIndex()
        .filter { (_, line) -> patterns.any { it.containsMatchIn(line) } }
        .map { (index, _) -> "$relativePath:${index + 1}" }

/**
 * baseline 밖의 새 매치 — 게이트가 실패시키는 유일한 축. baseline 에 있는 매치는 통과하고,
 * baseline 밖의 새 매치만 남긴다(방향이 반대로 뒤집히면 baseline 에 없는 것만 "정상"으로
 * 잘못 읽어 실제 유출을 놓친다).
 */
internal fun newLeakMatches(
    matches: Set<String>,
    baseline: Set<String>,
): List<String> = (matches - baseline).sorted()

/**
 * baseline 에는 있는데 지금 스캔에는 없는 항목 — **현재 동작을 그대로 고정**(게이트를
 * 실패시키지 않고 보고서에만 싣는다, 동작 변경 없음).
 */
internal fun staleLeakBaselineEntries(
    matches: Set<String>,
    baseline: Set<String>,
): List<String> = (baseline - matches).sorted()

/** 게이트 실패 메시지 — 새 매치가 있을 때만, 없으면 null(위반 없음). */
internal fun leakGateViolation(newMatches: List<String>): String? {
    if (newMatches.isEmpty()) return null
    return newMatches.joinToString(
        prefix =
            "leak-patterns.txt 매치가 baseline 밖에서 새로 나타났다 — 실제 유출이면 값을 제거하고, " +
                "검토된 오탐(패턴 어휘 인용 등)이면 config/quality/leak-pattern-baseline.txt 에 등재하라:\n  ",
        separator = "\n  ",
    )
}

/** 보고서 본문 — patterns/baseline/matches 셈, new·stale 항목 나열. */
internal fun leakGateReportText(
    patternCount: Int,
    baselineCount: Int,
    matchCount: Int,
    newMatches: List<String>,
    staleBaseline: List<String>,
): String {
    val lines =
        listOf(
            "patterns=$patternCount",
            "baseline=$baselineCount",
            "matches=$matchCount",
            "new=${newMatches.size}",
        ) + newMatches.map { "new: $it" } +
            listOf("stale_baseline=${staleBaseline.size}") +
            staleBaseline.map { "stale: $it" }
    return lines.joinToString(separator = "\n", postfix = "\n")
}
