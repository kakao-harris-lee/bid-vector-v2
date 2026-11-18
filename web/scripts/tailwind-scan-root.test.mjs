import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  cssEntryRoots,
  entryStateOf,
  scanOf,
  shippedSources,
  utilityClassesWithoutCandidates,
  widenedCss,
} from './tailwind-scan-root.mjs';

const WEB_ROOT = resolve(import.meta.dirname, '..');

/**
 * **음성 표본** — 출하 소스가 절대 쓰지 않을 꼴이고 이 파일(출하 밖)에만 있다. 뿌리가 넓어지면
 * 이 글자가 후보가 되고 출하 CSS 에 죽은 규칙으로 실린다 — 13B 라운드 4 가 겪은 그 기전이다.
 *
 * 표본을 **상류 어휘 밖**에 두는 것이 이 자리의 규율이다: 출하 소스가 이 이름을 쓰기 시작하면
 * 음성 대조가 조용히 공허해진다(그때는 이 게이트가 붉어 그 사실을 알린다).
 */
const OUT_OF_TREE_SAMPLE = 'mt-[13579px]';

describe('class 후보의 훑기 뿌리 (M13/13C — 13B 제한 27 · v L-10)', () => {
  it('출하 입구 CSS 는 자동 탐지를 끄고 출하 소스만 훑는다', async () => {
    const scan = await scanOf(WEB_ROOT);

    expect(scan.root, '자동 탐지가 켜져 있으면 뿌리가 저장소 쪽으로 넓어진다').toBe('none');
    expect(scan.sources.length, '훑을 자리를 선언하지 않으면 후보가 비어 CSS 가 통째로 빈다').toBeGreaterThan(0);
  });

  /**
   * **이 등식이 이 게이트의 힘이다.** 패턴을 넓히거나 test 제외를 지우거나 다른 CSS 에서 자동
   * 탐지를 되살리면 훑은 파일 집합이 출하 집합을 넘고, 이 줄이 붉는다.
   */
  it('훑은 파일 집합 == 출하 소스 집합', async () => {
    const scan = await scanOf(WEB_ROOT);

    expect(scan.scanned).toStrictEqual(shippedSources(WEB_ROOT));
  });

  it('후보가 비어 있지 않다 — 빈 훑기는 모든 단언을 공허하게 만든다', async () => {
    const scan = await scanOf(WEB_ROOT);

    expect(scan.candidates.size).toBeGreaterThan(0);
  });

  it('출하 밖 파일의 class 꼴은 후보에 들지 않는다', async () => {
    const scan = await scanOf(WEB_ROOT);

    expect(scan.candidates.has(OUT_OF_TREE_SAMPLE), '출하 밖 글자가 후보로 잡혔다').toBe(false);
  });

  /**
   * **양성 대조** — 측정이 살아 있는지 잰다. 자동 탐지를 되살린 변형에서는 같은 표본이 후보에
   * 들어야 한다. 그러지 않으면 위 음성 단언은 「표본이 어디에도 없다」로도 참이 된다.
   */
  it('뿌리를 넓히면 같은 표본이 후보에 든다', async () => {
    const widened = await scanOf(WEB_ROOT, widenedCss(WEB_ROOT));

    expect(widened.root, '변형이 자동 탐지를 되살리지 못했다').toBeNull();
    expect(widened.candidates.has(OUT_OF_TREE_SAMPLE), '넓힌 뿌리에서도 표본이 보이지 않는다').toBe(true);
  });

  /** 넓힌 뿌리는 **출하 밖 파일까지** 훑는다 — 위 등식이 재는 것이 그 차이다. */
  it('넓힌 뿌리의 훑은 집합은 출하 집합보다 크다', async () => {
    const pinned = await scanOf(WEB_ROOT);
    const widened = await scanOf(WEB_ROOT, widenedCss(WEB_ROOT));

    expect(widened.scanned.length).toBeGreaterThan(pinned.scanned.length);
  });
});

/**
 * M13/13C 수정 라운드 1 — verifier M-4. 설계 검토 D 의 주장 둘이 실측과 달랐다: `@source inline`
 * 과 **둘째 CSS 입구**가 모두 초록이었다. 둘 다 구조적 입구라 닫는다.
 */
describe('훑기 밖에서 들어오는 유틸리티 (v M-4)', () => {
  /**
   * `@source inline(…)` 은 훑기 **뿌리와 패턴을 바꾸지 않는다** — 후보를 직접 심는다. 그래서
   * 「훑은 파일 == 출하 소스」 등식이 그것을 보지 못한다. 재는 자리는 **후보 0 으로 지은 CSS** 다:
   * 훑기에서 오지 않은 유틸리티가 있으면 그 빌드에 클래스 규칙이 남는다.
   */
  it('후보가 0 이면 출하 CSS 에 클래스 규칙이 0 이다', async () => {
    expect(await utilityClassesWithoutCandidates(WEB_ROOT)).toStrictEqual([]);
  });

  it('양성 대조 — 어휘를 직접 심으면 그 빌드에 규칙이 남는다', async () => {
    const seeded = `${widenedCss(WEB_ROOT).replace("@import 'tailwindcss';", "@import 'tailwindcss' source(none);")}\n@source inline('${OUT_OF_TREE_SAMPLE}');\n`;

    expect(await utilityClassesWithoutCandidates(WEB_ROOT, seeded)).not.toStrictEqual([]);
  });

  /**
   * **입구가 하나여야 한다.** 다른 CSS 가 `@import 'tailwindcss'` 를 자동 탐지로 들이면 그 파일의
   * 뿌리가 저장소 쪽으로 열린다 — 번들은 두 CSS 를 모두 싣는다.
   */
  it('출하 소스의 모든 CSS 입구가 자동 탐지를 끈다', async () => {
    const roots = await cssEntryRoots(WEB_ROOT);

    expect(roots.length).toBeGreaterThan(0);
    expect(roots.filter((one) => one.root !== 'none')).toStrictEqual([]);
  });
});

/**
 * M13/13C 수정 라운드 2 — code-review N-7(오탐). **Tailwind 를 들이지 않는 CSS 는 입구가 아니다.**
 * 앞 판은 「뿌리가 `none` 이 아니면 위반」이라, 글꼴·컴포넌트 전용 스타일시트를 하나 더하는 순간
 * 붉었다 — 그 파일의 뿌리가 비어 있는 것은 자동 탐지가 아니라 **입구가 아니라는 뜻**이다.
 */
describe('CSS 입구 판별 (cr N-7)', () => {
  it('평범한 CSS 는 입구가 아니다', async () => {
    const state = await entryStateOf(WEB_ROOT, '.brand { color: red }\n');

    expect(state.isEntry).toBe(false);
  });

  it('양성 대조 — 자동 탐지로 들인 둘째 입구는 입구이고 뿌리가 묶이지 않았다', async () => {
    const state = await entryStateOf(WEB_ROOT, "@import 'tailwindcss';\n");

    expect(state.isEntry).toBe(true);
    expect(state.root).toBeNull();
  });

  it('출하 입구는 입구이고 뿌리가 묶여 있다', async () => {
    const entries = await cssEntryRoots(WEB_ROOT);
    const real = entries.filter((one) => one.isEntry);

    expect(real.length).toBeGreaterThan(0);
    expect(real.filter((one) => one.root !== 'none')).toStrictEqual([]);
  });
});
