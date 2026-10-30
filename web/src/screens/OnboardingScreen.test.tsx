// @vitest-environment jsdom
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { ITEM_STATE_DISPLAY, ONBOARDING_ITEM_DISPLAY, ONBOARDING_ITEM_ORDER } from '../display/onboardingDisplay';

import { ONBOARDING_QUERY_KEY, OnboardingBanner, OnboardingScreen } from './OnboardingScreen';

vi.mock('../api/queries');

type Checklist = queries.OnboardingChecklist;
type Profile = queries.Profile;

function checklist(overrides: Partial<Checklist> = {}): Checklist {
  return { completed: [], pending: [], awaiting: [], ...overrides };
}

/** **전부 채워진** 프로필 — 체크리스트와 어긋나게 투입해 「화면이 값을 보고 판정하는가」를 잰다. */
function filledProfile(): Profile {
  return {
    qualificationDeclared: true,
    businessTypes: ['토목공사업'],
    licensesDeclared: true,
    licenseNames: ['토목건축공사업'],
    regionTerms: ['서울'],
    tradeName: '여정 건설',
    headquartersRegion: 'SEOUL',
    naraRegistration: 'REGISTERED',
    naraRegisteredFields: ['CONSTRUCTION'],
    businessCertificateExpiresOn: '2099-01-01',
    unjudgedAxes: [],
  };
}

function emptyProfile(): Profile {
  return {
    qualificationDeclared: false,
    businessTypes: [],
    licensesDeclared: false,
    licenseNames: [],
    regionTerms: [],
    naraRegistration: 'UNANSWERED',
    naraRegisteredFields: [],
    unjudgedAxes: [],
  };
}

function emptyStrategy(): queries.StrategyReadResponse {
  return {
    revision: 7,
    focusCategories: [],
    focusRegionTerms: [],
    excludeRegionTerms: [],
    requiredKeywordTerms: [],
    excludeKeywordTerms: [],
  };
}

/** 막 연 편집 세션 — 서버가 준 모양 그대로이고 state 만 바꿔 쓴다. */
function openSession(field: NonNullable<queries.EditSessionResponse['field']>): queries.EditSessionResponse {
  return {
    sessionId: 'sess-1',
    state: 'WAITING_FOR_VALUE',
    sessionVersion: 1,
    expiresAt: '2099-01-01T00:00:00Z',
    field,
  };
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/start', element: <OnboardingScreen /> },
      { path: '/', element: <p>오늘의 공고 자리</p> },
    ],
    { initialEntries: ['/start'] },
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
  mocked.readProfile.mockResolvedValue(emptyProfile());
  mocked.listConstructionCapacities.mockResolvedValue([]);
  mocked.readStrategy.mockResolvedValue(emptyStrategy());
  mocked.readOnboardingChecklist.mockResolvedValue(checklist({ pending: [...ONBOARDING_ITEM_ORDER] }));
});

afterEach(() => {
  cleanup();
});

