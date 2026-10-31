// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { EDIT_SESSION_STATE_DISPLAY, STRATEGY_FIELD_DISPLAY } from '../display/editSessionDisplay';

import { SettingsScreen } from './SettingsScreen';

vi.mock('../api/queries');

type Strategy = queries.StrategyReadResponse;
type Session = queries.EditSessionResponse;

function strategy(overrides: Partial<Strategy> = {}): Strategy {
  return {
    revision: 7,
    focusCategories: [],
    focusRegionTerms: [],
    excludeRegionTerms: [],
    requiredKeywordTerms: [],
    excludeKeywordTerms: [],
    ...overrides,
  };
}

function session(overrides: Partial<Session> = {}): Session {
  return {
    sessionId: 'sess-1',
    state: 'WAITING_FOR_VALUE',
    sessionVersion: 1,
    expiresAt: '2099-01-01T00:00:00Z',
    ...overrides,
  };
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([{ path: '/settings', element: <SettingsScreen /> }], {
    initialEntries: ['/settings'],
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
  mocked.readStrategy.mockResolvedValue(strategy());
  // 설정 화면은 프로필 절도 품는다 — 그 읽기의 기본값을 둔다(이 파일이 재는 것은 전략 축이다).
  mocked.readProfile.mockResolvedValue({
    qualificationDeclared: false,
    businessTypes: [],
    licensesDeclared: false,
    licenseNames: [],
    regionTerms: [],
    naraRegistration: 'UNANSWERED',
    naraRegisteredFields: [],
    unjudgedAxes: [],
  });
  mocked.listConstructionCapacities.mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
});

/**
 * 「고치기」 → 값 입력 칸이 뜰 때까지. **행에 자기 testId 가 있다** — DOM 조상을 더듬어 행을
 * 찾으면(`closest`·`ancestor::div[1]`) 마크업이 한 겹 달라질 때 조용히 빗나간다(여정에서
 * 실측으로 걸린 자리다).
 */
async function beginEdit(field: string): Promise<void> {
  const row = await screen.findByTestId(`strategy-row-${field}`);
  fireEvent.click(within(row).getByRole('button', { name: '고치기' }));
  await screen.findByTestId('edit-session');
}

describe('설정 — 전략의 지금 값은 API 가 준 값이다', () => {
  it('값이 없으면 빈 자리다 — 0 으로 지어내지 않는다', async () => {
    renderScreen();

    const row = await screen.findByTestId('strategy-value-MAX_ACTIVE_BIDS');
    expect(row.textContent).toBe('값 없음');
  });

  it('어휘·금액·수를 API 가 준 그대로 보인다', async () => {
    mocked.readStrategy.mockResolvedValue(
      strategy({
        requiredKeywordTerms: ['합성', '공사'],
        minBudgetWon: 1_200_000_000,
        minBudgetCurrency: 'KRW',
        minBudgetVatTreatment: 'INCLUSIVE',
        minBudgetProvenance: 'OPERATOR_DECLARED',
        maxActiveBids: 5,
      }),
    );
    renderScreen();

    expect((await screen.findByTestId('strategy-value-REQUIRED_KEYWORD')).textContent).toBe('합성, 공사');
    expect((await screen.findByTestId('strategy-value-MIN_BUDGET')).textContent).toBe('1,200,000,000원');
    expect((await screen.findByTestId('strategy-value-MAX_ACTIVE_BIDS')).textContent).toBe('5');
  });

  /** R-BASIS-06 — 금액은 **단위·과세·출처와 함께** 보인다. */
  it('금액에 통화·과세·출처가 함께 붙는다', async () => {
    mocked.readStrategy.mockResolvedValue(
      strategy({
        minBudgetWon: 500,
        minBudgetCurrency: 'KRW',
        minBudgetVatTreatment: 'EXCLUSIVE',
        minBudgetProvenance: 'OPERATOR_DECLARED',
      }),
    );
    renderScreen();

    const qualifier = await screen.findByTestId('strategy-qualifier-MIN_BUDGET');
    expect(qualifier.textContent).toContain('KRW');
    expect(qualifier.textContent).toContain('EXCLUSIVE');
  });

  it('칸 열셋이 전부 뜬다', async () => {
    renderScreen();

    await screen.findByTestId('strategy-value-FOCUS_CATEGORY');
    for (const field of Object.keys(STRATEGY_FIELD_DISPLAY)) {
      expect(screen.getByTestId(`strategy-value-${field}`), field).toBeTruthy();
    }
  });
});

describe('설정 — 전략은 편집 세션으로만 바뀐다', () => {
  it('고치기가 세션을 시작하고 그 칸을 싣는다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    expect(mocked.beginEditSession).toHaveBeenCalledWith({ field: 'MAX_ACTIVE_BIDS' });
  });

  it('값은 그 칸이 쓰는 요청 칸 하나로만 간다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(
      session({ state: 'WAITING_FOR_CONFIRMATION', field: 'MAX_ACTIVE_BIDS' }),
    );
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    await waitFor(() => expect(mocked.provideEditSessionValue).toHaveBeenCalled());
    const [sessionId, body] = mocked.provideEditSessionValue.mock.calls[0]!;
    expect(sessionId).toBe('sess-1');
    expect(Object.keys(body).sort()).toStrictEqual(['commandId', 'count', 'field'].sort());
    expect(body.count).toBe(5);
  });

  it('어휘 칸은 terms 로 가고 빈 입력은 그 조건을 지운다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'REQUIRED_KEYWORD' }));
    mocked.provideEditSessionValue.mockResolvedValue(
      session({ state: 'WAITING_FOR_CONFIRMATION', field: 'REQUIRED_KEYWORD' }),
    );
    renderScreen();
    await beginEdit('REQUIRED_KEYWORD');

    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    await waitFor(() => expect(mocked.provideEditSessionValue).toHaveBeenCalled());
    const [, body] = mocked.provideEditSessionValue.mock.calls[0]!;
    expect(body.terms).toStrictEqual([]);
  });

  /**
   * **CR H-2 · v H-1 의 화면 쪽 회귀 test.** 자리 구분 쉼표를 적으면 앞 판은 `50,000,000` 을
   * **50** 으로 보냈고 확인 화면은 원문을 보였다. 이제 보내지 않는다.
   */
  it('자리 구분 쉼표가 있으면 보내지 않고 받는 모양을 말한다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_BUDGET' }));
    renderScreen();
    await beginEdit('MAX_BUDGET');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_BUDGET.label), {
      target: { value: '50,000,000' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    expect(await screen.findByTestId('edit-value-missing')).toBeTruthy();
    expect(mocked.provideEditSessionValue).not.toHaveBeenCalled();
  });

  /** 「보낸 값」은 **본문의 값**이다 — 원문을 되풀이하지 않는다(그것이 확인 단계의 뜻이다). */
  it('확인 화면의 보낸 값이 본문의 값과 같은 꼴이다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_BUDGET' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    renderScreen();
    await beginEdit('MAX_BUDGET');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_BUDGET.label), {
      target: { value: '1200000000' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    await waitFor(() => expect(mocked.provideEditSessionValue).toHaveBeenCalled());
    const [, body] = mocked.provideEditSessionValue.mock.calls[0]!;
    expect(body.amountWon).toBe(1_200_000_000);
    // 본문의 값을 **지금 값과 같은 포맷터**로 보인다 — 둘이 다르면 눈에 보인다.
    expect((await screen.findByTestId('edit-sent-value')).textContent).toBe('1,200,000,000원');
  });

  /** 수로 읽히지 않으면 **보내지 않는다** — 0 으로 지어내지 않는다. */
  it('숫자 칸에 수가 아닌 값이면 보내지 않고 무엇이 빠졌는지 말한다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'CANDIDATE_LIMIT' }));
    renderScreen();
    await beginEdit('CANDIDATE_LIMIT');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.CANDIDATE_LIMIT.label), {
      target: { value: '많이' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    expect(await screen.findByTestId('edit-value-missing')).toBeTruthy();
    expect(mocked.provideEditSessionValue).not.toHaveBeenCalled();
  });

  /**
   * 확인 화면이 보이는 값은 **둘뿐**이다: 서버가 준 지금 값과 운영자가 방금 적은 값. 세션
   * 응답에 초안이 없으므로(계약) 그 밖의 값을 지어낼 자리가 없다.
   */
  it('확인 화면은 지금 값과 보낸 값 둘만 보인다', async () => {
    mocked.readStrategy.mockResolvedValue(strategy({ maxActiveBids: 3 }));
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(
      session({ state: 'WAITING_FOR_CONFIRMATION', field: 'MAX_ACTIVE_BIDS' }),
    );
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '9' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    expect((await screen.findByTestId('edit-current-value')).textContent).toBe('3');
    expect((await screen.findByTestId('edit-sent-value')).textContent).toBe('9');
  });

  /**
   * 조건을 **지우려고 비워 보낸 것**과 아직 보내지 않은 것은 다른 사실이다 — 둘 다 빈 칸으로
   * 그리면 확인 화면이 무엇을 적용하려는지 말하지 못한다(여정이 그 자리를 잡았다).
   */
  it('비워 보낸 값은 「비움」이라고 말한다 — 빈 칸이 아니다', async () => {
    mocked.readStrategy.mockResolvedValue(strategy({ requiredKeywordTerms: ['합성'] }));
    mocked.beginEditSession.mockResolvedValue(session({ field: 'REQUIRED_KEYWORD' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    renderScreen();
    await beginEdit('REQUIRED_KEYWORD');

    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    const sent = await screen.findByTestId('edit-sent-value');
    expect(sent.textContent).toContain('비움');
    expect(sent.textContent).not.toBe('');
    expect(sent.textContent).not.toBe('값 없음');
  });

  it('적용은 운영자가 본 개정을 싣는다', async () => {
    mocked.readStrategy.mockResolvedValue(strategy({ revision: 11 }));
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    mocked.confirmEditSession.mockResolvedValue(session({ state: 'APPLIED', strategyRevision: 12 }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));
    fireEvent.click(await screen.findByRole('button', { name: '적용' }));

    await waitFor(() => expect(mocked.confirmEditSession).toHaveBeenCalled());
    const [, body] = mocked.confirmEditSession.mock.calls[0]!;
    expect(body.seenRevision).toBe(11);
  });

  it('적용되면 세션 자리가 닫히고 전략을 다시 읽는다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    mocked.confirmEditSession.mockResolvedValue(session({ state: 'APPLIED' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));
    fireEvent.click(await screen.findByRole('button', { name: '적용' }));

    await waitFor(() => expect(screen.queryByTestId('edit-session')).toBeNull());
    expect(mocked.readStrategy.mock.calls.length).toBeGreaterThan(1);
  });

  it('값 고치기는 같은 세션에서 값 단계로 돌아간다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    mocked.requestEditSessionEdit.mockResolvedValue(session({ state: 'WAITING_FOR_VALUE' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));
    fireEvent.click(await screen.findByRole('button', { name: '값 고치기' }));

    await waitFor(() => expect(mocked.requestEditSessionEdit).toHaveBeenCalled());
    expect(await screen.findByRole('button', { name: '값 보내기' })).toBeTruthy();
  });

  it('그만두기는 세션을 취소하고 자리를 닫는다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.cancelEditSession.mockResolvedValue(session({ state: 'CANCELLED' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.click(screen.getByRole('button', { name: '그만두기' }));

    await waitFor(() => expect(mocked.cancelEditSession).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByTestId('edit-session')).toBeNull());
  });
});

