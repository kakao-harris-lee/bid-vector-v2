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

// 그 가운데 **전역 이름**으로도 서는 것들(`sendBeacon` 은 전역이 아니라 `navigator` 의 멤버다).
// 아래 블록 셋이 **같은 목록**을 쓴다 — `no-restricted-globals` 의 옵션도 뒤 블록이 덮어쓰므로
// (M9/web-followups 실측: 시계 블록이 이것을 덮어 화면 파일의 맨 `fetch('/x')` 가 초록이었다)
// 목록을 상수로 두고 싣는 자리마다 함께 싣는다. 그 사실을 게이트 test 가 잰다.
const NETWORK_GLOBALS = ['fetch', 'XMLHttpRequest', 'EventSource', 'WebSocket'];

const restrictedMessage =
  '서버 호출은 src/api/client.ts 의 생성 타입 클라이언트(openapi-fetch)로만 한다 — ADR 0011 D-2.';

// ── M9/screen-display D-1·D-4 ─────────────────────────────────────────────────
// 벽시계를 읽는 자리는 **한 파일**이다(`OPEN-7E-GATE-RESIDUAL` ⑨ 처방 — 7E-2 checklist §3.1).
// 그 잔여의 실측은 「지남/남음 잠금이 **문면 두 낱말**에 걸려 있다」였다: 카드에 브라우저 시계
// 판정을 더하면서 낱말만 바꾼 변이가 tsc 0 · vitest 전건 통과였다. 낱말을 더 열거하는 대신
// **시계 읽기 자체**를 막는다 — 열거가 아니라 구성이다.
const CLOCK_SITE = 'src/display/timeDisplay.ts';

const clockMessage =
  '벽시계는 src/display/timeDisplay.ts 의 readClock() 한 자리에서만 읽고 now 를 props 로 내린다 — M9 D-1.';

// **`Date` 전역 축을 막는다**(r1 v L-1). 앞 판의 술어는 식별자 이름 `Date` 에 걸린 호출·생성
// 꼴만 보았고, 그래서 `Date()` · `globalThis.Date.now()` · `const D = Date; D.now()` ·
// `Reflect.construct(Date, [])` 가 전부 초록이었다(verifier 실측). 값 참조를 막으면 **그 축의
// 우회가 닫힌다** — 타입 자리(`now: Date`)는 값 참조가 아니라 그대로 통과한다.
//
// **대상은 「`Date` 라는 이름」이 아니라 「지금을 낼 수 있는 표면」이다**(M9/web-followups —
// `OPEN-M9-CLOCK-LINT-SCOPE`). 앞 판의 범위는 `Date` 하나였고, 그래서 아래가 전부 초록이었다
// (실측): `new Intl.DateTimeFormat().format()` · 받아 온 서식기의 `f.format()` ·
// `Temporal.Now.instant()` · `performance.timeOrigin`. 그 표면들을 **목록의 구성**으로 받는다.
//
// **`Intl` 을 통째로 막지 않는다**: `Intl.NumberFormat(...).format(1200)` 은 시계가 아니라 수
// 서식이고, 그것까지 막으면 금액 서식이 이 금지에 걸려 다음 라운드가 규칙을 끈다. 그래서 막는
// 것은 `DateTimeFormat` 쪽과 **인자 없는** 서식 호출이다 — 인자가 없는 `format`·`formatToParts`
// 는 **지금**을 쓴다(그것이 이 축의 실제 위험이다).
//
// 이것이 가능해진 것은 `datetime-local` 변환을 `timeDisplay.localInputToIso` 로 옮겼기
// 때문이다 — 그 전에는 화면이 `new Date(값)` 을 **정당하게** 써서 금지를 좁힐 수 없었다.

// 지금을 낼 수 있는 표면의 **이름**. 소유자는 열거하지 않는다(네트워크 금지와 같은 관례).
const CLOCK_MEMBERS = ['Date', 'DateTimeFormat', 'Temporal', 'performance'];

// 그 가운데 **전역 이름**으로 서는 것 — `DateTimeFormat` 은 `Intl` 의 멤버라 전역이 아니다.
const CLOCK_GLOBALS = ['Date', 'Temporal', 'performance'];