describe('시작하기 — 항목의 상태는 서버 판정에서만 온다', () => {
  /**
   * **우회 1 의 자리**(D-7E2b-1): 프로필이 전부 채워져 있어도 체크리스트가 미완이라고 하면
   * 미완이다. 화면이 프로필·전략 값을 보고 완료를 추론하면 이 단언이 깨진다.
   */
  it('프로필이 채워져 있어도 체크리스트가 미완이면 미완이다', async () => {
    mocked.readProfile.mockResolvedValue(filledProfile());
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ pending: [...ONBOARDING_ITEM_ORDER] }));
    renderScreen();

    for (const item of ONBOARDING_ITEM_ORDER) {
      expect((await screen.findByTestId(`onboarding-state-${item}`)).textContent, item).toContain(
        ITEM_STATE_DISPLAY.PENDING.label,
      );
    }
  });

  /**
   * 같은 축의 반대쪽 — 프로필·전략이 **둘 다 비어** 있어도 서버가 완료라고 하면 완료다.
   *
   * **배지 여덟을 전부 센다**(v r1 M-1): 앞 판은 `TRADE_NAME` 하나만 단언해서, 「비어 있는 값을
   * 보고 그 항목만 미완으로 그린다」는 변이 둘이 지나갔다 — 프로필 축(나라장터 미응답 →
   * `NARA_READINESS` 미완)과 전략 축(관심 업종 빈 배열 → `WATCH_CONDITIONS` 미완) 각각. 어느
   * 항목이 추론으로 넘어갈지는 미리 알 수 없으므로 전수로 센다.
   */
  it('프로필·전략이 비어 있어도 체크리스트가 완료라면 여덟 배지가 전부 완료다', async () => {
    mocked.readProfile.mockResolvedValue(emptyProfile());
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ completed: [...ONBOARDING_ITEM_ORDER] }));
    renderScreen();

    for (const item of ONBOARDING_ITEM_ORDER) {
      expect((await screen.findByTestId(`onboarding-state-${item}`)).textContent, item).toContain(
        ITEM_STATE_DISPLAY.COMPLETED.label,
      );
    }
  });

  /**
   * **전략 축의 반대 방향**(v r1 M-1). 위 둘은 프로필을 어긋나게 투입했고, 전략 mock 은 늘 비어
   * 있었다 — 그래서 「전략 값을 보고 그 항목을 완료로 그린다」는 자리가 비어 있었다. 전략을 채운
   * 채 체크리스트가 미완이라고 하면 **미완**이다.
   */
  it('전략이 채워져 있어도 체크리스트가 미완이면 미완이다', async () => {
    mocked.readStrategy.mockResolvedValue({
      revision: 7,
      focusCategories: ['토목'],
      focusRegionTerms: [],
      excludeRegionTerms: [],
      requiredKeywordTerms: [],
      excludeKeywordTerms: [],
      minBudgetWon: 100_000_000,
      maxBudgetWon: 900_000_000,
    });
    mocked.readProfile.mockResolvedValue(filledProfile());
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ pending: [...ONBOARDING_ITEM_ORDER] }));
    renderScreen();

    for (const item of ONBOARDING_ITEM_ORDER) {
      expect((await screen.findByTestId(`onboarding-state-${item}`)).textContent, item).toContain(
        ITEM_STATE_DISPLAY.PENDING.label,
      );
    }
  });

  it('선행 대기는 그 사실과 선행 항목의 이름을 함께 낸다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(
      checklist({
        pending: ['TRADE_NAME'],
        awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
      }),
    );
    renderScreen();

    expect((await screen.findByTestId('onboarding-state-CONSTRUCTION_CAPACITY')).textContent).toContain(
      ITEM_STATE_DISPLAY.AWAITING.label,
    );
    expect((await screen.findByTestId('onboarding-prerequisite-CONSTRUCTION_CAPACITY')).textContent).toContain(
      ONBOARDING_ITEM_DISPLAY.NARA_READINESS.label,
    );
  });

  /** 「해당 없음」은 **세 목록의 여집합**이다 — 미완으로 접으면 끝나지 않는 항목이 생긴다. */
  it('세 목록 어디에도 없는 항목은 해당 없음이다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ pending: ['TRADE_NAME'] }));
    renderScreen();

    expect((await screen.findByTestId('onboarding-state-CONSTRUCTION_CAPACITY')).textContent).toContain(
      ITEM_STATE_DISPLAY.NOT_APPLICABLE.label,
    );
  });

  it('진행률의 분모에 선행 대기가 들지 않는다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(
      checklist({
        completed: ['TRADE_NAME'],
        pending: ['BUSINESS_TYPES'],
        awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
      }),
    );
    renderScreen();

    const progress = await screen.findByTestId('onboarding-progress');
    expect(progress.textContent).toContain('1 / 2');
    expect(progress.textContent).not.toContain('/ 3');
  });
});

