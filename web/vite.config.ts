import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// vitest 설정을 같은 파일에 둔다 — 단위 test 가 앱과 같은 해석 규칙(플러그인·별칭)을 쓰게
// 하려는 것이고, 설정 파일이 둘이면 그 둘이 어긋날 자리가 생긴다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
  build: { outDir: 'dist', emptyOutDir: true },
  test: {
    // 7A 의 단위 test 는 라우트 표처럼 DOM 이 필요 없는 것뿐이다 — jsdom 을 의존에 들이지
    // 않는다. 화면 렌더 test 는 7E 가 환경과 함께 들인다.
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    // Playwright spec 을 vitest 가 수집하면 같은 test 가 두 레인에서 서로 다른 방식으로
    // 돌아간다.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
});
