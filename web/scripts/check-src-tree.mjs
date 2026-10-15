#!/usr/bin/env node
// ADR 0011 D-3 — **컴파일 경계와 그 전제**를 잰다. 이 게이트가 닫는 것은 넷이다:
//
//   ① 기준점 자체(`web/src`)가 링크가 아니고 `web/` 안인가 — code-review r1 F-1. 기준점을
//      realpath 로 먼저 풀어 쓰면 「링크가 가리키는 디렉터리의 파일이 그 디렉터리 안에 있다」는
//      **항진명제**가 되고, 게이트는 그 상태에서 「링크 0」을 적극적으로 보고한다(초록보다 나쁜
//      거짓 보증 — 실측으로 확인했다).
//   ② tsconfig 의 네 값(rootDir·include·allowJs·paths) — 경계 자신의 전제다. 하나만 바꾸면
//      TS6059 가 날 자리가 사라지는데 typecheck 는 자기 전제의 소실을 보지 못한다.
//   ③ **프로그램 전수**가 `web/src` ∪ `web/node_modules` 안인가 — verifier r1 H-2. `.d.ts` 는
//      TS6059 의 대상이 아니라서 밖의 선언 파일을 `import type` 하면 게이트 넷이 모두 초록이었다.
//      루트 이름(`parsed.fileNames`)이 아니라 `ts.createProgram(...).getSourceFiles()` 를 본다 —
//      import 로 끌려온 파일·`files`·triple-slash reference·`src` 안 링크가 한 술어로 닫힌다.
//   ④ 호출 자리 집합 등식 — code-review r1 F-2. **증명 범위는 「클라이언트 패키지를 끌어오는
//      자리 == 한 자리」까지이고 「서버 호출 경로가 하나뿐」은 아니다**(7A 검토 r5).
//      `openapi-fetch` 를 import 하는 파일 집합이
//      `{src/api/client.ts}` 와 **같아야** 한다. eslint 의 `no-restricted-imports` 는 정적
//      import 만 보므로 리터럴 동적 `import('openapi-fetch')` 로 손 계약 클라이언트를 만들 수
//      있었다. 대상이 리터럴이 아닌 동적 import 는 「어느 모듈인지 게이트가 볼 수 없다」이므로
//      같은 자리에서 막는다.
//
// 그리고 번들 그래프 경계(H-1)를 지키는 Vite 플러그인이 `vite.config.ts` 의 **두 자리**(메인
// `plugins` · `worker.plugins`)에 **원소로 등재**됐는지 AST 로 본다(⑥) — grep 도 아니고 「이름이
// 배열 안에 나타난다」도 아니다.
//
// **이 ⑥ 은 빠른 선행 검사다 — 「실수로 떼어낸 것」을 잡는다.** 플러그인이 실제로 **활성**인지는
// 설정 문법으로 증명되지 않으므로(조건식·spread·`apply` 등 형태가 끝없다) **카나리 빌드**(W-8)가
// 직접 잰다: 고정 probe 가 web 밖 파일을 끌어오는 빌드를 돌려 경계 오류로 **실패해야** 통과다.
//
// 읽는 방식: JSON 을 정규식으로 긁지 않고 **컴파일러 자신의 API**로 유효 옵션·프로그램·import 를
// 얻는다. 열거하는 것은 legacy 경로 이름이 아니라 이 게이트 자신의 전제다.
import { existsSync, lstatSync, readdirSync, readFileSync, realpathSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';

import { packageNameOf } from './module-graph-boundary.mjs';

const webRoot = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));
const tsconfigPath = resolve(webRoot, 'tsconfig.json');
const srcDir = resolve(webRoot, 'src');
const nodeModulesDir = resolve(webRoot, 'node_modules');
const boundaryPluginSpecifier = './scripts/module-graph-boundary.mjs';
const API_CALL_SITE = 'src/api/client.ts';
const API_CLIENT_PACKAGE = 'openapi-fetch';
// 「레지스트리 URL」을 origin 으로 고정한다(verifier r4 L-10) — `https://` 접두만 보던 동안
// `https://codeload.github.com/<x>/tar.gz/<sha>` 같은 비레지스트리 tarball 이 지나갔다(실측).
// 사설 레지스트리로 옮기는 날 이 값을 바꾸는 것이 그 결정의 자리다.
const REGISTRY_ORIGIN = 'https://registry.npmjs.org/';

