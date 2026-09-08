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

/**
 * JSON 항목 하나 → [RawItemOutcome] 변환 전략(3B-2) — [walkKonepsNoticePages]가 걷기 로직을
 * 재사용하면서도 축마다 다른 매핑(공고 축은 [mapRawItem] 전체 보존, 개찰 축은
 * [mapMaskedOpeningItem] allow-list 치환)을 꽂아 넣게 한다.
 *
 * **기본값이 없다(verifier r1 F-4 수정)** — 이전 판은 [walkKonepsNoticePages]의 `itemMapper`
 * 매개변수가 [defaultKonepsItemMapper](원문 보존 mapper)로 기본값이 있어, 개찰 축 walker 가
 * 이 인자를 잊으면 **누출이 조용한 폴백**이 됐다(설계 검토 게이트 ①이 막으려던 것과 반대
 * 방향 — 실수의 기본 결과가 누출). 세 호출부([KonepsOpenApiNoticeSource.fetchNotices]·
 * [KonepsOpeningResultSource.fetchOpeningResults]·[fetchSingleKonepsNotice])가 모두 명시한다.
 */
internal fun interface KonepsItemMapper {
    operator fun invoke(
        item: JsonValue.JsonObject,
        policy: KonepsCollectionPolicyData,
        observedAt: Instant,
    ): RawItemOutcome
}

/**
 * 기존 3B 동작(공고 목록, 계약 여부와 무관하게 전 필드 보존) — 낙찰 목록군 오퍼레이션(한 행=한
 * 공고)의 행 식별자는 공고번호·차수뿐이라 `rowIdentifierRawKeys`는 `emptyList()`다. F-4 로 이
 * 값의 기본값 지위는 없어졌다 — [KonepsOpenApiNoticeSource.fetchNotices]가 명시적으로 참조한다.
 */
internal val defaultKonepsItemMapper: KonepsItemMapper =
    KonepsItemMapper { item, policy, observedAt ->
        mapRawItem(item, policy, SourceEndpoint.NOTICE_LIST, observedAt, rowIdentifierRawKeys = emptyList())
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

    // verifier r1 F-8 수정(운영자 승인 2026-09-08, 3A Accounting.kt 좁은 확장) — masking 실패
    // (opengCorpInfo 성분 배치 불일치, 값 전체 폐기)를 unknownFields 와 별도로 센다.
    var maskingFailures = 0
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
        itemMapper: KonepsItemMapper,
    ) {
        pagesFetched++
        sourceTotal = page.totalCount ?: sourceTotal
        lastPageSignature = signature
        for (rawItem in page.items) recordItem(rawItem, observedAt, policy, itemMapper)
    }

    private fun recordItem(
        rawItem: JsonValue.JsonObject,
        observedAt: Instant,
        policy: KonepsCollectionPolicyData,
        itemMapper: KonepsItemMapper,
    ) {
        when (val mapped = itemMapper(rawItem, policy, observedAt)) {
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
            maskingFailures += mapped.maskingFailureCount
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

    /**
     * M-3 — truncated 로 끝났으면 재개 지점을 실은 cursor, 아니면 null(완료를 거짓 진술하지
     * 않는다). **N-4(verifier r2)** — 사유가 비재시도 축(`NotRetryable`·`Unclassified`·
     * `InputError`)이면 `next` 를 안 낸다. 같은 cursor 로 다시 불러도 서버 응답·요청 형태가
     * 안 바뀌는 한 같은 실패가 재현될 뿐이라, `truncationCause` 를 안 보고 `next` 만 따라가는
     * 소비자가 같은 실패를 무한 재개하는 것을 막는다.
     */
    fun nextCursor(): PageCursor? =
        truncationCause
            ?.takeIf(::isResumable)
            ?.let { resumePageNo }
            ?.let { PageCursor(it.toString()) }

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
            maskingFailures = maskingFailures,
        )
}

/**
 * N-4(verifier r2) — 사유가 「다시 시도하면 뚫릴 수 있는」 축(백스톱·전송·quota·rate limiter
 * 자체 거부)이면 재개 가능, 「입력·구성 자체가 틀렸다」 축(비재시도 resultCode·미지 코드·
 * 무효 cursor)이면 재개 불가로 둔다 — `when` 이 [TruncationCause] 전 분기를 소진해 새 사유가
 * 추가되면 컴파일이 깨진다(회귀 방지).
 */
