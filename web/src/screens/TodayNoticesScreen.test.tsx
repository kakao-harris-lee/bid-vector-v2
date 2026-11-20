// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { absenceTextOf } from '../display/absence';
import { localInputToIso } from '../display/timeDisplay';

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

const SUGGESTION: queries.SuggestedNotice = {
  ...NOTICE,
  licenseVerdict: 'ELIGIBLE',
  licenseUncertainReason: null,
  unjudgedAxes: [],
};

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/', element: <TodayNoticesScreen /> },
      { path: '/settings', element: <p>설정 자리</p> },
    ],
    { initialEntries: ['/'] },
  );
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

/**
 * **검색을 지나야 결과 목록이 선다**(M13/13E, 받는 항목 8). 기본 화면은 제안이므로, 결과
 * 카드를 재는 test 는 모두 이 걸음을 지난다 — 앞 판은 마운트만으로 전체 목록이 떠서 운영자가
 * 매일 보는 제안이 아래로 밀렸다.
 */
async function searchFor(query: string): Promise<void> {
  fireEvent.change(screen.getByLabelText(/공고명·공고번호/u), { target: { value: query } });
  fireEvent.click(screen.getByRole('button', { name: '검색' }));
  await waitFor(() => expect(mocked.searchNotices).toHaveBeenCalled());
}

/**
 * 칩 하나를 펼치고 그 안의 칸에 값을 넣는다 — 조건은 **칩 안**에 산다(§6.5 「필터는 칩」).
 *
 * **이미 펼쳐져 있으면 누르지 않는다**: 칩은 토글이라 같은 칩의 둘째 칸을 채우려고 다시 누르면
 * 접힌다(한 칩에 칸이 둘인 축이 금액대·마감일이다).
 */
function fillChip(chip: string, label: RegExp, value: string): void {
  const button = screen.getByTestId(`chip-${chip}`);
  if (button.getAttribute('aria-expanded') !== 'true') {
    fireEvent.click(button);
  }
  fireEvent.change(within(screen.getByTestId(`chip-panel-${chip}`)).getByLabelText(label), {
    target: { value },
  });
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

/**
 * **받는 항목 8 의 축**(M13/13E). 기본 목록은 제안이고, 검색하면 그 결과가 **그 자리를
 * 대신한다**. 두 목록이 함께 서면 운영자가 매일 보는 쪽이 아래로 밀린다.
 */
describe('화면 B — 기본은 제안, 검색이 그 자리를 대신한다', () => {
  it('검색하기 전에는 공고 검색을 **부르지 않는다**', async () => {
    renderScreen();

    await screen.findByTestId('suggestions-empty');
    expect(mocked.searchNotices, '검색 전에 전체 목록을 긁어 왔다').not.toHaveBeenCalled();
    expect(screen.queryByTestId('notice-total')).toBeNull();
    expect(screen.queryByTestId(`notice-card-${NOTICE.noticeNumber}`)).toBeNull();
  });

  it('검색하면 결과가 서고 제안 자리가 사라진다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 1, resultState: 'MATCHED' });
    renderScreen();

    await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`);
    await searchFor('SYN-7E1');

    expect(await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`)).not.toBeNull();
    await waitFor(() =>
      expect(
        screen.queryByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`),
        '결과가 제안을 대신하지 않고 둘이 함께 섰다',
      ).toBeNull(),
    );
  });

  it('조건을 지우면 다시 제안으로 돌아온다 — 돌아갈 길이 있다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 1, resultState: 'MATCHED' });
    renderScreen();

    await searchFor('SYN-7E1');
    await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    fireEvent.click(screen.getByTestId('search-clear'));

    expect(await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`)).not.toBeNull();
    expect(screen.queryByTestId(`notice-card-${NOTICE.noticeNumber}`)).toBeNull();
  });

  it('조건이 없으면 「조건 지우기」가 없다 — 지울 것이 없는 버튼을 두지 않는다', async () => {
    renderScreen();

    await screen.findByTestId('suggestions-empty');
    expect(screen.queryByTestId('search-clear')).toBeNull();
  });

  /**
   * **제안은 서버가 준 차례 그대로**다(§6.2 B 「마감 임박 × 자격 통과」). 화면이 다시 정렬하면
   * 서버의 게이트 순서와 갈리고, 그 차이를 운영자가 볼 방법이 없다.
   */
  it('제안 카드의 차례가 응답의 차례와 같다', async () => {
    const second: queries.SuggestedNotice = { ...SUGGESTION, noticeNumber: 'SYN-LATER', deadlineAt: null };
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION, second], total: 2, resultState: 'MATCHED' });
    renderScreen();

    await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`);
    const cards = screen.getAllByTestId(/^suggestion-card-/u).map((one) => one.getAttribute('data-testid'));

    expect(cards).toStrictEqual([`suggestion-card-${SUGGESTION.noticeNumber}`, `suggestion-card-${second.noticeNumber}`]);
  });

  /**
   * **표시 상한 초과를 말한다**(팀장 착수 판정 ⓐ). `GET /api/suggestions` 에 쪽 인자가 없어
   * 상한 초과분에 가는 길이 **없으므로**, 그 사실을 숨기면 운영자는 통과한 건을 다 본 줄 안다.
   */
  it('통과 전수가 받은 수보다 많으면 그 사실과 다음 걸음을 보인다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 9, resultState: 'MATCHED' });
    renderScreen();

    const line = await screen.findByTestId('suggestion-total');
    expect(line.textContent ?? '').toContain('9');
    expect(line.textContent ?? '').toContain('1');
    expect(
      within(line.parentElement as HTMLElement).getByRole('link', { name: /설정/u }).getAttribute('href'),
      '상한을 넓히러 갈 자리가 없다',
    ).toBe('/settings');
  });

  it('통과 전수와 받은 수가 같으면 그 줄이 없다 — 늘 참인 문면을 두지 않는다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 1, resultState: 'MATCHED' });
    renderScreen();

    await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`);
    expect(screen.queryByTestId('suggestion-total')).toBeNull();
  });
});

