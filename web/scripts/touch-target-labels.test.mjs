import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  ariaLabelOverrides,
  MIN_HEIGHT_DISPLAY_TOKENS,
  MIN_TOUCH_CLASS,
  NAME_OVERRIDE_ATTRIBUTES,
  scanAriaLabelOverrides,
  scanControls,
  unwrappedControls,
  WRAPPED_CONTROL_TYPES,
} from './touch-target-labels.mjs';

/**
 * M13/13B — **입찰함 라디오의 44px 를 잠그는 자리**(verifier L-5).
 *
 * 터치 타깃 게이트는 상자로 재지만 그 fixture 가 결과 기록 폼을 열지 않아 라디오를 한 번도 보지
 * 못한다. 이 축은 같은 규칙을 **원문에서** 재므로 fixture 가 닿지 않는 자리도 든다.
 */
const srcDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src');

/**
 * 대조에 쓰는 어휘를 **글자로 적지 않는다.** Tailwind 의 훑기는 `src/` 에 묶여 있지 않아, 이
 * 파일에 적은 class 꼴 글자가 **출하 CSS 에 그대로 실린다**(실측: 아래 셋이 번들에 들어가 CSS 가
 * 105 B 늘었다). 상수에서 유도하면 그 자리가 사라진다 — 주석에 적어도 훑기는 읽는다.
 */
const MIN_HEIGHT_PREFIX = MIN_TOUCH_CLASS.slice(0, MIN_TOUCH_CLASS.lastIndexOf('-'));
/** 같은 접두를 쓰지만 값이 0 인 덮어쓰기(우선 표시 포함). */
const OVERRIDE_IMPORTANT = `!${MIN_HEIGHT_PREFIX}-0`;
/** 변형 접두가 붙은 덮어쓰기. */
const OVERRIDE_VARIANT = `max-md:${MIN_HEIGHT_PREFIX}-0`;
/** 접두가 같을 뿐 다른 어휘 — 칸으로 가르지 않으면 이것이 지나간다. */
const LOOKALIKE = `${MIN_TOUCH_CLASS}0`;


describe('체크박스·라디오의 누르는 자리 (M13/13B v L-5)', () => {
  const { files, findings } = scanControls(srcDir);

  it('훑기가 실제 소스를 읽었다 — 빈 훑기는 모든 등식을 공허하게 만든다', () => {
    expect(files.length).toBeGreaterThan(5);
    expect(files).toContain('screens/BidBoxCard.tsx');
  });

  it('출하 트리에 위반이 0 이다', () => {
    expect(
      findings.map((one) => `${one.file}:${one.line} ${one.why}`),
      '체크박스·라디오는 최소 높이를 든 레이블이 감싼다',
    ).toStrictEqual([]);
  });

  /**
   * **이 축이 실제로 세 자리를 보고 있는가.** 보이지 않으면 위 「위반 0」은 공허하다 — 세 자리는
   * 터치 타깃 게이트가 상자로 재는 둘과, **그 게이트가 닿지 못하는 입찰함 라디오**다.
   */
  it('모집단에 세 자리가 모두 들어 있다', () => {
    expect(
      ['screens/ProfileFactsCard.tsx', 'screens/ProfileDetailsCard.tsx', 'screens/BidBoxCard.tsx'].filter((one) =>
        files.includes(one),
      ),
    ).toHaveLength(3);
  });
});

describe('양성 대조 — 심은 위반이 붉는다', () => {
  it('레이블이 감싸지 않으면 걸린다', () => {
    const found = unwrappedControls('export const X = () => <input type="checkbox" />;\n');

    expect(found).toHaveLength(1);
    expect(found[0].why).toContain('감싼 label 이 없다');
  });

  it(`감쌌어도 레이블이 ${MIN_TOUCH_CLASS} 를 들지 않으면 걸린다`, () => {
    const found = unwrappedControls(
      'export const X = () => (\n  <label className="flex items-center gap-1">\n    <input type="radio" />\n  </label>\n);\n',
    );

    expect(found).toHaveLength(1);
    expect(found[0].why).toContain(MIN_TOUCH_CLASS);
  });

  /** 접두만 같은 다른 어휘로 지나가지 않는다 — 칸으로 가른다. */
  it('비슷한 이름의 다른 어휘는 그 어휘가 아니다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${LOOKALIKE} items-center">\n    <input type="radio" />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
  });

  /** `type` 을 식으로 적어 **증명을 피하는** 길도 같은 의무를 진다. */
  it('type 이 식이면 레이블 밖에 둘 수 없다', () => {
    expect(unwrappedControls('export const X = () => <input type={kind} />;\n')).toHaveLength(1);
  });
});

