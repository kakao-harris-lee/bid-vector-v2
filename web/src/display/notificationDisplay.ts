import type { NotificationItem } from '../api/queries';

import type { StateDisplay } from './stateDisplay';

/**
 * 알림함의 표시(M7/7E-3a, NOTI-10).
 *
 * **종류와 부가 사실만 문면으로 옮긴다.** 「어느 종류가 부가 사실을 갖는가」는 저장 층의
 * CHECK 가 지는 서버의 규칙이고, 화면이 그것을 한 벌 더 적으면 두 규칙이 갈린다 — 여기서는
 * 값이 있으면 그리고 없으면 그리지 않는다.
 */
export type NotificationKind = NotificationItem['kind'];
export type NotificationDetail = NonNullable<NotificationItem['detail']>;

export const NOTIFICATION_KIND_DISPLAY = {
  NEW_CANDIDATE: { label: '새 후보', why: '자격을 통과한 공고가 생겼다', tone: 'good' },
  BID_DEADLINE_IMMINENT: { label: '투찰 마감 D-1', why: '아직 투찰을 기록하지 않았다', tone: 'bad' },
  BID_DEADLINE_TODAY: { label: '투찰 마감 당일', why: '아직 투찰을 기록하지 않았다', tone: 'bad' },
  DOCUMENT_DEADLINE_IMMINENT: { label: '서류 기한 D-2', tone: 'bad' },
  OPENING_COMPLETED: { label: '개찰 완료', tone: 'neutral' },
  AWARD_DECIDED: { label: '낙찰 여부 확정', tone: 'neutral' },
  NOTICE_REVISED: { label: '공고 변경', why: '정정공고가 떴다', tone: 'neutral' },
  BID_PRICE_REQUEST_RECORDED: { label: '투찰가 요청 기록', tone: 'neutral' },
} as const satisfies Record<NotificationKind, StateDisplay>;

/**
 * 부가 사실 넷. **「1위가 아니다」와 「낙찰이 아니다」는 다른 사실이다** — 1위 ≠ 낙찰이고
 * (UX §2.4) 그 사이에 적격심사가 있다. 한 문면으로 적으면 개찰 단계의 소식과 확정 단계의
 * 소식이 같아 보인다.
 */
export const NOTIFICATION_DETAIL_DISPLAY = {
  PROVISIONAL_FIRST: { label: '잠정 1위', tone: 'good' },
  NOT_PROVISIONAL_FIRST: { label: '잠정 1위 아님', tone: 'neutral' },
  AWARDED: { label: '낙찰', tone: 'good' },
  NOT_AWARDED: { label: '낙찰 아님', tone: 'neutral' },
} as const satisfies Record<NotificationDetail, StateDisplay>;

/**
 * 한 공고의 묶음. 표시 칸(`notice*`)은 묶음의 **첫 항목**에서 온다 — 같은 공고의 항목이면
 * 서버가 같은 값을 합쳐 싣기 때문이고, 그래서 이 자리는 고르는 것이지 짓는 것이 아니다.
 */
export interface NoticeGroup {
  readonly noticeNumber: string;
  readonly noticeRound: string;
  /** 이 페이지에 온 항목들 — 응답 순서 그대로다. */
  readonly items: readonly NotificationItem[];
  /** 묶음 **전체** 크기(서버 값, 페이지 무관). */
  readonly itemCount: number;
  /** 묶음의 안읽음 수(서버 값). */
  readonly unreadCount: number;
  /** 묶음이 페이지 경계에 걸렸는가 — 전체 크기가 받은 수보다 크다. */
  readonly hasMoreThanShown: boolean;
  /** 그 키의 공고 행이 저장소에 있는가. `false` 면 아래 세 칸이 전부 `null` 이다(계약). */
  readonly noticeKnown: boolean;
  readonly noticeTitle: string | null;
  readonly noticeStatus: string | null;
  readonly noticeDeadlineAt: string | null;
}

/**
 * 「건별 묶음」 — **연속 접기이고 정렬이 아니다.** 계약이 「정렬이 묶음을 연속시키므로
 * 클라이언트가 재정렬할 필요가 없다」고 둔 자리라, 화면이 다시 정렬하면 서버가 정한 최신순이
 * 무너진다.
 *
 * 서버가 연속시키지 못한 경우에도 **말없이 합치지 않는다**: 떨어진 같은 공고는 두 묶음으로
 * 남는다. 합치면 그 항목이 응답에서 있던 자리보다 위로 올라가고 그 이동을 아무도 보지 못한다
 * — 어긋남이 화면에 **보이는** 쪽이 낫다.
 *
 * 묶음의 키는 **번호와 차수 둘 다**이다(번호만으로 묶으면 다른 차수의 소식이 한 묶음에 든다).
 */
export function groupByNotice(items: readonly NotificationItem[]): readonly NoticeGroup[] {
  const groups: NoticeGroup[] = [];
  let current: NotificationItem[] | null = null;

  for (const item of items) {
    const last = groups.at(-1);
    const sameNotice =
      last !== undefined && last.noticeNumber === item.noticeNumber && last.noticeRound === item.noticeRound;

    if (sameNotice && current !== null) {
      current.push(item);
      continue;
    }
    current = [item];
    groups.push({
      noticeNumber: item.noticeNumber,
      noticeRound: item.noticeRound,
      items: current,
      // **계수는 서버 값이다**(D-7E3a-6) — 이 페이지의 행을 세면 묶음이 페이지 경계에 걸릴 때
      // 「더 있다」를 말할 수 없다.
      itemCount: item.noticeItemCount,
      unreadCount: item.noticeUnreadCount,
      // 받은 배열을 그대로 들고 있으므로 아래 getter 가 아니라 **읽는 시점**에 세야 한다 —
      // 그 자리를 `Object.defineProperty` 로 숨기지 않고 아래에서 다시 짓는다.
      hasMoreThanShown: false,
      noticeKnown: item.noticeKnown,
      noticeTitle: item.noticeTitle,
      noticeStatus: item.noticeStatus,
      noticeDeadlineAt: item.noticeDeadlineAt,
    });
  }

  // `hasMoreThanShown` 은 묶음이 다 모인 **뒤에만** 알 수 있다(접는 중에는 받은 수가 자란다).
  return groups.map((group) => ({ ...group, hasMoreThanShown: group.itemCount > group.items.length }));
}
