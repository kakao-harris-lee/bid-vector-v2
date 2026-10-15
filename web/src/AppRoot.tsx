import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router';

import { AppShell } from './AppShell';
import { RouteError } from './RouteError';
import { fallbackRoute, screenRoutes } from './routes';

// 경로 없는 레이아웃 라우트 하나가 셸을 들고, 화면 자리들이 그 자식이다. 알 수 없는 URL 은
// 자리 표 밖의 `fallbackRoute` 가 받는다(code-review r1 F-10).
//
// **오류 경계는 셸 라우트 자체가 아니라 그 안의 경로 없는 자식에 둔다**(PR #75 리뷰 A). 셸
// 라우트에 `errorElement` 를 달면 자식이 throw 할 때 react-router 가 **그 라우트의 element 를
// errorElement 로 갈아치우므로 셸이 사라진다**. 자식 layout 에 두면 오류가 셸의 `Outlet` 안에
// 뜬다 — `AppRoot.test.tsx` 가 「자식 throw → 셸과 오류 자리가 둘 다 보인다」로 그 자리를 잠근다
// (구판 구조에서 그 test 는 RED 다, 실측).
export const appRoutes: RouteObject[] = [
  {
    id: 'shell',
    element: <AppShell />,
    children: [
      {
        id: 'shell-boundary',
        errorElement: <RouteError />,
        children: [...screenRoutes, fallbackRoute],
      },
    ],
  },
];

const router = createBrowserRouter(appRoutes);

// 서버 상태는 TanStack Query 가 든다(ADR 0011 D-1). 7A 는 질의를 하나도 걸지 않는다 —
// 어떤 질의를 거는지는 데이터 요구표의 ⓐ 행이 열릴 때(7B·7C 뒤) 7E 가 정한다.
const queryClient = new QueryClient();

export function AppRoot() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
