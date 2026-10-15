import { defineConfig, devices } from '@playwright/test';

// 7A 의 e2e 는 셸이 뜨는지 하나다(ADR 0011 D-5). 여정 스모크는 7E 가 화면과 함께 들인다.
//
// `webServer` 를 둔다 — legacy 는 이 블록이 없어 사람이 서버를 먼저 띄워야 했고(조사 §1.4),
// 그러면 CI 와 로컬에서 같은 명령이 다른 전제를 갖는다. 빌드 산출물을 `vite preview` 로
// 띄우므로 이 스모크가 보는 것은 dev server 가 아니라 **빌드된 번들**이다.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: process.env.CI ? 'list' : [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: { timeout: 5_000 },
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run preview',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
