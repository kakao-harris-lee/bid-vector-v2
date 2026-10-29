// 이 하네스가 만든 컨테이너를 **그것만** 가리키는 술어와, 락 소유자의 **생존 판정**(CR r3 M-1).
//
// 왜 모듈이 따로인가: `journey-up.mjs` 는 적재되면 곧 `main()` 을 돌리므로 test 가 그 술어만
// 집어 올 수 없다. 파괴적 경로의 가드는 **test 가 직접 부를 수 있는 자리**에 있어야 한다.
import { spawnSync } from 'node:child_process';
import { linkSync, readFileSync, rmSync, writeFileSync } from 'node:fs';

export const CONTAINER_PREFIX = 'bidvector-7e1-journey-';

/** 이름 규약(접두 + 16진 여덟 자)에 **정확히** 맞는가. 빈 값·공백·남이 쓴 내용은 표지가 아니다. */
export function isOwnContainerMarker(marker) {
  return typeof marker === 'string' && new RegExp(`^${CONTAINER_PREFIX}[0-9a-f]{8}$`).test(marker);
}

/**
 * 락 표지는 **소유자까지** 적는다. 컨테이너 이름만 적던 앞 판은 「앞 실행이 살아 있는가」를
 * 물을 수 없어, 기동 창(preview 가 아직 포트를 잡기 전)에 들어온 둘째 실행이 **살아 있는 첫
 * 실행의 컨테이너를 걷었다**(CR r3 M-1).
 */
export function formatLockMarker({ pid, startToken, container }) {
  return `${JSON.stringify({ pid, startToken, container })}\n`;
}

export function parseLockMarker(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return undefined;
  }
  if (typeof parsed !== 'object' || parsed === null) {
    return undefined;
  }
  const { pid, startToken, container } = parsed;
  if (!Number.isInteger(pid) || pid <= 0 || !isOwnContainerMarker(container)) {
    return undefined;
  }
  return { pid, startToken: typeof startToken === 'string' ? startToken : undefined, container };
}

/**
 * 그 pid 가 **언제 시작했는가** — pid 재사용을 가른다. Linux `/proc/<pid>/stat` 의 22 번째
 * 항목(starttime)이다. 괄호를 담을 수 있는 둘째 항목(comm) 때문에 **마지막 `)` 뒤**부터 센다.
 * 읽을 수 없으면 `undefined` — 그때는 아래 생존 판정이 **보수적인 쪽**으로 기운다.
 */
export function processStartToken(pid, { read = readFileSync } = {}) {
  let stat;
  try {
    stat = read(`/proc/${pid}/stat`, 'utf8');
  } catch {
    return undefined;
  }
  const afterComm = stat.slice(stat.lastIndexOf(')') + 1).trim().split(/\s+/u);
  // afterComm[0] 이 state(3 번째 항목)이므로 starttime(22 번째)은 그로부터 19 칸 뒤다.
  const token = afterComm[19];
  return token === undefined ? undefined : token;
}

/**
 * 락을 쥔 프로세스가 **아직 살아 있는가**.
 *
 * 살아 있다고 보는 쪽이 안전하다 — 틀리면 멈출 뿐이고, 반대로 틀리면 **남의 실행을 걷는다**.
 * 그래서 ⓐ 신호가 닿으면 살아 있다고 보고, ⓑ 시작 시각을 둘 다 읽을 수 있을 때에만 그것으로
 * pid 재사용을 걸러 낸다(한쪽이라도 모르면 살아 있다고 본다).
 */
