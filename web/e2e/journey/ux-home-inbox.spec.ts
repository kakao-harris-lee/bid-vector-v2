import { expect, test, type Page } from '@playwright/test';

import { assertOnlyAllowedOperations, search, signIn, watchRequests } from './shared';

/**
 * **홈과 알림함의 여정**(M13/13E — 받는 항목 8 · 9 · 11 · 7).
 *
 * 여기서 재는 것은 **브라우저에서만 설 수 있는 것**이다. 구조와 전수는 vitest 가 잠근다
 * (`src/screens/TodayNoticesScreen.test.tsx` · `InboxScreen.test.tsx` ·
 * `src/components/StateBadge.test.tsx`) — 이 자리는 ⓐ 조건 칩의 값이 **실제 서버 파라미터로**
 * 나가 200 을 받는가 ⓑ 2층 라벨을 **탭했을 때 글자가 자리를 차지하는가**(시각 은폐의 1px 상자와
 * 가른다 — jsdom 은 상자를 모른다) ⓒ 링크를 열면 **셸의 안 읽음 배지가 줄어드는가**를 본다.
 *
 * **이 spec 은 건의 상태를 하나도 바꾸지 않는다.** 한 하네스를 공유하므로 건의 상태가
 * 전역이고(fixture 의 `why_third_notice`·`why_fourth_notice` 가 같은 사유를 적어 둔 자리),
 * 마감이 남은 공고는 전부 다른 여정의 것이다. 그래서 홈 쪽은 **읽기만** 하고(검색), 알림함
 * 쪽은 앞선 여정이 만든 **안읽음 행 하나**를 읽음으로 바꾼다 — 파일 이름이 `touch-target`
 * **뒤로** 정렬되는 것이 그 전제다(13B 알려진 제한 19 가 미리 적어 둔 자리).
 */
