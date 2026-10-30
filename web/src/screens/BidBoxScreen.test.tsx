// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { BID_BOX_TABS, STATE_TAB } from '../display/bidBoxDisplay';
import { BID_CASE_STATE_DISPLAY, NEXT_CHECKPOINT_DISPLAY, type BidCaseState } from '../display/caseDisplay';

import { BidBoxScreen } from './BidBoxScreen';

vi.mock('../api/queries');

type BidCase = queries.BidCase;

/** 한 건의 기본 모양 — 각 test 가 **자기가 재는 칸만** 바꾼다. */
function bidCase(overrides: Partial<BidCase> = {}): BidCase {
  return {
    caseId: 11,
    noticeNumber: 'SYN-7E2-CASE-1',
    noticeRound: '000',
    bidKind: 'REAL',
    state: 'WATCHING',
    caseVersion: 3,
    nextCheckpointKind: 'DEADLINE',
    nextCheckpointAt: '2099-01-03T00:00:00Z',
    overdue: false,
    reconciliationCandidate: false,
    ...overrides,
  };
}

/** 같은 트리를 **다시** 그릴 수 있게 뽑았다 — 포커스 test 가 실제 재렌더를 만들어야 한다. */
function screenTree(client: QueryClient) {
  const router = createMemoryRouter(
    [
      { path: '/bids', element: <BidBoxScreen /> },
      { path: '/notices/:noticeNumber/:noticeRound', element: <p>워크시트 자리</p> },
    ],
    { initialEntries: ['/bids'] },
  );
  return (
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/bids', element: <BidBoxScreen /> },
      { path: '/notices/:noticeNumber/:noticeRound', element: <p>워크시트 자리</p> },
    ],
    { initialEntries: ['/bids'] },
  );
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

const mocked = vi.mocked(queries);

/**
 * 탭을 고른다 — 첫 탭은 「관심」이고 화면은 **보이는 탭의 건만** 그린다(그것이 탭의 뜻이다).
 * 다른 묶음의 건을 재는 test 는 그 탭을 먼저 골라야 한다.
 */
async function openTab(label: RegExp): Promise<void> {
  fireEvent.click(await screen.findByRole('tab', { name: label }));
}

beforeEach(() => {
  vi.resetAllMocks();
  mocked.readSession.mockResolvedValue({ csrf: 'csrf-value', expiresAt: '2099-01-01T00:00:00Z' });
  mocked.listBidCases.mockResolvedValue([bidCase()]);
});

afterEach(() => {
  cleanup();
});

describe('화면 D — 카드가 보이는 값은 API 응답의 값이다', () => {
  it('실투찰 종류로 목록을 묻는다 — 페이퍼와 섞지 않는다', async () => {
    renderScreen();

    await waitFor(() => expect(mocked.listBidCases).toHaveBeenCalledWith('REAL'));
  });

  it('상태·다음 확인 시점 문면이 표의 값이고 화면이 지어내지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'BID_RECORDED', nextCheckpointKind: 'OPENING' })]);
    renderScreen();
    await openTab(/투찰함/u);

    const card = await screen.findByTestId('bid-case-11');
    expect(within(card).getByText(BID_CASE_STATE_DISPLAY.BID_RECORDED.label)).toBeTruthy();
    expect(within(card).getByText(NEXT_CHECKPOINT_DISPLAY.OPENING.label)).toBeTruthy();
  });

  it('공고번호와 차수를 두 칸 그대로 보인다 — 공고명은 계약에 없다', async () => {
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    expect(within(card).getByText(/SYN-7E2-CASE-1/u)).toBeTruthy();
    expect(within(card).getByText(/000/u)).toBeTruthy();
  });

  /** 「없음」은 빈 값이 아니라 **답**이다(§6.1) — 그 자리에 시각을 지어내지 않는다. */
  it('다음 확인 시점이 NONE 이면 「없음」이고 시각 자리에 숫자가 없다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ nextCheckpointKind: 'NONE', nextCheckpointAt: null })]);
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    expect(within(card).getByText(NEXT_CHECKPOINT_DISPLAY.NONE.label)).toBeTruthy();
    expect(within(card).getByTestId('checkpoint-at-11').textContent).not.toMatch(/\d/u);
  });

  it('지난 일정 경고는 서버의 overdue 에서만 나온다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ overdue: true })]);
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    expect(within(card).getByTestId('overdue-11')).toBeTruthy();
  });

  /**
   * **이 slice 의 추론 금지 단언**(D-7E-4): 다음 확인 시점이 지났는데 서버가 `overdue: false`
   * 라면 화면은 **아무 말도 만들지 않는다** — 경고도 없고 「N일 지남」도 없다.
   *
   * v r1 M-1 이 잡은 자리: 앞 판은 브라우저 시계로 「9799일 지남」을 냈다. 그 말은 서버 판정과
   * 어긋나고, 사용자마다 시계가 달라 답이 갈린다.
   */
  it('시각이 지나도 overdue 가 거짓이면 경고도 「지남」 문면도 없다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ nextCheckpointAt: '2000-01-01T00:00:00Z', overdue: false }),
    ]);
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('overdue-11')).toBeNull();
    expect(card.textContent).not.toContain('지남');
    expect(card.textContent).not.toContain('남음');
  });

  /** 시각은 **서버가 준 값 그대로**이고 기계 가독 `<time>` 이다(7E-1 화면 C 와 같은 꼴). */
  it('다음 확인 시점 시각을 API 값 그대로 time 으로 낸다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ nextCheckpointAt: '2099-01-03T00:00:00Z' })]);
    renderScreen();

    const slot = await screen.findByTestId('checkpoint-at-11');
    expect(slot.textContent).toBe('2099-01-03T00:00:00Z');
    expect(slot.querySelector('time')?.getAttribute('dateTime')).toBe('2099-01-03T00:00:00Z');
  });
});

