/**
 * **Kotlin 원문 읽기의 공유 한 자리**(M10/gates-web — `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP`).
 *
 * node 에 Kotlin 파서가 없으므로 web 의 게이트 둘은 Kotlin 정본을 **텍스트로** 읽는다(교차언어
 * 어휘 등식 · 개찰 축 하네스의 오퍼레이션 등식). 두 소비자가 주석 걷기를 **각자** 가지고 있었고
 * 둘 다 부분 구현이었다 — 한 벌은 문자열 안의 `//` 를 주석으로 지워 base URL 상수를 절단했고,
 * 다른 벌은 문자열 안의 블록 주석 글자를 줄이고 raw string 을 절단했다. 절단된 값은 등식을
 * **조용히** 지나간다.
 *
 * **경계 한 문장**: 이 모듈은 값을 **조용히 바꾸지 않는다**. 다룰 수 없는 꼴을 만나면 **던진다**.
 * 「조용히 빗나간 값」과 「던진 읽기」 사이에 제3의 결과가 없다.
 *
 * 그래서 표기를 **열거하지 않는다**(「URL 안의 `//`」·「raw string 안의 `//`」마다 정규식을 더하는
 * 꼴 — 그 목록은 다음 꼴에서 또 뒤처진다). 상태 다섯을 지나는 **단일 패스 주사기**로 짓고, 주석
 * 술어와 문자열 술어가 한 자리에서 갈리게 둔다. 읽기 집합에 없는 꼴(raw string)은 읽기를 넓히지
 * 않고 거절한다 — 그 꼴이 생기는 날 RED 로 사람을 부르는 쪽이 이 층의 성질이다.
 *
 * **문자열 안에도 코드가 있다**(템플릿 식 `${…}`). 그 구간의 따옴표를 문자열의 끝으로 읽으면
 * 바로 이 모듈이 없애려던 절단이 그대로 재발한다 — `"${f("//")}"` 가 `"${f("` 가 된다(cr r0
 * HIGH). 식은 중괄호 깊이로 훑어 통째로 넘기고, 깊이 계수가 깨지는 꼴(식 안의 따옴표·주석
 * 표지)은 거절한다. 따옴표 없는 평범한 식은 읽기 집합에 실제로 있으므로 일률 거절하지 않는다.
 *
 * **이 모듈이 읽기까지 소유한다**(r2 — verifier r1 M-2). 걷기만 공유 자리에 두면 소비자가 원문을
 * 쥔 채 **자기 사본**을 들일 수 있고, 그 변이 둘(별칭 import + `replaceAll` · 별칭 import +
 * `indexOf('//')` 줄 절단)이 r1 의 텍스트 술어를 **초록으로** 지나갔다. 그래서 경로 표를 이
 * 모듈의 **비공개**로 두고 소비자에게는 **키**만 받는다 — 원문이 없으면 걷을 것이 없다.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * **읽기 집합 — `web/` 안에서 `.kt` 경로를 아는 유일한 자리다.**
 *
 * 비공개다. 내보내면 소비자가 그 경로로 원문을 다시 열 수 있어 사본의 길이 그대로 남는다.
 * 여기 전수로 서는 덕에 「읽기 집합 전부가 읽힌다」를 게이트 test 가 들 수 있다 — 앞 판까지
 * 그 목록은 어디에도 전수로 없었고, 확인하려면 손으로 적어야 했다.
 *
 * **일곱이다.** 착수 때는 여덟이었고 그중 명령 축(`BidCommand.kt`)은 10B(#149)가 openapi `enum`
 * 으로 올려 web 이 더 이상 읽지 않는다 — 읽지 않는 파일을 표에 남기면 「읽기 집합」이 거짓이 된다.
 */
const KOTLIN_SOURCES = {
  basis: 'shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt',
  provenance: 'procurement/src/main/kotlin/bidvector/procurement/ResolvedBaseAmount.kt',
  provenanceWire: 'app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt',
  openingEndpoints: 'app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt',
  noticeEndpoints: 'app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt',
  detailAxes: 'workflow/src/main/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCase.kt',
  progressPolicy: 'workflow/src/main/kotlin/bidvector/workflow/opening/OpeningProgressPolicyData.kt',
};

/** 읽기 집합의 **키 전수** — 경로가 아니라 키다(위 표를 내보내지 않는 사유와 같다). */
export const KOTLIN_SOURCE_KEYS = Object.keys(KOTLIN_SOURCES);

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * 읽기 집합의 키 하나를 **걷힌 텍스트**로 읽는다. 소비자가 Kotlin 원문을 얻는 유일한 길이다.
 *
 * @throws 읽기 집합에 없는 키면 — 조용히 빈 문자열을 내면 호출자의 모든 등식이 공허해진다.
 * @throws 걷기가 다룰 수 없는 꼴을 만나면(`withoutKotlinComments` 의 계약 그대로).
 */
