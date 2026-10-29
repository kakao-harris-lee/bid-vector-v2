#!/usr/bin/env node
// ADR 0011 D-3 — **컴파일 경계와 그 전제**를 잰다. 이 게이트가 닫는 것은 넷이다:
//
//   ① 기준점 자체(`web/src`)가 링크가 아니고 `web/` 안인가 — code-review r1 F-1. 기준점을
//      realpath 로 먼저 풀어 쓰면 「링크가 가리키는 디렉터리의 파일이 그 디렉터리 안에 있다」는
//      **항진명제**가 되고, 게이트는 그 상태에서 「링크 0」을 적극적으로 보고한다(초록보다 나쁜
//      거짓 보증 — 실측으로 확인했다).
//   ② tsconfig 의 네 값(rootDir·include·allowJs·paths) — 경계 자신의 전제다. 하나만 바꾸면
//      TS6059 가 날 자리가 사라지는데 typecheck 는 자기 전제의 소실을 보지 못한다.
//   ④ **프로그램 전수**가 `web/src` ∪ `web/node_modules` 안인가 — verifier r1 H-2. `.d.ts` 는
//      TS6059 의 대상이 아니라서 밖의 선언 파일을 `import type` 하면 게이트 넷이 모두 초록이었다.
//      루트 이름(`parsed.fileNames`)이 아니라 `ts.createProgram(...).getSourceFiles()` 를 본다 —
//      import 로 끌려온 파일·`files`·triple-slash reference·`src` 안 링크가 한 술어로 닫힌다.
//   ⑤ 호출 자리 집합 등식 — code-review r1 F-2. **증명 범위는 「클라이언트 패키지를 끌어오는
//      자리 == 한 자리」까지이고 「서버 호출 경로가 하나뿐」은 아니다**(7A 검토 r5).
//      `openapi-fetch` 를 import 하는 파일 집합이
//      `{src/api/client.ts}` 와 **같아야** 한다. eslint 의 `no-restricted-imports` 는 정적
//      import 만 보므로 리터럴 동적 `import('openapi-fetch')` 로 손 계약 클라이언트를 만들 수
//      있었다. 대상이 리터럴이 아닌 동적 import 는 「어느 모듈인지 게이트가 볼 수 없다」이므로
//      같은 자리에서 막는다.
//
// 본문의 번호는 실행 순서다 — ① 기준점 · ② tsconfig 유효 옵션 · ③ `web/` 링크 · ③b `src` 안
// 비 TS 스크립트 · ④ 프로그램 전수 · ⑤ 집합 등식 · ⑦ 잠금 출처 · ⑥ 플러그인 등재(PR #75 리뷰 P
// — 머리와 본문이 어긋나 있었다).
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

// 종료는 **`process.exitCode` 로** 한다(PR #75 리뷰 R · 다른 두 스크립트와 같게) — `process.exit()`
// 는 보류 중인 출력 쓰기를 끊을 수 있다. 전제가 깨진 자리에서는 `true` 를 돌려 호출자가 아래
// 단계를 건너뛰게 한다(아래 단계는 그 전제를 쓰므로 그냥 두면 스택 트레이스로 죽는다).
function finishIfFailed() {
  if (failures.length === 0) {
    return false;
  }
  console.error('컴파일 경계 전제가 깨졌다 (ADR 0011 D-3):');
  for (const line of failures) {
    console.error(`  - ${line}`);
  }
  process.exitCode = 1;
  return true;
}

// 본문을 함수에 담아 그 `return` 으로 조기 종료한다 — 줄은 그대로 두고(들여쓰기를 바꾸면 템플릿
// 리터럴의 내용이 바뀐다) 바깥만 감싼다.
function run() {

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
if (finishIfFailed()) return;

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
  if (finishIfFailed()) return;
}
const parsed = ts.parseJsonConfigFileContent(config, ts.sys, webRoot, undefined, tsconfigPath);
for (const diagnostic of parsed.errors) {
  fail(`tsconfig 진단: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, ' ')}`);
}
if (finishIfFailed()) return;

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

