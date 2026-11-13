/**
 * vitest 의 **setup 진입** — 「여는 행위」 층을 세우는 **유일한 자리**다(M11/gates-web-read ⑤).
 *
 * 층 자체는 `kotlin-read-guard.mjs` 에 있고 import 만으로는 서지 않는다. 세우는 자리를 이 한
 * 파일로 두는 이유는 **그 배선을 측정할 수 있게** 하는 것이다 — `vite.config.ts` 의 `setupFiles`
 * 에서 이 줄을 지우면 `kotlin-read-guard.test.mjs` 의 양성 묶음이 RED 다.
 */
import { installKotlinReadGuard } from './kotlin-read-guard.mjs';

installKotlinReadGuard();
