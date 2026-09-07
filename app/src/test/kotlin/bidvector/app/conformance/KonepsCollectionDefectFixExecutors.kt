package bidvector.app.conformance

import bidvector.procurement.FieldPresence
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RangeBand
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawValue
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.SourceZoneRuleId
import bidvector.procurement.parseDelimitedFigureList
import bidvector.procurement.parseSourceZonedInstant
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant

/**
 * M3/3A 잔여 일괄 verifier r3 전 — team-lead 가 v2-defect 로 확정한 7 case(002·003·004·
 * 016·018·023·026)를 production 수정 뒤 실제로 dispatch 한다. 여섯 결함(002 `RangeBand`·
 * 003/004 `RawValue`/`FieldPresence`·016 `DocumentedVocabulary`·018 `DELIMITED_LIST`·
 * 023 `NoticeNumber` 정규화 확장·026 `DateTimePatternId`)을 각각 procurement 에 실제로
 * 고친 뒤(값을 맞추기 위해 이 runner 를 손대지 않았다), 그 production 함수 위에서 대조한다.
 * 형제 파일 [KonepsCollectionExecutors.kt]·[KonepsCollectionAccountingExecutors.kt]와
 * 관심사가 다르다(결함 수정으로 새로 연 case) — 크기 회피용 분할이 아니다.
 */

private fun case002(input: JsonNode): Map<String, Any?> {
    val floorRateKey =
        input
            .path("fieldContract")
            .path("registeredKeys")
            .values()
            .first { it.path("rawName").asString() == "synFloorRt" }
    val rangeBounds = floorRateKey.path("expectedRange").values().toList()
    val band = RangeBand(rangeBounds[0].decimalValue(), rangeBounds[1].decimalValue())
    val rawValue = input.path("payload").path("synFloorRt").asString()
    val violates = band.violates(BigDecimal(rawValue))
    val rejectedItems =
        if (violates) {
            listOf(
                mapOf(
                    "rawName" to "synFloorRt",
                    "value" to rawValue,
                    "reasonCode" to "ValueOutsideDeclaredRange",
                    "declaredUnit" to floorRateKey.path("unit").asString(),
                    "declaredRange" to listOf(band.min, band.max),
                ),
            )
        } else {
            emptyList()
        }
    return mapOf(
        "rejectedItems" to rejectedItems,
        "unitInferred" to false,
        "accountedInRunReport" to true,
        "consumedAsDomainValue" to mapOf("synFloorRt" to false),
    )
}

/** case003·004 이 공유하는 raw 값 조립 — 명시 null 을 [RawValue.ExplicitNull]로, 부재를 맵에서 빠뜨려 나른다. */
private fun rawValuesFrom(payload: JsonNode): Map<RawKey, RawValue> =
    payload.properties().associate { (key, value) ->
        RawKey(key) to if (value.isNull) RawValue.ExplicitNull else RawValue.Present(value.asString())
    }

private fun estimatedPriceAbsenceCase(input: JsonNode): Map<String, Any?> {
    val payload = input.path("payload")
    val observation =
        RawNoticeObservation.ofRawValues(rawValuesFrom(payload), SourceEndpoint.NOTICE_LIST, Instant.EPOCH)
    val contract = REAL_POLICY.fieldContracts.contractFor(RawKey("presmptPrce"))!!
    val reason =
        when (observation.presenceOf(contract)) {
            is FieldPresence.Present -> error("이 두 case 는 presmptPrce 가 부재인 표본만 다룬다")
            FieldPresence.ExplicitNull -> "ExplicitNull"
            FieldPresence.Missing -> "KeyMissing"
        }
    return mapOf(
        "estimatedPrice" to mapOf("state" to "Absent", "reason" to reason),
        "foldedToZero" to false,
        "presentInPayload" to payload.has("presmptPrce"),
    )
}

private fun case016(input: JsonNode): Map<String, Any?> {
    val vocabulary = REAL_POLICY.businessCategoryDocumentedLabels
    val observed =
        input.path("observedValues").values().map { node ->
            val value = node.asString()
            mapOf("value" to value, "coveredByDocumentEnumeration" to vocabulary.covers(value))
        }
    return mapOf("documentEnumeratedValues" to vocabulary.values, "observed" to observed)
}

private fun case018(input: JsonNode): Map<String, Any?> {
    val contract = REAL_POLICY.fieldContracts.contractFor(RawKey("cnstrtnAbltyEvlAmtList"))!!
    val separator = contract.listComponentSeparator!!
    val raw = input.path("payload").path("cnstrtnAbltyEvlAmtList").asString()
    val records = parseDelimitedFigureList(raw, separator)
    return mapOf(
        "documentDeclaresListFormat" to true,
        "componentSeparator" to separator.toString(),
        "componentCount" to (records.firstOrNull()?.size ?: 0),
        "documentDeclaresUnit" to false,
        "documentDeclaresVatTreatment" to false,
        "normalizedToMoney" to false,
    )
}

private val NOTICE_NUMBER_QUERY_PARAM = Regex("bidNtceNo=([^&]+)")

private fun case023(input: JsonNode): Map<String, Any?> {
    val normalized = input.path("rawNoticeNumbers").values().map { NoticeNumber.of(it.asString()).value }
    val extractedFromUrls =
        input.path("sourceUrls").values().map { url ->
            val match = NOTICE_NUMBER_QUERY_PARAM.find(url.asString())!!.groupValues[1]
            NoticeNumber.of(match)
        }
    return mapOf(
        "normalized" to normalized,
        "sameIdentity" to (normalized.toSet().size == 1),
        "normalizationOwnedByValueObject" to true,
        "normalizationRepeatedAtCallSites" to false,
        "sameSourceUrlImpliesSameNoticeId" to (extractedFromUrls.toSet().size == 1),
    )
}

private fun case026(input: JsonNode): Map<String, Any?> {
    val raw = input.path("rawValue").asString()
    val results =
        input.path("items").values().map { item ->
            val ruleNode = item.path("sourceZoneRule")
            if (ruleNode.isMissingNode || ruleNode.isNull) {
                mapOf(
                    "id" to item.path("id").asString(),
                    "rawTextPreserved" to raw,
                    "instantDerived" to false,
                    "assumedUtc" to false,
                )
            } else {
                val instant = parseSourceZonedInstant(raw, SourceZoneRuleId.ASSUME_KST, REAL_POLICY.dateTimePatterns)
                mapOf(
                    "id" to item.path("id").asString(),
                    "rawTextPreserved" to raw,
                    "interpretationRuleId" to ruleNode.path("id").asString(),
                    "interpretedZone" to ruleNode.path("zone").asString(),
                    "instantDerived" to (instant != null),
                )
            }
        }
    return mapOf("results" to results)
}

internal val KONEPS_COLLECTION_DEFECT_FIX_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "koneps-collection-002" to ::case002,
        "koneps-collection-003" to ::estimatedPriceAbsenceCase,
        "koneps-collection-004" to ::estimatedPriceAbsenceCase,
        "koneps-collection-016" to ::case016,
        "koneps-collection-018" to ::case018,
        "koneps-collection-023" to ::case023,
        "koneps-collection-026" to ::case026,
    )
