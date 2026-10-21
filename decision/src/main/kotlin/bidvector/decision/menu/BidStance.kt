package bidvector.decision.menu

import bidvector.sharedkernel.Rate

/**
 * 투찰가 메뉴의 태도 셋(`capability-map.md` DEC-05) — 운영자는 값 하나가 아니라 **태도가 다른
 * 선택지**를 근거와 함께 본다.
 *
 * 태도는 **확률이 아니다**(DEC-04 정직 명세 1 — 「확률이라고 부르지 않는다」). `Aggressive`
 * 가 「낙찰 가능성이 높다」를 뜻하지 않는다 — 더 낮은 투찰율을 쓴다는 운영자의 자세일 뿐이고,
 * 그 자세의 대가는 [bidvector.decision.FloorShortfall] 이 빈도로 말한다.
 */
enum class BidStance {
    Safe,
    Recommended,
    Aggressive,
}

/**
 * 태도별 투찰율 셋 — **정확히 셋이고 리스트가 아니다**(`BidRateCandidates` 관례, 2B ③ 라벨
 * 순서 고정). 「2안만 있는 메뉴」가 표현 불가능하다.
 *
 * **라벨 대응**: ML 계약의 `bidvector.workflow.prediction.BidRateCandidates(conservative,
 * base, aggressive)` 와 이름이 다르다(`conservative` ↔ [safe], `base` ↔ [recommended]).
 * 그 대응은 **`workflow` 의 변환 함수 한 자리**에 선언한다 — 이 모듈은 `workflow` 를 볼 수
 * 없다(`decision` 의 의존은 `shared-kernel` 하나, ADR 0006 D-4 · `decision/build.gradle.kts`).
 * 두 자리에 적으면 라벨이 갈린다.
 *
 * 순서 불변식은 `BidRateCandidates` 와 같은 이유로 생성 시점에 건다 — 역순·중복 값의 위조를
 * 막는다.
 */
data class StanceRates(
    val safe: Rate,
    val recommended: Rate,
    val aggressive: Rate,
) {
    init {
        require(safe <= recommended) { "StanceRates.safe($safe)는 recommended($recommended) 이하여야 한다" }
        require(recommended <= aggressive) { "StanceRates.recommended($recommended)는 aggressive($aggressive) 이하여야 한다" }
    }

    /** 태도 → 투찰율. `when` 이 전수라 태도가 늘면 이 자리가 컴파일로 드러난다. */
    fun rateOf(stance: BidStance): Rate =
        when (stance) {
            BidStance.Safe -> safe
            BidStance.Recommended -> recommended
            BidStance.Aggressive -> aggressive
        }
}
