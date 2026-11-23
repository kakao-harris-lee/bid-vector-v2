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
 */

const webRoot = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));

/** 이 층이 덮어야 하는 자리 — e2e 레인의 TS 전수와 뿌리의 playwright 설정. */
const E2E_DIR = 'e2e';
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

/** strict 메타 플래그가 접지 않는 하위 축 — `options[f] ?? options.strict` 로 평가해야 한다. */
const STRICT_FAMILY = [
  'alwaysStrict',
  'noImplicitAny',
  'noImplicitThis',
  'strictBindCallApply',
  'strictFunctionTypes',
  'strictNullChecks',
  'strictPropertyInitialization',
  'useUnknownInCatchVariables',
];

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
const PARSED = new Map(SCRIPT.projects.map((projectPath) => [projectPath, openProject(projectPath)]));
const PROGRAMS = new Map([...PARSED].map(([projectPath, parsed]) => [projectPath, programOf(parsed)]));
const COVERED = new Set([...PROGRAMS.values()].flatMap((program) => [...filesOf(program)]));
const show = (absolute) => relative(webRoot, absolute).split(sep).join('/');

describe('e2e 타입 검사 층 — 진단 · 기준 · 덮는 자리', () => {
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
