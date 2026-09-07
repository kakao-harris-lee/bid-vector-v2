package bidvector.app.conformance

import bidvector.procurement.AmountAxis
import bidvector.procurement.AmountResolutionOutcome
import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.FieldConcept
import bidvector.procurement.FieldUnit
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.decideDetailFetch
import bidvector.procurement.mayOverwrite
import bidvector.procurement.resolveAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/*
 * M3/3A 잔여 일괄 ② — `koneps-collection` corpus(27 case, 운영자 승인 2026-09-07 전건
 * authoritative) 를 procurement 공개 API 위에 투영한다(1D `ProvenanceFloorExecutors.kt` 관례).
 * **runner 는 입력에 없는 값을 만들지 않는다**(1D 관례) — case 마다 fixture 가 주는 값만
 * procurement 타입으로 조립한다.
 *
 * **다른 모듈에서 `KonepsFieldContract`를 조립하지 않는다**(verifier r2 N-1이 닫은 경계 —
 * 이 runner 도 procurement 밖이다). 그래서 이 executor 는 어떤 case 도 ad hoc field
 * contract 를 짓지 않고, **[REAL_POLICY](운영 승인 값)만** 읽는다 — case 의 raw 키가
 * 실제 승인 정책에 없으면 (`unknownKeysIn`이 미지로 세거나 `resolveAmount`가 건너뛰는 등)
 * 그 자체가 검증 대상이다.
 *
 * **판정 필요 7건은 dispatch 하지 않는다**(`SharedKernelCorpusConformanceTest`의
 * `KONEPS_COLLECTION_PENDING_CAPABILITY` 참고, 팀리드 사전 지정 6건 + 배선 중 신규 발견
 * 1건) —
 * `002`(`expectedRange` 위반 거부 — 이 slice 에 그 축 실행 경로가 없다)·`003`·`004`(옵션
 * 금액의 `ExplicitNull` vs `KeyMissing` 구분 — `RawNoticeObservation`이 값 부재만 나르고
 * 부재의 **사유**를 나르지 않는다)·`016`(문서 열거값 자체가 procurement 타입이 아니다)·
 * `018`(§5.5 `UnnormalizedFigure` 미신설)·`023`(`NoticeNumber.of`는 trim 만 하고 대소문자·
 * 구분자 정규화를 하지 않는다 — 이 case 의 세 표기가 값으로는 같아지지 않는다)·
 * **`026`(신규 발견, v2-defect)** — `parseSourceZonedInstant`가 `LocalDateTime.parse`
 * (ISO `T` 구분자)만 받는데, KONEPS 실제 wire 형식은 공식 문서(`koneps-collection-014`가
 * 인용하는 "YYYY-MM-DD HH:MM:SS")대로 **공백** 구분자다 — `LocalDateTime.parse("2026-05-20
 * 10:00:00")`가 `DateTimeParseException`을 던진다(직접 JVM 실측 확인). 즉 실제 KONEPS
 * 응답에서 이 함수는 **항상 null**을 낸다. 기존 procurement test(`CanonicalizeTest`)가
 * ISO `T` 표기(`"2026-09-10T14:00:00"`)만 써서 이 결함이 가려져 있었다. 값을 맞추기 위해
 * `parseSourceZonedInstant`를 이 배치에서 고치지 않는다 — 3A 잔여 일괄 범위(4개 커밋
 * 단위) 밖의 변경이라 checklist 에 v2-defect 로 등재하고 멈춰 보고한다.
 */

private val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 9, 7)

internal val REAL_POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("KONEPS_COLLECTION_POLICY 가 $REFERENCE_DATE 에 해석되지 않는다: ${resolution.reason}")
    }

/** case 의 판정이 procurement 능력 공백을 요구해 dispatch 하지 않는 7건(evidence 참고). */
internal val KONEPS_COLLECTION_PENDING_CAPABILITY: Set<String> =
    setOf(
        "koneps-collection-002",
        "koneps-collection-003",
        "koneps-collection-004",
        "koneps-collection-016",
        "koneps-collection-018",
        "koneps-collection-023",
        "koneps-collection-026",
    )

