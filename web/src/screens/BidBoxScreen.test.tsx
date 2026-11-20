// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { absenceTextOf } from '../display/absence';
import { ACTION_OPERATION, BID_BOX_TABS, STATE_TAB, tabOf, type BidBoxActionKind } from '../display/bidBoxDisplay';
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
    // M14/14E — 공고 읽기 투영. 이 고정물은 그 축을 모델링하지 않으므로
    // 「공고 행이 없다」를 둔다(새 문면을 들이지 않는다). 카드가 그 칸을
    // 쓰기 시작하면 그 slice 가 값을 세운다.
    noticeKnown: false,
    noticeTitle: null,
    demandAgencyName: null,
    noticeAgencyName: null,
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

function renderScreen(entry = '/bids') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/bids', element: <BidBoxScreen /> },
      { path: '/notices/:noticeNumber/:noticeRound', element: <p>워크시트 자리</p> },
    ],
    { initialEntries: [entry] },
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

/** 결과 조회의 건 하나 — 이 화면이 읽는 칸은 `caseId` 와 투찰 금액뿐이다. */
function myBidCase(overrides: Partial<queries.MyBidCase> = {}): queries.MyBidCase {
  return {
    caseId: 11,
    noticeNumber: 'SYN-7E2-CASE-1',
    noticeRound: '000',
    recordedState: 'BID_RECORDED',
    submittedAmountWon: 1_350_000_000,
    submittedAmountCurrency: 'KRW',
    submittedAmountVatTreatment: 'INCLUSIVE',
    submittedAmountOrigin: 'OPERATOR_ENTERED',
    chosenOptionRef: null,
    submittedAt: '2026-10-31T03:20:31.073Z',
    confirmedAt: '2026-10-31T03:20:31.082Z',
    openingState: 'NOT_OPENED_YET',
    confirmedAwardAmountWon: null,
    confirmedAwardAmountCurrency: null,
    confirmedAwardAmountBasis: null,
    confirmedAwardAmountVatTreatment: null,
    confirmedAwardAmountProvenance: null,
    reconciledOutcome: 'UNDECIDABLE',
    reconciliationBlocker: 'NOT_OPENED_YET',
    outcomeAgreement: 'NOT_COMPARABLE',
    gapState: 'AWARD_ABSENT',
    gapWon: null,
    gapCurrency: null,
    ...overrides,
  };
}

/** 결과 조회의 빈 답 — 카드의 「내 투찰」은 **그 응답**에서만 온다(M14/14D). */
function emptyMyBidResults(): queries.MyBidResults {
  return {
    cases: [],
    aggregate: {
      sampleSize: 0,
      recordedRateState: 'ABSENT_NO_RECORDED_BIDS',
      recordedAwardRate: null,
      recordedWonCount: 0,
      recordedLostCount: 0,
      recordedDenominator: 0,
      recordedFailedOrRebidCount: 0,
      recordedUndecidedCount: 0,
      reconciledRateState: 'ABSENT_NO_RECORDED_BIDS',
      reconciledAwardRate: null,
      reconciledWonCount: 0,
      reconciledLostCount: 0,
      reconciledDenominator: 0,
      reconciledFailedOrRebidCount: 0,
      reconciledUndecidedCount: 0,
      reconciliationBlockers: [],
    },
  };
}

beforeEach(() => {
  vi.resetAllMocks();
  mocked.readSession.mockResolvedValue({ csrf: 'csrf-value', expiresAt: '2099-01-01T00:00:00Z' });
  mocked.listBidCases.mockResolvedValue([bidCase()]);
  mocked.readMyBidResults.mockResolvedValue(emptyMyBidResults());
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

  /**
   * 보이는 글자는 **KST 표기**이고(M9 D-1 — 앞 판은 응답의 ISO 원문이었다) 기계 가독 `<time>`
   * 은 원문을 그대로 든다. 서식은 보이는 글자의 일이고 기계 칸의 일이 아니다.
   */
  it('다음 확인 시점 시각을 KST 표기로 내고 기계 칸에 원문을 든다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ nextCheckpointAt: '2099-01-03T00:00:00Z' })]);
    renderScreen();

    const slot = await screen.findByTestId('checkpoint-at-11');
    expect(slot.textContent).toBe('2099-01-03 09:00');
    expect(slot.textContent, 'ISO 원문이 화면에 남았다').not.toContain('2099-01-03T00:00:00Z');
    // testId 가 `<time>` 자신에 붙는다(r1 cr LOW — 화면 E 와 같은 꼴). 래퍼를 찾지 않는다.
    expect(slot.getAttribute('dateTime')).toBe('2099-01-03T00:00:00Z');
  });
});

