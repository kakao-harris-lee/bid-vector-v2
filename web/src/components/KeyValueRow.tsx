import { useId, useState, type ReactNode } from 'react';

import { AbsentValue } from './AbsentValue';

/**
 * 용어의 **아래층 문면**을 보조기기와 **손가락** 둘 다에 닿게 묶는 한 자리
 * (CR r2 M-4 · r3 M-5 · L-10 · M14/14A — M13 잔여 §12.3 (w) ⓖ).
 *
 * `title` 하나는 터치에서 볼 수 없고 `<dt>` 는 포커스를 받지 않는다. 그 문면(「투찰 기준 금액.
 * 추정가격과 다르다」)은 혼동하면 투찰 금액이 틀리는 자리라, 보는 사람이 **볼 방법이 없는** 채로
 * 두면 그 혼동이 그대로 남는다. 2층 라벨(§6.4)의 둘째 층을 상태 배지가 닫았고 여기만 열려
 * 있었다 — 같은 모양으로 닫는다.
 *
 * **문면의 노드가 하나다.** 접히면 시각 은폐, 펼치면 보이는 글자 — 바뀌는 것은 보는 사람의
 * 몫이고 보조기기는 두 상태에서 같은 설명을 **한 번** 받는다. 펼친 자리에 문면을 또 그리면
 * 같은 말이 두 번 낭독된다.
 *
 * `aria-expanded` 가 아니라 `aria-pressed` 다: 내용이 트리에 늘 있으므로 「접혀 있다」는 거짓이고,
 * 참인 것은 **그 버튼 자신의 두 상태**다(상태 배지와 같은 사유).
 *
 * 아래층 노드는 호출자가 **값 요소 안**에 둔다. 형제 `<dd>` 로 두면 `dl` 의 내용 모형에서 한
 * 용어에 값이 둘이 된다.
 */
export function useTermHint(hint: string | undefined): {
  readonly describedBy: string | undefined;
  /** `<dt>` 안에 들어갈 것 — 아래층이 있으면 누를 수 있는 라벨, 없으면 그냥 글자다. */
  readonly termNode: (label: string) => ReactNode;
  /** `<dd>` 안에 들어갈 **한 노드**. */
  readonly hintNode: ReactNode;
} {
  const generatedId = useId();
  const [expanded, setExpanded] = useState(false);
  if (hint === undefined) {
    return { describedBy: undefined, termNode: (label) => label, hintNode: null };
  }
  const describedBy = `field-hint-${generatedId}`;
  return {
    describedBy,
    termNode: (label) => (
      <button
        type="button"
        aria-describedby={describedBy}
        aria-pressed={expanded}
        onClick={() => setExpanded((open) => !open)}
        /* 누르는 자리는 **상자**다 — 터치 타깃 하한까지 키우고 글자 모양은 그대로 둔다. */
        className="inline-flex min-h-11 min-w-11 items-center rounded-md text-left underline decoration-dotted underline-offset-4"
      >
        {label}
      </button>
    ),
    hintNode: (
      <span
        id={describedBy}
        aria-hidden="true"
        data-expanded={expanded}
        className={expanded ? 'mt-0.5 block text-xs text-ink-soft' : 'sr-only'}
      >
        {hint}
      </span>
    ),
  };
}

/**
 * 라벨 + 값 한 줄. 값이 없으면 **빈 자리**를 그린다(§6.5 가짜 기본값 금지) — 「0」·「미정」을
 * 넣지 않는다.
 */
export function KeyValueRow({
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
  const { describedBy, termNode, hintNode } = useTermHint(hint);
  return (
    <div className="flex flex-col gap-0.5">
      {/* **아래층이 있으면 라벨이 누를 수 있는 자리다**(M14/14A — M13 잔여 ⓖ). `title` 은
          걷었다: 손가락이 볼 수 없고, 같은 문면이 세 곳에 사는 꼴을 없앤다. */}
      <dt className="text-xs text-ink-soft">{termNode(label)}</dt>
      {/* sr-only 문면은 **그 `<dd>` 안**에 둔다(CR r3 L-10) — 둘째 `<dd>` 로 두면 `dl` 의 내용
          모형에서 한 용어에 값이 둘이 되고, 보조기기가 설명을 값으로 읽는다. */}
      <dd aria-describedby={describedBy} className="text-sm">
        {/* 값이 `null` 인 것은 **응답을 읽은 뒤** 그 칸이 비었다는 뜻이다(적재 중의 자리가
            아니다 — 그 자리는 자기 사유를 따로 고른다). */}
        <span data-testid={testId}>{value ?? <AbsentValue reason="notSent" />}</span>
        {hintNode}
      </dd>
    </div>
  );
}
