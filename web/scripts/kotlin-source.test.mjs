import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { withoutKotlinComments } from './kotlin-source.mjs';

/**
 * **Kotlin 원문 읽기의 공유 한 자리를 잠근다**(M10/gates-web — `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP`).
 *
 * 등재된 사실: web 의 게이트 둘이 Kotlin 정본을 텍스트로 읽는데 주석 걷기가 **두 벌**이었고,
 * 줄 단위 정규식 판이 **문자열 안의 `//` 를 주석으로 지웠다**. 읽기 집합의 한 파일이 base URL
 * 상수를 가지고 있어 그 판으로 읽으면 값이 `"https:` 로 **절단된다** — 그리고 절단된 값은 등식을
 * 조용히 지나간다(그 축은 조용한 404 로만 나타난다).
 *
 * **이 층의 경계 한 문장**: 값을 **조용히 바꾸지 않는다**. 다룰 수 없는 꼴을 만나면 **던진다**.
 * 「조용히 빗나간 값」과 「던진 읽기」 사이에 제3의 결과를 두지 않는 것이 이 모듈의 전부다.
 *
 * 그래서 아래 대조는 두 묶음이다 — **보존·걷기의 전수**와 **던지는 꼴의 전수**. 앞 판들이 조용히
 * 틀렸던 자리가 전부 첫 묶음에 있다(설계 검토 §2b 의 표가 그 전수다).
 */

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..');
const read = (relative) => readFileSync(resolve(webRoot, relative), 'utf8');

describe('주석 걷기 — 문자열 안의 `//` 는 주석이 아니다', () => {
  /**
   * **이 slice 의 RED.** 줄 단위 정규식 판은 이 입력에서 `"https:` 를 낸다 — 읽기 집합에 실제로
   * 있는 꼴이고(출하 기본값의 base URL 상수), 절단된 값이 등식을 지나가는 것이 이 OPEN 이다.
   */
  it('URL 리터럴을 절단하지 않는다 — 뒤에 붙은 주석만 걷는다', () => {
    expect(withoutKotlinComments('val u = "https://a.example/b" // 꼬리')).toBe('val u = "https://a.example/b" ');
  });

  it('문자열 안의 블록 주석 글자도 값이다 — 조용히 줄이지 않는다', () => {
    expect(withoutKotlinComments('val u = "a/*b*/c"')).toBe('val u = "a/*b*/c"');
  });

  it('escape 된 따옴표는 문자열을 닫지 않는다', () => {
    expect(withoutKotlinComments('val u = "a\\"//b" // 꼬리')).toBe('val u = "a\\"//b" ');
  });

  /**
   * escape 된 **backslash** 뒤의 따옴표는 문자열을 **닫는다**. 앞 판은 바로 앞 글자만 보아
   * 그것을 escape 로 읽었고, 문자열이 열린 채 남아 **그 줄의 주석이 걷히지 않았다** — 남은
   * 주석은 downstream 의 헤더 계수를 흔든다(「정본을 가리지 못한다」).
   */
  it('escape 된 backslash 뒤 따옴표는 문자열을 닫는다 — 그 뒤 주석은 걷힌다', () => {
    expect(withoutKotlinComments('val u = "a\\\\" // 꼬리')).toBe('val u = "a\\\\" ');
  });

  it('따옴표를 담은 문자 리터럴은 문자열 상태를 뒤집지 않는다', () => {
    expect(withoutKotlinComments('val c = \'"\' // 꼬리')).toBe('val c = \'"\' ');
  });

  it('escape 된 따옴표 문자 리터럴도 같다', () => {
    expect(withoutKotlinComments("val c = '\\'' // 꼬리")).toBe("val c = '\\'' ");
  });
});

describe('주석 걷기 — 주석은 실제로 걷는다', () => {
  it('줄 주석을 걷고 줄바꿈은 남긴다', () => {
    expect(withoutKotlinComments('val a = 1 // 꼬리\nval b = 2\n')).toBe('val a = 1 \nval b = 2\n');
  });

  it('블록 주석을 걷는다', () => {
    expect(withoutKotlinComments('val a = /* 가운데 */ 1')).toBe('val a =  1');
  });

  it('블록 주석 안의 줄 주석 표지는 주석의 일부다', () => {
    expect(withoutKotlinComments('val a = /* // 안 */ 1')).toBe('val a =  1');
  });

  it('줄 주석 안의 블록 주석 여는 표지는 줄에서 끝난다', () => {
    expect(withoutKotlinComments('val a = 1 // /* 안 열린다\nval b = 2')).toBe('val a = 1 \nval b = 2');
  });

  /**
   * Kotlin 의 블록 주석은 **중첩된다**. 비탐욕 정규식은 첫 닫는 표지에서 멈춰 짝 없는 닫는
   * 표지를 남겼고, 그 글자가 downstream 의 균형 괄호·중괄호 읽기에 섞인다.
   */
  it('중첩 블록 주석을 깊이로 걷는다', () => {
    expect(withoutKotlinComments('val a = 1 /* 겉 /* 속 */ 겉 */ val b = 2')).toBe('val a = 1  val b = 2');
  });

  it('여러 줄 블록 주석의 줄바꿈은 남긴다 — 줄 구조를 바꾸지 않는다', () => {
    expect(withoutKotlinComments('val a = 1\n/* 두\n줄 */\nval b = 2')).toBe('val a = 1\n\n\nval b = 2');
  });

  /**
   * **음성 대조** — 주석이 없는 원문은 글자 하나도 바뀌지 않는다. 이 대조가 없으면 「전부 걷어
   * 빈 문자열을 내는」 구현도 위의 보존 대조 몇 개를 우연히 지날 수 있다.
   */
  it('주석이 없으면 원문 그대로다', () => {
    const plain = 'enum class Probe {\n    A,\n    B,\n}\n';

    expect(withoutKotlinComments(plain)).toBe(plain);
  });
});

