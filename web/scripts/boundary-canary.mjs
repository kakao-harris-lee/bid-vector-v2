#!/usr/bin/env node
// ADR 0011 D-3 / verifier r2 M-3 · code-review r2 G-1 — **경계가 활성인지 직접 잰다.**
//
// 왜 필요한가: W-4 의 등재 단언은 설정의 **문법**을 본다. 그런데 플러그인을 비활성으로 만드는
// 형태는 끝없다(조건식 · spread 로 덮어쓰기 · 같은 키 중복 · `apply: 'serve'` · 객체 spread 로
// `buildEnd` 덮어쓰기 · 참조만 하고 호출 안 함 · **본문 비우기**). 측정으로 그 형태들이 W-4 를
// 지나면서 경계를 끄는 것을 봤다. **「안 돌린 게이트는 아무것도 막지 못한다」**의 다른 얼굴이다 —
// 그래서 이 카나리는 설정을 읽지 않고 **빌드를 돌려** 경계가 실제로 거부하는지 본다.
//
// 어떻게: 저장소의 **실제 `vite.config.ts`** 로 Vite 의 `build()` 를 프로그램으로 부른다. 입력은
// 이 스크립트가 만든 probe 둘이고, 둘 다 **web 밖 임시 디렉터리**의 fixture 를 import 한다 —
//   ① 메인 그래프: probe 자신이 fixture 를 import 한다.
//   ② 워커 그래프: probe 가 `new Worker(new URL('./…', import.meta.url))` 로 워커를 띄우고,
//      그 워커 파일이 fixture 를 import 한다(워커는 별도 Rollup 빌드 — vr r2 H-3).
//
// **통과 조건 셋**(code-review r3 H-4 · verifier r3 M-6 ③): 빌드가 실패하고 · 그 오류가 경계
// 플러그인에서 왔고(이름은 Vite 의 래퍼 플러그인에 감싸여 올라오므로 `error.plugin` 이 아니라
// **문면**에서 찾는다, 실측) · **그 오류가 바로 이 실행의 fixture 실경로를 적는다**.
//
// 셋째 조건이 주장하는 것은 거기까지다 — 「경계가 **다른 사유로** 낸 오류를 통과로 읽지 않는다」와
// 「**탐침 id 에만 반응하는** 가짜(경로를 적지 않는 가짜)를 가른다」. 탐침 경로를 그대로 적어
// 주는 가짜는 가르지 못한다(verifier r4 ③' 실측) — 그 부류는 아래 「남는 자리」다.
//
// 그리고 **probe 이름·자리와 fixture 이름·자리는 실행마다 무작위**다(M-6 ③) — 탐침을 알아보고
// 특례로 처리하는 설정을 어렵게 한다. 빌드는 `write: false` 대신 **임시 `outDir`** 에 쓴다:
// 「쓰기 여부」로 카나리와 실빌드를 가르던 축(M-6 ①)을 없애고, 저장소의 `dist/` 는 건드리지 않는다.
//
// 남는 자리: **카나리를 알아채는 설정 일반**은 이 층으로 닫히지 않는다 — 자기 검사는 언제나
// 특례로 갈 수 있다. 실측된 형태 넷: `process.argv` 분기 · 빌드 옵션 모양으로 가르기
// (`rollupOptions.input` 이 문자열인지 · `outDir` 위치) · 탐침 이름 접두(`__boundary_canary_` ·
// `outside_`)에 반응 · 같은 이름의 가짜 플러그인이 그 경로를 그대로 적어 `this.error`. 무작위
// nonce 와 임시 `outDir` 은 그 폭을 줄이는 조치일 뿐 닫는 것이 아니다. 그 부류는 리뷰 소관이고
// checklist 의 알려진 제한에 등재돼 있다.
//
// probe 는 `finally` 에서 **반드시** 지운다(성공·실패·예외 무관). 강제 종료로 남으면 typecheck·
// W-4·clean-tree 가 받아낸다(fail-closed).
import { randomBytes } from 'node:crypto';
import { mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { build } from 'vite';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const srcDir = resolve(webRoot, 'src');
const configFile = resolve(webRoot, 'vite.config.ts');

const BOUNDARY_PLUGIN_NAME = 'bidvector:module-graph-boundary';

const nonce = randomBytes(5).toString('hex');
const probeDir = resolve(srcDir, `__boundary_canary_${nonce}`);
const probeMain = join(probeDir, `main_${nonce}.ts`);
const probeWorker = join(probeDir, `worker_${nonce}.ts`);

const fixtureDir = mkdtempSync(join(tmpdir(), 'bidvector-boundary-canary-'));
const fixtureFile = join(fixtureDir, `outside_${nonce}.ts`);
const fixtureReal = join(realpathSync(fixtureDir), `outside_${nonce}.ts`);
const outDir = mkdtempSync(join(tmpdir(), 'bidvector-boundary-canary-out-'));

const failures = [];

function cleanup() {
  rmSync(probeDir, { recursive: true, force: true });
  rmSync(fixtureDir, { recursive: true, force: true });
  rmSync(outDir, { recursive: true, force: true });
}

async function expectBoundaryRejection(label) {
  let error;
  try {
    await build({
      configFile,
      root: webRoot,
      logLevel: 'silent',
      build: {
        outDir,
        emptyOutDir: true,
        rollupOptions: { input: probeMain },
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

  const message = String(error.message ?? error);
  if (!message.includes(BOUNDARY_PLUGIN_NAME)) {
    failures.push(
      `${label}: 빌드는 실패했지만 경계 플러그인의 오류가 아니다 — 다른 이유로 실패한 빌드를 통과로 읽지 않는다: ${message.slice(0, 200)}`,
    );
    return;
  }
  if (!message.includes(fixtureReal) && !message.includes(fixtureFile)) {
    failures.push(
      `${label}: 경계 플러그인이 멈추기는 했지만 **이 실행의 fixture 를 거부한 것이 아니다**(오류에 그 실경로가 없다) — 같은 이름·같은 문면을 내는 다른 플러그인일 수 있다: ${message.slice(0, 200)}`,
    );
  }
}

try {
  mkdirSync(probeDir, { recursive: true });
  writeFileSync(fixtureFile, `export const OUTSIDE = 'BOUNDARY_CANARY_${nonce}';\n`);

  // ① 메인 그래프
  writeFileSync(
    probeMain,
    `import { OUTSIDE } from ${JSON.stringify(fixtureFile)};\nexport const probe = OUTSIDE;\n`,
  );
  await expectBoundaryRejection('메인 그래프');

  // ② 워커 그래프 — probe 자신은 src 안만 import 하고, 워커 파일이 밖을 끌어온다.
  writeFileSync(
    probeWorker,
    `import { OUTSIDE } from ${JSON.stringify(fixtureFile)};\nself.postMessage(OUTSIDE);\n`,
  );
  writeFileSync(
    probeMain,
    `export const probe = new Worker(new URL('./worker_${nonce}.ts', import.meta.url), { type: 'module' });\n`,
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
  console.error(
    '  고치는 법: vite.config.ts 의 plugins·worker.plugins 등재와 플러그인 본문을 되살린다.',
  );
  process.exitCode = 1;
} else {
  console.log(
    '번들 그래프 경계가 활성이다: 메인·워커 두 그래프에서 **이 실행의** fixture 실경로를 경계 플러그인이 거부했다.',
  );
}