describe('화면 D — 탭은 API 상태의 묶음이다', () => {
  it('탭이 그 묶음의 건만 보인다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, state: 'WATCHING' }),
      bidCase({ caseId: 22, state: 'BID_RECORDED' }),
    ]);
    renderScreen();

    // 처음은 「전체」라 둘 다 선다(M14/14D).
    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('bid-case-22')).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: /관심/u }));

    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('bid-case-22')).toBeNull();

    fireEvent.click(screen.getByRole('tab', { name: /투찰함/u }));

    await screen.findByTestId('bid-case-22');
    expect(screen.queryByTestId('bid-case-11')).toBeNull();
  });

  /**
   * **「전체」는 상태 탭이 아니다**(M14/14D). 그 고르기에서는 상태와 무관하게 전부 서고, 그
   * 수가 상태 탭 수의 합과 같다 — 겹치는 묶음이 아니라는 뜻이다.
   */
  it('「전체」에서는 상태가 다른 건이 함께 서고 수가 상태 탭의 합이다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, state: 'WATCHING' }),
      bidCase({ caseId: 22, state: 'BID_RECORDED' }),
      bidCase({ caseId: 33, state: 'WON' }),
    ]);
    renderScreen();

    expect(await screen.findByTestId('bid-case-11')).toBeTruthy();
    expect(screen.getByTestId('bid-case-22')).toBeTruthy();
    // 종단 건도 「전체」에는 선다 — 사이드바 배지와 다른 축이다.
    expect(screen.getByTestId('bid-case-33')).toBeTruthy();

    const sum = BID_BOX_TABS.map((one) => {
      const text = screen.getByRole('tab', { name: new RegExp(`^${one.label} `, 'u') }).textContent ?? '';
      return Number(text.replace(/[^0-9]/gu, ''));
    }).reduce((left, right) => left + right, 0);

    expect(sum).toBe(3);
    expect(screen.getByRole('tab', { name: /^전체 /u }).textContent).toContain('3');
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

    // 줄의 첫 자리는 「전체」다(M14/14D) — 화살표의 출발도 거기다.
    const first = await screen.findByRole('tab', { name: /^전체 /u });
    fireEvent.keyDown(first, { key: 'ArrowRight' });
    const moved = screen.getByRole('tab', { selected: true });
    expect(moved.textContent).toContain('관심');
    // 옮긴 탭에 **포커스가 간다**(그것이 roving tabindex 의 뜻이다).
    expect(document.activeElement).toBe(moved);

    // 첫 탭에서 왼쪽은 **마지막 탭**으로 감싼다.
    fireEvent.keyDown(screen.getByRole('tab', { name: /관심/u }), { key: 'ArrowLeft' });
    fireEvent.keyDown(screen.getByRole('tab', { name: /^전체 /u }), { key: 'ArrowLeft' });
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

/**
 * M7/7E-3a r1 M-2 — 화면 E 의 「다음 행동」이 **그 건이 보이는 탭**으로 보낸다. 받는 쪽이
 * 여기다: 첫 탭을 URL 이 정할 수 있어야 그 링크가 뜻을 가진다.
 */
describe('첫 탭을 URL 이 정한다 (r1 M-2)', () => {
  it('`?tab=` 이 가리키는 탭으로 열리고 그 묶음의 건이 보인다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 77, state: 'AWAITING_OPENING' })]);
    renderScreen('/bids?tab=awaiting-opening');

    expect(await screen.findByTestId('bid-case-77')).toBeTruthy();
    expect(screen.getByRole('tab', { selected: true }).textContent ?? '').toContain('개찰 대기');
  });

  /**
   * **기본이 「전체」다**(M14/14D · 와이어프레임의 선 탭). 앞 판의 기본은 첫 상태 탭(관심)이라
   * 담은 건 대부분이 첫 화면에 없었다.
   */
  it('칸이 없으면 「전체」다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase()]);
    renderScreen();

    expect(await screen.findByRole('tab', { selected: true })).toBeTruthy();
    expect(screen.getByRole('tab', { selected: true }).textContent ?? '').toContain('전체');
  });

  /** 바깥에서 온 문자열에 탭을 **지어내지 않는다** — 모르는 값은 기본으로 떨어진다. */
  it('아는 탭이 아니면 기본으로 떨어진다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase()]);
    renderScreen('/bids?tab=존재하지-않는-탭');

    expect(await screen.findByRole('tab', { selected: true })).toBeTruthy();
    expect(screen.getByRole('tab', { selected: true }).textContent ?? '').toContain('전체');
  });

  /** 상태 이름은 고르기가 아니다 — URL 에 그것이 와도 탭을 지어내지 않는다. */
  it('상태 이름을 보내도 기본으로 떨어진다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase()]);
    renderScreen('/bids?tab=WATCHING');

    expect(screen.getByRole('tab', { selected: true }).textContent ?? '').toContain('전체');
  });

  it('URL 로 연 뒤에도 운영자의 클릭이 탭을 옮긴다 — URL 이 다시 덮지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 77, state: 'AWAITING_OPENING' })]);
    renderScreen('/bids?tab=awaiting-opening');
    await screen.findByTestId('bid-case-77');

    fireEvent.click(screen.getByRole('tab', { name: /관심/u }));

    expect(screen.getByRole('tab', { selected: true }).textContent ?? '').toContain('관심');
  });
});

