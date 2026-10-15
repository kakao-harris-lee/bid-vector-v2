package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.NoticeId
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** 어댑터가 429 를 관측한 횟수 — 재시도마다 센다(소진이면 1 보다 크다). */
private const val QUOTA_SIGNALS = 3

private const val SAMPLE_SIZE = 2

private const val SLOT_DAY = "2026-06-03"

/** 표본틀 — 표본보다 넉넉해야 「표본만 부른다」가 말이 된다. */
private const val FRAME_SIZE = 6

/**
 * COL-03 셋째 acceptance — **예비가격 상세가 429 로 재시도를 소진하면 그 공고는 예비가격 없이
 * 남고 그 실행은 멈추며, 사유가 회계에 실리고 다음 실행이 그 축을 이어받는다.**
 *
 * capability map 의 그 bullet 은 2026-10-16 에 V2 거동으로 개정됐다(운영자 승인) — 원래 문면은
 * 「나머지 수집은 **같은 실행 안에서** 계속」이었고, V2 는 쿼터 소진 뒤 남은 표본을 계속 부르면
 * 거부만 쌓이므로 멈추는 쪽을 택했다. 「계속」은 **다음 실행의 이어받기**로 읽는다.
 *
 * 인용 가능한 429 test 는 전부 「연속 실패 뒤 **성공**」(bounded retry)이고, 소진 뒤의 네 사실
 * (멈춤 · 그 공고의 결측 · 회계 · 이어받기)을 **한 사슬로** 잇는 자리는 없었다. 각 고리는 따로
 * 측정된다 — 멈춤은 K6 두 건, 어휘 분류는 원장 test, 이어 돌기는 원장을 **손으로 심은** test 들이
 * 잰다. 이 test 가 더하는 것은 **실행 1 이 적은 줄이 실행 2 의 입력이 되는가**다: 한 use case 를
 * 두 번 돌려 그 사이에 쿼터만 푼다(원장을 심지 않는다).
 */
class OpeningQuotaHaltResumptionTest {
    private val raw = RecordingRawStore()
    private val runs = RecordingRunStore()
    private val attempts = FakeAttemptLedger()
    private val sampleList = FakeSampleListLedger()
    private val port =
        QuotaScriptedOpeningPort(
            notices = (1..FRAME_SIZE).map { "SYN-G1T-COL03-%04d".format(it) },
            day = LocalDate.parse(SLOT_DAY),
        )

    private val useCase =
        CollectOpeningResultsUseCase(
            rawObservations = raw,
            runs = runs,
            sampler = StratifiedSampler(SamplingSeed("g1t-col03-seed"), SampleSize(SAMPLE_SIZE)),
            policyFor = { COLLECTION_POLICY },
            gates = COLLECTION_POLICY.detailFetchGates,
            collectedAxes = FakeCollectedAxisStore(),
            sampleList = sampleList,
            attempts = attempts,
            clock = Clock { COLLECTION_NOW },
        )

    private val sources =
        listOf(OpeningCollectionSource(sourceName("service"), BusinessDivision.SERVICE, port))

    private fun collect(): OpeningCollectionReport = useCase.collect(rangeOf(SLOT_DAY, SLOT_DAY), sources)

    private fun rawCount(endpoint: SourceEndpoint): Int = raw.appended.count { it.sourceEndpoint == endpoint }

    private fun axisConclusionsFor(endpoint: SourceEndpoint): List<AttemptOutcome> =
        attempts.appended.filter { it.kind == AttemptKind.AXIS && it.axis == endpoint }.map { it.outcome }

    @Test
    fun `예비가격 상세가 쿼터를 소진하면 그 실행은 멈추고 다음 실행이 그 축을 이어받는다`() {
        // 앞 공고는 받고 **둘째** 공고의 예비가격 축에서 막힌다 — 받은 것과 못 받은 것이 함께 있는 판.
        port.quotaFromReservePriceCall = 1

        val first = collect()

        val halt = requireNotNull(first.halted) { "쿼터 소진은 그 실행을 멈춰야 한다" }
        halt.truncationCause shouldBe TruncationCause.QuotaExhausted
        // 쿼터는 KONEPS 의 거절이다 — 우리 승인 상한(예산)과 같은 칸에 접지 않는다.
        halt.budgetLimit shouldBe null
        // 막힌 그 공고는 축 하나도 끝내지 못했으므로 반쪽이 아니고, 「손대지 않은」 수에 든다.
        halt.partialNotice shouldBe false
        halt.notAttempted shouldBe 1

        // 그 공고는 **예비가격 없이** 남는다 — 앞 공고의 한 행만 적재됐다.
        rawCount(SourceEndpoint.RESERVE_PRICE_DETAIL) shouldBe 1
        // 멈춤이므로 막힌 공고의 나머지 축은 부르지도 않는다.
        port.openingCompleteCalls.size shouldBe 1

        // 회계에 사유와 429 관측 수가 실린다 — use case 가 배치의 회계를 그대로 원장에 넘긴다.
        val quotaRun = runs.recorded.last { it.second.source == SourceEndpoint.RESERVE_PRICE_DETAIL }
        quotaRun.first.truncationCause shouldBe TruncationCause.QuotaExhausted
        quotaRun.first.truncated shouldBe true
        quotaRun.first.quotaExceeded shouldBe QUOTA_SIGNALS
        // 그 축의 결말은 **관문 거부**다 — 재호출 상한을 쓰지 않는 어휘(그래서 다음 실행이 다시 부른다).
        axisConclusionsFor(SourceEndpoint.RESERVE_PRICE_DETAIL)
            .last()
            .shouldBeInstanceOf<AttemptOutcome.Refused>()

        val blockedNotice = port.reservePriceCalls.last()
        val reservePriceCallsBefore = port.reservePriceCalls.size

        // 쿼터가 풀린 **다음 실행** — 원장을 심지 않는다. 실행 1 이 적은 줄이 그대로 입력이다.
        port.reservePriceQuotaExhausted = false
        val second = collect()

        second.halted shouldBe null
        // 이어받는 것은 **그 공고의 미완 축**뿐이다 — 끝난 축은 다시 부르지 않는다.
        port.reservePriceCalls.drop(reservePriceCallsBefore) shouldContainExactly listOf(blockedNotice)
        rawCount(SourceEndpoint.RESERVE_PRICE_DETAIL) shouldBe SAMPLE_SIZE
        port.openingCompleteCalls.size shouldBe SAMPLE_SIZE
        port.baseAmountCalls.size shouldBe SAMPLE_SIZE
        // 표본은 다시 뽑지 않는다 — 두 실행이 같은 표본을 본다.
        sampleList.confirmCount shouldBe 1
        second.sample.selected shouldContainExactly first.sample.selected
    }
}

