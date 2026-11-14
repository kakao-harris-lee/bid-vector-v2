import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it, vi } from 'vitest';

import { KOTLIN_SOURCE_KEYS, withKotlinSource } from './kotlin-source.mjs';

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
 * **층마다 범위가 다르다**(r7 — verifier r6 H-1): JS/TS 는 경로 표기 전부를, **데이터 파일은
 * `이름.kt` 꼴만** 본다(확장자 단독 값·별표/중괄호 glob 은 지나간다). 걷기 이름 축만 **전 파일**
 * 이다. 닫히지 않는 것은 `design-review.md` §4 (3) 미달 ①②⑤⑥⑧⑨⑩ 과 capability map §12.3 (j)
 * 의 `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS` 가 든다.
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
 * **추출 커널 — 동일성 단언을 거치지 않는 입구다**(M11 — 팀장 조건 1).
 *
 * 공개 입구(`shipped…` 넷)는 받은 텍스트가 읽기가 내준 그것인지를 단언하고 커널로 넘긴다. 커널을
 * 직접 부르면 그 단언이 없으므로, 소비자가 커널을 쥐는 순간 ⑥ 이 다시 열린다. 그래서 참조자를
 * **소유 파일과 그 단위 test 둘**로 고정한다 — 걷기 본체를 같은 꼴로 묶어 둔 선례를 잇는다.
 */
const EXTRACT_KERNEL = 'e2e/harness/opening-axis-extract.mjs';

/**
 * **쥐는 자리와 부르는 자리의 허용이 다르다**(r1). 아래 import 그래프 술어는 「누가 **쥐는가**」를
 * 보고, 커널 쪽 호출자 프레임 판정은 「누가 **부르는가**」를 본다 — 부르는 자리는 소유자와 그 단위
 * test **둘**이고, 쥐는 자리에는 이 파일이 하나 더 든다. 이 파일이 커널을 쥐는 이유가 바로
 * **쥐어도 부를 수 없음을 재는 것**이라, 그 비대칭 자체가 구조 층의 증거다.
 */
const EXTRACT_REF_ALLOWED = [
  'e2e/harness/opening-axis.mjs',
  'e2e/harness/opening-axis.test.mjs',
  'scripts/kotlin-source-ownership.test.mjs',
];

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
/**
 * 데이터 파일용 — **확장자 토큰 자체**를 든다(M11, ⑩). 파일 이름을 요구하던 앞 판은 확장자 단독
 * 값과 별표/중괄호 glob 을 지나보냈다. 앞의 이름을 요구하지 않는 대신 **`#` 줄 제외 + allowlist
 * 차감**이 오탐을 0 으로 유지한다(그 둘이 한 묶음의 처방이다).
 */
const KT_FILE_OR_EXTENSION_TOKEN = new RegExp(`\\b${'k'}${'t'}s?\\b`, 'giu');

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
 * **해석된 import 대상 전수**(M11 — 팀장 조건 1: 문자열 grep 이 아니라 **import 그래프**).
 *
 * 정적 import · 재수출(`export … from`) · `export *` · 리터럴 동적 import 를 모두 들고, 지정자를
 * **그 파일 기준으로 해석**한다. 그래서 별칭(`import { x as y }`)·재수출·쿼리(`?v=1`)·다른
 * 디렉터리에서 오는 긴 상대 경로가 **같은 대상 하나로 접힌다** — 표기를 세지 않는다.
 *
 * 비리터럴 지정자(런타임 조립)는 해석되지 않는다 — 의도적 은닉이고 경계 밖이다.
 */
export function resolvedImports(source, fileName) {
  const fromDir = dirname(resolve(webRoot, fileName));
  const targets = new Set();
  const take = (specifier) => {
    if (specifier === undefined || !specifier.startsWith('.')) {
      return;
    }
    const withoutQuery = specifier.split('?')[0];
    targets.add(relative(webRoot, resolve(fromDir, withoutQuery)));
  };
  const visit = (node) => {
    if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) {
      const specifier = node.moduleSpecifier;
      if (specifier !== undefined && ts.isStringLiteral(specifier)) {
        take(specifier.text);
      }
    }
    if (
      ts.isCallExpression(node) &&
      node.expression.kind === ts.SyntaxKind.ImportKeyword &&
      node.arguments[0] !== undefined &&
      ts.isStringLiteral(node.arguments[0])
    ) {
      take(node.arguments[0].text);
    }
    ts.forEachChild(node, visit);
  };
  visit(parse(source, fileName));
  return [...targets];
}

