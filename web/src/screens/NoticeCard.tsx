import { Link } from 'react-router';

import type { NoticeSummary, SuggestedNotice } from '../api/queries';
import { StateBadge } from '../components/StateBadge';
import { amountDisplayOf, daysUntil, noticeStatusDisplayOf, SUGGESTION_LICENSE_VERDICT_DISPLAY } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { UNJUDGED_AXIS_DISPLAY } from '../display/worksheetDisplay';

/** 워크시트 주소 — 공고번호·차수가 **경로의 두 칸**이다(식별자를 한 문자열로 접지 않는다). */
export function worksheetPathOf(notice: Pick<NoticeSummary, 'noticeNumber' | 'noticeRound'>): string {
  return `/notices/${encodeURIComponent(notice.noticeNumber)}/${encodeURIComponent(notice.noticeRound)}`;
}

/** 남은 날 — **숫자만** 낸다. 「마감됨」 같은 상태 말은 만들지 않는다(그것은 `status` 가 한다). */
function DeadlineBadge({ deadlineAt, now }: { readonly deadlineAt: string | null | undefined; readonly now: Date }) {
  const days = daysUntil(deadlineAt, now);
  if (days === null) {
    return <span className="text-xs text-absent-ink">마감 없음</span>;
  }
  return (
    <span className="font-numeric text-xs" title="마감까지 남은 날(내림)">
      {days >= 0 ? `D-${days}` : `D+${-days}`}
    </span>
  );
}

/**
 * 공고 카드 한 줄(§6.2 B) — 제목 · 발주기관 · 기초금액(+provenance) · D-day · 상태 · 다음
 * 행동 하나. 모든 값은 **응답 필드 그대로**이고 카드가 계산하는 것은 남은 날뿐이다.
 */
export function NoticeCard({
  notice,
  now,
  action,
}: {
  readonly notice: NoticeSummary;
  readonly now: Date;
  readonly action: React.ReactNode;
}) {
  const amount = amountDisplayOf(notice);
  return (
    <li
      data-testid={`notice-card-${notice.noticeNumber}`}
      className="rounded-lg border border-line bg-card p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <Link to={worksheetPathOf(notice)} className="min-w-0 flex-1 hover:underline">
          <span data-testid="notice-title" className="block text-sm font-semibold break-words">
            {notice.title ?? notice.noticeNumber}
          </span>
        </Link>
        <StateBadge display={noticeStatusDisplayOf(notice.status)} testId="notice-status" />
      </div>
      <dl className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
        <div className="flex flex-col gap-0.5">
          <dt className="text-xs text-ink-soft">발주기관</dt>
          <dd data-testid="notice-agency" className="text-sm break-words">
            {notice.demandAgencyName ?? notice.noticeAgencyName ?? <span className="text-absent-ink">값 없음</span>}
          </dd>
        </div>
        <div className="flex flex-col gap-0.5">
          <dt className="text-xs text-ink-soft" title="투찰 기준 금액. 추정가격과 다르다.">
            기초금액
          </dt>
          <dd data-testid="notice-amount" className="font-numeric text-sm">
            {amount.amount}
          </dd>
          <dd data-testid="notice-amount-provenance" className="text-xs text-ink-soft">
            {amount.provenance ?? ''}
          </dd>
        </div>
        <div className="flex flex-col gap-0.5">
          <dt className="text-xs text-ink-soft">마감</dt>
          <dd>
            <DeadlineBadge deadlineAt={notice.deadlineAt} now={now} />
          </dd>
        </div>
        <div className="flex items-end">{action}</div>
      </dl>
    </li>
  );
}

/**
 * 제안 카드 — 공고 카드와 **같은 형태**에 면허 축 판정을 더한다(§6.2 B 「검색 결과와 제안
 * 목록이 같은 카드」). 지역·실적·시공능력은 아직 판정하지 않으므로 그 사실을 함께 싣는다.
 */
export function SuggestionCard({
  suggestion,
  now,
  action,
}: {
  readonly suggestion: SuggestedNotice;
  readonly now: Date;
  readonly action: React.ReactNode;
}) {
  const amount = amountDisplayOf(suggestion);
  return (
    <li
      data-testid={`suggestion-card-${suggestion.noticeNumber}`}
      className="rounded-lg border border-line bg-card p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <Link to={worksheetPathOf(suggestion)} className="min-w-0 flex-1 text-sm font-semibold hover:underline">
          {suggestion.title ?? suggestion.noticeNumber}
        </Link>
        <StateBadge
          display={displayOrUnknown(SUGGESTION_LICENSE_VERDICT_DISPLAY, suggestion.licenseVerdict)}
          testId="suggestion-license"
        />
      </div>
      <p className="mt-2 font-numeric text-sm">{amount.amount}</p>
      <p className="text-xs text-ink-soft">{amount.provenance ?? ''}</p>
      <p className="mt-1">
        <DeadlineBadge deadlineAt={suggestion.deadlineAt} now={now} />
      </p>
      <p data-testid="suggestion-unjudged" className="mt-2 flex flex-wrap gap-1">
        {suggestion.unjudgedAxes.map((axis) => (
          <StateBadge key={axis} display={displayOrUnknown(UNJUDGED_AXIS_DISPLAY, axis)} />
        ))}
      </p>
      <div className="mt-3">{action}</div>
    </li>
  );
}