/**
 * **자기 규칙 「증명 불가 → 같은 의무」를 지키는가**(cr Q-1 = v M-10). 앞 판은 레이블 **밖**의
 * 증명 불가는 위반으로 세면서 레이블 **안**의 증명 불가에는 최소 높이 의무를 지우지 않았다 —
 * 같은 파일 주석이 선언한 규칙과 코드가 어긋난 자리다.
 */
describe('증명할 수 없는 자리도 같은 의무를 진다 (cr Q-1 · v M-10)', () => {
  it('레이블 안에서 type 이 식이면 최소 높이 의무를 진다', () => {
    const found = unwrappedControls(
      "export const X = () => (\n  <label className=\"flex\">\n    <input type={kind} />\n  </label>\n);\n",
    );

    expect(found).toHaveLength(1);
  });

  it('음성 — 같은 꼴이라도 레이블이 최소 높이를 들면 초록이다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS}">\n    <input type={kind} />\n  </label>\n);\n`,
      ),
    ).toStrictEqual([]);
  });

  /** HTML 은 `type` 의 대소문자를 가리지 않는다 — 이 축이 가리면 한 글자로 열린다. */
  it('type 의 대소문자를 접어 본다', () => {
    expect(
      unwrappedControls('export const X = () => <input type="CHECKBOX" />;\n'),
    ).toHaveLength(1);
  });

  /** **볼 수 없는 것을 통과시키지 않는다** — 형제 술어가 spread 를 그렇게 다룬다. */
  it('spread 로 준 type 은 증명 불가다', () => {
    expect(unwrappedControls("export const X = () => <input {...{ type: 'radio' }} />;\n")).toHaveLength(1);
  });

  /** 태그를 변수에 담아 `input` 임을 숨기는 길 — 태그 판정은 ① 축과 같은 층이어야 한다. */
  it('변수에 담은 input 태그도 같은 의무를 진다', () => {
    const text = "const Input = 'input' as const;\nexport const X = () => <Input type=\"checkbox\" />;\n";

    expect(unwrappedControls(text)).toHaveLength(1);
  });

  /** 최소 높이를 **덮어쓰는** 어휘가 함께 있으면 그 레이블이 44px 를 든다고 증명할 수 없다. */
  it('레이블이 최소 높이를 덮어쓰면 걸린다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS} ${OVERRIDE_IMPORTANT}">\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS} ${OVERRIDE_VARIANT}">\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
  });

  /**
   * **레이블이 켜는 것은 컨트롤 하나뿐이다**(cr Q-4). 둘을 감싸면 뒤의 체크박스는 레이블을 눌러도
   * 켜지지 않는다 — 그 자리의 누르는 상자는 자기 13px 이다. 증명할 수 없으니 보수적으로 센다.
   */
  it('레이블 하나가 컨트롤 둘을 감싸면 걸린다', () => {
    const found = unwrappedControls(
      `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS}">\n    <input className="x" />\n    <input type="checkbox" />\n  </label>\n);\n`,
    );

    expect(found.length).toBeGreaterThan(0);
  });

  /** `htmlFor` 가 다른 곳을 가리키면 그 레이블의 컨트롤은 이 체크박스가 아니다. */
  it('htmlFor 가 다른 곳을 가리키면 걸린다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label htmlFor="other" className="flex ${MIN_TOUCH_CLASS}">\n    <input type="checkbox" id="mine" />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
  });

  it('음성 — htmlFor 가 자기 컨트롤을 가리키면 초록이다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label htmlFor="mine" className="flex ${MIN_TOUCH_CLASS}">\n    <input type="checkbox" id="mine" />\n  </label>\n);\n`,
      ),
    ).toStrictEqual([]);
  });
});

