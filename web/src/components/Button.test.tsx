// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import {
  Button,
  BUTTON_SIZES,
  BUTTON_TONES,
  controlClass,
  LinkButton,
  type ButtonSize,
  type ButtonTone,
} from './Button';

/**
 * 공유 버튼의 짝 test(M13/13B). 이 파일이 잠그는 것은 **표의 전수**와 **요소의 신원**이다 —
 * 실제 상자 높이가 44px 인지는 클래스 글자로 설 수 없고 터치 타깃 게이트(390px)가 잰다.
 */
afterEach(cleanup);

const renderWithRouter = (node: React.ReactNode) =>
  render(<RouterProvider router={createMemoryRouter([{ path: '/', element: node }])} />);

describe('어조·크기 표의 전수 (M13/13B)', () => {
  /**
   * 유니온이 목록에서 유도되므로 「목록 밖의 어조」는 **컴파일되지 않는다**. 여기서 재는 것은
   * 그 목록이 **비어 있지 않고 서로 다른 클래스를 낸다**는 것 — 표가 한 값으로 접히면 어조가
   * 셋이라는 주장이 공허해진다.
   */
  it('어조 셋 · 크기 둘이 서로 다른 클래스를 낸다', () => {
    expect(BUTTON_TONES.length).toBe(3);
    expect(BUTTON_SIZES.length).toBe(2);

    const produced = BUTTON_TONES.flatMap((tone) => BUTTON_SIZES.map((size) => controlClass(tone, size)));

    expect(new Set(produced).size).toBe(produced.length);
  });

  /**
   * **크기 축이 높이를 말하는가.** 클래스 글자를 세는 단언은 상자를 재지 못하지만, 「크기 둘이
   * 같은 최소 높이를 쓴다」는 꼴(크기 축이 아무것도 안 하는 변이)은 여기서 멈춘다.
   */
  it('크기 둘이 서로 다른 최소 높이 유틸리티를 쓴다', () => {
    const heights = BUTTON_SIZES.map((size) => {
      const found = controlClass('primary', size)
        .split(' ')
        .filter((one) => one.startsWith('min-h-'));

      expect(found, `크기 ${size} 에 최소 높이가 없다`).toHaveLength(1);
      return found[0];
    });

    expect(new Set(heights).size).toBe(BUTTON_SIZES.length);
  });

  it('어조마다 다른 바탕·테두리를 쓴다 — 어조 축이 같은 값으로 접히지 않는다', () => {
    const tones = BUTTON_TONES.map((tone) => controlClass(tone, 'md').replace(controlClass('primary', 'md'), ''));

    expect(new Set(tones).size).toBe(BUTTON_TONES.length);
  });
});

describe('요소의 신원', () => {
  it('Button 은 button 이고 기본 type 이 button 이다', () => {
    render(<Button testId="probe">누르기</Button>);
    const found = screen.getByTestId('probe');

    expect(found.tagName).toBe('BUTTON');
    // 폼 안의 버튼이 **말없이 제출하지 않는다** — 기본이 submit 인 HTML 과 반대로 둔 자리다.
    expect(found.getAttribute('type')).toBe('button');
  });

  it('type="submit" 을 주면 그대로 submit 이다', () => {
    render(
      <Button testId="probe" type="submit">
        보내기
      </Button>,
    );

    expect(screen.getByTestId('probe').getAttribute('type')).toBe('submit');
  });

  it('disabled 를 주면 비활성이다', () => {
    render(
      <Button testId="probe" disabled>
        누르기
      </Button>,
    );

    expect(screen.getByTestId<HTMLButtonElement>('probe').disabled).toBe(true);
  });

  /** 이동은 **링크**다 — 버튼으로 그리면 새 탭·주소 복사 같은 브라우저의 일이 사라진다. */
  it('LinkButton 은 a 이고 href 를 든다', () => {
    renderWithRouter(
      <LinkButton to="/bids" testId="probe">
        입찰함으로
      </LinkButton>,
    );
    const found = screen.getByTestId('probe');

    expect(found.tagName).toBe('A');
    expect(found.getAttribute('href')).toBe('/bids');
  });

  /**
   * 둘이 **같은 표**를 쓴다. 사본이 생기면 버튼만 고치고 링크가 남는 꼴이 되고, 터치 타깃
   * 게이트는 그것을 한참 뒤(390px 실행)에야 말한다.
   */
  it('Button 과 LinkButton 이 같은 어조·크기 클래스를 낸다', () => {
    render(
      <Button testId="as-button" tone="primary" size="lg">
        누르기
      </Button>,
    );
    renderWithRouter(
      <LinkButton to="/" testId="as-link" tone="primary" size="lg">
        가기
      </LinkButton>,
    );

    expect(screen.getByTestId('as-link').className).toBe(screen.getByTestId('as-button').className);
  });

  it('fullWidth 는 두 꼴 모두에서 같은 한 가지를 더한다', () => {
    render(
      <Button testId="wide" fullWidth>
        누르기
      </Button>,
    );
    render(<Button testId="narrow">누르기</Button>);

    const wide = screen.getByTestId('wide').className;
    const narrow = screen.getByTestId('narrow').className;

    expect(wide).toBe(`${narrow} w-full`);
  });
});

