// W-13 의 판정 — 여정 레인이 **무엇을 돌렸는지**를 리포터 산출물로 본다(M7/7E-3a r1 H-1).
//
// `npm run test:e2e:journey` 가 Playwright 뒤에 이것을 이어 돌린다. Playwright 가 붉으면 `&&`
// 가 끊어 여기 닿지 않고, 초록인데 **건너뛴 test 가 있으면** 이 자리가 붉다.
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { REPORT_PATH as REPORT } from './journey-report-path.mjs';
import { parseSpecRegistry, verdictOf } from './journey-results.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const REGISTRY = resolve(here, '..', 'config', 'journey-specs.txt');

/**
 * **인자를 받지 않는다**(r2 v L-1). `playwright … && node <이 파일>` 체인의 **마지막** 명령이
 * `npm run test:e2e:journey -- <인자>` 의 인자를 받는다 — Playwright 는 전건을 돌리는데
 * 호출자는 일부만 돌렸다고 믿게 된다. 그 꼴을 조용히 지나보내지 않는다.
 *
 * 부분 실행은 `npx playwright test --config=playwright.journey.config.ts <인자>` 로 한다.
 * (그래도 이 게이트를 지나려면 등록부 등식이 전건을 요구한다 — 여정 레인의 판정은 전건이다.)
 */
if (process.argv.length > 2) {
  process.stderr.write(
    `이 게이트는 인자를 받지 않는다: ${process.argv.slice(2).join(' ')}\n` +
      '  `npm run test:e2e:journey -- <인자>` 의 인자는 Playwright 가 아니라 여기로 온다.\n' +
      '  부분 실행은 `npx playwright test --config=playwright.journey.config.ts <인자>` 로 하십시오.\n',
  );
  process.exit(1);
}

if (!existsSync(REPORT)) {
  // **없는 것은 통과가 아니다.** 리포터가 꺼지면 이 게이트가 조용히 사라지므로 그 자체를 막는다.
  // 실행 시작에 `journey-run-begin.mjs` 가 지난 리포트를 지우므로, 여기 있는 파일은 **이 실행이
  // 낸 것**이다(r2 v L-3 — 낡은 리포트 방어를 게이트 자신이 진다).
  process.stderr.write(
    `여정 결과 리포트가 없다: ${REPORT}\n` +
      '  json 리포터가 꺼졌거나(--reporter= 덮어쓰기 포함) 실행이 그 자리까지 가지 못했다.\n',
  );
  process.exit(1);
}

if (!existsSync(REGISTRY)) {
  process.stderr.write(`여정 spec 등록부가 없다: ${REGISTRY}\n`);
  process.exit(1);
}

let report;
try {
  report = JSON.parse(readFileSync(REPORT, 'utf8'));
} catch (failure) {
  process.stderr.write(`여정 결과 리포트를 읽지 못했다: ${failure.message}\n`);
  process.exit(1);
}

const registry = parseSpecRegistry(readFileSync(REGISTRY, 'utf8'));
const { ok, problems, counts, specFiles } = verdictOf(report, registry);
if (!ok) {
  process.stderr.write('여정 레인 판정이 깨졌다 (M7/7E-3a r1 H-1):\n');
  for (const problem of problems) {
    process.stderr.write(`  - ${problem}\n`);
  }
  process.exit(1);
}

process.stdout.write(
  `여정 레인: 통과 ${counts.expected} · flaky ${counts.flaky} · 건너뜀 0 · 중단 0 · ` +
    `spec ${specFiles.size}/${registry.size}(등록부와 같다)\n`,
);
