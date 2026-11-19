import { useId, useState, type ReactNode } from 'react';

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

/** 알약 하나의 모양 — 어조 토큰만 갈린다. */
const PILL_CLASS = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium';

/**
 * 상태 하나의 표시. **2층 라벨**(§6.4)이다.
 *
 * **아래층(`why`)에 가는 길이 둘이고 문면은 하나다**(M13/13E, 받는 항목 11). `why` 는 이
 * 저장소의 정직 문면을 나른다 — 「미달이 아니라 판정하지 못했다」·「요건이 없는 것과 다른
 * 사실이다」. 앞 판에서 **보이는 쪽**은 툴팁 속성뿐이라 터치 기기에서 볼 방법이 없었고(보조기기
 * 경로는 `aria-describedby` + 시각 은폐 문면으로 이미 닫혀 있었다), 그래서 손가락만 남아 있었다.
 *
 * 이제 라벨 자리가 **내장 `button`** 이고 탭하면 아래층이 펼쳐진다. 요점은 **문면의 노드가
 * 하나**라는 것이다 — 펼침은 그 한 노드의 모양을 바꾸는 것이고 사본을 만들지 않는다. 펼친 자리에
 * 문면을 **또 그리면** 보조기기가 설명과 본문에서 같은 말을 두 번 읽는다.
 *
 * **펼침은 보이는 사용자만의 장치다**(수정 라운드 1 — verifier F-1 · 리뷰 M-2). 사유 노드는
 * `aria-hidden` 이라 접근성 트리에 **본문 텍스트로 서지 않고**, 보조기기는 접힘·펼침과 무관하게
 * 버튼의 **설명으로** 같은 문면을 한 번 받는다. 직접 참조된 숨김 노드라 설명 계산은 그대로
 * 선다 — CDP AX 트리 실측: 앞 판은 설명 1 + 본문 1 로 **둘**, 지금은 접힘·펼침 둘 다 **하나**다.
 * 그래서 `aria-expanded`·`aria-controls` 를 **걷었다**: 내용이 트리에 늘 있는데 「접혀 있다」고
 * 선언하면 그 선언이 거짓이고, 숨긴 노드를 가리키는 `aria-controls` 도 뜻이 없다.
 *
 * **툴팁 속성은 걷었다**(팀장 착수 판정 2026-11-18 ⓑ): 손가락·마우스가 같은 길을 쓰므로 할 일이
 * 없고, 같은 문면이 세 곳(속성·시각 은폐 문면·펼친 자리)에 사는 꼴을 없앤다.
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
  const [expanded, setExpanded] = useState(false);
  const shown = display ?? unknownStateDisplay('표에 없음');
  const pillClass = `${PILL_CLASS} ${TONE_CLASS[shown.tone]}`;

  // **아래층이 없으면 펼칠 것이 없다** — 빈 펼침 버튼을 두면 운영자가 눌러 보고 아무 일도
  // 일어나지 않는다(가짜 affordance). 그 배지는 앞 판 그대로 글자다.
  if (shown.why === undefined) {
    return (
      <span data-testid={testId} className={pillClass}>
        {shown.label}
      </span>
    );
  }

  const whyId = `state-why-${generatedId}`;
  return (
    <span className="inline-flex flex-col items-start">
      {/*
        손가락이 닿는 것은 알약이 아니라 **상자**다(M13/13B 의 블록 타깃과 같은 축) — 알약의
        모양은 그대로 두고 누르는 자리만 터치 타깃 하한까지 키운다. 실제 상자는 390px 여정의
        터치 타깃 게이트가 전수로 잰다.
      */}
      <button
        type="button"
        data-testid={testId}
        aria-describedby={whyId}
        onClick={() => setExpanded((open) => !open)}
        className="inline-flex min-h-11 items-center rounded-md"
      >
        <span className={pillClass}>{shown.label}</span>
      </button>
      {/*
        **한 노드다.** 접히면 시각 은폐, 펼치면 보이는 글자 — 바뀌는 것은 **보는 사람의 몫**이고
        보조기기는 두 상태에서 같은 설명을 받는다. `aria-hidden` 이 그 문면을 본문 텍스트에서
        빼고, `data-expanded` 는 상태를 클래스 글자에 기대지 않고 재는 자리다(13B 제한 27).

        **그래도 DOM 텍스트 노드로 남는다** — 13A 문면 게이트의 술어는 잎 요소의 `textContent`
        만 읽고 속성은 모집단에 든 적이 없다(실측). `aria-hidden` 은 접근성 트리의 축이라 그
        훑기와 무관하다.
      */}
      <span
        id={whyId}
        aria-hidden="true"
        data-expanded={expanded}
        className={expanded ? 'mt-0.5 text-xs text-ink-soft' : 'sr-only'}
      >
        {shown.why}
      </span>
    </span>
  );
}

/**
 * 라벨 + 값 한 줄. 값이 없으면 **빈 자리**를 그린다(§6.5 가짜 기본값 금지) — 「0」·「미정」을
 * 넣지 않는다.
 */
/**
 * 용어의 **아래층 문면**을 보조기기에도 닿게 묶는 한 자리(CR r2 M-4 · r3 M-5 · L-10).
 *
 * `title` 하나는 터치에서 볼 수 없고 `<dt>` 는 포커스를 받지 않는다. 그 문면(「투찰 기준 금액.
 * 추정가격과 다르다」)은 혼동하면 투찰 금액이 틀리는 자리다. 배선을 여기 하나로 두는 이유는
 * 같은 꼴이 화면마다 **복제되던 자리**이기 때문이다 — 셋째 자리에서 빠진 것이 M-5 였다.
 *
 * sr-only 문면은 호출자가 **값 요소 안**에 둔다. 형제 `<dd>` 로 두면 `dl` 의 내용 모형에서 한
 * 용어에 값이 둘이 된다.
 */
export function useHint(hint: string | undefined): {
  readonly describedBy: string | undefined;
  readonly hintNode: ReactNode;
} {
  const generatedId = useId();
  if (hint === undefined) {
    return { describedBy: undefined, hintNode: null };
  }
  const describedBy = `field-hint-${generatedId}`;
  return {
    describedBy,
    hintNode: (
      <span id={describedBy} className="sr-only">
        {hint}
      </span>
    ),
  };
}

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
  const { describedBy, hintNode } = useHint(hint);
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-ink-soft" title={hint}>
        {label}
      </dt>
      {/* sr-only 문면은 **그 `<dd>` 안**에 둔다(CR r3 L-10) — 둘째 `<dd>` 로 두면 `dl` 의 내용
          모형에서 한 용어에 값이 둘이 되고, 보조기기가 설명을 값으로 읽는다. */}
      <dd aria-describedby={describedBy} className="text-sm">
        <span data-testid={testId}>{value ?? <span className="text-absent-ink">값 없음</span>}</span>
        {hintNode}
      </dd>
    </div>
  );
}
