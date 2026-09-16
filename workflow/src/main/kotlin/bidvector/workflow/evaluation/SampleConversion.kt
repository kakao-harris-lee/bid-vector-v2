package bidvector.workflow.evaluation

import bidvector.decision.ProvenancePolicyData
import bidvector.decision.ProvenanceRow
import bidvector.decision.ProvenanceRules
import bidvector.procurement.DrawNumberObservation
import bidvector.procurement.Notice
import bidvector.procurement.OpeningReservePriceRow
import bidvector.procurement.OpeningResult
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import java.math.BigDecimal

/**
 * `SampleEligibility.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions` — 한 파일에 열넷을
 * 몰아두지 않는다, `MoneyMapping.kt`와 같은 판단). `internal` — 같은 모듈 안 `judgeEligibility`
 * (`SampleEligibility.kt`, 같은 패키지)가 그대로 쓴다.
 *
 * D-4B7-1 (a) — `bsisPlnprc`(기초예정가격, basis 미확정 값 객체)를 기초금액 축 원문
 * 관측값으로 옮긴다: `basis = BASE_AMOUNT`, `currency` 그대로, `vatTreatment = UNKNOWN`,
 * `provenance = Published(표본 공고 자기 회차)`(우회 (18) — 대상 공고 회차를 빌리지 않는다).
 * 행 하나라도 `baseReservePrice`가 없으면(D-4B7-2 ④) 전체를 실격시킨다.
 */
internal fun reservePriceAmounts(
    notice: Notice,
    rows: List<OpeningReservePriceRow>,
): List<BaseAmount>? {
    val published = Provenance.Published(notice.id.round)
    val amounts = mutableListOf<BaseAmount>()
    for (row in rows) {
        val candidate = row.baseReservePrice ?: return null
        amounts += BaseAmount(candidate.won, candidate.currency, VatTreatment.UNKNOWN, published)
    }
    return amounts
}

/** D-4B7-2 ⑤ — `NotObserved`는 엔진이 거부하지 않는 빈 집합, `OutOfRange`만 실격시킨다. */
internal fun drawNumberSet(observation: DrawNumberObservation): Set<Int>? =
    when (observation) {
        DrawNumberObservation.NotObserved -> emptySet()
        is DrawNumberObservation.Verified -> observation.numbers
        is DrawNumberObservation.RangeCheckUnavailable -> observation.numbers
        is DrawNumberObservation.OutOfRange -> null
    }

/**
 * D-4B7-8 — `ProvenanceRules.judgeRow`(1D 커널, `decision`)의 첫 production 호출부.
 * `budgetEstimate`는 legacy `Project.budget_estimate`(사업금액/추정가격) 축이다 —
 * `docs/discovery/data-dictionary.md` §1.2가 `Notice.estimatedAmount`를 그 개념의 V2
 * 대응으로 확정한다(`bid-vector/app/services/base_amount_basis.py:96-99`의
 * `_BasisContext.budget_estimate` 주석). CLEAN 강제는 하지 않는다 — 라벨만 붙이고
 * 필터링은 엔진(`admit_clean`)이 진다(설계 검토 우회 (5)).
 */
internal fun provenanceLabelFor(
    notice: Notice,
    opening: OpeningResult,
    original: BaseAmount,
    policy: Resolution.Resolved<ProvenancePolicyData>,
): BaseAmountProvenance {
    val budgetEstimate =
        notice.estimatedAmount
            ?.amount
            ?.export()
            ?.won
            ?.let(BigDecimal::valueOf)
    val winningAmount =
        opening.finalAwardAmount
            ?.export()
            ?.won
            ?.let(BigDecimal::valueOf)
    val row =
        ProvenanceRow(
            rawBaseAmount = BigDecimal.valueOf(original.export().won),
            budgetEstimate = budgetEstimate,
            winningAmount = winningAmount,
            winningRate = opening.winningRate,
        )
    // 이 slice는 복구 추정치를 계산하지 않는다(3D 소관) — Absent로 그 사실을 정직하게 나른다.
    val recoveryEstimate = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE)
    return ProvenanceRules.judgeRow(original, row, recoveryEstimate, policy).classification
}
