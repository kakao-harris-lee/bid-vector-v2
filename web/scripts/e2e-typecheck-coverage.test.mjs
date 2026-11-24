import { readFileSync, readdirSync, realpathSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

/**
 * M15/15L — **`web/e2e/**` 가 실제로 타입 검사를 받는가**(`OPEN-M15-E2E-TYPECHECK`).
 *
 * 15L 이 세운 층은 `tsconfig.e2e.json` 과 「`typecheck` 가 그 프로젝트도 돈다」 둘이다. 수정
 * 라운드 1 전의 술어는 **지목과 축 여섯**에 걸려 있었고, 그래서 아래가 전부 **typecheck 초록 ·
 * 덮개 초록**으로 지나갔다(verifier H-1 · cr M-1·M-2 실측):
 *
 *   `"noCheck": true` 한 줄 · `tsc --noCheck …` · `… -p tsconfig.e2e.json || true` ·
 *   `… ; true` · `strict: true` 아래 `strictNullChecks: false`.
 *
 * **지목을 세는 것으로는 닫히지 않는다** — 검사를 끄는 길이 지목을 건드리지 않기 때문이다.
 * 그래서 이 파일이 재는 것을 셋으로 바꾼다:
 *
 *   ① **진단 자체**. e2e 프로그램을 이 test 가 직접 세우고(`noCheck` 는 **강제로 끈다**)
 *      `ts.getPreEmitDiagnostics` 가 0 인지 본다. 스크립트 문면이 무엇이든 W-6 이 타입 검사를
 *      든다 — `|| true` 도 `--noCheck` 도 이 칸을 지나가지 못한다. 그 칸이 공허하지 않다는 것은
 *      **같은 함수**에 심은 오류를 먹여 보인다(양성 대조).
 *   ② **유효 옵션 전체 등식**. 축을 열거하지 않는다 — 두 프로젝트의 유효 옵션을 **전 키** 비교하고
 *      **허용 차이만** 집합으로 선언한다(그 집합은 실측해서 만들었다). strict 계열은 메타 플래그가
 *      접히지 않으므로 컴파일러 자신의 `getStrictOptionValue` 로 푼다.
 *   ③ **명령의 꼴**. `&&` 로 쪼갠 조각이 각각 `tsc [--noEmit] -p <project>` **그대로**여야 한다.
 *      `||`·`;`·괄호·`echo`·그 밖의 플래그는 그 술어에서 바로 떨어진다.
 *
 * 덮는 자리(전수 ⊆ 프로그램)와 **상한**(프로그램 ⊆ `web/`)도 함께 본다. 전수는 `git ls-files` 가
 * 아니라 **디스크 훑기**로 센다 — 아직 추적되지 않은 새 spec 이 술어 밖이면 그 커밋이 조용하다.
 *
 * **M15/15O — 넷째 칸이 두 프로그램에 걸린다**(`OPEN-M15-SRC-TS-COMMENT-DISABLE` (a)·(c)ⓘ).
 * 「타입 억제 지시 0」의 대상이 15L 에서는 `e2e/` 디스크 전수 + 뿌리 설정이었고, 그 경계 밖의
 * 둘(화면 레인의 `src/**` · e2e 프로그램이 끌어들이는 `e2e/` 밖 `.ts`)이 **네 층을 모두 지났다**.
 * 지금 그 칸은 **화면·e2e 두 프로그램의 `node_modules` 밖 전수**를 본다 — 디스크 glob 이 아니라
 * 프로그램 집합이므로 「파일을 옆 디렉터리로 옮긴다」가 길이 되지 않는다. 같은 OPEN 의 (b)
 * (eslint 규칙이 미치는 범위)는 `scripts/ban-ts-comment-reach.test.mjs` 가, (c)ⓙ
 * (`referencedFiles`·`libReferenceDirectives`)는 W-4 ④ 가 받는다.
 */

const webRoot = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));

/** 이 층이 덮어야 하는 자리 — e2e 레인의 TS 전수와 뿌리의 playwright 설정. */
const E2E_DIR = 'e2e';
/** 화면 레인의 디스크 전수 뿌리 — 래칫이 여기를 훑는다(r1 cr H1). */
const SRC_DIR = 'src';
/** `.ts`·`.tsx`·`.mts`·`.cts` — `include` 와 **같은 확장자 집합**이다(비대칭이면 한쪽이 조용하다). */
const TS_FILE = /\.[cm]?tsx?$/;
const ROOT_CONFIG = /^playwright.*\.config\.[cm]?tsx?$/;

/** 조각에 허용하는 플래그 — 착수 base 의 명령에 있던 것뿐이다. */
const ALLOWED_SEGMENT_FLAGS = new Set(['--noEmit']);
const PROJECT_FLAGS = new Set(['-p', '--project']);

/**
 * 두 프로젝트의 유효 옵션이 **달라도 되는 키**. 열거하는 것은 축이 아니라 **이 면제 자체**이고,
 * 목록은 실측해서 만들었다(두 설정의 전 키 비교에서 실제로 갈린 다섯). 새 축이 생겨 갈리면
 * 면제 밖이므로 RED 다 — 축을 적어 두는 쪽과 반대 방향이다.
 */
