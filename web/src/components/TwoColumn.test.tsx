// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { TwoColumn } from './TwoColumn';
import { LAYOUT_SLOT_ATTRIBUTE, TWO_COLUMN_ATTRIBUTE } from './twoColumnShape';

/**
 * 두 단 배치의 짝 test(M14/14A). **상자 좌표는 여기서 설 수 없다** — jsdom 은 레이아웃을 풀지
 * 않는다. 그 축은 배치 게이트(출하 번들 · 1280/390)가 재고, 이 파일은 그 게이트가 재지 못하는
 * 것을 맡는다: **문서 순서**와 **표지**, 그리고 레일이 선택이 아닌 것.
 *
 * 왜 레일이 필수인가: 배치 게이트의 레일 모집단은 「표지를 단 요소 전수」다. 레일 없는 두 단을
 * 허용하면 표지는 있는데 잴 레일이 없는 자리가 생기고, 그때 게이트는 그 자리를 **건너뛸 수밖에
 * 없다** — 공허가 들어오는 입구다. 레일이 없는 화면은 이 컴포넌트를 쓰지 않는다.
 */
afterEach(cleanup);

/**
 * 칸 하나를 **찾지 못하면 던진다** — 옵셔널 체이닝으로 읽으면 칸이 사라진 날 단언이 조용히
 * `undefined` 를 비교하고 지나간다(공허가 들어오는 입구다).
 */
function slotOf(container: HTMLElement, slot: string): Element {
  const found = container.querySelector(`[${LAYOUT_SLOT_ATTRIBUTE}="${slot}"]`);
  if (found === null) {
    throw new Error(`두 단에서 「${slot}」 칸을 찾지 못했다`);
  }
  return found;
}

const mount = () =>
  render(
    <TwoColumn
      testId="probe"
      body={<p data-testid="body-child">본문</p>}
      rail={<p data-testid="rail-child">레일</p>}
    />,
  );

describe('표지', () => {
  it('바깥 상자가 두 단 표지를 단다 — 배치 게이트의 모집단이 이 표지다', () => {
    mount();

    expect(screen.getByTestId('probe').getAttribute(TWO_COLUMN_ATTRIBUTE)).not.toBeNull();
  });

  it('두 칸이 각자 자기 표지를 단다 — 어느 쪽이 본문인지 클래스 글자로 고르지 않는다', () => {
    const { container } = mount();
    const slots = [...container.querySelectorAll(`[${LAYOUT_SLOT_ATTRIBUTE}]`)];

    expect(slots.map((one) => one.getAttribute(LAYOUT_SLOT_ATTRIBUTE))).toStrictEqual(['body', 'rail']);
  });

  it('표지 값이 서로 다르다 — 같으면 게이트가 두 칸을 가르지 못한다', () => {
    const { container } = mount();
    const values = [...container.querySelectorAll(`[${LAYOUT_SLOT_ATTRIBUTE}]`)].map((one) =>
      one.getAttribute(LAYOUT_SLOT_ATTRIBUTE),
    );

    expect(new Set(values).size).toBe(values.length);
  });
});

describe('문서 순서', () => {
  /**
   * **좁은 화면의 한 단에서는 문서 순서가 곧 읽는 순서다**(계약 「레일은 본문 뒤」). 넓은 화면의
   * 좌우는 상자로 재지만, 이 축은 상자가 없는 폭에서도 참이어야 한다.
   */
  it('본문이 레일보다 앞이다', () => {
    const { container } = mount();
    const body = slotOf(container, 'body');
    const rail = slotOf(container, 'rail');

    expect(body.compareDocumentPosition(rail) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('자식이 자기 칸 안에 든다 — 두 칸이 섞이지 않는다', () => {
    const { container } = mount();
    const body = slotOf(container, 'body');
    const rail = slotOf(container, 'rail');

    expect(body.contains(screen.getByTestId('body-child'))).toBe(true);
    expect(rail.contains(screen.getByTestId('rail-child'))).toBe(true);
    expect(body.contains(screen.getByTestId('rail-child'))).toBe(false);
  });
});

describe('요소의 신원', () => {
  it('레일은 본문 곁의 보조 묶음이다 — 두 칸이 같은 요소가 아니다', () => {
    const { container } = mount();

    expect(slotOf(container, 'rail').tagName).toBe('ASIDE');
  });

  it('testId 가 없으면 속성을 내지 않는다', () => {
    const { container } = render(<TwoColumn body={<p>본문</p>} rail={<p>레일</p>} />);

    expect(container.firstElementChild?.hasAttribute('data-testid')).toBe(false);
  });
});
