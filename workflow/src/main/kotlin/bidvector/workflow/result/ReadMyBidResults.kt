package bidvector.workflow.result

import bidvector.bidding.BidCaseId
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.SubmittedBidAmount
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.settlement.AwardMatchingPolicyData
import bidvector.settlement.AwardWinnerTradeName
import bidvector.settlement.OperatorTradeName
import bidvector.workflow.access.AccountId
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OpeningProgressPolicyData
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.opening.resolveOpeningResultView
import bidvector.workflow.strategy.Clock
import java.time.Instant

/**
 * 건 하나의 결과 — 운영자 기록 축과 서버 대사 축이 **다른 칸**이다(D-7C5B-3).
 *
 * **낙찰자 상호를 담을 자리가 없다.** [verdict] 는 판정만 나르고 [opening] 의 잠정 1위 축도
 * 상호 자리가 없는 타입이다(D-7C5-3). 상호가 이 값에 닿으려면 **칸을 새로 만들어야** 하고,
 * 그 순간 표지 글자 단언이 붉어진다.
 */
data class MyBidResult(
    val caseId: BidCaseId,
    val notice: NoticeKey,
    val recordedState: BidStateTag,
    val submitted: SubmittedBidAmount,
    val submittedAt: Instant,
    val confirmedAt: Instant,
    val opening: OpeningResultView,
    val verdict: ReconciliationVerdict,
    val gap: AwardGap,
    val agreement: OutcomeAgreement,
)

/** 건별 목록과 집계 — **한 번의 판정 패스**에서 함께 난다(D-7C5B-9 「한 operation」의 근거). */
data class MyBidResults(
    val cases: List<MyBidResult>,
    val aggregate: MyBidResultAggregate,
)

/**
 * 내 입찰 결과 조회(M7/7C⑤-b) — **읽기 전용**이다.
 *
 * 집계를 목록과 같은 자리에서 내는 것이 「화면이 목록을 세어 집계를 지어내지 않는다」의 실물이다
 * (우회 ③): 둘이 다른 요청이면 서로 다른 판정 패스에서 나와 화면이 자기가 띄운 목록과 어긋나는
 * 집계를 보일 수 있다.
 *
 * **쓰기 경로가 없다** — 포트 셋이 전부 읽기 하나짜리 `fun interface` 이고, 기존
 * `BidCaseWorkflow` 를 부르지 않는다(그 읽기는 마감 정산이 타임라인에 쓴다, D-7C5B-8).
 *
 * 상호는 **프로필에서 원문 그대로** 받아 커널이 한 번만 정규화한다(이중 정규화 금지 — `TradeName`
 * KDoc 과 맞물린다).
 */
class ReadMyBidResults(
    private val bids: RecordedBidReadPort,
    private val openingFacts: BatchOpeningFactsPort,
    private val winnerNames: AwardWinnerNamePort,
    private val operatorNames: OperatorTradeNamePort,
    private val clock: Clock,
    private val openingPolicy: OpeningProgressPolicyData,
    private val matchingPolicy: AwardMatchingPolicyData,
) {
    fun read(account: AccountId): MyBidResults {
        val recorded = bids.recordedBids(account)
        val resolvable = recorded.mapNotNull { it.notice.toNoticeId() }.toSet()
        val facts = openingFacts.lookupAll(resolvable)
        val winners = winnerNames.winnerNames(resolvable)
        val operator = operatorNames.tradeNameOf(account)
        val now = clock.now()
        val cases =
            recorded.map { bid ->
                bid.toResult(
                    opening = bid.openingViewOf(facts, now),
                    winner = bid.winnerOf(winners),
                    operator = operator,
                )
            }
        return MyBidResults(cases, cases.aggregate())
    }

    /** 공고를 못 찾는 건도 **목록에 남는다** — 조용히 빠지면 운영자가 자기 기록을 잃는다. */
    private fun RecordedRealBid.openingViewOf(
        facts: Map<NoticeId, NoticeOpeningFacts>,
        now: Instant,
    ): OpeningResultView {
        val found = notice.toNoticeId()?.let(facts::get)
        return found?.let { resolveOpeningResultView(it, now, openingPolicy) } ?: OpeningResultView.NoticeAbsent
    }

    private fun RecordedRealBid.winnerOf(winners: Map<NoticeId, AwardWinnerTradeName>): AwardWinnerTradeName =
        notice.toNoticeId()?.let(winners::get) ?: AwardWinnerTradeName.NotPublished

    /**
     * 건 하나의 조립 — **상호는 인자로만 지나간다.** [winner] 는 [reconcileBid] 에 넘어가고
     * 산출인 [ReconciliationVerdict] 에는 이름 자리가 없으므로, 이 함수가 끝나면 그 값을
     * 가리키는 것이 아무것도 없다.
     */
    private fun RecordedRealBid.toResult(
        opening: OpeningResultView,
        winner: AwardWinnerTradeName,
        operator: OperatorTradeName,
    ): MyBidResult {
        val verdict = reconcileBid(opening, operator, winner, matchingPolicy)
        return MyBidResult(
            caseId = caseId,
            notice = notice,
            recordedState = recordedState,
            submitted = amount,
            submittedAt = submittedAt,
            confirmedAt = confirmedAt,
            opening = opening,
            verdict = verdict,
            gap = awardGapOf(amount, opening.confirmedAward()),
            agreement = outcomeAgreementOf(recordedState, verdict),
        )
    }
}

/**
 * 건의 공고 키 → 조회 키. 번호는 `String` 이라 값 객체로 접어야 [NoticeId] 가 선다.
 *
 * **접는 것이 값을 바꾸지는 않는다**(실측): `RECORD_BID` 는 「투찰 예정 · 투찰 기록됨 · 마감
 * 지남」에서만 열리고 그 셋에 닿으려면 초안이나 마감 사본이 공고 행을 **정확히** 찾아야 하므로,
 * 투찰 기록이 있는 건의 번호는 이미 canonical 표의 번호와 같다. 그래서 조회가 canonical 을
 * 정확 일치로 묻는 것이 성립한다(`JdbcMyBidResultViewTest` 가 그 전제를 잰다).
 *
 * `null` 갈래도 **오늘 닿지 않는다**: [NoticeKey] 가 공백뿐인 번호를 거부하고 정규화는 글자를
 * 지우지 않는다. 그래도 [NoticeNumber.ofOrNull] 을 쓰는 것은 그 함수의 규율(「판독은 적재 경로를
 * 신뢰하지 않는다」)을 따르는 것이고, 전제가 바뀌면 건이 **사라지는 대신** 공고 부재로 남는다.
 */
private fun NoticeKey.toNoticeId(): NoticeId? = NoticeNumber.ofOrNull(noticeNumber)?.let { NoticeId(it, noticeRound) }

/**
 * **확정 축만** 꺼낸다 — 잠정 1위 금액은 이 함수가 보지 않는다(`AwardGap` 의 시그니처와 짝이
 * 되는 차단면, 우회 ⑥).
 */
private fun OpeningResultView.confirmedAward() = (this as? OpeningResultView.Observed)?.figures?.confirmedAward

private fun List<MyBidResult>.aggregate(): MyBidResultAggregate =
    MyBidResultAggregate(
        sampleSize = size,
        recorded = recordedAxisTally(map { it.recordedState }),
        reconciled = reconciledAxisTally(map { it.verdict }),
        blockersObserved =
            mapNotNull { (it.verdict as? ReconciliationVerdict.Undecidable)?.blocker }.toSet(),
    )
