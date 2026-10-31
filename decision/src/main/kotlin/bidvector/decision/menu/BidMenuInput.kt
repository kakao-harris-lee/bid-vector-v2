package bidvector.decision.menu

import bidvector.decision.FloorShortfallPolicyData
import bidvector.decision.PublishedFloorTrust
import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Resolution

/**
 * 메뉴 커널의 입력 — **port·Clock·전역을 읽지 않는다**. 호출부가 fact 를 값으로 풀어 준다.
 *
 * `publishedFloorRate` 는 **게이트 판정**이다(DEC-10, D-DEC10-3) — 날 `FloorRate?` 가 아니다.
 * 호출부가 `trustPublishedFloorRate` 를 거쳐야 이 칸을 채울 수 있고, 통과 값은 그 함수만
 * 만들 수 있다. 세 가지 실패(부재·개연 불가·심판 아님)는 전부 **출처 부재**
 * ([BidMenuOutcome.SourceAbsent])로 떨어지되 **사유가 서로 다르다** — 「재려 했으나 재지
 * 못했다」와도 다른 어휘다(`data-dictionary.md` §1.1 vs §1.6).
 *
 * `shortfallPolicy` 는 DEC-04 커널에 **그대로 넘긴다** — 메뉴는 그 정책을 해석하지 않는다.
 */
data class BidMenuInput(
    val base: BaseAmount,
    val rates: StanceRates,
    val publishedFloorRate: PublishedFloorTrust,
    val assessmentSamples: List<AssessmentRate>,
    val shortfallPolicy: Resolution.Resolved<FloorShortfallPolicyData>,
)
