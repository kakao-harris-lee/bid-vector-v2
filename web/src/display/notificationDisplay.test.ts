import { describe, expect, it } from 'vitest';

import type { NotificationItem } from '../api/queries';

import {
  NOTIFICATION_DETAIL_DISPLAY,
  NOTIFICATION_KIND_DISPLAY,
  groupByNotice,
  type NotificationDetail,
  type NotificationKind,
} from './notificationDisplay';

/** 계약 enum 축어 — 표에서 뽑아 자기와 대조하면 공허하다(`openingResultDisplay.test.ts` 와 같은 관례). */
const CONTRACT_KINDS: readonly NotificationKind[] = [
  'NEW_CANDIDATE',
  'BID_DEADLINE_IMMINENT',
  'BID_DEADLINE_TODAY',
  'DOCUMENT_DEADLINE_IMMINENT',
  'OPENING_COMPLETED',
  'AWARD_DECIDED',
  'NOTICE_REVISED',
  'BID_PRICE_REQUEST_RECORDED',
];

const CONTRACT_DETAILS: readonly NotificationDetail[] = [
  'PROVISIONAL_FIRST',
  'NOT_PROVISIONAL_FIRST',
  'AWARDED',
  'NOT_AWARDED',
];

function item(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    kind: 'OPENING_COMPLETED',
    detail: null,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    occurredAt: '2026-11-02T01:00:00Z',
    read: false,
    readAt: null,
    noticeItemCount: 1,
    noticeUnreadCount: 1,
    noticeKnown: true,
    noticeTitle: '합성 공고',
    noticeStatus: 'Open',
    noticeDeadlineAt: '2026-11-05T09:00:00Z',
    ...overrides,
  };
}

describe('알림 종류·부가 사실의 표', () => {
  it('종류 표의 키 집합이 계약 전수 여덟과 같다', () => {
    expect(Object.keys(NOTIFICATION_KIND_DISPLAY).sort()).toStrictEqual([...CONTRACT_KINDS].sort());
  });

  it('여덟의 라벨이 서로 다르다 — 종류가 한 얼굴로 접히지 않는다', () => {
    const labels = CONTRACT_KINDS.map((kind) => NOTIFICATION_KIND_DISPLAY[kind].label);

    expect(new Set(labels).size, `접힌 라벨: ${labels.join(' · ')}`).toBe(CONTRACT_KINDS.length);
  });

  it('부가 사실 표도 계약 전수 넷이고 라벨이 서로 다르다', () => {
    expect(Object.keys(NOTIFICATION_DETAIL_DISPLAY).sort()).toStrictEqual([...CONTRACT_DETAILS].sort());

    const labels = CONTRACT_DETAILS.map((one) => NOTIFICATION_DETAIL_DISPLAY[one].label);
    expect(new Set(labels).size).toBe(CONTRACT_DETAILS.length);
  });

  /**
   * 「1위가 아니다」와 「낙찰이 아니다」는 **다른 사실**이다(1위 ≠ 낙찰 — UX §2.4). 한 문면으로
   * 적으면 개찰 단계의 소식과 확정 단계의 소식이 같아 보인다.
   */
  it('NOT_PROVISIONAL_FIRST 와 NOT_AWARDED 가 다른 문면이다', () => {
    expect(NOTIFICATION_DETAIL_DISPLAY.NOT_PROVISIONAL_FIRST.label).not.toBe(
      NOTIFICATION_DETAIL_DISPLAY.NOT_AWARDED.label,
    );
  });
});

