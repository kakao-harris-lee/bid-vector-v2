import { expect, test, type Page, type Request, type Route } from '@playwright/test';

import {
  assertOnlyAllowedOperations,
  clearStrategyTerms,
  journeyContext,
  signIn,
  watchRequests,
} from './shared';

/**
 * 여정 ①(M7/7E-2b, D-7E2b-5 · M9 D-2) — 빈 계정 → 시작하기 → 마법사로 항목을 채움 → 되돌림.
 *
 * **금액대도 적용하고 되돌린다**(M9 D-1 · D-2). D-7E2b-9 의 「값을 적고 그만둔다」는 계약에
 * 비우는 편집 값이 없어서였고(`amountWon` 생략·`null`·`terms:[]` 모두 400), 그 자리가
 * `amountWon: null` 로 열렸다 — 이제 적용하면 **되돌릴 길이 있다**. 되돌리는 것이 이 여정의
 * 의무다: 금액대가 남으면 `GET /api/suggestions` 가 `WATCH_NOT_CONFIGURED` → `NO_MATCH` 로
 * 바뀌어 여정 ② 와 설정 여정의 출발 단언이 실행 순서에 따라 깨진다. 그래서 되돌림을 **본문에서
 * 단언하고** teardown 에 안전망을 둔다.
 *
 * `LICENSES` 는 아직 되돌릴 operation 이 없다(V12 행을 지우는 길이 계약에 없다). 그 사실을
 * **숨기지 않고 단언한다** — 되돌림 뒤에도 완료로 남는다. 여정 ②③ 의 단언에는 영향이 없다(착수
 * 실측: 면허 신고 여부와 무관하게 `licenseVerdict=UNCERTAIN`).
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

/** 체크리스트 읽기인가 — 포착 술어가 한 자리다(요청 장부와 응답 포착이 같은 것을 센다). */
function isChecklistRead(one: Request): boolean {
  return one.url().endsWith('/api/onboarding-checklist') && one.method() === 'GET';
}

/**
 * 다음에 닿는 200 읽기 — **쓰기가 없는 자리**(탐색)에서만 쓴다. 기준점으로 삼을 쓰기가 없으므로
 * 출발 순서로 거를 것도 없다(탐색 전후의 상태가 같다).
 */
function checklistResponse(page: Page) {
  return page.waitForResponse((one) => isChecklistRead(one.request()) && one.status() === 200);
}

interface ChecklistReads {
  /**
   * [write] 로 끝나는 POST 의 응답 **뒤에 출발한** 첫 200 체크리스트 읽기.
   *
   * 쓰기 뒤 다시 읽기가 **없으면** 이 기다림은 닫히지 않고 타임아웃이다(낡은 값으로 초록이 되는
   * 것보다 낫다 — CR r1 L-2). 그때 볼 자리는 쓰기의 `invalidateQueries` 다.
   */
  readonly afterWrite: (write: string) => Promise<Checklist>;
}

/**
 * 체크리스트 읽기의 **출발 장부**(여정 시작에 한 번 건다) — 「쓰기 뒤 다시 읽기」를 기다리는 자리가
 * 이것으로 **그 쓰기 응답 뒤에 출발한** 읽기만 받는다.
 *
 * 「다음에 오는 200 GET」으로는 닫히지 않는다(W-13 간헐 실패 · PR #118 v r1 H-1): 클라이언트는
 * 쓰기가 성공한 뒤에만 체크리스트를 다시 읽지만(`useProfileWrite` → `invalidateQueries`), **쓰기
 * 전에 출발한** 읽기의 응답이 그 뒤에 닿을 수 있다 — 홈을 거쳐 들어오면 배너와 화면이 같은 질의를
 * 각각 걸어 응답이 둘이다. 그 늦은 응답이 포착되면 단언은 쓰기 **전** 상태를 본다(실측: 지우기
 * 자리에서 `pending` 이 `["BUDGET_RANGE"]`). 화면 단언을 먼저 두는 것으로는 「늦은 응답이 아직
 * 날아오는 중이 아니다」가 서지 않는다 — 그래서 **요청 출발 순서**로 거른다.
 */
