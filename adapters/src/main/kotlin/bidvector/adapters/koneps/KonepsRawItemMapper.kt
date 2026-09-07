package bidvector.adapters.koneps

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawValue
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
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
 * 항목 중복 판별용 식별자 짝(M-2, verifier r1 — 이전 판은 원문 텍스트 짝이었고 canonical
 * 이 같고 원문이 다른 항목을 못 걸렀다, P6 실측). **공고번호 축은 3A canonical
 * [NoticeNumber]** — H-2 로 blank 가 이미 걸러져 `NoticeNumber.of`가 던지는 유일한 경로가
 * 막혀 있다(실측: blank 외에는 던지지 않는다). **차수 축은 원문이 기본**이고
 * `NoticeRound.of`가 받는 형식(제로패딩 3자리)일 때만 그 canonical 값으로 좁힌다 —
 * `NoticeRound.of`는 형식 위반에 `require`로 던지므로(원문이 3자리가 아닐 수 있는 악성·
 * 오류 응답), 실패하면 원문 그대로 남겨 dedup 경로가 예외로 흐르지 않는다(원 설계 판단의
 * 잔여 절반, checklist.md).
 */
internal data class NoticeIdentity(
    val canonicalNumber: String,
    val round: String,
)

private fun JsonValue.toRawValue(): RawValue =
    when (this) {
        is JsonValue.JsonString -> RawValue.Present(value)

        is JsonValue.JsonNumber -> RawValue.Present(raw)

        // M-1(verifier r1) — JSON boolean 은 원문 토큰 텍스트("true"/"false") 그대로
        // 옮긴다. 이전 판은 "Y"/"N" 으로 바꿨는데 그것 자체가 변환이다(⑥ 「원문 그대로,
        // 변환·정규화 없음」 위반, 골든 원문 바이트 동일 test 로 잡힌다).
        is JsonValue.JsonBool -> RawValue.Present(value.toString())

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

/** dedup 식별자 조립(M-2) — 공고번호는 canonical, 차수는 형식이 맞을 때만 canonical(그 외 원문). */
private fun identityOf(
    numberRaw: String,
    roundRaw: String,
): NoticeIdentity {
    val canonicalNumber = NoticeNumber.of(numberRaw).value
    val canonicalRound = runCatching { NoticeRound.of(roundRaw).value }.getOrDefault(roundRaw)
    return NoticeIdentity(canonicalNumber, canonicalRound)
}

/**
 * JSON 항목(⑥) → [RawNoticeObservation] — 값은 원문 그대로 옮긴다(변환·정규화 없음). 공고번호·
 * 차수 raw 키가 없거나 **값이 빈 문자열·공백뿐이면**(H-2, verifier r1 — 키 부재만이 아니라
 * 값 부재도 COL-01 「공고번호 없음」이다) 이 항목은 [CollectionDropReason
 * .CollectionMissingNoticeNumber]로 collection-time 에 떨어진다 — 3A `resolvedNoticeId`
 * (비공개)와 같은 판단을 어댑터 경계에서 미리 내야 `SourceBatch` 의 collection 회계가
 * COL-01 acceptance(「3건+dropped=1」)를 낼 수 있다. canonicalize(금액·일시 해석)는 3B 밖
 * workflow 가 나중에 부른다 — 이 함수는 그 전 단계다.
 */
internal fun mapRawItem(
    item: JsonValue.JsonObject,
    policy: KonepsCollectionPolicyData,
    sourceEndpoint: SourceEndpoint,
    observedAt: Instant,
): RawItemOutcome {
    val rawFields = item.fields
    // L-4(verifier r1) — blank 키는 [RawKey]가 거부해 걸러야 하나, 걸러진 사실 자체가
    // 회계에서 사라지면 안 된다 — unknownFieldCount 에 실어 낸다.
    val blankKeyCount = rawFields.keys.count { it.isBlank() }
    val fields =
        rawFields
            .filterKeys { it.isNotBlank() }
            .mapKeys { (name, _) -> RawKey(name) }
            .mapValues { (_, value) -> value.toRawValue() }
    val (numberKey, roundKey) = identityRawKeys(policy)
    val numberRaw = presentText(fields, numberKey)
    val roundRaw = presentText(fields, roundKey)
    if (numberRaw.isNullOrBlank() || roundRaw.isNullOrBlank()) {
        return RawItemOutcome.Dropped(CollectionDropReason.CollectionMissingNoticeNumber)
    }
    val observation = RawNoticeObservation.ofRawValues(fields, sourceEndpoint, observedAt)
    val unknownFieldCount = policy.fieldContracts.unknownKeysIn(observation).size + blankKeyCount
    return RawItemOutcome.Mapped(observation, identityOf(numberRaw, roundRaw), unknownFieldCount)
}
