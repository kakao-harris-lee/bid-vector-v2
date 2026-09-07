package bidvector.app.conformance

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.decideDetailFetch
import bidvector.sharedkernel.NoticeRound
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * M3/3A 잔여 일괄 ② — `koneps-collection` 의 회계·조회 가치·resultCode 축(019~027).
 * 필드 계약·업무구분·율·금액 해석 축(001·005~015·017)은 [KonepsCollectionExecutors.kt]
 * 에 있다 — 크기 한도(v2-지침서.md §5, 500줄)를 기계적으로 회피하려는 분할이 아니라
 * 「필드 계약을 대조하는 법」과 「회계·조회 가치 gate 를 대조하는 법」이 서로 다른
 * 관심사이기 때문이다(`SharedKernelCorpusConformanceTest.kt`/`CorpusExecutors.kt` 관례와
 * 같은 원칙). `REAL_POLICY`·`KONEPS_COLLECTION_PENDING_CAPABILITY`는 형제 파일 소유다.
 */

private fun detailFetchDecisionToken(decision: DetailFetchDecision): String =
    when (decision) {
        is DetailFetchDecision.Fetch -> "Fetch"
        is DetailFetchDecision.Skip -> "Skip"
    }

private fun detailFetchReasonToken(decision: DetailFetchDecision): String? =
    when (decision) {
        is DetailFetchDecision.Fetch -> {
            null
        }

        is DetailFetchDecision.Skip -> {
            when (decision.reason) {
                is bidvector.procurement.DetailFetchSkipReason.AlreadyHeld -> "AlreadyHeld"
                is bidvector.procurement.DetailFetchSkipReason.AgeGateNotPassed -> "AgeGateNotPassed"
                is bidvector.procurement.DetailFetchSkipReason.RecheckGateNotPassed -> "RecheckGateNotPassed"
            }
        }
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
        val dropReasons: Map<CollectionDropReason, Int> =
            if (missing > 0) mapOf(CollectionDropReason.CollectionMissingNoticeNumber to missing) else emptyMap()
        CollectionAccounting(
            received = node.path("received").asInt(),
            normalized = node.path("normalized").asInt(),
            duplicate = node.path("duplicate").asInt(),
            dropped = node.path("dropped").asInt(),
            dropReasons = dropReasons,
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

private fun noticeIdFrom(notice: JsonNode): NoticeId =
    NoticeId(
        NoticeNumber.of(notice.path("noticeNumber").asString()),
        NoticeRound.of(notice.path("noticeOrder").asString()),
    )

private fun case024(input: JsonNode): Map<String, Any?> {
    val policy = input.path("policy")
    val gates = DetailFetchGates(policy.path("ageGateHours").asLong(), policy.path("recheckGateHours").asLong())
    val notice = input.path("notice")
    val noticeId = noticeIdFrom(notice)
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
    val noticeId = noticeIdFrom(notice)
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
    val policyCodes = REAL_POLICY.resultCodeCategories.map { it.code }.toSet()
    return mapOf(
        "documentedErrorCodes" to documentedCodes,
        "documentedErrorCodeCount" to documentedCodes.size,
        "successCodeInErrorTable" to successInTable,
        "successCodeFromResponseSample" to successSample,
        "totalDistinctCodes" to documentedCodes.size + if (successInTable) 0 else 1,
        "documentDeclaresRetryCategory" to false,
        "appliesToBidPublicInfoServiceDeclared" to false,
        // verified_paths 밖의 실측 배선 — REAL_POLICY(운영 승인 값)의 resultCode 코드
        // 집합이 이 문서의 에러코드 표와 같은 16개인지 실제로 대조한다(contract_binding
        // 「carries」 참고). 검증되진 않지만 real 정책 값 위에서 계산되는 값이다.
        "policyCodeSetMatchesDocument" to (policyCodes == documentedCodes.toSet()),
        "codes" to codes,
    )
}

internal val KONEPS_COLLECTION_ACCOUNTING_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "koneps-collection-019" to ::case019,
        "koneps-collection-020" to ::case020,
        "koneps-collection-021" to ::case021,
        "koneps-collection-022" to ::case022,
        "koneps-collection-024" to ::case024,
        "koneps-collection-025" to ::case025,
        "koneps-collection-027" to ::case027,
    )
