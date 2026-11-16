import { expect, test } from '@playwright/test';

import { signIn } from './shared';

/**
 * 셸의 **세션 자리**(M13/13B, cr H-1). 데스크톱 폭에서 로그아웃이 실패하면 그 사실이 보이는가.
 *
 * **왜 여정인가.** 이 축은 jsdom 으로 설 수 없다 — 앞 판의 실패 알림은 `md:hidden` 하단 탐색
 * **안**에 있었고, DOM 존재만 재는 vitest 는 초록이었지만 데스크톱에서는 `display:none` 이라
 * 운영자가 아무것도 보지 못했다. 「보인다」를 재려면 CSS 가 있는 브라우저가 필요하다.
 *
 * **이 파일은 기본 폭(Desktop Chrome)으로 돈다** — 390px 레인(`touch-target.spec.ts`)과 **다른
 * 축**이다. 그 둘이 한 파일에 있으면 한쪽 뷰포트가 다른 쪽 단언을 조용히 덮는다.
 */

/** 실패를 **가로채기로** 만든다 — 서버 세션을 실제로 폐기하지 않으므로 다른 여정에 닿지 않는다. */
async function failSignOut(page: Parameters<typeof signIn>[0]): Promise<void> {
  await page.route('**/api/session', async (route, request) => {
    if (request.method() !== 'DELETE') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'INTERNAL_ERROR', message: '합성 실패' }),
    });
  });
}

test.describe.configure({ mode: 'serial' });

test('데스크톱 폭에서 로그아웃이 실패하면 그 사실이 보인다', async ({ page }) => {
  await signIn(page);
  // 이 레인이 **좁은 폭이 아닌 것**을 못박는다 — 설정이 바뀌어 모바일로 돌면 이 단언이 재려던
  // 자리(머리글의 로그아웃)가 사라지고 test 는 다른 것을 재게 된다.
  expect(page.viewportSize()?.width ?? 0).toBeGreaterThanOrEqual(768);
  await failSignOut(page);

  const before = new URL(page.url()).pathname;
  const refused = page.waitForResponse(
    (one) => one.url().endsWith('/api/session') && one.request().method() === 'DELETE',
  );
  await page.getByTestId('sign-out').click();
  expect((await refused).status()).toBe(500);

  // **보인다**가 이 test 의 전부다(DOM 에 있다가 아니라).
  await expect(page.getByTestId('sign-out-failure')).toBeVisible();
  // 실패했으므로 **떠나지 않는다** — 폐기되지 않았는데 로그아웃된 것처럼 보이면 운영자가
  // 세션을 남긴 채 떠난다.
  expect(new URL(page.url()).pathname).toBe(before);
});

test('성공하면 로그인 자리로 가고 탐색이 사라진다', async ({ page }) => {
  await signIn(page);

  const accepted = page.waitForResponse(
    (one) => one.url().endsWith('/api/session') && one.request().method() === 'DELETE',
  );
  await page.getByTestId('sign-out').click();
  expect((await accepted).status()).toBe(204);

  await expect(page.getByTestId('screen-login')).toBeVisible();
  // 로그인 자리에는 탐색을 그리지 않는다(착수 판정) — 그 결정이 실제로 선다.
  await expect(page.getByTestId('nav-more-toggle')).toHaveCount(0);
  await expect(page.getByTestId('sign-out')).toHaveCount(0);
});
