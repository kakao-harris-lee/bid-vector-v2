import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

/**
 * **표 선언에 타입 단언을 직접 붙이지 못한다**(M11/gates-web-read — 받는 항목 7).
 *
 * 등재된 사실: 화면 어휘의 표들은 `{…} as const satisfies Record<계약 enum, …>` 꼴이고, 그
 * `satisfies` 가 **전수성을 컴파일에서** 든다 — 계약에 값이 늘면 그 자리가 멈춘다. 그런데 같은 표를
 * `{…} as Record<계약 enum, …>` 로 바꾸면 단언이 **선언 타입만** 보게 되어 누락이 조용해진다
 * (PR #149 verifier r2 M-2). 규칙 하나로 그 바꿔치우기를 막는다.
 *
 * **대조는 실제 `eslint.config.js` 로 돈다**(사본이 아니다) — 설정이 바뀌면 이 test 가 함께 움직인다.
 * 그리고 flat config 는 **뒤 블록이 앞 블록의 같은 규칙 옵션을 덮어쓴다**(이 호스트의 등재된 함정).
 * 새 블록이 기존 금지를 지웠는지를 글이 아니라 **측정**으로 잠근다 — 아래 마지막 묶음이 그 자리다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const eslint = new ESLint({ cwd: webRoot });

const RULE = '@typescript-eslint/consistent-type-assertions';

/** 그 파일 경로로 린트한 뒤 **이 항목의 규칙** 메시지만 센다. */
async function assertionMessages(filePath, code) {
  const [result] = await eslint.lintText(code, { filePath: resolve(webRoot, filePath) });
  return result.messages.filter((one) => one.ruleId === RULE);
}

const TABLE = 'type Tone = "A" | "B";\ntype Row = Record<Tone, string>;\n';

describe('표 선언의 타입 단언은 RED 다 (항목 7)', () => {
  /** 어휘 표가 사는 자리 전부에서 선다 — 한 디렉터리만 보면 표를 옆으로 옮기는 길이 남는다. */
  it('객체 리터럴 단언은 표가 사는 어느 자리에서도 RED 다', async () => {
    const places = [
      'src/display/planted.ts',
      'src/screens/Planted.tsx',
      'src/components/Planted.tsx',
      'src/planted.ts',
      'src/api/client.ts',
      'src/display/planted.test.ts',
      'e2e/journey/planted.ts',
      'playwright.planted.ts',
    ];

    for (const place of places) {
      const messages = await assertionMessages(place, `${TABLE}export const T = { A: "1", B: "2" } as Row;\n`);

      expect(messages.length, `${place} 에서 초록이다`).toBe(1);
    }
  });

  /** 각괄호 꼴도 RED 다 — 표기를 바꿔 같은 단언을 쓰는 길을 닫는다. */
  it('각괄호 꼴 단언도 RED 다', async () => {
    const messages = await assertionMessages('src/display/planted.ts', 'export const a = <string>"x";\n');

    expect(messages.length).toBe(1);
  });

  /** 함수 반환 자리의 객체 리터럴 단언도 같은 꼴이다. */
  it('반환 자리의 객체 리터럴 단언도 RED 다', async () => {
    const messages = await assertionMessages(
      'src/display/planted.ts',
      `${TABLE}export const f = (): Row => ({ A: "1", B: "2" } as Row);\n`,
    );

    expect(messages.length).toBe(1);
  });
});

