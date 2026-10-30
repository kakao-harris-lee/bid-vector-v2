// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { NOTIFICATION_DETAIL_DISPLAY, NOTIFICATION_KIND_DISPLAY } from '../display/notificationDisplay';

import { InboxScreen } from './InboxScreen';

vi.mock('../api/queries');

type NotificationItem = queries.NotificationItem;

function item(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    kind: 'OPENING_COMPLETED',
    detail: null,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
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

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
    expect(row.textContent ?? '').toContain(NOTIFICATION_KIND_DISPLAY.OPENING_COMPLETED.label);
    expect(row.textContent ?? '').toContain('2026-11-02T01:00:00Z');
  });

  it('부가 사실이 있으면 그 문면도 선다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ detail: 'PROVISIONAL_FIRST' })], 1));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
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

    const row = within(await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000'));
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
    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
    const badge = screen.getByTestId('inbox-unread-badge');
    expect(badge.textContent ?? '').toContain('7');
  });

  it('안읽음이 0 이면 배지가 0 을 말한다 — 숨겨서 「모른다」로 만들지 않는다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ read: true, readAt: '2026-11-02T02:00:00Z' })], 0));
    renderScreen();

    await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
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

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
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
      within(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000')).getByTestId('read-state')
        .textContent ?? '',
      '응답 전에 읽음으로 바뀌었다 — 낙관 갱신이다',
    ).toContain('안읽음');

    release({ markedCount: 1, unreadCount: 0 });
    await waitFor(() =>
      expect(
        within(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000')).getByTestId('read-state')
          .textContent ?? '',
      ).toContain('읽음'),
    );
  });

  it('읽음 처리는 종류와 공고 쌍으로 보낸다 — 합성 id 가 없다', async () => {
    mocked.markNotificationRead.mockResolvedValue({ markedCount: 1, unreadCount: 0 });
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
    fireEvent.click(within(row).getByRole('button', { name: /읽음/u }));

    await waitFor(() =>
      expect(mocked.markNotificationRead).toHaveBeenCalledWith({
        kind: 'OPENING_COMPLETED',
        noticeNumber: 'SYN-7E3-RESULT',
        noticeRound: '000',
      }),
    );
  });

  it('묶음 전체 읽음은 공고 쌍만 보낸다', async () => {
    mocked.markNoticeNotificationsRead.mockResolvedValue({ markedCount: 2, unreadCount: 0 });
    renderScreen();

    const group = await screen.findByTestId('notice-group-SYN-7E3-RESULT-000');
    fireEvent.click(within(group).getByRole('button', { name: /묶음 전체/u }));

    await waitFor(() =>
      expect(mocked.markNoticeNotificationsRead).toHaveBeenCalledWith({
        noticeNumber: 'SYN-7E3-RESULT',
        noticeRound: '000',
      }),
    );
  });

  it('쓰기가 실패하면 실패 얼굴을 내고 읽음으로 바꾸지 않는다', async () => {
    mocked.markNotificationRead.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const row = await screen.findByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000');
    fireEvent.click(within(row).getByRole('button', { name: /읽음/u }));

    expect(await screen.findByTestId('write-failure')).toBeTruthy();
    expect(
      within(screen.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000')).getByTestId('read-state')
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

    const group = within(await screen.findByTestId('notice-group-SYN-7E3-RESULT-000'));
    expect(group.getByTestId('notification-OPENING_COMPLETED-SYN-7E3-RESULT-000')).toBeTruthy();
    expect(group.getByTestId('notification-AWARD_DECIDED-SYN-7E3-RESULT-000')).toBeTruthy();
  });

  /** 묶음이 페이지 경계에 걸리면 그 사실을 말한다 — 서버의 전체 크기에서만 안다. */
  it('묶음이 페이지에 다 오지 않으면 「더 있다」를 말한다', async () => {
    mocked.listNotifications.mockResolvedValue(page([item({ noticeItemCount: 3, noticeUnreadCount: 3 })], 3));
    renderScreen();

    const group = await screen.findByTestId('notice-group-SYN-7E3-RESULT-000');
    expect(group.textContent ?? '').toContain('3');
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
    const unknownText = (await screen.findByTestId('notice-group-SYN-7E3-RESULT-000')).textContent ?? '';
    unknown.unmount();

    mocked.listNotifications.mockResolvedValue(page([item({ noticeKnown: true, noticeTitle: null })], 1));
    renderScreen();
    const titlelessText = (await screen.findByTestId('notice-group-SYN-7E3-RESULT-000')).textContent ?? '';

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
  });
});
