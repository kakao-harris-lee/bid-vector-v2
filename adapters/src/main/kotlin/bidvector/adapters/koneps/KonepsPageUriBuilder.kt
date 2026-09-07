package bidvector.adapters.koneps

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.PageCursor
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant

/** [walkKonepsNoticePages] 한 스텝이 요구하는 페이지별 URI 조립 — 조회일·페이지 번호만 안다. */
internal fun interface KonepsPageUriBuilder {
    fun uriFor(pageNo: Int): java.net.URI
}

private const val START_PAGE = 1

private enum class WalkStep { CONTINUE, STOP }

/**
 * page-walk 누적 상태(④⑤, COL-06) — 함수 하나 50줄 한도 때문에 루프 본체에서 분리한
 * 상태 기계다. `seenIdentities`는 **걷기 전체**(페이지 안 + 재시도로 다시 온 페이지)에
 * 걸쳐 dedup 한다 — 「재시도된 페이지의 항목이 배치에 중복 삽입되지 않는다」(scope.md
 * 완료 조건)가 페이지 하나만이 아니라 걷기 전체의 요구이기 때문이다.
 */
private class KonepsPageWalkAccumulator {
    val items = mutableListOf<RawNoticeObservation>()
    private val seenIdentities = mutableSetOf<NoticeIdentity>()
    private val dropReasons = mutableMapOf<CollectionDropReason, Int>()
    var duplicate = 0
        private set
    var dropped = 0
        private set
    var unknownFields = 0
        private set
    var pagesFetched = 0
        private set
    var truncated = false
        private set
    var sourceTotal: Int? = null
        private set
    private var truncationCause: TruncationCause? = null
    private var resumePageNo: Int? = null
    private var lastPageSignature: List<String>? = null

    fun isRepeatOf(signature: List<String>): Boolean = lastPageSignature != null && lastPageSignature == signature

    fun recordPage(
        page: KonepsEnvelopeOutcome.Success,
        signature: List<String>,
        observedAt: Instant,
        policy: KonepsCollectionPolicyData,
    ) {
        pagesFetched++
        sourceTotal = page.totalCount ?: sourceTotal
        lastPageSignature = signature
        for (rawItem in page.items) recordItem(rawItem, observedAt, policy)
    }

    private fun recordItem(
        rawItem: JsonValue.JsonObject,
        observedAt: Instant,
        policy: KonepsCollectionPolicyData,
    ) {
        when (val mapped = mapRawItem(rawItem, policy, SourceEndpoint.NOTICE_LIST, observedAt)) {
            is RawItemOutcome.Mapped -> recordMapped(mapped)
            is RawItemOutcome.Dropped -> recordDrop(mapped.reason)
        }
    }

    private fun recordMapped(mapped: RawItemOutcome.Mapped) {
        if (mapped.identity != null && !seenIdentities.add(mapped.identity)) {
            duplicate++
        } else {
            items += mapped.observation
            unknownFields += mapped.unknownFieldCount
        }
    }

    private fun recordDrop(reason: CollectionDropReason) {
        dropped++
        dropReasons[reason] = (dropReasons[reason] ?: 0) + 1
    }

    fun recordNoData() {
        pagesFetched++
        sourceTotal = sourceTotal ?: 0
    }

    /**
     * L-8(verifier r1) — 반복 감지로 걷기를 멈추는 페이지도 HTTP 호출은 실제로 나갔으니
     * `pagesFetched` 에 센다(항목은 이미 이전 페이지에서 셌으므로 다시 세지 않는다).
     */
    fun recordRepeatedPage(resumeAt: Int) {
        pagesFetched++
        markTruncated(TruncationCause.RepeatedPage, resumeAt)
    }

    /** M-3·H-3(verifier r1) — 사유와 재개 지점을 함께 남긴다. */
    fun markTruncated(
        cause: TruncationCause,
        resumeAt: Int,
    ) {
        truncated = true
        truncationCause = cause
        resumePageNo = resumeAt
    }

    fun currentlyComplete(): Boolean {
        val total = sourceTotal ?: return false
        return items.size + duplicate + dropped >= total
    }

    /** M-3 — truncated 로 끝났으면 재개 지점을 실은 cursor, 아니면 null(완료를 거짓 진술하지 않는다). */
    fun nextCursor(): PageCursor? = if (truncated) resumePageNo?.let { PageCursor(it.toString()) } else null

    fun toAccounting(counters: KonepsAttemptCounters): CollectionAccounting =
        CollectionAccounting(
            received = items.size + duplicate + dropped,
            normalized = items.size,
            duplicate = duplicate,
            dropped = dropped,
            dropReasons = dropReasons.toMap(),
            sourceTotal = sourceTotal,
            pagesFetched = pagesFetched,
            truncated = truncated,
            unknownFields = unknownFields,
            truncationCause = truncationCause,
            quotaExceeded = counters.quotaExceeded,
            backoffSkipped = counters.backoffSkipped,
        )
}

private fun applySuccess(
    accumulator: KonepsPageWalkAccumulator,
    page: KonepsEnvelopeOutcome.Success,
    policy: KonepsCollectionPolicyData,
    clock: Clock,
    pageNo: Int,
): WalkStep {
    val signature = page.items.map { it.render() }
    if (accumulator.isRepeatOf(signature)) {
        accumulator.recordRepeatedPage(pageNo)
        return WalkStep.STOP
    }
    accumulator.recordPage(page, signature, clock.instant(), policy)
    return if (accumulator.currentlyComplete() || page.items.isEmpty()) WalkStep.STOP else WalkStep.CONTINUE
}

