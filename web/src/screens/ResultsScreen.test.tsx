// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { tabOf } from '../display/bidBoxDisplay';
import { BID_CASE_STATE_DISPLAY, NEXT_CHECKPOINT_DISPLAY } from '../display/caseDisplay';
import {
  GAP_STATE_DISPLAY,
  OUTCOME_AGREEMENT_DISPLAY,
  RECONCILED_OUTCOME_DISPLAY,
  RECONCILIATION_BLOCKER_DISPLAY,
  SUBMITTED_AMOUNT_ORIGIN_DISPLAY,
} from '../display/myBidResultDisplay';
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
type MyBidCase = queries.MyBidCase;
type MyBidResults = queries.MyBidResults;

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

/** 하네스 실측값(착수 보고 ③)을 기본값으로 — 오늘의 답은 「대사 판정 불가 · 차이 부재」다. */
function myBidCase(overrides: Partial<MyBidCase> = {}): MyBidCase {
  return {
    caseId: 51,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    recordedState: 'AWAITING_OPENING',
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

function myBidResults(
  cases: readonly MyBidCase[] = [myBidCase()],
  overrides: Partial<MyBidResults['aggregate']> = {},
): MyBidResults {
  return {
    cases: [...cases],
    aggregate: {
      sampleSize: cases.length,
      recordedRateState: 'ABSENT_NO_DECIDED_CASES',
      recordedAwardRate: null,
      recordedWonCount: 0,
      recordedLostCount: 0,
      recordedDenominator: 0,
      recordedFailedOrRebidCount: 0,
      recordedUndecidedCount: cases.length,
      reconciledRateState: 'ABSENT_NO_DECIDED_CASES',
      reconciledAwardRate: null,
      reconciledWonCount: 0,
      reconciledLostCount: 0,
      reconciledDenominator: 0,
      reconciledFailedOrRebidCount: 0,
      reconciledUndecidedCount: cases.length,
      reconciliationBlockers: ['NOT_OPENED_YET'],
      ...overrides,
    },
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
  mocked.readMyBidResults.mockResolvedValue(myBidResults());
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

    // **그 건이 보이는 탭으로** 보낸다(r1 M-2) — 맨 `/bids` 는 첫 탭을 열고 결과 단계 건은
    // 거기 없다. 탭 id 는 D 의 표에서 오고 spec 이 지어내지 않는다.
    expect(card.getByRole('link', { name: /입찰함/u }).getAttribute('href')).toBe(
      `/bids?tab=${tabOf('AWAITING_OPENING')}`,
    );
    expect(card.getByRole('link', { name: /워크시트/u }).getAttribute('href')).toBe(
      '/notices/SYN-7E3-RESULT/000',
    );
  });
});

describe('링크가 그 건의 탭을 싣는다 (r1 M-2)', () => {
  it.each([
    ['OPENED_PROVISIONAL_FIRST', 'under-review'],
    ['WON', 'closed'],
    ['AWAITING_OPENING', 'awaiting-opening'],
  ] as const)('%s 는 %s 탭으로 보낸다', async (state, tab) => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state })]);
    renderScreen();

    const card = within(await screen.findByTestId('result-case-51'));
    expect(card.getByRole('link', { name: /입찰함/u }).getAttribute('href')).toBe(`/bids?tab=${tab}`);
  });

  /** 세 상태가 **서로 다른 탭**으로 간다 — 한 값으로 굳어 있으면 위 표가 공허하다. */
  it('상태가 다르면 탭도 다르다', () => {
    expect(new Set([tabOf('AWAITING_OPENING'), tabOf('WON'), tabOf('OPENED_PROVISIONAL_FIRST')]).size).toBe(3);
  });
});

