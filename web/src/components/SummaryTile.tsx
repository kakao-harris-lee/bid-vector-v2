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
  | { readonly kind: 'unavailable'; readonly why: string };

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

/** 안쪽 자리의 표지 — 바깥 표지가 없으면 안쪽도 없다(장식 자리에 쓸 수 있어야 한다). */
const slotTestId = (testId: string | undefined, slot: string): string | undefined =>
  testId === undefined ? undefined : `${testId}-${slot}`;

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
      {face.kind === 'value' ? (
        <span data-testid={slotTestId(testId, 'value')} className="font-numeric text-xl font-bold">
          {face.text}
          {face.beside === undefined ? null : (
            <span className="ml-1 text-sm font-medium text-ink-soft">{face.beside}</span>
          )}
        </span>
      ) : (
        <span data-testid={slotTestId(testId, 'unavailable')} className="text-sm text-absent-ink">
          {face.why}
        </span>
      )}
      {face.kind === 'value' && face.note !== undefined ? (
        <span className="text-xs text-ink-soft">{face.note}</span>
      ) : null}
    </div>
  );
}