describe('시작하기 — 쓰기 뒤에는 다시 읽는다', () => {
  /** **우회 2**(D-7E2b-1): 쓰기 200 을 낙관적 완료로 쓰지 않는다. */
  it('쓰기가 성공해도 체크리스트가 미완이라고 하면 미완이다', async () => {
    mocked.replaceBusinessDetails.mockResolvedValue(emptyProfile());
    renderScreen();

    await screen.findByLabelText('상호');
    fireEvent.click(screen.getByRole('button', { name: '사업 정보 저장' }));

    await waitFor(() => expect(mocked.replaceBusinessDetails).toHaveBeenCalled());
    await waitFor(() => expect(mocked.readOnboardingChecklist.mock.calls.length).toBeGreaterThan(1));
    expect((await screen.findByTestId('onboarding-state-TRADE_NAME')).textContent).toContain(
      ITEM_STATE_DISPLAY.PENDING.label,
    );
  });

  it('다시 읽은 체크리스트가 완료라고 하면 그때 완료가 된다', async () => {
    mocked.replaceBusinessDetails.mockResolvedValue(filledProfile());
    mocked.readOnboardingChecklist
      .mockResolvedValueOnce(checklist({ pending: [...ONBOARDING_ITEM_ORDER] }))
      .mockResolvedValue(
        checklist({
          completed: ['TRADE_NAME', 'NARA_READINESS', 'HEADQUARTERS_REGION'],
          pending: ['BUSINESS_TYPES', 'LICENSES', 'CONSTRUCTION_CAPACITY', 'BUDGET_RANGE', 'WATCH_CONDITIONS'],
        }),
      );
    renderScreen();

    await screen.findByLabelText('상호');
    fireEvent.click(screen.getByRole('button', { name: '사업 정보 저장' }));

    await waitFor(() =>
      expect(screen.getByTestId('onboarding-state-TRADE_NAME').textContent).toContain(
        ITEM_STATE_DISPLAY.COMPLETED.label,
      ),
    );
  });
});

describe('시작하기 — 단계는 쓰기 단위다', () => {
  /** D-7E2b-7 — 같은 절을 세 번 덮어쓰지 않도록 항목 셋이 **한 폼** 뒤에 선다. */
  it('사업 정보 단계 하나가 상호·소재지·나라장터 준비를 함께 든다', async () => {
    renderScreen();

    expect(await screen.findByLabelText('상호')).toBeTruthy();
    expect(screen.getByLabelText('본사 소재지')).toBeTruthy();
    expect(screen.getByLabelText('나라장터 준비')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: '사업 정보 저장' }).length).toBe(1);
  });

  it('첫 단계는 미완 항목이 있는 첫 단계다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(
      checklist({
        completed: ['TRADE_NAME', 'NARA_READINESS', 'HEADQUARTERS_REGION', 'BUSINESS_TYPES', 'LICENSES'],
        pending: ['BUDGET_RANGE'],
      }),
    );
    renderScreen();

    // 금액대 단계가 서 있다 — 전략은 **편집 세션**으로만 바뀐다(계약에 직접 쓰는 길이 없다).
    // 커널의 `BUDGET_RANGE` 는 **최소·최대 가운데 하나 이상**이라 그 단계가 **둘 다** 세워야
    // 운영자가 어느 쪽으로든 채울 수 있다(CR r1 OQ — 앞 판은 최소만 단언했다).
    expect(await screen.findByTestId('strategy-row-MIN_BUDGET')).toBeTruthy();
    expect(screen.getByTestId('strategy-row-MAX_BUDGET')).toBeTruthy();
    expect(screen.queryByLabelText('상호')).toBeNull();
  });

  it('단계를 골라 옮길 수 있고 그 단계의 폼만 선다', async () => {
    renderScreen();

    fireEvent.click(await screen.findByTestId('onboarding-step-WATCH'));

    expect(await screen.findByTestId('strategy-row-FOCUS_CATEGORY')).toBeTruthy();
    expect(screen.queryByLabelText('상호')).toBeNull();
  });

  /** 설정 화면과 **같은 컴포넌트**다 — 같은 필드를 두 폼이 따로 저장하지 않는다(D-7E2b-8). */
  it('전략 단계의 쓰기는 편집 세션을 지난다', async () => {
    mocked.beginEditSession.mockResolvedValue({
      sessionId: 'sess-1',
      state: 'WAITING_FOR_VALUE',
      sessionVersion: 1,
      expiresAt: '2099-01-01T00:00:00Z',
      field: 'FOCUS_CATEGORY',
    });
    renderScreen();

    fireEvent.click(await screen.findByTestId('onboarding-step-WATCH'));
    const row = await screen.findByTestId('strategy-row-FOCUS_CATEGORY');
    fireEvent.click(within(row).getByRole('button', { name: '고치기' }));

    await waitFor(() => expect(mocked.beginEditSession).toHaveBeenCalledWith({ field: 'FOCUS_CATEGORY' }));
    expect(await screen.findByTestId('edit-session')).toBeTruthy();
  });
});

