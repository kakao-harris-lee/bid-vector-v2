// 이 하네스가 만든 컨테이너를 **그것만** 가리키는 술어와, 락 소유자의 **생존 판정**(CR r3 M-1).
//
// 왜 모듈이 따로인가: `journey-up.mjs` 는 적재되면 곧 `main()` 을 돌리므로 test 가 그 술어만
// 집어 올 수 없다. 파괴적 경로의 가드는 **test 가 직접 부를 수 있는 자리**에 있어야 한다.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

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
  } catch {
    return false;
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