describe('화면 D — 탭은 API 상태의 묶음이다', () => {
  it('탭이 그 묶음의 건만 보인다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, state: 'WATCHING' }),
      bidCase({ caseId: 22, state: 'BID_RECORDED' }),
    ]);
    renderScreen();

    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('bid-case-22')).toBeNull();

    fireEvent.click(screen.getByRole('tab', { name: /투찰함/u }));

    await screen.findByTestId('bid-case-22');
    expect(screen.queryByTestId('bid-case-11')).toBeNull();
  });

  /**
   * **v r1 M-2 가 잡은 공백.** 탭 표는 `satisfies` 로 전수이고 표 test 도 있었지만, **화면의 필터**
   * 자리에 특례를 끼우면 살아남았다(실측: `row.state === 'BID_PLANNED' ? 'recorded' : …` 와
   * `startsWith('OPENED'|…)` 추론이 34/34 통과). 그래서 **상태 14 각각을 렌더해** 「그 건이
   * `STATE_TAB` 이 말하는 탭에만 있고 그 탭의 수에 들어간다」를 표에서 파생해 잰다.
   */
  it.each(Object.keys(STATE_TAB) as BidCaseState[])('%s 은 표가 말하는 탭에만 있다', async (state) => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state })]);
    const expectedTab = BID_BOX_TABS.find((one) => one.id === STATE_TAB[state])!;
    renderScreen();

    // 그 탭을 고르면 카드가 있고, 탭 이름 옆의 수가 1 이다.
    const tab = await screen.findByRole('tab', { name: new RegExp(`${expectedTab.label} 1`, 'u') });
    fireEvent.click(tab);
    expect(await screen.findByTestId('bid-case-11')).toBeTruthy();

    // 나머지 다섯 탭에서는 **없고** 그 수가 0 이다.
    for (const other of BID_BOX_TABS.filter((one) => one.id !== expectedTab.id)) {
      fireEvent.click(screen.getByRole('tab', { name: new RegExp(`${other.label} 0`, 'u') }));
      expect(screen.queryByTestId('bid-case-11'), `${state} 가 ${other.label} 탭에도 있다`).toBeNull();
    }
  });

  /** CR r1 M-5 — 탭이 **무엇을 여는지** 말해야 한다(배선이 반쪽이면 보조기술이 못 읽는다). */
  it('탭과 패널이 aria 로 묶이고 비선택 탭은 tab 순서에서 빠진다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    renderScreen();

    const panel = await screen.findByRole('tabpanel');
    const selected = screen.getByRole('tab', { selected: true });
    expect(selected.getAttribute('aria-controls')).toBe(panel.getAttribute('id'));
    expect(panel.getAttribute('aria-labelledby')).toBe(selected.getAttribute('id'));
    expect(selected.getAttribute('tabindex')).toBe('0');
    for (const other of screen.getAllByRole('tab').filter((one) => one !== selected)) {
      expect(other.getAttribute('tabindex')).toBe('-1');
    }
  });

  /**
   * **CR r2 L2R-1 의 회귀 test.** 앞 판은 `role="tabpanel"` 을 카드 `<ul>` 자체에 붙였고, 빈 탭에서는
   * 그 `<ul>` 이 없어 `aria-controls` 가 **없는 id** 를 가리켰다(보조기술이 그 탭이 무엇을 여는지
   * 말할 수 없는 자리로 되돌아갔다).
   */
  it('빈 탭에서도 패널이 있고 aria-controls 가 실재하는 요소를 가리킨다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    renderScreen();

    await screen.findByTestId('bid-case-11');
    fireEvent.click(screen.getByRole('tab', { name: /종료/u }));

    expect(await screen.findByTestId('bid-box-empty')).toBeTruthy();
    const panel = screen.getByRole('tabpanel');
    const selected = screen.getByRole('tab', { selected: true });
    expect(selected.getAttribute('aria-controls')).toBe(panel.getAttribute('id'));
    // 빈 얼굴이 **패널 안**에 있다.
    expect(panel.contains(screen.getByTestId('bid-box-empty'))).toBe(true);
  });

  /** 카드는 `<ul>` 의 자식으로 남는다 — `tabpanel` 이 그 목록을 **감싼다**. */
  it('카드가 목록 부모를 잃지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    expect(card.parentElement?.tagName).toBe('UL');
    expect(screen.getByRole('tabpanel').contains(card)).toBe(true);
  });

  it('좌우 화살표로 탭을 옮기고 끝에서 감싼다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'BID_RECORDED' })]);
    renderScreen();

    const first = await screen.findByRole('tab', { name: /관심/u });
    fireEvent.keyDown(first, { key: 'ArrowRight' });
    const moved = screen.getByRole('tab', { selected: true });
    expect(moved.textContent).toContain('투찰가 받음');
    // 옮긴 탭에 **포커스가 간다**(그것이 roving tabindex 의 뜻이다).
    expect(document.activeElement).toBe(moved);

    // 첫 탭에서 왼쪽은 **마지막 탭**으로 감싼다.
    fireEvent.keyDown(screen.getByRole('tab', { name: /투찰가 받음/u }), { key: 'ArrowLeft' });
    fireEvent.keyDown(screen.getByRole('tab', { name: /관심/u }), { key: 'ArrowLeft' });
    expect(screen.getByRole('tab', { selected: true }).textContent).toContain('종료');
  });

  /**
   * **CR r2 M2R-1 의 회귀 test.** 앞 판은 포커스 의도를 state 에 두고 인라인 `ref` 콜백에서
   * 적용했다 — 콜백이 렌더마다 다시 붙으면서 값이 남아 있어 `focus()` 가 **재렌더마다** 돌았고,
   * 운영자가 포커스를 옮긴 뒤에도 부모 재렌더가 그것을 **되가져갔다**(카드 입력 중 탈취).
   */
  it('화살표로 옮긴 뒤 다른 곳으로 간 포커스를 재렌더가 되가져가지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 11, state: 'BID_RECORDED' })]);

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const rendered = render(screenTree(client));

    fireEvent.keyDown(await screen.findByRole('tab', { name: /관심/u }), { key: 'ArrowRight' });
    const movedTab = screen.getByRole('tab', { selected: true });
    expect(document.activeElement).toBe(movedTab);

    // 운영자가 포커스를 **다른 곳으로** 옮긴다(실 화면에서는 카드의 입력 칸이다).
    const elsewhere = screen.getByRole('tab', { name: /종료/u });
    elsewhere.focus();
    expect(document.activeElement).toBe(elsewhere);

    // 부모를 **여러 번 실제로 재렌더한다** — 전이 `isPending` 전환·무효화 refetch·창 포커스
    // 복귀가 만드는 자리와 같다. 여기서 포커스가 탭으로 끌려가면 안 된다.
    for (let round = 0; round < 3; round += 1) {
      rendered.rerender(screenTree(client));
      await waitFor(() => expect(document.activeElement).toBe(elsewhere));
    }
  });

  it('건이 없는 탭은 빈 얼굴을 낸다 — 가짜 행을 만들지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    renderScreen();

    await screen.findByTestId('bid-case-11');
    fireEvent.click(screen.getByRole('tab', { name: /종료/u }));

    expect(await screen.findByTestId('bid-box-empty')).toBeTruthy();
  });
});