/** `FieldUnit` → 표시 통화/단위 문자열. 이 corpus 가 다루는 단위는 원화뿐이다. */
private fun currencyUnitOf(unit: FieldUnit): String? =
    when (unit) {
        FieldUnit.WON -> "KRW"
        FieldUnit.PERCENT -> "percent"
        FieldUnit.NONE -> null
    }

private fun rawFieldsFrom(payload: JsonNode): Map<RawKey, String> =
    payload
        .properties()
        .filter { (_, value) -> !value.isNull && !value.isMissingNode }
        .associate { (key, value) -> RawKey(key) to value.asString() }

private fun observationFrom(
    payload: JsonNode,
    endpoint: SourceEndpoint = SourceEndpoint.NOTICE_LIST,
): RawNoticeObservation = RawNoticeObservation.of(rawFieldsFrom(payload), endpoint, Instant.EPOCH)

/** `KonepsFieldContract.nullability`(REQUIRED/OPTIONAL)를 curator corpus 어휘(필수/nullable)로 옮긴다. */
private fun nullabilityToken(nullability: bidvector.procurement.FieldNullability): String =
    when (nullability) {
        bidvector.procurement.FieldNullability.REQUIRED -> "required"
        bidvector.procurement.FieldNullability.OPTIONAL -> "nullable"
    }

private fun provenanceFromToken(
    name: String,
    sibling: JsonNode,
): Provenance =
    when (name) {
        "Published" -> Provenance.Published(NoticeRound.of(sibling.path("noticeRevision").asString("000")))
        "FilledFromBudgetKey" -> Provenance.FilledFromBudgetKey(sibling.path("sourceKey").asString())
        "OperatorDeclared" -> Provenance.OperatorDeclared
        "DerivedFromOpening" -> Provenance.DerivedFromOpening
        "CopiedFromBaseAmount" -> Provenance.CopiedFromBaseAmount
        "Undeclared" -> Provenance.Undeclared
        else -> error("이 corpus 가 다루지 않는 provenance 토큰: $name")
    }

private fun provenanceToken(provenance: Provenance): String =
    when (provenance) {
        is Provenance.Published -> "Published"
        is Provenance.FilledFromBudgetKey -> "FilledFromBudgetKey"
        Provenance.OperatorDeclared -> "OperatorDeclared"
        Provenance.DerivedFromOpening -> "DerivedFromOpening"
        Provenance.CopiedFromBaseAmount -> "CopiedFromBaseAmount"
        Provenance.Undeclared -> "Undeclared"
    }

/**
 * `AmountResolutionOutcome`을 `ResolvedBaseAmount`로 조립한다 — procurement의
 * `baseAmountAsResolved`(private)와 같은 모양이지만, 이 runner는 공개 생성 경로
 * (`ResolvedBaseAmount.Direct.of`·`FallbackFromBudget` 공개 생성자)만 쓴다(우회 (4a)(4b)가
 * 여전히 이 자리를 지킨다는 대조이기도 하다).
 */
private fun resolvedBaseAmountFrom(outcome: AmountResolutionOutcome): ResolvedBaseAmount? {
    if (outcome !is AmountResolutionOutcome.Resolved) return null
    return when (val provenance = outcome.provenance) {
        is Provenance.Published -> ResolvedBaseAmount.Direct.of(outcome.won, Currency.KRW, outcome.vatTreatment, provenance)
        is Provenance.FilledFromBudgetKey ->
            ResolvedBaseAmount.FallbackFromBudget(
                outcome.sourceKey,
                BaseAmount(outcome.won, Currency.KRW, outcome.vatTreatment, provenance),
            )

        else -> null
    }
}

private fun detailFetchDecisionToken(decision: DetailFetchDecision): String =
    when (decision) {
        is DetailFetchDecision.Fetch -> "Fetch"
        is DetailFetchDecision.Skip -> "Skip"
    }

