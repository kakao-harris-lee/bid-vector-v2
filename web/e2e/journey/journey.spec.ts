import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

import { expect, test, type Locator, type Page, type Request } from '@playwright/test';

import { APPROVED_OPERATIONS } from '../../src/api/approvedOperations';

/**
 * 여정 ②③ 의 e2e(D-7E-3) — **출하 app.jar · 버릴 Postgres · loopback fake KONEPS** 위에서
 * 돈다(API 목 0). 공고는 수집 경로로 들어왔고 화면은 V2 API 만 부른다.
 *
 * 단언의 규율: **DOM 에서 읽은 값을 같은 실행의 API 응답과 대조한다.** 기대값을 spec 에 적지
 * 않는다 — 적으면 「화면이 보이는 것」과 「서버가 준 것」이 갈려도 test 가 초록일 수 있다.
 * 그래서 `?? ''`·`?? 0` 같은 **폴백 기대값을 쓰지 않는다**(v M-2 · CR M-6): null 인 축은 화면의
 * 빈 자리 문면과 대조하고 두 갈래를 각각 단언한다.
 *
 * 각 여정은 **단독으로도 선다**(v M-1) — 앞 여정이 무엇을 담았는지에 기대지 않고, 기다림은
 * 전부 조건 대기다(`isVisible()` 즉시 검사를 쓰지 않는다).
 *
 * 하네스가 적어 준 실행 맥락은 **Playwright 산출물 밖**이다(D-7E-6).
 */
interface JourneyContext {
  readonly previewUrl: string;
  readonly loginName: string;
  readonly passphrase: string;
  /** 이 실행의 번들이 낸 자산 경로 전수 — 하네스가 빌드 직후 적는다(v r3 M-2). */
  readonly bundleAssets: readonly string[];
}

const CONTEXT_PATH = resolve(tmpdir(), 'bidvector-7e1-journey-context.json');
let cached: JourneyContext | undefined;

/**
 * **지연 읽기**다(OQ-2). 모듈 적재 때 읽으면 하네스가 뜨지 않은 상태에서 **수집 자체가**
 * 실패한다 — `--list` 로 「이 설정이 무엇을 수집하는가」를 세는 길이 막히고, 수집 단계의 실패와
 * test 의 실패가 같은 모양이 된다. 함수로 미루면 없는 파일은 **test 의 실패**다.
 */
function journeyContext(): JourneyContext {
  cached ??= parseContext(readFileSync(CONTEXT_PATH, 'utf8'));
  return cached;
}

/**
 * 맥락 파일의 **모양을 한 번 본다**(CR r4 L-9). 앞 판은 검증 없이 캐스팅해서, 이 라운드 전의
 * 옛 꼴 파일이 남아 있으면 `bundleAssets` 가 `undefined` 이고 뒤에서 `TypeError` 로 터졌다 —
 * 멈추는 방향이라 안전하지만 **사유를 말하지 못한다**. 같은 파일의 다른 입구(`parseLockMarker`)와
 * 같은 꼴로 사유를 적는다.
 */
function parseContext(raw: string): JourneyContext {
  const parsed: unknown = JSON.parse(raw);
  if (typeof parsed !== 'object' || parsed === null) {
    throw new Error(`하네스 맥락이 객체가 아니다(${CONTEXT_PATH})`);
  }
  const candidate = parsed as Partial<JourneyContext>;
  const missing = (
    [
      ['previewUrl', typeof candidate.previewUrl === 'string'],
      ['loginName', typeof candidate.loginName === 'string'],
      ['passphrase', typeof candidate.passphrase === 'string'],
      ['bundleAssets', Array.isArray(candidate.bundleAssets)],
    ] as const
  )
    .filter(([, ok]) => !ok)
    .map(([key]) => key);
  if (missing.length > 0) {
    throw new Error(
      `하네스 맥락에 ${missing.join(', ')} 이(가) 없다(${CONTEXT_PATH}) — 옛 꼴 파일이 남았거나 ` +
        '하네스가 그 자리까지 가지 못했다. 그 파일을 지우고 다시 돌리십시오.',
    );
  }
  return candidate as JourneyContext;
}

function sameOrigin(): string {
  return new URL(journeyContext().previewUrl).origin;
}

/** 화면의 「값 없음」 문면 — null 인 축은 이 문면과 대조한다. */
const ABSENT = '값 없음';

interface NoticeRow {
  readonly noticeNumber: string;
  readonly noticeRound: string;
  readonly status: string;
  readonly title: string | null;
  readonly demandAgencyName: string | null;
  readonly noticeAgencyName: string | null;
  readonly baseAmountWon: number | null;
  readonly baseAmountBasis: string | null;
  readonly baseAmountProvenance: string | null;
}

