// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import { act } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { apiClient, hasCsrf, rememberCsrf } from './api/client';
import { NOTIFICATIONS_QUERY_KEY } from './api/queryKeys';
import { absenceTextOf } from './display/absence';
import { tabOf } from './display/bidBoxDisplay';
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
  realCases = 0,
  tradeName = null,
  bidCasesBody,
}: {
  unread?: number;
  signOutStatus?: number;
  /** 두 번째 읽기부터 돌려줄 값 — 「다시 돌았다」를 **보이는 값**으로 재려는 자리다. */
  unreadAfterRefetch?: number;
  /** 사이드바 입찰함 배지가 세는 것 — 응답 **배열의 길이**다(M14/14A). */
  realCases?: number;
  /** 사이드바 발치의 사업자명. `null` 은 계약의 「미설정」이다. */
  tradeName?: string | null;
  /** 목록 자리의 답을 통째로 갈아 끼운다 — 꼴이 계약과 다른 경우를 세우는 자리다. */
  bidCasesBody?: unknown;
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
      // 입찰함 배지(M14/14A) — 건의 **배열**이다. 화면 D 와 같은 질의라 꼴이 같아야 한다.
      if (path === '/api/bid-cases') {
        // **표본이 종단과 비종단을 함께 든다**(cr M-2). 한쪽만 담으면 「종단을 뺀다」가 공허하고,
        // 모두 같은 꼴이면 「거르지 않는다」가 공허하다 — 두 축이 같은 표본을 쓴다.
        return new Response(JSON.stringify(bidCasesBody ?? bidCaseSample(realCases)), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }
      // 사이드바 발치의 사업자명 — 프로필 한 벌이다(빈 배열이 아니다).
      if (path === '/api/profile') {
        return new Response(JSON.stringify({ tradeName }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
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

describe('탐색의 두 모양 (M13/13B · M14/14A 사이드바)', () => {
  it('사이드바와 하단 탭을 함께 그린다 — 가리는 것은 CSS 다', async () => {
    stub();
    await renderAt('/');

    // 자리 표의 `primary` 는 **두 곳 모두**에 선다(사이드바 · 하단 탭).
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

    // 패널은 **늘 그려지고** 접힘은 `hidden` 이다(cr L-2) — 그래야 `aria-controls` 의 IDREF 가
    // 접힌 동안에도 선다. 숨는 동안 접근성 트리·포커스 순서에서는 함께 빠진다.
    expect(screen.getByTestId('nav-more').hidden).toBe(true);
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
    expect(screen.getByTestId('nav-more').hidden).toBe(false);
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

    expect(screen.getByTestId('nav-more').hidden).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  /**
   * **탭 순서가 설계와 같다**(cr M-2). 토글 다음 포커스 가능 요소가 드러난 패널의 첫 링크여야
   * 한다 — 앞 판은 패널이 DOM 에서 토글보다 **앞**이라, 편 사람이 Tab 을 누르면 탐색 밖으로
   * 나갔다. `aria-expanded`·존재·Escape 복귀만 재던 세 test 는 이 축에 공허했다.
   */
  it('펼치면 토글 다음 포커스 자리가 패널의 첫 링크다', async () => {
    stub();
    await renderAt('/');
    const toggle = screen.getByTestId('nav-more-toggle');
    await act(async () => {
      toggle.click();
    });

    const focusable = [...document.querySelectorAll<HTMLElement>('a[href], button:not([disabled])')].filter(
      (one) => one.closest('[hidden]') === null,
    );
    const next = focusable[focusable.indexOf(toggle) + 1];

    expect(next, '토글 뒤에 포커스 받을 자리가 없다').toBeDefined();
    expect(screen.getByTestId('nav-more').contains(next ?? null)).toBe(true);
    expect(next?.tagName).toBe('A');
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
    expect(screen.getByTestId('nav-more').hidden).toBe(true);
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

/**
 * **머리글은 문서에 하나다**(v L-1). 오늘의 코드는 그렇지만 그 사실을 **아무도 잠그지
 * 않았다** — 폭마다 다른 `h1` 을 그리는 꼴(`md:hidden` 로 하나를 가리는)이 jsdom 과 스모크
 * 둘 다에서 초록이었다(스모크의 머리글 단언은 넓은 폭에서만 돈다).
 *
 * **숨은 것도 센다**: jsdom 은 CSS 를 풀지 않으므로 여기서 세는 수가 곧 **DOM 의 수**이고,
 * 그것이 이 축에서 재고 싶은 것이다(폭에 따라 다른 머리글을 그리지 않는다).
 */
describe('머리글의 단일성 (v L-1)', () => {
  it('어느 자리에서도 h1 이 하나다', async () => {
    for (const path of ['/', '/start', '/bids', '/results', '/inbox', '/settings', '/login', '/no-such-place']) {
      cleanup();
      queryClient.clear();
      stub();
      await renderAt(path);

      expect(document.querySelectorAll('h1'), path).toHaveLength(1);
      eject?.();
      eject = undefined;
    }
  });
});

describe('사이드바 (M14/14A)', () => {
  /**
   * 와이어프레임의 사이드바는 항목이 다섯이고 「시작하기」가 없다 — 그것이 **시작하기가 끝난
   * 뒤**의 모습이기 때문이다. 이 앱은 체크리스트가 남은 계정에게 그 자리가 첫 걸음이라 **여섯**을
   * 그린다(계약의 이탈 하나). 수를 박지 않고 **자리 표에서 유도한다**.
   */
  it('전역 탐색에 두는 자리 전수를 그린다 — 수를 박지 않는다', async () => {
    stub();
    await renderAt('/');

    const sidebar = screen.getAllByRole('navigation', { name: '화면' })[0];
    const expected = screenSlots.filter((one) => one.nav !== 'none');

    expect(expected.length).toBeGreaterThan(1);
    expect(within(sidebar!).getAllByRole('link').map((one) => one.getAttribute('href'))).toStrictEqual(
      expected.map((one) => one.path),
    );
  });

  it('차례가 자리 표의 배열 순서다 — 셸이 자기 차례를 따로 들지 않는다', async () => {
    stub();
    await renderAt('/');

    const sidebar = screen.getAllByRole('navigation', { name: '화면' })[0];
    const labels = within(sidebar!)
      .getAllByRole('link')
      .map((one) => one.textContent ?? '');

    expect(labels.map((one) => one.replace(/[0-9]+$/, ''))).toStrictEqual(
      screenSlots.filter((one) => one.nav !== 'none').map((one) => one.label),
    );
  });
});

/**
 * 배지 표본 — **종단과 비종단을 번갈아** 담는다. 기대값은 수를 박지 않고 **표시 계약의 표**에서
 * 유도한다(`tabOf`), 그래서 계약에 상태가 늘어도 단언이 따라간다.
 */
const SAMPLE_STATES = ['WATCHING', 'WON', 'BID_RECORDED', 'LOST', 'AWAITING_OPENING', 'SKIPPED'] as const;

/** 표본이 비면 모든 단언이 공허해진다 — 인덱스가 빗나가면 **던진다**. */
const stateAt = (index: number): (typeof SAMPLE_STATES)[number] => {
  const found = SAMPLE_STATES[index % SAMPLE_STATES.length];
  if (found === undefined) {
    throw new Error('배지 표본이 비었다');
  }
  return found;
};

const bidCaseSample = (count: number) =>
  Array.from({ length: count }, (_unused, index) => ({ caseId: `case-${index}`, state: stateAt(index) }));

const openCountOf = (count: number): number =>
  bidCaseSample(count).filter((one) => tabOf(one.state) !== 'closed').length;

describe('입찰함 배지 (M14/14A)', () => {
  /**
   * **종단 건을 뺀 수다**(cr M-2). 와이어프레임의 「내 입찰함 9」는 「전체 9」이고 「종료 12」는
   * 따로 선 탭이다 — 배지는 **지금 들여다볼 건**을 센다.
   *
   * 기대값을 박지 않고 **표시 계약의 표**에서 유도한다. 표본이 종단을 담으므로 거르지 않는
   * 변이는 수가 커져 붉고, 더 걸러도 작아져 붉는다.
   */
  it('종단 건을 뺀 수가 배지 수다', async () => {
    const realCases = 6;
    stub({ realCases });
    await renderAt('/');

    const open = openCountOf(realCases);
    expect(open, '표본에 비종단이 없다').toBeGreaterThan(0);
    expect(open, '표본에 종단이 없다 — 「뺀다」가 공허하다').toBeLessThan(realCases);
    await waitFor(() => expect(screen.getByTestId('nav-bid-box').textContent).toBe(String(open)));
  });

  /** 어조가 알림함과 **다르다**(cr M-2) — 요구가 아닌 수를 경보로 그리지 않는다. */
  it('알림함 배지와 다른 어조다', async () => {
    stub({ realCases: 6, unread: 3 });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-bid-box')).not.toBeNull());
    const bidBox = screen.getByTestId('nav-bid-box').className;
    const inbox = screen.getByTestId('nav-unread').className;

    expect(bidBox).not.toBe(inbox);
    expect(bidBox, '입찰함 배지가 경보 어조다').not.toContain('bad');
    expect(inbox, '알림함 배지가 경보 어조를 잃었다').toContain('bad');
  });

  /** 페이퍼를 섞지 않는다 — 그 operation 은 `kind` 를 필수로 받고 계약이 둘을 섞지 않는다. */
  it('실투찰만 묻는다', async () => {
    stub({ realCases: 1 });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-bid-box')).not.toBeNull());
    const asked = sent.filter((one) => one.url.includes('/api/bid-cases'));
    expect(asked.length).toBeGreaterThan(0);
    for (const one of asked) {
      expect(new URL(one.url).searchParams.get('kind')).toBe('REAL');
    }
  });

  it('0 이면 그리지 않는다 — 0 은 「비었다」이지 표시할 값이 아니다', async () => {
    stub({ realCases: 0 });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-trade-name')).not.toBeNull());
    expect(screen.queryByTestId('nav-bid-box')).toBeNull();
  });

  it('좁은 화면의 탭에도 같은 수가 선다 — 폭에 따라 아는 것이 달라지지 않는다', async () => {
    const realCases = 6;
    stub({ realCases });
    await renderAt('/');

    await waitFor(() =>
      expect(screen.getByTestId('nav-tab-bid-box').textContent).toBe(String(openCountOf(realCases))),
    );
  });

  /**
   * **배지 하나가 셸을 무너뜨리지 않는다.** 목록 자리에 배열이 아닌 답이 오면(계약 위반이거나
   * 200 에 실린 오류 본문) 앞 판은 셸 전체가 오류 경계로 갔다 — 모든 화면이 함께 사라진다.
   * 「모르면 그리지 않는다」가 이 경우에도 선다.
   */
  it('배열이 아닌 답에서도 셸이 서고 배지만 빠진다', async () => {
    stub({ realCases: 0, bidCasesBody: { message: '배열이 아니다' } });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-trade-name')).not.toBeNull());
    expect(screen.getByTestId('app-shell')).not.toBeNull();
    expect(screen.queryByTestId('nav-bid-box')).toBeNull();
  });

  it('로그인 자리에서는 묻지 않는다', async () => {
    stub({ realCases: 2 });
    await renderAt('/login');

    expect(sent.some((one) => one.url.includes('/api/bid-cases'))).toBe(false);
    expect(sent.some((one) => one.url.includes('/api/profile'))).toBe(false);
  });
});

describe('사이드바의 사업자명 (M14/14A)', () => {
  it('서버가 준 상호를 그대로 그린다', async () => {
    stub({ tradeName: '제주바다건설(주)' });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-trade-name').textContent).toBe('제주바다건설(주)'));
  });

  /** 미설정은 **값**이다 — 빈 자리로 두면 운영자가 그 칸이 있는 줄도 모른다. */
  it('상호가 비었으면 사유를 그린다', async () => {
    stub({ tradeName: null });
    await renderAt('/');

    await waitFor(() => expect(screen.getByTestId('nav-trade-name').textContent).toBe(absenceTextOf('notSent')));
  });

  /**
   * **받기 전에는 아무 말도 하지 않는다.** 받지 못한 것을 「비었다」로 말하면 운영자가 상호를
   * 지운 줄 안다 — 배지가 0 을 그리지 않는 것과 같은 규율이다.
   */
  it('응답 전에는 자리를 그리지 않는다', async () => {
    stub({ tradeName: '제주바다건설(주)' });
    queryClient.clear();
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/'] });
    render(<RouterProvider router={router} />);

    expect(screen.queryByTestId('nav-trade-name')).toBeNull();
  });
});

describe('로그아웃', () => {
  it('성공하면 DELETE 를 보내고 로그인 자리로 간다', async () => {
    stub({ signOutStatus: 204 });
    const router = await renderAt('/');

    await act(async () => {
      screen.getByTestId('sign-out').click();
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
      screen.getByTestId('sign-out').click();
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