/**
 * **조건은 칩이다**(§6.5 「검색 우선·필터는 칩」). 칩은 **서버의 기존 파라미터**만 싣는다 —
 * 프리셋 구간을 지어내지 않으므로 정책 상수가 화면에 생기지 않는다.
 */
describe('화면 B — 조건 칩', () => {
  /**
   * 접힌 칸은 **DOM 에 남고 숨는다** — 지우면 `aria-controls` 가 없는 자리를 가리켜 보조기기가
   * 「무엇을 펼치는 버튼인지」를 잃는다. 숨기는 수단은 `hidden` 속성이다(전역 CSS 가 그 속성에
   * `display:none` 을 `!important` 로 걸어 두어 칸의 배치 클래스가 그것을 덮지 못한다).
   */
  it('칩 셋이 서고, 접힌 칸은 숨되 가리킬 수 있다', async () => {
    renderScreen();

    await screen.findByTestId('suggestions-empty');
    for (const chip of ['agency', 'amount', 'deadline']) {
      const button = screen.getByTestId(`chip-${chip}`);
      expect(button.getAttribute('aria-expanded'), `${chip} 칩이 처음부터 펼쳐져 있다`).toBe('false');
      const panel = document.getElementById(button.getAttribute('aria-controls') ?? '');
      expect(panel, `${chip} 칩의 aria-controls 가 없는 자리를 가리킨다`).not.toBeNull();
      expect(panel?.hidden, `${chip} 칩의 칸이 접히지 않았다`).toBe(true);
    }
  });

  it('칩을 누르면 그 칸이 드러나고 다시 누르면 숨는다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fireEvent.click(screen.getByTestId('chip-agency'));
    expect(screen.getByTestId('chip-panel-agency').hidden).toBe(false);

    fireEvent.click(screen.getByTestId('chip-agency'));
    expect(screen.getByTestId('chip-panel-agency').hidden, '칩이 한 방향 문이다').toBe(true);
  });

  it('기관 칩의 값이 서버 인자로 나간다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('agency', /기관명/u, '합성 수요기관');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(mocked.searchNotices).toHaveBeenCalledWith(
        expect.objectContaining({ agency: '합성 수요기관', page: 0 }),
      ),
    );
  });

  it('금액대 칩의 두 칸이 하한·상한으로 나간다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('amount', /하한/u, '100000000');
    fillChip('amount', /상한/u, '900000000');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(mocked.searchNotices).toHaveBeenCalledWith(
        expect.objectContaining({ amountMin: 100_000_000, amountMax: 900_000_000 }),
      ),
    );
  });

  /**
   * **마감일은 기존 파라미터 둘로 선다**(`deadlineFrom`·`deadlineTo`, `date-time`). 운영자가
   * 적은 지역 시각을 순간으로 바꾸는 자리는 `timeDisplay` **하나**다 — 이 test 가 그 함수를
   * 직접 불러 대조하므로, 화면이 자기 변환을 지으면 값이 갈려 붉는다.
   */
  it('마감일 칩의 값이 그 한 자리의 변환을 지나 나간다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('deadline', /부터/u, '2099-01-02T09:00');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(mocked.searchNotices).toHaveBeenCalledWith(
        expect.objectContaining({ deadlineFrom: localInputToIso('2099-01-02T09:00') }),
      ),
    );
  });

  /** **빈 칸은 조건이 아니다** — 빈 문자열·0 을 조건으로 보내면 서버가 다른 질문을 받는다. */
  it('비워 둔 칩은 인자를 보내지 않는다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');
    await searchFor('SYN-7E1');

    const sent = mocked.searchNotices.mock.calls[0]![0];
    expect(Object.keys(sent).sort()).toStrictEqual(['page', 'q', 'size']);
  });

  /**
   * **값이 선 칩은 그 값과 지우기를 보인다**(§6.5 칩). 지우면 그 인자만 빠지고 나머지 조건은
   * 남는다 — 한 칩이 전체 검색을 되돌리면 운영자가 조건을 다시 쌓아야 한다.
   */
  it('값이 선 칩은 그 값을 보이고, 지우면 그 인자만 빠진다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('agency', /기관명/u, '합성 수요기관');
    fireEvent.change(screen.getByLabelText(/공고명·공고번호/u), { target: { value: 'SYN-7E1' } });
    fireEvent.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(mocked.searchNotices).toHaveBeenCalled());

    expect(screen.getByTestId('chip-agency').textContent ?? '').toContain('합성 수요기관');
    fireEvent.click(screen.getByTestId('chip-clear-agency'));

    await waitFor(() => {
      const last = mocked.searchNotices.mock.calls.at(-1)![0];
      expect(last).not.toHaveProperty('agency');
      expect(last, '한 칩을 지우니 검색어까지 사라졌다').toHaveProperty('q', 'SYN-7E1');
    });
  });

  /**
   * **숫자 칸의 해석기가 하나다**(수정 라운드 1, 리뷰 H-1). 앞 판은 `conditionsOf` 가
   * `parseInt` 로 앞부분만 읽어 `100,000,000` 을 **100** 으로 보냈고, 칩 얼굴은 원문을 그대로
   * 보였다 — 운영자는 「1억 이상」을 보고 있다고 믿는데 wire 는 「100원 이상」이었다. 서버 계약이
   * 같은 자리에서 「정수 칸은 숫자와 선택적 부호만 받는다 · 형식 오류는 조용히 절삭·변환하지
   * 않고 400」이라고 못박은 자리다.
   */
  it('쉼표가 섞인 금액은 조용히 절삭되지 않고 요청이 나가지 않는다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('amount', /하한/u, '100,000,000');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(await screen.findByTestId('filter-problems')).toBeTruthy();
    expect(mocked.searchNotices, '잘린 값으로 요청이 나갔다').not.toHaveBeenCalled();
  });

  /**
   * **장면 ⓑ 의 음성**(리뷰 H-1): 「검색 중인가」와 「칩이 값을 보이는가」가 **같은 답**이어야
   * 한다. 앞 판은 숫자 축에서만 두 술어가 갈려, 칩은 값과 지우기를 보이는데 화면은 제안인
   * 틈이 생겼다.
   */
  it('숫자가 아닌 금액은 칩도 값을 보이지 않는다 — 조건과 얼굴이 같은 답이다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('amount', /하한/u, '₩1억');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(await screen.findByTestId('filter-problems')).toBeTruthy();
    // 조건이 서지 않았으므로 화면은 제안 그대로다.
    expect(screen.getByTestId('suggestions-empty')).toBeTruthy();
    // 그렇다면 칩도 값을 보이지 않아야 한다(둘이 같은 답이다).
    expect(screen.getByTestId('chip-amount').textContent ?? '', '조건이 아닌 값이 칩 얼굴에 섰다').not.toContain('1억');
    expect(screen.queryByTestId('chip-clear-amount'), '조건이 없는데 지우기가 섰다').toBeNull();
    expect(screen.queryByTestId('search-clear')).toBeNull();
  });

  /**
   * **서버가 400 으로 못박은 조합은 화면이 먼저 안다**(리뷰 M-1). 그 400 은 어느 입력이 틀렸는지
   * 싣지 않으므로, 보내고 나면 운영자가 받는 것은 **영원히 실패하는 「다시 시도」** 하나다.
   */
  it('범위가 뒤집힌 금액은 보내지 않고 사유를 보인다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('amount', /하한/u, '900000000');
    fillChip('amount', /상한/u, '100000000');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect((await screen.findByTestId('filter-problems')).textContent ?? '').toContain('하한');
    expect(mocked.searchNotices).not.toHaveBeenCalled();
  });

  it('음수 금액은 보내지 않고 사유를 보인다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('amount', /하한/u, '-1');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(await screen.findByTestId('filter-problems')).toBeTruthy();
    expect(mocked.searchNotices).not.toHaveBeenCalled();
  });

  it('마감일 범위가 뒤집히면 보내지 않고 사유를 보인다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('deadline', /부터/u, '2099-02-02T09:00');
    fillChip('deadline', /까지/u, '2099-01-01T09:00');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect((await screen.findByTestId('filter-problems')).textContent ?? '').toContain('마감일');
    expect(mocked.searchNotices).not.toHaveBeenCalled();
  });

  /** **음성 대조** — 규칙이 참을 막지 않는다. 제대로 적은 범위는 그대로 나간다. */
  it('제대로 적은 금액 범위는 그대로 나가고 사유 자리가 없다', async () => {
    renderScreen();
    await screen.findByTestId('suggestions-empty');

    fillChip('amount', /하한/u, '100000000');
    fillChip('amount', /상한/u, '900000000');
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(mocked.searchNotices).toHaveBeenCalledWith(
        expect.objectContaining({ amountMin: 100_000_000, amountMax: 900_000_000 }),
      ),
    );
    expect(screen.queryByTestId('filter-problems'), '올바른 입력에 사유가 섰다').toBeNull();
  });

  it('값이 없는 칩에는 지우기가 없다', async () => {
    renderScreen();

    await screen.findByTestId('suggestions-empty');
    expect(screen.queryByTestId('chip-clear-agency')).toBeNull();
  });
});

