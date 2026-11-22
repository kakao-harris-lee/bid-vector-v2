import { expect, test, type Page } from '@playwright/test';

import {
  assertNoPrivateValues,
  OWN_BUSINESS_REGISTRATION_NUMBER,
  PLANTED_BUSINESS_REGISTRATION_NUMBER,
  PLANTED_BUSINESS_REGISTRATION_NUMBER_TYPED,
  PRIVATE_VALUE_PROBLEM_PLANTED,
  PRIVATE_VALUE_PROBLEM_SHAPE,
  privateValueProblems,
  SCANNED_ATTRIBUTES,
} from './privateValues';
import { teardownMessage, teardownStep } from './teardown';
import { nameSourcesInPage } from './touchMeasure';

import {
  ABSENT,
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

/**
 * 설정 화면으로 — 셸의 전역 탐색을 지난다(주소를 손으로 적지 않는다).
 *
 * **이름을 정확히 맞춘다**(M13/13E). Playwright 의 `name` 은 기본이 부분 일치라, 화면 안에
 * 「설정에서 …」으로 가는 걸음이 생기면 이 조회가 둘을 집어 strict 위반으로 멈춘다(실측). 셸의
 * 자리 라벨은 정확히 「설정」이므로 그 축으로 가른다.
 */
async function openSettings(page: Page): Promise<number> {
  const read = page.waitForResponse(
    (one) => one.url().endsWith('/api/strategy') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: '설정', exact: true }).click();
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
  /**
   * **홈에 서 있으면 먼저 떠난다**(CI W-13 실패의 자리, 2026-11-20).
   *
   * 같은 자리로 가는 탐색 클릭은 **재마운트를 만들지 않고**, 제안 질의는 이미 캐시에 있어
   * 아무 요청도 나가지 않는다 — 그러면 아래 포착이 120초를 기다리다 멈춘다. 로그인 직후가 바로
   * 그 자리다(`signIn` 이 홈에서 끝나고, 홈은 마운트에서 제안을 읽는다).
   *
   * **이 구조는 13E 이전부터 있었다**(base 의 홈도 `enabled` 없이 마운트에서 읽었다 — 실측).
   * 달라진 것은 **도착 차례**다: 앞 판의 홈은 공고 목록까지 함께 읽어 제안 응답이 늦게 왔고,
   * 그래서 포착이 먼저 서는 쪽으로 **운이 좋았다**. 13E 가 그 읽기를 없애자 응답이 빨라져 순서가
   * 뒤집혔다.
   *
   * 떠났다가 돌아오면 홈이 **다시 마운트**되고 기본 staleTime 이 0 이라 그 마운트가 새 읽기를
   * 낸다 — 도착 차례에 기대지 않는다. 주소를 손으로 적지 않는 규율은 그대로다(셸 탐색을 지난다).
   */
  if (await page.getByTestId('screen-home').isVisible()) {
    await page.getByRole('link', { name: '설정', exact: true }).click();
    await expect(page.getByTestId('screen-settings')).toBeVisible();
  }
  const read = page.waitForResponse(
    (one) => one.url().endsWith('/api/suggestions') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: '오늘의 공고', exact: true }).click();
  await expect(page.getByTestId('screen-home')).toBeVisible();
  const response = await read;
  return {
    resultState: response.headers()['x-result-state'] ?? '',
    rows: (await response.json()) as NoticeRow[],
    total: response.headers()['x-total-count'] ?? null,
  };
}

/**
 * **전역 전략을 원래 자리로 돌린다 — 성공 경로가 아니라 teardown 에서**(CR r1 M-4).
 *
 * 왜 test 본문의 ④ 단계로는 부족한가: ② 뒤 어느 단언이 깨지면 `REQUIRED_KEYWORD` 가 서버에
 * **남고**, `journey.spec.ts` 여정 ② 의 `WATCH_NOT_CONFIGURED` 단언이 **같은 DB 를 다시 쓰는
 * 실행**에서 깨진다 — 그리고 그 실패가 **다른 여정에** 나타난다(진단이 가장 비싼 꼴). 계약
 * D-7E2-9 의 「여정 순서 독립」은 성공 경로에서만 성립했다.
 *
 * 화면이 아니라 **출하 API 로** 되돌린다: 실패 뒤 페이지가 어디에 있는지 알 수 없고, 되돌림은
 * 화면의 능력이 아니라 **운영자 설정 행위**다(하네스가 전략을 심는 D-7E-8 과 같은 자리). 브라우저
 * 세션 쿠키를 그대로 쓰므로 자격 값을 spec 이 들고 있지 않아도 된다.
 *
 * **멱등하다**: 이미 비어 있으면 아무것도 쓰지 않는다(쓸데없는 개정 증가를 만들지 않는다).
 * teardown 이 suite 를 깨뜨리지 않도록 실패는 삼키고 **끝 상태만** 확인한다.
 */
