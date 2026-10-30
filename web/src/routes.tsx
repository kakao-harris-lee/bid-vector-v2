import type { RouteObject } from 'react-router';

import { LoginScreen } from './LoginScreen';
import { ScreenPlaceholder } from './ScreenPlaceholder';
import { BidBoxScreen } from './screens/BidBoxScreen';
import { OnboardingScreen } from './screens/OnboardingScreen';
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
export interface ScreenSlot {
  readonly id: string;
  readonly path: string;
  readonly label: string;
  /**
   * 이 slice 가 구현한 화면의 element. **없으면 「준비 중」 빈 자리**다(D-7E-5 — 범위 밖
   * 기능은 빈 자리뿐이고 탭·버튼을 만들지 않는다). 7E-2·3 이 자기 자리를 채운다.
   */
  readonly element?: React.ReactNode;
}

export const screenSlots: readonly ScreenSlot[] = [
  { id: 'onboarding', path: '/start', label: 'A 시작하기', element: <OnboardingScreen /> },
  { id: 'home', path: '/', label: 'B 오늘의 공고', element: <TodayNoticesScreen /> },
  // 공고 식별자는 **번호와 차수 두 칸**이다 — 한 문자열로 접으면 화면이 구분자를 지어내야 한다.
  { id: 'worksheet', path: '/notices/:noticeNumber/:noticeRound', label: 'C 공고 워크시트', element: <WorksheetScreen /> },
  { id: 'bid-box', path: '/bids', label: 'D 내 입찰함', element: <BidBoxScreen /> },
  { id: 'results', path: '/results', label: 'E 결과 확인' },
  { id: 'inbox', path: '/inbox', label: '알림함' },
  // 「설정」은 요구표의 화면 다섯(A~E)·알림함과 **다른 축**이지만 운영자가 늘 닿는 자리이고
  // 전역 탐색에 있어야 한다(M7/7E-2, D-7E2-9 — 자리 하나 추가는 in_scope).
  { id: 'settings', path: '/settings', label: '설정', element: <SettingsScreen /> },
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
  path: '/login',
  element: <LoginScreen />,
};

/**
 * 알 수 없는 URL 자리(code-review r1 F-10). **화면 자리 표에는 넣지 않는다** — 이것은 화면이
 * 아니라 「셸이 모든 URL 에서 뜬다」를 참으로 만드는 자리다.
 */
export const fallbackRoute: RouteObject = {
  id: 'not-found',
  path: '*',
  element: <ScreenPlaceholder slot={{ id: 'not-found', path: '*', label: '없는 주소' }} />,
};