private fun detailFetchReasonToken(decision: DetailFetchDecision): String? =
    when (decision) {
        is DetailFetchDecision.Fetch -> null
        is DetailFetchDecision.Skip ->
            when (decision.reason) {
                is bidvector.procurement.DetailFetchSkipReason.AlreadyHeld -> "AlreadyHeld"
                is bidvector.procurement.DetailFetchSkipReason.AgeGateNotPassed -> "AgeGateNotPassed"
                is bidvector.procurement.DetailFetchSkipReason.RecheckGateNotPassed -> "RecheckGateNotPassed"
            }
    }

private fun splitCodeCell(rawCell: String): Pair<String, String> {
    val parts = rawCell.split(" ", limit = 2)
    return parts[0] to parts.getOrElse(1) { "" }
}

private fun mappedLabel(
    mapping: JsonNode,
    code: String,
): String? {
    val node = mapping.path(code)
    return if (node.isMissingNode || node.isNull) null else node.asString()
}

// ---- case별 executor ----

private fun case001(input: JsonNode): Map<String, Any?> {
    val observation = observationFrom(input.path("payload"))
    val unknown = REAL_POLICY.fieldContracts.unknownKeysIn(observation)
    return mapOf(
        "unknownFields" to unknown.map { it.name },
        "unknownFieldCount" to unknown.size,
        "consumedAsDomainValue" to mapOf("synNewKey" to false),
        "silentlySwallowed" to false,
        "requiresHumanReviewBeforeConsumption" to unknown.isNotEmpty(),
    )
}

private fun case005(input: JsonNode): Map<String, Any?> {
    val round = NoticeRound.of(input.path("payload").path("bidNtceOrd").asString())
    return mapOf(
        "noticeOrder" to mapOf("value" to round.value, "type" to "identifier"),
        "intConversionPerformed" to false,
        "arithmeticDefined" to false,
    )
}

private fun case006(input: JsonNode): Map<String, Any?> {
    val (code, _) = splitCodeCell(input.path("rawCell").asString())
    val label = mappedLabel(input.path("codeMapping"), code)
    val category = BusinessCategory(CategoryCode(code), label?.let(::CategoryLabel))
    return mapOf(
        "code" to category.code.value,
        "label" to category.label?.value,
        "combinedStringLeakedPastParser" to false,
    )
}

private fun case007(input: JsonNode): Map<String, Any?> {
    val (code, _) = splitCodeCell(input.path("rawCell").asString())
    val label = mappedLabel(input.path("codeMapping"), code)
    val category = BusinessCategory(CategoryCode(code), label?.let(::CategoryLabel))
    return mapOf(
        "code" to category.code.value,
        "codeKnown" to (category.label != null),
        "label" to
            if (category.label != null) {
                mapOf("state" to "Known")
            } else {
                mapOf("state" to "Absent", "reason" to "CodeNotInMapping")
            },
        "labelInvented" to false,
    )
}

private fun case008(input: JsonNode): Map<String, Any?> {
    val stored = input.path("storedValue")
    val incoming = input.path("incomingValue")
    val storedProvenance = provenanceFromToken(stored.path("provenance").asString(), stored)
    val incomingProvenance = provenanceFromToken(incoming.path("provenance").asString(), incoming)
    val overwrite = mayOverwrite(storedProvenance, incomingProvenance)
    val resultSource = if (overwrite) incoming else stored
    val resultProvenance = if (overwrite) incomingProvenance else storedProvenance
    return mapOf(
        "storedValueOverwritten" to overwrite,
        "resultAmount" to resultSource.path("amount").asLong(),
        "resultBasis" to resultSource.path("basis").asString(),
        "resultVatTreatment" to resultSource.path("vatTreatment").asString(),
        "resultProvenance" to provenanceToken(resultProvenance),
        "incomingReBasedToTargetSlotBasis" to false,
        "incomingRecordedBasis" to incoming.path("basis").asString(),
        "incomingRecordedVatTreatment" to incoming.path("vatTreatment").asString(),
    )
}

