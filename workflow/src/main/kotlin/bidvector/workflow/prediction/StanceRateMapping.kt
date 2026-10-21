package bidvector.workflow.prediction

import bidvector.decision.menu.StanceRates

/**
 * ML 후보 셋 → 메뉴 태도 셋의 **유일한 대응 선언**(M6/G1-DEC05 산출물 1 — 「라벨이 다르다,
 * 매핑을 한 자리에 선언」).
 *
 * 두 체계의 이름이 다르다 — 계약은 `conservative`·`base`·`aggressive`(2B ③ 라벨 고정,
 * legacy 「기준 후보는 항상 가운데」)이고 메뉴는 `safe`·`recommended`·`aggressive`
 * (`capability-map.md` DEC-05 의 운영자 어휘)다. 같은 순서 축의 다른 라벨이므로 대응은
 * 자명하지만, **자명한 대응을 두 자리에 적으면 갈린다** — 그래서 이 함수 하나만 둔다.
 *
 * 여기 있는 이유: `decision` 은 `shared-kernel` 하나에만 의존하므로(ADR 0006 D-4)
 * [BidRateCandidates] 를 볼 수 없다. 반대 방향(`workflow` → `decision`)은 이미 열려 있다.
 *
 * 순서 불변식은 양쪽이 각자 건다 — [BidRateCandidates] 와 [StanceRates] 의 `init` 이 같은
 * 부등식을 독립으로 재므로, 이 변환이 순서를 뒤집으면 생성 시점에 깨진다.
 */
fun BidRateCandidates.toStanceRates(): StanceRates =
    StanceRates(
        safe = conservative,
        recommended = base,
        aggressive = aggressive,
    )
