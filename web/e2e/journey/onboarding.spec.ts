import { expect, test, type Page } from '@playwright/test';

import {
  assertOnlyAllowedOperations,
  clearStrategyTerms,
  journeyContext,
  signIn,
  watchRequests,
} from './shared';

/**
 * 여정 ①(M7/7E-2b, D-7E2b-5·9) — 빈 계정 → 시작하기 → 마법사로 항목을 채움 → 되돌림.
 *
 * **금액대만 적용하지 않는다**(D-7E2b-9): 계약에 전략 금액대를 비우는 편집 값이 없어
 * (`amountWon` 생략·`null`·`terms:[]` 모두 400, 착수 실측) 한 번 적용하면 되돌릴 수 없고,
 * 그러면 `GET /api/suggestions` 가 `WATCH_NOT_CONFIGURED` → `NO_MATCH` 로 **영구히** 바뀌어
 * 여정 ② 와 설정 여정의 출발 단언이 실행 순서에 따라 깨진다. 그래서 그 단계는 **값을 적고
 * 그만둔다** — 적용되지 않았다는 것 자체가 단언이다.
 *
 * `LICENSES` 도 되돌릴 operation 이 없다(V12 행을 지우는 길이 계약에 없다). 그 사실을 **숨기지
 * 않고 단언한다** — 되돌림 뒤에도 완료로 남는다. 여정 ②③ 의 단언에는 영향이 없다(착수 실측:
 * 면허 신고 여부와 무관하게 `licenseVerdict=UNCERTAIN`).
 */

/** 계약의 항목 어휘 — **spec 이 자기 몫을 적는다**(화면의 표를 끌어오면 대조가 공허해진다). */
const ITEMS = [
  'TRADE_NAME',
  'NARA_READINESS',
  'BUSINESS_TYPES',
  'LICENSES',
  'HEADQUARTERS_REGION',
  'CONSTRUCTION_CAPACITY',
  'BUDGET_RANGE',
  'WATCH_CONDITIONS',
] as const;

/** 상태 → 화면 문면. 화면의 표와 같은 어휘이고, 화면이 문면을 바꾸면 이 대조가 붉어진다. */
const STATE_LABEL = {
  COMPLETED: '완료',
  PENDING: '아직',
  AWAITING: '선행 대기',
  NOT_APPLICABLE: '해당 없음',
} as const;

interface Checklist {
  readonly completed: readonly string[];
  readonly pending: readonly string[];
  readonly awaiting: readonly { readonly item: string; readonly prerequisite: string }[];
}

function checklistResponse(page: Page) {
  return page.waitForResponse(
    (one) =>
      one.url().endsWith('/api/onboarding-checklist') &&
      one.request().method() === 'GET' &&
      one.status() === 200,
  );
}

/** 시작하기로 — 셸의 전역 탐색을 지난다(주소를 손으로 적지 않는다). */
async function openOnboarding(page: Page): Promise<Checklist> {
  const read = checklistResponse(page);
  await page.getByRole('link', { name: 'A 시작하기' }).click();
  await expect(page.getByTestId('screen-onboarding')).toBeVisible();
  return (await (await read).json()) as Checklist;
}

/**
 * 시작하기를 **다시 연다** — 이미 그 주소에 있으면 같은 링크를 눌러도 라우터가 움직이지 않아
 * 다시 읽지 않는다(첫 실행이 그 자리에서 멈췄다). 홈을 거쳐 들어와 **서버에 다시 묻는다**.
 */
async function reopenOnboarding(page: Page): Promise<Checklist> {
  await page.getByRole('link', { name: 'B 오늘의 공고' }).click();
  await expect(page.getByTestId('screen-home')).toBeVisible();
  return openOnboarding(page);
}

/** 쓰기 하나 — 누르고 **다시 읽은 체크리스트**를 돌려준다(쓰기 응답을 완료로 세지 않는다). */
async function saveAndReread(page: Page, button: string): Promise<Checklist> {
  const read = checklistResponse(page);
  await page.getByRole('button', { name: button }).click();
  return (await read).json() as Promise<Checklist>;
}

function expectedState(checklist: Checklist, item: string): string {
  if (checklist.awaiting.some((pair) => pair.item === item)) {
    return STATE_LABEL.AWAITING;
  }
  if (checklist.completed.includes(item)) {
    return STATE_LABEL.COMPLETED;
  }
  return checklist.pending.includes(item) ? STATE_LABEL.PENDING : STATE_LABEL.NOT_APPLICABLE;
}

