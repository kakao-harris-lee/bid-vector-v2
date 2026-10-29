import { describe, expect, it } from 'vitest';

import { displayOf, displayOrUnknown, unknownStateDisplay, type StateDisplay } from './stateDisplay';

/**
 * 표 조회의 **유일한 자리**를 잠근다(D-7E-4). 표 밖의 값에 문면을 지어내면 화면이 계약에
 * 없는 상태를 아는 것처럼 보인다 — 폴백을 더하는 변이가 여기서 RED 다.
 */
describe('상태 표시 표 조회', () => {
  const table = {
    KNOWN: { label: '아는 값', tone: 'neutral' },
  } as const satisfies Record<'KNOWN', StateDisplay>;

  it('표에 있는 값은 그 행을 낸다', () => {
    expect(displayOf(table, 'KNOWN')?.label).toBe('아는 값');
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

/**
 * CR M-3 — 표 밖의 값이 **자리마다 다르게** 사라지던 것을 하나로 모았다. 배포 비틀림(새 app.jar
 * + 옛 번들)에서 실제로 오는 값이고, 조용한 빈 자리는 「서버가 안 보냈다」로 읽힌다.
 */
describe('표 밖의 값', () => {
  const table = {
    KNOWN: { label: '아는 값', tone: 'neutral' },
  } as const satisfies Record<'KNOWN', StateDisplay>;

  it('보이는 미지 상태로 낸다 — 서버가 준 코드를 그대로 싣는다', () => {
    const shown = displayOrUnknown(table, 'FUTURE_VALUE' as 'KNOWN');

    expect(shown.label).toContain('FUTURE_VALUE');
    expect(shown.tone).toBe('unjudged');
  });

  it('아는 값에는 그 행을 그대로 낸다', () => {
    expect(displayOrUnknown(table, 'KNOWN').label).toBe('아는 값');
  });

  it('미지 상태의 어조는 「아직 판정하지 않음」이다 — 미달로 읽히지 않는다', () => {
    expect(unknownStateDisplay('X').tone).toBe('unjudged');
  });
});
