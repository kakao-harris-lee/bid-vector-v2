package bidvector.decision.menu

import bidvector.decision.FloorShortfallPolicyData
import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Resolution

/**
 * 메뉴 커널의 입력 — **port·Clock·전역을 읽지 않는다**. 호출부가 fact 를 값으로 풀어 준다.
 *
 * `publishedFloorRate` 가 nullable 인 것은 「게시 하한율이 없는 공고」가 실재하기 때문이고,
 * 그 경우는 실패가 아니라 **출처 부재**([BidMenuOutcome.SourceAbsent])다 — 「재려 했으나
 * 재지 못했다」와 다른 어휘다(`data-dictionary.md` §1.1 vs §1.6).
 *
 * `shortfallPolicy` 는 DEC-04 커널에 **그대로 넘긴다** — 메뉴는 그 정책을 해석하지 않는다.
 */
data class BidMenuInput(
    val base: BaseAmount,
    val rates: StanceRates,
    val publishedFloorRate: FloorRate?,
    val assessmentSamples: List<AssessmentRate>,
    val shortfallPolicy: Resolution.Resolved<FloorShortfallPolicyData>,
)
