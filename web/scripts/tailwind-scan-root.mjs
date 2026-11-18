// M13/13C — **class 후보의 훑기 뿌리**가 출하 소스로 묶여 있는지 재는 술어(13B 제한 27 · v L-10).
//
// Tailwind v4 는 CSS 가 설정이다. 자동 탐지를 켜 두면 뿌리가 저장소 쪽으로 넓어져, 게이트
// test·여정 spec·설정에 **글자로** 적은 class 꼴이 후보가 되어 출하 CSS 에 죽은 규칙으로 실린다
// (13B 라운드 4 가 번들 자산 해시 변화로 발견했다 — `web/src` 가 한 줄도 다르지 않은데 해시가
// 갈렸다). 거둔 것은 **글자**였고 **축**이 아니어서, 다음 사람이 test 에 class 꼴을 적으면 같은
// 일이 다시 일어난다.
//
// **선언을 확인하지 않는다.** `@source` 글자를 찾는 presence 술어는 다른 자리에서 뿌리를 넓히는
// 변경(다른 CSS 의 자동 탐지 · `@source inline(…)` · 패턴 넓히기)을 보지 못한다. 그래서 **실제
// 컴파일러와 실제 훑기 엔진**으로 거동을 잰다: 번들이 쓰는 그 CSS 를 컴파일해 훑기 설정을 받고,
// 그 설정으로 훑은 **파일 집합**과 **후보 집합**을 본다.
//
// 재는 것은 셋이다.
//  ① 훑은 파일 집합 == **출하 소스 집합**(`shippedSourceFiles` — test 를 뺀 그 술어를 재사용한다).
//  ② 출하 밖 파일의 class 꼴이 **후보에 들지 않는다**(음성 표본은 이 게이트 test 자신의 글자다).
//  ③ 뿌리를 넓히면 그 표본이 **후보에 든다**(양성 대조 — 측정이 살아 있다는 증거).
import { readFileSync, readdirSync } from 'node:fs';
import { relative, resolve } from 'node:path';

import { compile } from '@tailwindcss/node';
import { Scanner } from '@tailwindcss/oxide';

import { shippedSourceFiles } from './jsx-handler-tags.mjs';

/** 번들이 쓰는 **그 CSS** — 입구를 인자로 받지 않는다(대조가 다른 CSS 를 먹이는 길을 두지 않는다). */
const ENTRY_CSS = 'src/index.css';

/** 훑기 패턴이 보는 확장자 꼴 — 출하 소스 집합과 **같은 축**이어야 한다. */
const SOURCE_SUFFIX = /\.tsx?$/u;

/**
 * 컴파일 결과의 훑기 설정을 훑기 엔진의 입력 꼴로. `root` 가 비어 있으면 **자동 탐지**이고,
 * 그때 실제 뿌리는 호출자(번들러)가 준다 — 그 경우를 `base` 로 모형화해 **넓은 뿌리**를 만든다.
 */
function entriesOf(compiled, autoDetectBase) {
  const entries = [];
  if (compiled.root === null) {
    entries.push({ base: autoDetectBase, pattern: '**/*', negated: false });
  } else if (compiled.root !== 'none') {
    entries.push({ base: compiled.root.base, pattern: compiled.root.pattern, negated: false });
  }
  for (const one of compiled.sources) {
    entries.push({ base: one.base, pattern: one.pattern, negated: one.negated });
  }
  return entries;
}

/**
 * 그 CSS 가 **무엇을 훑는가**. `css` 를 주면 그 글자를(대조용), 주지 않으면 **출하 입구 CSS** 를
 * 그대로 컴파일한다.
 */
export async function scanOf(webRoot, css = null) {
  const cssPath = resolve(webRoot, ENTRY_CSS);
  const text = css ?? readFileSync(cssPath, 'utf8');
  const compiled = await compile(text, { base: resolve(webRoot, 'src'), onDependency: () => undefined });
  const entries = entriesOf(compiled, webRoot);
  const scanner = new Scanner({ sources: entries });
  const candidates = new Set(scanner.scan());
  const scanned = scanner.scannedFiles
    .filter((one) => SOURCE_SUFFIX.test(one))
    .map((one) => relative(resolve(webRoot, 'src'), one))
    .sort();
  return { root: compiled.root, sources: compiled.sources, candidates, scanned };
}

/** 그 CSS 의 자동 탐지를 **되살린** 변형 — 양성 대조의 재료다(출하 CSS 를 고치지 않는다). */
export function widenedCss(webRoot) {
  const text = readFileSync(resolve(webRoot, ENTRY_CSS), 'utf8');
  return text.replace(/@import 'tailwindcss' source\(none\);/u, "@import 'tailwindcss';");
}

/**
 * **후보가 0 일 때 지어지는 유틸리티**(M13/13C 수정 라운드 1 — v M-4). `@source inline(…)` 은 훑기
 * 뿌리·패턴을 바꾸지 않고 **후보를 직접 심는다** — 그래서 「훑은 파일 == 출하 소스」 등식이 그것을
 * 보지 못한다. 재는 자리는 **후보 0 으로 지은 CSS** 다: 훑기에서 오지 않은 유틸리티가 있으면 그
 * 빌드에 클래스 규칙이 남는다.
 */
export async function utilityClassesWithoutCandidates(webRoot, css = null) {
  const cssPath = resolve(webRoot, ENTRY_CSS);
  const text = css ?? readFileSync(cssPath, 'utf8');
  const compiled = await compile(text, { base: resolve(webRoot, 'src'), onDependency: () => undefined });
  const built = compiled.build([]);
  return [...built.matchAll(/(?:^|[{}])\s*(\.[^{}]*?)\s*\{/gu)].map((one) => one[1].trim());
}

/**
 * 출하 소스의 **CSS 입구 전수와 그 훑기 뿌리**(v M-4). 다른 CSS 가 자동 탐지로 Tailwind 를 들이면
 * 그 파일의 뿌리가 저장소 쪽으로 열리고 번들은 둘을 모두 싣는다 — 입구가 하나라는 사실을
 * **선언이 아니라 컴파일 결과**로 잰다.
 */
export async function cssEntryRoots(webRoot) {
  const srcRoot = resolve(webRoot, 'src');
  const entries = [];
  const walk = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const full = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        walk(full);
        continue;
      }
      if (entry.name.endsWith('.css')) {
        entries.push(full);
      }
    }
  };
  walk(srcRoot);
  const roots = [];
  for (const file of entries) {
    const compiled = await compile(readFileSync(file, 'utf8'), {
      base: resolve(file, '..'),
      onDependency: () => undefined,
    });
    roots.push({ file: relative(srcRoot, file), root: compiled.root });
  }
  return roots;
}

/** 출하 소스 집합 — **다른 게이트와 같은 술어**를 쓴다(사본을 만들지 않는다). */
export function shippedSources(webRoot) {
  return shippedSourceFiles(resolve(webRoot, 'src')).sort();
}
