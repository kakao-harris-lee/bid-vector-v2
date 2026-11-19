import { describe, expect, it } from 'vitest';

import { exitTargetsOf } from '../journey/touchMeasure.ts';

/** 화면마다 꼴이 다르다 — 워크시트와 입찰함 카드 둘을 같은 술어로 잰다. */
const WORKSHEET = 'worksheet-exit-';
const CARD = 'exit-';

/**
 * **내려놓기 두 자리를 고르는 술어**(M13/13E 머지 차례 — v L-1).
 *
 * 여정 ③ 은 「나중에」·「건너뜀」이 **상자로 선다**를 재는데, 앞 판은 그 자리를 **이름**으로 골랐다.
 * 13E 가 2층 라벨을 버튼으로 만들면서 **상태 배지도 그 낱말을 이름으로 들게** 됐고(「나중에」
 * 상태의 건), 그때 이름 필터는 **엉뚱한 자리를 세고도 초록**이 된다 — 고르는 축과 재는 축이
 * 같아지는 자리다.
 *
 * 이 파일은 그 좁힘(**testId 로 고른다**)이 실제로 가르는지를 **합성 타깃**으로 잰다. 여정은
 * 하네스가 있어야 돌지만 술어는 값이므로 여기서 선다 — 그 둘이 **같은 함수**를 본다.
 */
const target = (name, testId) => ({ tag: 'button', name, testId, width: 80, height: 44 });

/** 「나중에」 상태의 건에서 실제로 서는 모양 — 배지가 그 낱말을 이름으로 든다. */
const DEFERRED_BADGE = target('나중에', '');
const EXIT_LATER = target('나중에', 'worksheet-exit-DEFER');
const EXIT_SKIP = target('건너뜀', 'worksheet-exit-SKIP');

describe('내려놓기 자리는 이름이 아니라 testId 로 고른다', () => {
  it('상태 배지가 같은 낱말을 들어도 그것을 세지 않는다', () => {
    const picked = exitTargetsOf([DEFERRED_BADGE, EXIT_LATER, EXIT_SKIP], WORKSHEET);

    expect(picked.map((one) => one.testId)).toStrictEqual(['worksheet-exit-DEFER', 'worksheet-exit-SKIP']);
  });

  /**
   * **이것이 거짓 초록의 모양이다.** 내려놓기 컨트롤 둘이 사라지고 배지만 남았을 때, 이름으로
   * 고르는 술어는 「나중에」를 찾아내 **무언가 서 있다**고 말한다. testId 로 고르면 **빈 집합**이
   * 되어 그 사실이 드러난다.
   */
  it('컨트롤이 사라지고 배지만 남으면 빈 집합이다 — 이름 필터는 그것을 숨긴다', () => {
    const onlyBadge = [DEFERRED_BADGE];

    expect(exitTargetsOf(onlyBadge, WORKSHEET)).toStrictEqual([]);
    // 앞 판의 술어(이름으로 고르기)는 같은 입력에서 **하나를 센다** — 그것이 거짓 초록의 재료다.
    expect(onlyBadge.filter((one) => ['나중에', '건너뜀'].includes(one.name))).toHaveLength(1);
  });

  it('접두가 다른 testId 는 들지 않는다', () => {
    const picked = exitTargetsOf([target('담기', 'add-7'), target('나중에', 'exit-DEFER-7')], CARD);

    expect(picked.map((one) => one.testId)).toStrictEqual(['exit-DEFER-7']);
  });

  /**
   * **접두는 화면마다 다르다**(여정 실측으로 밟은 자리). 한 꼴을 고정하면 다른 화면에서 **빈
   * 집합**이 되고, 빈 집합은 「컨트롤이 없다」와 구별되지 않는다 — 그래서 부르는 자리가 준다.
   */
  it('워크시트 꼴과 카드 꼴이 서로의 자리를 집지 않는다', () => {
    const both = [target('나중에', 'worksheet-exit-DEFER'), target('나중에', 'exit-DEFER-7')];

    expect(exitTargetsOf(both, WORKSHEET).map((one) => one.testId)).toStrictEqual(['worksheet-exit-DEFER']);
    expect(exitTargetsOf(both, CARD).map((one) => one.testId)).toStrictEqual(['exit-DEFER-7']);
  });
});