// **M7/7E-1 — `resolveJsonModule` 은 파일에 `false` 로 **명시**돼 있어야 한다.**
//
// 켜져 있으면 `web/src` 안의 정적 JSON import 가 세 층을 모두 지난다(경계의 술어가 「src 밖
// 금지」이고 그 파일은 안에 있다 — 7E-1 Phase 1 설계 검토 우회 ③).
//
// **생략은 「꺼짐」이 아니다(실측).** `moduleResolution: "bundler"` 에서는 이 값을 적지 않으면
// JSON import 가 **그대로 컴파일된다**(probe: typecheck exit 0). `false` 를 명시하면 TS2732 로
// 거부된다(같은 probe: exit 2). 그러므로 술어는 「true 가 아님」이 아니라 「명시된 false」다 —
// `parsed.options` 는 암묵 기본값을 드러내지 않으므로 **파일의 선언**을 본다.
const declaredResolveJson = config.compilerOptions?.resolveJsonModule;
if (declaredResolveJson !== false) {
  fail(
    'tsconfig 의 compilerOptions.resolveJsonModule 이 false 로 명시돼 있지 않다 ' +
      `(지금: ${JSON.stringify(declaredResolveJson)}) — 생략은 moduleResolution=bundler 에서 「켜짐」이다(실측)`,
  );
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
/**
 * `src` 안에 **둘 수 있는 파일 종류**(v r2 M-2). 앞 판은 금지 확장자를 둘로 **열거**했고
 * (`js|mjs|cjs|jsx` 와 `json|json5|ya?ml|csv|xml`), JSON 내용을 `rows.txt` 로 두고
 * `new URL('./rows.txt', import.meta.url)` 로 참조하면 세 층이 모두 초록이었다(실측: 번들이
 * `dist/assets/rows-*.txt` 로 내보내고 화면이 그 값을 받았다).
 *
 * 열거를 늘리는 대신 **뒤집는다** — 아는 종류만 받고 **모르는 종류는 위반**이다. 번들 층의
 * 모듈 종류 술어와 같은 꼴이고, 자산이 필요해지면 이 목록에 한 줄을 더해야 하므로 **빠뜨림이
 * 조용하지 않다**.
 */
const ALLOWED_SRC_FILE = /\.(tsx?|css)$/;

function collectSrcFileKinds(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) {
      collectSrcFileKinds(full);
      continue;
    }
    if (entry.isFile() && !ALLOWED_SRC_FILE.test(entry.name)) {
      fail(
        `src 안에 아는 종류가 아닌 파일이 있다: ${relative(webRoot, full)} ` +
          '— 코드(.ts/.tsx)와 스타일(.css)만 둔다. 데이터·자산은 계약 밖 데이터 모양의 입구다',
      );
    }
  }
}
collectSrcFileKinds(srcDir);

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
/**
 * **서버 호출 능력을 쥐는 자리도 한 곳이다**(v M-3).
 *
 * 앞 판은 `openapi-fetch` **패키지**의 import 집합만 셌다. 그 등식은 화면이 `../api/client` 를
 * 직접 import 해 **계약 목록 밖 operation** 을 부르는 길을 막지 못했다(실측: lint·typecheck·W-4·
 * 번들·vitest·journey 전부 초록). 패키지 한 겹 안쪽의 **우리 모듈**에 같은 등식을 건다.
 *
 * 술어는 **가져오는 이름**이다 — 서버 호출 능력을 나르는 것은 `apiClient` 바인딩(그리고 그것을
 * 품는 namespace·default import)이고, 같은 모듈의 CSRF 상태 함수(`rememberCsrf` 등)는 호출
 * 능력이 아니다. 이름을 보지 않고 모듈만 세면 그 함수를 쓰는 자리까지 막혀 게이트가 넓어진다.
 *
 * **test 파일은 대상 밖**이다: 출하되지 않고 번들 그래프에도 없으며, 클라이언트의 미들웨어를
 * 재는 test 는 그 객체를 손에 쥐어야 한다(`client.test.ts` 가 그 자리다).
 */
const CLIENT_MODULE = 'src/api/client.ts';
const CLIENT_MODULE_CALLER = 'src/api/queries.ts';
const CLIENT_CAPABILITY_BINDING = 'apiClient';
const isTestFile = (name) => /\.test\.tsx?$/.test(name);