describe('음성 대조 — 걸리면 안 되는 것', () => {
  it(`${MIN_TOUCH_CLASS} 를 든 레이블이 감싸면 걸리지 않는다`, () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS} items-center gap-1">\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toStrictEqual([]);
  });

  /** 글자 입력은 이 축 밖이다 — 누르는 자리가 컨트롤 자신이고 그 높이는 제 클래스가 든다. */
  it('체크박스·라디오가 아닌 input 은 이 축 밖이다', () => {
    expect(unwrappedControls('export const X = () => <input type="text" className="min-h-11" />;\n')).toStrictEqual(
      [],
    );
  });

  it('종류 표가 비어 있지 않다 — 비면 이 축이 공허하다', () => {
    expect([...WRAPPED_CONTROL_TYPES].sort()).toStrictEqual(['checkbox', 'radio']);
  });
});

/**
 * **암묵 레이블 위의 `aria-label` 0**(M14/14F · cr D4).
 *
 * 보이는 레이블이 이미 이름을 주는 컨트롤에 `aria-label` 을 덧붙이면 그 보이는 글자의 **일부가
 * 낭독에서 사라진다** — 지워지는 것이 형식 지시(「쉼표로 구분」)와 파괴적 결과 경고(「끄면 면허
 * 목록을 비워 보냅니다」)라, 눈으로 보는 운영자와 듣는 운영자가 **다른 지시**를 받는다.
 *
 * 저장소는 그 규칙을 이미 적어 두었다(`components/Button.tsx` 의 KDoc — 「보이는 글자가 있으면
 * 주지 않는다(낭독이 갈린다)」). **선언만 있고 재는 자리가 없었다.**
 *
 * 설계 검토는 `reports/evidence/m14/fullreview-fixes/design-review.md` 가 정본이다.
 */