private fun isResumable(cause: TruncationCause): Boolean =
    when (cause) {
        TruncationCause.MaxPages,
        TruncationCause.RepeatedPage,
        TruncationCause.QuotaExhausted,
        TruncationCause.Timeout,
        TruncationCause.TransportFailure,
        TruncationCause.ServerError,
        TruncationCause.SelfThrottled,
        // 구조 실패는 서버가 그 순간 보낸 응답이 무너졌다는 관측이지 입력·구성이 틀렸다는
        // 판정이 아니다 — 다음 호출은 정상 JSON 을 낼 수 있어 재개 가능 축에 둔다.
        TruncationCause.StructureFailure,
        -> true

        TruncationCause.NotRetryable,
        TruncationCause.Unclassified,
        TruncationCause.InputError,
        -> false
    }

private fun applySuccess(
    accumulator: KonepsPageWalkAccumulator,
    page: KonepsEnvelopeOutcome.Success,
    policy: KonepsCollectionPolicyData,
    clock: Clock,
    pageNo: Int,
    itemMapper: KonepsItemMapper,
): WalkStep {
    val signature = page.items.map { it.render() }
    if (accumulator.isRepeatOf(signature)) {
        accumulator.recordRepeatedPage(pageNo)
        return WalkStep.STOP
    }
    accumulator.recordPage(page, signature, clock.instant(), policy, itemMapper)
    return if (accumulator.currentlyComplete() || page.items.isEmpty()) WalkStep.STOP else WalkStep.CONTINUE
}

private fun applyOutcome(
    accumulator: KonepsPageWalkAccumulator,
    outcome: KonepsCallOutcome,
    policy: KonepsCollectionPolicyData,
    clock: Clock,
    pageNo: Int,
    itemMapper: KonepsItemMapper,
): WalkStep =
    when (outcome) {
        is KonepsCallOutcome.Success -> {
            applySuccess(accumulator, outcome.body, policy, clock, pageNo, itemMapper)
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
    val itemMapper: KonepsItemMapper,
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
    val step = applyOutcome(accumulator, outcome, context.collectionPolicy, context.clock, pageNo, context.itemMapper)
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

// N-5(verifier r2) — `toIntOrNull()` 단독은 "007"·"+4" 처럼 표기가 관대한 토큰도 받아준다
// (선행 0·부호 기호). 이 어댑터가 스스로 내는 cursor(`resumePageNo.toString()`)는 항상 이
// 형식(선행 0·부호 없는 순수 양의 정수)이므로 실질 위험은 낮지만, 「엄격 파싱」을 형태
// 자체로 강제해 다른 발급자가 끼어들 여지를 남기지 않는다.
private val STRICT_PAGE_TOKEN = Regex("[1-9][0-9]*")

private fun startPageOf(cursor: PageCursor?): Int? {
    val token = cursor?.token ?: return START_PAGE
    return token.takeIf(STRICT_PAGE_TOKEN::matches)?.toIntOrNull()
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
    // 3B-2 — 축마다 다른 항목 매핑을 꽂는다. **기본값 없음(verifier r1 F-4)** — 공고 축
    // 호출부([KonepsOpenApiNoticeSource.fetchNotices])도 [defaultKonepsItemMapper]를 명시한다.
    itemMapper: KonepsItemMapper,
): SourceBatch<RawNoticeObservation> {
    val startPage = startPageOf(cursor) ?: return invalidCursorBatch()
    val counters = KonepsAttemptCounters()
    val context =
        KonepsWalkContext(
            httpClient,
            retry,
            rateLimiter,
            uriBuilder,
            httpPolicy,
            collectionPolicy,
            clock,
            counters,
            itemMapper,
        )
    val accumulator = KonepsPageWalkAccumulator()
    var pageNo = startPage
    var walking = true
    while (walking) {
        walking = nextWalkState(accumulator, context, pageNo)
        if (walking) pageNo++
    }
    return SourceBatch(accumulator.items, accumulator.toAccounting(counters), next = accumulator.nextCursor())
}
