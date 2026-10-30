import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { ApiError } from '../api/apiError';
import {
  beginEditSession,
  cancelEditSession,
  confirmEditSession,
  provideEditSessionValue,
  readEditSession,
  readStrategy,
  requestEditSessionEdit,
  type EditSessionResponse,
  type StrategyReadResponse,
} from '../api/queries';
import { ErrorFace, Skeleton } from '../components/ScreenFaces';
import { Field, StateBadge } from '../components/StateBadge';
import { isUnauthorized, WriteFailureFace } from '../components/WriteFailure';
import {
  EDIT_SESSION_CONFLICT_DISPLAY,
  EDIT_SESSION_STATE_DISPLAY,
  FIELD_VALUE_SLOT,
  SLOT_INPUT,
  STRATEGY_FIELD_DISPLAY,
  STRATEGY_FIELD_ORDER,
  STRATEGY_FIELD_VALUE,
  valueBodyOf,
  type StrategyField,
} from '../display/editSessionDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

const HTTP_CONFLICT = 409;

export const STRATEGY_QUERY_KEY = ['strategy'] as const;

/** 한 명령 — `commandId` 를 **여기서 한 번** 만든다. 재시도가 같은 값을 다시 쓰므로 멱등하다. */
type EditCommand =
  | { readonly kind: 'BEGIN'; readonly commandId: string; readonly field: StrategyField }
  | { readonly kind: 'VALUE'; readonly commandId: string; readonly sessionId: string; readonly field: StrategyField; readonly raw: string }
  | { readonly kind: 'CONFIRM'; readonly commandId: string; readonly sessionId: string; readonly seenRevision: number }
  | { readonly kind: 'EDIT'; readonly commandId: string; readonly sessionId: string; readonly field: StrategyField }
  | { readonly kind: 'CANCEL'; readonly commandId: string; readonly sessionId: string };

/** 열린 세션 하나 — 서버가 준 상태와 **운영자가 방금 적은 값**을 함께 쥔다. */
interface OpenSession {
  readonly session: EditSessionResponse;
  readonly field: StrategyField;
  readonly sent: string | null;
}

/**
 * 전략 설정 — **쓰기는 편집 세션 하나를 지난다**(계약에 전략을 직접 쓰는 operation 이 없다).
 *
 * 확인 화면이 보이는 것은 둘뿐이다: ⓐ `GET /api/strategy` 가 준 **지금 값** ⓑ 운영자가 방금
 * 적은 값. 세션 응답에 초안이 없으므로(계약) 그 밖의 값을 지어낼 자리가 없다.
 *
 * 세션 상태는 **서버가 준 `state`** 다 — `expiresAt` 을 지금 시각과 비교하지 않는다(D-7E-4).
 * 409 를 받으면 세션을 다시 읽어 **서버가 말하는 상태**를 보인다.
 */
