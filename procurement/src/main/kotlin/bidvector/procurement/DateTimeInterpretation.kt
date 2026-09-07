package bidvector.procurement

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * D-3A-4(조사 G-7) — 타임존 없는 KONEPS 일시 문자열을 [SourceZoneRuleId]로 해석한다. 규칙
 * **선택**은 정책 데이터(계약의 `sourceZone` 슬롯)이지만, 규칙 하나(`ASSUME_KST`)의 **의미**
 * (문자열 → `ZoneId`)는 이 매핑 표가 갖는다 — 규칙이 늘면 이 `when`에 분기를 더한다.
 */
fun parseSourceZonedInstant(
    raw: String,
    rule: SourceZoneRuleId,
): Instant? {
    val local = runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: return null
    val zone =
        when (rule) {
            SourceZoneRuleId.ASSUME_KST -> ZoneId.of("Asia/Seoul")
        }
    return local.atZone(zone).toInstant()
}

/**
 * 시각 축 배선(verifier r2 N-3) — `DEADLINE_AT`·`OPENING_SCHEDULED_AT` 두 개념이 공유하는
 * 진입점. 계약의 `sourceZone`(각 필드가 스스로 나른다, D-3A-4)로 해석하고, 계약이 없거나
 * `DATETIME_NO_ZONE` 축이 아니거나 `sourceZone`이 없으면(구성상 있어야 하지만 방어적으로)
 * `null`이다. 원문은 [NoticeCollected.raw]가 그대로 보존한다 — 이 함수는 해석값만 낸다.
 */
fun instantFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    concept: FieldConcept,
): Instant? =
    registry
        .contractsFor(concept)
        .firstOrNull()
        ?.takeIf { it.scale == FieldScale.DATETIME_NO_ZONE }
        ?.let { contract ->
            contract.sourceZone?.let { zone -> observation.valueOf(contract)?.let { raw -> raw to zone } }
        }?.let { (raw, zone) -> parseSourceZonedInstant(raw, zone) }