async function clearWatchCondition(page: Page): Promise<string | null> {
  const read = await page.request.get('/api/strategy');
  if (!read.ok()) {
    // **「되돌릴 수 없었다」와 「남겼다」는 다른 진단이다**(CR r2 L2R-9): 세션이 끊긴 뒤라면
    // 전역 상태를 볼 수조차 없다. 그 사실을 그대로 올린다.
    return `전략을 읽지 못해 되돌릴 수 없었다(GET /api/strategy ${read.status()})`;
  }
  const strategy = (await read.json()) as {
    readonly revision: number;
    readonly requiredKeywordTerms: readonly string[];
  };
  if (strategy.requiredKeywordTerms.length === 0) {
    return null;
  }
  const session = (await (await page.request.get('/api/session')).json()) as { readonly csrf: string };
  const headers = { 'X-Operator-Csrf': session.csrf };
  const begin = await page.request.post('/api/strategy/edit-sessions', {
    headers,
    data: { field: KEYWORD_FIELD },
  });
  if (!begin.ok()) {
    // **열린 세션이 있으면 그것을 취소하고 다시 연다**(앞 판은 `sessionId` 가 `undefined` 가 되어
    // `/edit-sessions/undefined/…` 로 조용히 실패했다). 세션 id 를 모르므로 남은 세션은 서버의
    // `OPEN-6A2B-ABANDONED-SESSIONS` 소관이고, 여기서는 **그 사실을 사유로 올린다**.
    return `편집 세션을 열지 못해 되돌릴 수 없었다(POST edit-sessions ${begin.status()})`;
  }
  const { sessionId } = (await begin.json()) as { readonly sessionId?: string };
  if (sessionId === undefined) {
    return '편집 세션 응답에 sessionId 가 없어 되돌릴 수 없었다';
  }
  const stamp = Date.now();
  await page.request.post(`/api/strategy/edit-sessions/${sessionId}/value`, {
    headers,
    data: { commandId: `teardown-value-${stamp}`, field: KEYWORD_FIELD, terms: [] },
  });
  const confirmed = await page.request.post(`/api/strategy/edit-sessions/${sessionId}/confirm`, {
    headers,
    data: { commandId: `teardown-confirm-${stamp}`, seenRevision: strategy.revision },
  });
  if (!confirmed.ok()) {
    // 적용이 안 됐으면 **세션을 닫아** 다음 실행의 `begin` 을 막지 않는다.
    await page.request.post(`/api/strategy/edit-sessions/${sessionId}/cancel`, {
      headers,
      data: { commandId: `teardown-cancel-${stamp}` },
    });
    return `되돌림 적용이 거부됐다(confirm ${confirmed.status()})`;
  }
  return null;
}

/** 사업 정보 절이 서버에 들고 있는 값 — teardown 이 **다시 쓸** 때 필요한 칸 전부다. */
interface StoredBusinessDetails {
  readonly tradeName: string | null;
  readonly businessRegistrationNumber: string | null;
  readonly headquartersRegion: string | null;
  readonly naraRegistration: string;
  readonly naraRegisteredFields: readonly string[];
  readonly businessCertificateExpiresOn: string | null;
}

