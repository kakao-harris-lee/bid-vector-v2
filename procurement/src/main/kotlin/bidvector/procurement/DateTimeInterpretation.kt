package bidvector.procurement

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 타임존 없는 KONEPS 일시 문자열을 해석하는 형식의 id(D-3A-4 파급, 3A 잔여 일괄 verifier r3
 * 전 수정) — **선택**은 정책 데이터([KonepsCollectionPolicyData.dateTimePatterns]), 형식의
 * **의미**(문자열을 실제로 어떻게 나누는가)는 이 파일의 고정 파서가 갖는다. [SourceZoneRuleId]
 * 와 같은 원칙이다 — 「규칙 선택은 정책 데이터, 규칙의 의미는 상수」(`ZoneId.of("Asia/Seoul")`
 * 리터럴과 같은 자리). `java.time.format.DateTimeFormatter`(패턴 문자열 해석기)는 domain
 * 허용 목록 밖(`config/quality/architecture-policy.properties` — `java.time`만 허용,
 * `java.time.format`은 아니다, 실측: `domainSourceReferenceGate` 실패)이라 쓰지 않는다.
 *
 * 이전 판은 `LocalDateTime.parse(raw)`(인자 없는 기본 ISO `T` 파서)를 직접 썼는데, KONEPS
 * 실제 wire 형식(공식 문서, `policy-values.md` §1.4 authoritative)은 **공백** 구분자라 실제
 * 응답에서 이 함수가 항상 `null`을 냈다(v2-defect, koneps-collection-026 이 발견).
 */
enum class DateTimePatternId {
    /**
     * KONEPS 공식 문서 authoritative 형식(`policy-values.md` §1.4) —
     * `"YYYY-MM-DD HH:MM:SS"`(항목크기 19, offset 없음). 예: `"2026-09-10 14:00:00"`.
     */
    KONEPS_SPACE_DELIMITED_19,
}

private val KONEPS_SPACE_DELIMITED_19_SHAPE = Regex("""^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$""")

// 정규식 캡처 그룹 색인 — groupValues[0]은 전체 매치이므로 년부터 1이다.
private const val GROUP_YEAR = 1
private const val GROUP_MONTH = 2
private const val GROUP_DAY = 3
private const val GROUP_HOUR = 4
private const val GROUP_MINUTE = 5
private const val GROUP_SECOND = 6

/** [DateTimePatternId.KONEPS_SPACE_DELIMITED_19] 하나의 고정 폭 해석 — 자리 구분자를 정규식으로 검증한다. */
private fun parseKonepsSpaceDelimited19(raw: String): LocalDateTime? {
    val match = KONEPS_SPACE_DELIMITED_19_SHAPE.matchEntire(raw) ?: return null
    val fields = match.groupValues
    return runCatching {
        LocalDateTime.of(
            fields[GROUP_YEAR].toInt(),
            fields[GROUP_MONTH].toInt(),
            fields[GROUP_DAY].toInt(),
            fields[GROUP_HOUR].toInt(),
            fields[GROUP_MINUTE].toInt(),
            fields[GROUP_SECOND].toInt(),
        )
    }.getOrNull()
}

private fun parseWithPattern(
    raw: String,
    pattern: DateTimePatternId,
): LocalDateTime? =
    when (pattern) {
        DateTimePatternId.KONEPS_SPACE_DELIMITED_19 -> parseKonepsSpaceDelimited19(raw)
    }

/**
 * [parseSourceZonedInstant] 한 호출의 결과 — 필드 부재(`Absent`)와 파싱 실패(`ParseFailed`)를
 * 구분한다. 이전 판은 값이 없거나 파싱이 실패하거나 똑같이 `null`을 내 그 둘을 [canonicalize]
 * 가 구별할 수 없었다 — 파싱 실패를 조용한 `null`(COL-02 「미상 = 부재」 갈래)로 접지 않고
 * 관측 가능한 [CollectionDropReason.CollectionParseFailure]로 낸다.
 */
sealed interface InstantResolutionOutcome {
    data class Resolved(
        val instant: Instant,
    ) : InstantResolutionOutcome

    data object Absent : InstantResolutionOutcome

    data object ParseFailed : InstantResolutionOutcome
}

/**
 * 원문 일시 문자열 + [SourceZoneRuleId](해석 규칙 **선택**, 계약의 `sourceZone`) +
 * `patterns`(허용 형식 id 목록, 정책 데이터) → [Instant]. `patterns`를 순서대로 시도해 첫
 * 성공을 낸다 — 여러 형식이 동시에 유효한 기간을 허용한다(예: 형식 변경 이행기).
 */
fun parseSourceZonedInstant(
    raw: String,
    rule: SourceZoneRuleId,
    patterns: List<DateTimePatternId>,
): Instant? {
    val local = patterns.firstNotNullOfOrNull { pattern -> parseWithPattern(raw, pattern) } ?: return null
    val zone =
        when (rule) {
            SourceZoneRuleId.ASSUME_KST -> ZoneId.of("Asia/Seoul")
        }
    return runCatching { local.atZone(zone).toInstant() }.getOrNull()
}

/**
 * 시각 축 배선(verifier r2 N-3, 3A 잔여 일괄로 [InstantResolutionOutcome] 반환하도록 정정) —
 * `DEADLINE_AT`·`OPENING_SCHEDULED_AT` 두 개념이 공유하는 진입점. 계약이 없거나
 * `DATETIME_NO_ZONE` 축이 아니거나 `sourceZone`이 없으면(구성상 있어야 하지만 방어적으로)
 * [InstantResolutionOutcome.Absent]다. **값이 있는데 정책의 모든 패턴으로 파싱에 실패하면
 * [InstantResolutionOutcome.ParseFailed]**다(조용한 `null` 금지) — [canonicalize]가 그 경우
 * 항목을 `CollectionParseFailure(DATE_TIME)`로 떨어뜨린다.
 */
fun instantFrom(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
    concept: FieldConcept,
): InstantResolutionOutcome {
    val contract =
        policy.fieldContracts
            .contractsFor(concept)
            .firstOrNull()
            ?.takeIf { it.scale == FieldScale.DATETIME_NO_ZONE }
    val zone = contract?.sourceZone
    val raw = contract?.let(observation::valueOf)
    return when {
        zone == null || raw == null -> {
            InstantResolutionOutcome.Absent
        }

        else -> {
            val instant = parseSourceZonedInstant(raw, zone, policy.dateTimePatterns)
            if (instant != null) InstantResolutionOutcome.Resolved(instant) else InstantResolutionOutcome.ParseFailed
        }
    }
}
