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
  /** 제목 곁의 한 줄 — 그 화면이 지금 무엇을 말하는지. 없으면 그리지 않는다. */
  readonly summary?: string;
  /** 머리의 오른쪽에 서는 행동. 제목 **뒤**라 탭 차례가 제목을 건너뛰지 않는다. */
  readonly actions?: ReactNode;
  readonly testId?: string;
}) {
  return (
    <div data-testid={testId} className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
        <h2 className="text-xl font-bold tracking-tight">{title}</h2>
        {summary === undefined ? null : (
          <p data-testid={slotTestId(testId, 'summary')} className="text-sm text-ink-soft">
            {summary}
          </p>
        )}
      </div>
      {actions === undefined ? null : (
        <div data-testid={slotTestId(testId, 'actions')} className="flex flex-wrap items-center gap-2">
          {actions}
        </div>
      )}
    </div>
  );
}
