// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { stateDisplayOf } from '../display/caseDisplay';

import { controlClass } from '../components/Button';

import { SECTION_SUMMARY_CLASS } from './WorksheetGrounds';
import { BID_CASE_STATE_DISPLAY } from '../display/caseDisplay';
import { ACTION_INPUT, exitsOf } from '../display/bidBoxDisplay';
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
  // M14/14E — 공고 읽기 투영. 이 고정물은 그 축을 모델링하지 않으므로
  // 「공고 행이 없다」를 둔다(새 문면을 들이지 않는다). 카드가 그 칸을
  // 쓰기 시작하면 그 slice 가 값을 세운다.
  noticeKnown: false,
  noticeTitle: null,
  demandAgencyName: null,
  noticeAgencyName: null,
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
  inboxRecordState: 'RECORDED_NEW',
  inboxRecordNotRecordedReason: null,
  licenseVerdict: 'UNCERTAIN',
  licenseUncertainReason: 'REQUIREMENT_DATA_ABSENT',
  licenseRequirementSourceFields: [],
  licenseGroupsSatisfied: [],
  licenseGroupsMissing: [],
  licenseMissingLicenses: [],
  licenseMissingDetailState: 'NOT_APPLICABLE',
  licenseUnparsableRowCount: 0,
  licenseValidity: 'NOT_VERIFIED',
  /**
   * **이 값은 적대적 입력이다**(팀장 판정 2026-11-18). 실제 서버가 내는 꼴 그대로이고, 그 안에
   * 개발 식별자가 들어 있다 — QUAL-05 의 **재현용 출처**이지 사용자 문면이 아니다.
   *
   * 그래서 이 fixture 는 「어휘가 금지 집합 밖이어야 한다」는 13A 문면 게이트의 전제를 **일부러
   * 어기는 입력**이다: 화면이 그것을 **그리지 않는다**는 것이 여기서 재는 것이고, 금지 어휘가
   * fixture 에 있다는 사실 자체는 이 test 의 재료다(그 게이트의 모집단이 아니라 이 짝 test 의
   * 입력 자리다).
   */
  licensePolicySource: 'OPEN-QUAL-07 — 별칭·포괄 코드 내용 미확정(2026-09-06), 형태만',
  licensePolicyEffectiveFrom: '2026-09-06',
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

  /**
   * M9 D-1 — 마감은 **KST 표기 + D-day** 한 칸이고, 다음 확인 시점도 같은 표기를 쓴다. 앞 판은
   * 둘 다 응답의 ISO 원문이었다(`OPEN-7E3B-TIME-DISPLAY`).
   */
  it('마감과 다음 확인 시점을 KST 표기로 보인다 — ISO 원문이 아니다', async () => {
    renderScreen();

    const deadline = (await screen.findByTestId('worksheet-deadline')).textContent ?? '';
    expect(deadline).toContain('2099-01-03 09:00');
    expect(deadline, 'ISO 원문이 화면에 남았다').not.toContain(NOTICE.deadlineAt as string);
    // D-day 는 그 자리에 **함께** 선다(요구표 ⓐ 행 — 클라이언트 계산으로 승인된 유일한 축).
    expect(deadline).toMatch(/\(D[-+]\d+\)/);

    const checkpoint = (await screen.findByTestId('worksheet-checkpoint')).textContent ?? '';
    expect(checkpoint).toContain('2099-01-03 09:00');
    expect(checkpoint, 'ISO 원문이 화면에 남았다').not.toContain(CASE.nextCheckpointAt as string);
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
  /** CR r2 L-6 — 떠 있는 화면에서 세션이 끝나면 읽기가 401 이다. 그 재시도는 영원히 실패한다. */
  it('읽기가 401 이면 로그인 얼굴이 뜬다', async () => {
    mocked.searchNotices.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    expect(await screen.findByTestId('signed-out')).not.toBeNull();
  });

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

  /**
   * DEC-10(7C②-b) — 개연 범위 밖 게시값은 **사유가 보이고 숫자는 보이지 않는다**. 계약이 그
   * 칸을 `null` 로 내므로 화면에 숫자가 설 자리가 없고, 출처는 원문 사실로 남는다.
   */
  it('개연 범위 밖 게시 하한율은 사유가 보이고 숫자는 보이지 않는다', async () => {
    mocked.requestBidPrice.mockResolvedValue({
      ...PRICE,
      floorRateGroundsState: 'IMPLAUSIBLE',
      publishedFloorRateFraction: null,
      publishedFloorRateOrigin: 'NOTICE_VALUE',
    });
    renderScreen();
    await askPrice();

    const grounds = await screen.findByTestId('floor-rate-grounds');
    expect(grounds.textContent).toContain('개연 범위 밖');
    expect(grounds.textContent).toContain('공고 게시값');
    expect(grounds.textContent).not.toMatch(/\d/);
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
    // M13/13C — 초안 앞에 **확인**이 선다(§6.2 C 3). 누름이 한 번 늘었을 뿐 보내는 진술은 같다.
    fireEvent.click(await screen.findByTestId('bid-confirm-accept'));

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
    fireEvent.click(await screen.findByTestId('bid-confirm-accept'));

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

  /** CR r2 M-3 — 라벨이 「다시 시도」라면 **실제로 다시 보내야** 한다(앞 판은 얼굴만 닫혔다). */
  it('실패 뒤 「다시 시도」를 누르면 같은 기록을 다시 보낸다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED' }]);
    mocked.recordBid.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    const failure = await screen.findByTestId('write-failure');
    expect(mocked.recordBid).toHaveBeenCalledTimes(1);

    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));

    await waitFor(() => {
      expect(mocked.recordBid).toHaveBeenCalledTimes(2);
    });
    expect(mocked.recordBid.mock.calls[1]).toStrictEqual(mocked.recordBid.mock.calls[0]);
  });

  /**
   * v r3 M-3 — **재전송은 멱등이 아니다.** 하네스로 서버를 직접 쟀다: `BID_RECORDED` 인 건에
   * 같은 본문을 다시 보내면 200 이 돌아오고 기록 행이 하나 더 생긴다(`record_seq` 0·1·2 —
   * 금액이 달라도 받는다). 실패 뒤 다시 읽은 건의 `caseVersion` 이 앞서 있으면 그 쓰기는
   * 닿은 것이므로 **보내지 않는다**.
   */
  it('실패했지만 서버에 닿은 기록은 다시 보내지 않는다 — 중복 행을 만들지 않는다', async () => {
    mocked.listBidCases.mockResolvedValueOnce([{ ...CASE, state: 'BID_PLANNED', caseVersion: 3 }]);
    // 실패 뒤 다시 읽기(onError)가 **앞선 version** 을 돌려준다 = 그 쓰기가 닿았다는 사실.
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_RECORDED', caseVersion: 4 }]);
    mocked.recordBid.mockRejectedValue(new ApiError(504, 'GATEWAY_TIMEOUT'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    const failure = await screen.findByTestId('write-failure');
    expect(mocked.recordBid).toHaveBeenCalledTimes(1);

    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));

    expect((await screen.findByTestId('toast')).textContent).toContain('움직였습니다');
    expect(mocked.recordBid).toHaveBeenCalledTimes(1);
  });

  /** 뒤집은 쪽 — 건이 움직이지 않았으면 그 진술을 **그대로 다시** 보낸다(위 test 와 한 쌍이다). */
  it('건이 움직이지 않았으면 같은 진술을 다시 보낸다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED', caseVersion: 3 }]);
    mocked.recordBid.mockRejectedValue(new ApiError(504, 'GATEWAY_TIMEOUT'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    const failure = await screen.findByTestId('write-failure');

    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));

    await waitFor(() => {
      expect(mocked.recordBid).toHaveBeenCalledTimes(2);
    });
    expect(mocked.recordBid.mock.calls[1]).toStrictEqual(mocked.recordBid.mock.calls[0]);
  });

  /**
   * CR r3 M-3 — 선언은 「그때 적었던 것」이 아니라 **지금 적혀 있는 것**이다. 앞 판은 누를 때의
   * 값을 다시 보내, 화면이 보이는 수와 wire 에 간 수가 갈릴 수 있었다.
   */
  it('투찰가 재시도는 지금 화면에 보이는 선언값을 보낸다', async () => {
    mocked.requestBidPrice.mockRejectedValue(new ApiError(503, 'UNAVAILABLE'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('active-bids'), { target: { value: '2' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    const failure = await screen.findByTestId('write-failure');
    expect(mocked.requestBidPrice.mock.calls[0]?.[0]).toMatchObject({ currentActiveBids: 2 });

    fireEvent.change(screen.getByTestId('active-bids'), { target: { value: '4' } });
    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));

    await waitFor(() => {
      expect(mocked.requestBidPrice).toHaveBeenCalledTimes(2);
    });
    expect(mocked.requestBidPrice.mock.calls[1]?.[0]).toMatchObject({ currentActiveBids: 4 });
  });

  /** CR r3 M-2 — 얼굴이 「다시 읽었습니다」라고 말하면 그 말이 사실이어야 한다. */
  it('투찰가 요청 실패도 서버 상태를 다시 읽는다', async () => {
    mocked.requestBidPrice.mockRejectedValue(new ApiError(503, 'UNAVAILABLE'));
    renderScreen();

    const before = mocked.listBidCases.mock.calls.length;
    await askPrice();

    const failure = await screen.findByTestId('write-failure');
    expect(failure.textContent).toContain('다시 읽었습니다');
    await waitFor(() => {
      expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(before);
    });
  });

  /** v r2 M-3 — 넷 가운데 하나만 「다시 읽기」가 잠겨 있었다. 초안·다시 보기도 잰다. */
  it('초안 실패도 서버 상태를 다시 읽는다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'PRICE_RECEIVED' }]);
    mocked.draftBid.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    fireEvent.change(await screen.findByTestId('record-amount'), { target: { value: '1100000000' } });
    fireEvent.change(await screen.findByTestId('record-vat'), { target: { value: 'INCLUSIVE' } });
    const before = mocked.listBidCases.mock.calls.length;
    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    fireEvent.click(await screen.findByTestId('bid-confirm-accept'));

    await screen.findByTestId('write-failure');
    await waitFor(() => {
      expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(before);
    });
  });

  it('다시 보기 실패도 얼굴을 내고 서버 상태를 다시 읽는다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'DEFERRED' }]);
    mocked.resumeCase.mockRejectedValue(new ApiError(409, 'CONFLICT'));
    renderScreen();

    const before = mocked.listBidCases.mock.calls.length;
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    expect((await screen.findByTestId('write-failure')).textContent).toContain('되돌리지 못했습니다');
    await waitFor(() => {
      expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(before);
    });
  });

  /**
   * CR r3 L-5 — 앞 라운드의 a11y 수정 둘에 **잠금이 없었다**(지우면 전 층이 초록이었다).
   * 선언 입력의 보조 문면이 **프로그램적으로** 묶이는지와, 차단이 `aria-invalid` 로 드러나는지.
   */
  it('선언 입력의 안내 문면이 입력에 묶이고, 차단은 aria-invalid 로 드러난다', async () => {
    renderScreen();

    const input = await screen.findByTestId('active-bids');
    const noteId = input.getAttribute('aria-describedby');
    expect(noteId, 'aria-describedby 가 없으면 안내가 입력과 끊긴 것이다').not.toBeNull();
    expect(document.getElementById(noteId ?? '')?.textContent).toContain('지어내지 않으므로');
    expect(input.getAttribute('aria-invalid')).toBeNull();

    // 선언 없이 누르면 차단이 선다 — 그때 입력 자신이 그 사실을 들고 있어야 한다.
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    expect((await screen.findByTestId('active-bids')).getAttribute('aria-invalid')).toBe('true');
    expect(mocked.requestBidPrice).not.toHaveBeenCalled();
  });

  /** CR r3 L-4 — 범위를 넘긴 선언은 **사유가 다르다**(「0 이상의 정수로」는 그에게 틀린 말이다). */
  it('범위를 넘긴 선언은 그 사유로 막는다', async () => {
    renderScreen();

    fireEvent.change(await screen.findByTestId('active-bids'), { target: { value: '99999999999999999999' } });
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    const blocked = await screen.findByTestId('active-bids-blocked');
    expect(blocked.textContent).toContain('2,147,483,647');
    expect(blocked.textContent).not.toContain('0 이상의 정수');
    expect(mocked.requestBidPrice).not.toHaveBeenCalled();
  });

  /**
   * CR r3 L-5 — 표 밖 코드로 목록이 그려지는지(r2 M-6 의 잠금). 직접 색인이 돌아오면 `undefined`
   * 가 렌더되어 **빈 항목**이 서는데, 그것은 「고지가 없다」로 읽힌다.
   */
  it('고지 표에 없는 코드도 미지 문면으로 선다 — 빈 항목이 되지 않는다', async () => {
    // 계약 **밖** 코드를 서버가 보내는 상황이다. 타입은 그것을 막지만 wire 는 막지 못하므로
    // (그래서 `displayOrUnknown` 이 있다) 여기서만 캐스팅으로 그 상황을 만든다.
    const outsideContract = 'SOMETHING_NOT_IN_TABLE' as unknown as (typeof PRICE)['disclosures'][number];
    mocked.requestBidPrice.mockResolvedValue({ ...PRICE, disclosures: [outsideContract] });
    renderScreen();

    await askPrice();

    const list = await screen.findByTestId('disclosures');
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(1);
    expect(items[0]?.textContent?.trim().length ?? 0).toBeGreaterThan(0);
    expect(items[0]?.textContent).not.toContain('undefined');
  });

  /**
   * CR r4 L-1 — r3 L-5 의 넷 가운데 **`KeyValueRow` 자리**가 아직 잠기지 않았다(실측: `aria-describedby`·
   * `hintNode` 를 걷어도 200 전부 초록). 화면 C 의 기초금액이 화면 B 와 **같은 문면**을 같은
   * `KeyValueRow` 로 낸다 — r2 M-4 가 고친 원래 자리이고 r3 L-10 이 내용 모형을 고친 자리다.
   */
  it('기초금액의 아래층 문면이 보조기기에 닿는다 — KeyValueRow 자리도 잠근다', async () => {
    renderScreen();

    const value = await screen.findByTestId('worksheet-amount');
    const slot = value.closest('dd');
    const describedBy = slot?.getAttribute('aria-describedby');

    expect(describedBy, 'aria-describedby 가 없으면 title 하나로 돌아간 것이다').not.toBeNull();
    expect(document.getElementById(describedBy ?? '')?.textContent).toContain('추정가격과 다르다');
    // 설명은 **그 `<dd>` 안**에 있고(한 용어에 값 하나), 값 단언은 그것을 읽지 않는다.
    expect(slot?.contains(document.getElementById(describedBy ?? ''))).toBe(true);
    expect(value.textContent).not.toContain('추정가격과 다르다');
  });

  /**
   * CR r4 L-2 — 401 `.some()` 수정에 잠금이 없었다(실측: `??` 연쇄로 되돌려도 200 초록).
   * r3 L-2 가 적은 상황을 그대로 쓴다 — **공고 읽기 500 + 건 목록 401**.
   */
  it('앞의 오류가 비 401 이어도 뒤의 401 을 보고 로그인 얼굴을 낸다', async () => {
    mocked.searchNotices.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    mocked.listBidCases.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    const face = await screen.findByTestId('signed-out');
    expect(within(face).getByRole('link', { name: /로그인/u }).getAttribute('href')).toBe('/login');
  });

  /**
   * CR r4 L-3 — 되돌림 재시도만 `writeRetry` 밖에 남아 있었다. 서버가 되돌림을 409 로 막는 것은
   * 이 라운드가 직접 쟀으므로, 닿은 되돌림을 다시 보내면 **성공한 작업에 실패 얼굴**이 선다.
   */
  it('닿은 되돌림은 다시 보내지 않는다 — 네 쓰기가 같은 길로 간다', async () => {
    mocked.listBidCases.mockResolvedValueOnce([{ ...CASE, state: 'DEFERRED', caseVersion: 2 }]);
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'WATCHING', caseVersion: 3 }]);
    mocked.resumeCase.mockRejectedValue(new ApiError(504, 'GATEWAY_TIMEOUT'));
    renderScreen();

    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    const failure = await screen.findByTestId('write-failure');
    expect(mocked.resumeCase).toHaveBeenCalledTimes(1);

    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));

    expect((await screen.findByTestId('toast')).textContent).toContain('움직였습니다');
    expect(mocked.resumeCase).toHaveBeenCalledTimes(1);
  });

  /** CR r2 L-5 — 투찰가 요청(POST)도 쓰기와 같은 얼굴·같은 401 갈래를 쓴다. */
  it('투찰가 요청이 401 이면 로그인으로 가는 길을 보인다', async () => {
    mocked.requestBidPrice.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    await askPrice();

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

  /**
   * 명령 칸은 **표의 문면**이다(`OPEN-M9-TIMELINE-COMMAND-WORDING`). 앞 판은 서버가 준 토큰
   * 원문이 그대로 섰고, 그것이 타임라인의 마지막 원문 축이었다.
   */
  it('타임라인의 명령은 문면으로 서고 토큰 원문이 아니다', async () => {
    renderScreen();

    const timeline = await screen.findByTestId('timeline');
    expect(timeline.textContent).toContain('입찰함에 담음');
    expect(timeline.textContent, '명령 토큰 원문이 그대로 섰다').not.toContain('ADD_TO_BID_BOX');
  });

  /**
   * **상태 문면도 표시 모듈의 공용 조회를 지난다**(M11/11B D-11B-3). 앞 판은 이 화면만 자기
   * 폴백을 적어, 표 밖 값에 서버가 준 코드를 문면 자리에 **그대로** 세웠다 — 그러면 화면이
   * 계약에 없는 상태를 아는 것처럼 보인다.
   *
   * 표 밖 값은 계약이 어휘를 닫은 뒤로 **타입으로 만들 수 없다**(그것이 닫은 요점이다). 그래서
   * `string` 에서 좁혀 세운다 — 그 값이 닿는 경로는 배포 비틀림 하나다. 기댓값을 공용 조회에서
   * 끌어오므로 이 단언은 **「두 자리가 같은 문면을 낸다」**를 재고, 화면이 자기 폴백으로 되돌아
   * 가면 그 자리에서 멈춘다.
   */
  it('표 밖 상태는 공용 조회의 문면으로 선다 — 코드 원문이 아니다', async () => {
    const outOfVocabulary: string = 'ON_HOLD';
    mocked.readTimeline.mockResolvedValue([
      {
        caseVersion: 0,
        fromState: null,
        toState: outOfVocabulary as queries.BidTimelineEntry['toState'],
        command: 'ADD_TO_BID_BOX',
        actorKind: 'USER',
        retroactive: false,
        occurredAt: '2026-10-29T05:00:00Z',
      },
    ]);
    renderScreen();

    const timeline = await screen.findByTestId('timeline');
    expect(timeline.textContent, '타임라인이 공용 조회 자리를 지나지 않았다').toContain(
      stateDisplayOf(outOfVocabulary).label,
    );
  });

  /**
   * **화살표 줄의 두 조회도 같은 자리를 지난다**(v M-1 · cr MEDIUM-1). 위 단언은 머리의 도착
   * 상태 하나만 보았고, 출발 → 도착 줄은 **생성 전이가 아닐 때만** 그려지므로 그 두 조회가
   * 잠기지 않은 채였다 — 승인된 표시 변경(D-11B-3)의 잠금이 세 자리 가운데 한 자리에만 서 있었다.
   *
   * 두 줄로 **양쪽을 따로** 가른다: 표 안 출발 + 표 밖 도착 · 표 밖 출발 + 표 안 도착. 한 줄로
   * 양쪽을 표 밖으로 두면 한 조회만 되돌려도 다른 조회의 문면이 남아 단언이 통과할 수 있다.
   *
   * 기댓값은 **화살표로 이은 통째**다 — 조각으로 재면 머리 줄의 같은 문면이 그 포함을 만족시킨다.
   * 표본은 도메인이 명시적으로 거부한 어휘 둘이다(「보류」는 어휘에 없다 — 「나중에」와 「건너뜀」
   * 으로 갈렸다 · 「후보(제안됨/검색됨)」도 이 집합에 없다 — 담는 순간 건이 태어난다). 상류가 그
   * 이름을 채택해 음성 대조가 조용히 공허해지는 자리를 피한다.
   */
  it('화살표 줄의 출발 · 도착도 공용 조회의 문면으로 선다', async () => {
    const outOfVocabularyTo: string = 'ON_HOLD';
    const outOfVocabularyFrom: string = 'PROPOSED';
    const row = (caseVersion: number, fromState: string, toState: string): queries.BidTimelineEntry => ({
      caseVersion,
      fromState: fromState as queries.BidTimelineEntry['fromState'],
      toState: toState as queries.BidTimelineEntry['toState'],
      command: 'RECORD_BID',
      actorKind: 'USER',
      retroactive: false,
      occurredAt: '2026-10-29T05:00:00Z',
    });
    mocked.readTimeline.mockResolvedValue([
      row(1, 'WATCHING', outOfVocabularyTo),
      row(2, outOfVocabularyFrom, 'WATCHING'),
    ]);
    renderScreen();

    const rows = within(await screen.findByTestId('timeline')).getAllByRole('listitem');
    expect(rows[0]?.textContent, '도착 쪽 조회가 공용 자리를 지나지 않았다').toContain(
      `${stateDisplayOf('WATCHING').label} → ${stateDisplayOf(outOfVocabularyTo).label}`,
    );
    expect(rows[1]?.textContent, '출발 쪽 조회가 공용 자리를 지나지 않았다').toContain(
      `${stateDisplayOf(outOfVocabularyFrom).label} → ${stateDisplayOf('WATCHING').label}`,
    );
  });
});

