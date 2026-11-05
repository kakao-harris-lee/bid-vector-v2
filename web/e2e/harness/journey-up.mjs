// 여정 e2e 하네스(D-7E-1) — **출하 app.jar · 버릴 Postgres · loopback fake KONEPS · vite
// preview** 를 띄운다. API 목이 0 이고(완료 조건 「V2 API 만」) 공고는 **수집 경로로** 들어온다:
// 수집 → 저장 → API → 화면이 한 사슬이어야 하므로 DB 에 직접 넣지 않는다.
//
// Playwright 의 `webServer.command` 가 이 스크립트 하나다 — 순서(백엔드 먼저, preview 나중)를
// 한 프로세스 트리에 두면 `globalSetup` 과 `webServer` 의 기동 순서에 기대지 않는다.
//
// 출하 명령만 쓴다(D-7E-8): `bidvector.collection.mode=once` · `bidvector.account.mode=CREATE` ·
// 전략 여력 상한은 출하 편집 세션 API(자격 헤더). **화면이 부르는 operation 은 건드리지 않는다.**
//
// 비밀값은 **실행마다 난수**다 — 저장소에 자격 리터럴을 두지 않는다(D-7E-6).
import { spawn, spawnSync } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { assertDistRoot, bundleAssetPaths } from './bundle-shape.mjs';
import {
  acquireSingleRunLock,
  CONTAINER_PREFIX,
  processStartToken,
} from './container-marker.mjs';
import {
  assertRunnerCounts,
  openingAxisDesign,
  promotionExpectation,
  runnerLine,
} from './opening-axis.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..', '..');
const repoRoot = resolve(webRoot, '..');

/** `docker/compose.yaml` 과 **같은 digest** — 두 자리가 다른 postgres 로 갈라지지 않는다. */
const POSTGRES_IMAGE =
  'postgres:16.4@sha256:e62fbf9d3e2b49816a32c400ed2dba83e3b361e6833e624024309c35d334b412';

const APP_JAR = process.env.BIDVECTOR_APP_JAR ?? resolve(repoRoot, 'app', 'build', 'libs', 'app.jar');
const PREVIEW_PORT = Number.parseInt(process.env.BIDVECTOR_E2E_PREVIEW_PORT ?? '4174', 10);
/**
 * 여정 spec 에 넘길 값(preview 주소·로그인 자격) — **Playwright 산출물 디렉터리 밖**이다.
 * `web/test-results/` 는 실패 시 CI 아티팩트로 올라가므로 거기에 자격 값을 두면 일회용이라도
 * 산출물에 실린다(D-7E-6). 이름은 고정이고(한 번에 한 실행) 권한은 0600 이며 종료 때 지운다.
 */
const CONTEXT_PATH = resolve(tmpdir(), 'bidvector-7e1-journey-context.json');

/** compose 는 프로젝트 이름이 고정이라 같은 호스트의 다른 레인과 이름공간을 공유한다 — `docker run` + 유일 이름. */
const CONTAINER = `${CONTAINER_PREFIX}${randomUUID().slice(0, 8)}`;

/**
 * **한 호스트에 한 실행**(v L-6 · CR L-3). 이 레인은 호스트를 다섯 레인과 공유하고, preview
 * 포트는 Playwright 설정이 미리 알아야 해서 고정이다. 겹치면 ① 뒤 실행이 앞 실행의 postgres 를
 * 지우고 ② 맥락 파일을 덮어쓰고 ③ `strictPort` 로 preview 가 못 뜬다.
 *
 * 그래서 **접두 전체를 걷는 수거를 버리고** 포트에 매인 락 하나로 겹침을 막는다 — 앞 실행이
 * 살아 있으면 **빠르게, 사유를 적고** 멈춘다(남의 컨테이너를 지우지 않는다). 락이 묵은
 * 것이면(그 포트에 아무도 없다) 그 표지의 컨테이너만 걷고 잇는다.
 */
const LOCK_PATH = resolve(tmpdir(), `bidvector-7e1-journey-${PREVIEW_PORT}.lock`);

async function previewPortIsBusy() {
  try {
    const probe = await fetch(`http://127.0.0.1:${PREVIEW_PORT}/`, { signal: AbortSignal.timeout(1500) });
    return probe.ok || probe.status > 0;
  } catch {
    return false;
  }
}

/** 락을 **얻었는가** — 공유 경로를 정리할 자격이 이것 하나다(CR r2 M-5). */
let lockOwned = false;

