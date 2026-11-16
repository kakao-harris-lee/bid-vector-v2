// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { Card, CARD_ELEMENTS, CARD_PADDINGS, cardClass } from './Card';

/**
 * 공유 카드의 짝 test(M13/13B). **요소를 고를 수 있는 것**이 이 컴포넌트의 요점이다 — 목록 안의
 * 카드가 `div` 가 되면 `ul` 의 내용 모형이 깨지고, 그 깨짐은 어느 화면 test 도 말하지 않는다.
 */
afterEach(cleanup);

describe('요소 축', () => {
  it('고를 수 있는 내장 태그 넷을 그대로 그린다', () => {
    for (const element of CARD_ELEMENTS) {
      cleanup();
      render(
        <Card as={element} testId="probe">
          내용
        </Card>,
      );

      expect(screen.getByTestId('probe').tagName).toBe(element.toUpperCase());
    }
  });

  it('자식을 그대로 담는다', () => {
    render(
      <Card as="div" testId="probe">
        <span data-testid="child">안쪽</span>
      </Card>,
    );

    expect(screen.getByTestId('probe').contains(screen.getByTestId('child'))).toBe(true);
  });

  /** 표지 없이도 선다 — 카드가 testId 를 요구하면 장식 자리에 쓸 수 없다. */
  it('testId 가 없으면 속성을 내지 않는다', () => {
    const { container } = render(<Card as="div">내용</Card>);

    expect(container.firstElementChild?.hasAttribute('data-testid')).toBe(false);
  });
});

describe('여백 표', () => {
  it('여백 둘이 서로 다른 클래스를 낸다', () => {
    const produced = CARD_PADDINGS.map((one) => cardClass(one));

    expect(produced).toHaveLength(2);
    expect(new Set(produced).size).toBe(produced.length);
  });

  it('기본 여백이 표 안의 값이다 — 표 밖 기본값을 지어내지 않는다', () => {
    expect(CARD_PADDINGS).toContain('md');
    expect(cardClass()).toBe(cardClass('md'));
  });

  it('여백이 달라도 테두리·바탕은 같다', () => {
    const [first, ...rest] = CARD_PADDINGS.map((one) =>
      cardClass(one)
        .split(' ')
        .filter((token) => !token.startsWith('p-'))
        .join(' '),
    );

    expect(rest.every((one) => one === first)).toBe(true);
  });
});