describe('설정 — 세션 상태와 충돌은 서버가 말한다', () => {
  it('세션 상태 문면이 서버가 준 state 에서 온다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ state: 'WAITING_FOR_VALUE' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    expect((await screen.findByTestId('edit-session-state')).textContent).toContain(
      EDIT_SESSION_STATE_DISPLAY.WAITING_FOR_VALUE.label,
    );
  });

  /**
   * **만료를 화면이 판정하지 않는다**(D-7E-4): `expiresAt` 이 이미 지난 세션이라도 서버가
   * `WAITING_FOR_VALUE` 라고 하면 그 말을 보인다. 만료는 서버가 접는다.
   */
  it('expiresAt 이 지나도 서버가 준 state 를 보인다 — 화면이 접지 않는다', async () => {
    mocked.beginEditSession.mockResolvedValue(
      session({ state: 'WAITING_FOR_VALUE', expiresAt: '2000-01-01T00:00:00Z' }),
    );
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    const badge = await screen.findByTestId('edit-session-state');
    expect(badge.textContent).toContain(EDIT_SESSION_STATE_DISPLAY.WAITING_FOR_VALUE.label);
    expect(badge.textContent).not.toContain(EDIT_SESSION_STATE_DISPLAY.EXPIRED.label);
  });

  /** 409 를 받으면 **세션을 다시 읽어** 서버가 말하는 상태를 보인다(추측하지 않는다). */
  it('409 면 세션을 다시 읽고 사유와 상태를 보인다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    mocked.confirmEditSession.mockRejectedValue(new ApiError(409, 'STALE_REVISION'));
    mocked.readEditSession.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));
    fireEvent.click(await screen.findByRole('button', { name: '적용' }));

    await waitFor(() => expect(mocked.readEditSession).toHaveBeenCalledWith('sess-1'));
    expect((await screen.findByTestId('edit-conflict-reason')).textContent).toContain('그동안 전략이 바뀌었습니다');
  });

  it('쓰기 실패의 재시도는 같은 commandId 를 다시 쓴다 — 멱등 키다', async () => {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');

    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    const failure = await screen.findByTestId('write-failure');
    fireEvent.click(within(failure).getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(mocked.provideEditSessionValue.mock.calls.length).toBe(2));
    const [, first] = mocked.provideEditSessionValue.mock.calls[0]!;
    const [, second] = mocked.provideEditSessionValue.mock.calls[1]!;
    expect(second.commandId).toBe(first.commandId);
  });

  it('읽기가 401 이면 로그인 길을 낸다', async () => {
    mocked.readStrategy.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    expect(await screen.findByTestId('signed-out')).toBeTruthy();
  });
});

