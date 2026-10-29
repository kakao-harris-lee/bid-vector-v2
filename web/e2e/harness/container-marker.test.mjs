import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

import {
  acquireSingleRunLock,
  formatLockMarker,
  isOwnContainerMarker,
  lockOwnerAlive,
  parseLockMarker,
  processStartToken,
  reapStaleContainer,
} from './container-marker.mjs';

/**
 * CR r2 H-1 — 묵은 락 수거가 **호스트의 모든 컨테이너**를 지우는 경로였다. 빈 표지를 그대로
 * `--filter name=` 에 넘기면 docker 가 그것을 「전부」로 읽는다(실측: 이 호스트에서
 * `docker ps -aq` 와 같은 45). 그 가드를 여기서 잠근다.
 *
 * **이 test 는 docker 를 부르지 않는다** — `run` 을 주입해 **넘어간 인자**와 **고른 수**를 센다.
 * 실제 컨테이너를 지우는 양성 대조를 단위 test 로 두면 그 자체가 파괴적 경로가 된다.
 */
describe('이 하네스의 컨테이너 표지', () => {
  it('이름 규약에 맞는 표지만 받는다', () => {
    expect(isOwnContainerMarker('bidvector-7e1-journey-0b2b919e')).toBe(true);
  });

  it.each([
    ['빈 문자열', ''],
    ['공백', '   '],
    ['접두만', 'bidvector-7e1-journey-'],
    ['남의 이름', 'postgres'],
    ['다른 레인 접두', 'bidvector-v2-app'],
    ['16진이 아닌 꼬리', 'bidvector-7e1-journey-zzzzzzzz'],
    ['길이가 다른 꼬리', 'bidvector-7e1-journey-0b2b919'],
    ['뒤에 덧붙음', 'bidvector-7e1-journey-0b2b919e-extra'],
    ['와일드카드', '*'],
    ['정규식 조각', '.*'],
    ['문자열이 아님', undefined],
  ])('%s 은 표지가 아니다', (_label, marker) => {
    expect(isOwnContainerMarker(marker)).toBe(false);
  });
});

describe('묵은 컨테이너 수거', () => {
  /** 양성 대조 ① — **빈 표지면 docker 를 아예 부르지 않는다**(「전부」 필터가 만들어지지 않는다). */
  it('빈 표지에서는 수거 0 건이고 docker 를 부르지 않는다', () => {
    const run = vi.fn();

    expect(reapStaleContainer('', { run })).toStrictEqual([]);
    expect(run).not.toHaveBeenCalled();
  });

  /** 양성 대조 ② — 옳은 표지에서는 **앵커 필터**로 그 하나만 고른다. */
  it('옳은 표지에서는 앵커 필터로 그 이름만 고른다', () => {
    const marker = 'bidvector-7e1-journey-0b2b919e';
    const run = vi.fn((_cmd, args) =>
      args.includes('ps') ? { stdout: 'c0ffee123456\n' } : { stdout: '' },
    );

    const reaped = reapStaleContainer(marker, { run });

    expect(reaped).toStrictEqual(['c0ffee123456']);
    const listArgs = run.mock.calls[0]?.[1];
    expect(listArgs).toContain(`name=^${marker}$`);
    // 앵커 없는 필터는 접두가 같은 **다른 실행**까지 고른다 — 그 꼴이 아님을 못박는다.
    expect(listArgs).not.toContain(`name=${marker}`);
    expect(run.mock.calls[1]?.[1]).toStrictEqual(['rm', '-f', 'c0ffee123456']);
  });

  it('고를 것이 없으면 rm 을 부르지 않는다', () => {
    const run = vi.fn(() => ({ stdout: '\n' }));

    expect(reapStaleContainer('bidvector-7e1-journey-0b2b919e', { run })).toStrictEqual([]);
    expect(run).toHaveBeenCalledTimes(1);
  });

  it('dryRun 은 고르기만 하고 지우지 않는다 — 양성 대조를 계수로 세는 자리다', () => {
    const run = vi.fn((_cmd, args) => (args.includes('ps') ? { stdout: 'abc123\n' } : { stdout: '' }));

    expect(reapStaleContainer('bidvector-7e1-journey-0b2b919e', { dryRun: true, run })).toStrictEqual(['abc123']);
    expect(run).toHaveBeenCalledTimes(1);
  });
});

/**
 * CR r3 M-1 — 기동 창의 상호 배제. **주입 test 뿐이다**(실 컨테이너를 지우는 대조는 하지
 * 않는다 — 공유 호스트다). 생존 판정이 틀릴 때 어느 쪽으로 기우는지를 못박는 것이 이 절의 일.
 */