/**
 * 데이터 파일(JSON·txt 등)용 술어 — **확장자 토큰**을 든다(M11 — ⑩ 을 닫는다).
 *
 * 앞 판은 `이름.kt` 꼴만 보았다. 그래서 확장자 단독 값(`{"ext": ".kt"}`)·별표 glob(`*.kt`)·중괄호
 * glob(`X.{kt,kts}`)·확장자 목록 값이 전부 지나갔다 — 앞에 단어 문자가 없어 파일 이름 꼴이 아니기
 * 때문이다. 단어 토큰이 그 넷을 함께 닫는다.
 *
 * **주석 개념이 없다**(M12 — ⓕ-2). M11 판은 `#` 로 시작하는 줄을 **주석으로** 빼서 등록부 산문의
 * 확장자 언급을 넘겼고, 그 규약이 곧 미탐이었다 — `#` 로 시작하는 **데이터** 줄을 보지 못했다.
 * 형식마다 주석 구문 표(`#` · `//` · `<!-- -->`)를 두는 길은 **목록**이고, 「문자열 안의 `//` 를
 * 주석으로 읽는」 함정이 딸려 온다(이 저장소가 Kotlin 에서 밟아 걷기 모듈을 만든 그 함정이다).
 *
 * 그래서 예외는 **파일별 값 차감 하나뿐**이다. allowlist 는 토큰을 걸러내는 것이 아니라 **값을
 * 차감한다** — 단어 토큰은 파일 이름을 남기지 않으므로 뒤에서 걸러낼 수 없다. 새 형식이 생기면
 * 가장 엄격한 쪽으로 떨어진다: 조용한 미탐이 아니라 **시끄러운 오탐**이고, 그쪽이 안전한 실패다.
 *
 * **대소문자를 가리지 않는다**(M12 — ⓕ-1). 런타임 층과 같은 성질이다. 「Linux 에서 대문자 표기가
 * 아무것도 열지 못한다」는 파일시스템의 성질에 기대지 않는다 — WSL 의 Windows 마운트와 macOS 는
 * 가리지 않는다. 같은 플래그를 **AST 술어에는 붙이지 않았다**: 그 술어는 식별자도 보므로 게이트
 * 자신의 상수 이름이 걸렸다(실측 — 등재).
 *
 * **두 글자 단어 토큰의 성질**(r1 — cr LOW): 오늘 트리에서 오탐은 0 이지만, 경계뿐인 두 글자라
 * 임의 토큰 묶음(base64·hex 조각)이나 같은 글자의 상호를 담은 **미래의 fixture** 가 걸린다. 그때의
 * 탈출구는 allowlist **차감**뿐이다 — 그 차감을 「구멍」으로 읽지 말 것. 술어를 좁히면(앞에 파일
 * 이름을 요구하면) 확장자 단독 값과 glob 이 다시 열린다.
 */