describe('암묵 레이블 위의 이름 덮어쓰기 (M14/14F cr D4)', () => {
  const { files, findings } = scanAriaLabelOverrides(srcDir);

  it('훑기가 실제 소스를 읽었다 — 빈 훑기는 아래 등식을 공허하게 만든다', () => {
    expect(files.length).toBeGreaterThan(5);
    expect(files).toContain('screens/ProfileFactsCard.tsx');
  });

  it('출하 트리에 위반이 0 이다', () => {
    expect(
      findings.map((one) => `${one.file} ${one.why}`),
      '보이는 레이블이 있는 컨트롤에 이름 덮어쓰기가 남았다',
    ).toStrictEqual([]);
  });

  /** 재는 속성 집합이 비면 이 축이 통째로 공허하다. `aria-labelledby` 가 더 센 쪽이라 함께 든다. */
  it('재는 속성이 둘이다', () => {
    expect([...NAME_OVERRIDE_ATTRIBUTES].sort()).toStrictEqual(['aria-label', 'aria-labelledby']);
  });

  it('양성 — 암묵 레이블 안의 입력에 aria-label 이 있으면 걸린다', () => {
    const found = ariaLabelOverrides(
      'export const X = () => (\n  <label>\n    <span>업종(쉼표)</span>\n    <input aria-label="업종" />\n  </label>\n);\n',
    );

    expect(found).toHaveLength(1);
    expect(found[0].why).toContain('aria-label');
  });

  it('양성 — 더 센 aria-labelledby 로 갈음해도 걸린다', () => {
    expect(
      ariaLabelOverrides(
        'export const X = () => (\n  <label>\n    <span id="t">업종(쉼표)</span>\n    <input aria-labelledby="t" />\n  </label>\n);\n',
      ),
    ).toHaveLength(1);
  });

  /** **볼 수 없는 것을 통과시키지 않는다**(설계 검토 (2) ①) — 형제 술어와 같은 규율이다. */
  it('양성 — 펼친 속성 뒤에 숨긴 자리도 걸린다', () => {
    expect(
      ariaLabelOverrides(
        "export const X = () => (\n  <label>\n    <span>업종</span>\n    <input {...rest} />\n  </label>\n);\n",
      ),
    ).toHaveLength(1);
  });

  /** 체크박스·토글도 같은 자리다 — 거기서 사라지는 것이 파괴적 결과 경고다. */
  it('양성 — 암묵 레이블 안의 체크박스도 같은 의무를 진다', () => {
    expect(
      ariaLabelOverrides(
        'export const X = () => (\n  <label>\n    <input type="checkbox" aria-label="신고함" />\n    신고함 (끄면 목록을 비워 보냅니다)\n  </label>\n);\n',
      ),
    ).toHaveLength(1);
  });

  it('음성 — 레이블이 감싸지 않은 컨트롤의 aria-label 은 정당하다', () => {
    expect(ariaLabelOverrides('export const X = () => <button aria-label="조건 지우기">×</button>;\n')).toStrictEqual(
      [],
    );
  });

  it('음성 — 레이블 안이지만 이름을 덮지 않으면 초록이다', () => {
    expect(
      ariaLabelOverrides(
        'export const X = () => (\n  <label>\n    <span>업종(쉼표)</span>\n    <input name="types" />\n  </label>\n);\n',
      ),
    ).toStrictEqual([]);
  });

  /**
   * **레이블 자신의 이름 덮어쓰기도 같은 결함이다**(r1 v F1).
   *
   * 앞 판은 이 자리를 **음성 대조로 굳혔다** — 「레이블의 `aria-label` 은 컨트롤의 이름을 덮지
   * 않는다」는 전제 위에서였다. 그 전제는 **Chromium 에서 거짓이다**(verifier 실측:
   * `<label aria-label="업종"><span>업종(쉼표)</span><input/></label>` 에서 입력의 이름은 「업종」
   * 이다 — 막으려는 결함과 **같은 결과**다).
   *
   * 더 나쁜 것은 그 틈이 **술어가 만드는 회피로**라는 점이다: 입력의 `aria-label` 이 붉으면
   * 그것을 레이블로 옮기는 것이 가장 자연스러운 손질이고, 그 손질이 같은 낭독 결함을 낸다.
   */
  it('양성 — 레이블 요소 자신의 aria-label 도 걸린다', () => {
    const found = ariaLabelOverrides(
      'export const X = () => (\n  <label aria-label="업종">\n    <span>업종(쉼표)</span>\n    <input name="types" />\n  </label>\n);\n',
    );

    expect(found).toHaveLength(1);
    expect(found[0].why).toContain('aria-label');
  });

  it('양성 — 레이블 자신의 aria-labelledby 도 걸린다', () => {
    expect(
      ariaLabelOverrides(
        'export const X = () => (\n  <label aria-labelledby="t">\n    <span id="t">업종</span>\n    <input name="types" />\n  </label>\n);\n',
      ),
    ).toHaveLength(1);
  });

  /** 켤 컨트롤이 없는 레이블은 이 축 밖이다 — 덮을 이름이 없다. */
  it('음성 — labelable 자손이 없는 레이블의 aria-label 은 걸리지 않는다', () => {
    expect(
      ariaLabelOverrides('export const X = () => <label aria-label="업종">업종</label>;\n'),
    ).toStrictEqual([]);
  });

  /**
   * **명시 연결(`htmlFor`)이 같은 결함을 낸다**(r1 cr M①). 위협 모델이 적은 꼴 그대로인데 앞
   * 판의 술어는 **감싼 레이블만** 보아 통째로 놓쳤다. 오늘 출하 소스의 `htmlFor` 는 0 이라 live
   * 위반은 없지만, 그 0 은 이 꼴에 대해 **공허했다**.
   */
  it('양성 — htmlFor 로 이은 레이블 밖의 컨트롤도 걸린다', () => {
    const found = ariaLabelOverrides(
      'export const X = () => (\n  <div>\n    <label htmlFor="t">업종(쉼표)</label>\n    <input id="t" aria-label="업종" />\n  </div>\n);\n',
    );

    expect(found).toHaveLength(1);
    expect(found[0].why).toContain('aria-label');
  });

  /**
   * **반대쪽도 막는다**(r1 cr M① 의 과잉 축): 레이블의 `htmlFor` 가 **다른 컨트롤**을 가리키면
   * 그 레이블은 이 버튼에게 이름을 주지 않는다 — 버튼의 `aria-label` 은 정당하다. 과잉으로
   * 붉히면 유지보수자가 그것을 떼고 **이름 없는 컨트롤**이 남는다.
   */
  it('음성 — htmlFor 가 남을 가리키면 그 안의 다른 컨트롤은 자기 이름을 가질 수 있다', () => {
    expect(
      ariaLabelOverrides(
        'export const X = () => (\n  <label htmlFor="other">\n    <span>금액</span>\n    <input id="other" />\n    <button aria-label="조건 지우기">x</button>\n  </label>\n);\n',
      ),
    ).toStrictEqual([]);
  });

  /**
   * 암묵 연결에서 레이블이 켜는 것은 **첫 labelable 하나**다 — 뒤의 컨트롤은 이름을 받지 못하므로
   * 자기 `aria-label` 이 필요하다(같은 과잉 축).
   */
  it('음성 — 암묵 레이블의 둘째 컨트롤은 자기 이름을 가질 수 있다', () => {
    expect(
      ariaLabelOverrides(
        'export const X = () => (\n  <label>\n    <span>금액</span>\n    <input name="amount" />\n    <button aria-label="조건 지우기">x</button>\n  </label>\n);\n',
      ),
    ).toStrictEqual([]);
  });

  /**
   * **글자 없는 레이블의 사유가 달라야 한다**(r1 cr M②). 그 레이블은 이름을 주지 않으므로
   * 「덮는다」는 거짓이고, 그 말을 읽은 유지보수자의 자연스러운 손질(`aria-label` 떼기)은
   * **이름 없는 컨트롤**을 남긴다. 그래서 사유가 **다른 손질**을 가리켜야 한다.
   */
  it('글자 없는 레이블이 감싼 자리는 다른 사유를 낸다', () => {
    const found = ariaLabelOverrides(
      'export const X = () => (\n  <label className="x">\n    <input aria-label="검색어" />\n  </label>\n);\n',
    );

    expect(found).toHaveLength(1);
    expect(found[0].why, '덮는다고 말한다 — 그 레이블은 이름을 주지 않는다').not.toContain('덮는다');
    expect(found[0].why).toContain('감싸지 말고');
  });
});

