// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { absenceTextOf } from '../display/absence';
import { KeyValueRow, useTermHint } from './KeyValueRow';

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

/**
 * **빈 글자도 부재다**(cr L-1). `?? ` 만 두면 `''` 가 그 분기를 비켜 가 아무것도 그리지 않는다 —
 * 이 slice 가 없애려는 「조용한 빈칸」이 호출부 인자 하나로 되살아나는 자리다.
 */
describe('빈 값 (cr L-1)', () => {
  const absent = absenceTextOf('notSent');

  it('빈 글자를 부재로 그린다', () => {
    render(
      <dl>
        <KeyValueRow label="상호" value="" testId="probe" />
      </dl>,
    );

    expect(screen.getByTestId('probe').textContent).toBe(absent);
  });

  it('공백뿐인 값도 부재다', () => {
    render(
      <dl>
        <KeyValueRow label="상호" value="   " testId="probe" />
      </dl>,
    );

    expect(screen.getByTestId('probe').textContent).toBe(absent);
  });

  it('값이 있으면 그대로 그린다 — 음성 대조', () => {
    render(
      <dl>
        <KeyValueRow label="상호" value="제주바다건설(주)" testId="probe" />
      </dl>,
    );

    expect(screen.getByTestId('probe').textContent).toBe('제주바다건설(주)');
  });
});

/**
 * **표지를 받는 자리**(M14/14B 가 더한 인자). 더하는 쪽으로만 넓혔으므로 ⓐ 주지 않으면 앞과
 * 같고 ⓑ 주면 **배선을 쥔 그 버튼**에 붙는다 — 바깥에 두르면 `aria-describedby` 를 표지 노드에서
 * 읽을 수 없어 「아래층으로 가는 길」을 한 자리에서 잴 수 없다.
 */
describe('용어 줄의 표지 인자 — useTermHint (M14/14B)', () => {
  function Probe({ testId }: { readonly testId?: string }) {
    const hint = useTermHint('아래층 문면', testId === undefined ? undefined : { testId });
    return (
      <p>
        {hint.termNode('라벨')}
        {hint.hintNode}
      </p>
    );
  }

  it('표지를 주지 않으면 앞과 같다 — 음성 대조', () => {
    render(<Probe />);

    expect(screen.getByRole('button', { name: '라벨' }).getAttribute('data-testid')).toBeNull();
  });

  it('표지가 배선을 쥔 버튼에 붙는다', () => {
    render(<Probe testId="probe-term" />);

    const button = screen.getByTestId('probe-term');
    expect(button.tagName).toBe('BUTTON');
    const described = document.getElementById(button.getAttribute('aria-describedby') ?? '');
    expect(described?.textContent).toBe('아래층 문면');
  });
});