describe('화면 D — 다음 행동 하나', () => {
  it('담기 뒤 상태는 워크시트로 보낸다 — D 가 그 전이를 부르지 않는다', async () => {
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('link', { name: '공고 워크시트' }));

    expect(await screen.findByText('워크시트 자리')).toBeTruthy();
    expect(mocked.awaitOpening).not.toHaveBeenCalled();
  });

  it('투찰 기록됨에서 개찰일시를 적어 개찰 대기로 보낸다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'BID_RECORDED' })]);
    mocked.awaitOpening.mockResolvedValue(bidCase({ state: 'AWAITING_OPENING' }));
    renderScreen();
    await openTab(/투찰함/u);

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '개찰 대기로' }));
    fireEvent.change(screen.getByLabelText('개찰 일시'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    await waitFor(() =>
      expect(mocked.awaitOpening).toHaveBeenCalledWith(11, { openingAt: new Date('2099-02-03T10:30').toISOString() }),
    );
  });

  it('개찰 결과는 고르기 전에 보내지 않는다 — 기본값이 없다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'AWAITING_OPENING', nextCheckpointKind: 'OPENING' })]);
    renderScreen();
    await openTab(/개찰 대기/u);

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '개찰 결과 기록' }));
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    await waitFor(() => expect(screen.getByTestId('action-missing')).toBeTruthy());
    expect(mocked.recordOpening).not.toHaveBeenCalled();
  });

  it('잠정 1위를 고르면 그 값을 그대로 싣는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'AWAITING_OPENING' })]);
    mocked.recordOpening.mockResolvedValue(bidCase({ state: 'OPENED_PROVISIONAL_FIRST' }));
    renderScreen();
    await openTab(/개찰 대기/u);

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '개찰 결과 기록' }));
    fireEvent.click(screen.getByRole('radio', { name: /잠정 1위/u }));
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    await waitFor(() => expect(mocked.recordOpening).toHaveBeenCalledWith(11, { provisionalFirst: true }));
  });

  it('결과는 셋 가운데 고른 것을 싣는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'UNDER_QUALIFICATION_REVIEW' })]);
    mocked.recordOutcome.mockResolvedValue(bidCase({ state: 'LOST' }));
    renderScreen();
    await openTab(/심사 중/u);

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '결과 기록' }));
    fireEvent.click(screen.getByRole('radio', { name: BID_CASE_STATE_DISPLAY.LOST.label }));
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    await waitFor(() => expect(mocked.recordOutcome).toHaveBeenCalledWith(11, { outcome: 'LOST' }));
  });

  it('잠정 1위에는 둘째 행동이 있고 종단 상태에는 버튼이 없다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, state: 'OPENED_PROVISIONAL_FIRST' }),
      bidCase({ caseId: 22, state: 'WON' }),
    ]);
    renderScreen();
    await openTab(/심사 중/u);

    const first = await screen.findByTestId('bid-case-11');
    expect(within(first).getByRole('button', { name: '적격심사 중으로' })).toBeTruthy();
    expect(within(first).getByRole('button', { name: '결과 기록' })).toBeTruthy();

    await openTab(/종료/u);
    const won = await screen.findByTestId('bid-case-22');
    expect(within(won).queryAllByRole('button')).toStrictEqual([]);
  });

  it('나중에는 되돌림을 부른다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'DEFERRED', nextCheckpointKind: 'REMINDER' })]);
    mocked.resumeCase.mockResolvedValue(bidCase({ state: 'WATCHING' }));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '다시 보기' }));

    await waitFor(() => expect(mocked.resumeCase).toHaveBeenCalledWith(11));
  });
});

