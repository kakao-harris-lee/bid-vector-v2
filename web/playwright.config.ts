import { defineConfig, devices } from '@playwright/test';

// **「여는 행위」 층을 이 프로세스에 세운다**(M12/gates-web-read-3 — ⓑ⑥). 이 설정이 적재되는
// 프로세스는 저장소가 쓴 코드를 돌리므로 Kotlin 원문을 열 수 있는 자리다. 층 모듈이 자기를
// `NODE_OPTIONS` 에 실어 **이 도구가 띄우는 node 프로세스**까지 덮는다(실측: playwright spec
// 워커가 그렇게 거부됐다). 이 의무의 모집단은 손 목록이 아니라 **추적된 뿌리 `*.config.*`
// 전수**이고 `scripts/kotlin-read-guard.test.mjs` 가 센다.
import './scripts/kotlin-read-guard-setup.mjs';


// 7A 의 e2e 는 셸이 뜨는지 하나다(ADR 0011 D-5). 여정 스모크는 7E 가 화면과 함께 들인다.
//
// `webServer` 를 둔다 — legacy 는 이 블록이 없어 사람이 서버를 먼저 띄워야 했고(조사 §1.4),
// 그러면 CI 와 로컬에서 같은 명령이 다른 전제를 갖는다. 빌드 산출물을 `vite preview` 로
// 띄우므로 이 스모크가 보는 것은 dev server 가 아니라 **빌드된 번들**이다.
export default defineConfig({
  testDir: './e2e',
  // M7/7E-1 — 여정 레인은 자기 설정 파일(`playwright.journey.config.ts`)이 돈다. 하네스를
  // 띄우는 그 spec 들이 이 스모크에 섞이면 셸 스모크가 하네스 비용을 물게 된다.
  testIgnore: ['journey/**'],
  // **Playwright 가 수집하는 것은 `*.spec.ts` 뿐이다.** 기본 `testMatch` 는 `*.test.*` 도 받는데,
  // 하네스의 단위 test(`e2e/harness/*.test.mjs`, vitest 가 돌린다)가 그 패턴에 걸려 Playwright
  // 런너가 그것을 집어 들고 죽었다(실측: `Cannot read properties of undefined (reading 'config')`).
  // 두 레인의 수집 경계를 **파일 이름 규약**으로 못박는다.
  testMatch: '**/*.spec.ts',

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