const CLOCK_SELECTORS = [
  // ① 소유자를 지나 집어 가는 꼴 — `globalThis.Date` · `window.Date` · `Intl.DateTimeFormat` ·
  //    `globalThis.performance`. `no-restricted-globals` 는 **전역 이름 참조**만 보므로 이 꼴이
  //    그 밖에 있었다(실측). **속성 이름**을 본다(소유자를 열거하지 않는다).
  ...CLOCK_MEMBERS.map((name) => ({
    selector: `MemberExpression[property.name='${name}']`,
    message: clockMessage,
  })),
  // ② 리터럴 계산 접근 — `globalThis['performance']`.
  {
    selector: `MemberExpression[computed=true][property.value=/^(${CLOCK_MEMBERS.join('|')})$/]`,
    message: clockMessage,
  },
  // ③ 템플릿 표기 — 같은 접근의 다른 글자.
  {
    selector: `MemberExpression[computed=true] > TemplateLiteral > TemplateElement[value.raw=/^(${CLOCK_MEMBERS.join('|')})$/]`,
    message: clockMessage,
  },
  // ④ `Date.now` — 소유자가 `Date` 인 속성 이름으로 잡는다(호출이 아니라 **접근**을 막는다:
  //    `const n = Date.now; n()` 도 같은 읽기다). 위 ①②③ 은 `Date` 가 **속성**인 꼴만 보므로
  //    이 셋이 따로 필요하다.
  { selector: "MemberExpression[object.name='Date'][property.name='now']", message: clockMessage },
  { selector: "MemberExpression[computed=true][object.name='Date'][property.value='now']", message: clockMessage },
  {
    selector: "MemberExpression[computed=true][object.name='Date'] > TemplateLiteral > TemplateElement[value.raw='now']",
    message: clockMessage,
  },
  // ⑤ **인자 없는** `new Date()` 만 막는다 — `new Date(millis)` 는 시계 읽기가 아니라 변환이고,
  //    그것까지 막으면 서식 모듈이 자기 일을 못 한다.
  { selector: "NewExpression[callee.name='Date'][arguments.length=0]", message: clockMessage },
  // ⑥ **인자 없는 서식 호출** — `f.format()` · `f.formatToParts()` 는 지금을 쓴다. 서식기를
  //    props 로 받아 부르면 `Intl` 이라는 이름을 지나지 않으므로 ① 이 못 본다. 인자를 받은
  //    호출(`format(1200)` · `format(new Date(ms))`)은 **대상이 아니다**(음성 대조로 잰다).
  {
    selector: "CallExpression[arguments.length=0] > MemberExpression[property.name=/^(format|formatToParts)$/]",
    message: clockMessage,
  },
];

// 「화면 파일이 `*At` 필드를 직접 보간하는 자리 0」(D-4). 두 자리를 막는다 — **글자 자리**
// (`<time>{entry.occurredAt}</time>`)와 **속성 자리**(`at={view.observedAt}` · `dateTime={…}`).
// 그러면 모든 `*At` 가 `timeDisplay` 를 지나고, 기계 가독 칸도 **파싱을 통과한 값**만 받는다.
//
// **이 층이 무엇인지 적는다**: 직접 자식만 본다. `at={instantDisplayOf(view.observedAt)}` 는
// 지나야 하기 때문이다(그 호출이 이 slice 가 원하는 꼴이다). 그래서 **중간 변수로 비켜 가는
// 꼴**(`const at = item.occurredAt; <time>{at}</time>`)은 이 선택자가 막지 못한다 — 그 축을
// 지는 것은 렌더한 DOM 을 재는 게이트 test 이고, 여기는 **실수로 쓴 보간**을 잡는 보조 층이다.
const rawInstantMessage =
  '*At 원문을 화면에 보간하지 않는다 — src/display/timeDisplay.ts 의 instantDisplayOf() 를 지난다 (M9 D-2·D-4).';

const RAW_INSTANT_SELECTORS = [
  { selector: 'JSXElement > JSXExpressionContainer > MemberExpression[property.name=/At$/]', message: rawInstantMessage },
  { selector: 'JSXAttribute > JSXExpressionContainer > MemberExpression[property.name=/At$/]', message: rawInstantMessage },
  // ③ 템플릿 보간 — 앞 둘이 **직접 자식만** 보므로 `value={`${notice.deadlineAt} (D-3)`}` 가
  //    그대로 지나갔다(이 slice 의 실측: 워크시트의 마감 칸이 바로 그 꼴이었다). 템플릿은
  //    보간한 값을 **글자로** 만드는 자리라 예외가 없다 — 그래서 자리 깊이를 보지 않는다.
  //
  //    **자손을 보는 유일한 선택자라 오탐이 있다**(r1 cr LOW): `At` 로 끝나는 문자열 메서드
  //    (`charAt`·`codePointAt`)가 시각과 무관하게 걸린다. 그 둘만 음성 조건으로 뺀다 — 열거를
  //    늘리는 대신, 걸리는 이름이 더 나오면 **변수로 빼는 쪽**이 맞다(`eslint-disable` 을 달면
  //    그 줄의 진짜 시각 보간까지 함께 덮인다). 이 범위는 게이트 test 가 음성 대조로 잰다.
  {
    selector: 'JSXExpressionContainer TemplateLiteral MemberExpression[property.name=/(?<!char|codePoint)At$/]',
    message: rawInstantMessage,
  },
];