/**
 * M13/13C — **최소 높이가 실제로 서는가**(13B v M-11). 레이블의 기본 display 는 inline 이고
 * `min-height` 는 inline 요소에 **적용되지 않는다** — 그래서 `min-h-11` 을 들고도 17px 로 서는
 * 꼴이 이 축을 지났다(verifier 가 Chromium 으로 실측했다). 어휘를 하나 더 요구해 그 틈을 닫는다.
 */
describe('실효 높이 — min-height 가 서는 display 를 함께 요구한다 (v M-11)', () => {
  it(`display 어휘가 없으면 ${MIN_TOUCH_CLASS} 를 들어도 걸린다`, () => {
    const found = unwrappedControls(
      `export const X = () => (\n  <label className="${MIN_TOUCH_CLASS}">\n    <input type="radio" />\n  </label>\n);\n`,
    );

    expect(found).toHaveLength(1);
  });

  it.each([
    ['상자가 없는 display', 'contents'],
    ['줄 안에 서는 display', 'inline'],
    ['그리지 않는 display', 'hidden'],
  ])('%s 은 증명이 되지 않는다', (_label, token) => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="${token} ${MIN_TOUCH_CLASS}">\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
  });

  /** 변형 접두가 붙은 display 는 **그 폭에서만** 선다 — 390px 에서 선다고 증명할 수 없다. */
  it('변형 접두가 붙은 display 는 증명이 되지 않는다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="md:flex ${MIN_TOUCH_CLASS}">\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
  });

  it.each([
    ['flex'],
    ['grid'],
    ['block'],
    ['inline-flex'],
    ['inline-block'],
    ['flow-root'],
    ['table'],
  ])('음성 — %s 는 min-height 가 서는 display 다', (token) => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="${token} ${MIN_TOUCH_CLASS}">\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toStrictEqual([]);
  });

  /**
   * **컴포넌트도 labelable 일 수 있다.** 레이블이 켜는 것은 첫 labelable 자손 하나인데, 그 자리에
   * 컴포넌트가 있으면 그것이 무엇을 그리는지 이 축은 모른다 — 보수적으로 **센다**.
   */
  it('레이블 안의 컴포넌트가 컨트롤보다 앞서면 걸린다', () => {
    const found = unwrappedControls(
      `declare const Thing: () => unknown;\nexport const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS}">\n    <Thing />\n    <input type="radio" />\n  </label>\n);\n`,
    );

    expect(found).toHaveLength(1);
  });

  /**
   * **자리를 보지 않고 센다 — 과차단 쪽이다.** 컨트롤 **뒤**의 컴포넌트는 HTML 상 켜지는 자리를
   * 바꾸지 않지만, 이 축은 「labelable 이 둘 이상이면 어느 것이 켜지는지 증명할 수 없다」는 앞
   * 판의 규칙(13B v M-9 · cr Q-4)을 그대로 쓴다. 문서 순서로 첫째를 고르는 쪽이 더 정밀하지만
   * 그것은 앞 판이 **일부러 막아 둔 자리를 푸는** 변경이라 이 slice 에서 하지 않는다.
   * 오늘 출하 트리에 레이블 안 컴포넌트가 0 이라 비용이 없다 — 알려진 제한으로 등재한다.
   */
  it('컨트롤 뒤의 컴포넌트도 보수적으로 센다 — 과차단', () => {
    expect(
      unwrappedControls(
        `declare const Thing: () => unknown;\nexport const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS}">\n    <input type="radio" />\n    <Thing />\n  </label>\n);\n`,
      ),
    ).toHaveLength(1);
  });

  it('음성 — 글자만 담은 span 은 labelable 이 아니다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS}">\n    <span>이름</span>\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toStrictEqual([]);
  });

  /** 어휘 집합이 **비어 있지 않다** — 비면 이 축이 모든 레이블을 거짓으로 센다. */
  it('display 어휘 집합이 비어 있지 않다', () => {
    expect(MIN_HEIGHT_DISPLAY_TOKENS.size).toBeGreaterThan(0);
  });
});