export function kotlinPathsInText(text, allowedValues = new Set()) {
  let body = text;
  for (const value of allowedValues) {
    body = body.split(value).join('');
  }
  return [...body.matchAll(KT_FILE_OR_EXTENSION_TOKEN)].map((match) => match[0]);
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
        const literals = isAstFile(relative)
          ? kotlinPathTokens(text, relative).filter((token) => !allowed.has(token.split('/').pop()))
          : // 데이터 파일은 allowlist 를 **차감으로** 받는다 — 단어 토큰이 파일 이름을 남기지
            // 않으므로 뒤에서 걸러낼 수 없다(처방의 둘째 조각).
            kotlinPathsInText(text, allowed);
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
      // **brace glob 세 꼴 상설**(cr r4 HIGH①): r4 가 닫은 꼴이 r5 에서 회귀했고, 변이 재생을
      // B 계열로만 줄인 것이 그 회귀를 놓친 원인이다. 세 꼴을 모두 여기 둔다.
      '중괄호 glob 두 확장자(N10)': `const g = globSync('a/X.{${'k'}${'t'},${'k'}${'t'}s}');`,
      '중괄호 glob 섞인 확장자': `const g = globSync('a/*.{${'k'}${'t'},java}');`,
      '중괄호 glob 하나': `const g = globSync('a/X.{${'k'}${'t'}}');`,
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
      "import { withKotlinSource } from './kotlin-source.mjs';",
      "const basis = withKotlinSource('basis', countEntries);",
      "const moneyTs = readFileSync('src/display/moneyDisplay.ts', 'utf8');",
    ].join('\n');

    expect(kotlinPathTokens(clean)).toStrictEqual([]);
  });

  /** 주석·산문 안의 같은 글자는 리터럴이 아니다 — AST 술어라 그것을 들지 않는다. */
  it('주석 안의 확장자는 걸리지 않는다 — 글자 검색이 아니다', () => {
    expect(kotlinPathTokens(`// 정본은 Basis${KT} 다\n/* Other${KT} */\n`)).toStrictEqual([]);
  });

  /**
   * **데이터 파일 술어 — 확장자 단독 값·glob·대소문자·`#` 데이터 줄까지 든다**(M11 ⑩ · M12 ⓕ-1·ⓕ-2).
   *
   * M11 판은 둘을 지나보냈다. **대문자**(술어에 `i` 가 없었다)와 **`#` 로 시작하는 데이터 줄**
   * (그 규약이 그 줄을 주석으로 읽었다). 둘 다 이 slice 가 닫는다 — 수치는 `commands.md`.
   */
  it('데이터 파일 술어가 확장자 단독 값과 glob 을 든다', () => {
    const extension = `${'k'}${'t'}`;
    const planted = {
      '이름 꼴(기존)': `{"basis": "Basis.${extension}"}`,
      'P6 확장자 단독 값': `{"ext": ".${extension}"}`,
      'P7 별표 glob': `{"glob": "../**/*.${extension}"}`,
      'P7b 중괄호 glob': `{"glob": "a/X.{${extension},${extension}s}"}`,
      'P7c 확장자 목록 값': `{"ext": ["${extension}", "${extension}s"]}`,
      // **ⓕ-1 대소문자** — 런타임 층과 같은 성질을 쓴다. 「Linux 에서 그 표기가 아무것도 열지
      // 못한다」는 **파일시스템의 성질에 기대지 않는다**(WSL 의 Windows 마운트·macOS 는 가리지
      // 않는다). 글자를 조립해 드는 것은 이 파일이 자기 술어에 걸리지 않게 하려는 것이다.
      'ⓕ-1 대문자 glob': `{"glob": "../**/*.${extension.toUpperCase()}"}`,
      'ⓕ-1 섞인 대소문자': `{"basis": "Basis.${'K'}${'t'}"}`,
      // **ⓕ-2 `#` 로 시작하는 데이터 줄** — 술어에 주석 개념이 없다(아래 음성 대조의 사유).
      'ⓕ-2 # 로 시작하는 데이터 줄': `#a/Basis.${extension}\n`,
      'ⓕ-2 들여쓴 # 데이터 줄': `  #a/Basis.${extension}\n`,
    };

    for (const [name, text] of Object.entries(planted)) {
      expect(kotlinPathsInText(text).length, `${name} 이 술어를 지나간다`).toBeGreaterThan(0);
    }
  });

  /**
   * **음성 대조 — 탈출구는 값 차감 하나다**(M12 ⓕ-2).
   *
   * 앞 판은 `#` 로 시작하는 줄을 **주석으로** 빼서 등록부 산문의 확장자 언급을 넘겼고, 그
   * 규약이 곧 미탐이었다. 형식마다 주석 구문 표(`#` · `//` · `<!-- -->`)를 두는 길은 **목록**
   * 이고, 「문자열 안의 `//` 를 주석으로 읽는」 함정이 딸려 온다 — 이 저장소가 Kotlin 에서
   * 밟아 걷기 모듈을 만든 바로 그 함정이다.
   *
   * 그래서 **주석 개념을 술어에서 없앤다.** 예외는 파일별 **값 차감** 하나뿐이고, 새 형식은
   * 가장 엄격한 쪽(전수 적용)으로 떨어진다 — 조용한 미탐이 아니라 시끄러운 오탐이다. 그
   * 대가로 등록부 산문 한 줄의 표기를 고쳤다(`config/gate-tests.txt` — 오늘 차감 0).
   */
  it('allowlist 값만 데이터 파일 술어를 지나간다 — 주석 규약은 없다', () => {
    const extension = `${'k'}${'t'}`;
    const allowed = new Set([`KonepsOpeningAxisFieldContracts.${extension}`]);

    expect(kotlinPathsInText(`{"from": "KonepsOpeningAxisFieldContracts.${extension}"}`, allowed)).toStrictEqual(
      [],
    );
    // 확장자 토큰이 없는 데이터는 그대로 지나간다 — 일률 거절이 아니다.
    expect(kotlinPathsInText('scripts/x.mjs\nconfig/y.json\n')).toStrictEqual([]);
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
      // 데이터 파일도 본다(cr r4 MEDIUM) — `package.json` 의 `imports` 별칭이나 JSON 지정자로
      // 가리키는 길이 AST 필터 밖에 있었다. 오늘 데이터 파일 쪽 적중은 0 이다.
      .filter((relative) => (isAstFile(relative) ? mentionsStripModule(read(relative), relative) : read(relative).includes(STRIP_BASENAME)))
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
    const clean = "import { withKotlinSource } from './kotlin-source.mjs';";

    expect(mentionsStripModule(clean, 'scripts/probe.mjs')).toBe(false);
  });

});