describe('다루지 않는 자리 (D-7E3a-3 → M7/7E-3b)', () => {
  it('남은 자리가 사유와 함께 뜨고 입력칸·버튼이 없다', async () => {
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

  /**
   * **앞 판의 단언을 바꿔치운 자리**다. 7E-3a 는 「`pending-win-rate` 에 숫자가 없다」로 「목록을
   * 세어 집계를 지어내지 않는다」를 잠갔다. 그 자리는 이제 집계 절이 받았으므로 단언을 지우지
   * 않고 **같은 주장의 새 모양**으로 옮긴다: 서버가 분모 0 을 말하면 화면에도 비율 숫자가 없다.
   */
  it('서버가 분모 0 을 말하면 화면에 비율 숫자가 없다 — 목록을 세어 짓지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([
      bidCase({ caseId: 51, state: 'WON' }),
      bidCase({ caseId: 52, state: 'LOST' }),
    ]);
    mocked.readMyBidResults.mockResolvedValue(
      myBidResults([myBidCase({ caseId: 51 }), myBidCase({ caseId: 52 })]),
    );
    renderScreen();

    const aggregate = await screen.findByTestId('results-aggregate');
    expect(within(aggregate).queryByTestId('rate-value-recorded'), '분모 0 인데 비율 값을 그렸다').toBeNull();
    expect(aggregate.textContent ?? '').not.toMatch(/\d+(\.\d+)?\s*%/u);
  });

  /** 7E-3b 가 채운 다섯은 **빈 자리 목록에서 사라졌다** — 값 옆에 「준비 중」이 함께 서지 않는다. */
  it('7E-3b 가 채운 자리의 빈 칸이 남아 있지 않다', async () => {
    renderScreen();
    await screen.findByTestId('results-pending');

    for (const id of ['my-bid-amount', 'award-difference', 'chosen-plan', 'reconciliation', 'win-rate']) {
      expect(screen.queryByTestId(`pending-${id}`), `${id} 가 아직 빈 자리로 있다`).toBeNull();
    }
  });
});

