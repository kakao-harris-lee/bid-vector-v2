import { describe, expect, it } from 'vitest';

import { countOutcomes, verdictOf } from './journey-results.mjs';

/** Playwright JSON 리포터의 모양 — 중첩 suite 안에 spec, spec 안에 test(결말). */
function report(specs) {
  return {
    suites: [
      {
        title: 'chromium',
        suites: [{ title: 'results.spec.ts', specs }],
      },
    ],
  };
}

const spec = (title, status) => ({ title, tests: [{ status }] });

describe('여정 레인 판정 — 건너뛴 test 0', () => {
  it('전부 통과면 ok 다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'expected'), spec('알림함', 'expected')]));

    expect(verdict.ok).toBe(true);
    expect(verdict.counts.expected).toBe(2);
  });

  /**
   * **이 게이트가 막으려는 바로 그 꼴**(r1 H-1): 여정 하나가 건너뛰었는데 Playwright 는 exit 0
   * 이다. verifier 가 오염 spec 으로 재현한 `1 skipped · 2 passed` 가 이 모양이다.
   */
  it('건너뛴 test 가 하나라도 있으면 ok 가 아니고 제목을 말한다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'skipped'), spec('알림함', 'expected')]));

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join(' ')).toContain('skipped 1건');
    expect(verdict.problems.join(' '), '어느 test 가 건너뛰었는지 말한다').toContain('여정 ⑤');
  });

  it('중단된 test 도 「돌지 않았다」로 센다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'interrupted')]));

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join(' ')).toContain('interrupted 1건');
  });

  /**
   * **수집 0 이 통과가 되면 이 게이트가 공허하다** — 단언이 사라진 꼴이 「아무것도 안 돌았다」
   * 라는 다른 문으로 들어온다(`testMatch` 오타 · 경로 필터 하나면 된다).
   */
  it('돈 test 가 0 이면 ok 가 아니다', () => {
    expect(verdictOf(report([])).ok).toBe(false);
    expect(verdictOf(report([])).problems.join(' ')).toContain('돈 test 가 0');
  });

  it('실패가 있어도 건너뜀만 없으면 이 축은 ok 다 — Playwright 가 이미 붉다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'unexpected')]));

    expect(verdict.ok).toBe(true);
    expect(verdict.counts.unexpected).toBe(1);
  });

  /**
   * 맨 위 `stats` 를 읽지 않는 이유를 잠근다 — 리포터가 그 칸을 빼도 **test 의 결말**은 명세가
   * 보장한다. `stats` 에 기대면 칸이 사라진 날 `undefined` 를 0 으로 읽어 조용히 통과한다.
   */
  it('맨 위 stats 가 거짓말을 해도 test 의 결말로 판정한다', () => {
    const faked = { ...report([spec('여정 ⑤', 'skipped')]), stats: { skipped: 0, expected: 99 } };

    expect(verdictOf(faked).ok).toBe(false);
  });

  it('모르는 상태는 조용히 통과로 세지 않는다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'timedOut')]));

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join(' ')).toContain('상태를 모르는 test');
  });

  it('중첩 suite 를 끝까지 훑는다 — 깊은 자리의 건너뜀도 센다', () => {
    const nested = {
      suites: [
        {
          title: 'chromium',
          suites: [{ title: 'a', suites: [{ title: 'b', specs: [spec('깊은 것', 'skipped')] }] }],
        },
      ],
    };

    expect(countOutcomes(nested).counts.skipped).toBe(1);
    expect(verdictOf(nested).ok).toBe(false);
  });

  it('빈 리포트도 터지지 않고 「돈 test 0」으로 멈춘다', () => {
    expect(verdictOf({}).ok).toBe(false);
  });
});
