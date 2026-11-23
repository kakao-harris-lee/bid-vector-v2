import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { expect, test } from '@playwright/test';

import { isKotlinSourcePath } from '../scripts/kotlin-read-guard.mjs';

/**
 * **층이 playwright spec 프로세스에도 서 있는가**(M12/gates-web-read-3 — ⓑ⑥).
 *
 * 이 spec 은 화면을 보지 않는다(브라우저 fixture 를 쓰지 않는다). 재는 것은 **이 프로세스가 층
 * 아래인가** 하나다 — spec 워커는 playwright 가 띄우므로 setup 을 꽂을 자리가 없고, 층이 자기를
 * `NODE_OPTIONS` 에 실어 전파하는 것이 유일한 기구다.
 *
 * **의무 게이트가 보지 못하는 자리를 이 칸이 받는다.** 그 게이트는 뿌리 config 전수가 층을
 * **import 하는가**를 세는데, 전파 블록을 층 모듈에서 빼면 **배선은 그대로 초록인 채** 워커가
 * 층 밖으로 나간다. 그 변이를 RED 로 만드는 자리가 여기다.
 *
 * Kotlin 정본의 **경로를 적지 않는다** — `git` 에게 물어 첫 정본을 고른다(경로 리터럴을 적으면
 * 소유권 게이트의 경로 술어가 이 파일을 집는다).
 */
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

const shippedKotlinFile = (): string => {
  const tracked = execFileSync('git', ['ls-files', '--', 'shared-kernel/src/main/kotlin'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
    .split('\n')
    .filter((line) => isKotlinSourcePath(line));
  // 공허를 **값 자체**로 가른다(M15/15L). `noUncheckedIndexedAccess` 아래 첨자는
  // `string | undefined` 이고 `expect(tracked.length)` 는 그 타입을 좁히지 않는다. 폴백을 두면
  // 「정본을 못 찾았다」가 **다른 파일을 읽는 통과**로 접히므로 없으면 던진다.
  const [firstTracked] = tracked;
  if (firstTracked === undefined) {
    throw new Error('Kotlin 정본을 찾지 못했다 — 이 대조가 공허하다');
  }
  return resolve(repoRoot, firstTracked);
};

test('spec 프로세스에서 Kotlin 원문을 직접 열면 던진다', () => {
  const target = shippedKotlinFile();

  expect(() => readFileSync(target, 'utf8')).toThrow(/kotlin-read-guard/u);
});

/** 음성 대조 — Kotlin 원문이 아닌 파일은 이 프로세스에서도 그대로 읽힌다(일률 거절이 아니다). */
test('spec 프로세스의 그 밖의 읽기는 통과한다', () => {
  expect(readFileSync(resolve(repoRoot, 'web', 'package.json'), 'utf8').length).toBeGreaterThan(0);
});
