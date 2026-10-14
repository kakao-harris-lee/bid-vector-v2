import { NavLink, Outlet } from 'react-router';

import { screenSlots } from './routes';

// 경로 인자가 있는 자리(C 워크시트)는 공고를 고른 뒤에 닿는 곳이라 전역 탐색에 두지 않는다.
const navigableSlots = screenSlots.filter((slot) => !slot.path.includes(':'));

export function AppShell() {
  return (
    <div data-testid="app-shell" className="min-h-screen bg-white text-gray-900">
      <header className="border-b border-gray-200 px-6 py-4">
        <h1 className="text-xl font-bold">bid-vector 운영자</h1>
        <nav className="mt-3 flex gap-4 text-sm">
          {navigableSlots.map((slot) => (
            <NavLink key={slot.id} to={slot.path} className="underline-offset-4 hover:underline">
              {slot.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