/* ── 내려놓기의 배선(M13/13D) ─────────────────────────────────────────────────
   카드가 표를 따르는지는 짝 test(`BidBoxCard.test.tsx`)가 상태 전수로 든다. 여기서 재는 것은
   **화면이 그 요청을 어떻게 보내고 무엇을 되돌릴 수 있게 하는가**다. */

describe('화면 D — 건을 내려놓는다', () => {
  it('「나중에」는 지역 시각을 ISO 로 바꿔 싣는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.deferCase.mockResolvedValue(bidCase({ state: 'DEFERRED', nextCheckpointKind: 'REMINDER' }));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-DEFER-11'));
    fireEvent.change(screen.getByLabelText('리마인드 시각'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() =>
      expect(mocked.deferCase).toHaveBeenCalledWith(11, {
        remindAt: new Date('2099-02-03T10:30').toISOString(),
      }),
    );
  });

  /**
   * 실행 취소는 **되돌림 전이가 있는 유일한 쌍**에만 선다(D-7E-5). 되돌릴 건의 식별자는 **서버가
   * 준 응답**에서 오므로, 응답이 다른 건을 말하면 그 건이 되돌려진다 — 그것을 여기서 잠근다.
   */
  it('「나중에」 뒤 실행 취소가 서버가 준 건을 되돌린다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.deferCase.mockResolvedValue(bidCase({ caseId: 42, state: 'DEFERRED' }));
    mocked.resumeCase.mockResolvedValue(bidCase({ caseId: 42, state: 'WATCHING' }));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-DEFER-11'));
    fireEvent.change(screen.getByLabelText('리마인드 시각'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByTestId('action-send'));

    const toast = await screen.findByTestId('toast');
    fireEvent.click(within(toast).getByRole('button', { name: '실행 취소' }));

    await waitFor(() => expect(mocked.resumeCase).toHaveBeenCalledWith(42));
  });

  /** 종단 전이에 실행 취소를 두면 그 버튼이 거짓말이 된다 — 그 자리는 카드의 확인이 든다. */
  it('「건너뜀」 뒤에는 실행 취소가 없다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.skipCase.mockResolvedValue(bidCase({ state: 'SKIPPED' }));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-SKIP-11'));
    fireEvent.change(screen.getByLabelText('건너뜀 사유'), { target: { value: '예산이 맞지 않는다' } });
    fireEvent.click(screen.getByTestId('action-send'));
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() => expect(mocked.skipCase).toHaveBeenCalledWith(11, { reason: '예산이 맞지 않는다' }));
    const toast = await screen.findByTestId('toast');
    expect(within(toast).queryByRole('button', { name: '실행 취소' })).toBeNull();
  });

  /** 쓰기가 실패하면 토스트를 **걷는다** — 되돌릴 것이 없는데 실행 취소가 남으면 거짓말이다. */
  it('「나중에」가 실패하면 실행 취소가 남지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.deferCase.mockRejectedValue(new ApiError(409, 'INVALID_TRANSITION'));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-DEFER-11'));
    fireEvent.change(screen.getByLabelText('리마인드 시각'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() => expect(screen.getByTestId('write-failure')).toBeTruthy());
    expect(screen.queryByTestId('toast')).toBeNull();
    expect(mocked.resumeCase).not.toHaveBeenCalled();
  });

  /** 빈 얼굴이 **다음 걸음**을 준다(항목 7) — 사유만 있고 길이 없던 자리다. */
  it('빈 탭이 담을 자리로 가는 길을 준다', async () => {
    mocked.listBidCases.mockResolvedValue([]);
    renderScreen();

    const empty = await screen.findByTestId('bid-box-empty');
    expect(within(empty).getByTestId('bid-box-empty-action').getAttribute('href')).toBe('/');
  });
});

/**
 * 서버만 공백으로 보는 제어문자 넷은 **화면이 보내고 서버가 400 으로 거부하는** 잔여다(실측 —
 * 화면 술어가 서버보다 좁은 쪽). 그 400 이 운영자에게 **사용자 말로** 보이는지 여기서 잰다.
 */
describe('화면 D — 화면이 보낸 사유를 서버가 거부할 때', () => {
  it('400 이 사용자 말의 쓰기 실패 얼굴로 보이고 서버 코드가 새지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.skipCase.mockRejectedValue(new ApiError(400, 'INVALID_REQUEST'));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-SKIP-11'));
    // 서버만 공백으로 보는 글자 하나 — 화면 술어는 이것을 글자로 본다(그래서 보낸다).
    fireEvent.change(screen.getByLabelText('건너뜀 사유'), { target: { value: String.fromCodePoint(0x1f) } });
    fireEvent.click(screen.getByTestId('action-send'));
    fireEvent.click(screen.getByTestId('action-send'));

    const face = await screen.findByTestId('write-failure');
    expect(mocked.skipCase, '화면이 그 값을 보냈다 — 술어가 서버보다 좁은 쪽이다').toHaveBeenCalled();
    expect(face.textContent ?? '').toContain('보내지 못했습니다');
    // 서버 메시지·코드를 그대로 내보이지 않는다(D-7E-6).
    expect(face.textContent ?? '', '서버 코드가 화면에 샜다').not.toContain('INVALID_REQUEST');
    // **무엇이 잘못됐는지는 말하지 않는다** — 그 잔여를 알려진 제한에 적었다.
    expect(within(face).getByRole('button', { name: '다시 시도' })).toBeTruthy();
  });
});

