import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

/**
 * **게이트 술어 자신의 측정**(M9/screen-display D-4). lint 규칙은 「돌았다」만으로는 아무것도
 * 보증하지 않는다 — 심은 위반이 **실제로 RED 인지**, 그리고 지나야 할 꼴이 **초록인지**(음성
 * 대조) 둘을 같은 자리에서 잰다.
 *
 * 실제 `eslint.config.js` 를 쓴다(사본이 아니다) — 설정이 바뀌면 이 test 가 함께 움직인다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const eslint = new ESLint({ cwd: webRoot });

/**
 * 실제로 린트가 **몇 번** 돌았는가. 아래 전제 칸의 입력이고, **상수로 바꿔치울 수 없다는 것**이
 * 이 값의 요점이다(r1 v R3) — 앞 판의 전제는 불리언이라 `const warmedUp = true` 한 줄로 참이
 * 되었고, 그러면 예열을 hook 으로 옮겨도 초록이었다.
 */
let lintCalls = 0;

/** 그 파일 경로로 린트한 뒤 **이 slice 의 규칙** 메시지만 센다. */
async function lintAs(filePath, code) {
  const [result] = await eslint.lintText(code, { filePath: resolve(webRoot, filePath) });
  // **센 뒤가 아니라 끝난 뒤에 올린다**(r1 v N-R3). 부르는 순간 올리면 이 수는 「린트를 **걸었다**」
  // 를 세고, 그러면 예열을 기다리지 않는 꼴(`await` 를 `void` 로)이 적재 단계에서 이미 1 이라
  // 전제 칸을 지난다 — r1 의 불리언 판이 잡던 축이 거기서 후퇴한다. 이 자리에서 올리면 수가
  // 「린트가 **끝났다**」를 세고, 기다리지 않은 예열은 0 으로 남는다.
  lintCalls += 1;
  return result.messages.filter((one) => one.ruleId === 'no-restricted-syntax');
}

function messagesMatching(messages, fragment) {
  return messages.filter((one) => one.message.includes(fragment));
}

/**
 * **콜드 기동을 어느 예산에도 싣지 않는다**(M11/11E 서명 A).
 *
 * `new ESLint(…)` 는 아무것도 읽지 않는다 — flat config 해석과 파서·플러그인 적재(`typescript`
 * 모듈까지)는 **첫 `lintText`** 에서 일어난다. 앞 판에서는 그 비용이 **첫 `it` 하나의 5000ms
 * 예산 안**에 있었고, 부하가 그 예산을 먹는 날 이 게이트가 자기 주장과 무관한 이유로 붉어졌다
 * (베이스라인 52회 중 5회, 전부 같은 칸).
 *
 * **예산을 늘리는 것은 고치는 것이 아니다 — 실측으로 확인했다.** 중간 판에서 이 예열을
 * `beforeAll` 로 옮겼더니(hook 예산 10000ms) 서명 A 는 0/52 가 됐지만 **같은 결함이 hook
 * 예산에서 1/52 로 다시 났다**(`Hook timed out in 10000ms`, 그 파일의 35 칸이 건너뛰어짐).
 * 예산을 두는 모든 자리가 부하 앞에서 추첨이다.
 *
 * 그래서 비용을 **모듈 적재 단계**로 옮긴다. 거기에는 칸·hook 예산이 없고(상한은 CI job 의
 * `timeout-minutes` 라는 올바른 층이다), 무엇보다 **예열은 측정이 아니다** — 측정에 예산을 더
 * 주는 것과 측정 아닌 준비를 예산 밖으로 내보내는 것은 다른 일이다.
 */
await (async () => {
  // 경로를 첫 칸과 같은 꼴로 둔 것은 **값싼 조심**이지 이 수정이 기대는 축이 아니다(r1 cr F-6).
  // 실측: 같은 프로세스에서 예열 경로를 바꿔 첫 린트를 재면 `src/screens/*.tsx` 10ms ·
  // `src/api/*.ts` 9ms · `e2e/*.ts` 21ms 다 — 5000ms 예산에 무해하다. 비싼 것은 **프로세스 단위의
  // 모듈 적재**이고(파서·플러그인·`typescript`) 그것은 어느 경로로 예열하든 한 번만 치른다.
  await lintAs('src/screens/__warmup__.tsx', 'export const warm = 1;\n');
})();