interface BidCaseRow {
  readonly caseId: number;
  readonly noticeNumber: string;
  readonly noticeRound: string;
  readonly state: string;
  readonly caseVersion: number;
  readonly nextCheckpointKind: string;
  readonly nextCheckpointAt: string | null;
  readonly overdue: boolean;
}

interface PriceBody {
  readonly currentActiveBids: number;
  readonly maxActiveBids: number;
  readonly bidPriceMenuState: string;
  readonly floorRateGroundsState: string;
  readonly publishedFloorRateFraction: string | null;
  readonly licenseVerdict: string;
  readonly licenseValidity: string;
  readonly unjudgedAxes: readonly string[];
  readonly disclosures: readonly string[];
}

/**
 * 계약의 상태 → 화면 문면. **spec 이 문면을 지어내지 않고** 표에서 찾는다 — 화면의 표와 같은
 * 어휘이고, 화면이 그 문면을 바꾸면 이 대조가 붉어진다.
 */
const NOTICE_STATUS_LABEL: Readonly<Record<string, string>> = {
  Open: '입찰 중',
  Renoticed: '재공고',
  Closed: '마감',
  Awarded: '낙찰자 결정',
  Failed: '유찰',
  Cancelled: '취소',
};
const CASE_STATE_LABEL: Readonly<Record<string, string>> = {
  WATCHING: '관심',
  PRICE_RECEIVED: '투찰가 받음',
  BID_PLANNED: '투찰 예정',
  BID_RECORDED: '투찰 기록됨',
  AWAITING_OPENING: '개찰 대기',
};

/**
 * 상태 → **화면 D 의 탭 문면**(M7/7E-2). 화면의 표를 import 하지 않고 spec 이 자기 몫을
 * 적는다 — 같은 출처에서 양쪽을 끌어오면 표가 틀려도 대조가 초록이다(`CASE_STATE_LABEL` 과
 * 같은 이유).
 */
const CASE_TAB_LABEL: Readonly<Record<string, string>> = {
  WATCHING: '관심',
  DEFERRED: '관심',
  PRICE_RECEIVED: '투찰가 받음',
  BID_PLANNED: '투찰가 받음',
  BID_RECORDED: '투찰함',
  AWAITING_OPENING: '개찰 대기',
  OPENED_PROVISIONAL_FIRST: '심사 중',
  OPENED_OUT_OF_RANK: '심사 중',
  UNDER_QUALIFICATION_REVIEW: '심사 중',
  WON: '종료',
  LOST: '종료',
  FAILED_OR_REBID: '종료',
  SKIPPED: '종료',
  DEADLINE_PASSED_UNRECORDED: '종료',
};
const CHECKPOINT_LABEL: Readonly<Record<string, string>> = {
  DEADLINE: '투찰 마감',
  OPENING: '개찰',
  NONE: '없음',
};
const UNJUDGED_LABEL: Readonly<Record<string, string>> = {
  QUAL_08_REGION: '지역',
  QUAL_10_PERFORMANCE: '수행실적',
  QUAL_11_CONSTRUCTION_CAPACITY: '시공능력평가금액',
};

/**
 * 같은 출처의 **비 API 요청**도 아는 종류만 받는다(v r2 M-2). 앞 판은 `/api/` 밖 요청을 아예
 * 기록하지 않았고, 그래서 화면이 `new URL('./rows.txt', import.meta.url)` 로 내보낸 자산을
 * 읽어 가도 「V2 API 밖」 감시가 그것을 보지 못했다(실측).
 */
