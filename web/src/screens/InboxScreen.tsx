import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';

import {
  listNotifications,
  markNoticeNotificationsRead,
  markNotificationRead,
  type MarkReadResult,
  type NotificationItem,
} from '../api/queries';
import { NOTIFICATIONS_QUERY_KEY } from '../api/queryKeys';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { Button, LinkButton } from '../components/Button';
import { cardClass } from '../components/Card';
import { StateBadge } from '../components/StateBadge';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import {
  NOTIFICATION_BID_KIND_DISPLAY,
  NOTIFICATION_DETAIL_DISPLAY,
  NOTIFICATION_KIND_DISPLAY,
  groupNotifications,
  notificationItemTestId,
  type NotificationGroup,
} from '../display/notificationDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { instantDisplayOf } from '../display/timeDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { InstantText } from './InstantText';

const ABSENT = '값 없음';

/** 한 쪽 크기 — 계약의 상한(100) 안이고 서버 기본값과 같다. 「더 보기」가 다음 쪽을 이어 붙인다. */
const PAGE_SIZE = 20;

/**
 * 읽음 처리 요청의 두 모양. **단건과 묶음을 갈라 둔 것이 계약의 모양**이다(선택적 `kind` 가
 * 뜻을 바꾸는 모양은 조용히 잘못 맞는다) — 화면도 그 둘을 한 값으로 합치지 않는다.
 */
type ReadRequest = {
  /** 쓰기가 끝난 뒤 포커스를 돌려놓을 **DOM 노드**(행이냐 묶음이냐) — id 가 아니다(r3 CR M-1). */
  readonly focus: FocusTarget;
} & (
  | {
      readonly scope: 'ITEM';
      readonly kind: NotificationItem['kind'];
      readonly noticeNumber: string;
      readonly noticeRound: string;
      readonly bidKind: NotificationItem['bidKind'];
    }
  | {
      readonly scope: 'NOTICE';
      readonly noticeNumber: string;
      readonly noticeRound: string;
      readonly bidKind: NotificationItem['bidKind'];
    }
);

function sendRead(request: ReadRequest): Promise<MarkReadResult> {
  switch (request.scope) {
    case 'ITEM':
      return markNotificationRead({
        kind: request.kind,
        noticeNumber: request.noticeNumber,
        noticeRound: request.noticeRound,
        bidKind: request.bidKind,
      });
    case 'NOTICE':
      return markNoticeNotificationsRead({
        noticeNumber: request.noticeNumber,
        noticeRound: request.noticeRound,
        bidKind: request.bidKind,
      });
    default: {
      const unreachable: never = request;
      throw new Error(`모르는 읽음 처리 범위: ${JSON.stringify(unreachable)}`);
    }
  }
}

const EMPTY_DISPLAY = {
  label: '알림이 없습니다.',
  why: '읽지 않은 소식이 생기면 여기에 쌓입니다',
  tone: 'absent',
} as const;

/**
 * 이 묶음이 **같은 키의 몇 번째**인가 — key 가 삽입에 흔들리지 않게 하는 자리(r2 CR L-3).
 * 키는 번호·차수·**투찰 종류** 셋이다(M9) — 종류를 빼면 서면 묶음과 실투찰 묶음이 같은 차례를
 * 다투어 key 가 다시 겹친다.
 */
function sameGroupOrdinal(groups: readonly NotificationGroup[], index: number): number {
  const self = groups[index]!;
  let seen = 0;
  for (let i = 0; i < index; i += 1) {
    const one = groups[i]!;
    if (one.noticeNumber === self.noticeNumber && one.noticeRound === self.noticeRound && one.bidKind === self.bidKind) {
      seen += 1;
    }
  }
  return seen;
}

