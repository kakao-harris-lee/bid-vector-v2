#!/usr/bin/env node
// 게이트 test 가 **실제로 돌았는가**를 실행 결과로 단언한다(verifier r6 M-12 처방 ⓐ).
//
// 앞의 판은 `vite.config.ts` 의 AST 에서 `include` 리터럴을 찾는 **문자열 단언**이었다. 그 형태로는
// 수집 여부가 서지 않았다(실측) — `test.exclude` 에 `'scripts/**'` 한 줄 · 같은 리터럴을 담은 **미끼**
// 속성 · `describe.skip` · `package.json` 의 CLI 경로 필터(`vitest run src`)가 모두 그 단언을 지나면서
// 게이트 test 를 수집에서 뺐다. 지키려던 형태(「`include` 에서 빼면 조용히 초록」)의 거울상을 자기가
// 못 막은 것이다.
//
// 그래서 **W-6 가 실제로 돌리는 명령**(`npm run test`)에 JSON reporter 를 붙여 한 번 더 돌리고, 그
// 결과를 본다. 같은 npm 스크립트를 타므로 그 스크립트에 박힌 필터·인자가 **그대로 적용된다** — CLI
// 필터로 범위를 줄이면 이 게이트도 함께 줄어든 결과를 보고 멈춘다.
//
// 단언 대상 목록은 이 스크립트가 아니라 **등록부 한 자리**(`config/gate-tests.txt`)에서 끌어온다
// (저장소 선례 `config/quality/gate-tests.properties`).
//
// 남는 자리: 이 스크립트나 그 step 을 지우는 것, 등록부에서 줄을 빼는 것은 **리뷰 소관**이다
// (등재된 「게이트 스크립트 자체의 수정」 부류 — diff 에 남는다).
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, realpathSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = resolve(webRoot, 'config', 'gate-tests.txt');

const failures = [];
const workDir = mkdtempSync(join(tmpdir(), 'bidvector-gate-tests-'));
const reportPath = join(workDir, 'vitest.json');

function readRegistry() {
  if (!existsSync(registryPath)) {
    failures.push(`게이트 test 등록부가 없다: config/gate-tests.txt`);
    return [];
  }
  const entries = readFileSync(registryPath, 'utf8')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'));
  if (entries.length === 0) {
    failures.push('게이트 test 등록부가 비었다 — 전수 단언이 공허하게 통과한다');
  }
  return entries;
}

try {
  const registered = readRegistry();

  for (const relative of registered) {
    if (!existsSync(resolve(webRoot, relative))) {
      failures.push(`등록부의 게이트 test 파일이 없다: ${relative}`);
    }
  }

  // 실패해도(테스트가 깨져도) JSON 은 쓰이므로 종료 코드로 끊지 않고 결과를 읽는다.
  try {
    execFileSync('npm', ['run', 'test', '--', '--reporter=json', `--outputFile=${reportPath}`], {
      cwd: webRoot,
      stdio: ['ignore', 'ignore', 'inherit'],
    });
  } catch {
    /* 아래에서 JSON 으로 판정한다 */
  }

  if (!existsSync(reportPath)) {
    failures.push('W-6 의 JSON 결과가 만들어지지 않았다 — 수집 결과를 볼 수 없다');
  } else {
    let report;
    try {
      report = JSON.parse(readFileSync(reportPath, 'utf8'));
    } catch (caught) {
      failures.push(`W-6 의 JSON 결과를 읽지 못했다: ${caught.message}`);
    }
    const files = Array.isArray(report?.testResults) ? report.testResults : [];
    for (const relative of registered) {
      // 식별은 **절대 경로 등식**이다(verifier r7 M-13). 접미 일치로 찾던 동안, 실제 파일을
      // `exclude` 로 빼고 **같은 접미의 다른 파일**(`scripts/x/scripts/<같은 이름>` ·
      // `scripts/xscripts/<같은 이름>`)을 두면 그 다른 파일의 결과가 「등록부의 게이트 test」로
      // 읽혔다 — r6 M-12 가 닫은 `exclude` 형태가 파일 하나로 되살아났다(실측).
      let expected;
      try {
        expected = realpathSync(resolve(webRoot, relative));
      } catch {
        // 등록부의 파일이 디스크에 없다 — 위의 존재 검사가 이미 사유를 적었다.
        continue;
      }
      const entry = files.find((file) => {
        if (typeof file.name !== 'string') {
          return false;
        }
        try {
          return realpathSync(resolve(webRoot, file.name)) === expected;
        } catch {
          return false;
        }
      });
      if (entry === undefined) {
        failures.push(
          `게이트 test 가 W-6 의 수집에 없다: ${relative} — 수집에서 빠진 test 는 아무것도 막지 못한다`,
        );
        continue;
      }
      const statuses = (entry.assertionResults ?? []).map((assertion) => assertion.status);
      const passed = statuses.filter((status) => status === 'passed').length;
      const failed = statuses.filter((status) => status === 'failed').length;
      const inactive = statuses.filter(
        (status) => status === 'pending' || status === 'skipped' || status === 'todo',
      ).length;
      if (passed < 1) {
        failures.push(`게이트 test 에 통과한 단언이 없다: ${relative}`);
      }
      if (failed > 0) {
        failures.push(`게이트 test 가 실패했다: ${relative} (failed ${failed})`);
      }
      if (inactive > 0) {
        failures.push(
          `게이트 test 에 꺼진 단언이 있다: ${relative} (skipped/todo ${inactive}) — 꺼진 단언은 아무것도 막지 못한다`,
        );
      }
    }
  }
} finally {
  rmSync(workDir, { recursive: true, force: true });
}

if (failures.length > 0) {
  console.error('게이트 test 가 실제로 돌지 않았다 (verifier r6 M-12):');
  for (const line of failures) {
    console.error(`  - ${line}`);
  }
  console.error('  고치는 법: vitest 수집 범위(include/exclude·CLI 인자)와 등록부를 되살린다.');
  process.exitCode = 1;
} else {
  console.log(
    '게이트 test 가 W-6 의 수집에 있고 전부 통과했다(등록부 기준 · skipped/todo 0 · failed 0).',
  );
}