describe('락 소유자 생존 판정', () => {
  const MARKER = { pid: 4321, startToken: '777', container: 'bidvector-7e1-journey-0b2b919e' };

  it('신호가 닿고 시작 시각이 같으면 살아 있다 — 걷지 않고 멈추는 쪽이다', () => {
    const alive = lockOwnerAlive(MARKER, {
      signal: () => undefined,
      startToken: () => '777',
    });

    expect(alive).toBe(true);
  });

  it('신호가 닿지 않으면 죽었다 — 그때만 묵은 락을 잇는다', () => {
    const alive = lockOwnerAlive(MARKER, {
      signal: () => {
        throw Object.assign(new Error('no such process'), { code: 'ESRCH' });
      },
      startToken: () => undefined,
    });

    expect(alive).toBe(false);
  });

  it('같은 pid 가 다시 쓰인 경우는 죽은 것으로 센다 — 시작 시각이 다르다', () => {
    const alive = lockOwnerAlive(MARKER, { signal: () => undefined, startToken: () => '999' });

    expect(alive).toBe(false);
  });

  /**
   * CR r4 M-2 — `kill(pid, 0)` 은 **살아 있지만 신호 권한이 없는** 프로세스에 `EPERM` 을 던진다
   * (죽은 pid 는 `ESRCH`). 앞 판은 예외 종류를 가르지 않아 그것을 「죽었다」로 셌다 — 자기 주석이
   * 선언한 안전 방향의 **반대**다. 이 축이 비어 있어 그 결함이 초록이었다.
   */
  it('EPERM 은 살아 있다로 센다 — 권한이 없을 뿐이다', () => {
    const alive = lockOwnerAlive(MARKER, {
      signal: () => {
        throw Object.assign(new Error('operation not permitted'), { code: 'EPERM' });
      },
      startToken: () => undefined,
    });

    expect(alive).toBe(true);
  });

  /** CR r5 L-4 — 모르는 코드도 **살아 있다**로 센다(선언한 방향이 그렇다). `ESRCH` 만 죽음이다. */
  it('모르는 오류 코드는 살아 있다로 센다 — 기본값이 선언과 같은 쪽이다', () => {
    const alive = lockOwnerAlive(MARKER, {
      signal: () => {
        throw Object.assign(new Error('invalid argument'), { code: 'EINVAL' });
      },
      startToken: () => undefined,
    });

    expect(alive).toBe(true);
  });

  it('실제 pid 1 도 살아 있다로 센다 — 이 호스트에서 EPERM 이 나는 자리다', () => {
    expect(lockOwnerAlive({ ...MARKER, pid: 1, startToken: undefined })).toBe(true);
  });

  it('시작 시각을 모르면 **살아 있다고 본다** — 틀려도 멈출 뿐, 남의 실행을 걷지 않는다', () => {
    expect(lockOwnerAlive({ ...MARKER, startToken: undefined }, { signal: () => undefined })).toBe(true);
    expect(lockOwnerAlive(MARKER, { signal: () => undefined, startToken: () => undefined })).toBe(true);
  });

  it('자기 자신은 살아 있고, 그 시작 시각은 /proc 에서 읽힌다', () => {
    const token = processStartToken(process.pid);

    expect(token).toMatch(/^[0-9]+$/u);
    expect(lockOwnerAlive({ pid: process.pid, startToken: token, container: MARKER.container })).toBe(true);
  });
});

describe('락 표지의 꼴', () => {
  it('적은 것을 그대로 돌려 읽는다', () => {
    const marker = { pid: 7, startToken: '1', container: 'bidvector-7e1-journey-0b2b919e' };

    expect(parseLockMarker(formatLockMarker(marker))).toStrictEqual(marker);
  });

  it.each([
    ['빈 표지', ''],
    ['공백', '   \n'],
    ['컨테이너 이름만(옛 꼴)', 'bidvector-7e1-journey-0b2b919e'],
    ['pid 가 없다', '{"container":"bidvector-7e1-journey-0b2b919e"}'],
    ['pid 가 수가 아니다', '{"pid":"x","container":"bidvector-7e1-journey-0b2b919e"}'],
    ['컨테이너가 규약 밖', '{"pid":7,"container":"other-thing"}'],
    ['JSON 이 아니다', '{not json'],
  ])('%s 은 표지가 아니다 — 소유자를 알 수 없으면 멈추는 쪽이다', (_label, text) => {
    expect(parseLockMarker(text)).toBeUndefined();
  });
});

/**
 * CR r4 M-3 · v r4 L-1 — 획득이 **원자적 배타**인가. 앞 판은 `existsSync` 확인 → 임시 파일
 * `wx` → `renameSync` 였고, `rename` 은 있는 락을 말없이 덮어쓰므로 동시 시작 창에서 **둘 다**
 * 소유자가 됐다.
 *
 * 이 절은 **실제 파일시스템**(버릴 임시 디렉터리)에서 잰다 — 배타는 커널이 주는 성질이라
 * 모의 fs 로는 그것을 재지 못한다. docker 는 부르지 않는다(수거를 주입으로 센다).
 */
