import type { ReactNode } from 'react';
import { Link } from 'react-router';

/**
 * 공유 버튼(M13/13B) — **모양만** 든다. 어떤 전이를 부르는지는 화면의 일이다.
 *
 * 어조 셋과 크기 둘이 **표**다(유니온 × `Record` 전수). 화면마다 유틸리티 클래스를 적던 앞 판은
 * 같은 버튼이 네 가지 여백으로 갈렸고(착수 실측: `px-3 py-1` 24 · `px-2 py-1` 22 · `px-3 py-2` 10 ·
 * 그 밖 8), 그 가운데 **44px 을 넘는 것이 하나도 없었다**(착수 실측 101/101).
 *
 * **클래스 입력 prop 을 두지 않는다**(설계 검토 §2b). 호출자가 클래스를 덧붙일 수 있으면 크기
 * 규칙이 호출자마다 덮이고, 그때 이 표는 「권고」가 된다. 자리마다 다른 것은 `fullWidth` 하나다.
 */
export const BUTTON_TONES = ['primary', 'secondary', 'danger'] as const;

/**
 * 어조는 **목록에서 유도한다** — 유니온을 따로 적으면 목록과 유니온이 갈리고, 그때 test 의
 * 「전수」가 조용히 줄어든다(돌 수 있는 것만 돈다). 표는 `Record` 라 목록이 늘면 컴파일이 멈춘다.
 */
export type ButtonTone = (typeof BUTTON_TONES)[number];

/** 크기 둘 — 둘 다 터치 타깃 하한(44px) 위다. `lg` 는 그 화면의 **다음 행동 하나**가 쓴다. */
export const BUTTON_SIZES = ['md', 'lg'] as const;

export type ButtonSize = (typeof BUTTON_SIZES)[number];

const TONE_CLASS = {
  primary: 'bg-accent text-accent-ink hover:opacity-90',
  secondary: 'border border-control-line bg-card text-ink hover:bg-neutral-skin',
  danger: 'border border-bad-ink/40 bg-bad-skin text-bad-ink hover:opacity-90',
} as const satisfies Record<ButtonTone, string>;

/**
 * `min-h-11` 은 44px 이고 `min-h-12` 는 48px 이다(Tailwind 의 기본 간격 눈금 — 0.25rem 배수).
 * **상자 높이의 정본은 이 표**이고, 그것이 실제로 44px 인지는 터치 타깃 게이트가 390px 에서
 * 잰다(클래스 글자를 세는 단언은 그 축을 재지 못한다).
 */
const SIZE_CLASS = {
  md: 'min-h-11 px-4 py-2 text-sm',
  lg: 'min-h-12 px-5 py-3 text-base',
} as const satisfies Record<ButtonSize, string>;

const BASE_CLASS =
  'inline-flex items-center justify-center gap-2 rounded-md font-medium disabled:cursor-not-allowed disabled:opacity-50';

/**
 * 어조·크기 한 쌍의 클래스 — **한 자리**다.
 *
 * 내보내는 이유는 `<Button>` 이 받을 수 없는 자리가 있기 때문이다: `role="tab"` 묶음처럼
 * 자기 ARIA 배선(`aria-selected`·`aria-controls`·`tabIndex`·방향키)을 쥔 버튼은 그 배선을 이
 * 컴포넌트로 옮기면 Button 이 탭 구현을 품게 된다. 그 자리는 **같은 표**를 쓰고 요소만 자기가
 * 그린다 — 사본이 생기지 않는 것이 요점이다.
 */
export function controlClass(tone: ButtonTone, size: ButtonSize): string {
  return `${BASE_CLASS} ${TONE_CLASS[tone]} ${SIZE_CLASS[size]}`;
}

interface SharedProps {
  readonly tone?: ButtonTone;
  readonly size?: ButtonSize;
  readonly children: ReactNode;
  readonly testId?: string;
  /** 글자가 짧아 뜻이 모자란 자리에만 — 보이는 글자가 있으면 **주지 않는다**(낭독이 갈린다). */
  readonly ariaLabel?: string;
  readonly title?: string;
  /** 모바일에서 한 줄을 다 쓰는 주 행동. 기본은 내용 폭이다. */
  readonly fullWidth?: boolean;
}

const widthClass = (fullWidth: boolean | undefined): string => (fullWidth === true ? ' w-full' : '');

export function Button({
  tone = 'secondary',
  size = 'md',
  type = 'button',
  onClick,
  disabled,
  children,
  testId,
  ariaLabel,
  title,
  fullWidth,
}: SharedProps & {
  /** `submit` 은 폼이 쥔 자리에만. 기본이 `button` 인 이유는 폼 안의 버튼이 말없이 제출하지 않게 하려는 것이다. */
  readonly type?: 'button' | 'submit';
  readonly onClick?: () => void;
  readonly disabled?: boolean;
}) {
  return (
    <button
      type={type === 'submit' ? 'submit' : 'button'}
      onClick={onClick}
      disabled={disabled}
      data-testid={testId}
      aria-label={ariaLabel}
      title={title}
      className={`${controlClass(tone, size)}${widthClass(fullWidth)}`}
    >
      {children}
    </button>
  );
}

/**
 * 같은 모양의 **이동**(react-router `Link`). 버튼과 링크를 한 컴포넌트에 `as` 로 섞지 않는다 —
 * 섞으면 props 가 합집합이 되어 「이동인데 `onClick` 이 있다」 같은 꼴이 타입에서 선다.
 */
export function LinkButton({
  to,
  tone = 'secondary',
  size = 'md',
  children,
  testId,
  ariaLabel,
  title,
  fullWidth,
}: SharedProps & { readonly to: string }) {
  return (
    <Link
      to={to}
      data-testid={testId}
      aria-label={ariaLabel}
      title={title}
      className={`${controlClass(tone, size)}${widthClass(fullWidth)}`}
    >
      {children}
    </Link>
  );
}