/**
 * M13/13C 수정 라운드 1 — verifier M-3. 설계 검토 B (2) 가 「display 를 `style` 로 주면 증명 불가」
 * 라고 적었는데 구현이 없었다. 덮어쓰는 display 어휘(변형 접두·우선 표시)도 같은 자리다 —
 * min-height 쪽은 이미 그렇게 센다(비대칭을 없앤다).
 */
describe('실효 높이 — 덮어쓰는 display 를 증명 불가로 센다 (v M-3)', () => {
  it.each([
    ['인라인 style 로 display 를 준 꼴', `<label className="flex ${MIN_TOUCH_CLASS}" style={{ display: 'inline' }}>`],
    ['인라인 style 로 최소 높이를 덮은 꼴', `<label className="flex ${MIN_TOUCH_CLASS}" style={{ minHeight: 0 }}>`],
    ['변형 접두로 덮은 display', `<label className="flex max-md:inline ${MIN_TOUCH_CLASS}">`],
    ['우선 표시로 덮은 display', `<label className="flex !inline ${MIN_TOUCH_CLASS}">`],
    ['그리지 않는 display 를 덧붙인 꼴', `<label className="flex hidden ${MIN_TOUCH_CLASS}">`],
    ['상자를 없애는 display 를 덧붙인 꼴', `<label className="flex contents ${MIN_TOUCH_CLASS}">`],
  ])('%s 은 증명이 되지 않는다', (_label, opening) => {
    expect(unwrappedControls(`export const X = () => (\n  ${opening}\n    <input type="checkbox" />\n  </label>\n);\n`)).toHaveLength(
      1,
    );
  });

  it('음성 — 덮어쓰지 않는 style 은 그대로 둔다', () => {
    expect(
      unwrappedControls(
        `export const X = () => (\n  <label className="flex ${MIN_TOUCH_CLASS}" style={{ gap: 4 }}>\n    <input type="checkbox" />\n  </label>\n);\n`,
      ),
    ).toStrictEqual([]);
  });
});
