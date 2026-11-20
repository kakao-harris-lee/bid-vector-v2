import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  CARD_SEPARATION_FLOOR,
  NON_TEXT_RATIO,
  TEXT_RATIO,
  contrastOf,
  failingPairs,
  pairsOf,
  paletteOf,
  readPalette,
} from './contrast.mjs';

/**
 * 대비 게이트의 짝 test(M14/14A).
 *
 * 재는 대상은 **출하 `index.css` 그 파일**이고 값을 여기 복사하지 않는다 — 복사하면 파일을
 * 고쳐도 게이트가 옛 값을 재고, 그 둘이 갈린 날 아무도 붉지 않는다.
 *
 * 0 건이 「막았다」는 뜻이려면 **쌍이 실제로 섰다**는 것이 함께 서야 한다. 그래서 라이트·다크
 * 두 블록을 찾았는지, 쌍 계수가 하한을 넘는지, 유도한 쌍마다 두 토큰이 다 있는지를 단언한다.
 */
const cssPath = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'index.css');

/** 어조 여섯 + 면 둘 × 글자 둘 + 강조 + 컨트롤 선 둘 + 면 차이 = 열넷. 하한이라 늘면 따라 오른다. */
const MIN_PAIRS = 13;

describe('출하 팔레트', () => {
  it('라이트에서 기준을 넘지 못하는 쌍이 없다', () => {
    expect(failingPairs(readPalette(cssPath).light)).toStrictEqual([]);
  });

  it('다크에서 기준을 넘지 못하는 쌍이 없다', () => {
    expect(failingPairs(readPalette(cssPath).dark)).toStrictEqual([]);
  });
});

describe('비공허 — 쌍이 실제로 섰다', () => {
  it('두 블록을 **파일에서** 찾았다', () => {
    const { light, dark } = readPalette(cssPath);

    expect(light.size).toBeGreaterThan(0);
    expect(dark.size).toBeGreaterThan(0);
  });

  it('두 블록 모두 쌍 계수가 하한을 넘는다', () => {
    const { light, dark } = readPalette(cssPath);

    expect(pairsOf(light).length).toBeGreaterThanOrEqual(MIN_PAIRS);
    expect(pairsOf(dark).length).toBeGreaterThanOrEqual(MIN_PAIRS);
  });

  it('유도한 쌍마다 두 토큰이 다 선다 — 값이 비면 읽기가 던진다', () => {
    for (const tokens of Object.values(readPalette(cssPath))) {
      for (const pair of pairsOf(tokens)) {
        expect(pair.background.length).toBeGreaterThan(0);
        expect(pair.foreground.length).toBeGreaterThan(0);
      }
    }
  });

  /** 다크는 **상속을 접은 뒤** 선다 — 다크 블록이 재선언하지 않은 토큰도 쌍에 든다. */
  it('다크가 라이트의 토큰 집합을 덮는다', () => {
    const { light, dark } = readPalette(cssPath);

    for (const name of light.keys()) {
      expect(dark.has(name), name).toBe(true);
    }
  });

  /** 축이 셋이다 — 본문 글자 · 컨트롤 경계 · **면 차이**(접근성 기준이 아니라 래칫). */
  it('세 축이 서로 다른 기준을 쓴다', () => {
    const floors = new Set(pairsOf(readPalette(cssPath).light).map((one) => one.floor));

    expect(floors).toStrictEqual(new Set([TEXT_RATIO, NON_TEXT_RATIO, CARD_SEPARATION_FLOOR]));
  });

  /**
   * **카드 테두리는 측정 밖이다**(팀장 판정). 그 사실이 글로만 있지 않도록 모집단에서 그
   * 이름이 **없다는 것**을 센다 — 다시 들어오면 이 단언이 먼저 말한다.
   */
  it('카드 테두리는 측정 모집단에 없다', () => {
    const names = pairsOf(readPalette(cssPath).light).map((one) => one.name);

    expect(names.filter((one) => one.startsWith('line/'))).toStrictEqual([]);
    expect(names.filter((one) => one.startsWith('control-line/'))).toHaveLength(2);
  });
});

/** 합성 CSS — 술어 자신의 대조. 출하 트리를 건드리지 않는다. */
const synthetic = (light, dark = '') => `
@theme {
${Object.entries(light)
  .map(([name, value]) => `  --color-${name}: ${value};`)
  .join('\n')}
}
@layer base {
  @media (prefers-color-scheme: dark) {
    :root {
${Object.entries(dark)
  .map(([name, value]) => `      --color-${name}: ${value};`)
  .join('\n')}
    }
  }
}
`;

