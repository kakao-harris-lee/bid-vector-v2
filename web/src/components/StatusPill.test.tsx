// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { PILL_TONE_CLASS, PILL_TONES, StatusPill, pillClass } from './StatusPill';

/**
 * 상태 알약의 짝 test(M14/14A). 이 컴포넌트는 **모양만** 든다 — 어느 어조인지는 표시층이 정하고
 * (`StateBadge` 가 2층 라벨로 감싼다), 여기 있는 것은 어조 → 토큰 한 자리다.
 *
 * **색만으로 말하지 않는다**(§6.5): 라벨이 선택이 아니다. 색을 보지 못하는 사람과 흑백 출력에서
 * 알약이 아무 말도 하지 않는 자리를 타입이 막는다.
 */
afterEach(cleanup);

describe('어조 표', () => {
  /**
   * **완전성은 컴파일이 든다**(`satisfies Record<DisplayTone, …>`). 여기서는 그 표와 목록이
   * 같은 집합인지만 잰다 — 둘이 갈리면 아래 전수 루프가 조용히 줄어든다.
   */
  it('목록과 표가 같은 집합이다', () => {
    expect(new Set(PILL_TONES)).toStrictEqual(new Set(Object.keys(PILL_TONE_CLASS)));
  });

  it('어조마다 서로 다른 클래스를 낸다 — 두 어조가 한 얼굴이면 가를 수 없다', () => {
    const produced = PILL_TONES.map((one) => pillClass(one));

    expect(produced.length).toBeGreaterThan(1);
    expect(new Set(produced).size).toBe(produced.length);
  });

  /**
   * 「없음」·「아직 판정하지 않음」이 「미달」과 **다른 얼굴**이어야 한다(정직 명세). 이 둘이
   * 어느 날 `bad` 로 접히면 화면이 판정하지 않은 것을 미달로 말한다.
   */
  it('없음 · 미판정이 미달과 다른 얼굴이다', () => {
    expect(pillClass('absent')).not.toBe(pillClass('bad'));
    expect(pillClass('unjudged')).not.toBe(pillClass('bad'));
  });

  it('어조 전수가 바탕과 글자 토큰을 **둘 다** 든다 — 한 쪽만 있으면 대비를 잴 쌍이 없다', () => {
    for (const tone of PILL_TONES) {
      expect(pillClass(tone)).toContain(`bg-${tone}-skin`);
      expect(pillClass(tone)).toContain(`text-${tone}-ink`);
    }
  });
});

describe('라벨', () => {
  it('어조 전수에서 라벨 글자를 그린다', () => {
    for (const tone of PILL_TONES) {
      cleanup();
      render(<StatusPill tone={tone} label={`${tone} 라벨`} testId="probe" />);

      expect(screen.getByTestId('probe').textContent).toBe(`${tone} 라벨`);
    }
  });

  it('testId 가 없으면 속성을 내지 않는다', () => {
    const { container } = render(<StatusPill tone="neutral" label="입찰 중" />);

    expect(container.firstElementChild?.hasAttribute('data-testid')).toBe(false);
  });
});
