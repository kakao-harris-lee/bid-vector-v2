package bidvector.adapters.koneps

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawValue
import bidvector.procurement.SourceEndpoint
import java.time.Instant

private const val OPENG_CORP_INFO_KEY = "opengCorpInfo"
private const val OPENG_CORP_INFO_SEPARATOR = '^'

// §1.7.5 — "업체명^사업자번호^대표자명^투찰금액^투찰율"(단일 낙찰자 형태)뿐이다. 「낙찰예정자
// 다수」·협상 계약 형태는 성분 수가 이것과 다르거나(같아도 자리 뜻이 다르다, 설계 검토
// (1)(3)) 판별할 수 없어 fail-closed(값 전체 폐기)로 떨어진다 — 위치 추측을 하지 않는다.
private const val SINGLE_WINNER_COMPONENT_COUNT = 5
private const val COMPANY_NAME_INDEX = 0
private const val BID_AMOUNT_INDEX = 3
private const val BID_RATE_INDEX = 4

/**
 * `opengCorpInfo` 원문을 masked 값으로 치환한다(P-10 (a), 설계 검토 게이트 ① (4)) — 5 성분
 * (단일 낙찰자)일 때만 업체명·투찰금액·투찰율 셋을 남기고 사업자번호·대표자명을 버린다.
 * 그 외(성분 수가 다르거나 「낙찰예정자 다수」·협상 계약 형태)는 **`null`**(값 전체 폐기,
 * 부분 치환·위치 추측 금지) — 호출부가 이것을 명시적 실패로 회계한다.
 */
internal fun maskOpengCorpInfo(raw: String): String? {
    val components = raw.split(OPENG_CORP_INFO_SEPARATOR)
    if (components.size != SINGLE_WINNER_COMPONENT_COUNT) return null
    return listOf(components[COMPANY_NAME_INDEX], components[BID_AMOUNT_INDEX], components[BID_RATE_INDEX])
        .joinToString(OPENG_CORP_INFO_SEPARATOR.toString())
}

/** [fieldOutcomeOf] 한 필드 판정 — [MaskedKonepsItem.from]의 누적 루프가 `continue`/`break` 없이 소진하게 한다. */
private sealed interface FieldMaskOutcome {
    /** allow-list 밖(계약 미등재) 또는 blank 키 — 담지 않는다, 회계도 남기지 않는다. */
    data object Excluded : FieldMaskOutcome

    /** `opengCorpInfo` 성분 배치가 선언과 다르다 — 값 전체 폐기, 명시적 실패로 회계한다. */
    data object DecompositionFailed : FieldMaskOutcome

    data class Included(
        val renderValue: JsonValue,
        val rawValue: RawValue,
    ) : FieldMaskOutcome
}

private fun fieldOutcomeOf(
    name: String,
    value: JsonValue,
    policy: KonepsCollectionPolicyData,
): FieldMaskOutcome =
    when {
        // allow-list 반전 — 계약이 없는 키는 담는 단계에서 제외된다(사업자등록번호·대표자명 등
        // §1.7.5 각주의 미등재 식별자가 여기서 자동으로 빠진다, P-10 (a)).
        name.isBlank() || policy.fieldContracts.contractFor(RawKey(name)) == null -> {
            FieldMaskOutcome.Excluded
        }

        name != OPENG_CORP_INFO_KEY -> {
            FieldMaskOutcome.Included(value, value.toRawValue())
        }

        else -> {
            val masked = (value as? JsonValue.JsonString)?.value?.let(::maskOpengCorpInfo)
            if (masked == null) {
                FieldMaskOutcome.DecompositionFailed
            } else {
                FieldMaskOutcome.Included(JsonValue.JsonString(masked), RawValue.Present(masked))
            }
        }
    }

/**
 * allow-list 로 걸러 치환까지 마친 개찰 축 항목(설계 검토 게이트 ① — 「계약 등재 키만 담는다」
 * + 「단일 통로 타입」). **생성자가 `private`다** — 유일한 생성 경로는 [from]이고, 그 함수가
 * (a) 계약에 없는 키를 자동 제외(allow-list 반전, 새 식별자 키가 KONEPS 에 추가돼도 계약이
 * 없으므로 자동으로 빠진다) (b) `opengCorpInfo`만 [maskOpengCorpInfo]를 거치게 한다. 개찰 축
 * mapper([mapMaskedOpeningItem])는 원문 `JsonValue.JsonObject`를 받지 않고 이 타입만 받아
 * 호출부가 masking 을 우회해 원문 item 을 직접 넘기는 경로가 컴파일되지 않는다.
 */
internal class MaskedKonepsItem private constructor(
    val fields: Map<RawKey, RawValue>,
    val sourceText: String,
    val decompositionFailures: Int,
) {
    companion object {
        fun from(
            item: JsonValue.JsonObject,
            policy: KonepsCollectionPolicyData,
        ): MaskedKonepsItem {
            val forRender = LinkedHashMap<String, JsonValue>()
            val fields = LinkedHashMap<RawKey, RawValue>()
            var decompositionFailures = 0
            for ((name, value) in item.fields) {
                when (val outcome = fieldOutcomeOf(name, value, policy)) {
                    // allow-list 밖 — 담지 않는다, 회계도 남기지 않는다.
                    FieldMaskOutcome.Excluded -> {
                    }

                    FieldMaskOutcome.DecompositionFailed -> {
                        decompositionFailures++
                    }

                    is FieldMaskOutcome.Included -> {
                        forRender[name] = outcome.renderValue
                        fields[RawKey(name)] = outcome.rawValue
                    }
                }
            }
            val sourceText = JsonValue.JsonObject(forRender, sourceText = "").render()
            return MaskedKonepsItem(fields, sourceText, decompositionFailures)
        }
    }
}

/**
 * 개찰 축 항목(⑥b, COL-02·03·04) → [RawNoticeObservation] — [mapRawItem](공고 축, 전체 원문
 * 보존)과 달리 [MaskedKonepsItem]을 거쳐 계약 등재 키만 담고 `sourceText`도 걸러진 값의
 * `render()`다(설계 검토 게이트 ① (2) — 원문 substring 을 나르지 않는다, 3D 감사 통로에도
 * 원문이 남지 않는다). 식별자(공고번호·차수) 부재는 공고 축과 같은 사유로 떨어진다(M-2 관례).
 */
internal fun mapMaskedOpeningItem(
    item: JsonValue.JsonObject,
    policy: KonepsCollectionPolicyData,
    sourceEndpoint: SourceEndpoint,
    observedAt: Instant,
): RawItemOutcome {
    val masked = MaskedKonepsItem.from(item, policy)
    val (numberKey, roundKey) = identityRawKeys(policy)
    val numberRaw = presentText(masked.fields, numberKey)
    val roundRaw = presentText(masked.fields, roundKey)
    if (numberRaw.isNullOrBlank() || roundRaw.isNullOrBlank()) {
        return RawItemOutcome.Dropped(CollectionDropReason.CollectionMissingNoticeNumber)
    }
    val observation = RawNoticeObservation.ofRawValues(masked.fields, sourceEndpoint, observedAt, masked.sourceText)
    return RawItemOutcome.Mapped(observation, identityOf(numberRaw, roundRaw), masked.decompositionFailures)
}
