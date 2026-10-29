import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// ADR 0011 D-3 — **번들 모듈 그래프**의 경계(verifier r1 H-1). tsc 의 프로그램과 Vite 의 그래프는
// 다른 집합이라, 이 플러그인이 없으면 `import.meta.glob`·`?raw`·`?worker`·`resolve.alias`·
// `index.html` 의 `<script src="../">` 로 web 밖 파일이 게이트 전부 초록인 채 번들에 들어간다.
// 이 import 와 아래 `plugins` 등재는 W-4(`scripts/check-src-tree.mjs`)가 AST 로 단언한다 —
// 조용히 떼어내면 그 게이트가 멈춘다.
import { moduleGraphBoundary } from './scripts/module-graph-boundary.mjs';

// vitest 설정을 같은 파일에 둔다 — 단위 test 가 앱과 같은 해석 규칙(플러그인·별칭)을 쓰게
// 하려는 것이고, 설정 파일이 둘이면 그 둘이 어긋날 자리가 생긴다.
export default defineConfig({
  plugins: [react(), tailwindcss(), moduleGraphBoundary({ webRoot: import.meta.dirname })],
  // **워커는 별도 Rollup 빌드다**(verifier r2 H-3). Vite 는 워커를 하위 빌드로 묶고 그 빌드는
  // `worker.plugins` 만 쓴다(기본 빈 목록) — 메인 그래프에는 `src/w.ts?worker_file…` 하나만
  // 보이므로 메인 쪽 플러그인은 그것을 `src` 안으로 본다. 그래서 화면 코드 두 줄(Vite 문서의
  // 표준 워커 관용구)로 web 밖 파일이 워커 chunk 에 실렸다. 같은 경계를 여기에도 등재한다 —
  // W-4 가 **두 자리 모두**를 단언하고, 활성 자체는 카나리 빌드(W-8)가 잰다.
  worker: { plugins: () => [moduleGraphBoundary({ webRoot: import.meta.dirname })] },
  // **host 를 리터럴 IP 로 고정한다**(PR #75 CI RED). Vite 의 기본 host 는 `localhost` 이고, 그
  // 이름이 `::1` 로 먼저 해석되는 장비에서는 서버가 **IPv6 에만** 뜬다. Playwright 의 webServer 는
  // `http://127.0.0.1:4173` 을 기다리므로 그 조합에서 60s 뒤 「Timed out waiting」으로 죽는다
  // (CI 의 `web` W-10 과 `check` S-20 이 같은 사유로 함께 붉었다). 리터럴 IP 는 이름 해석을 타지
  // 않으므로 해석 순서와 무관하게 같은 주소에 뜬다 — `playwright.config.ts` 의 `url`·`baseURL` 과
  // 같은 값이다.
  server: { host: '127.0.0.1', port: 5173, strictPort: true },
  // M7/7E-1 — 여정 e2e 하네스만 쓰는 두 값. **번들에 들어가지 않는다**(preview 서버 설정이고
  // `VITE_` 접두가 아니다 — ADR 0011 D-6). 값이 없으면 7A 때와 **같은 preview** 다: 포트
  // 4173, 프록시 없음. 그래서 production 코드에 e2e 분기가 0 이고 이 파일에만 입구가 있다.
  preview: {
    host: '127.0.0.1',
    port: Number.parseInt(process.env.BIDVECTOR_E2E_PREVIEW_PORT ?? '4173', 10),
    strictPort: true,
    // 화면과 API 를 **같은 출처**로 둔다 — 세션 쿠키(SameSite=Strict)와 CSRF 가 운영 형태
    // (7F 의 리버스 프록시)와 같은 모양으로 돈다. 교차 출처로 쿠키를 보내는 설정을 쓰지 않는다.
    proxy:
      process.env.BIDVECTOR_E2E_API_ORIGIN === undefined
        ? undefined
        : { '/api': { target: process.env.BIDVECTOR_E2E_API_ORIGIN, changeOrigin: false } },
  },
  build: { outDir: 'dist', emptyOutDir: true },
  test: {
    // M7/7D — **test 에서만** API 기준 주소를 절대 URL 로 둔다. 브라우저는 상대 주소(`/`)를
    // 문서 기준으로 풀지만 Node·jsdom 의 `Request` 는 **절대 URL 을 요구한다**(실측:
    // `ERR_INVALID_URL input: '/api/session'`). production 번들의 기본값은 그대로 상대 주소다
    // (`client.ts` 의 `VITE_API_BASE_URL ?? '/'`) — 이 값은 vitest 프로세스에만 실린다.
    env: { VITE_API_BASE_URL: 'http://localhost' },
    // 7A 의 단위 test 는 라우트 표처럼 DOM 이 필요 없는 것뿐이다 — jsdom 을 의존에 들이지
    // 않는다. 화면 렌더 test 는 7E 가 환경과 함께 들인다.
    environment: 'node',
    // 게이트 스크립트의 순수 판정 함수도 여기서 잠근다(`scripts/*.test.mjs`) — 그 판정이
    // 번들 경계의 실제 술어다.
    // 하네스의 **파괴적 경로 가드**도 단위 test 로 잠근다(CR r2 H-1) — 그 술어는 부수효과
    // 없는 모듈(`e2e/harness/container-marker.mjs`)에 있다.
    include: [
      'src/**/*.test.ts',
      'src/**/*.test.tsx',
      'scripts/**/*.test.mjs',
      'e2e/harness/**/*.test.mjs',
    ],
    // Playwright spec 을 vitest 가 수집하면 같은 test 가 두 레인에서 서로 다른 방식으로
    // 돌아간다. **spec 만** 제외한다 — 하네스의 단위 test 는 위 `include` 가 받는다.
    exclude: ['e2e/**/*.spec.ts', 'node_modules/**', 'dist/**'],
  },
});
