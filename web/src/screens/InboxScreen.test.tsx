// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { NOTIFICATIONS_QUERY_KEY, notificationsUnreadQueryKey } from '../api/queryKeys';
import {
  NOTIFICATION_BID_KIND_DISPLAY,
  NOTIFICATION_DETAIL_DISPLAY,
  NOTIFICATION_KIND_DISPLAY,
} from '../display/notificationDisplay';

import { InboxScreen } from './InboxScreen';

vi.mock('../api/queries');

type NotificationItem = queries.NotificationItem;

function item(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    kind: 'OPENING_COMPLETED',
    detail: null,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    bidKind: 'REAL',
    occurredAt: '2026-11-02T01:00:00Z',
    read: false,
    readAt: null,
    noticeItemCount: 1,
    noticeUnreadCount: 1,
    noticeKnown: true,
    noticeTitle: '합성 공고',
    noticeStatus: 'Open',
    noticeDeadlineAt: '2026-11-05T09:00:00Z',
    ...overrides,
  };
}

function page(rows: readonly NotificationItem[], unread: number, total = rows.length): queries.NotificationPage {
  return { rows, total, unread };
}

let client: QueryClient;

function renderScreen() {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/inbox', element: <InboxScreen /> },
      { path: '/notices/:noticeNumber/:noticeRound', element: <p>워크시트 자리</p> },
    ],
    { initialEntries: ['/inbox'] },
  );
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

const mocked = vi.mocked(queries);

beforeEach(() => {
  vi.resetAllMocks();
  mocked.readSession.mockResolvedValue({ csrf: 'csrf-value', expiresAt: '2099-01-01T00:00:00Z' });
  mocked.listNotifications.mockResolvedValue(page([item()], 1));
});

afterEach(() => {
  cleanup();
});

describe('알림함 — 받은편지함 하나', () => {
  it('항목이 종류의 문면과 사실 시각으로 선다', async () => {
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    expect(row.textContent ?? '').toContain(NOTIFICATION_KIND_DISPLAY.OPENING_COMPLETED.label);
    // M9 D-1 — 보이는 글자는 KST 표기다(앞 판은 응답의 ISO 원문이었다).
    expect(row.textContent ?? '').toContain('2026-11-02 10:00');
    expect(row.textContent ?? '', 'ISO 원문이 화면에 남았다').not.toContain('2026-11-02T01:00:00Z');
  });

  it('부가 사실이 있으면 그 문면도 선다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ detail: 'PROVISIONAL_FIRST' })], 1));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    expect(row.textContent ?? '').toContain(NOTIFICATION_DETAIL_DISPLAY.PROVISIONAL_FIRST.label);
  });

  it('항목이 없으면 빈 얼굴이다', async () => {
    mocked.listNotifications.mockResolvedValue(page([], 0));
    renderScreen();

    expect(await screen.findByTestId('inbox-empty')).toBeTruthy();
  });

  it('세션이 없다는 답에는 로그인 얼굴이다', async () => {
    mocked.readSession.mockResolvedValue(null);
    mocked.listNotifications.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    expect(await screen.findByTestId('signed-out')).toBeTruthy();
  });

  /** 클릭 → 그 건의 워크시트(요구표 「클릭 → 그 건의 워크시트」 ⓐ). */
  it('항목이 그 공고의 워크시트로 가는 링크를 가진다', async () => {
    renderScreen();

    const row = within(await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL'));
    expect(row.getByRole('link').getAttribute('href')).toBe('/notices/SYN-7E3-RESULT/000');
  });
});

describe('안읽음 배지는 헤더 값에서만 온다 (D-7E3a-6)', () => {
  /**
   * **본문 행을 세면 틀린다**: `X-Unread-Count` 는 `unreadOnly` 와 무관한 계정 전수이고 본문은
   * 한 페이지다. 이 test 는 그 둘을 **다른 수**로 주어, 화면이 어느 쪽을 읽는지 가른다.
   */
  it('페이지의 안읽음 행 수가 아니라 헤더의 전수를 보인다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ read: false })], 7, 1));
    renderScreen();

    // **목록이 온 뒤에 읽는다**(7E-2b r1 교훈): 적재 중에는 배지가 「값 없음」이라 여기서 바로
    // 읽으면 「본문을 세지 않는다」가 아니라 「아직 모른다」를 재게 된다.
    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    const badge = screen.getByTestId('inbox-unread-badge');
    expect(badge.textContent ?? '').toContain('7');
  });

  /**
   * **접근성 축으로 잠근다**(r2 CR L-2). `data-testid` 로만 집으면 `role="status"` 를 지우는
   * 변이가 초록이다 — 그 속성이 쓰기 성공의 **유일한** 신호다(알림함에는 토스트가 없다).
   */
  it('배지가 live region 이다 — 쓰기 성공이 조용하지 않다', async () => {
    renderScreen();
    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');

    const live = screen.getByRole('status');
    expect(live.getAttribute('data-testid'), 'live region 이 배지가 아니다').toBe('inbox-unread-badge');
    expect(live.getAttribute('aria-live')).toBe('polite');
  });

  it('안읽음이 0 이면 배지가 0 을 말한다 — 숨겨서 「모른다」로 만들지 않는다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ read: true, readAt: '2026-11-02T02:00:00Z' })], 0));
    renderScreen();

    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    const badge = screen.getByTestId('inbox-unread-badge');
    expect(badge.textContent ?? '').toContain('0');
    expect(badge.textContent ?? '', '적재 중의 「값 없음」을 0 으로 읽지 않았다').not.toContain('값 없음');
  });
});

