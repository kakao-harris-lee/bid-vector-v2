#!/usr/bin/env node
// ADR 0011 D-3 / verifier r2 M-3 · code-review r2 G-1 — **경계가 활성인지 직접 잰다.**
//
// 왜 필요한가: W-4 의 등재 단언은 설정의 **문법**을 본다. 그런데 플러그인을 비활성으로 만드는
// 형태는 끝없다(조건식 · spread 로 덮어쓰기 · 같은 키 중복 · `apply: 'serve'` · 객체 spread 로
// `buildEnd` 덮어쓰기 · 참조만 하고 호출 안 함 …). 측정으로 그 여덟 형태가 전부 W-4 를 지나는
// 것을 봤다. **「안 돌린 게이트는 아무것도 막지 못한다」**의 다른 얼굴이다 — 그래서 이 카나리는
// 설정을 읽지 않고 **빌드를 돌려** 경계가 실제로 거부하는지 본다.
//
// 어떻게: 저장소의 **실제 `vite.config.ts`** 로 Vite 의 `build()` 를 프로그램으로 부른다. 입력은
// 이 스크립트가 만든 probe 둘이고, 둘 다 **web 밖 임시 디렉터리**의 fixture 를 import 한다 —
//   ① 메인 그래프: probe 자신이 fixture 를 import 한다.
//   ② 워커 그래프: probe 가 `new Worker(new URL('./…', import.meta.url))` 로 워커를 띄우고,
//      그 워커 파일이 fixture 를 import 한다(워커는 별도 Rollup 빌드 — vr r2 H-3).
// 두 빌드가 **경계 플러그인의 오류로 실패**해야 통과다. 하나라도 성공하면 그 그래프의 경계가
// 꺼져 있다는 뜻이므로 exit 1.
//
// probe 는 `web/src` 안에 잠깐 쓰고 **반드시** 지운다(성공·실패·예외 무관). 빌드는 `write: false`
// 라 `dist/` 를 건드리지 않는다.
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { build } from 'vite';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const srcDir = resolve(webRoot, 'src');
const configFile = resolve(webRoot, 'vite.config.ts');

// 경계 플러그인이 내는 오류라는 것을 문면으로 확인한다 — 다른 이유로 실패한 빌드를 통과로 읽지
// 않는다(이름은 플러그인의 `name` 과 같은 값이다).
const BOUNDARY_PLUGIN_NAME = 'bidvector:module-graph-boundary';
const BOUNDARY_MESSAGE = '번들 모듈 그래프가';

const PROBE_MAIN = resolve(srcDir, '__boundary_canary_main.ts');
const PROBE_WORKER = resolve(srcDir, '__boundary_canary_worker.ts');

const fixtureDir = mkdtempSync(join(tmpdir(), 'bidvector-boundary-canary-'));
const fixtureFile = join(fixtureDir, 'outside.ts');

const failures = [];

function cleanup() {
  for (const path of [PROBE_MAIN, PROBE_WORKER]) {
    rmSync(path, { force: true });
  }
  rmSync(fixtureDir, { recursive: true, force: true });
}

async function expectBoundaryRejection(label) {
  let error;
  try {
    await build({
      configFile,
      root: webRoot,
      logLevel: 'silent',
      build: {
        write: false,
        rollupOptions: { input: PROBE_MAIN },
      },
    });
  } catch (caught) {
    error = caught;
  }

  if (error === undefined) {
    failures.push(
      `${label}: web 밖 파일을 끌어온 빌드가 **성공**했다 — 그 그래프의 경계가 활성이 아니다`,
    );
    return;
  }
  const text = `${error.message ?? ''} ${error.plugin ?? ''}`;
  if (!text.includes(BOUNDARY_MESSAGE) && !text.includes(BOUNDARY_PLUGIN_NAME)) {
    failures.push(
      `${label}: 빌드는 실패했지만 경계 플러그인의 오류가 아니다 — 다른 이유로 실패한 빌드를 통과로 읽지 않는다: ${String(error.message ?? error).slice(0, 200)}`,
    );
  }
}

try {
  writeFileSync(fixtureFile, "export const OUTSIDE = 'BOUNDARY_CANARY_OUTSIDE';\n");

  // ① 메인 그래프
  writeFileSync(
    PROBE_MAIN,
    `import { OUTSIDE } from ${JSON.stringify(fixtureFile)};\nexport const probe = OUTSIDE;\n`,
  );
  await expectBoundaryRejection('메인 그래프');

  // ② 워커 그래프 — probe 자신은 src 안만 import 하고, 워커 파일이 밖을 끌어온다.
  writeFileSync(
    PROBE_WORKER,
    `import { OUTSIDE } from ${JSON.stringify(fixtureFile)};\nself.postMessage(OUTSIDE);\n`,
  );
  writeFileSync(
    PROBE_MAIN,
    "export const probe = new Worker(new URL('./__boundary_canary_worker.ts', import.meta.url), { type: 'module' });\n",
  );
  await expectBoundaryRejection('워커 그래프');
} finally {
  cleanup();
}

if (failures.length > 0) {
  console.error('번들 그래프 경계가 활성이 아니다 (ADR 0011 D-3):');
  for (const line of failures) {
    console.error(`  - ${line}`);
  }
  console.error('  고치는 법: vite.config.ts 의 plugins·worker.plugins 등재를 되살린다.');
  process.exitCode = 1;
} else {
  console.log(
    '번들 그래프 경계가 활성이다: 메인·워커 두 그래프에서 web 밖 파일을 끌어온 빌드가 경계 오류로 거부됐다.',
  );
}