/**
 * **이 줄을 `lintCalls` 로 인라인하지 말 것** — 그러면 예열을 hook 으로 옮기는 변이가 다시
 * 초록이 된다(r1 cr N-3). 값의 뜻은 「**모듈 평가가 끝나는 자리**에서 린트가 몇 번 돌았는가」
 * 이고, 그 시점이 이 줄의 자리다.
 *
 * 이 값이 수인 것이 앞 판과의 차이다(r1 v R3) — 불리언은 `true` 한 줄로 바꿔치울 수 있었지만
 * 이 수는 **실제 린트만** 올린다.
 *
 * suite 가 잴 수 없는 나머지 절반(「예산이 **없다**」)은 예열에 지연을 심는 대조로만 선다. 그
 * 대조는 15s 를 쓰므로 suite 에 두지 않고 evidence 에 둔다.
 */
const lintCallsDuringModuleEvaluation = lintCalls;

const CLOCK_FRAGMENT = 'readClock()';
const RAW_INSTANT_FRAGMENT = 'instantDisplayOf()';
const NETWORK_FRAGMENT = 'openapi-fetch';

describe('이 파일의 전제 (M11/11E 서명 A)', () => {
  /**
   * 예열을 지우는 변이는 **다음 실행 52번 가운데 다섯 번**만 보인다 — 그런 결함은 단언이 없으면
   * 조용히 되돌아온다. 그래서 「칸이 돌기 전에 린터가 이미 더워져 있다」를 결과로 고정한다.
   */
  it('ESLint 콜드 기동은 **적재 단계에서** 끝나 있다', () => {
    expect(
      lintCallsDuringModuleEvaluation,
      '적재가 끝날 때 린트가 0 회였다 — 콜드 기동이 칸이나 hook 예산 안에 있다',
    ).toBeGreaterThan(0);
  });
});