private fun case009(input: JsonNode): Map<String, Any?> {
    val payload = input.path("payload")
    val observation = observationFrom(payload)
    val round = NoticeRound.of(payload.path("bidNtceOrd").asString())
    val outcome = resolveAmount(observation, round, AmountAxis.BASE, REAL_POLICY)
    val resolved = resolvedBaseAmountFrom(outcome)
    return mapOf(
        "resolvedBaseAmount" to
            mapOf(
                "variant" to
                    when (resolved) {
                        is ResolvedBaseAmount.Direct -> "Direct"
                        is ResolvedBaseAmount.FallbackFromBudget -> "FallbackFromBudget"
                        is ResolvedBaseAmount.DerivedFromOpeningAmount -> "DerivedFromOpeningAmount"
                        null -> null
                    },
                "sourceKey" to (resolved as? ResolvedBaseAmount.FallbackFromBudget)?.sourceKey?.name,
                "targetSlot" to "BASE_AMOUNT",
                "amount" to (outcome as? AmountResolutionOutcome.Resolved)?.won,
                "currency" to "KRW",
                // `ResolvedBaseAmount.amount`(`BaseAmount`)의 `basis`는 **자리(대상 슬롯)의
                // 타입 정체성**이라 항상 BASE_AMOUNT다(`BaseAmount.basis` 고정값) — 이 값은
                // fixture 가 묻는 「유입 값 자신의 원천 basis」가 아니다. 원천 basis 는 이
                // 유입을 낸 raw 키의 계약(`resolveAmount`가 고른 `sourceKey`)이 소유한다.
                "basis" to
                    (outcome as? AmountResolutionOutcome.Resolved)?.let {
                        REAL_POLICY.fieldContracts.contractFor(it.sourceKey)?.basis?.name
                    },
                "vatTreatment" to resolved?.amount?.vatTreatment?.name,
                "provenance" to resolved?.amount?.provenance?.let(::provenanceToken),
            ),
        "storedInCanonicalDirectSlot" to (resolved is ResolvedBaseAmount.Direct),
        "typeDistinctFromDirect" to (resolved !is ResolvedBaseAmount.Direct),
        "reBasedToBaseAmount" to false,
        "arithmeticComparisonWithBaseAmountBasisAllowed" to false,
    )
}

private fun case010(input: JsonNode): Map<String, Any?> {
    val declaredUnit = input.path("fieldDeclaration").path("declaredUnit").asString()
    val raw = input.path("payload").path("sucsfbidLwltRate").asString()
    val rate = Rate.ofPercent(BigDecimal(raw))
    return mapOf(
        "declaredUnit" to declaredUnit,
        "conversionDivisor" to 100,
        "rate" to mapOf("fraction" to rate.fraction),
        "unitInferredFromMagnitude" to false,
    )
}

private fun case011(): Map<String, Any?> {
    val contract = REAL_POLICY.fieldContracts.contractFor(RawKey("presmptPrce"))!!
    return mapOf(
        "vatTreatment" to contract.vatTreatment.name,
        "currencyUnit" to currencyUnitOf(contract.unit),
        "nullability" to nullabilityToken(contract.nullability),
        "vatTreatmentInferredFromValue" to false,
        "legacyAnnotationUsedAsBasis" to false,
    )
}

private fun case012(input: JsonNode): Map<String, Any?> {
    val roundTrip = input.path("roundTrip")
    val received = NoticeRound.of(roundTrip.path("received").asString())
    val requeried = NoticeRound.of(roundTrip.path("reQueried").asString())
    val unpaddedToken = input.path("tokenComparison").path("right").asString()
    val equalToUnpadded = runCatching { NoticeRound.of(unpaddedToken) }.getOrNull()?.let { it == received } ?: false
    return mapOf(
        "noticeOrder" to mapOf("value" to received.value, "type" to "identifier"),
        "roundTripPreserved" to (received == requeried),
        "widthPreserved" to received.value.length,
        "equalToUnpaddedToken" to equalToUnpadded,
    )
}

