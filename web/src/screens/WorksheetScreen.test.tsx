// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';

import { WorksheetScreen } from './WorksheetScreen';

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

const CASE: queries.BidCase = {
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
};

/** 출하 조립이 실제로 내는 모양(Phase 1 실측) — 세 가격 축이 전부 「아직」이다. */
const PRICE: queries.BidPriceRequestResponse = {
  noticeNumber: NOTICE.noticeNumber,
  noticeRound: NOTICE.noticeRound,
  correlationId: 'corr-1',
  strategyRevision: 1,
  currentActiveBids: 0,
  maxActiveBids: 5,
  outcome: 'NOT_REACHED',
  verdict: null,
  verdictReasonCodes: [],
  dropStage: 'WATCH_GATE',
  dropReasonCode: 'WATCH_GATE_NOT_CONFIGURED',
  noticeStatus: 'Open',
  failedWatchRuleIds: [],
  wouldNotify: false,
  inboxRecordState: 'NOT_RECORDED_YET',
  inboxRecordNotRecordedReason: 'INBOX_WRITE_PORT_NOT_MERGED',
  licenseVerdict: 'UNCERTAIN',
  licenseUncertainReason: 'REQUIREMENT_DATA_ABSENT',
  licenseRequirementSourceFields: [],
  licenseGroupsSatisfied: [],
  licenseGroupsMissing: [],
  licenseMissingLicenses: [],
  licenseMissingDetailState: 'NOT_APPLICABLE',
  licenseUnparsableRowCount: 0,
  licenseValidity: 'NOT_VERIFIED',
  licensePolicySource: 'policy-values.md §1.2',
  unjudgedAxes: ['QUAL_08_REGION', 'QUAL_10_PERFORMANCE', 'QUAL_11_CONSTRUCTION_CAPACITY'],
  baseAmountWon: 1_200_000_000,
  baseAmountBasis: 'BASE_AMOUNT',
  baseAmountResolution: 'DIRECT',
  publishedFloorRateFraction: null,
  floorRateGroundsState: 'ABSENT',
  floorShortfallState: 'NOT_COMPUTED',
  floorShortfallNotComputedReason: 'SHORTFALL_POLICY_NOT_PUBLISHED',
  mlAnalysisState: 'NOT_REACHED',
  mlUnavailableReason: null,
  confidenceState: 'NOT_AVAILABLE',
  confidenceNotAvailableReason: 'EVALUATION_NOT_REACHED_ML_STAGE',
  bidPriceMenuState: 'NOT_COMPUTED',
  bidPriceMenuNotComputedReason: 'MENU_PATH_NOT_WIRED',
  disclosures: [
    'YEGA_DRAWN_PRIVATELY',
    'NOT_AUTO_SUBMITTED',
    'STATUTORY_FLOOR_RATE_NOT_INTERPRETED',
    'PRICE_FITNESS_IS_NOT_PROBABILITY',
  ],
};

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([{ path: '/notices/:noticeNumber/:noticeRound', element: <WorksheetScreen /> }], {
    initialEntries: [`/notices/${NOTICE.noticeNumber}/${NOTICE.noticeRound}`],
  });
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

const mocked = vi.mocked(queries);

/** 투찰가 요청 경로 — 진행 중 건수를 **선언한 뒤** primary 를 누른다(CR H-1). */
async function askPrice(declared = '2'): Promise<void> {
  fireEvent.change(await screen.findByTestId('active-bids'), { target: { value: declared } });
  fireEvent.click(await screen.findByTestId('worksheet-primary'));
}

// `globals: false` 이므로 자동 cleanup 이 없다 — 남은 DOM 이 같은 testId 를 둘로 만든다.
afterEach(() => {
  cleanup();
});

beforeEach(() => {
  vi.resetAllMocks();
  mocked.readSession.mockResolvedValue({ csrf: 'csrf-value', expiresAt: '2099-01-01T00:00:00Z' });
  mocked.searchNotices.mockResolvedValue({ rows: [NOTICE], total: 1, resultState: 'MATCHED' });
  mocked.listBidCases.mockResolvedValue([CASE]);
  mocked.readTimeline.mockResolvedValue([
    { caseVersion: 0, fromState: null, toState: 'WATCHING', command: 'ADD_TO_BID_BOX', actorKind: 'USER', retroactive: false, occurredAt: '2026-10-29T05:00:00Z' },
  ]);
  mocked.requestBidPrice.mockResolvedValue(PRICE);
});