/**
 * 쓰기가 **보류 중일 때** 버튼이 살아 있으면 연타가 그대로 요청이 된다(cr MEDIUM). 이 slice
 * 전에는 화면이 `DEFERRED` 를 만드는 길이 없어 그 버튼이 사실상 닿지 않았고, 13D 가 그것을 켠다.
 */
describe('화면 D — 보류 중에는 다시 보내지 않는다', () => {
  it('「다시 보기」 연타가 요청을 늘리지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'DEFERRED', nextCheckpointKind: 'REMINDER' })]);
    // 보류를 **해소되지 않는 약속**으로 고정한다 — 시간을 흉내 내지 않는다.
    mocked.resumeCase.mockReturnValue(new Promise<queries.BidCase>(() => {}));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    const again = within(card).getByRole('button', { name: '다시 보기' });
    fireEvent.click(again);
    fireEvent.click(again);
    fireEvent.click(again);

    await waitFor(() => expect(mocked.resumeCase).toHaveBeenCalledTimes(1));
  });

  /**
   * 실패가 **성공한 쓰기의 실행 취소를 지우지 않는다**. 되돌릴 수 있는데 되돌릴 길이 화면에서
   * 사라지는 것이 가장 비싼 손해다 — 실패 얼굴은 따로 선다.
   */
  it('뒤이은 쓰기가 실패해도 실행 취소 토스트가 남는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.deferCase.mockResolvedValue(bidCase({ state: 'DEFERRED' }));
    mocked.skipCase.mockRejectedValue(new ApiError(409, 'INVALID_TRANSITION'));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-DEFER-11'));
    fireEvent.change(screen.getByLabelText('리마인드 시각'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByTestId('action-send'));
    const toast = await screen.findByTestId('toast');
    expect(within(toast).getByRole('button', { name: '실행 취소' })).toBeTruthy();

    fireEvent.click(within(card).getByTestId('exit-SKIP-11'));
    fireEvent.change(screen.getByLabelText('건너뜀 사유'), { target: { value: '예산이 맞지 않는다' } });
    fireEvent.click(screen.getByTestId('action-send'));
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() => expect(screen.getByTestId('write-failure')).toBeTruthy());
    expect(
      within(screen.getByTestId('toast')).getByRole('button', { name: '실행 취소' }),
      '성공한 쓰기의 되돌릴 길이 실패와 함께 사라졌다',
    ).toBeTruthy();
  });
});

/**
 * 가드가 **실행 취소를 먹지 않는다**(cr r1 HIGH). 토스트는 `onSuccess` 맨 앞에서 뜨는데 그
 * 함수는 재조회를 기다리며 계속 돈다 — 그 창 안의 누름이 거절되면 **아무것도 가지 않은 채**
 * 토스트만 사라진다(누르면 `ToastHost` 가 닫는다).
 */
describe('화면 D — 실행 취소는 뜨는 순간부터 살아 있다', () => {
  it('재조회가 아직 안 끝난 창에서 눌러도 되돌림이 간다', async () => {
    // 첫 적재만 풀고, 무효화가 부르는 재조회는 **해소되지 않는 약속**으로 묶는다.
    mocked.listBidCases.mockResolvedValueOnce([bidCase({ state: 'WATCHING' })]);
    mocked.listBidCases.mockReturnValue(new Promise<readonly queries.BidCase[]>(() => {}));
    mocked.deferCase.mockResolvedValue(bidCase({ caseId: 42, state: 'DEFERRED' }));
    mocked.resumeCase.mockResolvedValue(bidCase({ caseId: 42, state: 'WATCHING' }));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-DEFER-11'));
    fireEvent.change(screen.getByLabelText('리마인드 시각'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByTestId('action-send'));

    const toast = await screen.findByTestId('toast');
    fireEvent.click(within(toast).getByRole('button', { name: '실행 취소' }));

    await waitFor(() => expect(mocked.resumeCase).toHaveBeenCalledWith(42));
  });
});

/** 쓰기 입구가 **하나**인가 — 실패 얼굴의 다시 시도도 같은 가드를 지나야 한다(cr r1 MEDIUM). */
describe('화면 D — 다시 시도도 같은 입구를 지난다', () => {
  it('다시 시도 연타가 요청을 하나만 더 낸다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    // **거절 약속을 미리 만들지 않는다**: `mockReturnValueOnce(Promise.reject(…))` 는 처리자가
    // 붙기 전에 거절이 서서 vitest 가 「전부 통과인데 exit 1」로 끝난다(실측 — 등록된 서명).
    mocked.deferCase.mockRejectedValueOnce(new ApiError(409, 'INVALID_TRANSITION'));
    mocked.deferCase.mockReturnValue(new Promise<queries.BidCase>(() => {}));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-DEFER-11'));
    fireEvent.change(screen.getByLabelText('리마인드 시각'), { target: { value: '2099-02-03T10:30' } });
    fireEvent.click(screen.getByTestId('action-send'));

    const face = await screen.findByTestId('write-failure');
    expect(mocked.deferCase).toHaveBeenCalledTimes(1);
    const retry = within(face).getByRole('button', { name: '다시 시도' });
    fireEvent.click(retry);
    fireEvent.click(retry);
    fireEvent.click(retry);

    await waitFor(() => expect(mocked.deferCase).toHaveBeenCalledTimes(2));
  });
});

