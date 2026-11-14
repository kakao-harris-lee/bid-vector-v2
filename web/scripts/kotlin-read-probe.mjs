/**
 * **층이 선 자리를 끝에서 끝까지 재는 상설 탐침**(M12/gates-web-read-3 — ⓑ⑥, r1).
 *
 * 평범한 스크립트가 Kotlin 정본을 읽으려 든다. 층이 서 있으면 **거부되고**, 서 있지 않으면 **읽는다**.
 * 그 둘을 `kotlin-read-guard.test.mjs` 가 `npm run` 으로 돌려 잰다 — 술어가 명령의 **글자**를 보는
 * 대신 그 명령이 띄운 **프로세스의 거동**을 보게 하는 자리다(v H-1: 텍스트 술어는 `NODE_ENV=test`
 * 접두 하나로 초록인 채 열렸다).
 *
 * **경로를 적지 않는다** — `git` 에게 물어 첫 정본을 고른다. 적으면 소유권 게이트의 경로 술어가 이
 * 파일을 집는다.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { isKotlinSourcePath } from './kotlin-read-guard.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const canon = execFileSync('git', ['ls-files', '--', 'shared-kernel/src/main/kotlin'], {
  cwd: repoRoot,
  encoding: 'utf8',
})
  .split('\n')
  .filter((line) => isKotlinSourcePath(line));

if (canon.length === 0) {
  // 정본을 못 찾으면 **조용히 지나가지 않는다** — 그러면 이 탐침이 늘 REFUSED 처럼 보인다.
  console.log('NO_CANON');
  process.exit(0);
}

try {
  console.log(`READ ${readFileSync(resolve(repoRoot, canon[0]), 'utf8').length}`);
} catch (error) {
  console.log(String(error.message).includes('kotlin-read-guard') ? 'REFUSED' : 'OTHER');
}
