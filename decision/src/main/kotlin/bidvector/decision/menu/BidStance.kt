package bidvector.decision.menu

import bidvector.sharedkernel.Rate

/**
 * 투찰가 메뉴의 태도 셋(`capability-map.md` DEC-05) — 운영자는 값 하나가 아니라 **태도가 다른
 * 선택지**를 근거와 함께 본다.
 *
 * **축의 방향**(legacy 1차 출처로 확정, M6/G1-DEC05 라운드 2): 투찰율이 **낮을수록 공격**이고
 * **높을수록 안전**이다. `app/ai/bid_target.py` docstring 이 *「aggressive (band floor — most
 * competitive, 낙하 위험), safe (band ceiling — safe from 낙하, less competitive)」* 라고 적는다 —
 * 낮은 율은 가격 경쟁력을 얻는 대신 낙찰하한 **미달(낙하)** 위험을 지고, 높은 율은 그 반대다.
 * 그래서 [Safe] 가 셋 중 **가장 높은 투찰율**이다.
 *
 * 태도는 **확률이 아니다**(DEC-04 정직 명세 1 — 「확률이라고 부르지 않는다」). [Aggressive]
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
 * **라벨 대응 — 두 축은 서로 뒤집혀 있다.** ML 계약의
 * `bidvector.workflow.prediction.BidRateCandidates(conservative, base, aggressive)` 는
 * **통계 spread** 축(값 오름차순)이고 이 타입은 **투찰 전략** 축이다. legacy 브리지
 * (`app/ai/price_prediction/guardrails_apply.py:191-199`)가 그 뒤집힘을 선언한다 —
 * *「후보 conservative=최저 추정가(낮은 율)=최경쟁=공격 stance … aggressive=최고 추정가
 * (높은 율)=낙하 안전=안전 stance」*. 계약 `.proto` 도 두 축이 다름을 못박는다
 * (`contracts/proto/bidvector/ml/v1/prediction.proto:18-21` — 운영자 메뉴는 `CandidateLabel`
 * 축이 아니다). 그래서 `conservative` ↔ [aggressive] · `aggressive` ↔ [safe] 다.
 *
 * **이 대응을 「자명하다」고 적지 않는다** — 이름이 비슷해 자명해 보이는 것이 정확히 라운드 1
 * 의 반전 결함(HIGH-1)을 낳았다. 대응은 위 1차 출처로만 정당화하고, 변환은 **`workflow` 의
 * 함수 한 자리**에 둔다(이 모듈은 `workflow` 를 볼 수 없다 — ADR 0006 D-4).
 *
 * 순서 불변식은 `BidRateCandidates` 와 같은 이유로 생성 시점에 건다 — 역순·중복 값의 위조를
 * 막는다. **방향이 반대**다: 공격이 가장 낮고 안전이 가장 높다.
 */
data class StanceRates(
    val safe: Rate,
    val recommended: Rate,
    val aggressive: Rate,
) {
    init {
        require(aggressive <= recommended) {
            "StanceRates.aggressive($aggressive)는 recommended($recommended) 이하여야 한다(공격이 가장 낮다)"
        }
        require(recommended <= safe) {
            "StanceRates.recommended($recommended)는 safe($safe) 이하여야 한다(안전이 가장 높다)"
        }
    }

    /** 태도 → 투찰율. `when` 이 전수라 태도가 늘면 이 자리가 컴파일로 드러난다. */
    fun rateOf(stance: BidStance): Rate =
        when (stance) {
            BidStance.Safe -> safe
            BidStance.Recommended -> recommended
            BidStance.Aggressive -> aggressive
        }
}