/** 라우터가 **문서**로 받는 경로 — 화면의 주소다(자산이 아니다). */
const ALLOWED_ROUTES = [/^\/$/u, /^\/login$/u, /^\/notices\//u, /^\/bids$/u];

/**
 * 자산은 **이 빌드가 실제로 낸 파일 이름만** 받는다(v r3 M-2 · CR r3 M-4).
 *
 * **이 층이 사는 값을 정확히 적는다**(CR r4 L-5): 허용 집합은 하네스가 그 빌드에서 읽은 목록이라
 * 자산이 늘면 양쪽이 함께 는다 — 「번들이 커지면 먼저 깨진다」는 **이 단언의 성질이 아니다**.
 * 그 축은 하네스가 진다(산출물 모양을 {js 1 · css 1} 로 못박고, 늘면 거기서 멈춘다). 여기서
 * 잡는 것은 **이 빌드가 내지 않은** 같은 출처 요청이다: `public/` 복사물 · 손으로 적은 경로 ·
 * 묵은 자산. 번들 안으로 들어온 데이터는 자산 술어(`importedCss`)와 `import.meta` 금지가 막는다.
 */
function bundleAssetPaths(): Set<string> {
  const names = journeyContext().bundleAssets;
  expect(names.length, '번들 산출물 목록이 비면 아래 단언이 공허해진다').toBeGreaterThan(0);
  return new Set(names);
}

/** 브라우저가 스스로 거는 요청 — 화면 코드가 부르는 것이 아니다(오늘 404 다). */
const BROWSER_INITIATED = ['/favicon.ico'];

/** 화면이 **V2 API 밖**을 부르지 않는지, **목록 안**만 부르는지, 자산도 아는 종류인지 센다. */
function watchRequests(page: Page): {
  readonly outside: string[];
  readonly apiPaths: Set<string>;
  readonly unexpectedSameOrigin: string[];
} {
  const allowedAssets = bundleAssetPaths();
  const outside: string[] = [];
  const apiPaths = new Set<string>();
  const unexpectedSameOrigin: string[] = [];
  page.on('request', (request: Request) => {
    const url = new URL(request.url());
    if (url.origin !== sameOrigin()) {
      outside.push(request.url());
      return;
    }
    if (url.pathname.startsWith('/api/')) {
      apiPaths.add(`${request.method()} ${url.pathname}`);
      return;
    }
    const known =
      ALLOWED_ROUTES.some((allowed) => allowed.test(url.pathname)) ||
      allowedAssets.has(url.pathname) ||
      BROWSER_INITIATED.includes(url.pathname);
    if (!known) {
      unexpectedSameOrigin.push(url.pathname);
    }
  });
  return { outside, apiPaths, unexpectedSameOrigin };
}

/**
 * 런타임 대조 집합을 **승인 목록에서 파생**한다(v r3 M-1). 앞 판은 같은 목록을 이 파일에
 * 복제했고, r2 에서 세 쌍을 지웠을 때 **여기만 남아** 두 자리가 갈렸다(실측: 지운 셋이 여전히
 * 허용 목록에 있었다). 한 자리에서 파생하면 그 갈림이 생기지 않는다.
 */
const ALLOWED_API = new Set(APPROVED_OPERATIONS.map((one) => `${one.method} ${one.path}`));

function templated(methodAndPath: string): string {
  return methodAndPath.replace(/\/api\/bid-cases\/\d+/u, '/api/bid-cases/{caseId}');
}

function assertOnlyAllowedOperations(apiPaths: Set<string>): void {
  const outside = [...apiPaths].map(templated).filter((one) => !ALLOWED_API.has(one));
  expect(outside, '화면이 계약 목록 밖의 operation 을 불렀다').toStrictEqual([]);
}

async function signIn(page: Page): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name="loginName"]', journeyContext().loginName);
  await page.fill('input[name="passphrase"]', journeyContext().passphrase);
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/api/session') && response.request().method() === 'POST',
    ),
    page.getByRole('button', { name: '로그인' }).click(),
  ]);
  await expect(page.getByTestId('screen-home')).toBeVisible();
}

/**
 * 검색 — 술어에 **질의까지** 넣는다(CR M-4). 경로·상태만 보면 화면 B 가 마운트 때 거는 빈 폼
 * 조회를 잡을 수 있고, 그러면 검색 경로를 **재지 않으면서 초록**이 된다.
 */
async function search(page: Page, query: string): Promise<NoticeRow[]> {
  const response = page.waitForResponse(
    (one) =>
      one.url().includes('/api/notices') &&
      new URL(one.url()).searchParams.get('q') === query &&
      one.status() === 200,
  );
  await page.fill('input[name="q"]', query);
  await page.getByRole('button', { name: '검색' }).click();
  return (await (await response).json()) as NoticeRow[];
}

/** 카드 **전부**를 응답 행과 대조한다(v M-2 — 한 행만 보면 모든 카드가 첫 행을 보여도 초록이다). */
async function assertCardsMatch(page: Page, rows: readonly NoticeRow[]): Promise<void> {
  expect(rows.length).toBeGreaterThan(0);
  for (const row of rows) {
    const card = page.getByTestId(`notice-card-${row.noticeNumber}`);
    await expect(card).toBeVisible();
    await expect(card.getByTestId('notice-title')).toHaveText(row.title ?? row.noticeNumber);
    await expect(card.getByTestId('notice-status')).toHaveText(NOTICE_STATUS_LABEL[row.status] ?? row.status);
    await expect(card.getByTestId('notice-agency')).toHaveText(
      row.demandAgencyName ?? row.noticeAgencyName ?? ABSENT,
    );

    if (row.baseAmountWon === null) {
      await expect(card.getByTestId('notice-amount')).toHaveText(ABSENT);
      continue;
    }
    await expect(card.getByTestId('notice-amount')).toHaveText(`${row.baseAmountWon.toLocaleString('ko-KR')}원`);
    // provenance 는 **null 이 아님을 먼저 단언**한다 — `toContainText('')` 는 무조건 통과하므로
    // 그 자리를 공허하게 두지 않는다(CR M-6).
    expect(row.baseAmountBasis, 'fixture 가 basis 를 싣는다').not.toBeNull();
    expect(row.baseAmountProvenance, 'fixture 가 provenance 를 싣는다').not.toBeNull();
    const provenance = card.getByTestId('notice-amount-provenance');
    await expect(provenance).toContainText(row.baseAmountBasis!);
    await expect(provenance).toContainText(row.baseAmountProvenance!);
  }
}