describe('읽음 처리는 서버 응답 뒤에만 보인다 (낙관 갱신 금지)', () => {
  /**
   * 7E-2b 와 같은 원칙이다. 낙관 갱신은 쓰기가 실패해도 읽은 것처럼 보이게 하고, 그러면
   * 운영자가 그 소식을 다시 보지 않는다.
   */
  it('응답 전에는 안읽음 그대로이고 응답 뒤에 바뀐다', async () => {
    let release: (value: queries.MarkReadResult) => void = () => {};
    mocked.markNotificationRead.mockReturnValue(
      new Promise<queries.MarkReadResult>((resolve) => {
        release = resolve;
      }),
    );
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    const button = within(row).getByRole('button', { name: /읽음/u });
    expect(within(row).getByTestId('read-state').textContent ?? '').toContain('안읽음');

    // 다음 읽기는 **읽음으로 바뀐 행**을 준다 — 화면은 그 재조회 결과로만 바뀐다.
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    fireEvent.click(button);

    /**
     * **양성 표지를 먼저 기다린다**(7E-2b r1 교훈 · 변이 M5 가 가르쳐 준 자리). 누른 직후
     * 바로 읽으면 쓰기가 아직 뜨지도 않아 「낙관 갱신을 하지 않았다」가 아니라 「아직 아무 일도
     * 일어나지 않았다」를 재게 된다 — 앞 판은 그래서 **낙관 갱신을 넣은 변이에서도 초록**이었다.
     * 버튼이 잠긴 것이 「요청이 떠 있다」의 관측이고, 그 동안에만 아래 음성 단언이 뜻을 가진다.
     */
    await waitFor(() => expect(button.hasAttribute('disabled')).toBe(true));
    expect(
      within(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')).getByTestId('read-state')
        .textContent ?? '',
      '응답 전에 읽음으로 바뀌었다 — 낙관 갱신이다',
    ).toContain('안읽음');

    release({ markedCount: 1, unreadCount: 0 });
    await waitFor(() =>
      expect(
        within(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')).getByTestId('read-state')
          .textContent ?? '',
      ).toContain('읽음'),
    );
  });

  /**
   * **쓰기 뒤 포커스를 돌려놓는다**(r2 CR M-1). 「버튼을 지우지 않는다」만으로는 닫히지 않는다 —
   * 포커스된 요소가 `disabled` 가 되면 브라우저가 blur 하므로 포커스는 그대로 `<body>` 로
   * 떨어진다. 그래서 행으로 **명시적으로** 옮긴다.
   *
   * jsdom 은 disable 시 blur 를 구현하지 않으므로 여기서 재는 것은 **옮기는 쪽**이다(「옮겼다」는
   * 참, 「안 옮기면 잃는다」는 브라우저 몫 — evidence 제한에 적었다).
   */
  it('읽음 처리가 끝나면 포커스가 그 행으로 간다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    fireEvent.click(within(row).getByRole('button', { name: /읽음/u }));

    await waitFor(() =>
      expect(
        document.activeElement,
        '쓰기 뒤 포커스가 행으로 돌아오지 않았다 — 키보드 운영자가 자리를 잃는다',
      ).toBe(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')),
    );
  });

  /**
   * **갈라진 묶음에서 둘째를 눌러도 그 묶음으로 간다**(r3 CR M-1). 앞 판은 번호·차수로 지은
   * 문자열 id 를 `getElementById` 로 찾았고, 떨어진 같은 공고는 **두 묶음으로 남는 것이 결정**
   * 이라 그 id 가 문서에 둘이었다 — 둘째에서 누르면 포커스가 **첫째**로 끌려갔다.
   */
  it('떨어진 같은 공고의 둘째 묶음에서 눌러도 그 묶음으로 간다', async () => {
    mocked.markNoticeNotificationsRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    mocked.listNotifications.mockResolvedValue(
      page(
        [
          item({ noticeNumber: 'A' }),
          item({ noticeNumber: 'B' }),
          item({ noticeNumber: 'A' }),
        ],
        3,
      ),
    );
    renderScreen();

    const second = await screen.findByTestId('notice-group-2-A-000-REAL');
    fireEvent.click(within(second).getByRole('button', { name: /묶음 전체/u }));

    await waitFor(() =>
      expect(
        document.activeElement,
        '첫째 묶음으로 끌려갔다 — 운영자가 조작한 자리가 아니다',
      ).toBe(screen.getByTestId('notice-group-2-A-000-REAL')),
    );
    expect(document.activeElement).not.toBe(screen.getByTestId('notice-group-0-A-000-REAL'));
  });

  /**
   * **실패 경로에서도 자리를 잃지 않는다**(r3 CR M-2). 앞 판은 방아쇠가 `inbox.data` 참조라,
   * 서버 상태가 안 바뀌는 실패에서는 structural sharing 이 **같은 참조**를 돌려줘 effect 가 서지
   * 않았다 — 버튼은 누른 순간 잠겨 blur 됐는데 돌려놓을 자리는 없었다.
   */
  it('쓰기가 실패하면 포커스가 실패 얼굴로 간다', async () => {
    mocked.markNotificationRead.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    fireEvent.click(within(row).getByRole('button', { name: /읽음으로/u }));

    const failure = await screen.findByTestId('write-failure');
    await waitFor(() =>
      expect(document.activeElement, '실패 경로에서 포커스를 잃었다').toBe(failure.parentElement),
    );
  });

  /**
   * **실패 뒤 「다시 시도」가 성공하면 그 행으로 돌아간다**(r3 CR M-2 ③). 그 버튼은 클릭 경로를
   * 지나지 않아 장전이 비어 있고, 성공하면 실패 얼굴이 사라지면서 포커스가 또 `<body>` 로
   * 떨어졌다.
   */
  it('실패 뒤 다시 시도가 성공하면 포커스가 그 행으로 돌아온다', async () => {
    mocked.markNotificationRead.mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    fireEvent.click(within(row).getByRole('button', { name: /읽음으로/u }));
    const failure = await screen.findByTestId('write-failure');

    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    fireEvent.click(within(failure).getByRole('button', { name: /다시 시도/u }));

    await waitFor(() =>
      expect(
        document.activeElement,
        '다시 시도가 성공했는데 포커스가 갈 곳을 잃었다',
      ).toBe(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')),
    );
  });

  /**
   * **장전이 남지 않는다**(r3 CR M-2 ⓑ). 남기면 나중의 **무관한** 재조회가 포커스를 옛 행으로
   * 끌어간다 — `BidBoxScreen` 주석이 적어 둔 바로 그 사고다.
   */
  it('나중의 무관한 재조회가 포커스를 끌어가지 않는다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    fireEvent.click(within(row).getByRole('button', { name: /읽음으로/u }));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId(
      'notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL',
    )));

    // 운영자가 포커스를 다른 데로 옮긴 뒤, 무관한 재조회가 새 데이터를 가져온다.
    const badge = screen.getByTestId('inbox-unread-badge');
    badge.setAttribute('tabindex', '-1');
    badge.focus();
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z' }), item({ kind: 'AWARD_DECIDED' })], 1),
    );
    await act(async () => {
      await client.invalidateQueries({ queryKey: ['notifications'] });
    });

    expect(document.activeElement, '장전이 남아 포커스가 옛 행으로 끌려갔다').toBe(badge);
  });

  it('묶음 전체 읽음 뒤에는 포커스가 그 묶음으로 간다', async () => {
    mocked.markNoticeNotificationsRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    renderScreen();

    const group = await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL');
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    fireEvent.click(within(group).getByRole('button', { name: /묶음 전체/u }));

    await waitFor(() =>
      expect(document.activeElement).toBe(screen.getByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL')),
    );
  });

  /**
   * **누른 버튼이 사라지지 않는다**(r1 CR L-1) — 포커스 복귀와 **다른 축**이다(locator 안정).
   */
  it('읽은 뒤에도 그 버튼이 남는다 — 잠기고 문면만 바뀐다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    const button = within(row).getByRole('button', { name: /읽음/u });
    expect(button.hasAttribute('disabled'), '읽은 행의 버튼이 열려 있다').toBe(true);
    expect(button.textContent ?? '').toContain('읽음');
  });

  /**
   * **투찰 종류가 그 행의 값으로 실려 나간다**(v M-1). fixture 를 `PAPER` 로 두는 것이 이
   * test 의 요점이다 — `REAL` 행에서 누르면 축을 `'REAL'` 로 **고정한** 회귀가 그대로
   * 통과한다(verifier 변이 W3 가 초록이었던 자리). 묶음 경로는 이미 `PAPER` 행에서 재고
   * 있었고, 단건 경로만 그 축이 열려 있었다.
   *
   * 그 회귀가 조용한 이유: 상태 코드는 200 이고 `markedCount` 도 그럴듯하다 — 다만 서버가
   * **실투찰 항목**을 읽음으로 바꾼다. 서면을 읽으면 실제 소식이 사라지는 꼴이다.
   */
  it('읽음 처리는 종류·공고 쌍·그 행의 투찰 종류로 보낸다 — 합성 id 가 없다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    mocked.listNotifications.mockResolvedValue(page([item({ bidKind: 'PAPER' })], 1));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-PAPER');
    fireEvent.click(within(row).getByRole('button', { name: /읽음/u }));

    await waitFor(() =>
      expect(mocked.markNotificationRead).toHaveBeenCalledWith({
        kind: 'OPENING_COMPLETED',
        noticeNumber: 'SYN-7E3-RESULT',
        noticeRound: '000',
        bidKind: 'PAPER',
      }),
    );
  });

  /**
   * **묶음 전체 읽음이 투찰 종류를 함께 보낸다**(M9/paper-bid). 공고 쌍만 보내면 서버가 같은
   * 공고의 **실투찰 묶음까지** 읽어, 연습이 실제 소식을 지우는 꼴이 읽음 축에서 되살아난다.
   * 그래서 여기서 재는 것은 「서면 묶음에서 눌렀을 때 PAPER 가 실려 나가는가」다.
   */
  it('묶음 전체 읽음은 공고 쌍과 그 묶음의 투찰 종류를 보낸다', async () => {
    mocked.markNoticeNotificationsRead.mockResolvedValue({ markedCount: 2, unreadCount: 0 });
    mocked.listNotifications.mockResolvedValue(page([item({ bidKind: 'PAPER' })], 1));
    renderScreen();

    const group = await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-PAPER');
    fireEvent.click(within(group).getByRole('button', { name: /묶음 전체/u }));

    await waitFor(() =>
      expect(mocked.markNoticeNotificationsRead).toHaveBeenCalledWith({
        noticeNumber: 'SYN-7E3-RESULT',
        noticeRound: '000',
        bidKind: 'PAPER',
      }),
    );
  });

  it('쓰기가 실패하면 실패 얼굴을 내고 읽음으로 바꾸지 않는다', async () => {
    mocked.markNotificationRead.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    fireEvent.click(within(row).getByRole('button', { name: /읽음/u }));

    expect(await screen.findByTestId('write-failure')).toBeTruthy();
    expect(
      within(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')).getByTestId('read-state')
        .textContent ?? '',
    ).toContain('안읽음');
  });
});

