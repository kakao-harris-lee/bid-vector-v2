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
import { mkdtempSync, writeFileSync, rmSync, existsSync } from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

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
const CONTAINER_PREFIX = 'bidvector-7e1-journey-';
const CONTAINER = `${CONTAINER_PREFIX}${randomUUID().slice(0, 8)}`;

/**
 * 앞 실행이 비정상으로 끊겼으면 같은 접두의 컨테이너가 남는다(SIGKILL 은 정리 핸들러를 건너
 * 뛴다). **시작 때 걷는다** — 누출을 한 실행으로 묶고, 다음 실행이 반드시 치운다.
 */
function removeStaleContainers() {
  const listed = spawnSync('docker', ['ps', '-aq', '--filter', `name=${CONTAINER_PREFIX}`], {
    encoding: 'utf8',
  });
  const ids = (listed.stdout ?? '').split('\n').map((one) => one.trim()).filter((one) => one !== '');
  if (ids.length > 0) {
    log(`묵은 컨테이너 ${ids.length}개를 걷는다`);
    spawnSync('docker', ['rm', '-f', ...ids], { stdio: 'ignore' });
  }
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
  rmSync(CONTEXT_PATH, { force: true });
  process.exit(code);
}

async function main() {
  removeStaleContainers();
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

  const common = [
    `--bidvector.persistence.jdbc-url=jdbc:postgresql://127.0.0.1:${pgPort}/${PG_DB}`,
    `--bidvector.persistence.username=${PG_USER}`,
    `--bidvector.persistence.credential=${PG_PASSWORD}`,
    `--operator.credential.value=${OPERATOR_CREDENTIAL}`,
    '--bidvector.evaluation.candidate-cap=1000',
  ];
  const today = new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);

  // ① 수집 — 출하 일회 러너. 공고가 **이 경로로만** DB 에 들어온다.
  runJar(
    [
      ...common,
      '--server.port=0',
      '--bidvector.collection.mode=once',
      `--bidvector.collection.from=${today}`,
      `--bidvector.collection.to=${today}`,
      '--bidvector.collection.categories=construction',
      '--bidvector.collection.calls-per-day=100',
      '--bidvector.collection.calls-total=100',
      `--bidvector.collection.run-state-dir=${runStateDir}`,
      `--bidvector.koneps.service-key=${SERVICE_KEY}`,
      `--bidvector.koneps.base-url=http://127.0.0.1:${konepsPort}`,
    ],
    '수집 러너(bidvector.collection.mode=once)',
  );

  // ② 로그인 계정 — 출하 운영자 명령(가입 화면·endpoint 는 없다).
  runJar(
    [
      ...common,
      '--server.port=0',
      '--bidvector.account.mode=CREATE',
      `--bidvector.account.login=${LOGIN_NAME}`,
      `--bidvector.account.initial-value=${PASSPHRASE}`,
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
        ...common,
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

  writeFileSync(
    CONTEXT_PATH,
    `${JSON.stringify(
      {
        previewUrl: `http://127.0.0.1:${PREVIEW_PORT}`,
        loginName: LOGIN_NAME,
        passphrase: PASSPHRASE,
        maxActiveBids: MAX_ACTIVE_BIDS,
      },
      null,
      2,
    )}\n`,
    { mode: 0o600 },
  );
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