/**
 * **추출 커널을 쥐는 자리가 둘이다.** 술어는 **해석된 import 그래프**이므로 별칭·재수출·쿼리·
 * 리터럴 동적 import·다른 디렉터리의 긴 상대 경로가 전부 같은 대상으로 접힌다.
 *
 * **이 층은 보조다**(r1 — verifier H-1). 주장은 「**지정자로 적힌** 획득」까지이고, 확장자 없는
 * 정적 import·`createRequire`·템플릿 `import()`·`vi.importActual` 은 이 술어 밖이다. 그 축을 닫는
 * 것은 커널 쪽 **호출자 프레임** 판정이고(아래 묶음), 이 층은 그보다 이른 시점에 **이름으로**
 * 알려 주는 값 때문에 남긴다.
 */
describe('소유권 — 추출 커널을 쥐는 자리가 둘이다', () => {
  it('커널을 쥐는 파일은 허용 목록뿐이다', () => {
    const referrers = files
      .filter(isAstFile)
      .filter((relativePath) => resolvedImports(read(relativePath), relativePath).includes(EXTRACT_KERNEL))
      .sort();

    expect(referrers, '이 파일들이 단언을 거치지 않는 입구를 쥔다').toStrictEqual(
      [...EXTRACT_REF_ALLOWED].sort(),
    );
  });

  /** 양성 대조 — 제3 파일이 어떤 표기로 가리든 든다. 표기를 세지 않는 것이 이 술어의 성질이다. */
  it('술어가 제3 파일의 획득 꼴 전부를 잡는다', () => {
    const forms = {
      named: "import { openingOperationPathsOf } from '../e2e/harness/opening-axis-extract.mjs';",
      별칭: "import { openingOperationPathsOf as f } from '../e2e/harness/opening-axis-extract.mjs';",
      namespace: "import * as kernel from '../e2e/harness/opening-axis-extract.mjs';",
      재수출: "export { openingOperationPathsOf } from '../e2e/harness/opening-axis-extract.mjs';",
      'export *': "export * from '../e2e/harness/opening-axis-extract.mjs';",
      동적: "const kernel = await import('../e2e/harness/opening-axis-extract.mjs');",
      쿼리: "import * as kernel from '../e2e/harness/opening-axis-extract.mjs?v=1';",
      '정규화가 필요한 경로': "import * as k from '../e2e/./harness/../harness/opening-axis-extract.mjs';",
    };

    for (const [name, source] of Object.entries(forms)) {
      expect(resolvedImports(source, 'scripts/probe.mjs'), `${name} 이 술어를 지나간다`).toContain(
        EXTRACT_KERNEL,
      );
    }
  });

  /**
   * **구조 층 — 부르는 순간 호출자 프레임이 선다**(r1 — verifier H-1). 위 import 그래프 술어는
   * **지정자 문자열**을 보는 보조 층이라 평범한 획득 꼴 넷(확장자 없는 정적 import · `createRequire` ·
   * 템플릿 `import()` · `vi.importActual`)이 지나갔다. 획득 꼴을 더 열거하지 않고 **호출자**를 묻는다 —
   * 이 test 파일은 허용 둘이 아니므로, **어떤 꼴로 얻었든** 부르면 거부된다.
   *
   * 이 단언 자체가 그 증거다: 이 파일은 커널을 평범한 정적 import 로 얻었고, 그래도 거부된다.
   */
  it('허용 밖 파일이 커널을 부르면 거부된다 — 획득 꼴과 무관하다', async () => {
    const specifier = '../e2e/harness/opening-axis-extract.mjs';
    // **상설 양성 대조**: 보조 층을 지나간 꼴 넷(K1~K4)을 여기 둔다 — 그 넷이 r1 의 HIGH 였고,
    // 구조 층이 그것을 꼴과 무관하게 받는다는 것이 이 묶음의 주장이다.
    const namespaces = [
      ['K1 확장자 없는 정적 import', await import('../e2e/harness/opening-axis-extract')],
      ['K2 createRequire', createRequire(import.meta.url)(specifier)],
      ['K3 템플릿 import()', await import(`../e2e/harness/opening-axis-extract.${'mjs'}`)],
      ['K4 vi.importActual', await vi.importActual(specifier)],
      // **리터럴 지정자**로도 한 번 얻는다 — 이 파일이 보조 층의 허용 목록에 드는 이유가 이 줄이다.
      // 계산된 지정자로만 얻으면 보조 층은 이 파일을 **보지 못하고**(그 층의 주장이 「지정자로 적힌
      // 획득」까지다), 그러면 이 파일이 자기 게이트를 비켜 가는 꼴이 된다.
      ['정적 import(리터럴)', await import('../e2e/harness/opening-axis-extract.mjs')],
    ];

    for (const [name, namespace] of namespaces) {
      expect(() => namespace.openingOperationPathsOf('val x = 1'), `${name} 이 거부되지 않는다`).toThrow(
        /직접 불렀다/u,
      );
      expect(() => namespace.detailAxisCountsOf('val x = 1')).toThrow(/직접 불렀다/u);
      expect(() => namespace.noticeListOperationsOf('val x = 1')).toThrow(/직접 불렀다/u);
      expect(() => namespace.progressLabelsOf('val x = 1')).toThrow(/직접 불렀다/u);
    }
  });

  /** 음성 대조 — 공개 입구만 쓰는 소비자는 걸리지 않는다(여정 하네스가 그 자리다). */
  it('공개 입구만 쓰는 소비자는 걸리지 않는다', () => {
    const clean = [
      "import { shippedDetailAxisCounts } from './opening-axis.mjs';",
      "import { withKotlinSource } from '../../scripts/kotlin-source.mjs';",
      "import ts from 'typescript';",
    ].join('\n');

    expect(resolvedImports(clean, 'e2e/harness/probe.mjs')).not.toContain(EXTRACT_KERNEL);
  });
});

