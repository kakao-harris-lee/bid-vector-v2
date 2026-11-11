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
 * 꼴 — 그 목록은 다음 꼴에서 또 뒤처진다). 상태 넷을 지나는 **단일 패스 주사기**로 짓고, 주석
 * 술어와 문자열 술어가 한 자리에서 갈리게 둔다. 읽기 집합에 없는 꼴(raw string)은 읽기를 넓히지
 * 않고 거절한다 — 그 꼴이 생기는 날 RED 로 사람을 부르는 쪽이 이 층의 성질이다.
 */

/** 주사기의 상태 — Kotlin 에서 `//` 가 주석인지 값인지를 가르는 자리가 이 넷이다. */
const CODE = 'code';
const STRING = 'string';
const CHAR = 'char';
const BLOCK = 'block';

const CLOSER = { [STRING]: '"', [CHAR]: "'" };
const UNCLOSED = {
  [STRING]: '문자열이 닫히지 않았다',
  [CHAR]: '문자 리터럴이 닫히지 않았다',
  [BLOCK]: '블록 주석이 닫히지 않았다',
};

/**
 * Kotlin 원문에서 주석을 걷는다. **문자열·문자 리터럴 안의 주석 표지는 값이므로 남긴다.**
 *
 * 줄바꿈은 걷어 낸 자리에도 남긴다 — 줄 구조가 바뀌면 호출자의 다른 읽기가 엉뚱한 줄을 본다.
 *
 * @throws raw string(`"""`)을 만나면 — 읽기 집합에 없는 꼴이고 절단이 조용하기 때문이다.
 * @throws 문자열·문자 리터럴·블록 주석이 닫히지 않으면.
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
        // 주석 본문은 버리고 줄바꿈만 남긴다.
        out += ch === '\n' ? ch : '';
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
          while (at < source.length && source[at] !== '\n') {
            at += 1;
          }
        }
        continue;
      }
      state = ch === '"' ? STRING : ch === "'" ? CHAR : CODE;
      out += ch;
      at += 1;
      continue;
    }

    // STRING · CHAR — 안의 `//` 와 블록 주석 표지는 **값이다**.
    if (ch === '\\') {
      // escape 는 **두 글자**다. 바로 앞 글자만 보면 `"a\\"` 의 닫는 따옴표를 escape 로 읽는다.
      out += ch + (next ?? '');
      at += 2;
      continue;
    }
    if (ch === '\n') {
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
