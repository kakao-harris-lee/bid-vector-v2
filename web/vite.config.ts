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
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
  build: { outDir: 'dist', emptyOutDir: true },
  test: {
    // 7A 의 단위 test 는 라우트 표처럼 DOM 이 필요 없는 것뿐이다 — jsdom 을 의존에 들이지
    // 않는다. 화면 렌더 test 는 7E 가 환경과 함께 들인다.
    environment: 'node',
    // 게이트 스크립트의 순수 판정 함수도 여기서 잠근다(`scripts/*.test.mjs`) — 그 판정이
    // 번들 경계의 실제 술어다.
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx', 'scripts/**/*.test.mjs'],
    // Playwright spec 을 vitest 가 수집하면 같은 test 가 두 레인에서 서로 다른 방식으로
    // 돌아간다.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
});
