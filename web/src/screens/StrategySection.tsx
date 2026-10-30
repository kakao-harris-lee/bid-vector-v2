import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { ApiError } from '../api/apiError';
import type { components } from '../api/generated/operator-api';
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
  sentValueView,
  SLOT_INPUT,
  STRATEGY_FIELD_DISPLAY,
  STRATEGY_FIELD_ORDER,
  STRATEGY_FIELD_VALUE,
  valueBodyOf,
  type FieldValueView,
  type StrategyField,
} from '../display/editSessionDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

type ValueRequest = components['schemas']['EditSessionValueRequest'];

const HTTP_CONFLICT = 409;

export const STRATEGY_QUERY_KEY = ['strategy'] as const;

/** 한 명령 — `commandId` 를 **여기서 한 번** 만든다. 재시도가 같은 값을 다시 쓰므로 멱등하다. */
type EditCommand =
  | { readonly kind: 'BEGIN'; readonly commandId: string; readonly field: StrategyField }
  | {
      readonly kind: 'VALUE';
      readonly commandId: string;
      readonly sessionId: string;
      readonly field: StrategyField;
      /** **보낸 본문 그대로**. 확인 화면의 「보낸 값」이 이것에서 나온다(CR H-2 ②). */
      readonly body: ValueRequest;
    }
  | { readonly kind: 'CONFIRM'; readonly commandId: string; readonly sessionId: string; readonly seenRevision: number }
  | { readonly kind: 'EDIT'; readonly commandId: string; readonly sessionId: string; readonly field: StrategyField }
  | { readonly kind: 'CANCEL'; readonly commandId: string; readonly sessionId: string };

/** 열린 세션 하나 — 서버가 준 상태와 **운영자가 방금 적은 값**을 함께 쥔다. */
interface OpenSession {
  readonly session: EditSessionResponse;
  readonly field: StrategyField;
  /** `null` 은 「아직 값을 보내지 않았다」다. 값을 보냈으면 **본문에서 뽑은 문면**이 온다. */
  readonly sent: FieldValueView | null;
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
        case 'VALUE':
          // 본문은 **보내기 전에** 만들어졌다(아래 `onSendValue`) — 여기서 다시 만들지 않는다.
          return provideEditSessionValue(command.sessionId, command.body);
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
      if (field === undefined) {
        // 어느 칸인지 모르면 **패널을 닫는다** — 기본값을 지어내면 그 뒤 「값 보내기」가 엉뚱한
        // 칸으로 간다(CR L-1).
        setOpen(null);
        setRaw('');
        return;
      }
      setOpen({
        session,
        field,
        // **보낸 본문의 값**에서 뽑은 문면이다 — 운영자가 적은 원문이 아니다(CR H-2 ②).
        sent: command.kind === 'VALUE' ? sentValueView(command.field, command.body) : null,
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
            // 본문을 **한 번만** 만든다(앞 판은 가짜 `commandId` 로 검증용 본문을 한 번 더
            // 만들었다 — CR L-10). 모양에 맞지 않으면 보내지 않고 받는 모양을 말한다.
            const commandId = crypto.randomUUID();
            const body = valueBodyOf(open.field, commandId, raw);
            if (body === null) {
              setMissing(true);
              return;
            }
            run.mutate({ kind: 'VALUE', commandId, sessionId: open.session.sessionId, field: open.field, body });
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
  /**
   * 보낸 값의 문면. 둘을 가른다.
   *  · `open.sent === null` — **아직 값을 보내지 않았다** → `Field` 가 「값 없음」을 낸다.
   *  · 보냈는데 그 본문의 값이 비었다(어휘 0개) — **조건을 지우려고 비워 보낸 것**이다. 빈 칸으로
   *    그리면 위와 같은 얼굴이 되어 무엇을 적용하려는지 말하지 못한다(여정이 잡은 자리).
   *
   * 값이 있으면 **본문에서 뽑은 문면**을 그대로 쓴다 — 운영자가 적은 원문이 아니다(CR H-2 ②).
   */
  const sentView = open.sent === null ? null : (open.sent.text ?? '비움 — 이 조건을 지웁니다');

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
              {input.accepts} 을(를) 적으십시오 — 자리 구분 쉼표·단위·꼬리 문자가 있으면 보내지
              않습니다(잘린 수가 저장되지 않게).
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
            <Field label="보낸 값" value={sentView} testId="edit-sent-value" />
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
