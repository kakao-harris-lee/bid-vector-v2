#!/usr/bin/env node
// ADR 0011 D-3 — **컴파일 경계의 전제**를 잰다. 실측으로 열린 구멍 둘을 닫는 자리다
// (설계 검토 U-3·U-8 행의 기대가 실측과 달랐다):
//
//   U-3 심볼릭 링크: `web/src` 안의 링크가 legacy 파일을 가리키면 tsc 는 **링크 경로**로
//       파일을 프로그램에 넣는다 — rootDir 밖이 아니므로 TS6059 가 나지 않는다(실측:
//       typecheck exit 0). 「preserveSymlinks 기본값이면 실제 경로로 보니 RED」는 성립하지
//       않았다.
//   U-8 `allowJs`: 켜고 rootDir 밖 legacy `.js` 를 import 하면 TS6059 가 **나지 않는다**
//       (실측: typecheck exit 0). 즉 그 방어는 「켜지 않는다」는 약속뿐이고, 약속은 게이트가
//       아니다.
//
// 그래서 **tsconfig 의 유효 옵션을 컴파일러 자신의 API 로 읽어** 경계를 여는 손잡이 넷
// (rootDir · include · allowJs · paths)을 단언하고, 프로그램 파일 전부가 realpath 기준으로
// `src` 안인지 본다. 열거하는 것은 legacy 경로 이름이 아니라 **이 게이트 자신의 전제**다 —
// 우회하려면 이 스크립트를 바꿔야 하고, 그 변경은 diff 에 남는다.
import { readdirSync, realpathSync } from 'node:fs';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const tsconfigPath = resolve(webRoot, 'tsconfig.json');
const srcDir = resolve(webRoot, 'src');
const srcReal = realpathSync(srcDir);

const failures = [];

const { config, error } = ts.readConfigFile(tsconfigPath, ts.sys.readFile);
if (error !== undefined) {
  console.error(`tsconfig.json 을 읽지 못했다: ${ts.flattenDiagnosticMessageText(error.messageText, ' ')}`);
  process.exit(1);
}
const parsed = ts.parseJsonConfigFileContent(config, ts.sys, webRoot, undefined, tsconfigPath);
if (parsed.errors.length > 0) {
  for (const diagnostic of parsed.errors) {
    console.error(`tsconfig 진단: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, ' ')}`);
  }
  process.exit(1);
}

// ① rootDir 이 src 그대로인가 — 경계 자체.
if (parsed.options.rootDir === undefined || realpathSync(parsed.options.rootDir) !== srcReal) {
  failures.push(`rootDir 이 web/src 가 아니다: ${String(parsed.options.rootDir)}`);
}

// ② include 가 src 하나인가 — 다른 디렉터리를 프로그램에 더하는 자리.
const include = Array.isArray(config.include) ? config.include : undefined;
if (include === undefined || include.length !== 1 || include[0] !== 'src') {
  failures.push(`include 가 ["src"] 가 아니다: ${JSON.stringify(config.include)}`);
}

// ③ allowJs — 켜면 rootDir 밖 .js 가 진단 없이 들어온다(U-8 실측).
if (parsed.options.allowJs === true) {
  failures.push('allowJs 가 켜져 있다 — rootDir 밖 .js 가 진단 없이 프로그램에 들어온다(U-8)');
}

// ④ paths 별칭 — rootDir 밖을 가리키는 짧은 이름을 만드는 자리(U-2). 지금은 별칭이 없고,
//    필요해지면 이 단언을 「src 안만 가리킨다」로 좁혀 고친다.
if (parsed.options.paths !== undefined && Object.keys(parsed.options.paths).length > 0) {
  failures.push(`paths 별칭이 있다: ${Object.keys(parsed.options.paths).join(', ')}`);
}

// ⑤ src 안의 심볼릭 링크 0 (U-3). 링크는 디렉터리든 파일이든 걸린다 — 소스 트리에 링크를 둘
//    이유가 없고, 링크 하나가 rootDir 경계를 통째로 무력화한다.
function collectSymlinks(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (entry.isSymbolicLink()) {
      let pointsTo = '(해석 불가)';
      try {
        pointsTo = realpathSync(full);
      } catch {
        /* 끊어진 링크 — 가리키는 곳을 못 적어도 링크라는 사실로 이미 실패다 */
      }
      failures.push(`src 안에 심볼릭 링크가 있다: ${full} → ${pointsTo}`);
      continue;
    }
    if (entry.isDirectory()) {
      collectSymlinks(full);
    }
  }
}
collectSymlinks(srcDir);

// ⑥ 프로그램 파일 전부가 realpath 기준 src 안인가 — `files`·`include` 로 밖의 파일을 끌어온
//    경우를 ①~④ 와 무관하게 잡는다.
for (const fileName of parsed.fileNames) {
  let real;
  try {
    real = realpathSync(fileName);
  } catch {
    failures.push(`프로그램 파일을 해석하지 못했다: ${fileName}`);
    continue;
  }
  if (!real.startsWith(srcReal + sep)) {
    failures.push(`프로그램 파일이 src 밖을 가리킨다: ${fileName} → ${real}`);
  }
}

if (failures.length > 0) {
  console.error('src 트리·컴파일 경계 전제가 깨졌다 (ADR 0011 D-3):');
  for (const line of failures) {
    console.error(`  - ${line}`);
  }
  process.exit(1);
}

console.log(
  `컴파일 경계 전제 성립: rootDir=src · include=["src"] · allowJs 꺼짐 · paths 없음 · src 심볼릭 링크 0 · 프로그램 파일 ${parsed.fileNames.length} 전부 src 안.`,
);
