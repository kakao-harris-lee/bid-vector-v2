// 색 토큰의 **대비**를 `index.css` 그 파일에서 재는 술어(M14/14A, 계약 「게이트」 절).
//
// **값을 test 에 복사하지 않는다.** 복사하면 파일을 고쳐도 게이트가 옛 값을 재고, 그 둘이 갈린
// 날 아무도 붉지 않는다. 그래서 선언을 파싱하고 **쌍도 파일에서 유도한다** — 쌍 목록을 손으로
// 적으면 새 어조가 들어올 때 그 쌍이 조용히 모집단 밖에 선다.
//
// 다크는 **상속을 접은 뒤** 쌓는다(설계 검토 우회 C-4): 다크 블록이 토큰 **일부만** 재선언하면
// 나머지는 라이트 값이 그대로 어두운 바탕 위에 서는데, 다크 블록만 보면 그 쌍이 보이지 않는다.
import { readFileSync } from 'node:fs';

/** 본문 글자의 기준(WCAG AA). 큰 글자 예외는 쓰지 않는다 — 이 팔레트의 쌍은 전부 본문·배지다. */
export const TEXT_RATIO = 4.5;

/** 비텍스트 기준(WCAG 1.4.11). 카드와 바탕을 가르는 **선**이 이 축이다. */
export const NON_TEXT_RATIO = 3;

/** 토큰 선언 하나 — `--color-이름: 값;`. 주석을 걷은 뒤 읽는다. */
const DECLARATION = /--color-([a-z0-9-]+)\s*:\s*([^;]+);/gu;

const stripComments = (text) => text.replace(/\/\*[\s\S]*?\*\//gu, '');

/**
 * 여는 중괄호 뒤부터 짝이 맞는 닫는 중괄호까지. 정규식으로 블록을 집으면 안쪽 블록에서 끊긴다
 * (다크 블록은 `@media { :root { … } }` 라 두 겹이다).
 */
function blockAfter(text, startIndex) {
  const open = text.indexOf('{', startIndex);
  if (open === -1) {
    return null;
  }
  let depth = 0;
  for (let i = open; i < text.length; i += 1) {
    if (text[i] === '{') {
      depth += 1;
    } else if (text[i] === '}') {
      depth -= 1;
      if (depth === 0) {
        return text.slice(open + 1, i);
      }
    }
  }
  return null;
}

function tokensIn(body) {
  const found = new Map();
  for (const [, name, value] of body.matchAll(DECLARATION)) {
    found.set(name, value.trim());
  }
  return found;
}

/**
 * 라이트·다크 두 벌. **둘 다 찾지 못하면 던진다** — 블록을 지우면 그 쪽 쌍이 모집단에서
 * 사라지는데, 빈 모집단은 모든 단언을 공허하게 만든다(설계 검토 우회 C-3).
 */
export function paletteOf(cssText) {
  const text = stripComments(cssText);

  const themeAt = text.indexOf('@theme');
  const lightBody = themeAt === -1 ? null : blockAfter(text, themeAt);
  if (lightBody === null) {
    throw new Error('라이트 토큰 블록(@theme)을 찾지 못했다');
  }
  const light = tokensIn(lightBody);

  const darkAt = text.search(/@media[^{]*prefers-color-scheme\s*:\s*dark/u);
  const darkBody = darkAt === -1 ? null : blockAfter(text, darkAt);
  if (darkBody === null) {
    throw new Error('다크 토큰 블록(prefers-color-scheme: dark)을 찾지 못했다');
  }
  // **상속을 접는다** — 다크에서 재선언하지 않은 토큰은 라이트 값 그대로 어두운 면 위에 선다.
  const dark = new Map([...light, ...tokensIn(darkBody)]);

  return { light, dark };
}

const hexOf = (value) => {
  const match = /^#([0-9a-f]{6})$/iu.exec(value.trim());
  return match === null ? null : match[1];
};

/** 상대 휘도(WCAG). 토큰이 6자리 hex 가 아니면 **던진다** — 읽지 못한 값을 통과로 세지 않는다. */
export function luminanceOf(value) {
  const hex = hexOf(value);
  if (hex === null) {
    throw new Error(`6자리 hex 가 아닌 색 토큰이다: ${value}`);
  }
  const channels = [0, 2, 4]
    .map((at) => Number.parseInt(hex.slice(at, at + 2), 16) / 255)
    .map((one) => (one <= 0.04045 ? one / 12.92 : ((one + 0.055) / 1.055) ** 2.4));
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

export function contrastOf(first, second) {
  const [high, low] = [luminanceOf(first), luminanceOf(second)].sort((a, b) => b - a);
  return (high + 0.05) / (low + 0.05);
}

/**
 * 재야 하는 쌍 — **파일에서 유도한다**.
 *
 *  · 모든 `*-skin` 과 짝 `*-ink`(짝이 없으면 그 자리에서 붉는다 — 설계 검토 우회 C-2)
 *  · `page`·`card` 와 `ink`·`ink-soft`
 *  · `accent` 와 `accent-ink`
 *  · 비텍스트: `line` 과 `page`·`card`
 */
export function pairsOf(tokens) {
  const need = (name) => {
    const value = tokens.get(name);
    if (value === undefined) {
      throw new Error(`쌍을 세울 토큰이 없다: --color-${name}`);
    }
    return value;
  };

  const pairs = [];
  for (const name of [...tokens.keys()].filter((one) => one.endsWith('-skin')).sort()) {
    const tone = name.slice(0, -'-skin'.length);
    pairs.push({ name: `${tone} skin/ink`, background: need(name), foreground: need(`${tone}-ink`), floor: TEXT_RATIO });
  }
  for (const surface of ['page', 'card']) {
    for (const ink of ['ink', 'ink-soft']) {
      pairs.push({ name: `${surface}/${ink}`, background: need(surface), foreground: need(ink), floor: TEXT_RATIO });
    }
  }
  pairs.push({ name: 'accent/accent-ink', background: need('accent'), foreground: need('accent-ink'), floor: TEXT_RATIO });
  for (const surface of ['page', 'card']) {
    pairs.push({
      name: `line/${surface}`,
      background: need(surface),
      foreground: need('line'),
      floor: NON_TEXT_RATIO,
    });
  }
  return pairs;
}

/** 기준에 못 미치는 쌍. 빈 배열이면 통과다. */
export function failingPairs(tokens) {
  return pairsOf(tokens)
    .map((pair) => ({ ...pair, ratio: contrastOf(pair.background, pair.foreground) }))
    .filter((pair) => pair.ratio < pair.floor)
    .map((pair) => `${pair.name} ${pair.ratio.toFixed(2)} < ${pair.floor}`);
}

export function readPalette(cssPath) {
  return paletteOf(readFileSync(cssPath, 'utf8'));
}