describe('화면 B — 카드가 보이는 값은 API 응답의 값이다', () => {
  it('공고명·기관·기초금액·상태를 API 가 준 그대로 보인다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).getByTestId('notice-title').textContent).toBe(NOTICE.title);
    expect(within(card).getByTestId('notice-agency').textContent).toBe(NOTICE.demandAgencyName);
    expect(within(card).getByTestId('notice-amount').textContent).toContain('1,200,000,000원');
    expect(within(card).getByTestId('notice-status').textContent).toBe('입찰 중');
  });

  /** 금액은 **provenance 와 한 값**이다(DEC-01) — 숫자만 떼어 보여 주지 않는다. */
  it('기초금액에는 basis·과세·provenance 가 함께 붙는다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    const provenance = within(card).getByTestId('notice-amount-provenance').textContent ?? '';
    // M9 D-3 — 세 축이 표의 문면으로 선다(앞 판은 `BASE_AMOUNT · … · PUBLISHED` 였다).
    expect(provenance).toContain('기초금액');
    expect(provenance).toContain('공고 게시값');
    for (const token of ['BASE_AMOUNT', 'PUBLISHED']) {
      expect(provenance, `${token} 이 화면에 남았다`).not.toContain(token);
    }
  });

  /**
   * **D-day 의 뜻을 말하는 문면**(PR #132 cr M-3). 숫자의 뜻이 「24시간 나눗셈의 내림」에서
   * 「KST 달력일의 차」로 바뀌었는데, 운영자가 그 뜻을 읽는 **유일한 산출물 문면**이 아직
   * 「내림」이라고 말하고 있었다. 모듈 주석·test·evidence 는 갱신됐고 화면 문면만 남았던 자리다.
   */
  it('D-day 의 설명 문면이 달력일 기준이라고 말한다 — 옛 「내림」이 아니다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const badge = within(card).getByTestId('notice-deadline');

    expect(badge.getAttribute('title')).toBe('마감일까지의 KST 달력일 차');
    expect(badge.getAttribute('title'), '옛 어림 문면이 남았다').not.toContain('내림');
  });

  it('금액이 없는 공고는 0 이 아니라 부재 어휘다', async () => {
    mocked.searchNotices.mockResolvedValue({
      rows: [{ ...NOTICE, baseAmountWon: null, baseAmountProvenance: null, baseAmountBasis: null }],
      total: 1,
      resultState: 'MATCHED',
    });
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).getByTestId('notice-amount').textContent).toBe(absenceTextOf('notSent'));
  });

  /** 전체 건수는 **헤더**가 정본이다 — 이 페이지의 길이로 세지 않는다. */
  it('전체 건수는 X-Total-Count 를 보인다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [NOTICE], total: 42, resultState: 'MATCHED' });
    renderScreen();
    await searchFor('SYN-7E1');

    expect((await screen.findByTestId('notice-total')).textContent).toContain('42');
  });

  /**
   * CR M-1 — 앞 판은 전체 42건을 보이면서 카드 20장만 내고 나머지로 가는 길이 없었다.
   * 「더 보기」가 다음 쪽을 **이어 붙인다**.
   */
  it('받은 수가 전체보다 적으면 「더 보기」가 있고, 누르면 다음 쪽을 이어 붙인다', async () => {
    const second: queries.NoticeSummary = { ...NOTICE, noticeNumber: 'SYN-7E1-SECOND' };
    mocked.searchNotices.mockResolvedValueOnce({ rows: [NOTICE], total: 2, resultState: 'MATCHED' });
    mocked.searchNotices.mockResolvedValueOnce({ rows: [second], total: 2, resultState: 'MATCHED' });
    renderScreen();
    await searchFor('SYN-7E1');

    const more = await screen.findByTestId('notices-more');
    fireEvent.click(more);

    expect(await screen.findByTestId(`notice-card-${second.noticeNumber}`)).not.toBeNull();
    expect(screen.getByTestId(`notice-card-${NOTICE.noticeNumber}`)).not.toBeNull();
    await waitFor(() => {
      expect(screen.queryByTestId('notices-more')).toBeNull();
    });
  });

  it('받은 수가 전체와 같으면 「더 보기」가 없다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [NOTICE], total: 1, resultState: 'MATCHED' });
    renderScreen();
    await searchFor('SYN-7E1');

    await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    expect(screen.queryByTestId('notices-more')).toBeNull();
  });

  /** v L-3 — 제안 목록의 오류 얼굴도 test 가 있다(분기 제거 변이가 지나갔던 자리). */
  it('제안 목록 오류에도 재시도가 있다', async () => {
    mocked.listSuggestions.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    const face = await screen.findByTestId('suggestions-error');
    expect(within(face).getByRole('button', { name: '다시 시도' })).not.toBeNull();
  });
});

