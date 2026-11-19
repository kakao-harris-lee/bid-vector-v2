import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import {
  awaitOpening,
  deferCase,
  enterQualificationReview,
  listBidCases,
  recordOpening,
  recordOutcome,
  resumeCase,
  skipCase,
  type BidCase,
} from '../api/queries';
import { LinkButton } from '../components/Button';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { ToastHost, useToast } from '../components/Toast';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import { bidCasesQueryKey } from '../api/queryKeys';
import { BID_BOX_TABS, isBidBoxTabId, tabOf, type BidBoxTabId } from '../display/bidBoxDisplay';
import { localInputToIso } from '../display/timeDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { BidBoxCard, type ActionPayload } from './BidBoxCard';

/** D 는 **실투찰**만 보인다 — 페이퍼(가상) 탭은 SET-03 이 `근거 부족` 이라 만들지 않는다. */
const BID_KIND: BidCase['bidKind'] = 'REAL';

const QUERY_KEY = bidCasesQueryKey(BID_KIND);

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
/**
 * `datetime-local` 값을 wire 의 ISO 로. **변환 자리는 `timeDisplay` 하나**다(M9 D-2 · r1 v L-1)
 * — 앞 판은 이 파일이 `new Date(...)` 를 직접 불렀고, 그래서 시계 금지를 「`Date` 전역 값 참조
 * 금지」로 좁히지 못했다.
 *
 * 읽히지 않으면 **보내지 않는다**(앞 판은 `RangeError` 를 던졌다 — 쓰기 실패 얼굴이 아니라
 * 오류 경계로 갔다).
 */
function instantOrRefuse(value: string): string {
  const iso = localInputToIso(value);
  if (iso === null) {
    throw new Error('시각을 읽지 못했다 — 날짜와 시각을 다시 적으십시오.');
  }
  return iso;
}