// 링크 검사에서 건너뛰는 자리 — 설치본과 빌드·실행 산출물(모두 `.gitignore` 대상).
const LINK_SCAN_SKIP = new Set([
  'node_modules',
  'dist',
  'test-results',
  'playwright-report',
  'blob-report',
]);

const failures = [];

function fail(line) {
  failures.push(line);
}

function finishIfFailed() {
  if (failures.length > 0) {
    console.error('컴파일 경계 전제가 깨졌다 (ADR 0011 D-3):');
    for (const line of failures) {
      console.error(`  - ${line}`);
    }
    process.exit(1);
  }
}

// ① 기준점 — realpath 를 쓰기 **전에** 잰다.
let srcReal;
try {
  if (lstatSync(srcDir).isSymbolicLink()) {
    fail('web/src 자체가 심볼릭 링크다 — 그러면 아래 모든 단언이 항진명제가 된다');
  }
  srcReal = realpathSync(srcDir);
  if (srcReal !== resolve(webRoot, 'src')) {
    fail(`web/src 의 실제 경로가 web/ 안이 아니다: ${srcReal}`);
  }
} catch (error) {
  fail(`web/src 를 해석하지 못했다: ${error.message}`);
}
finishIfFailed();

const allowedRoots = [srcReal];
if (existsSync(nodeModulesDir)) {
  allowedRoots.push(realpathSync(nodeModulesDir));
}

function insideAllowedRoots(candidate) {
  return allowedRoots.some((root) => candidate === root || candidate.startsWith(root + sep));
}

// ② tsconfig 의 유효 옵션.
const { config, error } = ts.readConfigFile(tsconfigPath, ts.sys.readFile);
if (error !== undefined) {
  fail(`tsconfig.json 을 읽지 못했다: ${ts.flattenDiagnosticMessageText(error.messageText, ' ')}`);
  finishIfFailed();
}
const parsed = ts.parseJsonConfigFileContent(config, ts.sys, webRoot, undefined, tsconfigPath);
for (const diagnostic of parsed.errors) {
  fail(`tsconfig 진단: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, ' ')}`);
}
finishIfFailed();

if (parsed.options.rootDir === undefined) {
  fail('rootDir 이 없다 — 경계가 선언되지 않았다');
} else {
  // 없는 디렉터리를 가리키면 ENOENT 가 스택 트레이스로 올라가 「무엇이 깨졌는지」를 말하지
  // 못했다(code-review r1 F-8).
  try {
    if (realpathSync(parsed.options.rootDir) !== srcReal) {
      fail(`rootDir 이 web/src 가 아니다: ${parsed.options.rootDir}`);
    }
  } catch (caught) {
    fail(`rootDir 을 해석하지 못했다(${parsed.options.rootDir}): ${caught.message}`);
  }
}

const include = Array.isArray(config.include) ? config.include : undefined;
if (include === undefined || include.length !== 1 || include[0] !== 'src') {
  fail(`include 가 ["src"] 가 아니다: ${JSON.stringify(config.include)}`);
}

if (parsed.options.allowJs === true) {
  fail('allowJs 가 켜져 있다 — legacy .js 가 프로그램에 들어올 입구가 생긴다');
}

if (parsed.options.paths !== undefined && Object.keys(parsed.options.paths).length > 0) {
  fail(`paths 별칭이 있다: ${Object.keys(parsed.options.paths).join(', ')}`);
}