describe('화면 B — 빈·로딩·오류 세 얼굴', () => {
  /** CR r2 L-6 — 떠 있는 화면에서 세션이 끝나면 읽기가 401 이다. 그 재시도는 영원히 실패한다. */
  it('읽기가 401 이면 로그인 얼굴이 뜬다', async () => {
    mocked.searchNotices.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();
    await searchFor('SYN-7E1');

    expect(await screen.findByTestId('signed-out')).not.toBeNull();
  });

  /** CR M-2 — 세션이 없으면 재시도가 아니라 로그인으로 보낸다(서버의 답을 소비한다). */
  it('세션이 없으면 로그인 얼굴이 뜬다', async () => {
    mocked.readSession.mockResolvedValue(null);
    renderScreen();

    const face = await screen.findByTestId('signed-out');
    expect(face.textContent).toContain('로그인이 필요합니다');
  });

  it('검색 적재 중에는 스켈레톤이 뜬다', async () => {
    mocked.searchNotices.mockReturnValue(new Promise(() => undefined));
    renderScreen();
    await searchFor('SYN-7E1');

    expect(screen.getByTestId('notices-loading')).not.toBeNull();
  });

  /** 제안도 **세 얼굴**을 쓴다 — 기본 화면이 그것이므로 적재 중을 빈 화면으로 접지 않는다. */
  it('제안 적재 중에는 스켈레톤이고 빈 얼굴이 아니다', () => {
    mocked.listSuggestions.mockReturnValue(new Promise(() => undefined));
    renderScreen();

    expect(screen.getByTestId('suggestions-loading')).not.toBeNull();
    expect(screen.queryByTestId('suggestions-empty')).toBeNull();
  });

  it('빈 결과 — 아직 수집 전이면 그 사실을 말한다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NOTHING_COLLECTED' });
    renderScreen();
    await searchFor('SYN-7E1');

    expect((await screen.findByTestId('notices-empty')).textContent).toContain('아직 수집된 공고가 없습니다');
  });

  it('빈 결과 — 조건이 좁은 것은 다른 문면이다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_MATCH' });
    renderScreen();
    await searchFor('SYN-7E1');

    expect((await screen.findByTestId('notices-empty')).textContent).toContain('조건에 맞는 공고가 없습니다');
  });

  /**
   * **빈 화면이 다음 걸음을 준다**(받는 항목 7). 다만 **모든 상태에 걸음이 있는 것은 아니다** —
   * 「아직 수집 전」은 운영자가 할 수 있는 일이 없고, 거기에 버튼을 두면 가짜 걸음이 된다.
   */
  it('조건이 좁은 빈 결과에는 조건을 지우는 걸음이 있다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_MATCH' });
    renderScreen();
    await searchFor('SYN-7E1');

    const face = await screen.findByTestId('notices-empty');
    fireEvent.click(within(face).getByRole('button', { name: /조건/u }));

    expect(await screen.findByTestId('suggestions-empty')).not.toBeNull();
  });

  it('수집 전 빈 결과에는 걸음을 지어내지 않는다', async () => {
    mocked.searchNotices.mockResolvedValue({ rows: [], total: 0, resultState: 'NOTHING_COLLECTED' });
    renderScreen();
    await searchFor('SYN-7E1');

    const face = await screen.findByTestId('notices-empty');
    expect(within(face).queryAllByRole('button'), '할 일이 없는 자리에 가짜 걸음을 두었다').toStrictEqual([]);
    expect(within(face).queryAllByRole('link')).toStrictEqual([]);
  });

  it('오류에는 재시도가 있고 서버 메시지를 그대로 내보이지 않는다', async () => {
    mocked.searchNotices.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();
    await searchFor('SYN-7E1');

    const face = await screen.findByTestId('notices-error');
    expect(within(face).getByRole('button', { name: '다시 시도' })).not.toBeNull();
    expect(face.textContent).not.toContain('INTERNAL_ERROR');
  });
});

