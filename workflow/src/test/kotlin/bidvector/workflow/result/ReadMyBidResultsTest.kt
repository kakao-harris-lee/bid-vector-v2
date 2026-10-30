package bidvector.workflow.result

import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.SubmittedBidAmount
import bidvector.bidding.tag
import bidvector.settlement.AwardMatchingPolicyData
import bidvector.settlement.AwardWinnerTradeName
import bidvector.settlement.OperatorTradeName
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import bidvector.workflow.opening.CanonicalOpeningFacts
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.opening.OpeningResultFigures
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.opening.ProvisionalRankOne
import bidvector.workflow.profile.TradeName
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

/** 심어 두고 응답 어디에도 나타나면 안 되는 표지 — 낙찰자 상호의 자리를 값으로 잰다. */
private const val WINNER_SENTINEL = "ZZSENTINELWINNERZZ"

/**
 * 조회 조립(M7/7C⑤-b) — **mock framework 없이** fake port 와 값으로만 잰다.
 *
 * 여기서 잠기는 것: 다른 계정의 건이 섞이지 않는다는 것(우회 ④) · 낙찰자 상호가 조립을 지나
 * **산출 값에 남지 않는다**는 것(우회 ①의 도메인 층) · 공고를 못 찾는 건이 조용히 사라지지
 * 않는다는 것 · 두 축이 **병기**되고 어느 쪽도 덮지 않는다는 것.
 */
class ReadMyBidResultsTest {
    private val now = Instant.parse("2026-10-31T02:00:00Z")
    private val me = AccountId(1L)
    private val other = AccountId(2L)
    private val matchingPolicy = AwardMatchingPolicyData(listOf("주식회사", "(주)"))

    private fun noticeKey(number: String) = NoticeKey(number, NoticeRound("001"))

