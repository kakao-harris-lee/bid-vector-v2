import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  CARD_LINE_CLASS,
  CONTROL_LINE_CLASS,
  CONTROL_TAGS,
  cardLineOnControls,
  cardLineOnControlsIn,
  controlLineUses,
  sourceFilesIn,
} from './control-line.mjs';

/**
 * 대비 게이트의 **비공허 짝**(M14/14A · 팀장 판정 2026-11-20).
 *
 * 그 게이트는 `control-line` 을 3:1 로 잰다. 컨트롤이 **다른 토큰**을 쓰면 그 측정은 아무것도
 * 지키지 못한다 — 재는 값과 쓰는 값이 갈리는 자리다. 이 파일이 그 둘을 묶는다.
 *
 * 카드 테두리는 `line` 을 **써야 한다**(측정 밖이 그 판정이다). 그래서 토큰 이름만 세는 판으로는
 * 둘을 가를 수 없고, 술어가 **어느 요소의 class 인가**를 봐야 한다.
 */
const srcRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src');

describe('출하 트리', () => {
  it('컨트롤이 카드 토큰을 쓰지 않는다', () => {
    expect(cardLineOnControls(srcRoot)).toStrictEqual([]);
  });

  /** **비공허 ①** — 0 이 「읽지 않아서 0」이 아님을 센다. */
  it('훑기가 실제로 여러 파일을 읽었다', () => {
    expect(sourceFilesIn(srcRoot).length).toBeGreaterThan(20);
  });

  /**
   * **비공허 ②** — 컨트롤 토큰이 **실제로 쓰이고 있다**. 아무도 쓰지 않으면 대비 게이트가 재는
   * 값이 화면에 닿지 않고, 위 단언은 「컨트롤이 없다」로도 참이 된다.
   */
  it('컨트롤 토큰이 여러 자리에서 실제로 쓰인다', () => {
    expect(controlLineUses(srcRoot)).toBeGreaterThan(10);
  });

  /** 카드 토큰은 **여전히 쓰인다** — 둘을 가른 판정이 「하나를 없앤 것」이 아니다. */
  it('카드 토큰도 여전히 쓰인다 — 가른 것이지 없앤 것이 아니다', () => {
    const files = sourceFilesIn(srcRoot).filter((file) => readFileSync(file, 'utf8').includes(CARD_LINE_CLASS));

    expect(files.length).toBeGreaterThan(5);
  });
});

describe('양성·음성 대조 — 술어 자신', () => {
  it('컨트롤 전수에서 카드 토큰을 심으면 잡힌다', () => {
    for (const tag of CONTROL_TAGS) {
      const found = cardLineOnControlsIn('probe.tsx', `const a = <${tag} className="rounded ${CARD_LINE_CLASS}" />;`);

      expect(found, tag).toHaveLength(1);
    }
  });

  it('컨트롤이 컨트롤 토큰을 쓰면 지난다 — 음성 대조', () => {
    for (const tag of CONTROL_TAGS) {
      const found = cardLineOnControlsIn(
        'probe.tsx',
        `const a = <${tag} className="rounded ${CONTROL_LINE_CLASS}" />;`,
      );

      expect(found, tag).toStrictEqual([]);
    }
  });

  /** **카드는 카드 토큰을 써도 된다** — 그 판정이 거동으로 선다. */
  it('컨트롤이 아닌 요소의 카드 토큰은 잡지 않는다', () => {
    const found = cardLineOnControlsIn('probe.tsx', `const a = <div className="${CARD_LINE_CLASS}" />;`);

    expect(found).toStrictEqual([]);
  });

  /** 긴 이름이 짧은 이름에 걸리지 않는다 — 토큰이 **홀로 선** 자리만 센다. */
  it('컨트롤 토큰은 카드 토큰의 부분 일치로 걸리지 않는다', () => {
    const found = cardLineOnControlsIn('probe.tsx', `const a = <input className="${CONTROL_LINE_CLASS}" />;`);

    expect(found).toStrictEqual([]);
  });

  /** 템플릿·삼항 안에 숨겨도 같은 글자가 거기 있다. */
  it('식 안에 숨긴 카드 토큰도 잡는다', () => {
    const found = cardLineOnControlsIn(
      'probe.tsx',
      'const a = <input className={`rounded ${on ? "border-line" : "x"}`} />;',
    );

    expect(found).toHaveLength(1);
  });
});