describe('시작하기 — 다음 행동 하나', () => {
  /** D-7E2b-4 — 미완 0 이면 화면 B 로 가는 행동 하나다. */
  it('미완이 0 이면 오늘의 공고로 가는 행동 하나가 뜬다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ completed: [...ONBOARDING_ITEM_ORDER] }));
    renderScreen();

    const done = await screen.findByTestId('onboarding-complete');
    expect(within(done).getByRole('link').getAttribute('href')).toBe('/');
  });

  it('선행 대기만 남아도 미완이 0 이면 그 행동이 뜬다 — 선행 대기는 막지 않는다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(
      checklist({
        completed: ['TRADE_NAME'],
        awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
      }),
    );
    renderScreen();

    expect(await screen.findByTestId('onboarding-complete')).toBeTruthy();
  });

  it('미완이 있으면 그 행동이 없다', async () => {
    renderScreen();

    await screen.findByTestId('onboarding-progress');
    expect(screen.queryByTestId('onboarding-complete')).toBeNull();
  });
});

describe('시작하기 — 세 얼굴과 범위 밖 자리', () => {
  /** **우회 8** — 읽기 실패를 빈 체크리스트로 접으면 「전부 완료」로 보인다. */
  it('읽기가 실패하면 오류 얼굴이고 완료 행동이 없다', async () => {
    mocked.readOnboardingChecklist.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderScreen();

    expect(await screen.findByTestId('onboarding-error')).toBeTruthy();
    expect(screen.queryByTestId('onboarding-complete')).toBeNull();
    expect(screen.queryByTestId('onboarding-progress')).toBeNull();
  });

  it('읽기가 401 이면 로그인 길을 낸다', async () => {
    mocked.readOnboardingChecklist.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    expect(await screen.findByTestId('signed-out')).toBeTruthy();
  });

  /** D-7E2b-3 — 빈 자리 + 「준비 중」뿐이다. 입력칸·버튼을 만들지 않는다. */
  it('사업자번호·알림 채널·첫 공고 미리보기는 빈 자리뿐이다', async () => {
    renderScreen();

    const notReady = await screen.findByTestId('onboarding-not-ready');
    expect(notReady.textContent).toContain('사업자번호');
    expect(notReady.textContent).toContain('알림 채널');
    expect(notReady.textContent).toContain('첫 공고');
    expect(within(notReady).queryAllByRole('textbox')).toStrictEqual([]);
    expect(within(notReady).queryAllByRole('button')).toStrictEqual([]);
  });

  /**
   * 체크리스트는 읽혔는데 **프로필만 401** 이면 앞 판은 단계 본문이 말 없이 비었다(CR r1 LOW).
   * 화면 머리의 401 분기는 체크리스트 오류만 보기 때문이다 — 그 자리에 로그인 길을 낸다.
   */
  it('프로필만 401 이면 단계 본문이 말 없이 비지 않고 로그인 길을 낸다', async () => {
    mocked.readProfile.mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    await screen.findByTestId('signed-out');
    const body = screen.getByTestId('onboarding-step-body');
    expect(within(body).getByTestId('signed-out')).toBeTruthy();
    // 체크리스트는 읽혔으므로 목록·진행률은 그대로 선다(전부를 로그인 얼굴로 덮지 않는다).
    expect(screen.getByTestId('onboarding-progress')).toBeTruthy();
  });
});

/**
 * 홈의 진입 배너(D-7E2b-4) — **서버 `pending` 이 비어 있지 않을 때만** 뜬다.
 *
 * 앞 판은 이 술어의 **음성 쪽을 세는 자리가 없었다**(v r1 M-2 · CR r1 MEDIUM): 여정은 미완이
 * 있는 상태에서만 배너를 보았고, 홈 화면 test 는 자동 mock 이 `undefined` 를 내 배너가 아예
 * 렌더되지 않았다. 「조건을 지우고 항상 띄운다」가 전건을 초록으로 지났다.
 */
