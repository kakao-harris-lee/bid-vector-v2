import type { BidTimelineEntry } from '../api/queries';
import { StateBadge } from '../components/StateBadge';
import {
  BID_CASE_STATE_DISPLAY,
  TIMELINE_ACTOR_DISPLAY,
  commandDisplayOf,
  stateDisplayOf,
  type BidCaseState,
} from '../display/caseDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { instantDisplayOf } from '../display/timeDisplay';

import { InstantText } from './InstantText';

/**
 * 건별 타임라인(§6.2 C 6) — **모든 전이가 시각·주체와 함께** 남는다. 순서는 서버가 준 순서
 * 그대로다(화면이 다시 정렬하지 않는다).
 *
 * 상태·명령 문면은 **표시 모듈의 공용 조회**를 지난다(M11/11B). 앞 판은 상태 칸만 이 파일이
 * 자기 조회를 적었다 — 계약이 그 칸을 `type: string` 으로 두던 때의 자리이고, 표 밖 값에
 * 서버가 준 코드를 문면인 척 세웠다. 계약이 어휘를 닫은 지금도 **배포 비틀림**으로 번들이
 * 모르는 값이 닿을 수 있고, 그 값은 공용 자리가 「보이는 미지 상태」로 낸다.
 */
export function WorksheetTimeline({ entries }: { readonly entries: readonly BidTimelineEntry[] }) {
  return (
    <section className="rounded-lg border border-line bg-card p-4">
      <h3 className="text-sm font-semibold">타임라인</h3>
      <ol data-testid="timeline" className="mt-3 flex flex-col gap-3">
        {entries.map((entry) => (
          <li key={entry.caseVersion} className="border-l-2 border-line pl-3">
            <p className="flex flex-wrap items-center gap-2 text-sm">
              <span>{stateDisplayOf(entry.toState).label}</span>
              <StateBadge display={displayOrUnknown(TIMELINE_ACTOR_DISPLAY, entry.actorKind)} />
              {entry.retroactive ? <span className="text-xs text-unjudged-ink">소급</span> : null}
            </p>
            <p className="flex flex-wrap items-center gap-2 text-xs text-ink-soft">
              <InstantText display={instantDisplayOf(entry.occurredAt)} />
              {' · '}
              {/* 명령은 **표의 문면**이다(`OPEN-M9-TIMELINE-COMMAND-WORDING`) — 앞 판은 서버가 준
                  토큰 원문(`ADD_TO_BID_BOX` 꼴)이 이 자리에 그대로 섰다. 한 명령이 여러 동작인
                  자리(초안 수정 · 소급 확인)는 배지의 아래층이 그 사실을 나른다. */}
              <StateBadge display={commandDisplayOf(entry.command)} />
              {entry.fromState === null || entry.fromState === undefined
                ? ''
                : ` · ${stateDisplayOf(entry.fromState).label} → ${stateDisplayOf(entry.toState).label}`}
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