/**
 * **사업자번호를 원래 자리로 돌린다 — 성공 경로가 아니라 teardown 에서**(15C r1 CR M-6).
 *
 * 위 [clearWatchCondition] 이 세운 규율과 같은 자리다: 이 파일의 한 test 가 사업자번호를
 * **서버에 실제로 쓰므로**(누출 술어의 모집단을 채우는 수단이다), 그 앞의 어느 단언이 깨지면
 * 심은 번호가 **공유 계정의 프로필에 남는다**. 수집 순서가 바뀌거나 프로필을 보는 spec 이
 * 뒤에 붙는 날 그 잔류가 **남의 여정을** 깨고 진단이 거기서 시작된다.
 *
 * 화면이 아니라 **출하 API 로** 되돌린다(실패 뒤 페이지가 어디에 있는지 알 수 없다).
 * **멱등하다**: 이미 비어 있으면 아무것도 쓰지 않는다.
 *
 * 사업 정보는 **절 단위 교체**라 번호만 지울 수 없다 — 읽어 온 현재 값을 그대로 싣고
 * 번호만 `null` 로 바꿔 다시 쓴다. 키를 빼는 것은 「그대로 두기」가 아니라 **지우기**다.
 */
async function clearBusinessRegistrationNumber(page: Page): Promise<string | null> {
  const read = await page.request.get('/api/profile');
  if (!read.ok()) {
    return `프로필을 읽지 못해 되돌릴 수 없었다(GET /api/profile ${read.status()})`;
  }
  const profile = (await read.json()) as StoredBusinessDetails;
  if (profile.businessRegistrationNumber === null) {
    return null;
  }
  const session = (await (await page.request.get('/api/session')).json()) as { readonly csrf: string };
  const base = {
    naraRegistration: profile.naraRegistration,
    tradeName: profile.tradeName,
    businessRegistrationNumber: null,
    headquartersRegion: profile.headquartersRegion,
  };
  const written = await page.request.post('/api/profile/business-details', {
    headers: { 'X-Operator-Csrf': session.csrf },
    data:
      profile.naraRegistration === 'REGISTERED'
        ? {
            ...base,
            naraRegisteredFields: [...profile.naraRegisteredFields],
            ...(profile.businessCertificateExpiresOn === null
              ? {}
              : { businessCertificateExpiresOn: profile.businessCertificateExpiresOn }),
          }
        : base,
  });
  if (!written.ok()) {
    return `되돌림 적용이 거부됐다(POST business-details ${written.status()})`;
  }
  return null;
}

test.afterEach(async ({ page }) => {
  // **되돌림을 먼저 다 돌리고 단언은 나중에 모아 한다.** `expect` 는 그 자리에서 던지므로,
  // 되돌림과 단언을 번갈아 두면 **앞 축의 단언이 깨질 때 뒤 축의 되돌림이 통째로 건너뛴다** —
  // 본문 ④ 를 teardown 으로 옮긴 바로 그 사유(CR r1 M-6)가 teardown 안에서 되살아난다.
  // 네 걸음 모두 [teardownStep] 을 지나므로 **예외도 그 순서를 깨지 못한다**.
  const cleared = await teardownStep('전역 감시 조건을 되돌리다', () => clearWatchCondition(page));
  const numberCleared = await teardownStep('사업자번호를 되돌리다', () =>
    clearBusinessRegistrationNumber(page),
  );
  const after = await teardownStep(
    '끝 상태의 전역 전략을 읽다',
    async () =>
      (await (await page.request.get('/api/strategy')).json()) as {
        readonly requiredKeywordTerms: readonly string[];
      },
  );
  const profileAfter = await teardownStep(
    '끝 상태의 프로필을 읽다',
    async () => (await (await page.request.get('/api/profile')).json()) as StoredBusinessDetails,
  );

  // **되돌릴 수 없었던 것**과 **남긴 것**을 가른다(둘은 다른 진단이다). 「되돌리다 던졌다」는
  // 앞쪽이고, 두 사유가 한 단언에 모이되 문면이 어느 쪽인지 말한다.
  //
  // 던진 경우의 **스택과 `cause` 사슬**은 [teardownMessage] 가 문면 아래에 붙인다(15I ⓒ) —
  // 머리말은 그대로 한 줄이다.
  expect(
    cleared.failure ?? cleared.value,
    teardownMessage('설정 여정의 전역 상태를 되돌릴 수 없었다', cleared),
  ).toBeNull();
  expect(
    numberCleared.failure ?? numberCleared.value,
    teardownMessage('설정 여정의 사업자번호를 되돌릴 수 없었다', numberCleared),
  ).toBeNull();
  // 끝 상태를 **읽지 못한 것**은 또 다른 진단이다 — 아래 두 단언이 `undefined` 를 보고 엉뚱한
  // 문면으로 붉지 않게 먼저 가른다.
  expect(after.failure, teardownMessage('끝 상태의 전역 전략을 읽지 못했다', after)).toBeNull();
  expect(
    profileAfter.failure,
    teardownMessage('끝 상태의 프로필을 읽지 못했다', profileAfter),
  ).toBeNull();
  // 이 단언이 깨지면 **이 여정이** 붉어진다 — 뒤 여정이 남의 잔류로 깨지는 것보다 낫다.
  // **끝 상태를 단언한다** — 「되돌리는 명령을 보냈다」가 아니라 「서버가 비어 있다」가 조건이다.
  expect(after.value?.requiredKeywordTerms, '설정 여정이 전역 감시 조건을 남겼다').toStrictEqual([]);
  expect(profileAfter.value?.businessRegistrationNumber, '설정 여정이 사업자번호를 남겼다').toBeNull();
});

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
  await expect(page.getByTestId(`strategy-value-${KEYWORD_FIELD}`)).toHaveText(ABSENT);

  const after = await readSuggestions(page);
  expect(after.resultState, '조건을 지우면 그 빈 얼굴로 돌아온다').toBe('WATCH_NOT_CONFIGURED');
  await expect(page.getByTestId('suggestions-empty')).toContainText('감시 조건이 설정되지 않았습니다');

  await expect(page.getByTestId('write-failure')).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