describe('소유권 — 읽기 표면이 둘로 고정된다', () => {
  /**
   * **V12·V13 이 선 자리**(verifier r2 H-2). 앞 판의 설계 검토는 「export 집합을 들어 RED」라고
   * 적었는데 **그런 단언이 없었다** — 경로 표나 원문 reader 를 한 줄 더하면 「소비자는 원문을 쥘
   * 수 없다」가 초록인 채 무너졌다. 모듈 네임스페이스로 **정확한 집합**을 든다.
   *
   * **셋에서 둘로 줄었다**(M12 — ⓐ). 원문을 **문자열로 내주던** 표면과 소비자가 **부르기로
   * 약속했던** 출처 단언이 함께 사라지고, 추출 함수를 넘겨받는 읽기 하나가 남았다. 이 등식이
   * ⓐ 의 **구조적 대체물**이다 — 텍스트를 내주는 표면이 한 줄 늘면 여기가 먼저 붉어진다
   * (앞 판은 그 자리를 「추출 함수 전수가 단언을 부르는가」라는 모집단 추적으로 지켰다).
   */
  it('export 집합이 정확히 둘이다 — 걷기도 원문 reader 도 공개 표면에 없다', async () => {
    const namespace = await import('./kotlin-source.mjs');

    expect(Object.keys(namespace).sort()).toStrictEqual(['KOTLIN_SOURCE_KEYS', 'withKotlinSource']);
  });
});

