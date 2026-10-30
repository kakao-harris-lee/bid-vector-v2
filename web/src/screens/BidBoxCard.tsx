import { useId, useState } from 'react';
import { Link } from 'react-router';

import type { BidCase } from '../api/queries';
import { Field, StateBadge } from '../components/StateBadge';
import { BID_CASE_STATE_DISPLAY, NEXT_CHECKPOINT_DISPLAY } from '../display/caseDisplay';
import {
  ACTION_INPUT,
  OVERDUE_DISPLAY,
  bidBoxActionOf,
  type BidBoxAction,
  type BidBoxActionKind,
} from '../display/bidBoxDisplay';
import { daysUntil } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

/** 운영자가 적은 것 — 전이 하나를 보낼 때 실리는 값이다. 빈 문자열은 「아직 고르지 않았다」다. */
export interface ActionPayload {
  readonly kind: BidBoxActionKind;
  readonly value: string;
}

/**
 * 「남은 날 수」는 요구표가 클라이언트 계산으로 승인한 **유일한 자리**다(7E-1 과 같은 함수를
 * 쓴다). 지난 쪽도 날 수로만 말하고 「지났다」는 판정을 만들지 않는다 — 그 판정은 서버의
 * `overdue` 하나다.
 */
function checkpointHint(bidCase: BidCase, now: Date): string | null {
  const days = daysUntil(bidCase.nextCheckpointAt, now);
  if (days === null) {
    return null;
  }
  return days >= 0 ? `${days}일 남음` : `${Math.abs(days)}일 지남`;
}

/**
 * 전이 하나를 받는 자리. 입력의 모양은 `ACTION_INPUT` 표가 정하고 이 컴포넌트는 그 모양을
 * 그린다 — 행동마다 `if` 를 더하지 않는다.
 *
 * **기본값이 없다**: 고르기 전에 보내면 무엇이 빠졌는지 말하고 요청을 보내지 않는다.
 */
function ActionForm({
  action,
  onSend,
  onCancel,
}: {
  readonly action: BidBoxAction;
  readonly onSend: (payload: ActionPayload) => void;
  readonly onCancel: () => void;
}) {
  const input = ACTION_INPUT[action.kind];
  const [value, setValue] = useState('');
  const [missing, setMissing] = useState(false);
  const groupId = useId();

  function send(): void {
    if (value === '') {
      setMissing(true);
      return;
    }
    onSend({ kind: action.kind, value });
  }

  return (
    <div className="mt-3 rounded-md border border-line p-3">
      <p className="text-xs font-medium">{action.label}</p>
      {input.shape === 'DATETIME' ? (
        <label className="mt-2 flex flex-col gap-1">
          <span className="text-xs text-ink-soft">{input.label}</span>
          <input
            type="datetime-local"
            value={value}
            onChange={(event) => setValue(event.target.value)}
            className="rounded-md border border-line bg-card px-2 py-1 text-sm"
          />
        </label>
      ) : null}
      {input.shape === 'CHOICE' ? (
        <fieldset className="mt-2">
          <legend className="text-xs text-ink-soft">{input.label}</legend>
          <div className="mt-1 flex flex-wrap gap-3">
            {(input.choices ?? []).map((choice) => (
              <label key={choice.value} className="flex items-center gap-1 text-sm">
                <input
                  type="radio"
                  name={groupId}
                  value={choice.value}
                  checked={value === choice.value}
                  onChange={() => setValue(choice.value)}
                />
                {choice.label}
              </label>
            ))}
          </div>
        </fieldset>
      ) : null}
      {missing && value === '' ? (
        <p data-testid="action-missing" role="alert" className="mt-2 text-xs text-bad-ink">
          {input.label} 을(를) 먼저 고르십시오 — 고르지 않은 값을 보내지 않습니다.
        </p>
      ) : null}
      <div className="mt-3 flex gap-2">
        <button
          type="button"
          onClick={send}
          className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink"
        >
          보내기
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-line px-3 py-1 text-sm hover:bg-neutral-skin"
        >
          그만두기
        </button>
      </div>
    </div>
  );
}

