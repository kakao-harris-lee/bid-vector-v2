package bidvector.app.http

import bidvector.bidding.BidAmountOrigin
import bidvector.sharedkernel.export
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.result.AwardGap
import bidvector.workflow.result.AwardRateAbsence
import bidvector.workflow.result.AwardRateState
import bidvector.workflow.result.MyBidResult
import bidvector.workflow.result.MyBidResultAggregate
import bidvector.workflow.result.MyBidResults
import bidvector.workflow.result.ReconciliationVerdict

/**
 * 내 입찰 결과 응답(M7/7C⑤-b) — **깊이 1** 이다(D-7C4C-6 이 연 한 겹). 건 목록은 깊이 1 object 의
 * 배열이고 집계는 깊이 1 object 하나다. 깊이 2 는 계약 게이트가 거부한다.
 *
 * **다른 업체의 상호·사업자번호·대표자명 칸이 없다**(D-7C5B-5). 낙찰자 상호는 커널에 들어가
 * 판정으로만 나오고 그 판정 타입에 이름 자리가 없어, 이 DTO 로 올 **타입 경로가 없다**. 표지
 * 글자 단언이 그 부재를 전문에서 잰다.
 *
 * 메모·증빙 참조도 없다 — 읽기 포트의 값에 그 자리가 없다.
 */
data class MyBidResultsResponse(
    val cases: List<MyBidCaseResponse>,
    val aggregate: MyBidAggregateResponse,
) {
    companion object {
        fun from(results: MyBidResults): MyBidResultsResponse =
            MyBidResultsResponse(
                cases = results.cases.map(MyBidCaseResponse::from),
                aggregate = MyBidAggregateResponse.from(results.aggregate),
            )
    }
}

/**
 * 건 하나 — **운영자 기록 축과 서버 대사 축이 다른 칸**이다(D-7C5B-3). 어긋나도 어느 쪽도
 * 덮지 않고 [outcomeAgreement] 가 그 사실만 나른다.
 *
 * 금액은 값만 싣지 않는다 — 원·통화·과세·출처가 함께 간다(R-BASIS-06). 중첩 object 대신
 * **접두사**로 편다(7C⑤-a 선례 — 평탄 규칙).
 */
data class MyBidCaseResponse(
    val caseId: Long,
    val noticeNumber: String,
    val noticeRound: String,
    val recordedState: String,
    val submittedAmountWon: Long,
    val submittedAmountCurrency: String,
    val submittedAmountVatTreatment: String,
    val submittedAmountOrigin: String,
    val chosenOptionRef: String?,
    val submittedAt: String,
    val confirmedAt: String,
    val openingState: String,
    val confirmedAwardAmountWon: Long?,
    val confirmedAwardAmountCurrency: String?,
    val confirmedAwardAmountBasis: String?,
    val confirmedAwardAmountVatTreatment: String?,
    val confirmedAwardAmountProvenance: String?,
    val reconciledOutcome: String,
    val reconciliationBlocker: String?,
    val outcomeAgreement: String,
    val gapState: String,
    val gapWon: Long?,
    val gapCurrency: String?,
) {
    companion object {
        fun from(result: MyBidResult): MyBidCaseResponse {
            val award =
                ConfirmedAwardFields(
                    (result.opening as? OpeningResultView.Observed)?.figures?.confirmedAward?.export(),
                )
            val gap = result.gap as? AwardGap.Computed
            return MyBidCaseResponse(
                caseId = result.caseId.value,
                noticeNumber = result.notice.noticeNumber,
                noticeRound = result.notice.noticeRound.value,
                recordedState = result.recordedState.name,
                submittedAmountWon = result.submitted.won,
                submittedAmountCurrency = result.submitted.currency.name,
                submittedAmountVatTreatment = result.submitted.vatTreatment.name,
                submittedAmountOrigin = originTokenOf(result.submitted.origin),
                chosenOptionRef = chosenOptionRefOf(result.submitted.origin),
                submittedAt = result.submittedAt.toString(),
                confirmedAt = result.confirmedAt.toString(),
                openingState = stateTokenOf(result.opening),
                confirmedAwardAmountWon = award.won,
                confirmedAwardAmountCurrency = award.currency,
                confirmedAwardAmountBasis = award.basis,
                confirmedAwardAmountVatTreatment = award.vatTreatment,
                confirmedAwardAmountProvenance = award.provenance,
                reconciledOutcome = verdictTokenOf(result.verdict),
                reconciliationBlocker = (result.verdict as? ReconciliationVerdict.Undecidable)?.blocker?.name,
                outcomeAgreement = result.agreement.name,
                gapState = gapStateTokenOf(result.gap),
                gapWon = gap?.differenceWon,
                gapCurrency = gap?.currency?.name,
            )
        }
    }
}

