package bidvector.workflow.prediction

import bidvector.decision.menu.StanceRates

/**
 * ML 후보 셋 → 메뉴 태도 셋의 **유일한 대응 선언**(M6/G1-DEC05 산출물 1).
 *
 * **두 축은 뒤집혀 있다 — 이름이 비슷하다고 같은 자리가 아니다.** 계약의
 * `conservative`·`base`·`aggressive` 는 **통계 spread**(값 오름차순, 2B ③ 라벨 고정)이고,
 * 메뉴의 `safe`·`recommended`·`aggressive` 는 **투찰 전략 태도**다. 1차 출처 셋이 그 뒤집힘을
 * 말한다:
 * - `app/ai/bid_target.py` docstring — *「aggressive (band floor — most competitive, 낙하 위험),
 *   safe (band ceiling — safe from 낙하, less competitive)」*
 * - `app/ai/price_prediction/guardrails_apply.py:191-199` 브리지 — *「후보 conservative=최저
 *   추정가(낮은 율)=최경쟁=공격 stance … aggressive=최고 추정가(높은 율)=낙하 안전=안전 stance」*
 * - `contracts/proto/bidvector/ml/v1/prediction.proto:18-21` — 운영자 메뉴는 `CandidateLabel`
 *   축이 **아니다**
 *
 * 그래서 **`conservative` → [StanceRates.aggressive]**, **`aggressive` → [StanceRates.safe]** 다.
 *
 * **라운드 1 의 결함(HIGH-1)이 바로 이 자리였다.** 앞 판은 이 대응을 「같은 순서 축의 다른
 * 라벨이라 **자명하다**」고 적고 이름 그대로 이었다 — `conservative`→`safe`. 그 결과 운영자가
 * 보는 「안전」이 셋 중 **낙하 위험이 가장 큰** 후보가 됐다. 자명해 보이는 대응일수록 1차
 * 출처로 정당화한다.
 *
 * 여기 있는 이유: `decision` 은 `shared-kernel` 하나에만 의존하므로(ADR 0006 D-4)
 * [BidRateCandidates] 를 볼 수 없다.
 *
 * 순서 불변식은 양쪽이 각자 건다 — 계약은 `conservative <= base <= aggressive`, 메뉴는
 * `aggressive <= recommended <= safe` 로 **방향이 반대**이고, 이 변환이 그 뒤집음을 수행한다.
 */
fun BidRateCandidates.toStanceRates(): StanceRates =
    StanceRates(
        safe = aggressive,
        recommended = base,
        aggressive = conservative,
    )
