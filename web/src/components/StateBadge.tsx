import type { DisplayTone, StateDisplay } from '../display/stateDisplay';

/**
 * 어조 → 토큰. **다섯 어조 전수**이고 `satisfies` 가 그 등식을 든다 — 어조가 늘면 이 자리가
 * 컴파일에서 멈춘다. 색 리터럴은 `index.css` 의 토큰에만 있다(D-7E-5 「토큰은 한 자리」).
 */
const TONE_CLASS = {
  neutral: 'bg-neutral-skin text-neutral-ink',
  absent: 'bg-absent-skin text-absent-ink',
  unjudged: 'bg-unjudged-skin text-unjudged-ink',
  good: 'bg-good-skin text-good-ink',
  bad: 'bg-bad-skin text-bad-ink',
} as const satisfies Record<DisplayTone, string>;

/**
 * 상태 하나의 표시. **2층 라벨**(§6.4)이다 — 짧은 라벨은 배지로, 아래층(`why`)은 `title` 로
 * 둔다. 값을 지어내지 않으므로 `display` 가 없으면 아무것도 그리지 않는다.
 */
export function StateBadge({
  display,
  testId,
}: {
  readonly display: StateDisplay | undefined;
  readonly testId?: string;
}) {
  if (display === undefined) {
    return null;
  }
  return (
    <span
      data-testid={testId}
      title={display.why}
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${TONE_CLASS[display.tone]}`}
    >
      {display.label}
    </span>
  );
}

/**
 * 라벨 + 값 한 줄. 값이 없으면 **빈 자리**를 그린다(§6.5 가짜 기본값 금지) — 「0」·「미정」을
 * 넣지 않는다.
 */
export function Field({
  label,
  value,
  hint,
  testId,
}: {
  readonly label: string;
  readonly value: string | null;
  readonly hint?: string;
  readonly testId?: string;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-ink-soft" title={hint}>
        {label}
      </dt>
      <dd data-testid={testId} className="text-sm">
        {value ?? <span className="text-absent-ink">값 없음</span>}
      </dd>
    </div>
  );
}
