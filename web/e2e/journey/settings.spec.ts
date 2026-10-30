import { expect, test, type Page } from '@playwright/test';

import {
  assertOnlyAllowedOperations,
  journeyContext,
  signIn,
  watchRequests,
  type NoticeRow,
} from './shared';

/**
 * 설정 여정(M7/7E-2, D-7E2-2) — **감시 조건을 화면으로 세우면 제안 목록이 `MATCHED` 가 된다.**
 * 7E-1 이 D-7E-9 로 7E-2 에 넘긴 자리다: 그때는 감시 조건이 없어 제안 목록의 빈 얼굴
 * (`WATCH_NOT_CONFIGURED`)까지만 쟀다.
 *
 * **이 여정은 끝에서 감시 조건을 다시 비운다.** 제안 상태는 전역 전략에서 나오고(서버의
 * `watchRules.isEmpty()`) 설정이 `workers: 1` 이라 한 하네스에서 여정들이 차례로 돈다 — 조건을
 * 남겨 두면 여정 ② 의 빈 얼굴 단언이 **실행 순서에 따라** 깨진다. 되돌림 자체도 단언이다:
 * 「조건을 지우면 그 빈 얼굴로 돌아온다」.
 *
 * 전략 쓰기는 화면이 **편집 세션으로만** 한다(계약에 직접 쓰는 operation 이 없다).
 */

/** 감시 조건으로 쓸 어휘 — fixture 세 공고의 공고명에 모두 들어 있다(그래서 통과가 1 건 이상이다). */
const WATCH_TERM = '합성';

const KEYWORD_FIELD = 'REQUIRED_KEYWORD';

interface SuggestionsRead {
  readonly resultState: string;
  readonly rows: readonly NoticeRow[];
  readonly total: string | null;
}

/** 설정 화면으로 — 셸의 전역 탐색을 지난다(주소를 손으로 적지 않는다). */
async function openSettings(page: Page): Promise<number> {
  const read = page.waitForResponse(
    (one) => one.url().endsWith('/api/strategy') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: '설정' }).click();
  await expect(page.getByTestId('screen-settings')).toBeVisible();
  const body = (await (await read).json()) as { readonly revision: number };
  return body.revision;
}

/**
 * 필수 키워드를 `raw` 로 바꾼다 — 고치기 → 값 → 적용. 적용 응답을 돌려준다(개정이 올랐는지는
 * 부르는 쪽이 본다).
 *
 * 빈 문자열을 주면 **그 조건을 지운다**(계약이 빈 배열을 받는다 — 이 여정이 그것을 실측한다).
 */
async function setRequiredKeyword(page: Page, raw: string): Promise<{ readonly state: string; readonly revision: number | null }> {
  const row = page.getByTestId(`strategy-row-${KEYWORD_FIELD}`);
  await row.getByRole('button', { name: '고치기' }).click();
  const panel = page.getByTestId('edit-session');
  await expect(panel).toBeVisible();
  // 세션 상태는 **서버가 준 값**이다 — 화면이 단계를 지어내지 않는다.
  await expect(panel.getByTestId('edit-session-state')).toContainText('값을 기다린다');

  await panel.getByRole('textbox').fill(raw);
  const valued = page.waitForResponse((one) => one.url().includes('/value') && one.request().method() === 'POST');
  await panel.getByRole('button', { name: '값 보내기' }).click();
  expect((await valued).status()).toBe(200);
  await expect(panel.getByTestId('edit-session-state')).toContainText('확인을 기다린다');

  // 확인 화면이 보이는 것은 **보낸 값**이다(세션 응답에 초안이 없다 — 계약).
  // 비워 보낸 것은 **「비움」이라고 말한다** — 빈 칸으로 그리면 「아직 안 보냄」과 같아 보인다.
  await expect(panel.getByTestId('edit-sent-value')).toHaveText(
    raw === '' ? '비움 — 이 조건을 지웁니다' : raw,
  );

  const confirmed = page.waitForResponse((one) => one.url().includes('/confirm') && one.request().method() === 'POST');
  await panel.getByRole('button', { name: '적용' }).click();
  const response = await confirmed;
  expect(response.status()).toBe(200);
  const body = (await response.json()) as { readonly state: string; readonly strategyRevision: number | null };
  // 적용되면 세션 자리가 닫힌다(종단 상태다).
  await expect(page.getByTestId('edit-session')).toHaveCount(0);
  return { state: body.state, revision: body.strategyRevision };
}

