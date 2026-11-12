/**
 * **Kotlin 주석 걷기의 본체 — 내부 모듈이다**(M10/gates-web r4 — verifier r3 H-1 · cr r2 HIGH).
 *
 * 이 함수는 **공유 모듈의 공개 표면에 없다**. r3 까지는 `kotlin-source.mjs` 가 이것을 export 했고,
 * 「소비자는 걷기를 import 하지 않는다」를 **import 구문 하나**(named import)로 셌다. 그래서
 * `import * as` · `await import()` · `createRequire` · 재수출 사슬 · `?v=1` 쿼리가 전부 초록으로
 * 지나갔다 — 전부 **평범한 관용구**다(위협 모델 경계: 우발적 이탈은 막는다).
 *
 * 공개 표면에서 빼면 **어떤 구문으로 공유 모듈을 불러도** 걷기를 얻을 수 없다. 이 모듈을
 * 참조하는 파일의 집합은 소유 모듈과 그 **단위 test** 둘뿐이고, 그것을 **해석된 경로**로 센다.
 */

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

  while (at < source.length && !isLineBreakAt(source, at)) {
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
    if (ch === '\n' || ch === '\r') {
      // **줄끝 판정이 escape 보다 앞이다**(verifier r3 / cr r2 LOW): `"x\` 뒤의 줄끝을 escape 가
      // 두 글자로 먹으면 문자열이 줄을 넘어 그 줄의 주석이 걷히지 않은 채 남는다.
      throw new Error(`${UNCLOSED[state]} — 줄이 끝났다`);
    }
    if (state !== BACKTICK && ch === '\\') {
      // escape 는 **두 글자**다. 바로 앞 글자만 보면 `"a\\"` 의 닫는 따옴표를 escape 로 읽는다.
      if (next === undefined || next === '\n' || next === '\r') {
        // escape 는 **줄끝을 넘지 못한다**(cr r2 LOW). 앞 판은 `\`+줄끝을 두 글자로 먹어 문자열이
        // 줄을 넘었고, 그 줄의 주석이 **걷히지 않은 채** 추출에 닿았다 — 경계 문장의 반례였다.
        throw new Error(`${UNCLOSED[state]} — 줄이 끝났다`);
      }
      out += ch + next;
      at += 2;
      continue;
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
