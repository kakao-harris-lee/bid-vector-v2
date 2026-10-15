// @vitest-environment jsdom
import { StrictMode, act } from 'react';
import { createRoot } from 'react-dom/client';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';

import { appRoutes } from './AppRoot';

/**
 * 오류 경계가 **셸 안**에 있는지 잰다(PR #75 리뷰 A).
 *
 * 경계가 셸 라우트 **자체**에 있으면 자식이 throw 할 때 react-router 가 그 라우트의 element 를
 * errorElement 로 **갈아치우므로 셸이 사라진다**. 경로 없는 자식 layout 에 두면 오류가 셸의
 * `Outlet` 안에 뜬다. 이 test 는 shipped 트리의 그 구조를 그대로 쓰고 **잎 하나만** 던지는
 * 컴포넌트로 바꿔치운다 — 바꾸는 것이 잎이므로 측정 대상(셸 ↔ 경계의 자리)은 shipped 그대로다.
 */
function Thrower(): never {
  throw new Error('자식이 렌더 중에 던진다');
}

function withThrowingLeaf(routes: readonly RouteObject[]): RouteObject[] {
  return routes.map((route, index) => {
    if (route.children !== undefined) {
      return { ...route, children: withThrowingLeaf(route.children) };
    }
    return index === 0 ? { ...route, element: <Thrower /> } : route;
  });
}

function renderAt(routes: RouteObject[], path: string): HTMLElement {
  const container = document.createElement('div');
  document.body.append(container);
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  act(() => {
    createRoot(container).render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    );
  });
  return container;
}

describe('오류 경계의 자리', () => {
  it('자식이 throw 하면 셸과 오류 자리가 **둘 다** 보인다', () => {
    const container = renderAt(withThrowingLeaf(appRoutes), '/start');

    expect(container.querySelector('[data-testid="app-shell"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="route-error"]')).not.toBeNull();
  });

  it('평소에는 셸 안에 화면 자리가 뜬다', () => {
    const container = renderAt([...appRoutes], '/');

    expect(container.querySelector('[data-testid="app-shell"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="screen-home"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="route-error"]')).toBeNull();
  });
});
