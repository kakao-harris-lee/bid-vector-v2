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

  /** 접두가 같은 다른 어휘(`min-h-110`)로 지나가지 않는다 — 칸으로 가른다. */
  it('비슷한 이름의 다른 어휘는 그 어휘가 아니다', () => {
    expect(
      unwrappedControls(
        'export const X = () => (\n  <label className="flex min-h-110 items-center">\n    <input type="radio" />\n  </label>\n);\n',
      ),
    ).toHaveLength(1);
  });

  /** `type` 을 식으로 적어 **증명을 피하는** 길도 같은 의무를 진다. */
  it('type 이 식이면 레이블 밖에 둘 수 없다', () => {
    expect(unwrappedControls('export const X = () => <input type={kind} />;\n')).toHaveLength(1);
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