export function lockOwnerAlive(marker, { signal = process.kill, startToken = processStartToken } = {}) {
  try {
    signal(marker.pid, 0);
  } catch (error) {
    // **`ESRCH` 만 죽음이다**(CR r4 M-2 · r5 L-4). 신호 권한이 없는 것(`EPERM`)은 **살아
    // 있다는 뜻**이고, 모르는 코드도 같은 쪽으로 기운다 — 이 모듈이 선언한 방향이 「모르면
    // 살아 있다」이기 때문이다(틀리면 멈출 뿐, 반대로 틀리면 남의 실행을 걷는다). 앞 판은
    // `EPERM` 만 가리고 나머지를 죽음으로 세어 선언과 기본값이 어긋나 있었다.
    if (error?.code === 'ESRCH') {
      return false;
    }
    return true;
  }
  const current = startToken(marker.pid);
  if (marker.startToken === undefined || current === undefined) {
    return true;
  }
  return current === marker.startToken;
}

/**
 * 묵은 락이 가리키는 **그 컨테이너만** 걷는다.
 *
 * 앞 판은 읽은 값을 그대로 `--filter name=<값>` 에 넘겼다. 값이 비면 docker 가 그 필터를
 * **「전부」로 읽어** 호스트의 모든 컨테이너를 골랐고(실측: `docker ps -aq` 와 `--filter name=`
 * 가 같은 45 를 냈다) 그 뒤 줄이 `rm -f` 였다 — 다른 레인의 버릴 Postgres 와 상주 서비스가 함께
 * 사라지는 경로였다. 그래서 ⓐ 표지가 규약에 맞을 때만 ⓑ 필터를 **앵커**로 건다.
 *
 * 반환은 걷은(또는 `dryRun` 에서 고른) id 목록이라 양성 대조가 **계수**로 선다.
 */
export function reapStaleContainer(marker, { dryRun = false, run = spawnSync, log = () => {} } = {}) {
  if (!isOwnContainerMarker(marker)) {
    log(`묵은 락의 표지가 이름 규약이 아니다(${JSON.stringify(marker)}) — 아무것도 걷지 않는다`);
    return [];
  }
  const listed = run('docker', ['ps', '-aq', '--filter', `name=^${marker}$`], { encoding: 'utf8' });
  const ids = (listed.stdout ?? '')
    .split('\n')
    .map((one) => one.trim())
    .filter((one) => one !== '');
  if (ids.length > 0 && !dryRun) {
    run('docker', ['rm', '-f', ...ids], { stdio: 'ignore' });
  }
  return ids;
}

/**
 * 단일 실행 락의 **원자적 배타 획득**(CR r4 M-3 · v r4 L-1).
 *
 * 앞 판은 `existsSync` 확인 → 임시 파일 `wx` → `renameSync` 였다. `rename` 은 **있는 락을
 * 말없이 덮어쓰므로**, 두 실행이 그 창 안에서 시작하면 둘 다 「락 없음」을 보고 둘 다 소유자가
 * 됐다(그 뒤 맥락 파일을 서로 덮고, 먼저 끝나는 쪽이 공유 둘을 지운다).
 *
 * 그래서 **락 경로 자체를 `wx`(`O_CREAT|O_EXCL`)로 연다** — 커널이 하나만 열어 준다. `EEXIST`
 * 면 표지를 읽어 ⓐ 소유자가 살아 있으면 멈추고 ⓑ 죽었으면 그 컨테이너를 걷고 락을 치운 뒤
 * 한 번 다시 시도한다. 표지를 읽을 수 없으면(옛 꼴·깨진 값·열고 쓰기 사이의 찰나) **멈춘다** —
 * 소유자를 알 수 없을 때 넘겨받지 않는 것이 이 모듈의 방향이다.
 *
 * 하네스의 배선을 test 가 부를 수 있도록 **이 모듈에 둔다**(v r4 L-2): `journey-up.mjs` 는
 * 적재되면 곧 `main()` 을 돌려 test 가 import 할 수 없다.
 */
/** 락 자리를 읽은 결과 — 「비었다」와 「못 읽겠다」는 다른 답이다. */
function readLockSlot(fs, lockPath) {
  let raw;
  try {
    raw = fs.readFileSync(lockPath, 'utf8');
  } catch (error) {
    // **그 자리가 비었다**(CR r5 L-1): `EEXIST` 와 읽기 사이에 앞 실행이 정상 종료하며 락을
    // 지울 수 있다. 그것은 재시도가 처리할 정상 경합이고 실패가 아니다.
    if (error?.code === 'ENOENT') {
      return { kind: 'gone' };
    }
    throw error;
  }
  const marker = parseLockMarker(raw);
  return marker === undefined ? { kind: 'unreadable' } : { kind: 'marker', marker };
}

