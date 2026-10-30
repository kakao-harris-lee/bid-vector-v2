// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { BID_CASE_STATE_DISPLAY, NEXT_CHECKPOINT_DISPLAY } from '../display/caseDisplay';
import {
  OPENING_RESULT_STATE_DISPLAY,
  PROVISIONAL_RANK_ONE_DISPLAY,
  type OpeningResultState,
} from '../display/openingResultDisplay';
import { RESULT_PENDING_SLOTS } from '../display/resultDisplay';

import { ResultsScreen } from './ResultsScreen';

vi.mock('../api/queries');

type BidCase = queries.BidCase;
type OpeningResultView = queries.OpeningResultView;

function bidCase(overrides: Partial<BidCase> = {}): BidCase {
  return {
    caseId: 51,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    bidKind: 'REAL',
    state: 'AWAITING_OPENING',
    caseVersion: 3,
    nextCheckpointKind: 'OPENING',
    nextCheckpointAt: '2099-01-03T00:00:00Z',
    overdue: false,
    reconciliationCandidate: false,
    ...overrides,
  };
}

function opening(overrides: Partial<OpeningResultView> = {}): OpeningResultView {
  return {
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    state: 'NOT_OPENED_YET',
    participantCount: null,
    actualOpeningAt: null,
    observedAt: null,
    confirmedAwardAmountWon: null,
    confirmedAwardAmountCurrency: null,
    confirmedAwardAmountBasis: null,
    confirmedAwardAmountVatTreatment: null,
    confirmedAwardAmountProvenance: null,
    provisionalRankOneState: 'NOT_OBSERVED',
    provisionalRankOneDuplicateCount: null,
    provisionalRankOneBidAmountWon: null,
    provisionalRankOneBidAmountCurrency: null,
    provisionalRankOneBidAmountVatTreatment: null,
    provisionalRankOneBidRateFraction: null,
    provisionalRankOneObservedAt: null,
    ...overrides,
  };
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/results', element: <ResultsScreen /> },
      { path: '/bids', element: <p>입찰함 자리</p> },
      { path: '/notices/:noticeNumber/:noticeRound', element: <p>워크시트 자리</p> },
    ],
    { initialEntries: ['/results'] },
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
  mocked.listBidCases.mockResolvedValue([bidCase()]);
  mocked.readOpeningResult.mockResolvedValue(opening());
});

afterEach(() => {
  cleanup();
});

describe('화면 E — 결과 단계 건만, 서버 상태 그대로', () => {
  it('실투찰 종류로 묻는다', async () => {
    renderScreen();

    await waitFor(() => expect(mocked.listBidCases).toHaveBeenCalledWith('REAL'));
  });

  it('결과 단계가 아닌 건은 보이지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase(), bidCase({ caseId: 52, state: 'WATCHING' })]);
    renderScreen();

    expect(await screen.findByTestId('result-case-51')).toBeTruthy();
    expect(screen.queryByTestId('result-case-52')).toBeNull();
  });

  it('상태 배지가 응답 상태의 문면이다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'UNDER_QUALIFICATION_REVIEW' })]);
    renderScreen();

    const badge = await screen.findByTestId('result-state-51');
    expect(badge.textContent ?? '').toContain(BID_CASE_STATE_DISPLAY.UNDER_QUALIFICATION_REVIEW.label);
  });

  /** D-7E3a-7 — 「없음」은 빈 칸이 아니라 **답**이다. */
  it('다음 확인 시점이 NONE 이면 「없음」을 답으로 그리고 시각 자리는 값 없음이다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ state: 'WON', nextCheckpointKind: 'NONE', nextCheckpointAt: null }),
    ]);
    renderScreen();

    const kind = await screen.findByTestId('result-checkpoint-kind-51');
    expect(kind.textContent ?? '').toContain(NEXT_CHECKPOINT_DISPLAY.NONE.label);
    expect(screen.getByTestId('result-checkpoint-at-51').textContent ?? '').toContain('값 없음');
  });

  it('시점이 있으면 서버가 준 값 그대로 그린다 — 남은 날을 세지 않는다', async () => {
    renderScreen();

    const at = await screen.findByTestId('result-checkpoint-at-51');
    expect(at.textContent ?? '').toContain('2099-01-03T00:00:00Z');
  });

  it('건이 없으면 빈 얼굴이고 「결과가 없다」로 접지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([]);
    renderScreen();

    expect(await screen.findByTestId('results-empty')).toBeTruthy();
  });

  it('세션이 없다는 답에는 로그인 얼굴이다 — 재시도 얼굴이 아니다', async () => {
    mocked.readSession.mockResolvedValue(null);
    mocked.listBidCases.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    expect(await screen.findByTestId('signed-out')).toBeTruthy();
  });
});

