import { useId } from 'react';

import { unknownStateDisplay, type DisplayTone, type StateDisplay } from '../display/stateDisplay';

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
 * 상태 하나의 표시. **2층 라벨**(§6.4)이다.
 *
 * **아래층(`why`)이 `title` 하나에만 있지 않다**(CR M-7). `why` 는 이 slice 의 정직 문면을
 * 나른다 — 「미달이 아니라 판정하지 못했다」·「요건이 없는 것과 다른 사실이다」 — 그것을
 * `title` 로만 두면 터치 기기에서 볼 방법이 없고, 포커스를 받지 않는 `<span>` 이라 키보드로
 * 닿지 않으며, 보조기술 노출이 설정에 따라 들쭉날쭉하다. 그래서 `aria-describedby` 로 묶은
 * **sr-only 문면**을 함께 둔다 — 보조기술은 늘 읽고, 보는 사람에게는 `title` 이 남는다.
 *
 * **표 밖의 값은 조용히 사라지지 않는다**(CR M-3): `display` 가 없으면 부르는 자리가
 * `displayOrUnknown` 으로 **보이는** 미지 상태를 주게 되어 있고, 그래도 없으면 그 사실을
 * 드러낸다(빈 자리는 「서버가 안 보냈다」로 읽히므로 그 쪽이 더 나쁘다).
 */
export function StateBadge({
  display,
  testId,
}: {
  readonly display: StateDisplay | undefined;
  readonly testId?: string;
}) {
  // `useId` 를 쓴다 — 모듈 전역 카운터를 렌더 중에 올리는 것은 부수효과이고 리렌더 시점에
  // 따라 값이 달라진다(react-hooks 규칙이 그 자리를 잡았다).
  const generatedId = useId();
  const shown = display ?? unknownStateDisplay('표에 없음');
  const describedBy = shown.why === undefined ? undefined : `state-why-${generatedId}`;
  return (
    <span className="inline-flex items-center gap-1">
      <span
        data-testid={testId}
        title={shown.why}
        aria-describedby={describedBy}
        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${TONE_CLASS[shown.tone]}`}
      >
        {shown.label}
      </span>
      {describedBy === undefined ? null : (
        <span id={describedBy} className="sr-only">
          {shown.why}
        </span>
      )}
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
  // CR r2 M-4 — 아래층이 `title` 하나이면 터치에서 볼 수 없고 `<dt>` 는 포커스를 받지 않는다.
  // `hint` 가 나르는 문면(「투찰 기준 금액. 추정가격과 다르다」)은 혼동하면 투찰 금액이 틀리는
  // 자리다. 배지와 같은 꼴로 sr-only 문면을 묶는다.
  const generatedId = useId();
  const describedBy = hint === undefined ? undefined : `field-hint-${generatedId}`;
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-ink-soft" title={hint}>
        {label}
      </dt>
      <dd data-testid={testId} aria-describedby={describedBy} className="text-sm">
        {value ?? <span className="text-absent-ink">값 없음</span>}
      </dd>
      {describedBy === undefined ? null : (
        <dd id={describedBy} className="sr-only">
          {hint}
        </dd>
      )}
    </div>
  );
}
