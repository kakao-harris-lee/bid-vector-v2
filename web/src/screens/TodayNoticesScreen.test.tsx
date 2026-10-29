// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as queries from '../api/queries';

import { TodayNoticesScreen } from './TodayNoticesScreen';

vi.mock('../api/queries');

const NOTICE: queries.NoticeSummary = {
  noticeNumber: 'SYN-7E1-CNSTWK-1',
  noticeRound: '000',
  status: 'Open',
  title: '합성 공고 Cnstwk 1',
  demandAgencyName: '합성 수요기관',
  noticeAgencyName: '합성 공고기관',
  baseAmountWon: 1_200_000_000,
  baseAmountCurrency: 'KRW',
  baseAmountBasis: 'BASE_AMOUNT',
  baseAmountVatTreatment: 'UNKNOWN',
  baseAmountProvenance: 'PUBLISHED',
  deadlineAt: '2099-01-03T00:00:00Z',
};

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([{ path: '/', element: <TodayNoticesScreen /> }], {
    initialEntries: ['/'],
  });
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
  mocked.searchNotices.mockResolvedValue({ rows: [NOTICE], total: 1, resultState: 'MATCHED' });
  mocked.listSuggestions.mockResolvedValue({ rows: [], total: 0, resultState: 'WATCH_NOT_CONFIGURED' });
  mocked.listBidCases.mockResolvedValue([]);
});

// `globals: false` 이므로 testing-library 의 자동 cleanup 이 등록되지 않는다 — 직접 건다.
// 없으면 앞 test 의 DOM 이 남아 같은 testId 가 둘이 된다(실측).
afterEach(() => {
  cleanup();
});

describe('화면 B — 카드가 보이는 값은 API 응답의 값이다', () => {
  it('공고명·기관·기초금액·상태를 API 가 준 그대로 보인다', async () => {
    renderScreen();

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).getByTestId('notice-title').textContent).toBe(NOTICE.title);
    expect(within(card).getByTestId('notice-agency').textContent).toBe(NOTICE.demandAgencyName);
    expect(within(card).getByTestId('notice-amount').textContent).toContain('1,200,000,000원');
    expect(within(card).getByTestId('notice-status').textContent).toBe('입찰 중');
  });

  /** 금액은 **provenance 와 한 값**이다(DEC-01) — 숫자만 떼어 보여 주지 않는다. */
  it('기초금액에는 basis·과세·provenance 가 함께 붙는다', async () => {
    renderScreen();

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).getByTestId('notice-amount-provenance').textContent).toContain('BASE_AMOUNT');
    expect(within(card).getByTestId('notice-amount-provenance').textContent).toContain('PUBLISHED');
  });

  it('금액이 없는 공고는 0 이 아니라 「값 없음」이다', async () => {
    mocked.searchNotices.mockResolvedValue({
      rows: [{ ...NOTICE, baseAmountWon: null, baseAmountProvenance: null, baseAmountBasis: null }],
      total: 1,
      resultState: 'MATCHED',
    });
    renderScreen();

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).getByTestId('notice-amount').textContent).toBe('값 없음');
  });

  /** 전체 건수는 **헤더**가 정본이다 — 이 페이지의 길이로 세지 않는다. */
  it('전체 건수는 X-Total-Count 를 보인다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [NOTICE], total: 42, resultState: 'MATCHED' });
    renderScreen();

    expect((await screen.findByTestId('notice-total')).textContent).toContain('42');
  });
});

describe('화면 B — 빈·로딩·오류 세 얼굴', () => {
  it('로딩 중에는 스켈레톤이 뜬다', () => {
    mocked.searchNotices.mockReturnValue(new Promise(() => undefined));
    renderScreen();

    expect(screen.getByTestId('notices-loading')).not.toBeNull();
  });

  it('빈 결과 — 아직 수집 전이면 그 사실을 말한다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NOTHING_COLLECTED' });
    renderScreen();

    expect((await screen.findByTestId('notices-empty')).textContent).toContain('아직 수집된 공고가 없습니다');
  });

  it('빈 결과 — 조건이 좁은 것은 다른 문면이다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_MATCH' });
    renderScreen();

    expect((await screen.findByTestId('notices-empty')).textContent).toContain('조건에 맞는 공고가 없습니다');
  });

  it('오류에는 재시도가 있고 서버 메시지를 그대로 내보이지 않는다', async () => {
    mocked.searchNotices.mockRejectedValue(new queries.ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const face = await screen.findByTestId('notices-error');
    expect(within(face).getByRole('button', { name: '다시 시도' })).not.toBeNull();
    expect(face.textContent).not.toContain('INTERNAL_ERROR');
  });
});

