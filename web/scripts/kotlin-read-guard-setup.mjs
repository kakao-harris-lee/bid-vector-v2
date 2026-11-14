/**
 * **「여는 행위」 층을 세우는 유일한 자리**(M11/gates-web-read ⑤ · M12/gates-web-read-3 ⓑ⑤·ⓑ⑥).
 *
 * 층 자체는 `kotlin-read-guard.mjs` 에 있고 import 만으로는 서지 않는다. 세우는 자리를 이 한 파일로
 * 두는 이유는 **그 배선을 측정할 수 있게** 하는 것이고, 이제 그 배선이 셋이다.
 * - `vite.config.ts` 의 `test.setupFiles` — vitest **소비자 프로세스**(워커).
 * - `vite.config.ts` 의 적재 — **vite 메인 프로세스**(번들러 적재가 파일을 여는 자리, ⓑ⑤).
 * - 그 밖의 뿌리 config 적재와 `package.json` 의 `node --import` — playwright·eslint·게이트
 *   스크립트 프로세스(ⓑ⑥). 그 **의무의 모집단은 선언에서 뽑고**(추적된 뿌리 `*.config.*` 전수 ·
 *   `scripts` 의 `node` 명령 전수) `kotlin-read-guard.test.mjs` 가 센다.
 *
 * **자기를 자식 프로세스로 전파한다**(ⓑ⑥). 도구가 띄우는 프로세스(playwright 의 spec 워커 ·
 * `webServer`)에는 setup 을 꽂을 자리가 없으므로, 이 모듈이 `NODE_OPTIONS` 에 자기 `--import` 를
 * 실어 **그 도구가 만드는 node 프로세스**를 층 아래로 넣는다. 실측으로 가른 두 사실이 이 선택의
 * 근거다 — playwright spec 워커는 그렇게 **막히고**, `worker_threads` 는 `--import` 가 서지 않아
 * (기본 상속도 명시 `execArgv` 도) **막히지 않는다**(후자는 경계 안의 미달로 등재).
 *
 * `setupFiles` 를 **함께 둔다**: 두 기구가 덮는 집합이 다르다. vitest 의 워커 풀이 프로세스일 때는
 * 전파가 닿고 **스레드일 때는 닿지 않는다**(같은 실측의 다른 쪽). 어느 하나만 두면 풀 선택이
 * 층의 유무를 정하게 된다.
 */
import { installKotlinReadGuard } from './kotlin-read-guard.mjs';

installKotlinReadGuard();

/**
 * **경로에 공백이 있으면 전파하지 않는다.** `NODE_OPTIONS` 는 공백으로 토큰을 가르고 인용을
 * 지원하지 않으므로, 공백이 든 경로를 실으면 그 변수 전체가 깨져 **모든 node 자식이 뜨지 못한다**.
 * 건너뛰는 쪽을 고르고 그 사실을 여기 적는다 — 저장소를 공백 있는 경로에 두면 ⓑ⑥ 의 전파 축이
 * 조용히 사라진다(알려진 제한). 이미 실려 있으면 다시 싣지 않는다(중첩 호출의 누적을 막는다).
 */
const own = import.meta.url;
const current = process.env.NODE_OPTIONS ?? '';

if (!own.includes(' ') && !current.includes(own)) {
  process.env.NODE_OPTIONS = `${current} --import ${own}`.trim();
}