describe('건별 묶음', () => {
  it('같은 공고의 항목이 한 묶음에 서고 묶음 머리가 공고를 말한다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page(
        [
          item({ kind: 'OPENING_COMPLETED', noticeItemCount: 2, noticeUnreadCount: 2 }),
          item({ kind: 'AWARD_DECIDED', noticeItemCount: 2, noticeUnreadCount: 2 }),
        ],
        2,
      ),
    );
    renderScreen();

    const group = within(await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL'));
    expect(group.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')).toBeTruthy();
    expect(group.getByTestId('notification-AWARD_DECIDED-SYN-7E3-RESULT-000-REAL')).toBeTruthy();
  });

  /**
   * **같은 공고의 두 연습이 두 묶음이고 각자의 계수를 보인다**(M9/paper-bid). 한 블록에 섞이면
   * 서버가 축별로 센 계수가 그 블록의 항목 수와 맞지 않고, 무엇보다 운영자가 연습 결과를
   * 실제 결과로 읽는다.
   *
   * 묶음 머리의 **문면까지** 단언한다 — 행이 둘로 갈려 있어도 어느 쪽이 연습인지 보이지 않으면
   * 갈라 놓은 뜻이 없다.
   */
  it('같은 공고의 서면·실투찰이 두 묶음이고 각자의 문면과 계수를 보인다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page(
        [
          item({ bidKind: 'REAL', noticeItemCount: 1, noticeUnreadCount: 1 }),
          item({ bidKind: 'PAPER', noticeItemCount: 2, noticeUnreadCount: 2 }),
        ],
        3,
      ),
    );
    renderScreen();

    const real = (await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL')).textContent ?? '';
    const paper = screen.getByTestId('notice-group-1-SYN-7E3-RESULT-000-PAPER').textContent ?? '';

    expect(real).toContain(NOTIFICATION_BID_KIND_DISPLAY.REAL.label);
    expect(real).toContain('항목 1');
    expect(paper).toContain(NOTIFICATION_BID_KIND_DISPLAY.PAPER.label);
    expect(paper).toContain('항목 2');
    // 두 묶음이 서로의 문면을 쓰지 않는다 — 한 얼굴로 접히면 행이 둘이어도 구분이 없다.
    expect(real).not.toContain(NOTIFICATION_BID_KIND_DISPLAY.PAPER.label);
  });

  /** 묶음이 페이지 경계에 걸리면 그 사실을 말한다 — 서버의 전체 크기에서만 안다. */
  it('묶음이 페이지에 다 오지 않으면 그 문면을 말한다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ noticeItemCount: 3, noticeUnreadCount: 3 })], 3));
    renderScreen();

    // **문면 자체를 잰다**(r1 CR M-4): 앞 판은 `'3'` 만 봤고 묶음 머리가 이미 「항목 3」을 적어
    // `hasMoreThanShown` 줄을 통째로 지워도 초록이었다 — 이름이 잰다고 한 것을 재지 않았다.
    const group = await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL');
    expect(group.textContent ?? '').toContain('이 페이지에 다 오지 않았습니다');
  });

  /** 음성 대조 — 다 왔으면 그 문면이 **없다**(있으면 늘 참인 단언이 된다). */
  it('묶음이 다 왔으면 그 문면이 없다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page(
        [
          item({ kind: 'OPENING_COMPLETED', noticeItemCount: 2, noticeUnreadCount: 2 }),
          item({ kind: 'AWARD_DECIDED', noticeItemCount: 2, noticeUnreadCount: 2 }),
        ],
        2,
      ),
    );
    renderScreen();

    const group = await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL');
    expect(group.textContent ?? '').not.toContain('이 페이지에 다 오지 않았습니다');
  });

  /**
   * **key 가 삽입에 강하다**(r2 CR L-3). key 에 전체 차례를 쓰면 맨 앞에 묶음이 하나 끼었을 때
   * 뒤 묶음이 **통째로 재마운트**되고, 그러면 「버튼이 DOM 에 남는다」·포커스 복귀가 그 실행에서
   * 성립하지 않는다. DOM 노드의 **정체**로 잰다.
   */
  it('앞에 묶음이 끼어도 뒤 묶음의 DOM 노드가 유지된다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 1 });
    mocked.listNotifications.mockResolvedValue(page([item({ noticeNumber: 'B' })], 1));
    renderScreen();
    const before = await screen.findByTestId('notice-group-0-B-000-REAL');

    // 맨 앞에 새 공고의 묶음이 들어온다(새 알림이 그 사이에 오는 꼴) — 읽음 처리의 재조회가
    // 그 새 목록을 받는다.
    mocked.listNotifications.mockResolvedValue(
      page([item({ noticeNumber: 'A' }), item({ noticeNumber: 'B', read: true, readAt: '2026-11-02T02:00:00Z' })], 1),
    );
    fireEvent.click(within(before).getByRole('button', { name: /읽음으로/u }));

    await waitFor(() => expect(screen.queryByTestId('notice-group-0-A-000-REAL')).not.toBeNull());
    expect(
      screen.getByTestId('notice-group-1-B-000-REAL'),
      '앞에 끼어든 묶음 때문에 뒤 묶음이 재마운트됐다',
    ).toBe(before);
  });

  /** 갈라진 같은 공고의 두 묶음이 **서로 다른 testId** 를 가진다(r1 CR L-3). */
  it('떨어진 같은 공고의 두 묶음이 겹치지 않는다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page(
        [
          item({ noticeNumber: 'A', kind: 'OPENING_COMPLETED' }),
          item({ noticeNumber: 'B', kind: 'OPENING_COMPLETED' }),
          item({ noticeNumber: 'A', kind: 'OPENING_COMPLETED' }),
        ],
        3,
      ),
    );
    renderScreen();

    expect(await screen.findByTestId('notice-group-0-A-000-REAL')).toBeTruthy();
    expect(screen.getByTestId('notice-group-2-A-000-REAL')).toBeTruthy();
  });

  /**
   * 「공고 행이 없다」와 「행은 있고 제목 칸이 비었다」는 **다른 사실**이다(계약). 둘을 같은
   * 빈 자리로 그리면 운영자가 파기된 공고와 제목 없는 공고를 구별할 수 없다.
   */
  it('공고를 모르는 묶음과 제목만 빈 묶음이 다른 문면이다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page([item({ noticeKnown: false, noticeTitle: null, noticeStatus: null, noticeDeadlineAt: null })], 1),
    );
    const unknown = renderScreen();
    const unknownText = (await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL')).textContent ?? '';
    unknown.unmount();

    mocked.listNotifications.mockResolvedValue(page([item({ noticeKnown: true, noticeTitle: null })], 1));
    renderScreen();
    const titlelessText = (await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL')).textContent ?? '';

    expect(unknownText).not.toBe(titlelessText);
    expect(unknownText).toContain('공고를 모름');
  });
});

