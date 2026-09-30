package bidvector.workflow.collection

import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptLedger
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BusinessDivision
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.NoticeId
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.strategy.Clock
import java.time.LocalDate

/** 6G 수집 갈래 test 의 fake 포트·조립 — 값과 호출 기록뿐이고 mock framework 는 없다. */
internal class ScriptedOpeningPort(
    private val division: BusinessDivision,
    /**
     * 출하 경로에서 상세 호출은 관문(`KonepsCallGate`)을 지나고, 그 자리가 **의도 줄과 호출 줄**을
     * 원장에 남긴다(D-6G-61 ①). 대역이 그것을 빠뜨리면 「결말 없이 끝난 호출」이 원장에 아예 없어
     * 그 셈(cr r4 ②)을 이 harness 로 잴 수 없다.
     *
     * 절단으로 돌아온 호출에는 줄을 남기지 않는다 — 관문 거부는 **호출 전에** 접히므로 실물도 남기지
     * 않고(D-6G2d-16), 그 밖의 절단은 축의 결말 줄이 이미 그 라운드를 센다.
     */
    private val attempts: AttemptLedger? = null,
) : OpeningResultSourcePort {
    /** (공고일 → 그 슬롯이 낼 공고번호들). */
    val listScript = mutableMapOf<LocalDate, List<String>>()
    var listTruncation: TruncationCause? = null
    var listPagesFetched: Int = 1

    val listCalls = mutableListOf<LocalDate>()
    val reservePriceCalls = mutableListOf<NoticeId>()
    val openingCompleteCalls = mutableListOf<NoticeId>()
    val formulaACalls = mutableListOf<NoticeId>()

    /** 이 축의 응답을 쿼터 소진으로 만든다 — 상세 단계의 멈춤을 재는 자리(K6). */
    var detailTruncation: TruncationCause? = null

    /** 몇 번째 상세 호출부터 절단인가(0-based) — 「앞 축은 받았고 이 축에서 막혔다」를 짓는다. */
    var detailTruncationFromCall: Int = 0

    /**
     * 한 걷기가 거는 **쪽 수**(D-6G2d-42) — 실물은 쪽마다 관문을 지나므로 원장에 쪽마다 두 줄이 남는다.
     * 크래시 라운드를 「라운드 하나」로 세는지 재려면 쪽이 여럿인 축이 필요하다.
     */
    var detailPagesPerCall: Int = 1

    private var detailCallCount = 0

    override fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        listCalls += referenceDate.date
        // 한 장도 못 받은 배치에는 항목이 없다 — 페이지를 못 받았는데 행이 오는 모양은 실물에 없다.
        val numbers = if (listPagesFetched == 0) emptyList() else listScript[referenceDate.date].orEmpty()
        val items = numbers.map { openingListObservation(it) }
        return SourceBatch(
            items,
            sourceAccounting(
                normalized = items.size,
                pagesFetched = listPagesFetched,
                truncationCause = listTruncation,
            ),
            next = null,
            // 목록 갈래 대역도 걷기의 이름을 단다(D-6G2d-4 ⓓ) — 빈 배치도 걷기는 돌았다.
            observedAt = COLLECTION_NOW,
        )
    }

    override fun fetchReservePrices(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        reservePriceCalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.RESERVE_PRICE_DETAIL)
    }

    override fun fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        openingCompleteCalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.OPENING_COMPLETE)
    }

    val baseAmountCalls = mutableListOf<NoticeId>()

    override fun fetchBaseAmount(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        baseAmountCalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.BASE_AMOUNT_DETAIL)
    }

    override fun fetchBidPriceFormulaA(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        check(division == BusinessDivision.CONSTRUCTION) { "A값은 공사에서만 불려야 한다" }
        formulaACalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.BID_PRICE_FORMULA_A)
    }

    private fun detailBatch(
        evidence: DetailFetchDecision.Fetch,
        endpoint: SourceEndpoint,
    ): SourceBatch<RawNoticeObservation> {
        val item = observationOf(evidence.noticeId.number.value, endpoint)
        val truncation = detailTruncation.takeIf { detailCallCount >= detailTruncationFromCall }
        detailCallCount++
        if (truncation == null) repeat(detailPagesPerCall) { recordCallLines(evidence, endpoint) }
        return SourceBatch(
            listOf(item),
            sourceAccounting(normalized = 1, truncationCause = truncation),
            next = null,
            // 대역도 걷기의 이름을 단다(D-6G-68) — 실물이 그렇고, 없으면 그 축이 0 행으로 읽힌다.
            observedAt = COLLECTION_NOW,
        )
    }

    /** 관문이 적는 두 줄 — 한 줄이 한 호출이다(D-6G-61 ①). 호출 단위 줄은 걷기를 모른다(D-6G2d-4 ⓒ). */
    private fun recordCallLines(
        evidence: DetailFetchDecision.Fetch,
        endpoint: SourceEndpoint,
    ) {
        val ledger = attempts ?: return
        val key = NoticeKeyHash.of(evidence.noticeId.number.value, evidence.noticeId.round.value).value
        listOf(AttemptKind.PENDING, AttemptKind.HTTP).forEach { kind ->
            ledger.append(CollectionAttempt(key, endpoint, AttemptOutcome.Succeeded, COLLECTION_NOW, kind, null))
        }
    }
}

private fun openingListObservation(number: String): RawNoticeObservation =
    observationOf(number, SourceEndpoint.OPENING_RESULT_LIST)