describe('단일 실행 락의 원자적 배타 획득', () => {
  const CONTAINER = 'bidvector-7e1-journey-0b2b919e';
  let dir;
  let lockPath;

  beforeEach(() => {
    dir = mkdtempSync(resolve(tmpdir(), 'bidvector-7e1-lock-test-'));
    lockPath = resolve(dir, 'journey.lock');
  });

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true });
  });

  const acquire = (overrides = {}) =>
    acquireSingleRunLock({
      lockPath,
      pid: process.pid,
      startToken: processStartToken(process.pid),
      container: CONTAINER,
      ...overrides,
    });

  it('빈 자리에서는 얻고, 표지를 그 자리에 적는다', () => {
    const reap = vi.fn(() => []);

    expect(acquire({ reap }).attempt).toBe(1);

    expect(parseLockMarker(readFileSync(lockPath, 'utf8'))).toStrictEqual({
      pid: process.pid,
      startToken: processStartToken(process.pid),
      container: CONTAINER,
    });
    expect(reap, '빈 자리에서는 아무것도 걷지 않는다').not.toHaveBeenCalled();
  });

  it('**둘째 획득은 실패한다** — 소유자가 살아 있다(이 프로세스다)', () => {
    const reap = vi.fn(() => []);
    acquire({ reap });

    expect(() => acquire({ reap })).toThrow(/앞 실행이 살아 있다/u);
    expect(reap, '살아 있는 소유자의 컨테이너를 걷지 않는다').not.toHaveBeenCalled();
    // 첫 소유자의 표지가 그대로다 — 덮어쓰지 않았다.
    expect(parseLockMarker(readFileSync(lockPath, 'utf8'))?.pid).toBe(process.pid);
  });

  it('죽은 소유자의 락은 그 컨테이너를 걷고 이어받는다', () => {
    const dead = { pid: 2_147_480_000, startToken: '1', container: 'bidvector-7e1-journey-deadbeef' };
    writeFileSync(lockPath, formatLockMarker(dead), { mode: 0o600 });
    const reap = vi.fn(() => ['c0ffee123456']);

    expect(acquire({ reap }).attempt).toBe(2);

    expect(reap).toHaveBeenCalledTimes(1);
    expect(reap.mock.calls[0]?.[0], '걷는 대상은 **그 표지의** 컨테이너다').toBe(dead.container);
    expect(parseLockMarker(readFileSync(lockPath, 'utf8'))?.container).toBe(CONTAINER);
  });

  it('읽을 수 없는 표지는 멈춘다 — 소유자를 모르면 넘겨받지 않는다', () => {
    writeFileSync(lockPath, 'bidvector-7e1-journey-0b2b919e\n', { mode: 0o600 });
    const reap = vi.fn(() => []);

    expect(() => acquire({ reap })).toThrow(/락 표지를 읽을 수 없다/u);
    expect(reap).not.toHaveBeenCalled();
    expect(existsSync(lockPath), '그 파일을 지우지 않는다').toBe(true);
  });

  /** v r4 L-2 — 「생존 판정을 **부르는지**」를 잠근다. 앞 판은 그 블록을 지워도 초록이었다. */
  it('획득은 생존 판정을 거친다 — 그 블록을 지우면 이 test 가 붉다', () => {
    const dead = { pid: 2_147_480_000, startToken: '1', container: 'bidvector-7e1-journey-deadbeef' };
    writeFileSync(lockPath, formatLockMarker(dead), { mode: 0o600 });
    const alive = vi.fn(() => false);

    acquire({ alive, reap: () => [] });

    expect(alive, '표지를 읽었으면 생존을 물어야 한다').toHaveBeenCalledTimes(1);
    expect(alive.mock.calls[0]?.[0]).toStrictEqual(dead);
  });

  it('치운 자리를 남이 또 잡으면 두 번만 시도하고 사유를 말한다', () => {
    const dead = { pid: 2_147_480_000, startToken: '1', container: 'bidvector-7e1-journey-deadbeef' };
    writeFileSync(lockPath, formatLockMarker(dead), { mode: 0o600 });
    // `rmSync` 를 무력화해 「치웠는데 자리가 그대로」인 경합을 흉내 낸다.
    const fs = {
      writeFileSync: () => undefined,
      linkSync: () => {
        throw Object.assign(new Error('file already exists'), { code: 'EEXIST' });
      },
      readFileSync: () => formatLockMarker(dead),
      rmSync: () => undefined,
    };

    expect(() => acquire({ fs, alive: () => false, reap: () => [] })).toThrow(/락을 얻지 못했다/u);
  });
});