const sameOwner = (a, b) => a.pid === b.pid && a.startToken === b.startToken && a.container === b.container;

export function acquireSingleRunLock({
  lockPath,
  pid,
  startToken,
  container,
  fs = { writeFileSync, linkSync, readFileSync, rmSync },
  alive = lockOwnerAlive,
  reap = reapStaleContainer,
  log = () => {},
}) {
  const marker = formatLockMarker({ pid, startToken, container });
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    // **표지를 다 쓴 뒤 `link` 로 건다**(CR r5 L-1 ⓑ) — `link` 는 대상이 있으면 `EEXIST` 이므로
    // 배타는 그대로이고, 락 파일은 **처음부터 완전한 내용**으로 나타난다. 앞 판(`wx` 로 열고
    // 그 fd 에 쓰기)은 열기와 쓰기 사이에 죽으면 **빈 락**을 남겨, 이후 모든 실행이 「표지를
    // 읽을 수 없다」로 멈추는 운영 비용을 만들었다.
    const pending = `${lockPath}.${pid}.${attempt}.tmp`;
    fs.rmSync(pending, { force: true });
    fs.writeFileSync(pending, marker, { flag: 'wx', mode: 0o600 });
    try {
      fs.linkSync(pending, lockPath);
      return { attempt };
    } catch (error) {
      if (error?.code !== 'EEXIST') {
        throw error;
      }
      const slot = readLockSlot(fs, lockPath);
      if (slot.kind === 'gone') {
        continue;
      }
      if (slot.kind === 'unreadable') {
        throw new Error(
          `락 표지를 읽을 수 없다(${lockPath}) — 앞 실행이 살아 있는지 알 수 없으므로 ` +
            '아무것도 걷지 않고 멈춘다. 그 실행이 끝난 것을 확인한 뒤 그 파일을 지우십시오.',
          { cause: error },
        );
      }
      if (alive(slot.marker)) {
        throw new Error(
          `앞 실행이 살아 있다(pid ${slot.marker.pid} · 컨테이너 ${slot.marker.container}) — ` +
            '같은 호스트에서 둘이 돌 수 없다. 그 실행이 끝난 뒤 다시 돌리십시오.',
          { cause: error },
        );
      }
      log(`묵은 락을 잇는다(죽은 소유자 pid ${slot.marker.pid})`);
      const reaped = reap(slot.marker.container, { log });
      log(`묵은 컨테이너 ${reaped.length}개를 걷었다`);

      // **지우기 전에 그 자리를 다시 본다**(v r5 L-1) — 수거(docker 호출)가 도는 사이에 다른
      // 실행이 같은 묵은 락을 이어받았을 수 있다. 그때 지우면 **살아 있는 남의 락**을 지우고
      // 둘 다 소유자가 된다(실측). 내용이 처음 읽은 표지와 같을 때만 지운다.
      const current = readLockSlot(fs, lockPath);
      if (current.kind === 'gone') {
        continue;
      }
      if (current.kind !== 'marker' || !sameOwner(current.marker, slot.marker)) {
        throw new Error(
          `묵은 락이 그 사이 바뀌었다(${lockPath}) — 남의 락을 지우지 않는다. 다시 돌리십시오.`,
          { cause: error },
        );
      }
      fs.rmSync(lockPath, { force: true });
      continue;
    } finally {
      fs.rmSync(pending, { force: true });
    }
  }
  throw new Error(
    `락을 얻지 못했다(${lockPath}) — 묵은 락을 치운 직후 다른 실행이 그 자리를 잡았다. 다시 돌리십시오.`,
  );
}
