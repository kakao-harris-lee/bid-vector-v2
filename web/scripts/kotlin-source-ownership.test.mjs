import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
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

/** 걷기 본체가 사는 **내부 모듈** — 공개 표면에 없다. */
const STRIP_INTERNAL = 'scripts/kotlin-comment-strip.mjs';

/** 그 내부 모듈을 참조해도 되는 자리 — 소유 모듈과 그 **단위 test** 둘이다. */
const STRIP_REF_ALLOWED = [OWNER, 'scripts/kotlin-source.test.mjs'];

/**
 * 데이터 파일 가운데 Kotlin 경로를 **출처 주석으로** 담는 자리. 여는 데 쓰이지 않고 「이 fixture 가
 * 어느 계약에서 왔는가」를 적는다 — 그래서 경로 술어의 **참 양성이지만 의도상 허용**이다.
 * 목록으로 두는 것은 열거이고 그 약점을 안다(새 fixture 가 같은 꼴을 쓰면 여기 한 줄이 늘어난다).
 * **여는 행위 축은 이 목록이 지지 않는다** — 그 자리는 `design-review.md` §4 (3) 미달 ⑤ 다.
 */
const PROVENANCE_DATA_ALLOWED = new Set(['e2e/harness/fixtures/opening-axis.json']);

/**
 * **훑기 대상은 `git ls-files web` — 추적 파일 전수**다(r4 — verifier r3 H-2·M-2).
 *
 * r3 은 확장자 목록(`ts.getSupportedExtensions`)과 생성물 이름 집합으로 골랐다. 그래서 두 틈이
 * 열렸다: ① **JSON 등 데이터 파일**에 완전한 경로를 두면 보지 못했다(경로 목록을 데이터 파일에
 * 두는 것은 이 저장소의 관용이다 — `config/gate-tests.txt`) ② 생성물 제외가 **이름 일치**라서
 * `scripts/dist/`·`e2e/coverage/` 처럼 **커밋 가능한** 디렉터리까지 깊이 무관하게 빠졌다.
 *
 * 추적 파일 전수는 둘을 함께 없앤다 — 생성물은 추적되지 않으므로 **자연히** 빠지고, 데이터
 * 파일은 추적되므로 들어온다. 목록이 아니라 **정의**다.
 */
const trackedWebFiles = () =>
  execFileSync('git', ['ls-files', '-z', '--', '.'], { cwd: webRoot, encoding: 'utf8' })
    .split('\0')
    .filter((relative) => relative.length > 0);

/** Kotlin 원문의 확장자. 이 파일이 자기 술어에 걸리지 않도록 **조립해서** 든다(미달 ①). */
const KT = `.${'kt'}`;