describe('화면 D — 세 얼굴과 쓰기 실패', () => {
  it('건이 하나도 없으면 빈 얼굴이다', async () => {
    mocked.listBidCases.mockResolvedValue([]);
    renderScreen();

    expect(await screen.findByTestId('bid-box-empty')).toBeTruthy();
  });

  it('읽기가 실패하면 오류 얼굴이고 다시 시도가 있다', async () => {
    mocked.listBidCases.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    expect(await screen.findByTestId('bid-box-error')).toBeTruthy();
  });

  it('읽기가 401 이면 로그인 길을 낸다 — 재시도 얼굴이 아니다', async () => {
    mocked.listBidCases.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    expect(await screen.findByTestId('signed-out')).toBeTruthy();
  });

  it('쓰기가 실패하면 성공 토스트가 없고 목록을 다시 읽는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'DEFERRED' })]);
    mocked.resumeCase.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '다시 보기' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.queryByTestId('toast')).toBeNull();
    expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(1);
  });

  /**
   * 쓰기 401 은 **일시 장애와 다른 얼굴**이다 — 재시도로 풀리지 않는다. 7E-1 의 관례대로
   * `WriteFailureFace` 안에서 로그인 길을 낸다(읽기 401 은 화면 전체가 `SignedOutFace` 다).
   */
  it('쓰기가 401 이면 재시도가 아니라 로그인 길을 낸다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'DEFERRED' })]);
    mocked.resumeCase.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByRole('button', { name: '다시 보기' }));

    const failure = await screen.findByTestId('write-failure');
    expect(within(failure).getByRole('link', { name: /로그인/u })).toBeTruthy();
    expect(within(failure).queryByRole('button', { name: '다시 시도' })).toBeNull();
  });
});