describe('홈 진입 배너 — 없는 사실을 지어내지 않는다', () => {
  /**
   * **질의가 끝났다는 양성 신호**. 배너의 「뜨지 않는다」는 음성 단언인데, 응답이 닿기 **전**에도
   * 참이다 — `waitFor` 로 감싸면 첫 렌더에서 바로 통과하고 test 가 끝난다. 실제로 그 꼴이었다:
   * 「선행 대기까지 센다」 변이(B3)가 **초록으로 지나갔다**. 같은 질의를 쓰는 이 표지를 먼저
   * 기다린 뒤에 음성 단언을 **한 번만** 센다.
   */
  function BannerSettled() {
    const checklist = useQuery({ queryKey: ONBOARDING_QUERY_KEY, queryFn: queries.readOnboardingChecklist });
    return checklist.isPending ? null : <span data-testid="banner-settled" />;
  }

  function renderBanner() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(
      [
        {
          path: '/',
          element: (
            <>
              <OnboardingBanner />
              <BannerSettled />
            </>
          ),
        },
        { path: '/start', element: <p>시작하기 자리</p> },
      ],
      { initialEntries: ['/'] },
    );
    return render(
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
  }

  it('미완이 있으면 뜨고 그 수가 서버의 미완 수다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(
      checklist({ completed: ['TRADE_NAME'], pending: ['BUSINESS_TYPES', 'LICENSES', 'BUDGET_RANGE'] }),
    );
    renderBanner();

    const banner = await screen.findByTestId('onboarding-banner');
    expect(banner.textContent).toContain('3');
    expect(within(banner).getByRole('link').getAttribute('href')).toBe('/start');
  });

  it('미완이 0 이면 뜨지 않는다 — 전부 채운 운영자에게 「0개 있습니다」가 서지 않는다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ completed: [...ONBOARDING_ITEM_ORDER] }));
    renderBanner();

    await screen.findByTestId('banner-settled');
    expect(screen.queryByTestId('onboarding-banner')).toBeNull();
  });

  /** 선행 대기는 **막지도 채우지도 않는다** — 미완이 아니므로 배너를 띄울 사유가 아니다. */
  it('선행 대기만 남으면 뜨지 않는다', async () => {
    mocked.readOnboardingChecklist.mockResolvedValue(
      checklist({
        completed: ['TRADE_NAME'],
        awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
      }),
    );
    renderBanner();

    await screen.findByTestId('banner-settled');
    expect(screen.queryByTestId('onboarding-banner')).toBeNull();
  });

  /** 읽지 못했으면 **아무것도 그리지 않는다** — 실패를 「미완 0」으로도 「미완 있음」으로도 접지 않는다. */
  it('읽기가 실패하면 뜨지 않는다', async () => {
    mocked.readOnboardingChecklist.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderBanner();

    await screen.findByTestId('banner-settled');
    expect(screen.queryByTestId('onboarding-banner')).toBeNull();
  });
});

