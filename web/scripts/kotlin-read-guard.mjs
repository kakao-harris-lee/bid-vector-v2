/**
 * **Kotlin 원문을 「여는 행위」를 재는 런타임 층**(M11/gates-web-read —
 * `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS` ⑤).
 *
 * 앞 판의 게이트는 **적힌 토큰**만 보았다. 그래서 세 축이 지나갔다: 추적되는 심볼릭 링크가 Kotlin
 * 원문을 가리키면 코드에 토큰이 남지 않고(⑧), 대소문자가 다르면 술어의 글자와 어긋나고(⑨),
 * 데이터 파일의 확장자 단독 값·별표 glob 은 경로 꼴이 아니었다(⑩). 세 축은 **표기**의 문제이고,
 * 표기를 더 열거하는 길은 끝나지 않는다(M10/10D 가 r0~r7 동안 그 자리였다).
 *
 * 그래서 이 층은 **표기를 보지 않는다**. 열리는 경로를 **해석된 실경로**로 바꿔 Kotlin 확장자인지를
 * 대소문자 무시로 보고, 그 파일을 연 **호출자가 소유 모듈이 아니면 던진다**. 경로를 어떻게 적었는지,
 * 어느 데이터 파일에서 얻었는지, 링크를 몇 번 거쳤는지가 술어 밖으로 나간다.
 *
 * **감싸는 범위는 `fs` 표면 전수**다 — 읽기 함수를 **목록으로** 고르지 않는다. 읽기/쓰기를 가르는
 * 순간 그 분류가 목록이 되고, 목록 밖이 다음 라운드의 우회가 된다. 전수를 감싸는 비용은 쟀다
 * (전건 소요가 등 맞댄 통제군과 측정 잡음 안이다 — `commands.md`).
 *
 * **이 층이 서는 자리는 선언에서 뽑는다**(M12 — ⓑ⑤·ⓑ⑥). M11 판은 vitest 프로세스 하나였고
 * playwright spec·여정 하네스·`node` 게이트 스크립트·번들러 적재가 층 밖이었다. 지금은 세우는
 * 자리가 셋이다 — vitest `setupFiles`(소비자 프로세스) · 추적된 뿌리 `*.config.*` 전수의 적재
 * (vite 메인·playwright·eslint 프로세스) · `package.json` 의 `node --import` 전수(게이트
 * 스크립트). 그 의무를 `kotlin-read-guard.test.mjs` 가 **선언에서 센다**.
 *
 * **그래도 층 밖인 자리가 있다** — `worker_threads`(`--import` 가 서지 않는다 · 실측)와 자식
 * 프로세스 텍스트 검색(`grep`·`git grep`)이고, 그 자리는 정적 술어(추적 파일 전수)가 **부분만**
 * 받는다. 덮는 집합이 다른 두 층이라는 사실과 그 잔여는 capability map §12.3 의
 * `OPEN-M12-KOTLIN-READ-GATE-RESIDUALS-3` 이 등재한다.
 *
 * **막지 않는 것**: 이 모듈이나 setup 등재 자체를 고치는 것, 그리고 가로채기를 피해 자기 바인딩을
 * 미리 붙들어 두는 것. 계약의 위협 모델 경계가 「의도적 은닉은 리뷰 소관」으로 둔 자리다.
 */

import { createRequire, syncBuiltinESMExports } from 'node:module';

const require = createRequire(import.meta.url);
const fs = require('node:fs');
const path = require('node:path');
const { fileURLToPath } = require('node:url');

const webRoot = path.resolve(import.meta.dirname, '..');
const repoRoot = path.resolve(webRoot, '..');

/** 읽기를 소유하는 한 자리 — 이 파일만 Kotlin 원문을 열 수 있다. */
const OWNER = path.resolve(webRoot, 'scripts/kotlin-source.mjs');

/**
 * Kotlin 원문의 확장자 술어. **조립해서** 만든다 — 통째로 적으면 소유권 게이트의 경로 토큰 술어가
 * 이 파일을 집고, 그러면 그 게이트의 예외 목록에 자기를 넣어야 한다(목록이 구멍이 된다).
 *
 * 대소문자를 무시하는 것이 ⑨ 이고, **끝**만 보는 것이 의도다(`X.kt.bak` 은 Kotlin 원문이 아니다).
 */
