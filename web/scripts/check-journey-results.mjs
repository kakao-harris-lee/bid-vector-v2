// W-13 의 판정 — 여정 레인이 **무엇을 돌렸는지**를 리포터 산출물로 본다(M7/7E-3a r1 H-1).
//
// `npm run test:e2e:journey` 가 Playwright 뒤에 이것을 이어 돌린다. Playwright 가 붉으면 `&&`
// 가 끊어 여기 닿지 않고, 초록인데 **건너뛴 test 가 있으면** 이 자리가 붉다.
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { verdictOf } from './journey-results.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const REPORT = resolve(here, '..', 'test-results', 'journey-results.json');

if (!existsSync(REPORT)) {
  // **없는 것은 통과가 아니다.** 리포터가 꺼지면 이 게이트가 조용히 사라지므로 그 자체를 막는다.
  process.stderr.write(
    `여정 결과 리포트가 없다: ${REPORT}\n` +
      '  playwright.journey.config.ts 의 json 리포터가 꺼졌거나 실행이 그 자리까지 가지 못했다.\n',
  );
  process.exit(1);
}

let report;
try {
  report = JSON.parse(readFileSync(REPORT, 'utf8'));
} catch (failure) {
  process.stderr.write(`여정 결과 리포트를 읽지 못했다: ${failure.message}\n`);
  process.exit(1);
}

const { ok, problems, counts } = verdictOf(report);
if (!ok) {
  process.stderr.write('여정 레인 판정이 깨졌다 (M7/7E-3a r1 H-1):\n');
  for (const problem of problems) {
    process.stderr.write(`  - ${problem}\n`);
  }
  process.exit(1);
}

process.stdout.write(
  `여정 레인: 통과 ${counts.expected} · flaky ${counts.flaky} · 건너뜀 0 · 중단 0\n`,
);
