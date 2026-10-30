import { describe, expect, it } from 'vitest';

import { fallbackRoute, loginRoute, screenRoutes, screenSlots } from './routes';

// 자리 여섯은 그대로다. M7/7E-1 이 둘(B·C)을, M7/7E-2 가 D 를 실화면으로 채웠고 셋은 여전히
// 빈 자리다 — 아래 둘째 describe 가 그 경계를 잠근다(범위 밖 화면을 만들지 않는다, D-7E-5).
const EXPECTED_PATHS = [
  '/start',
  '/',
  '/notices/:noticeNumber/:noticeRound',
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

/** D-7E-5 — 범위 밖 화면은 「준비 중」 빈 자리뿐이다. 그 경계가 표의 `element` 유무다. */
describe('이 slice 가 채운 자리', () => {
  it('실화면은 B·C·D 셋이고 나머지 셋은 빈 자리다', () => {
    const implemented = screenSlots.filter((slot) => slot.element !== undefined).map((slot) => slot.id);

    expect(implemented).toEqual(['home', 'worksheet', 'bid-box']);
  });
});