/** D-7E-9 — 제안 목록의 빈 얼굴은 **API 상태**에서 나오고 화면이 전략을 읽어 추론하지 않는다. */
describe('화면 B — 제안 목록', () => {
  it('감시 조건이 없으면 그 사실을 그대로 보이고 설정으로 가는 걸음을 준다', async () => {
    renderScreen();

    const empty = await screen.findByTestId('suggestions-empty');
    expect(empty.textContent).toContain('감시 조건이 설정되지 않았습니다');
    expect(within(empty).getByRole('link', { name: /설정/u }).getAttribute('href')).toBe('/settings');
  });

  it('열린 공고가 없으면 걸음을 지어내지 않는다 — 운영자가 할 일이 없다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_OPEN_NOTICES' });
    renderScreen();

    const empty = await screen.findByTestId('suggestions-empty');
    expect(empty.textContent).toContain('입찰 가능한 열린 공고가 없습니다');
    expect(within(empty).queryAllByRole('link'), '할 일이 없는 자리에 가짜 걸음을 두었다').toStrictEqual([]);
    expect(within(empty).queryAllByRole('button')).toStrictEqual([]);
  });

  it('통과가 없으면 조건을 넓히러 가는 걸음을 준다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [], total: 0, resultState: 'NO_MATCH' });
    renderScreen();

    const empty = await screen.findByTestId('suggestions-empty');
    expect(within(empty).getByRole('link', { name: /설정/u }).getAttribute('href')).toBe('/settings');
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

  /**
   * **2층 라벨이 카드에서도 터치로 열린다**(받는 항목 11). 배지의 축은
   * `components/StateBadge.test.tsx` 가 전수로 잠그고, 이 자리는 **카드가 그 자리를 실제로
   * 쓰는지**를 본다 — 카드가 자기 배지를 손으로 지으면 그 축이 조용히 빠진다.
   */
  it('제안 카드의 판정 배지를 누르면 그 사유가 펼쳐진다', async () => {
    mocked.listSuggestions.mockResolvedValue({
      rows: [{ ...SUGGESTION, licenseVerdict: 'UNCERTAIN', licenseUncertainReason: 'REQUIREMENT_DATA_ABSENT' }],
      total: 1,
      resultState: 'MATCHED',
    });
    renderScreen();

    const card = await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`);
    const badge = within(card).getByTestId('suggestion-license');
    const why = document.getElementById(badge.getAttribute('aria-describedby') ?? '');
    expect(why?.getAttribute('data-expanded')).toBe('false');

    fireEvent.click(badge);

    expect(why?.getAttribute('data-expanded')).toBe('true');
    expect(why?.textContent ?? '').toContain('미달이 아니라');
    // 보조기기 쪽은 접힘·펼침과 무관하게 **설명 하나**다(F-1 처방).
    expect(why?.getAttribute('aria-hidden')).toBe('true');
  });
});

describe('화면 B — 담기(여정 ②의 마지막 걸음)', () => {
  /** CR L-8 — 스크린리더의 버튼 목록에서 어느 공고의 담기인지 구별돼야 한다. */
  it('담기 버튼의 접근 이름에 그 공고가 들어간다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const button = within(card).getByRole('button', { name: /담기/u });

    expect(button.getAttribute('aria-label')).toContain(NOTICE.title as string);
  });

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
      // M14/14E — 공고 읽기 투영. 이 고정물은 그 축을 모델링하지 않으므로
      // 「공고 행이 없다」를 둔다(새 문면을 들이지 않는다). 카드가 그 칸을
      // 쓰기 시작하면 그 slice 가 값을 세운다.
      noticeKnown: false,
      noticeTitle: null,
      demandAgencyName: null,
      noticeAgencyName: null,
    });
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: /담기/u }));

    await waitFor(() => {
      expect(mocked.addBidCase).toHaveBeenCalledWith({
        noticeNumber: NOTICE.noticeNumber,
        noticeRound: NOTICE.noticeRound,
        bidKind: 'REAL',
      });
    });
  });

  /**
   * CR H-2 — 쓰기 실패가 아무 얼굴도 없이 지나가면 운영자는 **다시 누른다**. 실패는 그 사실과
   * 다시 시도를 보이고, 성공 토스트는 **뜨지 않아야** 한다.
   */
  it('담기가 실패하면 그 사실이 뜨고 성공 토스트는 뜨지 않는다', async () => {
    mocked.addBidCase.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: /담기/u }));

    const failure = await screen.findByTestId('write-failure');
    expect(failure.textContent).toContain('담지 못했습니다');
    expect(within(failure).getByRole('button', { name: '다시 시도' })).not.toBeNull();
    expect(screen.queryByTestId('toast')).toBeNull();
    // 서버 메시지·코드를 그대로 내보이지 않는다(D-7E-6).
    expect(failure.textContent).not.toContain('INTERNAL_ERROR');
  });

  /**
   * CR r4 L-2 — 401 `.some()` 수정에 잠금이 없었다(실측: `??` 연쇄로 되돌려도 초록). 이 화면은
   * 읽기가 셋이라 **앞의 오류가 비 401 이어도 뒤의 401** 을 봐야 한다.
   */
  it('앞의 오류가 비 401 이어도 뒤의 401 을 보고 로그인 얼굴을 낸다', async () => {
    mocked.searchNotices.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    mocked.listBidCases.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();
    await searchFor('SYN-7E1');

    const face = await screen.findByTestId('signed-out');
    expect(within(face).getByRole('link', { name: /로그인/u }).getAttribute('href')).toBe('/login');
  });

  /**
   * CR r3 L-11 — 담기도 **다시 읽는다**. 앞 판은 얼굴 문면만 재서, `onError` 를 지워도 이 test 가
   * 초록이었다(v r2 실측). 얼굴이 「서버에 남은 상태를 다시 읽었습니다」라고 말하므로 그 말이
   * 사실인지를 호출 계수로 센다.
   */
  it('담기가 실패하면 서버 상태를 다시 읽는다 — 얼굴의 말이 사실이다', async () => {
    mocked.addBidCase.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const before = mocked.listBidCases.mock.calls.length;
    fireEvent.click(within(card).getByRole('button', { name: /담기/u }));

    const failure = await screen.findByTestId('write-failure');
    expect(failure.textContent).toContain('다시 읽었습니다');
    await waitFor(() => {
      expect(mocked.listBidCases.mock.calls.length).toBeGreaterThan(before);
    });
  });

  /**
   * CR r3 L-5 — 이 라운드·앞 라운드의 a11y 수정에 **잠금이 없었다**. 아래층 문면이 보조기기에
   * 닿는지(세 자리 가운데 화면 B 의 그것), 그리고 그 배선이 값 단언을 더럽히지 않는지를 센다.
   */
  it('기초금액의 아래층 문면이 보조기기에 닿는다 — 툴팁 하나가 아니다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const value = within(card).getByTestId('notice-amount');
    const describedBy = value.closest('dd')?.getAttribute('aria-describedby');

    expect(describedBy, 'aria-describedby 가 없으면 툴팁 하나로 돌아간 것이다').not.toBeNull();
    expect(document.getElementById(describedBy ?? '')?.textContent).toContain('추정가격과 다르다');
    // 값 단언이 설명까지 읽지 않는다 — 그 둘이 한 요소에 있으면 금액 대조가 망가진다.
    expect(value.textContent).not.toContain('추정가격과 다르다');
  });

  /** CR M-2 — 401 은 일시 장애와 다른 답이다: 재시도로 풀리지 않으므로 로그인으로 보낸다. */
  it('담기가 401 이면 로그인으로 가는 길을 보인다', async () => {
    mocked.addBidCase.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: /담기/u }));

    const failure = await screen.findByTestId('write-failure');
    expect(failure.textContent).toContain('로그인');
    expect(within(failure).getByRole('link', { name: /로그인/u }).getAttribute('href')).toBe('/login');
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
      // M14/14E — 공고 읽기 투영. 이 고정물은 그 축을 모델링하지 않으므로
      // 「공고 행이 없다」를 둔다(새 문면을 들이지 않는다). 카드가 그 칸을
      // 쓰기 시작하면 그 slice 가 값을 세운다.
      noticeKnown: false,
      noticeTitle: null,
      demandAgencyName: null,
      noticeAgencyName: null,
    });
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: /담기/u }));

    const toast = await screen.findByTestId('toast');
    expect(toast.textContent).toContain('입찰함에 담았습니다');
    expect(within(toast).queryByRole('button', { name: '실행 취소' })).toBeNull();
  });

  /** 제안 카드에서도 담긴다(§6.2 B 「같은 카드·같은 버튼」) — 검색 결과만의 능력이 아니다. */
  it('제안 카드에서도 담긴다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 1, resultState: 'MATCHED' });
    mocked.addBidCase.mockResolvedValue({
      caseId: 7,
      noticeNumber: SUGGESTION.noticeNumber,
      noticeRound: SUGGESTION.noticeRound,
      bidKind: 'REAL',
      state: 'WATCHING',
      caseVersion: 0,
      nextCheckpointKind: 'DEADLINE',
      nextCheckpointAt: SUGGESTION.deadlineAt,
      overdue: false,
      reconciliationCandidate: false,
      // M14/14E — 공고 읽기 투영. 이 고정물은 그 축을 모델링하지 않으므로
      // 「공고 행이 없다」를 둔다(새 문면을 들이지 않는다). 카드가 그 칸을
      // 쓰기 시작하면 그 slice 가 값을 세운다.
      noticeKnown: false,
      noticeTitle: null,
      demandAgencyName: null,
      noticeAgencyName: null,
    });
    renderScreen();

    const card = await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`);
    fireEvent.click(within(card).getByRole('button', { name: /담기/u }));

    await waitFor(() =>
      expect(mocked.addBidCase).toHaveBeenCalledWith({
        noticeNumber: SUGGESTION.noticeNumber,
        noticeRound: SUGGESTION.noticeRound,
        bidKind: 'REAL',
      }),
    );
  });
});

