import { describe, expect, it } from 'vitest';

// 걷기 본체는 내부 모듈이다(r4) — 이 단위 test 가 그것을 참조하는 **유일한** 소비자다.
import { withoutKotlinComments } from './kotlin-comment-strip.mjs';

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

  /**
   * **백틱 식별자 안의 `'` 는 문자 리터럴을 열지 않는다**(cr r0 LOW). 백틱 구간을 모르면
   * `` `can't resolve` `` 의 아포스트로피가 CHAR 를 열어 **원문 끝까지 닫히지 않고**, 읽기가
   * 통째로 던져진다 — 값이 틀리진 않지만 정당한 Kotlin 을 읽지 못한다.
   */
  it('백틱 식별자 안의 아포스트로피는 문자 리터럴이 아니다', () => {
    expect(withoutKotlinComments("fun `can't resolve`() = 1 // 꼬리")).toBe("fun `can't resolve`() = 1 ");
  });

  it('백틱 식별자 안의 `//` 도 값이다', () => {
    expect(withoutKotlinComments('fun `a//b`() = 1 // 꼬리')).toBe('fun `a//b`() = 1 ');
  });
});

/**
 * **문자열 템플릿 식**(`${…}`) — cr r0 HIGH. 앞 판은 템플릿 안의 따옴표를 **문자열을 닫는**
 * 따옴표로 읽어, `"${f("//")}"` 에서 그 뒤를 줄 주석으로 지웠다(`val s = "${f("` 로 절단).
 * 절단은 조용했고, 그것이 이 모듈이 없애려던 바로 그 결함의 재발이다.
 *
 * 처분은 **일률 거절이 아니다** — 읽기 집합(`ResolvedBaseAmount.kt`)에 따옴표 없는 평범한
 * `${…}` 가 둘 있고 그것까지 던지면 이 층은 쓸 수 없다. 중괄호 깊이로 식을 훑고, **그 구간에
 * 이 층이 다루지 못하는 글자가 있을 때만** 던진다.
 */