/**
 * **CR H-2 · v H-1 의 여정 쪽 단언.** 자리 구분 쉼표가 든 금액은 **보내지 않는다** — 앞 판은
 * `50,000,000` 을 `50` 으로 보내고 확인 화면엔 원문을 보였다(실 서버 실측). 그리고 받아들인
 * 입력에서는 **화면의 「보낸 값」이 본문의 값과 같다**.
 */
test('설정 — 쉼표 금액은 보내지 않고, 보낸 값은 본문의 값과 같다', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);
  await openSettings(page);

  const row = page.getByTestId('strategy-row-MAX_BUDGET');
  await row.getByRole('button', { name: '고치기' }).click();
  const panel = page.getByTestId('edit-session');
  await expect(panel).toBeVisible();

  // ① 쉼표 금액 — 거부 얼굴이 뜨고 value 요청이 **가지 않는다**.
  let valueCalls = 0;
  page.on('request', (request) => {
    if (request.url().includes('/value') && request.method() === 'POST') {
      valueCalls += 1;
    }
  });
  await panel.getByRole('textbox').fill('50,000,000');
  await panel.getByRole('button', { name: '값 보내기' }).click();
  await expect(panel.getByTestId('edit-value-missing')).toBeVisible();
  expect(valueCalls, '쉼표 금액으로 요청이 가면 안 된다').toBe(0);

  // ② 받는 모양 — 본문의 값과 화면의 「보낸 값」이 같다.
  const valued = page.waitForResponse((one) => one.url().includes('/value') && one.request().method() === 'POST');
  await panel.getByRole('textbox').fill('50000000');
  await panel.getByRole('button', { name: '값 보내기' }).click();
  // 단언은 **보낸 본문**에서 읽는다 — 화면이 보이는 문면과 같은 출처를 쓰면 대조가 공허해진다.
  const sent = (await valued).request().postDataJSON() as { readonly amountWon: number };
  expect(sent.amountWon).toBe(50_000_000);
  await expect(panel.getByTestId('edit-sent-value')).toHaveText(`${sent.amountWon.toLocaleString('ko-KR')}원`);

  // 적용하지 않고 그만둔다 — 이 여정은 전역 전략을 바꾸지 않는다.
  await panel.getByRole('button', { name: '그만두기' }).click();
  await expect(page.getByTestId('edit-session')).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

/**
 * 심는 값은 이 실행이 실제로 저장하는 **합성** 번호다(실제 사업자의 것이 아니다). 두 꼴 모두
 * `privateValues.ts` 가 한 값에서 낸다 — 술어가 금지하는 것과 여정이 심는 것이 같은 값이어야
 * 양성 대조가 공허하지 않다.
 */