describe('설정 — 범위 밖 자리', () => {
  /** D-7E2-7 — 사업자번호는 **입력칸을 만들지 않는다**(계약에 칸이 0 이다). */
  it('사업자번호는 「준비 중」 빈 자리이고 입력칸이 없다', async () => {
    renderScreen();

    const notReady = await screen.findByTestId('settings-not-ready');
    expect(notReady.textContent).toContain('사업자번호');
    expect(within(notReady).queryAllByRole('textbox')).toStrictEqual([]);
    expect(within(notReady).queryAllByRole('button')).toStrictEqual([]);
  });

  it('페이퍼 투찰과 알림 채널도 빈 자리뿐이다', async () => {
    renderScreen();

    const notReady = await screen.findByTestId('settings-not-ready');
    expect(notReady.textContent).toContain('페이퍼');
    expect(notReady.textContent).toContain('알림 채널');
  });
});

/**
 * 「한 번에 하나」의 술어 — **진행 중**인 세션만 다른 칸을 닫는다(M7/7E-2b r2·r3).
 *
 * 이 축은 마법사와 설정이 **같은 한 줄**을 쓰는데 r2 까지 세는 자리가 마법사 쪽뿐이었다
 * (v·CR r3 L-3): 그 줄을 「열린 세션」으로 되돌리는 변이가 전건을 초록으로 지났다. 여기가 그
 * 변이를 붉히는 자리다.
 */
