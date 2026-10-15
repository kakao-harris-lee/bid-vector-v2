import { expect, test } from '@playwright/test';

// 7A 스모크 하나 — 빌드된 번들이 뜨고 셸이 그려진다. 여정 스모크는 7E(화면)·7D(인증) 뒤.
test('빌드된 번들에서 운영자 셸이 뜬다', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('bid-vector 운영자');
  // '/' 는 B 오늘의 공고 자리다.
  await expect(page.getByTestId('screen-home')).toBeVisible();
});

// code-review r1 F-10 — 「셸이 뜬다」가 알 수 없는 URL 에서도 참인지 잰다(자리 표 밖의
// catch-all). 이 단언이 없으면 404 자리는 코드에만 있고 실제로 뜨는지는 아무도 모른다.
test('알 수 없는 주소에서도 셸 안의 없는 주소 자리가 뜬다', async ({ page }) => {
  await page.goto('/no-such-page');

  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('screen-not-found')).toBeVisible();
});