const PLANTED_NUMBER = PLANTED_BUSINESS_REGISTRATION_NUMBER_TYPED;

/**
 * 예외 칸에서도 모집단에 남아야 하는 속성 넷 — 아래 ⑤ 의 양성 대조가 **하나씩** 심는 축이다.
 *
 * **이 목록을 `privateValues.ts` 에서 가져오지 않는다**(일부러 두 번 적는다). 술어의 수집 줄과
 * 한 값을 공유하면, 거기서 한 속성이 빠지는 변이가 **대조의 모집단에서도 함께** 빠져 대조가
 * 조용히 통과한다 — 양성 대조가 자기가 재려는 것을 따라 움직이면 재는 것이 없다. 여기 적힌
 * 넷이 술어의 수집 줄과 갈리는 날 **이 대조가 그 자리에서 붉는 것**이 설계된 동작이다.
 *
 * **그래도 한 방향은 비어 있었다**(M15/15I ⓑ): 술어가 속성을 **잃으면** 아래 심기가 붉지만
 * 다섯째를 **얻으면** 아무것도 붉지 않았다 — 늘어난 속성은 이 목록에 없으니 심기지 않고,
 * 심지 않은 축은 측정되지 않는다. 그 자리는 **순회가 아니라 등식**으로 닫는다: 심는 것은
 * 여전히 이 리터럴이고, [assertExemptScanPopulationMatches] 가 이 리터럴과 술어의 모집단을
 * 집합으로 맞댄다. 그러면 양쪽 어긋남이 둘 다 붉는다.
 */
const EXEMPT_FIELD_SCANNED_ATTRIBUTES = ['placeholder', 'title', 'alt', 'aria-label'] as const;

/**
 * 위 리터럴과 술어의 **훑는 속성 모집단**이 같은 집합인가 — ⓑ 의 「얻는 쪽」을 드는 단언이다.
 *
 * 등식이지 순회가 아니다: 심기 루프는 이 spec 의 리터럴만 쓰므로, 술어의 목록을 가져와
 * 대조가 그것을 따라 움직이는 꼴(공허해지는 꼴)이 되지 않는다.
 */
function assertExemptScanPopulationMatches(): void {
  const control = [...EXEMPT_FIELD_SCANNED_ATTRIBUTES].sort();
  const predicate = [...SCANNED_ATTRIBUTES].sort();
  expect(
    predicate,
    '술어가 훑는 속성 모집단이 이 대조의 목록과 갈렸다 — 심지 않는 속성은 측정되지 않는다',
  ).toStrictEqual(control);
}

/**
 * 셸의 전역 탐색으로 한 화면 간다 — 주소를 손으로 적지 않는다(`openSettings` 와 같은 관례).
 *
 * **이름을 정확히 맞추지 않는다**: 배지를 다는 자리는 접근성 이름이 「내 입찰함 3」처럼 **수를
 * 품는다**(실측). 그 수는 앞 여정들이 남긴 상태라 고정값이 아니다. 대신 **탐색으로 좁혀** 같은
 * 글자를 품은 본문의 걸음과 갈린다.
 */
async function goToScreen(page: Page, label: string, screen: string): Promise<void> {
  await page.getByRole('navigation', { name: '화면' }).getByRole('link', { name: label }).click();
  await expect(page.getByTestId(screen)).toBeVisible();
}

/** 사업 정보 절을 저장하고 **그 응답**을 돌려준다 — 화면의 믿음이 아니라 서버의 사실을 본다. */
async function saveBusinessDetails(page: Page): Promise<{ readonly businessRegistrationNumber: string | null }> {
  const written = page.waitForResponse(
    (one) =>
      one.url().endsWith('/api/profile/business-details') &&
      one.request().method() === 'POST' &&
      one.status() === 200,
  );
  await page.getByRole('button', { name: '사업 정보 저장' }).click();
  return (await (await written).json()) as { readonly businessRegistrationNumber: string | null };
}

