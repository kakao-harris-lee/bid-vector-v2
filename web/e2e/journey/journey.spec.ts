import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

import { expect, test, type Page, type Request } from '@playwright/test';

/**
 * 여정 ②③ 의 e2e(D-7E-3) — **출하 app.jar · 버릴 Postgres · loopback fake KONEPS** 위에서
 * 돈다(API 목 0). 공고는 수집 경로로 들어왔고 화면은 V2 API 만 부른다.
 *
 * 단언의 규율: **DOM 에서 읽은 값을 같은 실행의 API 응답과 대조한다.** 기대값을 spec 에 적지
 * 않는다 — 적으면 「화면이 보이는 것」과 「서버가 준 것」이 갈려도 test 가 초록일 수 있다.
 */
/**
 * 하네스가 적어 준 실행 맥락 — **Playwright 산출물 밖**이다(실패 시 아티팩트로 올라가는
 * 자리에 일회용 자격 값을 두지 않는다, D-7E-6).
 */
const context = JSON.parse(
  readFileSync(resolve(tmpdir(), 'bidvector-7e1-journey-context.json'), 'utf8'),
) as { readonly previewUrl: string; readonly loginName: string; readonly passphrase: string };

/** 같은 출처의 기준 — 하네스가 적어 준 preview 주소다(`page.url()` 은 첫 요청 때 `about:blank` 다). */
const SAME_ORIGIN = new URL(context.previewUrl).origin;

interface NoticeRow {
  readonly noticeNumber: string;
  readonly noticeRound: string;
  readonly title: string | null;
  readonly demandAgencyName: string | null;
  readonly baseAmountWon: number | null;
  readonly baseAmountBasis: string | null;
  readonly baseAmountProvenance: string | null;
}

interface PriceBody {
  readonly bidPriceMenuState: string;
  readonly floorRateGroundsState: string;
  readonly publishedFloorRateFraction: string | null;
  readonly licenseVerdict: string;
  readonly licenseValidity: string;
  readonly unjudgedAxes: readonly string[];
  readonly disclosures: readonly string[];
}

/** 화면이 **V2 API 밖**을 부르지 않는지 실행 중에 센다(ADR 0011 D-2 의 런타임 대조). */
function watchRequests(page: Page): { readonly outside: string[] } {
  const outside: string[] = [];
  page.on('request', (request: Request) => {
    // 문서·정적 자산은 preview 가 내므로 같은 출처다. 그 밖의 출처는 전부 적는다 —
    // legacy 백엔드·CDN·목 서버가 섞이면 이 목록이 빈 배열이 아니게 된다.
    if (new URL(request.url()).origin !== SAME_ORIGIN) {
      outside.push(request.url());
    }
  });
  return { outside };
}

async function signIn(page: Page): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name="loginName"]', context.loginName);
  await page.fill('input[name="passphrase"]', context.passphrase);
  await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/session') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '로그인' }).click(),
  ]);
  await expect(page.getByTestId('screen-home')).toBeVisible();
}

test.describe.configure({ mode: 'serial' });