/**
 * 그 파일이 **호출 능력을 나르는 간선**을 갖는가 — 셋을 본다(CR r2 M-1 · v r2 M-1).
 *
 * 앞 판은 `ts.isImportDeclaration` **하나만** 방문했다. 그래서 `export { apiClient } from './client'`
 * (`ExportDeclaration`) · `export * from './client'` · 리터럴 동적 `import('./client')` 가 모두
 * 지나갔고(실측 넷), 화면이 그 재수출을 통해 목록 밖 operation 을 부를 수 있었다.
 *
 * 세 간선의 성질이 다르다:
 * - **import 선언**: `apiClient` 라는 **이름**을 가져올 때만 능력이다(같은 모듈의 CSRF 상태
 *   함수는 능력이 아니다 — 그 이름 좁히기를 유지한다).
 * - **재수출**: 무엇이 나가는지와 무관하게 **그 모듈을 다른 이름으로 다시 내보낸다** — 허용
 *   자리를 포함해 **어디서도** 두지 않는다(그 한 줄이 집합 등식을 통째로 우회시킨다).
 * - **동적 import**: 모듈 객체를 그대로 손에 쥔다 — 허용 자리 밖이면 능력이다.
 */
const clientCapabilityEdges = (sourceFile, moduleRealPath) => {
  const edges = [];
  const resolvesToClient = (specifier) =>
    ts.isStringLiteralLike(specifier) && resolveSpecifierReal(specifier.text, sourceFile.fileName) === moduleRealPath;

  const visit = (node) => {
    if (ts.isImportDeclaration(node) && resolvesToClient(node.moduleSpecifier)) {
      const clause = node.importClause;
      if (clause === undefined || clause.name !== undefined) {
        edges.push('default·side-effect import');
      } else if (clause.namedBindings !== undefined) {
        if (ts.isNamespaceImport(clause.namedBindings)) {
          edges.push('namespace import');
        } else {
          for (const element of clause.namedBindings.elements) {
            if ((element.propertyName ?? element.name).text === CLIENT_CAPABILITY_BINDING) {
              edges.push(`import { ${CLIENT_CAPABILITY_BINDING} }`);
            }
          }
        }
      }
    }
    if (ts.isExportDeclaration(node) && node.moduleSpecifier !== undefined && resolvesToClient(node.moduleSpecifier)) {
      edges.push('재수출(export … from)');
    }
    if (
      ts.isCallExpression(node) &&
      node.expression.kind === ts.SyntaxKind.ImportKeyword &&
      node.arguments.length > 0 &&
      resolvesToClient(node.arguments[0])
    ) {
      edges.push('동적 import()');
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return edges;
};
let clientModuleReal;
try {
  clientModuleReal = realpathSync(resolve(webRoot, CLIENT_MODULE));
} catch {
  fail(`생성 타입 클라이언트 모듈이 없다: ${CLIENT_MODULE}`);
}

const resolveSpecifierReal = (specifier, containingFile) => {
  const resolved = ts.resolveModuleName(specifier, containingFile, parsed.options, ts.sys).resolvedModule;
  if (resolved === undefined) {
    return undefined;
  }
  try {
    return realpathSync(resolved.resolvedFileName);
  } catch {
    return resolved.resolvedFileName;
  }
};

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
// 지정자(`./index.css` 처럼 tsc 가 타입을 모르는 것). 근거는 그 파일이 **번들 모듈 그래프
// 경계**(메인·워커)의 전수 대상이라는 것 — 같은 파일을 두 층이 본다. 반대로 **bare 지정자**가
// 해석되지 않으면 모름이고, 모르는 것은 통과시키지 않는다.
//
// **쿼리 접미(`?raw`·`?url`·`?inline` …)는 허용하지 않는다**(v M-4). 그 형태는 tsc 를 지나고
// (`vite/client` 의 주변 선언이 타입을 준다) 파일 종류 열거를 비켜 간다 — 같은 JSON 을
// `rows.txt?raw` 로 들이면 데이터 파일 술어도, 데이터 모듈 술어도 못 봤다(실측). 열거를 늘리는
// 대신 **그 입구 자체**를 없앤다: `src` 안에서 쿼리 import 를 쓰지 않는다. 필요가 생기면 이
// 게이트를 고쳐야 하고, 그 변경은 diff 에 남는다.
const hasImportQuery = (specifier) => specifier.includes('?');

const unresolvedIsFileInSrc = (specifier, containingFile) => {
  if (!specifier.startsWith('.') && !specifier.startsWith('/')) {
    return false;
  }
  if (hasImportQuery(specifier)) {
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
const clientModuleImporters = new Set();
for (const { sourceFile, name } of inSrcFiles) {
  for (const imported of ts.preProcessFile(sourceFile.text, true, true).importedFiles) {
    if (hasImportQuery(imported.fileName)) {
      fail(
        `src 안에서 쿼리 접미 import 를 쓴다: ${name} → ${imported.fileName} ` +
          '— tsc 를 지나고 파일 종류 술어를 비켜 가는 입구다(v M-4)',
      );
    }
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

  if (name !== CLIENT_MODULE && !isTestFile(name)) {
    for (const edge of clientCapabilityEdges(sourceFile, clientModuleReal)) {
      // **재수출은 허용 자리에서도 금지다** — 그 한 줄이 집합 등식을 통째로 우회시킨다.
      if (edge.startsWith('재수출') || name !== CLIENT_MODULE_CALLER) {
        clientModuleImporters.add(`${name}(${edge})`);
      } else {
        clientModuleImporters.add(name);
      }
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

// ⑤b-2 **심볼 참조 전수**(v r3 M-1) — 간선 종류 열거가 보지 못하는 꼴을 TypeChecker 로 받는다.
//
// 간선 술어는 「어느 파일이 그 모듈을 끌어오는가」를 센다. 그러나 허용 자리 안에서 `export
// { apiClient };`(로컬 재수출 — 모듈 지정자가 **없다**)를 쓰면 화면은 `../api/queries` 에서
// 그 이름을 가져오고, 그 import 는 client.ts 로 가는 간선이 아니다(실측: 다섯 층 0).
//
// 그래서 **그 심볼 자체**를 쫓는다: 식별자의 심볼을 해소해(별칭이면 끝까지) client.ts 의 그것과
// 같은지 본다. 어느 이름으로 다시 내보내도 같은 심볼이다.
//
// **이름으로 거르지 않는다**(v r4 M-1): 앞 판은 텍스트가 `apiClient` 인 식별자만 해소했고,
// 그래서 `import * as C from './client'` 처럼 **그 이름이 나타나지 않는** 꼴을 보지 못했다.
const checker = program.getTypeChecker();

/** 별칭을 한 단 따라간다(실패하면 그대로). */
function aliasOrSelf(typeChecker, symbol) {
  try {
    return typeChecker.getAliasedSymbol(symbol);
  } catch {
    return symbol;
  }
}

/** 상대 지정자를 실제 파일 경로로 — 확장자는 TS 해석 규칙대로 붙여 본다. */
function resolveImportTarget(containingFile, specifier) {
  const base = resolve(dirname(containingFile.fileName), specifier);
  for (const candidate of [base, `${base}.ts`, `${base}.tsx`, resolve(base, 'index.ts')]) {
    try {
      return realpathSync(candidate);
    } catch {
      /* 다음 후보 */
    }
  }
  return undefined;
}

/** 별칭(import·재수출·네임스페이스)을 끝까지 따라간 심볼. */
function resolveToTarget(typeChecker, node) {
  let symbol = typeChecker.getSymbolAtLocation(node);
  if (symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0) {
    try {
      symbol = typeChecker.getAliasedSymbol(symbol);
    } catch {
      /* 해소 실패는 호출 자리의 비교에서 걸린다 */
    }
  }
  return symbol;
}

const clientSourceFile = programFiles.find((one) => {
  try {
    return realpathSync(one.fileName) === clientModuleReal;
  } catch {
    return false;
  }
});
const clientModuleSymbol =
  clientSourceFile === undefined ? undefined : checker.getSymbolAtLocation(clientSourceFile);
const clientSymbol = clientModuleSymbol?.exports?.get(CLIENT_CAPABILITY_BINDING);

if (clientSymbol === undefined) {
  fail(`${CLIENT_MODULE} 에서 ${CLIENT_CAPABILITY_BINDING} 심볼을 찾지 못했다 — 이 층이 공허해진다`);
} else {
  for (const { sourceFile, name } of inSrcFiles) {
    if (name === CLIENT_MODULE || name === CLIENT_MODULE_CALLER || isTestFile(name)) {
      continue;
    }
    const visit = (node) => {
      if (ts.isIdentifier(node)) {
        const resolved = resolveToTarget(checker, node);
        if (resolved === clientSymbol || resolved === clientModuleSymbol) {
          fail(
            `서버 호출 능력이 ${CLIENT_MODULE_CALLER} 밖에서 참조된다: ${name} (${node.text}) ` +
              '— 이름을 바꾸거나 네임스페이스로 받아도 같은 심볼이다',
          );
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(sourceFile);
  }
}

// ⑤b-2b **허용 자리의 import 꼴 고정 · export 타입 전수**(v r4 M-1).
//
// 심볼 추적만으로는 한 꼴이 남는다: `import * as C from './client'; export const ns = C;` —
// 그 자리는 **허용된 import** 이고, 밖으로 나가는 것은 `ns` 라는 **다른 이름의 네임스페이스**다.
// 화면은 그것을 계산 키로 집어 미승인 쌍을 부를 수 있었다(실측: 정적 다섯 층 모두 0).
//
// 두 자리에서 닫는다.
//   ⓐ 호출 자리가 client 모듈을 끌어오는 꼴은 **이름 지정 `{ apiClient }` 하나**뿐이다 —
//      네임스페이스·default import 는 그 자체로 위반이다(그 꼴이 아니면 능력이 이름을 잃는다).
//   ⓑ 호출 자리의 **export 전수**에서 그 능력의 타입이 나가지 않는다 — 이름을 무엇으로 바꾸든,
//      `export default` 로 내보내든, 타입 동일성으로 걸린다.
if (clientSymbol !== undefined && clientSourceFile !== undefined) {
  const callerSource = inSrcFiles.find((one) => one.name === CLIENT_MODULE_CALLER)?.sourceFile;
  if (callerSource === undefined) {
    fail(`${CLIENT_MODULE_CALLER} 를 프로그램에서 찾지 못했다 — 아래 두 단언이 공허해진다`);
  } else {
    // ⓐ
    let namedClientImports = 0;
    for (const statement of callerSource.statements) {
      if (
        !ts.isImportDeclaration(statement) ||
        !ts.isStringLiteral(statement.moduleSpecifier) ||
        resolveImportTarget(callerSource, statement.moduleSpecifier.text) !== clientModuleReal
      ) {
        continue;
      }
      const clause = statement.importClause;
      if (clause === undefined || clause.name !== undefined) {
        fail(`${CLIENT_MODULE_CALLER} 가 client 모듈을 default import 로 받는다 — 이름 지정만 허용한다`);
        continue;
      }
      const bindings = clause.namedBindings;
      if (bindings === undefined || ts.isNamespaceImport(bindings)) {
        fail(
          `${CLIENT_MODULE_CALLER} 가 client 모듈을 **네임스페이스**로 받는다 ` +
            '— 그러면 능력이 이름을 잃고 심볼 추적을 비켜 간다(이름 지정 { apiClient } 만 허용)',
        );
        continue;
      }
      for (const element of bindings.elements) {
        const wanted = (element.propertyName ?? element.name).text;
        if (wanted !== CLIENT_CAPABILITY_BINDING) {
          fail(`${CLIENT_MODULE_CALLER} 가 client 모듈에서 ${wanted} 도 가져온다 — 허용은 ${CLIENT_CAPABILITY_BINDING} 하나다`);
        } else {
          namedClientImports += 1;
        }
      }
    }
    if (namedClientImports !== 1) {
      fail(
        `${CLIENT_MODULE_CALLER} 의 client import 가 이름 지정 ${CLIENT_CAPABILITY_BINDING} 하나가 ` +
          `아니다(계수 ${namedClientImports})`,
      );
    }

    // ⓑ
    const capabilityType = checker.getTypeOfSymbolAtLocation(
      clientSymbol,
      clientSymbol.valueDeclaration ?? clientSourceFile,
    );
    const callerModuleSymbol = checker.getSymbolAtLocation(callerSource);
    for (const exported of callerModuleSymbol === undefined ? [] : checker.getExportsOfModule(callerModuleSymbol)) {
      const target = (exported.flags & ts.SymbolFlags.Alias) !== 0 ? aliasOrSelf(checker, exported) : exported;
      if (target === clientSymbol || target === clientModuleSymbol) {
        fail(`${CLIENT_MODULE_CALLER} 가 서버 호출 능력을 ${exported.name} 으로 내보낸다`);
        continue;
      }
      const declaration = target.valueDeclaration ?? target.declarations?.[0];
      if (declaration === undefined) {
        continue;
      }
      const type = checker.getTypeOfSymbolAtLocation(target, declaration);
      if (type === capabilityType || type === checker.getTypeOfSymbolAtLocation(clientModuleSymbol ?? clientSymbol, clientSourceFile)) {
        fail(
          `${CLIENT_MODULE_CALLER} 의 export ${exported.name} 이 서버 호출 능력 자체를 나른다 ` +
            '— 이름을 바꾸거나 네임스페이스로 싸도 타입이 같다',
        );
      }
    }
  }
}

// ⑤b-3 **재수출 금지**(v r3 M-1 양성 대조 ①) — 위 층은 「허용 자리 밖의 참조」를 센다. 그
// 참조를 만들 수 있게 하는 자리는 **허용 자리 안의 재수출**이다. 그것 자체를 금하면 소비자가
// 생기기 전에 붉다 — 가능 조건을 닫는 쪽이 사용을 세는 쪽보다 앞선다. 모듈 지정자가 **있는**
// 재수출(`export … from './client'`)은 간선 층이 이미 허용 자리에서도 거부한다; 여기서 받는
// 것은 지정자가 **없어** 간선이 아닌 꼴(`export { apiClient };`)이다.
if (clientSymbol !== undefined) {
  for (const { sourceFile, name } of inSrcFiles) {
    if (isTestFile(name)) {
      continue;
    }
    const visitExports = (node) => {
      if (ts.isExportSpecifier(node)) {
        let symbol = checker.getExportSpecifierLocalTargetSymbol(node);
        if (symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0) {
          try {
            symbol = checker.getAliasedSymbol(symbol);
          } catch {
            /* 해소 실패는 아래 비교에서 걸린다 */
          }
        }
        if (symbol === clientSymbol) {
          fail(
            `${CLIENT_CAPABILITY_BINDING} 심볼을 재수출한다: ${name} (${node.getText()}) ` +
              '— 재수출이 있으면 허용 자리를 거쳐 어느 파일로도 능력이 건너간다',
          );
        }
      }
      ts.forEachChild(node, visitExports);
    };
    visitExports(sourceFile);
  }
}

// ⑤b 서버 **호출 능력**의 자리 집합 등식(v M-3) — 화면은 `queries.ts` 를 지난다.
for (const importer of clientModuleImporters) {
  if (importer !== CLIENT_MODULE_CALLER) {
    fail(
      `서버 호출 능력을 ${CLIENT_MODULE_CALLER} 밖으로 나르는 간선이 있다: ${importer} ` +
        '— 화면이 계약 목록 밖 operation 을 부르는 길이 열린다(재수출·동적 import 포함)',
    );
  }
}
if (!clientModuleImporters.has(CLIENT_MODULE_CALLER)) {
  fail(`${CLIENT_MODULE_CALLER} 가 ${CLIENT_MODULE} 을 import 하지 않는다 — 집합 등식이 깨졌다`);
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
        // 부모 출처도 **같은 결정 자리**를 쓴다(PR #75 리뷰 C) — `https://` 접두만 보면 부모가
        // 비레지스트리 tarball 일 때 bundled 자식이 지나갔다.
        if (typeof parent?.resolved !== 'string' || !parent.resolved.startsWith(REGISTRY_ORIGIN)) {
          fail(`inBundle 항목의 부모 출처가 레지스트리(${REGISTRY_ORIGIN})가 아니다: ${name}`);
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

// ⑤c **`import.meta` 멤버 허용 목록**(v r3 M-2 · CR r3 M-4) — 정적 데이터가 화면에 닿는 주소를
// 없앤다.
//
// 파일 종류 허용 목록(`{ts,tsx,css}`)은 **종류**를 가리지만 그 파일을 **데이터로 읽는 주소**를
// 가리지 못한다: `rows.css` 는 허용 종류이고 `new URL('./rows.css', import.meta.url)` 로 번들에
// 자산으로 실려 화면이 가져다 읽는다(실측). `import.meta.glob` 은 디렉터리째 끌어온다.
//
// 그래서 확장자를 더 열거하는 대신 **주소 쪽을 닫는다**: `import.meta` 의 멤버는 `env` 하나만
// 허용한다(기준 URL 을 얻는 길도, 파일을 훑는 길도 남기지 않는다). 새 멤버가 필요해지면 이
// 게이트가 먼저 붉다.
// **test 파일 면제의 전제**(CR r4 L-11): 다른 층과 같은 면제다. 그 전제는 「앱 코드가 test
// 파일을 import 하지 않는다」이고, 그것은 번들 그래프 층이 센다(진입에서 닿지 않는 파일은
// 번들에 들어오지 않는다). 오늘 `src` 안 test 가 `import.meta.url` 로 자산을 들이는 자리는 0 이다.
const ALLOWED_IMPORT_META_MEMBERS = new Set(['env']);
for (const { sourceFile, name } of inSrcFiles) {
  if (isTestFile(name)) {
    continue;
  }
  const visitMeta = (node) => {
    if (ts.isMetaProperty(node) && node.keywordToken === ts.SyntaxKind.ImportKeyword) {
      const access = node.parent;
      const member =
        access !== undefined && ts.isPropertyAccessExpression(access) && access.expression === node
          ? access.name.text
          : undefined;
      if (member === undefined || !ALLOWED_IMPORT_META_MEMBERS.has(member)) {
        fail(
          `src 안에서 허용 밖 import.meta 멤버를 쓴다: ${name} → import.meta.${member ?? '<동적>'} ` +
            '— 자산 주소·파일 훑기가 정적 데이터를 화면에 들인다(허용: env)',
        );
      }
    }
    ts.forEachChild(node, visitMeta);
  };
  visitMeta(sourceFile);
}

// ⑥ 번들 그래프 경계 플러그인이 `vite.config.ts` 에 등재돼 있는가 (AST).
const viteConfigPath = resolve(webRoot, 'vite.config.ts');
let publicDirVerdict = '판정 없음';
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

  // 게이트 test 의 **수집 여부는 여기서 보지 않는다**(verifier r6 M-12). 앞선 판은 `vite.config.ts`
  // 의 AST 에서 `include` 리터럴을 찾았는데, 그 술어는 자기가 막겠다고 한 형태의 거울상을 못
  // 막았다 — `test.exclude` 한 줄 · 같은 리터럴을 담은 **미끼** 속성 · `describe.skip` ·
  // `package.json` 의 CLI 경로 필터가 모두 그것을 지나면서 test 를 수집에서 뺐다(실측). 수집은
  // **실행 결과**로만 선다: `scripts/check-gate-tests.mjs`(W-7)가 W-6 의 명령에 JSON reporter 를
  // 붙여 돌려 등록부(`config/gate-tests.txt`)의 파일이 passed ≥ 1 · skipped/todo 0 · failed 0
  // 인지 본다.

  // **`publicDir` 는 꺼져 있어야 한다**(CR r3 M-4 · r4 M-1) — 켜져 있으면 그 디렉터리의 아무
  // 파일이 번들 밖 경로로 그대로 복사돼 화면이 같은 출처에서 읽어 갈 수 있고, 그 디렉터리는
  // 위의 파일 종류 술어 **밖**이다. 생략은 꺼짐이 아니라 기본값 켜짐이다.
  //
  // 앞 판은 **파일 안 마지막 `publicDir` 대입**을 봤다. 그래서 최상위에 `publicDir: 'static'`
  // 을 두고 아래 `test:` 블록에 미끼 `publicDir: false` 를 적으면 **초록인 채 입구가 열렸다**
  // (실측). 그러므로 대입을 **모아** 「정확히 하나 · 그 값이 `false`」를 센다. 디렉터리 부재도
  // 이름을 박지 않고 **대입된 값에서** 뽑는다.
  const publicDirAssignments = [];
  const visitPublicDir = (node) => {
    if (
      ts.isPropertyAssignment(node) &&
      node.name !== undefined &&
      (ts.isIdentifier(node.name) || ts.isStringLiteralLike(node.name)) &&
      node.name.text === 'publicDir'
    ) {
      publicDirAssignments.push(node.initializer);
    }
    ts.forEachChild(node, visitPublicDir);
  };
  visitPublicDir(viteSource);

  if (publicDirAssignments.length !== 1) {
    fail(
      `vite.config.ts 의 publicDir 대입이 정확히 하나가 아니다(계수 ${publicDirAssignments.length}) ` +
        '— 둘 이상이면 어느 것이 설정인지 게이트가 볼 수 없다(미끼 한 줄로 열리던 자리)',
    );
  } else {
    const [value] = publicDirAssignments;
    if (value.kind === ts.SyntaxKind.FalseKeyword) {
      publicDirVerdict = '꺼짐';
    } else if (ts.isStringLiteralLike(value)) {
      // 꺼져 있지 않다면 **그 경로의 부재**를 센다(이름을 박지 않는다).
      publicDirVerdict = `'${value.text}'`;
      if (existsSync(resolve(webRoot, value.text))) {
        fail(
          `vite.config.ts 의 publicDir 가 '${value.text}' 이고 그 디렉터리가 있다 ` +
            '— 번들 경계 밖 자산 입구다(이 slice 는 그 입구를 쓰지 않는다)',
        );
      }
    } else {
      fail('vite.config.ts 의 publicDir 값이 false 도 문자열 리터럴도 아니다 — 게이트가 판정할 수 없다');
    }
  }
  // 기본값 경로도 함께 센다 — `publicDir: false` 라도 그 디렉터리가 있으면 의도가 갈린다.
  if (existsSync(resolve(webRoot, 'public'))) {
    fail('web/public/ 이 있다 — 이 slice 는 그 입구를 쓰지 않는다(publicDir 는 꺼져 있다)');
  }

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

if (finishIfFailed()) return;

console.log(
  `컴파일 경계 전제 성립: src 는 web/ 안의 실디렉터리 · rootDir=src · include=["src"] · allowJs 꺼짐 · ` +
    `paths 없음 · resolveJsonModule=false 명시 · web/ 심볼릭 링크 0(설치본·빌드·실행 산출물 제외) · src 안 파일 종류 == {ts,tsx,css} · 프로그램 파일 ${programFiles.length}(src 안 ${inSrcFiles.length}) 전부 ` +
    `src·node_modules 안 · **이 프로그램 안에서** ${API_CLIENT_PACKAGE} import 집합 == {${API_CALL_SITE}}(TS 프로그램 한정 — 전수는 번들 층이 센다) · 호출 능력 간선(import·재수출·동적) 집합 == {src/api/queries.ts} · 그 능력의 심볼 참조가 그 밖에서 0 · 재수출 0 · 호출 자리의 client import 꼴 == 이름 지정 하나 · 그 자리의 export 전수에 그 능력 타입 0(TypeChecker) · 비리터럴 동적 import 0 · src 안 쿼리 접미 import 0 · src 안 import.meta 멤버 == {env} · publicDir 대입 하나 == ${publicDirVerdict} · 그 입구 디렉터리 없음 · ` +
    `번들 그래프 경계 플러그인이 plugins·worker.plugins 두 자리에 원소로 등재됨(활성은 카나리 W-8 이 잰다).`,
);
}

run();
