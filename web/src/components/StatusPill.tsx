import type { DisplayTone } from '../display/stateDisplay';

/**
 * 상태 알약의 **모양 한 자리**(M14/14A). 어느 어조인지는 표시층이 정하고(`StateBadge` 가 2층
 * 라벨로 감싼다), 여기 있는 것은 어조 → 토큰 하나다.
 *
 * **색만으로 말하지 않는다**(§6.5): `label` 이 선택이 아니다. 색을 보지 못하는 사람과 흑백
 * 출력에서 알약이 아무 말도 하지 않는 자리를 타입이 막는다.
 *
 * 색 리터럴은 `index.css` 의 토큰에만 있다(D-7E-5 「토큰은 한 자리」). `absent`·`unjudged` 가
 * `bad` 와 다른 색을 갖는 것이 요점이다 — 「없음」과 「아직 판정하지 않음」이 「미달」로 읽히면
 * 정직 명세가 무너진다.
 */
export const PILL_TONE_CLASS = {
  neutral: 'bg-neutral-skin text-neutral-ink',
  absent: 'bg-absent-skin text-absent-ink',
  unjudged: 'bg-unjudged-skin text-unjudged-ink',
  good: 'bg-good-skin text-good-ink',
  bad: 'bg-bad-skin text-bad-ink',
} as const satisfies Record<DisplayTone, string>;

/**
 * 어조 전수의 **런타임 목록**. 완전성은 위 `satisfies` 가 컴파일에서 들고, 이 목록이 그 표와
 * 같은 집합인지는 짝 test 가 잰다 — 둘이 갈리면 전수를 도는 단언이 조용히 줄어든다.
 */
export const PILL_TONES = ['neutral', 'absent', 'unjudged', 'good', 'bad'] as const satisfies readonly DisplayTone[];

const PILL_SHAPE_CLASS = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium';

/**
 * 알약 하나의 클래스 — **한 자리**다. `StatusPill` 이 받을 수 없는 자리(2층 라벨의 버튼 안쪽)가
 * 같은 표를 쓴다. 사본이 생기지 않는 것이 요점이다(`controlClass`·`cardClass` 의 선례).
 */
export function pillClass(tone: DisplayTone): string {
  return `${PILL_SHAPE_CLASS} ${PILL_TONE_CLASS[tone]}`;
}

export function StatusPill({
  tone,
  label,
  testId,
}: {
  readonly tone: DisplayTone;
  readonly label: string;
  readonly testId?: string;
}) {
  return (
    <span data-testid={testId} className={pillClass(tone)}>
      {label}
    </span>
  );
}
