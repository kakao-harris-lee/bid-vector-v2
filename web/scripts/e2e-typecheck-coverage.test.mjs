import { readFileSync, readdirSync, realpathSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

/**
 * M15/15L — **`web/e2e/**` 가 실제로 타입 검사를 받는가**(`OPEN-M15-E2E-TYPECHECK`).
 *
 * 15L 이 세운 층은 `tsconfig.e2e.json` + `typecheck` 가 그 프로젝트도 돌리는 것 둘이다. 그
 * 층을 **조용히 비우는 길**이 셋 있고, 이 test 가 그 셋을 받는다:
 *
 *   ⓐ `typecheck` 에서 둘째 프로젝트가 빠진다 — tsc 는 한 마디도 하지 않는다.
 *   ⓑ `include` 가 좁아진다(예: `e2e/journey/**` 만) — 하나도 못 맞히면 TS18003 으로 떨어지지만
 *      (실측) **일부만 맞히는 좁힘은 exit 0** 이다. 15K 가 산 자리가 바로 그 꼴이다.
 *   ⓒ strict 축이 약해진다 — e2e 가 「검사받는다」는 말만 서고 기준이 달라진다.
 *
 * 술어를 **문자열이 아니라 구조로** 둔다: `package.json` 의 `typecheck` 명령이 실제로 지목하는
 * 프로젝트들을 컴파일러 API 로 **열어** 프로그램 전수를 얻고, 디스크의 e2e 파일 전수가 그 합집합
 * 안인지 본다. ⓐ 는 합집합이 줄어 RED, ⓑ 는 빠진 파일 이름이 RED, ⓒ 는 옵션 대조가 RED 다.
 *
 * **파일 전수는 디스크에서 센다** — `git ls-files` 로 세면 아직 추적되지 않은 새 spec 이 술어
 * 밖이고, 그 파일이 들어오는 커밋이 조용하다.
 */

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** 이 층이 덮어야 하는 자리 — e2e 레인의 `.ts` 전수와 뿌리의 playwright 설정. */
const E2E_DIR = 'e2e';
const ROOT_CONFIG = /^playwright.*\.config\.tsx?$/;
const TS_FILE = /\.tsx?$/;

/** `tsconfig.json` 과 `tsconfig.e2e.json` 이 **같아야** 하는 축. 약해지면 층이 말만 남는다. */
const STRICT_AXES = [
  'strict',
  'noUnusedLocals',
  'noUnusedParameters',
  'noUncheckedIndexedAccess',
  'noImplicitOverride',
  'noFallthroughCasesInSwitch',
];

/** `package.json` 의 `typecheck` 가 지목하는 프로젝트 파일 — `-p`·`--project` 둘 다 받는다. */
function typecheckProjects() {
  const pkg = JSON.parse(readFileSync(join(webRoot, 'package.json'), 'utf8'));
  const command = pkg.scripts?.typecheck;
  if (typeof command !== 'string') {
    throw new Error('package.json 에 typecheck 스크립트가 없다');
  }
  // `&&` 로 이어 붙이든 한 줄이든 **토큰**에서 읽는다 — 명령의 모양에 술어를 걸지 않는다.
  const tokens = command.split(/\s+/u).filter((token) => token.length > 0);
  const projects = [];
  for (let i = 0; i < tokens.length - 1; i += 1) {
    if (tokens[i] === '-p' || tokens[i] === '--project') {
      projects.push(tokens[i + 1]);
    }
  }
  return projects;
}

/** 한 프로젝트의 **프로그램 전수**(import·reference 로 끌려 들어온 파일 포함)와 유효 옵션. */
function openProject(projectPath) {
  const configPath = resolve(webRoot, projectPath);
  const { config, error } = ts.readConfigFile(configPath, ts.sys.readFile);
  if (error !== undefined) {
    throw new Error(`${projectPath} 을 읽지 못했다: ${ts.flattenDiagnosticMessageText(error.messageText, ' ')}`);
  }
  const parsed = ts.parseJsonConfigFileContent(config, ts.sys, webRoot, undefined, configPath);
  if (parsed.errors.length > 0) {
    throw new Error(
      `${projectPath} 진단: ${parsed.errors
        .map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, ' '))
        .join(' / ')}`,
    );
  }
  const program = ts.createProgram({ rootNames: parsed.fileNames, options: parsed.options });
  const files = new Set();
  for (const sourceFile of program.getSourceFiles()) {
    try {
      files.add(realpathSync(sourceFile.fileName));
    } catch {
      /* 해석할 수 없는 파일은 덮은 것으로 세지 않는다 */
    }
  }
  return { options: parsed.options, files };
}

/** 디스크의 e2e `.ts` 전수 + 뿌리의 playwright 설정. */
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
  return found;
}

