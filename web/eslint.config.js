import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// ADR 0011 D-2·D-3 — 서버 호출은 생성 타입으로 타입이 매겨진 클라이언트(`src/api/client.ts`)로
// 한다. **이 블록의 대상은 `src` 의 `.ts`·`.tsx` 뿐이다** — `index.html` 의 인라인 스크립트와
// `src` 밖 파일은 아예 린트 대상이 아니다(7A 검토 r5 M-10: 인라인 평문 `fetch` 는 우회 형태가
// 아니라 **대상 밖**이다). 그 범위는 checklist 의 알려진 제한에 등재돼 있다.
//
// **이 층이 무엇인지 정확히 적는다(verifier r1 M-1 · code-review r1 F-4).** 브라우저 전역의
// 네트워크 표면은 구조로 닫을 층이 마땅치 않다(타입 lib 에서 DOM 을 뺄 수 없다). 그래서 아래
// 규칙들은 **실수로 쓴 직접 호출을 잡는 보조 층**이고 **의도적 우회는 리뷰 소관**이다 —
// 계산으로 만든 이름(`globalThis['fe'+'tch']`)·별칭 변수(`const k = 'fetch'; window[k]`)·**반사
// API 일반**(`Reflect.get(window,'fetch')` · `Object.getOwnPropertyDescriptor(window,'fetch')` 등)·
// 구조 분해의 따옴표·계산 키는 막지 못한다(실측 · 알려진 제한에 등재 — verifier r7 L-15). 「스타일 하나로 열리지 않는다」는 주장을 이 자리에 쓰지
// 않는다 — 그 주장이 서는 자리는 아래 셋이고 전부 여기가 아니다: `openapi-fetch` import 집합
// 등식과 비리터럴 동적 import 금지(W-4, 컴파일러 API) · 번들에 들어오는 파일 경계(Vite 플러그인,
// 모듈 그래프) · 프로그램 파일 경계(W-4 전수).
//
// 멤버 표기는 **소유자를 열거하지 않는다** — `window.fetch` 를 적은 손이 `self.XMLHttpRequest` 를
// 빠뜨린 것이 열거의 성질을 보인 실측이라, 술어를 속성 이름 쪽으로 돌렸다(`no-restricted-syntax`).
// 그 대가는 **오탐**이다(code-review r2 G-6): 속성 이름이 같으면 네트워크와 무관한 멤버도 걸린다
// (실측 — `const cache = { fetch: (k) => k }; cache.fetch('x')` 가 lint 1). 그때는 그 줄에
// `eslint-disable-next-line` 을 달고 리뷰로 간다 — 규칙을 넓히지 않는다.
const API_CALL_SITE = 'src/api/client.ts';

// 네트워크 표면의 이름 — 아래 네 선택자가 이 목록의 **구성**으로 만들어진다(소유자는 열거하지
// 않는다).
const NETWORK_MEMBERS = ['fetch', 'XMLHttpRequest', 'sendBeacon', 'EventSource', 'WebSocket'];

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
  // 훅 규칙 — legacy 에는 lint 레인이 아예 없었다(조사 §1.1). 7A 의 골격에는 훅을 쓰는 코드가
  // 아직 없지만, 7E 가 컴포넌트를 쓰기 **전에** 규칙이 자리에 있어야 한다(팀장 결정, 이월 항목).
  //
  // `configs.flat[…]` 을 쓴다 — 같은 이름의 `configs['recommended-latest']` 는 **eslintrc 형**
  // (`plugins` 가 배열)이라 flat config 에서 설정 오류(exit 2)를 낸다. 실측으로 가린 자리다.
  reactHooks.configs.flat['recommended-latest'],
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
        { name: 'EventSource', message: restrictedMessage },
        { name: 'WebSocket', message: restrictedMessage },
      ],
      // 소유자 열거는 비대칭을 만든다(실측: `self.fetch` 는 있고 `self.XMLHttpRequest` 는 없었다).
      // 아래 두 블록은 **속성 이름**으로 잡는다 — 소유자가 무엇이든(`window`·`self`·`globalThis`·
      // `document.defaultView?.`·지역 별명 `const w = window`) 같은 술어에 걸린다.
      'no-restricted-syntax': [
        'error',
        // ① 점 접근 — 소유자와 무관하게 **속성 이름**으로 잡는다.
        ...NETWORK_MEMBERS.map((name) => ({
          selector: `MemberExpression[property.name='${name}']`,
          message: restrictedMessage,
        })),
        // ② 리터럴 계산 접근 — **소유자를 열거하지 않는다**(verifier r6 L-14). 앞선 판은
        //    소유자 × 이름이라 목록 밖 소유자(`globalThis.window['fetch']` ·
        //    `document.defaultView!['fetch']` · `top`·`parent`·`frames`)가 전부 초록이었다(실측).
        {
          selector: `MemberExpression[computed=true][property.value=/^(${NETWORK_MEMBERS.join('|')})$/]`,
          message: restrictedMessage,
        },
        // ③ 템플릿 리터럴 형태(`x[\`fetch\`]`) — 같은 접근의 다른 표기.
        {
          selector: `MemberExpression[computed=true] > TemplateLiteral > TemplateElement[value.raw=/^(${NETWORK_MEMBERS.join('|')})$/]`,
          message: restrictedMessage,
        },
        // ④ 구조 분해 — `const { fetch: f } = <무엇이든>`. 소유자를 보지 않는다. **키가 식별자일
        //    때만** 걸린다(verifier r7 L-15 실측) — 따옴표 키 `{ 'fetch': k }` · 계산 키
        //    `{ ['fetch']: h }` · 템플릿 키는 지나간다. 같은 리터럴의 다른 표기이고, 이 층의
        //    주장 범위(실수로 쓴 직접 호출)를 넘지 않으므로 알려진 제한에 등재한다.
        {
          selector: `ObjectPattern > Property[key.name=/^(${NETWORK_MEMBERS.join('|')})$/]`,
          message: restrictedMessage,
        },
      ],
      // `no-restricted-properties`(소유자 × 이름)는 **걷었다**(verifier r6 L-14) — 위 ②③④ 가 소유자를
      // 보지 않으므로 그 열거가 필요 없고, 열거는 목록 밖 소유자를 남겼다.
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
    // 하네스(`e2e/harness/*.mjs`)도 Node 다 — 여정 e2e 가 출하 jar·컨테이너를 띄우는 자리다.
    files: ['e2e/**/*.ts', 'e2e/**/*.mjs'],
    languageOptions: { globals: globals.node },
  },
  {
    // 저장소 뿌리의 설정 파일과 생성 check 스크립트 — Node 환경이다.
    files: ['*.js', '*.ts', 'scripts/**/*.mjs'],
    languageOptions: { globals: globals.node },
  },
);
