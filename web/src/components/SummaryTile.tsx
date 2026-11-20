import type { ReactNode } from 'react';

import { cardClass } from './Card';

/**
 * 요약 타일(M14/14A · 와이어프레임 결과 화면의 타일 넷).
 *
 * 요점은 **두 얼굴이 타입으로 갈려 있는 것**이다. 와이어프레임의 타일 가운데 둘은 서버가 그
 * 값을 내지 않는다(계약 「데이터」 절) — 값 얼굴 하나에 빈 글자를 넣어 그리면 운영자가 그것을
 * 0 이나 「계산 중」으로 읽는다. 없는 값은 **숫자 자리를 아예 갖지 않고** 사유를 든다.
 *
 * 그래서 타일은 숫자를 **글자로** 받는다: 서식(자릿점·단위·부호)은 표시층의 일이고, 타일이
 * 숫자를 받으면 그 서식이 여기로 흘러들어 자리마다 갈린다.
 */
export type TileFace =
  | {
      readonly kind: 'value';
      readonly text: string;
      /** 값 곁의 작은 글자 — 분모(`/ 12`)처럼 **같은 한 값의 일부**다. 다른 줄로 흘리지 않는다. */
      readonly beside?: string;
      /** 값 아래 한 줄 — 표본의 범위나 셈의 근거. */
      readonly note?: string;
    }
  | { readonly kind: 'unavailable'; readonly why: string }
  /**
   * **두 축을 한 타일에 나란히**(cr L-4 · 팀장 판정 2026-11-20 · 14D 가 쓸 자리).
   *
   * 낙찰률은 **운영자 기록 축**과 **서버 대사 축**이 서로 다른 수를 낼 수 있고, 계약은 「타일
   * 하나에 한 축만 쓰는 것」을 금지한다. `beside` 는 그 자리가 아니다 — 그 칸의 뜻은 「분모처럼
   * **같은 한 값의 일부**」이고, 거기에 둘째 **축**을 넣는 것은 그 뜻을 깨는 오용이다.
   *
   * 축을 **길이 둘의 튜플**로 받는다. 하나만 그리는 것이 **타입에서** 서지 않는다 — 계약이
   * 금지한 꼴을 컴파일이 막는다.
   */
  | {
      readonly kind: 'twoAxis';
      readonly axes: readonly [TileAxis, TileAxis];
      readonly note?: string;
    };

/** 축 하나 — 이름과 값. 값이 비면 **그 축이 자기 사유를 말한다**(빈 칸으로 두지 않는다). */
export interface TileAxis {
  readonly label: string;
  readonly text: string;
  /** 빈 글자일 때 그 축이 대신 말할 사유. */
  readonly whenEmpty?: string;
}

/**
 * 값 얼굴을 만든다. **빈 글자는 값이 아니다**(cr L-1) — `tileValue('')` 는 빈 숫자 칸을 그려
 * `tileUnavailable` 과 타입으로 가른 뜻을 그 자리에서 없앤다. 그래서 빈 글자가 오면 **없는 값
 * 얼굴로 접고** 사유를 요구한다: 왜 비었는지는 부르는 자리가 안다.
 */
export function tileValue(
  text: string,
  options?: { readonly beside?: string; readonly note?: string; readonly whenEmpty?: string },
): TileFace {
  if (text.trim() === '') {
    return tileUnavailable(options?.whenEmpty ?? '값이 비어 있다');
  }
  return { kind: 'value', text, beside: options?.beside, note: options?.note };
}

export function tileUnavailable(why: string): TileFace {
  return { kind: 'unavailable', why };
}

/**
 * 두 축 얼굴을 만든다. 인자가 **길이 둘의 튜플**이라 축 하나로는 부를 수 없다 — 계약의
 * 「두 축 병기」가 타입으로 선다.
 */
export function tileTwoAxis(axes: readonly [TileAxis, TileAxis], options?: { readonly note?: string }): TileFace {
  return { kind: 'twoAxis', axes, note: options?.note };
}

/** 축 하나가 보이는 글자 — 비면 사유다(빈 칸이 값처럼 읽히지 않게). */
function axisTextOf(axis: TileAxis): { readonly text: string; readonly present: boolean } {
  return axis.text.trim() === ''
    ? { text: axis.whenEmpty ?? '값이 비어 있다', present: false }
    : { text: axis.text, present: true };
}

/** 안쪽 자리의 표지 — 바깥 표지가 없으면 안쪽도 없다(장식 자리에 쓸 수 있어야 한다). */
const slotTestId = (testId: string | undefined, slot: string): string | undefined =>
  testId === undefined ? undefined : `${testId}-${slot}`;

/** 얼굴 하나를 그린다 — 세 갈래를 **소진**한다(갈래가 늘면 반환 타입이 그 자리를 막는다). */
function faceNodeOf(face: TileFace, testId: string | undefined): ReactNode {
  if (face.kind === 'value') {
    return (
      <span data-testid={slotTestId(testId, 'value')} className="font-numeric text-xl font-bold">
        {face.text}
        {face.beside === undefined ? null : (
          <span className="ml-1 text-sm font-medium text-ink-soft">{face.beside}</span>
        )}
      </span>
    );
  }
  if (face.kind === 'unavailable') {
    return (
      <span data-testid={slotTestId(testId, 'unavailable')} className="text-sm text-absent-ink">
        {face.why}
      </span>
    );
  }
  return (
    <div data-testid={slotTestId(testId, 'axes')} className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
      {face.axes.map((axis, at) => {
        const shown = axisTextOf(axis);
        return (
          <span key={axis.label} className="flex min-w-0 flex-col">
            <span className="text-xs text-ink-soft">{axis.label}</span>
            <span
              data-testid={slotTestId(testId, `axis-${at}`)}
              className={shown.present ? 'font-numeric text-lg font-bold' : 'text-sm text-absent-ink'}
            >
              {shown.text}
            </span>
          </span>
        );
      })}
    </div>
  );
}

export function SummaryTile({
  label,
  face,
  testId,
}: {
  readonly label: string;
  readonly face: TileFace;
  readonly testId?: string;
}) {
  return (
    <div data-testid={testId} className={`${cardClass('sm')} flex min-w-0 flex-1 flex-col gap-1`}>
      <span className="text-xs text-ink-soft">{label}</span>
      {/* **얼굴 전수를 소진한다** — 갈래가 늘면 이 자리가 컴파일에서 멈춘다. */}
      {faceNodeOf(face, testId)}
      {(face.kind === 'value' || face.kind === 'twoAxis') && face.note !== undefined ? (
        <span className="text-xs text-ink-soft">{face.note}</span>
      ) : null}
    </div>
  );
}