describe('주석 걷기 — 문자열 템플릿 식', () => {
  const q = '"';

  it('템플릿 안의 따옴표를 만나면 던진다 — 절단하지 않는다', () => {
    expect(() => withoutKotlinComments(`val s = ${q}\${f(${q}//${q})}${q} + ${q}TAIL${q}\n`)).toThrow(
      /템플릿/u,
    );
  });

  /** **통제군** — 따옴표 없는 평범한 템플릿은 그대로 지나가고 꼬리 주석만 걷힌다. */
  it('따옴표 없는 템플릿은 그대로 지나간다', () => {
    expect(withoutKotlinComments(`val s = ${q}a \${amount.provenance} b${q} // 꼬리`)).toBe(
      `val s = ${q}a \${amount.provenance} b${q} `,
    );
  });

  /** 중괄호가 중첩돼도 식의 끝을 깊이로 찾는다 — 첫 `}` 에서 끊으면 뒤가 값으로 샌다. */
  it('중첩 중괄호 템플릿도 그대로 지나간다', () => {
    expect(withoutKotlinComments(`val s = ${q}\${ if (x) { 1 } else { 2 } }${q} // 꼬리`)).toBe(
      `val s = ${q}\${ if (x) { 1 } else { 2 } }${q} `,
    );
  });

  /**
   * 템플릿 안의 **문자 리터럴**은 앞 판에서 우연히 통과했다(STRING 에서 `'` 가 무력이라).
   * 중괄호 깊이로 훑으면 `'}'` 의 `}` 가 식을 일찍 끊어 같은 구멍이 다시 열리므로 **던진다.**
   */
  it('템플릿 안의 문자 리터럴은 던진다 — 중괄호 계수가 깨진다', () => {
    expect(() => withoutKotlinComments(`val s = ${q}\${c == '}'}${q} // 꼬리`)).toThrow(/템플릿/u);
  });

  /**
   * **줄끝을 보는 자리는 넷이다**(cr r2 LOW · verifier r3). r3 은 둘만 고쳤고 나머지 둘 —
   * `templateExpressionEnd` 의 루프 가드와 **escape 분기** — 가 남아 있었다. escape 는 `\`+줄끝을
   * 두 글자로 먹어 문자열이 **줄을 넘었고**, 그 줄의 주석이 걷히지 않은 채 추출에 닿았다(경계
   * 문장의 반례). 넷 모두 같은 술어(`isLineBreakAt`)로 닫고 그 입력을 여기서 든다.
   */
  it('줄을 넘는 템플릿 식은 LF·CR 양쪽에서 던진다', () => {
    expect(() => withoutKotlinComments(`val a = ${q}\${ f\nval b = 2 }${q}\n`)).toThrow(/닫히지 않았다/u);
    expect(() => withoutKotlinComments(`val a = ${q}\${ f\rval b = 2 }${q}\n`)).toThrow(/닫히지 않았다/u);
  });

  it('escape 는 줄끝을 넘지 못한다 — LF·CR 양쪽에서 던진다', () => {
    expect(() => withoutKotlinComments(`val a = ${q}x\\\nval b = 1 // c${q}`)).toThrow(/줄이 끝났다/u);
    expect(() => withoutKotlinComments(`val a = ${q}x\\\rval b = 1 // c${q}`)).toThrow(/줄이 끝났다/u);
  });

  it('닫히지 않은 템플릿 식은 던진다', () => {
    expect(() => withoutKotlinComments(`val s = ${q}\${f(\n`)).toThrow(/닫히지 않았다/u);
  });

  it('escape 된 달러는 템플릿을 열지 않는다', () => {
    expect(withoutKotlinComments(`val s = ${q}\\\${not} //x${q} // 꼬리`)).toBe(
      `val s = ${q}\\\${not} //x${q} `,
    );
  });

  /**
   * **깊이 계수를 고정한다**(verifier r2 M-2). `depth === 0` 을 `depth <= 1` 로 느슨하게 바꾸면
   * 중첩 중괄호 안의 따옴표가 식의 끝을 **첫 닫는 중괄호**로 보이게 만들어 이 입력이 `val a = "${ m { x } + "`
   * 로 **조용히 절단된다**(실측). 기존 「중첩 중괄호 템플릿도 그대로 지나간다」 test 는 두 판을
   * 가르지 못한다 — 어느 쪽이든 식 텍스트를 그대로 복사하기 때문이다.
   */
  it('중첩 중괄호 안의 따옴표도 던진다 — 깊이 계수가 느슨해지면 절단된다', () => {
    expect(() => withoutKotlinComments(`val a = ${q}\${ m { x } + ${q}//${q} }${q} // c`)).toThrow(
      /템플릿/u,
    );
  });

  /**
   * **verifier r1 M-1 의 입력** — 같은 축의 다른 꼴이다. 기본값을 템플릿 **안에서** 주는 모양은
   * 읽기 집합에 실제로 생길 수 있고, r0 판은 이것을 `val u = "${base ?: "https:` 로 **무성
   * 절단**했다. 이 OPEN 이 처음 지목한 base URL 절단과 **같은 해악**이라 축어로 든다.
   */
  it('템플릿 안 기본값 문자열도 절단하지 않고 던진다 (v r1 M-1)', () => {
    expect(() => withoutKotlinComments(`val u = ${q}\${base ?: ${q}https://x${q}}/p${q}`)).toThrow(
      /템플릿/u,
    );
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
   * **CRLF 원문의 `\r` 도 값이다**(cr r0 LOW). 앞 판은 줄 주석을 `\n` 까지 건너뛰어 그 앞의
   * `\r` 을 **함께 지웠다** — 같은 파일 안에서 줄끝이 섞이고, 그것은 이 층이 금지한 「조용한
   * 값 변경」이다(줄 수는 그대로라 아무 대조도 붉어지지 않는다).
   */
  it('CRLF 줄 주석을 걷어도 줄끝은 그대로다', () => {
    expect(withoutKotlinComments('val a = 1 // 꼬리\r\nval b = 2\r\n')).toBe(
      'val a = 1 \r\nval b = 2\r\n',
    );
  });

  /**
   * **홀로 선 `\r` 도 줄끝이다**(verifier r2 L-1). Kotlin 의 `NL` 은 `'\n' | '\r' '\n'?` 이라
   * 단독 CR 뒤에 선언이 올 수 있다. 그 자리를 줄끝으로 세지 않으면 줄 주석이 `\r` 를 넘어
   * **다음 선언을 먹고**, `\n` 기준 줄 수는 그대로라 아무 대조도 붉어지지 않는다.
   */
  it('홀로 선 CR 에서 줄 주석이 멈춘다 — 다음 선언을 먹지 않는다', () => {
    expect(withoutKotlinComments('val a = 1 // 꼬리\rval b = 2\n')).toBe('val a = 1 \rval b = 2\n');
  });

  it('CRLF 블록 주석의 줄끝도 그대로다', () => {
    expect(withoutKotlinComments('val a = 1 /* 두\r\n줄 */\r\nval b = 2')).toBe(
      'val a = 1 \r\n\r\nval b = 2',
    );
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
 * **가리는 선언** — 옛 이름을 주석으로 남기고 새 값을 아래에 두는 꼴은 출하 파일에 실제로 생긴다.
 * 걷기가 그 줄을 지우므로 **추출이 보는 것은 실제 값 하나**다. 이 성질은 r3 까지 소비자 test 에
 * 있었는데(추출 함수에 주석 든 원문을 먹였다) 그 자리는 이제 계약상 **거부**다 — 성질 자체는
 * 사라지지 않고 걷기 쪽인 여기로 옮겨 왔다.
 */
describe('주석 걷기 — 가리는 선언을 지운다', () => {
  it('주석으로 가린 옛 선언은 걷히고 실제 값만 남는다', () => {
    const shadowed = [
      '// 옛 이름: val p: String = "old"',
      '    val p: String = "new"',
    ].join('\n');

    const stripped = withoutKotlinComments(shadowed);

    expect(stripped).not.toContain('old');
    expect(stripped).toContain('"new"');
  });
});

/**
 * **복제 금지 술어는 이 파일에 없다**(r3 — verifier r1 M-2 · r2 H-3 · cr r1 MEDIUM②).
 *
 * r1·r2 는 여기서 소비자를 `CONSUMERS` **배열로 열거**하고 걷기 사본을 **정규식 텍스트**로 찾았다.
 * 그 열거는 소비자가 넷으로 늘어도 둘에 머물렀고(실측), 그 술어는 표기를 바꾼 사본 둘을 **초록으로**
 * 지나갔다. 둘 다 `scripts/kotlin-source-ownership.test.mjs` 로 옮겼다 — 뿌리 훑기 + AST 로
 * 「경로를 아는 자리」·「걷기를 import 하는 자리」·「export 집합」을 들고, 표기를 찾는 텍스트 층은
 * **완화**라고 이름 붙여 2 차로 둔다.
 */