async function acquireRunLock() {
  if (await previewPortIsBusy()) {
    throw new Error(
      `preview 포트 ${PREVIEW_PORT} 에 이미 무언가 떠 있다 — 같은 호스트의 앞 실행이 살아 있을 수 있다. ` +
        '그 실행이 끝난 뒤 다시 돌리십시오(남의 컨테이너·파일을 건드리지 않는다).',
    );
  }
  // 획득 자체는 `container-marker.mjs` 가 든다 — 원자적 배타이고 주입 test 가 그 배선을
  // 잠근다(CR r4 M-3 · v r4 L-1·L-2). 이 자리는 **포트 선검사와 소유 표시**만 한다.
  acquireSingleRunLock({
    lockPath: LOCK_PATH,
    pid: process.pid,
    startToken: processStartToken(process.pid),
    container: CONTAINER,
    log,
  });
  lockOwned = true;
}


const secret = () => randomBytes(24).toString('base64url');
const PG_USER = 'bidvector_admin';
const PG_PASSWORD = secret();
const PG_DB = 'bidvector_journey';
const OPERATOR_CREDENTIAL = secret();
const SERVICE_KEY = secret();
const LOGIN_NAME = 'journey-operator';
const PASSPHRASE = secret();

/** 전략 여력 상한 — 이 값이 없으면 투찰가 요청이 409 다(Phase 1 실측, D-7E-8). */
const MAX_ACTIVE_BIDS = 5;

/**
 * 개찰 축 설계(M9/opening-harness) — 어느 공고가 어느 갈래를 낳는지가 이 표에 있고, 아래 수집·
 * 승격 걸음의 **계수 기대**가 거기서 나온다. 여정 spec 은 이 표를 맥락 파일로 받아 **살아 있는
 * 응답**과 맞댄다.
 */
const OPENING_DESIGN = openingAxisDesign(
  JSON.parse(readFileSync(resolve(here, 'fixtures', 'opening-axis.json'), 'utf8')),
);
const OPENING_EXPECTATION = promotionExpectation(OPENING_DESIGN);

/**
 * 개찰 축 호출 상한 — 설계의 공고 수에서 넉넉히 센다(표본 × 상세 축 넷 + 목록 걷기). 승인 상한을
 * 흉내내는 자리가 아니라 **버릴 fake 를 향한 호출의 울타리**다: 상한을 넘으면 러너가 미완으로
 * 끝나고 아래 계수 등식이 그 사실을 이름 붙여 낸다.
 */
const OPENING_CALL_BUDGET = 400;

const children = [];
let runStateDir = null;
let shuttingDown = false;

function log(line) {
  process.stdout.write(`journey-up: ${line}\n`);
}

async function freePort() {
  return new Promise((done, fail) => {
    const probe = createServer();
    probe.on('error', fail);
    probe.listen(0, '127.0.0.1', () => {
      const { port } = probe.address();
      probe.close(() => done(port));
    });
  });
}

function track(child, name) {
  children.push({ child, name });
  child.on('exit', (code, signal) => {
    if (!shuttingDown && code !== 0) {
      log(`${name} 가 먼저 죽었다(code=${code} signal=${signal}) — 하네스를 끝낸다`);
      void shutdown(1);
    }
  });
  return child;
}

/**
 * 출하 jar 한 번 돌리고 **끝까지 기다린다** — 일회 러너는 스스로 JVM 을 끝낸다.
 *
 * 출력을 **돌려준다**(M9/opening-harness): 러너의 요약 줄이 「무엇을 실제로 했는가」의 유일한
 * 공시 자리이고, `exit=0` 은 「0건 수집」과 구별되지 않는다. 그 줄의 계수를 설계와 맞대는 것이
 * 이 하네스가 조용히 비는 것을 막는 자리다.
 */
