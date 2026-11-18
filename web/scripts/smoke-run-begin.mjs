// 셸 스모크 레인의 **실행 시작 표지**(M13/13C r2) — 지난 실행의 판정 리포트를 지운다.
//
// 여정 레인이 같은 자리를 같은 이유로 둔다: 「없는 것은 통과가 아니다」가 받으려면, 여기 있는
// 파일이 **이 실행이 낸 것**이어야 한다. `test-results/` 를 Playwright 가 비우는 데 기대면
// `outputDir`·`outputFile` 중 하나만 옮겨져도 지난 실행의 통과 JSON 이 읽힌다.
import { existsSync, rmSync } from 'node:fs';

import { REPORT_PATH } from './smoke-lane.mjs';

if (existsSync(REPORT_PATH)) {
  rmSync(REPORT_PATH, { force: true });
}
process.stdout.write('셸 스모크: 지난 판정 리포트를 지웠다 — 이 실행이 낸 것만 읽는다\n');
