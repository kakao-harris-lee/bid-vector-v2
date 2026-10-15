import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router';

import { AppShell } from './AppShell';
import { RouteError } from './RouteError';
import { fallbackRoute, screenRoutes } from './routes';

// 경로 없는 레이아웃 라우트 하나가 셸을 들고, 화면 자리들이 그 자식이다. 알 수 없는 URL 은
// 자리 표 밖의 `fallbackRoute` 가 받고 던져진 오류는 `errorElement` 가 받는다 — 둘 다 셸
// **안**이라 「셸이 뜬다」가 모든 URL 에서 참이다(code-review r1 F-10).
const router = createBrowserRouter([
  {
    id: 'shell',
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [...screenRoutes, fallbackRoute],
  },
]);

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