const OPTION_EXEMPT = new Set([
  'configFilePath', // 설정 파일 자신의 경로
  'rootDir', //       화면 경계의 전제(e2e 프로젝트에는 없다 — W-4 가 화면 쪽을 단언한다)
  'allowJs', //       e2e 만 켠다 — `.mjs` 에서 타입을 얻는다
  'checkJs', //       e2e 만 끈다 — `scripts/**` 를 새 검사 표면으로 들이지 않는다
  'types', //         e2e 만 `node` 를 더한다
]);

/**
 * strict 메타 플래그가 접지 않는 하위 축 — `options[f] ?? options.strict` 로 평가해야 한다.
 *
 * **열거하지 않는다**(r2 cr N-1). 손으로 적은 여덟은 컴파일러가 strict 계열이라고 말하는 아홉에서
 * `strictBuiltinIteratorReturn` 하나가 빠져 있었고, **양쪽을 함께** 끄면 등식도 못박기도 초록이었다
 * (실측). 그 꼴은 이 파일이 고치려던 바로 그 열거 결함이다 — 컴파일러 자신에게 묻는다.
 */
const STRICT_FAMILY = ts.optionDeclarations
  .filter((declaration) => declaration.strictFlag === true)
  .map((declaration) => declaration.name)
  .sort();

/**
 * 컴파일러에게 **조용히 하라고 말하는** 주석의 이름. 파일에 이것이 있으면 타입 층이 그 자리를
 * 보지 않는다 — 그리고 그 억제는 `eslint-disable-next-line` 과 짝지으면 lint 층도 지나간다
 * (r2 verifier M-2 실측: lint 0 · typecheck 0 · 덮개 0). 그래서 **eslint 주석으로 끌 수 없는
 * 층**에서 같은 것을 본다.
 *
 * **M15/15O — 보조 그물 ⓑ 는 주석 본문의 줄머리에서만 센다.** 15L 판은 주석 토큰 **어디에나** 이
 * 꼴이 있으면 셌고, 그대로 화면 레인까지 넓히면 **산문이 붉는다**(실측: `src` 전수에 실해 0 ·
 * 산문 언급 하나).
 *
 * **ⓐ 가 정의이고 ⓑ 는 그 바깥을 더 받는다**(r1 cr M1·M3). ⓑ 는 ⓐ 의 **상위집합이 아니다** —
 * 둘은 서로의 사각을 메우는 관계이고, ⓐ 를 「중복」이라며 걷어내면 실제 구멍이 열린다. 실측으로
 * 양쪽을 적는다.
 *
 *   **ⓐ 만 잡는 꼴**: 슬래시와 별표가 섞인 머리. TS 의 다줄 지시 정규식은 그 둘의 **임의 교차**를
 *   허용한다(`^\s*(?:\/|\*)*\s*@…`). 15O 첫 판의 걷개는 슬래시 한 번 · 별표 한 번이라 그 꼴을
 *   놓쳤다 — 지금은 같은 꼴 한 번으로 걷어 **둘 다** 잡는다.
 *   **ⓑ 만 잡는 꼴**: 파일 머리의 블록 주석 `nocheck` 와 JSDoc 줄의 세 이름. 컴파일러는 오늘
 *   이것을 **인지하지 않지만** 자리만 옮기면 먹는다(막는 쪽이므로 넓은 편이 안전한 방향이다).
 *
 * **「블록 주석은 컴파일러가 무시한다」는 틀렸다**(r1 cr M3) — 블록 주석의 `ignore`·`expect-error`
 * 는 인지되고 실제로 억제한다. ⓑ 를 **홀로** 재는 칸과 산문 음성 대조가 아래 대조 묶음에 있다.
 */
const TS_SILENCING_DIRECTIVE = /^@ts-(?:nocheck|ignore|expect-error)\b/u;