/** JS/TS 는 AST 로 본다. 그 밖의 추적 파일은 **텍스트**로 본다(조립할 코드가 없으므로 충분하다). */
const AST_SUFFIXES = new Set(ts.getSupportedExtensions({ allowJs: true }).flat());
const isAstFile = (relative) => [...AST_SUFFIXES].some((suffix) => relative.endsWith(suffix));

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
  const hit = (text) => {
    // `.kt` 로 끝나거나, **brace glob 안**에 확장자가 든 꼴(`X.{kt,kts}`)도 경로다
    // (verifier r3 M-3 — glob 은 조립이 아니라 평범한 관용구다).
    if (text.endsWith(KT) || /\.\{[^}]*\bkts?\b[^}]*\}$/u.test(text)) {
      found.push(text);
    }
  };
  const visit = (node) => {
    if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
      hit(node.text);
    } else if (ts.isTemplateExpression(node)) {
      hit(node.head.text);
      for (const span of node.templateSpans) {
        hit(span.literal.text);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(parse(source, fileName));
  return found;
}

/**
 * 원문이 **참조하는 모듈 지정자 전수**를 **해석된 경로**로 낸다 — 이름이 아니라 **지정자**가 키다
 * (r4 — verifier r3 H-1 · cr r2 HIGH).
 *
 * r3 은 `ImportDeclaration` 이고 바인딩이 `NamedImports` 일 때만 셌다. 그래서 `import * as` ·
 * `await import()` · `createRequire(...)(...)` · `export … from` · `export *` · `?v=1` 쿼리가 전부
 * 지나갔다. 전부 **평범한 관용구**다(경계 (0) — 우발적 이탈은 막는다).
 *
 * 지정자를 **해석·정규화**한다: 쿼리·프래그먼트를 떼고, 호출 파일 기준 상대 경로를 `web/` 기준
 * 상대 경로로 통일한다. 그래서 `'./x.mjs'`·`'../../scripts/x.mjs'`·`'./x.mjs?v=1'` 이 **같은 키**다.
 */
export function referencedModules(source, fileName) {
  const fromDir = dirname(fileName);
  const out = new Set();
  const add = (raw) => {
    if (typeof raw !== 'string' || !raw.startsWith('.')) {
      return; // 패키지 지정자는 이 축이 아니다.
    }
    const bare = raw.split('?')[0].split('#')[0];
    out.add(resolve('/', fromDir, bare).slice(1));
  };
  const visit = (node) => {
    // 정적 import 전부 — named·default·namespace·side-effect 를 가리지 않는다.
    if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
      add(node.moduleSpecifier.text);
    }
    // 재수출 사슬 — `export { … } from` · `export * from` · `export * as ns from`.
    if (
      (ts.isExportDeclaration(node) || ts.isExportAssignment(node)) &&
      node.moduleSpecifier !== undefined &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      add(node.moduleSpecifier.text);
    }
    // 동적 `import(...)` 와 `require(...)`/`createRequire(...)(...)`.
    if (ts.isCallExpression(node) && node.arguments.length > 0) {
      const target = node.arguments[0];
      const isDynamicImport = node.expression.kind === ts.SyntaxKind.ImportKeyword;
      const isRequire = ts.isIdentifier(node.expression) && node.expression.text === 'require';
      // `createRequire(import.meta.url)('…')` — 호출 결과를 다시 호출하는 꼴.
      const isCreatedRequire =
        ts.isCallExpression(node.expression) &&
        ts.isIdentifier(node.expression.expression) &&
        node.expression.expression.text === 'createRequire';
      if ((isDynamicImport || isRequire || isCreatedRequire) && ts.isStringLiteral(target)) {
        add(target.text);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(parse(source, fileName));
  return [...out].sort();
}

/**
 * 데이터 파일(JSON·txt 등)용 술어 — **경로 꼴**을 든다. 디렉터리 구분자를 요구하는 이유는 산문의
 * 확장자 언급(「`.kt` 경로 리터럴」 같은 설명)이 경로가 아니기 때문이다. 데이터 파일에는 조립할
 * 코드가 없으므로 경로는 **통째로** 적히고, 그래서 이 술어로 구조가 성립한다.
 */
export function kotlinPathsInText(text) {
  return [...text.matchAll(/[\w.-]+(?:\/[\w.-]+)+\.kts?\b/gu)].map((m) => m[0]);
}

const files = trackedWebFiles();
const read = (relative) => readFileSync(resolve(webRoot, relative), 'utf8');

describe('소유권 — 경로를 아는 자리가 하나다', () => {
  /** 훑기 자체가 공허하지 않은지 먼저 센다 — 0 개를 훑고 통과하는 길을 막는다. */
  it('훑기가 추적 파일 전수다 — 확장자 목록도 뿌리 목록도 없다', () => {
    expect(files.length).toBeGreaterThan(40);
    expect(files).toContain(OWNER);
    expect(files).toContain(STRIP_INTERNAL);
    // r2 가 못 보던 자리(최상위 `.js`·`.ts`)와 r3 가 못 보던 자리(데이터 파일)가 함께 들어온다.
    expect(files).toContain('eslint.config.js');
    expect(files).toContain('config/gate-tests.txt');
    // 생성물은 추적되지 않으므로 이름 집합 없이 빠진다 — 깊이 축이 사라진다.
    expect(files.some((relative) => relative.includes('node_modules/'))).toBe(false);
    expect(files.some((relative) => relative.includes('/dist/') || relative.startsWith('dist/'))).toBe(
      false,
    );
  });

  it('AST 로 볼 파일을 컴파일러 정의로 고른다 — 그 밖의 추적 파일은 텍스트로 본다', () => {
    for (const suffix of ['.js', '.cjs', '.mjs', '.jsx', '.ts', '.cts', '.mts', '.tsx']) {
      expect([...AST_SUFFIXES], `${suffix} 가 AST 대상 밖이다`).toContain(suffix);
    }
    expect(isAstFile('scripts/x.mjs')).toBe(true);
    expect(isAstFile('config/kotlin-paths.json')).toBe(false);
  });

  it('Kotlin 경로는 공유 모듈 밖의 어느 추적 파일에도 없다', () => {
    const offenders = files
      .filter((relative) => relative !== OWNER && !PROVENANCE_DATA_ALLOWED.has(relative))
      .map((relative) => {
        const text = read(relative);
        return {
          relative,
          literals: isAstFile(relative)
            ? kotlinPathLiterals(text, relative)
            : kotlinPathsInText(text),
        };
      })
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

describe('소유권 — 걷기 본체를 쥐는 자리가 둘이다', () => {
  /**
   * **r2 H-3·r3 H-1 이 여기서 닫힌다.** 걷기는 **공개 표면에 없고**(내부 모듈), 그 내부 모듈을
   * 참조하는 파일은 소유 모듈과 그 단위 test 둘이다. 술어가 **해석된 지정자**라 import 구문의
   * 종류·별칭·쿼리·재수출에 **면역**이다.
   */
  it('내부 걷기 모듈을 참조하는 파일은 소유 모듈과 그 단위 test 둘뿐이다', () => {
    const referrers = files
      .filter(isAstFile)
      .filter((relative) => referencedModules(read(relative), relative).includes(STRIP_INTERNAL))
      .sort();

    expect(referrers, '이 파일들이 걷기 본체를 쥔다 — 사본을 덧씌울 수 있다').toStrictEqual(
      [...STRIP_REF_ALLOWED].sort(),
    );
  });

  /** 양성 대조 — r3 을 지나간 **여섯 꼴 전부**를 합성 원문에서 잡는다. */
  it('술어가 import 꼴 여섯을 전부 잡는다', () => {
    const forms = {
      'named(N0)': "import { withoutKotlinComments } from './kotlin-comment-strip.mjs';",
      '별칭': "import { withoutKotlinComments as s } from './kotlin-comment-strip.mjs';",
      'namespace(N1)': "import * as ks from './kotlin-comment-strip.mjs';",
      'dynamic(N2)': "const { x } = await import('./kotlin-comment-strip.mjs');",
      'createRequire(N3)': "const m = createRequire(import.meta.url)('./kotlin-comment-strip.mjs');",
      're-export(N4)': "export { withoutKotlinComments as strip } from './kotlin-comment-strip.mjs';",
      'export *(N5)': "export * from './kotlin-comment-strip.mjs';",
      'query(N6)': "import * as k from './kotlin-comment-strip.mjs?v=1';",
      'require': "const m = require('./kotlin-comment-strip.mjs');",
    };

    for (const [name, source] of Object.entries(forms)) {
      expect(
        referencedModules(source, 'scripts/probe.mjs'),
        `${name} 이 술어를 지나간다`,
      ).toContain(STRIP_INTERNAL);
    }
  });

  /** 음성 대조 — 공유 모듈만 부르는 소비자는 걸리지 않는다(일률 거절이 아니다). */
  it('공개 표면만 쓰는 소비자는 걸리지 않는다', () => {
    const clean = "import { assertStrippedKotlin, readKotlinSource } from './kotlin-source.mjs';";

    expect(referencedModules(clean, 'scripts/probe.mjs')).toStrictEqual(['scripts/kotlin-source.mjs']);
  });

  /** 상대 경로 깊이가 달라도 **같은 키**로 선다 — 하네스는 두 칸 위에서 부른다. */
  it('다른 깊이의 상대 경로도 같은 키다', () => {
    expect(referencedModules("import x from '../../scripts/kotlin-comment-strip.mjs';", 'e2e/harness/h.mjs')).toStrictEqual(
      [STRIP_INTERNAL],
    );
  });
});

describe('소유권 — 읽기 표면이 셋으로 고정된다', () => {
  /**
   * **V12·V13 이 선 자리**(verifier r2 H-2). 앞 판의 설계 검토는 「export 집합을 들어 RED」라고
   * 적었는데 **그런 단언이 없었다** — 경로 표나 원문 reader 를 한 줄 더하면 「소비자는 원문을 쥘
   * 수 없다」가 초록인 채 무너졌다. 모듈 네임스페이스로 **정확한 집합**을 든다.
   */
  it('export 집합이 정확히 셋이다 — 걷기는 공개 표면에 없다', async () => {
    const namespace = await import('./kotlin-source.mjs');

    expect(Object.keys(namespace).sort()).toStrictEqual([
      'KOTLIN_SOURCE_KEYS',
      'assertStrippedKotlin',
      'readKotlinSource',
    ]);
  });
});

describe('소유권 — 단언이 추출 함수 전수에 선다', () => {
  /**
   * **cr r2 MEDIUM② 가 선 자리.** 호출 자리는 일곱인데 걷히지 않은 원문을 먹이는 test 는 **둘**
   * 뿐이었고, 나머지 **다섯은 단언 호출을 지워도 전건이 초록**이었다. 그 다섯은 `readKotlinSource`
   * 로만 불리기 때문이다. 그런데 §4 (2) 10 이 막겠다고 적은 것이 정확히 「입력이 이미 걷혔으니
   * 중복이라며 조용히 지우는」 길이다. 그래서 **열거가 아니라 export 전수**로 센다.
   */
  const CONSUMER_MODULES = ['e2e/harness/opening-axis.mjs', 'scripts/money-vocab-cross-lang.test.mjs'];

  /** 원문에서 **export 된 함수 선언**의 이름과 그 본문의 호출 이름들을 낸다. */
  const exportedFunctionCalls = (source, fileName) => {
    const out = new Map();
    const visit = (node) => {
      const exported =
        ts.isFunctionDeclaration(node) &&
        node.name !== undefined &&
        (node.modifiers ?? []).some((m) => m.kind === ts.SyntaxKind.ExportKeyword);
      if (exported) {
        const calls = new Set();
        const inner = (child) => {
          if (ts.isCallExpression(child) && ts.isIdentifier(child.expression)) {
            calls.add(child.expression.text);
          }
          ts.forEachChild(child, inner);
        };
        ts.forEachChild(node, inner);
        out.set(node.name.text, calls);
      }
      ts.forEachChild(node, visit);
    };
    visit(parse(source, fileName));
    return out;
  };

  it('Kotlin 원문을 받는 export 추출 함수 전수가 입력 자리에서 단언을 부른다', () => {
    const missing = [];
    for (const relative of CONSUMER_MODULES) {
      for (const [name, calls] of exportedFunctionCalls(read(relative), relative)) {
        // Kotlin 원문을 받는 함수만 대상이다 — 그 표지는 단언 또는 걷기를 부르는 것이다.
        const takesKotlin = calls.has('assertStrippedKotlin') || calls.has('withoutKotlinComments');
        if (calls.has('withoutKotlinComments')) {
          missing.push(`${relative}:${name} 가 걷기를 직접 부른다`);
        } else if (!takesKotlin && /^(kotlin|shipped)/u.test(name)) {
          missing.push(`${relative}:${name} 가 단언을 부르지 않는다`);
        }
      }
    }

    expect(missing, '추출 함수가 걷힌 상태 단언을 거치지 않는다').toStrictEqual([]);
  });

  /** 양성 대조 — 단언 호출을 지운 합성 추출 함수를 술어가 잡는다. */
  it('술어가 단언 없는 추출 함수를 잡는다', () => {
    const planted = 'export function kotlinProbe(source) {\n  return source.trim();\n}\n';
    const calls = exportedFunctionCalls(planted, 'scripts/probe.mjs');

    expect(calls.get('kotlinProbe')?.has('assertStrippedKotlin')).toBe(false);
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
    ...STRIP_REF_ALLOWED,
    STRIP_INTERNAL,
    'scripts/kotlin-source-ownership.test.mjs',
  ]);

  /**
   * 주석 표지를 인자로 넘기는 치환·탐색. 정규식 분기는 **escape 된 슬래시 둘**(또는 슬래시 + `*`)
   * 을 요구한다 — 하나만 요구하면 `replace(/\/api\/…/u, …)` 같은 **평범한 경로 정규식**이 걸린다
   * (실측: `e2e/journey/shared.ts` 가 그 꼴로 오탐이었다).
   */
  const COPY_SPELLING =
    /\.(?:replace|replaceAll|indexOf|lastIndexOf|split)\(\s*(?:'\/\/|"\/\/|`\/\/|\/\\\/(?:\\\/|\\\*))/u;

  it('걷기 사본의 흔한 표기가 어느 추적 파일에도 없다', () => {
    const offenders = files
      .filter(isAstFile)
      .filter((relative) => !MENTIONS_ALLOWED.has(relative))
      .filter((relative) => COPY_SPELLING.test(read(relative)));

    // 문면 정정(cr r2 LOW): 이 층은 `web/` 전체를 훑으므로 걸린 파일이 **소비자가 아닐 수 있다**.
    expect(
      offenders,
      '이 파일들이 주석 표지를 인자로 잘라 낸다 — 걷기 사본일 수 있다(소비자/출하 구분은 사람이 본다)',
    ).toStrictEqual([]);
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