describe('빈 자리 (R-4)', () => {
  it('채널 선택·복제가 「준비 중」이고 입력칸·버튼이 없다', async () => {
    renderScreen();

    const slot = await screen.findByTestId('pending-notification-channel');
    expect(slot.textContent ?? '').toContain('준비 중');
    expect(within(slot).queryAllByRole('button')).toStrictEqual([]);
    expect(within(slot).queryAllByRole('checkbox')).toStrictEqual([]);
    // M13/13A — 내부 용어를 걷은 자리다. 고친 문면만 축어로 잠근다(일반 축은 열지 않는다).
    for (const term of ['effect', '발송']) {
      expect(slot.textContent ?? '').not.toContain(term);
    }
  });
});

describe('로딩·오류 얼굴과 적재 중 배지 (r1 v M-1 · CR M-2)', () => {
  it('적재 중에는 로딩 얼굴이고 빈 얼굴이 **아니다**', async () => {
    mocked.listNotifications.mockReturnValue(new Promise<queries.NotificationPage>(() => {}));
    renderScreen();

    expect(await screen.findByTestId('inbox-loading')).toBeTruthy();
    expect(screen.queryByTestId('inbox-empty'), '적재 중을 「알림이 없다」로 접었다').toBeNull();
  });

  /**
   * **응답 전에 0 을 지어내지 않는다**(D-7E3a-6 · v M-1). 배지 test 둘이 모두 목록이 온 **뒤**
   * 를 읽어서, `?? 0` 으로 바꾸는 변이가 초록이었다 — 「모른다」와 「0 이다」는 다른 답이다.
   */
  it('응답 전 배지는 0 이 아니라 「값 없음」이다', async () => {
    mocked.listNotifications.mockReturnValue(new Promise<queries.NotificationPage>(() => {}));
    renderScreen();

    expect(await screen.findByTestId('inbox-loading')).toBeTruthy();
    const badge = screen.getByTestId('inbox-unread-badge').textContent ?? '';
    expect(badge, '응답 전에 안읽음 수를 지어냈다').toContain('값 없음');
    expect(badge).not.toMatch(/\d/u);
  });

  it('목록이 실패하면 오류 얼굴이고 「다시 시도」가 다시 읽는다', async () => {
    mocked.listNotifications.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    expect(await screen.findByTestId('inbox-error')).toBeTruthy();
    expect(screen.queryByTestId('inbox-empty'), '오류를 「알림이 없다」로 접었다').toBeNull();

    const before = mocked.listNotifications.mock.calls.length;
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(mocked.listNotifications.mock.calls.length).toBeGreaterThan(before));
  });
});