export function StrategySection() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState<OpenSession | null>(null);
  const [raw, setRaw] = useState('');
  const [missing, setMissing] = useState(false);

  const strategy = useQuery({ queryKey: STRATEGY_QUERY_KEY, queryFn: readStrategy });

  const run = useMutation({
    mutationFn: async (command: EditCommand): Promise<EditSessionResponse> => {
      switch (command.kind) {
        case 'BEGIN':
          return beginEditSession({ field: command.field });
        case 'VALUE': {
          const body = valueBodyOf(command.field, command.commandId, command.raw);
          if (body === null) {
            // 수로 읽히지 않는 값은 **보내지 않는다** — 0 으로 지어내지 않는다.
            throw new ApiError(0, 'VALUE_NOT_READABLE');
          }
          return provideEditSessionValue(command.sessionId, body);
        }
        case 'CONFIRM':
          return confirmEditSession(command.sessionId, {
            commandId: command.commandId,
            seenRevision: command.seenRevision,
          });
        case 'EDIT':
          return requestEditSessionEdit(command.sessionId, {
            commandId: command.commandId,
            field: command.field,
          });
        case 'CANCEL':
          return cancelEditSession(command.sessionId, { commandId: command.commandId });
        default: {
          // 명령이 늘면 **이 자리가 컴파일에서 멈춘다**.
          const unreachable: never = command;
          throw new Error(`모르는 편집 명령: ${String(unreachable)}`);
        }
      }
    },
    onSuccess: async (session, command) => {
      if (session.state === 'APPLIED' || session.state === 'CANCELLED' || session.state === 'EXPIRED') {
        setOpen(null);
        setRaw('');
        // 적용됐으면 **지금 값을 다시 읽는다** — 화면이 바뀐 값을 짐작하지 않는다.
        await queryClient.invalidateQueries({ queryKey: STRATEGY_QUERY_KEY });
        return;
      }
      const field = command.kind === 'CANCEL' || command.kind === 'CONFIRM' ? open?.field : command.field;
      setOpen({
        session,
        field: field ?? STRATEGY_FIELD_ORDER[0]!,
        sent: command.kind === 'VALUE' ? command.raw : null,
      });
      if (command.kind === 'EDIT') {
        setRaw('');
      }
    },
    /**
     * 409 는 **세션 쪽 사실**이다 — 서버가 접근 시점에 만료를 접거나 전략이 그동안 바뀐 것이다.
     * 그 상태를 추측하지 않고 세션을 다시 읽어 서버가 말하는 `state` 를 들고 온다.
     */
    onError: async (error) => {
      if (error instanceof ApiError && error.status === HTTP_CONFLICT && open !== null) {
        try {
          const session = await readEditSession(open.session.sessionId);
          setOpen({ ...open, session });
        } catch {
          // 다시 읽는 것도 실패하면 **아래 쓰기 실패 얼굴이 그대로 선다**(상태를 지어내지 않는다).
        }
      }
      await queryClient.invalidateQueries({ queryKey: STRATEGY_QUERY_KEY });
    },
  });

  if (strategy.isPending) {
    return <Skeleton testId="strategy-loading" />;
  }
  if (strategy.isError) {
    return isUnauthorized(strategy.error) ? null : (
      <ErrorFace testId="strategy-error" onRetry={() => void strategy.refetch()} />
    );
  }

  const current = strategy.data;
  const conflictCode = run.error instanceof ApiError ? run.error.code : null;

  return (
    <section data-testid="settings-strategy" className="flex flex-col gap-3">
      <h3 className="text-base font-semibold">감시 조건과 임계치</h3>
      <p className="text-xs text-ink-soft">
        값은 편집 세션을 지나 바뀝니다 — 고치기 → 값 → 확인. 개정 {current.revision}
      </p>

      {run.isError ? (
        <>
          <WriteFailureFace
            what="전략을 바꾸지"
            error={run.error}
            onRetry={() => {
              if (run.variables !== undefined) {
                // **같은 `commandId`** 로 다시 보낸다 — 멱등 키가 중복 적용을 막는다.
                run.mutate(run.variables);
              }
            }}
          />
          {conflictCode === null ? null : (
            <div data-testid="edit-conflict" className="rounded-md border border-line p-3">
              <StateBadge
                display={displayOrUnknown(EDIT_SESSION_CONFLICT_DISPLAY, conflictCode)}
                testId="edit-conflict-reason"
              />
            </div>
          )}
        </>
      ) : null}

      {open === null ? null : (
        <EditPanel
          open={open}
          strategy={current}
          raw={raw}
          missing={missing}
          busy={run.isPending}
          onRaw={(value) => {
            setRaw(value);
            setMissing(false);
          }}
          onSendValue={() => {
            if (valueBodyOf(open.field, 'probe', raw) === null) {
              setMissing(true);
              return;
            }
            run.mutate({
              kind: 'VALUE',
              commandId: crypto.randomUUID(),
              sessionId: open.session.sessionId,
              field: open.field,
              raw,
            });
          }}
          onConfirm={() =>
            run.mutate({
              kind: 'CONFIRM',
              commandId: crypto.randomUUID(),
              sessionId: open.session.sessionId,
              // **운영자가 본 개정**이다 — 그 사이 바뀌었으면 서버가 거부한다(우회 3).
              seenRevision: current.revision,
            })
          }
          onEdit={() =>
            run.mutate({
              kind: 'EDIT',
              commandId: crypto.randomUUID(),
              sessionId: open.session.sessionId,
              field: open.field,
            })
          }
          onCancel={() =>
            run.mutate({ kind: 'CANCEL', commandId: crypto.randomUUID(), sessionId: open.session.sessionId })
          }
        />
      )}

      <dl className="grid gap-3 sm:grid-cols-2">
        {STRATEGY_FIELD_ORDER.map((field) => {
          const display = STRATEGY_FIELD_DISPLAY[field];
          const view = STRATEGY_FIELD_VALUE[field](current);
          return (
            <div key={field} data-testid={`strategy-row-${field}`} className="rounded-md border border-line p-3">
              <Field
                label={display.label}
                value={view.text}
                hint={display.why}
                testId={`strategy-value-${field}`}
              />
              {view.qualifier === undefined ? null : (
                <p data-testid={`strategy-qualifier-${field}`} className="mt-1 text-xs text-ink-soft">
                  {view.qualifier}
                </p>
              )}
              <button
                type="button"
                disabled={open !== null}
                onClick={() => {
                  setRaw('');
                  setMissing(false);
                  run.mutate({ kind: 'BEGIN', commandId: crypto.randomUUID(), field });
                }}
                className="mt-2 rounded-md border border-line px-2 py-1 text-xs hover:bg-neutral-skin disabled:opacity-50"
              >
                고치기
              </button>
            </div>
          );
        })}
      </dl>
    </section>
  );
}

