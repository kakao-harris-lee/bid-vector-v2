// 셸 스모크 레인의 **상수 한 자리**(M13/13C r2 · v R1-M5) — 판정 리포트 경로 · 등록부 경로 ·
// 사유 문면.
//
// CLI(`check-smoke-results.mjs`)와 **따로** 둔다: CLI 를 들여오면 그 파일의 종말 판정이 돌아
// 짝 test 가 프로세스와 함께 죽는다(실측).
//
// 시작 표지(지우는 쪽) · 설정의 `outputFile`(쓰는 쪽) · 게이트(읽는 쪽) 셋이 같은 경로를 봐야
// 한다 — 어긋나면 지우지 않은 파일을 읽거나(낡은 리포트) 늘 없는 파일을 읽는다(늘 붉다).
//
// **여정 레인과 다른 파일 이름이다**: 두 레인이 같은 `test-results/` 를 쓰므로 한 이름을 나눠
// 쓰면 뒤에 도는 레인이 앞 레인의 판정을 지우거나 덮는다.
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

/** `playwright.config.ts` 의 `outputFile` 과 **같은 자리**여야 한다. */
export const REPORT_RELATIVE = 'test-results/smoke-results.json';

export const REPORT_PATH = resolve(here, '..', REPORT_RELATIVE);

/** 돌려야 하는 spec 집합의 정본. */
export const REGISTRY_PATH = resolve(here, '..', 'config', 'smoke-specs.txt');

/** 이 레인이 건너뛰지 않는 이유 — 합성 페이지와 빌드된 번들뿐이라 환경 조건이 없다. */
export const SKIP_NOTE = '셸 스모크는 건너뛰지 않는다(합성 페이지와 빌드된 번들뿐이라 환경 조건이 없다)';
