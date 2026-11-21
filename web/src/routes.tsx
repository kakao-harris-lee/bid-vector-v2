import type { RouteObject } from 'react-router';

import { LoginScreen } from './LoginScreen';
import { ScreenPlaceholder } from './ScreenPlaceholder';
import { LOGIN_PATH, SIGN_UP_PATH } from './paths';
import { SignUpScreen } from './SignUpScreen';
import { BidBoxScreen } from './screens/BidBoxScreen';
import { InboxScreen } from './screens/InboxScreen';
import { OnboardingScreen } from './screens/OnboardingScreen';
import { ResultsScreen } from './screens/ResultsScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { TodayNoticesScreen } from './screens/TodayNoticesScreen';
import { WorksheetScreen } from './screens/WorksheetScreen';

/**
 * 화면 자리의 정본 표. 라우터는 이 표에서 파생한다 — 경로를 두 곳에 적지 않는다.
 *
 * 화면 다섯(A~E)과 알림함은 `docs/discovery/m7-screen-data-requirements.md` 의 화면
 * 구분을 따른다. 7A 는 **자리만** 만든다(설계 검토 (3) 과잉 금지) — 화면 동작·상태
 * 관리·디자인 시스템은 7E 다.
 */
/**
 * 전역 탐색에서 이 자리가 서는 **묶음**(M13/13B).
 *
 *  · `primary` — 좁은 화면의 **하단 탭**이 칸을 준다(§6.5 모바일 관행).
 *  · `more` — 하단 탭의 「더 보기」 안에 든다. 넓은 화면에서는 `primary` 와 **같은 줄**이다.
 *  · `none` — 전역 탐색에 두지 않는다. 경로 인자가 있는 자리(워크시트)가 여기다 — 공고를 고른
 *    뒤에 닿는 곳이라 「아무 공고 없이」 열 주소가 없다.
 *
 * **옵셔널이 아니다.** 자리 표에 줄을 더하면 묶음을 적어야 컴파일된다 — 새 화면이 분류 없이
 * 들어와 탐색에서 조용히 빠지는 자리를 타입이 막는다.
 */
export type NavGroup = 'primary' | 'more' | 'none';

export interface ScreenSlot {
  readonly id: string;
  readonly path: string;
  readonly label: string;
  readonly nav: NavGroup;
  /**
   * 이 slice 가 구현한 화면의 element. **없으면 「준비 중」 빈 자리**다(D-7E-5 — 범위 밖
   * 기능은 빈 자리뿐이고 탭·버튼을 만들지 않는다). 7E-2·3 이 자기 자리를 채운다.
   */
  readonly element?: React.ReactNode;
}

export const screenSlots: readonly ScreenSlot[] = [
  { id: 'onboarding', path: '/start', label: '시작하기', nav: 'primary', element: <OnboardingScreen /> },
  { id: 'home', path: '/', label: '오늘의 공고', nav: 'primary', element: <TodayNoticesScreen /> },
  // 공고 식별자는 **번호와 차수 두 칸**이다 — 한 문자열로 접으면 화면이 구분자를 지어내야 한다.
  { id: 'worksheet', path: '/notices/:noticeNumber/:noticeRound', label: '공고 워크시트', nav: 'none', element: <WorksheetScreen /> },
  { id: 'bid-box', path: '/bids', label: '내 입찰함', nav: 'primary', element: <BidBoxScreen /> },
  { id: 'results', path: '/results', label: '결과 확인', nav: 'primary', element: <ResultsScreen /> },
  { id: 'inbox', path: '/inbox', label: '알림함', nav: 'more', element: <InboxScreen /> },
  // 「설정」은 요구표의 화면 다섯(A~E)·알림함과 **다른 축**이지만 운영자가 늘 닿는 자리이고
  // 전역 탐색에 있어야 한다(M7/7E-2, D-7E2-9 — 자리 하나 추가는 in_scope).
  { id: 'settings', path: '/settings', label: '설정', nav: 'more', element: <SettingsScreen /> },
];

export const screenRoutes: RouteObject[] = screenSlots.map((slot) => ({
  id: slot.id,
  path: slot.path,
  element: slot.element ?? <ScreenPlaceholder slot={slot} />,
}));

/**
 * 로그인 자리(M7/7D). **화면 자리 표에는 넣지 않는다** — 화면 다섯(A~E)과 알림함은 요구표의
 * 화면 구분이고, 이것은 그 구분 밖의 **인증 자리**다(`fallbackRoute` 와 같은 이유로 표 밖이다).
 * 셸의 전역 탐색에도 뜨지 않는다(`AppShell` 은 자리 표에서만 링크를 만든다).
 */
export const loginRoute: RouteObject = {
  id: 'login',
  // **문자열 리터럴이다**(상수를 쓰지 않는다) — 여정 화면 표 게이트가 이 파일을 AST 로 읽어
  // `id`·`path` 를 리터럴로 요구한다(식이면 「읽지 못한다」로 던진다). 같은 값이 두 자리에
  // 있는 것은 `routes.test.ts` 의 등식이 든다.
  path: '/login',
  element: <LoginScreen />,
};

/**
 * 가입 자리(M15/15B) — 로그인과 **같은 축**이다(요구표의 화면 구분 밖, 인증 자리). 그래서 화면
 * 자리 표에 넣지 않고 셸의 전역 탐색에도 뜨지 않는다.
 */
export const signUpRoute: RouteObject = {
  id: 'signup',
  path: SIGN_UP_PATH,
  element: <SignUpScreen />,
};

/**
 * **세션 전 자리 전수** — 셸이 탐색을 그리지 않는 경로다. 집합으로 두는 이유는 자리가 둘이 된
 * 순간 「로그인이면」이라는 술어가 가입 화면에서 조용히 틀리기 때문이다.
 */
export const PRE_SESSION_PATHS: ReadonlySet<string> = new Set([LOGIN_PATH, SIGN_UP_PATH]);

/**
 * 알 수 없는 URL 자리(code-review r1 F-10). **화면 자리 표에는 넣지 않는다** — 이것은 화면이
 * 아니라 「셸이 모든 URL 에서 뜬다」를 참으로 만드는 자리다.
 */
/**
 * 그 자리의 라벨은 **셸의 문서 제목**도 읽는다(M13/13B) — 그래서 리터럴을 아래 라우트 안에
 * 묻어 두지 않고 이름을 준다. 글자는 그대로다.
 */
export const notFoundSlot: ScreenSlot = { id: 'not-found', path: '*', label: '없는 주소', nav: 'none' };

export const fallbackRoute: RouteObject = {
  id: notFoundSlot.id,
  path: notFoundSlot.path,
  element: <ScreenPlaceholder slot={notFoundSlot} />,
};
