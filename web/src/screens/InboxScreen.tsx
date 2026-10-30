import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import { StateBadge } from '../components/StateBadge';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import {
  NOTIFICATION_DETAIL_DISPLAY,
  NOTIFICATION_KIND_DISPLAY,
  groupByNotice,
  type NoticeGroup,
} from '../display/notificationDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

const ABSENT = '값 없음';

/**
 * 읽음 처리 요청의 두 모양. **단건과 묶음을 갈라 둔 것이 계약의 모양**이다(선택적 `kind` 가
 * 뜻을 바꾸는 모양은 조용히 잘못 맞는다) — 화면도 그 둘을 한 값으로 합치지 않는다.
 */
type ReadRequest =
  | { readonly scope: 'ITEM'; readonly kind: NotificationItem['kind']; readonly noticeNumber: string; readonly noticeRound: string }
  | { readonly scope: 'NOTICE'; readonly noticeNumber: string; readonly noticeRound: string };

function sendRead(request: ReadRequest): Promise<MarkReadResult> {
  switch (request.scope) {
    case 'ITEM':
      return markNotificationRead({
        kind: request.kind,
        noticeNumber: request.noticeNumber,
        noticeRound: request.noticeRound,
      });
    case 'NOTICE':
      return markNoticeNotificationsRead({
        noticeNumber: request.noticeNumber,
        noticeRound: request.noticeRound,
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

/** 항목의 신원은 **종류와 공고 쌍**이다(합성 id 가 없다) — testId 도 그 셋으로 짓는다. */
function itemTestId(item: NotificationItem): string {
  return `notification-${item.kind}-${item.noticeNumber}-${item.noticeRound}`;
}

function NotificationRow({
  item,
  disabled,
  onRead,
}: {
  readonly item: NotificationItem;
  readonly disabled: boolean;
  readonly onRead: () => void;
}) {
  return (
    <li data-testid={itemTestId(item)} className="flex flex-wrap items-center gap-2 border-t border-line py-2">
      <StateBadge display={displayOrUnknown(NOTIFICATION_KIND_DISPLAY, item.kind)} />
      {/* 부가 사실은 **있으면 그린다** — 어느 종류가 그것을 갖는지는 서버의 규칙이고, 화면이
          그 규칙을 한 벌 더 적으면 두 규칙이 갈린다. */}
      {item.detail === null || item.detail === undefined ? null : (
        <StateBadge display={displayOrUnknown(NOTIFICATION_DETAIL_DISPLAY, item.detail)} />
      )}

      {/* **사실의 시각**이다(알림함에 적재된 시각이 아니다 — 계약). */}
      <time dateTime={item.occurredAt} className="font-numeric text-xs text-ink-soft">
        {item.occurredAt}
      </time>

      {/* 읽음 상태는 **서버가 준 `read`** 다 — 누른 적이 있는지를 화면이 기억하지 않는다. */}
      <span data-testid="read-state" className="text-xs">
        {item.read ? '읽음' : '안읽음'}
      </span>

      <Link
        to={`/notices/${encodeURIComponent(item.noticeNumber)}/${encodeURIComponent(item.noticeRound)}`}
        className="text-xs underline"
      >
        워크시트 열기
      </Link>

      {/* **버튼을 지우지 않는다**(r1 CR L-1): 서버 응답 뒤 재조회가 그 행을 읽음으로 만들면
          누른 버튼이 DOM 에서 사라져 포커스가 `<body>` 로 떨어졌다. 잠그고 문면만 바꾼다 —
          누른 자리가 그대로 남아 키보드 운영자가 위치를 잃지 않는다. */}
      <button
        type="button"
        disabled={disabled || item.read}
        onClick={onRead}
        className="rounded-md border border-line px-2 py-0.5 text-xs hover:bg-neutral-skin disabled:opacity-50"
      >
        {item.read ? '읽음' : '읽음으로'}
      </button>
    </li>
  );
}

function NoticeGroupBlock({
  group,
  index,
  disabled,
  onRead,
}: {
  readonly group: NoticeGroup;
  /**
   * 묶음의 **차례**(r1 CR L-3). `groupByNotice` 는 떨어진 같은 공고를 **두 묶음으로 남기는**
   * 것이 결정이라, 번호·차수만으로 짓는 testId·key 는 그때 겹친다(strict locator 가 모호해지고
   * React key 도 「한 공고·한 종류에 항목 하나」라는 **서버 계약**에 기대게 된다). 차례는
   * 그 계약에 기대지 않는다.
   */
  readonly index: number;
  readonly disabled: boolean;
  readonly onRead: (request: ReadRequest) => void;
}) {
  return (
    <li
      data-testid={`notice-group-${index}-${group.noticeNumber}-${group.noticeRound}`}
      className="rounded-lg border border-line bg-card p-4"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-numeric text-sm font-medium">{group.noticeNumber}</span>
        <span className="text-xs text-ink-soft">차수 {group.noticeRound}</span>

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

        <button
          type="button"
          disabled={disabled || group.unreadCount === 0}
          onClick={() =>
            onRead({ scope: 'NOTICE', noticeNumber: group.noticeNumber, noticeRound: group.noticeRound })
          }
          className="rounded-md border border-line px-2 py-0.5 text-xs hover:bg-neutral-skin disabled:opacity-50"
        >
          묶음 전체 읽음
        </button>
      </div>

      <ul className="mt-2 flex flex-col">
        {group.items.map((item) => (
          <NotificationRow
            key={itemTestId(item)}
            item={item}
            disabled={disabled}
            onRead={() =>
              onRead({
                scope: 'ITEM',
                kind: item.kind,
                noticeNumber: item.noticeNumber,
                noticeRound: item.noticeRound,
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

  const inbox = useQuery({ queryKey: NOTIFICATIONS_QUERY_KEY, queryFn: () => listNotifications() });

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
  });

  if (signedIn === false || isUnauthorized(inbox.error)) {
    return (
      <section data-testid="screen-inbox" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }

  const groups = groupByNotice(inbox.data?.rows ?? []);

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
          안읽음 {inbox.data === undefined ? ABSENT : inbox.data.unread}
        </span>
      </div>

      {read.isError ? (
        <WriteFailureFace
          what="읽음으로 바꾸지"
          error={read.error}
          onRetry={() => {
            if (read.variables !== undefined) {
              read.mutate(read.variables);
            }
          }}
        />
      ) : null}

      {inbox.isPending ? <Skeleton testId="inbox-loading" /> : null}
      {inbox.isError && !isUnauthorized(inbox.error) ? (
        <ErrorFace testId="inbox-error" onRetry={() => void inbox.refetch()} />
      ) : null}
      {inbox.isSuccess && groups.length === 0 ? (
        <EmptyFace display={EMPTY_DISPLAY} testId="inbox-empty" />
      ) : null}

      {groups.length === 0 ? null : (
        <ul className="flex flex-col gap-3">
          {groups.map((group, index) => (
            <NoticeGroupBlock
              key={`${index}-${group.noticeNumber}-${group.noticeRound}`}
              group={group}
              index={index}
              disabled={read.isPending}
              onRead={(request) => read.mutate(request)}
            />
          ))}
        </ul>
      )}

      <section
        data-testid="pending-notification-channel"
        className="rounded-lg border border-dashed border-line p-4"
      >
        <h3 className="text-sm font-medium">
          채널 복제(텔레그램·메일) <span className="text-absent-ink">준비 중</span>
        </h3>
        <p className="mt-1 text-xs text-ink-soft">
          외부 effect 는 알림함 기록까지입니다(R-4) — 채널 발송은 `OPEN-STR-12` 결정 뒤에 섭니다.
        </p>
      </section>
    </section>
  );
}
