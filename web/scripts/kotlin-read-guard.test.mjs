import { execFileSync } from 'node:child_process';
import {
  Dirent,
  Stats,
  closeSync,
  createReadStream,
  globSync,
  mkdtempSync,
  openSync,
  readFileSync,
  readFile as readFileCallback,
  realpathSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { readFileSync as aliasedRead } from 'fs';
import { readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { isKotlinSourcePath } from './kotlin-read-guard.mjs';
import { KOTLIN_SOURCE_KEYS, withKotlinSource } from './kotlin-source.mjs';

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
 * 그것이 `web/.npmrc` 의 선언 한 줄을 지우는 변이의 자리다(r2 — 앞 판은 `setupFiles` 를 그
 * 자리로 적었는데, 층이 `.npmrc` 로 옮겨간 뒤 그 줄은 아무 하중도 지지 않았다).
 *
 * **범위**(M12 — ⓑ⑤·ⓑ⑥): 세우는 자리는 **`web/.npmrc` 의 선언 하나**다. 아래 묶음이 그것을
 * 선언(구조)과 거동(명령 꼴 셋 · 하위 config · cwd 가 다른 자식)으로 재고, spec 프로세스가 실제로
 * 층 아래인지는 `e2e/kotlin-read-guard.spec.ts` 가 잰다. 남는 자리(`eval: true` worker · 자식
 * 프로세스 텍스트 검색 · npm 밖에서 직접 띄운 node)는 capability map §12.3 의 신설 OPEN 이 등재한다.
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
    // scratch 는 **playwright 가 비우는 자리 밖**에 둔다(r2 — cr LOW-5). `web/test-results/` 는
    // 그 러너가 시작할 때 비우므로, 로컬에서 두 레인을 겹쳐 돌리면 아래 `finally` 와 경합한다.
    const dir = mkdtempSync(resolve(tmpdir(), 'guard-link-'));
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
   * **콜백 표면은 콜백으로 실패한다**(r2 — cr LOW-4). 동기로 던지면 호출자의 계약이 바뀐다 —
   * 이 층의 머리 주석이 「원래 함수의 계약을 바꾸지 않는다」로 서 있으므로 그 자리를 잰다.
   */
  it('콜백 표면은 던지지 않고 `cb(error)` 로 실패한다', async () => {
    const target = shippedKotlinFile();

    const error = await new Promise((resolvePromise) => {
      readFileCallback(target, 'utf8', (failure) => resolvePromise(failure));
    });

    expect(error, '콜백이 오류를 받지 못했다').toBeInstanceOf(Error);
    expect(String(error.message)).toMatch(/kotlin-read-guard/u);
  });

  /**
   * **ⓑ③ — 저장소 밖으로 복사한 뒤 읽는다**(M12). 앞 판은 열리는 경로가 저장소 안인지 **묻고**
   * 밖이면 건너뛰었다. 그래서 디렉터리째 밖으로 복사하면(복사 자체는 디렉터리 인자라 지나간다)
   * 그 사본을 그대로 읽을 수 있었다 — 실측으로 읽혔다.
   *
   * 지금은 **위치를 묻지 않는다**. 남는 예외는 `node_modules` 하나이고, 그 분기의 값은 문면이
   * 아니라 **측정**이다(아래 음성 대조 · `commands.md`).
   *
   * 준비는 `cp` 로 한다 — 층은 Kotlin 원문을 **인자로 받는 복사**도 거부하므로(넓은 쪽으로 고른
   * 범위) 탐침이 자기 준비에 걸리면 안 된다. 실제 ⓑ③ 은 **디렉터리째** 복사하는 꼴이다.
   */
  it('저장소 밖으로 복사한 사본도 걸린다 — 위치를 묻지 않는다', () => {
    const dir = mkdtempSync(resolve(tmpdir(), 'guard-outside-'));
    const copy = resolve(dir, 'copied-source.txt');
    const kotlinCopy = resolve(dir, `X.${'k'}${'t'}`);
    execFileSync('cp', [target, kotlinCopy]);
    execFileSync('cp', [target, copy]);

    try {
      // 이름이 보존된 사본 — **읽을 때** 실경로의 확장자가 선다.
      expect(() => readFileSync(kotlinCopy, 'utf8')).toThrow(/kotlin-read-guard/u);
      // 이름을 바꾼 사본은 Kotlin 원문이 **아니다**(확장자가 성질이다). 그 꼴을 받는 자리는
      // 복사하는 순간이다 — 원본 인자가 Kotlin 실경로라 층이 거기서 거부한다.
      expect(() => readFileSync(copy, 'utf8')).not.toThrow();
      expect(() => readFileSync(target, 'utf8')).toThrow(/kotlin-read-guard/u);
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

/**
 * **번들러 적재는 vite 메인 프로세스가 연다**(M12 — ⓑ⑤). 소비자 프로세스의 `fs` 표면을 감싸는
 * 층은 그 읽기를 보지 못했고, 패턴에 확장자 토큰이 없으면 정적 술어도 보지 못했다(착수 실측:
 * 42 모듈 적재). 지금은 `.npmrc` 선언이 **그 프로세스도** 층 아래에 둔다 — 그래서 선언을 지우는
 * 변이에서 이 칸이 함께 RED 다.
 */
describe('여는 행위 — 번들러 적재도 층 아래다 (ⓑ⑤)', () => {
  /**
   * 쥐는 자리를 **동적**으로 둔다 — 정적으로 import 하면 이 test 파일 자체가 적재되지 못한다.
   * 거절의 **문면은 층의 것이 아니다**(vite 가 층의 거부를 잡고 상대 경로로 다시 열어 `ENOENT` 를
   * 낸다 — 알려진 제한). 그래서 「거절한다」까지만 재고, 그 거절이 **Kotlin 축에서만** 일어남은
   * 아래 통제군이 든다.
   */
  it('확장자 토큰 없는 `?raw` glob 은 적재되지 않는다', async () => {
    // **matcher 를 둔다**(r2 — cr LOW-2): 빈 `toThrow()` 는 탐침의 오타·경로 변경 같은 **층과
    // 무관한 실패**도 초록으로 받는다. 문면이 vite 의 `ENOENT` 라 층 문면으로는 묶지 못하므로
    // 그 둘 가운데 하나를 요구한다.
    await expect(import('./kotlin-raw-glob-probe.mjs')).rejects.toThrow(/ENOENT|kotlin-read-guard/u);
  });

  /**
   * **통제군** — 저장소 밖의 **비 Kotlin** 파일을 같은 꼴로 끌어오면 그대로 적재된다. 이것이
   * 통과해야 위 거절이 「glob 이 통째로 막혔다」가 아니라 **Kotlin 축**임이 선다.
   */
  it('저장소 밖 비 Kotlin 파일의 같은 glob 은 적재된다', () => {
    const loaded = import.meta.glob('../../openapi/*', { query: '?raw', import: 'default', eager: true });

    expect(Object.keys(loaded).length, '통제군이 아무것도 적재하지 않았다 — 대조가 공허하다').toBeGreaterThan(
      0,
    );
  });
});

describe('여는 행위 — 음성 대조', () => {
  /**
   * 소유 모듈은 읽기 집합 전수를 그대로 읽는다 — 일률 거절이 아니다.
   *
   * 읽기는 **추출 함수를 넘겨받는다**(M12 — ⓐ). 여기서 넘기는 것은 길이를 재는 함수다: 원문을
   * 그대로 돌려주면 읽기가 **거부하므로**(항등 함수 후조건) 이 대조도 추출을 해야 한다.
   */
  it('소유 모듈의 읽기는 통과한다', () => {
    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(
        () => withKotlinSource(key, (text) => text.length),
        `${key} 를 읽지 못한다`,
      ).not.toThrow();
    }
  });

  /** Kotlin 원문이 아닌 파일은 층을 지나간다. 이것이 붉으면 test 레인 전체가 멈춘다. */
  it('그 밖의 파일 읽기는 통과한다', () => {
    const fd = openSync(resolve(webRoot, 'package.json'), 'r');
    closeSync(fd);

    expect(readFileSync(resolve(webRoot, 'package.json'), 'utf8').length).toBeGreaterThan(0);
  });

  /**
   * **남는 위치 예외는 `node_modules` 하나다**(M12 — ⓑ③ 의 대가).
   *
   * 앞 판은 이 자리에서 「저장소 밖 경로는 층의 대상이 아니다」를 주장했는데, 재는 입력이
   * **저장소 안의** 비 Kotlin 파일이라 그 주장을 재지 못했다. 그 칸은 위 양성 묶음이 받았고
   * (위치를 묻지 않는다) 이 칸은 **남은 예외 하나**를 재는 자리로 주장을 바꾼다.
   *
   * 지우지 않는 이유는 **값이 있기 때문**이다: 이 분기가 없으면 모든 모듈 적재가 실경로 호출을
   * 타고 vitest 전건이 느려진다(같은 조건 3회 중위값 — `commands.md` 「위치 분기 셋 갈래」).
   * 그래서 「`node_modules` 아래로 복사한 뒤 읽기」는 열려 있고, 그 꼴은 평범한 관용구가 아니라
   * **의도적 은닉**이라 경계 밖으로 등재한다.
   */
  it('node_modules 아래의 Kotlin 사본은 층을 지나간다 — 값이 있는 예외 하나', () => {
    const dir = mkdtempSync(resolve(webRoot, 'node_modules', '.guard-probe-'));
    const inside = resolve(dir, `X.${'k'}${'t'}`);
    execFileSync('cp', [shippedKotlinFile(), inside]);

    try {
      expect(() => readFileSync(inside, 'utf8')).not.toThrow();
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  /** 저장소 밖의 **비 Kotlin** 파일은 그대로 지나간다 — 일률 거절이 아니다. */
  it('저장소 밖의 비 Kotlin 파일 읽기는 통과한다', () => {
    const dir = mkdtempSync(resolve(tmpdir(), 'guard-plain-'));
    const plain = resolve(dir, 'notes.txt');
    writeFileSync(plain, 'plain\n');

    try {
      expect(readFileSync(plain, 'utf8')).toBe('plain\n');
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
});

/**
 * **층이 원래 계약을 바꾸지 않는다**(r1 — cr H-2). `fs` 의 함수 값에는 **생성자**가 섞여 있어,
 * 앞 판처럼 평범한 함수로 감싸면 `instanceof` 가 거짓이 되고 ES class 는 `new` 가 던졌다. 그
 * 깨짐은 **오늘의 전건이 밟지 않는 잠복**이라 초록으로 지나갔다 — 그래서 그 자리를 test 로 든다.
 */
describe('여는 행위 — 감싸기가 생성·동일성 계약을 바꾸지 않는다', () => {
  it('`instanceof` 가 유지된다', () => {
    const stats = statSync(resolve(webRoot, 'package.json'));

    expect(stats instanceof Stats, 'statSync 결과가 Stats 가 아니다').toBe(true);
  });

  it('ES class 를 `new` 로 부를 수 있다', () => {
    const entry = new Dirent('x', 1, '/tmp');

    expect(entry instanceof Dirent).toBe(true);
    expect(entry.name).toBe('x');
  });

  it('함수에 붙은 속성이 보존된다', () => {
    expect(typeof realpathSync.native).toBe('function');
  });

  /** 그 셋이 통과하는 동안에도 **거부는 그대로 발화한다** — 계약 보존이 층을 끄는 것이 아니다. */
  it('계약을 지키면서도 거부는 발화한다', () => {
    expect(() => readFileSync(shippedKotlinFile(), 'utf8')).toThrow(/kotlin-read-guard/u);
  });
});

/**
 * **공개 표면이 셋으로 고정된다**(r1 — cr MEDIUM). 층이 자라면서 내보내는 것이 늘면 그만큼
 * 「밖에서 쓸 수 있는 것」이 늘고, 그 증가는 문면으로만 두면 **조용히** 일어난다. 공유 읽기 모듈이
 * 같은 자리에서 그렇게 열렸던 선례가 있어(그 모듈의 export 집합 등식) 같은 꼴을 여기에도 둔다.
 *
 * 늘리거나 줄이면 이 줄이 먼저 붉어지고, 사람이 그 표면이 무엇을 내주는지 보고 판단한다.
 */
describe('여는 행위 — 공개 표면이 다섯으로 고정된다', () => {
  /**
   * **셋에서 다섯으로 늘었다**(r2 — cr HIGH). 악수 둘(`runWithInFlightText` · `isInFlightText`)이
   * 들어왔다. 늘린 사유를 여기 적는다: 읽기 모듈과 추출 커널이 **둘 다 이 층을 이미 쓰므로**
   * 슬롯을 여기 두면 공유 읽기가 특정 하네스의 커널을 알 필요가 없다. 내주는 것은 **불리언**이고
   * 거는 자리는 호출자 프레임으로 읽기 하나에 고정된다.
   */
  it('export 집합이 정확히 다섯이다', async () => {
    const namespace = await import('./kotlin-read-guard.mjs');

    expect(Object.keys(namespace).sort(), '층의 공개 표면이 바뀌었다 — 늘었는지 줄었는지 보고 판단하라').toStrictEqual([
      'callerOutside',
      'installKotlinReadGuard',
      'isInFlightText',
      'isKotlinSourcePath',
      'runWithInFlightText',
    ]);
  });

  /** 악수를 거는 자리는 **읽기 하나**다 — 밖에서 걸려 하면 던진다(프레임으로 판정). */
  it('읽기 밖에서는 in-flight 텍스트를 걸 수 없다', async () => {
    const { runWithInFlightText } = await import('./kotlin-read-guard.mjs');

    expect(() => runWithInFlightText('아무 텍스트', () => 1)).toThrow(/읽기 모듈만 건다/u);
  });
});

/**
 * **층이 선 자리를 글자가 아니라 거동으로 센다**(M12 — ⓑ⑥, r1 에 바꿨다).
 *
 * **앞 판은 열거였다.** `package.json` 의 `node` 명령마다 플래그를, 뿌리 `*.config.*` 마다 import 를
 * 요구하고 그 의무를 **텍스트 꼴**로 셌다 — `scripts` 값을 `&&` 로 나눠 `^node\s` 로 시작하는 조각만
 * 보고, config 는 뿌리에 있는 것만 봤다. 그래서 평범한 표기 넷이 게이트를 초록으로 지나가며 원문을
 * 읽었다(실측, r1 — 환경변수 접두 · `;` 로 이은 둘째 명령 · `env` 명령 · 하위 디렉터리 config).
 *
 * 지금은 **선언 한 줄**(`web/.npmrc` 의 `node-options`)이 npm 이 띄우는 모든 프로세스를 덮는다.
 * 그래서 이 묶음이 세는 것은 둘뿐이다 — **그 선언이 있는가**(구조)와 **그 넷이 실제로 거부되는가**
 * (거동). 명령의 모양도 config 의 자리도 술어에 없다.
 *
 * **경계**: npm 밖에서 직접 띄운 node 는 이 기구가 닿지 않는다(`scope.md` §위협 모델 경계).
 */
describe('여는 행위 — 층이 선 자리를 거동으로 센다 (ⓑ⑥)', () => {
  /** 층을 세우는 **유일한 모듈** — 선언이 가리키는 자리다. */
  const SETUP = 'scripts/kotlin-read-guard-setup.mjs';

  /**
   * **부모의 `NODE_OPTIONS` 를 비우고 npm 을 띄운다.** 비우지 않으면 이 test 프로세스가 이미 지고
   * 있는 값이 자식에게 **상속되어**, `.npmrc` 선언을 지운 변이에서도 대조가 초록이었다(실측 —
   * 그 변이가 이 사각을 찾아 줬다). 비우면 층이 서는 길이 **npm 의 선언 하나**뿐이다.
   */
  const npmRun = (script, overrides = {}) =>
    execFileSync('npm', ['run', '-s', script], {
      cwd: webRoot,
      encoding: 'utf8',
      env: { ...process.env, NODE_OPTIONS: '', ...overrides },
    }).trim();

  it('`.npmrc` 가 층을 선언한다 — 그 선언이 npm 의 모든 스크립트에 싣는다', () => {
    const npmrc = readFileSync(resolve(webRoot, '.npmrc'), 'utf8');
    const declared = npmrc
      .split('\n')
      .filter((line) => !line.trimStart().startsWith('#'))
      .map((line) => line.trim())
      .filter((line) => line.startsWith('node-options='));

    expect(declared, '`.npmrc` 에 층 선언이 없거나 둘이다 — npm 은 마지막 값만 쓴다').toHaveLength(1);
    expect(declared[0], '선언이 층 모듈을 가리키지 않는다').toContain(SETUP);
    expect(declared[0], '선언이 `--import` 가 아니다 — 그러면 층이 서지 않는다').toContain('--import');
  });

  /**
   * **r1 이 지나보낸 명령 꼴 셋을 상설 양성 대조로 둔다.** 탐침 스크립트 하나를 세 꼴로 띄우고
   * 세 줄이 모두 거부인지 본다 — 글자를 세지 않고 **그 프로세스가 무엇을 할 수 있는지**를 센다.
   */
  it('명령의 꼴이 달라도 거부된다 — 환경변수 접두 · `;` · `env` 명령', () => {
    const lines = npmRun('probe:kotlin-read').split('\n').map((line) => line.trim());

    expect(lines, '탐침이 세 꼴로 돌지 않았다 — 대조가 공허하다').toHaveLength(3);
    expect(lines).toStrictEqual(['REFUSED', 'REFUSED', 'REFUSED']);
  });

  /**
   * **음성 대조** — 층을 싣지 않은 같은 탐침은 **읽는다**. 이것이 초록이어야 위 묶음이 「탐침이
   * 늘 거부한다」가 아니라 「층이 거부한다」가 된다.
   */
  it('층 없이 같은 탐침을 돌리면 읽는다 — 대조가 공허하지 않다', () => {
    const output = execFileSync(process.execPath, ['scripts/kotlin-read-probe.mjs'], {
      cwd: webRoot,
      encoding: 'utf8',
      env: { ...process.env, NODE_OPTIONS: '' },
    }).trim();

    expect(output.startsWith('READ'), `층 없이도 읽지 못했다: ${output}`).toBe(true);
  });

  /**
   * **전파가 node 자식까지 닿고, cwd 가 달라도 자식이 뜬다.**
   *
   * `.npmrc` 가 싣는 지정자는 **상대 경로**라 그대로 물려주면 cwd 가 다른 자식이
   * `ERR_MODULE_NOT_FOUND` 로 **뜨지도 못한다**(r1 실측 — `/tmp` 를 cwd 로 띄운 자식이 죽었다).
   * 층 모듈이 자기 **절대 URL 로 바로잡아** 싣기 때문에 이 칸이 초록이다. 바로잡기를 지우면
   * 층이 조용히 사라지는 것이 아니라 **자식이 죽어서** 붉어진다.
   *
   * 자식에게 경로를 **인자로** 넘긴다 — `-e` 본문에 적으면 이 파일이 소유권 게이트의 경로
   * 술어에 걸린다.
   */
  it('전파가 cwd 가 다른 node 자식까지 닿는다', () => {
    const target = shippedKotlinFile();
    const code =
      'const { readFileSync } = require("node:fs");' +
      'try { readFileSync(process.argv[1], "utf8"); console.log("READ"); }' +
      'catch (error) { console.log(String(error.message).includes("kotlin-read-guard") ? "REFUSED" : "OTHER"); }';
    const run = (options) =>
      execFileSync(process.execPath, ['-e', code, target], { encoding: 'utf8', ...options }).trim();

    expect(run({ cwd: webRoot }), '같은 cwd 자식이 읽는다').toBe('REFUSED');
    expect(run({ cwd: tmpdir() }), 'cwd 가 다른 자식이 죽거나 읽는다').toBe('REFUSED');
    // 음성 — 그 변수를 비우면 같은 자식이 읽는다(전파가 **그 변수로** 일한다는 측정).
    expect(run({ cwd: webRoot, env: { ...process.env, NODE_OPTIONS: '' } })).toBe('READ');
  });

  /**
   * **하위 디렉터리 config 로 띄운 spec 프로세스**(r1 이 지나보낸 넷째 꼴). 그 config 는 층을
   * import 하지 않는다 — 덮는 것은 `.npmrc` 다. 돌리는 spec 은 스모크 레인의 그 파일이고, 그
   * 안의 단언이 **거부**를 요구한다. 그래서 이 칸은 그 실행이 통과하는지만 보면 된다.
   */
  it('config 의 자리가 달라도 거부된다 — 하위 디렉터리 config', () => {
    const output = npmRun('probe:kotlin-read-config');

    expect(output, '탐침 config 가 그 spec 을 돌리지 않았다 — 대조가 공허하다').toContain(
      'kotlin-read-guard.spec.ts',
    );
    expect(output).toContain('passed');
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
