// 판정 리포트의 **한 자리**. 시작 표지(지우는 쪽)와 게이트(읽는 쪽)가 같은 경로를 봐야 하고,
// 그 경로는 `playwright.journey.config.ts` 의 `outputFile` 과도 같아야 한다 — 어긋나면 지우지
// 않은 파일을 읽거나(낡은 리포트) 늘 없는 파일을 읽는다(늘 붉다).
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

/** `playwright.journey.config.ts` 의 `outputFile` 과 **같은 자리**여야 한다. */
export const REPORT_RELATIVE = 'test-results/journey-results.json';

export const REPORT_PATH = resolve(here, '..', REPORT_RELATIVE);
