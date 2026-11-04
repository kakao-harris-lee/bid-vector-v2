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

/** 그 파일 경로로 린트한 뒤 **이 slice 의 규칙** 메시지만 센다. */
async function lintAs(filePath, code) {
  const [result] = await eslint.lintText(code, { filePath: resolve(webRoot, filePath) });
  return result.messages.filter((one) => one.ruleId === 'no-restricted-syntax');
}

function messagesMatching(messages, fragment) {
  return messages.filter((one) => one.message.includes(fragment));
}

const CLOCK_FRAGMENT = 'readClock()';
const RAW_INSTANT_FRAGMENT = 'instantDisplayOf()';
const NETWORK_FRAGMENT = 'openapi-fetch';

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

  /** 화면 파일 밖(세션·API 층)은 이 금지의 대상이 아니다. */
  it('화면·표시 밖 파일은 대상이 아니다', async () => {
    const messages = await lintAs('src/session/planted.ts', 'export const at = new Date();\n');

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

  it('표시 파일의 계산 접근도 여전히 RED 다', async () => {
    const messages = await lintAs('src/display/planted.ts', "export const r = globalThis['fetch'];\n");

    expect(messagesMatching(messages, NETWORK_FRAGMENT).length).toBeGreaterThanOrEqual(1);
  });
});