/**
 * M15/15C — **주장이 뒤집혔다**(앞 판은 「입력칸이 없다」였다): 사업자번호는 선택 입력이 되었고
 * 「준비 중」에 남은 것은 진위 확인이다. 그 칸은 **자기 계정의 값**이라 누출 게이트의 예외
 * 하나이고, 이 test 가 그 예외의 **범위**를 잰다.
 *
 * 양성 대조가 이 test 의 절반이다: 같은 값을 **상호 칸**에 두면 같은 술어가 문제를 내야 한다.
 * 그것을 재지 않으면 「예외를 주었더니 조용하다」와 「게이트가 통째로 눈이 멀었다」가 같은 값이다.
 * 심은 값은 **칸을 옮긴다**(r1 cr L-1) — ③ 전에 자기 칸을 비워, 그 시점에 값이 예외 **밖에만**
 * 있게 한다. 두 칸에 함께 두면 대조의 힘이 남아 있어도 주석이 참이 아니다.
 *
 * **대조는 축마다 하나씩, 둘이다**(r1 v F1). 술어는 서로 다른 두 축을 들고 있다 — 하이픈 꼴
 * 정규식과 **저장되는 꼴(숫자 열 자리)의 축어**. 한 쌍만 심으면 한 축이 꺼져도 다른 축이 가려
 * 통과한다(실측: 축어 검사를 꺼도 하이픈 쌍만으로는 조용하다). 그래서 **문제 목록을 축어로**
 * 단언한다 — 심은 꼴이 내는 문제 **하나**만 나와야 하고, 그 축을 끄면 빈 목록이 되어 붉는다.
 *
 * **예외의 범위도 여기서 잰다**(r1 cr L-11): 면제는 그 칸의 **`value` 하나**이지 노드 전체가
 * 아니다. 예외 칸에 값이 아닌 속성 넷(`placeholder`·`title`·`alt`·`aria-label`) 각각에 같은 글자를 심으면 술어가 문제를 내야 한다.
 *
 * 이 test 는 **저장하지 않는다** — 서버 상태는 아래 test 가 쥔다.
 */