/**
 * 담기 — **멱등**이다(이미 담긴 건은 200). 조건 대기 뒤 버튼 유무로 가른다(v M-1 · CR M-5).
 * 반환은 「이 실행이 실제로 담았는가」다 — 반복 실행에서 토스트 단언을 가르는 데 쓴다(CR r2 L-12).
 */
async function ensureInBidBox(page: Page, card: Locator): Promise<boolean> {
  await expect(card).toBeVisible();
  const addButton = card.getByRole('button', { name: /담기/u });
  if ((await addButton.count()) === 0) {
    return false;
  }
  const added = page.waitForResponse(
    (one) => one.url().endsWith('/api/bid-cases') && one.request().method() === 'POST',
  );
  await addButton.click();
  expect([200, 201]).toContain((await added).status());
  return true;
}

/**
 * **`serial` 을 두지 않는다**(v r2 참고 · r3 H-1). `serial` 에서는 앞 여정이 실패하면 뒤 셋이
 * `did not run` 이 되어 **CI 실패 하나가 나머지 결과를 가린다**.
 *
 * 걷어도 순서는 그대로다 — 설정이 `workers: 1` 이라 한 하네스 위에서 차례로 돈다. 바뀌는 것은
 * **실패 전파**뿐이고, 넷이 서로에 기대지 않는 근거는 셋이다: 각 여정이 스스로 로그인하고,
 * 담기가 멱등이며(`ensureInBidBox`), 이미 담긴 건의 모양도 단언한다(CR r2 L-12). 단독 실행 넷이
 * 각각 초록인 것이 같은 사실의 측정이다.
 */

