// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { StateDisplay } from '../display/stateDisplay';

import { StateBadge } from './StateBadge';

/**
 * **2층 라벨의 둘째 층**(M13/13E, 받는 항목 11). 앞 판에서 아래층(`why`)이 **눈에 보이는
 * 쪽은 툴팁 속성뿐**이라 터치 기기에서 볼 방법이 없었다 — 보조기기 경로는 `aria-describedby` +
 * 시각 은폐 문면으로 이미 닫혀 있었고, 손가락만 닫히지 않았다.
 *
 * 이 파일이 잠그는 것은 셋이다.
 * ⓐ **탭하면 펼쳐진다**(터치 경로) — 그 자리는 내장 `button` 이다.
 * ⓑ **보조기기 배선을 없애지 않았다**(접힌 상태에서도 `aria-describedby` 가 그 문면을 가리킨다).
 * ⓒ **같은 문면이 접근성 트리에 한 번만 있다** — 사유 노드는 `aria-hidden` 이라 본문 텍스트로는
 *    서지 않고 **버튼의 설명으로만** 전달된다.
 *
 * **왜 `aria-expanded`·`aria-controls` 가 없는가**(수정 라운드 1, verifier F-1 · 리뷰 M-2):
 * 펼침은 **보이는 사용자를 위한 시각 장치**다. 보조기기 사용자는 접힘·펼침과 무관하게 설명으로
 * 같은 문면을 받으므로, 「접혀 있다」고 선언하면서 그 내용을 트리에 그대로 두는 것은 모순이고
 * 실제로 **두 번 전달**됐다(접힌 상태에서도 설명 1 + 본문 텍스트 1 — CDP AX 트리로 실측).
 *
 * **이 파일이 재는 층은 DOM 이다.** 접근성 트리의 「정확히 한 번」은 상자와 마찬가지로 브라우저가
 * 있어야 서므로 **390px 여정**이 CDP 로 재고, 여기서는 그 조건(`aria-hidden` · 설명 배선 · 노드
 * 하나)을 잠근다.
 *
 * **펼침 여부는 `data-expanded` 로 잰다 — 클래스 글자로 재지 않는다**(13B 알려진 제한 27):
 * Tailwind 의 후보 훑기가 `src/` 에 묶여 있지 않아 test 에 적은 클래스 꼴이 출하 CSS 로 실린다.
 */
const WHY = '미달이 아니라 판정하지 못했다';

const WITH_WHY: StateDisplay = { label: '면허 판정 불가', why: WHY, tone: 'unjudged' };
const WITHOUT_WHY: StateDisplay = { label: '입찰 중', tone: 'neutral' };

afterEach(() => {
  cleanup();
});

/** 그 문면을 **텍스트로 담은 요소**의 수 — 사본이 생기면 는다. */
function nodesWithWhy(root: HTMLElement, why: string): readonly Element[] {
  return [...root.querySelectorAll('*')].filter((one) => one.textContent === why);
}

/** 아래층 문면의 자리 — **설명으로 가리키는** 바로 그 노드다(사본을 찾지 않는다). */
function whyNodeOf(badge: HTMLElement): HTMLElement | null {
  return document.getElementById(badge.getAttribute('aria-describedby') ?? '');
}