test('여정 ② — 로그인 → 검색 → 카드 → 담기', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  // 검색 — 화면이 부른 응답을 **그 실행에서** 받아 둔다.
  const search = page.waitForResponse(
    (response) => response.url().includes('/api/notices') && response.status() === 200,
  );
  await page.fill('input[name="q"]', 'SYN-7E1');
  await page.getByRole('button', { name: '검색' }).click();
  const rows = (await (await search).json()) as NoticeRow[];
  expect(rows.length).toBeGreaterThan(0);

  // 수집 경로로 들어온 공고가 화면에 뜨고, **보이는 값이 응답의 값이다**.
  const first = rows[0]!;
  const card = page.getByTestId(`notice-card-${first.noticeNumber}`);
  await expect(card).toBeVisible();
  await expect(card.getByTestId('notice-title')).toHaveText(first.title ?? first.noticeNumber);
  await expect(card.getByTestId('notice-agency')).toHaveText(first.demandAgencyName ?? '');
  await expect(card.getByTestId('notice-amount')).toHaveText(
    `${(first.baseAmountWon ?? 0).toLocaleString('ko-KR')}원`,
  );
  await expect(card.getByTestId('notice-amount-provenance')).toContainText(first.baseAmountBasis ?? '');
  await expect(card.getByTestId('notice-amount-provenance')).toContainText(first.baseAmountProvenance ?? '');

  // D-7E-9 — 제안 목록은 감시 조건이 없으므로 **API 상태 그대로** 빈 얼굴이다.
  const suggestions = page.getByTestId('suggestions-empty');
  await expect(suggestions).toBeVisible();
  await expect(suggestions).toContainText('감시 조건이 설정되지 않았습니다');

  // 담기 — 201 을 받고 토스트가 뜬다. 되돌림 전이가 없으므로 실행 취소 버튼이 없다.
  const added = page.waitForResponse(
    (response) => response.url().endsWith('/api/bid-cases') && response.request().method() === 'POST',
  );
  await card.getByRole('button', { name: '담기' }).click();
  expect((await added).status()).toBe(201);
  const toast = page.getByTestId('toast');
  await expect(toast).toContainText('입찰함에 담았습니다');
  await expect(toast.getByRole('button', { name: '실행 취소' })).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
});

