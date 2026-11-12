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

/** Kotlin 원문의 확장자. 자기 술어를 피하려 **조립해서** 든다(미달 ①). */
const KT = `.${'k'}${'t'}`;

/** 읽기를 소유하는 자리 — 이 파일만 Kotlin 경로를 안다. */
const OWNER = 'scripts/kotlin-source.mjs';

/**
 * 걷기 본체가 사는 **내부 모듈** — 공개 표면에 없다. 이름을 **조립**해 든다: 통째로 적으면 이
 * 파일이 아래 이름 술어에 걸려 예외 목록에 자기를 넣어야 하고, 그러면 그 목록이 구멍이 된다.
 */
const STRIP_BASENAME = `kotlin-${'comment'}-strip`;
const STRIP_INTERNAL = `scripts/${STRIP_BASENAME}.mjs`;

/** 그 내부 모듈을 참조해도 되는 자리 — 소유 모듈과 그 **단위 test** 둘이다. */
const STRIP_REF_ALLOWED = [OWNER, 'scripts/kotlin-source.test.mjs'];

/**
 * 데이터 파일 가운데 Kotlin 경로를 **출처 주석으로** 담는 자리. 여는 데 쓰이지 않고 「이 fixture 가
 * 어느 계약에서 왔는가」를 적는다 — 그래서 경로 술어의 **참 양성이지만 의도상 허용**이다.
 * 목록으로 두는 것은 열거이고 그 약점을 안다(새 fixture 가 같은 꼴을 쓰면 여기 한 줄이 늘어난다).
 * **여는 행위 축은 이 목록이 지지 않는다** — 그 자리는 `design-review.md` §4 (3) 미달 ⑤ 다.
 */
const PROVENANCE_DATA_ALLOWED = new Map([
  // 파일 전체가 아니라 **그 자리의 값 전수**를 적는다(verifier r4 M-2) — 파일 단위로 허용하면
  // 그 파일에 **새 경로**를 더하는 것까지 먹는다(B7b). 여기 적힌 값 밖은 RED 다.
  [
    'e2e/harness/fixtures/opening-axis.json',
    new Set(
      ['KonepsOpeningAxisFieldContracts', 'KonepsOpeningCompleteFieldContracts', 'MockOpeningKonepsHttp'].map(
        (stem) => `${stem}${KT}`,
      ),
    ),
  ],
]);

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

/** JS/TS 는 AST 로, 그 밖의 추적 파일은 **텍스트**로 본다(데이터 파일에는 조립할 코드가 없다). */
const AST_SUFFIXES = new Set(ts.getSupportedExtensions({ allowJs: true }).flat());
const isAstFile = (relative) => [...AST_SUFFIXES].some((suffix) => relative.endsWith(suffix));

const parse = (source, fileName) =>
  ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, false);

/**
 * **경로 축은 단어 토큰 술어다**(r6 — verifier r5 H-1). r5 는 「**점 바로 뒤**의 kt」를 봤고,
 * 그래서 `*.{kt,kts}`(점 다음이 `{`) · `/\.(?:kt|kts)$/` · `[.]kt` · `split('.').pop() === 'kt'` ·
 * 데이터 파일의 `".kt"`·glob 이 전부 지나갔다 — **r4 가 닫았던 N10 이 다시 열린 것**이 그 증거다.
 * 이제 **단어 꼴**(`\bkts?\b`)로 본다: 점에 기대지 않으므로 그 여섯이 함께 닫힌다.
 *
 * r5 문면이 적었던 「확장자로 끝난다」·「`/` 를 담는다」·「노드 종류가 무엇이다」는 이미 버렸고 — 그 셋이 각각 B4(파일 이름만) · B5(뒤에 파이프가 붙어
 * 끝이 아니다) · B6(정규식 리터럴) · B11(중괄호 자리가 다른 glob) 을 열었다. 확장자 토큰이
 * **어디에 있든** 경로로 친다.
 *
 * 술어의 정규식은 **조립해서** 만든다 — 통째로 적으면 이 파일이 자기 술어에 걸린다(미달 ①).
 */
const KT_TOKEN = new RegExp(`\\b${'k'}${'t'}s?\\b`, 'u');
const KT_FILE_TOKEN = new RegExp(`\\b[\\w-]+\\.${'k'}${'t'}s?\\b`, 'gu');