/**
 * **지운 자리 — 「단언이 추출 함수 전수에 선다」 모집단 등식**(M12 — ⓐ).
 *
 * 여기에는 추적 AST 파일 전수를 **두 번 파싱해** ① `readKotlinSource(...)` 의 결과가 인자로
 * 흘러드는 호출의 피호출자를 모집단으로 모으고 ② 그 이름의 선언 전부가 출처 단언을 부르는지
 * 세는 기계가 있었다. 지운 사유는 **그 질문의 전제가 사라졌기 때문**이다 — 읽기가 원문을
 * 문자열로 내주지 않으므로 「추출 함수가 단언을 부르는가」를 물을 대상이 없다.
 *
 * 그 기계는 **한 홉까지만** 따라갔고, 두 홉으로 받는 추출 함수를 모집단에서 놓쳤다(직접 인자
 * 꼴은 1 failed · 두 홉 꼴은 조용한 초록 — 이 slice 의 착수 실측). 홉을 더 쫓는 길은 끝나지
 * 않는다(M10/10D 가 그 자리에서 재작업 상한을 넘겼다).
 *
 * **대체 주장 셋**이 그 보증을 나눠 진다.
 * 1. 읽기의 **export 집합이 둘**이다(위 묶음) — 텍스트를 내주는 표면이 늘면 거기서 붉어진다.
 * 2. 읽기가 **원문을 그대로 돌려주는 추출을 거부한다**(아래 묶음 · 하네스 입구 test).
 * 3. 커널은 **읽기 모듈과 자기 단위 test 만** 부를 수 있다(위 호출자 프레임 묶음).
 *
 * 흐름의 꼴(두 홉·별칭·다듬기·spread·콜백)은 **주어를 잃었다**: 원문은 넘긴 함수의 매개변수로만
 * 존재하므로 소비자 쪽에 그 값을 나르는 흐름이 없다.
 */