// 측정이 아닌 비용은 칸 밖(모듈 적재 단계)에 둔다 — 프로그램을 세우는 것이 이 파일의 무거운
// 쪽이고, 아래 세 칸이 같은 것을 다시 세울 이유가 없다.
const PROJECTS = typecheckProjects();
const OPENED = PROJECTS.map((projectPath) => [projectPath, openProject(projectPath)]);
const REQUIRED = requiredFiles();
const show = (absolute) => relative(webRoot, absolute).split(sep).join('/');

describe('e2e 타입 검사 층 — 덮는 자리와 그 기준', () => {
  it('재는 대상이 비어 있지 않다', () => {
    // 전수가 0 이면 아래 포함 단언은 **무엇이든 통과한다**. 공허를 먼저 가른다.
    expect(REQUIRED.length).toBeGreaterThan(0);
    expect(PROJECTS.length).toBeGreaterThan(0);
  });

  it('typecheck 가 여는 프로그램들이 e2e 파일 전수를 덮는다', () => {
    const covered = new Set();
    for (const [, opened] of OPENED) {
      for (const file of opened.files) {
        covered.add(file);
      }
    }
    const uncovered = REQUIRED.filter((file) => !covered.has(file)).map(show);
    expect(uncovered, 'typecheck 의 어느 프로그램에도 들어 있지 않은 e2e 파일').toEqual([]);
  });

  it('음성 대조 — 화면 프로그램 하나로는 그 전수를 덮지 못한다', () => {
    // 이 칸이 없으면 위 단언이 「늘 참」인지 「실제로 가르는지」를 말하지 못한다. 15K 가 산
    // 상태(프로젝트가 `tsconfig.json` 하나)에서 같은 술어가 RED 라는 것이 그 증명이다.
    const [, screenOnly] = OPENED.find(([projectPath]) => projectPath === 'tsconfig.json') ?? [];
    expect(screenOnly, 'typecheck 가 tsconfig.json 을 열지 않는다').toBeDefined();
    const uncovered = REQUIRED.filter((file) => !screenOnly.files.has(file));
    expect(uncovered.length).toBeGreaterThan(0);
  });

  it('e2e 프로젝트의 strict 축이 화면 프로젝트와 같다', () => {
    const byPath = new Map(OPENED);
    const screen = byPath.get('tsconfig.json');
    const e2e = byPath.get('tsconfig.e2e.json');
    expect(screen, 'typecheck 가 tsconfig.json 을 열지 않는다').toBeDefined();
    expect(e2e, 'typecheck 가 tsconfig.e2e.json 을 열지 않는다').toBeDefined();
    const axesOf = (opened) => Object.fromEntries(STRICT_AXES.map((axis) => [axis, opened.options[axis]]));
    expect(axesOf(e2e)).toEqual(axesOf(screen));
    // 두 쪽이 **함께 꺼져도** 위 등식은 참이다 — 값까지 못박는다.
    expect(axesOf(screen)).toEqual(Object.fromEntries(STRICT_AXES.map((axis) => [axis, true])));
  });
});
