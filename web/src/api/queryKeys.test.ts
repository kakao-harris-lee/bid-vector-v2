import { describe, expect, it } from 'vitest';

import {
  bidCasesQueryKey,
  MY_BID_RESULTS_QUERY_KEY,
  NOTIFICATIONS_QUERY_KEY,
  notificationsUnreadQueryKey,
  openingResultQueryKey,
} from './queryKeys';

/**
 * 질의 키의 **관계**를 잠근다(M13/13B, cr H-2).
 *
 * 착수 판정 ⓔ 가 요구한 자리다: 셸의 안 읽음 배지는 알림함 목록과 **다른 질의**이지만 키는
 * 목록 키의 **접두 아래**에 둔다. react-query 의 무효화가 접두로 번지므로, 읽음 쓰기가 이미
 * 거는 `invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY })` 가 배지까지 닿는다.
 *
 * **타입이 이 축을 막지 않는다**: 반환 타입은 `readonly [string, string]` 이라
 * `['notifications-unread', 'x']` 같은 꼴도 그대로 통과한다. 접두가 깨지면 13E 계약의 「쓰기가
 * 실패하면 배지가 줄지 않는 것으로 보인다」가 **조용히** 거짓이 된다 — 실패와 성공이 같은
 * 모양이 되는, 이 slice 가 막으려던 꼴이다. 그 침묵을 여기서 막는다.
 */
describe('안 읽음 배지 키와 알림함 키의 관계 (cr H-2)', () => {
  it('배지 키가 알림함 키의 접두 아래다', () => {
    const badge = notificationsUnreadQueryKey();

    expect(badge.slice(0, NOTIFICATIONS_QUERY_KEY.length)).toStrictEqual([...NOTIFICATIONS_QUERY_KEY]);
  });

  /** 접두만 보면 **같은 키**도 통과한다 — 그러면 배지가 목록 질의를 재사용하게 된다. */
  it('두 키가 서로 다르다', () => {
    expect([...notificationsUnreadQueryKey()]).not.toStrictEqual([...NOTIFICATIONS_QUERY_KEY]);
    expect(notificationsUnreadQueryKey().length).toBeGreaterThan(NOTIFICATIONS_QUERY_KEY.length);
  });

  /** 접두가 **우연히** 서지 않게 — 목록 키가 비면 모든 키가 그 접두 아래가 된다. */
  it('알림함 키가 비어 있지 않다 — 빈 접두는 모든 키를 삼킨다', () => {
    expect(NOTIFICATIONS_QUERY_KEY.length).toBeGreaterThan(0);
  });

  /**
   * **다른 축의 키는 그 접두 아래에 들지 않는다.** 들어가면 읽음 쓰기 하나가 관계없는 화면의
   * 질의까지 무효화하고, 그 비용이 어디서 오는지 아무도 모른다.
   */
  it('다른 축의 키는 알림함 접두 밖이다', () => {
    const others = [bidCasesQueryKey('REAL'), MY_BID_RESULTS_QUERY_KEY, openingResultQueryKey('SYN-1', '000')];

    for (const key of others) {
      expect(key.slice(0, NOTIFICATIONS_QUERY_KEY.length)).not.toStrictEqual([...NOTIFICATIONS_QUERY_KEY]);
    }
  });
});
