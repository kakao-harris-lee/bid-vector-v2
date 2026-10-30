import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import {
  awaitOpening,
  enterQualificationReview,
  listBidCases,
  recordOpening,
  recordOutcome,
  resumeCase,
  type BidCase,
} from '../api/queries';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { ToastHost, useToast } from '../components/Toast';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import { BID_BOX_TABS, tabOf, type BidBoxTabId } from '../display/bidBoxDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { BidBoxCard, type ActionPayload } from './BidBoxCard';

/** D 는 **실투찰**만 보인다 — 페이퍼(가상) 탭은 SET-03 이 `근거 부족` 이라 만들지 않는다. */
const BID_KIND: BidCase['bidKind'] = 'REAL';

const QUERY_KEY = ['bid-cases', BID_KIND] as const;

/** 탭 ↔ 패널을 묶는 id 둘. 한 화면에 하나뿐이라 고정 문자열로 둔다. */
const PANEL_ID = 'bid-box-panel';

function tabId(id: BidBoxTabId): string {
  return `bid-box-tab-${id}`;
}

/** 탭이 비었을 때의 문면 — 「이 묶음에 건이 없다」는 **사실**이고 사유를 지어내지 않는다. */
const EMPTY_TAB_DISPLAY = {
  label: '이 묶음에 건이 없습니다.',
  why: '다른 탭을 보거나 오늘의 공고에서 담으십시오',
  tone: 'absent',
} as const;

interface Sent {
  readonly caseId: number;
  readonly payload: ActionPayload;
}

/**
 * 전이 하나를 보낸다 — **행동 종류 전수의 `switch`** 다. 종류가 늘면 `never` 단언이 컴파일에서
 * 멈춘다(기본 분기가 없다). `datetime-local` 값은 브라우저 지역 시각이라 ISO 로 바꿔 싣는다.
 */
function send({ caseId, payload }: Sent): Promise<BidCase> {
  switch (payload.kind) {
    case 'RESUME':
      return resumeCase(caseId);
    case 'AWAIT_OPENING':
      return awaitOpening(caseId, { openingAt: new Date(payload.value).toISOString() });
    case 'RECORD_OPENING':
      return recordOpening(caseId, { provisionalFirst: payload.value === 'true' });
    case 'ENTER_QUALIFICATION_REVIEW':
      return enterQualificationReview(caseId, { documentDueAt: new Date(payload.value).toISOString() });
    case 'RECORD_OUTCOME':
      // 선택지는 계약 enum 전수에서 왔다 — 이 자리에서 문자열을 검사하지 않는다.
      return recordOutcome(caseId, { outcome: payload.value as 'WON' | 'LOST' | 'FAILED_OR_REBID' });
    case 'OPEN_WORKSHEET':
      // 화면 이동은 서버를 부르지 않는다 — 이 자리에 닿을 길이 없다(`ActionButton` 이 링크다).
      throw new Error('전이가 아닌 행동이 전송에 닿았다: OPEN_WORKSHEET');
    default: {
      // 종류가 늘면 **이 자리가 컴파일에서 멈춘다** — 기본 분기가 값을 지어내지 않는다.
      const unreachable: never = payload.kind;
      throw new Error(`모르는 행동 종류: ${String(unreachable)}`);
    }
  }
}

/**
 * 화면 D — 내 입찰함(§6.2 D). 탭은 **API 가 준 상태의 묶음**이고, 다음 확인 시점·지난 일정
 * 경고도 서버가 판정해 보낸 값이다(D-7E-4 — 화면이 시각을 비교하지 않는다).
 *
 * 부르는 operation 은 목록 다섯뿐이다: 목록 읽기 하나와 「제출함」 뒤 생애주기 전이 넷(되돌림
 * 포함). 담기 뒤 네 상태의 다음 전이는 화면 C 가 가지므로 그 카드는 C 로 보낸다.
 */
