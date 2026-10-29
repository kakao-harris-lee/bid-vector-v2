import { describe, expect, it, vi } from 'vitest';

import {
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
