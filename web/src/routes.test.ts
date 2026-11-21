import { describe, expect, it } from 'vitest';

import { LOGIN_PATH } from './LoginScreen';
import { fallbackRoute, loginRoute, PRE_SESSION_PATHS, screenRoutes, screenSlots, signUpRoute } from './routes';
import { SIGN_UP_PATH } from './SignUpScreen';

// 화면 자리 여섯에 M7/7E-2 가 「설정」 하나를 더해 일곱이다. M7/7E-3a 가 마지막 둘(E 결과 확인 ·
// 알림함)을 채워 **일곱 자리가 전부 실화면**이다 — 아래 둘째 describe 가 그 경계를 잠근다(D-7E-5).
// 빈 자리가 0 이 된 것은 이 slice 의 결과이고, 「범위 밖 화면을 만들지 않는다」는 그 표의 **길이**가
// 그대로인 것으로 남는다(자리를 새로 늘리지 않았다).
const EXPECTED_PATHS = [
  '/start',
  '/',
  '/notices/:noticeNumber/:noticeRound',
  '/bids',
  '/results',
  '/inbox',
  // M7/7E-2 — 설정 자리(D-7E2-9). 요구표의 「설정」 행이고 화면 다섯과 다른 축이다.
  '/settings',
] as const;

describe('화면 자리 표', () => {
  it('경로가 일곱, 순서까지 요구표의 화면 구분과 같다', () => {
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

  /**
   * M15/15B — 가입 자리도 같은 축이다(인증 자리, 표 밖). 그리고 **세션 전 자리 집합**이 그 둘과
   * 같다 — 셸이 전역 탐색을 지우는 모집단이라, 자리가 늘었는데 집합이 늘지 않으면 새 화면에
   * 탐색이 뜬다.
   */
  it('가입 자리는 표 밖에 있고 세션 전 자리 집합이 로그인과 가입 둘이다', () => {
    expect(signUpRoute.path).toBe(SIGN_UP_PATH);
    expect(screenSlots.map((slot) => slot.path)).not.toContain(SIGN_UP_PATH);
    expect([...PRE_SESSION_PATHS].sort()).toEqual([loginRoute.path, SIGN_UP_PATH].sort());
  });

  /**
   * 같은 값이 **두 자리**에 있다(라우트 표의 리터럴 · 화면 모듈의 상수). 리터럴인 이유는 여정
   * 화면 표 게이트가 이 파일을 AST 로 읽기 때문이고(식이면 던진다), 그래서 그 둘이 갈리지
   * 않는다는 것을 여기서 잰다.
   */
  it('로그인 경로의 두 자리가 같다 — 리터럴과 상수', () => {
    expect(loginRoute.path).toBe(LOGIN_PATH);
  });
});

/** D-7E-5 — 범위 밖 화면은 「준비 중」 빈 자리뿐이다. 그 경계가 표의 `element` 유무다. */
describe('이 slice 가 채운 자리', () => {
  it('일곱 자리가 전부 실화면이다 — 빈 자리 0', () => {
    const implemented = screenSlots.filter((slot) => slot.element !== undefined).map((slot) => slot.id);

    expect(implemented).toEqual([
      'onboarding',
      'home',
      'worksheet',
      'bid-box',
      'results',
      'inbox',
      'settings',
    ]);
    expect(screenSlots.filter((slot) => slot.element === undefined)).toStrictEqual([]);
  });
});