export function readKotlinSource(key) {
  const relative = KOTLIN_SOURCES[key];
  if (relative === undefined) {
    throw new Error(`읽기 집합에 없는 키다: ${key} — 아는 키는 ${KOTLIN_SOURCE_KEYS.join('·')}`);
  }
  return withoutKotlinComments(readFileSync(resolve(repoRoot, relative), 'utf8'));
}

/**
 * **받은 텍스트가 걷힌 그대로인지 단언하고 그대로 돌려준다.** 소비자 추출 함수가 입력 자리에서
 * 부르는 **유일한** 걷기 표면이다(걷기 함수 자체는 소비자가 import 하지 않는다).
 *
 * 이 함수가 있는 이유는 중복 걷기가 아니라 **방어**다(cr r1 LOW④ 가 실측한 자리). 걷힌 텍스트에도
 * 문자열 안의 `//` 는 설계상 남으므로, 소비자가 받은 텍스트에 **걷기 사본을 덧씌우는** 길이 남는다.
 * 그 사본이 줄을 끊는 꼴이면 문자열의 균형이 깨지고, 그 순간 이 단언이 **던진다**. 이름을 「다시
 * 걷는다」가 아니라 「걷힌 상태를 단언한다」로 둔 것도 그래서다 — 다음 라운드가 「입력이 이미
 * 걷혔으니 중복」이라며 조용히 지우지 못하게 사유를 이름에 박았다.
 *
 * **막지 못하는 것**: 줄을 끊지 않는 치환(`replaceAll` 로 주석 표지만 지우는 꼴)은 걷힌 상태를
 * 유지하므로 이 단언을 지나간다 — 값은 망가지는데 조용하다. 설계 검토 §4 (3) 미달 ② 가 그 자리다.
 *
 * @throws 입력이 걷힌 텍스트가 아니면 — 주석이 남아 있거나, 사본이 값을 바꿔 걷기 결과가 달라지면.
 * @throws 걷기가 다룰 수 없는 꼴을 만나면(`withoutKotlinComments` 의 계약 그대로).
 */
export function assertStrippedKotlin(text) {
  if (withoutKotlinComments(text) !== text) {
    throw new Error(
      '걷힌 Kotlin 텍스트가 아니다 — 읽기를 거치지 않았거나 사본이 값을 바꿨다',
    );
  }
  return text;
}

/** 주사기의 상태 — Kotlin 에서 `//` 가 주석인지 값인지를 가르는 자리가 이 다섯이다. */
const CODE = 'code';
const STRING = 'string';
const CHAR = 'char';
const BLOCK = 'block';
const BACKTICK = 'backtick';

/** 여는 글자 → 상태. 분기 사슬 대신 표로 둔다 — 꼴이 늘어도 갈림이 한 줄씩만 는다. */
const OPENER = { '"': STRING, "'": CHAR, '`': BACKTICK };
const CLOSER = { [STRING]: '"', [CHAR]: "'", [BACKTICK]: '`' };
const UNCLOSED = {
  [STRING]: '문자열이 닫히지 않았다',
  [CHAR]: '문자 리터럴이 닫히지 않았다',
  [BLOCK]: '블록 주석이 닫히지 않았다',
  [BACKTICK]: '백틱 식별자가 닫히지 않았다',
};