/** 주석 한 줄에서 주석 표지(`//`·`/*`·JSDoc 의 `*`)와 BOM·여백을 걷어 **본문의 줄머리**를 낸다. */
function commentLineBody(line) {
  return line.replace(/^[\s\uFEFF/*]*/u, '');
}

const pkg = JSON.parse(readFileSync(join(webRoot, 'package.json'), 'utf8'));

/**
 * `typecheck` 명령을 **꼴까지** 읽는다 — 지목만 세면 검사를 끄는 길이 전부 그 밑을 지나간다.
 * `&&` 로 쪼갠 조각이 각각 `tsc` 로 시작하고, 허용 플래그와 `-p <project>` 하나만 갖는다.
 */
function parseTypecheckScript() {
  const command = pkg.scripts?.typecheck;
  if (typeof command !== 'string') {
    return { segments: [], projects: [], violations: ['package.json 에 typecheck 스크립트가 없다'] };
  }
  const violations = [];
  const projects = [];
  const segments = command.split('&&').map((segment) => segment.trim());
  for (const segment of segments) {
    const tokens = segment.split(/\s+/u).filter((token) => token.length > 0);
    if (tokens[0] !== 'tsc') {
      violations.push(`조각이 tsc 로 시작하지 않는다: ${JSON.stringify(segment)}`);
      continue;
    }
    const found = [];
    for (let i = 1; i < tokens.length; i += 1) {
      const token = tokens[i];
      if (PROJECT_FLAGS.has(token)) {
        const value = tokens[i + 1];
        if (value === undefined || value.startsWith('-')) {
          violations.push(`${token} 뒤에 프로젝트가 없다: ${JSON.stringify(segment)}`);
        } else {
          found.push(value);
        }
        i += 1;
        continue;
      }
      if (ALLOWED_SEGMENT_FLAGS.has(token)) {
        continue;
      }
      violations.push(`허용하지 않는 토큰 ${JSON.stringify(token)}: ${JSON.stringify(segment)}`);
    }
    if (found.length !== 1) {
      violations.push(`조각이 프로젝트 하나를 지목하지 않는다(${found.length}): ${JSON.stringify(segment)}`);
    }
    // 경로는 **절대 경로로 풀어서** 센다 — `./tsconfig.e2e.json` 같은 같은 뜻의 글자가
    // 거짓 RED 를 내지 않게(cr L-2).
    for (const value of found) {
      projects.push(resolve(webRoot, value));
    }
  }
  return { segments, projects, violations };
}

/** 한 프로젝트의 유효 설정. */
function openProject(projectPath) {
  const { config, error } = ts.readConfigFile(projectPath, ts.sys.readFile);
  if (error !== undefined) {
    throw new Error(`${projectPath} 을 읽지 못했다: ${ts.flattenDiagnosticMessageText(error.messageText, ' ')}`);
  }
  const parsed = ts.parseJsonConfigFileContent(config, ts.sys, webRoot, undefined, projectPath);
  if (parsed.errors.length > 0) {
    throw new Error(
      `${projectPath} 진단: ${parsed.errors
        .map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, ' '))
        .join(' / ')}`,
    );
  }
  return parsed;
}

/**
 * 프로그램을 세운다. **`noCheck` 는 강제로 끄고** `noEmit` 은 강제로 켠다 — 설정 파일이나 CLI 가
 * 검사를 끄더라도 이 자리는 끄지 못한다. `overlay` 는 파일 하나의 본문을 갈아끼우는 자리다
 * (양성 대조가 쓴다).
 */
function programOf(parsed, overlay = new Map()) {
  const options = { ...parsed.options, noCheck: false, noEmit: true };
  const host = ts.createCompilerHost(options, true);
  const baseGetSourceFile = host.getSourceFile.bind(host);
  const baseReadFile = host.readFile.bind(host);
  host.getSourceFile = (fileName, languageVersionOrOptions, onError, shouldCreate) => {
    const text = overlay.get(resolve(fileName));
    if (text !== undefined) {
      return ts.createSourceFile(fileName, text, languageVersionOrOptions, true);
    }
    return baseGetSourceFile(fileName, languageVersionOrOptions, onError, shouldCreate);
  };
  host.readFile = (fileName) => overlay.get(resolve(fileName)) ?? baseReadFile(fileName);
  return ts.createProgram({ rootNames: parsed.fileNames, options, host });
}

/** 프로그램 전수의 realpath 집합. */
function filesOf(program) {
  const files = new Set();
  for (const sourceFile of program.getSourceFiles()) {
    try {
      files.add(realpathSync(sourceFile.fileName));
    } catch {
      /* 해석할 수 없는 파일은 덮은 것으로도 상한 안으로도 세지 않는다 */
    }
  }
  return files;
}

/** 디스크의 e2e TS 전수 + 뿌리의 playwright 설정. */
function requiredFiles() {
  const found = [];
  const walk = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const full = join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(full);
        continue;
      }
      if (entry.isFile() && TS_FILE.test(entry.name)) {
        found.push(realpathSync(full));
      }
    }
  };
  walk(join(webRoot, E2E_DIR));
  for (const entry of readdirSync(webRoot, { withFileTypes: true })) {
    if (entry.isFile() && ROOT_CONFIG.test(entry.name)) {
      found.push(realpathSync(join(webRoot, entry.name)));
    }
  }
  return found.sort();
}

/**
 * 한 원문이 담은 **억제 지시** 전수. 그물은 둘이고 둘 다 「본문에 정규식」이 아니다.
 *
 *   ⓐ **컴파일러 자신의 파스 결과** — `checkJsDirective`(`@ts-nocheck`)와
 *      `commentDirectives`(`@ts-ignore`·`@ts-expect-error`). 이것이 **실제로 먹는** 억제의 정의다.
 *   ⓑ **렉서로 뽑은 주석 토큰** — `ts.createScanner` 가 낸 주석 토큰만 본다(문자열 리터럴·식별자는
 *      애초에 토큰이 다르므로 들어오지 않는다). 오늘 컴파일러가 무시하지만 자리만 옮기면 먹는 꼴과,
 *      앞으로 늘어날 꼴을 받는다 — 막는 쪽이므로 넓은 편이 안전한 방향이다.
 */