describe('읽기 집합 — 한 자리에 전수로 선다', () => {
  it('읽기 집합은 일곱이고 키 전수가 읽힌다', () => {
    // 착수 때 여덟이었다. 명령 축은 10B(#149)의 openapi `enum` 승격으로 web 에서 읽지 않는다 —
    // 수를 줄인 것이 아니라 **읽지 않는 파일을 표에서 뺀** 것이다.
    expect(KOTLIN_SOURCE_KEYS).toHaveLength(7);

    for (const key of KOTLIN_SOURCE_KEYS) {
      // 넘기는 것은 **추출 함수**다(M12 — ⓐ). 길이를 재는 함수를 넘기는 것은 원문을 그대로
      // 돌려주면 읽기가 거부하기 때문이고(항등 함수 후조건), 그래서 이 대조도 추출을 한다.
      expect(() => withKotlinSource(key, (text) => text.length), `${key} 를 읽지 못한다`).not.toThrow();
      expect(withKotlinSource(key, (text) => text.length), `${key} 가 빈 원문이다`).toBeGreaterThan(0);
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
    expect(() => withKotlinSource('없는키', (text) => text.length)).toThrow(/읽기 집합/u);
  });

  /**
   * **추출 함수를 받지 못하면 던진다**(M12 — ⓐ). 인자를 빠뜨린 호출이 `undefined` 를 내고
   * 조용히 지나가면 그 자리의 등식이 공허해진다 — 읽기의 계약이 「값을 내준다」가 아니라
   * 「함수를 받아 부른다」이므로 그 인자는 선택이 아니다.
   */
  it('추출 함수를 받지 못하면 던진다', () => {
    expect(() => withKotlinSource('basis')).toThrow(/추출 함수/u);
    expect(() => withKotlinSource('basis', '함수가 아니다')).toThrow(/추출 함수/u);
  });

  /**
   * **원문은 넘긴 함수 안에서만 산다**(M12 — ⓐ). 남는 유출 길은 **항등 함수**이고 읽기가 그
   * 결과를 보고 거부한다. 문자열 동일성이라 **값이 같은 사본**까지 들지만, 값이 다른 사본은
   * 지나간다 — 그 잔여는 등재된 것이다(추출은 텍스트를 쥐어야 성립한다).
   */
  it('원문을 그대로 돌려주는 추출은 거부된다', () => {
    expect(() => withKotlinSource('basis', (text) => text)).toThrow(/원문을 그대로/u);
    expect(() => withKotlinSource('basis', (text) => text.slice(0))).toThrow(/원문을 그대로/u);
    expect(() => withKotlinSource('basis', (text) => String(text))).toThrow(/원문을 그대로/u);
    // 음성 대조 — 값이 다른 사본은 지나간다(일률 거절이 아니다).
    expect(withKotlinSource('basis', (text) => `${text} `).length).toBeGreaterThan(0);
  });

  /** 읽기가 걷기를 떼지 못한다 — 호출을 AST 로 들고 거동으로도 잰다. */
  it('읽기는 걷기를 거쳐서만 값을 낸다', () => {
    const tree = parse(read(OWNER), OWNER);

    let callsStripInsideReader = false;
    const visit = (node) => {
      if (ts.isFunctionDeclaration(node) && node.name?.text === 'withKotlinSource') {
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

    expect(callsStripInsideReader, '읽기가 공유 걷기를 호출하지 않는다').toBe(true);
    expect(withKotlinSource('provenance', (text) => text.includes('/**'))).toBe(false);
  });
});

/**
 * **원문은 읽기 모듈 밖으로 나가지 않는다**(M12 — ⓐ. M11 의 「내준 텍스트와의 동일성」을 잇는다).
 *
 * M11 판의 기준은 **출처**였다 — 읽기가 내준 텍스트 집합을 쥐고 소비자가 넘긴 값이 그 원소인지
 * 보았다. 그 단언은 **손댄 텍스트를 어떤 흐름의 꼴로 와도** 같은 자리에서 던졌고(두 홉·별칭·
 * 다듬기·spread·콜백 전부 실측), 그래서 ⑥ 에서 꼴을 세는 일이 끝났다.
 *
 * 남은 것은 **그 단언을 아예 부르지 않는** 추출 함수였다(ⓐ). 그 자리를 닫으려고 방향을 뒤집어,
 * 원문을 **문자열로 내주지 않는다** — 소비자는 추출 함수를 넘기고 읽기가 그것을 부른다. 그래서
 * 「흐름의 꼴」이라는 축 자체가 사라진다: 소비자 쪽에 원문을 나르는 흐름이 없다.
 *
 * 아래가 그 성질의 실측이다. 「던지는가」가 아니라 **텍스트가 어디까지 가는가**를 잰다.
 */
describe('읽기 — 원문은 넘긴 함수 안에서만 산다 (ⓐ)', () => {
  it('넘긴 함수가 받는 것은 걷힌 텍스트다 — 주석이 닿지 않는다', () => {
    const seen = withKotlinSource('provenance', (text) => ({
      hasBlockComment: text.includes('/**'),
      length: text.length,
    }));

    expect(seen.hasBlockComment, '걷히지 않은 텍스트가 추출에 닿는다').toBe(false);
    expect(seen.length, '빈 원문이 넘어왔다 — 이 대조가 공허하다').toBeGreaterThan(0);
  });

  /**
   * **추출의 결과만 밖으로 나온다.** M11 의 동일성 단언이 받던 「손댄 텍스트」 꼴들은 이제 넘긴
   * 함수 **안**에서 일어나고, 그 결과는 추출한 값이다 — 밖으로 나온 값이 원문과 같은지는 읽기가
   * 본다(위 묶음의 항등 함수 후조건).
   */
  it('같은 키를 두 번 읽어도 같은 값을 낸다 — 읽기가 상태를 지니지 않는다', () => {
    const first = withKotlinSource('basis', (text) => text.length);
    const second = withKotlinSource('basis', (text) => text.length);

    expect(first).toBe(second);
  });

  /**
   * **추출 안에서 던지면 그대로 올라온다** — 읽기가 예외를 삼키지 않는다. 삼키면 「꼴이 바뀌면
   * 던진다」로 닫아 둔 추출 술어들의 계약이 읽기 자리에서 무너진다.
   */
  it('추출이 던진 예외를 읽기가 삼키지 않는다', () => {
    expect(() =>
      withKotlinSource('basis', () => {
        throw new Error('추출이 꼴을 다룰 수 없다');
      }),
    ).toThrow(/추출이 꼴을 다룰 수 없다/u);
  });

  /** 추가 인자는 추출 함수에 **그대로** 전달된다(어휘 등식이 쓰는 두 번째 인자의 자리다). */
  it('추가 인자는 추출 함수로 그대로 간다', () => {
    const tail = withKotlinSource('basis', (text, suffix) => `${text.length}${suffix}`, '-개');

    expect(tail.endsWith('-개')).toBe(true);
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