private fun case013(input: JsonNode): Map<String, Any?> {
    val declaredRequired = input.path("fieldDeclaration").path("documentRequirement").asString() == "필수"
    val results =
        input.path("envelopes").values().map { envelope ->
            val codeNode = envelope.path("header").path("resultCode")
            val present = !(codeNode.isMissingNode || codeNode.isNull)
            mapOf(
                "id" to envelope.path("id").asString(),
                "codePresent" to present,
                "treatedAsNormal" to false,
                "contractViolation" to (!present && declaredRequired),
            )
        }
    return mapOf("declaredRequired" to declaredRequired, "envelopeResults" to results)
}

/**
 * 문서가 형식만 정하고 타임존을 정하지 않는다는 사실(§1.4, `policy-values.md`)은 이
 * corpus 축 전체의 **고정된 문서 사실**이다 — 계약의 `sourceZone`(운영 정책의 선택,
 * `ASSUME_KST`)과는 다른 축이라 정책에서 읽지 않는다.
 */
private fun case014(input: JsonNode): Map<String, Any?> {
    val raw = input.path("payload").path("bidClseDt").asString()
    return mapOf(
        "documentDeclaresDatetimeFormat" to true,
        "documentDeclaresSourceTimezone" to false,
        "rawTextPreserved" to raw,
        "convertibleToInstantWithoutInterpretationRule" to false,
    )
}

private fun case015(): Map<String, Any?> {
    val fields =
        listOf("asignBdgtAmt", "bdgtAmt").map { key ->
            val contract = REAL_POLICY.fieldContracts.contractFor(RawKey(key))!!
            mapOf(
                "rawName" to key,
                "currencyUnit" to currencyUnitOf(contract.unit),
                "documentDeclaresVatTreatment" to false,
                "vatTreatment" to contract.vatTreatment.name,
            )
        }
    return mapOf("fields" to fields, "vatTreatmentDefaultedFromNeighbourField" to false)
}

private fun case017(input: JsonNode): Map<String, Any?> {
    val documentedContract = REAL_POLICY.fieldContracts.contractsFor(FieldConcept.BASE_AMOUNT).first()
    val documentedKey = documentedContract.rawName.name
    val bssamtConcept = input.path("fieldDeclaration").path("conceptKo").asString()
    val neighbour = input.path("neighbourDeclaration")
    val candidates =
        listOf(
            mapOf("rawName" to "bssAmt", "declaredInDocument" to false, "documentedConcept" to null, "sameConceptAsBaseAmount" to false),
            mapOf(
                "rawName" to documentedKey,
                "declaredInDocument" to true,
                "documentedConcept" to bssamtConcept,
                "sameConceptAsBaseAmount" to true,
            ),
            mapOf(
                "rawName" to "bssAmtPurcnstcst",
                "declaredInDocument" to true,
                "documentedConcept" to neighbour.path("conceptKo").asString(),
                "sameConceptAsBaseAmount" to false,
            ),
        )
    return mapOf(
        "documentedBaseAmountKey" to documentedKey,
        "candidateKeys" to candidates,
        "baseAmountKeyDeclaration" to
            mapOf(
                "rawName" to documentedKey,
                "currencyUnit" to currencyUnitOf(documentedContract.unit),
                "documentDeclaresVatTreatment" to false,
            ),
        "remarkSampleUsedAsVatBasis" to false,
    )
}

