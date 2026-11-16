// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { act } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { apiClient, hasCsrf, rememberCsrf } from './api/client';
import { NOTIFICATIONS_QUERY_KEY } from './api/queryKeys';
import { appRoutes } from './AppRoot';
import { queryClient } from './queryClient';
import { screenSlots } from './routes';

/**
 * 셸의 짝 test(M13/13B). 폭에 따라 **어느 탐색이 보이는가**는 jsdom 이 미디어 질의를 풀지
 * 않으므로 여기서 설 수 없다 — 그 축은 터치 타깃 게이트(390px)와 여정이 잰다. 이 파일이
 * 잠그는 것은 **구조**다: 두 탐색이 같은 자리 집합을 그리는가 · 배지가 헤더 값에서 오는가 ·
 * 로그아웃이 성공할 때만 떠나는가 · 포커스와 문서 제목이 무엇을 보고 움직이는가.
 */
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

interface Sent {
  readonly method: string;
  readonly url: string;
}

const NO_CONTENT = 204;

let sent: Sent[] = [];
let eject: (() => void) | undefined;

/** 안읽음 전수를 **헤더로** 낸다 — 배지가 읽는 값이 거기 있다(본문 행이 아니다). */
function stub({
  unread = 0,
  signOutStatus = 204,
  unreadAfterRefetch,
}: {
  unread?: number;
  signOutStatus?: number;
  /** 두 번째 읽기부터 돌려줄 값 — 「다시 돌았다」를 **보이는 값**으로 재려는 자리다. */
  unreadAfterRefetch?: number;
} = {}): void {
  let notificationReads = 0;
  // 서버를 **상태 있게** 흉내 낸다. 로그아웃이 성공하면 그 뒤의 세션 조회는 404 다(계약) —
  // 늘 200 으로 답하면 화면의 CSRF 재획득이 되살아나 「로그아웃 뒤 값이 비었다」가 거짓이 된다
  // (실측: 그 stub 에서 이 단언이 뒤집혔다).
  let signedOut = false;
  const middleware = {
    onRequest({ request }: { request: Request }): Response {
      sent.push({ method: request.method, url: request.url });
      const path = new URL(request.url).pathname;
      if (path === '/api/session' && request.method === 'DELETE') {
        signedOut = signOutStatus === NO_CONTENT;
        return new Response(null, { status: signOutStatus });
      }
      if (path === '/api/session') {
        return signedOut
          ? new Response(JSON.stringify({ code: 'NOT_FOUND' }), {
              status: 404,
              headers: { 'content-type': 'application/json' },
            })
          : new Response(JSON.stringify({ csrf: 'csrf-for-test' }), {
              status: 200,
              headers: { 'content-type': 'application/json' },
            });
      }
      if (path === '/api/notifications') {
        notificationReads += 1;
        const count = notificationReads > 1 && unreadAfterRefetch !== undefined ? unreadAfterRefetch : unread;
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: {
            'content-type': 'application/json',
            'X-Total-Count': '0',
            'X-Unread-Count': String(count),
          },
        });
      }
      // 체크리스트는 **목록 셋을 든 객체**다 — 빈 배열로 답하면 그것을 읽는 배너가 터진다
      // (실측: `pending.length` 가 `undefined`). 모양이 다른 응답을 같은 꼴로 답하지 않는다.
      if (path === '/api/onboarding-checklist') {
        return new Response(JSON.stringify({ completed: [], pending: [], awaiting: [] }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }
      // 그 밖의 화면 질의는 **빈 목록**으로 답한다 — 이 파일이 재는 것은 셸이고, 화면이 빈
      // 얼굴을 그리든 오류를 그리든 셸의 구조는 같아야 한다.
      return new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'content-type': 'application/json', 'X-Total-Count': '0', 'X-Result-State': 'MATCHED' },
      });
    },
  };
  apiClient.use(middleware);
  eject = () => apiClient.eject(middleware);
}

async function renderAt(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  await act(async () => {
    render(<RouterProvider router={router} />);
  });
  return router;
}

beforeEach(() => {
  sent = [];
  queryClient.clear();
  rememberCsrf('csrf-for-test');
});

afterEach(() => {
  eject?.();
  eject = undefined;
  cleanup();
});

const navPaths = (): string[] =>
  screen
    .getAllByRole('link')
    .map((one) => one.getAttribute('href') ?? '')
    .filter((one) => one !== '#main');

