import { describe, expect, it } from 'vitest';

import { countOutcomes, parseSpecRegistry, verdictOf } from './journey-results.mjs';

/** Playwright JSON 리포터의 모양 — 중첩 suite 안에 spec, spec 안에 test(결말). */
function report(specs) {
  return {
    suites: [
      {
        title: 'chromium',
        suites: [{ title: 'results.spec.ts', file: 'results.spec.ts', specs }],
      },
    ],
  };
}

/** 기본은 **통과하기로 선언된** test 다 — `expectedStatus` 를 주는 자리가 그 축을 잰다. */
const spec = (title, status, expectedStatus = 'passed') => ({
  title,
  file: 'results.spec.ts',
  tests: [{ status, expectedStatus }],
});

/** 등록부가 있는 판정 — 축 ⓓ 를 재는 test 가 쓴다. */
const REGISTRY = parseSpecRegistry('# 주석\nresults.spec.ts\n');

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

  /**
   * **오늘의 리포터는 이 값을 내지 않는다**(r2 CR L-5 실측 — 중단된 실행은 `skipped` 로 접힌다).
   * 축을 남겨 둔 것은 방어이고, 이 test 는 「그 값을 내는 판에서도 센다」를 잠근다.
   */
  it('중단된 test 도 「돌지 않았다」로 센다 — 리포터가 그 값을 내는 판에서도', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'interrupted', 'passed')]));

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

/**
 * **`test.fail()` 은 실패해도 `status: "expected"` 다**(r2 v M-1) — 결말만 보면 통과로 세어진다.
 * 조건부 `test.fail(cond)` 은 r1 의 `test.skip(cond)` 과 같은 구조로, 회귀가 생겼을 때만 발화해
 * 단언을 뒤집은 채 초록으로 끝난다.
 */
describe('통과하기로 선언되지 않은 test (r2 v M-1)', () => {
  it('`test.fail` 이 붙은 test 는 결말이 expected 여도 ok 가 아니다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'expected', 'failed')]));

    expect(verdict.ok, 'test.fail 이 게이트를 지났다').toBe(false);
    expect(verdict.problems.join(' ')).toContain('통과하기로 선언되지 않은 test 1건');
    expect(verdict.problems.join(' '), '어느 test 인지 말한다').toContain('여정 ⑤');
  });

  it('`skip`·`fixme` 도 같은 축이 함께 덮는다', () => {
    const verdict = verdictOf(report([spec('여정 ⑤', 'skipped', 'skipped')]));

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join(' ')).toContain('expectedStatus: skipped');
  });

  it('전부 통과하기로 선언됐으면 이 축은 ok 다', () => {
    expect(verdictOf(report([spec('여정 ⑤', 'expected')])).ok).toBe(true);
  });
});

/**
 * **spec 이 사라지는 길**(r2 CR M-2). 「돈 test ≥ 1」 축만으로는 다섯 중 넷이 사라져도 하나만
 * 돌면 초록이다 — 삭제만이 아니라 `testMatch` 밖으로의 개명 하나면 된다.
 */
describe('여정 spec 등록부 등식 (r2 CR M-2)', () => {
  it('등록부와 같은 집합이면 ok 다', () => {
    expect(verdictOf(report([spec('여정 ⑤', 'expected')]), REGISTRY).ok).toBe(true);
  });

  it('등록부의 spec 이 돌지 않으면 ok 가 아니고 이름을 말한다', () => {
    const wider = parseSpecRegistry('results.spec.ts\nonboarding.spec.ts\n');
    const verdict = verdictOf(report([spec('여정 ⑤', 'expected')]), wider);

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join(' ')).toContain('onboarding.spec.ts');
  });

  it('등록부에 없는 spec 이 돌아도 ok 가 아니다 — 장부 없이 들어오지 않는다', () => {
    const narrower = parseSpecRegistry('onboarding.spec.ts\n');
    const verdict = verdictOf(report([spec('여정 ⑤', 'expected')]), narrower);

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join(' ')).toContain('등록부에 없는 spec');
  });

  it('spec 에 file 이 없으면 감싼 suite 의 file 로 센다', () => {
    const noFile = {
      suites: [
        { title: 'chromium', file: 'results.spec.ts', specs: [{ title: 'x', tests: [{ status: 'expected', expectedStatus: 'passed' }] }] },
      ],
    };

    expect(verdictOf(noFile, REGISTRY).ok).toBe(true);
  });

  it('등록부는 주석과 빈 줄을 무시한다', () => {
    expect([...parseSpecRegistry('# 머리\n\n a.spec.ts \n#b.spec.ts\n')]).toStrictEqual(['a.spec.ts']);
  });

  it('등록부를 주지 않으면 그 축은 서지 않는다 — 다른 축은 그대로다', () => {
    expect(verdictOf(report([spec('여정 ⑤', 'expected')])).ok).toBe(true);
    expect(verdictOf(report([spec('여정 ⑤', 'skipped', 'skipped')])).ok).toBe(false);
  });
});
