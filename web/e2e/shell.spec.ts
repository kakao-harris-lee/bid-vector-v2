import { expect, test } from '@playwright/test';

// 7A 스모크 하나 — 빌드된 번들이 뜨고 셸이 그려진다. 여정 스모크는 7E(화면)·7D(인증) 뒤.
test('빌드된 번들에서 운영자 셸이 뜬다', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('bid-vector 운영자');
  // '/' 는 B 오늘의 공고 자리다.
  await expect(page.getByTestId('screen-home')).toBeVisible();
});
