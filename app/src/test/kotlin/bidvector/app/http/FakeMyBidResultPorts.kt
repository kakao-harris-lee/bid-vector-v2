package bidvector.app.http

import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.SubmittedBidAmount
import bidvector.procurement.NoticeId
import bidvector.settlement.AwardWinnerTradeName
import bidvector.workflow.access.AccountId
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.result.AwardWinnerNamePort
import bidvector.workflow.result.BatchOpeningFactsPort
import bidvector.workflow.result.RecordedBidReadPort
import bidvector.workflow.result.RecordedRealBid
import bidvector.workflow.result.recordedRealBidOrNull
import java.time.Instant

/**
 * 결과 조회 포트 셋의 이중체(M7/7C⑤-b) — **자기 파일에 둔다**(D-7C5-10: `HttpTestApplication` 이
 * 파일 크기 한도에 닿아 있어 레인마다 그 파일에 이중체를 얹으면 한도가 레인 사이 경합이 된다).
 *
 * 계정별로 답을 나눠 둔다 — 소유 경계를 wire 에서 재려면 **다른 계정의 건이 실재해야** 한다.
 */
class FakeRecordedBids : RecordedBidReadPort {
    private val byAccount: MutableMap<Long, List<RecordedRealBid>> = mutableMapOf()

    override fun recordedBids(account: AccountId): List<RecordedRealBid> = byAccount[account.value].orEmpty()

    fun reset() = byAccount.clear()

    /** 페이퍼를 넘기면 값이 서지 않아 담기지 않는다 — 그 사실이 이중체에서도 같다. */
    fun put(
        accountId: Long,
        bids: List<RecordedRealBid>,
    ) {
        byAccount[accountId] = bids
    }
}

/** 투찰 기록 하나를 만드는 자리 — 모듈 밖이라 `recordedRealBidOrNull` 로만 얻는다. */
fun fakeRecordedBid(
    caseId: Long,
    notice: NoticeKey,
    amount: SubmittedBidAmount,
    recordedState: BidStateTag = BidStateTag.BID_RECORDED,
    kind: BidKind = BidKind.REAL,
    submittedAt: Instant = Instant.parse("2026-10-20T05:00:00Z"),
    confirmedAt: Instant = Instant.parse("2026-10-20T06:00:00Z"),
): RecordedRealBid? =
    recordedRealBidOrNull(
        kind = kind,
        caseId = BidCaseId(caseId),
        notice = notice,
        recordedState = recordedState,
        amount = amount,
        submittedAt = submittedAt,
        confirmedAt = confirmedAt,
    )

class FakeBatchOpeningFacts : BatchOpeningFactsPort {
    var facts: Map<NoticeId, NoticeOpeningFacts> = emptyMap()

    /** 요청한 공고로 좁혀 답한다 — 묻지 않은 공고를 끼워 넣지 않는다. */
    override fun lookupAll(notices: Set<NoticeId>): Map<NoticeId, NoticeOpeningFacts> =
        facts.filterKeys { it in notices }

    fun reset() {
        facts = emptyMap()
    }
}

class FakeAwardWinnerNames : AwardWinnerNamePort {
    var names: Map<NoticeId, AwardWinnerTradeName> = emptyMap()

    override fun winnerNames(notices: Set<NoticeId>): Map<NoticeId, AwardWinnerTradeName> =
        names.filterKeys { it in notices }

    fun reset() {
        names = emptyMap()
    }
}