/**
 * M13/13C — **나라장터 넘김**(계약 받는 항목 2 · §6.2 C 4). 이 화면이 대신 제출하지 않으므로
 * 운영자는 금액을 손으로 옮긴다 — 그 옮기는 길에서 나는 실수(「0 을 하나 더/덜」·하한 미달)를
 * 화면이 줄인다: 복사 · 한글 병기 · 하한 대비 여유 · 보내기 전 확인.
 */

/** 하한 여유가 **서는** 응답 — 근거가 `USABLE` 이고 기초금액의 과세 축이 선언돼 있다. */
const PRICED_WITH_FLOOR: queries.BidPriceRequestResponse = {
  ...PRICE,
  floorRateGroundsState: 'USABLE',
  publishedFloorRateFraction: '0.8',
  publishedFloorRateOrigin: 'NOTICE_VALUE',
  baseAmountWon: 1_000_000_000,
  baseAmountVatTreatment: 'INCLUSIVE',
};

/** 클립보드는 **브라우저가 줄 수도, 거부할 수도** 있다. 두 길을 따로 세운다. */
function stubClipboard(writeText: ((text: string) => Promise<void>) | null): void {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: writeText === null ? undefined : { writeText },
  });
}

afterEach(() => {
  Reflect.deleteProperty(navigator, 'clipboard');
});