/**
 * **공유 컨트롤의 경계 토큰**(M14/14A · 팀장 판정 2026-11-20).
 *
 * 대비 게이트가 `control-line` 을 3:1 로 재는데, 컨트롤이 **다른 토큰**을 쓰면 그 측정은
 * 아무것도 지키지 못한다. 손으로 적은 입력칸은 AST 축(`scripts/control-line.test.mjs`)이 받지만
 * **이 컴포넌트의 어조 표는 그 축 밖**이다 — JSX 의 className 이 식(`controlClass(…)`)이라
 * 리터럴이 요소에 붙어 있지 않기 때문이다(변이 실측: 표를 되돌려도 그 축은 초록이었다).
 *
 * 그래서 표를 **값으로** 잰다. 어조 전수를 돌므로 새 어조가 들어와도 함께 든다.
 */
describe('경계 토큰 (M14/14A)', () => {
  const CARD_LINE = 'border-line';
  const CONTROL_LINE = 'border-control-line';

  const classTokens = (tone: ButtonTone, size: ButtonSize): readonly string[] => controlClass(tone, size).split(/\s+/u);

  it('어조 전수에서 카드 토큰을 쓰지 않는다', () => {
    for (const tone of BUTTON_TONES) {
      for (const size of BUTTON_SIZES) {
        expect(classTokens(tone, size), `${tone}/${size}`).not.toContain(CARD_LINE);
      }
    }
  });

  /**
   * **비공허** — 테두리를 가진 어조가 **실제로** 컨트롤 토큰을 쓴다. 위 단언만으로는 테두리를
   * 전부 없앤 변이도 지나간다(그때 카드 토큰은 당연히 없다).
   *
   * 색을 **어조 자신에서** 끌어오는 테두리(`danger` 의 경고 색)는 그 축이 다르다 — 중립 경계가
   * 아니라 **그 버튼이 무엇인지**를 말하는 색이고, 중립 토큰으로 바꾸면 그 신호가 사라진다.
   * 그래서 테두리 색 클래스는 **둘 중 하나**여야 한다: 컨트롤 토큰이거나 어조 자신의 색.
   */
  it('테두리 색이 컨트롤 토큰이거나 어조 자신의 색이다', () => {
    const borderColors = BUTTON_TONES.filter((tone) => classTokens(tone, 'md').includes('border')).map((tone) => ({
      tone,
      colors: classTokens(tone, 'md').filter((one) => one.startsWith('border-')),
    }));

    expect(borderColors.length, '테두리를 가진 어조가 하나도 없다').toBeGreaterThan(0);
    for (const { tone, colors } of borderColors) {
      expect(colors.length, tone).toBe(1);
      // 어조 이름과 토큰 이름이 1:1 이 아니다(`danger` 의 색은 `bad` 다) — 그래서 이름을 맞대지
      // 않고 **어조 글자색 토큰을 가리키는가**로 본다.
      const ownTone = /^border-[a-z]+-ink(\/\d+)?$/u.test(colors[0] ?? '');
      expect(colors[0] === CONTROL_LINE || ownTone, `${tone}: ${colors[0]}`).toBe(true);
    }
    // 적어도 하나는 **중립 컨트롤 토큰**이다 — 전부 어조 색이면 그 토큰이 어디에도 닿지 않는다.
    expect(borderColors.some(({ colors }) => colors[0] === CONTROL_LINE)).toBe(true);
  });
});