const KOTLIN_EXTENSION = new RegExp(`\\.${'k'}${'t'}s?$`, 'iu');

/** 해석된 경로가 Kotlin 원문인가. 순수 함수라 단위 대조가 파일시스템 없이 선다. */
export function isKotlinSourcePath(candidate) {
  return KOTLIN_EXTENSION.test(candidate);
}

// 층이 자기 안에서 쓰는 해석기 — **감싸기 전에** 붙든다(감싼 판을 다시 부르면 재귀한다).
const realpathSync = fs.realpathSync;

/**
 * **경로는 `PATH_MAX` 를 넘을 수 없다**(r1 — cr M-1). 이보다 긴 값은 경로가 아니라 **내용**이다 —
 * `writeFileSync(path, data)`·`readSync(fd, buffer, …)` 처럼 내용이 인자인 표면에서 그 값을
 * 디코딩하고 `path.resolve` 에 태우면 비용이 **내용 크기에 비례**한다(실측: 8MB 버퍼 쓰기 20회가
 * 통제군 314ms → 층 설치 2327ms, 약 7.4배). 길이 하한 한 줄이 그 비례를 없앤다 — 표면을
 * 열거하지 않는다.
 */
const PATH_MAX = 4096;

/**
 * 인자에서 **경로로 읽히는 값**을 낸다. 파일 서술자(수)·옵션 객체·콜백은 경로가 아니고,
 * `PATH_MAX` 를 넘는 문자열·버퍼도 경로가 아니다.
 */
function asPath(value) {
  if (typeof value === 'string') {
    return value.length > PATH_MAX ? undefined : value;
  }
  if (value instanceof URL) {
    return value.pathname;
  }
  if (!Buffer.isBuffer(value) || value.length > PATH_MAX) {
    return undefined;
  }
  return value.toString('utf8');
}

/**
 * **호출자 파일을 낸다** — 구조화된 프레임으로 읽는다(문자열 스택을 만들면 호출마다 서식 비용이
 * 든다). 이 모듈과 node 내부 프레임, 그리고 `ownFile` 을 지나 처음 나오는 사용자 파일이 호출자다.
 *
 * **표기에 면역이다**: 호출자가 어떤 구문으로 모듈을 얻었는지(확장자 없는 import · `createRequire` ·
 * 템플릿 `import()` · `vi.importActual`)가 프레임에 남지 않는다. 그래서 「누가 불렀는가」를 묻는
 * 층은 획득 꼴을 **열거하지 않는다**. `ownFile` 을 받는 것은 이 판정을 **자기 모듈 안에서** 쓰는
 * 소유자(추출 커널)가 자기 프레임을 건너뛰어야 하기 때문이다.
 */
export function callerOutside(ownFile) {
  const prepare = Error.prepareStackTrace;
  const limit = Error.stackTraceLimit;
  Error.prepareStackTrace = (_error, frames) => frames;
  Error.stackTraceLimit = 20;
  const { stack } = new Error();
  Error.prepareStackTrace = prepare;
  Error.stackTraceLimit = limit;

  for (const frame of stack) {
    const file = frame.getFileName();
    if (typeof file !== 'string' || file.startsWith('node:')) {
      continue;
    }
    const plain = file.startsWith('file:') ? fileURLToPath(file) : file;
    if (plain === import.meta.filename || plain === ownFile) {
      continue;
    }
    return plain;
  }
  return '(호출자를 읽지 못했다)';
}