/** 화면의 배지 전수가 **같은 실행의 응답**과 같다 — 화면이 상태를 지어내지 않는다. */
async function assertRowsMatch(page: Page, checklist: Checklist): Promise<void> {
  for (const item of ITEMS) {
    await expect(page.getByTestId(`onboarding-state-${item}`), item).toHaveText(expectedState(checklist, item));
  }
}

/** 전략 한 칸을 편집 세션으로 바꾼다 — 설정 화면과 **같은 컴포넌트**다. */
async function editStrategyField(page: Page, field: string, raw: string): Promise<void> {
  const row = page.getByTestId(`strategy-row-${field}`);
  await row.getByRole('button', { name: '고치기' }).click();
  const panel = page.getByTestId('edit-session');
  await expect(panel).toBeVisible();
  await panel.getByRole('textbox').fill(raw);
  const valued = page.waitForResponse((one) => one.url().includes('/value') && one.request().method() === 'POST');
  await panel.getByRole('button', { name: '값 보내기' }).click();
  expect((await valued).status()).toBe(200);
}

test.afterEach(async ({ page }) => {
  // 감시 관심 업종은 **전역 전략**이고 제안 목록의 빈 얼굴을 바꾼다 — 남겨 두면 뒤 여정이
  // 남의 잔류로 깨진다(그 실패가 가장 비싼 꼴이다).
  const outcome = await clearStrategyTerms(
    page,
    'FOCUS_CATEGORY',
    (strategy) => (strategy.focusCategories as readonly string[] | undefined) ?? [],
  );
  if (outcome.kind === 'no-session') {
    // **원래 실패를 덮지 않는다**(CR r1 LOW): 로그인 전에 죽었으면 전역 상태를 바꿀 기회도
    // 없었으므로 되돌릴 것이 없다. 여기서 또 실패하면 진단이 teardown 으로 옮겨 간다.
    //
    // 다만 **포기했다는 사실은 남긴다**(CR r2 LOW): 남기지 않으면 뒤 여정이 남의 잔류로 깨졌을 때
    // 그 원인을 여기서 찾을 수 없다. 보고서 주석이라 콘솔 단언과도 부딪히지 않는다.
    test.info().annotations.push({ type: 'teardown 포기', description: outcome.why });
    return;
  }
  expect(outcome.kind === 'blocked' ? outcome.why : null, '여정 ① 의 전역 감시 조건을 되돌릴 수 없었다').toBeNull();
  const after = (await (await page.request.get('/api/strategy')).json()) as {
    readonly focusCategories: readonly string[];
  };
  expect(after.focusCategories, '여정 ① 이 전역 감시 조건을 남겼다').toStrictEqual([]);
});

/** D-7E-6 — 지금 화면에 비밀 문구도 사업자번호 꼴도 없다. 두 자리에서 부른다(채운 뒤·비운 뒤). */
async function assertNoPrivateValues(page: Page): Promise<void> {
  const shown = await page.locator('body').innerText();
  expect(shown).not.toContain(journeyContext().passphrase);
  expect(shown).not.toMatch(/\b\d{3}-\d{2}-\d{5}\b/u);
}

