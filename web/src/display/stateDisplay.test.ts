import { describe, expect, it } from 'vitest';

import { displayOf, type StateDisplay } from './stateDisplay';

/**
 * 표 조회의 **유일한 자리**를 잠근다(D-7E-4). 표 밖의 값에 문면을 지어내면 화면이 계약에
 * 없는 상태를 아는 것처럼 보인다 — 폴백을 더하는 변이가 여기서 RED 다.
 */
describe('상태 표시 표 조회', () => {
  const table = {
    KNOWN: { label: '아는 값', tone: 'neutral' },
  } as const satisfies Record<'KNOWN', StateDisplay>;

  it('표에 있는 값은 그 행을 낸다', () => {
    expect(displayOf(table, 'KNOWN').label).toBe('아는 값');
  });

  /**
   * 계약 밖 값을 캐스팅으로 넣는다 — 런타임에 그런 값이 오는 경로는 「계약이 늘었는데 화면이
   * 아직 모른다」 하나이고, 그때 화면은 **멈춰야** 한다(오류 경계가 받는다). 문면을 지어내면
   * 그 상태를 운영자가 아는 것처럼 읽힌다.
   */
  it('표 밖의 값에는 문면을 지어내지 않는다 — 폴백 0', () => {
    const outsideContract = 'FUTURE_VALUE' as 'KNOWN';

    expect(displayOf(table, outsideContract)).toBeUndefined();
  });
});
