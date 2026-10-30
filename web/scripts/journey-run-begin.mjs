// 여정 레인의 **실행 시작 표지**(M7/7E-3a r2 · v L-3 · CR L-5) — 지난 실행의 판정 리포트를
// 지운다.
//
// **왜 게이트 자신이 져야 하는가**: 오늘 낡은 리포트가 읽히지 않는 이유는 `outputFile` 이
// Playwright 가 실행 시작에 비우는 `test-results/` 안에 있기 때문이다. 그것은 **게이트 밖의
// 결합**이고, `outputDir` 나 `outputFile` 중 하나만 옮겨지면 지난 실행의 통과 JSON 이 읽힌다.
//
// 여기서 **먼저 지우면** 그 결합이 필요 없다: Playwright 가 리포터를 켜지 않았거나(`--reporter=`
// 덮어쓰기) 그 자리까지 가지 못하면 파일이 **없고**, 게이트의 「없는 것은 통과가 아니다」가 받는다.
import { existsSync, rmSync } from 'node:fs';

import { REPORT_PATH } from './journey-report-path.mjs';

if (existsSync(REPORT_PATH)) {
  rmSync(REPORT_PATH, { force: true });
}
process.stdout.write('여정 레인: 지난 판정 리포트를 지웠다 — 이 실행이 낸 것만 읽는다\n');