describe('시작하기 — 마법사의 걸음', () => {
  /**
   * 저장 뒤의 걸음은 **현재 단계 뒤의 첫 미완**이다(CR r1·r2 LOW). r1 은 `picked` 를 놓지 않아
   * 그 자리에 머물렀고, 그 다음 판은 **늘 첫 미완**으로 가서 뒷단계에서 저장하면 앞쪽으로
   * 끌려갔다. 여기서는 **앞에도 뒤에도 미완이 있는** 입력으로 그 둘을 가른다.
   */
  it('뒷단계에서 저장하면 앞으로 끌려가지 않고 그 뒤의 첫 미완으로 간다', async () => {
    mocked.replaceQualificationFacts.mockResolvedValue(emptyProfile());
    mocked.readOnboardingChecklist
      .mockResolvedValueOnce(checklist({ pending: [...ONBOARDING_ITEM_ORDER] }))
      .mockResolvedValue(
        checklist({
          completed: ['BUSINESS_TYPES', 'LICENSES'],
          // 앞(사업 정보)에도 뒤(금액대)에도 미완이 있다 — 「첫 미완」이면 앞으로 간다.
          pending: ['TRADE_NAME', 'NARA_READINESS', 'HEADQUARTERS_REGION', 'BUDGET_RANGE'],
        }),
      );
    renderScreen();

    fireEvent.click(await screen.findByTestId('onboarding-step-QUALIFICATION_FACTS'));
    await screen.findByLabelText('업종', { exact: true });
    fireEvent.click(screen.getByRole('button', { name: '자격 사실 저장' }));

    expect(await screen.findByTestId('strategy-row-MIN_BUDGET')).toBeTruthy();
    expect(screen.queryByLabelText('상호')).toBeNull();
  });

  it('뒤에 미완이 없으면 앞의 첫 미완으로 돌아온다', async () => {
    mocked.replaceQualificationFacts.mockResolvedValue(emptyProfile());
    mocked.readOnboardingChecklist
      .mockResolvedValueOnce(checklist({ pending: [...ONBOARDING_ITEM_ORDER] }))
      .mockResolvedValue(
        checklist({
          completed: ['BUSINESS_TYPES', 'LICENSES', 'CONSTRUCTION_CAPACITY', 'BUDGET_RANGE', 'WATCH_CONDITIONS'],
          pending: ['TRADE_NAME', 'NARA_READINESS', 'HEADQUARTERS_REGION'],
        }),
      );
    renderScreen();

    fireEvent.click(await screen.findByTestId('onboarding-step-QUALIFICATION_FACTS'));
    await screen.findByLabelText('업종', { exact: true });
    fireEvent.click(screen.getByRole('button', { name: '자격 사실 저장' }));

    expect(await screen.findByLabelText('상호')).toBeTruthy();
  });

  /**
   * **열린 편집 세션을 조용히 버리지 않는다**(v r1 L-1). 설정 화면에는 없던 길이다 — 거기서는
   * 화면을 떠나야 생기던 일이 마법사에서는 버튼 한 번으로 생긴다.
   */
  it('편집 세션이 열려 있는 동안에는 단계를 옮길 수 없다', async () => {
    mocked.beginEditSession.mockResolvedValue({
      sessionId: 'sess-1',
      state: 'WAITING_FOR_VALUE',
      sessionVersion: 1,
      expiresAt: '2099-01-01T00:00:00Z',
      field: 'FOCUS_CATEGORY',
    });
    mocked.cancelEditSession.mockResolvedValue({
      sessionId: 'sess-1',
      state: 'CANCELLED',
      sessionVersion: 2,
      expiresAt: '2099-01-01T00:00:00Z',
    });
    renderScreen();

    fireEvent.click(await screen.findByTestId('onboarding-step-WATCH'));
    const row = await screen.findByTestId('strategy-row-FOCUS_CATEGORY');
    fireEvent.click(within(row).getByRole('button', { name: '고치기' }));
    await screen.findByTestId('edit-session');

    await waitFor(() =>
      expect((screen.getByTestId('onboarding-step-BUSINESS_DETAILS') as HTMLButtonElement).disabled).toBe(true),
    );

    // 그만두면 다시 옮길 수 있다 — 막혀서 갇히는 자리가 없다.
    fireEvent.click(screen.getByRole('button', { name: '그만두기' }));
    await waitFor(() =>
      expect((screen.getByTestId('onboarding-step-BUSINESS_DETAILS') as HTMLButtonElement).disabled).toBe(false),
    );
  });

  /** §6.5 — 저장 뒤 바뀌는 진행률이 보조기술에 알려진다(CR r1 LOW). */
  it('진행률이 live region 이고 단계 버튼이 본문을 가리킨다', async () => {
    renderScreen();

    expect((await screen.findByTestId('onboarding-progress')).getAttribute('aria-live')).toBe('polite');
    expect(screen.getByTestId('onboarding-step-WATCH').getAttribute('aria-controls')).toBe('onboarding-step-body');
  });

  /**
   * 잠긴 탐색의 **사유가 보조기술에 닿는다**(CR r2 LOW): `disabled` 버튼은 탭 순서에서 빠지고
   * `title` 은 읽히지 않는다.
   */
  it('단계가 잠기면 그 사유가 문단으로 서고 버튼이 그것을 가리킨다', async () => {
    mocked.beginEditSession.mockResolvedValue(openSession('FOCUS_CATEGORY'));
    renderScreen();

    await screen.findByTestId('onboarding-step-WATCH');
    expect(screen.queryByTestId('onboarding-step-lock')).toBeNull();

    fireEvent.click(screen.getByTestId('onboarding-step-WATCH'));
    const row = await screen.findByTestId('strategy-row-FOCUS_CATEGORY');
    fireEvent.click(within(row).getByRole('button', { name: '고치기' }));
    await screen.findByTestId('edit-session');

    const reason = await screen.findByTestId('onboarding-step-lock');
    expect(reason.textContent).toContain('편집을 마치거나 그만두면');
    expect(screen.getByTestId('onboarding-step-BUSINESS_DETAILS').getAttribute('aria-describedby')).toBe(reason.id);
  });

  /**
   * 단계가 **사라질 때** 잠금이 풀리는지(v r2 L-1)와 전략 401 의 로그인 길(v r2 L-2)을 한 입력에서
   * 센다: 값 전송이 500 이면 그 실패가 전략을 다시 읽게 하고, 그 읽기가 401 이면 단계 본문이
   * 교체되면서 전략 단계가 언마운트된다.
   */
  it('전략 질의가 401 이면 로그인 길을 내고 사라진 단계가 잠금을 풀어 준다', async () => {
    mocked.beginEditSession.mockResolvedValue(openSession('FOCUS_CATEGORY'));
    mocked.provideEditSessionValue.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    mocked.readStrategy
      .mockResolvedValueOnce(emptyStrategy())
      .mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED'));
    renderScreen();

    fireEvent.click(await screen.findByTestId('onboarding-step-WATCH'));
    const row = await screen.findByTestId('strategy-row-FOCUS_CATEGORY');
    fireEvent.click(within(row).getByRole('button', { name: '고치기' }));
    await screen.findByTestId('edit-session');
    await waitFor(() =>
      expect((screen.getByTestId('onboarding-step-BUDGET') as HTMLButtonElement).disabled).toBe(true),
    );

    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));

    const body = await screen.findByTestId('onboarding-step-body');
    await waitFor(() => expect(within(body).queryByTestId('signed-out')).not.toBeNull());
    await waitFor(() =>
      expect((screen.getByTestId('onboarding-step-BUDGET') as HTMLButtonElement).disabled).toBe(false),
    );
  });
});

