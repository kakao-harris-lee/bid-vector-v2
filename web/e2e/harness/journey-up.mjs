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
  mkdtempSync,
  readdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { CONTAINER_PREFIX, reapStaleContainer } from './container-marker.mjs';

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

async function acquireSingleRunLock() {
  if (await previewPortIsBusy()) {
    throw new Error(
      `preview 포트 ${PREVIEW_PORT} 에 이미 무언가 떠 있다 — 같은 호스트의 앞 실행이 살아 있을 수 있다. ` +
        '그 실행이 끝난 뒤 다시 돌리십시오(남의 컨테이너·파일을 건드리지 않는다).',
    );
  }
  if (existsSync(LOCK_PATH)) {
    const stale = readFileSync(LOCK_PATH, 'utf8').trim();
    log(`묵은 락을 잇는다(앞 실행 표지 ${stale || '없음'})`);
    const reaped = reapStaleContainer(stale, { log });
    log(`묵은 컨테이너 ${reaped.length}개를 걷었다`);
  }
  // **원자 쓰기**(CR r2 H-1 ⓐ) — 같은 파일에 바로 쓰면 「파일은 생겼고 내용은 아직 없는」 창이
  // 생기고, 그 창에서 죽으면 다음 실행이 **빈 표지**를 읽는다. 임시 파일에 다 쓴 뒤 rename 한다.
  const pending = `${LOCK_PATH}.${process.pid}.tmp`;
  writeFileSync(pending, `${CONTAINER}\n`, { mode: 0o600 });
  chmodSync(pending, 0o600);
  renameSync(pending, LOCK_PATH);
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

/** 출하 jar 한 번 돌리고 **끝까지 기다린다** — 일회 러너는 스스로 JVM 을 끝낸다. */
function runJar(args, label) {
  log(`${label} 시작`);
  const result = spawnSync('java', ['-jar', APP_JAR, ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  if (result.status !== 0) {
    process.stderr.write(result.stdout?.toString() ?? '');
    process.stderr.write(result.stderr?.toString() ?? '');
    throw new Error(`${label} 실패(exit=${result.status})`);
  }
  log(`${label} 끝(exit=0)`);
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
  await acquireSingleRunLock();
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

  // 이 빌드가 낸 자산 이름을 맥락에 적는다(v r3 M-2) — 여정은 「같은 출처의 비 API 요청」을
  // **이 목록과 등식으로** 본다. 확장자 패턴으로 보면 번들이 자산으로 실어 준 데이터 파일이
  // 같은 자리에 떨어져도 초록이다. 목록은 **이 실행의 산출물**이어야 하므로 여기서 읽는다.
  const bundleAssets = readdirSync(resolve(webRoot, 'dist', 'assets')).map((one) => `/assets/${one}`);
  if (bundleAssets.length === 0) {
    throw new Error('번들 자산이 0 이다 — 여정의 자산 등식이 공허해진다');
  }

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