function compilerDirectivesIn(sourceFile) {
  const found = [];
  if (sourceFile.checkJsDirective !== undefined && sourceFile.checkJsDirective.enabled === false) {
    found.push('@ts-nocheck');
  }
  for (const directive of sourceFile.commentDirectives ?? []) {
    found.push(directive.type === ts.CommentDirectiveType.ExpectError ? '@ts-expect-error' : '@ts-ignore');
  }
  return [...new Set(found)].sort();
}

/**
 * ⓑ **홀로**. 대조가 ⓐ 와 섞인 합집합만 보면 ⓑ 가 죽어도 초록이다 — 실측으로 확인된 함정이다
 * (r1 cr M2: 첫 판의 양성 대조 여덟 가운데 여섯이 ⓐ 로 통과해서, 그물에서 이름 둘을 지우는 변이가
 * 조용했다). 아래 대조 묶음이 이 함수를 **직접** 부른다.
 */
function lexerDirectivesIn(sourceFile) {
  const found = [];
  const scanner = ts.createScanner(
    ts.ScriptTarget.Latest,
    false,
    sourceFile.fileName.endsWith('x') ? ts.LanguageVariant.JSX : ts.LanguageVariant.Standard,
    sourceFile.text,
  );
  for (let kind = scanner.scan(); kind !== ts.SyntaxKind.EndOfFileToken; kind = scanner.scan()) {
    if (kind !== ts.SyntaxKind.SingleLineCommentTrivia && kind !== ts.SyntaxKind.MultiLineCommentTrivia) {
      continue;
    }
    for (const line of scanner.getTokenText().split(/\r?\n/u)) {
      const matched = TS_SILENCING_DIRECTIVE.exec(commentLineBody(line));
      if (matched !== null) {
        found.push(matched[0]);
      }
    }
  }
  return [...new Set(found)].sort();
}

/** 두 그물의 합집합 — 모집단 전수를 세는 자리가 쓴다. */
function silencingDirectivesIn(sourceFile) {
  return [...new Set([...compilerDirectivesIn(sourceFile), ...lexerDirectivesIn(sourceFile)])].sort();
}

/** strict 하위 축이 접히도록 푼 유효 옵션 — 비교 가능한 꼴로. */
function comparableOptions(options) {
  const out = {};
  for (const key of Object.keys(options)) {
    if (OPTION_EXEMPT.has(key)) {
      continue;
    }
    out[key] = options[key];
  }
  for (const flag of STRICT_FAMILY) {
    out[flag] = ts.getStrictOptionValue(options, flag);
  }
  return JSON.parse(JSON.stringify(out));
}

// 측정이 아닌 비용은 칸 밖(모듈 적재 단계)에 둔다 — 프로그램 구성이 이 파일의 무거운 쪽이다.
const SCRIPT = parseTypecheckScript();
const REQUIRED = requiredFiles();
const SCREEN_PROJECT = resolve(webRoot, 'tsconfig.json');
const E2E_PROJECT = resolve(webRoot, 'tsconfig.e2e.json');
/**
 * **억제 지시 금지의 모집단은 `typecheck` 명령이 아니라 두 프로젝트 자신이다**(M15/15O).
 * 명령에서 프로젝트를 빼는 변이는 위 「명령의 꼴」 칸이 받지만, 같은 편집이 금지의 **대상까지**
 * 줄이면 한 변이가 두 칸을 함께 끄는 꼴이 된다. 그래서 프로젝트 둘을 **이름으로** 연다.
 */
const BANNED_PROJECTS = [...new Set([SCREEN_PROJECT, E2E_PROJECT, ...SCRIPT.projects])];
const PARSED = new Map(BANNED_PROJECTS.map((projectPath) => [projectPath, openProject(projectPath)]));
const PROGRAMS = new Map([...PARSED].map(([projectPath, parsed]) => [projectPath, programOf(parsed)]));
const COVERED = new Set(
  SCRIPT.projects.flatMap((projectPath) => [...filesOf(PROGRAMS.get(projectPath))]),
);
const show = (absolute) => relative(webRoot, absolute).split(sep).join('/');

