// 여정 레인(W-13)의 **판정 술어** — 「건너뛴 test 0」과 「돈 test 가 있다」(M7/7E-3a r1 H-1).
//
// **왜 vitest 쪽 게이트로는 안 되는가**: `scripts/check-gate-tests.mjs` 의 「skipped/todo 0」은
// `npm run test`(vitest)에 JSON reporter 를 붙여 보는 것이라 **vitest 등록부만** 덮는다.
// Playwright 는 건너뛴 test 가 있어도 exit 0 이므로 여정 레인은 그 등식 밖이었고, 여정 하나가
// 통째로 건너뛰어도 CI 가 초록이었다(verifier 재현: 오염 spec 을 앞에 두면 `1 skipped · 2 passed`).
//
// **술어를 문자열이 아니라 리포터 산출물의 구조로 둔다** — spec 을 `grep` 해 `test.skip` 을 찾는
// 꼴은 `test.fixme` · `test.describe.skip` · 조건부 skip · 런타임 `test.skip()` 어느 하나로도
// 열린다. 여기서 보는 것은 **그 실행이 실제로 무엇을 돌렸는가**다.

/** 리포터가 세는 상태 중 「돌지 않았다」에 해당하는 것들. */
const NOT_RUN_STATUSES = ['skipped', 'interrupted'];

/**
 * Playwright JSON 리포터의 `suites` 를 훑어 **test 하나하나의 결말**을 센다.
 *
 * 맨 위의 `stats` 를 읽지 않는 이유: 그 블록은 리포터 판에 따라 칸이 달라지고, `skipped` 가
 * 없으면 `undefined` 를 0 으로 읽게 되어 **게이트가 조용히 공허**해진다. 명세가 보장하는 것은
 * 각 test 의 `status` 이므로 그것을 센다.
 */
export function countOutcomes(report) {
  const counts = { expected: 0, unexpected: 0, flaky: 0, skipped: 0, interrupted: 0, unknown: 0 };
  const titles = { skipped: [], interrupted: [] };

  const visitSpec = (spec, trail) => {
    for (const test of spec.tests ?? []) {
      const status = typeof test.status === 'string' ? test.status : 'unknown';
      if (status in counts) {
        counts[status] += 1;
      } else {
        counts.unknown += 1;
      }
      if (NOT_RUN_STATUSES.includes(status)) {
        titles[status].push([...trail, spec.title].filter(Boolean).join(' › '));
      }
    }
  };

  const visitSuite = (suite, trail) => {
    const here = [...trail, suite.title].filter(Boolean);
    for (const spec of suite.specs ?? []) {
      visitSpec(spec, here);
    }
    for (const child of suite.suites ?? []) {
      visitSuite(child, here);
    }
  };

  for (const suite of report.suites ?? []) {
    visitSuite(suite, []);
  }
  return { counts, titles };
}

/**
 * 판정 — **두 축**이다.
 *
 * ⓐ 건너뛴·중단된 test 0. ⓑ **돈 test 가 하나라도 있다** — 수집이 0 이면 「전부 통과」와
 * 「아무것도 안 돌았다」가 같은 exit 0 이 되고, 그러면 이 게이트가 막으려던 바로 그 꼴(단언이
 * 사라졌는데 초록)이 다른 문으로 들어온다.
 */
export function verdictOf(report) {
  const { counts, titles } = countOutcomes(report);
  const problems = [];

  for (const status of NOT_RUN_STATUSES) {
    if (counts[status] > 0) {
      problems.push(
        `${status} ${counts[status]}건 — 여정은 건너뛰지 않는다(하네스는 실행마다 버릴 DB 다): ` +
          titles[status].join(' / '),
      );
    }
  }
  if (counts.unknown > 0) {
    problems.push(`상태를 모르는 test ${counts.unknown}건 — 리포터 모양이 바뀌었다`);
  }
  if (counts.expected + counts.unexpected + counts.flaky === 0) {
    problems.push('돈 test 가 0 이다 — 수집이 비면 「전부 통과」와 「아무것도 안 돌았다」가 같은 값이 된다');
  }

  return { ok: problems.length === 0, problems, counts };
}