test('사업자번호 — 자기 설정 칸만 예외이고 다른 칸에서는 같은 게이트가 붉어진다', async ({ page }) => {
  const consoleLines: string[] = [];
  page.on('console', (message) => consoleLines.push(message.text()));
  await signIn(page);
  await openSettings(page);

  const notReady = page.getByTestId('settings-not-ready');
  await expect(notReady).toContainText('진위 확인');
  await expect(notReady.getByRole('textbox')).toHaveCount(0);

  // ① 아무것도 적기 전 — 화면에 그 꼴이 0 이다.
  await assertNoPrivateValues(page);

  // ② 자기 칸에 적으면 통과한다(예외가 듣는다).
  const own = page.getByTestId(OWN_BUSINESS_REGISTRATION_NUMBER);
  await own.fill(PLANTED_NUMBER);
  await assertNoPrivateValues(page);

  // ③ **양성 대조 ①(꼴 축)** — 값을 예외 **밖으로 옮기면** 같은 술어가 문제를 낸다(더하기가
  //    아니다). 하이픈 꼴은 꼴 정규식만 깨운다.
  await own.fill('');
  const tradeName = page.getByTestId('profile-trade-name-input');
  await tradeName.fill(PLANTED_NUMBER);
  expect(await privateValueProblems(page), '꼴 축이 예외 밖에서 붉지 않았다').toStrictEqual([
    PRIVATE_VALUE_PROBLEM_SHAPE,
  ]);

  // ④ **양성 대조 ②(축어 축)** — **저장되는 꼴**(숫자 열 자리)은 꼴 정규식이 영영 못 본다.
  //    그 축을 끄면 이 단언이 빈 목록을 본다(r1 v F1 이 지목한 자리).
  await tradeName.fill(PLANTED_BUSINESS_REGISTRATION_NUMBER);
  expect(await privateValueProblems(page), '축어 축이 예외 밖에서 붉지 않았다').toStrictEqual([
    PRIVATE_VALUE_PROBLEM_PLANTED,
  ]);

  // ⑤ **양성 대조 ③(예외의 범위) — 속성 넷을 각각**(15F r1 v F-2). 면제는 그 칸의 **값**이다.
  //    앞 판은 `title` 하나만 심었고, 나머지 셋(`placeholder`·`alt`·`aria-label`)을 모집단에서
  //    빼는 변이가 **생존했다**(verifier 실측). 넷이 술어의 같은 수집 줄에 서는 것은 사실이지만
  //    그것은 구조 추론이지 측정이 아니다 — 한 줄이 넷으로 갈리는 날 조용해지는 것이 그 차이다.
  //    ⑥ 의 되돌림 쌍도 속성마다 둔다(붉음이 심은 값 때문임을 넷 각각이 말한다).
  //
  //    **넷을 다 재고 한 번에 단언한다**(15I ⓓ). 앞 판은 속성마다 그 자리에서 `expect` 했고,
  //    `expect` 는 던지므로 **둘째가 붉으면 셋째·넷째가 그 실행에서 미측정**이었다 — 한 라운드가
  //    한 속성만 알려 주면 진단이 속성 수만큼 걸린다. 쌍(심음·되돌림)을 모아 마지막에 전체
  //    목록으로 맞대면 어긋난 속성이 **한 번에 전부** 문면에 선다.
  assertExemptScanPopulationMatches();
  await tradeName.fill('');
  const exemptionScope: { attribute: string; planted: string[]; restored: string[] }[] = [];
  for (const attribute of EXEMPT_FIELD_SCANNED_ATTRIBUTES) {
    const original = await own.evaluate((node, name) => node.getAttribute(name), attribute);
    await own.evaluate(
      (node, [name, planted]) => node.setAttribute(name, planted),
      [attribute, PLANTED_BUSINESS_REGISTRATION_NUMBER] as const,
    );
    const planted = await privateValueProblems(page);

    // ⑥ 되돌리면 다시 조용하다 — 들어올 때의 값으로 돌린다(오늘 넷 다 없지만, 생기는 날
    //    이 대조가 그 칸을 지우고 가지 않는다).
    await own.evaluate(
      (node, [name, value]) => {
        if (value === null) {
          node.removeAttribute(name);
        } else {
          node.setAttribute(name, value);
        }
      },
      [attribute, original] as const,
    );
    exemptionScope.push({ attribute, planted, restored: await privateValueProblems(page) });
  }
  expect(exemptionScope, '예외의 범위가 그 칸의 값을 넘었거나, 되돌림이 조용해지지 않았다').toStrictEqual(
    EXEMPT_FIELD_SCANNED_ATTRIBUTES.map((attribute) => ({
      attribute,
      planted: [PRIVATE_VALUE_PROBLEM_PLANTED],
      restored: [],
    })),
  );

  expect(consoleLines.join('\n')).not.toContain(journeyContext().passphrase);
});

/**
 * **저장된 번호가 있는 상태**를 재는 자리(r1 cr H-1 · v F1). 앞 판의 모집단은 공허했다 —
 * 어느 여정도 사업자번호를 **저장하지 않아서**, 넓힌 술어가 「저장된 값이 있는 화면」을 한 번도
 * 보지 못했다. 방어가 없는 것과 재지 않은 것이 같은 값이 되는 꼴이고, 이 slice 가 스스로
 * 경고한 바로 그 패턴이다.
 *
 * 그래서 **저장하고 · 재진입하고 · 다른 화면을 돈다**. 술어는 그 합성 번호의 **숫자 열 자리
 * 축어**도 금지하므로, 계약 값을 그대로 그리는 자리가 생기면(표시 서식을 거치지 않는 줄)
 * 여기서 붉어진다 — 꼴 정규식이 영영 못 보는 사각이다.
 *
 * **전역 상태는 teardown 이 되돌린다**(r1 cr M-6) — 이 본문의 마지막 걸음이 아니다. 사업 정보는
 * 계정당 한 행이고 여정들이 한 하네스를 차례로 쓰므로, 아래 어느 단언이 깨져도 심은 번호가
 * 남아서는 안 된다. 되돌림과 **끝 상태 단언**은 [clearBusinessRegistrationNumber] 와 `afterEach` 에 있다.
 */
