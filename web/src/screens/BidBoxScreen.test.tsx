// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { BID_CASE_STATE_DISPLAY, NEXT_CHECKPOINT_DISPLAY } from '../display/caseDisplay';

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
   * 라면 화면은 경고를 내지 않는다. 날 수만 내고 「지났다」는 판정을 만들지 않는다.
   */
  it('시각이 지나도 overdue 가 거짓이면 경고가 없다 — 화면이 판정하지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ nextCheckpointAt: '2000-01-01T00:00:00Z', overdue: false }),
    ]);
    renderScreen();

    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('overdue-11')).toBeNull();
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