/** 금액·과세를 적는 자리 — 복사와 확인이 같은 입력을 쓴다. */
function fillAmount(amount: string, vat = 'INCLUSIVE'): void {
  fireEvent.change(screen.getByTestId('record-amount'), { target: { value: amount } });
  fireEvent.change(screen.getByTestId('record-vat'), { target: { value: vat } });
}

describe('화면 C — 금액 복사', () => {
  it('복사를 누르면 원 단위 정수만 클립보드로 가고 그 사실이 보인다', async () => {
    const writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined);
    stubClipboard(writeText);
    renderScreen();
    await screen.findByTestId('koneps-copy');
    fillAmount('1050000000');

    fireEvent.click(screen.getByTestId('koneps-copy'));

    await waitFor(() => expect(writeText).toHaveBeenCalledWith('1050000000'));
    expect((await screen.findByTestId('koneps-copy-status')).textContent).toContain('복사했습니다');
  });

  /**
   * **거부는 조용히 지나가지 않는다.** 클립보드 권한은 브라우저·맥락에 따라 거부되고, 그때
   * 운영자가 할 수 있는 일은 **직접 고르는 것**이다 — 그래서 선택 가능한 원문을 그 자리에 낸다.
   */
  it('클립보드가 거부하면 사유와 선택 가능한 원문이 뜬다', async () => {
    stubClipboard(() => Promise.reject(new Error('denied')));
    renderScreen();
    await screen.findByTestId('koneps-copy');
    fillAmount('1050000000');

    fireEvent.click(screen.getByTestId('koneps-copy'));

    const status = await screen.findByTestId('koneps-copy-status');
    expect(status.textContent).toContain('복사하지 못했습니다');
    const fallback = await screen.findByTestId('koneps-copy-fallback');
    expect((fallback as HTMLInputElement).value).toBe('1050000000');
    expect(fallback.getAttribute('readonly')).not.toBeNull();
  });

  /** 클립보드 **자체가 없는** 맥락(비보안 출처 등)도 같은 길이다 — 아무 일도 안 일어나지 않는다. */
  it('클립보드가 없는 맥락에서도 같은 길로 간다', async () => {
    stubClipboard(null);
    renderScreen();
    await screen.findByTestId('koneps-copy');
    fillAmount('1050000000');

    fireEvent.click(screen.getByTestId('koneps-copy'));

    expect((await screen.findByTestId('koneps-copy-status')).textContent).toContain('복사하지 못했습니다');
    expect((await screen.findByTestId('koneps-copy-fallback')) as HTMLInputElement).not.toBeNull();
  });

  /** **없는 값을 복사하지 않는다** — 금액을 적기 전에는 누를 자리가 막혀 있다. */
  it('금액을 적기 전에는 복사가 막혀 있다', async () => {
    const writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined);
    stubClipboard(writeText);
    renderScreen();

    const copy = await screen.findByTestId('koneps-copy');
    expect((copy as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(copy);
    expect(writeText).not.toHaveBeenCalled();
  });
});