// ③ `web/` 안의 심볼릭 링크 0 — `src` 만이 아니라 `public/`·진입 문서까지 본다(verifier r1 L-3).
// 건너뛰는 자리(설치본·빌드·실행 산출물)는 아래 집합이고, 성공 문면도 그 예외를 적는다
// (code-review r2 G-3 — `node_modules` 안에는 `.bin/*` 링크가 실제로 여럿 있다).
// 건너뛰기는 **링크 판정 뒤**, 그리고 `web/` 깊이 1 의 **실디렉터리**에만 적용한다
// (code-review r2 G-4) — 이름만 보고 먼저 건너뛰면 그 이름을 링크 이름으로 써서 열거를 지나간다
// (`src/dist` → legacy). 그 형태도 ④ 전수가 받아내지만 사유 문면이 덜 정확해진다.
function collectLinks(directory, depth) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (!entry.isSymbolicLink() && depth === 0 && LINK_SCAN_SKIP.has(entry.name)) {
      continue;
    }
    if (entry.isSymbolicLink()) {
      let pointsTo = '(해석 불가)';
      try {
        pointsTo = realpathSync(full);
      } catch {
        /* 끊어진 링크 — 링크라는 사실로 이미 실패다 */
      }
      fail(`web/ 안에 심볼릭 링크가 있다: ${relative(webRoot, full)} → ${pointsTo}`);
      continue;
    }
    if (entry.isDirectory()) {
      collectLinks(full, depth + 1);
    }
  }
}
collectLinks(webRoot, 0);

// ③b `src` 안에 **TS 프로그램이 보지 못하는 스크립트**를 두지 않는다(verifier r4 M-8 처방 ③).
// `allowJs` 가 꺼져 있으므로 `src/*.js` 는 tsc 의 프로그램에 아예 들어오지 않는다 — 그 파일의
// `import('openapi-fetch')` 는 TS 층의 집합 등식에도, 네트워크 금지 린트의 `files` 에도 걸리지
// 않았다(실측). 파일 종류로 그 사각을 없앤다. 번들 층은 그 파일을 보지만, 한 층이 아예 보지
// 못하는 자리를 남기지 않는다.
const NON_TS_SCRIPT = /\.(js|mjs|cjs|jsx)$/;
function collectNonTsScripts(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) {
      collectNonTsScripts(full);
      continue;
    }
    if (entry.isFile() && NON_TS_SCRIPT.test(entry.name)) {
      fail(`src 안에 TS 프로그램이 보지 못하는 스크립트가 있다: ${relative(webRoot, full)}`);
    }
  }
}
collectNonTsScripts(srcDir);

// ④ 프로그램 **전수**(import·files·reference 로 끌려 들어온 파일 포함).
const program = ts.createProgram({ rootNames: parsed.fileNames, options: parsed.options });
const programFiles = program.getSourceFiles();
const inSrcFiles = [];
for (const sourceFile of programFiles) {
  let realPath;
  try {
    realPath = realpathSync(sourceFile.fileName);
  } catch {
    fail(`프로그램 파일을 해석하지 못했다: ${sourceFile.fileName}`);
    continue;
  }
  if (!insideAllowedRoots(realPath)) {
    fail(`프로그램 파일이 web/src·web/node_modules 밖이다: ${sourceFile.fileName} → ${realPath}`);
    continue;
  }
  if (realPath.startsWith(srcReal + sep)) {
    inSrcFiles.push({ sourceFile, name: `src/${relative(srcReal, realPath).split(sep).join('/')}` });
  }
}

// ⑤ 호출 자리 집합 등식 + 비리터럴 동적 import 금지.
//
// 세는 기준은 지정자 **문자열**이 아니라 **해석된 모듈의 realpath** 다(verifier r2 M-5).
// 문자열로 세면 `import cc from '../node_modules/openapi-fetch'` 같은 상대 경로 한 줄로 집합
// 등식이 열렸다(실측: 게이트 넷 exit 0). 해석 결과가 `node_modules/openapi-fetch/` 안이면 —
// 지정자가 패키지 이름이든 서브패스든 상대 경로든 — 같은 자리에 센다.
// 식별은 **패키지 이름**이다 — 디렉터리 이름이 아니다(verifier r4 M-9). npm 별칭 의존
// (`"of2": "npm:openapi-fetch@^0.17.0"`)은 같은 코드를 `node_modules/of2` 에 설치하고, 디렉터리
// 접두로 비교하던 동안 두 층이 모두 그것을 클라이언트 패키지로 보지 못했다(실측).

