package bidvector.workflow.collection

import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptLedger
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
        return SourceBatch(
            listOf(item),
            sourceAccounting(normalized = 1, truncationCause = truncation),
            next = null,
            // 대역도 걷기의 이름을 단다(D-6G-68) — 실물이 그렇고, 없으면 그 축이 0 행으로 읽힌다.
            observedAt = COLLECTION_NOW,
        )
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
    val construction = ScriptedOpeningPort(BusinessDivision.CONSTRUCTION)
    val service = ScriptedOpeningPort(BusinessDivision.SERVICE)

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
            gates = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48),
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
