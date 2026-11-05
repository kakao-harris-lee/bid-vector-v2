import { describe, expect, it } from 'vitest';

import type { NotificationItem } from '../api/queries';

import {
  NOTIFICATION_BID_KIND_DISPLAY,
  NOTIFICATION_DETAIL_DISPLAY,
  NOTIFICATION_KIND_DISPLAY,
  groupNotifications,
  notificationItemTestId,
  type NotificationBidKind,
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
  'FAILED_OR_REBID',
];

/** 투찰 종류 축의 계약 어휘 셋(M9/paper-bid) — 축어다(표에서 뽑으면 공허하다). */
const CONTRACT_BID_KINDS: readonly NotificationBidKind[] = ['REAL', 'PAPER', 'NONE'];

function item(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    kind: 'OPENING_COMPLETED',
    detail: null,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    bidKind: 'REAL',
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

  it('부가 사실 표도 계약 전수 다섯이고 라벨이 서로 다르다', () => {
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

  /**
   * 「내가 졌다」(패찰)와 「아무도 못 땄다」(유찰)는 **다른 사실**이다(D-M9-1). 위 distinct
   * 등식도 이것을 재지만, 그 등식은 다섯 중 **어느 둘**이 접혔는지 말하지 않는다 — 이 축은
   * 접기가 거부된 바로 그 자리라 이름을 들어 따로 단언한다.
   */
  it('NOT_AWARDED 와 FAILED_OR_REBID 가 다른 문면이다', () => {
    expect(NOTIFICATION_DETAIL_DISPLAY.FAILED_OR_REBID.label).not.toBe(NOTIFICATION_DETAIL_DISPLAY.NOT_AWARDED.label);
  });

  /** 투찰 종류 표도 계약 전수와 같아야 한다 — 값을 더하고 문면을 빠뜨리면 「모름」으로 떨어진다. */
  it('투찰 종류 표의 키 집합이 계약 전수 셋과 같고 라벨이 서로 다르다', () => {
    expect(Object.keys(NOTIFICATION_BID_KIND_DISPLAY).sort()).toStrictEqual([...CONTRACT_BID_KINDS].sort());

    const labels = CONTRACT_BID_KINDS.map((one) => NOTIFICATION_BID_KIND_DISPLAY[one].label);
    expect(new Set(labels).size, `접힌 라벨: ${labels.join(' · ')}`).toBe(CONTRACT_BID_KINDS.length);
  });

  /**
   * **서면 연습과 실제 투찰이 한 얼굴이면 이 slice 의 뜻이 사라진다**(M9/paper-bid). 위 distinct
   * 등식도 이것을 재지만 셋 중 **어느 둘**이 접혔는지 말하지 않는다 — 접기가 거부된 자리라
   * 이름을 들어 따로 단언한다.
   */
  it('서면 연습과 실투찰이 다른 문면이다', () => {
    expect(NOTIFICATION_BID_KIND_DISPLAY.PAPER.label).not.toBe(NOTIFICATION_BID_KIND_DISPLAY.REAL.label);
  });
});

describe('항목 행의 testId — 이 꼴을 적는 자리는 하나다 (v H-1)', () => {
  /**
   * **신원 넷이 전부 들어간다.** 화면과 여정 e2e 가 이 함수를 같이 부르므로, 축이 빠지면 둘이
   * **함께** 붉다 — 앞 판은 양쪽이 각자 템플릿을 적어 화면만 고치고 여정이 조용히 아무 행도
   * 못 찾았다(`getByTestId(string)` 은 정확 일치다).
   */
  it('항목 testId 가 종류·공고 쌍·투찰 종류 넷을 담는다', () => {
    expect(
      notificationItemTestId({
        kind: 'AWARD_DECIDED',
        noticeNumber: 'SYN-1',
        noticeRound: '007',
        bidKind: 'PAPER',
      }),
    ).toBe('notification-AWARD_DECIDED-SYN-1-007-PAPER');
  });

  /** 투찰 종류만 다른 두 행은 **다른** testId 다 — 같으면 strict locator 가 모호해진다. */
  it('투찰 종류만 달라도 testId 가 갈린다', () => {
    const real = notificationItemTestId(item({ bidKind: 'REAL' }));
    const paper = notificationItemTestId(item({ bidKind: 'PAPER' }));

    expect(real).not.toBe(paper);
  });
});

describe('건별 묶음 — 응답 순서를 보존한다', () => {
  /**
   * 계약: 「정렬이 묶음을 **연속**시키므로 클라이언트가 재정렬할 필요가 없다」. 그래서 묶음은
   * **연속 접기**이고 정렬이 아니다 — 화면이 다시 정렬하면 서버가 정한 최신순이 무너진다.
   */
  it('연속한 같은 공고가 한 묶음이고 묶음 순서는 응답 순서다', () => {
    const groups = groupNotifications([
      item({ noticeNumber: 'A', kind: 'OPENING_COMPLETED' }),
      item({ noticeNumber: 'A', kind: 'AWARD_DECIDED' }),
      item({ noticeNumber: 'B', kind: 'NEW_CANDIDATE' }),
    ]);

    expect(groups.map((one) => one.noticeNumber)).toStrictEqual(['A', 'B']);
    expect(groups[0]?.items.map((one) => one.kind)).toStrictEqual(['OPENING_COMPLETED', 'AWARD_DECIDED']);
  });

  /**
   * **투찰 종류가 묶음 키의 셋째 열이다**(M9/paper-bid). 빠지면 같은 공고의 서면 소식과 실투찰
   * 소식이 한 블록에 섞이고, 그 블록의 계수(서버가 축별로 센 값)가 자기 항목 수와 맞지 않는다.
   *
   * 음성 대조가 같은 등식 안에 있다 — 종류가 **같으면** 여전히 한 묶음이다(모두 쪼개는 술어도
   * 위 단언만으로는 초록이다).
   */
  it('번호와 차수가 같아도 투찰 종류가 다르면 다른 묶음이다', () => {
    const split = groupNotifications([
      item({ noticeNumber: 'A', bidKind: 'REAL' }),
      item({ noticeNumber: 'A', bidKind: 'PAPER' }),
    ]);
    const together = groupNotifications([
      item({ noticeNumber: 'A', bidKind: 'PAPER', kind: 'OPENING_COMPLETED' }),
      item({ noticeNumber: 'A', bidKind: 'PAPER', kind: 'AWARD_DECIDED' }),
    ]);

    expect(split.map((one) => one.bidKind)).toStrictEqual(['REAL', 'PAPER']);
    expect(together).toHaveLength(1);
    expect(together[0]?.bidKind).toBe('PAPER');
  });

  /** 차수가 다르면 **다른 공고**다 — 번호만으로 묶으면 다른 건의 소식이 한 묶음에 든다. */
  it('번호가 같아도 차수가 다르면 다른 묶음이다', () => {
    const groups = groupNotifications([
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
    const groups = groupNotifications([
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
    const groups = groupNotifications([item({ noticeItemCount: 3, noticeUnreadCount: 2 })]);

    expect(groups[0]?.itemCount).toBe(3);
    expect(groups[0]?.unreadCount).toBe(2);
    expect(groups[0]?.items).toHaveLength(1);
    expect(groups[0]?.hasMoreThanShown, '전체 3 중 1 만 왔으면 「더 있다」다').toBe(true);
  });

  it('페이지에 묶음이 다 왔으면 「더 있다」가 아니다', () => {
    const groups = groupNotifications([
      item({ noticeItemCount: 2, kind: 'OPENING_COMPLETED' }),
      item({ noticeItemCount: 2, kind: 'AWARD_DECIDED' }),
    ]);

    expect(groups[0]?.hasMoreThanShown).toBe(false);
  });

  it('빈 목록은 빈 묶음 목록이다', () => {
    expect(groupNotifications([])).toStrictEqual([]);
  });

  /**
   * `noticeKnown:false`(공고 행이 없다)와 `noticeTitle:null`(행은 있고 제목 칸이 비었다)은
   * **다른 사실**이다(계약). 묶음이 그 둘을 같은 값으로 접으면 화면이 가를 근거를 잃는다.
   */
  it('공고를 모르는 묶음과 제목만 빈 묶음이 다른 값으로 남는다', () => {
    const [unknown] = groupNotifications([
      item({ noticeKnown: false, noticeTitle: null, noticeStatus: null, noticeDeadlineAt: null }),
    ]);
    const [titleless] = groupNotifications([item({ noticeKnown: true, noticeTitle: null })]);

    expect(unknown?.noticeKnown).toBe(false);
    expect(titleless?.noticeKnown).toBe(true);
    expect(unknown?.noticeTitle).toBeNull();
    expect(titleless?.noticeTitle).toBeNull();
  });
});