const BASE = {
  page: '#f6f5f2',
  card: '#ffffff',
  ink: '#111111',
  'ink-soft': '#444444',
  line: '#d9d6cf',
  'control-line': '#767676',
  accent: '#0f5f5a',
  'accent-ink': '#ffffff',
  'neutral-skin': '#eeeeee',
  'neutral-ink': '#333333',
};

describe('양성·음성 대조 — 술어 자신', () => {
  it('기준을 넘는 팔레트는 빈 목록이다', () => {
    expect(failingPairs(paletteOf(synthetic(BASE)).light)).toStrictEqual([]);
  });

  it('일부러 떨어뜨린 글자색이 잡힌다', () => {
    const found = failingPairs(paletteOf(synthetic({ ...BASE, 'neutral-ink': '#bbbbbb' })).light);

    expect(found).toHaveLength(1);
    expect(found[0]).toContain('neutral skin/ink');
  });

  /** 컨트롤 선은 **비텍스트 기준**이라 텍스트 기준으로 재면 붉어야 할 것이 지나거나 그 반대가 된다. */
  it('컨트롤 선이 비텍스트 기준에 못 미치면 잡힌다', () => {
    const found = failingPairs(paletteOf(synthetic({ ...BASE, 'control-line': '#e5e5e5' })).light);

    expect(found.filter((one) => one.startsWith('control-line/'))).toHaveLength(2);
  });

  it('컨트롤 선이 텍스트 기준에는 못 미쳐도 비텍스트 기준을 넘으면 지난다 — 축을 섞지 않는다', () => {
    const found = failingPairs(paletteOf(synthetic({ ...BASE, 'control-line': '#8c8c8c' })).light);

    expect(found.filter((one) => one.startsWith('control-line/'))).toStrictEqual([]);
    expect(contrastOf('#8c8c8c', '#ffffff')).toBeLessThan(TEXT_RATIO);
  });

  /** **카드 테두리는 아무리 옅어도 지난다** — 측정 밖이라는 판정이 거동으로 선다. */
  it('카드 테두리가 옅어도 붉지 않는다', () => {
    expect(failingPairs(paletteOf(synthetic({ ...BASE, line: '#fdfdfc' })).light)).toStrictEqual([]);
  });

  /** 대신 **면 차이**가 사라지면 붉는다 — 카드가 바탕에 녹는 퇴행을 그 축이 받는다. */
  it('카드와 바탕이 같은 값이면 붉는다', () => {
    const found = failingPairs(paletteOf(synthetic({ ...BASE, page: '#ffffff' })).light);

    expect(found.filter((one) => one.includes('면 차이'))).toHaveLength(1);
  });

  it('짝 없는 skin 은 던진다 — 쌍이 조용히 모집단에서 빠지지 않는다', () => {
    const withoutInk = Object.fromEntries(Object.entries(BASE).filter(([name]) => name !== 'neutral-ink'));

    expect(() => pairsOf(paletteOf(synthetic(withoutInk)).light)).toThrow(/neutral-ink/u);
  });

  it('다크 블록이 없으면 던진다', () => {
    expect(() => paletteOf('@theme { --color-page: #ffffff; }')).toThrow(/다크/u);
  });

  it('라이트 블록이 없으면 던진다', () => {
    expect(() => paletteOf('@media (prefers-color-scheme: dark) { :root { --color-page: #000000; } }')).toThrow(
      /라이트/u,
    );
  });

  it('hex 가 아닌 값은 던진다 — 읽지 못한 값을 통과로 세지 않는다', () => {
    expect(() => failingPairs(paletteOf(synthetic({ ...BASE, ink: 'var(--something)' })).light)).toThrow(/hex/u);
  });

  /**
   * 주석 **처리한 선언**이 쌍 수를 부풀리지 않는다(설계 검토 우회 C-5). 같은 줄 앞에 표지가
   * 있으면 줄 머리 규칙이 그것을 받는다 — 주석 표지를 인자로 자르지 않고도 선다.
   */
  it('주석 처리한 선언은 읽지 않는다', () => {
    const css = synthetic(BASE).replace('@theme {', '@theme {\n  /* --color-fake-skin: #000000; */');

    expect(paletteOf(css).light.has('fake-skin')).toBe(false);
  });

  /** 줄 머리가 아닌 선언도 읽지 않는다 — 값 뒤에 이어 붙인 꼴이 쌍을 만들지 않는다. */
  it('줄 가운데의 선언 꼴은 읽지 않는다', () => {
    const css = synthetic(BASE).replace('@theme {', '@theme {\n  color: red; --color-fake-skin: #000000;');

    expect(paletteOf(css).light.has('fake-skin')).toBe(false);
  });
});
