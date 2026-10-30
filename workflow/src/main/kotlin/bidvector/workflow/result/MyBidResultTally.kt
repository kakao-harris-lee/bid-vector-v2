package bidvector.workflow.result

import bidvector.bidding.BidStateTag
import bidvector.sharedkernel.Rate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 비율의 소수 자릿수 — 분모가 표본 수라 1/3 같은 무한소수가 나온다. **정책 값이 아니라 표기
 * 정밀도**다: 분자와 분모를 응답에 함께 싣으므로, 더 정밀한 값이 필요한 소비자는 그 둘로 다시
 * 센다(이 상수가 바뀌어도 「무엇이 몇 건인가」는 바뀌지 않는다).
 */
private const val RATE_SCALE = 4

/**
 * 집계 한 축 — 분자 둘(낙찰·패찰)과 분모 밖 둘(유찰·재입찰 · 판정 불가/미확정).
 *
 * **표본 수가 파생이다.** 생성자가 받는 것은 네 칸뿐이고 [sampleSize] 는 그 합이라
 * 「분자 + 제외 = 표본」 등식이 **깨질 수 있는 자리가 없다**(등식을 단언으로 지키면 세는 쪽과
 * 단언하는 쪽 둘이 생기고 둘이 갈린다).
 */
data class AxisTally(
    val wonCount: Int,
    val lostCount: Int,
    val failedOrRebidCount: Int,
    val undecidedCount: Int,
) {
    init {
        require(wonCount >= 0 && lostCount >= 0 && failedOrRebidCount >= 0 && undecidedCount >= 0) {
            "집계 칸은 음수일 수 없다: $wonCount·$lostCount·$failedOrRebidCount·$undecidedCount"
        }
    }

    /** 분모 — **낙찰 + 패찰**이다. 유찰·재입찰·판정 불가는 들지 않는다(D-7C5B-4). */
    val denominator: Int get() = wonCount + lostCount

    /** 표본 — 분모에 제외 둘을 더한 값. */
    val sampleSize: Int get() = denominator + failedOrRebidCount + undecidedCount

    /** 비율 — 분모가 0 이면 **0% 가 아니라 부재**다. 그 둘은 다른 사실이다. */
    val rate: AwardRateState
        get() =
            when {
                denominator > 0 -> AwardRateState.Measured(rateOf(wonCount, denominator))
                sampleSize == 0 -> AwardRateState.Absent(AwardRateAbsence.NO_RECORDED_BIDS)
                else -> AwardRateState.Absent(AwardRateAbsence.NO_DECIDED_CASES)
            }
}

private fun rateOf(
    numerator: Int,
    denominator: Int,
): Rate =
    Rate.ofFraction(
        BigDecimal(numerator).divide(BigDecimal(denominator), RATE_SCALE, RoundingMode.HALF_UP),
    )

/** 비율이 **섰는가** — 부재를 `null` 이나 `0` 으로 나르지 않는다. */
sealed interface AwardRateState {
    data class Measured(
        val rate: Rate,
    ) : AwardRateState

    data class Absent(
        val reason: AwardRateAbsence,
    ) : AwardRateState
}

/** 비율이 서지 않는 사유 둘 — 「셀 것이 없다」와 「셌는데 분모에 드는 것이 없다」는 다르다. */
enum class AwardRateAbsence {
    /** 투찰 기록이 있는 실투찰 건 자체가 없다. */
    NO_RECORDED_BIDS,

    /** 표본은 있으나 전부 분모 밖이다(유찰·재입찰 · 판정 불가 · 미확정). */
    NO_DECIDED_CASES,
}

/**
 * 집계 전체 — 두 축을 **병기**한다(D-7C5B-9). 어느 쪽도 다른 쪽을 덮지 않는다.
 *
 * 두 축의 표본이 같은 건 집합이라는 것이 이 타입의 불변식이고, 그래서 한 축에서만 건이 빠지는
 * 조립 결함이 생성 시점에 선다.
 */
data class MyBidResultAggregate(
    val sampleSize: Int,
    val recorded: AxisTally,
    val reconciled: AxisTally,
    val blockersObserved: Set<ReconciliationBlocker>,
) {
    init {
        require(sampleSize >= 0) { "표본 수는 음수일 수 없다: $sampleSize" }
        require(recorded.sampleSize == sampleSize) {
            "운영자 기록 축의 표본이 전체와 다르다: ${recorded.sampleSize} ≠ $sampleSize"
        }
        require(reconciled.sampleSize == sampleSize) {
            "대사 축의 표본이 전체와 다르다: ${reconciled.sampleSize} ≠ $sampleSize"
        }
    }
}