describe('탐색의 두 모양 (M13/13B)', () => {
  it('상단 줄과 하단 탭을 함께 그린다 — 가리는 것은 CSS 다', async () => {
    stub();
    await renderAt('/');

    // 자리 표의 `primary` 는 **두 곳 모두**에 선다(상단 줄 · 하단 탭).
    const primaries = screenSlots.filter((one) => one.nav === 'primary');
    for (const slot of primaries) {
      expect(navPaths().filter((one) => one === slot.path).length, `${slot.id} 가 두 탐색에 없다`).toBe(2);
    }
  });

  it('하단 탭은 주 화면 넷과 「더 보기」 다섯 칸이다', async () => {
    stub();
    await renderAt('/');

    const tabs = screen.getByTestId('nav-more-toggle').closest('ul');

    expect(tabs?.children.length).toBe(screenSlots.filter((one) => one.nav === 'primary').length + 1);
  });

  it('「더 보기」 안의 자리는 접혀 있을 때 DOM 에 없다', async () => {
    stub();
    await renderAt('/');

    expect(screen.queryByTestId('nav-more')).toBeNull();
    // 「더 보기」가 품는 자리는 상단 줄에만 있다(접힌 하단 쪽은 그리지 않는다).
    for (const slot of screenSlots.filter((one) => one.nav === 'more')) {
      expect(navPaths().filter((one) => one === slot.path).length).toBe(1);
    }
  });

  it('경로 인자가 있는 자리는 탐색에 두지 않는다', async () => {
    stub();
    await renderAt('/');

    expect(navPaths().some((one) => one.includes(':'))).toBe(false);
  });
});

describe('「더 보기」 disclosure', () => {
  it('펼치면 그 안의 자리가 서고 aria-expanded 가 따라간다', async () => {
    stub();
    await renderAt('/');
    const toggle = screen.getByTestId('nav-more-toggle');

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    await act(async () => {
      toggle.click();
    });

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByTestId('nav-more')).not.toBeNull();
  });

  it('Escape 로 닫고 포커스가 연 버튼으로 돌아온다', async () => {
    stub();
    await renderAt('/');
    const toggle = screen.getByTestId('nav-more-toggle');
    await act(async () => {
      toggle.click();
    });

    await act(async () => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    });

    expect(screen.queryByTestId('nav-more')).toBeNull();
    expect(document.activeElement).toBe(toggle);
  });

  /**
   * **떠났다가 돌아와도 닫힌 채다**(cr M-1). 앞 판은 펼침을 「열린 자리와의 동일성」으로 접어
   * 읽어서, 「닫혔다」가 상태에 남지 않았다 — 그 자리로 돌아오면 저절로 다시 펼쳐졌다.
   */
  it('펼친 자리를 떠났다가 돌아와도 접혀 있다', async () => {
    stub();
    const router = await renderAt('/');
    const toggle = screen.getByTestId('nav-more-toggle');
    await act(async () => {
      toggle.click();
    });
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    await act(async () => {
      await router.navigate('/inbox');
    });
    await act(async () => {
      await router.navigate('/');
    });

    expect(screen.getByTestId('nav-more-toggle').getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByTestId('nav-more')).toBeNull();
  });

  /** 그 안의 자리가 현재일 때 **버튼도 현재**다 — 그러지 않으면 어느 탭도 켜지지 않는다. */
  it('품은 자리에 서 있으면 버튼이 현재로 표시된다', async () => {
    stub();
    await renderAt('/inbox');

    expect(screen.getByTestId('nav-more-toggle').getAttribute('aria-current')).toBe('true');
  });

  it('품지 않은 자리에서는 현재 표시가 없다', async () => {
    stub();
    await renderAt('/');

    expect(screen.getByTestId('nav-more-toggle').hasAttribute('aria-current')).toBe(false);
  });
});

