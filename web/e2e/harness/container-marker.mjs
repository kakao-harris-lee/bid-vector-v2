// 이 하네스가 만든 컨테이너를 **그것만** 가리키는 술어(CR r2 H-1).
//
// 왜 모듈이 따로인가: `journey-up.mjs` 는 적재되면 곧 `main()` 을 돌리므로 test 가 그 술어만
// 집어 올 수 없다. 파괴적 경로의 가드는 **test 가 직접 부를 수 있는 자리**에 있어야 한다.
import { spawnSync } from 'node:child_process';

export const CONTAINER_PREFIX = 'bidvector-7e1-journey-';

/** 이름 규약(접두 + 16진 여덟 자)에 **정확히** 맞는가. 빈 값·공백·남이 쓴 내용은 표지가 아니다. */
export function isOwnContainerMarker(marker) {
  return typeof marker === 'string' && new RegExp(`^${CONTAINER_PREFIX}[0-9a-f]{8}$`).test(marker);
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