/**
 * **타입 억제 지시 금지가 서는 자리**(M15/15O — `OPEN-M15-SRC-TS-COMMENT-DISABLE` (a)·(c)ⓘ).
 *
 * 15L 의 대상은 `REQUIRED`(= `e2e/` 디스크 전수 + 뿌리 playwright 설정)였고, 그 경계 밖에 둘이
 * 남았다: **화면 레인의 `src/**`**(이름을 단 `eslint-disable` 한 줄로 lint·typecheck 가 함께
 * 조용해진다 — 실측 0/0/0/0)과 **e2e 프로그램이 끌어들이는 `e2e/` 밖의 `.ts`**(예:
 * `web/e2elib/helper.ts` — eslint glob 밖이라 tseslint 기본값이 `@ts-expect-error: <설명>` 을
 * disable 없이도 통과시킨다, 실측 0/0/0).
 *
 * 둘을 한 술어로 닫는다 — **디스크 glob 이 아니라 두 프로그램의 전수**다. 그러면 「파일을 다른
 * 디렉터리로 옮긴다」가 길이 되지 않는다(프로그램이 끌어들이는 한 모집단 안이다).
 *
 * **차감은 설치본 하나다 — 경로 성분 이름이 아니다**(r1 verifier H-1). 첫 판은 `node_modules` 라는
 * **경로 성분**을 가진 파일을 전부 뺐는데, `.gitignore` 가 무시하는 것은 `web/node_modules/` 뿐이라
 * `web/src/node_modules/helper.ts` 나 `web/e2elib/node_modules/helper.ts` 는 **추적·커밋할 수 있고**
 * TS 는 그것을 검사한다. 그 자리는 eslint 기본 ignore 대상이기도 해서, 지시 한 줄이면 네 층이 모두
 * 초록이었다(실측 — 대조로 지시를 빼면 typecheck 2 다. 즉 실제로 검사받는 원문이다).
 *
 * 그래서 차감을 **`web/node_modules/` 의 realpath 접두**로 못박는다. 그 밖의 `node_modules` 성분은
 * 차감이 아니라 **위반**이고, 아래 전용 칸이 그것을 따로 센다(지시가 없어도 붉는다 — 그 자리는
 * eslint 가 보지 않는 곳에 우리 원문을 두는 것 자체가 길이다).
 */
const INSTALLED_DEPENDENCIES = realpathSync(resolve(webRoot, 'node_modules'));

/** 설치본 안인가 — 접두 비교다. 중첩 설치(`node_modules/a/node_modules/b`)도 이 접두 아래다. */
function isInstalledDependency(realPath) {
  return realPath === INSTALLED_DEPENDENCIES || realPath.startsWith(INSTALLED_DEPENDENCIES + sep);
}

/** 설치본 **밖**인데 `node_modules` 성분을 가진 자리 — eslint 가 기본으로 보지 않는 자리다. */
function hidesUnderNodeModules(realPath) {
  return !isInstalledDependency(realPath) && realPath.split(sep).includes('node_modules');
}

const BANNED_POPULATION = new Map();
for (const projectPath of BANNED_PROJECTS) {
  for (const sourceFile of PROGRAMS.get(projectPath).getSourceFiles()) {
    let realPath;
    try {
      realPath = realpathSync(sourceFile.fileName);
    } catch {
      continue;
    }
    if (isInstalledDependency(realPath)) {
      continue;
    }
    BANNED_POPULATION.set(realPath, sourceFile);
  }
}

/**
 * **화면 쪽 래칫**(r1 cr H1). e2e 쪽은 `REQUIRED` 대비 「빠진 파일 0」이라는 래칫인데 화면 쪽은
 * 「≥ 1」 하나뿐이었다 — 그래서 `tsconfig.json` 에 `exclude` 한 줄(테스트 파일을 제외하는 흔한
 * 관용구)을 더하면 그 파일들이 모집단에서 **조용히** 빠졌다. `include` 는 `["src"]` 그대로라 W-4 ②
 * 초록 · 화면 계수가 여전히 크니 비공허 칸 초록 · 검사 대상이 줄었으니 typecheck 초록이다. 그리고
 * 빠진 파일들은 eslint 가 여전히 린트하므로 거기서 억제 주석을 쓰려면 이름 단 disable 이 필요한데 —
 * **그것이 이 slice 가 닫았다고 적은 (a) 축이고, 그 축을 받던 유일한 층이 방금 빠졌다.**
 *
 * 그래서 `requiredFiles()` 와 **같은 꼴**로 `web/src` 를 디스크 훑어 전수가 모집단 안인지 본다.
 * 확장자 집합도 같다 — 한쪽만 넓으면 그 차이만큼 조용하다.
 */
function srcFiles() {
  const found = [];
  const walk = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const full = join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(full);
        continue;
      }
      if (entry.isFile() && TS_FILE.test(entry.name)) {
        found.push(realpathSync(full));
      }
    }
  };
  walk(join(webRoot, SRC_DIR));
  return found.sort();
}

const SRC_REQUIRED = srcFiles();