/** 열린 세션의 얼굴 — **서버가 준 `state`** 가 어느 단계를 보일지 정한다. */
function EditPanel({
  open,
  strategy,
  raw,
  missing,
  busy,
  onRaw,
  onSendValue,
  onConfirm,
  onEdit,
  onCancel,
}: {
  readonly open: OpenSession;
  readonly strategy: StrategyReadResponse;
  readonly raw: string;
  readonly missing: boolean;
  readonly busy: boolean;
  readonly onRaw: (value: string) => void;
  readonly onSendValue: () => void;
  readonly onConfirm: () => void;
  readonly onEdit: () => void;
  readonly onCancel: () => void;
}) {
  const display = STRATEGY_FIELD_DISPLAY[open.field];
  const input = SLOT_INPUT[FIELD_VALUE_SLOT[open.field]];
  const currentView = STRATEGY_FIELD_VALUE[open.field](strategy);

  return (
    <div data-testid="edit-session" className="rounded-lg border border-accent/40 bg-card p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium">{display.label}</span>
        <StateBadge
          display={displayOrUnknown(EDIT_SESSION_STATE_DISPLAY, open.session.state)}
          testId="edit-session-state"
        />
      </div>

      {open.session.state === 'WAITING_FOR_VALUE' ? (
        <div className="mt-3 flex flex-col gap-2">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">{input.hint}</span>
            <input
              name="edit-value"
              aria-label={display.label}
              inputMode={input.kind === 'TERMS' ? 'text' : 'numeric'}
              value={raw}
              onChange={(event) => onRaw(event.target.value)}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
            />
          </label>
          {missing ? (
            <p data-testid="edit-value-missing" role="alert" className="text-xs text-bad-ink">
              수로 읽을 수 있는 값을 적으십시오 — 읽을 수 없는 값을 보내지 않습니다.
            </p>
          ) : null}
          <div className="flex gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={onSendValue}
              className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
            >
              값 보내기
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={onCancel}
              className="rounded-md border border-line px-3 py-1 text-sm hover:bg-neutral-skin"
            >
              그만두기
            </button>
          </div>
        </div>
      ) : null}

      {open.session.state === 'WAITING_FOR_CONFIRMATION' ? (
        <div className="mt-3 flex flex-col gap-2">
          {/* 보이는 값 둘 — **지금 값**(서버)과 **보낸 값**(운영자 입력)뿐이다. */}
          <dl className="grid gap-2 sm:grid-cols-2">
            <Field label="지금 값" value={currentView.text} testId="edit-current-value" />
            <Field label="보낸 값" value={open.sent} testId="edit-sent-value" />
          </dl>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={onConfirm}
              className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
            >
              적용
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={onEdit}
              className="rounded-md border border-line px-3 py-1 text-sm hover:bg-neutral-skin"
            >
              값 고치기
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={onCancel}
              className="rounded-md border border-line px-3 py-1 text-sm hover:bg-neutral-skin"
            >
              그만두기
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
