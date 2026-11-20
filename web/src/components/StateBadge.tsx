import { useId, useState } from 'react';

import { unknownStateDisplay, type StateDisplay } from '../display/stateDisplay';

import { StatusPill, pillClass } from './StatusPill';

/**
 * 알약의 **모양과 어조 표는 `StatusPill` 한 자리**다(M14/14A). 앞 판은 그 표가 이 파일에
 * 있었고, 와이어프레임 정렬에서 같은 알약을 쓰는 자리가 늘면서 사본이 생길 참이었다.
 * 여기 남는 것은 그 알약을 **2층 라벨로 감싸는 일**뿐이다.
 */

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

  // **아래층이 없으면 펼칠 것이 없다** — 빈 펼침 버튼을 두면 운영자가 눌러 보고 아무 일도
  // 일어나지 않는다(가짜 affordance). 그 배지는 앞 판 그대로 글자다.
  if (shown.why === undefined) {
    return <StatusPill tone={shown.tone} label={shown.label} testId={testId} />;
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
        /*
          **`aria-pressed` 는 `aria-expanded` 와 다른 축이다**(cr N-5). `aria-expanded` 는 「가리키는
          묶음이 숨겨져 있다」는 선언이라, 내용이 트리에 늘 있는 이 자리에서는 거짓이었다. 반면
          `aria-pressed` 는 **그 버튼 자신의 두 상태**를 말한다 — 눌린 동안 아래층이 눈에 보이는
          토글이라는 사실은 참이고, 설명은 두 상태에서 같으므로 두 번 낭독되지도 않는다.
        */
        aria-pressed={expanded}
        onClick={() => setExpanded((open) => !open)}
        className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-md"
      >
        <span className={pillClass(shown.tone)}>{shown.label}</span>
      </button>
      {/*
        **한 노드다.** 접히면 시각 은폐, 펼치면 보이는 글자 — 바뀌는 것은 **보는 사람의 몫**이고
        보조기기는 두 상태에서 같은 설명을 받는다. `aria-hidden` 이 그 문면을 본문 텍스트에서
        빼고, `data-expanded` 는 상태를 클래스 글자에 기대지 않고 재는 자리다(13B 제한 27).

        **그래도 DOM 텍스트 노드로 남는다** — 13A 문면 게이트는 렌더한 텍스트 **와** `title`·
        `aria-label` 을 함께 읽으므로 어느 쪽으로 날라도 그 눈에는 들지만, 텍스트로 남기는 쪽이
        **보는 사람에게도** 닿는다(속성은 터치에서 볼 수 없어 이 slice 가 걷은 길이다).
        `aria-hidden` 은 접근성 트리의 축이라 그 훑기와 무관하다.
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