// 판정은 세 갈래다 — `client` · `other` · `unresolved`. **해석 실패를 지정자 문자열 비교로
// 돌리지 않는다**(verifier r3 M-7): 그 fail-open 때문에 `resolve.alias` 로 붙인 이름 + `src` 안
// 주변 선언(`declare module '@oa'`)이 집합 등식을 통째로 지나갔다 — 문자열 `'@oa'` 는
// `'openapi-fetch'` 와 다르고, 그 별칭을 실제로 해석하는 것은 Vite 뿐이다(그래서 같은 등식을
// 번들 그래프 경계에서도 센다).
const classifyApiSpecifier = (specifier, containingFile) => {
  const resolved = ts.resolveModuleName(
    specifier,
    containingFile,
    parsed.options,
    ts.sys,
  ).resolvedModule;
  if (resolved === undefined) {
    return 'unresolved';
  }
  let resolvedReal;
  try {
    resolvedReal = realpathSync(resolved.resolvedFileName);
  } catch {
    resolvedReal = resolved.resolvedFileName;
  }
  return packageNameOf(resolvedReal, { stopAt: webRoot }) === API_CLIENT_PACKAGE
    ? 'client'
    : 'other';
};

// 해석되지 않은 지정자 중 **허용하는 것은 하나**다: `src` 안에 그 파일이 실제로 있는 경로
// 지정자(`./index.css` 처럼 tsc 가 타입을 모르는 것, `?raw` 같은 접미가 붙은 것). 근거는 그
// 파일이 **번들 모듈 그래프 경계**(메인·워커)의 전수 대상이라는 것 — 같은 파일을 두 층이 본다.
// 반대로 **bare 지정자**가 해석되지 않으면 모름이고, 모르는 것은 통과시키지 않는다.
const unresolvedIsFileInSrc = (specifier, containingFile) => {
  if (!specifier.startsWith('.') && !specifier.startsWith('/')) {
    return false;
  }
  const withoutQuery = specifier.split('?')[0];
  let candidateReal;
  try {
    candidateReal = realpathSync(resolve(dirname(containingFile), withoutQuery));
  } catch {
    return false;
  }
  return candidateReal === srcReal || candidateReal.startsWith(srcReal + sep);
};