export function BidBoxScreen() {
  const queryClient = useQueryClient();
  const { signedIn } = useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  const [tab, setTab] = useState<BidBoxTabId>('watching');
  /** 화살표로 옮긴 뒤 **그 탭에 포커스를 둔다**(클릭으로 고른 경우는 브라우저가 이미 둔다). */
  const [focusTab, setFocusTab] = useState<BidBoxTabId | null>(null);

  const cases = useQuery({ queryKey: QUERY_KEY, queryFn: () => listBidCases(BID_KIND) });

  const transition = useMutation({
    mutationFn: send,
    onSuccess: async (updated) => {
      // 실행 취소를 두지 않는다 — 이 전이들의 **역전이가 계약에 없다**(되돌림은 `resume` 하나고
      // 그것 자체가 역전이다). 「취소할 수 있다」고 보이면 거짓말이 된다(D-7E-5).
      show({ text: '보냈습니다.' });
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
      // 상태가 바뀌었으면 그 건이 있는 탭으로 따라간다 — 보낸 뒤 빈 탭을 보지 않게 한다.
      setTab(tabOf(updated.state));
    },
    // 실패는 **서버 상태를 다시 읽고** 얼굴을 낸다 — 카드가 옛 버튼을 계속 보이면 운영자가
    // 다시 누르고, 그 사이 실제로 전이됐는지는 아무도 모른다.
    onError: async () => {
      dismiss();
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });

  // 세션이 없다는 **서버의 답**은 재시도로 풀리지 않는다 — 일시 장애와 같은 얼굴을 내지 않는다.
  if (signedIn === false || isUnauthorized(cases.error)) {
    return (
      <section data-testid="screen-bid-box" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }

  const rows = cases.data ?? [];
  const shown = rows.filter((row) => tabOf(row.state) === tab);

  return (
    <section data-testid="screen-bid-box" className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <h2 className="text-lg font-semibold">내 입찰함</h2>

      {/* 좁은 화면에서 가로로 스크롤되는 줄 — 셸의 탐색과 같은 모양이다(§6.5 모바일 우선).
          **WAI-ARIA 탭 패턴을 끝까지 배선한다**(CR r1 M-5): 앞 판은 `tablist`·`tab`·`aria-selected`
          까지만 두어 보조기술이 「탭 1/6, 선택됨」이라 읽고도 **그 탭이 무엇을 여는지 말할 수
          없었다**. `aria-controls` ↔ `tabpanel` 로 묶고, 비선택 탭은 tab 순서에서 빼고
          (roving tabindex) 좌우 화살표로 옮긴다. */}
      <div role="tablist" aria-label="상태" className="flex gap-1 overflow-x-auto text-sm">
        {BID_BOX_TABS.map((one, index) => {
          const count = rows.filter((row) => tabOf(row.state) === one.id).length;
          const selected = tab === one.id;
          return (
            <button
              key={one.id}
              type="button"
              role="tab"
              id={tabId(one.id)}
              aria-selected={selected}
              aria-controls={PANEL_ID}
              tabIndex={selected ? 0 : -1}
              ref={(node) => {
                if (node !== null && focusTab === one.id) {
                  node.focus();
                }
              }}
              onKeyDown={(event) => {
                const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
                if (step === 0) {
                  return;
                }
                event.preventDefault();
                // 줄 끝에서 감싼다 — 그것이 그 패턴이 요구하는 이동이다.
                const next = BID_BOX_TABS[(index + step + BID_BOX_TABS.length) % BID_BOX_TABS.length]!;
                setTab(next.id);
                setFocusTab(next.id);
              }}
              onClick={() => {
                setTab(one.id);
                setFocusTab(null);
              }}
              className={`shrink-0 rounded-md px-2 py-1 ${
                selected ? 'bg-neutral-skin font-medium text-neutral-ink' : 'text-ink-soft hover:bg-neutral-skin'
              }`}
            >
              {/* 받은 행의 수다 — 계약에 총수 헤더가 없으므로 「전체 몇 건」이라 말하지 않는다. */}
              {one.label} {count}
            </button>
          );
        })}
      </div>

      {transition.isError ? (
        <WriteFailureFace
          what="보내지"
          error={transition.error}
          onRetry={() => {
            if (transition.variables !== undefined) {
              transition.mutate(transition.variables);
            }
          }}
        />
      ) : null}

      {cases.isPending ? <Skeleton testId="bid-box-loading" /> : null}
      {cases.isError && !isUnauthorized(cases.error) ? (
        <ErrorFace testId="bid-box-error" onRetry={() => void cases.refetch()} />
      ) : null}
      {cases.isSuccess && shown.length === 0 ? (
        <EmptyFace display={EMPTY_TAB_DISPLAY} testId="bid-box-empty" />
      ) : null}

      {shown.length === 0 ? null : (
        <ul role="tabpanel" id={PANEL_ID} aria-labelledby={tabId(tab)} className="flex flex-col gap-3">
          {shown.map((row) => (
            <BidBoxCard
              key={row.caseId}
              bidCase={row}
              onSend={(caseId, payload) => transition.mutate({ caseId, payload })}
            />
          ))}
        </ul>
      )}

      <ToastHost toast={toast} onDismiss={dismiss} />
    </section>
  );
}