private fun applyOutcome(
    accumulator: KonepsPageWalkAccumulator,
    outcome: KonepsCallOutcome,
    policy: KonepsCollectionPolicyData,
    clock: Clock,
    pageNo: Int,
): WalkStep =
    when (outcome) {
        is KonepsCallOutcome.Success -> {
            applySuccess(accumulator, outcome.body, policy, clock, pageNo)
        }

        KonepsCallOutcome.NoData -> {
            accumulator.recordNoData()
            WalkStep.STOP
        }

        is KonepsCallOutcome.Failed -> {
            accumulator.markTruncated(outcome.cause, pageNo)
            WalkStep.STOP
        }

        is KonepsCallOutcome.Throttled -> {
            accumulator.markTruncated(TruncationCause.SelfThrottled, pageNo)
            WalkStep.STOP
        }
    }

/** page-walk 한 번을 이루는 고정 배선 — 매개변수 개수를 줄이려고 묶은 값 전달 객체(설계 판단, 로직 없음). */
private class KonepsWalkContext(
    val httpClient: HttpClient,
    val retry: Retry,
    val rateLimiter: RateLimiter,
    val uriBuilder: KonepsPageUriBuilder,
    val httpPolicy: KonepsHttpPolicyData,
    val collectionPolicy: KonepsCollectionPolicyData,
    val clock: Clock,
    val counters: KonepsAttemptCounters,
)

/** 페이지 하나를 부르고 누적한다 — 반환값은 「다음 페이지로 계속할지」. */
private fun fetchNextPage(
    accumulator: KonepsPageWalkAccumulator,
    context: KonepsWalkContext,
    pageNo: Int,
): Boolean {
    val uri = context.uriBuilder.uriFor(pageNo)
    val outcome =
        fetchPageResilient(
            context.httpClient,
            context.retry,
            context.rateLimiter,
            uri,
            context.httpPolicy,
            context.collectionPolicy,
            context.counters,
        )
    val step = applyOutcome(accumulator, outcome, context.collectionPolicy, context.clock, pageNo)
    return step == WalkStep.CONTINUE
}

private fun nextWalkState(
    accumulator: KonepsPageWalkAccumulator,
    context: KonepsWalkContext,
    pageNo: Int,
): Boolean =
    if (accumulator.pagesFetched >= context.httpPolicy.maxPages) {
        accumulator.markTruncated(TruncationCause.MaxPages, pageNo)
        false
    } else {
        fetchNextPage(accumulator, context, pageNo)
    }

/** M-5(verifier r1) — cursor 토큰이 숫자가 아니거나 0 이하면 조용히 page 1 로 접지 않고 명시 실패를 낸다. */
private fun invalidCursorBatch(): SourceBatch<RawNoticeObservation> {
    val accounting =
        CollectionAccounting(
            received = 0,
            normalized = 0,
            duplicate = 0,
            dropped = 0,
            dropReasons = emptyMap(),
            sourceTotal = null,
            pagesFetched = 0,
            truncated = true,
            unknownFields = 0,
            truncationCause = TruncationCause.InputError,
        )
    return SourceBatch(emptyList(), accounting, next = null)
}

private fun startPageOf(cursor: PageCursor?): Int? {
    val token = cursor?.token ?: return START_PAGE
    return token.toIntOrNull()?.takeIf { it > 0 }
}

/**
 * 공고 목록 page-walk(④⑤, COL-06) — `cursor`가 시작 페이지, 종료는 `totalCount` 도달·짧은
 * 페이지·백스톱(최대 페이지·동일 페이지 반복) 중 먼저 오는 쪽이다. **한 번의 호출이 전체
 * 걷기를 담당한다** — `NoticeSourcePort.fetchNotices`는 「페이지 하나=port 호출 하나」가
 * 아니라 「조회일 하나(부터 cursor 재개 지점까지)=port 호출 하나」다(design judgment,
 * checklist.md 「판단이 갈린 지점」).
 */
internal fun walkKonepsNoticePages(
    httpClient: HttpClient,
    retry: Retry,
    rateLimiter: RateLimiter,
    uriBuilder: KonepsPageUriBuilder,
    httpPolicy: KonepsHttpPolicyData,
    collectionPolicy: KonepsCollectionPolicyData,
    clock: Clock,
    cursor: PageCursor?,
): SourceBatch<RawNoticeObservation> {
    val startPage = startPageOf(cursor) ?: return invalidCursorBatch()
    val counters = KonepsAttemptCounters()
    val context =
        KonepsWalkContext(httpClient, retry, rateLimiter, uriBuilder, httpPolicy, collectionPolicy, clock, counters)
    val accumulator = KonepsPageWalkAccumulator()
    var pageNo = startPage
    var walking = true
    while (walking) {
        walking = nextWalkState(accumulator, context, pageNo)
        if (walking) pageNo++
    }
    return SourceBatch(accumulator.items, accumulator.toAccounting(counters), next = accumulator.nextCursor())
}