const apiImporters = new Set();
for (const { sourceFile, name } of inSrcFiles) {
  for (const imported of ts.preProcessFile(sourceFile.text, true, true).importedFiles) {
    const verdict = classifyApiSpecifier(imported.fileName, sourceFile.fileName);
    if (verdict === 'client') {
      apiImporters.add(name);
    } else if (
      verdict === 'unresolved' &&
      !unresolvedIsFileInSrc(imported.fileName, sourceFile.fileName)
    ) {
      fail(
        `지정자를 해석할 수 없다(어느 모듈인지 게이트가 볼 수 없다): ${name} → ${imported.fileName}`,
      );
    }
  }

  const visit = (node) => {
    if (
      ts.isCallExpression(node) &&
      node.expression.kind === ts.SyntaxKind.ImportKeyword &&
      !(node.arguments.length > 0 && ts.isStringLiteralLike(node.arguments[0]))
    ) {
      fail(`동적 import 대상이 리터럴이 아니다(어느 모듈인지 게이트가 볼 수 없다): ${name}`);
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
}

for (const importer of apiImporters) {
  if (importer !== API_CALL_SITE) {
    fail(`${API_CLIENT_PACKAGE} 를 호출 자리 밖에서 import 한다: ${importer}`);
  }
}
if (!apiImporters.has(API_CALL_SITE)) {
  fail(
    `${API_CLIENT_PACKAGE} 의 호출 자리(${API_CALL_SITE})가 그것을 import 하지 않는다 — 집합 등식이 깨졌다`,
  );
}

// ⑦ 허용 뿌리 `node_modules` 의 **출처**를 잠금 파일에서 본다(verifier r2 M-4).
//
// ④ 는 `node_modules` 안을 허용 뿌리로 둔다 — 그 전제는 「거기 있는 것은 레지스트리에서 온
// 패키지다」이고, `file:` 의존 한 줄 + `.npmrc` 의 `install-links` 한 줄이면 web 밖 디렉터리의
// **사본**이 `node_modules` 안에 실디렉터리로 들어온다(실측: 그 상태에서 게이트 넷 전부 exit 0 ·
// dist 에 밖 코드). 그래서 `npm ci` 가 따르는 정본인 잠금 파일을 같은 층에서 단언한다.
const lockPath = resolve(webRoot, 'package-lock.json');
if (!existsSync(lockPath)) {
  fail('package-lock.json 이 없다 — `npm ci` 의 정본이 없으면 node_modules 의 출처를 알 수 없다');
} else {
  let lock;
  try {
    lock = JSON.parse(readFileSync(lockPath, 'utf8'));
  } catch (caught) {
    fail(`package-lock.json 을 읽지 못했다: ${caught.message}`);
  }
  // **등록부가 있고 비지 않았다**를 먼저 단언한다(code-review r3 H-2 · verifier r3 L-7) —
  // 전수 단언은 대상이 0 건이면 공허하게 참이다. `lockfileVersion: 1`(npm 6 형: `packages`
  // 없음)과 `packages: {}` 둘 다 그 자리였다(실측: 둘 다 exit 0). 이 저장소에 같은 클래스의
  // 선례가 있다 — `leakPatternGate` 는 패턴이 0 건이면 멈춘다.
  const lockVersion = typeof lock?.lockfileVersion === 'number' ? lock.lockfileVersion : 0;
  if (lockVersion < 2) {
    fail(`package-lock.json 의 lockfileVersion 이 2 미만이다(${lockVersion}) — packages 등록부가 없다`);
  }
  const lockPackages = lock?.packages;
  if (typeof lockPackages !== 'object' || lockPackages === null) {
    fail('package-lock.json 에 packages 등록부가 없다');
  } else if (Object.keys(lockPackages).length === 0) {
    fail('package-lock.json 의 packages 등록부가 비었다 — 전수 단언이 공허하게 통과한다');
  } else if (!Object.prototype.hasOwnProperty.call(lockPackages, '')) {
    fail('package-lock.json 의 packages 에 뿌리 항목("")이 없다');
  }
  for (const [name, entry] of Object.entries(lock?.packages ?? {})) {
    if (name === '') {
      continue; // 뿌리 항목 자신 — `resolved` 가 없는 것이 정상이다.
    }
    if (entry.link === true) {
      fail(`잠금 파일 항목이 로컬 링크다: ${name}`);
      continue;
    }
    if (typeof entry.resolved !== 'string') {
      // bundled dependency 는 `resolved` 가 없는 것이 npm 의 **정상 모양**이다(code-review r3
      // H-3) — 그 파일은 부모 패키지의 tarball 안에 들어 있으므로 출처는 **부모**가 정한다.
      // 그래서 부모 항목이 레지스트리 URL 인지로 센다. 부모를 못 찾으면 모름이고 위반이다.
      if (entry.inBundle === true) {
        const parentName = name.slice(0, name.lastIndexOf('/node_modules/'));
        const parent = name.includes('/node_modules/') ? lockPackages?.[parentName] : undefined;
        if (typeof parent?.resolved !== 'string' || !parent.resolved.startsWith('https://')) {
          fail(`inBundle 항목의 부모 출처를 확인할 수 없다: ${name}`);
        }
        continue;
      }
      fail(`잠금 파일 항목에 resolved 가 없다(출처 불명): ${name}`);
      continue;
    }
    if (!entry.resolved.startsWith(REGISTRY_ORIGIN)) {
      fail(
        `잠금 파일 항목의 출처가 레지스트리(${REGISTRY_ORIGIN})가 아니다: ${name} → ${entry.resolved}`,
      );
    }
    if (typeof entry.version === 'string' && /^(file:|link:|git|github:|npm:)/.test(entry.version)) {
      fail(`잠금 파일 항목의 버전이 레지스트리 판이 아니다: ${name} → ${entry.version}`);
    }
  }

  // 클라이언트 패키지가 **한 자리에만** 설치돼 있는가 — 집합 등식(verifier r4 M-9).
  // npm 별칭 항목은 `name` 필드로 실제 패키지를 밝히고, `resolved` 는 그 tarball 을 가리킨다.
  // 둘 중 어느 쪽으로든 같은 패키지인 항목을 모아 경로 집합이 하나인지 본다 — 별칭 의존은
  // `package.json`·잠금 diff 에 한 줄로 남지만, 게이트 없이는 그 한 줄이 집합 등식을 열었다.
  const apiInstallPaths = new Set();
  for (const [name, entry] of Object.entries(lockPackages ?? {})) {
    if (name === '') {
      continue;
    }
    const byName = entry?.name === API_CLIENT_PACKAGE;
    const byTarball =
      typeof entry?.resolved === 'string' && entry.resolved.includes(`/${API_CLIENT_PACKAGE}/-/`);
    if (byName || byTarball) {
      apiInstallPaths.add(name);
    }
  }
  const expectedInstallPath = `node_modules/${API_CLIENT_PACKAGE}`;
  for (const installPath of apiInstallPaths) {
    if (installPath !== expectedInstallPath) {
      // 사유를 갈라 적는다(verifier r5 L-13) — 중첩 판 분기도 같은 술어로 멈추는데(fail-closed 는
      // 맞다) 문면이 「별칭 의존」이라고만 말하면 진단이 어긋난다. 판정은 그대로다.
      const nested = installPath.lastIndexOf('/node_modules/') > 0;
      const aliasDirectory = !installPath.endsWith(`/${API_CLIENT_PACKAGE}`);
      const reason = nested
        ? aliasDirectory
          ? '중첩 사본 + 별칭 디렉터리'
          : '중첩 사본'
        : aliasDirectory
          ? '별칭 디렉터리'
          : '같은 이름의 다른 자리';
      fail(
        `${API_CLIENT_PACKAGE} 가 ${expectedInstallPath} 밖에도 설치돼 있다(${reason}): ${installPath}`,
      );
    }
  }
  if (!apiInstallPaths.has(expectedInstallPath)) {
    fail(`${API_CLIENT_PACKAGE} 가 ${expectedInstallPath} 에 설치돼 있지 않다 — 집합 등식이 깨졌다`);
  }
}

// `install-links` 는 `file:` 의존을 **복사**로 바꿔 realpath 검사를 지나가게 만드는 손잡이다 —
// 그 한 줄이 들어오면 멈춘다.
const npmrcPath = resolve(webRoot, '.npmrc');
if (existsSync(npmrcPath)) {
  for (const line of readFileSync(npmrcPath, 'utf8').split('\n')) {
    if (/^\s*install-links\s*=/.test(line)) {
      fail(`web/.npmrc 에 install-links 설정이 있다: ${line.trim()}`);
    }
  }
}

// ⑥ 번들 그래프 경계 플러그인이 `vite.config.ts` 에 등재돼 있는가 (AST).
const viteConfigPath = resolve(webRoot, 'vite.config.ts');
if (!existsSync(resolve(webRoot, 'scripts', 'module-graph-boundary.mjs'))) {
  fail('번들 그래프 경계 플러그인 파일이 없다: scripts/module-graph-boundary.mjs');
}
if (!existsSync(viteConfigPath)) {
  fail('vite.config.ts 가 없다');
} else {
  const viteSource = ts.createSourceFile(
    viteConfigPath,
    readFileSync(viteConfigPath, 'utf8'),
    ts.ScriptTarget.ESNext,
    true,
    ts.ScriptKind.TS,
  );

  const boundaryBindings = new Set();
  for (const statement of viteSource.statements) {
    if (
      !ts.isImportDeclaration(statement) ||
      !ts.isStringLiteralLike(statement.moduleSpecifier) ||
      statement.moduleSpecifier.text !== boundaryPluginSpecifier
    ) {
      continue;
    }
    const clause = statement.importClause;
    if (clause?.name !== undefined) {
      boundaryBindings.add(clause.name.text);
    }
    if (clause?.namedBindings !== undefined && ts.isNamedImports(clause.namedBindings)) {
      for (const element of clause.namedBindings.elements) {
        boundaryBindings.add(element.name.text);
      }
    }
  }

  // 세는 것은 「이름이 배열 안에 나타나는가」가 아니라 **배열의 직접 원소가 그 플러그인의
  // 호출인가**다(code-review r2 G-1). 조건식·spread·중첩 인자 안의 언급은 등재로 세지 않는다 —
  // `cond ? false : boundary()` 한 줄로 경계를 끈 채 「등재됨」을 출력하던 자리다.
  //
  // 그리고 **두 자리**를 본다: 메인 `plugins` 와 `worker.plugins`(워커는 별도 Rollup 빌드 —
  // verifier r2 H-3).
  const arrayLiteralOf = (initializer) => {
    if (ts.isArrayLiteralExpression(initializer)) {
      return initializer;
    }
    if (ts.isArrowFunction(initializer) || ts.isFunctionExpression(initializer)) {
      if (ts.isArrayLiteralExpression(initializer.body)) {
        return initializer.body;
      }
      if (ts.isBlock(initializer.body)) {
        for (const statement of initializer.body.statements) {
          if (
            ts.isReturnStatement(statement) &&
            statement.expression !== undefined &&
            ts.isArrayLiteralExpression(statement.expression)
          ) {
            return statement.expression;
          }
        }
      }
    }
    return undefined;
  };

  const hasBoundaryElement = (initializer) => {
    const array = arrayLiteralOf(initializer);
    if (array === undefined) {
      return false;
    }
    return array.elements.some(
      (element) =>
        ts.isCallExpression(element) &&
        ts.isIdentifier(element.expression) &&
        boundaryBindings.has(element.expression.text),
    );
  };

  // `worker` 속성의 범위를 먼저 모아 두고, 그 안의 `plugins` 와 밖의 `plugins` 를 가른다.
  const workerRanges = [];
  const collectWorkerRanges = (node) => {
    if (ts.isPropertyAssignment(node) && ts.isIdentifier(node.name) && node.name.text === 'worker') {
      workerRanges.push([node.pos, node.end]);
    }
    ts.forEachChild(node, collectWorkerRanges);
  };
  collectWorkerRanges(viteSource);
  const insideWorker = (node) =>
    workerRanges.some(([start, end]) => node.pos >= start && node.end <= end);

  let mainRegistered = false;
  let workerRegistered = false;
  const visitConfig = (node) => {
    if (ts.isPropertyAssignment(node) && ts.isIdentifier(node.name) && node.name.text === 'plugins') {
      if (hasBoundaryElement(node.initializer)) {
        if (insideWorker(node)) {
          workerRegistered = true;
        } else {
          mainRegistered = true;
        }
      }
    }
    ts.forEachChild(node, visitConfig);
  };
  visitConfig(viteSource);

  if (boundaryBindings.size === 0) {
    fail(`vite.config.ts 가 ${boundaryPluginSpecifier} 를 import 하지 않는다`);
  } else {
    if (!mainRegistered) {
      fail('vite.config.ts 의 plugins 배열에 번들 그래프 경계 플러그인 **호출**이 없다');
    }
    if (!workerRegistered) {
      fail(
        'vite.config.ts 의 worker.plugins 에 번들 그래프 경계 플러그인 **호출**이 없다 — 워커는 별도 Rollup 빌드다',
      );
    }
  }
}

finishIfFailed();

console.log(
  `컴파일 경계 전제 성립: src 는 web/ 안의 실디렉터리 · rootDir=src · include=["src"] · allowJs 꺼짐 · ` +
    `paths 없음 · web/ 심볼릭 링크 0(설치본·빌드·실행 산출물 제외) · src 안 비 TS 스크립트 0 · 프로그램 파일 ${programFiles.length}(src 안 ${inSrcFiles.length}) 전부 ` +
    `src·node_modules 안 · **이 프로그램 안에서** ${API_CLIENT_PACKAGE} import 집합 == {${API_CALL_SITE}}(TS 프로그램 한정 — 전수는 번들 층이 센다) · 비리터럴 동적 import 0 · ` +
    `번들 그래프 경계 플러그인이 plugins·worker.plugins 두 자리에 원소로 등재됨(활성은 카나리 W-8 이 잰다).`,
);
