package bidvector.decision

import bidvector.sharedkernel.Resolution
import java.math.BigDecimal

/**
 * 게이트 사다리(scope.md ②, 조사 §2.1~§2.2) — legacy `allocation_core.py:163-181`의
 * `if/elif/elif/else`를 옮긴다. **순서가 도메인 의미를 갖는다**(조사 §2.1 실측 —
 * `elif`라 capacity-hold 가 참이면 force-bid 는 평가조차 되지 않는다. 「한도가 찼고
 * priority < 0.8」이면 확률·적합도가 아무리 높아도 `bid_now`로 갈 수 없다). 이 순서는
 * **정책이 아니라 도메인 규칙**이라 코드에 고정한다(1D `ProvenanceRules.ruleOrder`와
 * 다른 판단 — provenance 넷은 같은 행 위의 독립 술어라 순서 자체가 정책 대상이지만,
 * 이 넷은 서로 다른 입력 부분집합을 요구하는 이질적 조건이고 순서 반전은 운영자가
 * 원할 수 있는 선택이 아니라 구조적 불변식이다) — `VerdictLadderTest`의 순서 민감도
 * test 가 그 사실을 실행으로 고정한다.
 *
 * **모든 분기가 결과를 명시 반환한다**(조사 §2.3 N-3 폐쇄 — legacy 의 두 `skip`은
 * 대입이 아니라 부작위였다). 가변 변수·초기값이 없다 — 판정 순서(4A `apply` 관례)를
 * guard 함수 체인(`?:`)으로 쓴다.
 */
object VerdictLadder {
    /**
     * `priorityScore`가 없으면 아무 분기도 평가하지 않고 즉시 `Review(MlUnavailable)`을
     * 낸다 — capacity-hold·review 밴드·priority 기반 승격 전부가 이 값을 요구한다
     * (조사 §7 사다리 입력 다섯 중 유일하게 세 분기가 공유하는 값). priority 만으로
     * `BidNow`가 이미 확정되면 `probability`·`matched` 부재는 묻지 않는다(불필요한
     * `MlUnavailable`을 내지 않는다 — 실제로 그 값을 쓰는 분기에 도달했을 때만 결측을
     * 따진다). **M4/4B-3(scope.md ②)** — 이 분기만 `input.mlUnavailableReason` 슬롯을
     * 실어 낸다(호출자가 「왜 없는가」를 전달할 수 있는 유일한 자리 — force-bid 분기의
     * 결측은 다른 축이라 그대로 `ScoreNotProvided`다).
     */
    fun judge(
        input: LadderInput,
        policy: Resolution.Resolved<VerdictLadderPolicyData>,
    ): Verdict {
        val priority =
            input.priorityScore?.value
                ?: return Verdict.Review(listOf(mlUnavailable(input.mlUnavailableReason)))
        val thresholds = policy.value
        return capacityHoldOutcome(input, priority, thresholds)
            ?: priorityBidNowOutcome(priority, thresholds)
            ?: forceBidOutcome(input, thresholds)
            ?: reviewBandOutcome(priority, thresholds)
            ?: Verdict.Skip(SkipReason.LowPriority)
    }

    /** 분기 1(조사 §2.2) — 진행중 입찰이 한도에 닿고 priority 가 임계 미만이면 보류. */
    private fun capacityHoldOutcome(
        input: LadderInput,
        priority: BigDecimal,
        thresholds: VerdictLadderPolicyData,
    ): Verdict? =
        if (input.currentActiveBids >= input.maxActiveBids && priority < thresholds.capacityHoldPriorityThreshold) {
            Verdict.Skip(SkipReason.CapacityHold)
        } else {
            null
        }

    /** 분기 2(a) — priority 가 bidNowThreshold 이상이면 정상 승격. */
    private fun priorityBidNowOutcome(
        priority: BigDecimal,
        thresholds: VerdictLadderPolicyData,
    ): Verdict? =
        if (priority >= thresholds.bidNowThreshold) {
            Verdict.BidNow(listOf(BidNowReason.PriorityAboveBidNowThreshold(priority, thresholds.bidNowThreshold)))
        } else {
            null
        }

    /**
     * 분기 2(b) — force-bid 게이트(조사 §5.1, `OPEN-STR-03`). `probability`·`matched`가
     * 둘 다 있어야 판정할 수 있다. 하나라도 없으면 「force-bid 가 아니다」로 조용히
     * 넘어가지 않고 `Review(MlUnavailable)`을 낸다 — 그 판정을 안다고 주장하지 않는다.
     * 둘 다 있고 임계를 만족하면 `BidNow`, 아니면 `null`(다음 분기로 넘어간다).
     */
    private fun forceBidOutcome(
        input: LadderInput,
        thresholds: VerdictLadderPolicyData,
    ): Verdict? {
        val probability = input.probabilityScore?.value
        val matched = input.matchedScore?.value
        if (probability == null || matched == null) return Verdict.Review(listOf(mlUnavailable()))
        val qualifies =
            probability >= thresholds.forceBidProbabilityThreshold && matched >= thresholds.forceBidMatchedThreshold
        return if (qualifies) {
            Verdict.BidNow(
                listOf(
                    BidNowReason.ForceBidOverride(
                        probability,
                        matched,
                        thresholds.forceBidProbabilityThreshold,
                        thresholds.forceBidMatchedThreshold,
                    ),
                ),
            )
        } else {
            null
        }
    }

    /** 분기 3 — priority 가 review 밴드 안이면 검토 대기열. */
    private fun reviewBandOutcome(
        priority: BigDecimal,
        thresholds: VerdictLadderPolicyData,
    ): Verdict? =
        if (priority >= thresholds.reviewThreshold) {
            Verdict.Review(
                listOf(
                    ReviewReason.PriorityInReviewBand(priority, thresholds.reviewThreshold, thresholds.bidNowThreshold),
                ),
            )
        } else {
            null
        }

    private fun mlUnavailable(
        reason: MlUnavailableReason = MlUnavailableReason.ScoreNotProvided,
    ): ReviewReason.MlUnavailable = ReviewReason.MlUnavailable(reason)
}