/**
 * **실행 취소는 「나중에」에만 선다**(D-7E-5 · v M-2). 지금까지 그 축은 건너뜀 하나로만 재서,
 * 조건을 `kind === 'DEFER'` 에서 `kind !== 'SKIP'` 으로 바꾸는 변이가 **전건을 초록으로** 지났다.
 *
 * 보낼 수 있는 종류 **전수**를 돌며 「나중에가 아니면 실행 취소가 없다」를 단언한다. 모집단은
 * operation 표에서 유도하므로(화면 이동은 전이가 아니라 제외) 종류가 늘면 이 표가 모를 때 붉는다.
 */
describe('실행 취소는 되돌림 전이가 있는 하나에만 선다 — 보낼 수 있는 종류 전수', () => {
  interface Drive {
    readonly kind: Exclude<BidBoxActionKind, 'OPEN_WORKSHEET' | 'DEFER'>;
    readonly state: BidCaseState;
    readonly openName: RegExp;
    readonly fill?: () => void;
    readonly confirms?: boolean;
  }

  const fillDateTime = (label: string) => (): void => {
    fireEvent.change(screen.getByLabelText(label), { target: { value: '2099-02-03T10:30' } });
  };
  const pickFirstChoice = (): void => {
    fireEvent.click(screen.getAllByRole('radio')[0]!);
  };

  const DRIVES: readonly Drive[] = [
    { kind: 'RESUME', state: 'DEFERRED', openName: /다시 보기/u },
    {
      kind: 'SKIP',
      state: 'WATCHING',
      openName: /건너뜀/u,
      fill: () => fireEvent.change(screen.getByLabelText('건너뜀 사유'), { target: { value: '예산이 맞지 않는다' } }),
      confirms: true,
    },
    { kind: 'AWAIT_OPENING', state: 'BID_RECORDED', openName: /개찰 대기로/u, fill: fillDateTime('개찰 일시') },
    { kind: 'RECORD_OPENING', state: 'AWAITING_OPENING', openName: /개찰 결과 기록/u, fill: pickFirstChoice },
    {
      kind: 'ENTER_QUALIFICATION_REVIEW',
      state: 'OPENED_PROVISIONAL_FIRST',
      openName: /적격심사 중으로/u,
      fill: fillDateTime('서류 기한'),
    },
    { kind: 'RECORD_OUTCOME', state: 'OPENED_OUT_OF_RANK', openName: /결과 기록/u, fill: pickFirstChoice },
  ];

  /** 보낼 수 있는 종류 전수 — 화면 이동은 서버를 부르지 않으므로 빠진다. */
  const SENDABLE = (Object.keys(ACTION_OPERATION) as BidBoxActionKind[]).filter(
    (kind) => ACTION_OPERATION[kind] !== null && kind !== 'DEFER',
  );

  it('이 표가 보낼 수 있는 종류 전수를 덮는다 — 빠지면 루프가 공허해진다', () => {
    expect(SENDABLE.length, '보낼 수 있는 종류가 없다').toBeGreaterThan(0);
    expect([...DRIVES.map((one) => one.kind)].sort()).toStrictEqual([...SENDABLE].sort());
  });

  for (const drive of DRIVES) {
    it(`${drive.kind} 의 성공 토스트에는 실행 취소가 없다`, async () => {
      mocked.listBidCases.mockResolvedValue([bidCase({ state: drive.state })]);
      for (const fn of [
        mocked.resumeCase,
        mocked.skipCase,
        mocked.awaitOpening,
        mocked.recordOpening,
        mocked.enterQualificationReview,
        mocked.recordOutcome,
      ]) {
        fn.mockResolvedValue(bidCase({ state: drive.state }));
      }
      renderScreen(`/bids?tab=${tabOf(drive.state)}`);

      const card = await screen.findByTestId('bid-case-11');
      fireEvent.click(within(card).getByRole('button', { name: drive.openName }));
      drive.fill?.();
      if (drive.fill !== undefined) {
        fireEvent.click(screen.getByTestId('action-send'));
      }
      if (drive.confirms === true) {
        fireEvent.click(screen.getByTestId('action-send'));
      }

      const toast = await screen.findByTestId('toast');
      expect(
        within(toast).queryByRole('button', { name: '실행 취소' }),
        `${drive.kind}: 되돌림 전이가 없는데 실행 취소가 섰다`,
      ).toBeNull();
    });
  }
});

