import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scanOf, shippedSources, widenedCss } from './tailwind-scan-root.mjs';

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
