import { createElement, type ReactNode } from 'react';

/**
 * 공유 카드(M13/13B) — 테두리·모서리·바탕·여백 **한 자리**다.
 *
 * 앞 판은 같은 네 유틸리티가 열세 파일에 복제돼 있었고(착수 실측: `rounded-lg border border-line`
 * 꼴 21곳 / 13 파일), 한 자리를 고치면 나머지가 갈렸다.
 *
 * **요소를 닫힌 유니온으로 받는다.** 목록 안의 카드는 `li` 여야 하고(그러지 않으면 `ul` 의 내용
 * 모형이 깨진다) 화면 절은 `section` 이다. 다형 `as` 를 임의 컴포넌트로 열지 않는 이유는 그
 * 순간 props 가 합집합이 되어 타입이 흐려지기 때문이다 — 여기서 여는 것은 **내장 태그 넷**뿐이다.
 *
 * **표시층을 들여오지 않는다.** 들여오는 순간 이 파일이 어휘 게이트의 **의무**가 되고
 * `GATE_MOUNTED_CARDS` 등식이 붉는다 — 그 등식이 이 규율의 측정 자리다(설계 검토 §2b).
 */
export const CARD_ELEMENTS = ['li', 'div', 'section', 'article'] as const;

export type CardElement = (typeof CARD_ELEMENTS)[number];

/** 안쪽 여백 — 카드가 **줄**이면 좁고(`sm`), 묶음이면 보통(`md`), 얼굴이면 넓다(`lg`). */
export const CARD_PADDINGS = ['sm', 'md', 'lg'] as const;

export type CardPadding = (typeof CARD_PADDINGS)[number];

const PADDING_CLASS = {
  sm: 'p-3',
  md: 'p-4',
  // 얼굴(빈·오류·세션 없음)처럼 **가운데 정렬된 한 덩어리**가 쓰는 넓은 여백. 앞 판은 그 자리가
  // `cardClass()` 에 `p-6` 을 덧붙여 **`p-4` 와 `p-6` 이 한 요소에** 섰고, 어느 쪽이 이기는지는
  // Tailwind 의 출력 순서에 달려 있었다(클래스 문자열 순서가 아니다 — cr L-3).
  lg: 'p-6',
} as const satisfies Record<CardPadding, string>;

const BASE_CLASS = 'rounded-lg border border-line bg-card';

/**
 * 카드 한 장의 클래스 — `Card` 가 받을 수 없는 자리(자기 포커스 배선을 쥔 행)가 같은 표를 쓴다.
 */
export function cardClass(padding: CardPadding = 'md'): string {
  return `${BASE_CLASS} ${PADDING_CLASS[padding]}`;
}

export function Card({
  as,
  children,
  testId,
  padding = 'md',
}: {
  readonly as: CardElement;
  readonly children: ReactNode;
  readonly testId?: string;
  readonly padding?: CardPadding;
}) {
  // `createElement` 로 그린다 — 내장 태그 **유니온**을 JSX 태그 자리에 그대로 둘 수 없고,
  // 분기 넷으로 펴면 같은 본문이 네 번 선다(그 사본이 갈리는 자리다).
  return createElement(as, { 'data-testid': testId, className: cardClass(padding) }, children);
}
