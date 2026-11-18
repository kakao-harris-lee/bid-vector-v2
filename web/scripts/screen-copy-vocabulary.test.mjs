// 문면 게이트의 **소스 층**(M13/13A) — 운영자가 보는 글자에 개발 코드가 있는지 `src/**` 전수에서
// 센다. 렌더 층(`src/screens/screenCopy.test.tsx`)이 못 보는 자리를 이 층이 받고, 그 반대도 참이다:
// 이 층은 **런타임 조립**을 보지 못하고 렌더 층은 **그리지 않는 자리**를 보지 못한다.
//
// 네 축이 여기 있다.
//  · **판정** — `src/**` 비-test 전수의 문자열 리터럴·템플릿 조각·JSX 텍스트에 금지 꼴이 0 인가.
//    모집단은 디렉터리 **전수 걷기**(`screenFilesIn`)이고 차감이 **하나뿐**이다(`*.test.*`).
//    차감을 늘리지 않는 것이 설계다 — 차감은 그 자체가 우회로이고, 오늘 차감 없이도 적중이
//    세 자리뿐이다(착수 실측).
//  · **포함 단언** — capability map 이 선언한 ID 전수가 그 꼴들에 걸리는가. 금지 판정은 꼴이
//    하고 등록부는 이 단언에만 쓴다(계약 「등록부 ∪ 꼴」을 좁힌 근거는 설계 검토 (1)).
//  · **라우트 경계** — 던진 값의 원문을 쥐는 자리가 0 인가. 이 축은 13A 전에 **1** 이다.
//  · **등식 둘** — 렌더 층이 표시층 모듈 전수를 들고 있는가 · 카드 의무 전수를 그리는가.
//    렌더 층의 모집단이 조용히 뒤처지는 자리를 이 층이 센다.
import { readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

import { collectScreenVocabulary, screenFilesIn } from './screen-card-coverage.mjs';

const webRoot = resolve(import.meta.dirname, '..');
const repoRoot = resolve(webRoot, '..');
const srcDir = resolve(webRoot, 'src');
const RENDER_LAYER = 'screens/screenCopy.test.tsx';

/* ── 금지 꼴 — 두 층이 읽는 한 자리 ───────────────────────────────────────────── */

/**
 * 금지 꼴을 **렌더 층의 선언에서** 읽는다(AST — 문자열 grep 은 주석 한 줄로 열린다). 정본이 그
 * 파일에 있는 이유는 경계다: `src` 의 TS 프로그램에는 node 타입이 없어 그 층이 데이터 파일을
 * 읽을 수 없다. 사본을 두는 대신 **한 선언을 두 층이 나눠 쓴다**.
 *
 * **비면 던진다** — 빈 집합은 모든 판정을 공허하게 만들고, 그 상태에서 게이트는 초록을 보고한다.
 */
export function shapesOf(text) {
  const source = sourceOf(RENDER_LAYER, text);
  let shapes = null;
  ts.forEachChild(source, (node) => {
    if (!ts.isVariableStatement(node)) {
      return;
    }
    for (const declaration of node.declarationList.declarations) {
      if (
        !ts.isIdentifier(declaration.name) ||
        declaration.name.text !== 'COPY_FORBIDDEN_SHAPES' ||
        declaration.initializer === undefined
      ) {
        continue;
      }
      const array = ts.isAsExpression(declaration.initializer)
        ? declaration.initializer.expression
        : declaration.initializer;
      if (!ts.isArrayLiteralExpression(array)) {
        continue;
      }
      shapes = array.elements.map((element) => {
        if (!ts.isObjectLiteralExpression(element)) {
          throw new Error('금지 꼴 선언의 원소가 객체 리터럴이 아니다');
        }
        const read = (key) => {
          const property = element.properties.find(
            (one) => ts.isPropertyAssignment(one) && one.name.getText(source) === key,
          );
          if (property === undefined || !ts.isPropertyAssignment(property)) {
            throw new Error(`금지 꼴 선언에 ${key} 가 없다`);
          }
          const value = property.initializer;
          if (ts.isStringLiteral(value) || ts.isNoSubstitutionTemplateLiteral(value)) {
            return value.text;
          }
          // `String.raw` 태그 템플릿 — 이스케이프를 그대로 쓰려고 그 꼴로 적는다.
          if (ts.isTaggedTemplateExpression(value) && ts.isNoSubstitutionTemplateLiteral(value.template)) {
            return value.template.rawText ?? value.template.text;
          }
          throw new Error(`금지 꼴 선언의 ${key} 가 리터럴이 아니다`);
        };
        return { name: read('name'), source: read('source') };
      });
    }
  });
  if (shapes === null || shapes.length === 0) {
    throw new Error('렌더 층에서 금지 꼴 선언을 읽지 못했다');
  }
  return shapes;
}

/** 한 글자에서 걸린 꼴의 이름 — **전수**를 돌린다(첫 적중에서 멈추지 않는다: 진단이 흐려진다). */
export function forbiddenIn(value, shapes) {
  const found = [];
  for (const shape of shapes) {
    const re = new RegExp(shape.source, 'gu');
    const hits = value.match(re);
    if (hits !== null) {
      found.push({ name: shape.name, hits: [...new Set(hits)] });
    }
  }
  return found;
}

/* ── 등록부의 유도 — capability map §0.6 ──────────────────────────────────────── */

/**
 * ID 전수를 **문서 자신의 표**에서 유도한다. 단순 토큰 훑기를 쓰지 않는 근거는 실측이다: 그
 * 문서는 리뷰 지적 ID(`M-1`·`L-2`)와 조사 노트 행 ID(`A-01`·`B-01`)도 담고, 그 가운데 둘이
 * 계약이 **음성 대조로 지명한** D-day 문면과 글자가 같다. 접두사 표로 좁히면 그 충돌이 사라진다.
 */
export function derivedIdsOf(root = repoRoot) {
  const text = readFileSync(resolve(root, 'docs/discovery/capability-map.md'), 'utf8');
  const after = text.split('\n### 0.6 ')[1];
  if (after === undefined) {
    throw new Error('capability map 의 접두사 표 절(§0.6)을 찾지 못했다');
  }
  const prefixes = [];
  for (const line of after.split('\n### ')[0].split('\n')) {
    if (!line.startsWith('|')) {
      continue;
    }
    const cell = line.split('|').map((one) => one.trim())[2] ?? '';
    const matched = /^`([A-Z]{2,})`$/u.exec(cell);
    if (matched !== null) {
      prefixes.push(matched[1]);
    }
  }
  if (prefixes.length < 2) {
    throw new Error(`접두사 표에서 접두사를 ${prefixes.length} 개만 얻었다 — 표의 모양이 바뀌었다`);
  }
  const open = [...new Set(text.match(/OPEN-[A-Z0-9][A-Z0-9-]*/gu) ?? [])];
  // **넓게** 유도한다(팀장 판정 2026-11-18): 번호가 붙은 것만 담으면 번호 없는 ID 토막이 유도
  // 집합 밖이라 포함 단언이 그 잔여를 보지 못했다. 전칭이 성립하려면 유도가 먼저 전수여야 한다.
  const capability = [
    ...new Set(text.match(new RegExp(`\\b(?:${prefixes.join('|')})-[A-Z0-9][A-Z0-9-]*\\b`, 'gu')) ?? []),
  ];
  if (open.length === 0 || capability.length === 0) {
    throw new Error('유도 집합이 비었다 — 훑기가 문서의 꼴을 따라가지 못한다');
  }
  return { prefixes, open, capability };
}

/**
 * 렌더 층의 「등록부 접두 ID」 꼴에 **박힌** 접두사 집합. 정규식 안의 교대를 꺼내는 좁은 추출이고,
 * 꼴의 모양이 바뀌면 **던진다** — 조용히 빈 집합을 돌려주면 아래 등식이 공허해진다.
 */
export function shapePrefixesOf(shapes) {
  const shape = shapes.find((one) => one.name === '등록부 접두 ID');
  if (shape === undefined) {
    throw new Error('렌더 층에 「등록부 접두 ID」 꼴이 없다');
  }
  const alternation = /\(\?:([A-Z|]+)\)/u.exec(shape.source);
  if (alternation === null) {
    throw new Error(`「등록부 접두 ID」 꼴에서 접두사 교대를 꺼내지 못했다: ${shape.source}`);
  }
  return alternation[1].split('|').filter((one) => one !== '');
}

/* ── 모집단 — DOM 에 닿을 수 있는 문자열 전수 ────────────────────────────────── */

const literalOf = (node) => {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return node.text;
  }
  if (ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node)) {
    return node.text;
  }
  if (ts.isJsxText(node)) {
    return node.text;
  }
  return undefined;
};