/** 노드에서 **리터럴 텍스트**를 낸다 — 문자열·템플릿 조각·정규식 리터럴·식별자. */
function literalTexts(node) {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return [node.text];
  }
  if (ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node)) {
    return [node.text];
  }
  if (ts.isRegularExpressionLiteral(node) || ts.isIdentifier(node)) {
    return [node.text];
  }
  return [];
}

const walkLiterals = (source, fileName, take) => {
  const visit = (node) => {
    for (const text of literalTexts(node)) {
      take(text);
    }
    ts.forEachChild(node, visit);
  };
  visit(parse(source, fileName));
};

/** AST 의 어느 리터럴에든 확장자 토큰이 있으면 경로다. 주석은 리터럴이 아니라 들지 않는다. */
export function kotlinPathTokens(source, fileName = 'probe.mjs') {
  const found = [];
  walkLiterals(source, fileName, (text) => {
    if (KT_TOKEN.test(text)) {
      found.push(text);
    }
  });
  return found;
}

/**
 * **걷기 획득 축은 이름 술어다**(verifier r4 H-2). r4 는 `import()`/`require` 의 **첫 인자가
 * `StringLiteral`** 일 때만 지정자를 들었다. 그래서 백틱(B1) · `new URL(...).href`(B2) ·
 * `import.meta.resolve`(B3) · 상수에 담아 넘기기(B3b) 가 전부 지나갔다 — 전부 평범한 관용구다.
 * 지정자 수집을 버리고 **이름이 어느 리터럴에든 나타나는지**를 센다 — 어떤 API 로 부르든 모듈을
 * 가리키려면 그 이름을 적어야 한다.
 */
export function mentionsStripModule(source, fileName = 'probe.mjs') {
  let found = false;
  walkLiterals(source, fileName, (text) => {
    found ||= text.includes(STRIP_BASENAME);
  });
  return found;
}

/**
 * 데이터 파일(JSON·txt 등)용 술어 — **경로 꼴**을 든다. 디렉터리 구분자를 요구하는 이유는 산문의
 * 확장자 언급(「`.kt` 경로 리터럴」 같은 설명)이 경로가 아니기 때문이다. 데이터 파일에는 조립할
 * 코드가 없으므로 경로는 **통째로** 적히고, 그래서 이 술어로 구조가 성립한다.
 */
