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
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import bidvector.workflow.prediction.CompetitionSample
import bidvector.workflow.prediction.ReserveDrawObservation
import java.math.BigDecimal
import java.time.LocalDate

/**
 * `SampleEligibility.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions` — 한 파일에 열넷을
 * 몰아두지 않는다, `MoneyMapping.kt`와 같은 판단). `internal` — 같은 모듈 안 `judgeEligibility`
 * (`SampleEligibility.kt`, 같은 패키지)가 그대로 쓴다.
 *
 * `sortedBy { ... }`(inline `compareBy` 경유)는 합성 클래스의 `SourceFile` 디버그 속성이
 * stdlib `Comparisons.kt`로 남아 `jarContentGate`(ADR 0006 §6)가 「게이트를 통과한 소스가
 * 아니다」로 거부한다(실측, `ObservationPayloadCodec.kt`와 같은 함정) — [RESERVE_PRICE_SEQUENCE_ORDER]
 * 처럼 이름 있는 [Comparator]로 둔다(`reservePriceAmounts`가 쓴다).
 */
private val RESERVE_PRICE_SEQUENCE_ORDER =
    Comparator<OpeningReservePriceRow> { left, right ->
        val leftSequence = left.sequenceNumber.toIntOrNull() ?: Int.MAX_VALUE
        val rightSequence = right.sequenceNumber.toIntOrNull() ?: Int.MAX_VALUE
        leftSequence.compareTo(rightSequence)
    }

/**
 * D-4B7-1 (a) — `bsisPlnprc`(기초예정가격, basis 미확정 값 객체)를 기초금액 축 원문
 * 관측값으로 옮긴다: `basis = BASE_AMOUNT`, `currency` 그대로, `vatTreatment = UNKNOWN`,
 * `provenance = Published(표본 공고 자기 회차)`(우회 (18) — 대상 공고 회차를 빌리지 않는다).
 * 행 하나라도 `baseReservePrice`가 없으면(D-4B7-2 ④) 전체를 실격시킨다.
 *
 * **[RESERVE_PRICE_SEQUENCE_ORDER]로 정렬한다(verifier r1 F-4 뒤).** 엔진은
 * `selected_numbers`를 결과 리스트의 1-기반 인덱스로 소비한다 — 그래서 출력 위치
 * i(0-기반)는 항상 순번 i+1의 예비가격이어야 한다. 이전 판은 `rows`의 입력 순서를 그대로
 * 썼는데(production 유일한 공급자인 `SELECT_OPENING_RESERVE_PRICES … ORDER BY
 * reserve_price_sequence`가 우연히 정렬해 주었을 뿐), 그 결합이 암묵적이고 무측정이었다.
 * `SampleEligibility.judgeEligibility`의 `reservePriceSequenceCheck`가 이 함수를 부르기
 * 전에 순번 집합이 정확히 `1..expectedReservePriceCount`임을 이미 보증하므로, 여기서는
 * 그 정렬만 하면 된다(`toIntOrNull()`이 `null`을 낼 일은 그 검사를 통과한 입력에서는 없다).
 */
internal fun reservePriceAmounts(
    notice: Notice,
    rows: List<OpeningReservePriceRow>,
): List<BaseAmount>? {
    val published = Provenance.Published(notice.id.round)
    val sorted = rows.sortedWith(RESERVE_PRICE_SEQUENCE_ORDER)
    val amounts = mutableListOf<BaseAmount>()
    for (row in sorted) {
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
 *
 * M4/4B-8(D-4B8-1·2) — `opening`을 `OpeningResult?`로 일반화했다. 표본(`sampleOf`)은
 * 항상 있는 개찰 결과를 넘기고, 대상 공고(`predictionRequestFor`)는 개찰 전이라 `null`을
 * 넘긴다 — `winningAmount`·`winningRate`가 그대로 `null`이 되어 `ProvenanceRules.
 * isDerivedYega`(둘 다 non-null을 요구)가 구조적으로 `false`다. 대상용 별도 함수를 두지
 * 않는다(같은 분류기 재사용, D-4B8-1 「두 번째 분류기 금지」).
 */
internal fun provenanceLabelFor(
    notice: Notice,
    opening: OpeningResult?,
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
        opening
            ?.finalAwardAmount
            ?.export()
            ?.won
            ?.let(BigDecimal::valueOf)
    val row =
        ProvenanceRow(
            rawBaseAmount = BigDecimal.valueOf(original.export().won),
            budgetEstimate = budgetEstimate,
            winningAmount = winningAmount,
            winningRate = opening?.winningRate,
        )
    // 이 slice는 복구 추정치를 계산하지 않는다(3D 소관) — Absent로 그 사실을 정직하게 나른다.
    val recoveryEstimate = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE)
    return ProvenanceRules.judgeRow(original, row, recoveryEstimate, policy).classification
}

/**
 * `SampleEligibility.judgeEligibility`의 guard 체인이 전부 통과한 뒤 값을 조립한다
 * (`SampleEligibility.kt`에서 갈라낸 함수 — detekt `TooManyFunctions`, verifier r1
 * 뒤 12개로 늘어나 한계를 넘었다). `internal` — 같은 패키지의 `judgeEligibility`만 쓴다.
 */
@Suppress("LongParameterList")
internal fun sampleOf(
    notice: Notice,
    opening: OpeningResult,
    provenancePolicy: Resolution.Resolved<ProvenancePolicyData>,
    bidRate: Rate,
    baseAmount: BaseAmount,
    openedOn: LocalDate,
    reservePrices: List<BaseAmount>,
    selectedNumbers: Set<Int>,
): CompetitionSample =
    CompetitionSample(
        observedBidRate = bidRate,
        baseAmount = baseAmount,
        baseAmountProvenanceLabel = provenanceLabelFor(notice, opening, baseAmount, provenancePolicy),
        openedOn = openedOn,
        awardRate = opening.winningRate,
        agencyId = null,
        categoryCode = notice.businessCategory?.code,
        reserveDraw = ReserveDrawObservation(reservePrices, selectedNumbers),
    )