/**
 * **링크를 열면 읽음이다**(M13/13E, 받는 항목 9). 앞 판은 워크시트 링크를 열어도 읽음이
 * 되지 않아, 운영자가 소식을 **보고 나서** 따로 「읽음으로」를 눌러야 했다.
 *
 * 쓰기를 **걸고 바로 이동한다** — 그래서 알림함 안의 실패 얼굴은 볼 수 없고, 쓰기가 실패한
 * 사실은 **셸의 안 읽음 배지가 줄지 않는 것**으로 보인다(계약 13E 행). 그 배지는 알림함 질의
 * 키의 접두 아래에 있으므로, 이 축은 「그 키가 무효화되는가」로 **구조로** 잰다.
 */
describe('링크로 읽음 (받는 항목 9)', () => {
  /** 배지가 쓰는 질의가 **이미 있는** 상태를 만든다 — 셸은 어느 화면에서나 그것을 쥐고 있다. */
  function seedBadgeQuery(unread: number): void {
    client.setQueryData(notificationsUnreadQueryKey(), { rows: [], total: 0, unread });
  }

  function badgeInvalidated(): boolean {
    return client.getQueryState(notificationsUnreadQueryKey())?.isInvalidated ?? false;
  }

  it('워크시트 링크를 누르면 읽음 쓰기가 나가고 이동이 막히지 않는다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    fireEvent.click(within(row).getByRole('link', { name: /워크시트/u }));

    await waitFor(() =>
      expect(mocked.markNotificationRead, '링크를 열었는데 읽음 쓰기가 나가지 않았다').toHaveBeenCalledWith({
        kind: 'OPENING_COMPLETED',
        noticeNumber: 'SYN-7E3-RESULT',
        noticeRound: '000',
        bidKind: 'REAL',
      }),
    );
    // **이동을 막지 않는다** — 쓰기를 기다리는 동안 운영자를 알림함에 붙들어 두지 않는다.
    expect(await screen.findByText('워크시트 자리'), '읽음 쓰기가 이동을 삼켰다').toBeTruthy();
  });

  /**
   * **「걸고 **바로** 이동」의 순서를 잠근다**(수정 라운드 1, verifier F-3). 앞 판의 test 는
   * 즉시 resolve 되는 mock 위에서 이동을 기다려, 「쓰기가 **끝난 뒤에** 이동」으로 바꾼 변이가
   * 초록이었다. 쓰기를 **보류한 채로** 워크시트가 이미 서 있는지를 본다.
   */
  it('쓰기가 아직 끝나지 않았는데 워크시트가 이미 서 있다', async () => {
    let release: (value: queries.MarkReadResult) => void = () => {};
    mocked.markNotificationRead.mockReturnValue(
      new Promise<queries.MarkReadResult>((resolve) => {
        release = resolve;
      }),
    );
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    fireEvent.click(within(row).getByRole('link', { name: /워크시트/u }));

    await waitFor(() => expect(mocked.markNotificationRead).toHaveBeenCalled());
    expect(
      screen.getByText('워크시트 자리'),
      '쓰기가 끝나기를 기다렸다가 이동했다 — 운영자를 붙들어 두었다',
    ).toBeTruthy();

    release({ markedCount: 1, unreadCount: 0 });
  });

  /** 이미 읽은 행에서는 **다시 쓰지 않는다** — 같은 사실을 두 번 보내지 않는다. */
  it('이미 읽은 행의 링크는 쓰기를 더 보내지 않는다', async () => {
    mocked.listNotifications.mockResolvedValue(
      page([item({ read: true, readAt: '2026-11-02T02:00:00Z', noticeUnreadCount: 0 })], 0),
    );
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    fireEvent.click(within(row).getByRole('link', { name: /워크시트/u }));

    expect(await screen.findByText('워크시트 자리')).toBeTruthy();
    expect(mocked.markNotificationRead).not.toHaveBeenCalled();
  });

  /**
   * **낙관 갱신 금지는 그대로다**(D-7E3a-1). 떠나기 전의 마지막 그림에서도, 그리고 배지가 쥔
   * 값에서도 읽은 것으로 보이지 않는다 — 서버가 답하기 전에는 **어디서도** 아니다.
   */
  it('응답 전에는 어디서도 읽음으로 보이지 않는다', async () => {
    let release: (value: queries.MarkReadResult) => void = () => {};
    mocked.markNotificationRead.mockReturnValue(
      new Promise<queries.MarkReadResult>((resolve) => {
        release = resolve;
      }),
    );
    renderScreen();
    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    seedBadgeQuery(3);

    fireEvent.click(within(row).getByRole('link', { name: /워크시트/u }));
    await waitFor(() => expect(mocked.markNotificationRead).toHaveBeenCalled());

    expect(
      (client.getQueryData(notificationsUnreadQueryKey()) as queries.NotificationPage).unread,
      '서버가 답하기 전에 배지 값을 깎았다 — 낙관 갱신이다',
    ).toBe(3);
    expect(badgeInvalidated(), '응답 전에 배지를 다시 읽혔다').toBe(false);
    /**
     * **목록 캐시까지 본다**(수정 라운드 1, verifier F-2). 배지만 재면 「어디서도」라는 이름이
     * 거짓이 된다 — 목록 캐시의 그 행을 미리 읽음으로 바꾸는 변이가 **초록**이었다(그 변이를
     * verifier 가 실제로 넣어 보였다). 알림함으로 돌아왔을 때 보이는 것이 이 캐시다.
     */
    const cached = client.getQueryData(NOTIFICATIONS_QUERY_KEY) as
      | { readonly pages: readonly queries.NotificationPage[] }
      | undefined;
    expect(cached?.pages[0]?.rows[0]?.read, '응답 전에 목록 캐시가 읽음으로 바뀌었다').toBe(false);

    release({ markedCount: 1, unreadCount: 2 });
    await waitFor(() => expect(badgeInvalidated()).toBe(true));
  });

  /**
   * **성공 → 배지가 다시 읽힌다**(그래서 줄어든다). 그 무효화는 배선이 아니라 **키의 접두
   * 관계**로 선다 — 알림 질의를 무효화하면 배지가 그 아래에 있어 함께 닿는다.
   */
  it('성공하면 배지가 쓰는 질의 키도 무효화된다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    renderScreen();
    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    seedBadgeQuery(1);

    fireEvent.click(within(row).getByRole('link', { name: /워크시트/u }));

    await waitFor(() => expect(badgeInvalidated(), '성공했는데 배지가 그대로다').toBe(true));
  });

  /**
   * **실패 → 배지가 그대로다.** 알림함을 떠난 뒤라 실패 얼굴이 설 자리가 없고, 그래서 배지가
   * **줄지 않는 것**이 운영자가 보는 신호다(계약 13E 행). 실패에서 무효화를 걸면 같은 수를
   * 다시 읽어 오느라 「변하지 않았다」가 **우연히** 참이 된다 — 그 우연에 기대지 않는다.
   */
  it('실패하면 배지 값도 그 질의도 건드리지 않는다', async () => {
    mocked.markNotificationRead.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();
    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    seedBadgeQuery(5);

    fireEvent.click(within(row).getByRole('link', { name: /워크시트/u }));

    expect(await screen.findByText('워크시트 자리')).toBeTruthy();
    await waitFor(() => expect(mocked.markNotificationRead).toHaveBeenCalled());
    expect(
      (client.getQueryData(notificationsUnreadQueryKey()) as queries.NotificationPage).unread,
      '쓰기가 실패했는데 배지 값이 바뀌었다',
    ).toBe(5);
    expect(badgeInvalidated(), '실패인데 배지를 다시 읽혔다').toBe(false);
  });

  /** 묶음 머리의 「읽음으로」 길은 **그대로 남는다** — 열지 않고 읽는 길이 있어야 한다. */
  it('「읽음으로」 버튼이 남아 있다 — 열지 않고 읽는 길', async () => {
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    expect(within(row).getByRole('button', { name: /읽음으로/u })).toBeTruthy();
  });
});

