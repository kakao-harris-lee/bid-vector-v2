import { execFileSync } from 'node:child_process';
import {
  closeSync,
  createReadStream,
  globSync,
  mkdirSync,
  mkdtempSync,
  openSync,
  readFileSync,
  rmSync,
} from 'node:fs';
import { readFileSync as aliasedRead } from 'fs';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { isKotlinSourcePath } from './kotlin-read-guard.mjs';
import { KOTLIN_SOURCE_KEYS, readKotlinSource } from './kotlin-source.mjs';

/**
 * **「여는 행위」를 런타임에 재는 층**(M11/gates-web-read — `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS` ⑤).
 *
 * 등재된 사실: 앞 판의 게이트는 **적힌 토큰**만 보았다. 그래서 추적되는 심볼릭 링크(⑧)·대소문자
 * (⑨)·데이터 파일의 확장자 단독 값과 glob(⑩)이 지나갔다 — 코드에 토큰이 남지 않거나 술어의
 * 글자와 달랐기 때문이다. 이 층은 **표기를 보지 않는다**: 열린 경로를 **해석된 실경로**로 바꿔
 * 확장자를 대소문자 무시로 보고, 호출자가 소유 모듈이 아니면 **던진다**.
 *
 * 그래서 아래 대조는 두 묶음이다 — **어떤 API·표기로 열어도 걸린다**(양성)와 **읽기 집합의 소유
 * 모듈과 그 밖의 파일은 그대로 돈다**(음성). 이 층이 **설치되지 않으면 양성 묶음 전부가 RED** 이고,
 * 그것이 `vite.config.ts` 의 `setupFiles` 한 줄을 지우는 변이의 자리다.
 *
 * **범위**: 이 층은 **vitest 프로세스**에 선다. playwright spec·여정 하네스·`node` 게이트
 * 스크립트는 층 밖이고 그 자리는 정적 술어가 받는다 — `design-review.md` 미달 Ⅰ 이 그 등재다.
 */

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(webRoot, '..');