const sourceOf = (path, text) =>
  ts.createSourceFile(path, text, ts.ScriptTarget.ESNext, true, path.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);

/**
 * `src/**` 전수의 문자열. `extra` 는 **합성 원문**을 같은 추출 경로로 흘려보내는 자리다(양성
 * 대조가 정규식만이 아니라 **모집단까지** 지나게 하려는 것 — `screenProgram` 의 오버레이 관례).
 */
export function domReachableStringsOf(root = srcDir, extra = new Map()) {
  const found = [];
  const files = [
    ...screenFilesIn(root).map((relative) => [relative, readFileSync(resolve(root, relative), 'utf8')]),
    ...extra,
  ];
  for (const [path, text] of files) {
    const source = sourceOf(path, text);
    const visit = (node) => {
      const value = literalOf(node);
      if (value !== undefined && value.trim() !== '') {
        found.push({
          file: path,
          line: source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1,
          value,
        });
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }
  return found;
}

/* ── 라우트 경계 — 던진 값의 원문을 쥐는 자리 ────────────────────────────────── */

/**
 * 「오류 원문을 그리지 않는다」를 **소스에서** 재는 축. 앵커는 이름이 아니라 **그 훅의 호출**이다:
 * `useRouteError()` 를 부르는 파일은 던진 값을 쥔 파일이고, 그 안에서 `.message` 접근이나
 * `String(…)` 변환은 원문을 글자로 바꾸는 유일한 두 꼴이다.
 *
 * **경계를 적어 둔다**: 오류 값을 다른 파일로 넘겨 그리는 길은 이 축이 보지 못한다(오늘 0).
 * 그리고 이 축은 그 파일 안의 `.message` 를 **쓰임과 무관하게** 문다 — 보수적인 방향이고,
 * 그 값을 쥐어야 하는 날 축을 어디서 재는지는 그때의 결정이다(승인 경계).
 */
export function routeErrorRawUsesOf(root = srcDir, extra = new Map()) {
  const found = [];
  const files = [
    ...screenFilesIn(root).map((relative) => [relative, readFileSync(resolve(root, relative), 'utf8')]),
    ...extra,
  ];
  for (const [path, text] of files) {
    const source = sourceOf(path, text);
    let anchored = false;
    const anchor = (node) => {
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'useRouteError') {
        anchored = true;
      }
      ts.forEachChild(node, anchor);
    };
    anchor(source);
    if (!anchored) {
      continue;
    }
    const visit = (node) => {
      if (ts.isPropertyAccessExpression(node) && node.name.text === 'message') {
        found.push({ file: path, kind: '원문 속성 접근' });
      }
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'String') {
        found.push({ file: path, kind: '원문 문자열 변환' });
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }
  return found;
}

/* ── 렌더 층을 읽는다 — 등식 둘 ──────────────────────────────────────────────── */

/** 렌더 층이 import 한 표시층 모듈의 basename. 문자열 grep 이 아니라 import 선언을 읽는다. */
export function displayModulesOf(text, path = RENDER_LAYER) {
  const source = sourceOf(path, text);
  const found = new Set();
  ts.forEachChild(source, (node) => {
    if (!ts.isImportDeclaration(node) || !ts.isStringLiteral(node.moduleSpecifier)) {
      return;
    }
    const specifier = node.moduleSpecifier.text;
    if (specifier.includes('/display/')) {
      found.add(`${basename(specifier)}.ts`);
    }
  });
  return found;
}

/** 렌더 층이 **실제로 그리는** JSX 요소 이름. 선언 목록이 아니라 코드 자신을 읽는다. */
export function renderedElementsOf(text, path = RENDER_LAYER) {
  const source = sourceOf(path, text);
  const found = new Set();
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const name = node.tagName.getText(source);
      if (/^[A-Z]/u.test(name)) {
        found.add(name);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/* ── 단언 ─────────────────────────────────────────────────────────────────────── */

const renderLayer = readFileSync(resolve(srcDir, RENDER_LAYER), 'utf8');
const shapes = shapesOf(renderLayer);
// **모듈 꼭대기에서 부른다**(선례 `screen-card-coverage.test.mjs` 와 같은 자리). 이 호출은 TS
// 프로그램과 타입 검사기를 세우므로, `it` 안에 두면 전체 suite 의 병렬 아래에서 5s 기본 상한을
// 넘는다(실측: 단독 2.1s · 전건 5.8s 타임아웃).
const { obligations } = collectScreenVocabulary(webRoot);
const derived = derivedIdsOf();
const corpus = domReachableStringsOf();

describe('문면 게이트 — 모집단과 금지 집합이 비지 않았다', () => {
  it('금지 꼴 · 접두사 · OPEN ID · 문자열 모집단의 하한', () => {
    // 하한은 **오늘 실측의 바닥**이다(수를 박지 않는다 — 늘어나는 쪽은 자유다).
    expect(shapes.length).toBeGreaterThanOrEqual(5);
    expect(derived.prefixes.length).toBeGreaterThanOrEqual(5);
    expect(derived.open.length).toBeGreaterThanOrEqual(100);
    expect(derived.capability.length).toBeGreaterThanOrEqual(50);
    expect(corpus.length).toBeGreaterThanOrEqual(1500);
  });
});

describe('문면 게이트 — 포함 단언(금지 판정이 등록부를 덮는다)', () => {
  /**
   * **전칭이다**(팀장 판정 2026-11-18): 「잔여가 오늘 몇 개」가 아니라 「유도 집합 ⊂ 금지 판정」.
   * 유도를 번호 없는 ID 토막까지 넓혔으므로 이 단언이 그 잔여까지 든다.
   */
  it('capability map 이 선언한 ID 전수가 금지 판정에 걸린다', () => {
    const uncovered = [...derived.open, ...derived.capability].filter((id) => forbiddenIn(id, shapes).length === 0);
    expect(uncovered).toEqual([]);
  });

  /** 접두 꼴의 접두사 집합 == §0.6 의 표. 사본이 아니라 등식이다(양방향). */
  it('접두 꼴에 박힌 접두사가 등록부의 표와 같다', () => {
    const inShape = [...shapePrefixesOf(shapes)].sort();
    const inTable = [...derived.prefixes].sort();
    expect(inShape).toEqual(inTable);
    expect(inShape.length).toBeGreaterThanOrEqual(5);
  });

  /** 그 잔여가 **오늘 화면에 닿는 자리**는 0 이다(위 판정이 그것을 센다 — 여기서는 수를 적는다). */
  it('번호 없는 ID 토막이 DOM 에 닿는 자리가 0 이다', () => {
    const bare = derived.capability.filter((id) => !/-\d+$/u.test(id));
    expect(bare.length).toBeGreaterThan(0);
    const reached = corpus.filter((one) => bare.some((id) => one.value.includes(id)));
    expect(reached.map((one) => `${one.file}:${one.line}`)).toEqual([]);
  });
});

describe('문면 게이트 — 소스 층 판정', () => {
  it('src 전수의 DOM 에 닿을 수 있는 문자열에 개발 코드가 0 이다', () => {
    const hits = corpus
      .flatMap((one) => forbiddenIn(one.value, shapes).map((found) => `${one.file}:${one.line} [${found.name}] ${found.hits.join(' ')}`))
      .sort();
    expect(hits).toEqual([]);
  });
});

describe('문면 게이트 — 양성·음성 대조', () => {
  it('심은 문면 하나가 모집단을 지나 붉는다', () => {
    // 합성 원문이 **같은 추출 경로**를 지난다 — 정규식만 재는 대조가 아니다.
    const planted = new Map([['__copy_gate_probe__.tsx', 'export const P = () => <p>사유는 SET-03 이다</p>;\n']]);
    const probed = domReachableStringsOf(srcDir, planted).filter((one) => one.file.includes('probe'));
    expect(probed.length).toBeGreaterThan(0);
    expect(probed.flatMap((one) => forbiddenIn(one.value, shapes)).length).toBeGreaterThan(0);
  });

  it('계약이 지명한 음성 대조가 걸리지 않는다', () => {
    const negatives = ['D-0', 'D-1', 'D-14', 'D+3', 'SYN-M9-0', 'SYN-M9-CASE', 'policy-values.md §1.2', 'UNSUPPORTED_SCHEMA', '2026-11-18'];
    const flagged = negatives.filter((one) => forbiddenIn(one, shapes).length > 0);
    expect(flagged).toEqual([]);
  });

  it('상류 하네스 공고번호는 **양성**이다 — 렌더되면 붉어야 한다', () => {
    expect(forbiddenIn('SYN-M9-OPEN-REBID', shapes).length).toBeGreaterThan(0);
  });

  it('음수 금액이 이어 붙은 글자는 가짜 ID 를 만든다 — 그래서 렌더 층이 노드 단위로 읽는다', () => {
    expect(forbiddenIn('차이 금액 R-4,000원', shapes).length).toBeGreaterThan(0);
    expect(forbiddenIn('차이 금액 R\u0001-4,000원', shapes)).toEqual([]);
  });
});

describe('문면 게이트 — 라우트 경계가 던진 값의 원문을 쥐지 않는다', () => {
  it('출하 코드에 그 자리가 0 이다', () => {
    expect(routeErrorRawUsesOf()).toEqual([]);
  });

  it('그 축이 공허하지 않다 — 심은 자리 하나가 붉는다', () => {
    const planted = new Map([
      [
        '__route_probe__.tsx',
        'import { useRouteError } from "react-router";\n' +
          'export function P() {\n  const e = useRouteError();\n  return <p>{e instanceof Error ? e.message : String(e)}</p>;\n}\n',
      ],
    ]);
    const found = routeErrorRawUsesOf(srcDir, planted).filter((one) => one.file.includes('probe'));
    expect(found.length).toBeGreaterThanOrEqual(2);
  });
});

describe('문면 게이트 — 렌더 층의 모집단이 뒤처지지 않는다', () => {
  it('표시층 모듈 전수를 렌더 층이 든다', () => {
    const onDisk = new Set(screenFilesIn(resolve(srcDir, 'display')));
    const held = displayModulesOf(renderLayer);
    expect([...onDisk].filter((one) => !held.has(one)).sort()).toEqual([]);
  });

  it('카드 의무 전수를 렌더 층이 그린다', () => {
    const drawn = renderedElementsOf(renderLayer);
    expect(obligations.map((one) => one.name).filter((name) => !drawn.has(name)).sort()).toEqual([]);
  });
});
