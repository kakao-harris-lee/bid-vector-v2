import type { BidTimelineEntry } from '../api/queries';
import { StateBadge } from '../components/StateBadge';
import { BID_CASE_STATE_DISPLAY, TIMELINE_ACTOR_DISPLAY, type BidCaseState } from '../display/caseDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

/**
 * 상태 문면은 상태 표에서 찾는다. **타임라인의 `toState` 는 계약이 `type: string`** 이라
 * 상태 어휘 밖의 값이 올 수 있으므로, 표에 없으면 **서버가 준 코드를 그대로** 보인다(문면을
 * 지어내지 않는다 — `noticeStatus` 와 같은 관례).
 */
function stateLabelOf(state: string): string {
  const known = (BID_CASE_STATE_DISPLAY as Readonly<Record<string, { readonly label: string } | undefined>>)[state];
  return known?.label ?? state;
}

/**
 * 건별 타임라인(§6.2 C 6) — **모든 전이가 시각·주체와 함께** 남는다. 순서는 서버가 준 순서
 * 그대로다(화면이 다시 정렬하지 않는다).
 */
export function WorksheetTimeline({ entries }: { readonly entries: readonly BidTimelineEntry[] }) {
  return (
    <section className="rounded-lg border border-line bg-card p-4">
      <h3 className="text-sm font-semibold">타임라인</h3>
      <ol data-testid="timeline" className="mt-3 flex flex-col gap-3">
        {entries.map((entry) => (
          <li key={entry.caseVersion} className="border-l-2 border-line pl-3">
            <p className="flex flex-wrap items-center gap-2 text-sm">
              <span>{stateLabelOf(entry.toState)}</span>
              <StateBadge display={displayOrUnknown(TIMELINE_ACTOR_DISPLAY, entry.actorKind)} />
              {entry.retroactive ? <span className="text-xs text-unjudged-ink">소급</span> : null}
            </p>
            <p className="text-xs text-ink-soft">
              <time dateTime={entry.occurredAt} className="font-numeric">
                {entry.occurredAt}
              </time>
              {' · '}
              {entry.command}
              {entry.fromState === null || entry.fromState === undefined
                ? ''
                : ` · ${stateLabelOf(entry.fromState)} → ${stateLabelOf(entry.toState)}`}
            </p>
            {entry.reason === null || entry.reason === undefined ? null : (
              <p className="text-xs text-ink-soft">사유: {entry.reason}</p>
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}

/** 상태 하나의 배지 — 머리와 타임라인이 같은 표를 쓴다. */
export function CaseStateBadge({ state, testId }: { readonly state: BidCaseState; readonly testId?: string }) {
  return <StateBadge display={displayOrUnknown(BID_CASE_STATE_DISPLAY, state)} testId={testId} />;
}