/**
 * 인자 가운데 **Kotlin 원문을 가리키는 실경로**가 있으면 그것을 낸다.
 *
 * **위치를 묻지 않는다**(M12 — ⓑ③). 앞 판은 열리는 경로가 저장소 안인지 물어 밖이면 건너뛰었고,
 * 그래서 디렉터리째 저장소 밖으로 복사한 뒤 그 사본을 읽는 꼴이 지나갔다(실측). 「Kotlin 정본이
 * 살 수 없는 자리」라는 전제가 **복사 앞에서 무너진다** — 사본은 어디서든 살 수 있다. 그래서
 * 남는 것은 실경로의 확장자 하나이고, 위치 술어가 사라진다.
 *
 * 남는 예외는 **`node_modules` 하나**다. 건너뛰지 않으면 모든 모듈 적재가 실경로 호출을 타고
 * 전건이 느려진다 — 문면이 아니라 측정으로 든다(같은 조건 3회 중위값: 분기 둘 8.16s · 이 판
 * 9.19s · 예외까지 지운 판 10.44s). 그 아래로 복사한 뒤 읽는 꼴은 평범한 관용구가 아니라
 * 의도적 은닉이라 **경계 밖으로 등재**한다.
 */
function kotlinTargetOf(args) {
  for (const arg of args) {
    const given = asPath(arg);
    if (given === undefined) {
      continue;
    }
    const absolute = path.resolve(given);
    if (absolute.includes(`${path.sep}node_modules${path.sep}`)) {
      continue;
    }
    // **해석되지 않는 경로는 대상이 아니다.** 없는 파일은 아무것도 열지 않고, 적힌 글자를 그대로
    // 믿으면 이 층이 **표기 술어로 되돌아간다**(실측: glob 패턴 `…/*.kt` 가 그 자리에서 거부됐다 —
    // 패턴은 열리는 파일이 아니다). 그 꼴을 받는 것은 데이터 파일 쪽 정적 술어다.
    let real;
    try {
      real = realpathSync.call(fs, absolute);
    } catch {
      continue;
    }
    if (isKotlinSourcePath(real)) {
      return real;
    }
  }
  return undefined;
}

function refusal(caller, target) {
  return new Error(
    `[kotlin-read-guard] ${caller} 가 Kotlin 원문을 직접 열었다: ${path.relative(repoRoot, target)} — ` +
      '원문은 scripts/kotlin-source.mjs 의 읽기 집합만 열 수 있다(키로 받아라)',
  );
}

/**
 * **읽기가 추출 함수를 부르는 동안 그 텍스트를 걸어 두는 비공개 슬롯**(M12 r2 — cr HIGH).
 *
 * 왜 여기 있나: 읽기 모듈과 추출 커널이 **둘 다 이 층을 이미 쓴다**(호출자 판정). 슬롯을 커널에
 * 두면 읽기가 한 소비자의 커널을 import 해야 하고, 그러면 공유 읽기가 특정 하네스를 알게 된다.
 *
 * 무엇을 막나: `bind` 처럼 **프레임을 남기지 않는 래퍼**로 손댄 텍스트를 미리 채워 읽기에 넘기면,
 * 커널이 보는 호출자가 **읽기 모듈**이 되어 호출자 판정을 지나간다(실측). 커널은 읽기 경유
 * 호출일 때 **받은 텍스트가 걸린 텍스트인지**를 함께 묻는다 — 값 동일성을 전역으로 되돌리는 것이
 * 아니라 **그 한 호출에만** 거는 것이라, 단위 test 가 합성 조각을 먹이는 길은 그대로다.
 */
let inFlightText;

/**
 * 텍스트를 걸고 `run` 을 부른 뒤 **반드시 비운다**. 거는 자리는 **읽기 모듈 하나**이고 그것을
 * 호출자 프레임으로 단언한다.
 *
 * **중첩을 금지한다**: 걸려 있는데 또 걸면 던진다. 허용하면 안쪽 호출이 바깥 슬롯을 덮어, 바깥
 * 호출의 단언이 **다른 값**을 기준으로 서게 된다.
 */
export function runWithInFlightText(text, run) {
  const caller = callerOutside(undefined);
  if (caller !== OWNER) {
    throw new Error(`[kotlin-read-guard] ${caller} 가 in-flight 텍스트를 걸려 했다 — 읽기 모듈만 건다`);
  }
  if (inFlightText !== undefined) {
    throw new Error('[kotlin-read-guard] in-flight 텍스트가 이미 걸려 있다 — 읽기를 중첩해 부를 수 없다');
  }
  inFlightText = text;
  try {
    return run();
  } finally {
    inFlightText = undefined;
  }
}