describe('탐색의 안 읽음 배지', () => {
  it('배지 수가 헤더의 안읽음 전수다', async () => {
    stub({ unread: 7 });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-unread').textContent).toBe('7'));
    expect(screen.getByTestId('nav-tab-unread').textContent).toBe('7');
  });

  it('0 이면 배지를 그리지 않는다 — 0 은 「다 읽었다」이지 표시할 값이 아니다', async () => {
    stub({ unread: 0 });
    await renderAt('/');

    await waitFor(() => expect(sent.some((one) => one.url.includes('/api/notifications'))).toBe(true));
    expect(screen.queryByTestId('nav-unread')).toBeNull();
    expect(screen.queryByTestId('nav-tab-unread')).toBeNull();
  });

  /** 배지는 **한 행만** 받아 온다 — 화면이 그 본문을 쓰지 않으므로 목록을 통째로 끌 이유가 없다. */
  it('배지 질의는 한 행만 요청한다', async () => {
    stub({ unread: 1 });
    await renderAt('/');

    await waitFor(() => {
      const call = sent.find((one) => one.url.includes('/api/notifications'));
      expect(call).toBeDefined();
      expect(new URL(call!.url).searchParams.get('size')).toBe('1');
    });
  });

  /**
   * **접두의 뜻을 동작으로 잰다**(cr H-2 ③). 키의 모양은 `api/queryKeys.test.ts` 가 보고, 여기서는
   * 알림함 키를 무효화했을 때 배지가 **실제로 다시 돌아 값이 바뀌는지**를 본다 — 13E 의 읽음
   * 쓰기가 거는 것이 바로 그 무효화다.
   */
  it('알림함 키를 무효화하면 배지가 다시 돌아 값이 바뀐다', async () => {
    stub({ unread: 7, unreadAfterRefetch: 2 });
    await renderAt('/');
    await waitFor(() => expect(screen.getByTestId('nav-unread').textContent).toBe('7'));

    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
    });

    await waitFor(() => expect(screen.getByTestId('nav-unread').textContent).toBe('2'));
  });

  it('로그인 자리에서는 배지 질의를 걸지 않는다', async () => {
    stub({ unread: 5 });
    await renderAt('/login');

    expect(sent.some((one) => one.url.includes('/api/notifications'))).toBe(false);
    expect(screen.queryByTestId('nav-more-toggle')).toBeNull();
  });
});

describe('로그아웃', () => {
  it('성공하면 DELETE 를 보내고 로그인 자리로 간다', async () => {
    stub({ signOutStatus: 204 });
    const router = await renderAt('/');

    await act(async () => {
      screen.getAllByTestId('sign-out')[0]!.click();
    });

    await waitFor(() =>
      expect(sent.some((one) => one.method === 'DELETE' && one.url.endsWith('/api/session'))).toBe(true),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    // 기억한 CSRF 값을 버린다 — 다음 사람이 앞 세션의 값을 들고 쓰지 않는다.
    expect(hasCsrf()).toBe(false);
  });

  /**
   * **실패하면 떠나지 않는다.** 폐기되지 않았는데 로그아웃된 것처럼 보이면 운영자가 세션을
   * 남긴 채 떠난다 — 이 제품에서 가장 비싼 침묵의 한 꼴이다.
   */
  it('실패하면 사유를 보이고 자리도 CSRF 도 그대로다', async () => {
    stub({ signOutStatus: 500 });
    const router = await renderAt('/');

    await act(async () => {
      screen.getAllByTestId('sign-out')[0]!.click();
    });

    await waitFor(() => expect(screen.getByTestId('sign-out-failure')).not.toBeNull());
    expect(screen.getByTestId('sign-out-failure').getAttribute('role')).toBe('alert');
    expect(router.state.location.pathname).toBe('/');
    expect(hasCsrf()).toBe(true);
  });
});

describe('문서 제목과 포커스', () => {
  it('제목이 자리 표의 라벨에서 온다 — 사본을 만들지 않는다', async () => {
    stub();
    const slot = screenSlots.find((one) => one.id === 'results')!;
    await renderAt(slot.path);

    expect(document.title.startsWith(slot.label)).toBe(true);
  });

  it('라벨이 없는 자리는 앱 이름만 쓴다', async () => {
    stub();
    await renderAt('/login');

    expect(document.title).toBe('bid-vector 운영자');
  });

  it('자리를 옮기면 본문 머리로 포커스가 간다', async () => {
    stub();
    const router = await renderAt('/');

    await act(async () => {
      await router.navigate('/results');
    });

    expect(document.activeElement).toBe(document.getElementById('main'));
  });

  /**
   * **질의 문자열만 바뀌면 포커스를 옮기지 않는다.** 입찰함의 `?tab=` 이동과 홈의 검색이 같은
   * 화면 안의 상태 변화이고, 거기서 포커스를 빼앗으면 탭을 누른 손이 본문으로 끌려간다.
   */
  it('같은 자리 안에서 질의만 바뀌면 포커스를 옮기지 않는다', async () => {
    stub();
    const router = await renderAt('/bids');
    const toggle = screen.getByTestId('nav-more-toggle');
    await act(async () => {
      toggle.focus();
    });

    await act(async () => {
      await router.navigate('/bids?tab=closed');
    });

    expect(document.activeElement).toBe(toggle);
  });
});