/**
 * 집계 — **두 축을 병기**한다(D-7C5B-9). 축마다 분자·분모·표본·제외 둘을 함께 싣고, 분모가 0 이면
 * 비율 칸이 비고 상태가 「부재」다(**0% 가 아니다**).
 *
 * [reconciliationBlockers] 는 표본에 나타난 판정 불가 사유 전수다 — 대사 분모가 0 일 때 그것이
 * 곧 **분모 0 의 사유**다(오늘 production 의 답은 `WINNER_NAME_NOT_PUBLISHED` 하나가 될
 * 것이다: 낙찰자 상호 적재 경로가 출하 조립에 없다, `OPEN-SET01-AWARD-NAME-INGEST`).
 *
 * 비율은 **문자열**이다 — JSON 수치는 클라이언트에서 배정밀도로 읽힌다(7C⑤-a 의 투찰율과 같은
 * 이유). 분자·분모를 함께 내므로 더 정밀한 값이 필요하면 소비자가 그 둘로 다시 센다.
 */
data class MyBidAggregateResponse(
    val sampleSize: Int,
    val recordedState: String,
    val recordedAwardRate: String?,
    val recordedWonCount: Int,
    val recordedLostCount: Int,
    val recordedDenominator: Int,
    val recordedFailedOrRebidCount: Int,
    val recordedUndecidedCount: Int,
    val reconciledState: String,
    val reconciledAwardRate: String?,
    val reconciledWonCount: Int,
    val reconciledLostCount: Int,
    val reconciledDenominator: Int,
    val reconciledFailedOrRebidCount: Int,
    val reconciledUndecidedCount: Int,
    val reconciliationBlockers: List<String>,
) {
    companion object {
        fun from(aggregate: MyBidResultAggregate): MyBidAggregateResponse =
            MyBidAggregateResponse(
                sampleSize = aggregate.sampleSize,
                recordedState = rateStateTokenOf(aggregate.recorded.rate),
                recordedAwardRate = rateValueOf(aggregate.recorded.rate),
                recordedWonCount = aggregate.recorded.wonCount,
                recordedLostCount = aggregate.recorded.lostCount,
                recordedDenominator = aggregate.recorded.denominator,
                recordedFailedOrRebidCount = aggregate.recorded.failedOrRebidCount,
                recordedUndecidedCount = aggregate.recorded.undecidedCount,
                reconciledState = rateStateTokenOf(aggregate.reconciled.rate),
                reconciledAwardRate = rateValueOf(aggregate.reconciled.rate),
                reconciledWonCount = aggregate.reconciled.wonCount,
                reconciledLostCount = aggregate.reconciled.lostCount,
                reconciledDenominator = aggregate.reconciled.denominator,
                reconciledFailedOrRebidCount = aggregate.reconciled.failedOrRebidCount,
                reconciledUndecidedCount = aggregate.reconciled.undecidedCount,
                reconciliationBlockers = aggregate.blockersObserved.map { it.name }.sorted(),
            )
    }
}

/** 소진 `when` — 대사 결말이 늘면 답을 주지 않고는 컴파일되지 않는다. */
private fun verdictTokenOf(verdict: ReconciliationVerdict): String =
    when (verdict) {
        ReconciliationVerdict.Won -> "WON"
        ReconciliationVerdict.Lost -> "LOST"
        ReconciliationVerdict.FailedBid -> "FAILED_BID"
        ReconciliationVerdict.Rebid -> "REBID"
        is ReconciliationVerdict.Undecidable -> "UNDECIDABLE"
    }

/**
 * 투찰 금액의 출처 — [BidAmountOrigin.PriceMenu] 는 **오늘 도달 불가**다(D-7C5B-10 ①): 투찰가
 * 메뉴가 출하 경로에 없고 HTTP 요청 DTO 에 provenance·메뉴 참조 칸이 없어 use case 가
 * `OperatorEntered` 를 상수로 싣는다. 칸은 두되 그 사실이 상태로 보이게 한다.
 */
private fun originTokenOf(origin: BidAmountOrigin): String =
    when (origin) {
        BidAmountOrigin.OperatorEntered -> "OPERATOR_ENTERED"
        is BidAmountOrigin.PriceMenu -> "PRICE_MENU"
    }

private fun chosenOptionRefOf(origin: BidAmountOrigin): String? =
    when (origin) {
        BidAmountOrigin.OperatorEntered -> null
        is BidAmountOrigin.PriceMenu -> origin.chosenOption.value
    }

/** 차이의 상태 — 「확정 낙찰가가 없다」와 「차이가 0 이다」가 다른 값이다. */
private fun gapStateTokenOf(gap: AwardGap): String =
    when (gap) {
        AwardGap.AwardAbsent -> "AWARD_ABSENT"
        is AwardGap.Computed -> "COMPUTED"
    }

private fun rateStateTokenOf(state: AwardRateState): String =
    when (state) {
        is AwardRateState.Measured -> "MEASURED"
        is AwardRateState.Absent -> absenceTokenOf(state.reason)
    }

private fun absenceTokenOf(reason: AwardRateAbsence): String =
    when (reason) {
        AwardRateAbsence.NO_RECORDED_BIDS -> "ABSENT_NO_RECORDED_BIDS"
        AwardRateAbsence.NO_DECIDED_CASES -> "ABSENT_NO_DECIDED_CASES"
    }

/** 분모가 0 이면 **비율 칸이 없다** — 0 을 싣지 않는다. */
private fun rateValueOf(state: AwardRateState): String? =
    when (state) {
        is AwardRateState.Measured -> state.rate.fraction.toPlainString()
        is AwardRateState.Absent -> null
    }