describe('개찰 결과 — 아홉 갈래를 접지 않는다 (D-7E3a-7)', () => {
  const STATES = Object.keys(OPENING_RESULT_STATE_DISPLAY) as readonly OpeningResultState[];

  it.each(STATES)('%s 가 자기 문면으로 그려진다', async (state) => {
    mocked.readOpeningResult.mockResolvedValue(opening({ state }));
    renderScreen();

    const badge = await screen.findByTestId('opening-state-51');
    expect(badge.textContent ?? '').toContain(OPENING_RESULT_STATE_DISPLAY[state].label);
  });

  it('번호와 차수 **둘 다**로 묻는다 — 차수를 기본값으로 접지 않는다', async () => {
    renderScreen();

    await waitFor(() => expect(mocked.readOpeningResult).toHaveBeenCalledWith('SYN-7E3-RESULT', '000'));
  });

  it('잠정 축의 상태도 서버 값의 문면이다', async () => {
    mocked.readOpeningResult.mockResolvedValue(opening({ provisionalRankOneState: 'RANK_DUPLICATED' }));
    renderScreen();

    const badge = await screen.findByTestId('provisional-state-51');
    expect(badge.textContent ?? '').toContain(PROVISIONAL_RANK_ONE_DISPLAY.RANK_DUPLICATED.label);
  });
});

describe('잠정은 확정 자리에 서지 않는다 (SET-02)', () => {
  const PROVISIONAL_ONLY = {
    state: 'OPENED',
    provisionalRankOneState: 'DETERMINED',
    provisionalRankOneBidAmountWon: 1_310_000_000,
    provisionalRankOneBidAmountCurrency: 'KRW',
    provisionalRankOneBidAmountVatTreatment: 'INCLUSIVE',
    provisionalRankOneBidRateFraction: '0.87745',
  } as const;

  /**
   * **이 slice 가 막는 핵심 오표시.** 잠정 1위 금액이 관측됐고 확정 낙찰가는 아직 없는 답에서,
   * 확정 칸에 그 숫자가 나타나면 운영자는 낙찰가를 안다고 믿는다.
   */
  it('잠정만 관측된 답에서 확정 낙찰가 칸에 그 숫자가 없다', async () => {
    mocked.readOpeningResult.mockResolvedValue(opening(PROVISIONAL_ONLY));
    renderScreen();

    const confirmed = (await screen.findByTestId('confirmed-award-51')).textContent ?? '';
    expect(confirmed).not.toContain('1,310,000,000');
    expect(confirmed).not.toContain('1310000000');
    expect(confirmed).toContain('값 없음');
  });

  it('그 잠정 금액은 잠정 칸에 통화·과세와 함께 선다', async () => {
    mocked.readOpeningResult.mockResolvedValue(opening(PROVISIONAL_ONLY));
    renderScreen();

    const provisional = (await screen.findByTestId('provisional-award-51')).textContent ?? '';
    expect(provisional).toContain('1,310,000,000');
    expect(provisional).toContain('KRW');
    expect(provisional).toContain('INCLUSIVE');
  });

  it('투찰율은 서버 표기 그대로다', async () => {
    mocked.readOpeningResult.mockResolvedValue(opening(PROVISIONAL_ONLY));
    renderScreen();

    const rate = await screen.findByTestId('provisional-rate-51');
    expect(rate.textContent ?? '').toContain('0.87745');
  });

  it('확정이 다 오면 다섯 칸과 함께 선다', async () => {
    mocked.readOpeningResult.mockResolvedValue(
      opening({
        state: 'OPENED',
        confirmedAwardAmountWon: 1_350_000_000,
        confirmedAwardAmountCurrency: 'KRW',
        confirmedAwardAmountBasis: 'BASE_AMOUNT',
        confirmedAwardAmountVatTreatment: 'INCLUSIVE',
        confirmedAwardAmountProvenance: 'PUBLISHED',
      }),
    );
    renderScreen();

    const confirmed = (await screen.findByTestId('confirmed-award-51')).textContent ?? '';
    expect(confirmed).toContain('1,350,000,000');
    expect(confirmed).toContain('BASE_AMOUNT');
    expect(confirmed).toContain('PUBLISHED');
  });

  /** 반쯤 온 금액은 **숫자를 그리지 않는다** — 통화·basis 없는 금액은 해석되지 않는다. */
  it('확정 칸이 반쯤 오면 숫자 대신 그 사실과 빠진 칸을 말한다', async () => {
    mocked.readOpeningResult.mockResolvedValue(
      opening({
        state: 'OPENED',
        confirmedAwardAmountWon: 1_350_000_000,
        confirmedAwardAmountCurrency: 'KRW',
        confirmedAwardAmountBasis: null,
        confirmedAwardAmountVatTreatment: 'INCLUSIVE',
        confirmedAwardAmountProvenance: 'PUBLISHED',
      }),
    );
    renderScreen();

    const confirmed = (await screen.findByTestId('confirmed-award-51')).textContent ?? '';
    expect(confirmed).not.toContain('1,350,000,000');
    expect(confirmed).toContain('confirmedAwardAmountBasis');
  });
});

