import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

/**
 * **`ban-ts-comment` 금지가 **어디까지 미치는가**(M15/15O — `OPEN-M15-SRC-TS-COMMENT-DISABLE` (b)).
 *
 * 등재된 사실: `eslint.config.js` 는 억제 주석 세 형태 금지를 **형제 블록 둘**에 싣고(화면 레인 ·
 * e2e 레인) 옵션은 상수 한 자리다. 그런데 **그 금지가 실제로 어느 파일에 서는지를 재는 측정이
 * 없었다** — 블록을 지우거나 glob 을 좁히면 lint 가 **조용히 초록으로** 돌아간다. 실측 선례가
 * 둘이다: 15L r2 의 N-3(뿌리 glob 만 `.ts` 였다 — `playwright.*.config.mts` 한 파일에서 금지가
 * 상속 기본값 `allow-with-description` 으로 되돌아갔다)과, 사본이 둘이 되면서 갈릴 자리도 둘이
 * 된 것. **그 test 가 있었으면 N-3 는 자동으로 RED 였다.**
 *
 * 선례는 `scripts/type-assertion-gate.test.mjs` 로, lint 규칙 하나의 **초록을 측정으로** 든다.
 * 여기서 재는 것은 **거동이 아니라 유효 설정**이다: `calculateConfigForFile` 이 돌려준 그 규칙의
 * 값이 대표 자리마다 **엄격 상수와 같은가**. 거동으로 재면 「한 꼴이 걸렸다」까지만 서고 옵션이
 * 조용히 느슨해지는 축(`'allow-with-description'`)을 못 본다 — 설정 **전체 등식**이 그 자리를
 * 받는다. 그리고 그 상수가 실제로 「금지」를 뜻한다는 것은 **비공허 칸**이 거동으로 잰다.
 *
 * **이 층의 범위**: 타입 **검사**를 끄는 주석이다. 저장소 규약이 리뷰로 가는 탈출구로 두는
 * `eslint-disable-next-line` 자체는 여기서 건드리지 않는다 — 그 한 줄로 이 규칙을 꺼도 타입
 * 억제는 `scripts/e2e-typecheck-coverage.test.mjs` 의 **eslint 밖 층**이 막는다. 두 규율이
 * 서로를 침범하지 않도록 자리를 나눈 것이 15O 의 선택이다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const eslint = new ESLint({ cwd: webRoot });

const RULE = '@typescript-eslint/ban-ts-comment';

/**
 * **엄격 상수** — `eslint.config.js` 의 `BAN_TS_COMMENT` 가 뜻하는 값이다. 심각도는 ESLint 가
 * 숫자로 정규화해서 돌려준다(`'error'` → `2`).
 */
const STRICT = [
  2,
  { 'ts-ignore': true, 'ts-expect-error': true, 'ts-nocheck': true, 'ts-check': false },
];

/**
 * **콜드 기동 비용을 모듈 적재 단계에서 치른다**(이 저장소의 등재된 서명 A — 첫 호출이 설정 해석
 * 전체를 타고, 그 비용이 칸 안에 있으면 부하가 걸린 호스트에서 예산을 넘긴다).
 */
await eslint.calculateConfigForFile(resolve(webRoot, 'src/display/warm.ts'));

/**
 * 금지가 **서야 하는** 자리. 두 블록의 glob 을 각각 대표하고, 확장자 넷을 전부 든다 — 비대칭이
 * 생기면 그 한 줄이 RED 다.
 */
const STRICT_PLACES = [
  'src/x.ts',
  'src/x.tsx',
  'src/deep/nested/x.ts',
  'e2e/x.ts',
  'e2e/x.tsx',
  'e2e/x.mts',
  'e2e/x.cts',
  'e2e/deep/x.spec.ts',
  'playwright.config.ts',
  'playwright.journey.config.ts',
  'playwright.x.config.mts',
  'playwright.x.config.cts',
  'playwright.x.config.tsx',
];

describe('ban-ts-comment 가 미치는 범위 (OPEN-M15-SRC-TS-COMMENT-DISABLE (b))', () => {
  it.each(STRICT_PLACES)('%s 의 유효 설정이 엄격 상수다', async (place) => {
    const config = await eslint.calculateConfigForFile(resolve(webRoot, place));

    expect(config.rules?.[RULE], `${place} 의 ${RULE}`).toEqual(STRICT);
  });

  /**
   * **비공허** — 위 등식이 「늘 참」이 아니라 실제로 금지를 뜻하는가. 상수 쪽만 보면 그 값이
   * 느슨해도(예: `'ts-expect-error': 'allow-with-description'`) 등식은 **상수를 함께 고치는**
   * 변이에서 초록이다. 거동 한 칸이 그 자리를 받는다 — **설명을 단 꼴**로 재는 것이 요점이다
   * (상속 기본값이 통과시키는 바로 그 꼴).
   */
  it.each(['src/x.ts', 'e2e/x.mts', 'playwright.x.config.mts'])(
    '%s 에서 설명을 단 @ts-expect-error 가 실제로 RED 다',
    async (place) => {
      const [result] = await eslint.lintText('// @ts-expect-error: 설명\nexport const a: number = 1;\n', {
        filePath: resolve(webRoot, place),
      });

      expect(result.messages.filter((one) => one.ruleId === RULE).length).toBe(1);
    },
  );

  /**
   * **음성 대조** — 그 엄격 상수가 두 블록의 glob 에서 **온다**는 것. 어느 블록도 덮지 않는 자리는
   * 상속 기본값이고, 거기서는 같은 꼴이 초록이다. 이 칸이 없으면 위 등식이 「어디서나 참」인
   * 경우(예: 뿌리 블록 하나로 전부 걸린 경우)와 구별되지 않는다.
   *
   * 그 자리들이 **방어가 없다는 뜻은 아니다** — `vite.config.ts`·`scripts/**` 는 어느 TS
   * 프로그램에도 없고(15L 알려진 제한 ①), e2e 프로그램이 끌어들이는 `.ts`·`.mjs` 는
   * `scripts/e2e-typecheck-coverage.test.mjs` 의 두 프로그램 전수 칸이 받는다.
   */
  it('두 블록 밖은 상속 기본값이다 — 금지가 뿌리에서 오지 않는다', async () => {
    const outside = await eslint.calculateConfigForFile(resolve(webRoot, 'vite.config.ts'));

    expect(outside.rules?.[RULE]).not.toEqual(STRICT);
  });
});