describe('대사 절 — 내 투찰가 · 고른 안 · 차이 · 판정 (D-7E3b-1·7)', () => {
  it('목록과 같은 화면에서 결과 조회를 한 번 부른다', async () => {
    renderScreen();

    await waitFor(() => expect(mocked.readMyBidResults).toHaveBeenCalledTimes(1));
  });

  /**
   * 투찰 금액 칸은 **넷이고 전부 non-null** 이다(D-7E3b-7 ⒝ — `basis` 가 없다). 세 갈래 읽기가
   * 적용될 자리가 아니라 숫자와 부속 칸이 함께 선다.
   */
  it('내 투찰가가 통화·과세와 함께 선다', async () => {
    renderScreen();

    const amount = (await screen.findByTestId('submitted-amount-51')).textContent ?? '';
    expect(amount).toContain('1,350,000,000');
    expect(amount).toContain('KRW');
    expect(amount).toContain('INCLUSIVE');
  });

  /** 「고른 안」은 **빈칸이 아니라 상태**다 — 오늘의 답은 「메뉴에서 고른 것이 아님」이다. */
  it('고른 안이 빈칸이 아니라 출처 상태로 선다', async () => {
    renderScreen();

    const chosen = (await screen.findByTestId('chosen-option-51')).textContent ?? '';
    expect(chosen).toContain(SUBMITTED_AMOUNT_ORIGIN_DISPLAY.OPERATOR_ENTERED.label);
    expect(chosen.trim().length).toBeGreaterThan(0);
  });

  it('차이가 서지 않으면 그 상태가 답이고 숫자가 없다', async () => {
    renderScreen();

    const gap = (await screen.findByTestId('award-gap-51')).textContent ?? '';
    expect(gap).toContain(GAP_STATE_DISPLAY.AWARD_ABSENT.label);
    expect(gap).not.toMatch(/\d/u);
  });

  it('차이가 서면 음수 부호까지 그대로 그린다', async () => {
    mocked.readMyBidResults.mockResolvedValue(
      myBidResults([myBidCase({ gapState: 'COMPUTED', gapWon: -40_000_000, gapCurrency: 'KRW' })]),
    );
    renderScreen();

    const gap = (await screen.findByTestId('award-gap-51')).textContent ?? '';
    expect(gap).toContain('-40,000,000');
    expect(gap).toContain('KRW');
  });

  /** **판정 불가는 패찰이 아니다** — 사유가 함께 서야 운영자가 기다릴지 말지를 안다. */
  it('판정 불가가 자기 문면과 사유로 선다 — 패찰 문면이 아니다', async () => {
    renderScreen();

    const verdict = (await screen.findByTestId('reconciled-outcome-51')).textContent ?? '';
    expect(verdict).toContain(RECONCILED_OUTCOME_DISPLAY.UNDECIDABLE.label);
    expect(verdict).not.toContain(RECONCILED_OUTCOME_DISPLAY.LOST.label);
    expect(screen.getByTestId('reconciliation-blocker-51').textContent ?? '').toContain(
      RECONCILIATION_BLOCKER_DISPLAY.NOT_OPENED_YET.label,
    );
  });

  /** 사유는 **판정 불가일 때만** 값이 있다(계약) — 결말이 선 건에 사유 자리를 만들지 않는다. */
  it('판정이 선 건에는 사유 자리가 없다', async () => {
    mocked.readMyBidResults.mockResolvedValue(
      myBidResults([myBidCase({ reconciledOutcome: 'LOST', reconciliationBlocker: null })]),
    );
    renderScreen();

    expect((await screen.findByTestId('reconciled-outcome-51')).textContent ?? '').toContain(
      RECONCILED_OUTCOME_DISPLAY.LOST.label,
    );
    expect(screen.queryByTestId('reconciliation-blocker-51')).toBeNull();
  });

  it('운영자 기록과의 일치가 자기 칸으로 선다', async () => {
    mocked.readMyBidResults.mockResolvedValue(
      myBidResults([myBidCase({ recordedState: 'WON', reconciledOutcome: 'LOST', outcomeAgreement: 'DISAGREE' })]),
    );
    renderScreen();

    expect((await screen.findByTestId('outcome-agreement-51')).textContent ?? '').toContain(
      OUTCOME_AGREEMENT_DISPLAY.DISAGREE.label,
    );
  });

  /**
   * **두 축을 한 값으로 접지 않는다**(D-7C5B-3). 운영자 기록은 카드 머리의 상태 배지가, 서버
   * 대사는 이 절의 판정 배지가 든다 — 어긋나도 어느 쪽도 상대를 덮지 않는다.
   */
  it('운영자 기록과 서버 대사가 어긋나도 둘 다 보인다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase({ state: 'WON' })]);
    mocked.readMyBidResults.mockResolvedValue(
      myBidResults([myBidCase({ recordedState: 'WON', reconciledOutcome: 'LOST', outcomeAgreement: 'DISAGREE' })]),
    );
    renderScreen();

    const card = within(await screen.findByTestId('result-case-51'));
    expect(card.getByTestId('result-state-51').textContent ?? '').toContain(BID_CASE_STATE_DISPLAY.WON.label);
    expect(card.getByTestId('reconciled-outcome-51').textContent ?? '').toContain(
      RECONCILED_OUTCOME_DISPLAY.LOST.label,
    );
  });

  /**
   * **개찰 축은 두 출처가 따로 선다**(D-7E3b-9). `readOpeningResult` 가 카드의 개찰 절을, 대사
   * 응답의 `openingState` 가 「판정이 본 입력」을 든다 — 어긋나면 둘 다 보인다(접지 않는다).
   */
  it('개찰 축이 어긋나면 두 자리가 각자의 값을 보인다', async () => {
    mocked.readOpeningResult.mockResolvedValue(opening({ state: 'OPENED' }));
    mocked.readMyBidResults.mockResolvedValue(myBidResults([myBidCase({ openingState: 'NOT_COLLECTED' })]));
    renderScreen();

    expect((await screen.findByTestId('opening-state-51')).textContent ?? '').toContain(
      OPENING_RESULT_STATE_DISPLAY.OPENED.label,
    );
    expect(screen.getByTestId('judged-opening-state-51').textContent ?? '').toContain(
      OPENING_RESULT_STATE_DISPLAY.NOT_COLLECTED.label,
    );
  });

  /**
   * 카드 ⊆ `cases[]` 는 **전이표의 성질**이지 타입이 아니다(D-7E3b-7 ⒞). 짝이 없으면 칸을
   * 비우지 않고 사유 한 줄을 적는다 — 빈 칸은 「값이 없다」로 읽혀 거짓말이 된다.
   */
  it('결과 조회에 그 건이 없으면 사유 한 줄이고 칸을 비우지 않는다', async () => {
    mocked.readMyBidResults.mockResolvedValue(myBidResults([myBidCase({ caseId: 99 })]));
    renderScreen();

    expect(await screen.findByTestId('result-missing-51')).toBeTruthy();
    expect(screen.queryByTestId('submitted-amount-51'), '짝이 없는데 투찰가 칸을 그렸다').toBeNull();
  });

  it('결과 조회만 실패하면 카드는 남고 대사 절만 오류 얼굴이다', async () => {
    mocked.readMyBidResults.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    expect(await screen.findByTestId('my-bid-results-error')).toBeTruthy();
    expect(screen.getByTestId('result-case-51'), '카드가 통째로 사라졌다').toBeTruthy();
    expect(screen.queryByTestId('reconciled-outcome-51'), '조회 실패에 판정을 지어냈다').toBeNull();
  });
});

