import { defineConfig } from '@playwright/test';

/**
 * **하위 디렉터리에 둔 config**(M12/gates-web-read-3 — ⓑ⑥, r1). 앞 판의 의무 술어는 **뿌리**의
 * `*.config.*` 만 셌고, 그래서 이 자리에 둔 config 로 뜬 spec 프로세스가 층 밖이었다(v H-1 실측
 * READ). 지금 층은 `.npmrc` 가 세우므로 **config 의 자리가 술어 밖**이고, 이 파일은 그 사실을
 * 재는 상설 대조다 — 층 test 가 이 config 로 spec 을 돌려 거부를 확인한다.
 *
 * 돌리는 spec 은 **스모크 레인의 그 파일**이다(새 spec 을 또 만들지 않는다).
 */
export default defineConfig({
  testDir: '..',
  testMatch: 'kotlin-read-guard.spec.ts',
  workers: 1,
  reporter: 'list',
});