describe('주석 걷기 — 다룰 수 없는 꼴은 던진다 (조용히 틀리지 않는다)', () => {
  /**
   * raw string 은 여러 줄에 걸치고 그 안의 `//` 는 값이다. 앞 판들은 따옴표 **세 개**를 홀수 번
   * 토글로 읽어 값을 절단했다(`https://a.b` → `https:`). 읽기 집합에 이 꼴이 **0** 이므로 읽기를
   * 넓히지 않고 **거절**한다 — 그 꼴이 생기는 날 RED 로 사람을 부르는 쪽이 이 층의 성질이다.
   */
  it('raw string 을 만나면 던진다', () => {
    expect(() => withoutKotlinComments('val u = """\nhttps://a.example\n"""\n')).toThrow(/raw string/u);
  });

  it('닫히지 않은 문자열은 던진다', () => {
    expect(() => withoutKotlinComments('val u = "안 닫힌다\n')).toThrow(/닫히지 않았다/u);
  });

  it('닫히지 않은 문자 리터럴은 던진다', () => {
    expect(() => withoutKotlinComments("val c = 'x\n")).toThrow(/닫히지 않았다/u);
  });

  it('닫히지 않은 블록 주석은 던진다', () => {
    expect(() => withoutKotlinComments('val a = 1 /* 안 닫힌다\n')).toThrow(/닫히지 않았다/u);
  });

  it('중첩이 덜 닫힌 블록 주석도 던진다', () => {
    expect(() => withoutKotlinComments('val a = 1 /* 겉 /* 속 */\n')).toThrow(/닫히지 않았다/u);
  });
});

/**
 * **사본이 되살아나는 축**(설계 검토 §2 우회 3·7). 공유 자리를 두어도 소비자가 자기 사본을 다시
 * 들이면 이 slice 는 아무것도 하지 않은 것이 된다. 그래서 「저장소에 주석 걷기 구현이 하나」와
 * 「두 소비자가 그 하나를 쓴다」를 함께 센다.
 *
 * 이 술어는 **텍스트**다(설계 검토 §3 의 등재된 미달) — 구조로 닫는 길은 「Kotlin 원문을 읽는
 * 파일」 자체가 경로 문자열 판정이라 한 겹 안쪽에서 같은 성질로 돌아온다. 의도적 우회는 리뷰
 * 소관이라는 네트워크·시계 금지의 같은 자리다.
 */
describe('주석 걷기 구현이 저장소에 하나다', () => {
  const CONSUMERS = ['scripts/money-vocab-cross-lang.test.mjs', 'e2e/harness/opening-axis.mjs'];
  const SHARED = 'scripts/kotlin-source.mjs';
  /** 줄 주석·블록 주석을 **정규식으로** 걷는 꼴. 사본의 성질 자체가 이 텍스트다. */
  const STRIP_REGEX = /replace\(\s*\/\\\/[\\/*]/u;

  it('소비자 둘이 공유 모듈에서 읽기를 끌어온다', () => {
    for (const relative of CONSUMERS) {
      expect(read(relative), `${relative} 가 공유 읽기를 쓰지 않는다`).toMatch(
        /import \{[^}]*withoutKotlinComments[^}]*\} from '[^']*kotlin-source\.mjs'/u,
      );
    }
  });

  it('소비자 둘에 자기 주석 걷기가 없다', () => {
    for (const relative of CONSUMERS) {
      expect(read(relative), `${relative} 에 사본이 되살아났다`).not.toMatch(STRIP_REGEX);
      expect(read(relative), `${relative} 가 자기 걷기를 선언한다`).not.toMatch(
        /function withoutKotlinComments/u,
      );
    }
  });

  /**
   * **술어 자신의 양성 대조** — 위 두 단언이 공허하지 않음을 잰다. 찾는 꼴이 공유 모듈에는
   * 실제로 있어야 하고, 없으면 `not.toMatch` 는 아무것도 재지 않는다.
   */
  it('공유 모듈은 걷기를 선언한다 — 위 단언이 공허하지 않다', () => {
    expect(read(SHARED)).toMatch(/function withoutKotlinComments/u);
  });
});