describe('음성 대조 — 오늘의 표와 정당한 단언은 초록이다', () => {
  /**
   * **이 slice 가 지키려는 바로 그 꼴**이다. 이것이 붉으면 저장소의 표 아흔 자리가 함께 붉고,
   * 다음 라운드가 규칙을 끈다 — 그래서 음성 대조를 양성과 같은 자리에 둔다.
   */
  it('`as const satisfies Record<…>` 는 초록이다', async () => {
    const messages = await assertionMessages(
      'src/display/planted.ts',
      `${TABLE}export const T = { A: "1", B: "2" } as const satisfies Row;\n`,
    );

    expect(messages.length).toBe(0);
  });

  it('`as const` 와 타입 주석도 초록이다', async () => {
    const asConst = await assertionMessages('src/display/planted.ts', 'export const T = ["a"] as const;\n');
    const annotated = await assertionMessages(
      'src/display/planted.ts',
      `${TABLE}export const T: Row = { A: "1", B: "2" };\n`,
    );

    expect(asConst.length).toBe(0);
    // 주석은 누락을 **컴파일에서** 든다 — 단언과 달리 전수성을 지우지 않으므로 대상이 아니다.
    expect(annotated.length).toBe(0);
  });

  /** 객체 리터럴이 아닌 단언은 이 층 밖이다 — 범위를 글이 아니라 측정으로 적는다. */
  it('객체 리터럴이 아닌 단언은 초록이다', async () => {
    const messages = await assertionMessages('src/display/planted.ts', 'export const a = "x" as string;\n');

    expect(messages.length).toBe(0);
  });

  /**
   * **알려진 제한의 측정**(`design-review.md` 미달 Ⅲ). 변수를 거친 단언과 `as unknown as` 는 규칙
   * 밖이다 — 닫으려면 타입 서비스를 켜야 하고 그것은 lint 레인 전체의 비용 축이다. 제한을 글로
   * 적는 대신 **초록인 것을 잰다**: 다음 라운드가 「막힌다」고 착각하지 않게.
   */
  it('변수를 거친 단언과 `as unknown as` 는 이 층 밖이다', async () => {
    const viaVariable = await assertionMessages(
      'src/display/planted.ts',
      `${TABLE}const raw = { A: "1", B: "2" };\nexport const T = raw as Row;\n`,
    );
    const viaUnknown = await assertionMessages(
      'src/display/planted.ts',
      `${TABLE}export const T = { A: "1", B: "2" } as unknown as Row;\n`,
    );

    expect(viaVariable.length).toBe(0);
    expect(viaUnknown.length).toBe(0);
  });
});

/**
 * **새 블록이 앞 블록의 금지를 덮어쓰지 않는다**(이 호스트의 등재된 flat config 함정 — 블록을
 * 더하면 앞 블록의 **같은 규칙** 옵션이 사라진다). 이 항목의 블록은 다른 규칙 이름을 쓰지만,
 * 그 전제를 글로 두지 않고 **기존 금지가 여전히 서는지**를 같은 자리에서 잰다.
 */
describe('기존 금지가 여전히 선다 (덮어쓰기 함정)', () => {
  async function ruleHits(filePath, code, ruleIds) {
    const [result] = await eslint.lintText(code, { filePath: resolve(webRoot, filePath) });
    return result.messages.filter((one) => ruleIds.includes(one.ruleId));
  }

  it('화면 파일의 네트워크 금지가 살아 있다', async () => {
    const member = await ruleHits('src/screens/Planted.tsx', "export const r = window.fetch('/x');\n", [
      'no-restricted-syntax',
    ]);
    const bare = await ruleHits('src/screens/Planted.tsx', "export const r = fetch('/x');\n", [
      'no-restricted-globals',
    ]);

    expect(member.length).toBeGreaterThanOrEqual(1);
    expect(bare.length).toBeGreaterThanOrEqual(1);
  });

  it('화면 파일의 시계 금지가 살아 있다', async () => {
    const hits = await ruleHits('src/screens/Planted.tsx', 'export const at = new Date();\n', [
      'no-restricted-syntax',
      'no-restricted-globals',
    ]);

    expect(hits.length).toBeGreaterThanOrEqual(1);
  });

  it('승인된 네트워크 자리의 `openapi-fetch` 는 여전히 초록이다', async () => {
    const hits = await ruleHits('src/api/client.ts', "import createClient from 'openapi-fetch';\nexport const c = createClient;\n", [
      'no-restricted-imports',
    ]);

    expect(hits.length).toBe(0);
  });
});
