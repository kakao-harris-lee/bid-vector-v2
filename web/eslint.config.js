import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// ADR 0011 D-2·D-3 — 서버를 부르는 자리는 생성 타입으로 타입이 매겨진 클라이언트
// (`src/api/client.ts`) 하나다. 아래 세 규칙은 그 「하나」를 지키는 2차 층이다(1차는
// tsconfig 의 rootDir). 린트 규칙은 우회하려면 이 설정 파일을 바꿔야 하는 층이라
// 주석·이름 목록과 달리 스타일 하나로 열리지 않는다.
const API_CALL_SITE = 'src/api/client.ts';

const restrictedMessage =
  '서버 호출은 src/api/client.ts 의 생성 타입 클라이언트(openapi-fetch)로만 한다 — ADR 0011 D-2.';

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'playwright-report/**',
      'test-results/**',
      // 생성물은 생성기가 정본이고 바이트 등식(api:types:check)이 지킨다 — 린트가 생성기의
      // 출력 양식에 간섭하면 두 정본이 생긴다.
      'src/api/generated/**',
    ],
  },
  js.configs.recommended,
  tseslint.configs.recommended,
  {
    files: ['src/**/*.ts', 'src/**/*.tsx'],
    languageOptions: { globals: globals.browser },
    rules: {
      // 실측(설계 검토 U-4): `// @ts-ignore` 와 `// @ts-nocheck` 는 rootDir 진단(TS6059)을
      // **억제한다** — 둘 중 하나를 얹으면 legacy 소스를 끌어온 채 typecheck 가 exit 0 이다.
      // `@ts-expect-error` 는 억제한 뒤 「쓰이지 않은 지시」로 다시 RED 가 되지만, 그 RED 는
      // 같은 줄에 다른 오류가 없다는 우연에 달려 있다. 그래서 세 형태를 **전부** 여기서 막고,
      // 기본값 상속에 맡기지 않는다(상위 preset 이 바뀌면 조용히 열린다).
      '@typescript-eslint/ban-ts-comment': [
        'error',
        {
          'ts-ignore': true,
          'ts-expect-error': true,
          'ts-nocheck': true,
          'ts-check': false,
        },
      ],
    },
  },
  {
    files: ['src/**/*.ts', 'src/**/*.tsx'],
    ignores: [API_CALL_SITE],
    rules: {
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: restrictedMessage },
        { name: 'XMLHttpRequest', message: restrictedMessage },
      ],
      // 전역 식별자 금지는 `fetch(...)` 만 잡는다 — `window.fetch` / `globalThis.fetch` 는
      // 같은 우회의 다른 표기라 같은 자리에서 닫는다.
      'no-restricted-properties': [
        'error',
        { object: 'window', property: 'fetch', message: restrictedMessage },
        { object: 'window', property: 'XMLHttpRequest', message: restrictedMessage },
        { object: 'globalThis', property: 'fetch', message: restrictedMessage },
        { object: 'globalThis', property: 'XMLHttpRequest', message: restrictedMessage },
        { object: 'self', property: 'fetch', message: restrictedMessage },
      ],
      'no-restricted-imports': [
        'error',
        {
          paths: [
            { name: 'axios', message: restrictedMessage },
            { name: 'openapi-fetch', message: restrictedMessage },
          ],
          patterns: [
            {
              group: ['axios/*', 'ky', 'superagent', 'node-fetch'],
              message: restrictedMessage,
            },
          ],
        },
      ],
    },
  },
  {
    // e2e 는 브라우저를 운전할 뿐 서버 모양을 지어내지 않는다 — 위 금지의 대상이 아니지만
    // 타입·문법 규칙은 같이 받는다.
    files: ['e2e/**/*.ts'],
    languageOptions: { globals: globals.node },
  },
  {
    // 저장소 뿌리의 설정 파일과 생성 check 스크립트 — Node 환경이다.
    files: ['*.js', '*.ts', 'scripts/**/*.mjs'],
    languageOptions: { globals: globals.node },
  },
);