function checklistReads(page: Page): ChecklistReads {
  // 장부는 **마지막 쓰기까지** 필요하고 그 뒤 곧 페이지가 닫힌다 — 해제 자리를 두어도 버는 것이
  // 없고, 이 여정의 체크리스트 읽기는 십여 건이다(CR r1 L-5 가 「기록으로만」이라 적은 자리).
  const started = new Set<Request>();
  page.on('request', (one) => {
    if (isChecklistRead(one)) {
      started.add(one);
    }
  });
  return {
    afterWrite(write: string): Promise<Checklist> {
      // 봉인은 **응답 핸들러 안에서** 한다 — 그러면 봉인과 다시 읽기의 출발 순서가 **이벤트
      // 디스패치로** 보장된다. `await` 뒤로 미룬 판은 지금 실측으로는 구분되지 않지만(v r1 L-1
      // 변이 M3 GREEN — 마이크로태스크 continuation 이 다음 프로토콜 메시지보다 먼저 돈다),
      // 그 판의 순서는 런타임의 처리 순서에 달린다. 더 안전한 쪽을 둔다.
      let stale: ReadonlySet<Request> | null = null;
      const read = page.waitForResponse(
        (one) =>
          stale !== null &&
          isChecklistRead(one.request()) &&
          one.status() === 200 &&
          !stale.has(one.request()),
      );
      const written = page.waitForResponse((one) => {
        if (one.request().method() !== 'POST' || !one.url().endsWith(write)) {
          return false;
        }
        stale = new Set(started);
        return true;
      });
      // 쓰기 단언이 터지면 `read` 는 관측되지 않은 채 남아 뒤늦게 reject 된다 — 이미 실패한
      // test 에 노이즈만 붙으므로 삼킨다(CR r1 L-3).
      void read.catch(() => {});
      return (async () => {
        expect((await written).status(), `${write} 쓰기 응답`).toBe(200);
        return (await read).json() as Promise<Checklist>;
      })();
    },
  };
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

/** 폼 하나 = 쓰기 하나 — 그 쓰기의 응답이 다시 읽기의 **기준점**이다(표가 한 자리다). */
const SAVE_WRITES = {
  '사업 정보 저장': '/api/profile/business-details',
  '자격 사실 저장': '/api/profile/qualification-facts',
  '시평액 저장': '/api/profile/construction-capacities',
} as const;

/**
 * 전략의 쓰기 — `/api/strategy/edit-sessions/{sessionId}/confirm` 의 접미다(세션 id 가 경로에 있어
 * 전체 경로를 적을 수 없다). 승인 목록에 `/confirm` 으로 끝나는 POST 는 이것 하나뿐이고, 새 것이
 * 생기면 그 목록이 먼저 걸린다(CR r1 L-1).
 */
const STRATEGY_CONFIRM = '/confirm';

/** 쓰기 하나 — 누르고 **그 쓰기 뒤에 출발한 다시 읽기**를 돌려준다(쓰기 응답을 완료로 세지 않는다). */
async function saveAndReread(
  page: Page,
  reads: ChecklistReads,
  button: keyof typeof SAVE_WRITES,
): Promise<Checklist> {
  const read = reads.afterWrite(SAVE_WRITES[button]);
  await page.getByRole('button', { name: button }).click();
  return read;
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

/** 로그인 전에 죽은 실행 — 전역 상태를 바꿀 기회도 없었으므로 되돌릴 것이 없다. */
const HTTP_UNAUTHORIZED = 401;

/**
 * 금액대 되돌림의 **안전망**(M9 D-2) — 본문이 되돌림까지 단언하지만 그 사이에서 터지면 적용된
 * 금액대가 남아 **여정 ②·설정 여정의 출발 단언**이 남의 잔류로 깨진다. 그 실패가 가장 비싼 꼴이라
 * teardown 에서 한 번 더 비운다(이미 비어 있으면 아무것도 보내지 않는다).
 *
 * `shared.ts` 의 어휘 되돌림(`clearStrategyTerms`)과 걸음은 같지만 **비우는 표기가 다르다**
 * (`terms: []` ↔ `amountWon: null`). 둘을 한 함수로 합치는 것은 설정 여정 teardown 의 중복과 함께
 * 뒤 slice 가 받는다(7E-2b 알려진 제한 ⑥ 과 같은 자리).
 *
 * 되돌리지 못한 사유를 **돌려준다** — 여기서 단언하면 뒤따르는 어휘 되돌림이 돌지 않는다.
 */
async function clearBudgetIfSet(page: Page): Promise<string | null> {
  const read = await page.request.get('/api/strategy');
  if (read.status() === HTTP_UNAUTHORIZED) {
    return null;
  }
  if (!read.ok()) {
    return `전략을 읽지 못해 금액대를 되돌릴 수 없었다(GET /api/strategy ${read.status()})`;
  }
  const strategy = (await read.json()) as { readonly minBudgetWon: number | null; readonly revision: number };
  if (strategy.minBudgetWon === null) {
    return null;
  }
  const session = (await (await page.request.get('/api/session')).json()) as { readonly csrf: string };
  const headers = { 'X-Operator-Csrf': session.csrf };
  const begin = await page.request.post('/api/strategy/edit-sessions', { headers, data: { field: 'MIN_BUDGET' } });
  if (!begin.ok()) {
    return `편집 세션을 열지 못해 금액대를 되돌릴 수 없었다(POST edit-sessions ${begin.status()})`;
  }
  const { sessionId } = (await begin.json()) as { readonly sessionId?: string };
  if (sessionId === undefined) {
    return '편집 세션 응답에 sessionId 가 없어 금액대를 되돌릴 수 없었다';
  }
  const stamp = Date.now();
  const valued = await page.request.post(`/api/strategy/edit-sessions/${sessionId}/value`, {
    headers,
    data: { commandId: `budget-teardown-value-${stamp}`, field: 'MIN_BUDGET', amountWon: null },
  });
  if (!valued.ok()) {
    // 세션을 닫아 다음 실행의 `begin` 을 막지 않는다(어휘 되돌림과 같은 규율).
    await page.request.post(`/api/strategy/edit-sessions/${sessionId}/cancel`, {
      headers,
      data: { commandId: `budget-teardown-cancel-${stamp}` },
    });
    return `금액대 되돌림 값이 거부됐다(value ${valued.status()})`;
  }
  const confirmed = await page.request.post(`/api/strategy/edit-sessions/${sessionId}/confirm`, {
    headers,
    data: { commandId: `budget-teardown-confirm-${stamp}`, seenRevision: strategy.revision },
  });
  return confirmed.ok() ? null : `금액대 되돌림 적용이 거부됐다(confirm ${confirmed.status()})`;
}

test.afterEach(async ({ page }) => {
  // 금액대도 **전역 전략**이다 — 본문의 되돌림이 지나지 못한 실행에서 여기가 받는다.
  const budgetLeft = await clearBudgetIfSet(page);
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
    readonly minBudgetWon: number | null;
  };
  expect(after.focusCategories, '여정 ① 이 전역 감시 조건을 남겼다').toStrictEqual([]);
  // **서버에서** 센다 — 안전망이 돌았는지가 아니라 남은 것이 0 인지가 뒤 여정의 전제다.
  expect(
    after.minBudgetWon,
    `여정 ① 이 전역 금액대를 남겼다${budgetLeft === null ? '' : ` — ${budgetLeft}`}`,
  ).toBeNull();
});

/** D-7E-6 — 지금 화면에 비밀 문구도 사업자번호 꼴도 없다. 두 자리에서 부른다(채운 뒤·비운 뒤). */
async function assertNoPrivateValues(page: Page): Promise<void> {
  const shown = await page.locator('body').innerText();
  expect(shown).not.toContain(journeyContext().passphrase);
  expect(shown).not.toMatch(/\b\d{3}-\d{2}-\d{5}\b/u);
}

/** 시평액 행 지우기의 쓰기 — ⑦ 의 기준점이고 경합 대조도 같은 자리를 쓴다. */
const CAPACITY_REMOVE = '/api/profile/construction-capacities/remove';

/** 경합 대조가 **자기 읽기에만** 붙이는 표지 — 역할을 순번이 아니라 이것으로 가른다. */
const LATE_READ_HEADER = 'x-late-read';

interface LateRead {
  /** 늦춘 응답이 **닿았는가** — 닿지 않았으면 경합이 없었던 것이고 대조가 빈다. */
  readonly delivered: () => boolean;
  /** 대조를 걷는다 — 뒤 단계의 읽기는 손대지 않는다. */
  readonly stop: () => Promise<void>;
}

/**
 * 간헐 실패의 꼴을 **인위로 만든다**(⑦ 의 상시 대조). 쓰기 **전**에 출발한 읽기를 하나 띄워 두고
 * 그 응답을 **쓰기 응답 바로 뒤에** 놓고, 쓰기 뒤 다시 읽기는 1초 미룬다 — 늦은 응답이 **먼저**
 * 닿으므로 「다음에 오는 200 GET」으로 포착하는 구판은 이 자리에서 반드시 그것을 집는다(RED 실측은
 * evidence 에 있다). 미루지 않으면 늦은 응답이 다시 읽기보다 20ms 가량 **앞서는 데 그쳐**, 대조가
 * 호스트 부하에 달린 경주가 된다(착수 실측).
 *
 * 요청을 내는 것은 화면이 아니라 이 하네스다 — 앱의 중복 질의(배너와 화면이 같은 질의를 각각 거는
 * 자리)는 출발 시점을 손에 쥘 수 없다.
 *
 * **`times` 를 쓰지 않는다**(착수 실측): 마지막 가로채기에서 경로가 걷히면 Playwright 가 가로채기
 * 자체를 끄고 **대기 중인 요청을 그대로 흘려보낸다** — 미루기가 조용히 사라지고, 뒤늦은
 * `route.continue()` 가 「Route is already handled」로 터진다(20회 중 5회). 그래서 경로를 남겨 두고
 * [LateRead.stop] 으로 걷는다.
 *
 * **역할은 순번이 아니라 요청 표지로 가른다**(CR r1 M-1): 가로채기를 건 뒤 하네스의 읽기보다 앞서
 * 앱의 읽기가 하나 끼면 순번이 뒤바뀌어, 늦추는 것과 미루는 것이 서로 자리를 바꾼 채 **조용히 다른
 * 것을 재게** 된다. 표지는 **헤더**다 — 질의 문자열을 쓰면 URL 이 달라져 [isChecklistRead] 와
 * 구판의 포착 술어가 어긋나고(그 응답을 집지 않게 되어) 재현 자체가 무력해진다. 같은 출처라
 * preflight 도 없다. 미루는 쪽도 **쓰기 응답을 본 뒤** 첫 읽기로 못박는다.
 *
 * **이 자리 전용이다**(CR r1 M-2): 쓰기 경로(`CAPACITY_REMOVE`)와 「지우기 전에는 미완이 아니다」는
 * 항목이 둘 다 안에 박혀 있다. 다른 쓰기에 그대로 쓰면 그 자기 점검이 **공허하게 통과**해 대조가
 * 조용히 빈다 — 범용이 필요해지면 그 항목을 인자로 받아 넓힌다.
 */
async function raceLateReadOnCapacityRemove(page: Page): Promise<LateRead> {
  const before = await page.request.get('/api/onboarding-checklist');
  expect(before.status(), '늦출 응답을 읽지 못했다').toBe(200);
  const stale = (await before.json()) as Checklist;
  expect(stale.pending, '늦출 응답은 지우기 전 상태다').not.toContain('CONSTRUCTION_CAPACITY');
  const written = page.waitForResponse(
    (one) => one.request().method() === 'POST' && one.url().endsWith(CAPACITY_REMOVE),
  );
  let writeSeen = false;
  void written.then(
    () => {
      writeSeen = true;
    },
    () => {},
  );
  const pattern = '**/api/onboarding-checklist';
  let delivered = false;
  let delayed = false;
  const intercept = async (route: Route) => {
    if (route.request().headers()[LATE_READ_HEADER] === '1') {
      // 하네스가 낸 읽기 — 쓰기 응답을 본 뒤에 놓는다(쓰기 전에 출발해 쓰기 뒤에 닿는 응답).
      await written;
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(stale) });
      delivered = true;
      return;
    }
    if (writeSeen && !delayed) {
      // 쓰기 뒤 다시 읽기 — 늦은 응답이 먼저 닿게 이것만 미룬다.
      delayed = true;
      await new Promise((done) => setTimeout(done, 1_000));
    }
    await route.continue();
  };
  await page.route(pattern, intercept);
  await page.evaluate(
    (header) => {
      void fetch('/api/onboarding-checklist', { headers: { [header]: '1' } });
    },
    LATE_READ_HEADER,
  );
  return { delivered: () => delivered, stop: () => page.unroute(pattern, intercept) };
}