/** 행동 버튼 하나 — `LINK` 는 화면 이동이고 `INSTANT` 는 바로 보내며 나머지는 폼을 연다. */
function ActionButton({
  action,
  bidCase,
  onOpen,
  onSend,
}: {
  readonly action: BidBoxAction;
  readonly bidCase: BidCase;
  readonly onOpen: (action: BidBoxAction) => void;
  readonly onSend: (payload: ActionPayload) => void;
}) {
  const shape = ACTION_INPUT[action.kind].shape;
  if (shape === 'LINK') {
    return (
      <Link
        to={`/notices/${encodeURIComponent(bidCase.noticeNumber)}/${encodeURIComponent(bidCase.noticeRound)}`}
        className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink"
      >
        {action.label}
      </Link>
    );
  }
  return (
    <button
      type="button"
      onClick={() => (shape === 'INSTANT' ? onSend({ kind: action.kind, value: 'instant' }) : onOpen(action))}
      className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink"
    >
      {action.label}
    </button>
  );
}

/**
 * 건 하나의 카드. 보이는 값 전부가 `BidCase` 의 칸이다 — 상태·다음 확인 시점·지난 일정은
 * **서버가 준 값**이고 화면은 표로 문면만 고른다(D-7E-4).
 */
export function BidBoxCard({
  bidCase,
  now,
  onSend,
}: {
  readonly bidCase: BidCase;
  readonly now: Date;
  readonly onSend: (caseId: number, payload: ActionPayload) => void;
}) {
  const [open, setOpen] = useState<BidBoxAction | null>(null);
  const row = bidBoxActionOf(bidCase.state);
  const hint = checkpointHint(bidCase, now);

  return (
    <li data-testid={`bid-case-${bidCase.caseId}`} className="rounded-lg border border-line bg-card p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-numeric text-sm font-medium">{bidCase.noticeNumber}</span>
        <span className="text-xs text-ink-soft">차수 {bidCase.noticeRound}</span>
        <StateBadge
          display={displayOrUnknown(BID_CASE_STATE_DISPLAY, bidCase.state)}
          testId={`state-${bidCase.caseId}`}
        />
        {/* 경고는 **서버의 판정 하나**에서만 뜬다 — 시각 비교가 이 자리에 없다. */}
        {bidCase.overdue ? <StateBadge display={OVERDUE_DISPLAY} testId={`overdue-${bidCase.caseId}`} /> : null}
      </div>

      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-0.5">
          <dt className="text-xs text-ink-soft">다음 확인 시점</dt>
          <dd className="text-sm">
            <StateBadge
              display={displayOrUnknown(NEXT_CHECKPOINT_DISPLAY, bidCase.nextCheckpointKind)}
              testId={`checkpoint-kind-${bidCase.caseId}`}
            />
          </dd>
        </div>
        <Field
          label="시점"
          value={bidCase.nextCheckpointAt ?? null}
          hint={hint ?? undefined}
          testId={`checkpoint-at-${bidCase.caseId}`}
        />
      </dl>

      {row.primary === null && row.secondary === null ? null : (
        <div className="mt-3 flex flex-wrap gap-2">
          {[row.primary, row.secondary]
            .filter((action): action is BidBoxAction => action !== null)
            .map((action) => (
              <ActionButton
                key={action.kind}
                action={action}
                bidCase={bidCase}
                onOpen={setOpen}
                onSend={(payload) => onSend(bidCase.caseId, payload)}
              />
            ))}
        </div>
      )}

      {open === null ? null : (
        <ActionForm
          action={open}
          onCancel={() => setOpen(null)}
          onSend={(payload) => {
            setOpen(null);
            onSend(bidCase.caseId, payload);
          }}
        />
      )}
    </li>
  );
}