/** 오늘의 공고로 가서 제안 목록 응답을 받는다 — 상태·총수는 **헤더**가 나른다. */
async function readSuggestions(page: Page): Promise<SuggestionsRead> {
  const read = page.waitForResponse(
    (one) => one.url().endsWith('/api/suggestions') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: 'B 오늘의 공고' }).click();
  await expect(page.getByTestId('screen-home')).toBeVisible();
  const response = await read;
  return {
    resultState: response.headers()['x-result-state'] ?? '',
    rows: (await response.json()) as NoticeRow[],
    total: response.headers()['x-total-count'] ?? null,
  };
}

test('설정 여정 — 감시 조건을 세우면 제안 목록이 MATCHED 가 되고, 지우면 빈 얼굴로 돌아온다', async ({
  page,
}) => {
  const watched = watchRequests(page);
  await signIn(page);

  // ① 조건이 없는 자리에서 출발한다 — 그 빈 얼굴은 **API 상태 그대로**다(7E-1 D-7E-9).
  const before = await readSuggestions(page);
  expect(before.resultState).toBe('WATCH_NOT_CONFIGURED');
  await expect(page.getByTestId('suggestions-empty')).toContainText('감시 조건이 설정되지 않았습니다');

  // ② 설정 화면에서 필수 키워드를 세운다(편집 세션 셋을 지난다).
  const revisionBefore = await openSettings(page);
  const applied = await setRequiredKeyword(page, WATCH_TERM);
  expect(applied.state).toBe('APPLIED');
  expect(applied.revision, '적용되면 전략 개정이 오른다(계약)').toBeGreaterThan(revisionBefore);

  // 화면의 지금 값이 **다시 읽은 전략**에서 온다.
  await expect(page.getByTestId(`strategy-value-${KEYWORD_FIELD}`)).toHaveText(WATCH_TERM);

  // ③ 제안 목록이 MATCHED 다 — 카드가 **그 응답의 행**과 같다.
  const matched = await readSuggestions(page);
  expect(matched.resultState).toBe('MATCHED');
  expect(matched.rows.length).toBeGreaterThan(0);
  expect(Number(matched.total)).toBe(matched.rows.length);
  await expect(page.getByTestId('suggestions-empty')).toHaveCount(0);
  for (const row of matched.rows) {
    const card = page.getByTestId(`suggestion-card-${row.noticeNumber}`);
    await expect(card).toBeVisible();
    // 제안 카드의 제목은 **워크시트로 가는 링크**다(`notice-title` 를 쓰는 검색 카드와 다른
    // 컴포넌트다 — 첫 실행이 그 차이로 붉었다). 접근 가능한 이름을 응답의 값과 대조한다.
    await expect(card.getByRole('link')).toHaveText(row.title ?? row.noticeNumber);
  }

  // ④ 조건을 **다시 비운다** — 빈 얼굴로 돌아오는 것이 이 여정의 마지막 단언이고, 그래서 이
  //    여정은 다른 여정의 순서를 바꾸지 않는다.
  const revisionAfterSet = await openSettings(page);
  const cleared = await setRequiredKeyword(page, '');
  expect(cleared.state).toBe('APPLIED');
  expect(cleared.revision, '지우는 것도 쓰기다 — 개정이 또 오른다').toBeGreaterThan(revisionAfterSet);
  await expect(page.getByTestId(`strategy-value-${KEYWORD_FIELD}`)).toHaveText('값 없음');

  const after = await readSuggestions(page);
  expect(after.resultState, '조건을 지우면 그 빈 얼굴로 돌아온다').toBe('WATCH_NOT_CONFIGURED');
  await expect(page.getByTestId('suggestions-empty')).toContainText('감시 조건이 설정되지 않았습니다');

  await expect(page.getByTestId('write-failure')).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

/** D-7E2-7 — 사업자번호는 **입력칸이 없다**. 화면·콘솔에 그 꼴의 값이 0 인 것도 함께 센다. */
test('설정에 사업자번호 입력칸이 없고 화면·콘솔에 그 꼴이 없다', async ({ page }) => {
  const consoleLines: string[] = [];
  page.on('console', (message) => consoleLines.push(message.text()));
  await signIn(page);
  await openSettings(page);

  const notReady = page.getByTestId('settings-not-ready');
  await expect(notReady).toContainText('사업자번호');
  await expect(notReady.getByRole('textbox')).toHaveCount(0);

  const body = await page.locator('body').innerText();
  expect(body).not.toMatch(/\b\d{3}-\d{2}-\d{5}\b/u);
  expect(body).not.toContain(journeyContext().passphrase);
  expect(consoleLines.join('\n')).not.toContain(journeyContext().passphrase);
});