// 네트워크 금지의 네 선택자 — 아래 두 블록이 **같은 목록**을 쓴다. flat config 의 규칙 옵션은
// 뒤 블록이 **덮어쓴다**(이어 붙이지 않는다). 그래서 화면·표시 전용 블록이 자기 선택자만
// 적으면 그 파일들에서 네트워크 금지가 조용히 사라진다 — 목록을 상수로 두고 둘 다 싣는다.
// 그 사실 자체를 게이트 test 가 잰다(「화면 파일에서 `window.fetch` 가 여전히 1」).
const NETWORK_SELECTORS = [
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
];

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
      'no-restricted-globals': ['error', ...NETWORK_GLOBALS.map((name) => ({ name, message: restrictedMessage }))],
      // 소유자 열거는 비대칭을 만든다(실측: `self.fetch` 는 있고 `self.XMLHttpRequest` 는 없었다).
      // 아래 두 블록은 **속성 이름**으로 잡는다 — 소유자가 무엇이든(`window`·`self`·`globalThis`·
      // `document.defaultView?.`·지역 별명 `const w = window`) 같은 술어에 걸린다.
      'no-restricted-syntax': ['error', ...NETWORK_SELECTORS],
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
    // M9/screen-display D-1·D-4 — 화면과 표시 모듈의 두 금지. **네트워크 선택자를 함께 싣는다**
    // (위 블록을 덮어쓰기 때문이다 — 그 사실은 게이트 test 가 잰다).
    //
    // `ignores` 가 시계 읽기의 **승인된 한 자리**를 뺀다. 그 파일은 위 블록으로 돌아가므로
    // 네트워크 금지는 그대로 받는다.
    // **화면이 그리는 코드 전부**가 대상이다(r1 cr M-2). 앞 판은 `src/screens`·`src/display` 두
    // 디렉터리뿐이었는데, ⑨ 의 잔여가 말하는 「카드에 브라우저 시계 판정을 더하는」 배지가 살
    // 가장 자연스러운 자리는 `src/components` 다 — 그 디렉터리가 금지 밖이었다.
    //
    // 제외는 **의도를 문면으로 남긴다**: `CLOCK_SITE` 는 승인된 한 자리이고, `API_CALL_SITE` 는
    // **네트워크 금지만** 벗는 자리라 바로 아래 블록이 시계 금지를 따로 싣는다.
    //
    // **`src/api/**`·`src/session/**` 는 더 이상 제외가 아니다**(M9/web-followups —
    // `OPEN-M9-CLOCK-LINT-SCOPE`). 앞 판은 그 둘이 시계 금지 밖이었고(실측 초록), 그러면 그
    // 디렉터리에 `export const now = new Date()` 를 두고 화면이 import 하는 길이 승인된 한
    // 자리를 비켜 간다 — 「제외 디렉터리 경유」가 그 축이다.
    files: ['src/**/*.ts', 'src/**/*.tsx'],
    // **test 는 대상이 아니다**: 고정 시각 fixture(`new Date('2026-…Z')`)를 짓는 것이 그 일이고,
    // 그 코드는 출하되지 않는다. ⑨ 의 잔여가 말하는 위험은 **카드가 브라우저 시계로 판정하는**
    // 것이므로 출하 경로만 닫으면 된다. 이 제외가 의도임을 게이트 test 가 잰다.
    ignores: [CLOCK_SITE, API_CALL_SITE, 'src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: {
      'no-restricted-syntax': ['error', ...NETWORK_SELECTORS, ...CLOCK_SELECTORS, ...RAW_INSTANT_SELECTORS],
      // **두 목록을 함께 싣는다** — 이 옵션도 앞 블록을 덮어쓰므로(실측: 앞 판은 네트워크 전역이
      // 여기서 사라져 화면 파일의 맨 `fetch('/x')` 가 초록이었다) 시계만 적으면 안 된다.
      'no-restricted-globals': [
        'error',
        ...NETWORK_GLOBALS.map((name) => ({ name, message: restrictedMessage })),
        ...CLOCK_GLOBALS.map((name) => ({ name, message: clockMessage })),
      ],
    },
  },
  {
    // **승인된 네트워크 자리도 시계는 못 읽는다**(`OPEN-M9-CLOCK-LINT-SCOPE`). 두 제외는 다른
    // 축이다 — 그 파일이 벗는 것은 네트워크 금지이고, 시계 금지를 함께 벗을 이유가 없다. 블록을
    // 따로 두는 이유는 flat config 가 규칙 **옵션을 덮어쓰기** 때문이다(한 블록에 둘을 섞으면
    // 그 파일의 `openapi-fetch` 가 자기 금지에 걸린다).
    files: [API_CALL_SITE],
    rules: {
      'no-restricted-syntax': ['error', ...CLOCK_SELECTORS, ...RAW_INSTANT_SELECTORS],
      'no-restricted-globals': ['error', ...CLOCK_GLOBALS.map((name) => ({ name, message: clockMessage }))],
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