/**
 * 사유는 **적힌 그대로** 간다(v L-1). 다듬으면 기록에 남는 말이 운영자가 본 말과 달라지고,
 * 서버가 다듬지 않으므로(원문을 그대로 담는다) 상한 근처에서 화면과 서버의 셈이 갈린다.
 */
describe('건너뜀 사유는 다듬지 않고 보낸다', () => {
  it('앞뒤 공백이 붙은 사유가 원문 그대로 실린다', async () => {
    const typed = '  예산이 맞지 않는다  ';
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    mocked.skipCase.mockResolvedValue(bidCase({ state: 'SKIPPED' }));
    renderScreen();

    const card = await screen.findByTestId('bid-case-11');
    fireEvent.click(within(card).getByTestId('exit-SKIP-11'));
    fireEvent.change(screen.getByLabelText('건너뜀 사유'), { target: { value: typed } });
    fireEvent.click(screen.getByTestId('action-send'));
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() => expect(mocked.skipCase).toHaveBeenCalledWith(11, { reason: typed }));
  });
});

/* ── M14/14D — 와이어프레임 D 의 머리·레일·카드 ─────────────────────────────── */

describe('화면 D — 카드의 머리는 공고명이다 (14E 투영)', () => {
  it('공고명이 머리에 서고 수요기관이 그 아래 줄이다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({
        caseId: 11,
        noticeKnown: true,
        noticeTitle: '합성 공고명',
        demandAgencyName: '합성 수요기관',
      }),
    ]);
    renderScreen();

    expect((await screen.findByTestId('notice-title-11')).textContent).toContain('합성 공고명');
    expect(screen.getByTestId('demand-agency-11').textContent).toContain('합성 수요기관');
  });

  /**
   * **「공고 행이 없다」와 「행은 있고 제목이 비었다」는 다른 사실이다**(14E 계약). 앞쪽은 서버가
   * 적극적으로 답한 사실이라 자기 문면을 가지고, 뒤쪽은 부재 어휘다. 한 값으로 접으면 그 둘이
   * 같은 얼굴이 된다.
   */
  it('공고 행이 없을 때와 제목만 빈 때가 다른 문면이다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, noticeKnown: false, noticeTitle: null }),
      bidCase({ caseId: 22, noticeKnown: true, noticeTitle: null }),
    ]);
    renderScreen();

    const rowAbsent = (await screen.findByTestId('notice-title-11')).textContent ?? '';
    const titleAbsent = screen.getByTestId('notice-title-22').textContent ?? '';

    expect(rowAbsent.length, '공고 행 부재가 빈 글자다').toBeGreaterThan(0);
    expect(titleAbsent.length, '제목 부재가 빈 글자다').toBeGreaterThan(0);
    expect(rowAbsent, '두 사실이 한 문면으로 접혔다').not.toBe(titleAbsent);
  });

  it('수요기관이 없으면 그 자리가 아예 없다 — 부재 문면을 그리지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 11, demandAgencyName: null })]);
    renderScreen();

    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('demand-agency-11')).toBeNull();
  });

  it('내 투찰은 결과 조회가 그 건을 낼 때만 선다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, state: 'BID_RECORDED' }),
      bidCase({ caseId: 22, state: 'WATCHING' }),
    ]);
    const results = emptyMyBidResults();
    mocked.readMyBidResults.mockResolvedValue({
      ...results,
      cases: [myBidCase({ caseId: 11, submittedAmountWon: 128400000 })],
    });
    renderScreen();

    await waitFor(() => expect(screen.getByTestId('submitted-amount-11').textContent).toContain('128,400,000'));
    // 투찰 기록이 없는 건에는 **자리가 없다** — 0 이나 부재 문면을 지어내지 않는다.
    expect(screen.queryByTestId('submitted-amount-22')).toBeNull();
  });

  /** 결과 조회가 한 건에 행을 여럿 내면 **고르지 않는다** — 모르는 것을 금액으로 보이지 않는다. */
  it('한 건에 결과 행이 둘이면 내 투찰을 그리지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 11, state: 'BID_RECORDED' })]);
    const results = emptyMyBidResults();
    mocked.readMyBidResults.mockResolvedValue({
      ...results,
      cases: [
        myBidCase({ caseId: 11, submittedAmountWon: 100 }),
        myBidCase({ caseId: 11, submittedAmountWon: 200 }),
      ],
    });
    renderScreen();

    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('submitted-amount-11')).toBeNull();
  });
});