test('여정 ③ — 투찰가 요청 → 「아직 산출 안 함」과 근거 → 초안 → 제출함 → 타임라인', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const search = page.waitForResponse(
    (response) => response.url().includes('/api/notices') && response.status() === 200,
  );
  await page.fill('input[name="q"]', 'SYN-7E1-FLOOR-USABLE');
  await page.getByRole('button', { name: '검색' }).click();
  const rows = (await (await search).json()) as NoticeRow[];
  const target = rows.find((row) => row.noticeNumber === 'SYN-7E1-FLOOR-USABLE')!;

  // **이 여정은 앞 여정의 담기에 기대지 않는다.** 하네스 한 벌을 두 여정이 나눠 쓰므로 앞
  // 여정이 어느 공고를 담았는지에 기대면(검색 정렬이 바뀌는 날) 사유가 흐려진 실패가 된다.
  // 담기는 멱등이다 — 이미 담긴 건이면 계약이 200 으로 같은 건을 돌려주고, 그때는 카드에
  // 버튼 대신 「담긴 건」이 뜨므로 누를 것이 없다.
  const card = page.getByTestId(`notice-card-${target.noticeNumber}`);
  const addButton = card.getByRole('button', { name: '담기' });
  if (await addButton.isVisible()) {
    const added = page.waitForResponse(
      (response) => response.url().endsWith('/api/bid-cases') && response.request().method() === 'POST',
    );
    await addButton.click();
    expect([200, 201]).toContain((await added).status());
  }

  await card.getByTestId('notice-title').click();
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  await expect(page.getByTestId('worksheet-title')).toHaveText(target.title ?? target.noticeNumber);

  // 투찰가 요청 — primary 버튼 하나가 그 상태의 다음 전이다.
  const priced = page.waitForResponse(
    (response) => response.url().includes('/api/bid-price-requests') && response.request().method() === 'POST',
  );
  await expect(page.getByTestId('worksheet-primary')).toHaveText('투찰가 요청');
  await page.getByTestId('worksheet-primary').click();
  const priceResponse = await priced;
  expect(priceResponse.status()).toBe(200);
  const price = (await priceResponse.json()) as PriceBody;

  // 「아직 산출 안 함」 — 상태는 응답에서 오고 **그 자리에 숫자가 없다**.
  expect(price.bidPriceMenuState).toBe('NOT_COMPUTED');
  await expect(page.getByTestId('bid-price-menu')).toContainText('아직 산출 안 함');
  expect(await page.getByTestId('bid-price-menu-value').innerText()).not.toMatch(/\d/u);

  // 게시 하한율 — fixture 가 게시값을 가진 공고이므로 근거로 서고 **원문 그대로** 보인다.
  expect(price.floorRateGroundsState).toBe('USABLE');
  await expect(page.getByTestId('floor-rate-grounds')).toContainText('게시 하한율');
  await expect(page.getByTestId('floor-rate-grounds')).toContainText(price.publishedFloorRateFraction ?? '');

  // 자격 — 판정 불가·유효기간 미검증·아직 판정하지 않는 축 셋.
  expect(price.licenseVerdict).toBe('UNCERTAIN');
  await expect(page.getByTestId('license-verdict')).toContainText('면허 판정 불가');
  expect(price.licenseValidity).toBe('NOT_VERIFIED');
  await expect(page.getByTestId('license-validity')).toContainText('유효기간 미검증');
  for (const axis of price.unjudgedAxes) {
    await expect(page.getByTestId('unjudged-axes')).toContainText(
      axis === 'QUAL_08_REGION' ? '지역' : axis === 'QUAL_10_PERFORMANCE' ? '수행실적' : '시공능력평가금액',
    );
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
  const drafted = page.waitForResponse(
    (response) => response.url().includes('/draft') && response.request().method() === 'POST',
  );
  await expect(page.getByTestId('worksheet-primary')).toHaveText('투찰 예정 초안');
  await page.getByTestId('worksheet-primary').click();
  expect((await drafted).status()).toBe(200);

  // 「제출함」 — 같은 입력으로 기록하고 상태가 투찰 기록됨이 된다.
  await page.getByTestId('record-amount').fill('1050000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  await page.getByTestId('record-draw-numbers').fill('3, 7');
  const recorded = page.waitForResponse(
    (response) => response.url().includes('/record') && response.request().method() === 'POST',
  );
  // 기록 뒤의 타임라인 재조회를 **누르기 전에** 기다려 둔다 — 무효화가 응답보다 먼저 올 수
  // 있어, 누른 뒤에 걸면 그 재조회를 놓친다(실측: 그 자리에서 timeout).
  const timelineAfterRecord = page.waitForResponse(
    (response) => response.url().includes('/timeline') && response.status() === 200,
  );
  await expect(page.getByTestId('worksheet-primary')).toHaveText('제출함');
  await page.getByTestId('worksheet-primary').click();
  expect((await recorded).status()).toBe(200);
  await expect(page.getByTestId('worksheet-case-state')).toContainText('투찰 기록됨');

  // 타임라인 — 그 사건이 남는다. **API 가 준 개수와 같다.**
  const entries = (await (await timelineAfterRecord).json()) as { readonly toState: string }[];
  await expect(page.getByTestId('timeline').getByRole('listitem')).toHaveCount(entries.length);
  await expect(page.getByTestId('timeline')).toContainText('투찰 기록됨');

  // 기록 뒤 다음 확인 시점은 **「없음」이라는 답**이다(빈 값이 아니다).
  await expect(page.getByTestId('worksheet-checkpoint')).toContainText('없음');

  expect(watched.outside).toStrictEqual([]);
});

/** D-7E-6 — 화면·콘솔에 사업자번호·토큰·세션 쿠키 평문이 0 이다. */
test('개인정보·비밀값이 화면과 콘솔에 나오지 않는다', async ({ page }) => {
  const consoleLines: string[] = [];
  page.on('console', (message) => consoleLines.push(message.text()));
  await signIn(page);
  await page.fill('input[name="q"]', 'SYN-7E1');
  await page.getByRole('button', { name: '검색' }).click();
  await expect(page.getByTestId('notice-total')).toBeVisible();

  const body = await page.locator('body').innerText();
  // 세션 쿠키는 HttpOnly 라 스크립트가 읽지 못한다 — 그 사실을 화면 쪽에서 확인한다.
  expect(await page.evaluate(() => document.cookie)).not.toContain('bidvector_session');
  expect(body).not.toContain(context.passphrase);
  expect(consoleLines.join('\n')).not.toContain(context.passphrase);
  // 사업자등록번호 형태(3-2-5)가 화면에 없다.
  expect(body).not.toMatch(/\b\d{3}-\d{2}-\d{5}\b/u);
});