test('여정 ② — 로그인 → 검색 → 카드 → 담기', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const rows = await search(page, 'SYN-7E1');
  await assertCardsMatch(page, rows);

  // D-7E-9 — 제안 목록은 감시 조건이 없으므로 **API 상태 그대로** 빈 얼굴이다.
  const suggestions = page.getByTestId('suggestions-empty');
  await expect(suggestions).toBeVisible();
  await expect(suggestions).toContainText('감시 조건이 설정되지 않았습니다');

  const first = rows[0]!;
  const card = page.getByTestId(`notice-card-${first.noticeNumber}`);
  const added = await ensureInBidBox(page, card);

  // CR r2 L-12 — 같은 하네스에 두 번 미는 꼴(`--repeat-each`)에서는 둘째 회차에 이미 담겨 있다.
  // 그때는 토스트가 없고 카드가 「담긴 건」을 보인다 — 두 모양을 각각 단언한다.
  if (added) {
    const toast = page.getByTestId('toast');
    await expect(toast).toContainText('입찰함에 담았습니다');
    await expect(toast.getByRole('button', { name: '실행 취소' })).toHaveCount(0);
  } else {
    await expect(card).toContainText('담긴 건');
  }
  await expect(page.getByTestId('write-failure')).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

test('여정 ③ — 투찰가 요청 → 「아직 산출 안 함」과 근거 → 초안 → 제출함 → 타임라인', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const rows = await search(page, 'SYN-7E1-FLOOR-USABLE');
  const target = rows.find((row) => row.noticeNumber === 'SYN-7E1-FLOOR-USABLE')!;
  const card = page.getByTestId(`notice-card-${target.noticeNumber}`);
  await ensureInBidBox(page, card);

  // 워크시트로 — 그 화면이 스스로 공고와 건을 읽는다. 두 응답을 **이 실행에서** 받아 둔다.
  const worksheetNotices = page.waitForResponse(
    (one) =>
      one.url().includes('/api/notices') &&
      new URL(one.url()).searchParams.get('q') === target.noticeNumber &&
      one.status() === 200,
  );
  const worksheetCases = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  await card.getByTestId('notice-title').click();
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  const noticeRows = (await (await worksheetNotices).json()) as NoticeRow[];
  const caseRows = (await (await worksheetCases).json()) as BidCaseRow[];
  const notice = noticeRows.find((row) => row.noticeNumber === target.noticeNumber)!;
  const bidCase = caseRows.find((row) => row.noticeNumber === target.noticeNumber)!;

  // 머리 — **다섯 축을 전부** 응답과 대조한다(v M-2: 리터럴 다섯이 지나갔던 자리).
  await expect(page.getByTestId('worksheet-title')).toHaveText(notice.title ?? notice.noticeNumber);
  await expect(page.getByTestId('worksheet-agency')).toHaveText(
    notice.demandAgencyName ?? notice.noticeAgencyName ?? ABSENT,
  );
  await expect(page.getByTestId('worksheet-notice-status')).toHaveText(
    NOTICE_STATUS_LABEL[notice.status] ?? notice.status,
  );
  await expect(page.getByTestId('worksheet-case-state')).toHaveText(
    CASE_STATE_LABEL[bidCase.state] ?? bidCase.state,
  );
  await expect(page.getByTestId('worksheet-checkpoint')).toContainText(
    CHECKPOINT_LABEL[bidCase.nextCheckpointKind] ?? bidCase.nextCheckpointKind,
  );
  expect(notice.baseAmountWon, 'fixture 가 기초금액을 싣는다').not.toBeNull();
  await expect(page.getByTestId('worksheet-amount')).toContainText(
    notice.baseAmountWon!.toLocaleString('ko-KR'),
  );

  // 투찰가 요청 — 진행 중 건수를 **선언**해야 간다(CR H-1).
  await expect(page.getByTestId('worksheet-primary')).toHaveText('투찰가 요청');
  await page.getByTestId('active-bids').fill('2');
  const priced = page.waitForResponse(
    (one) => one.url().includes('/api/bid-price-requests') && one.request().method() === 'POST',
  );
  await page.getByTestId('worksheet-primary').click();
  const priceResponse = await priced;
  expect(priceResponse.status()).toBe(200);
  const price = (await priceResponse.json()) as PriceBody;

  // 선언한 값이 그대로 실려 되돌아오고 화면이 그것을 보인다(CR H-1).
  expect(price.currentActiveBids).toBe(2);
  const capacity = page.getByTestId('capacity');
  await expect(capacity).toContainText(String(price.currentActiveBids));
  await expect(capacity).toContainText(String(price.maxActiveBids));

  // 「아직 산출 안 함」 — 상태는 응답에서 오고 **그 자리에 숫자가 없다**.
  expect(price.bidPriceMenuState).toBe('NOT_COMPUTED');
  await expect(page.getByTestId('bid-price-menu')).toContainText('아직 산출 안 함');
  expect(await page.getByTestId('bid-price-menu-value').innerText()).not.toMatch(/\d/u);

  // 게시 하한율 — fixture 가 게시값을 가진 공고이므로 근거로 서고 **원문 그대로** 보인다.
  expect(price.floorRateGroundsState).toBe('USABLE');
  expect(price.publishedFloorRateFraction).not.toBeNull();
  await expect(page.getByTestId('floor-rate-grounds')).toContainText('게시 하한율');
  await expect(page.getByTestId('floor-rate-grounds')).toContainText(price.publishedFloorRateFraction!);

  // 자격 — 판정 불가·유효기간 미검증·아직 판정하지 않는 축 전수.
  expect(price.licenseVerdict).toBe('UNCERTAIN');
  await expect(page.getByTestId('license-verdict')).toContainText('면허 판정 불가');
  expect(price.licenseValidity).toBe('NOT_VERIFIED');
  await expect(page.getByTestId('license-validity')).toContainText('유효기간 미검증');
  expect(price.unjudgedAxes.length).toBeGreaterThan(0);
  for (const axis of price.unjudgedAxes) {
    await expect(page.getByTestId('unjudged-axes')).toContainText(UNJUDGED_LABEL[axis] ?? axis);
  }

  // 고지 — **응답이 준 코드 전수**가 문장으로 뜬다(법정 하한율 미해석 포함).
  expect(price.disclosures).toContain('STATUTORY_FLOOR_RATE_NOT_INTERPRETED');
  await expect(page.getByTestId('disclosures').getByRole('listitem')).toHaveCount(price.disclosures.length);
  await expect(page.getByTestId('disclosures')).toContainText('법정 하한율은 해석하지 않습니다');

  // 나라장터 이동 — 딥링크는 공고번호에서 조립되고 자동 제출이 아니다.
  await expect(page.getByTestId('koneps-deeplink')).toHaveAttribute(
    'href',
    new RegExp(encodeURIComponent(target.noticeNumber), 'u'),
  );

  // 투찰 예정 초안 — 금액·과세를 적으면 primary 가 초안으로 바뀌어 있다.
  await page.getByTestId('record-amount').fill('1050000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  const drafted = page.waitForResponse((one) => one.url().includes('/draft') && one.request().method() === 'POST');
  await expect(page.getByTestId('worksheet-primary')).toHaveText('투찰 예정 초안');
  await page.getByTestId('worksheet-primary').click();
  const draftResponse = await drafted;
  expect(draftResponse.status()).toBe(200);
  const draftedCase = (await draftResponse.json()) as BidCaseRow;
  await expect(page.getByTestId('worksheet-case-state')).toHaveText(
    CASE_STATE_LABEL[draftedCase.state] ?? draftedCase.state,
  );

  // 「제출함」 — 같은 입력으로 기록하고 **응답이 준 상태**를 화면이 보인다.
  await page.getByTestId('record-amount').fill('1050000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  await page.getByTestId('record-draw-numbers').fill('3, 7');
  const recorded = page.waitForResponse((one) => one.url().includes('/record') && one.request().method() === 'POST');
  const timelineAfterRecord = page.waitForResponse(
    (one) => one.url().includes('/timeline') && one.status() === 200,
  );
  await expect(page.getByTestId('worksheet-primary')).toHaveText('제출함');
  await page.getByTestId('worksheet-primary').click();
  const recordResponse = await recorded;
  expect(recordResponse.status()).toBe(200);
  const recordedCase = (await recordResponse.json()) as BidCaseRow;
  await expect(page.getByTestId('worksheet-case-state')).toHaveText(
    CASE_STATE_LABEL[recordedCase.state] ?? recordedCase.state,
  );
  // 기록 뒤 다음 확인 시점도 **응답이 준 값**에서 온다(「없음」도 답이다).
  await expect(page.getByTestId('worksheet-checkpoint')).toContainText(
    CHECKPOINT_LABEL[recordedCase.nextCheckpointKind] ?? recordedCase.nextCheckpointKind,
  );
  await expect(page.getByTestId('write-failure')).toHaveCount(0);

  // 타임라인 — 그 사건이 남는다. **API 가 준 개수·마지막 상태와 같다.**
  const entries = (await (await timelineAfterRecord).json()) as { readonly toState: string }[];
  await expect(page.getByTestId('timeline').getByRole('listitem')).toHaveCount(entries.length);
  const last = entries[entries.length - 1]!;
  await expect(page.getByTestId('timeline')).toContainText(CASE_STATE_LABEL[last.toState] ?? last.toState);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

/**
 * CR H-2 — 쓰기 실패가 화면에 **얼굴로** 나오는지 실제 서버 응답으로 잰다. 목을 쓰지 않는다:
 * 브라우저의 세션 쿠키를 지우면 출하 인증 경계가 그 쓰기를 401 로 거부한다(계약 선언대로).
 */
test('쓰기가 401 로 거부되면 로그인으로 가는 길이 뜬다', async ({ page }) => {
  await signIn(page);

  const rows = await search(page, 'SYN-7E1-FLOOR-ABSENT');
  const target = rows.find((row) => row.noticeNumber === 'SYN-7E1-FLOOR-ABSENT')!;
  const card = page.getByTestId(`notice-card-${target.noticeNumber}`);
  await expect(card).toBeVisible();
  const addButton = card.getByRole('button', { name: /담기/u });
  await expect(addButton).toBeVisible();

  // 세션 쿠키를 지운다 — 서버 세션은 그대로이고 **브라우저가 자격을 잃는다**.
  await page.context().clearCookies();

  const rejected = page.waitForResponse(
    (one) => one.url().endsWith('/api/bid-cases') && one.request().method() === 'POST',
  );
  await addButton.click();
  expect((await rejected).status()).toBe(401);

  /**
   * 단언은 **결과**다 — 「로그인으로 가는 길이 뜬다」. 다만 **어느 얼굴이 그것을 내는지 둘 가운데
   * 하나임을 명시한다**(CR r3 L-6): 쓰기 401 뒤 `onError` 가 서버 상태를 다시 읽고 **그 읽기도
   * 401** 이므로 화면이 로그인 얼굴로 바뀐다(CR r2 L-6 의 읽기 갈래). 그래서 이 자리에 설 수 있는
   * 얼굴은 쓰기 실패 얼굴과 로그아웃 얼굴 둘이다. 앞 판은 링크만 봐서 **쓰기 얼굴이 아예 사라져도**
   * 초록이었다 — 목 없는 유일한 층의 단언이 한 단 약해진 자리였다.
   */
  const face = page.getByTestId('write-failure').or(page.getByTestId('signed-out'));
  await expect(face).toHaveCount(1);
  await expect(face.getByRole('link', { name: /로그인/u })).toHaveAttribute('href', '/login');
  await expect(page.getByTestId('toast')).toHaveCount(0);
});

/**
 * 여정 ④ 가 쓰는 건을 **「제출함」까지** 민다 — 여정 ③ 이 그 각 단계를 자세히 재므로 여기서는
 * 같은 클릭만 하고 단언은 결과 상태 하나다. `null` 은 「이미 제출 뒤라 밀 것이 없다」다.
 *
 * 여정 ③ 과 **다른 공고**를 쓰는 이유는 fixture 의 `why_third_notice` 에 있다 — 한 하네스를
 * 공유하므로 건의 상태가 전역이고, 같은 건을 쓰면 두 여정이 서로의 전이를 본다.
 */
async function driveToRecorded(page: Page, noticeNumber: string): Promise<BidCaseRow | null> {
  const rows = await search(page, noticeNumber);
  const target = rows.find((row) => row.noticeNumber === noticeNumber)!;
  const card = page.getByTestId(`notice-card-${target.noticeNumber}`);
  await ensureInBidBox(page, card);

  const cases = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  await card.getByTestId('notice-title').click();
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  const caseRows = (await (await cases).json()) as BidCaseRow[];
  const opened = caseRows.find((row) => row.noticeNumber === noticeNumber)!;

  // 이미 제출 뒤면(같은 DB 에 두 번 미는 지역 실행) 밀지 않는다 — CI 는 버릴 DB 라 늘 여기서
  // `WATCHING` 이고 아래 세 단계를 탄다.
  if (opened.state !== 'WATCHING') {
    return null;
  }

  await page.getByTestId('active-bids').fill('1');
  const priced = page.waitForResponse(
    (one) => one.url().includes('/api/bid-price-requests') && one.request().method() === 'POST',
  );
  await page.getByTestId('worksheet-primary').click();
  expect((await priced).status()).toBe(200);

  await page.getByTestId('record-amount').fill('1350000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  const drafted = page.waitForResponse((one) => one.url().includes('/draft') && one.request().method() === 'POST');
  await page.getByTestId('worksheet-primary').click();
  expect((await drafted).status()).toBe(200);

  await page.getByTestId('record-amount').fill('1350000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  const recorded = page.waitForResponse((one) => one.url().includes('/record') && one.request().method() === 'POST');
  await page.getByTestId('worksheet-primary').click();
  const response = await recorded;
  expect(response.status()).toBe(200);
  return (await response.json()) as BidCaseRow;
}

/** `datetime-local` 이 받는 꼴(`YYYY-MM-DDTHH:mm`, **지역 시각**) — 시각을 조작하지 않고 지금에서 센다. */
function localDateTimeInput(daysFromNow: number): string {
  const when = new Date(Date.now() + daysFromNow * 86_400_000);
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${when.getFullYear()}-${pad(when.getMonth() + 1)}-${pad(when.getDate())}T${pad(when.getHours())}:${pad(
    when.getMinutes(),
  )}`;
}

/** 카드가 보이는 세 축이 **그 응답의 값**과 같은지 — 상태·다음 확인 시점·지난 일정. */
async function assertCardMatchesCase(page: Page, row: BidCaseRow): Promise<void> {
  const card = page.getByTestId(`bid-case-${row.caseId}`);
  await expect(card).toBeVisible();
  await expect(card.getByTestId(`state-${row.caseId}`)).toHaveText(CASE_STATE_LABEL[row.state] ?? row.state);
  await expect(card.getByTestId(`checkpoint-kind-${row.caseId}`)).toHaveText(
    CHECKPOINT_LABEL[row.nextCheckpointKind] ?? row.nextCheckpointKind,
  );
  // 「없음」은 빈 값이 아니라 답이다 — 시각이 null 이면 그 자리에 숫자가 없다.
  const at = card.getByTestId(`checkpoint-at-${row.caseId}`);
  if (row.nextCheckpointAt === null) {
    await expect(at).toHaveText(ABSENT);
  } else {
    expect(await at.innerText()).toMatch(/\d/u);
  }
  // 경고는 **서버의 판정 하나**를 따른다 — 있으면 1, 없으면 0 이다(화면이 시각을 비교하지 않는다).
  await expect(card.getByTestId(`overdue-${row.caseId}`)).toHaveCount(row.overdue ? 1 : 0);
}

test('여정 ④ — 내 입찰함: 상태 탭 · 다음 확인 시점 · 개찰 대기 전이', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const recorded = await driveToRecorded(page, 'SYN-7E2-BIDBOX');

  // 셸의 전역 탐색으로 간다 — 주소를 손으로 적지 않는다(셸이 자리 표에서 링크를 만든다).
  const listed = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: 'D 내 입찰함' }).click();
  await expect(page.getByTestId('screen-bid-box')).toBeVisible();
  const rows = (await (await listed).json()) as BidCaseRow[];
  const mine = rows.find((row) => row.noticeNumber === 'SYN-7E2-BIDBOX')!;

  // 질의는 **실투찰 종류**다 — 페이퍼와 한 목록에 섞이지 않는다(계약의 모양).
  const listUrl = new URL((await listed).url());
  expect(listUrl.searchParams.get('kind')).toBe('REAL');

  if (recorded !== null) {
    expect(mine.state, '제출 직후의 상태는 응답이 말한다').toBe(recorded.state);
  }

  // 그 건이 **자기 상태의 탭**에 있다 — 탭을 고르면 카드가 뜬다.
  await page.getByRole('tab', { name: new RegExp(CASE_TAB_LABEL[mine.state]!, 'u') }).click();
  await assertCardMatchesCase(page, mine);

  // 다른 탭에서는 보이지 않는다 — 탭이 실제로 가린다(그러지 않으면 탭 표가 아무 일도 안 한다).
  const otherTab = ['관심', '종료'].find((label) => label !== CASE_TAB_LABEL[mine.state])!;
  await page.getByRole('tab', { name: new RegExp(otherTab, 'u') }).click();
  await expect(page.getByTestId(`bid-case-${mine.caseId}`)).toHaveCount(0);
  await page.getByRole('tab', { name: new RegExp(CASE_TAB_LABEL[mine.state]!, 'u') }).click();

  // 개찰 대기 전이 — 개찰 일시는 **운영자 입력**이고 지금에서 센다(시각 조작 0).
  //
  // 단언과 클릭을 **내 카드 안으로 좁힌다**: 한 하네스를 공유하므로 여정 ③ 의 건도 같은 탭에
  // 「투찰 기록됨」으로 앉아 있고, 화면 전체를 가리키면 locator 가 둘을 집어 **남의 건을 밀 수**
  // 있었다(첫 실행 실측: strict mode violation — 건 1·2 가 함께 걸렸다).
  if (mine.state === 'BID_RECORDED') {
    const card = page.getByTestId(`bid-case-${mine.caseId}`);
    await card.getByRole('button', { name: '개찰 대기로' }).click();
    await card.getByLabel('개찰 일시').fill(localDateTimeInput(3));
    const sent = page.waitForResponse(
      (one) => one.url().includes('/await-opening') && one.request().method() === 'POST',
    );
    await card.getByRole('button', { name: '보내기' }).click();
    const response = await sent;
    expect(response.status()).toBe(200);
    const moved = (await response.json()) as BidCaseRow;

    expect(moved.state).toBe('AWAITING_OPENING');
    expect(moved.nextCheckpointKind, '그 시각이 다음 확인 시점이 된다(계약)').toBe('OPENING');
    /**
     * `OPEN-7E-GATE-RESIDUAL` ⑤ — 「이미 닿았다」 가드의 전제는 **쓰기가 버전을 올린다**는 것이다.
     * 화면이 쓰는 전이에 한해 그 전제를 **실측으로** 잠근다(계약 전체의 단언은 아니다 — 그 잔여는
     * 알려진 제한에 남는다).
     */
    expect(moved.caseVersion).toBeGreaterThan(mine.caseVersion);

    // 화면이 보이는 것은 **그 응답의 값**이다 — 전이 뒤 그 건의 탭으로 따라간다.
    await assertCardMatchesCase(page, moved);
    await expect(page.getByRole('tab', { name: new RegExp(CASE_TAB_LABEL[moved.state]!, 'u') })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByTestId('toast')).toContainText('보냈습니다');
  }

  await expect(page.getByTestId('write-failure')).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

/** D-7E-6 — 화면·콘솔에 사업자번호·토큰·세션 쿠키 평문이 0 이다. */
test('개인정보·비밀값이 화면과 콘솔에 나오지 않는다', async ({ page }) => {
  const consoleLines: string[] = [];
  page.on('console', (message) => consoleLines.push(message.text()));
  await signIn(page);
  await search(page, 'SYN-7E1');
  await expect(page.getByTestId('notice-total')).toBeVisible();

  const body = await page.locator('body').innerText();
  // 세션 쿠키는 HttpOnly 라 스크립트가 읽지 못한다 — 그 사실을 화면 쪽에서 확인한다.
  expect(await page.evaluate(() => document.cookie)).not.toContain('bidvector_session');
  expect(body).not.toContain(journeyContext().passphrase);
  expect(consoleLines.join('\n')).not.toContain(journeyContext().passphrase);
  // 사업자등록번호 형태(3-2-5)가 화면에 없다.
  expect(body).not.toMatch(/\b\d{3}-\d{2}-\d{5}\b/u);
});
