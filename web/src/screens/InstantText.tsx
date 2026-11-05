import type { InstantDisplay } from '../display/timeDisplay';

/**
 * 시각 한 칸의 **그리는 자리**(M9/screen-display D-1·D-2). 문면은 `timeDisplay` 가 만들고 이
 * 컴포넌트는 세 갈래를 **다른 얼굴로** 낸다 — 앞 판은 다섯 화면이 각자 `<time>` 과 「값 없음」
 * 분기를 손으로 적었고 그 가운데 넷이 ISO 원문을 그대로 그렸다.
 *
 * **display 를 받고 `*At` 을 받지 않는다**: `at={entry.occurredAt}` 꼴이면 원문이 다시 화면
 * 파일을 지나고 lint 게이트(D-4)에도 걸린다. 부르는 자리가 `instantDisplayOf(…)` 를 적는 것이
 * 「이 값은 서식을 지났다」는 표시다.
 */
export function InstantText({
  display,
  testId,
  className,
}: {
  readonly display: InstantDisplay;
  readonly testId?: string;
  /** 자리마다 글자 크기가 다르다 — 숫자 서체는 아래가 든다. */
  readonly className?: string;
}) {
  if (display.kind === 'readable') {
    return (
      <time
        data-testid={testId}
        dateTime={display.machine ?? undefined}
        className={className === undefined ? 'font-numeric' : `font-numeric ${className}`}
      >
        {display.text}
      </time>
    );
  }
  // 없는 값과 읽히지 않은 값은 **어조가 다르다** — 뒤는 「서버가 안 보냈다」가 아니라 「보낸
  // 값을 읽지 못했다」이고, 그것은 판정이 아니라 진단이다.
  return (
    <span
      data-testid={testId}
      className={`${display.kind === 'absent' ? 'text-absent-ink' : 'text-unjudged-ink'}${
        className === undefined ? '' : ` ${className}`
      }`}
    >
      {display.text}
    </span>
  );
}
