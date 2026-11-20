import { absenceTextOf, type AbsenceReason } from '../display/absence';

/**
 * 부재 한 칸의 **유일한 얼굴**(M14/14A ④).
 *
 * 앞 판은 화면 다섯이 각자 `<span className="text-absent-ink">…</span>` 을 손으로 적었고, 그
 * 가운데 하나는 **응답 전**에도 같은 글자를 냈다. 이제 부재를 그리려면 **사유를 골라야 한다** —
 * 글자를 적는 길이 남아 있지 않다.
 *
 * `reason` 이 선택이 아닌 것이 요점이다. 기본값을 두면 「무엇이 없는지 모른 채 없다고 말하는」
 * 자리가 다시 생기고, 그 자리가 정확히 지금 걷어내는 것이다.
 */
export function AbsentValue({
  reason,
  testId,
  className,
}: {
  readonly reason: AbsenceReason;
  readonly testId?: string;
  /** 자리마다 글자 크기가 다르다 — 어조 토큰은 이 컴포넌트가 든다. */
  readonly className?: string;
}) {
  return (
    <span
      data-testid={testId}
      className={className === undefined ? 'text-absent-ink' : `text-absent-ink ${className}`}
    >
      {absenceTextOf(reason)}
    </span>
  );
}