/** 걸린 텍스트와 같은가. **값을 내주지 않는다** — 묻는 쪽이 이미 쥔 값으로만 답한다. */
export function isInFlightText(text) {
  return inFlightText !== undefined && text === inFlightText;
}

/** 거부해야 하면 그 오류를, 아니면 `undefined` 를 낸다. 호출·생성 트랩이 같은 판정을 쓴다. */
function refusalFor(args) {
  const target = kotlinTargetOf(args);
  if (target === undefined) {
    return undefined;
  }
  const caller = callerOutside(undefined);
  return caller === OWNER ? undefined : refusal(caller, target);
}

/**
 * 감싼 표면. **원래 함수의 계약을 바꾸지 않는다** — 실패 계약(동기는 던지고 `fs.promises` 는
 * **거절된 promise**)도, **생성·동일성 계약**도 그대로다.
 *
 * **`Proxy` 로 두는 이유**(r1 — cr H-2). 앞 판은 래퍼를 평범한 함수로 짓고 속성을
 * `Object.keys` 로 옮겼다. 그래서 `fs` 의 **함수 값에 섞인 생성자**가 깨졌다(실측, node v24):
 * `statSync(…) instanceof fs.Stats` 가 **거짓**이 되고, `new fs.Dirent(…)` 가
 * 「Class constructor … cannot be invoked without 'new'」로 **던졌다**. 원인 둘 — ES class 는
 * `apply` 로 부를 수 없고, `prototype` 은 non-enumerable 이라 복사 루프가 지나쳤다.
 *
 * `Proxy` 는 `name`·`prototype`·붙은 속성(`realpathSync.native`)을 원본에서 그대로 비추고
 * `apply`·`construct` 두 자리에서 같은 판정을 건다 — **생성자 분류를 열거하지 않고** 전수 감싸기를
 * 유지하는 길이다. 앞 판의 `defineProperty` 와 복사 루프는 함께 사라진다.
 */
function guarded(surface, original, rejectsInstead) {
  return new Proxy(original, {
    apply(target, _thisArg, args) {
      const error = refusalFor(args);
      if (error !== undefined) {
        if (rejectsInstead) {
          return Promise.reject(error);
        }
        throw error;
      }
      return Reflect.apply(target, surface, args);
    },
    construct(target, args, newTarget) {
      const error = refusalFor(args);
      if (error !== undefined) {
        throw error;
      }
      return Reflect.construct(target, args, newTarget);
    },
  });
}

/** 층이 자기 해석기를 다시 감싸지 않도록 두 이름만 비운다 — 그 밖은 전수다. */
const NOT_WRAPPED = new Set(['realpathSync', 'realpath']);

function patch(surface, rejectsInstead = false) {
  for (const key of Object.keys(surface)) {
    if (NOT_WRAPPED.has(key)) {
      continue;
    }
    const value = surface[key];
    if (typeof value !== 'function') {
      continue;
    }
    try {
      surface[key] = guarded(surface, value, rejectsInstead);
    } catch {
      // 쓸 수 없는 속성은 감쌀 수 없다 — 그 자리는 정적 술어가 받는다.
    }
  }
}

let installed = false;

/**
 * 층을 세운다. **import 만으로는 서지 않는다** — 세우는 자리가 `vite.config.ts` 의
 * `test.setupFiles` 하나여야, 그 등재를 지우는 변이가 양성 대조를 RED 로 만든다(이 모듈을
 * 술어 때문에 import 하는 파일이 층을 함께 세우면 그 배선을 잴 수 없다).
 *
 * 두 번 불러도 한 번만 감싼다 — 두 번 감싸면 호출마다 실경로 해석이 두 번 돈다.
 */
export function installKotlinReadGuard() {
  if (installed) {
    return;
  }
  installed = true;
  patch(fs);
  patch(fs.promises, true);
  // 감싼 값을 ESM 이름 묶음에도 싣는다 — 이 줄이 없으면 `import { readFileSync } from 'node:fs'` 가
  // 감싸기 **전의** 함수를 그대로 쥔다(그 상태로는 층이 아무것도 막지 못한다).
  syncBuiltinESMExports();
}