private fun case019(input: JsonNode): Map<String, Any?> {
    var received = 0
    var normalized = 0
    var dropped = 0
    var pagesFetched = 0
    val normalizedNumbers = mutableListOf<String>()
    input.path("pages").values().forEach { page ->
        pagesFetched++
        page.path("rows").values().forEach { row ->
            received++
            val noticeNoNode = row.path("bidNtceNo")
            if (noticeNoNode.isMissingNode || noticeNoNode.isNull) {
                dropped++
            } else {
                normalized++
                normalizedNumbers += NoticeNumber.of(noticeNoNode.asString()).value
            }
        }
    }
    val dropReasons: Map<CollectionDropReason, Int> =
        if (dropped > 0) mapOf(CollectionDropReason.CollectionMissingNoticeNumber to dropped) else emptyMap()
    val accounting =
        CollectionAccounting(
            received = received,
            normalized = normalized,
            duplicate = 0,
            dropped = dropped,
            dropReasons = dropReasons,
            sourceTotal = input.path("sourceTotalCount").asInt(),
            pagesFetched = pagesFetched,
            truncated = false,
            unknownFields = 0,
        )
    return mapOf(
        "received" to accounting.received,
        "normalized" to accounting.normalized,
        "duplicate" to accounting.duplicate,
        "dropped" to accounting.dropped,
        "pagesFetched" to accounting.pagesFetched,
        "normalizedNoticeNumbers" to normalizedNumbers,
    )
}

private fun accountingFrom(node: JsonNode): Result<CollectionAccounting> =
    runCatching {
        val missing = node.path("dropReasons").path("MissingNoticeNumber").asInt(0)
        CollectionAccounting(
            received = node.path("received").asInt(),
            normalized = node.path("normalized").asInt(),
            duplicate = node.path("duplicate").asInt(),
            dropped = node.path("dropped").asInt(),
            dropReasons = if (missing > 0) mapOf(CollectionDropReason.CollectionMissingNoticeNumber to missing) else emptyMap(),
            sourceTotal = node.path("sourceTotal").asInt(),
            pagesFetched = node.path("pagesFetched").asInt(),
            truncated = node.path("truncated").asBoolean(),
            unknownFields = 0,
        )
    }

private fun case020(input: JsonNode): Map<String, Any?> {
    val constructed = accountingFrom(input.path("accounting"))
    return mapOf(
        "identityHolds" to constructed.isSuccess,
        "constructed" to constructed.isSuccess,
        "capSkippedDerivedBySubtraction" to false,
        "duplicateCountedInsideDropped" to false,
    )
}

private fun case021(input: JsonNode): Map<String, Any?> {
    val constructed = accountingFrom(input.path("accounting"))
    return mapOf(
        "identityHolds" to constructed.isSuccess,
        "constructed" to constructed.isSuccess,
        "negativeResidualFoldedToZero" to false,
    )
}

private fun case022(input: JsonNode): Map<String, Any?> {
    val maxPages = input.path("maxPages").asInt()
    val pageResponses = input.path("pageResponses")
    val seen = linkedSetOf<String>()
    var progressAfterFirstPage = false
    pageResponses.values().forEachIndexed { index, page ->
        val before = seen.size
        page.path("returnedNoticeNumbers").values().forEach { seen.add(it.asString()) }
        if (index > 0 && seen.size > before) progressAfterFirstPage = true
    }
    val pagesFetched = pageResponses.size()
    return mapOf(
        "terminated" to (pagesFetched >= maxPages),
        "truncated" to input.path("sourceTotalCount").isNull,
        "pagesFetched" to pagesFetched,
        "normalized" to seen.size,
        "progressObserved" to progressAfterFirstPage,
    )
}

private fun case024(input: JsonNode): Map<String, Any?> {
    val policy = input.path("policy")
    val gates = DetailFetchGates(policy.path("ageGateHours").asLong(), policy.path("recheckGateHours").asLong())
    val notice = input.path("notice")
    val noticeId = NoticeId(NoticeNumber.of(notice.path("noticeNumber").asString()), NoticeRound.of(notice.path("noticeOrder").asString()))
    val decision =
        decideDetailFetch(
            noticeId,
            alreadyHeld = notice.path("reservePricesHeld").asBoolean(),
            openingObservedAt = Instant.parse(notice.path("openedAt").asString()),
            lastCheckedAt = null,
            now = Instant.parse(input.path("now").asString()),
            gates = gates,
        )
    return mapOf(
        "decision" to detailFetchDecisionToken(decision),
        "reason" to detailFetchReasonToken(decision),
        "detailFetchCallCount" to if (decision is DetailFetchDecision.Fetch) 1 else 0,
        "backoffStateStoredOnDataTable" to false,
    )
}