describe('E 는 읽기 화면이다 (D-7E3a-8)', () => {
  /**
   * 전이는 D 하나가 든다 — 전이표의 사본이 둘이 되면 갈리고, 여정 ④⑤ 가 같은 건을 민다.
   * 이 단언은 **그 결정이 코드에 남아 있는지**를 본다.
   */
  it('결과 기록 전이를 부르는 자리가 화면에 없다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'OPENED_PROVISIONAL_FIRST' })]);
    renderScreen();
    await screen.findByTestId('result-case-51');

    for (const label of [/개찰 대기로/u, /개찰 결과 기록/u, /적격심사 중으로/u, /결과 기록/u, /보내기/u]) {
      expect(screen.queryByRole('button', { name: label }), `${String(label)} 버튼이 E 에 있다`).toBeNull();
    }
    expect(mocked.awaitOpening).not.toHaveBeenCalled();
    expect(mocked.recordOpening).not.toHaveBeenCalled();
    expect(mocked.enterQualificationReview).not.toHaveBeenCalled();
    expect(mocked.recordOutcome).not.toHaveBeenCalled();
  });

  it('다음 행동은 입찰함과 워크시트로 가는 링크 둘이다', async () => {
    renderScreen();
    const card = within(await screen.findByTestId('result-case-51'));

    expect(card.getByRole('link', { name: /입찰함/u }).getAttribute('href')).toBe('/bids');
    expect(card.getByRole('link', { name: /워크시트/u }).getAttribute('href')).toBe(
      '/notices/SYN-7E3-RESULT/000',
    );
  });
});

describe('빈 자리 (D-7E3a-3)', () => {
  it('일곱 자리가 전부 「준비 중」 또는 사유와 함께 뜨고 입력칸·버튼이 없다', async () => {
    renderScreen();
    const slots = within(await screen.findByTestId('results-pending'));

    for (const slot of RESULT_PENDING_SLOTS) {
      expect(slots.getByTestId(`pending-${slot.id}`).textContent ?? '', `${slot.id} 자리가 없다`).toContain(
        slot.label,
      );
    }
    expect(slots.queryAllByRole('button')).toStrictEqual([]);
    expect(slots.queryAllByRole('textbox')).toStrictEqual([]);
  });

  /** 집계는 **목록을 세어 짓지 않는다**(계약) — 분모가 서지 않으므로 숫자 자체가 없다. */
  it('낙찰률 자리에 숫자가 없다 — 목록을 세어 집계를 지어내지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 51, state: 'WON' }),
      bidCase({ caseId: 52, state: 'LOST' }),
    ]);
    renderScreen();

    const rate = await screen.findByTestId('pending-win-rate');
    expect(rate.textContent ?? '').not.toMatch(/\d+(\.\d+)?\s*%/u);
  });
});
