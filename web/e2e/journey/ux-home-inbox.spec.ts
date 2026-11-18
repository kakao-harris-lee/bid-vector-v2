import { expect, test, type Page } from '@playwright/test';

import { notificationItemTestId } from '../../src/display/notificationDisplay';

import { assertOnlyAllowedOperations, ensureInBidBox, search, signIn, watchRequests } from './shared';

/**
 * **홈과 알림함의 여정**(M13/13E — 받는 항목 8 · 9 · 11 · 7).
 *
 * 여기서 재는 것은 **브라우저에서만 설 수 있는 것**이다. 구조와 전수는 vitest 가 잠근다
 * (`src/screens/TodayNoticesScreen.test.tsx` · `InboxScreen.test.tsx` ·
 * `src/components/StateBadge.test.tsx`) — 이 자리는 ⓐ 조건 칩의 값이 **실제 서버 파라미터로**
 * 나가 200 을 받는가 ⓑ 2층 라벨을 **탭했을 때 글자가 자리를 차지하는가**(시각 은폐의 1px 상자와
 * 가른다 — jsdom 은 상자를 모른다) ⓒ 링크를 열면 **셸의 안 읽음 배지가 줄어드는가**를 본다.
 *
 * **이 spec 은 자기 공고를 쓴다.** 한 하네스를 공유하므로 건의 상태가 전역이고(fixture 의
 * `why_third_notice`·`why_fourth_notice` 가 같은 사유를 적어 둔 자리), 다른 여정이 미는 공고를
 * 쓰면 서로의 전이를 본다. 파일 이름이 `touch-target` **뒤로** 정렬되는 것도 그 축이다 — 이
 * spec 이 세우는 건·알림을 앞 여정들이 보지 않는다(13B 알려진 제한 19 가 미리 적어 둔 전제).
 */
const OWN_NOTICE = 'SYN-M9-OPEN-PROGRESS-NONE';
const OWN_ROUND = '000';

/** 그 셸 배지의 지금 값 — 0 이면 **배지가 없다**(13B: 0 은 그리지 않는다). */
async function navUnreadCount(page: Page): Promise<number> {
  const badge = page.getByTestId('nav-unread');
  if ((await badge.count()) === 0) {
    return 0;
  }
  return Number((await badge.textContent()) ?? '0');
}