/**
 * **서버가 끝낸 세션이 마법사를 가두지 않는다**(CR·v r2 MEDIUM — r2 의 단계 잠금이 들여온 회귀).
 *
 * 세션이 서버에서 끝나면 그 뒤 **모든 명령이 409** 라 `onSuccess` 가 오지 않고, 409 재조회는
 * `open` 을 살린 채 state 만 종말로 바꾼다. 「열린 세션」으로 잠그면 패널에 버튼이 0 이고 단계
 * 버튼도 `disabled` 라 화면 안에 나갈 길이 없다.
 */
describe('시작하기 — 끝난 세션에서 빠져나갈 길', () => {
  async function openAndExpire(readBack: queries.EditSessionResponse | Error) {
    mocked.beginEditSession.mockResolvedValue(openSession('MIN_BUDGET'));
    mocked.confirmEditSession.mockRejectedValue(new ApiError(409, 'SESSION_EXPIRED'));
    mocked.provideEditSessionValue.mockResolvedValue({
      ...openSession('MIN_BUDGET'),
      state: 'WAITING_FOR_CONFIRMATION',
    });
    if (readBack instanceof Error) {
      mocked.readEditSession.mockRejectedValue(readBack);
    } else {
      mocked.readEditSession.mockResolvedValue(readBack);
    }
    mocked.readOnboardingChecklist.mockResolvedValue(checklist({ pending: ['BUDGET_RANGE'] }));
    renderScreen();

    const row = await screen.findByTestId('strategy-row-MIN_BUDGET');
    fireEvent.click(within(row).getByRole('button', { name: '고치기' }));
    fireEvent.change(await screen.findByLabelText('예산 하한'), { target: { value: '100000000' } });
    fireEvent.click(screen.getByRole('button', { name: '값 보내기' }));
    fireEvent.click(await screen.findByRole('button', { name: '적용' }));
    await waitFor(() => expect(mocked.confirmEditSession).toHaveBeenCalled());
  }

  it('409 뒤 다시 읽은 세션이 만료면 단계 잠금이 풀리고 패널에 닫기가 선다', async () => {
    await openAndExpire({ ...openSession('MIN_BUDGET'), state: 'EXPIRED' });

    await waitFor(() =>
      expect((screen.getByTestId('onboarding-step-WATCH') as HTMLButtonElement).disabled).toBe(false),
    );
    fireEvent.click(await screen.findByTestId('edit-session-dismiss'));
    await waitFor(() => expect(screen.queryByTestId('edit-session')).toBeNull());
  });

  it('취소된 세션도 같다', async () => {
    await openAndExpire({ ...openSession('MIN_BUDGET'), state: 'CANCELLED' });

    await waitFor(() =>
      expect((screen.getByTestId('onboarding-step-WATCH') as HTMLButtonElement).disabled).toBe(false),
    );
    expect(await screen.findByTestId('edit-session-dismiss')).toBeTruthy();
  });

  /** 재조회가 404 로 떨어지면 `open` 이 그대로다 — 그때는 **진행 중 state** 라 「그만두기」가 있다. */
  it('재조회가 실패하면 진행 중 상태가 남고 그만두기로 빠져나간다', async () => {
    await openAndExpire(new ApiError(404, 'SESSION_NOT_FOUND'));

    expect(await screen.findByRole('button', { name: '그만두기' })).toBeTruthy();
  });
});