describe('화면 D — 레일 둘', () => {
  it('일정 줄의 사건 낱말이 전부 `nextCheckpointKind` 표의 라벨이다', async () => {
    const kinds = Object.keys(NEXT_CHECKPOINT_DISPLAY).filter((one) => one !== 'NONE');
    mocked.listBidCases.mockResolvedValue(
      kinds.map((kind, at) =>
        bidCase({
          caseId: 100 + at,
          nextCheckpointKind: kind as BidCase['nextCheckpointKind'],
          nextCheckpointAt: `2099-01-0${at + 1}T00:00:00Z`,
        }),
      ),
    );
    renderScreen();

    await screen.findByTestId('schedule-kind-100');
    const rail = screen.getByTestId('bid-box-schedule');
    const allowed = new Set(Object.values(NEXT_CHECKPOINT_DISPLAY).map((one) => one.label));
    const drawn = kinds.map((_, at) => within(rail).getByTestId(`schedule-kind-${100 + at}`).textContent ?? '');

    expect(drawn.length, '줄이 없으면 이 단언이 공허하다').toBeGreaterThan(0);
    for (const one of drawn) {
      expect(allowed, `${one} 은 열거 밖의 낱말이다`).toContain(one);
    }
  });

  it('확인 시점이 없는 건은 일정 줄을 만들지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, nextCheckpointKind: 'NONE', nextCheckpointAt: null }),
    ]);
    renderScreen();

    await screen.findByTestId('bid-box-schedule');
    expect(screen.queryByTestId('schedule-11')).toBeNull();
  });

  /**
   * **줄이 어느 건인지 말한다**(M14/14F · v M-1). 앞 판은 사건 낱말과 시각뿐이라 건이 둘
   * 이상이면 같은 꼴의 줄이 나란히 서서 **서로 구별되지 않았다**. 레일은 보는 탭과 무관하게
   * 입찰함 전체를 보므로(바로 아래 test 가 그 사실을 잠근다) 「종료」 탭을 보는 중에 **관심 탭
   * 건의** 마감 시각이 서기도 한다 — 그때 그 줄이 어느 건인지 말하지 않으면 운영자는 보고 있는
   * 건의 일정으로 읽는다.
   */
  it('일정 줄이 건을 부른다 — 둘이면 서로 다른 이름이다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, noticeKnown: true, noticeTitle: '합성 공고 가' }),
      bidCase({ caseId: 22, noticeKnown: true, noticeTitle: '합성 공고 나' }),
    ]);
    renderScreen();

    const rail = screen.getByTestId('bid-box-schedule');
    expect((await within(rail).findByTestId('schedule-title-11')).textContent).toBe('합성 공고 가');
    expect(within(rail).getByTestId('schedule-title-22').textContent).toBe('합성 공고 나');
  });

  /** 제목이 없는 두 꼴이 레일에서도 **다른 사실**이다(카드와 같은 규율). */
  it('일정 줄에서도 공고 행 부재와 제목 부재가 다른 문면이다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, noticeKnown: false, noticeTitle: null }),
      bidCase({ caseId: 22, noticeKnown: true, noticeTitle: null }),
    ]);
    renderScreen();

    const rail = screen.getByTestId('bid-box-schedule');
    const rowAbsent = (await within(rail).findByTestId('schedule-title-11')).textContent ?? '';
    const titleAbsent = within(rail).getByTestId('schedule-title-22').textContent ?? '';

    expect(rowAbsent.length, '공고 행 부재가 빈 글자다').toBeGreaterThan(0);
    expect(titleAbsent, '제목 부재가 부재 어휘가 아니다').toBe(absenceTextOf('notSent'));
    expect(rowAbsent, '두 사실이 한 문면으로 접혔다').not.toBe(titleAbsent);
  });

  /** 레일은 **묶음이 아니라 입찰함 전체**를 본다 — 탭을 옮겨도 같은 사실이다. */
  it('탭을 옮겨도 일정 줄이 그대로다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, state: 'WATCHING' }),
      bidCase({ caseId: 22, state: 'BID_RECORDED' }),
    ]);
    renderScreen();

    await screen.findByTestId('schedule-11');
    expect(screen.getByTestId('schedule-22')).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: /관심/u }));

    expect(screen.getByTestId('schedule-11')).toBeTruthy();
    expect(screen.getByTestId('schedule-22'), '탭을 옮기자 일정이 줄었다').toBeTruthy();
  });

  it('「상태 뜻」의 문면이 상태 표의 것이다 — 레일이 자기 글을 들지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 11, state: 'OPENED_PROVISIONAL_FIRST' })]);
    renderScreen();

    const meaning = await screen.findByTestId('state-meaning-OPENED_PROVISIONAL_FIRST');
    const display: { readonly why?: string } = BID_CASE_STATE_DISPLAY.OPENED_PROVISIONAL_FIRST;

    expect(display.why, '상태 표에 뜻이 없으면 이 단언이 공허하다').toBeDefined();
    expect(meaning.textContent).toContain(display.why);
  });

  it('보이지 않는 상태의 뜻은 서지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 11, state: 'WATCHING' })]);
    renderScreen();

    await screen.findByTestId('state-meaning-WATCHING');
    expect(screen.queryByTestId('state-meaning-SKIPPED')).toBeNull();
  });

  /** 「전체」가 배지와 다른 수를 내는 까닭을 레일이 **한 줄로** 말한다(팀장 판정 2026-11-20). */
  it('「전체」와 사이드바 배지가 다른 수라는 것을 레일이 말한다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ caseId: 11, state: 'WON' })]);
    renderScreen();

    expect((await screen.findByTestId('bid-box-state-meanings')).textContent).toContain('종료한 건까지');
  });
});

