import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

import { KOTLIN_SOURCE_KEYS, readKotlinSource } from './kotlin-source.mjs';

/**
 * **Kotlin 원문 읽기의 소유권을 구조로 잠근다**(M10/gates-web — verifier r1 M-2 · r2 H-1~H-3·M-1).
 *
 * 등재된 사실: r1 의 복제 금지 술어가 **텍스트**였고 변이 둘이 초록으로 지나갔다. r2 가 술어를
 * AST 로 옮겼으나 **그 술어도 반쯤 열거였다** — 뿌리 둘·확장자 셋을 손으로 적었고, 그 목록 밖
 * (`web/config/`·`.js`·`.mts`)에 **조립이 전혀 없는 완전한 리터럴 경로**를 두면 보지 못했다.
 * 게다가 「원문이 없으면 걷을 것이 없다」는 논거가 틀렸다 — **걷힌 텍스트에도 문자열 안 `//` 는
 * 남으므로** 받은 텍스트에 사본을 덧씌우면 base URL 이 그대로 절단된다.
 *
 * r3 는 세 축을 **정의**로 바꾼다.
 * 1. 뿌리 = `web/` 전체에서 **생성물만** 뺀다. 확장자 = `ts.getSupportedExtensions` 가 주는
 *    **JS/TS 전 계열**(손으로 적은 목록이 곧 열거였다).
 * 2. 경로 꼴 = **확장자로 끝나는 모든 리터럴·템플릿 조각**. `/` 를 요구하지 않는다 —
 *    `resolve(dir, 'X.kt')` 는 난독화가 아니라 평범한 Node 관용구다.
 * 3. **export 집합 전수**와 **import 그래프**를 단언한다. 전자는 경로 표·원문 reader 가 조용히
 *    늘어나는 길을, 후자는 소비자가 걷기 함수를 다시 쥐는 길을 막는다.
 *
 * 닫히지 **않는** 것은 `design-review.md` §4 (3) 미달 ①② 에 적는다 — 글자 단위 조립과
 * import 없는 지역 사본이고, 뒤쪽은 **줄을 끊는 꼴만** 안쪽 재걷기가 던진다(측정).
 */

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..');

/** 읽기를 소유하는 자리 — 이 파일만 Kotlin 경로를 안다. */
const OWNER = 'scripts/kotlin-source.mjs';

/** 걷기 함수를 import 해도 되는 자리 — 소유 모듈의 **단위 test** 하나다. */
const STRIP_IMPORT_ALLOWED = ['scripts/kotlin-source.test.mjs'];

/**
 * 훑지 않는 자리 — **생성물만** 뺀다. 뿌리를 열거하지 않는 것이 요점이므로 이 목록은
 * 「무엇을 보는가」가 아니라 「무엇이 산출물인가」다.
 */
const GENERATED = new Set([
  'node_modules',
  'dist',
  'test-results',
  'playwright-report',
  'blob-report',
  'coverage',
  '.vite',
]);

/** 확장자는 **컴파일러의 정의**로 고른다 — 손으로 적으면 그 목록이 다음 꼴에서 뒤처진다. */
const SOURCE_SUFFIXES = ts.getSupportedExtensions({ allowJs: true }).flat();

/** Kotlin 원문의 확장자. 이 파일이 자기 술어에 걸리지 않도록 **조립해서** 든다(미달 ①). */
const KT = `.${'kt'}`;

/** `web/` 전체의 원문 파일 전수(재귀, 생성물 제외). */
function sourceFilesUnder(root = '.') {
  const found = [];
  const walk = (directory) => {
    for (const entry of readdirSync(join(webRoot, directory), { withFileTypes: true })) {
      if (GENERATED.has(entry.name)) {
        continue;
      }
      const relative = directory === '.' ? entry.name : join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(relative);
      } else if (SOURCE_SUFFIXES.some((suffix) => entry.name.endsWith(suffix))) {
        found.push(relative);
      }
    }
  };
  walk(root);
  return found;
}

const parse = (source, fileName) =>
  ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, false);

/**
 * 원문에서 **Kotlin 원문의 경로가 될 수 있는 문자열 조각**을 낸다 — 글자 검색이 아니라 AST 다.
 * 주석·산문의 같은 글자는 리터럴이 아니므로 들지 않는다.
 *
 * `/` 를 요구하지 **않는다**(verifier r2 M-1 · cr r1 MEDIUM①): `resolve(dir, 'X.kt')` 처럼 파일
 * 이름만 쓴 꼴도 경로이고, 확장자 토큰 하나도 변수에 담으면 그대로 경로가 된다. 템플릿의
 * **조각**까지 같은 자리에서 든다.
 */