test('여정 ① — 빈 계정 → 시작하기 마법사로 항목을 채우고 되돌린다', async ({ page }) => {
  const watched = watchRequests(page);
  // 콘솔은 **여정 전체**에서 모은다(설정 여정과 같은 축, v r1 L-2) — 한 자리의 스냅숏이 아니라
  // 모든 전이에서 비밀 문구가 새지 않았다는 것을 끝에서 한 번 센다.
  const consoleLines: string[] = [];
  page.on('console', (message) => consoleLines.push(message.text()));
  await signIn(page);

  // ⓪ 홈에 **A 로 가는 진입 하나**가 떠 있다(서버의 미완이 비어 있지 않을 때만, D-7E2b-4).
  await expect(page.getByTestId('onboarding-banner')).toBeVisible();

  // ① 미완 목록이 **같은 실행의 응답**과 같다. 시평액은 **선행 대기**이고 그 선행을 함께 낸다.
  const start = await openOnboarding(page);
  expect(start.pending.length, '빈 계정이면 미완이 있어야 이 여정이 할 일이 있다').toBeGreaterThan(0);
  await assertRowsMatch(page, start);
  expect(start.awaiting).toContainEqual({ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' });
  await expect(page.getByTestId('onboarding-prerequisite-CONSTRUCTION_CAPACITY')).toContainText('나라장터 준비');
  await expect(page.getByTestId('onboarding-complete'), '미완이 있으면 완료 행동이 없다').toHaveCount(0);

  // ② 사업 정보 — 항목 셋이 **폼 하나** 뒤에 선다(D-7E2b-7).
  const expires = new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10);
  await page.getByTestId('onboarding-step-BUSINESS_DETAILS').click();
  await page.getByLabel('상호').fill('여정 건설');
  await page.getByLabel('본사 소재지').selectOption('SEOUL');
  await page.getByLabel('나라장터 준비').selectOption('REGISTERED');
  await page.getByLabel('인증서 만료일').fill(expires);
  await page.getByTestId('nara-fields-choice').getByLabel('공사').check();
  const afterDetails = await saveAndReread(page, '사업 정보 저장');

  for (const item of ['TRADE_NAME', 'NARA_READINESS', 'HEADQUARTERS_REGION']) {
    expect(afterDetails.completed, item).toContain(item);
  }
  // **선행이 풀리면 선행 대기가 미완으로 간다** — 세 상태가 실제로 세 갈래로 움직인다.
  expect(afterDetails.awaiting).toStrictEqual([]);
  expect(afterDetails.pending).toContain('CONSTRUCTION_CAPACITY');
  await assertRowsMatch(page, afterDetails);

  // ③ 자격 사실 — 업종과 면허가 **같은 폼**이다.
  await page.getByTestId('onboarding-step-QUALIFICATION_FACTS').click();
  // **`exact`** — 부분 일치면 전략 패널의 `관심 업종` 입력도 같은 패턴에 걸린다(CR r1 LOW).
  // 오늘은 단계마다 카드 하나뿐이라 지나가지만, 화면 전체 locator 가 남의 건을 집는 자리다.
  await page.getByLabel('업종', { exact: true }).fill('토목공사업');
  await page.getByLabel('면허를 신고함').check();
  await page.getByLabel('면허', { exact: true }).fill('토목건축공사업');
  await page.getByLabel('지역어').fill('서울');
  const afterFacts = await saveAndReread(page, '자격 사실 저장');

  expect(afterFacts.completed).toContain('BUSINESS_TYPES');
  expect(afterFacts.completed).toContain('LICENSES');
  await assertRowsMatch(page, afterFacts);

  // ④ 시공능력평가액 — 행 단위다.
  await page.getByTestId('onboarding-step-CONSTRUCTION_CAPACITY').click();
  await page.getByLabel('시평액 면허명').fill('토목건축공사업');
  await page.getByLabel('시평액 금액').fill('1200000000');
  await page.getByLabel('평가 연도').fill('2026');
  const afterCapacity = await saveAndReread(page, '시평액 저장');

  expect(afterCapacity.completed).toContain('CONSTRUCTION_CAPACITY');
  await assertRowsMatch(page, afterCapacity);

  // ④-b D-7E-6 — **프로필이 채워진 상태**에서 본다(v r1 L-2). 아래 ⑨ 는 비운 뒤라, 값이 실제로
  // 화면에 그려지는 동안 비밀 문구·사업자번호 꼴이 없는지는 이 자리에서만 잰다.
  await assertNoPrivateValues(page);

  // ⑤ 감시 관심 업종 — 전략은 **편집 세션**으로만 바뀐다.
  await page.getByTestId('onboarding-step-WATCH').click();
  await editStrategyField(page, 'FOCUS_CATEGORY', '토목');
  const watchApplied = checklistResponse(page);
  await page.getByTestId('edit-session').getByRole('button', { name: '적용' }).click();
  const afterWatch = (await (await watchApplied).json()) as Checklist;

  expect(afterWatch.completed).toContain('WATCH_CONDITIONS');
  await assertRowsMatch(page, afterWatch);

  // ⑥ 금액대 — **값을 적되 적용하지 않는다**(D-7E2b-9). 적용하면 되돌릴 길이 계약에 없다.
  await page.getByTestId('onboarding-step-BUDGET').click();
  await editStrategyField(page, 'MIN_BUDGET', '100000000');
  await expect(page.getByTestId('edit-sent-value')).toHaveText('100,000,000원');
  await page.getByTestId('edit-session').getByRole('button', { name: '그만두기' }).click();
  await expect(page.getByTestId('edit-session')).toHaveCount(0);
  // 적용하지 않았으므로 **지금 값이 그대로**이고 항목도 미완 그대로다. 화면과 **서버** 둘 다 본다
  // — 화면만 보면 「아직 다시 읽지 않아서 그대로」와 구분되지 않는다.
  await expect(page.getByTestId('strategy-value-MIN_BUDGET')).toHaveText('값 없음');
  const strategyAfterCancel = (await (await page.request.get('/api/strategy')).json()) as {
    readonly minBudgetWon: number | null;
  };
  expect(strategyAfterCancel.minBudgetWon, '그만둔 편집은 서버에 아무것도 쓰지 않는다').toBeNull();
  const afterCancel = await reopenOnboarding(page);
  expect(afterCancel.pending, '그래서 금액대는 미완 그대로다').toContain('BUDGET_RANGE');

  // ⑦ 되돌림 — **화면으로** 되돌린다(같은 폼이 그 능력을 쥔다).
  await page.getByTestId('onboarding-step-CONSTRUCTION_CAPACITY').click();
  const capacityGone = checklistResponse(page);
  await page.getByTestId('capacity-토목건축공사업').getByRole('button', { name: '지우기' }).click();
  expect(((await (await capacityGone).json()) as Checklist).pending).toContain('CONSTRUCTION_CAPACITY');

  await page.getByTestId('onboarding-step-QUALIFICATION_FACTS').click();
  // ③ 과 같은 사유로 `exact` 다(CR r1 지적이 ③ 만 닫혔던 자리 — CR r2 LOW).
  await page.getByLabel('업종', { exact: true }).fill('');
  await page.getByLabel('지역어').fill('');
  await page.getByLabel('면허를 신고함').uncheck();
  const afterFactsCleared = await saveAndReread(page, '자격 사실 저장');

  expect(afterFactsCleared.pending).toContain('BUSINESS_TYPES');
  // **되돌릴 operation 이 없는 항목**: V12 행이 남아 면허는 완료로 선다(착수 실측 · 숨기지 않는다).
  expect(afterFactsCleared.completed, '면허는 되돌릴 길이 계약에 없다').toContain('LICENSES');

  await page.getByTestId('onboarding-step-BUSINESS_DETAILS').click();
  await page.getByLabel('상호').fill('');
  await page.getByLabel('본사 소재지').selectOption('');
  await page.getByLabel('나라장터 준비').selectOption('UNANSWERED');
  const afterDetailsCleared = await saveAndReread(page, '사업 정보 저장');

  for (const item of ['TRADE_NAME', 'NARA_READINESS', 'HEADQUARTERS_REGION']) {
    expect(afterDetailsCleared.pending, item).toContain(item);
  }
  // 선행을 다시 모르게 되면 시평액이 **선행 대기로 돌아온다** — 되돌림이 세 상태 모두에 닿는다.
  expect(afterDetailsCleared.awaiting).toContainEqual({
    item: 'CONSTRUCTION_CAPACITY',
    prerequisite: 'NARA_READINESS',
  });
  await assertRowsMatch(page, afterDetailsCleared);

  await page.getByTestId('onboarding-step-WATCH').click();
  await editStrategyField(page, 'FOCUS_CATEGORY', '');
  await expect(page.getByTestId('edit-sent-value')).toHaveText('비움 — 이 조건을 지웁니다');
  const watchCleared = checklistResponse(page);
  await page.getByTestId('edit-session').getByRole('button', { name: '적용' }).click();
  const afterWatchCleared = (await (await watchCleared).json()) as Checklist;
  expect(afterWatchCleared.pending).toContain('WATCH_CONDITIONS');

  // ⑧ D-7E-6 — 비운 뒤에도 같다. 콘솔은 이 여정 전체에서 모은 것을 ⑩ 에서 한 번 센다.
  await assertNoPrivateValues(page);

  // ⑨ 미완이 남았으므로 완료 행동은 없고, 홈의 진입은 그대로 서 있다.
  await expect(page.getByTestId('onboarding-complete')).toHaveCount(0);
  await page.getByRole('link', { name: 'B 오늘의 공고' }).click();
  await expect(page.getByTestId('screen-home')).toBeVisible();
  await expect(page.getByTestId('onboarding-banner')).toBeVisible();

  await expect(page.getByTestId('write-failure')).toHaveCount(0);

  // ⑩ 콘솔에도 비밀 문구가 없다 — 여정 전체에서 모은 줄을 한 번에 센다.
  expect(consoleLines.join('\n')).not.toContain(journeyContext().passphrase);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});