/** 마감이 **가까운** 공고 하나 — 「곧 마감」 축을 재는 자리다(먼 마감은 위 `NOTICE` 가 든다). */
function soonNotice(days: number): queries.SuggestedNotice {
  return {
    ...SUGGESTION,
    noticeNumber: `SYN-SOON-${days}`,
    deadlineAt: new Date(Date.now() + days * 86_400_000).toISOString(),
  };
}

/**
 * **머리 요약**(M14/14B · 와이어프레임 B 의 머리 한 줄).
 *
 * 이름이 「감시 조건 통과」다 — 제안 집합은 **자격 통과 집합이 아니다**(면허 판정이
 * `ELIGIBLE | UNCERTAIN` 둘이고 판정 불가는 미달이 아니다). 분모는 서버의 `total` 이고,
 * 분자는 **상한 초과가 없을 때만** 붙는다: 보이는 m 장에서 센 수를 전체 분모에 걸면 거짓이 된다.
 */
describe('화면 B — 머리 요약 (M14/14B)', () => {
  it('기본 상태의 요약이 「감시 조건 통과」와 서버의 전체 건수를 말한다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 7, resultState: 'MATCHED' });
    renderScreen();

    const summary = await screen.findByTestId('home-header-summary');

    expect(summary.textContent).toContain('감시 조건 통과');
    expect(summary.textContent).toContain('7');
    // 제안을 「자격 통과」로 부르면 정직성 퇴행이다(계약 「데이터」 절).
    expect(summary.textContent, '제안 집합을 자격 통과로 불렀다').not.toContain('자격 통과');
  });

  it('상한 초과가 없으면 곧 마감인 수를 함께 말한다', async () => {
    mocked.listSuggestions.mockResolvedValue({
      rows: [soonNotice(2), soonNotice(3), { ...SUGGESTION, noticeNumber: 'SYN-FAR' }],
      total: 3,
      resultState: 'MATCHED',
    });
    renderScreen();

    const summary = await screen.findByTestId('home-header-summary');

    expect(summary.textContent).toContain('3');
    expect(summary.textContent).toContain('2');
    expect(summary.textContent).toContain('곧 마감');
  });

  /**
   * **경계를 양쪽에서 밟는다**(v M-1 · cr M-2). 앞 판은 +2·+3 과 2099 만 보아, 그 사이의 어떤
   * 값도 같은 답을 냈다 — 상수를 27 년으로 올리거나 `>= 0` 을 지워도 조용했다(실측).
   *
   * 한 칸에 공고 **하나**만 둔다. 그래야 분자가 0 이냐 1 이냐로 갈리고, 그 수가 곧 「이 날이
   * 곧인가」의 답이다.
   */
  it.each([
    ['경계 당일은 든다', 7, true],
    ['경계 다음 날은 빠진다', 8, false],
    ['어제 마감(거울상)은 빠진다', -1, false],
  ])('%s', async (_label, days, counted) => {
    mocked.listSuggestions.mockResolvedValue({
      rows: [soonNotice(days)],
      total: 1,
      resultState: 'MATCHED',
    });
    renderScreen();

    const summary = await screen.findByTestId('home-header-summary');

    expect(summary.textContent).toContain('감시 조건 통과 1건');
    expect(summary.textContent?.includes('곧 마감'), `${days}일: 곧 마감 ${counted ? '이어야' : '이 아니어야'} 한다`).toBe(
      counted,
    );
  });

  /**
   * **상한 가드의 경계**(v M-1 의 V11). 앞 판은 9 대 1 만 보아 「`rows + 5` 보다 클 때만」으로
   * 느슨해지는 변이가 초록이었다. 한 건만 더 통과해도 분자를 말하지 않는다.
   */
  it('통과가 받은 행보다 한 건만 많아도 분자를 말하지 않는다', async () => {
    mocked.listSuggestions.mockResolvedValue({
      rows: [soonNotice(2)],
      total: 2,
      resultState: 'MATCHED',
    });
    renderScreen();

    const summary = await screen.findByTestId('home-header-summary');

    expect(summary.textContent).toContain('감시 조건 통과 2건');
    expect(summary.textContent, '보이지 않는 건까지 센 척했다').not.toContain('곧 마감');
  });

  /**
   * **상한을 넘으면 분자를 붙이지 않는다.** 분자는 받은 m 장에서만 셀 수 있는데 분모는 통과한
   * 전체다 — 둘을 한 문장에 걸면 화면이 세지 않은 것을 센 척한다.
   */
  it('상한을 넘으면 곧 마감 수를 말하지 않는다 — 세지 않은 것을 센 척하지 않는다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [soonNotice(2)], total: 9, resultState: 'MATCHED' });
    renderScreen();

    const summary = await screen.findByTestId('home-header-summary');

    expect(summary.textContent).toContain('9');
    expect(summary.textContent, '보이지 않는 건까지 센 척했다').not.toContain('곧 마감');
  });

  /** 머리 요약은 기존 상한 초과 공시를 **대신하지 않는다**(계약 14B 행 · M13 잔여 ⓐ). */
  it('상한 초과 공시가 머리 요약과 함께 선다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 9, resultState: 'MATCHED' });
    renderScreen();

    await screen.findByTestId('home-header-summary');
    const disclosure = await screen.findByTestId('suggestion-total');

    expect(disclosure.textContent).toContain('9');
    expect(screen.getByRole('link', { name: /상한 넓히기/u }).getAttribute('href')).toBe('/settings');
  });

  /** 검색 중에는 요약이 **제안 수를 말하지 않는다** — 그 자리의 답은 결과 건수다. */
  it('검색 중에는 제안 요약이 서지 않는다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 7, resultState: 'MATCHED' });
    renderScreen();

    await screen.findByTestId('home-header-summary');
    await searchFor('SYN-7E1');

    await waitFor(() => expect(screen.queryByTestId('home-header-summary')).toBeNull());
  });
});