test('사업자번호 — 저장한 뒤 재진입·다른 화면에서도 그 값이 서지 않는다', async ({ page }) => {
  await signIn(page);
  await openSettings(page);

  const own = page.getByTestId(OWN_BUSINESS_REGISTRATION_NUMBER);
  await own.fill(PLANTED_NUMBER);
  expect((await saveBusinessDetails(page)).businessRegistrationNumber).toBe(
    PLANTED_BUSINESS_REGISTRATION_NUMBER,
  );

  // ① 저장 직후의 설정 화면 — 값은 예외 칸 하나에만 있다.
  await expect(own).toHaveValue(PLANTED_NUMBER);
  await assertNoPrivateValues(page);

  // ② 재진입 — 서버에서 다시 읽어 그린 화면에서도 같다.
  await goToScreen(page, '오늘의 공고', 'screen-home');
  await assertNoPrivateValues(page);
  await openSettings(page);
  await expect(page.getByTestId(OWN_BUSINESS_REGISTRATION_NUMBER)).toHaveValue(PLANTED_NUMBER);
  await assertNoPrivateValues(page);

  // ③ 다른 화면들 — 같은 카드가 서는 시작하기와, 카드가 없는 화면 셋.
  for (const [label, screen] of [
    ['시작하기', 'screen-onboarding'],
    ['내 입찰함', 'screen-bid-box'],
    ['결과 확인', 'screen-results'],
    ['알림함', 'screen-inbox'],
  ] as const) {
    await goToScreen(page, label, screen);
    await assertNoPrivateValues(page);
  }
});

/**
 * **보이는 라벨 셋이 실제로 보인다**(M14/14F r1 보강 · cr D3 · verifier 변이 V9).
 *
 * 단위 레인의 D3 단언은 「그 글자가 있고 그것이 입력의 이름이다」까지만 본다 — jsdom 이 CSS 를
 * 풀지 않아 **`sr-only` 를 걸어도 초록**이었다(이름도 글자도 그대로이고 눈에만 안 보인다).
 * 그 변이가 이 slice 의 가장 비싼 거짓 초록이었다: D3 이 고친 것이 바로 「눈으로 보는 운영자가
 * 어느 칸에 무엇을 적는지 모른다」이므로, **보인다**를 재지 않으면 그 고침이 지켜지지 않는다.
 *
 * 그래서 **브라우저에서** 본다. 은폐 기법은 여럿이라 클래스 글자로 집지 않는다 — 상자(`sr-only`
 * 의 1px)와 잘라내기에 더해 **`checkVisibility`** 로 `invisible`·`opacity-0` 까지 브라우저에게
 * 묻는다(열거를 늘리는 대신). 글자 색을 배경과 같게 하는 꼴(`text-transparent`)은 **대비 축**이고
 * 이 축이 아니다 — OPEN 으로 등재했다.
 */
test('설정 — 시평액 입력 셋의 라벨이 눈에 보인다', async ({ page }) => {
  await signIn(page);
  await openSettings(page);
  await expect(page.getByTestId('capacity-card')).toBeVisible();

  const sources = await page.evaluate(nameSourcesInPage, 'capacity-card');

  // **비공허가 비교 앞에 선다** — 칸을 못 찾으면 아래 전수 단언이 공집합 위에서 참이다.
  expect(sources.length, '시평액 카드에서 입력을 찾지 못했다').toBe(3);
  // 이름이 **보이는 라벨**에서 온다 — `aria-label` 로 돌아가면 그 자리가 다시 빈 상자 셋이 된다.
  expect(
    sources.filter((one) => one.from !== 'label').map((one) => `${one.control}: ${one.from}`),
    '이름이 보이는 라벨에서 오지 않는다',
  ).toStrictEqual([]);
  /**
   * 그리고 그 라벨이 **그려진다**. `sr-only` 는 상자를 1px 로 줄이고 잘라내므로 둘 다 걸린다 —
   * 글자 크기(12px)보다 넉넉히 작은 문턱을 둔다.
   */
  const hidden = sources.filter((one) => one.width < 8 || one.height < 8 || one.clipped || !one.shown);
  expect(
    hidden.map(
      (one) =>
        `${one.control}: ${one.width}x${one.height}${one.clipped ? ' clipped' : ''}${one.shown ? '' : ' hidden'}`,
    ),
    '라벨이 시각적으로 숨겨졌다 — 눈으로 보는 운영자에게는 빈 상자다',
  ).toStrictEqual([]);
});