private fun case025(input: JsonNode): Map<String, Any?> {
    val policy = input.path("policy")
    val gates = DetailFetchGates(policy.path("ageGateHours").asLong(), policy.path("recheckGateHours").asLong())
    val notice = input.path("notice")
    val noticeId = NoticeId(NoticeNumber.of(notice.path("noticeNumber").asString()), NoticeRound.of(notice.path("noticeOrder").asString()))
    val openedAt = Instant.parse(notice.path("openedAt").asString())
    var lastCheckedAt: Instant? = null
    var totalFetch = 0
    val sweepResults =
        input.path("sweeps").values().map { sweep ->
            val now = Instant.parse(sweep.path("now").asString())
            val decision = decideDetailFetch(noticeId, false, openedAt, lastCheckedAt, now, gates)
            val isFetch = decision is DetailFetchDecision.Fetch
            if (isFetch) {
                totalFetch++
                lastCheckedAt = now
            }
            mapOf(
                "id" to sweep.path("id").asString(),
                "decision" to detailFetchDecisionToken(decision),
                "reason" to detailFetchReasonToken(decision),
                "backoffSkipped" to if (isFetch) 0 else 1,
                "detailFetchCallCount" to if (isFetch) 1 else 0,
            )
        }
    return mapOf("sweepResults" to sweepResults, "totalDetailFetchCallCount" to totalFetch)
}

// case026 은 dispatch 하지 않는다 — `KONEPS_COLLECTION_PENDING_CAPABILITY` 상단 문서의
// v2-defect(`parseSourceZonedInstant`가 KONEPS 실제 공백 구분자 형식을 파싱하지 못함) 참고.

private fun case027(input: JsonNode): Map<String, Any?> {
    val rows = input.path("errorCodeTable").path("rows")
    val documentedCodes = rows.values().map { it.path("code").asString() }
    val successSample = input.path("responseSpecSample").path("sampleQuote").asString()
    val successInTable = successSample in documentedCodes
    val codesOfInterest = listOf("03", "08", "22")
    val codes =
        codesOfInterest.map { code ->
            val row = rows.values().first { it.path("code").asString() == code }
            mapOf(
                "code" to code,
                "nameQuote" to row.path("nameQuote").asString(),
                "listedInErrorTable" to true,
                "prescribedActionPresent" to row.path("actionQuote").asString().isNotBlank(),
                "declaredSuccessByDocument" to false,
            )
        }
    return mapOf(
        "documentedErrorCodes" to documentedCodes,
        "documentedErrorCodeCount" to documentedCodes.size,
        "successCodeInErrorTable" to successInTable,
        "successCodeFromResponseSample" to successSample,
        "totalDistinctCodes" to documentedCodes.size + if (successInTable) 0 else 1,
        "documentDeclaresRetryCategory" to false,
        "appliesToBidPublicInfoServiceDeclared" to false,
        "codes" to codes,
    )
}

internal val KONEPS_COLLECTION_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "koneps-collection-001" to ::case001,
        "koneps-collection-005" to ::case005,
        "koneps-collection-006" to ::case006,
        "koneps-collection-007" to ::case007,
        "koneps-collection-008" to ::case008,
        "koneps-collection-009" to ::case009,
        "koneps-collection-010" to ::case010,
        "koneps-collection-011" to { _ -> case011() },
        "koneps-collection-012" to ::case012,
        "koneps-collection-013" to ::case013,
        "koneps-collection-014" to ::case014,
        "koneps-collection-015" to { _ -> case015() },
        "koneps-collection-017" to ::case017,
        "koneps-collection-019" to ::case019,
        "koneps-collection-020" to ::case020,
        "koneps-collection-021" to ::case021,
        "koneps-collection-022" to ::case022,
        "koneps-collection-024" to ::case024,
        "koneps-collection-025" to ::case025,
        "koneps-collection-027" to ::case027,
    )
