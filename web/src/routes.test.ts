import { describe, expect, it } from 'vitest';

import { fallbackRoute, loginRoute, screenRoutes, screenSlots } from './routes';

// 7A 가 만든 것은 「화면 자리 여섯」이다. 그 여섯이 맞는지만 잠근다 — 화면 동작 test 는 7E.
const EXPECTED_PATHS = [
  '/start',
  '/',
  '/notices/:noticeId',
  '/bids',
  '/results',
  '/inbox',
] as const;

describe('화면 자리 표', () => {
  it('경로가 여섯, 순서까지 요구표의 화면 구분과 같다', () => {
    expect(screenSlots.map((slot) => slot.path)).toEqual([...EXPECTED_PATHS]);
  });

  it('라우터는 표에서 파생한다 — 경로 집합이 표와 같고 id 가 겹치지 않는다', () => {
    expect(screenRoutes.map((route) => route.path)).toEqual([...EXPECTED_PATHS]);
    const ids = screenRoutes.map((route) => route.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('없는 주소 자리는 표 **밖**에 있고 catch-all 이다', () => {
    expect(fallbackRoute.path).toBe('*');
    expect(screenSlots.map((slot) => slot.path)).not.toContain('*');
  });

  // 7D — 인증 자리도 표 **밖**이다(화면 구분이 아니다). 표에 들어오면 셸의 전역 탐색에
  // 로그인 링크가 생기고, 그것은 요구표의 화면 여섯과 다른 축이다.
  it('로그인 자리는 표 밖에 있고 경로가 하나다', () => {
    expect(loginRoute.path).toBe('/login');
    expect(screenSlots.map((slot) => slot.path)).not.toContain('/login');
  });
});