describe('화면 C — 한글 금액 병기', () => {
  it('넘김 카드가 숫자와 한글 병기를 함께 보인다', async () => {
    renderScreen();
    await screen.findByTestId('handoff-amount');
    fillAmount('1050000000');

    expect((await screen.findByTestId('handoff-amount')).textContent).toContain('1,050,000,000');
    expect((await screen.findByTestId('handoff-amount-korean')).textContent).toContain('10억 5000만원');
  });
});

describe('화면 C — 하한 대비 여유', () => {
  it('근거가 서고 과세 축이 같으면 여유가 선다', async () => {
    mocked.requestBidPrice.mockResolvedValue(PRICED_WITH_FLOOR);
    renderScreen();
    await askPrice();
    fillAmount('820000000');

    const margin = await screen.findByTestId('floor-margin');
    // **율 축이다** — 하한 금액이 아니라 임계 사정률과 두 율을 보인다(verifier H-1).
    expect(margin.textContent).toContain('임계 사정률 1.0250');
    expect(margin.textContent).toContain('투찰율 0.8200');
    expect(margin.textContent).toContain('게시 하한율 0.8');
    expect(margin.textContent, '실격을 단정한다').not.toMatch(/걸러진|실격/u);
  });

  it('임계가 1 아래면 그 사실이 보인다 — 추첨이 기초금액과 같아도 하한 아래다', async () => {
    mocked.requestBidPrice.mockResolvedValue(PRICED_WITH_FLOOR);
    renderScreen();
    await askPrice();
    fillAmount('780000000');

    const margin = await screen.findByTestId('floor-margin');
    expect(margin.textContent).toContain('예정가격이 기초금액과 같아도 하한 아래');
    expect(margin.textContent).toContain('임계 사정률 0.9750');
  });

  /** 과세 축이 다르면 **재지 않는다** — 0% 나 대시로 접지 않는다(계약). */
  it('과세 축이 다르면 사유가 보이고 숫자가 없다', async () => {
    mocked.requestBidPrice.mockResolvedValue(PRICED_WITH_FLOOR);
    renderScreen();
    await askPrice();
    fillAmount('820000000', 'EXCLUSIVE');

    const margin = await screen.findByTestId('floor-margin');
    expect(margin.textContent).toContain('과세 구분이 서로 달라');
    expect(margin.textContent).not.toContain('%');
  });

  it('근거가 서지 않는 상태에서는 사유만 있고 0% 가 없다', async () => {
    renderScreen();
    await askPrice();
    fillAmount('820000000');

    const margin = await screen.findByTestId('floor-margin');
    expect(margin.textContent).toContain('근거로 쓸 수 없어');
    expect(margin.textContent).not.toContain('0%');
  });

  /** 투찰가를 요청하기 **전에는** 하한율 자체가 없다 — 그 자리를 만들지 않는다. */
  it('요청 전에는 여유 자리가 없다', async () => {
    renderScreen();
    await screen.findByTestId('handoff-amount');

    expect(screen.queryByTestId('floor-margin')).toBeNull();
  });
});