describe('화면 C — 머리와 다음 행동 하나', () => {
  it('공고명·기관·마감을 API 가 준 그대로 보인다', async () => {
    renderScreen();

    expect((await screen.findByTestId('worksheet-title')).textContent).toBe(NOTICE.title);
    expect((await screen.findByTestId('worksheet-agency')).textContent).toContain(NOTICE.demandAgencyName as string);
  });

  /** §6.5 — primary 버튼은 **그 상태의 다음 전이 하나**다. */
  it('담긴 건의 primary 버튼은 투찰가 요청 하나다', async () => {
    renderScreen();

    const primary = await screen.findByTestId('worksheet-primary');
    expect(primary.textContent).toBe('투찰가 요청');
  });

  it('다음 확인 시점이 비어 있지 않다 — 「없음」도 답이다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, nextCheckpointKind: 'NONE', nextCheckpointAt: null }]);
    renderScreen();

    expect((await screen.findByTestId('worksheet-checkpoint')).textContent).toContain('없음');
  });
});

/**
 * CR H-1 · v M-5 — 계약은 `currentActiveBids` 를 「**호출자가 선언한다** — 서버가 0 으로 지어내지
 * 않는다」로 둔다. 실측: 계약 전체에 그 수를 **서버가 세어 주는 표면이 없다**(`listBidCases` 에
 * `X-Total-Count` 도 집계도 없다). 클라이언트가 상태를 「진행 중」으로 분류해 세면 그것은 계약에
 * 없는 정의를 화면이 만드는 것이라 D-7E-4 위반이다 — 그래서 **운영자가 선언**하고 화면은 그 값을
 * 그대로 싣고 되돌려 받은 값을 보인다.
 */
/** v L-3 — 화면 C 의 세 얼굴. 분기 제거 변이가 지나갔던 자리다. */
describe('화면 C — 빈·로딩·오류 세 얼굴', () => {
  /** CR M-2 — 세션이 없으면 재시도가 아니라 로그인으로 보낸다(서버의 답을 소비한다). */
  it('세션이 없으면 로그인 얼굴이 뜬다', async () => {
    mocked.readSession.mockResolvedValue(null);
    renderScreen();

    const face = await screen.findByTestId('signed-out');
    expect(face.textContent).toContain('로그인이 필요합니다');
  });

  it('로딩 중에는 스켈레톤이 뜬다', () => {
    mocked.searchNotices.mockReturnValue(new Promise(() => undefined));
    renderScreen();

    expect(screen.getByTestId('worksheet-loading')).not.toBeNull();
  });

  it('읽기 오류에는 재시도가 있다', async () => {
    mocked.searchNotices.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const face = await screen.findByTestId('worksheet-error');
    expect(within(face).getByRole('button', { name: '다시 시도' })).not.toBeNull();
  });

  it('그 공고가 수집본에 없으면 이유 있는 빈 얼굴이다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_MATCH' });
    renderScreen();

    expect((await screen.findByTestId('worksheet-missing')).textContent).toContain('찾지 못했습니다');
  });

  /** v L-1 — 「3안이 아직 …」은 **API 상태에서만** 나온다(요청 전에는 그 문장이 없다). */
  it('투찰가를 요청하기 전에는 「3안이 아직」 문장이 없다', async () => {
    renderScreen();

    await screen.findByTestId('worksheet-primary');
    expect(screen.queryByTestId('amount-self-entry-note')).toBeNull();
  });

  it('요청 뒤 상태가 「아직 산출 안 함」이면 그 문장이 나온다', async () => {
    renderScreen();
    await askPrice();

    expect(await screen.findByTestId('amount-self-entry-note')).not.toBeNull();
  });
});

/** v L-4 — 판정과 멈춘 단계·사유가 화면에 있다(`OUTCOME_DISPLAY` 가 가리키던 칸이다). */
describe('화면 C — 판정과 멈춘 자리', () => {
  it('판정·멈춘 단계·사유를 응답이 준 코드 그대로 보인다', async () => {
    renderScreen();
    await askPrice();

    expect((await screen.findByTestId('verdict-outcome')).textContent).toContain('판정 전에 멈춤');
    expect((await screen.findByTestId('drop-stage')).textContent).toBe(PRICE.dropStage as string);
    expect((await screen.findByTestId('drop-reason')).textContent).toBe(PRICE.dropReasonCode as string);
  });
});

