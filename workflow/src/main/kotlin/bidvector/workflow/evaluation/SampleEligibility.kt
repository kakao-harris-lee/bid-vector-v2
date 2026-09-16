package bidvector.workflow.evaluation

import bidvector.decision.ProvenancePolicyData
import bidvector.decision.ProvenanceRuleId
import bidvector.procurement.DrawNumberObservation
import bidvector.procurement.Notice
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.OpeningReservePriceRow
import bidvector.procurement.OpeningResult
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.workflow.prediction.CompetitionSample
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 표본 자격 판정 정책 슬롯(M4/4B-7, D-4B7-2 ③) — 예비가격 정상 건수. 엔진
 * `reserve.expected_price_count`(`ml-engine/policy/inference-v1.yaml`)와 1:1이어야
 * 한다(어긋나면 엔진이 `PRICE_COUNT_MISMATCH`로 거부한다) — 착수 값 15는
 * `reports/evidence/m4/4b7/policy-values.md`가 정본이고 `OPEN-4B7-POLICY-VALUES`로
 * 승인 대기다.
 */
data class SampleEligibilityPolicyData(
    val expectedReservePriceCount: Int,
) {
    init {
        require(expectedReservePriceCount > 0) {
            "expectedReservePriceCount는 양수여야 한다: $expectedReservePriceCount"
        }
    }
}

val SAMPLE_ELIGIBILITY_POLICY: EffectiveDatedPolicy<SampleEligibilityPolicyData> =
    EffectiveDatedPolicy(
        source =
            "ml-engine/policy/inference-v1.yaml reserve.expected_price_count(15) — " +
                "reports/evidence/m4/4b7/policy-values.md, 승인 대기(OPEN-4B7-POLICY-VALUES)",
        entries = listOf(EffectiveFrom.Initial to SampleEligibilityPolicyData(expectedReservePriceCount = 15)),
    )

/**
 * D-4B7-8 라벨링에 쓰는 provenance 정책 — legacy 값 그대로 옮긴 잠정 인스턴스다(순서 +
 * 임계 넷, `bid-vector/app/services/base_amount_basis.py:38-56,200-218` ·
 * `bid-vector/app/core/constants.py` `BID_BASE_TRUST_RATIO_MAX`). `decision` 모듈은 아직
 * 운영 정본(`EffectiveDatedPolicy<ProvenancePolicyData>`)을 갖지 않고 이 slice의 in_scope도
 * `decision` 모듈을 포함하지 않는다 — 그래서 이 인스턴스는 `workflow` 쪽에 둔다.
 * `reports/evidence/m4/4b7/policy-values.md`가 정본이고 `OPEN-4B7-POLICY-VALUES`로 승인
 * 대기다. `integerRoundingMode`(legacy `round()`의 정확한 반올림 모드는 `OPEN-DIC-10` 미결)는
 * `HALF_UP`을 다른 정책 슬롯(`OpportunityPolicyData.recommendedAmountRounding`)과 같은
 * 관례로 잠정 채택한다.
 */
val SAMPLE_PROVENANCE_POLICY: EffectiveDatedPolicy<ProvenancePolicyData> =
    EffectiveDatedPolicy(
        source =
            "legacy-behavior: bid-vector/app/services/base_amount_basis.py:38-56,200-218 · " +
                "bid-vector/app/core/constants.py BID_BASE_TRUST_RATIO_MAX — " +
                "reports/evidence/m4/4b7/policy-values.md, 승인 대기(OPEN-4B7-POLICY-VALUES)",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    ProvenancePolicyData(
                        ruleOrder =
                            listOf(
                                ProvenanceRuleId.SuspectRatio,
                                ProvenanceRuleId.CleanInteger,
                                ProvenanceRuleId.DerivedYega,
                                ProvenanceRuleId.DerivedVat,
                            ),
                        trustRatioMax = Rate.ofFraction(BigDecimal("1.15")),
                        cleanIntegerTolerance = BigDecimal("0.000001"),
                        vatMultiplier = BigDecimal("1.1"),
                        vatTolerance = BigDecimal("0.01"),
                        yegaTolerance = BigDecimal("1.0"),
                        integerRoundingMode = RoundingMode.HALF_UP,
                    ),
            ),
    )

/**
 * D-4B7-7 — 실개찰일시(`actual_opening_at`)를 날짜로 접는 시간대. 착수 가정이다(정본
 * `OPEN-3A-SOURCE-TZ`, 아직 미결) — 정책 슬롯을 새로 두지 않고 상수로 둔다(scope.md
 * 문면 그대로, `reports/evidence/m4/4b7/checklist.md`에 등재).
 */
val OPENING_DATE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/** [judgeEligibility]의 결과 — 조용한 drop 없이 사유를 값으로 나른다(D-4B7-2). */
sealed interface SampleEligibilityOutcome {
    data class Eligible(
        val sample: CompetitionSample,
    ) : SampleEligibilityOutcome

    data class Excluded(
        val reason: SampleExclusionReason,
    ) : SampleEligibilityOutcome
}

/**
 * guard 체인 조합자(v2-지침서 §5 분기 도배 금지 — `OpportunityAnalysis.Step`과 같은 처방을
 * 이 파일에도 적용한다, detekt `ReturnCount`) — 성공은 값을, 실패는 사유를 나른다. 7단계
 * (D-4B7-2 ②~⑦)를 사다리꼴 `if(...)return` 대신 각 단계를 독립 함수로 나눠
 * [Check.Failed]로 값으로 옮긴다.
 */