/**
 * **확인 단계는 「투찰 예정 초안」에만 선다**(§6.2 C 3 「안을 고르면 확인 → 초안이 만들어진다」).
 * 「제출함」은 §6.2 C 5 가 「한 번으로 ④ 완료」로 못박은 자리라 확인을 더하지 않는다.
 */
describe('화면 C — 보내기 전 확인', () => {
  it('초안을 누르면 확인이 뜨고 아직 보내지 않는다', async () => {
    renderScreen();
    await askPrice();
    fillAmount('1050000000');

    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    expect(await screen.findByTestId('bid-confirm')).not.toBeNull();
    expect(mocked.draftBid).not.toHaveBeenCalled();
  });

  it('확인은 보낼 금액을 숫자와 한글로 함께 보인다', async () => {
    renderScreen();
    await askPrice();
    fillAmount('1050000000');
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    const confirm = await screen.findByTestId('bid-confirm');
    expect(confirm.textContent).toContain('이 금액으로 투찰하시겠습니까');
    expect(confirm.textContent).toContain('1,050,000,000');
    expect(confirm.textContent).toContain('10억 5000만원');
  });

  it('확인을 누르면 그 금액으로 초안이 간다', async () => {
    mocked.draftBid.mockResolvedValue({ ...CASE, state: 'BID_PLANNED', caseVersion: 1 });
    renderScreen();
    await askPrice();
    fillAmount('1050000000');
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    fireEvent.click(await screen.findByTestId('bid-confirm-accept'));

    await waitFor(() =>
      expect(mocked.draftBid).toHaveBeenCalledWith(
        CASE.caseId,
        expect.objectContaining({ amountWon: 1_050_000_000, vatTreatment: 'INCLUSIVE' }),
      ),
    );
  });

  it('취소하면 보내지 않고 적은 금액이 남는다', async () => {
    renderScreen();
    await askPrice();
    fillAmount('1050000000');
    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    fireEvent.click(await screen.findByTestId('bid-confirm-cancel'));

    expect(screen.queryByTestId('bid-confirm')).toBeNull();
    expect(mocked.draftBid).not.toHaveBeenCalled();
    expect((screen.getByTestId('record-amount') as HTMLInputElement).value).toBe('1050000000');
  });

  /**
   * **확인한 금액과 보내는 금액이 갈리지 않는다.** 확인을 열어 둔 채 입력을 고치면 그 확인은
   * 다른 수를 가리킨다 — 그때 확인은 **닫힌다**(다시 누르면 새 금액으로 다시 묻는다).
   */
  it('확인을 열어 둔 채 금액을 고치면 확인이 닫힌다', async () => {
    renderScreen();
    await askPrice();
    fillAmount('1050000000');
    fireEvent.click(await screen.findByTestId('worksheet-primary'));
    await screen.findByTestId('bid-confirm');

    fireEvent.change(screen.getByTestId('record-amount'), { target: { value: '1050000001' } });

    expect(screen.queryByTestId('bid-confirm')).toBeNull();
    expect(mocked.draftBid).not.toHaveBeenCalled();
  });

  it('금액·과세가 없으면 확인이 뜨지 않고 무엇이 없는지 말한다', async () => {
    renderScreen();
    await askPrice();

    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    expect(screen.queryByTestId('bid-confirm')).toBeNull();
    expect((await screen.findByTestId('record-blocked')).textContent).toContain('투찰 금액');
  });

  it('「제출함」에는 확인 단계가 없다 — 한 번 누르면 기록된다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED', caseVersion: 1 }]);
    mocked.recordBid.mockResolvedValue({ ...CASE, state: 'BID_RECORDED', caseVersion: 2 });
    renderScreen();
    await screen.findByTestId('record-amount');
    fillAmount('1050000000');

    fireEvent.click(await screen.findByTestId('worksheet-primary'));

    await waitFor(() => expect(mocked.recordBid).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId('bid-confirm')).toBeNull();
  });
});

