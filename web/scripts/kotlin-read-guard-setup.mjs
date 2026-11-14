/**
 * **「여는 행위」 층을 세우는 유일한 자리**(M11/gates-web-read ⑤ · M12/gates-web-read-3 ⓑ⑤·ⓑ⑥).
 *
 * 층 자체는 `kotlin-read-guard.mjs` 에 있고 import 만으로는 서지 않는다. 세우는 자리를 이 한 파일로
 * 두는 이유는 **그 배선을 측정할 수 있게** 하는 것이고, 배선은 **하나**다.
 * - **`web/.npmrc` 의 `node-options`** — npm 이 띄우는 **모든** 스크립트 프로세스와 그 자식. 명령을
 *   어떤 꼴로 적었든(환경변수 접두 · `;` 로 이은 두 번째 명령 · `env` 명령 · 하위 디렉터리 config)
 *   npm 이 `NODE_OPTIONS` 에 이것을 실으므로 **꼴이 술어 밖으로 나간다**.
 * 배선은 **그 하나뿐**이다. 앞 판은 `vite.config.ts` 의 `test.setupFiles` 를 「풀이 스레드일 때의
 * 보험」으로 함께 두었는데, 그 전제가 **틀렸다**(r2 실측) — `--import` 는 **파일 worker 에 선다**
 * (vitest 의 스레드 풀 워커가 그 꼴이다). 두 풀 모두에서 그 줄을 지워도 층 test 가 초록이라,
 * **측정되지 않는 배선**으로 남기지 않고 지웠다.
 *
 * **앞 판은 명령과 config 를 열거했다**(r1 에 버렸다 — v H-1). `package.json` 의 `node` 명령마다
 * 플래그를 붙이고 뿌리 `*.config.*` 마다 import 를 두었는데, 그 의무를 세는 술어가 **텍스트 꼴**이라
 * `NODE_ENV=test node …` 한 줄로 게이트는 초록인 채 원문이 읽혔다(실측). 지금은 선언 한 줄이 그
 * 전부를 대신하고, 술어는 **그 키가 있는가**와 **실제로 거부하는가**를 본다.
 *
 * **막지 못하는 것**: npm 밖에서 **직접** 띄운 node(사람이 셸에서 띄우는 프로세스·IDE). 의도적
 * 은닉이 아니라 **npm 이 선언을 실을 수 없는 자리**이므로 경계 문장으로 등재한다.
 */
import { installKotlinReadGuard } from './kotlin-read-guard.mjs';

installKotlinReadGuard();

/**
 * **자기를 자식 프로세스로 전파하되 지정자를 절대 URL 로 바로잡는다**(r1 — v L-1 의 정정).
 *
 * `.npmrc` 가 싣는 지정자는 **상대 경로**이고 node 는 그것을 **자기 cwd 기준**으로 푼다. 그래서 cwd
 * 가 다른 node 손자는 `ERR_MODULE_NOT_FOUND` 로 **뜨지도 못한다**(실측: `/tmp` 를 cwd 로 띄운 자식이
 * 죽었다). 자기 절대 URL 로 바꿔 실어 그 자리를 없앤다 — 변수를 덮어쓰지 않고 **자기 지정자만**
 * 골라 바꾼다(남이 실은 `--import` 는 그대로 둔다).
 *
 * 앞 판은 여기서 「경로에 공백이 있으면 전파하지 않는다」고 적었는데 **사실이 아니었다**(v L-1):
 * `import.meta.url` 은 파일 URL 이라 공백이 `%20` 으로 인코딩되어 그 조건은 언제나 거짓이고 분기가
 * **죽어 있었다**. 공백 있는 경로에서도 전파가 서는 것을 실측으로 확인하고 분기를 지웠다.
 *
 * 이름으로 자기 토큰을 고르는 것이 이 처방의 약한 고리다. 못 고르면 상대 지정자가 남아 **cwd 가
 * 다른 자식이 죽는** 쪽으로 실패한다 — 조용히 층이 사라지는 쪽이 아니다.
 */
const own = import.meta.url;
const selfName = 'kotlin-read-guard-setup.mjs';
const tokens = (process.env.NODE_OPTIONS ?? '').split(/\s+/u).filter((token) => token.length > 0);
const kept = [];

for (let index = 0; index < tokens.length; index += 1) {
  const token = tokens[index];
  if (token === '--import' && tokens[index + 1]?.endsWith(selfName)) {
    index += 1;
    continue;
  }
  if (!(token.startsWith('--import=') && token.endsWith(selfName))) {
    kept.push(token);
  }
}

kept.push('--import', own);
process.env.NODE_OPTIONS = kept.join(' ');
