import type { MyBidAggregate } from '../api/queries';
import { StateBadge } from '../components/StateBadge';
import {
  AWARD_RATE_STATE_DISPLAY,
  RECONCILIATION_BLOCKER_DISPLAY,
  SAMPLE_NOTE,
  rateAxesOf,
  type RateAxis,
  type ReconciliationBlocker,
} from '../display/myBidResultDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

/** 계수 한 칸. 수는 **서버 값**이고 이 컴포넌트가 더하거나 빼지 않는다. */
function Count({ label, value, testId }: { readonly label: string; readonly value: number; readonly testId: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd data-testid={testId} className="font-numeric text-sm">
        {value}
      </dd>
    </div>
  );
}

/**
 * 집계 한 축. **두 축이 같은 컴포넌트를 쓴다** — 축마다 JSX 를 두면 한쪽만 고쳐지는 길이
 * 생기고, 「병기」라는 계약이 화면에서 한 축으로 접힐 수 있다.
 *
 * 비율은 **서버 문자열 그대로**다(D-7E3b-8). 퍼센트로 환산하지 않는 이유 둘: ⓐ 서버가 배정밀도
 * 를 피하려고 문자열로 싣는데 숫자로 접으면 그 보호가 사라진다 ⓑ 환산은 화면이 계산한 값이다.
 * 대신 **분자와 분모를 곁에** 둔다 — 그래야 서버가 보낸 `0.5` 가 건수로 읽히지 않는다.
 */
function Axis({ axis }: { readonly axis: RateAxis }) {
  return (
    <section data-testid={`rate-axis-${axis.id}`} className="rounded-lg border border-line p-3">
      <h4 className="text-sm font-medium">{axis.label}</h4>
      <p className="mt-0.5 text-xs text-ink-soft">{axis.why}</p>

      <div className="mt-2 flex flex-wrap items-baseline gap-2">
        <StateBadge
          display={displayOrUnknown(AWARD_RATE_STATE_DISPLAY, axis.state)}
          testId={`rate-state-${axis.id}`}
        />
        {/* **분모 0 이면 이 자리가 아예 없다**(0% 가 아니다) — 값 노드의 부재가 그 사실이다. */}
        {axis.rate === null ? null : (
          <span data-testid={`rate-value-${axis.id}`} className="font-numeric text-lg font-semibold">
            {axis.rate}
          </span>
        )}
      </div>

      <dl className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3">
        <Count label="낙찰(분자)" value={axis.wonCount} testId={`rate-won-${axis.id}`} />
        <Count label="패찰" value={axis.lostCount} testId={`rate-lost-${axis.id}`} />
        <Count label="분모(낙찰+패찰)" value={axis.denominator} testId={`rate-denominator-${axis.id}`} />
        <Count label="유찰·재입찰(분모 밖)" value={axis.failedOrRebidCount} testId={`rate-failed-${axis.id}`} />
        <Count label="미확정(분모 밖)" value={axis.undecidedCount} testId={`rate-undecided-${axis.id}`} />
      </dl>
    </section>
  );
}

/**
 * 판정 불가 사유 전수. **화면이 특정 사유를 전제하지 않는다**(D-7E3b-7 ⒜) — 하네스의 답은
 * `NOT_OPENED_YET` 이고 운영 환경의 답은 다를 수 있다(착수 실측). 서버가 준 목록을 그대로 편다.
 *
 * 목록이 비면 **자리를 만들지 않는다**: 표본이 0 이면 사유도 없고, 그때는 비율 상태 자신이
 * 답이다(빈 목록을 그리면 「사유를 모른다」로 읽힌다).
 */
function Blockers({ blockers }: { readonly blockers: readonly ReconciliationBlocker[] }) {
  if (blockers.length === 0) {
    return null;
  }
  return (
    <div data-testid="reconciliation-blockers" className="mt-3">
      <h4 className="text-sm font-medium">대사가 판정에 이르지 못한 사유</h4>
      <ul className="mt-1 flex flex-col gap-1">
        {blockers.map((blocker) => {
          const display = displayOrUnknown(RECONCILIATION_BLOCKER_DISPLAY, blocker);
          return (
            <li key={blocker} data-testid={`blocker-${blocker}`} className="text-xs">
              <span className="font-medium">{display.label}</span>
              {display.why === undefined ? null : <span className="ml-1 text-ink-soft">{display.why}</span>}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/**
 * 투찰 대비 낙찰률 — **두 축을 나란히**(D-7E3b-2).
 *
 * 화면의 **계산은 0** 이다: 비율·분자·분모·제외 수가 전부 서버 값이고, 목록을 세는 자리가
 * 없다. 건별 목록과 이 집계는 같은 응답에서 왔으므로 서로 어긋날 수도 없다.
 *
 * 표본 수에 **정의 문면을 붙인다**(D-7E3b-7 ⒟) — 표본은 「투찰 기록이 있는 실투찰 건 전부」라
 * 위 카드 수(결과 단계만)와 다르다(실측: 표본 3 · 카드 2).
 */
export function ResultsAggregate({ aggregate }: { readonly aggregate: MyBidAggregate }) {
  const axes = rateAxesOf(aggregate);

  return (
    <section data-testid="results-aggregate" className="rounded-lg border border-line bg-card p-4">
      <div className="flex flex-wrap items-baseline gap-2">
        <h3 className="text-sm font-semibold">투찰 대비 낙찰률</h3>
        <span className="text-xs text-ink-soft">
          표본{' '}
          <span data-testid="sample-size" className="font-numeric">
            {aggregate.sampleSize}
          </span>
        </span>
      </div>
      <p data-testid="sample-note" className="mt-0.5 text-xs text-ink-soft">
        {SAMPLE_NOTE}
      </p>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        {axes.map((axis) => (
          <Axis key={axis.id} axis={axis} />
        ))}
      </div>

      <Blockers blockers={aggregate.reconciliationBlockers} />
    </section>
  );
}