describe('설정 — 다른 칸의 「고치기」가 닫히는 조건', () => {
  function fixButton(field: string): HTMLButtonElement {
    return within(screen.getByTestId(`strategy-row-${field}`)).getByRole('button', {
      name: '고치기',
    }) as HTMLButtonElement;
  }

  /** 값 단계까지 밀어 **확인을 기다리는** 세션 하나를 만든다. */
  async function toConfirmation(): Promise<void> {
    mocked.beginEditSession.mockResolvedValue(session({ field: 'MAX_ACTIVE_BIDS' }));
    mocked.provideEditSessionValue.mockResolvedValue(session({ state: 'WAITING_FOR_CONFIRMATION' }));
    renderScreen();
    await beginEdit('MAX_ACTIVE_BIDS');
    fireEvent.change(screen.getByLabelText(STRATEGY_FIELD_DISPLAY.MAX_ACTIVE_BIDS.label), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));
    await screen.findByRole('button', { name: '적용' });
  }

  it('진행 중이면 다른 칸의 고치기가 닫힌다', async () => {
    await toConfirmation();

    expect(fixButton('CANDIDATE_LIMIT').disabled).toBe(true);
    expect(screen.queryByTestId('edit-session-dismiss')).toBeNull();
  });

  it('서버가 끝낸 세션이면 다시 열리고 패널에 닫기가 선다', async () => {
    await toConfirmation();
    mocked.confirmEditSession.mockRejectedValue(new ApiError(409, 'SESSION_EXPIRED'));
    mocked.readEditSession.mockResolvedValue(session({ state: 'EXPIRED' }));

    fireEvent.click(screen.getByRole('button', { name: '적용' }));

    await waitFor(() => expect(fixButton('CANDIDATE_LIMIT').disabled).toBe(false));
    expect(await screen.findByTestId('edit-session-dismiss')).toBeTruthy();
  });

  /** 다시 열린 뒤 새 세션을 시작하면 **패널은 하나**이고 잠금이 다시 선다. */
  it('끝난 세션 뒤에 새 세션을 시작하면 패널이 하나이고 다시 잠긴다', async () => {
    await toConfirmation();
    mocked.confirmEditSession.mockRejectedValue(new ApiError(409, 'SESSION_EXPIRED'));
    mocked.readEditSession.mockResolvedValue(session({ state: 'EXPIRED' }));
    fireEvent.click(screen.getByRole('button', { name: '적용' }));
    await waitFor(() => expect(fixButton('CANDIDATE_LIMIT').disabled).toBe(false));

    mocked.beginEditSession.mockResolvedValue(session({ sessionId: 'sess-2', field: 'CANDIDATE_LIMIT' }));
    fireEvent.click(fixButton('CANDIDATE_LIMIT'));

    await waitFor(() => expect(mocked.beginEditSession).toHaveBeenLastCalledWith({ field: 'CANDIDATE_LIMIT' }));
    await waitFor(() => expect(screen.queryByTestId('edit-session-dismiss')).toBeNull());
    expect(screen.getAllByTestId('edit-session')).toHaveLength(1);
    expect(fixButton('MAX_ACTIVE_BIDS').disabled).toBe(true);
  });
});