/**
 * **점진적 공개**(계약 받는 항목 11). 판정·근거·자격·고지 네 절이 요약 한 줄로 서고 펼치면
 * 전부 보인다. 요약은 **그 절의 핵심 상태**를 들어, 펼치지 않고도 판정을 읽을 수 있다.
 */
describe('화면 C — 네 절의 접기', () => {
  const sections = [
    ['판정', 'verdict', '판정 전에 멈춤'],
    ['3안 자리', 'bid-price-menu', '아직 산출 안 함'],
    ['자격', 'qualification', '면허 판정 불가'],
    ['고지', 'disclosures-section', '고지'],
  ] as const;

  it.each(sections)('%s 절이 접힌 상태로 서고 요약이 핵심 상태를 든다', async (_label, testId, summaryText) => {
    renderScreen();
    await askPrice();

    const section = await screen.findByTestId(testId);
    expect(section.tagName.toLowerCase()).toBe('details');
    expect((section as HTMLDetailsElement).open).toBe(false);
    const summary = within(section).getByTestId(`${testId}-summary`);
    expect(summary.textContent).toContain(summaryText);
  });

  it('근거 절도 같은 꼴이다', async () => {
    renderScreen();
    await askPrice();

    const section = await screen.findByTestId('grounds-section');
    expect((section as HTMLDetailsElement).open).toBe(false);
    expect(within(section).getByTestId('grounds-section-summary').textContent).toContain('게시 하한율');
  });

  it('펼치면 접혀 있던 값이 그 자리에 있다', async () => {
    renderScreen();
    await askPrice();
    const section = await screen.findByTestId('grounds-section');

    (section as HTMLDetailsElement).open = true;

    expect(within(section).getByTestId('base-amount').textContent).toContain('1,200,000,000');
  });

  /**
   * **요약 줄은 손가락이 누르는 자리다.** 다섯 절이 같은 한 자리에서 최소 높이를 받고, 그 토큰이
   * **공유 컨트롤 표와 같은 것**인지 두 글자를 맞대서 잰다 — 44px 를 두 곳에 적어 두면 한 곳만
   * 바뀐다. 상자로 재는 축은 390px 여정인데 그 fixture 가 이 절을 펼치지 않는다(알려진 제한).
   */
  it('다섯 요약이 한 자리에서 최소 높이를 받고 그 토큰이 공유 컨트롤과 같다', async () => {
    renderScreen();
    await askPrice();

    const minHeightTokenOf = (classes: string): string | undefined =>
      classes.split(/\s+/u).find((one) => one.startsWith('min-h-'));
    expect(minHeightTokenOf(SECTION_SUMMARY_CLASS)).toBe(minHeightTokenOf(controlClass('secondary', 'md')));
    for (const [, testId] of sections) {
      const summary = within(await screen.findByTestId(testId)).getByTestId(`${testId}-summary`);
      expect(summary.className, `${testId} 의 요약이 공용 자리를 지나지 않았다`).toBe(SECTION_SUMMARY_CLASS);
    }
    const grounds = within(await screen.findByTestId('grounds-section')).getByTestId('grounds-section-summary');
    expect(grounds.className).toBe(SECTION_SUMMARY_CLASS);
  });
});

/**
 * **담기와 다음 걸음**(계약 받는 항목 3·7 의 워크시트 자리). 서버는 같은 (공고, 종류)에 건을
 * 하나만 두므로(이미 담긴 건이면 그 건을 돌려준다) 이 길은 중복을 만들지 않는다.
 */
describe('화면 C — 담기와 빈 화면의 다음 걸음', () => {
  it('입찰함에 없는 공고는 그 자리에서 담을 수 있다', async () => {
    mocked.listBidCases.mockResolvedValue([]);
    mocked.addBidCase.mockResolvedValue(CASE);
    renderScreen();

    fireEvent.click(await screen.findByTestId('worksheet-add-case'));

    await waitFor(() =>
      expect(mocked.addBidCase).toHaveBeenCalledWith({
        noticeNumber: NOTICE.noticeNumber,
        noticeRound: NOTICE.noticeRound,
        bidKind: 'REAL',
      }),
    );
  });

  it('담기가 실패하면 그 사실이 뜨고 다시 시도가 있다', async () => {
    mocked.listBidCases.mockResolvedValue([]);
    mocked.addBidCase.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    fireEvent.click(await screen.findByTestId('worksheet-add-case'));

    const failure = await screen.findByTestId('write-failure');
    expect(failure.textContent).toContain('입찰함에 담지');
    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(mocked.addBidCase).toHaveBeenCalledTimes(2));
  });

  /** 빈 얼굴이 **다음 걸음**을 든다(계약 받는 항목 7 — 오늘 그 자리를 넘기는 호출부가 0 이다). */
  it('공고를 찾지 못한 빈 얼굴에 다음 걸음이 있다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_MATCH' });
    renderScreen();

    const face = await screen.findByTestId('worksheet-missing');
    const next = within(face).getByTestId('worksheet-missing-next');
    expect(next.getAttribute('href')).toBe('/');
  });
});

/**
 * M13/13C 수정 라운드 1 — code-review M-3 · M-5 · L-7.
 */
