// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { KeyValueRow } from './KeyValueRow';

afterEach(() => {
  cleanup();
});

/**
 * **용어 줄의 둘째 층**(M14/14A — M13 잔여 §12.3 (w) ⓖ). 상태 배지의 아래층은 13E 가 닫았고
 * 여기만 열려 있었다: 용어의 사유가 **속성 하나**(`title`)라 손가락으로 볼 방법이 없었다.
 *
 * 잠그는 것은 상태 배지와 같은 셋이다 — 탭하면 펼쳐지고, 보조기기 배선이 접힌 상태에서도 서며,
 * 같은 문면의 노드가 **하나**다. 실제 상자 높이·폭은 390px 여정이 재므로 여기서 복제하지 않는다.
 */
describe('용어 줄의 아래층 — KeyValueRow (M14/14A)', () => {
  const HINT = '투찰 기준 금액. 추정가격과 다르다.';

  const termButton = () => screen.getByRole('button', { name: '기초금액' });
  const hintNodeOf = (button: HTMLElement): HTMLElement | null => {
    const id = button.getAttribute('aria-describedby');
    return id === null ? null : document.getElementById(id);
  };

  it('아래층이 없으면 라벨이 버튼이 아니다 — 눌러도 아무 일 없는 자리를 만들지 않는다', () => {
    render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" testId="probe" />
      </dl>,
    );

    expect(screen.queryByRole('button')).toBeNull();
  });

  it('아래층이 있으면 라벨이 누를 수 있는 자리다', () => {
    render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" hint={HINT} testId="probe" />
      </dl>,
    );

    expect(termButton().tagName).toBe('BUTTON');
  });

  it('탭하면 아래층이 펼쳐지고 다시 탭하면 접힌다', () => {
    render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" hint={HINT} testId="probe" />
      </dl>,
    );
    const button = termButton();

    expect(hintNodeOf(button)?.getAttribute('data-expanded')).toBe('false');

    fireEvent.click(button);
    expect(hintNodeOf(button)?.getAttribute('data-expanded')).toBe('true');

    fireEvent.click(button);
    expect(hintNodeOf(button)?.getAttribute('data-expanded')).toBe('false');
  });

  /** 접힘·펼침은 **보는 사람의 몫**이다 — 그 버튼 자신의 두 상태라 `aria-pressed` 다. */
  it('토글 상태를 aria-pressed 로 말한다', () => {
    render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" hint={HINT} testId="probe" />
      </dl>,
    );

    expect(termButton().getAttribute('aria-pressed')).toBe('false');
    fireEvent.click(termButton());
    expect(termButton().getAttribute('aria-pressed')).toBe('true');
  });

  it('접힌 상태에서도 보조기기 배선이 선다 — 아래층을 가리키고 그 글자를 든다', () => {
    render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" hint={HINT} testId="probe" />
      </dl>,
    );
    const hint = hintNodeOf(termButton());

    expect(hint).not.toBeNull();
    expect(hint?.textContent).toBe(HINT);
    expect(hint?.getAttribute('aria-hidden')).toBe('true');
  });

  /** 사본을 만들면 같은 말이 두 번 낭독된다 — 노드는 **하나**다. */
  it('같은 문면의 노드가 하나다 — 펼쳐도 늘지 않는다', () => {
    const { container } = render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" hint={HINT} testId="probe" />
      </dl>,
    );
    const count = () => [...container.querySelectorAll('*')].filter((one) => one.textContent === HINT).length;

    expect(count()).toBe(1);
    fireEvent.click(termButton());
    expect(count()).toBe(1);
  });

  /** 걷은 길 — 같은 문면이 속성으로도 살면 자리가 셋이 된다(13E 가 배지에서 걷은 것과 같다). */
  it('툴팁 속성으로 같은 문면을 또 싣지 않는다', () => {
    const { container } = render(
      <dl>
        <KeyValueRow label="기초금액" value="100원" hint={HINT} testId="probe" />
      </dl>,
    );

    expect([...container.querySelectorAll('[title]')]).toStrictEqual([]);
  });
});
