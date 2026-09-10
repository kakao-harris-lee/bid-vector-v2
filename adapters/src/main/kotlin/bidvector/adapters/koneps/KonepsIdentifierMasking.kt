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
    /**
     * allow-list 밖(계약 미등재)·blank 키·**계약이 이 엔드포인트를 선언하지 않은 키**
     * (`presentIn` 불일치, verifier r1 F-6 재검토) — 담지 않는다. **verifier r1 F-3
     * 수정**(운영자 승인 2026-09-08, 3A `Accounting.kt` 좁은 확장) — 이전 판은 여기서
     * 회계를 남기지 않아 §5.3 규율 1(선언에 없는 키는 미지 필드로 리포트)이 개찰 축에서
     * 사라졌다. 이제 [MaskedKonepsItem.excludedFieldCount]로 센다 — 3B `mapRawItem`의
     * `unknownKeysIn` 계수와 같은 목적, 다른 메커니즘(allow-list 는 필터링 자체가 계약
     * 대조라 사후 재조회 대신 그 자리에서 센다). `presentIn` 대조(F-6)는 P-9 ④(오퍼레이션
     * 군 구별) 승인 취지가 실제로 하중을 지게 한다 — 대조 없이는 어느 오퍼레이션의 값인지
     * 계약이 서류로만 구별할 뿐이었다.
     */
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
    sourceEndpoint: SourceEndpoint,
): FieldMaskOutcome {
    val contract = if (name.isBlank()) null else policy.fieldContracts.contractFor(RawKey(name))
    return when {
        // allow-list 반전 — 계약이 없는 키는 담는 단계에서 제외된다(사업자등록번호·대표자명 등
        // §1.7.5 각주의 미등재 식별자가 여기서 자동으로 빠진다, P-10 (a)).
        contract == null -> {
            FieldMaskOutcome.Excluded
        }

        // F-6(verifier r1 재검토) — 계약은 있으나 이 엔드포인트를 선언하지 않은 키는 제외한다.
        // P-9 ④(오퍼레이션 군 구별)의 승인 취지가 실제로 강제되는 자리다.
        !contract.presentIn.contains(sourceEndpoint) -> {
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
    // verifier r1 F-3 수정 — allow-list 가 떨어뜨린 키(계약 미등재·blank)의 수. §5.3 규율 1
    // (미지 필드 리포트)이 개찰 축에서도 서게 한다. `decompositionFailures`(F-8, 이름이
    // 반대인 슬롯 문제)와 서로 다른 사유라 별도 필드로 센다 — 하나로 접지 않는다.
    val excludedFieldCount: Int,
    val decompositionFailures: Int,
) {
    companion object {
        fun from(
            item: JsonValue.JsonObject,
            policy: KonepsCollectionPolicyData,
            sourceEndpoint: SourceEndpoint,
        ): MaskedKonepsItem {
            val forRender = LinkedHashMap<String, JsonValue>()
            val fields = LinkedHashMap<RawKey, RawValue>()
            var excludedFieldCount = 0
            var decompositionFailures = 0
            for ((name, value) in item.fields) {
                when (val outcome = fieldOutcomeOf(name, value, policy, sourceEndpoint)) {
                    FieldMaskOutcome.Excluded -> {
                        excludedFieldCount++
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
            return MaskedKonepsItem(fields, sourceText, excludedFieldCount, decompositionFailures)
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
    // F-1(verifier r1) — 낙찰 목록·개찰결과 목록은 emptyList(), 예비가격 상세는
    // KonepsOperationPolicy.RESERVE_PRICE_DETAIL.rowIdentifierRawKeys(compnoRsrvtnPrceSno).
    // 기본값 없음 — 새 개찰 축 오퍼레이션이 이 값을 잊으면 컴파일이 깨진다.
    rowIdentifierRawKeys: List<String>,
): RawItemOutcome {
    val masked = MaskedKonepsItem.from(item, policy, sourceEndpoint)
    val (numberKey, roundKey) = identityRawKeys(policy)
    val numberRaw = presentText(masked.fields, numberKey)
    val roundRaw = presentText(masked.fields, roundKey)
    if (numberRaw.isNullOrBlank() || roundRaw.isNullOrBlank()) {
        return RawItemOutcome.Dropped(CollectionDropReason.CollectionMissingNoticeNumber)
    }
    val observation = RawNoticeObservation.ofRawValues(masked.fields, sourceEndpoint, observedAt, masked.sourceText)
    val identity = identityOf(numberRaw, roundRaw, rowDiscriminatorOf(masked.fields, rowIdentifierRawKeys))
    // verifier r1 F-3·F-8 수정 — unknownFieldCount 는 이름 그대로 계약 밖 키 수만(F-3),
    // masking 실패는 별도 축(F-8, maskingFailureCount)으로 낸다. 이전 판은 이 둘을 하나로
    // 접어(unknownFieldCount 자리에 decompositionFailures 를 실어) F-3 의 손실을 만들었다.
    return RawItemOutcome.Mapped(observation, identity, masked.excludedFieldCount, masked.decompositionFailures)
}

// M3/3F — 개찰완료(13) 축도 [mapMaskedOpeningItem]을 그대로 쓴다(계약 레지스트리 경로,
// P-13 (a) 승인). `prcbdrBizno`·`prcbdrCeoNm`은 계약 미등재로 allow-list 반전에서 자동
// 제외된다 — 이 축 전용 masking 함수를 새로 만들지 않는다(중복 금지).