private sealed interface Check<out T> {
    data class Passed<T>(
        val value: T,
    ) : Check<T>

    data class Failed(
        val reason: SampleExclusionReason,
    ) : Check<Nothing>
}

private fun <T> passed(value: T): Check<T> = Check.Passed(value)

private fun failed(reason: SampleExclusionReason): Check<Nothing> = Check.Failed(reason)

private inline fun <T, R> Check<T>.andThen(f: (T) -> Check<R>): Check<R> =
    when (this) {
        is Check.Passed -> f(value)
        is Check.Failed -> this
    }

/**
 * 표본 자격 판정(M4/4B-7, D-4B7-2 ②~⑦, scope.md ③) — port를 읽지 않는 순수 함수다. 어댑터가
 * 조인·창·상한만 진 candidate 쌍(D-4B7-4)을 판정한다 — SQL에 자격 판정을 두지 않는다. 판정
 * 순서는 D-4B7-2의 번호 순서(②~⑦) 그대로다.
 */
fun judgeEligibility(
    notice: Notice,
    opening: OpeningResult,
    eligibilityPolicy: SampleEligibilityPolicyData,
    provenancePolicy: Resolution.Resolved<ProvenancePolicyData>,
): SampleEligibilityOutcome {
    val check =
        rankOneBidRateCheck(opening.openingRankOne).andThen { bidRate ->
            reservePriceCountCheck(opening, eligibilityPolicy).andThen {
                reservePriceSequenceCheck(opening.reservePrices, eligibilityPolicy).andThen {
                    reservePriceAmountsCheck(notice, opening.reservePrices).andThen { reservePrices ->
                        drawNumberCheck(opening.drawNumbers).andThen { selectedNumbers ->
                            baseAmountCheck(notice).andThen { resolvedBaseAmount ->
                                openedOnCheck(opening).andThen { openedOn ->
                                    passed(
                                        sampleOf(
                                            notice,
                                            opening,
                                            provenancePolicy,
                                            bidRate,
                                            resolvedBaseAmount.amount,
                                            openedOn,
                                            reservePrices,
                                            selectedNumbers,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    return when (check) {
        is Check.Passed -> SampleEligibilityOutcome.Eligible(check.value)
        is Check.Failed -> SampleEligibilityOutcome.Excluded(check.reason)
    }
}

/** D-4B7-2 ② — `Determined`가 아니거나 `bidRate`가 없으면(협상 계약 등) 자격이 없다. */
private fun rankOneBidRateCheck(outcome: OpeningRankOneOutcome): Check<Rate> {
    val bidRate = (outcome as? OpeningRankOneOutcome.Determined)?.bid?.bidRate
    return bidRate?.let(::passed) ?: failed(SampleExclusionReason.RANK_ONE_RATE_MISSING)
}

/** D-4B7-2 ③ — 예비가격 행 수가 정책의 `expectedReservePriceCount`(착수 값 15)와 정확히 같아야 한다. */
private fun reservePriceCountCheck(
    opening: OpeningResult,
    policy: SampleEligibilityPolicyData,
): Check<Unit> =
    if (opening.reservePrices.size == policy.expectedReservePriceCount) {
        passed(Unit)
    } else {
        failed(SampleExclusionReason.RESERVE_PRICE_COUNT_MISMATCH)
    }

/**
 * D-4B7-2 ③ 확장(verifier r1 F-4) — 엔진이 `selected_numbers`를 wire `reserve_prices`
 * 리스트의 1-기반 인덱스로 소비하므로, 행 수가 맞아도 `sequenceNumber` 집합이 정확히
 * `1..expectedReservePriceCount`가 아니면(중복·결측·범위 밖) 위치가 번호와 어긋난다 —
 * `reservePriceCountCheck`(건수)와 다른 축이다.
 */
private fun reservePriceSequenceCheck(
    rows: List<OpeningReservePriceRow>,
    policy: SampleEligibilityPolicyData,
): Check<Unit> {
    val expected = (1..policy.expectedReservePriceCount).toSet()
    val parsed = rows.mapNotNull { it.sequenceNumber.toIntOrNull() }
    return if (parsed.size == rows.size && parsed.toSet() == expected) {
        passed(Unit)
    } else {
        failed(SampleExclusionReason.RESERVE_PRICE_SEQUENCE_INVALID)
    }
}

private fun reservePriceAmountsCheck(
    notice: Notice,
    rows: List<OpeningReservePriceRow>,
): Check<List<BaseAmount>> =
    reservePriceAmounts(notice, rows)?.let(::passed) ?: failed(SampleExclusionReason.RESERVE_PRICE_MISSING)

private fun drawNumberCheck(observation: DrawNumberObservation): Check<Set<Int>> =
    drawNumberSet(observation)?.let(::passed) ?: failed(SampleExclusionReason.DRAW_NUMBERS_OUT_OF_RANGE)

private fun baseAmountCheck(notice: Notice): Check<ResolvedBaseAmount> =
    notice.baseAmount?.let(::passed) ?: failed(SampleExclusionReason.BASE_AMOUNT_MISSING)

/** D-4B7-7 — `actualOpeningAt`을 [OPENING_DATE_ZONE]으로 접은 날짜. 결측이면 실격. */
private fun openedOnCheck(opening: OpeningResult): Check<LocalDate> {
    val openedOn = opening.actualOpeningAt?.atZone(OPENING_DATE_ZONE)?.toLocalDate()
    return openedOn?.let(::passed) ?: failed(SampleExclusionReason.OPENING_DATE_MISSING)
}