describe('e2e 타입 검사 층 — 진단 · 기준 · 덮는 자리 · 두 프로그램의 억제 지시 0', () => {
  it('재는 대상이 비어 있지 않다', () => {
    // 전수가 0 이면 아래 포함 단언은 **무엇이든 통과한다**. 공허를 먼저 가른다.
    expect(REQUIRED.length).toBeGreaterThan(0);
    expect(SCRIPT.projects.length).toBeGreaterThan(0);
  });

  it('typecheck 명령이 `tsc [--noEmit] -p <project>` 조각들 그대로다', () => {
    // 지목만 보면 `|| true`·`; true`·`--noCheck`·`echo` 가 전부 그 밑을 지나간다(cr M-2 실측).
    expect(SCRIPT.violations, 'typecheck 명령의 꼴 위반').toEqual([]);
    expect(SCRIPT.segments.length).toBe(SCRIPT.projects.length);
    expect(SCRIPT.projects).toContain(SCREEN_PROJECT);
    expect(SCRIPT.projects).toContain(E2E_PROJECT);
  });

  it('e2e 프로그램의 진단이 0 이다 — noCheck 를 강제로 끈 채', () => {
    // **이 칸이 이 파일의 중심이다.** 스크립트가 무엇을 하든, 설정이 `noCheck` 를 켜든,
    // e2e 프로그램의 타입 검사는 여기서 돈다.
    const parsed = PARSED.get(E2E_PROJECT);
    expect(parsed, 'typecheck 가 tsconfig.e2e.json 을 지목하지 않는다').toBeDefined();
    const diagnostics = ts.getPreEmitDiagnostics(PROGRAMS.get(E2E_PROJECT));
    const shown = diagnostics.map(
      (diagnostic) =>
        `${diagnostic.file === undefined ? '(전역)' : show(diagnostic.file.fileName)} TS${diagnostic.code}: ` +
        ts.flattenDiagnosticMessageText(diagnostic.messageText, ' '),
    );
    expect(shown, 'e2e 프로그램의 타입 진단').toEqual([]);
  });

  it('양성 대조 — 같은 함수가 심은 오류를 보고한다', () => {
    // 위 칸이 「늘 0」인지 「실제로 재는지」를 가른다. 전수의 첫 파일 본문에 오류 한 줄을
    // **메모리에서** 덧붙인다 — 디스크는 건드리지 않는다.
    const parsed = PARSED.get(E2E_PROJECT);
    const target = REQUIRED.find((file) => COVERED.has(file));
    expect(target, '심을 자리가 없다').toBeDefined();
    const overlay = new Map([
      [target, `${readFileSync(target, 'utf8')}\nexport const __plantedTypeError: number = 'not a number';\n`],
    ]);
    const planted = ts.getPreEmitDiagnostics(programOf(parsed, overlay));
    expect(planted.map((diagnostic) => diagnostic.code)).toContain(2322);
  });

  it('두 프로그램의 원문에 타입 억제 지시가 0 이다', () => {
    // **진단 0 만으로는 모자란다**(r2 v M-2). `// @ts-nocheck` 한 줄이면 그 파일의 진단이 사라지고,
    // 그 주석을 막는 eslint 규칙은 `// eslint-disable-next-line <규칙 이름>` 으로 꺼진다 —
    // 세 층이 모두 지나간다(실측 0/0/0). 그 꼴을 **eslint 주석으로 끌 수 없는 자리**에서 받는다.
    //
    // **M15/15O — 대상이 두 프로그램 전수다.** 저장소 규약은 `eslint-disable-next-line` 을
    // 리뷰로 가는 명시적 탈출구로 **그대로 둔다**; 그래서 「그 disable 을 금지」하는 쪽을 고르지
    // 않고, 타입 검사를 끄는 지시만 **eslint 가 아닌 층**에서 금지한다. 두 규율이 서로를 침범하지
    // 않는다.
    const offenders = [];
    for (const [realPath, sourceFile] of BANNED_POPULATION) {
      const directives = silencingDirectivesIn(sourceFile);
      if (directives.length > 0) {
        offenders.push(`${show(realPath)}: ${directives.join(' ')}`);
      }
    }
    expect(offenders, '화면·e2e 프로그램의 타입 억제 지시').toEqual([]);
  });

  it('그 모집단이 공허하지 않다 — 화면·e2e·15L 이 들던 자리를 함께 든다', () => {
    // 모집단이 0 이면 위 칸은 **무엇이든 통과한다**. 그리고 모집단이 **줄어드는** 변이(한 프로그램을
    // 빼거나 차감을 넓히는 것)도 거기서는 조용하다 — 세 갈래를 각각 센다.
    const names = [...BANNED_POPULATION.keys()].map(show);
    expect(names.filter((name) => name.startsWith('src/')).length, '화면 레인').toBeGreaterThan(0);
    expect(names.filter((name) => name.startsWith('e2e/')).length, 'e2e 레인').toBeGreaterThan(0);
    // 15L 이 들던 자리(`e2e/` 디스크 전수 + 뿌리 playwright 설정)는 **래칫**이다 — 넓히는 편집이
    // 그 자리를 조용히 반납하지 못한다.
    const dropped = REQUIRED.filter((file) => !BANNED_POPULATION.has(file)).map(show);
    expect(dropped, '15L 이 들던 자리가 모집단에서 빠졌다').toEqual([]);
  });

  it('화면 래칫 — web/src 디스크 전수가 모집단 안이다', () => {
    // **「≥ 1」은 래칫이 아니다**(r1 cr H1). `tsconfig.json` 의 `exclude` 한 줄이 화면 파일을 모집단에서
    // 조용히 빼는데, 계수가 여전히 크니 비공허 칸은 초록이다. e2e 쪽과 **같은 꼴**의 전수 단언을 둔다.
    expect(SRC_REQUIRED.length, '화면 디스크 전수').toBeGreaterThan(0);
    const dropped = SRC_REQUIRED.filter((file) => !BANNED_POPULATION.has(file)).map(show);
    expect(dropped, 'web/src 의 원문이 모집단에서 빠졌다').toEqual([]);
  });

  it('차감은 설치본 하나다 — 그 밖의 node_modules 자리는 위반이다', () => {
    // **r1 verifier H-1.** 첫 판은 `node_modules` 라는 **경로 성분**으로 차감했다. `.gitignore` 가
    // 무시하는 것은 `web/node_modules/` 뿐이라 `web/src/node_modules/` · `web/e2elib/node_modules/` 는
    // 추적·커밋할 수 있고 TS 가 검사하며 eslint 는 기본으로 보지 않는다 — 지시 한 줄이면 네 층이 모두
    // 초록이었다(실측). 지금 그 파일들은 **모집단 안**이고(위 칸이 지시를 센다), 지시가 없어도 이 칸이
    // 따로 붉는다: eslint 가 보지 않는 자리에 우리 원문을 두는 것 자체가 길이기 때문이다.
    const hidden = [];
    for (const projectPath of BANNED_PROJECTS) {
      for (const sourceFile of PROGRAMS.get(projectPath).getSourceFiles()) {
        let realPath;
        try {
          realPath = realpathSync(sourceFile.fileName);
        } catch {
          continue;
        }
        if (hidesUnderNodeModules(realPath)) {
          hidden.push(show(realPath));
        }
      }
    }
    expect([...new Set(hidden)].sort(), '설치본 밖의 node_modules 자리에 있는 프로그램 파일').toEqual([]);
  });

  it.each([
    [`${webRoot}${sep}node_modules${sep}react${sep}index.d.ts`, true],
    [`${webRoot}${sep}node_modules${sep}a${sep}node_modules${sep}b${sep}index.d.ts`, true],
    [`${webRoot}${sep}src${sep}node_modules${sep}helper.ts`, false],
    [`${webRoot}${sep}e2elib${sep}node_modules${sep}helper.ts`, false],
    [`${webRoot}${sep}src${sep}paths.ts`, false],
  ])('차감 술어 — %s 의 설치본 여부가 접두로 갈린다', (candidate, installed) => {
    // 술어 자신을 **합성 경로**로 잠근다. 위 칸은 오늘 디스크에 그 자리가 없어 공허에 가까우므로,
    // H-1 의 두 재현을 여기서 직접 든다 — 차감이 다시 성분 이름으로 돌아가면 이 칸이 붉는다.
    expect(isInstalledDependency(candidate)).toBe(installed);
    expect(hidesUnderNodeModules(candidate)).toBe(!installed && candidate.split(sep).includes('node_modules'));
  });

  it.each([
    ['한 줄 주석', '// @ts-ignore\nexport const a = 1;\n'],
    ['삼중 사선', '/// @ts-nocheck\nexport const a = 1;\n'],
    ['블록 주석', '/* @ts-nocheck */\nexport const a = 1;\n'],
    ['블록 주석 ignore', '/* @ts-ignore */\nexport const a = 1;\n'],
    ['JSDoc 줄', '/**\n * @ts-nocheck\n */\nexport const a = 1;\n'],
    ['설명 단 expect-error', '// @ts-expect-error: 설명\nexport const a = 1;\n'],
    ['들여쓴 줄', '    // @ts-ignore\nexport const a = 1;\n'],
    ['BOM 뒤', '\uFEFF// @ts-nocheck\nexport const a = 1;\n'],
    ['슬래시·별표 교차', '/*/@ts-ignore*/\nexport const a = 1;\n'],
    ['슬래시·별표 교차 + 여백', '/*/ @ts-ignore */\nexport const a = 1;\n'],
  ])('양성 대조 — %s 은 지시로 센다', (_label, text) => {
    // **합집합이 막아야 할 꼴을 놓치지 않는가.** 어느 그물이 받는지는 아래 ⓑ 전용 칸이 따로 가른다 —
    // 여기서 `length > 0` 만 보면 ⓑ 가 죽어도 ⓐ 가 떠받친다(r1 cr M2 의 실측).
    const sourceFile = ts.createSourceFile('planted.ts', text, ts.ScriptTarget.Latest, true);
    expect(silencingDirectivesIn(sourceFile).length).toBeGreaterThan(0);
  });

  it.each([
    ['@ts-nocheck', '/* @ts-nocheck */\nexport const a = 1;\n'],
    ['@ts-ignore', '/**\n * @ts-ignore\n */\nexport const a = 1;\n'],
    ['@ts-expect-error', '/**\n * @ts-expect-error\n */\nexport const a = 1;\n'],
  ])('ⓑ 전용 — %s 는 컴파일러가 인지하지 않는 자리에서 렉서 그물이 받는다', (name, text) => {
    // **대조가 ⓐ 와 섞인 합집합만 보면 ⓑ 는 측정되지 않는다**(r1 cr M2). 세 이름 각각에 대해
    // **컴파일러가 인지하지 않는 꼴**을 하나씩 두어, 그물에서 그 이름을 지우는 변이가 조용하지 않게
    // 한다. ⓐ 가 빈 것을 같은 칸에서 단언하는 것이 요점이다.
    const sourceFile = ts.createSourceFile('planted.ts', text, ts.ScriptTarget.Latest, true);
    expect(compilerDirectivesIn(sourceFile), 'ⓐ 가 이 꼴을 인지한다면 이 칸은 ⓑ 를 재지 않는다').toEqual([]);
    expect(lexerDirectivesIn(sourceFile)).toEqual([name]);
  });

  it('ⓐ 전용 — 슬래시·별표 교차는 컴파일러가 인지한다', () => {
    // 반대 방향의 비대칭(r1 cr M1). ⓑ 는 ⓐ 의 **상위집합이 아니다** — 이 꼴은 ⓐ 가 정의로 받는다.
    // 첫 판의 걷개는 이것을 놓쳤고, 지금은 둘 다 잡는다(그래서 아래 두 단언이 함께 선다).
    const sourceFile = ts.createSourceFile('planted.ts', '/*/@ts-ignore*/\nexport const a = 1;\n', ts.ScriptTarget.Latest, true);
    expect(compilerDirectivesIn(sourceFile)).toEqual(['@ts-ignore']);
    expect(lexerDirectivesIn(sourceFile)).toEqual(['@ts-ignore']);
  });

  it.each([
    ['한 줄 산문', '// 그 꼴(`@ts-nocheck`)은 막는다\nexport const a = 1;\n'],
    ['JSDoc 산문', '/**\n * `@ts-expect-error` 로는 쓸 수 없다\n */\nexport const a = 1;\n'],
  ])('음성 대조 — %s 속 언급은 지시가 아니다', (_label, text) => {
    // **거짓 RED 는 그 자체로 비용이다.** `src` 전수에 실해 0 · 산문 언급 하나인데(실측),
    // 15L 의 「토큰 어디에나」 그물은 그 산문을 붉혔다. 좁힘이 바로 이 칸을 위한 것이다.
    const sourceFile = ts.createSourceFile('prose.ts', text, ts.ScriptTarget.Latest, true);
    expect(silencingDirectivesIn(sourceFile)).toEqual([]);
  });

  it('두 프로젝트의 유효 옵션이 면제 집합 밖에서 같다', () => {
    const screen = PARSED.get(SCREEN_PROJECT);
    const e2e = PARSED.get(E2E_PROJECT);
    expect(screen, 'typecheck 가 tsconfig.json 을 지목하지 않는다').toBeDefined();
    expect(e2e, 'typecheck 가 tsconfig.e2e.json 을 지목하지 않는다').toBeDefined();
    expect(comparableOptions(e2e.options)).toEqual(comparableOptions(screen.options));
    // 두 쪽이 **함께 약해져도** 위 등식은 참이다 — 화면 쪽의 값을 못박는다(그 쪽의 그 밖의
    // 전제는 W-4 가 든다).
    const screenStrict = Object.fromEntries(
      ['strict', ...STRICT_FAMILY].map((flag) => [flag, ts.getStrictOptionValue(screen.options, flag)]),
    );
    expect(screenStrict).toEqual(
      Object.fromEntries(['strict', ...STRICT_FAMILY].map((flag) => [flag, true])),
    );
    for (const flag of ['noUnusedLocals', 'noUnusedParameters', 'noUncheckedIndexedAccess', 'noImplicitOverride', 'noFallthroughCasesInSwitch']) {
      expect(screen.options[flag], `화면 프로젝트의 ${flag}`).toBe(true);
    }
  });

  it('typecheck 가 여는 프로그램들이 e2e 파일 전수를 덮는다', () => {
    const uncovered = REQUIRED.filter((file) => !COVERED.has(file)).map(show);
    expect(uncovered, 'typecheck 의 어느 프로그램에도 들어 있지 않은 e2e 파일').toEqual([]);
  });

  it('음성 대조 — 화면 프로그램 하나로는 그 전수를 덮지 못한다', () => {
    const screenFiles = filesOf(PROGRAMS.get(SCREEN_PROJECT));
    const uncovered = REQUIRED.filter((file) => !screenFiles.has(file));
    expect(uncovered.length).toBeGreaterThan(0);
  });

  it('상한 — e2e 프로그램 전수가 web/ 안이다', () => {
    // 덮는 자리만 보면 「프로그램이 넓어지는 것」은 아무도 보지 않는다(cr L-3). e2e 프로젝트에는
    // `rootDir` 이 없고 `allowJs` 가 켜져 있어 TS6059 가 날 자리가 아예 없다 — 그 상한을 여기서
    // 세운다(`web/node_modules` 는 `web/` 안이다).
    const outside = [...filesOf(PROGRAMS.get(E2E_PROJECT))]
      .filter((file) => file !== webRoot && !file.startsWith(webRoot + sep))
      .map((file) => file);
    expect(outside, 'e2e 프로그램에 web/ 밖 파일이 있다').toEqual([]);
  });
});