/**
 * 쓰기 뒤 **포커스를 돌려놓을 자리**(r2 CR M-1). 누른 버튼으로는 못 돌아간다 — 읽은 뒤 그
 * 버튼은 `disabled` 이고, 포커스된 요소가 `disabled` 가 되면 브라우저가 그것을 blur 한다
 * (HTML 의 unfocusing steps). 그래서 **행·묶음 자체**를 받을 자리로 두고 `tabIndex={-1}` 로
 * 포커스를 받을 수 있게 한다.
 *
 * **대상은 DOM 노드다 — 문자열 id 가 아니다**(r3 CR M-1·L-5). `groupNotifications` 는 떨어진 같은
 * 공고를 **두 묶음으로 남기는 것이 결정**이라, 번호·차수로 지은 id 는 그때 문서에 둘이 되고
 * `getElementById` 가 **첫째**를 돌려준다 — 둘째 묶음에서 누르면 포커스가 위로 끌려갔다.
 * 노드를 그대로 들면 유일성에 기댈 일이 없다(r2 가 key 를 「DOM 노드의 정체」로 잰 것과 같은 축).
 */
type FocusTarget = { current: HTMLLIElement | null };

function NotificationRow({
  item,
  disabled,
  onRead,
  onOpen,
}: {
  readonly item: NotificationItem;
  readonly disabled: boolean;
  readonly onRead: (focus: FocusTarget) => void;
  /** 링크를 열 때 — 쓰기를 **걸고** 떠난다(아래 [readOnOpen]). */
  readonly onOpen: () => void;
}) {
  const focusTarget = useRef<HTMLLIElement | null>(null);

  return (
    <li
      ref={focusTarget}
      tabIndex={-1}
      data-testid={notificationItemTestId(item)}
      className="flex flex-wrap items-center gap-2 border-t border-line py-2 focus:outline focus:outline-2"
    >
      <StateBadge display={displayOrUnknown(NOTIFICATION_KIND_DISPLAY, item.kind)} />
      {/* 부가 사실은 **있으면 그린다** — 어느 종류가 그것을 갖는지는 서버의 규칙이고, 화면이
          그 규칙을 한 벌 더 적으면 두 규칙이 갈린다. */}
      {item.detail === null || item.detail === undefined ? null : (
        <StateBadge display={displayOrUnknown(NOTIFICATION_DETAIL_DISPLAY, item.detail)} />
      )}

      {/* **사실의 시각**이다(알림함에 적재된 시각이 아니다 — 계약). */}
      <InstantText display={instantDisplayOf(item.occurredAt)} className="text-xs text-ink-soft" />

      {/* 읽음 상태는 **서버가 준 `read`** 다 — 누른 적이 있는지를 화면이 기억하지 않는다. */}
      <span data-testid="read-state" className="text-xs">
        {item.read ? '읽음' : '안읽음'}
      </span>

      {/*
        본문 링크도 **블록 타깃**이다(M13/13B) — 이동 대상은 그대로다. 달라진 것은 **열면
        읽음**이라는 것뿐이고(M13/13E, 받는 항목 9), 그 쓰기는 이동을 막지 않는다.
      */}
      <Link
        to={`/notices/${encodeURIComponent(item.noticeNumber)}/${encodeURIComponent(item.noticeRound)}`}
        onClick={onOpen}
        className="inline-flex min-h-11 items-center rounded-md text-sm underline"
      >
        워크시트 열기
      </Link>

      {/* **버튼을 지우지 않는다**(r1 CR L-1): 서버 응답 뒤 재조회가 그 행을 읽음으로 만들면
          누른 버튼이 DOM 에서 사라져 포커스가 `<body>` 로 떨어졌다. 잠그고 문면만 바꾼다 —
          누른 자리가 그대로 남아 키보드 운영자가 위치를 잃지 않는다. */}
      <Button disabled={disabled || item.read} onClick={() => onRead(focusTarget)}>
        {item.read ? '읽음' : '읽음으로'}
      </Button>
    </li>
  );
}