/** 템플릿 식 안에서 이 층이 다루지 못하는 꼴 — 따옴표와 주석 표지. */
const TEMPLATE_UNHANDLED = /["']|\/\/|\/\*/u;

/**
 * 줄끝이 시작하는 자리인가. Kotlin 의 줄끝은 `'\n' | '\r' '\n'?` 이므로 **홀로 선 `\r` 도 줄끝**
 * 이다(verifier r2 L-1) — 그 자리를 줄끝으로 세지 않으면 줄 주석이 `\r` 를 넘어 **다음 선언을
 * 먹고**, `\n` 기준 줄 수는 그대로라 어느 대조도 붉어지지 않는다.
 */
function isLineBreakAt(source, at) {
  return source[at] === '\n' || source[at] === '\r';
}

/**
 * 문자열 템플릿 식(`${…}`)의 **끝 다음 자리**를 중괄호 깊이로 찾는다. 첫 `}` 에서 끊으면
 * 중첩된 식의 뒤가 값으로 샌다.
 *
 * @throws 식 안에 따옴표·주석 표지가 있으면 — 그 안에서 깊이 계수가 깨지고, 깨진 계수는
 *   문자열의 끝을 옮겨 **값을 조용히 절단한다**. 읽기 집합의 `${…}` 는 전부 따옴표가 없다.
 * @throws 식이 닫히기 전에 줄이나 원문이 끝나면.
 */
function templateExpressionEnd(source, start) {
  let depth = 0;
  let at = start + 1;

  while (at < source.length && source[at] !== '\n') {
    const ch = source[at];
    if (ch === '{') {
      depth += 1;
    } else if (ch === '}') {
      depth -= 1;
      if (depth === 0) {
        if (TEMPLATE_UNHANDLED.test(source.slice(start, at + 1))) {
          throw new Error('템플릿 식 안의 따옴표·주석 표지는 읽지 않는다 — 깊이 계수가 깨진다');
        }
        return at + 1;
      }
    }
    at += 1;
  }

  throw new Error('템플릿 식이 닫히지 않았다');
}

/**
 * Kotlin 원문에서 주석을 걷는다. **문자열·문자 리터럴 안의 주석 표지는 값이므로 남긴다.**
 *
 * 줄끝은 걷어 낸 자리에도 남긴다(CRLF 의 `\r` 까지) — 줄 구조가 바뀌면 호출자의 다른 읽기가
 * 엉뚱한 줄을 보고, 줄끝이 섞이는 것 자체가 조용한 값 변경이다.
 *
 * 백틱 식별자(`` `can't resolve` ``)는 한 상태로 지난다 — 그 안의 `'` 는 문자 리터럴을 열지
 * 않는다. 모르면 정당한 Kotlin 이 통째로 던져진다.
 *
 * @throws raw string(`"""`)을 만나면 — 읽기 집합에 없는 꼴이고 절단이 조용하기 때문이다.
 * @throws 템플릿 식 안에 따옴표·주석 표지가 있으면 — 깊이 계수가 깨져 값이 절단된다.
 * @throws 문자열·문자 리터럴·블록 주석·백틱 식별자·템플릿 식이 닫히지 않으면.
 */
export function withoutKotlinComments(source) {
  let state = CODE;
  let depth = 0;
  let out = '';
  let at = 0;

  while (at < source.length) {
    const ch = source[at];
    const next = source[at + 1];

    if (state === BLOCK) {
      if (ch === '/' && next === '*') {
        depth += 1;
        at += 2;
      } else if (ch === '*' && next === '/') {
        depth -= 1;
        at += 2;
        state = depth === 0 ? CODE : BLOCK;
      } else {
        // 주석 본문은 버리고 줄끝만 남긴다 — CRLF 의 `\r` 도 줄끝의 일부다.
        out += ch === '\n' || ch === '\r' ? ch : '';
        at += 1;
      }
      continue;
    }

    if (state === CODE) {
      if (ch === '"' && next === '"' && source[at + 2] === '"') {
        throw new Error('raw string(""")은 읽지 않는다 — 여러 줄 값의 절단이 조용하다');
      }
      if (ch === '/' && (next === '/' || next === '*')) {
        if (next === '*') {
          state = BLOCK;
          depth = 1;
          at += 2;
        } else {
          // 줄끝 **앞**에서 멈춘다 — `\r\n` 의 `\r` 까지 먹으면 줄끝이 조용히 바뀐다.
          while (at < source.length && !isLineBreakAt(source, at)) {
            at += 1;
          }
        }
        continue;
      }
      state = OPENER[ch] ?? CODE;
      out += ch;
      at += 1;
      continue;
    }

    // STRING · CHAR · BACKTICK — 안의 `//` 와 블록 주석 표지는 **값이다**.
    if (state === STRING && ch === '$' && next === '{') {
      // 템플릿 식은 통째로 넘긴다 — 그 안의 따옴표를 문자열의 끝으로 읽는 것이 절단의 자리다.
      const end = templateExpressionEnd(source, at);
      out += source.slice(at, end);
      at = end;
      continue;
    }
    if (state !== BACKTICK && ch === '\\') {
      // escape 는 **두 글자**다. 바로 앞 글자만 보면 `"a\\"` 의 닫는 따옴표를 escape 로 읽는다.
      out += ch + (next ?? '');
      at += 2;
      continue;
    }
    if (ch === '\n' || ch === '\r') {
      // 홀로 선 `\r` 도 Kotlin 의 줄끝이다 — 문자열·문자 리터럴·백틱은 줄을 넘지 못한다.
      throw new Error(`${UNCLOSED[state]} — 줄이 끝났다`);
    }
    out += ch;
    at += 1;
    state = ch === CLOSER[state] ? CODE : state;
  }

  if (state !== CODE) {
    throw new Error(`${UNCLOSED[state]} — 원문이 끝났다`);
  }
  return out;
}