describe('화면 C — 접힌 절의 펼침 표지 (cr M-3)', () => {
  it('요약마다 펼침 표지가 있다 — 커서만으로는 손가락에 보이지 않는다', async () => {
    renderScreen();
    await askPrice();

    for (const testId of ['verdict', 'bid-price-menu', 'qualification', 'grounds-section', 'disclosures-section']) {
      const summary = within(await screen.findByTestId(testId)).getByTestId(`${testId}-summary`);

      expect(
        within(summary).queryByTestId('disclosure-marker'),
        `${testId} 의 요약에 펼침 표지가 없다`,
      ).not.toBeNull();
    }
  });
});

describe('화면 C — 다룰 수 없는 금액 (cr M-5)', () => {
  const UNSAFE = '99999999999999999';

  it('안전 정수 밖 금액은 복사도 병기도 하지 않는다', async () => {
    const writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined);
    stubClipboard(writeText);
    renderScreen();
    await screen.findByTestId('koneps-copy');
    fillAmount(UNSAFE);

    expect((screen.getByTestId('koneps-copy') as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByTestId('koneps-copy'));
    expect(writeText).not.toHaveBeenCalled();
    expect(screen.queryByTestId('handoff-amount-korean')).toBeNull();
    expect((await screen.findByTestId('handoff-amount')).textContent).toContain('다룰 수 없는');
  });
});

/**
 * M13/13C — **자격 판정 규칙 판본 칸**(팀장 판정 2026-11-18). 서버가 주는 출처 문자열은 QUAL-05 의
 * **재현용 값**이지 사용자 문면이 아니다(오늘 그 값에 개발 식별자가 들어 있다). 화면은 그 원문을
 * 그리지 않고 **시행일로 판본**을 보인다 — 원문은 API 와 서버에 그대로 남아 재현성을 잃지 않는다.
 */
describe('화면 C — 규칙 판본은 시행일로 보인다', () => {
  it('투찰가 응답이 있는 상태에서 그 칸이 서고, 원문·개발 식별자를 그리지 않는다', async () => {
    renderScreen();
    await askPrice();

    // 이 단언이 **그 상태**(투찰가 응답이 있는 워크시트)를 실제로 그린다는 것을 먼저 든다.
    expect(await screen.findByTestId('qualification')).not.toBeNull();
    const cell = await screen.findByTestId('license-policy');

    expect(cell.textContent).toContain('2026-09-06');
    expect(cell.textContent, '서버의 재현용 출처 원문이 그대로 그려졌다').not.toContain(PRICE.licensePolicySource);
    expect(cell.textContent, '개발 식별자가 화면에 섰다').not.toMatch(/OPEN-[A-Z0-9-]+/u);
  });

  /**
   * **서버의 실제 꼴은 이쪽이다**(verifier R1-M4) — 오늘 정책 표의 항목이 최초 판 하나뿐이라
   * 시행일이 `null` 로 온다. 그래서 **부재 단언을 이 갈래에도** 건다: 원문과 개발 식별자가 0 인
   * 것을 운영 상태에서 재지 않으면, 「`null` 일 때만 원문을 덧붙이는」 변이가 조용히 초록이다.
   */
  it('시행일이 null 이면 최초 판이라는 사실을 말하고, 그 상태에서도 원문을 그리지 않는다', async () => {
    mocked.requestBidPrice.mockResolvedValue({ ...PRICE, licensePolicyEffectiveFrom: null });
    renderScreen();
    await askPrice();

    const cell = await screen.findByTestId('license-policy');
    expect(cell.textContent).toContain('최초 판');
    expect(cell.textContent, '서버의 재현용 출처 원문이 그대로 그려졌다').not.toContain(PRICE.licensePolicySource);
    expect(cell.textContent, '개발 식별자가 화면에 섰다').not.toMatch(/OPEN-[A-Z0-9-]+/u);
  });

  /**
   * **부재를 사실로 읽지 않는다**(cr N-2). 계약이 그 칸을 optional + nullable 로 두므로 「서버가
   * 보내지 않았다」와 「최초 판이다」는 **다른 사실**이다 — 앞은 판본을 모르는 것이고, 그것을
   * 「최초 판」으로 단정하면 재현 축에서 틀린 판본을 믿게 된다.
   */
  it('서버가 그 칸을 아예 보내지 않으면 최초 판이라고 단정하지 않는다', async () => {
    // 그 칸을 **아예 담지 않은** 응답이다(계약이 optional 로 둔 자리 — 배포 비틀림의 꼴).
    const withoutField: queries.BidPriceRequestResponse = { ...PRICE };
    delete withoutField.licensePolicyEffectiveFrom;
    mocked.requestBidPrice.mockResolvedValue(withoutField);
    renderScreen();
    await askPrice();

    const cell = await screen.findByTestId('license-policy');
    expect(cell.textContent, '부재를 최초 판이라는 사실로 읽었다').not.toContain('최초 판');
    expect(cell.textContent).toContain('받지 못했');
  });
});

/**
 * **내려놓기 둘이 워크시트에 선다**(M13/13C 머지 차례 · 계약 받는 항목 3 의 잔여).
 *
 * 13D 가 화면 D 에 세운 것을 **그 표와 그 쿼리로** 여기 잇는다 — 열린 집합은 `exitsOf` 가 정하고
 * (상태 전수 루프로 잠근다), 폼은 13D 의 `ActionForm` 하나이며, 「나중에」의 역전이는 이 화면이
 * 이미 가진 `resumeCase` 다. 새 사본을 만들지 않는 것이 이 자리의 요점이다.
 */
