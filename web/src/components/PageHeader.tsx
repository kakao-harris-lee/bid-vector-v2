import type { ReactNode } from 'react';

/**
 * 안쪽 자리의 표지 — 바깥 표지가 없으면 안쪽도 없다(장식 자리에 쓸 수 있어야 한다).
 *
 * **왜 자리마다 표지인가**: 「없으면 그리지 않는다」를 글자로만 재면 **빈 노드를 늘 그리는
 * 변이가 지나간다**(이 컴포넌트의 변이 실측 — 빈 `<p>` 의 `textContent` 는 빈 글자라 제목만
 * 남은 글자와 같다). 자리의 **존재**를 재야 그 변이가 붉는다.
 */
const slotTestId = (testId: string | undefined, slot: string): string | undefined =>
  testId === undefined ? undefined : `${testId}-${slot}`;

/**
 * **빈 자리로 읽히는 노드인가**(cr L-1 의 구멍을 `ReactNode` 로 넓힌 뒤에도 닫아 둔다).
 *
 * 없음·참/거짓은 React 가 아무것도 그리지 않는 값이고, 공백뿐인 글자는 **글자로는 비어 있다**.
 * **숫자는 비어 있지 않다** — `0` 은 값이고 그려야 한다(0 을 빈 값으로 접으면 「없다」와 「0 이다」가
 * 같은 얼굴이 되는데, 그것이 이 slice 가 없애려는 바로 그 혼동이다). 배열은 그 안이 전부 비어
 * 있을 때만 비었다.
 *
 * **알려진 제한**: 요소(`<></>` 처럼 비어 있는 조각)는 그려 봐야 비었는지 알 수 있어 여기서
 * 가리지 못한다 — 그 꼴은 호출부의 실수이고 이 술어가 잡는 범위 밖이다.
 */
function isBlankNode(node: ReactNode): boolean {
  if (node === undefined || node === null || typeof node === 'boolean') {
    return true;
  }
  if (typeof node === 'string') {
    return node.trim() === '';
  }
  if (typeof node === 'number') {
    return false;
  }
  if (Array.isArray(node)) {
    return node.every((one) => isBlankNode(one));
  }
  return false;
}

/**
 * 화면 머리의 **한 자리**(M14/14A · 와이어프레임의 머리). 제목 · 요약 한 줄 · 행동 셋이
 * 화면마다 다른 글자 크기와 다른 배치로 복제되던 자리를 모은다.
 *
 * **제목은 `h2` 다.** 셸의 `h1` 이 앱 이름이고(기존 셸 test 가 그 등식을 든다), 화면 제목이
 * 같은 층에 서면 한 문서에 `h1` 이 둘이 되어 보조기기의 개요가 갈린다. 와이어프레임에서 화면
 * 제목이 가장 큰 글자인 것은 **시각 층위**이고, 그것은 글자 크기가 말한다.
 *
 * **없는 자리는 그리지 않는다.** 빈 요약 노드를 늘 두면 보조기기가 빈 문단을 읽고 제목 아래
 * 여백이 거짓으로 벌어진다. 「값이 없으면 빈 자리」와 같은 규율이다.
 */
export function PageHeader({
  title,
  summary,
  actions,
  testId,
}: {
  readonly title: string;
  /**
   * 제목 곁의 한 줄 — 그 화면이 지금 무엇을 말하는지. 없으면 그리지 않는다.
   *
   * **`ReactNode` 다**(cr L-4 · 팀장 판정 2026-11-20). 글자만 받으면 뒤 레인이 요약 줄 안에
   * 부재 값이나 강조를 실을 수 없다 — 14B 의 상한 초과 공시가 그런 자리다.
   *
   * **빈 글자도 「없음」이다**(cr L-1): `''` 를 그대로 그리면 빈 문단이 서고, 그것은 변이 5 가
   * 찾은 바로 그 모양이 호출부 인자 하나로 되살아난 것이다. 넓힌 뒤에도 그 구멍을 열지 않는다 —
   * 빈 자리로 읽히는 값(없음 · 참/거짓 · 공백뿐인 글자 · 그것들만 담은 배열)은 **그리지 않는다**.
   */
  readonly summary?: ReactNode;
  /**
   * 머리의 오른쪽에 서는 행동. 제목 **뒤**라 탭 차례가 제목을 건너뛰지 않는다.
   *
   * **빈 자리로 읽히는 값은 그리지 않는다**(cr R3-L1) — 요약과 같은 규율이다. 조건부로 만든
   * 행동 묶음이 비면(`{open && <Button/>}` 꼴이 거짓일 때) 빈 상자가 서고, 그 상자가 `gap` 을
   * 벌려 머리의 오른쪽이 까닭 없이 밀린다.
   */
  readonly actions?: ReactNode;
  readonly testId?: string;
}) {
  return (
    <div data-testid={testId} className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
        <h2 className="text-xl font-bold tracking-tight">{title}</h2>
        {isBlankNode(summary) ? null : (
          <p data-testid={slotTestId(testId, 'summary')} className="text-sm text-ink-soft">
            {summary}
          </p>
        )}
      </div>
      {isBlankNode(actions) ? null : (
        <div data-testid={slotTestId(testId, 'actions')} className="flex flex-wrap items-center gap-2">
          {actions}
        </div>
      )}
    </div>
  );
}