test('홈 — 기본은 제안이고, 조건 칩이 서버의 기존 파라미터로 좁힌다', async ({ page }) => {
  const watched = watchRequests(page);
  /** 검색 **전에** 공고 목록을 긁는지 본다 — 앞 판은 조건 없는 전체 목록이 제안 위에 왔다. */
  const noticeReads: string[] = [];
  page.on('request', (one) => {
    if (one.url().includes('/api/notices') && one.method() === 'GET') {
      noticeReads.push(one.url());
    }
  });

  await signIn(page);
  await expect(page.getByTestId('screen-home')).toBeVisible();
  // 기본 자리는 제안이다 — 이 하네스에는 감시 조건이 없으므로 그 빈 얼굴이 선다(D-7E-9).
  await expect(page.getByTestId('suggestions-empty')).toBeVisible();
  expect(noticeReads, '검색하기 전에 전체 공고 목록을 긁어 왔다').toStrictEqual([]);

  // 칩은 접힌 채로 서고, 누르면 자기 칸이 나온다(§6.5 「필터는 칩」).
  const chip = page.getByTestId('chip-agency');
  await expect(chip).toHaveAttribute('aria-expanded', 'false');
  await chip.click();
  const panel = page.getByTestId('chip-panel-agency');
  await expect(panel).toBeVisible();

  // 그 공고의 **발주기관 이름**으로 좁힌다 — 기관 파라미터가 실제로 서버에 가 닿는지를 본다.
  const rows = await search(page, OWN_NOTICE);
  const mine = rows.find((one) => one.noticeNumber === OWN_NOTICE);
  expect(mine, `검색이 ${OWN_NOTICE} 를 내지 않았다 — fixture 가 들어오지 않았다`).toBeDefined();
  const agency = mine!.demandAgencyName ?? mine!.noticeAgencyName;
  expect(agency, 'fixture 가 기관명을 싣는다').not.toBeNull();

  const narrowed = page.waitForResponse(
    (one) =>
      one.url().includes('/api/notices') &&
      new URL(one.url()).searchParams.get('agency') === agency &&
      one.status() === 200,
  );
  await panel.getByLabel('기관명').fill(agency!);
  await page.getByRole('button', { name: '검색' }).click();
  const narrowedRows = (await (await narrowed).json()) as readonly { readonly noticeNumber: string }[];

  // **비공허**다 — 조건이 아무것도 내지 않으면 「파라미터가 닿았다」가 빈 집합으로 참이 된다.
  expect(narrowedRows.length, '기관 조건이 아무 행도 내지 않았다').toBeGreaterThan(0);
  expect(narrowedRows.map((one) => one.noticeNumber)).toContain(OWN_NOTICE);
  // 검색 결과가 제안 자리를 **대신한다**(받는 항목 8).
  await expect(page.getByTestId('suggestions-empty')).toHaveCount(0);
  // 칩이 적용된 값을 자기 얼굴에 싣고, 지우는 길이 함께 선다.
  await expect(chip).toContainText(agency!);
  await expect(page.getByTestId('chip-clear-agency')).toBeVisible();

  // 지우면 **그 조건만** 빠진다 — 공고명 조건은 남는다.
  const cleared = page.waitForResponse(
    (one) =>
      one.url().includes('/api/notices') &&
      new URL(one.url()).searchParams.get('agency') === null &&
      new URL(one.url()).searchParams.get('q') === OWN_NOTICE &&
      one.status() === 200,
  );
  await page.getByTestId('chip-clear-agency').click();
  expect((await cleared).status()).toBe(200);

  // 조건을 모두 지우면 제안으로 돌아온다(돌아갈 길이 있다).
  await page.getByTestId('search-clear').click();
  await expect(page.getByTestId('suggestions-empty')).toBeVisible();

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

test('알림함 — 2층 라벨이 탭으로 펼쳐지고, 링크를 열면 읽음이 되어 셸 배지가 줄어든다', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  /**
   * **자기 알림을 만든다.** 투찰가 요청이 알림함에 기록을 남긴다(M7/7C③-b 의 트리거) — 앞선
   * 여정의 알림에 기대면 이 여정이 그 순서에 매인다.
   *
   * 같은 하네스에 두 번 미는 꼴(`--repeat-each`)에서는 둘째 회차에 이미 요청된 건이라 주 행동이
   * 「투찰가 요청」이 아니다. 그때는 알림이 이미 있으므로 요청을 **다시 보내지 않는다**(여정 ②
   * 의 「이미 담긴 건」과 같은 판단).
   */
  const rows = await search(page, OWN_NOTICE);
  expect(rows.map((one) => one.noticeNumber), `검색이 ${OWN_NOTICE} 를 내지 않았다`).toContain(OWN_NOTICE);
  const card = page.getByTestId(`notice-card-${OWN_NOTICE}`);
  await ensureInBidBox(page, card);
  await card.getByTestId('notice-title').click();
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  const primary = page.getByTestId('worksheet-primary');
  if ((await primary.textContent()) === '투찰가 요청') {
    const priced = page.waitForResponse(
      (one) => one.url().includes('/api/bid-price-requests') && one.request().method() === 'POST',
    );
    await page.getByTestId('active-bids').fill('1');
    await primary.click();
    expect((await priced).status()).toBe(200);
  }

  await page.getByRole('link', { name: '알림함' }).click();
  await expect(page.getByTestId('screen-inbox')).toBeVisible();

  const rowId = notificationItemTestId({
    kind: 'BID_PRICE_REQUEST_RECORDED',
    noticeNumber: OWN_NOTICE,
    noticeRound: OWN_ROUND,
    bidKind: 'REAL',
  });
  const row = page.getByTestId(rowId);
  await expect(row).toBeVisible();
  await expect(row.getByTestId('read-state'), '내가 만든 알림이 이미 읽음이다').toHaveText('안읽음');

  /**
   * **2층 라벨의 아래층이 탭으로 자리를 차지한다**(받는 항목 11). 접힌 동안은 시각 은폐라 상자가
   * 1px 짜리이고, 보조기기는 그때도 읽는다 — 그 둘을 가르는 것이 **상자**이므로 이 축은 브라우저
   * 에서만 설 수 있다(jsdom 은 상자를 모른다).
   */
  // 아래층을 든 배지는 **묶음 머리의 투찰 종류**다(행의 종류 배지에는 아래층이 없다). 그 묶음은
  // 차례를 testId 에 담으므로 이름으로 짓지 않고 **내 행을 품은 묶음**으로 집는다.
  const group = page
    .locator('[data-testid^="notice-group-"]')
    .filter({ has: page.getByTestId(rowId) });
  const badge = group.locator('[aria-expanded]').first();
  const whyId = await badge.getAttribute('aria-controls');
  expect(whyId, '2층 라벨이 펼침 대상을 가리키지 않는다').not.toBeNull();
  const why = page.locator(`[id="${whyId}"]`);
  const collapsed = await why.boundingBox();
  expect(collapsed, '아래층 문면이 DOM 에 없다 — 보조기기 경로가 닫혔다').not.toBeNull();
  expect(collapsed!.height, '접힌 아래층이 처음부터 자리를 차지한다').toBeLessThan(2);

  await badge.click();

  await expect(badge).toHaveAttribute('aria-expanded', 'true');
  const expanded = await why.boundingBox();
  expect(expanded!.height, '탭했는데 아래층이 보이지 않는다').toBeGreaterThan(collapsed!.height);

  /**
   * **링크를 열면 읽음**(받는 항목 9). 쓰기를 걸고 **바로 이동**하므로, 성공의 신호는 알림함
   * 안이 아니라 **셸의 안 읽음 배지**다 — 그 배지는 알림함 질의 키의 접두 아래에 살아 읽음
   * 쓰기의 무효화가 함께 닿는다(13B 가 그 관계를 세웠다).
   */
  const before = await navUnreadCount(page);
  expect(before, '안 읽음이 0 인데 안읽음 행이 있다').toBeGreaterThan(0);

  await row.getByRole('link', { name: /워크시트/u }).click();

  // 이동을 막지 않는다 — 운영자를 쓰기가 끝날 때까지 붙들어 두지 않는다.
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  // 성공하면 **줄어든다**(실패하면 그대로인 갈래는 vitest 가 잠근다 — 서버 실패를 이 레인에서
  // 만들 수단이 없다).
  await expect
    .poll(async () => navUnreadCount(page), { message: '링크를 열었는데 안 읽음 수가 줄지 않았다' })
    .toBe(before - 1);

  // 돌아오면 그 행은 **서버가 준 값**으로 읽음이다(낙관 갱신이 아니라 재조회의 결과다).
  await page.getByRole('link', { name: '알림함' }).click();
  await expect(page.getByTestId(rowId).getByTestId('read-state')).toHaveText('읽음');

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});
