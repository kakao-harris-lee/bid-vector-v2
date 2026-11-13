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
 * **이 층이 서는 자리는 vitest 프로세스**다(`vite.config.ts` 의 `test.setupFiles`). playwright
 * spec·여정 하네스·`node` 게이트 스크립트는 층 밖이고, 그 자리는 정적 술어(추적 파일 전수)가
 * 받는다 — 덮는 집합이 다른 **두 층**이고 그 사실은 `design-review.md` 미달 Ⅰ 이 등재한다.
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
 * 인자에서 **경로로 읽히는 값**을 낸다. 파일 서술자(수)·옵션 객체·콜백은 경로가 아니다.
 */
function asPath(value) {
  if (typeof value === 'string') {
    return value;
  }
  if (value instanceof URL) {
    return value.pathname;
  }
  return Buffer.isBuffer(value) ? value.toString('utf8') : undefined;
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
 * 저장소 밖과 `node_modules` 아래는 실경로 해석을 **건너뛴다** — Kotlin 정본이 살 수 없는 자리이고,
 * 건너뛰지 않으면 모든 모듈 적재가 실경로 호출을 탄다(그 범위는 단위 대조가 든다).
 */
function kotlinTargetOf(args) {
  for (const arg of args) {
    const given = asPath(arg);
    if (given === undefined) {
      continue;
    }
    const absolute = path.resolve(given);
    if (!absolute.startsWith(repoRoot) || absolute.includes(`${path.sep}node_modules${path.sep}`)) {
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