function send({ caseId, payload }: Sent): Promise<BidCase> {
  switch (payload.kind) {
    case 'DEFER':
      return deferCase(caseId, { remindAt: instantOrRefuse(payload.value) });
    case 'RESUME':
      return resumeCase(caseId);
    case 'SKIP':
      // 사유는 **적힌 그대로** 싣는다 — 다듬으면 기록에 남는 말이 운영자가 본 말과 달라진다.
      // 빈 값·상한 초과는 카드가 보내기 전에 거른다(표시 층의 판정).
      return skipCase(caseId, { reason: payload.value });
    case 'AWAIT_OPENING':
      return awaitOpening(caseId, { openingAt: instantOrRefuse(payload.value) });
    case 'RECORD_OPENING':
      return recordOpening(caseId, { provisionalFirst: payload.value === 'true' });
    case 'ENTER_QUALIFICATION_REVIEW':
      return enterQualificationReview(caseId, { documentDueAt: instantOrRefuse(payload.value) });
    case 'RECORD_OUTCOME':
      // 선택지의 값 타입이 **계약 enum 그대로**라 캐스팅이 없다(CR r1 L-8).
      return recordOutcome(caseId, { outcome: payload.outcome });
    case 'OPEN_WORKSHEET':
      // 화면 이동은 서버를 부르지 않는다 — 이 자리에 닿을 길이 없다(`ActionButton` 이 링크다).
      throw new Error('전이가 아닌 행동이 전송에 닿았다: OPEN_WORKSHEET');
    default: {
      // 종류가 늘면 **이 자리가 컴파일에서 멈춘다** — 기본 분기가 값을 지어내지 않는다.
      const unreachable: never = payload;
      throw new Error(`모르는 행동 종류: ${JSON.stringify(unreachable)}`);
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
  /**
   * 첫 탭은 **URL 이 정할 수 있다**(M7/7E-3a r1 M-2 — D-7E3a-8 의 「그 건의 D 카드(해당 상태
   * 탭)」). 화면 E 가 `?tab=<id>` 로 보내면 그 건이 **보이는 자리**에 착지한다 — 앞 판은 맨
   * `/bids` 라 첫 탭(관심)이 열렸고 결과 단계 건은 DOM 에 없었다.
   *
   * 칸이 없거나 **아는 탭이 아니면** 지금까지의 기본(`watching`)이다 — 바깥에서 온 문자열에
   * 탭을 지어내지 않는다.
   *
   * 초기값으로만 읽는다(`useState` 의 첫 인자). 그 뒤의 탭은 운영자의 클릭·화살표가 정하고,
   * URL 을 다시 읽어 덮으면 방금 고른 탭이 되돌려진다.
   */
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<BidBoxTabId>(() => {
    const requested = searchParams.get('tab');
    return isBidBoxTabId(requested) ? requested : 'watching';
  });
  /**
   * 화살표로 옮긴 뒤 **그 탭에 포커스를 한 번 둔다**(클릭은 브라우저가 이미 둔다).
   *
   * **ref 에 두고 쓰는 즉시 비운다**(CR r2 M2R-1): 앞 판은 이것을 state 로 두고 인라인 `ref`
   * 콜백에서 `focus()` 를 했다. 그 콜백은 렌더마다 정체가 바뀌어 React 가 떼고 다시 붙이고,
   * 그때 값이 **여전히 남아 있어** `focus()` 가 다시 돌았다 — 운영자가 포커스를 옮긴 뒤에도
   * 부모가 재렌더되는 순간(전이 `isPending` 전환 · 무효화 refetch · 창 포커스 복귀) 포커스가
   * 탭으로 끌려갔다. 카드의 `datetime-local` 을 채우는 중 전이 응답이 오면 입력에서 빠진다.
   */
  const pendingFocus = useRef<BidBoxTabId | null>(null);
  /**
   * 지금 떠 있는 토스트가 **실행 취소를 들었는가** — 실패가 그것을 지우지 않게 하는 자리다
   * (`onError`). 토스트가 떠나면 함께 비운다: 남겨 두면 **다음 실패**가 그 묵은 참을 보고
   * 「보냈습니다」 토스트를 실패 얼굴 옆에 남긴다.
   */
  const undoable = useRef(false);
  /**
   * 쓰기가 날아가 있는가 — **동기 가드**다(cr MEDIUM).
   *
   * 버튼의 `disabled` 만으로는 모자란다: 같은 틱에 들어온 연타는 `isPending` 이 올린 재렌더보다
   * **빠르다**(실측 — 세 클릭이 요청 셋이 됐다). 손가락·더블클릭이 한 프레임 안에 두 번 닿는
   * 것은 평범하므로, 막는 자리는 상태가 아니라 **이 ref** 여야 한다. `disabled` 는 그 사실을
   * 눈과 보조기기에 **보이는** 몫이다.
   */
  const sending = useRef(false);

  useEffect(() => {
    const target = pendingFocus.current;
    if (target === null) {
      return;
    }
    // **먼저 비운다** — 이 뒤의 어떤 재렌더도 이 자리를 다시 쓰지 않는다.
    pendingFocus.current = null;
    document.getElementById(tabId(target))?.focus();
  }, [tab]);

  const cases = useQuery({ queryKey: QUERY_KEY, queryFn: () => listBidCases(BID_KIND) });

  const transition = useMutation({
    mutationFn: send,
    onSuccess: async (updated, sent) => {
      /*
       * **가드를 여기서 푼다 — `onSettled` 가 아니라.**
       *
       * 토스트는 바로 아래에서 뜨는데 이 함수는 재조회를 기다리며 **계속 돈다**. 가드를 끝에서
       * 풀면 그 창 안의 실행 취소 누름이 거절되고, 누르면 토스트는 닫히므로 **아무것도 가지
       * 않은 채 되돌릴 길만 사라진다**(실측: 되돌림 호출 0). 실행 취소가 살아나는 시점과 가드가
       * 풀리는 시점은 **같아야 한다**.
       *
       * 거절 경로는 `onSettled` 가 계속 든다(그 자리는 토스트를 띄우지 않는다).
       */
      sending.current = false;
      /*
       * 실행 취소는 **되돌림 전이가 있는 곳에만** 둔다(D-7E-5) — 없는 자리에 두면 그 버튼이
       * 거짓말이 된다. 계약에서 되돌림 전이가 있는 쌍은 **하나**다: 「나중에」의 역전이가
       * 「다시 보기」이고 그 둘이 서로의 역이다. 그래서 분기도 하나다(표로 펼칠 갈래가 없다).
       *
       * 「건너뜀」은 유일한 종단이라 실행 취소가 **없고**, 그 대신 카드가 보내기 전에 한 번 더
       * 묻는다(입력 꼴 표의 `confirm`).
       *
       * 되돌릴 건의 식별자는 **서버가 준 응답**에서 온다 — 화면이 쥔 값이 아니다.
       */
      undoable.current = sent.payload.kind === 'DEFER';
      show(
        sent.payload.kind === 'DEFER'
          ? {
              text: '나중에 보기로 했습니다.',
              undo: {
                label: '실행 취소',
                run: () => {
                  submit(updated.caseId, { kind: 'RESUME', value: 'instant' });
                },
              },
            }
          : { text: '보냈습니다.' },
      );
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
      // 전이는 타임라인에도 사건을 남긴다 — C 의 쓰기와 **같은 관례**로 그 질의도 무효화한다
      // (CR r1 L-6: 두 화면의 관례가 갈리면 C 로 돌아갔을 때 낡은 목록을 볼 수 있다).
      await queryClient.invalidateQueries({ queryKey: ['timeline'] });
      // 상태가 바뀌었으면 그 건이 있는 탭으로 따라간다 — 보낸 뒤 빈 탭을 보지 않게 한다.
      setTab(tabOf(updated.state));
    },
    // 실패는 **서버 상태를 다시 읽고** 얼굴을 낸다 — 카드가 옛 버튼을 계속 보이면 운영자가
    // 다시 누르고, 그 사이 실제로 전이됐는지는 아무도 모른다.
    // 거절 경로의 풀기 — 성공 경로는 `onSuccess` 맨 앞에서 이미 풀었다(그 자리의 사유 참조).
    onSettled: () => {
      sending.current = false;
    },
    onError: async () => {
      /*
       * 뒤이은 쓰기가 실패해도 **성공한 쓰기의 되돌릴 길**은 지우지 않는다(cr MEDIUM).
       * 되돌릴 수 있는데 되돌릴 길이 화면에서 사라지는 것이 가장 비싼 손해다 — 실패는 쓰기 실패
       * 얼굴이 따로 말한다. 실행 취소가 없는 토스트(「보냈습니다」)만 걷는다: 그것이 실패 얼굴
       * 옆에 남으면 무엇이 성공이고 무엇이 실패인지 흐려진다.
       */
      if (!undoable.current) {
        dismiss();
      }
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });

  /**
   * 쓰기로 가는 **한 입구** — 카드의 버튼도, 토스트의 실행 취소도, 쓰기 실패 얼굴의 다시
   * 시도도 이 가드를 지난다. 입구가 둘이면 가드가 하나여도 소용이 없다(실측: 다시 시도 연타가
   * 가드 밖에서 요청 넷을 냈다).
   */
  function submit(caseId: number, payload: ActionPayload): void {
    if (sending.current) {
      return;
    }
    sending.current = true;
    transition.mutate({ caseId, payload });
  }

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
              onKeyDown={(event) => {
                const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
                if (step === 0) {
                  return;
                }
                event.preventDefault();
                // 줄 끝에서 감싼다 — 그것이 그 패턴이 요구하는 이동이다.
                const next = BID_BOX_TABS[(index + step + BID_BOX_TABS.length) % BID_BOX_TABS.length]!;
                pendingFocus.current = next.id;
                setTab(next.id);
              }}
              onClick={() => setTab(one.id)}
              // 탭은 자기 ARIA 배선(선택·방향키·패널)을 쥐므로 공유 `Button` 이 아니다 —
              // **터치 타깃 하한만** 같이 쓴다(M13/13B).
              className={`inline-flex min-h-11 shrink-0 items-center rounded-md px-3 ${
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
              submit(transition.variables.caseId, transition.variables.payload);
            }
          }}
        />
      ) : null}

      {cases.isPending ? <Skeleton testId="bid-box-loading" /> : null}
      {cases.isError && !isUnauthorized(cases.error) ? (
        <ErrorFace testId="bid-box-error" onRetry={() => void cases.refetch()} />
      ) : null}
      {/* 패널 래퍼를 **항상** 그린다(CR r2 L2R-1): 앞 판은 `role="tabpanel"` 을 카드 `<ul>` 자체에
          붙였고, 빈 탭에서는 그 `<ul>` 이 아예 없어 **`aria-controls` 가 없는 id 를 가리켰다**
          (실측: `getElementById` null · `tabpanel` 역할 수 0). 겹쳐서 `<li>` 들이 목록 부모를
          잃었다. 이제 `<div role="tabpanel">` 가 감싸고 그 안에 빈 얼굴 또는 `<ul>` 이 온다. */}
      <div role="tabpanel" id={PANEL_ID} aria-labelledby={tabId(tab)}>
        {cases.isSuccess && shown.length === 0 ? (
          /* 빈 얼굴이 **다음 걸음을 준다**(항목 7). 사유는 이미 문면에 있고 없던 것은 「그래서
             무엇을 하면 되는가」다 — 담을 건을 찾는 자리로 보낸다. */
          <EmptyFace
            display={EMPTY_TAB_DISPLAY}
            testId="bid-box-empty"
            action={
              <LinkButton to="/" tone="primary" testId="bid-box-empty-action">
                오늘의 공고에서 담기
              </LinkButton>
            }
          />
        ) : null}
        {shown.length === 0 ? null : (
          <ul className="flex flex-col gap-3">
            {shown.map((row) => (
              <BidBoxCard
                key={row.caseId}
                bidCase={row}
                pending={transition.isPending}
                onSend={submit}
              />
            ))}
          </ul>
        )}
      </div>

      <ToastHost
        toast={toast}
        onDismiss={() => {
          // 토스트가 떠나면 그 기억도 함께 비운다 — 묵은 참이 다음 실패의 판단을 흐린다.
          undoable.current = false;
          dismiss();
        }}
      />
    </section>
  );
}