private fun observationOf(
    number: String,
    endpoint: SourceEndpoint,
): RawNoticeObservation =
    RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
        endpoint,
        COLLECTION_NOW,
    )

/**
 * 이어 돌기 입력 — [completedAxes] 에 든 축은 **표본 전부**가 이미 받은 것으로 본다. 공고를 골라
 * 지정하지 않는 이유는 표본이 seed 로 정해져 test 가 어느 공고가 뽑힐지에 기대면 안 되기 때문이다
 * (그리고 공고번호는 canonical 화에서 대문자가 된다 — 원문으로 맞추면 조용히 빗나간다).
 */
internal class FakeCollectedAxisStore(
    private val completedAxes: Set<SourceEndpoint> = emptySet(),
) : bidvector.procurement.CollectedAxisStore {
    override fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<bidvector.procurement.NoticeId>,
    ): Set<bidvector.procurement.NoticeId> = if (endpoint in completedAxes) noticeIds.toSet() else emptySet()
}

/** 메모리 시도 원장 — 파일 어댑터와 같은 규칙이다: 덧붙이기만 하고 읽기는 값이다. */
internal class FakeAttemptLedger(
    seed: List<CollectionAttempt> = emptyList(),
) : AttemptLedger {
    val appended = seed.toMutableList()

    override fun append(attempt: CollectionAttempt) {
        appended += attempt
    }

    override fun read(): AttemptHistory = AttemptHistory(appended.toList())
}

/** 메모리 원장 — 파일 어댑터와 같은 규칙이다: 한 번 확정하면 덮어쓰지 않는다. */
internal class FakeSampleListLedger : SampleListLedger {
    var confirmCount: Int = 0
        private set
    private var stored: SampleList? = null

    override fun confirmed(): SampleList? = stored

    override fun confirm(confirmation: SampleConfirmation): SampleList {
        stored?.let { return it }
        confirmCount++
        val sample = confirmation.sample
        return SampleList(sample.strataByKey, sample.strata, sample.requested, confirmation.scope)
            .also { stored = it }
    }
}

/**
 * 운영 정책의 재호출 상한 — test 가 그 값을 다시 적지 않는다(두 자리에 같은 수를 두지 않는다).
 * 상한 회계를 재는 판이 둘이라 공통 대역에 둔다.
 */
internal val POLICY_RETRY_LIMIT = COLLECTION_POLICY.detailFetchGates.axisRetryLimit

internal class OpeningFixture(
    sampleSize: Int,
    collectedAxes: bidvector.procurement.CollectedAxisStore = FakeCollectedAxisStore(),
    attemptSeed: List<CollectionAttempt> = emptyList(),
    /** 적재가 실패하는 축(D-6G-58 ⓑ) — 없으면 전 축이 적재된다. */
    rawFailsOn: SourceEndpoint? = null,
) {
    val sampleList = FakeSampleListLedger()
    val attempts = FakeAttemptLedger(attemptSeed)
    val raw = RecordingRawStore(rawFailsOn)
    val runs = RecordingRunStore()
    val construction = ScriptedOpeningPort(BusinessDivision.CONSTRUCTION, attempts)
    val service = ScriptedOpeningPort(BusinessDivision.SERVICE, attempts)

    private val sources =
        listOf(
            OpeningCollectionSource(sourceName("construction"), BusinessDivision.CONSTRUCTION, construction),
            OpeningCollectionSource(sourceName("service"), BusinessDivision.SERVICE, service),
        )

    private val useCase =
        CollectOpeningResultsUseCase(
            rawObservations = raw,
            runs = runs,
            sampler = StratifiedSampler(SamplingSeed("6g-test-seed"), SampleSize(sampleSize)),
            policyFor = { COLLECTION_POLICY },
            gates = COLLECTION_POLICY.detailFetchGates,
            collectedAxes = collectedAxes,
            sampleList = sampleList,
            attempts = attempts,
            clock = Clock { COLLECTION_NOW },
        )

    private var from: LocalDate? = null
    private var to: LocalDate? = null

    fun listRows(
        division: BusinessDivision,
        noticeDate: String,
        count: Int,
    ) {
        val day = LocalDate.parse(noticeDate)
        val port = portFor(division)
        // 합성 공고번호에 업무·공고일을 섞는다 — 같은 번호를 다른 층에 두면 키 해시가 같아 후보가 접힌다.
        port.listScript[day] = (1..count).map { "SYN-6G-$division-$noticeDate-%04d".format(it) }
        from = listOfNotNull(from, day).min()
        to = listOfNotNull(to, day).max()
    }

    /** 그 슬롯이 이번엔 아무것도 내지 않는다 — 목록 축 실패를 표본틀에 재현한다. */
    fun dropListRows(
        division: BusinessDivision,
        noticeDate: String,
    ) {
        portFor(division).listScript.remove(LocalDate.parse(noticeDate))
    }

    fun run(): OpeningCollectionReport = useCase.collect(range(), sources)

    fun plan(): SampleOutcome = useCase.plan(range(), sources).sample

    fun hashOf(id: NoticeId): String = NoticeKeyHash.of(id.number.value, id.round.value).value

    private fun portFor(division: BusinessDivision): ScriptedOpeningPort =
        when (division) {
            BusinessDivision.CONSTRUCTION -> construction
            else -> service
        }

    private fun range(): CollectionRange = rangeOf(requireNotNull(from).toString(), requireNotNull(to).toString())
}

internal val COLLECTION_BUDGET_DAY: LocalDate = LocalDate.ofInstant(COLLECTION_NOW, COLLECTION_BUDGET_ZONE)
