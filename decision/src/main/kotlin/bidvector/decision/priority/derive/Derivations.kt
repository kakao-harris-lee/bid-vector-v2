package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.decision.priority.clamp01
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BidAmount
import bidvector.sharedkernel.BidRateOrigin
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.bidRateAgainst
import bidvector.sharedkernel.export
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration

/** 부하율 나눗셈 정밀도 — `MoneyArithmetic.WEIGHT_DIVISION_CONTEXT` 류와 같은 값(금액이 아닌 카운트 비율이라 shared-kernel 금액 산술 대상은 아니다). */
private val LOAD_RATIO_CONTEXT = MathContext(20)

/**
 * ① 급박도(scope.md, legacy `allocation.py:47-54` `_compute_urgency_score`) — 마감까지
 * 남은 시간이 없으면 정책 상수(D-4B5-2, legacy `URGENCY_SCORE_UNKNOWN`). 항상 Present.
 */
fun deriveUrgency(
    remaining: Duration?,
    policy: Resolution.Resolved<DerivationPolicyData>,
): DerivationOutcome<UnitScore> {
    val score = remaining?.let { resolveBand(it, policy.value.urgencyLadder) } ?: policy.value.noDeadlineUrgency
    return DerivationOutcome.Present(UnitScore(score))
}

/**
 * ② 예산확보율(scope.md, legacy `allocation_core.py:104-` `budget_capture_score`) — legacy
 * 「base 없으면 중립 0.5」sentinel 을 [DerivationAbsence.BaseAmountMissing] 으로 뒤집는다
 * (D-4B5-1, 우회 (1)). 나눗셈은 1B [bidRateAgainst] 만 쓴다(D-4B5-4) — `BigDecimal` 직접
 * 나눗셈이 없다. `origin`은 `Recommended` 로 고정한다 — 이 함수가 재는 것 자체가 "추천
 * 투찰가가 기초금액을 얼마나 남기는가"이므로 정책값이 아니라 함수 정의의 일부다.
 */
fun deriveBudgetCapture(
    recommended: BidAmount?,
    base: BaseAmount?,
    policy: Resolution.Resolved<DerivationPolicyData>,
): DerivationOutcome<UnitScore> =
    when {
        base == null || base.export().won <= 0L -> DerivationOutcome.Absent(DerivationAbsence.BaseAmountMissing)
        recommended == null -> DerivationOutcome.Absent(DerivationAbsence.RecommendationMissing)
        else -> budgetCaptureFromRate(recommended, base, policy)
    }

private fun budgetCaptureFromRate(
    recommended: BidAmount,
    base: BaseAmount,
    policy: Resolution.Resolved<DerivationPolicyData>,
): DerivationOutcome<UnitScore> {
    val roundingPolicy = Resolution.Resolved(policy.value.budgetCaptureRounding, policy.version)
    return when (val measurement = recommended.bidRateAgainst(base, BidRateOrigin.Recommended, roundingPolicy)) {
        is Measurement.Measured -> {
            DerivationOutcome.Present(UnitScore(clamp01(measurement.value.value.rate.fraction)))
        }

        is Measurement.Unmeasurable -> {
            DerivationOutcome.Absent(DerivationAbsence.MoneyArithmeticUnmeasurable(measurement.reason))
        }
    }
}

/**
 * ⑥ 용량 부하율(scope.md, 4B-2 `CapacitySnapshot` 상당 값 — 정수 둘로 받는다). 항상 Present.
 */
fun deriveLoadRatio(
    current: Int,
    max: Int,
): DerivationOutcome<UnitScore> {
    require(current >= 0) { "current 는 음수일 수 없다: $current" }
    require(max >= 0) { "max 는 음수일 수 없다: $max" }
    val ratio = BigDecimal(current).divide(BigDecimal(maxOf(1, max)), LOAD_RATIO_CONTEXT)
    return DerivationOutcome.Present(UnitScore(clamp01(ratio)))
}

/** ⑦ workload 미수집(scope.md) — 집계 port 는 4B-6, 이 slice 는 상수 Absent 만 낸다. */
fun workloadNotCollected(): DerivationOutcome<UnitScore> =
    DerivationOutcome.Absent(DerivationAbsence.WorkloadNotCollected)

/**
 * ⑤ competitiveness 미수집(계약 갱신 2026-09-10 #1, `OPEN-4B5-COMPETITIVENESS`) — 시장
 * 평균 fact 자체가 V2 에 없어(산술이 아니라 fact 의 부재) `deriveCompetitiveness` 를
 * 만들지 않고 ⑦ 과 같은 축의 상수만 낸다. 4B-4 재정규화가 이 성분을 흡수한다.
 */
fun competitivenessNotCollected(): DerivationOutcome<UnitScore> =
    DerivationOutcome.Absent(DerivationAbsence.MarketAverageMissing)
