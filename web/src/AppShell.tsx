import { QueryClientProvider } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router';

import { queryClient } from './queryClient';
import { screenSlots } from './routes';

// 경로 인자가 있는 자리(C 워크시트)는 공고를 고른 뒤에 닿는 곳이라 전역 탐색에 두지 않는다.
const navigableSlots = screenSlots.filter((slot) => !slot.path.includes(':'));

/**
 * 공통 셸 — **모바일 우선 반응형 하나**다(§6.5). 탐색은 좁은 화면에서 가로로 스크롤되는 줄이고
 * 넓은 화면에서 그대로 펼쳐진다. 색은 전부 토큰(`index.css`)이라 시스템 테마가 바뀌면 함께
 * 바뀐다 — 컴포넌트에 색 리터럴이 없다.
 *
 * 서버 상태 provider 가 **여기** 있다(M7/7E-1) — 라우트 트리가 그것을 품으므로 「화면이 질의를
 * 걸 수 있는 자리」와 「셸이 뜨는 자리」가 같아지고, 라우트를 렌더하는 test 가 provider 를 따로
 * 세우지 않는다.
 */
export function AppShell() {
  return (
    <QueryClientProvider client={queryClient}>
      <div data-testid="app-shell" className="min-h-screen bg-page text-ink">
        {/* 키보드 접근(§6.5) — 첫 탭이 본문으로 건너뛸 수 있게 한다. */}
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:rounded-md focus:bg-card focus:px-3 focus:py-2 focus:text-sm"
        >
          본문으로 건너뛰기
        </a>
        <header className="border-b border-line px-4 py-3 sm:px-6">
          <h1 className="text-lg font-bold">bid-vector 운영자</h1>
          <nav aria-label="화면" className="mt-2 flex gap-1 overflow-x-auto text-sm">
            {navigableSlots.map((slot) => (
              <NavLink
                key={slot.id}
                to={slot.path}
                end={slot.path === '/'}
                className={({ isActive }) =>
                  `shrink-0 rounded-md px-2 py-1 ${
                    isActive ? 'bg-neutral-skin font-medium text-neutral-ink' : 'text-ink-soft hover:bg-neutral-skin'
                  }`
                }
              >
                {slot.label}
              </NavLink>
            ))}
          </nav>
        </header>
        <main id="main">
          <Outlet />
        </main>
      </div>
    </QueryClientProvider>
  );
}