const OWN_NOTICE = 'SYN-M9-OPEN-PROGRESS-NONE';

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
  const panel = page.getByTestId('chip-panel-agency');
  await expect(chip).toHaveAttribute('aria-expanded', 'false');
  /**
   * **접힌 칸이 실제로 보이지 않는다**(수정 라운드 1 — 리뷰 M-4). vitest 는 `hidden` **속성**만
   * 재고, 그 속성이 그리기에 닿는 것은 전역 CSS 의 `display:none !important` 에 달려 있다 —
   * 의존성 쪽 전제다. 그 전제가 깨지면(배치 클래스가 덮거나 preflight 가 꺼지면) 칸 셋이 늘
   * 펼쳐진 화면이 되는데, 상자를 재는 자리는 여기뿐이다.
   */
  await expect(panel, '접힌 칸이 보인다 — 숨김이 그리기에 닿지 않았다').not.toBeVisible();
  await chip.click();
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

  await page.getByRole('navigation', { name: '화면' }).getByRole('link', { name: '알림함' }).click();
  await expect(page.getByTestId('screen-inbox')).toBeVisible();

  /**
   * **안읽음 행 하나를 고른다 — 이 spec 은 알림을 새로 만들지 않는다.**
   *
   * 앞선 여정들이 트리거를 발화시켜 알림을 남기고(그 사실은 `results.spec.ts` 가 단언한다), 이
   * 파일은 `touch-target` **뒤로** 정렬되므로 그 알림이 이미 있다. 자기 알림을 만들려면 마감이
   * 남은 공고에 건을 세워야 하는데, 그런 fixture 는 전부 다른 여정의 것이다 — **남의 건을 밀지
   * 않는 쪽**을 골랐다(공유 하네스에서 건의 상태는 전역이다). 그래서 전제를 숨기지 않고 **아래
   * 단언으로 드러낸다**.
   *
   * 읽은 행은 쓰기를 내지 않으므로(그 분기는 vitest 가 잠근다) 고르는 것은 **안읽음** 행이다.
   * 행 문면의 「안읽음」으로 가른다 — 버튼은 「읽음으로」라 이 글자를 담지 않는다.
   */
  const unread = page
    .getByTestId('screen-inbox')
    .locator('[data-testid^="notification-"]')
    .filter({ hasText: '안읽음' });
  await expect(
    unread.first(),
    '안읽음 알림이 없다 — 앞선 여정의 트리거가 발화하지 않았다',
  ).toBeVisible();
  const row = unread.first();
  const rowId = await row.getAttribute('data-testid');
  expect(rowId, '행이 자기 신원을 싣지 않는다').not.toBeNull();

  /**
   * **2층 라벨의 아래층이 탭으로 자리를 차지한다**(받는 항목 11). 접힌 동안은 시각 은폐라 상자가
   * 1px 짜리이고, 보조기기는 그때도 읽는다 — 그 둘을 가르는 것이 **상자**이므로 이 축은 브라우저
   * 에서만 설 수 있다(jsdom 은 상자를 모른다).
   */
  // 아래층을 든 배지는 **묶음 머리의 투찰 종류**다(행의 종류 배지에는 아래층이 없다). 그 묶음은
  // 차례를 testId 에 담으므로 이름으로 짓지 않고 **내 행을 품은 묶음**으로 집고, 배지도
  // **이름으로** 집는다(DOM 차례에 기대지 않는다 — 수정 라운드 1, 리뷰 L).
  const group = page
    .locator('[data-testid^="notice-group-"]')
    .filter({ has: page.getByTestId(rowId!) });
  const badge = group.getByTestId('group-bid-kind');
  const whyId = await badge.getAttribute('aria-describedby');
  expect(whyId, '2층 라벨이 설명 자리를 가리키지 않는다').not.toBeNull();
  const why = page.locator(`[id="${whyId}"]`);
  const whyText = (await why.textContent())?.trim() ?? '';
  expect(whyText, '아래층 문면이 DOM 에 없다 — 문면 게이트의 눈에서도 사라진다').not.toBe('');
  const collapsed = await why.boundingBox();
  expect(collapsed, '아래층 문면이 DOM 에 없다 — 보조기기 경로가 닫혔다').not.toBeNull();
  expect(collapsed!.height, '접힌 아래층이 처음부터 자리를 차지한다').toBeLessThan(2);

  /**
   * **접근성 트리에 그 문면이 정확히 한 번**(수정 라운드 1 — verifier F-1 · 리뷰 M-2).
   *
   * DOM 노드 계수로는 이 축이 서지 않는다 — 한 노드가 **설명과 본문 둘로** 쓰이면 사본이 없어도
   * 두 번 전달된다(앞 판 실측: 접힘·펼침 둘 다 설명 1 + 본문 1). 낭독은 접근성 트리를 따르므로
   * 그 트리를 직접 본다.
   *
   * **자리를 특정한다** — 전체 트리에서 같은 문면을 세면 다른 묶음의 같은 배지까지 잡힌다(투찰
   * 종류 문면은 묶음마다 같다). 설명은 `aria-describedby` 로 그 노드를 찍어 읽고, 본문 쪽은
   * **묶음의 접근성 스냅숏**으로 본다(그 스냅숏은 `aria-hidden` 을 뺀 트리다).
   */
  const describedAs = async (): Promise<string> => {
    const client = await page.context().newCDPSession(page);
    await client.send('Accessibility.enable');
    const { root } = (await client.send('DOM.getDocument')) as { root: { nodeId: number } };
    const { nodeId } = (await client.send('DOM.querySelector', {
      nodeId: root.nodeId,
      selector: `[aria-describedby="${whyId}"]`,
    })) as { nodeId: number };
    const { nodes } = (await client.send('Accessibility.getPartialAXTree', {
      nodeId,
      fetchRelatives: false,
    })) as { nodes: readonly { description?: { value?: string } }[] };
    await client.detach();
    return nodes.map((one) => one.description?.value ?? '').find((one) => one !== '') ?? '';
  };

  expect(await describedAs(), '접힌 배지의 설명이 사라졌다 — 보조기기 경로가 닫혔다').toBe(whyText);
  expect(
    await group.ariaSnapshot(),
    '같은 문면이 본문 텍스트로도 선다 — 두 번 낭독된다',
  ).not.toContain(whyText);

  await badge.click();

  const expanded = await why.boundingBox();
  expect(expanded!.height, '탭했는데 아래층이 보이지 않는다').toBeGreaterThan(collapsed!.height);
  expect(await describedAs(), '펼치면서 설명이 사라졌다').toBe(whyText);
  expect(
    await group.ariaSnapshot(),
    '펼치면서 같은 문면이 본문으로도 섰다 — 두 번 낭독된다',
  ).not.toContain(whyText);

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
  await page.getByRole('navigation', { name: '화면' }).getByRole('link', { name: '알림함' }).click();
  await expect(page.getByTestId(rowId!).getByTestId('read-state')).toHaveText('읽음');

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});