    private fun recorded(
        caseId: Long,
        number: String,
        state: BidState = BidState.BidRecorded,
        won: Long = 1_200_000L,
        kind: BidKind = BidKind.REAL,
    ): RecordedRealBid? =
        recordedRealBidOrNull(
            kind = kind,
            caseId = BidCaseId(caseId),
            notice = noticeKey(number),
            recordedState = state.tag,
            amount = SubmittedBidAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, BidAmountOrigin.OperatorEntered),
            submittedAt = Instant.parse("2026-10-19T05:00:00Z"),
            confirmedAt = Instant.parse("2026-10-19T06:00:00Z"),
        )

    /** 실투찰 건 하나 — 페이퍼를 넘기면 값이 서지 않으므로 이 helper 는 `!!` 로 그 사실을 드러낸다. */
    private fun real(
        caseId: Long,
        number: String,
        state: BidState = BidState.BidRecorded,
        won: Long = 1_200_000L,
    ): RecordedRealBid = requireNotNull(recorded(caseId, number, state, won)) { "REAL 건이어야 한다" }

    private fun canonicalFacts(
        label: String?,
        award: AwardAmount? = null,
    ) = NoticeOpeningFacts.Present(
        deadlineAt = Instant.parse("2026-10-20T05:00:00Z"),
        canonical =
            CanonicalOpeningFacts(
                progressLabel = label,
                figures =
                    OpeningResultFigures(
                        participantCount = 5,
                        actualOpeningAt = Instant.parse("2026-10-21T05:00:00Z"),
                        confirmedAward = award,
                        provisionalRankOne = ProvisionalRankOne.NotObserved,
                        observedAt = Instant.parse("2026-10-21T06:00:00Z"),
                    ),
            ),
        openingAxisObserved = false,
    )

    private fun readMyBidResults(
        bids: Map<Long, List<RecordedRealBid>>,
        facts: Map<String, NoticeOpeningFacts> = emptyMap(),
        winners: Map<String, AwardWinnerTradeName> = emptyMap(),
        tradeName: TradeName? = TradeName("(주)가상해양기술"),
    ) = ReadMyBidResults(
        bids = { account -> bids[account.value].orEmpty() },
        openingFacts = { notices -> notices.mapNotNull { id -> facts[id.number.value]?.let { id to it } }.toMap() },
        winnerNames = { notices -> notices.mapNotNull { id -> winners[id.number.value]?.let { id to it } }.toMap() },
        operatorNames = {
            tradeName?.let { raw ->
                OperatorTradeName.Declared(raw.raw)
            } ?: OperatorTradeName.NotDeclared
        },
        clock = Clock { now },
        openingPolicy = OPENING_PROGRESS_POLICY,
        matchingPolicy = matchingPolicy,
    )

    @Test
    fun `내 건만 읽는다 — 다른 계정의 건이 섞이지 않는다`() {
        val read =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010")), 2L to listOf(real(20L, "20261000020"))),
            )
        val mineOnly = read.read(me)
        mineOnly.cases.map { it.caseId } shouldBe listOf(BidCaseId(10L))
        read.read(other).cases.map { it.caseId } shouldBe listOf(BidCaseId(20L))
    }

    /**
     * **페이퍼 건은 값이 서지 않는다** — 질의 필터가 틀려 페이퍼 행이 흘러와도 포트의 타입을
     * 만들 수 없다(`recordedRealBidOrNull` 이 `null` 을 준다). 문장이 아니라 구조가 막는다.
     */
    @Test
    fun `페이퍼 건은 실투찰 기록 값이 되지 않는다`() {
        recorded(10L, "20261000010", kind = BidKind.PAPER) shouldBe null
        recorded(10L, "20261000010", kind = BidKind.REAL) shouldNotBe null
    }

    @Test
    fun `투찰 기록이 없으면 표본 0 이고 비율은 부재다`() {
        val results = readMyBidResults(bids = emptyMap()).read(me)
        results.cases shouldBe emptyList()
        results.aggregate.sampleSize shouldBe 0
        results.aggregate.reconciled.rate shouldBe AwardRateState.Absent(AwardRateAbsence.NO_RECORDED_BIDS)
        results.aggregate.recorded.rate shouldBe AwardRateState.Absent(AwardRateAbsence.NO_RECORDED_BIDS)
    }

    /** 개찰 사실을 받지 못한 공고는 **공고 부재**다 — 건이 조용히 목록에서 빠지지 않는다. */
    @Test
    fun `개찰 사실이 없는 건도 목록에 남고 공고 부재로 판정된다`() {
        val results = readMyBidResults(bids = mapOf(1L to listOf(real(10L, "20261000010")))).read(me)
        results.cases.single().opening shouldBe OpeningResultView.NoticeAbsent
        results.cases.single().verdict shouldBe
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.NOTICE_ABSENT)
    }

    @Test
    fun `개찰완료에 상호가 같으면 낙찰이고 차이가 선다`() {
        val award = AwardAmount(1_000_000L, Currency.KRW, Provenance.DerivedFromOpening)
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010", won = 1_200_000L))),
                facts = mapOf("20261000010" to canonicalFacts("개찰완료", award)),
                winners = mapOf("20261000010" to AwardWinnerTradeName.Published("주식회사 가상해양기술")),
            ).read(me)
        val case = results.cases.single()
        case.verdict shouldBe ReconciliationVerdict.Won
        case.gap shouldBe AwardGap.Computed(200_000L, Currency.KRW)
        results.aggregate.reconciled.wonCount shouldBe 1
        results.aggregate.reconciled.denominator shouldBe 1
    }

    /** 낙찰가가 없으면 차이는 **부재**다 — 0 으로 채우지 않는다. */
    @Test
    fun `확정 낙찰가가 없으면 차이가 부재다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010"))),
                facts = mapOf("20261000010" to canonicalFacts("개찰완료")),
                winners = mapOf("20261000010" to AwardWinnerTradeName.Published("주식회사 가상해양기술")),
            ).read(me)
        results.cases.single().gap shouldBe AwardGap.AwardAbsent
    }

    /**
     * **낙찰자 상호가 조립을 지나 산출 값에 남지 않는다** — `MyBidResult` 에 그 자리가 없다는
     * 사실을 값으로 잰다(칸 이름을 열거하지 않으므로 새 칸이 생겨도 이 단언이 잡는다).
     */
    @Test
    fun `낙찰자 상호가 산출 값에 남지 않는다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010"))),
                facts = mapOf("20261000010" to canonicalFacts("개찰완료")),
                winners = mapOf("20261000010" to AwardWinnerTradeName.Published(WINNER_SENTINEL)),
            ).read(me)
        results.toString() shouldNotContain WINNER_SENTINEL
        results.cases.single().verdict shouldBe ReconciliationVerdict.Lost
    }

    /** 운영자 상호가 미설정이면 판정하지 않는다 — 패찰이 아니다(SET-01 관문). */
    @Test
    fun `상호 미설정이면 판정하지 않는다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010"))),
                facts = mapOf("20261000010" to canonicalFacts("개찰완료")),
                winners = mapOf("20261000010" to AwardWinnerTradeName.Published("(주)가상토목건설")),
                tradeName = null,
            ).read(me)
        results.cases.single().verdict shouldBe
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.OPERATOR_NAME_NOT_DECLARED)
    }

    /**
     * 운영자 기록과 대사를 **병기**한다 — 운영자가 「낙찰」이라 적었고 대사가 판정 불가여도
     * 어느 쪽도 덮이지 않는다(D-7C5B-3).
     */
    @Test
    fun `운영자 기록과 대사를 병기하고 어느 쪽도 덮지 않는다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010", state = BidState.Won))),
            ).read(me)
        val case = results.cases.single()
        case.recordedState shouldBe BidStateTag.WON
        case.verdict shouldBe ReconciliationVerdict.Undecidable(ReconciliationBlocker.NOTICE_ABSENT)
        case.agreement shouldBe OutcomeAgreement.NOT_COMPARABLE
        results.aggregate.recorded.wonCount shouldBe 1
        results.aggregate.reconciled.wonCount shouldBe 0
        results.aggregate.reconciled.undecidedCount shouldBe 1
    }

    /** 두 축이 어긋나면 표지가 선다 — 그 표지가 어느 쪽을 바꾸지는 않는다. */
    @Test
    fun `운영자 기록과 대사가 어긋나면 표지가 선다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010", state = BidState.Won))),
                facts = mapOf("20261000010" to canonicalFacts("개찰완료")),
                winners = mapOf("20261000010" to AwardWinnerTradeName.Published("(주)가상토목건설")),
            ).read(me)
        val case = results.cases.single()
        case.recordedState shouldBe BidStateTag.WON
        case.verdict shouldBe ReconciliationVerdict.Lost
        case.agreement shouldBe OutcomeAgreement.DISAGREE
    }

    /** 승격 전은 「수집 전」과 다른 사유다 — 오늘 production 의 답이 이 갈래다. */
    @Test
    fun `원문만 있고 승격이 없으면 승격 전이다`() {
        val facts =
            NoticeOpeningFacts.Present(
                deadlineAt = Instant.parse("2026-10-20T05:00:00Z"),
                canonical = null,
                openingAxisObserved = true,
            )
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "20261000010"))),
                facts = mapOf("20261000010" to facts),
            ).read(me)
        results.cases.single().verdict shouldBe
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.OBSERVED_NOT_PROMOTED)
        results.aggregate.blockersObserved shouldBe setOf(ReconciliationBlocker.OBSERVED_NOT_PROMOTED)
    }

    /**
     * 건의 공고번호는 **사본이라 표기가 제각각**이다(V21 은 `<> ''` 만 건다). 조회가 그것을
     * 값 객체로 접어 묻는지 잰다 — 접지 않으면 소문자·공백이 든 번호가 조용히 「공고 부재」가
     * 된다(7C⑤-a 가 COL-05 로 세운 규율과 같은 자리: 규칙의 사본을 만들지 않는다).
     */
    @Test
    fun `공고번호 표기가 달라도 정규형으로 찾는다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(10L, "syn  ntc 2301"))),
                facts = mapOf("SYN-NTC-2301" to canonicalFacts("유찰")),
            ).read(me)
        results.cases.single().verdict shouldBe ReconciliationVerdict.FailedBid
    }

    @Test
    fun `집계의 표본 수는 건 수와 같다`() {
        val results =
            readMyBidResults(
                bids =
                    mapOf(
                        1L to
                            listOf(
                                real(10L, "20261000010"),
                                real(11L, "20261000011", state = BidState.Lost),
                                real(12L, "20261000012", state = BidState.FailedOrRebid),
                            ),
                    ),
            ).read(me)
        results.aggregate.sampleSize shouldBe 3
        results.aggregate.recorded.sampleSize shouldBe 3
        results.aggregate.reconciled.sampleSize shouldBe 3
        results.cases.size shouldBe 3
    }

    /** 포트가 돌려준 순서를 조회가 바꾸지 않는다 — 정렬은 읽는 자리가 정한다. */
    @Test
    fun `포트 순서를 그대로 보존한다`() {
        val results =
            readMyBidResults(
                bids = mapOf(1L to listOf(real(12L, "20261000012"), real(10L, "20261000010"))),
            ).read(me)
        results.cases.map { it.caseId } shouldBe listOf(BidCaseId(12L), BidCaseId(10L))
    }

    @Test
    fun `투찰 금액은 통화와 과세와 출처를 함께 나른다`() {
        val results = readMyBidResults(bids = mapOf(1L to listOf(real(10L, "20261000010")))).read(me)
        val submitted = results.cases.single().submitted
        submitted.won shouldBe 1_200_000L
        submitted.currency shouldBe Currency.KRW
        submitted.vatTreatment shouldBe VatTreatment.INCLUSIVE
        submitted.origin.shouldBeInstanceOf<BidAmountOrigin.OperatorEntered>()
    }
}
