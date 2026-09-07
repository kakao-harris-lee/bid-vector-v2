package bidvector.adapters.koneps

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawValue
import bidvector.procurement.SourceEndpoint
import java.time.Instant

/** JSON 항목 하나를 [RawNoticeObservation]으로 옮긴 결과. */
internal sealed interface RawItemOutcome {
    data class Mapped(
        val observation: RawNoticeObservation,
        val identity: NoticeIdentity?,
        val unknownFieldCount: Int,
    ) : RawItemOutcome

    data class Dropped(
        val reason: CollectionDropReason,
    ) : RawItemOutcome
}

/**
 * 항목 중복 판별용 원문 식별자 짝 — canonical `NoticeId`가 **아니다**(⑥, 3B는 canonicalize
 * 하지 않는다). raw 텍스트째 비교한다 — `NoticeRound.of`가 요구하는 제로패딩 3자리 형식이
 * 원문에서 깨져 있으면(악성·오류 응답) 그 검증 실패를 dedup 경로에서 예외로 흘리지 않는다
 * (판단 갈린 지점, checklist.md).
 */
internal data class NoticeIdentity(
    val number: String,
    val round: String,
)

private fun JsonValue.toRawValue(): RawValue =
    when (this) {
        is JsonValue.JsonString -> RawValue.Present(value)
        is JsonValue.JsonNumber -> RawValue.Present(raw)
        is JsonValue.JsonBool -> RawValue.Present(if (value) "Y" else "N")
        JsonValue.JsonNull -> RawValue.ExplicitNull
        is JsonValue.JsonArray, is JsonValue.JsonObject -> RawValue.Present(render())
    }

private fun identityRawKeys(policy: KonepsCollectionPolicyData): Pair<RawKey?, RawKey?> {
    val number =
        policy.fieldContracts
            .contractsFor(FieldConcept.NOTICE_NUMBER)
            .firstOrNull()
            ?.rawName
    val round =
        policy.fieldContracts
            .contractsFor(FieldConcept.NOTICE_ROUND)
            .firstOrNull()
            ?.rawName
    return number to round
}

private fun presentText(
    fields: Map<RawKey, RawValue>,
    key: RawKey?,
): String? = (key?.let(fields::get) as? RawValue.Present)?.text

/**
 * JSON 항목(⑥) → [RawNoticeObservation] — 값은 원문 그대로 옮긴다(변환·정규화 없음). 공고번호·
 * 차수 raw 키 중 하나라도 없으면 이 항목은 COL-01 이 겨누는
 * [CollectionDropReason.CollectionMissingNoticeNumber]로 collection-time 에 떨어진다 — 3A
 * `resolvedNoticeId`(비공개)와 같은 판단을 어댑터 경계에서 미리 내야 `SourceBatch` 의
 * collection 회계가 COL-01 acceptance(「3건+dropped=1」)를 낼 수 있다. canonicalize(금액·일시
 * 해석)는 3B 밖 workflow 가 나중에 부른다 — 이 함수는 그 전 단계다.
 */
internal fun mapRawItem(
    item: JsonValue.JsonObject,
    policy: KonepsCollectionPolicyData,
    sourceEndpoint: SourceEndpoint,
    observedAt: Instant,
): RawItemOutcome {
    val fields =
        item.fields
            .filterKeys { it.isNotBlank() }
            .mapKeys { (name, _) -> RawKey(name) }
            .mapValues { (_, value) -> value.toRawValue() }
    val (numberKey, roundKey) = identityRawKeys(policy)
    val numberRaw = presentText(fields, numberKey)
    val roundRaw = presentText(fields, roundKey)
    if (numberRaw == null || roundRaw == null) {
        return RawItemOutcome.Dropped(CollectionDropReason.CollectionMissingNoticeNumber)
    }
    val observation = RawNoticeObservation.ofRawValues(fields, sourceEndpoint, observedAt)
    val unknownFieldCount = policy.fieldContracts.unknownKeysIn(observation).size
    return RawItemOutcome.Mapped(observation, NoticeIdentity(numberRaw, roundRaw), unknownFieldCount)
}