/**
 * **칸의 전수**(7E-3a r1 CR M-3 의 선례). 계약이 주는 칸을 화면이 버리지 않는다 — 계약에 칸이
 * 늘면 `satisfies` 가 컴파일에서, 키 집합 등식이 값 쪽에서 먼저 틀린다.
 */
describe('결과 응답의 칸 전수 (D-7E3b-3)', () => {
  /** 칸 → 그것을 싣는 testId 접두. `QUERY` 는 대응 키·카드 머리가 이미 그리는 칸이다. */
  const FIELD_PLACEMENT = {
    caseId: 'QUERY',
    noticeNumber: 'QUERY',
    noticeRound: 'QUERY',
    recordedState: 'QUERY',
    submittedAmountWon: 'submitted-amount',
    submittedAmountCurrency: 'submitted-amount',
    submittedAmountVatTreatment: 'submitted-amount',
    submittedAmountOrigin: 'chosen-option',
    chosenOptionRef: 'chosen-option',
    submittedAt: 'submitted-at',
    confirmedAt: 'confirmed-at',
    openingState: 'judged-opening-state',
    confirmedAwardAmountWon: 'judged-confirmed-award',
    confirmedAwardAmountCurrency: 'judged-confirmed-award',
    confirmedAwardAmountBasis: 'judged-confirmed-award',
    confirmedAwardAmountVatTreatment: 'judged-confirmed-award',
    confirmedAwardAmountProvenance: 'judged-confirmed-award',
    reconciledOutcome: 'reconciled-outcome',
    reconciliationBlocker: 'reconciliation-blocker',
    outcomeAgreement: 'outcome-agreement',
    gapState: 'award-gap',
    gapWon: 'award-gap',
    gapCurrency: 'award-gap',
  } as const satisfies Record<keyof MyBidCase, string>;

  it('표의 키 집합이 계약 칸 전수와 같다', () => {
    expect(Object.keys(FIELD_PLACEMENT).sort()).toStrictEqual(Object.keys(myBidCase()).sort());
  });

  it('QUERY 가 아닌 칸은 전부 화면에 자리가 있다', async () => {
    renderScreen();
    await screen.findByTestId('reconciled-outcome-51');

    const places = new Set(Object.values(FIELD_PLACEMENT).filter((one) => one !== 'QUERY'));
    for (const place of places) {
      expect(screen.queryByTestId(`${place}-51`), `${place} 자리가 화면에 없다`).not.toBeNull();
    }
  });
});

/**
 * 세 얼굴 중 **빈 얼굴만** 잠겨 있던 자리(r1 CR M-2 · v M-2·M-3). 적재 중을 빈 얼굴로 접거나
 * 개찰 조회 실패를 「아직 개찰 전」으로 접는 변이가 모두 초록이었다.
 */