describe('화면 C — 건을 내려놓는다', () => {
  const EXIT_TEST_ID = { DEFER: 'worksheet-exit-DEFER', SKIP: 'worksheet-exit-SKIP' } as const;

  /** 상태 전수 — 표가 정한 집합과 **화면이 그린 집합**이 같은지 본다(화면이 상태를 보고 고르지 않는다). */
  it.each(Object.keys(BID_CASE_STATE_DISPLAY) as readonly queries.BidCase['state'][])(
    '%s 에서 그려지는 내려놓기가 표와 같다',
    async (state) => {
      mocked.listBidCases.mockResolvedValue([{ ...CASE, state }]);
      renderScreen();
      await screen.findByTestId('worksheet-case-state');

      const expected = exitsOf(state).map((one) => one.kind);
      const drawn = (['DEFER', 'SKIP'] as const).filter(
        (kind) => screen.queryByTestId(EXIT_TEST_ID[kind]) !== null,
      );

      expect(drawn).toStrictEqual(expected);
    },
  );

  it('나중에는 리마인드 시각을 받아 보내고, 되돌릴 길을 함께 낸다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'WATCHING' }]);
    mocked.deferCase.mockResolvedValue({ ...CASE, state: 'DEFERRED', caseVersion: 1 });
    renderScreen();

    fireEvent.click(await screen.findByTestId(EXIT_TEST_ID.DEFER));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.DEFER.label ?? ''), {
      target: { value: '2026-12-01T09:00' },
    });
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() => expect(mocked.deferCase).toHaveBeenCalledTimes(1));
    expect(mocked.deferCase.mock.calls[0]?.[0]).toBe(CASE.caseId);
    // 시각은 **wire 의 ISO** 로 간다 — 입력 글자가 그대로 실리지 않는다.
    expect(mocked.deferCase.mock.calls[0]?.[1]?.remindAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/u);
    expect((await screen.findByTestId('toast')).textContent).toContain('실행 취소');
  });

  it('건너뜀은 한 번 더 묻고, 사유를 적힌 그대로 보낸다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'BID_PLANNED' }]);
    mocked.skipCase.mockResolvedValue({ ...CASE, state: 'SKIPPED', caseVersion: 1 });
    renderScreen();

    fireEvent.click(await screen.findByTestId(EXIT_TEST_ID.SKIP));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.SKIP.label ?? ''), {
      target: { value: '  예산 초과  ' },
    });
    fireEvent.click(screen.getByTestId('action-send'));

    // 되돌릴 수 없는 전이는 **확인 한 단**을 지난다 — 첫 누름은 요청을 보내지 않는다.
    expect(mocked.skipCase).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('action-send'));

    await waitFor(() => expect(mocked.skipCase).toHaveBeenCalledTimes(1));
    expect(mocked.skipCase.mock.calls[0]?.[1]?.reason).toBe('  예산 초과  ');
  });

  it('연타는 요청을 하나만 낸다 — 동기 가드가 막는다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'WATCHING' }]);
    mocked.deferCase.mockResolvedValue({ ...CASE, state: 'DEFERRED', caseVersion: 1 });
    renderScreen();

    fireEvent.click(await screen.findByTestId(EXIT_TEST_ID.DEFER));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.DEFER.label ?? ''), {
      target: { value: '2026-12-01T09:00' },
    });
    const send = screen.getByTestId('action-send');
    // 같은 틱의 두 누름 — `disabled` 가 올라오는 재렌더보다 빠르다(13D 실측).
    send.click();
    send.click();

    await waitFor(() => expect(mocked.deferCase).toHaveBeenCalledTimes(1));
  });

  it('쓰기가 실패하면 그 얼굴을 내고 건을 다시 읽는다', async () => {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'WATCHING' }]);
    mocked.deferCase.mockRejectedValue(new Error('409'));
    renderScreen();

    fireEvent.click(await screen.findByTestId(EXIT_TEST_ID.DEFER));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.DEFER.label ?? ''), {
      target: { value: '2026-12-01T09:00' },
    });
    fireEvent.click(screen.getByTestId('action-send'));

    expect(await screen.findByTestId('write-failure')).toBeTruthy();
  });
});

/**
 * **머지 차례 수정 라운드 — 13D 의 defect ④ 가 반복된 자리**(cr M-1 · L-1).
 *
 * 가드는 「한 입구」일 때만 가드다. 다시 시도가 가드 밖에 있으면 같은 틱의 두 누름이 요청 둘을
 * 내고, 둘째의 409 가 **첫째의 성공 위에 실패 얼굴**을 세운다 — 이 저장소가 이미 두 번 고친 꼴이다.
 */
describe('화면 C — 내려놓기의 입구는 하나다', () => {
  async function failedDefer(): Promise<void> {
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'WATCHING' }]);
    mocked.deferCase.mockRejectedValue(new Error('409'));
    renderScreen();

    fireEvent.click(await screen.findByTestId('worksheet-exit-DEFER'));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.DEFER.label ?? ''), {
      target: { value: '2026-12-01T09:00' },
    });
    fireEvent.click(screen.getByTestId('action-send'));
    await screen.findByTestId('write-failure');
  }

  it('다시 시도 연타는 요청을 하나만 더 낸다', async () => {
    await failedDefer();
    expect(mocked.deferCase).toHaveBeenCalledTimes(1);

    const retry = within(screen.getByTestId('write-failure')).getByRole('button', { name: '다시 시도' });
    // 같은 틱의 두 누름 — `disabled` 가 올라오는 재렌더보다 손가락이 빠르다(13D 실측).
    retry.click();
    retry.click();

    await waitFor(() => expect(mocked.deferCase.mock.calls.length).toBeGreaterThan(1));
    expect(mocked.deferCase).toHaveBeenCalledTimes(2);
  });

  /**
   * **실패가 성공한 내려놓기의 되돌릴 길을 걷지 않는다**(cr L-1 · 13D 가 같은 자리에 둔 예외).
   * 되돌릴 수 있는데 되돌릴 길이 화면에서 사라지는 것이 가장 비싼 손해다.
   */
  it('뒤이은 내려놓기가 실패해도 실행 취소 토스트는 남는다', async () => {
    mocked.listBidCases.mockResolvedValueOnce([{ ...CASE, state: 'WATCHING' }]);
    mocked.listBidCases.mockResolvedValue([{ ...CASE, state: 'DEFERRED', caseVersion: 1 }]);
    mocked.deferCase.mockResolvedValue({ ...CASE, state: 'DEFERRED', caseVersion: 1 });
    mocked.skipCase.mockRejectedValue(new Error('409'));
    renderScreen();

    fireEvent.click(await screen.findByTestId('worksheet-exit-DEFER'));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.DEFER.label ?? ''), {
      target: { value: '2026-12-01T09:00' },
    });
    fireEvent.click(screen.getByTestId('action-send'));
    expect((await screen.findByTestId('toast')).textContent).toContain('실행 취소');

    // 같은 건에서 건너뜀을 시도해 실패시킨다 — 그 실패가 위 토스트를 걷으면 안 된다.
    fireEvent.click(await screen.findByTestId('worksheet-exit-SKIP'));
    fireEvent.change(await screen.findByLabelText(ACTION_INPUT.SKIP.label ?? ''), {
      target: { value: '예산 초과' },
    });
    fireEvent.click(screen.getByTestId('action-send'));
    fireEvent.click(screen.getByTestId('action-send'));
    await screen.findByTestId('write-failure');

    expect(screen.queryByTestId('toast')?.textContent, '실패가 되돌릴 길을 걷었다').toContain('실행 취소');
  });
});