/**
 * **「전체」 칩**(M14/14B · 팀장 판정 ②). 뜻은 이미 있는 `clearAll` 이고 새 질의 인자가 없다.
 * 구간 프리셋(「이번 주」·「1억~30억」)은 더하지 않는다 — 그 값이 화면에 사는 정책 상수가 되고
 * KST 날 경계 셈을 화면이 다시 짓는다(13E 가 적은 사유).
 */
describe('화면 B — 「전체」 칩 (M14/14B)', () => {
  it('조건이 없으면 눌린 얼굴이다', async () => {
    renderScreen();

    await screen.findByTestId('suggestions-empty');

    expect(screen.getByTestId('chip-all').getAttribute('aria-pressed')).toBe('true');
  });

  it('조건이 서면 눌린 얼굴이 풀리고, 누르면 조건이 전부 지워진다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 1, resultState: 'MATCHED' });
    renderScreen();

    await searchFor('SYN-7E1');
    await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    expect(screen.getByTestId('chip-all').getAttribute('aria-pressed')).toBe('false');

    fireEvent.click(screen.getByTestId('chip-all'));

    expect(await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`)).not.toBeNull();
    expect(screen.getByTestId('chip-all').getAttribute('aria-pressed')).toBe('true');
  });
});

/**
 * **카드 재배치**(M14/14B · 와이어프레임 B 의 카드). 상자 좌표는 배치 게이트가 재고, 이 자리는
 * 그것이 재지 못하는 **묶음**을 본다 — D-day 와 담기가 한 자리에 서는 것이 그 배치의 사실이다.
 */
describe('화면 B — 카드의 배치와 자격 슬롯 (M14/14B)', () => {
  it('D-day 와 다음 행동이 한 묶음에 선다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const aside = within(card).getByTestId('notice-aside');

    expect(within(aside).getByTestId('notice-deadline')).not.toBeNull();
    expect(within(aside).getByRole('button', { name: /담기/u })).not.toBeNull();
  });

  /**
   * **검색 결과 카드의 자격 판정은 범위 밖이다**(계약 「범위 밖」 · M13 승계). 빈 자리로 두면
   * 운영자가 「판정했는데 통과다」로 읽는다 — 그 자리가 **왜 비었는지**를 말한다.
   */
  it('검색 결과 카드의 자격 자리는 판정이 아니라 상태 문면이다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).getByTestId('notice-qualification').textContent).toBe('제안 목록에서만 판정합니다');
  });

  /** 제안 카드의 그 자리는 **서버가 준 판정**이다 — 같은 자리가 두 사실을 가른다. */
  it('제안 카드의 자격 자리는 서버의 면허 판정이다', async () => {
    mocked.listSuggestions.mockResolvedValue({ rows: [SUGGESTION], total: 1, resultState: 'MATCHED' });
    renderScreen();

    const card = await screen.findByTestId(`suggestion-card-${SUGGESTION.noticeNumber}`);

    expect(within(card).getByTestId('suggestion-license').textContent).toContain('면허 충족');
    expect(within(card).queryByTestId('notice-qualification'), '제안 카드에 상태 문면이 섰다').toBeNull();
  });

  /**
   * **압축 금액은 모바일 배치의 글자**이고, 정확 금액에 닿는 길이 카드 안에 남는다(팀장 판정 ⑤).
   * 정확 금액 노드(`notice-amount`)는 **건드리지 않는다** — 여정 게이트가 그 축어를 든다.
   */
  it('압축 금액이 서고 누르면 정확 금액이 아래층에 펼쳐진다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const compact = within(card).getByTestId('notice-amount-compact');

    expect(compact.textContent).toBe('12억원');
    const exact = document.getElementById(compact.getAttribute('aria-describedby') ?? '');
    expect(exact?.textContent, '압축 글자에서 정확 금액으로 가는 길이 없다').toContain('1,200,000,000원');

    expect(exact?.getAttribute('data-expanded')).toBe('false');
    fireEvent.click(compact);
    expect(exact?.getAttribute('data-expanded')).toBe('true');
  });

  /**
   * 금액이 없으면 압축할 것도 없다 — 「약 0원」을 지어내지 않는다. 그러나 **자리째 사라지지도
   * 않는다**(v M-2): 정확 금액의 부재 어휘는 `md` 이상에서만 서는 `dl` 안에 있어, 좁은 화면에서
   * 압축 자리를 비우면 「금액이 없다」와 「배치가 숨겼다」가 같은 얼굴이 된다.
   */
  it('금액이 없는 공고는 압축 자리에서 사유를 말한다 — 수를 지어내지도 자리를 비우지도 않는다', async () => {
    mocked.searchNotices.mockResolvedValue({
      rows: [{ ...NOTICE, baseAmountWon: null, baseAmountProvenance: null, baseAmountBasis: null }],
      total: 1,
      resultState: 'MATCHED',
    });
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);

    expect(within(card).queryByTestId('notice-amount-compact'), '없는 금액에 압축 글자를 지어냈다').toBeNull();
    expect(within(card).getByTestId('notice-amount-compact-absent').textContent).toBe(absenceTextOf('notSent'));
    expect(within(card).getByTestId('notice-amount').textContent).toBe(absenceTextOf('notSent'));
  });

  /**
   * **압축 금액은 좁은 화면의 것이다**(팀장 판정 ⑤ · v M-3). jsdom 은 CSS 를 풀지 않으므로
   * 「보이는가」로는 설 수 없다 — 그 자리를 **클래스 선언**으로 잰다. 넓은 화면에서 압축과
   * 정확 금액이 함께 서면 어느 쪽이 금액인지 흐려진다.
   */
  it('압축 금액 묶음이 넓은 화면에서 숨는다 — 정확 금액과 겹치지 않는다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const compact = within(card).getByTestId('notice-amount-compact');
    const wrapper = compact.closest('span.md\\:hidden');

    expect(wrapper, '압축 금액이 md 이상에서 숨는 묶음 안에 있지 않다').not.toBeNull();
    expect(wrapper?.contains(compact)).toBe(true);
    // 그 짝 — 정확 금액의 `dl` 은 반대로 **좁은 화면에서** 숨는다.
    expect(within(card).getByTestId('notice-amount').closest('dl')?.className).toContain('md:flex');
  });

  /** 좁은 화면의 금액에도 **축 이름**이 붙는다(와이어프레임의 「기초 4.2억」 · v M-2). */
  it('압축 금액에 기초 축 이름이 붙는다', async () => {
    renderScreen();
    await searchFor('SYN-7E1');

    const card = await screen.findByTestId(`notice-card-${NOTICE.noticeNumber}`);
    const wrapper = within(card).getByTestId('notice-amount-compact').closest('span.md\\:hidden');

    expect(wrapper?.textContent).toContain('기초');
  });
});