test('여정 ① — 빈 계정 → 시작하기 마법사로 항목을 채우고 되돌린다', async ({ page }) => {
  const watched = watchRequests(page);
  // 체크리스트 읽기의 출발 장부 — **여정 시작에** 건다(쓰기 전에 출발한 읽기를 하나도 놓치지
  // 않아야 그 응답을 낡은 것으로 가를 수 있다).
  const reads = checklistReads(page);
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
  const afterDetails = await saveAndReread(page, reads, '사업 정보 저장');

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
  const afterFacts = await saveAndReread(page, reads, '자격 사실 저장');

  expect(afterFacts.completed).toContain('BUSINESS_TYPES');
  expect(afterFacts.completed).toContain('LICENSES');
  await assertRowsMatch(page, afterFacts);

  // ④ 시공능력평가액 — 행 단위다.
  await page.getByTestId('onboarding-step-CONSTRUCTION_CAPACITY').click();
  await page.getByLabel('시평액 면허명').fill('토목건축공사업');
  await page.getByLabel('시평액 금액').fill('1200000000');
  await page.getByLabel('평가 연도').fill('2026');
  const afterCapacity = await saveAndReread(page, reads, '시평액 저장');

  expect(afterCapacity.completed).toContain('CONSTRUCTION_CAPACITY');
  await assertRowsMatch(page, afterCapacity);

  // ④-b D-7E-6 — **프로필이 채워진 상태**에서 본다(v r1 L-2). 아래 ⑨ 는 비운 뒤라, 값이 실제로
  // 화면에 그려지는 동안 비밀 문구·사업자번호 꼴이 없는지는 이 자리에서만 잰다.
  await assertNoPrivateValues(page);

  // ⑤ 감시 관심 업종 — 전략은 **편집 세션**으로만 바뀐다.
  await page.getByTestId('onboarding-step-WATCH').click();
  await editStrategyField(page, 'FOCUS_CATEGORY', '토목');
  // 전략의 쓰기는 편집 세션 `confirm` 이다 — 그 응답이 다시 읽기의 기준점이다.
  const watchApplied = reads.afterWrite(STRATEGY_CONFIRM);
  await page.getByTestId('edit-session').getByRole('button', { name: '적용' }).click();
  const afterWatch = await watchApplied;

  expect(afterWatch.completed).toContain('WATCH_CONDITIONS');
  await assertRowsMatch(page, afterWatch);

  // ⑥ 금액대 — **적용한다**(M9 D-1 · D-2). 아래 ⑦-b 가 같은 폼으로 되돌린다.
  await page.getByTestId('onboarding-step-BUDGET').click();
  await editStrategyField(page, 'MIN_BUDGET', '100000000');
  await expect(page.getByTestId('edit-sent-value')).toHaveText('100,000,000원');
  const budgetApplied = reads.afterWrite(STRATEGY_CONFIRM);
  await page.getByTestId('edit-session').getByRole('button', { name: '적용' }).click();
  const afterBudget = await budgetApplied;

  expect(afterBudget.completed, '금액대를 적용하면 완료로 간다').toContain('BUDGET_RANGE');
  await assertRowsMatch(page, afterBudget);
  // **미완이 0 이 됐다** — 이 여정이 처음으로 그 얼굴에 닿는다(D-7E2b-4 의 「다음 행동 하나」는
  // 금액대를 적용할 수 없던 동안 화면 test 로만 잠겨 있었다).
  expect(afterBudget.pending, '여덟 항목이 전부 채워졌다').toStrictEqual([]);
  await expect(page.getByTestId('onboarding-complete')).toBeVisible();
  // 적용 뒤 **단계가 옮겨 간다**(쓰기 뒤 다음 미완으로 — 미완이 0 이면 첫 단계로). 그래서 값을
  // 보려면 그 단계로 돌아온다 — 화면과 **서버** 둘 다 본다(화면만 보면 「다시 읽지 않아서 그렇게
  // 보인다」와 구분되지 않는다).
  await page.getByTestId('onboarding-step-BUDGET').click();
  await expect(page.getByTestId('strategy-value-MIN_BUDGET')).toHaveText('100,000,000원');
  const strategyWithBudget = (await (await page.request.get('/api/strategy')).json()) as {
    readonly minBudgetWon: number | null;
  };
  expect(strategyWithBudget.minBudgetWon, '적용된 금액대는 서버에 있다').toBe(100_000_000);

  // **그만두기는 아무것도 쓰지 않는다** — 적용 경로가 열린 뒤에도 그 축은 그대로다(같은 폼의
  // 두 출구를 한 여정에서 함께 본다). 값을 적어 보내고 그만둔 뒤 서버 값이 위와 같아야 한다.
  await editStrategyField(page, 'MAX_BUDGET', '900000000');
  await page.getByTestId('edit-session').getByRole('button', { name: '그만두기' }).click();
  await expect(page.getByTestId('edit-session')).toHaveCount(0);
  const strategyAfterCancel = (await (await page.request.get('/api/strategy')).json()) as {
    readonly maxBudgetWon: number | null;
  };
  expect(strategyAfterCancel.maxBudgetWon, '그만둔 편집은 서버에 아무것도 쓰지 않는다').toBeNull();

  // ⑦ 되돌림 — **화면으로** 되돌린다(같은 폼이 그 능력을 쥔다).
  await page.getByTestId('onboarding-step-CONSTRUCTION_CAPACITY').click();
  // 화면이 최신을 그렸다 — 지우기 전 상태가 **완료**다(이 단언은 포착 경합을 닫지 않는다. 그것은
  // 아래 출발 순서가 한다 — 「화면이 최신을 그렸다」는 「늦은 응답이 아직 날아오는 중이 아니다」가
  // 아니다).
  await expect(page.getByTestId('onboarding-state-CONSTRUCTION_CAPACITY')).toHaveText('완료');
  // 늦은 응답을 **인위로 하나 띄워 둔 채** 지운다 — 포착이 출발 순서로 거르는지를 매 실행 잰다.
  const late = await raceLateReadOnCapacityRemove(page);
  const capacityGone = reads.afterWrite(CAPACITY_REMOVE);
  await page.getByTestId('capacity-토목건축공사업').getByRole('button', { name: '지우기' }).click();
  expect((await capacityGone).pending, '포착된 것은 지우기 뒤 다시 읽기다').toContain('CONSTRUCTION_CAPACITY');
  // 그 늦은 응답이 **실제로 닿았다** — 닿지 않았으면 위 단언은 경합 없이 지난 것이고 대조가 비었다.
  await expect
    .poll(late.delivered, { message: '인위로 늦춘 체크리스트 응답이 닿지 않았다 — 경합 대조가 비었다' })
    .toBe(true);
  await late.stop();

  await page.getByTestId('onboarding-step-QUALIFICATION_FACTS').click();
  // ③ 과 같은 사유로 `exact` 다(CR r1 지적이 ③ 만 닫혔던 자리 — CR r2 LOW).
  await page.getByLabel('업종', { exact: true }).fill('');
  await page.getByLabel('지역어').fill('');
  await page.getByLabel('면허를 신고함').uncheck();
  const afterFactsCleared = await saveAndReread(page, reads, '자격 사실 저장');

  expect(afterFactsCleared.pending).toContain('BUSINESS_TYPES');
  // **되돌릴 operation 이 없는 항목**: V12 행이 남아 면허는 완료로 선다(착수 실측 · 숨기지 않는다).
  expect(afterFactsCleared.completed, '면허는 되돌릴 길이 계약에 없다').toContain('LICENSES');

  await page.getByTestId('onboarding-step-BUSINESS_DETAILS').click();
  await page.getByLabel('상호').fill('');
  await page.getByLabel('본사 소재지').selectOption('');
  await page.getByLabel('나라장터 준비').selectOption('UNANSWERED');
  const afterDetailsCleared = await saveAndReread(page, reads, '사업 정보 저장');

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
  const watchCleared = reads.afterWrite(STRATEGY_CONFIRM);
  await page.getByTestId('edit-session').getByRole('button', { name: '적용' }).click();
  const afterWatchCleared = await watchCleared;
  expect(afterWatchCleared.pending).toContain('WATCH_CONDITIONS');

  // ⑦-b 금액대 되돌림(M9 D-1 · D-2) — **빈 칸이 비움이다**(어휘 칸과 같은 약속). 확인 화면이
  // 보이는 것도 어휘 비움과 같은 문면이고, 그 뒤 「적용」이 실제로 지운다.
  await page.getByTestId('onboarding-step-BUDGET').click();
  await editStrategyField(page, 'MIN_BUDGET', '');
  await expect(page.getByTestId('edit-sent-value')).toHaveText('비움 — 이 조건을 지웁니다');
  const budgetCleared = reads.afterWrite(STRATEGY_CONFIRM);
  await page.getByTestId('edit-session').getByRole('button', { name: '적용' }).click();
  const afterBudgetCleared = await budgetCleared;

  expect(afterBudgetCleared.pending, '비우면 금액대가 미완으로 돌아온다').toContain('BUDGET_RANGE');
  // 적용 뒤 단계가 옮겨 가므로 다시 그 단계로 온다(위 ⑥ 과 같은 사유).
  await page.getByTestId('onboarding-step-BUDGET').click();
  await expect(page.getByTestId('strategy-value-MIN_BUDGET')).toHaveText('값 없음');
  const strategyAfterClear = (await (await page.request.get('/api/strategy')).json()) as {
    readonly minBudgetWon: number | null;
  };
  expect(strategyAfterClear.minBudgetWon, '비움은 서버에서도 비어 있다').toBeNull();
  // **다시 물어도 같다** — 홈을 거쳐 들어와 서버에 새로 묻는다(화면이 쥐고 있는 값이 아니다).
  const reopened = await reopenOnboarding(page);
  expect(reopened.pending, '다시 물어도 금액대는 미완이다').toContain('BUDGET_RANGE');
  await assertRowsMatch(page, reopened);

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