function NotificationGroupBlock({
  group,
  index,
  disabled,
  onRead,
  onOpen,
}: {
  readonly group: NotificationGroup;
  /**
   * 묶음의 **차례**(r1 CR L-3). `groupNotifications` 는 떨어진 같은 묶음을 **둘로 남기는**
   * 것이 결정이라, 번호·차수만으로 짓는 testId·key 는 그때 겹친다(strict locator 가 모호해지고
   * React key 도 「한 공고·한 종류에 항목 하나」라는 **서버 계약**에 기대게 된다). 차례는
   * 그 계약에 기대지 않는다.
   */
  readonly index: number;
  readonly disabled: boolean;
  readonly onRead: (request: ReadRequest) => void;
  readonly onOpen: (item: NotificationItem) => void;
}) {
  const focusTarget = useRef<HTMLLIElement | null>(null);

  return (
    <li
      ref={focusTarget}
      tabIndex={-1}
      data-testid={`notice-group-${index}-${group.noticeNumber}-${group.noticeRound}-${group.bidKind}`}
      className={`${cardClass()} focus:outline focus:outline-2`}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-numeric text-sm font-medium">{group.noticeNumber}</span>
        <span className="text-xs text-ink-soft">차수 {group.noticeRound}</span>

        {/* **투찰 종류 축**(M9/paper-bid) — 서면 연습의 소식과 실제 투찰의 소식이 **다른
            묶음**이고, 그 구분이 화면에 보이지 않으면 갈라 놓은 뜻이 없다. 세 값 전부에 문면이
            있으므로 「어느 값에 그리는가」를 이 자리가 따로 정하지 않는다. */}
        {/* testId 를 준다 — 여정이 이 배지를 **차례가 아니라 이름으로** 집는다(리뷰 L). */}
        <StateBadge
          display={displayOrUnknown(NOTIFICATION_BID_KIND_DISPLAY, group.bidKind)}
          testId="group-bid-kind"
        />

        {/* **「공고 행이 없다」와 「행은 있고 제목이 비었다」는 다른 사실이다**(계약). 전자는
            보존 기한이 지나 파기됐거나 아직 수집되지 않은 것이고, 후자는 행이 있다. */}
        {group.noticeKnown ? (
          <span className="text-sm">{group.noticeTitle ?? <span className="text-absent-ink">{ABSENT}</span>}</span>
        ) : (
          <span className="text-xs text-absent-ink">공고를 모름 — 파기됐거나 아직 수집되지 않았습니다</span>
        )}

        {/* 계수는 **서버 값**이다 — 이 페이지의 행을 세지 않는다. */}
        <span className="text-xs text-ink-soft">
          항목 {group.itemCount} · 안읽음 {group.unreadCount}
          {group.hasMoreThanShown ? ' · 이 페이지에 다 오지 않았습니다' : ''}
        </span>

        <Button
          disabled={disabled || group.unreadCount === 0}
          onClick={() =>
            onRead({
              scope: 'NOTICE',
              focus: focusTarget,
              noticeNumber: group.noticeNumber,
              noticeRound: group.noticeRound,
              bidKind: group.bidKind,
            })
          }
        >
          묶음 전체 읽음
        </Button>
      </div>

      <ul className="mt-2 flex flex-col">
        {group.items.map((item) => (
          <NotificationRow
            key={notificationItemTestId(item)}
            item={item}
            disabled={disabled}
            onOpen={() => onOpen(item)}
            onRead={(focus) =>
              onRead({
                scope: 'ITEM',
                focus,
                kind: item.kind,
                noticeNumber: item.noticeNumber,
                noticeRound: item.noticeRound,
                bidKind: item.bidKind,
              })
            }
          />
        ))}
      </ul>
    </li>
  );
}

/**
 * 알림함(§6.5 「알림함 = 받은편지함」, M7/7E-3a).
 *
 * **읽음 상태는 서버가 준 `read` 다**(D-7E3a-1 — 낙관 갱신 금지): 누르면 요청을 보내고, 그
 * 응답 뒤 목록을 다시 읽어 바뀐 행을 받는다. 누른 사실을 화면이 기억해 미리 바꾸지 않는다 —
 * 쓰기가 실패해도 읽은 것처럼 보이면 운영자가 그 소식을 다시 보지 않는다.
 *
 * **안읽음 배지는 `X-Unread-Count` 헤더에서만** 온다(D-7E3a-6). 본문 행을 세면 한 페이지만
 * 보고 전체를 말하게 되고, `unreadOnly` 필터를 켜는 순간 그 수가 흔들린다.
 *
 * 채널 복제(텔레그램·메일)는 **빈 자리**다 — R-4 가 외부 effect 를 알림함 기록까지로 두었다.
 */
