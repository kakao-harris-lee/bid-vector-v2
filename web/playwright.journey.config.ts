import { defineConfig, devices } from '@playwright/test';

/**
 * 여정 e2e 설정(M7/7E-1, D-7E-1) — 7A 의 셸 스모크와 **다른 설정 파일**이다. 이유는 둘이다:
 * ⓐ 이 레인만 하네스(출하 app.jar · 버릴 Postgres · fake KONEPS)를 띄우므로 셸 스모크가 그
 * 비용을 물지 않는다 ⓑ 두 레인의 비용이 CI step 으로 따로 보인다.
 *
 * `webServer.command` 가 **하네스 하나**다 — 백엔드를 먼저 세우고 그 다음 `vite preview` 를
 * 띄우는 순서를 한 프로세스 트리에 두면 `globalSetup` 과 `webServer` 의 기동 순서에 기대지
 * 않는다. Playwright 가 이 프로세스를 끝내면 하네스가 자식과 컨테이너를 거둔다.
 */
const PREVIEW_PORT = 4174;
const PREVIEW_URL = `http://127.0.0.1:${PREVIEW_PORT}`;

export default defineConfig({
  testDir: './e2e/journey',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  // 하네스가 한 벌뿐이고 여정이 같은 계정·같은 건을 쓴다 — 워커를 늘리면 서로의 전이를 본다.
  workers: 1,
  reporter: process.env.CI ? 'list' : [['list'], ['html', { open: 'never' }]],
  timeout: 120_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: PREVIEW_URL,
    /**
     * D-7E-6 — **CI 에서는 trace 를 남기지 않는다**(v M-6).
     *
     * trace 는 네트워크 요청 본문과 쿠키를 담는다 — 강제 실패 실측에서 로그인 본문의 일회용
     * 비밀 문구 1건과 세션 쿠키 6건이 `trace.zip` 에 있었고, CI 는 실패 시 그 디렉터리를
     * 14일 아티팩트로 올린다. 값이 실행마다 난수이고 DB 가 버려지므로 위험은 낮지만, 계약
     * 문면은 「e2e trace 에 세션 쿠키 평문 0」이다.
     *
     * Playwright 에 「본문만 빼기」 옵션이 없으므로 **아티팩트에 들어가는 종류를 바꾼다**:
     * CI 는 **스크린샷만** 남긴다(비밀 문구는 password 입력이라 화면에 평문으로 그려지지
     * 않는다). 로컬은 trace 를 그대로 둔다 — 그 산출물은 올라가지 않고 디버깅에 필요하다.
     */
    trace: process.env.CI ? 'off' : 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'node e2e/harness/journey-up.mjs',
    url: PREVIEW_URL,
    reuseExistingServer: false,
    // 수집 러너 · 계정 명령 · 서버 기동(JVM 셋)과 postgres 이미지 pull 까지 든다.
    timeout: 300_000,
    stdout: 'pipe',
    stderr: 'pipe',
    // **기본값은 프로세스 그룹 SIGKILL 이다** — 그러면 하네스의 정리 핸들러가 돌 기회가 없고
    // postgres 컨테이너가 남는다(실측: 실행마다 한 개씩 세 개 남았다). SIGTERM 을 주고
    // 기다려야 하네스가 자식과 컨테이너를 거둔다.
    gracefulShutdown: { signal: 'SIGTERM', timeout: 20_000 },
    env: { BIDVECTOR_E2E_PREVIEW_PORT: String(PREVIEW_PORT) },
  },
});
