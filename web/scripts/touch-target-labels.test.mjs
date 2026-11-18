import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  MIN_TOUCH_CLASS,
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