export function kotlinPathsInText(text) {
  // 데이터 파일 — **파일 이름 토큰**을 든다(B4 의 `{"basis":"Basis.kt"}` 가 이 자리다).
  return [...text.matchAll(KT_FILE_TOKEN)].map((m) => m[0]);
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
      .filter((relative) => relative !== OWNER)
      .map((relative) => {
        const text = read(relative);
        const allowed = PROVENANCE_DATA_ALLOWED.get(relative) ?? new Set();
        const literals = (isAstFile(relative) ? kotlinPathTokens(text, relative) : kotlinPathsInText(text))
          .filter((token) => !allowed.has(token.split('/').pop()));
        return { relative, literals };
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
  it('술어가 r2~r4 의 반례 꼴을 전부 잡는다', () => {
    const planted = {
      '완전한 경로': `const p = 'workflow/src/main/kotlin/X${KT}';`,
      '조립(V1)': `const p = 'X' + '${KT}';`,
      '템플릿(V3)': `const p = \`\${repoRoot}/workflow/X${KT}\`;`,
      '확장자 변수(V4)': `const ext = '${KT}';`,
      '파일 이름만(V2)': `const p = resolve(repoRoot, 'workflow', 'X${KT}');`,
      '걸러내기(V5)': `const f = readdirSync(d).find((n) => n.endsWith('${KT}'));`,
      '파일 이름만(B4)': `const t = { basis: 'Basis${KT}' };`,
      '셸 파이프(B5)': `execSync('cat ../a/Basis${KT} | tr -d x');`,
      '정규식 리터럴(B6)': `const f = readdirSync(d).find((n) => /\\${KT}$/u.test(n));`,
      '중괄호 자리 다른 glob(B11)': `const g = globSync('a/Basis{${KT},${KT}s}');`,
      // **상설 양성 대조**(r6): r4 가 닫았던 이 꼴이 r5 의 「점 뒤 kt」 술어에서 **조용히 회귀**했다.
      '중괄호 glob N10(회귀 감시)': `const g = globSync('a/*.{${'k'}${'t'},${'k'}${'t'}s}');`,
      '두 확장자 정규식(P2)': `const f = d.find((n) => /\\.(?:${'k'}${'t'}|${'k'}${'t'}s)$/u.test(n));`,
      '문자 클래스(P3)': `const r = /[.]${'k'}${'t'}$/u;`,
      '점 없는 확장자(P5)': `const ok = n.split('.').pop() === '${'k'}${'t'}';`,
    };

    for (const [name, source] of Object.entries(planted)) {
      expect(kotlinPathTokens(source).length, `${name} 이 술어를 지나간다`).toBeGreaterThan(0);
    }
  });

  /** **음성 대조** — 키로만 읽는 소비자는 걸리지 않는다(일률 거절이 아니다). */
  it('키로만 읽는 소비자는 걸리지 않는다', () => {
    const clean = [
      "import { readKotlinSource } from './kotlin-source.mjs';",
      "const basis = readKotlinSource('basis');",
      "const moneyTs = readFileSync('src/display/moneyDisplay.ts', 'utf8');",
    ].join('\n');

    expect(kotlinPathTokens(clean)).toStrictEqual([]);
  });

  /** 주석·산문 안의 같은 글자는 리터럴이 아니다 — AST 술어라 그것을 들지 않는다. */
  it('주석 안의 확장자는 걸리지 않는다 — 글자 검색이 아니다', () => {
    expect(kotlinPathTokens(`// 정본은 Basis${KT} 다\n/* Other${KT} */\n`)).toStrictEqual([]);
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
      .filter((relative) => mentionsStripModule(read(relative), relative))
      .sort();

    expect(referrers, '이 파일들이 걷기 본체를 쥔다 — 사본을 덧씌울 수 있다').toStrictEqual(
      [...STRIP_REF_ALLOWED].sort(),
    );
  });

  /** 양성 대조 — r3·r4 를 지나간 **꼴 전부**를 합성 원문에서 잡는다(표기 축이 닫혔는가). */
  it('술어가 걷기 획득 꼴 전부를 잡는다', () => {
    const m = STRIP_INTERNAL;
    const forms = {
      'named': `import { withoutKotlinComments } from './${STRIP_BASENAME}.mjs';`,
      'namespace(N1)': `import * as ks from './${STRIP_BASENAME}.mjs';`,
      'dynamic(N2)': `const x = await import('./${STRIP_BASENAME}.mjs');`,
      'createRequire(N3)': `const r = createRequire(import.meta.url)('./${STRIP_BASENAME}.mjs');`,
      're-export(N4)': `export { withoutKotlinComments as s } from './${STRIP_BASENAME}.mjs';`,
      'export *(N5)': `export * from './${STRIP_BASENAME}.mjs';`,
      'query(N6)': `import * as k from './${STRIP_BASENAME}.mjs?v=1';`,
      '백틱(B1)': `const x = await import(\`../${m}\`);`,
      'new URL(B2)': `const x = await import(new URL('../${m}', import.meta.url).href);`,
      'import.meta.resolve(B3)': `const x = await import(import.meta.resolve('../${m}'));`,
      '상수 경유(B3b)': `const MOD = '../${m}';\nconst x = await import(MOD);`,
    };

    for (const [name, source] of Object.entries(forms)) {
      expect(mentionsStripModule(source, 'scripts/probe.mjs'), `${name} 이 술어를 지나간다`).toBe(true);
    }
  });

  /** 음성 대조 — 공개 표면만 쓰는 소비자는 걸리지 않는다(일률 거절이 아니다). */
  it('공개 표면만 쓰는 소비자는 걸리지 않는다', () => {
    const clean = "import { assertStrippedKotlin, readKotlinSource } from './kotlin-source.mjs';";

    expect(mentionsStripModule(clean, 'scripts/probe.mjs')).toBe(false);
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
   * **모집단을 이름이 아니라 데이터 흐름에서 뽑는다**(r5 보충 — cr r3 HIGH②). 앞 판은 소비자
   * 파일을 **손 목록**으로 들고 함수를 **이름 접두**(`kotlin`·`shipped`)로 골랐다. 그래서 단언을
   * 지우고 **접두 밖 이름으로 개명**하면 모집단에서 빠져 조용히 초록이었다.
   *
   * 모집단 = **`readKotlinSource(...)` 의 결과가 인자로 흘러드는 호출의 피호출자 전부**. 개명하면
   * 호출부도 같이 개명되므로 모집단이 **따라온다** — 이름에 의존하지 않는다.
   */
  const KOTLIN_SINKS = (() => {
    const sinks = new Set();
    for (const relative of files.filter(isAstFile)) {
      const tree = parse(read(relative), relative);
      // **한 단계 중간 변수**까지 따라간다(r6 — verifier r5 M-1 C6·C1). 실제 호출 열여덟 가운데
      // 여덟이 이 꼴이고, r4 에서 RED 였던 `shippedProgressLabels` 가 이 꼴로 바뀌며 회귀했다.
      // 두 단계 이상·별칭·메서드·`.trim()`·감싼 선언·spread·콜백은 **미달로 등재**한다(고치지 않음).
      const kotlinVars = new Set();
      const collectVars = (node) => {
        if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer !== undefined) {
          const init = node.initializer;
          if (ts.isCallExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'readKotlinSource') {
            kotlinVars.add(node.name.text);
          }
        }
        ts.forEachChild(node, collectVars);
      };
      collectVars(tree);

      const carriesKotlin = (arg) =>
        (ts.isCallExpression(arg) && ts.isIdentifier(arg.expression) && arg.expression.text === 'readKotlinSource') ||
        (ts.isIdentifier(arg) && kotlinVars.has(arg.text));

      const visit = (node) => {
        if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.arguments.some(carriesKotlin)) {
          sinks.add(node.expression.text);
        }
        ts.forEachChild(node, visit);
      };
      visit(tree);
    }
    return sinks;
  })();

  /** 이름 → 그 함수 본문이 부르는 이름들. **export 세 꼴**(선언·arrow·`export {}`)을 모두 든다. */
  const declaredCalls = () => {
    const out = new Map();
    for (const relative of files.filter(isAstFile)) {
      const source = read(relative);
      const visit = (node) => {
        let name;
        let body;
        if (ts.isFunctionDeclaration(node) && node.name !== undefined) {
          name = node.name.text;
          body = node;
        } else if (ts.isVariableStatement(node)) {
          const decl = node.declarationList.declarations[0];
          if (
            decl !== undefined &&
            ts.isIdentifier(decl.name) &&
            decl.initializer !== undefined &&
            (ts.isArrowFunction(decl.initializer) || ts.isFunctionExpression(decl.initializer))
          ) {
            name = decl.name.text;
            body = decl.initializer;
          }
        }
        if (name !== undefined && body !== undefined) {
          const calls = new Set();
          const inner = (child) => {
            if (ts.isCallExpression(child) && ts.isIdentifier(child.expression)) {
              calls.add(child.expression.text);
            }
            ts.forEachChild(child, inner);
          };
          ts.forEachChild(body, inner);
          out.set(name, { relative, calls });
        }
        ts.forEachChild(node, visit);
      };
      visit(parse(source, relative));
    }
    return out;
  };

  it('Kotlin 원문이 흘러드는 함수 전수가 입력 자리에서 단언을 부른다', () => {
    const declared = declaredCalls();
    const missing = [];

    expect(KOTLIN_SINKS.size, '모집단이 비었다 — 술어가 공허하다').toBeGreaterThan(2);

    for (const name of [...KOTLIN_SINKS].sort()) {
      const found = declared.get(name);
      if (found === undefined) {
        continue; // 공유 모듈 자신의 표면(`assertStrippedKotlin` 등)은 이 축이 아니다.
      }
      if (found.calls.has('withoutKotlinComments')) {
        missing.push(`${found.relative}:${name} 가 걷기를 직접 부른다`);
      } else if (!found.calls.has('assertStrippedKotlin')) {
        missing.push(`${found.relative}:${name} 가 단언을 부르지 않는다`);
      }
    }

    expect(missing, '추출 함수가 걷힌 상태 단언을 거치지 않는다').toStrictEqual([]);
  });

  /** 양성 대조 — arrow 꼴이고 이름이 접두 밖이어도 모집단에 들고 단언 부재를 잡는다. */
  it('술어가 arrow·접두 밖 이름의 단언 부재를 잡는다', () => {
    const planted = [
      "export const readPolicyTable = (source) => source.trim();",
      "const t = readPolicyTable(readKotlinSource('progressPolicy'));",
    ].join('\n');
    const tree = parse(planted, 'scripts/probe.mjs');

    let sink;
    const visit = (node) => {
      if (
        ts.isCallExpression(node) &&
        ts.isIdentifier(node.expression) &&
        node.arguments.some(
          (a) => ts.isCallExpression(a) && ts.isIdentifier(a.expression) && a.expression.text === 'readKotlinSource',
        )
      ) {
        sink = node.expression.text;
      }
      ts.forEachChild(node, visit);
    };
    visit(tree);

    expect(sink, '데이터 흐름에서 모집단을 못 뽑는다').toBe('readPolicyTable');
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
