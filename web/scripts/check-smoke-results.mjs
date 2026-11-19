// 셸 스모크 레인(CI `web` job W-11)의 **판정** — 그 레인이 **무엇을 돌렸는지**를 리포터 산출물로
// 본다(M13/13C r2 · v R1-M5).
//
// `npm run test:e2e` 가 Playwright 뒤에 이것을 이어 돌린다. Playwright 가 붉으면 `&&` 가 끊어
// 여기 닿지 않고, 초록인데 **건너뛴 test 가 있거나 등록부와 집합이 다르면** 이 자리가 붉다.
//
// **술어는 여정 레인과 같은 모듈**(`journey-results.mjs`)에서 온다 — 사본을 두면 한쪽만 고쳐지고,
// 이 레인에서 그 사각(개명으로 사라지는 spec)이 실제로 열려 있었다.
import { existsSync, readFileSync } from 'node:fs';
import { parseSpecRegistry, verdictOf } from './journey-results.mjs';
import { REGISTRY_PATH as REGISTRY, REPORT_PATH as REPORT, SKIP_NOTE } from './smoke-lane.mjs';

/**
 * **인자를 받지 않는다**(여정 게이트와 같은 이유): `npm run test:e2e -- <인자>` 의 인자는 체인의
 * 마지막 명령인 **여기로** 온다 — Playwright 는 전건을 돌리는데 호출자는 일부만 돌렸다고 믿게
 * 된다. 부분 실행은 `npx playwright test <인자>` 로 한다(그 꼴은 애초에 이 게이트를 지나지 않는다).
 */
if (process.argv.length > 2) {
  process.stderr.write(
    `이 게이트는 인자를 받지 않는다: ${process.argv.slice(2).join(' ')}\n` +
      '  `npm run test:e2e -- <인자>` 의 인자는 Playwright 가 아니라 여기로 온다.\n' +
      '  부분 실행은 `npx playwright test <인자>` 로 하십시오.\n',
  );
  process.exit(1);
}

if (!existsSync(REPORT)) {
  // **없는 것은 통과가 아니다.** 리포터가 꺼지면(`--reporter=` 덮어쓰기 포함) 게이트가 조용히
  // 사라지므로 그 자체를 막는다. 실행 시작에 `smoke-run-begin.mjs` 가 지난 리포트를 지운다.
  process.stderr.write(
    `셸 스모크 결과 리포트가 없다: ${REPORT}\n` +
      '  json 리포터가 꺼졌거나 실행이 그 자리까지 가지 못했다.\n',
  );
  process.exit(1);
}

if (!existsSync(REGISTRY)) {
  process.stderr.write(`셸 스모크 spec 등록부가 없다: ${REGISTRY}\n`);
  process.exit(1);
}

let report;
try {
  report = JSON.parse(readFileSync(REPORT, 'utf8'));
} catch (failure) {
  process.stderr.write(`셸 스모크 결과 리포트를 읽지 못했다: ${failure.message}\n`);
  process.exit(1);
}

const registry = parseSpecRegistry(readFileSync(REGISTRY, 'utf8'));
const { ok, problems, counts, specFiles } = verdictOf(report, registry, SKIP_NOTE);
if (!ok) {
  process.stderr.write('셸 스모크 레인 판정이 깨졌다 (M13/13C r2):\n');
  for (const problem of problems) {
    process.stderr.write(`  - ${problem}\n`);
  }
  process.exit(1);
}

process.stdout.write(
  `셸 스모크 레인: 통과 ${counts.expected} · flaky ${counts.flaky} · 건너뜀 0 · 중단 0 · ` +
    `spec ${specFiles.size}/${registry.size}(등록부와 같다)\n`,
);