/**
 * v r5 L-1 · CR r5 L-1 — 획득의 **남은 두 경합**. 하나는 수거가 도는 사이에 남이 같은 묵은
 * 락을 이어받는 것이고(그때 지우면 살아 있는 남의 락을 지운다), 하나는 `EEXIST` 와 읽기 사이에
 * 앞 실행이 정상 종료하는 것이다(정상 경합인데 `ENOENT` 로 죽었다).
 *
 * 주입으로만 잰다 — docker 를 부르지 않는다.
 */
describe('묵은 락을 잇는 길의 경합', () => {
  const DEAD = { pid: 2_147_480_000, startToken: '1', container: 'bidvector-7e1-journey-deadbeef' };
  const OTHER = { pid: 2_147_480_001, startToken: '2', container: 'bidvector-7e1-journey-c0ffee11' };
  const MINE = { pid: 4321, startToken: '777', container: 'bidvector-7e1-journey-0b2b919e' };

  /** 호출 순서대로 답을 돌려주는 fs — `linkSync` 는 지정한 횟수만 `EEXIST` 를 던진다. */
  const fakeFs = ({ slots, linkFailures = 1 }) => {
    const removed = [];
    let reads = 0;
    let links = 0;
    return {
      removed,
      readsDone: () => reads,
      fs: {
        writeFileSync: () => undefined,
        linkSync: () => {
          links += 1;
          if (links <= linkFailures) {
            throw Object.assign(new Error('file already exists'), { code: 'EEXIST' });
          }
        },
        readFileSync: () => {
          const slot = slots[Math.min(reads, slots.length - 1)];
          reads += 1;
          if (slot === 'gone') {
            throw Object.assign(new Error('no such file'), { code: 'ENOENT' });
          }
          return typeof slot === 'string' ? slot : formatLockMarker(slot);
        },
        rmSync: (path) => {
          removed.push(path);
        },
      },
    };
  };

  const acquire = (fs, overrides = {}) =>
    acquireSingleRunLock({
      lockPath: '/tmp/x.lock',
      pid: MINE.pid,
      startToken: MINE.startToken,
      container: MINE.container,
      fs,
      alive: () => false,
      reap: () => [],
      ...overrides,
    });

  it('수거 사이에 남이 이어받았으면 **그 락을 지우지 않고** 멈춘다', () => {
    const harness = fakeFs({ slots: [DEAD, OTHER], linkFailures: 2 });

    expect(() => acquire(harness.fs)).toThrow(/묵은 락이 그 사이 바뀌었다/u);
    expect(harness.removed.filter((one) => one === '/tmp/x.lock'), '남의 락을 지우지 않는다').toStrictEqual(
      [],
    );
  });

  it('내용이 그대로면 치우고 이어받는다', () => {
    const harness = fakeFs({ slots: [DEAD, DEAD], linkFailures: 1 });
    const reap = vi.fn(() => ['c0ffee123456']);

    expect(acquire(harness.fs, { reap }).attempt).toBe(2);

    expect(reap.mock.calls[0]?.[0]).toBe(DEAD.container);
    expect(harness.removed).toContain('/tmp/x.lock');
  });

  it('EEXIST 와 읽기 사이에 락이 사라지면 **그대로 재시도한다** — ENOENT 로 죽지 않는다', () => {
    const harness = fakeFs({ slots: ['gone'], linkFailures: 1 });
    const reap = vi.fn(() => []);

    expect(acquire(harness.fs, { reap }).attempt).toBe(2);

    expect(reap, '비어 버린 자리에서는 아무것도 걷지 않는다').not.toHaveBeenCalled();
  });

  it('표지를 끝까지 못 읽으면 멈춘다 — 그 자리를 지우지 않는다', () => {
    const harness = fakeFs({ slots: ['찌꺼기'], linkFailures: 1 });

    expect(() => acquire(harness.fs)).toThrow(/락 표지를 읽을 수 없다/u);
    expect(harness.removed.filter((one) => one === '/tmp/x.lock')).toStrictEqual([]);
  });

  it('살아 있는 소유자면 읽기 한 번으로 멈춘다 — 수거도 지우기도 없다', () => {
    const harness = fakeFs({ slots: [OTHER], linkFailures: 1 });
    const reap = vi.fn(() => []);

    expect(() => acquire(harness.fs, { alive: () => true, reap })).toThrow(/앞 실행이 살아 있다/u);
    expect(reap).not.toHaveBeenCalled();
    expect(harness.removed.filter((one) => one === '/tmp/x.lock')).toStrictEqual([]);
  });
});