/**
 * **다음 쪽은 홈과 같은 「더 보기」 꼴이다**(받는 항목 9). 앞 판은 「이 페이지에 다 오지
 * 않았습니다」라고 말하면서 다음으로 가는 길이 없었다(`listNotifications()` 무인자 호출).
 */
describe('알림함 — 「더 보기」', () => {
  it('화면의 읽기는 쪽을 **명시한다**', async () => {
    renderScreen();

    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    expect(mocked.listNotifications).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
  });

  it('받은 수가 전체보다 적으면 「더 보기」가 있고, 누르면 다음 쪽을 이어 붙인다', async () => {
    mocked.listNotifications.mockResolvedValueOnce(page([item({ noticeNumber: 'A' })], 2, 2));
    mocked.listNotifications.mockResolvedValueOnce(page([item({ noticeNumber: 'B' })], 2, 2));
    renderScreen();

    const more = await screen.findByTestId('inbox-more');
    fireEvent.click(more);

    expect(await screen.findByTestId('notice-group-1-B-000-REAL')).toBeTruthy();
    expect(screen.getByTestId('notice-group-0-A-000-REAL')).toBeTruthy();
    expect(mocked.listNotifications).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    await waitFor(() => expect(screen.queryByTestId('inbox-more')).toBeNull());
  });

  /**
   * **offset 쪽 사이에 적재가 생기면 같은 항목이 두 쪽에 걸친다**(수정 라운드 1, 리뷰 M-3).
   * 그 넷(종류·번호·차수·투찰 종류)이 행의 신원 전부라, 접지 않으면 React key 가 겹치고 같은
   * `data-testid` 가 문서에 둘이 되어 여정의 strict locator 가 모호해진다.
   */
  it('쪽 둘이 같은 항목을 내도 행은 하나다', async () => {
    const shared = item({ noticeNumber: 'A', noticeItemCount: 2, noticeUnreadCount: 2 });
    mocked.listNotifications.mockResolvedValueOnce(page([shared], 2, 3));
    mocked.listNotifications.mockResolvedValueOnce(page([shared, item({ noticeNumber: 'B' })], 2, 3));
    renderScreen();

    fireEvent.click(await screen.findByTestId('inbox-more'));

    await waitFor(() => expect(screen.queryByTestId('notice-group-1-B-000-REAL')).not.toBeNull());
    expect(
      screen.getAllByTestId('notification-OPENING_COMPLETED-A-000-REAL'),
      '같은 항목이 두 줄로 섰다',
    ).toHaveLength(1);
  });

  it('받은 수가 전체와 같으면 「더 보기」가 없다', async () => {
    renderScreen();

    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL');
    expect(screen.queryByTestId('inbox-more')).toBeNull();
  });

  /**
   * **페이지 경계에 걸린 묶음이 다음 쪽을 받으면 합쳐진다.** 묶음은 **연속 접기**라 이어 붙인
   * 배열에서 한 묶음이 되고, 그러면 「이 페이지에 다 오지 않았습니다」가 사라진다 — 그 문면이
   * 남으면 다 받았는데도 더 있다고 말하는 꼴이다.
   */
  it('이어 붙이면 걸쳐 있던 묶음이 합쳐지고 그 문면이 사라진다', async () => {
    const first = item({ kind: 'OPENING_COMPLETED', noticeItemCount: 2, noticeUnreadCount: 2 });
    const second = item({ kind: 'AWARD_DECIDED', noticeItemCount: 2, noticeUnreadCount: 2 });
    mocked.listNotifications.mockResolvedValueOnce(page([first], 2, 2));
    mocked.listNotifications.mockResolvedValueOnce(page([second], 2, 2));
    renderScreen();

    const group = await screen.findByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL');
    expect(group.textContent ?? '').toContain('이 페이지에 다 오지 않았습니다');

    fireEvent.click(screen.getByTestId('inbox-more'));

    await waitFor(() =>
      expect(
        screen.getByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL').textContent ?? '',
        '다 받았는데도 더 있다고 말한다',
      ).not.toContain('이 페이지에 다 오지 않았습니다'),
    );
    const merged = within(screen.getByTestId('notice-group-0-SYN-7E3-RESULT-000-REAL'));
    expect(merged.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000-REAL')).toBeTruthy();
    expect(merged.getByTestId('notification-AWARD_DECIDED-SYN-7E3-RESULT-000-REAL')).toBeTruthy();
  });
});

/** **빈 화면이 다음 걸음을 준다**(받는 항목 7 의 알림함 자리). */
describe('알림함 — 빈 화면의 다음 걸음', () => {
  it('알림이 없으면 오늘의 공고로 가는 걸음을 준다', async () => {
    mocked.listNotifications.mockResolvedValue(page([], 0));
    renderScreen();

    const empty = await screen.findByTestId('inbox-empty');
    expect(within(empty).getByRole('link', { name: /오늘의 공고/u }).getAttribute('href')).toBe('/');
  });
});