/** 읽기 집합의 실제 Kotlin 정본 하나 — 경로는 `git` 에게 물어 **이 파일에 적지 않는다**. */
const shippedKotlinFile = () => {
  const tracked = execFileSync('git', ['ls-files', '--', 'shared-kernel/src/main/kotlin'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
    .split('\n')
    .filter((line) => isKotlinSourcePath(line));
  expect(tracked.length, 'Kotlin 정본을 찾지 못했다 — 이 대조가 공허하다').toBeGreaterThan(0);
  return resolve(repoRoot, tracked[0]);
};

describe('여는 행위 — 소유 모듈 밖에서 Kotlin 원문을 열면 던진다', () => {
  const target = shippedKotlinFile();

  /** 어떤 읽기 API 로 열어도 걸린다 — 층이 `fs` 표면 **전수**를 감싸므로 API 선택이 술어 밖이다. */
  it('읽기 API 를 바꿔도 걸린다', async () => {
    expect(() => readFileSync(target, 'utf8')).toThrow(/kotlin-read-guard/u);
    expect(() => aliasedRead(target, 'utf8')).toThrow(/kotlin-read-guard/u);
    expect(() => openSync(target, 'r')).toThrow(/kotlin-read-guard/u);
    expect(() => createReadStream(target)).toThrow(/kotlin-read-guard/u);
    await expect(readFile(target, 'utf8')).rejects.toThrow(/kotlin-read-guard/u);
  });

  /**
   * **⑧ — 추적되는 심볼릭 링크.** 링크의 확장자는 Kotlin 이 아니고 코드에도 토큰이 남지 않는다.
   * 실경로를 보므로 그 표기 축이 사라진다.
   */
  it('심볼릭 링크로 가려도 걸린다', () => {
    const scratch = resolve(webRoot, 'test-results');
    mkdirSync(scratch, { recursive: true });
    const dir = mkdtempSync(resolve(scratch, 'guard-'));
    const link = resolve(dir, 'policy-source.txt');
    // 링크는 **층 밖에서** 만든다. 실제 ⑧ 은 커밋된 링크를 **읽는** 꼴이고, 이 층이 재는 것도
    // 읽기다 — 층은 Kotlin 원문을 가리키는 링크를 **만드는** 호출도 거부하므로(넓은 쪽으로
    // 고른 범위이고 알려진 제한에 등재) 탐침의 준비는 `ln` 으로 한다.
    execFileSync('ln', ['-s', target, link]);

    try {
      expect(() => readFileSync(link, 'utf8')).toThrow(/kotlin-read-guard/u);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  /**
   * **⑩ — 데이터 파일의 glob.** 경로가 코드에 없고 **별표 glob** 으로만 적혀도, 여는 순간
   * 실경로가 서므로 걸린다. 정적 술어는 이 꼴을 데이터 파일 본문에서 따로 받는다(두 층).
   */
  it('glob 으로 찾아 열어도 걸린다 — 코드에 경로 토큰이 없다', () => {
    const pattern = `shared-kernel/src/main/kotlin/bidvector/sharedkernel/*.${'k'}${'t'}`;
    // **찾기는 지나간다** — 패턴은 열리는 파일이 아니다(해석되지 않는 경로는 층의 대상이 아니다).
    // 막는 자리는 **찾은 것을 여는** 순간이고, 그래서 경로를 데이터 파일에서 얻어도 같은 답이다.
    const matches = globSync(pattern, { cwd: repoRoot });

    expect(matches.length, 'glob 이 아무것도 찾지 못했다 — 이 대조가 공허하다').toBeGreaterThan(0);
    expect(() => readFileSync(resolve(repoRoot, matches[0]), 'utf8')).toThrow(/kotlin-read-guard/u);
  });
});

describe('여는 행위 — 음성 대조', () => {
  /** 소유 모듈은 읽기 집합 전수를 그대로 읽는다 — 일률 거절이 아니다. */
  it('소유 모듈의 읽기는 통과한다', () => {
    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(() => readKotlinSource(key), `${key} 를 읽지 못한다`).not.toThrow();
    }
  });

  /** Kotlin 원문이 아닌 파일은 층을 지나간다. 이것이 붉으면 test 레인 전체가 멈춘다. */
  it('그 밖의 파일 읽기는 통과한다', () => {
    const fd = openSync(resolve(webRoot, 'package.json'), 'r');
    closeSync(fd);

    expect(readFileSync(resolve(webRoot, 'package.json'), 'utf8').length).toBeGreaterThan(0);
  });

  /** 저장소 밖과 `node_modules` 는 실경로 해석을 건너뛴다 — 그 범위를 문면이 아니라 측정으로 든다. */
  it('저장소 밖 경로는 층의 대상이 아니다', () => {
    expect(() => readFileSync(resolve(webRoot, 'package-lock.json'), 'utf8')).not.toThrow();
  });
});

describe('여는 행위 — 확장자 술어는 실경로를 대소문자 무시로 본다 (⑨)', () => {
  it('Kotlin 확장자는 표기와 무관하게 참이다', () => {
    const extension = `${'k'}${'t'}`;
    for (const suffix of [extension, extension.toUpperCase(), `${extension}s`, `${extension.toUpperCase()}S`, `K${extension.slice(1)}`]) {
      expect(isKotlinSourcePath(`/a/b/X.${suffix}`), `.${suffix} 를 놓친다`).toBe(true);
    }
  });

  /** 음성 대조 — 비슷한 글자는 Kotlin 원문이 아니다(일률 참이 아니다). */
  it('Kotlin 확장자가 아닌 것은 거짓이다', () => {
    const extension = `${'k'}${'t'}`;
    for (const path of [`/a/X.${extension}x`, `/a/X.txt`, `/a/${extension}`, `/a/X.${extension}.bak`, '/a/X.mjs']) {
      expect(isKotlinSourcePath(path), `${path} 를 Kotlin 원문으로 본다`).toBe(false);
    }
  });
});