/** D-7E-9 — 제안 목록의 빈 얼굴은 **API 상태**에서 나오고 화면이 전략을 읽어 추론하지 않는다. */
describe('화면 B — 제안 목록', () => {
  it('감시 조건이 없으면 그 사실을 그대로 보인다', async () => {
    renderScreen();

    const empty = await screen.findByTestId('suggestions-empty');
    expect(empty.textContent).toContain('감시 조건이 설정되지 않았습니다');
  });

  it('제안 카드에는 면허 판정과 아직 판정하지 않은 축이 함께 붙는다', async () => {
    mocked.listSuggestions.mockResolvedValue({
      rows: [
        {
          ...NOTICE,
          licenseVerdict: 'UNCERTAIN',
          licenseUncertainReason: 'REQUIREMENT_DATA_ABSENT',
          unjudgedAxes: ['QUAL_08_REGION', 'QUAL_11_CONSTRUCTION_CAPACITY'],
        },
      ],
      total: 1,
      resultState: 'MATCHED',
    });
    renderScreen();

    const card = await screen.findByTestId(`suggestion-card-${NOTICE.noticeNumber}`);
    expect(within(card).getByTestId('suggestion-license').textContent).toContain('면허 판정 불가');
    expect(within(card).getByTestId('suggestion-unjudged').textContent).toContain('지역');
    expect(within(card).getByTestId('suggestion-unjudged').textContent).toContain('시공능력평가금액');
  });
});

describe('화면 B — 담기(여정 ②의 마지막 걸음)', () => {
  it('담기를 누르면 그 공고의 번호·차수로 건을 연다', async () => {
    mocked.addBidCase.mockResolvedValue({
      caseId: 7,
      noticeNumber: NOTICE.noticeNumber,
      noticeRound: NOTICE.noticeRound,
      bidKind: 'REAL',
      state: 'WATCHING',
      caseVersion: 0,
      nextCheckpointKind: 'DEADLINE',
      nextCheckpointAt: NOTICE.deadlineAt,
      overdue: false,
      reconciliationCandidate: false,
    });
    renderScreen();

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: '담기' }));

    await waitFor(() => {
      expect(mocked.addBidCase).toHaveBeenCalledWith({
        noticeNumber: NOTICE.noticeNumber,
        noticeRound: NOTICE.noticeRound,
        bidKind: 'REAL',
      });
    });
  });

  /**
   * §6.5 「되돌리기 가능한 즉시 반응」 — **되돌림 전이를 API 가 줄 때만** 실행 취소를 보인다.
   * 담기의 역전이는 계약에 없으므로 토스트에 실행 취소가 **없다**(가짜 버튼을 두지 않는다).
   */
  it('담긴 뒤 토스트가 뜨고, 되돌림 전이가 없으므로 실행 취소 버튼이 없다', async () => {
    mocked.addBidCase.mockResolvedValue({
      caseId: 7,
      noticeNumber: NOTICE.noticeNumber,
      noticeRound: NOTICE.noticeRound,
      bidKind: 'REAL',
      state: 'WATCHING',
      caseVersion: 0,
      nextCheckpointKind: 'DEADLINE',
      nextCheckpointAt: NOTICE.deadlineAt,
      overdue: false,
      reconciliationCandidate: false,
    });
    renderScreen();

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: '담기' }));

    const toast = await screen.findByTestId('toast');
    expect(toast.textContent).toContain('입찰함에 담았습니다');
    expect(within(toast).queryByRole('button', { name: '실행 취소' })).toBeNull();
  });
});