/**
 * 개찰 수집 port 의 대역 — 이 test 의 축(쿼터 소진 → 멈춤 → 이어받기)에 필요한 것만 짓는다.
 *
 * 공유 대역(`ScriptedOpeningPort`)을 쓰지 않는 이유는 하나다: 회계의 **429 관측 수** 칸을 실어야
 * 하는데 그 대역은 그 칸을 늘 0 으로 둔다(그 칸은 어댑터가 센다). 그 칸을 바꾸려면 이 slice 의
 * in_scope 밖 파일을 고쳐야 한다. 관문 줄(의도·호출)도 적지 않는다 — 이 test 가 재는 것은 축의
 * **결말 줄**이 다음 실행의 입력이 되는가이고, 관문 줄의 셈은 상한 회계를 재는 test 들의 축이다.
 */
private class QuotaScriptedOpeningPort(
    private val notices: List<String>,
    private val day: LocalDate,
) : OpeningResultSourcePort {
    /** 예비가격 상세가 쿼터로 막혀 있는가 — 실행 **사이에** 바꾼다(실행 2 는 쿼터가 풀린 판이다). */
    var reservePriceQuotaExhausted: Boolean = true

    /** 몇 번째 예비가격 호출부터 막히는가(0-based). */
    var quotaFromReservePriceCall: Int = 0

    val reservePriceCalls = mutableListOf<NoticeId>()
    val openingCompleteCalls = mutableListOf<NoticeId>()
    val baseAmountCalls = mutableListOf<NoticeId>()

    override fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        val numbers = if (referenceDate.date == day) notices else emptyList()
        val items = numbers.map { rawRowOf(it, SourceEndpoint.OPENING_RESULT_LIST) }
        return batch(items, truncation = null)
    }

    override fun fetchReservePrices(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        val blocked = reservePriceQuotaExhausted && reservePriceCalls.size >= quotaFromReservePriceCall
        reservePriceCalls += evidence.noticeId
        // 재시도 소진은 **항목이 하나도 오지 않는다** — 그래서 그 공고가 예비가격 없이 남는다.
        if (blocked) return batch(emptyList(), TruncationCause.QuotaExhausted)
        return axisBatch(evidence, RESERVE_PRICE_AXIS)
    }

    override fun fetchOpeningCompleteResults(
        evidence: DetailFetchDecision.Fetch,
    ): SourceBatch<RawNoticeObservation> {
        openingCompleteCalls += evidence.noticeId
        return axisBatch(evidence, SourceEndpoint.OPENING_COMPLETE)
    }

    override fun fetchBaseAmount(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        baseAmountCalls += evidence.noticeId
        return axisBatch(evidence, SourceEndpoint.BASE_AMOUNT_DETAIL)
    }

    override fun fetchBidPriceFormulaA(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> =
        error("A값은 공사에서만 불린다 — 이 test 의 업무는 용역이다")

    private fun axisBatch(
        evidence: DetailFetchDecision.Fetch,
        endpoint: SourceEndpoint,
    ): SourceBatch<RawNoticeObservation> = batch(listOf(rawRowOf(evidence.noticeId.number.value, endpoint)), null)

    private fun batch(
        items: List<RawNoticeObservation>,
        truncation: TruncationCause?,
    ): SourceBatch<RawNoticeObservation> =
        SourceBatch(
            items,
            sourceAccounting(
                normalized = items.size,
                truncationCause = truncation,
                quotaExceeded = if (truncation == TruncationCause.QuotaExhausted) QUOTA_SIGNALS else 0,
            ),
            next = null,
            observedAt = COLLECTION_NOW,
        )
}

private val RESERVE_PRICE_AXIS = SourceEndpoint.RESERVE_PRICE_DETAIL

private fun rawRowOf(
    number: String,
    endpoint: SourceEndpoint,
): RawNoticeObservation =
    RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
        endpoint,
        COLLECTION_NOW,
    )