describe('벽시계 금지는 화면·표시 파일에서 선다 (D-1)', () => {
  it('화면 파일의 `new Date()` 는 RED 다', async () => {
    const messages = await lintAs('src/screens/Planted.tsx', 'export const at = new Date();\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  it('표시 파일의 `Date.now()` 도 RED 다', async () => {
    const messages = await lintAs('src/display/planted.ts', 'export const at = Date.now();\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  /** 호출하지 않고 **집어 가는** 꼴도 같은 읽기다. */
  it('`Date.now` 를 변수로 집어 가는 꼴도 RED 다', async () => {
    const messages = await lintAs('src/screens/Planted.tsx', 'export const read = Date.now;\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  it('계산 접근과 템플릿 접근도 RED 다', async () => {
    const computed = await lintAs('src/screens/Planted.tsx', "export const a = Date['now']();\n");
    const template = await lintAs('src/screens/Planted.tsx', 'export const b = Date[`now`]();\n');

    expect(messagesMatching(computed, CLOCK_FRAGMENT).length).toBe(1);
    expect(messagesMatching(template, CLOCK_FRAGMENT).length).toBe(1);
  });

  /**
   * **음성 대조** — 인자를 받은 `new Date(…)` 는 시계 읽기가 아니라 변환이다. 이것까지 막으면
   * 서식 모듈이 자기 일을 못 하고, 규칙을 끄는 주석이 늘어난다.
   */
  it('인자를 받은 `new Date(…)` 는 초록이다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      "export const a = new Date(0);\nexport const b = new Date('2026-11-05T00:00:00Z');\n",
    );

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(0);
  });

  /** 승인된 **한 자리**는 예외다 — 그 사실도 측정한다(예외가 사라지면 그 파일이 붉어진다). */
  it('승인된 자리에서는 초록이다', async () => {
    const messages = await lintAs('src/display/timeDisplay.ts', 'export const at = new Date();\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(0);
  });

  /**
   * **공용 컴포넌트도 대상이다**(r1 cr M-2). ⑨ 의 원래 잔여는 「카드에 브라우저 시계 판정을
   * 더하면서 낱말만 바꾼 변이」였고, 그 배지가 살 가장 자연스러운 자리가 `src/components` 다 —
   * 앞 판의 `files` 는 그 디렉터리를 담지 않았다.
   */
  it('공용 컴포넌트의 시계 읽기도 RED 다', async () => {
    const messages = await lintAs('src/components/Planted.tsx', 'export const at = new Date();\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  /** 화면 뿌리의 파일(`src/*.tsx` — 셸·라우트)도 같은 자리다. */
  it('화면 뿌리 파일의 시계 읽기도 RED 다', async () => {
    const messages = await lintAs('src/Planted.tsx', 'export const at = Date.now();\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  /**
   * **`Date` 전역 값 참조 자체가 막힌다**(r1 v L-1). 앞 판은 술어가 식별자 이름에 걸린 호출·
   * 생성 꼴만 보아 아래 전부가 초록이었다(verifier 실측). 값 참조를 막으면 한 술어로 닫힌다.
   */
  it('식별자 이름을 비켜 가던 꼴 전부가 RED 다', async () => {
    const probes = [
      'export const a = Date();',
      'export const b = globalThis.Date.now();',
      'export const c = new globalThis.Date();',
      'export const d = (() => { const D = Date; return D.now(); })();',
      'export const e = (() => { const { now } = Date; return now; })();',
      'export const f = Reflect.construct(Date, []);',
      'export const g = new Date(...[]);',
    ];
    for (const probe of probes) {
      const [result] = await eslint.lintText(`${probe}\n`, {
        filePath: resolve(webRoot, 'src/screens/Planted.tsx'),
      });
      const hits = result.messages.filter(
        (one) => one.ruleId === 'no-restricted-globals' || one.ruleId === 'no-restricted-syntax',
      );
      expect(hits.length, `${probe} 가 초록이다`).toBeGreaterThanOrEqual(1);
    }
  });

  /** **타입 자리는 값 참조가 아니다** — `now: Date` 를 막으면 화면이 시각을 받지 못한다. */
  it('타입 자리의 `Date` 는 초록이다', async () => {
    const [result] = await eslint.lintText('export function f(now: Date): number { return now.getTime(); }\n', {
      filePath: resolve(webRoot, 'src/screens/Planted.tsx'),
    });

    expect(result.messages.filter((one) => one.ruleId === 'no-restricted-globals').length).toBe(0);
  });

  /** test 는 고정 시각 fixture 를 짓는다 — 출하되지 않으므로 대상이 아니다(제외가 의도다). */
  it('test 파일은 대상이 아니다', async () => {
    const [result] = await eslint.lintText("export const at = new Date('2026-11-05T00:00:00Z');\n", {
      filePath: resolve(webRoot, 'src/screens/Planted.test.tsx'),
    });

    expect(result.messages.filter((one) => one.ruleId === 'no-restricted-globals').length).toBe(0);
  });

  /**
   * **제외 디렉터리를 지나 들어오는 값**을 닫았다(M9/web-followups — `OPEN-M9-CLOCK-LINT-SCOPE`).
   * 앞 판은 `src/api/**`·`src/session/**` 가 이 금지 **밖**이었다(실측 초록) — 그 디렉터리에
   * `export const now = new Date()` 를 두고 화면이 import 하면 승인된 한 자리를 비켜 간다.
   */
  it('API·세션 층의 시계 읽기도 RED 다', async () => {
    const session = await lintAs('src/session/planted.ts', 'export const at = new Date();\n');
    const api = await lintAs('src/api/planted.ts', 'export const at = new Date();\n');

    expect(messagesMatching(session, CLOCK_FRAGMENT).length).toBe(1);
    expect(messagesMatching(api, CLOCK_FRAGMENT).length).toBe(1);
  });

  /**
   * 승인된 **네트워크** 자리도 시계는 못 읽는다. 그 파일은 네트워크 금지에서 빠지지만 시계
   * 금지에서는 빠지지 않는다 — 두 제외가 **다른 축**임을 여기서 잰다.
   */
  it('승인된 네트워크 자리에서도 시계 읽기는 RED 다', async () => {
    const messages = await lintAs('src/api/client.ts', 'export const at = Date.now();\n');

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  it('승인된 네트워크 자리의 openapi-fetch 는 여전히 초록이다', async () => {
    const [result] = await eslint.lintText("import createClient from 'openapi-fetch';\nexport const c = createClient;\n", {
      filePath: resolve(webRoot, 'src/api/client.ts'),
    });

    expect(result.messages.filter((one) => one.ruleId === 'no-restricted-imports').length).toBe(0);
  });
});

/**
 * **`Date` 라는 이름 밖의 시계**(`OPEN-M9-CLOCK-LINT-SCOPE`). 앞 판의 주석은 그 범위를 정직하게
 * 적고 있었고, 이 라운드가 그 바깥 축을 닫는다. 아래 전부가 **앞 판에서 초록이었다**(실측).
 */
describe('지금을 낼 수 있는 다른 표면도 막는다 (OPEN-M9-CLOCK-LINT-SCOPE)', () => {
  it('인자 없는 `Intl.DateTimeFormat().format()` 은 RED 다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      "export const a = new Intl.DateTimeFormat('ko-KR').format();\n",
    );

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
  });

  /**
   * **서식기를 받아서 부르는 꼴** — `Intl` 이라는 이름을 지나지 않는다. 인자 없는 `format` 은
   * 지금을 쓰므로 그 호출 꼴 자체가 술어다.
   */
  it('받아 온 서식기의 인자 없는 `format()`·`formatToParts()` 도 RED 다', async () => {
    const format = await lintAs('src/screens/Planted.tsx', 'export const a = (f) => f.format();\n');
    const parts = await lintAs('src/display/planted.ts', 'export const a = (f) => f.formatToParts();\n');

    expect(messagesMatching(format, CLOCK_FRAGMENT).length).toBe(1);
    expect(messagesMatching(parts, CLOCK_FRAGMENT).length).toBe(1);
  });

  it('`Temporal.Now` 는 RED 다', async () => {
    const [result] = await eslint.lintText('export const a = Temporal.Now.instant();\n', {
      filePath: resolve(webRoot, 'src/screens/Planted.tsx'),
    });
    const hits = result.messages.filter(
      (one) => one.ruleId === 'no-restricted-globals' || one.ruleId === 'no-restricted-syntax',
    );

    expect(hits.length).toBeGreaterThanOrEqual(1);
  });

  it('`performance.now()` · `performance.timeOrigin` 은 RED 다', async () => {
    for (const probe of ['export const a = performance.now();', 'export const b = performance.timeOrigin;']) {
      const [result] = await eslint.lintText(`${probe}\n`, {
        filePath: resolve(webRoot, 'src/screens/Planted.tsx'),
      });
      const hits = result.messages.filter(
        (one) => one.ruleId === 'no-restricted-globals' || one.ruleId === 'no-restricted-syntax',
      );

      expect(hits.length, `${probe} 가 초록이다`).toBeGreaterThanOrEqual(1);
    }
  });

  /** 소유자를 지나 집어 가는 꼴도 같은 읽기다 — 소유자를 열거하지 않는다. */
  it('소유자를 지나 집어 가는 꼴도 RED 다', async () => {
    const computed = await lintAs('src/screens/Planted.tsx', "export const a = globalThis['performance'];\n");
    const template = await lintAs('src/screens/Planted.tsx', 'export const b = globalThis[`Temporal`];\n');

    expect(messagesMatching(computed, CLOCK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
    expect(messagesMatching(template, CLOCK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
  });

  /**
   * **음성 대조** — 수 서식은 시계가 아니다. `Intl` 전역을 통째로 막으면 금액 서식이 이 금지에
   * 걸리고, 그러면 다음 라운드가 규칙을 끈다. 그래서 막는 것은 `DateTimeFormat` 쪽과 **인자 없는**
   * 서식 호출이다.
   */
  it('인자를 받은 수 서식은 초록이다', async () => {
    const messages = await lintAs(
      'src/display/planted.ts',
      "export const a = new Intl.NumberFormat('ko-KR').format(1200);\n",
    );

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(0);
  });

  /**
   * **구조 분해 축**(PR #132 v M-1 = cr M-1). 네트워크 금지는 이 축을 선택자 ④ 로 닫아 두었는데
   * 시계 쪽에는 짝이 없었다 — 구조 분해는 `MemberExpression` 이 아니라 ①②③ 이 못 보고, 지역
   * 이름은 전역 참조가 아니라 `no-restricted-globals` 도 못 보며, `new D()` 는 `callee.name` 이
   * `Date` 가 아니라 ⑤ 도 비켜 간다(리뷰 실측: 셋 다 초록이었다).
   */
  it('구조 분해로 집어 가는 꼴도 RED 다', async () => {
    const date = await lintAs(
      'src/screens/Planted.tsx',
      'const { Date: D } = globalThis;\nexport const n = D.now();\nexport const m = new D();\n',
    );
    const formatter = await lintAs(
      'src/screens/Planted.tsx',
      "const { DateTimeFormat: F } = Intl;\nexport const s = new F('ko').format(undefined);\n",
    );

    expect(messagesMatching(date, CLOCK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
    expect(messagesMatching(formatter, CLOCK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
  });

  /**
   * **인자가 있어도 비어 있으면 지금을 쓴다**(PR #132 v M-2 = cr M-2). `format(undefined)` 은
   * 인자 **개수**가 1 이라 앞 판의 「인자 없는 호출」 선택자를 지나갔는데, 그 호출은 지금을
   * 서식한다. spread 로 빈 배열을 펴는 꼴도 같다.
   */
  it('`format(undefined)` · `formatToParts(...xs)` 도 RED 다', async () => {
    const explicit = await lintAs('src/screens/Planted.tsx', 'export const a = (f) => f.format(undefined);\n');
    const spread = await lintAs('src/display/planted.ts', 'export const a = (f, xs) => f.formatToParts(...xs);\n');

    expect(messagesMatching(explicit, CLOCK_FRAGMENT).length).toBe(1);
    expect(messagesMatching(spread, CLOCK_FRAGMENT).length).toBe(1);
  });

  /**
   * **`DateTimeFormat` 선택자를 홀로 잠근다**(PR #132 v L-3). 앞 판에서는 그 이름을 목록에서
   * 빼도 대조 서른하나가 전부 초록이었다 — 인자 없는 서식 호출 선택자가 대신 잡았기 때문이다.
   * 인자를 **받는** 서식 호출은 그 선택자 밖이라 이 꼴은 `DateTimeFormat` 이름으로만 걸린다.
   */
  it('인자를 받는 `Intl.DateTimeFormat(…).format(d)` 도 RED 다 — 이름 선택자 단독', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      "export const a = (d) => new Intl.DateTimeFormat('ko').format(d);\n",
    );

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(1);
  });

  /** test 는 여전히 대상이 아니다 — 고정 fixture 를 짓는 자리다(제외가 의도다). */
  it('test 파일은 새 축에서도 대상이 아니다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.test.tsx',
      "export const a = new Intl.DateTimeFormat('ko-KR').format();\n",
    );

    expect(messagesMatching(messages, CLOCK_FRAGMENT).length).toBe(0);
  });
});

describe('`*At` 보간 금지는 세 자리에서 선다 (D-2·D-4)', () => {
  it('글자 자리의 보간은 RED 다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      'export const E = (e) => <time>{e.occurredAt}</time>;\n',
    );

    expect(messagesMatching(messages, RAW_INSTANT_FRAGMENT).length).toBe(1);
  });

  it('속성 자리의 보간은 RED 다 — 기계 가독 칸도 서식을 지난 값만 받는다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      'export const E = (e) => <time dateTime={e.occurredAt} />;\n',
    );

    expect(messagesMatching(messages, RAW_INSTANT_FRAGMENT).length).toBe(1);
  });

  /**
   * **이 selector 는 이 slice 의 실측으로 더했다**: 앞 두 선택자가 직접 자식만 보아
   * `value={`${notice.deadlineAt} (D-3)`}` 꼴이 그대로 지나갔다(워크시트의 마감 칸).
   */
  it('템플릿 보간도 RED 다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      'export const E = (n) => <p title={`${n.deadlineAt} (D-3)`} />;\n',
    );

    expect(messagesMatching(messages, RAW_INSTANT_FRAGMENT).length).toBe(1);
  });

  /**
   * **음성 대조** — 서식을 지나는 꼴은 초록이어야 한다. 이것이 붉으면 규칙이 산출물을 막으므로
   * 다음 라운드가 규칙을 끈다.
   */
  /**
   * **오탐의 범위를 잰다**(r1 cr LOW). 셋째 선택자만 자손을 보므로 `charAt`·`codePointAt` 처럼
   * `At` 로 끝나는 **문자열 메서드**가 시각과 무관하게 걸린다. 음성 조건으로 그 둘을 뺐고, 그
   * 사실을 측정으로 남긴다 — 다음 라운드가 `eslint-disable` 로 풀면 그 줄이 진짜 시각 보간까지
   * 덮는다.
   */
  it('문자열 메서드 `charAt`·`codePointAt` 은 초록이다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      'export const E = (s) => <p title={`${s.charAt(0)}${s.codePointAt(1)}`} />;\n',
    );

    expect(messagesMatching(messages, RAW_INSTANT_FRAGMENT).length).toBe(0);
  });

  it('서식을 지나는 호출은 초록이다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      'export const E = (e) => <I display={instantDisplayOf(e.occurredAt)} />;\n',
    );

    expect(messagesMatching(messages, RAW_INSTANT_FRAGMENT).length).toBe(0);
  });

  /**
   * **알려진 제한의 측정** — 중간 변수로 비켜 가는 꼴은 이 층이 막지 못한다. 그 축을 지는 것은
   * 렌더한 DOM 을 재는 게이트 test(`screenVocabulary.test.tsx`)다. 제한을 글로 적는 대신
   * **초록인 것을 잰다** — 다음 라운드가 「막힌다」고 착각하지 않게.
   */
  it('중간 변수로 비켜 가는 꼴은 이 층 밖이다 — DOM 게이트가 받는다', async () => {
    const messages = await lintAs(
      'src/screens/Planted.tsx',
      'export const E = (e) => { const at = e.occurredAt; return <time>{at}</time>; };\n',
    );

    expect(messagesMatching(messages, RAW_INSTANT_FRAGMENT).length).toBe(0);
  });
});

/**
 * **flat config 의 덮어쓰기 함정**(이 slice 가 밟을 수 있었던 자리). 규칙 옵션은 뒤 블록이
 * 덮어쓴다 — 화면·표시 전용 블록이 자기 선택자만 적으면 그 파일들에서 ADR 0011 D-2 의
 * 네트워크 금지가 **조용히 사라진다**. 그 사실을 글이 아니라 측정으로 잠근다.
 */
describe('새 블록이 네트워크 금지를 덮어쓰지 않는다 (ADR 0011 D-2)', () => {
  it('화면 파일의 `window.fetch` 는 여전히 RED 다', async () => {
    const messages = await lintAs('src/screens/Planted.tsx', "export const r = window.fetch('/x');\n");

    expect(messagesMatching(messages, NETWORK_FRAGMENT).length).toBe(1);
  });

  /**
   * **같은 함정의 전역 축**(M9/web-followups 실측). 앞 판은 `no-restricted-syntax` 만 두 목록을
   * 합쳤고 `no-restricted-globals` 는 시계 블록이 **덮어썼다** — 그래서 화면 파일의 **맨
   * `fetch('/x')`** 가 초록이었다(선택자들은 전부 멤버·구조분해 꼴이라 맨 이름을 보지 못한다).
   */
  it('화면 파일의 맨 `fetch(…)` 도 RED 다 — 전역 목록이 덮이지 않는다', async () => {
    const [result] = await eslint.lintText("export const r = fetch('/x');\n", {
      filePath: resolve(webRoot, 'src/screens/Planted.tsx'),
    });
    const hits = result.messages.filter((one) => one.ruleId === 'no-restricted-globals');

    expect(hits.length).toBe(1);
    expect(hits[0].message).toContain(NETWORK_FRAGMENT);
  });

  it('표시 파일의 맨 `WebSocket` 도 RED 다', async () => {
    const [result] = await eslint.lintText("export const s = new WebSocket('wss://x');\n", {
      filePath: resolve(webRoot, 'src/display/planted.ts'),
    });

    expect(result.messages.filter((one) => one.ruleId === 'no-restricted-globals').length).toBe(1);
  });

  it('표시 파일의 계산 접근도 여전히 RED 다', async () => {
    const messages = await lintAs('src/display/planted.ts', "export const r = globalThis['fetch'];\n");

    expect(messagesMatching(messages, NETWORK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
  });
});