describe('2층 라벨 — 아래층이 터치에서도 선다', () => {
  it('`why` 가 있으면 그 자리가 버튼이고 접힌 상태로 시작한다', () => {
    render(<StateBadge display={WITH_WHY} testId="badge" />);

    const badge = screen.getByTestId('badge');
    expect(badge.tagName, 'why 가 있는 배지가 누를 수 없는 자리에 있다').toBe('BUTTON');
    expect(whyNodeOf(badge)?.getAttribute('data-expanded'), '접힌 아래층이 처음부터 펼쳐져 있다').toBe('false');
  });

  /**
   * **접근성 트리에 두 번 서지 않는다**(F-1 · M-2). 사유 노드는 설명으로만 전달되고 본문
   * 텍스트로는 서지 않는다 — 그 수단이 `aria-hidden` 이다. 직접 참조된 숨김 노드라 설명 계산은
   * 그대로 선다(CDP 실측: 접힘·펼침 둘 다 설명 1 · 본문 0).
   */
  it('사유 노드가 접힘·펼침 둘 다 접근성 트리에서 숨어 있다', () => {
    render(<StateBadge display={WITH_WHY} testId="badge" />);

    const badge = screen.getByTestId('badge');
    expect(whyNodeOf(badge)?.getAttribute('aria-hidden'), '사유가 본문 텍스트로도 읽힌다').toBe('true');

    fireEvent.click(badge);

    expect(whyNodeOf(badge)?.getAttribute('aria-hidden'), '펼치면서 본문 텍스트가 됐다').toBe('true');
  });

  /**
   * **「접혀 있다」고 선언하지 않는다**(F-1 처방). 보조기기는 접힘·펼침과 무관하게 설명으로 같은
   * 문면을 받으므로, 상태를 선언하면 그 선언이 거짓이 된다.
   */
  it('펼침 상태를 ARIA 로 선언하지 않는다 — 시각 장치일 뿐이다', () => {
    render(<StateBadge display={WITH_WHY} testId="badge" />);

    const badge = screen.getByTestId('badge');
    expect(badge.hasAttribute('aria-expanded'), '내용이 트리에 늘 있는데 접혔다고 선언한다').toBe(false);
    expect(badge.hasAttribute('aria-controls'), '숨긴 노드를 가리키는 선언이 남았다').toBe(false);
  });

  it('누르면 아래층 문면이 펼쳐지고, 다시 누르면 접힌다', () => {
    render(<StateBadge display={WITH_WHY} testId="badge" />);

    const badge = screen.getByTestId('badge');
    const why = whyNodeOf(badge);
    expect(why, 'aria-describedby 가 사유 노드를 가리키지 않는다').not.toBeNull();

    fireEvent.click(badge);

    expect(why?.getAttribute('data-expanded')).toBe('true');
    expect(why?.textContent).toBe(WHY);

    // 펼침이 한 방향 문이 아니다 — 접으면 보조기기 경로만 남는다.
    fireEvent.click(badge);

    expect(why?.getAttribute('data-expanded')).toBe('false');
    expect(why?.textContent, '접으면서 문면까지 사라졌다 — 보조기기가 읽을 것이 없다').toBe(WHY);
  });

  /**
   * **보조기기 경로를 없애지 않았다**(계약 13E 행의 명시 조건). 이 배선이 문면 게이트(13A)가
   * 사유 문자열을 보는 자리이기도 하다.
   */
  it('접힌 상태에서도 `aria-describedby` 가 그 문면을 가리킨다', () => {
    render(<StateBadge display={WITH_WHY} testId="badge" />);

    const badge = screen.getByTestId('badge');
    const describedBy = badge.getAttribute('aria-describedby');
    expect(describedBy, 'aria-describedby 가 사라졌다 — 보조기기 경로가 닫혔다').not.toBeNull();
    expect(document.getElementById(describedBy ?? '')?.textContent).toBe(WHY);
  });

  /**
   * **두 번 낭독되지 않는다.** 펼침을 「또 하나의 문면을 그리는 것」으로 지으면 보조기기가
   * 설명과 본문에서 같은 말을 두 번 읽는다. 펼침은 **같은 노드**의 모양만 바꾼다.
   */
  it('그 문면을 담은 노드가 접힐 때도 펼칠 때도 하나다', () => {
    const view = render(<StateBadge display={WITH_WHY} testId="badge" />);

    expect(nodesWithWhy(view.container, WHY)).toHaveLength(1);

    fireEvent.click(screen.getByTestId('badge'));

    expect(nodesWithWhy(view.container, WHY), '펼치면서 문면의 사본이 생겼다').toHaveLength(1);
    // 그 하나가 **describedby 가 가리키는 바로 그 노드**다(사본이 아니라 같은 자리다).
    const describedBy = screen.getByTestId('badge').getAttribute('aria-describedby');
    expect(nodesWithWhy(view.container, WHY)[0]).toBe(document.getElementById(describedBy ?? ''));
  });

  /**
   * **툴팁 속성은 걷었다**(팀장 착수 판정 ⓑ). 손가락·마우스가 같은 길을 쓰므로 툴팁이 할 일이
   * 없다. 다만 그 문면은 **DOM 텍스트 노드로 남아야** 한다 — 문면 게이트(13A)의 술어는 **잎
   * 요소의 `textContent` 만** 읽고 속성은 모집단에 든 적이 없다(실측, 리뷰 L-6 의 정정). 그래서
   * 속성으로만 나르면 그 게이트의 눈 밖으로 나간다. `aria-hidden` 은 접근성 트리의 축이고
   * 이 훑기와 무관하다(같은 실측).
   */
  it('툴팁 속성을 더 쓰지 않으면서 그 문면을 DOM 텍스트로 남긴다', () => {
    const view = render(<StateBadge display={WITH_WHY} testId="badge" />);

    expect(screen.getByTestId('badge').hasAttribute('title')).toBe(false);
    expect(view.container.querySelector('[title]'), '배지 안에 툴팁 속성이 남았다').toBeNull();
    expect(nodesWithWhy(view.container, WHY)).toHaveLength(1);
  });

  it('접근 이름은 라벨뿐이다 — 펼침 자리가 이름을 더럽히지 않는다', () => {
    render(<StateBadge display={WITH_WHY} testId="badge" />);

    expect(screen.getByRole('button', { name: WITH_WHY.label }).getAttribute('data-testid')).toBe('badge');
  });

  /** **아래층이 없으면 펼칠 것이 없다** — 빈 펼침 버튼을 지어내지 않는다(가짜 affordance 금지). */
  it('`why` 가 없으면 버튼이 아니다', () => {
    const view = render(<StateBadge display={WITHOUT_WHY} testId="badge" />);

    const badge = screen.getByTestId('badge');
    expect(badge.tagName).toBe('SPAN');
    expect(badge.textContent).toBe(WITHOUT_WHY.label);
    expect(within(view.container).queryByRole('button')).toBeNull();
    expect(badge.hasAttribute('aria-expanded')).toBe(false);
  });

  /**
   * **표 밖의 값도 보인다**(CR M-3 승계). 그 미지 상태는 `why` 를 들고 오므로 펼칠 수 있다 —
   * 「이 화면이 아직 모르는 상태 값이다」가 바로 운영자가 읽어야 하는 아래층이다.
   */
  it('표 밖의 값은 조용히 사라지지 않고, 그 사유도 펼쳐진다', () => {
    render(<StateBadge display={undefined} testId="badge" />);

    const badge = screen.getByTestId('badge');
    expect(badge.textContent ?? '').toContain('알 수 없는 상태');
    fireEvent.click(badge);
    expect(whyNodeOf(badge)?.textContent ?? '').toContain('아직 모르는 상태');
    expect(whyNodeOf(badge)?.getAttribute('data-expanded')).toBe('true');
  });

  /**
   * **같은 화면에 배지가 둘일 때 서로의 자리를 가리키지 않는다**(`useId` 가 그 자리다). 한
   * 배지를 펼쳤을 때 다른 배지가 함께 펼쳐지면 id 가 겹친 것이다 — 배지는 45 자리에 흩어져
   * 있어 한 화면에 여럿이 서는 것이 보통이다.
   */
  it('배지 둘이 서로 다른 사유 자리를 가지고 따로 펼쳐진다', () => {
    render(
      <>
        <StateBadge display={WITH_WHY} testId="first" />
        <StateBadge display={{ ...WITH_WHY, why: '다른 사유다' }} testId="second" />
      </>,
    );

    const first = screen.getByTestId('first');
    const second = screen.getByTestId('second');
    expect(first.getAttribute('aria-describedby')).not.toBe(second.getAttribute('aria-describedby'));

    fireEvent.click(first);

    expect(whyNodeOf(first)?.getAttribute('data-expanded')).toBe('true');
    expect(whyNodeOf(second)?.getAttribute('data-expanded'), '한 배지를 펼치니 다른 배지도 펼쳐졌다').toBe('false');
  });
});