export function InboxScreen() {
  const queryClient = useQueryClient();
  const { signedIn } = useSessionCsrf();

  /**
   * **다음 쪽은 홈과 같은 「더 보기」 꼴이다**(M13/13E, 받는 항목 9). 앞 판은 쪽 인자를 하나도
   * 주지 않아, 묶음이 「이 페이지에 다 오지 않았습니다」라고 말하면서 **다음으로 가는 길이
   * 없었다**. 다음 쪽의 유무는 헤더의 전체 건수(필터를 지난 수)와 지금까지 받은 수로 정한다.
   *
   * 질의 **키는 그대로**다(`['notifications']`). 셸의 안 읽음 배지가 그 접두 아래
   * (`['notifications','unread']`)에 살아 읽음 쓰기의 무효화가 배지까지 번지는데, 키를 바꾸면
   * 그 관계가 조용히 끊긴다(13B 알려진 제한 10 이 넘긴 자리).
   */
  const inbox = useInfiniteQuery({
    queryKey: NOTIFICATIONS_QUERY_KEY,
    queryFn: ({ pageParam }) => listNotifications({ page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => {
      const loaded = allPages.reduce((count, one) => count + one.rows.length, 0);
      // 빈 쪽을 받으면 더 받을 것이 없다 — 계수와 행이 갈려도 「더 보기」가 아무것도 더하지
      // 않으면서 남지 않는다(화면 B 의 같은 판단).
      if (lastPage.rows.length === 0) {
        return undefined;
      }
      return loaded < lastPage.total ? allPages.length : undefined;
    },
  });
  const inboxPages = inbox.data?.pages;
  // **안읽음 전수는 헤더 값**이고 쪽마다 같은 뜻이다(계정 전수) — 가장 나중에 받은 답을 쓴다.
  const unread = inboxPages?.at(-1)?.unread;

  /**
   * 쓰기 뒤 포커스를 **명시적으로** 돌려놓는다(r2 CR M-1 · `BidBoxScreen` 의 같은 관례).
   *
   * 「버튼을 지우지 않는다」만으로는 닫히지 않는 자리다: 포커스된 요소가 `disabled` 가 되면
   * 브라우저가 그것을 blur 하고(HTML unfocusing steps) 포커스는 앞 판과 똑같이 `<body>` 로
   * 떨어진다. 더구나 `disabled` 는 **누른 순간** 서므로 쓰기가 실패하는 실행에서도 그렇다.
   *
   * **방아쇠는 전이의 종결이다 — 데이터 참조가 아니다**(r3 CR M-2). react-query 의 structural
   * sharing 은 깊은 값이 같으면 **이전 참조를 그대로** 돌려주므로, `[inbox.data]` 에 걸면
   * ⓐ 쓰기 **실패** 경로(서버 상태가 안 바뀌어 재조회 결과가 같다)에서 effect 가 서지 않고
   * ⓑ 그때 장전이 남아 **나중의 무관한 재조회**가 포커스를 옛 행으로 끌어간다. 종결마다 느는
   * 수를 dep 으로 두면 그 둘이 함께 닫힌다.
   */
  const pendingFocus = useRef<FocusTarget | null>(null);
  const failureFocus = useRef<HTMLDivElement | null>(null);
  const [settleCount, setSettleCount] = useState(0);

  useEffect(() => {
    if (settleCount === 0) {
      return;
    }
    const target = pendingFocus.current;
    // **무슨 일이 있어도 비운다** — 남기면 다음 재렌더가 그 자리로 포커스를 끌어간다.
    pendingFocus.current = null;
    // 실패했으면 **실패 얼굴**로 보낸다(`role="alert"` 이라 읽히기도 한다) — 그 자리가 운영자가
    // 다음에 할 일(다시 시도)을 들고 있다.
    const node = failureFocus.current ?? target?.current ?? null;
    node?.focus();
  }, [settleCount]);

  const read = useMutation({
    mutationFn: sendRead,
    onSuccess: async () => {
      // 화면을 **서버의 답으로** 되돌린다 — 응답의 `markedCount`·`unreadCount` 를 믿고 행을
      // 고쳐 그리지 않는다(그것이 낙관 갱신의 다른 이름이다).
      await queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
    },
    // **무효화를 기다린 뒤** 선다 — 그때 화면은 이미 서버의 답으로 그려져 있다.
    onSettled: () => setSettleCount((count) => count + 1),
  });

  /**
   * **링크를 열면 읽음**(받는 항목 9) — 쓰기를 **걸고 바로 이동한다**.
   *
   * 그래서 이 경로는 **머무는 경로의 뮤테이션을 쓰지 않는다**: 공유 `read` 의 `isPending`·
   * `isError`·포커스 복귀 배선은 알림함에 **남아 있는 사람**의 것이다. 떠나는 길이 그것을
   * 건드리면 전 행이 잠기고 볼 사람도 없는 실패 얼굴이 뜬다. app 수준 `queryClient` 를 쥔
   * 평범한 호출은 그 상태를 건드리지 않는다.
   *
   * (수정 라운드 1 — 리뷰 L-1 의 정정. 앞 판 주석은 「언마운트 뒤 콜백이 서는지 보장되지
   * 않는다」였는데 설치본에서 **틀렸다**: v5 의 훅 수준 콜백은 Mutation 인스턴스에 붙어 마운트와
   * 무관하게 선다. 마운트에 매인 것은 `mutate(vars, { … })` 의 per-call 콜백뿐이다.)
   *
   * **실패는 무효화하지 않는다.** 떠난 뒤라 알림함 안의 실패 얼굴은 볼 수 없고, 쓰기가 실패한
   * 사실은 **셸의 안 읽음 배지가 줄지 않는 것**으로 보인다(계약 13E 행). 실패에서도 다시 읽으면
   * 같은 수를 받아 와 「변하지 않았다」가 **우연히** 참이 되므로, 그 우연에 기대지 않는다.
   *
   * 낙관 갱신은 **여기서도 없다**(D-7E3a-1): 서버가 답하기 전에는 어느 자리도 읽음으로 바뀌지
   * 않는다 — 성공하면 무효화가 배지와 목록을 서버의 답으로 되돌린다.
   */
  function readOnOpen(item: NotificationItem): void {
    if (item.read) {
      return;
    }
    void markNotificationRead({
      kind: item.kind,
      noticeNumber: item.noticeNumber,
      noticeRound: item.noticeRound,
      bidKind: item.bidKind,
    })
      .then(() => queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY }))
      .catch(() => {
        // 실패의 신호는 **배지가 줄지 않는 것**이다(위 주석) — 떠난 화면에 얼굴을 세울 자리가 없다.
      });
  }

  if (signedIn === false || isUnauthorized(inbox.error)) {
    return (
      <section data-testid="screen-inbox" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }

  /**
   * 받은 쪽들을 **이어 붙여** 묶는다. 묶음은 **연속 접기**라(계약) 쪽 경계에 걸쳐 있던 묶음이
   * 다음 쪽을 받으면 한 묶음이 되고, 그때 「이 페이지에 다 오지 않았습니다」가 스스로 사라진다.
   *
   * **같은 항목이 두 쪽에 걸칠 수 있다**(수정 라운드 1 — 리뷰 M-3): offset 쪽 사이에 알림이
   * 적재되면 목록이 밀려 다음 쪽이 앞 쪽의 마지막 항목을 다시 낸다. 행의 신원은 네 칸
   * (종류·번호·차수·투찰 종류)이 전부이므로 접지 않으면 React key 가 겹치고 같은 `data-testid`
   * 가 문서에 둘이 되어 strict locator 가 모호해진다. **먼저 온 자리를 남긴다** — 서버가 준
   * 차례가 그 자리이고, 뒤에 온 사본을 남기면 항목이 제 자리보다 아래로 내려간다.
   *
   * **접는 것은 쪽 경계뿐이다.** 한 쪽 **안**의 같은 신원은 말없이 합치지 않는다 — 그것은 쪽
   * 꼴이 만든 사본이 아니라 서버가 그렇게 보낸 것이고, 이 화면의 관례는 「어긋남은 화면에
   * 보이는 쪽이 낫다」다(떨어진 같은 묶음을 둘로 남기는 결정과 같은 자리).
   */
  const fromEarlierPages = new Set<string>();
  const rows = (inboxPages ?? []).flatMap((one) => {
    const kept = one.rows.filter((row) => !fromEarlierPages.has(notificationItemTestId(row)));
    for (const row of one.rows) {
      fromEarlierPages.add(notificationItemTestId(row));
    }
    return kept;
  });
  const groups = groupNotifications(rows);

  return (
    <section data-testid="screen-inbox" className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-semibold">알림함</h2>
        {/* 숨기지 않는다 — 0 도 **답**이다(안 보이면 「모른다」로 읽힌다).
            **live region 이다**(r1 CR L-2 · §6.5 즉시 반응): 읽음 처리의 성공 신호가 이 숫자와
            행 문면의 조용한 변화뿐이었다. 토스트를 새로 들이는 대신 **이미 바뀌는 값**을
            보조기술이 읽게 한다 — 알림함에는 되돌릴 행동이 없어 「실행 취소」 토스트가 할 일이 없다. */}
        <span
          data-testid="inbox-unread-badge"
          role="status"
          aria-live="polite"
          className="rounded-md bg-neutral-skin px-2 py-0.5 text-xs"
        >
          안읽음 {unread === undefined ? ABSENT : unread}
        </span>
      </div>

      {read.isError ? (
        <div ref={failureFocus} tabIndex={-1} className="focus:outline focus:outline-2">
        <WriteFailureFace
          what="읽음으로 바꾸지"
          error={read.error}
          onRetry={() => {
            if (read.variables !== undefined) {
              // **다시 장전한다**(r3 CR M-2 ③). 이 버튼은 클릭 경로를 지나지 않으므로 장전이
              // 비어 있고, 성공하면 실패 얼굴이 사라지면서 포커스가 또 `<body>` 로 떨어진다.
              pendingFocus.current = read.variables.focus;
              read.mutate(read.variables);
            }
          }}
        />
        </div>
      ) : null}

      {inbox.isPending ? <Skeleton testId="inbox-loading" /> : null}
      {inbox.isError && !isUnauthorized(inbox.error) ? (
        <ErrorFace testId="inbox-error" onRetry={() => void inbox.refetch()} />
      ) : null}
      {inbox.isSuccess && groups.length === 0 ? (
        /* 빈 화면이 **다음 걸음**을 준다(받는 항목 7) — 소식이 없으면 할 일은 공고를 보는 것이다. */
        <EmptyFace
          display={EMPTY_DISPLAY}
          testId="inbox-empty"
          action={<LinkButton to="/">오늘의 공고 보기</LinkButton>}
        />
      ) : null}

      {groups.length === 0 ? null : (
        <ul className="flex flex-col gap-3">
          {groups.map((group, index) => (
            <NotificationGroupBlock
              /**
               * **key 는 차례를 쓰지 않는다**(r2 CR L-3): 맨 앞에 묶음이 하나 끼면 뒤 묶음의
               * key 가 전부 밀려 통째로 재마운트되고, 그러면 「버튼이 DOM 에 남는다」도 그
               * 실행에서는 성립하지 않는다. 같은 **묶음 키** 안에서의 차례만 붙여 갈라진 두
               * 묶음을 가른다(testId 는 strict locator 때문에 전체 차례를 그대로 쓴다).
               */
              key={`${group.noticeNumber}-${group.noticeRound}-${group.bidKind}-${sameGroupOrdinal(groups, index)}`}
              group={group}
              index={index}
              disabled={read.isPending}
              onRead={(request) => {
                pendingFocus.current = request.focus;
                read.mutate(request);
              }}
              onOpen={readOnOpen}
            />
          ))}
        </ul>
      )}

      {inbox.hasNextPage ? (
        <div className="self-start">
          <Button
            testId="inbox-more"
            disabled={inbox.isFetchingNextPage}
            onClick={() => void inbox.fetchNextPage()}
          >
            더 보기
          </Button>
        </div>
      ) : null}

      <section
        data-testid="pending-notification-channel"
        className="rounded-lg border border-dashed border-line p-4"
      >
        <h3 className="text-sm font-medium">
          채널 복제(텔레그램·메일) <span className="text-absent-ink">준비 중</span>
        </h3>
        <p className="mt-1 text-xs text-ink-soft">
          지금은 이 알림함에만 적습니다. 텔레그램·메일로 보내는 것은 보내는 방법을 정한 뒤에 엽니다.
        </p>
      </section>
    </section>
  );
}