describe('화면 C — 진행 중 투찰 건수는 선언된 값이다(지어내지 않는다)', () => {
  it('선언 전에는 투찰가를 요청하지 않고 무엇이 없는지 말한다', async () => {
    renderScreen();

    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => {
      expect(screen.getByTestId('active-bids-blocked').textContent).toContain('진행 중 투찰 건수');
    });
    expect(mocked.requestBidPrice).not.toHaveBeenCalled();
  });

  it('선언한 값이 그대로 요청에 실린다 — 0 을 기본값으로 두지 않는다', async () => {
    renderScreen();

    fireEvent.change(await screen.findByTestId('active-bids'), { target: { value: '3' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => {
      expect(mocked.requestBidPrice).toHaveBeenCalledWith({
        noticeNumber: NOTICE.noticeNumber,
        noticeRound: NOTICE.noticeRound,
        currentActiveBids: 3,
      });
    });
  });

  it('0 을 선언하는 것은 가능하다 — 다만 그것은 운영자의 선언이다', async () => {
    renderScreen();

    fireEvent.change(await screen.findByTestId('active-bids'), { target: { value: '0' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => {
      expect(mocked.requestBidPrice).toHaveBeenCalledWith(
        expect.objectContaining({ currentActiveBids: 0 }),
      );
    });
  });

  it('응답이 되돌려 준 선언값과 여력 상한을 근거에 보인다', async () => {
    mocked.requestBidPrice.mockResolvedValue({ ...PRICE, currentActiveBids: 3, maxActiveBids: 5 });
    renderScreen();

    fireEvent.change(await screen.findByTestId('active-bids'), { target: { value: '3' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    const capacity = await screen.findByTestId('capacity');
    expect(capacity.textContent).toContain('3');
    expect(capacity.textContent).toContain('5');
    expect(capacity.textContent).toContain('선언');
  });
});

describe('화면 C — 투찰가 요청의 세 가격 축(G2 13)', () => {
  it('3안 자리는 「아직 산출 안 함」이고 그 자리에 숫자가 없다', async () => {
    renderScreen();
    await askPrice();

    const slot = await screen.findByTestId('bid-price-menu');
    expect(slot.textContent).toContain('아직 산출 안 함');
    // 숫자 부재는 **값 자리**에서만 잰다 — 제목의 「3안」은 도메인 용어이고 값이 아니다.
    expect((await screen.findByTestId('bid-price-menu-value')).textContent).not.toMatch(/\d/);
  });

  it('하한 미달 빈도와 신뢰도도 상태와 사유로만 보인다', async () => {
    renderScreen();
    await askPrice();

    expect((await screen.findByTestId('floor-shortfall')).textContent).toContain('재지 못함');
    expect((await screen.findByTestId('confidence')).textContent).toContain('신뢰도 없음');
  });

  it('게시 하한율의 「없다」가 0% 로 보이지 않는다', async () => {
    renderScreen();
    await askPrice();

    const grounds = await screen.findByTestId('floor-rate-grounds');
    expect(grounds.textContent).toContain('게시 하한율 없음');
    expect(grounds.textContent).not.toContain('0%');
  });

  it('게시 하한율이 근거로 설 때는 그 값을 원문 그대로 보인다', async () => {
    mocked.requestBidPrice.mockResolvedValue({
      ...PRICE,
      floorRateGroundsState: 'USABLE',
      publishedFloorRateFraction: '0.8745',
      publishedFloorRateOrigin: 'NOTICE_VALUE',
    });
    renderScreen();
    await askPrice();

    const grounds = await screen.findByTestId('floor-rate-grounds');
    expect(grounds.textContent).toContain('0.8745');
  });
});

describe('화면 C — 자격 카드(G2 13)', () => {
  beforeEach(async () => {
    renderScreen();
    await askPrice();
  });

  it('판정 불가가 미달로 읽히지 않는다', async () => {
    const card = await screen.findByTestId('qualification');
    expect(within(card).getByTestId('license-verdict').textContent).toContain('면허 판정 불가');
  });

  it('COL-04 — 「요건 없음」과 「수집 실패」가 다른 문면이다', async () => {
    const card = await screen.findByTestId('qualification');
    expect(within(card).getByTestId('license-reason').textContent).toContain('공고에 요건이 없음');
  });

  it('QUAL-02 — 유효기간 미검증이 보인다', async () => {
    const card = await screen.findByTestId('qualification');
    expect(within(card).getByTestId('license-validity').textContent).toContain('유효기간 미검증');
  });

  it('QUAL-08 · QUAL-11 — 아직 판정하지 않은 축 셋이 전부 보인다', async () => {
    const axes = await screen.findByTestId('unjudged-axes');
    expect(axes.textContent).toContain('지역');
    expect(axes.textContent).toContain('수행실적');
    expect(axes.textContent).toContain('시공능력평가금액');
  });

  it('고지 넷이 전부 문장으로 보인다 — 법정 하한율 미해석 포함', async () => {
    const disclosures = await screen.findByTestId('disclosures');
    expect(within(disclosures).getAllByRole('listitem')).toHaveLength(4);
    expect(disclosures.textContent).toContain('법정 하한율은 해석하지 않습니다');
    expect(disclosures.textContent).toContain('대신 제출하지 않습니다');
  });
});

describe('화면 C — 나라장터 이동과 투찰 기록(여정 ③)', () => {
  it('딥링크는 공고번호·차수에서 조립되고 자동 제출이 아님을 말한다', async () => {
    renderScreen();

    const link = await screen.findByTestId('koneps-deeplink');
    expect(link.getAttribute('href')).toContain(encodeURIComponent(NOTICE.noticeNumber));
    expect(link.getAttribute('rel')).toContain('noreferrer');
  });

  /** CR L-6 — 이름이 재는 것과 같아야 한다: 성공 뒤 서버가 주는 상태로 primary 가 바뀐다. */
  it('초안을 만들면 투찰 예정이 되고 primary 가 「제출함」으로 바뀐다', async () => {
    mocked.draftBid.mockResolvedValue({ ...CASE, state: 'BID_PLANNED', caseVersion: 1 });
    mocked.listBidCases.mockResolvedValueOnce([{ ...CASE, state: 'PRICE_RECEIVED' }]);
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED', caseVersion: 1 }]);
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => {
      expect(mocked.draftBid).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByTestId('worksheet-primary').textContent).toBe('제출함');
    });
  });

  it('「제출함」은 금액·제출 시각·추첨번호를 담아 기록한다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED' }]);
    mocked.recordBid.mockResolvedValue({ ...CASE, state: 'BID_RECORDED', caseVersion: 2 });
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    // 과세 구분은 **기본값이 없다** — 운영자가 고르기 전에는 기록 버튼이 잠겨 있다.
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => {
      expect(mocked.recordBid).toHaveBeenCalledWith(
        CASE.caseId,
        expect.objectContaining({ amountWon: 1_100_000_000, vatTreatment: 'INCLUSIVE' }),
      );
    });
  });

  it('과세 구분을 고르기 전에는 기록하지 않는다 — 기본값을 지어내지 않는다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED' }]);
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => {
      expect(screen.getByTestId('record-blocked').textContent).toContain('과세 구분');
    });
    expect(mocked.recordBid).not.toHaveBeenCalled();
  });

  /**
   * CR H-2 — 「제출함」은 이 제품의 핵심 기록이다. 실패했는데 아무 얼굴도 없으면 운영자는 투찰
   * 기록이 남았는지 알 길이 없다. 실패는 그 사실을 보이고 **서버 상태를 다시 읽는다**.
   */
  it('제출함이 실패하면 그 사실이 뜨고 서버 상태를 다시 읽는다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED' }]);
    mocked.recordBid.mockRejectedValue(new ApiError(409, 'CONFLICT'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    const callsBefore = mocked.listBidCases.mock.calls.length;
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    const failure = await screen.findByTestId('write-failure');
    expect(failure.textContent).toContain('기록하지 못했습니다');
    expect(screen.queryByTestId('toast')).toBeNull();
    // 다시 읽기 — 화면이 보이는 상태가 서버의 사실이어야 한다.
    await waitFor(() => {
      expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(callsBefore);
    });
  });

  it('초안 실패도 같은 얼굴을 쓴다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'PRICE_RECEIVED' }]);
    mocked.draftBid.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    expect((await screen.findByTestId('write-failure')).textContent).toContain('만들지 못했습니다');
  });

  it('쓰기가 401 이면 로그인으로 가는 길을 보인다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED' }]);
    mocked.recordBid.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    const failure = await screen.findByTestId('write-failure');
    expect(within(failure).getByRole('link', { name: /로그인/u }).getAttribute('href')).toBe('/login');
  });

  it('타임라인은 전이·주체를 API 가 준 순서로 보인다', async () => {
    renderScreen();

    const timeline = await screen.findByTestId('timeline');
    expect(within(timeline).getAllByRole('listitem')).toHaveLength(1);
    expect(timeline.textContent).toContain('관심');
    expect(timeline.textContent).toContain('나');
  });
});