function runJar(args, label) {
  log(`${label} 시작`);
  const result = spawnSync('java', ['-jar', APP_JAR, ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  const output = `${result.stdout?.toString() ?? ''}${result.stderr?.toString() ?? ''}`;
  if (result.status !== 0) {
    process.stderr.write(output);
    throw new Error(`${label} 실패(exit=${result.status})`);
  }
  log(`${label} 끝(exit=0)`);
  return output;
}

async function waitFor(label, probe, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    if (await probe()) {
      return;
    }
    if (Date.now() > deadline) {
      throw new Error(`${label} 를 ${timeoutMs}ms 안에 기다리지 못했다`);
    }
    await new Promise((done) => setTimeout(done, 400));
  }
}

async function shutdown(code) {
  if (shuttingDown) {
    return;
  }
  shuttingDown = true;
  for (const { child, name } of children) {
    if (child.exitCode === null && child.signalCode === null) {
      log(`${name} 종료`);
      child.kill('SIGTERM');
    }
  }
  spawnSync('docker', ['rm', '-f', CONTAINER], { stdio: 'ignore' });
  if (runStateDir !== null && existsSync(runStateDir)) {
    rmSync(runStateDir, { recursive: true, force: true });
  }
  // CR r2 M-5 — 공유 경로는 **락을 얻은 실행만** 정리한다. 앞 판은 락을 얻지 못해 죽는
  // 경로에서도 이 둘을 지웠고, 그러면 겹침에서 **옳게 멈추는 쪽이 앞 실행의 맥락 파일과 락을
  // 지웠다**(앞 실행은 ENOENT 로 전건 실패하고, 그 표지가 사라져 컨테이너를 걷을 근거도 없다).
  if (lockOwned) {
    rmSync(CONTEXT_PATH, { force: true });
    rmSync(LOCK_PATH, { force: true });
  }
  process.exit(code);
}

async function main() {
  await acquireRunLock();
  if (!existsSync(APP_JAR)) {
    throw new Error(`출하 jar 가 없다: ${APP_JAR} — 먼저 \`./gradlew :app:bootJar\` 를 돌리십시오`);
  }
  // 실행 상태는 **저장소 밖**이어야 기동한다(`requireOutsideRepository`).
  runStateDir = mkdtempSync(resolve(tmpdir(), 'bidvector-7e1-journey-'));

  const [pgPort, konepsPort, appPort, mgmtPort] = await Promise.all([
    freePort(),
    freePort(),
    freePort(),
    freePort(),
  ]);

  log(`버릴 Postgres(${CONTAINER})`);
  const run = spawnSync(
    'docker',
    [
      'run', '--rm', '-d', '--name', CONTAINER,
      '-e', `POSTGRES_USER=${PG_USER}`,
      '-e', `POSTGRES_PASSWORD=${PG_PASSWORD}`,
      '-e', `POSTGRES_DB=${PG_DB}`,
      '-p', `127.0.0.1:${pgPort}:5432`,
      POSTGRES_IMAGE,
    ],
    { stdio: ['ignore', 'ignore', 'inherit'] },
  );
  if (run.status !== 0) {
    throw new Error('postgres 기동 실패');
  }
  await waitFor(
    'postgres',
    () =>
      spawnSync('docker', ['exec', CONTAINER, 'pg_isready', '-U', PG_USER, '-d', PG_DB], {
        stdio: 'ignore',
      }).status === 0,
    90_000,
  );

  log(`fake KONEPS(127.0.0.1:${konepsPort})`);
  track(
    spawn(process.execPath, [resolve(here, 'fake-koneps.mjs'), String(konepsPort)], {
      stdio: ['ignore', 'inherit', 'inherit'],
    }),
    'fake-koneps',
  );
  await waitFor(
    'fake KONEPS',
    async () => {
      try {
        const probe = await fetch(`http://127.0.0.1:${konepsPort}/getBidPblancListInfoCnstwk`);
        return probe.ok;
      } catch {
        return false;
      }
    },
    30_000,
  );

  /**
   * CR L-4 — 비밀값을 **argv 에 싣지 않는다**. 명령줄 인자는 호스트의 아무 프로세스나 `ps` 로
   * 읽고, app 의 stdout 은 하네스 → Playwright webServer 로그 → 실패 시 CI 로그로 흐른다.
   * Spring 의 설정 가져오기로 넘기고 파일은 0600 · 저장소 밖 · 종료 때 지운다.
   */
  const COMMON_PROPERTIES = [
    `bidvector.persistence.jdbc-url=jdbc:postgresql://127.0.0.1:${pgPort}/${PG_DB}`,
    `bidvector.persistence.username=${PG_USER}`,
    `bidvector.persistence.credential=${PG_PASSWORD}`,
    `operator.credential.value=${OPERATOR_CREDENTIAL}`,
    'bidvector.evaluation.candidate-cap=1000',
  ];

  /**
   * 호출 하나가 쓸 설정을 **파일 하나**에 담는다. `--spring.config.import` 를 두 번 실으면
   * Spring 이 두 값을 쉼표로 이어 한 목록으로 보는 성질에 기대게 된다 — 그 성질에 기대지 않고
   * 호출마다 하나로 둔다. 파일은 0600 · 저장소 밖(run-state) · 종료 때 디렉터리째 지워진다.
   */
  const writeSecrets = (name, extra) => {
    const path = resolve(runStateDir, `${name}.properties`);
    writeFileSync(path, `${[...COMMON_PROPERTIES, ...extra].join('\n')}\n`, { mode: 0o600 });
    chmodSync(path, 0o600);
    return `--spring.config.import=file:${path}`;
  };
  const today = new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);

  // ① 수집 — 출하 일회 러너. 공고가 **이 경로로만** DB 에 들어온다.
  runJar(
    [
      writeSecrets('collect', [`bidvector.koneps.service-key=${SERVICE_KEY}`]),
      '--server.port=0',
      '--bidvector.collection.mode=once',
      `--bidvector.collection.from=${today}`,
      `--bidvector.collection.to=${today}`,
      '--bidvector.collection.categories=construction',
      '--bidvector.collection.calls-per-day=100',
      '--bidvector.collection.calls-total=100',
      `--bidvector.collection.run-state-dir=${runStateDir}`,
      `--bidvector.koneps.base-url=http://127.0.0.1:${konepsPort}`,
    ],
    '수집 러너(bidvector.collection.mode=once)',
  );

  // ①b 개찰 축 수집 — **업무마다 다른 실행 상태 디렉터리**다. 승격은 아래에서 공사 쪽 하나만
  //     받으므로 용역 공고는 목록 축 원문을 얻고도 canonical 이 서지 않는다(`OBSERVED_NOT_PROMOTED`).
  //     표본 크기로 그 갈래를 만들지 않는 이유는 **어느 공고가 빠지는지를 표본 seed 가 정해**
  //     설계가 선언할 수 없기 때문이다(설계표 `why_two_divisions`).
  //
  //     base 가 **둘**이다: 개찰결과 목록·예비가격 상세·개찰완료는 낙찰정보서비스이고 A값·기초금액은
  //     입찰공고정보서비스다. fake 는 경로만 보고 가르므로 두 base 가 같은 포트를 가리킨다.
  const openingRunStateDirs = {};
  for (const [division, counts] of Object.entries(OPENING_EXPECTATION.divisions)) {
    // `RunStateDirectory` 는 **디렉터리를 만들지 않는다**(경로 오타 하나로 빈 디렉터리가 생기면
    // 승인 상한이 0 에서 시작한다) — 하네스가 미리 만든다.
    const dir = resolve(runStateDir, `opening-${division}`);
    mkdirSync(dir);
    openingRunStateDirs[division] = dir;

    const output = runJar(
      [
        writeSecrets(`opening-${division}`, [`bidvector.koneps.service-key=${SERVICE_KEY}`]),
        '--server.port=0',
        '--bidvector.opening-collection.mode=once',
        `--bidvector.opening-collection.from=${today}`,
        `--bidvector.opening-collection.to=${today}`,
        `--bidvector.opening-collection.categories=${division}`,
        `--bidvector.opening-collection.sampling-seed=m9-opening-harness-${division}`,
        `--bidvector.opening-collection.sample-size=${counts.sampled}`,
        `--bidvector.opening-collection.calls-per-day=${OPENING_CALL_BUDGET}`,
        `--bidvector.opening-collection.calls-total=${OPENING_CALL_BUDGET}`,
        `--bidvector.opening-collection.run-state-dir=${dir}`,
        `--bidvector.koneps.base-url=http://127.0.0.1:${konepsPort}`,
        `--bidvector.koneps.opening.scsbid-base-url=http://127.0.0.1:${konepsPort}`,
      ],
      `개찰 축 수집(${division}, bidvector.opening-collection.mode=once)`,
    );
    // **표본틀이 설계의 공고 수와 같은가** — fixture 의 공고 이름이 출하 기본 오퍼레이션 표와
    // 갈리면 fake 가 404 를 내고 이 수가 0 이 된다(그때 러너는 조용히 끝난다).
    assertRunnerCounts(
      runnerLine(output, 'opening-collection finished', `개찰 축 수집(${division})`),
      counts,
      `개찰 축 수집(${division})`,
    );
  }

  // ①c 개찰 결과 승격 — **공사 쪽 디렉터리 하나만** 받는다(외부 호출 0 · 원문 쓰기 0).
  //     계수가 닫힌 등식이다: 읽은 묶음 == 승격 + 범위 밖.
  const promotion = runJar(
    [
      writeSecrets('promote', []),
      '--server.port=0',
      '--bidvector.opening-promote.mode=once',
      `--bidvector.opening-promote.run-state-dir=${openingRunStateDirs.construction}`,
      `--bidvector.opening-promote.reference-date=${today}`,
    ],
    '개찰 결과 승격(bidvector.opening-promote.mode=once)',
  );
  assertRunnerCounts(
    runnerLine(promotion, 'opening-promote finished', '개찰 결과 승격'),
    OPENING_EXPECTATION.promotion,
    '개찰 결과 승격',
  );

  // ② 로그인 계정 — 출하 운영자 명령(가입 화면·endpoint 는 없다).
  runJar(
    [
      writeSecrets('account', [`bidvector.account.initial-value=${PASSPHRASE}`]),
      '--server.port=0',
      '--bidvector.account.mode=CREATE',
      `--bidvector.account.login=${LOGIN_NAME}`,
    ],
    '계정 명령(bidvector.account.mode=CREATE)',
  );

  // ③ API 서버 — http loopback 이므로 쿠키 Secure 를 내린다(운영 TLS 는 7F).
  log(`API 서버(127.0.0.1:${appPort})`);
  track(
    spawn(
      'java',
      [
        '-jar', APP_JAR,
        writeSecrets('server', []),
        `--server.port=${appPort}`,
        `--management.server.port=${mgmtPort}`,
        '--bidvector.access.cookie-secure=false',
      ],
      { stdio: ['ignore', 'inherit', 'inherit'] },
    ),
    'app',
  );
  await waitFor(
    'app readiness',
    async () => {
      try {
        const probe = await fetch(`http://127.0.0.1:${mgmtPort}/actuator/health/readiness`);
        return probe.ok;
      } catch {
        return false;
      }
    },
    120_000,
  );

  await seedMaxActiveBids(appPort);

  // ④ 화면 — **빌드된 번들**을 preview 로 띄우고 `/api` 를 app 으로 프록시한다(같은 출처라
  //    세션 쿠키·CSRF 가 운영 형태와 같은 모양으로 돈다).
  //
  //    **빌드를 하네스가 돌린다.** `vite preview` 는 `dist/` 를 섬길 뿐이므로, 하네스가 짓지
  //    않으면 **지난 번들을 재는 수가 있다** — 화면 코드를 바꿔치운 변이가 e2e 에서 초록으로
  //    지나갔다(M1 실측). CI 는 build(W-8)가 앞에 있어 가렸지만, 그 순서에 기대는 것과
  //    「섬기는 번들이 지금 소스에서 나온 것」을 보장하는 것은 다르다. 비용은 실측 2s 다.
  log('번들 빌드(섬기는 것이 지금 소스에서 나오게 한다)');
  const built = spawnSync(resolve(webRoot, 'node_modules', '.bin', 'vite'), ['build'], {
    cwd: webRoot,
    stdio: ['ignore', 'inherit', 'inherit'],
  });
  if (built.status !== 0) {
    throw new Error(`번들 빌드 실패(exit=${built.status})`);
  }

  // 이 빌드가 낸 자산을 맥락에 적는다(v r3 M-2) — 여정은 「같은 출처의 비 API 요청」을 이
  // 목록과 등식으로 본다.
  //
  // **목록을 산출물에서 그냥 읽으면 방어가 아니다**(v r4 M-2 · CR r4 L-5): 자산이 늘면 허용
  // 집합도 함께 늘어 아무것도 깨지지 않는다. 그래서 **모양을 못박는다** — 이 화면 다발은
  // 진입 js 하나와 그것이 가져온 css 하나를 낸다. 번들에 자산이 하나라도 더 들어오면 **여기서
  // 먼저 깨진다**(그 주장이 사실이 된다). 상한을 올릴 일이 생기면 그것은 설계 변경이고, 이
  // 줄을 고치는 diff 로 드러나야 한다.
  const distDir = resolve(webRoot, 'dist');
  const assetDir = resolve(distDir, 'assets');
  if (!existsSync(assetDir)) {
    throw new Error(`번들 자산 디렉터리가 없다: ${assetDir} — 빌드가 무엇을 냈는지 알 수 없다`);
  }
  // **루트까지** 모양을 센다(OPEN ② 의 둘째 층) — `publicDir` 가 켜지면 복사물이 여기에 뜬다.
  assertDistRoot(readdirSync(distDir, { withFileTypes: true }));
  const bundleAssets = bundleAssetPaths(readdirSync(assetDir, { withFileTypes: true }));

  log(`vite preview(127.0.0.1:${PREVIEW_PORT} → /api ⇒ 127.0.0.1:${appPort})`);
  track(
    spawn(resolve(webRoot, 'node_modules', '.bin', 'vite'), ['preview'], {
      cwd: webRoot,
      stdio: ['ignore', 'inherit', 'inherit'],
      env: {
        ...process.env,
        BIDVECTOR_E2E_PREVIEW_PORT: String(PREVIEW_PORT),
        BIDVECTOR_E2E_API_ORIGIN: `http://127.0.0.1:${appPort}`,
      },
    }),
    'vite preview',
  );

  // CR L-3 — `writeFileSync` 의 `mode` 는 **이미 있는 파일의 권한을 바꾸지 않는다**(SIGKILL 로
  // 남은 0644 파일에 새 비밀 문구가 실린다). 먼저 지우고 쓰고, 쓴 뒤 권한을 못박는다.
  rmSync(CONTEXT_PATH, { force: true });
  writeFileSync(
    CONTEXT_PATH,
    `${JSON.stringify(
      {
        previewUrl: `http://127.0.0.1:${PREVIEW_PORT}`,
        loginName: LOGIN_NAME,
        passphrase: PASSPHRASE,
        maxActiveBids: MAX_ACTIVE_BIDS,
        bundleAssets,
        /**
         * 개찰 축 설계표(M9/opening-harness) — 여정 spec 이 **살아 있는 응답**과 맞댈 기대다.
         * spec 에 베껴 적지 않는 이유: 그러면 설계와 기대가 사본 둘이 되고 한쪽만 고쳐진다.
         */
        openingAxis: OPENING_DESIGN.all.map(({ noticeNumber, expectedOpeningState, why }) => ({
          noticeNumber,
          expectedOpeningState,
          why,
        })),
      },
      null,
      2,
    )}\n`,
    { mode: 0o600 },
  );
  chmodSync(CONTEXT_PATH, 0o600);
  log('준비 끝 — 여정 test 를 받는다');
}

/**
 * 전략 여력 상한을 **하네스가** 심는다(D-7E-8) — 투찰가 요청은 이 값이 없으면 409
 * `MAX_ACTIVE_BIDS_NOT_CONFIGURED` 다(fail-closed, 계약 선언). 그 값을 세우는 편집 세션
 * operation 은 화면 B·C 의 목록 밖(설정 화면 = 7E-2)이므로 **운영자 설정 행위를 하네스가
 * 대신한다** — 자격 헤더를 쓰고 브라우저 세션을 쓰지 않는다(화면 경로와 섞지 않는다).
 */
async function seedMaxActiveBids(appPort) {
  const origin = `http://127.0.0.1:${appPort}`;
  const headers = { 'content-type': 'application/json', 'X-Operator-Credential': OPERATOR_CREDENTIAL };
  const post = async (path, body) => {
    const response = await fetch(`${origin}${path}`, { method: 'POST', headers, body: JSON.stringify(body) });
    if (!response.ok) {
      throw new Error(`전략 심기 실패: POST ${path} → ${response.status}`);
    }
    return response.json();
  };

  const begun = await post('/api/strategy/edit-sessions', { field: 'MAX_ACTIVE_BIDS' });
  await post(`/api/strategy/edit-sessions/${begun.sessionId}/value`, {
    commandId: randomUUID(),
    field: 'MAX_ACTIVE_BIDS',
    count: MAX_ACTIVE_BIDS,
  });
  const applied = await post(`/api/strategy/edit-sessions/${begun.sessionId}/confirm`, {
    commandId: randomUUID(),
    seenRevision: 0,
  });
  log(`전략 여력 상한 ${MAX_ACTIVE_BIDS} 적용(개정 ${applied.strategyRevision ?? '?'})`);
}

process.on('SIGTERM', () => void shutdown(0));
process.on('SIGINT', () => void shutdown(0));
/** 정상 종료·미처리 예외에서도 컨테이너를 거둔다 — `exit` 핸들러는 동기 호출만 쓸 수 있다. */
process.on('exit', () => {
  spawnSync('docker', ['rm', '-f', CONTAINER], { stdio: 'ignore' });
});

main().catch(async (failure) => {
  process.stderr.write(`journey-up: ${failure.message}\n`);
  await shutdown(1);
});