export function kotlinPathLiterals(source, fileName = 'probe.mjs') {
  const found = [];
  const visit = (node) => {
    if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
      if (node.text.endsWith(KT)) {
        found.push(node.text);
      }
    } else if (ts.isTemplateExpression(node)) {
      if (node.head.text.endsWith(KT)) {
        found.push(node.head.text);
      }
      for (const span of node.templateSpans) {
        if (span.literal.text.endsWith(KT)) {
          found.push(span.literal.text);
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(parse(source, fileName));
  return found;
}

/** 원문이 공유 모듈에서 가져오는 이름들 — import 그래프 단언의 입력이다. */
export function sharedModuleImports(source, fileName = 'probe.mjs') {
  const names = [];
  const visit = (node) => {
    if (
      ts.isImportDeclaration(node) &&
      ts.isStringLiteral(node.moduleSpecifier) &&
      node.moduleSpecifier.text.endsWith('kotlin-source.mjs')
    ) {
      const bindings = node.importClause?.namedBindings;
      if (bindings && ts.isNamedImports(bindings)) {
        for (const element of bindings.elements) {
          // 별칭이면 원래 이름을 든다 — 별칭이 술어를 비켜 가지 않는다.
          names.push((element.propertyName ?? element.name).text);
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(parse(source, fileName));
  return names;
}

const files = sourceFilesUnder();
const read = (relative) => readFileSync(resolve(webRoot, relative), 'utf8');

describe('소유권 — 경로를 아는 자리가 하나다', () => {
  /** 훑기 자체가 공허하지 않은지 먼저 센다 — 0 개를 훑고 통과하는 길을 막는다. */
  it('훑기에 뿌리 목록이 없다 — 생성물만 빠진다', () => {
    expect(files.length).toBeGreaterThan(40);
    expect(files).toContain(OWNER);
    // r2 가 못 보던 자리 — `web/` 최상위의 `.ts`·`.js`.
    expect(files).toContain('vite.config.ts');
    expect(files).toContain('eslint.config.js');

    // **뿌리 허용 목록이 되살아나면 이 줄이 붉어진다**: 디렉터리에 직접 물어 원문이 있는 곳은
    // 전부 전체 훑기에도 들어와야 한다(앞 판은 `scripts`·`e2e` 둘만 들어왔다).
    const topLevel = readdirSync(webRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && !GENERATED.has(entry.name) && !entry.name.startsWith('.'))
      .map((entry) => entry.name);
    const withSource = topLevel.filter((name) => sourceFilesUnder(name).length > 0);

    expect(withSource.length, '원문을 담은 최상위 디렉터리가 너무 적다').toBeGreaterThan(2);
    for (const name of withSource) {
      expect(
        files.some((relative) => relative.startsWith(`${name}/`)),
        `${name} 이 훑기 밖이다 — 뿌리 목록이 되살아났다`,
      ).toBe(true);
    }
  });

  it('확장자 계열을 컴파일러 정의에서 고른다', () => {
    for (const suffix of ['.js', '.cjs', '.mjs', '.jsx', '.ts', '.cts', '.mts', '.tsx']) {
      expect(SOURCE_SUFFIXES, `${suffix} 가 훑기 밖이다`).toContain(suffix);
    }
  });

  it('Kotlin 경로 조각은 공유 모듈 밖에 없다', () => {
    const offenders = files
      .filter((relative) => relative !== OWNER)
      .map((relative) => ({ relative, literals: kotlinPathLiterals(read(relative), relative) }))
      .filter(({ literals }) => literals.length > 0);

    expect(offenders, '이 파일들이 Kotlin 원문을 직접 열 수 있다 — 읽기는 공유 모듈이 소유한다').toStrictEqual(
      [],
    );
  });

  /**
   * **술어 자신의 양성 대조** — 위 단언은 저장소가 깨끗하면 아무것도 재지 않는다. verifier r2 가
   * 초록으로 통과시킨 꼴들을 **합성 원문**으로 먹여 이제 걸리는지 잰다.
   */
  it('술어가 r2 의 반례 꼴을 전부 잡는다', () => {
    const planted = {
      '완전한 경로': `const p = 'workflow/src/main/kotlin/X${KT}';`,
      '조립(V1)': `const p = 'X' + '${KT}';`,
      '템플릿(V3)': `const p = \`\${repoRoot}/workflow/X${KT}\`;`,
      '확장자 변수(V4)': `const ext = '${KT}';`,
      '파일 이름만(V2)': `const p = resolve(repoRoot, 'workflow', 'X${KT}');`,
      '걸러내기(V5)': `const f = readdirSync(d).find((n) => n.endsWith('${KT}'));`,
    };

    for (const [name, source] of Object.entries(planted)) {
      expect(kotlinPathLiterals(source).length, `${name} 이 술어를 지나간다`).toBeGreaterThan(0);
    }
  });

  /** **음성 대조** — 키로만 읽는 소비자는 걸리지 않는다(일률 거절이 아니다). */
  it('키로만 읽는 소비자는 걸리지 않는다', () => {
    const clean = [
      "import { readKotlinSource } from './kotlin-source.mjs';",
      "const basis = readKotlinSource('basis');",
      "const moneyTs = readFileSync('src/display/moneyDisplay.ts', 'utf8');",
    ].join('\n');

    expect(kotlinPathLiterals(clean)).toStrictEqual([]);
  });

  /** 주석·산문 안의 같은 글자는 리터럴이 아니다 — AST 술어라 그것을 들지 않는다. */
  it('주석 안의 확장자는 걸리지 않는다 — 글자 검색이 아니다', () => {
    expect(kotlinPathLiterals(`// 정본은 Basis${KT} 다\n/* Other${KT} */\n`)).toStrictEqual([]);
  });
});

describe('소유권 — 걷기 함수를 쥐는 자리가 하나다', () => {
  /**
   * **r1 M-2 의 원형이 여기서 닫힌다**(verifier r2 H-3). 소비자가 걷기를 import 할 수 있는 동안은
   * 「별칭 import + 지역 사본」이 그대로 살아 있었다 — r2 의 변이표는 거기에 `readFileSync` 를
   * **더해** 심어서 경로 술어의 RED 를 사본 술어의 RED 로 착각했다.
   */
  it('걷기 함수는 소유 모듈과 그 단위 test 만 import 한다', () => {
    const importers = files
      .filter((relative) => relative !== OWNER)
      .map((relative) => ({ relative, names: sharedModuleImports(read(relative), relative) }))
      .filter(({ names }) => names.includes('withoutKotlinComments'))
      .map(({ relative }) => relative);

    expect(importers.sort(), '소비자가 걷기 함수를 쥐면 지역 사본을 다시 씌울 수 있다').toStrictEqual(
      [...STRIP_IMPORT_ALLOWED].sort(),
    );
  });

  /** 술어의 양성 대조 — 별칭 import 도 같은 이름으로 든다. */
  it('별칭 import 도 같은 이름으로 잡는다', () => {
    const aliased = "import { withoutKotlinComments as strip } from './kotlin-source.mjs';";

    expect(sharedModuleImports(aliased)).toStrictEqual(['withoutKotlinComments']);
  });

  it('키 읽기만 가져오는 import 는 걸리지 않는다', () => {
    const clean = "import { readKotlinSource } from './kotlin-source.mjs';";

    expect(sharedModuleImports(clean)).toStrictEqual(['readKotlinSource']);
  });
});

describe('소유권 — 읽기 표면이 넷으로 고정된다', () => {
  /**
   * **V12·V13 이 선 자리**(verifier r2 H-2). 앞 판의 설계 검토는 「export 집합을 들어 RED」라고
   * 적었는데 **그런 단언이 없었다** — 경로 표나 원문 reader 를 한 줄 더하면 「소비자는 원문을 쥘
   * 수 없다」가 초록인 채 무너졌다. 모듈 네임스페이스로 **정확한 집합**을 든다.
   */
  it('export 집합이 정확히 넷이다', async () => {
    const namespace = await import('./kotlin-source.mjs');

    expect(Object.keys(namespace).sort()).toStrictEqual([
      'KOTLIN_SOURCE_KEYS',
      'assertStrippedKotlin',
      'readKotlinSource',
      'withoutKotlinComments',
    ]);
  });
});

describe('읽기 집합 — 한 자리에 전수로 선다', () => {
  it('읽기 집합은 일곱이고 키 전수가 읽힌다', () => {
    // 착수 때 여덟이었다. 명령 축은 10B(#149)의 openapi `enum` 승격으로 web 에서 읽지 않는다 —
    // 수를 줄인 것이 아니라 **읽지 않는 파일을 표에서 뺀** 것이다.
    expect(KOTLIN_SOURCE_KEYS).toHaveLength(7);

    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(() => readKotlinSource(key), `${key} 를 읽지 못한다`).not.toThrow();
      expect(readKotlinSource(key).length, `${key} 가 빈 원문이다`).toBeGreaterThan(0);
    }
  });

  /** 경로를 내보내지 않는다 — 내보내면 소비자가 그것으로 다시 원문을 열 수 있다. */
  it('키만 내보내고 경로는 내보내지 않는다', () => {
    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(key.endsWith(KT), `${key} 가 경로다 — 키여야 한다`).toBe(false);
    }
  });

  /** 모르는 키는 **던진다** — 조용히 빈 문자열을 내면 모든 등식이 공허해진다. */
  it('모르는 키는 던진다', () => {
    expect(() => readKotlinSource('없는키')).toThrow(/읽기 집합/u);
  });

  /** 읽기가 걷기를 떼지 못한다 — 호출을 AST 로 들고 거동으로도 잰다. */
  it('읽기는 걷기를 거쳐서만 값을 낸다', () => {
    const tree = parse(read(OWNER), OWNER);

    let callsStripInsideReader = false;
    const visit = (node) => {
      if (ts.isFunctionDeclaration(node) && node.name?.text === 'readKotlinSource') {
        const inner = (child) => {
          if (ts.isCallExpression(child) && ts.isIdentifier(child.expression)) {
            callsStripInsideReader ||= child.expression.text === 'withoutKotlinComments';
          }
          ts.forEachChild(child, inner);
        };
        ts.forEachChild(node, inner);
      }
      ts.forEachChild(node, visit);
    };
    visit(tree);

    expect(callsStripInsideReader, 'readKotlinSource 가 공유 걷기를 호출하지 않는다').toBe(true);
    expect(readKotlinSource('provenance')).not.toContain('/**');
  });
});

/**
 * **2 차 층 — 구조가 아니라 완화다.** 미달 ② 의 남은 구멍(import 없는 지역 사본 가운데 **줄을
 * 끊지 않는 치환**)은 import 그래프로 닫히지 않는다. 표기를 찾는 텍스트 층을 `web/` 전체 훑기에
 * 얹어 **흔한 표기**를 막되, **표기를 바꾸면 열린다**는 사실을 이름과 문면에 적는다 — r1 의
 * 실패는 이런 층을 「닫았다」고 부른 것이었다.
 */
describe('완화 층 — 걷기 사본의 흔한 표기 (구조가 아니다)', () => {
  /** 걷기를 import 해도 되는 자리 + 이 게이트 자신은 그 표기를 **양성 대조로** 담는다. */
  const MENTIONS_ALLOWED = new Set([
    OWNER,
    ...STRIP_IMPORT_ALLOWED,
    'scripts/kotlin-source-ownership.test.mjs',
  ]);

  /**
   * 주석 표지를 인자로 넘기는 치환·탐색. 정규식 분기는 **escape 된 슬래시 둘**(또는 슬래시 + `*`)
   * 을 요구한다 — 하나만 요구하면 `replace(/\/api\/…/u, …)` 같은 **평범한 경로 정규식**이 걸린다
   * (실측: `e2e/journey/shared.ts` 가 그 꼴로 오탐이었다).
   */
  const COPY_SPELLING =
    /\.(?:replace|replaceAll|indexOf|lastIndexOf|split)\(\s*(?:'\/\/|"\/\/|`\/\/|\/\\\/(?:\\\/|\\\*))/u;

  it('걷기 사본의 흔한 표기가 소비자에 없다', () => {
    const offenders = files
      .filter((relative) => !MENTIONS_ALLOWED.has(relative))
      .filter((relative) => COPY_SPELLING.test(read(relative)));

    expect(offenders, '소비자가 주석 표지로 직접 자르고 있다').toStrictEqual([]);
  });

  /** 술어의 양성 대조 — r1·r2 가 초록으로 통과시킨 두 표기를 합성 입력에서 잡는다. */
  it('술어가 ⓐⓑ 두 표기를 잡는다', () => {
    const marker = `/${'/'}`;
    expect(COPY_SPELLING.test(`const t = text.replaceAll('${marker}', '');`)).toBe(true);
    expect(COPY_SPELLING.test(`const at = line.indexOf('${marker}');`)).toBe(true);
    expect(COPY_SPELLING.test(String.raw`const t = s.replace(/\/\/.*$/gmu, '');`)).toBe(true);
  });

  /** 음성 대조 — 평범한 치환은 걸리지 않는다(일률 거절이 아니다). */
  it('주석 표지가 아닌 치환은 걸리지 않는다', () => {
    expect(COPY_SPELLING.test("const t = s.replaceAll(',', '');")).toBe(false);
    expect(COPY_SPELLING.test("const at = s.indexOf('://');")).toBe(false);
    // 경로 정규식 — 술어를 좁히기 전에는 이 꼴이 오탐이었다(저장소에 실제로 있다).
    expect(COPY_SPELLING.test(String.raw`const t = p.replace(/\/api\/x\/\d+/u, '');`)).toBe(false);
  });
});