describe('로딩·오류 얼굴 (r1 CR M-2 · v M-2·M-3)', () => {
  it('적재 중에는 로딩 얼굴이고 빈 얼굴이 **아니다**', async () => {
    mocked.listBidCases.mockReturnValue(new Promise<readonly BidCase[]>(() => {}));
    renderScreen();

    // 양성 표지(로딩)를 먼저 기다린 뒤 음성 단언을 한 번 한다.
    expect(await screen.findByTestId('results-loading')).toBeTruthy();
    expect(screen.queryByTestId('results-empty'), '적재 중을 「건이 없다」로 접었다').toBeNull();
  });

  it('목록이 실패하면 오류 얼굴이고 「다시 시도」가 다시 읽는다', async () => {
    mocked.listBidCases.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    expect(await screen.findByTestId('results-error')).toBeTruthy();
    expect(screen.queryByTestId('results-empty'), '오류를 「건이 없다」로 접었다').toBeNull();

    const before = mocked.listBidCases.mock.calls.length;
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(before));
  });

  /** 이 slice 고유의 모양 — **카드는 남고 개찰 절만** 오류다(건 상태는 다른 응답이 준다). */
  it('개찰 조회만 실패하면 카드는 남고 개찰 절만 오류 얼굴이다', async () => {
    mocked.readOpeningResult.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    /**
     * **대사 절도 개찰 축을 낸다**(M7/7E-3b · D-7E3b-9) — 그 값은 다른 operation 의 답이라
     * 지어낸 것이 아니다. 아래 전문 스캔이 뜻을 가지려면 두 출처의 값이 **달라야** 하므로 이
     * 건의 대사 축을 다른 갈래로 둔다. 같은 값으로 두면 스캔이 정당한 값에 걸려, 「실패를 아직
     * 개찰 전으로 접었다」를 더 이상 재지 못한다.
     */
    mocked.readMyBidResults.mockResolvedValue(
      myBidResults([myBidCase({ openingState: 'NOT_COLLECTED', reconciliationBlocker: 'NOT_COLLECTED' })], {
        reconciliationBlockers: ['NOT_COLLECTED'],
      }),
    );
    renderScreen();

    expect(await screen.findByTestId('opening-error-51')).toBeTruthy();
    expect(screen.getByTestId('result-case-51'), '카드가 통째로 사라졌다').toBeTruthy();
    expect(screen.getByTestId('result-state-51').textContent ?? '').toContain(
      BID_CASE_STATE_DISPLAY.AWAITING_OPENING.label,
    );
    // **실패를 「아직 개찰 전」으로 접지 않는다** — 아홉 갈래를 접지 않는 술어가 오류 경로에도 선다.
    expect(screen.queryByTestId('opening-state-51'), '조회 실패에 개찰 상태를 지어냈다').toBeNull();
    const body = document.body.textContent ?? '';
    expect(body, '조회 실패를 「아직 개찰 전」으로 접었다').not.toContain(
      OPENING_RESULT_STATE_DISPLAY.NOT_OPENED_YET.label,
    );
  });

  it('개찰 조회가 적재 중이면 로딩이고 개찰 상태를 지어내지 않는다', async () => {
    mocked.readOpeningResult.mockReturnValue(new Promise<queries.OpeningResultView>(() => {}));
    renderScreen();

    expect(await screen.findByTestId('opening-loading-51')).toBeTruthy();
    expect(screen.queryByTestId('opening-state-51')).toBeNull();
  });

  /**
   * 세션이 없다는 답은 **재시도로 풀리지 않는다**(r1 CR L-5)이고, 그 얼굴은 **화면에 하나**다
   * (r3 CR L-1 — 앞 판은 카드마다 `SignedOutFace` 를 그려 같은 `role="alert"` 이 N 개 섰다).
   */
  it('개찰 조회가 401 이면 재시도 얼굴이 아니라 화면에 로그인 길 하나다', async () => {
    mocked.listBidCases.mockResolvedValue([bidCase(), bidCase({ caseId: 52, state: 'WON' })]);
    mocked.readOpeningResult.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    // 양성 표지를 먼저 — 카드 둘이 서고 각자 그 사실을 조용히 적는다.
    expect(await screen.findByTestId('opening-signed-out-51')).toBeTruthy();
    expect(screen.getByTestId('opening-signed-out-52')).toBeTruthy();
    expect(screen.queryByTestId('opening-error-51'), '401 에 「다시 시도」를 달았다').toBeNull();

    // **얼굴은 하나**다 — 카드 수만큼 서지 않는다.
    const faces = screen.getAllByTestId('signed-out');
    expect(faces, '401 얼굴이 카드 수만큼 섰다').toHaveLength(1);
    expect(
      within(faces[0]!).getByRole('link', { name: /로그인/u }).getAttribute('href'),
      '401 얼굴에 로그인으로 가는 길이 없다',
    ).toBe('/login');
    expect(screen.getAllByRole('alert'), 'alert 가 여럿이면 같은 문장을 N 번 읽는다').toHaveLength(1);
  });
});

