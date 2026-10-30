// 여정 레인(W-13)의 **판정 술어**(M7/7E-3a r1 H-1 · r2 에서 축 둘을 더했다).
//
// **왜 vitest 쪽 게이트로는 안 되는가**: `scripts/check-gate-tests.mjs` 의 「skipped/todo 0」은
// `npm run test`(vitest)에 JSON reporter 를 붙여 보는 것이라 **vitest 등록부만** 덮는다.
// Playwright 는 건너뛴 test 가 있어도 exit 0 이므로 여정 레인은 그 등식 밖이었고, 여정 하나가
// 통째로 건너뛰어도 CI 가 초록이었다(verifier 재현: 오염 spec 을 앞에 두면 `1 skipped · 2 passed`).
//
// **술어를 문자열이 아니라 리포터 산출물의 구조로 둔다** — spec 을 `grep` 해 `test.skip` 을 찾는
// 꼴은 `test.fixme` · `test.describe.skip` · 조건부 skip · 런타임 `test.skip()` 어느 하나로도
// 열린다. 여기서 보는 것은 **그 실행이 실제로 무엇을 돌렸는가**다.

/**
 * 「돌지 않았다」에 해당하는 결말.
 *
 * **`interrupted` 는 오늘의 리포터가 내지 않는 값이다**(r2 CR L-5 실측 — 1.64.1 의
 * `computeTestCaseOutcome` 은 `skipped|expected|unexpected|flaky` 넷만 돌려주고 중단된 실행은
 * `skipped` 로 접힌다). 지우지 않고 두는 이유는 **방어**다: 리포터가 그 값을 내게 되는 판에서도
 * 이 축이 함께 선다. 실제 중단을 오늘 잡는 자리는 `skipped` 쪽이다.
 */
const NOT_RUN_STATUSES = ['skipped', 'interrupted'];

/** test 가 **통과하기로 선언된** 상태. 그 밖은 `test.fail`·`fixme`·`skip` 이 바꾼 것이다. */
const EXPECTED_TO_PASS = 'passed';

/**
 * Playwright JSON 리포터의 `suites` 를 훑어 **test 하나하나의 결말**과 **spec 파일 집합**을 모은다.
 *
 * 맨 위의 `stats` 를 읽지 않는 이유: 그 블록은 리포터 판에 따라 칸이 달라지고, `skipped` 가
 * 없으면 `undefined` 를 0 으로 읽게 되어 **게이트가 조용히 공허**해진다. 명세가 보장하는 것은
 * 각 test 의 `status`·`expectedStatus` 이므로 그것을 센다.
 */
export function countOutcomes(report) {
  const counts = { expected: 0, unexpected: 0, flaky: 0, skipped: 0, interrupted: 0, unknown: 0 };
  const titles = { skipped: [], interrupted: [] };
  /** `test.fail`·`fixme` 처럼 **통과 아닌 결말을 선언한** test 들. */
  const notExpectedToPass = [];
  const specFiles = new Set();

  const visitSpec = (spec, trail, fallbackFile) => {
    const file = typeof spec.file === 'string' ? spec.file : fallbackFile;
    if (typeof file === 'string' && file.length > 0) {
      specFiles.add(file);
    }
    for (const test of spec.tests ?? []) {
      const status = typeof test.status === 'string' ? test.status : 'unknown';
      if (status in counts) {
        counts[status] += 1;
      } else {
        counts.unknown += 1;
      }
      const where = [...trail, spec.title].filter(Boolean).join(' › ');
      if (NOT_RUN_STATUSES.includes(status)) {
        titles[status].push(where);
      }
      // **`test.fail()` 은 실패해도 `status: "expected"` 다**(r2 v M-1) — 결말만 보면 통과로
      // 세어진다. 선언된 기대(`expectedStatus`)를 함께 봐야 그 꼴이 드러난다. 조건부
      // `test.fail(cond)` 은 회귀가 생겼을 때만 발화해 단언을 뒤집은 채 초록으로 끝난다.
      if (test.expectedStatus !== EXPECTED_TO_PASS) {
        notExpectedToPass.push(`${where} (expectedStatus: ${String(test.expectedStatus)})`);
      }
    }
  };

  const visitSuite = (suite, trail, fallbackFile) => {
    const here = [...trail, suite.title].filter(Boolean);
    const file = typeof suite.file === 'string' ? suite.file : fallbackFile;
    for (const spec of suite.specs ?? []) {
      visitSpec(spec, here, file);
    }
    for (const child of suite.suites ?? []) {
      visitSuite(child, here, file);
    }
  };

  for (const suite of report.suites ?? []) {
    visitSuite(suite, [], undefined);
  }
  return { counts, titles, notExpectedToPass, specFiles };
}

/** 등록부 본문 → spec 경로 집합. `#` 주석과 빈 줄은 무시한다(vitest 등록부와 같은 꼴). */
export function parseSpecRegistry(text) {
  return new Set(
    text
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0 && !line.startsWith('#')),
  );
}

/**
 * 판정 — **네 축**이다.
 *
 * ⓐ 건너뛴·중단된 test 0. ⓑ 모든 test 가 **통과하기로 선언**됐다(`test.fail` 차단 — r2 v M-1).
 * ⓒ **돈 test 가 하나라도 있다** — 수집이 0 이면 「전부 통과」와 「아무것도 안 돌았다」가 같은
 * exit 0 이 되고, 그러면 이 게이트가 막으려던 바로 그 꼴이 다른 문으로 들어온다. ⓓ **spec 파일
 * 집합 == 등록부**(r2 CR M-2) — ⓒ 만으로는 다섯 중 넷이 사라져도 하나만 돌면 초록이고, 사라지는
 * 길은 삭제만이 아니라 `testMatch` 밖으로의 개명 하나면 된다.
 */
export function verdictOf(report, registry) {
  const { counts, titles, notExpectedToPass, specFiles } = countOutcomes(report);
  const problems = [];

  for (const status of NOT_RUN_STATUSES) {
    if (counts[status] > 0) {
      problems.push(
        `${status} ${counts[status]}건 — 여정은 건너뛰지 않는다(하네스는 실행마다 버릴 DB 다): ` +
          titles[status].join(' / '),
      );
    }
  }
  if (notExpectedToPass.length > 0) {
    problems.push(
      `통과하기로 선언되지 않은 test ${notExpectedToPass.length}건 — ` +
        '`test.fail`·`fixme` 는 단언을 뒤집은 채 초록으로 끝난다: ' +
        notExpectedToPass.join(' / '),
    );
  }
  if (counts.unknown > 0) {
    problems.push(`상태를 모르는 test ${counts.unknown}건 — 리포터 모양이 바뀌었다`);
  }
  if (counts.expected + counts.unexpected + counts.flaky === 0) {
    problems.push('돈 test 가 0 이다 — 수집이 비면 「전부 통과」와 「아무것도 안 돌았다」가 같은 값이 된다');
  }

  if (registry !== undefined) {
    const missing = [...registry].filter((one) => !specFiles.has(one)).sort();
    const extra = [...specFiles].filter((one) => !registry.has(one)).sort();
    if (missing.length > 0) {
      problems.push(`등록부의 spec 이 돌지 않았다(삭제·개명·부분 실행): ${missing.join(', ')}`);
    }
    if (extra.length > 0) {
      problems.push(`등록부에 없는 spec 이 돌았다 — 등록부를 함께 고쳐라: ${extra.join(', ')}`);
    }
  }

  return { ok: problems.length === 0, problems, counts, specFiles };
}