/** 서버 대사 축을 센다. */
fun reconciledAxisTally(verdicts: List<ReconciliationVerdict>): AxisTally = tallyOf(verdicts.map(::bucketOf))

/** 운영자 기록 축을 센다 — 입력이 상태 꼬리표라 대사 결말과 섞일 수 없다. */
fun recordedAxisTally(states: List<BidStateTag>): AxisTally = tallyOf(states.map(::bucketOf))

/**
 * 운영자 기록과 서버 대사의 관계 — **어느 쪽도 덮지 않는다**(D-7C5B-3). 어긋남 표지는
 * 「둘이 다르다」는 사실일 뿐 어느 쪽이 틀렸다는 판정이 아니다.
 */
fun outcomeAgreementOf(
    recorded: BidStateTag,
    verdict: ReconciliationVerdict,
): OutcomeAgreement {
    val left = bucketOf(recorded)
    val right = bucketOf(verdict)
    return when {
        left == AxisBucket.UNDECIDED || right == AxisBucket.UNDECIDED -> OutcomeAgreement.NOT_COMPARABLE
        left == right -> OutcomeAgreement.AGREE
        else -> OutcomeAgreement.DISAGREE
    }
}

/** 두 축의 관계 셋. */
enum class OutcomeAgreement {
    AGREE,
    DISAGREE,

    /** 한쪽이 확정이 아니라 견줄 수 없다 — 없는 어긋남을 만들지 않는다. */
    NOT_COMPARABLE,
}

/**
 * 집계 칸 넷의 꼬리표 — 두 축이 **같은 세는 함수**([tallyOf])를 쓰게 하는 자리다. 축마다 세는
 * 코드를 따로 두면 한쪽만 고쳐지는 길이 생긴다.
 */
private enum class AxisBucket {
    WON,
    LOST,
    FAILED_OR_REBID,
    UNDECIDED,
}

private fun tallyOf(buckets: List<AxisBucket>): AxisTally =
    AxisTally(
        wonCount = buckets.count { it == AxisBucket.WON },
        lostCount = buckets.count { it == AxisBucket.LOST },
        failedOrRebidCount = buckets.count { it == AxisBucket.FAILED_OR_REBID },
        undecidedCount = buckets.count { it == AxisBucket.UNDECIDED },
    )

/** `when` 전수 — 대사 결말이 늘면 답을 주지 않고는 컴파일되지 않는다. */
private fun bucketOf(verdict: ReconciliationVerdict): AxisBucket =
    when (verdict) {
        ReconciliationVerdict.Won -> AxisBucket.WON
        ReconciliationVerdict.Lost -> AxisBucket.LOST
        ReconciliationVerdict.FailedBid, ReconciliationVerdict.Rebid -> AxisBucket.FAILED_OR_REBID
        is ReconciliationVerdict.Undecidable -> AxisBucket.UNDECIDED
    }

/**
 * `when` 전수 — 상태를 더하면 답을 주지 않고는 컴파일되지 않는다(`BidState.tag` 와 같은 규율).
 * 확정 셋 밖은 전부 **미확정**이다: 운영자가 아직 결과를 적지 않은 것이지 판정이 불가한 것이
 * 아니지만, 분모 밖이라는 점에서 같은 칸에 든다.
 */
private fun bucketOf(state: BidStateTag): AxisBucket =
    when (state) {
        BidStateTag.WON -> AxisBucket.WON
        BidStateTag.LOST -> AxisBucket.LOST
        BidStateTag.FAILED_OR_REBID -> AxisBucket.FAILED_OR_REBID
        BidStateTag.WATCHING,
        BidStateTag.DEFERRED,
        BidStateTag.PRICE_RECEIVED,
        BidStateTag.BID_PLANNED,
        BidStateTag.BID_RECORDED,
        BidStateTag.AWAITING_OPENING,
        BidStateTag.OPENED_PROVISIONAL_FIRST,
        BidStateTag.OPENED_OUT_OF_RANK,
        BidStateTag.UNDER_QUALIFICATION_REVIEW,
        BidStateTag.SKIPPED,
        BidStateTag.DEADLINE_PASSED_UNRECORDED,
        -> AxisBucket.UNDECIDED
    }
