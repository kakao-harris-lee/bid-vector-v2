import { describe, expect, it, vi } from 'vitest';

import { isOwnContainerMarker, reapStaleContainer } from './container-marker.mjs';

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
