// @vitest-environment jsdom
import { defaultScheduler, notifyManager } from '@tanstack/react-query';
import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { afterAll, afterEach, describe, expect, it } from 'vitest';

import { appRoutes } from './AppRoot';
import { queryClient } from './queryClient';

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

/**
 * **이 파일은 출하 라우트 트리를 통째로 그린다** — 셸(`AppShell`)이 모듈 전역 `queryClient` 를
 * 제공하므로 그려진 화면의 `useQuery` 들이 실제로 뜨고, 그 구독은 **이 test 가 거두지 않으면
 * 파일이 끝난 뒤에도 산다**. jsdom teardown 뒤 tanstack 의 알림 배치가 깨어나면 react-dom 이
 * `window` 를 찾다 죽고, vitest 는 **전부 통과인데 exit 1** 로 끝난다(M11/11E 서명 B — 베이스라인
 * 52회 중 5회).
 *
 * 그래서 만든 root 와 담은 요소를 들고 있다가 [afterEach] 에서 거둔다. 「test 를 지나면 공유
 * 상태가 원래대로」가 이 파일의 불변식이고, 아래 마지막 describe 가 그것을 **단언으로** 잰다.
 */
const mounted: { readonly root: Root; readonly container: HTMLElement }[] = [];

/**
 * 알림 배치를 타이머에 싣지 않는다 — 기본 스케줄러는 `setTimeout(callback, 0)` 이라 마지막 칸
 * 뒤에 남은 예약이 **환경이 걷힌 다음** 깨어날 수 있다.
 *
 * **이 줄이 서명 B 를 고친 것은 아니다**(r1 cr F-8 실측). 둘로 갈라 각각 26회를 쟀다 — 이 줄만
 * 두고 아래 `afterEach` 정리를 되돌리면 **5/26** 로 서명이 그대로 나고(base 는 4/26), 반대로 이
 * 줄만 되돌리고 정리를 두면 **0/26** 이다. 즉 **하중은 정리 쪽이 진다**. 기전도 그 결과와 맞는다:
 * 터지는 경로가 react-dom 의 setState 이므로 root 가 이미 걷혀 있으면 깨어난 배치가 할 일이
 * 없다.
 *
 * 그래도 남겨 둔다 — 정리를 지나지 않는 칸이 생기는 날을 받는 **값싼 구조 보강**이고, 그 사실을
 * 아래 칸이 단언한다. 전역을 바꾸므로 `afterAll` 이 되돌린다.
 */
notifyManager.setScheduler((callback) => {
  callback();
});

/**
 * **전역을 되돌린다**(r1 cr F-10). 위 한 줄은 `notifyManager` 의 **모듈 전역**을 바꾼다. 오늘은
 * vitest 가 파일마다 모듈 그래프를 따로 세우므로 이 파일 밖으로 새지 않지만, 그 전제는
 * `vite.config.ts` 의 `test:` 블록이 쥐고 있고 그 파일은 다른 레인과 공유하는 자리다 —
 * `isolate: false` 가 켜지는 날 이 파일이 남의 tanstack 거동을 바꾼다. 전제에 기대지 않는다.
 */
afterAll(() => {
  notifyManager.setScheduler(defaultScheduler);
});

function renderAt(routes: RouteObject[], path: string): HTMLElement {
  const container = document.createElement('div');
  document.body.append(container);
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const root = createRoot(container);
  mounted.push({ root, container });
  act(() => {
    root.render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    );
  });
  return container;
}

afterEach(async () => {
  // 순서가 중요하다 — 먼저 구독을 끊고(unmount), 그다음 **떠 있는 요청을 중단**한 뒤 캐시를
  // 비운다. 중단·비우기가 내는 알림은 위 스케줄러 덕에 그 자리에서 돈다.
  act(() => {
    for (const { root } of mounted) {
      root.unmount();
    }
  });
  for (const { container } of mounted.splice(0)) {
    container.remove();
  }
  await queryClient.cancelQueries();
  queryClient.clear();
});

describe('오류 경계의 자리', () => {
  it('자식이 throw 하면 셸과 오류 자리가 **둘 다** 보인다', () => {
    const container = renderAt(withThrowingLeaf(appRoutes), '/start');

    expect(container.querySelector('[data-testid="app-shell"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="route-error"]')).not.toBeNull();
  });

  /**
   * M7/7E-1 — `/`(화면 B)는 이제 질의를 거는 실화면이라 이 test 의 자리로 쓰지 않는다. 재는
   * 것은 **셸 ↔ 화면 자리의 구조**이고 그것은 아직 빈 자리인 `/start` 에서 그대로 보인다.
   */
  it('평소에는 셸 안에 화면 자리가 뜬다', () => {
    const container = renderAt([...appRoutes], '/start');

    expect(container.querySelector('[data-testid="app-shell"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="screen-onboarding"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="route-error"]')).toBeNull();
  });
});

/**
 * **공유 상태를 거두는 것이 이 파일의 불변식**(M11/11E 서명 B). 정리 코드만 두면 그것을 지우는
 * 변이가 조용하다 — 그 결함은 **다음 실행 52번 가운데 다섯 번**만 보이기 때문이다. 그래서 결과를
 * 단언으로 고정한다.
 *
 * 두 칸이 쌍이다: 앞 칸이 **그리면 실제로 공유 캐시가 찬다**는 것을(안 차면 뒤 칸이 공허하다),
 * 뒤 칸이 **그 사이의 `afterEach` 가 그것을 0 으로 되돌렸다**는 것을 잰다.
 */
let rendered = false;

describe('공유 상태는 칸을 넘어 살지 않는다 (서명 B)', () => {
  /**
   * **스케줄러 절반을 잠근다**(r1 F5). 아래 두 칸은 `afterEach` 를 비우거나 중단·비우기를 빼면
   * 붉지만, `setScheduler` 만 지워도 초록이었다 — 「예약될 창 자체를 없앤다」는 주장이 단언
   * 밖에 있었다. 그 주장을 결과로 잰다: 예약이 **돌아오기 전에** 이미 돌았는가.
   */
  it('알림 배치가 타이머가 아니라 그 자리에서 돈다', () => {
    let ran = false;

    notifyManager.schedule(() => {
      ran = true;
    });

    expect(ran, '알림이 타이머에 실렸다 — 환경이 걷힌 뒤에 깨어날 수 있다').toBe(true);
  });

  it('출하 라우트를 그리면 공유 클라이언트에 질의가 선다', () => {
    renderAt([...appRoutes], '/start');

    expect(queryClient.getQueryCache().getAll().length).toBeGreaterThan(0);
    expect(document.body.childElementCount).toBeGreaterThan(0);
    rendered = true;
  });

  it('앞 칸이 세운 질의와 DOM 이 남아 있지 않다', () => {
    // **뒤 칸만 남으면 공허하게 초록이다**(r1 cr F-9). 앞 칸이 실제로 돌았음을 여기서 단언한다 —
    // 오늘 수집 순서가 선언 순서인 것은 `vite.config.ts` 가 `sequence.shuffle` 을 안 켜기
    // 때문이고, 그 설정은 이 파일 밖에 있다.
    expect(rendered, '앞 칸이 돌지 않았다 — 이 칸은 아무것도 재지 못한다').toBe(true);
    expect(
      queryClient.getQueryCache().getAll().map((one) => one.queryHash),
      '공유 클라이언트에 앞 칸의 질의가 남았다 — jsdom teardown 뒤 알림 배치가 깨어난다',
    ).toStrictEqual([]);
    expect(document.body.childElementCount, '앞 칸의 DOM 이 남았다').toBe(0);
  });
});