describe('화면 D — 페이퍼 따로 보기', () => {
  it('처음은 실투찰이고 누르면 페이퍼를 묻는다 — 한 목록에 섞지 않는다', async () => {
    renderScreen();
    await screen.findByTestId('bid-case-11');
    expect(mocked.listBidCases).toHaveBeenCalledWith('REAL');

    fireEvent.click(screen.getByTestId('bid-kind-switch'));

    await waitFor(() => expect(mocked.listBidCases).toHaveBeenCalledWith('PAPER'));
    // 두 종류가 **같은 호출**에 섞이지 않는다 — 계약이 `kind` 를 필수로 받는다.
    for (const call of mocked.listBidCases.mock.calls) {
      expect(call.length).toBe(1);
    }
  });

  it('페이퍼를 보는 동안 머리가 그 사실을 말한다 — 빈 목록과 헷갈리지 않게', async () => {
    renderScreen();
    await screen.findByTestId('bid-case-11');

    fireEvent.click(screen.getByTestId('bid-kind-switch'));

    await waitFor(() =>
      expect(screen.getByTestId('bid-box-header-summary').textContent).toContain('페이퍼(가상)'),
    );
  });

  it('되돌아오는 버튼이 서고 다시 실투찰을 묻는다', async () => {
    renderScreen();
    await screen.findByTestId('bid-case-11');

    fireEvent.click(screen.getByTestId('bid-kind-switch'));
    await waitFor(() => expect(mocked.listBidCases).toHaveBeenCalledWith('PAPER'));

    fireEvent.click(screen.getByTestId('bid-kind-switch'));

    await waitFor(() => expect(screen.getByTestId('bid-box-header-summary').textContent).not.toContain('페이퍼'));
  });
});

describe('화면 D — 수정 라운드 1 의 자리들', () => {
  it('공백뿐인 수요기관은 자리를 만들지 않는다 — 보이지 않는 틈을 남기지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 11, demandAgencyName: '   ' }),
      bidCase({ caseId: 22, demandAgencyName: '합성 수요기관' }),
    ]);
    renderScreen();

    await screen.findByTestId('bid-case-11');
    expect(screen.queryByTestId('demand-agency-11'), '공백뿐인 값이 자리를 만들었다').toBeNull();
    expect(screen.getByTestId('demand-agency-22')).toBeTruthy();
  });

  it('탭의 낭독 이름에 단위가 있다 — 보이는 글자는 수만 든다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    renderScreen();

    const tab = await screen.findByRole('tab', { name: '관심 1건' });
    expect(tab.textContent).toContain('관심 1');
  });

  it('Home·End 가 줄의 양 끝으로 간다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WATCHING' })]);
    renderScreen();

    const first = await screen.findByRole('tab', { selected: true });
    fireEvent.keyDown(first, { key: 'End' });
    expect(screen.getByRole('tab', { selected: true }).textContent).toContain('종료');

    fireEvent.keyDown(screen.getByRole('tab', { selected: true }), { key: 'Home' });
    expect(screen.getByRole('tab', { selected: true }).textContent).toContain('전체');
  });

  /** 좁은 폭에서 레일이 본문 뒤로 가므로 두 수를 화해시키는 문장이 탭 곁에도 선다. */
  it('두 수를 화해시키는 문장이 탭 곁에도 있다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WON' })]);
    renderScreen();

    expect((await screen.findByTestId('bid-box-all-count-note')).textContent).toContain('종료한 건까지');
  });
});

describe('화면 D — 두 수를 화해시키는 문장은 폭마다 하나다 (수정 라운드 2)', () => {
  it('탭 곁의 것과 레일의 것이 서로 다른 폭에서만 선다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WON' })]);
    renderScreen();

    const beside = await screen.findByTestId('bid-box-all-count-note');
    const rail = screen.getByTestId('bid-box-rail-count-note');

    // 같은 문장이고, 서로 **반대 폭**에서만 보인다 — 한 화면에 두 번 서지 않는다.
    expect(beside.textContent).toBe(rail.textContent);
    expect(beside.className, '탭 곁의 것이 넓은 폭에서도 선다').toContain('md:hidden');
    expect(rail.className, '레일의 것이 좁은 폭에서도 선다').toContain('hidden');
    expect(rail.className).toContain('md:block');
  });
});