describe('건별 묶음 — 응답 순서를 보존한다', () => {
  /**
   * 계약: 「정렬이 묶음을 **연속**시키므로 클라이언트가 재정렬할 필요가 없다」. 그래서 묶음은
   * **연속 접기**이고 정렬이 아니다 — 화면이 다시 정렬하면 서버가 정한 최신순이 무너진다.
   */
  it('연속한 같은 공고가 한 묶음이고 묶음 순서는 응답 순서다', () => {
    const groups = groupByNotice([
      item({ noticeNumber: 'A', kind: 'OPENING_COMPLETED' }),
      item({ noticeNumber: 'A', kind: 'AWARD_DECIDED' }),
      item({ noticeNumber: 'B', kind: 'NEW_CANDIDATE' }),
    ]);

    expect(groups.map((one) => one.noticeNumber)).toStrictEqual(['A', 'B']);
    expect(groups[0]?.items.map((one) => one.kind)).toStrictEqual(['OPENING_COMPLETED', 'AWARD_DECIDED']);
  });

  /** 차수가 다르면 **다른 공고**다 — 번호만으로 묶으면 다른 건의 소식이 한 묶음에 든다. */
  it('번호가 같아도 차수가 다르면 다른 묶음이다', () => {
    const groups = groupByNotice([
      item({ noticeNumber: 'A', noticeRound: '000' }),
      item({ noticeNumber: 'A', noticeRound: '001' }),
    ]);

    expect(groups).toHaveLength(2);
  });

  /**
   * 서버가 연속시키지 못한 경우에도 **말없이 합치지 않는다** — 합치면 그 항목이 응답에서
   * 있던 자리보다 위로 올라가고, 그 이동을 아무도 보지 못한다.
   */
  it('떨어져 있는 같은 공고는 두 묶음으로 남는다 — 끌어올려 합치지 않는다', () => {
    const groups = groupByNotice([
      item({ noticeNumber: 'A' }),
      item({ noticeNumber: 'B' }),
      item({ noticeNumber: 'A' }),
    ]);

    expect(groups.map((one) => one.noticeNumber)).toStrictEqual(['A', 'B', 'A']);
  });

  /**
   * 묶음의 **크기는 서버 값**이다(`noticeItemCount` — 페이지와 무관한 전체 크기). 이 페이지에
   * 담긴 수로 세면 묶음이 페이지 경계에 걸릴 때 「더 있다」를 말할 수 없다.
   */
  it('묶음 전체 크기·안읽음 수는 서버 값이고 페이지의 행 수를 세지 않는다', () => {
    const groups = groupByNotice([item({ noticeItemCount: 3, noticeUnreadCount: 2 })]);

    expect(groups[0]?.itemCount).toBe(3);
    expect(groups[0]?.unreadCount).toBe(2);
    expect(groups[0]?.items).toHaveLength(1);
    expect(groups[0]?.hasMoreThanShown, '전체 3 중 1 만 왔으면 「더 있다」다').toBe(true);
  });

  it('페이지에 묶음이 다 왔으면 「더 있다」가 아니다', () => {
    const groups = groupByNotice([
      item({ noticeItemCount: 2, kind: 'OPENING_COMPLETED' }),
      item({ noticeItemCount: 2, kind: 'AWARD_DECIDED' }),
    ]);

    expect(groups[0]?.hasMoreThanShown).toBe(false);
  });

  it('빈 목록은 빈 묶음 목록이다', () => {
    expect(groupByNotice([])).toStrictEqual([]);
  });

  /**
   * `noticeKnown:false`(공고 행이 없다)와 `noticeTitle:null`(행은 있고 제목 칸이 비었다)은
   * **다른 사실**이다(계약). 묶음이 그 둘을 같은 값으로 접으면 화면이 가를 근거를 잃는다.
   */
  it('공고를 모르는 묶음과 제목만 빈 묶음이 다른 값으로 남는다', () => {
    const [unknown] = groupByNotice([
      item({ noticeKnown: false, noticeTitle: null, noticeStatus: null, noticeDeadlineAt: null }),
    ]);
    const [titleless] = groupByNotice([item({ noticeKnown: true, noticeTitle: null })]);

    expect(unknown?.noticeKnown).toBe(false);
    expect(titleless?.noticeKnown).toBe(true);
    expect(unknown?.noticeTitle).toBeNull();
    expect(titleless?.noticeTitle).toBeNull();
  });
});
