import { expect, test, type Page } from '@playwright/test';

import { assertOnlyAllowedOperations, templated, watchRequests } from './shared';

/**
 * 여정 — **열린 가입**(M15/15B). 로그인 화면의 「계정 만들기」에서 출발해 새 아이디로 가입하고,
 * 곧장 시작하기에 서서, **설정이 빈 계정으로도 화면 전부에 들어갈 수 있는지**를 잰다.
 *
 * 이 여정이 다른 여정과 다른 점은 **운영 계정으로 로그인하지 않는다**는 것이다 — 가입 자신이
 * 세션을 내므로 `signIn` 헬퍼를 지나지 않는다. 그것이 계약의 문장(「성공하면 곧장 시작하기」)이
 * 실제로 서는지를 재는 자리다.
 *
 * **아이디는 실행마다 새로 짓는다.** 하네스의 Postgres 는 실행마다 버려지지만 한 실행 안에서
 * 재시도가 돌면 같은 이름이 409 가 된다 — 그러면 재는 축(가입이 되는가)이 아니라 이름 충돌을
 * 보게 된다. 비밀번호는 **고정값**이고 계약의 하한(10자)을 넘는다.
 */
const PASSPHRASE = 'journey-signup-passphrase';

/** 화면 자리와 그 얼굴 — **빈 설정으로도 들어갈 수 있다**가 이 표가 재는 것이다. */
const SCREENS = [
  { path: '/start', testId: 'screen-onboarding' },
  { path: '/', testId: 'screen-home' },
  { path: '/bids', testId: 'screen-bid-box' },
  { path: '/results', testId: 'screen-results' },
  { path: '/inbox', testId: 'screen-inbox' },
  { path: '/settings', testId: 'screen-settings' },
] as const;

function freshLoginName(): string {
  // 계약의 글자 집합(`[a-z0-9._-]`) 안에서만 짓는다 — 밖으로 나가면 400 이고 그것은 다른 축이다.
  return `journey-signup-${Date.now().toString(36)}`;
}

async function signUpThroughScreen(page: Page, loginName: string): Promise<number> {
  await page.goto('/login');
  await page.getByTestId('go-signup').click();
  await expect(page.getByTestId('screen-signup')).toBeVisible();

  await page.fill('input[name="loginName"]', loginName);
  await page.fill('input[name="passphrase"]', PASSPHRASE);
  await page.fill('input[name="passphraseConfirm"]', PASSPHRASE);

  const created = page.waitForResponse(
    (one) => one.url().endsWith('/api/accounts') && one.request().method() === 'POST',
  );
  await page.getByTestId('signup-submit').click();
  return (await created).status();
}

test.describe.configure({ mode: 'serial' });

test('가입하면 곧장 시작하기에 서고, 빈 설정으로도 화면 전부에 들어간다', async ({ page }) => {
  const watched = watchRequests(page);
  const loginName = freshLoginName();

  expect(await signUpThroughScreen(page, loginName)).toBe(201);

  // 계약의 문장 — **따로 로그인하지 않는다.** 가입 응답의 세션으로 바로 보호 화면이 선다.
  await expect(page.getByTestId('screen-onboarding')).toBeVisible();
  expect(new URL(page.url()).pathname).toBe('/start');

  for (const screen of SCREENS) {
    await page.goto(screen.path);
    // **막는 자리가 없다**: 설정이 비어 있어도 각 화면이 자기 얼굴을 보인다(빈 얼굴 + 사유).
    await expect(page.getByTestId(screen.testId)).toBeVisible();
    // 로그인으로 떠밀리지 않는다 — 떠밀리면 「가입 직후 쓸 수 있다」가 거짓이다.
    expect(new URL(page.url()).pathname).toBe(screen.path);
  }

  expect(watched.outside, '화면이 V2 API 밖을 불렀다').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
  expect([...watched.apiPaths].map(templated)).toContain('POST /api/accounts');
});

test('같은 아이디로 다시 가입하면 아이디 칸 옆에 문장이 서고 세션이 서지 않는다', async ({ page }) => {
  const loginName = freshLoginName();
  expect(await signUpThroughScreen(page, loginName)).toBe(201);
  await expect(page.getByTestId('screen-onboarding')).toBeVisible();

  expect(await signUpThroughScreen(page, loginName)).toBe(409);

  await expect(page.getByTestId('signup-error-login-name')).toBeVisible();
  await expect(page.getByTestId('signup-error-login-name')).toContainText('이미 쓰이는 아이디');
  // 거부면 자리를 옮기지 않는다 — 가입 화면에 그대로 선다.
  expect(new URL(page.url()).pathname).toBe('/signup');
});

test('형식이 틀린 아이디는 그 칸 옆에서 거부된다 — 서버가 가른다', async ({ page }) => {
  await page.goto('/signup');
  await page.fill('input[name="loginName"]', 'ab');
  await page.fill('input[name="passphrase"]', PASSPHRASE);
  await page.fill('input[name="passphraseConfirm"]', PASSPHRASE);

  const refused = page.waitForResponse(
    (one) => one.url().endsWith('/api/accounts') && one.request().method() === 'POST',
  );
  await page.getByTestId('signup-submit').click();
  expect((await refused).status()).toBe(400);

  await expect(page.getByTestId('signup-error-login-name')).toBeVisible();
});