/**
 * **칸의 전수**(r1 CR M-3). 어휘(아홉 갈래·넷)는 전수로 잠갔는데 칸은 잠그지 않아 계약이 주는
 * 둘(`provisionalRankOneDuplicateCount` · 결과 축 `observedAt`)을 화면이 버리고 있었다.
 * 이 표가 그 자리를 든다 — 계약에 칸이 늘면 아래 키 집합 등식이 먼저 틀린다.
 */
describe('개찰 결과의 칸 전수 (r1 CR M-3)', () => {
  /** 칸 → 그것을 싣는 testId 접두. `QUERY` 는 조회 키라 카드 머리가 이미 그린다. */
  const FIELD_PLACEMENT = {
    noticeNumber: 'QUERY',
    noticeRound: 'QUERY',
    state: 'opening-state',
    participantCount: 'participants',
    actualOpeningAt: 'opening-at',
    observedAt: 'observed-at',
    confirmedAwardAmountWon: 'confirmed-award',
    confirmedAwardAmountCurrency: 'confirmed-award',
    confirmedAwardAmountBasis: 'confirmed-award',
    confirmedAwardAmountVatTreatment: 'confirmed-award',
    confirmedAwardAmountProvenance: 'confirmed-award',
    provisionalRankOneState: 'provisional-state',
    provisionalRankOneDuplicateCount: 'provisional-duplicate',
    provisionalRankOneBidAmountWon: 'provisional-award',
    provisionalRankOneBidAmountCurrency: 'provisional-award',
    provisionalRankOneBidAmountVatTreatment: 'provisional-award',
    provisionalRankOneBidRateFraction: 'provisional-rate',
    provisionalRankOneObservedAt: 'provisional-observed',
  } as const satisfies Record<keyof OpeningResultView, string>;

  it('표의 키 집합이 계약 칸 전수와 같다', () => {
    expect(Object.keys(FIELD_PLACEMENT).sort()).toStrictEqual(Object.keys(opening()).sort());
  });

  it('QUERY 가 아닌 칸은 전부 화면에 자리가 있다', async () => {
    renderScreen();
    await screen.findByTestId('opening-state-51');

    const places = new Set(Object.values(FIELD_PLACEMENT).filter((one) => one !== 'QUERY'));
    for (const place of places) {
      expect(screen.queryByTestId(`${place}-51`), `${place} 자리가 화면에 없다`).not.toBeNull();
    }
  });

  it('순위 1이 여럿이면 **몇인지**를 보인다 — 「여럿」으로 접지 않는다', async () => {
    mocked.readOpeningResult.mockResolvedValue(
      opening({ state: 'OPENED', provisionalRankOneState: 'RANK_DUPLICATED', provisionalRankOneDuplicateCount: 3 }),
    );
    renderScreen();

    const count = await screen.findByTestId('provisional-duplicate-51');
    expect(count.textContent ?? '').toContain('3');
  });

  it('결과 축 관측 시각을 그린다 — 확정 축의 낡음을 잴 재료다', async () => {
    mocked.readOpeningResult.mockResolvedValue(
      opening({ state: 'OPENED', observedAt: '2026-11-02T03:04:05Z' }),
    );
    renderScreen();

    const at = await screen.findByTestId('observed-at-51');
    expect(at.textContent ?? '').toContain('2026-11-02T03:04:05Z');
  });

  it('두 칸이 없으면 「값 없음」이다 — 0 이나 빈 칸으로 지어내지 않는다', async () => {
    renderScreen();

    expect((await screen.findByTestId('provisional-duplicate-51')).textContent ?? '').toContain('값 없음');
    expect(screen.getByTestId('observed-at-51').textContent ?? '').toContain('값 없음');
  });
});
