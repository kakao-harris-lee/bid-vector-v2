import type { components } from './generated/operator-api';

type BidKind = components['schemas']['BidCase']['bidKind'];

/**
 * 서버 상태 질의 키의 **한 자리**(CR r1 L-7).
 *
 * 앞 판은 `['bid-cases', 'REAL']` 을 화면 셋(B·C·D)에 **리터럴로 복제**했다. 한쪽을 고치면
 * 캐시가 조용히 갈리고 **무효화가 다른 화면에 닿지 않는다** — 담기·전이가 서로의 목록을 갱신하지
 * 못하는 꼴이다. 전략 키는 이미 내보내 공유했으므로 이 축만 남아 있던 복제였다.
 */
export function bidCasesQueryKey(kind: BidKind): readonly [string, BidKind] {
  return ['bid-cases', kind];
}

/**
 * 공고 하나의 개찰 결과(M7/7E-3a). 키가 **번호와 차수 둘 다**인 이유는 공고의 신원이 그
 * 쌍이기 때문이다 — 번호만 키로 두면 차수가 다른 공고의 답이 서로를 덮는다.
 */
export function openingResultQueryKey(
  noticeNumber: string,
  noticeRound: string,
): readonly [string, string, string] {
  return ['opening-result', noticeNumber, noticeRound];
}

/** 알림함 한 페이지(M7/7E-3a) — 읽음 처리가 무효화하는 자리다. */
export const NOTIFICATIONS_QUERY_KEY: readonly [string] = ['notifications'];

/**
 * 셸 탐색의 **안 읽음 배지**(M13/13B) — 알림함 목록과 **다른 질의**다.
 *
 * 왜 나누는가: 배지가 읽는 값은 `X-Unread-Count`(계정의 안읽음 **전수**)이고 목록의 필터·쪽
 * 꼴과 무관하다. 목록 질의를 그대로 쓰면 알림함이 쪽 꼴을 바꾸는 날 배지가 함께 흔들린다 —
 * 배지는 셸에 있어 **어느 화면에서나** 서는 값이다.
 *
 * **그런데 키는 목록 키의 접두 아래에 둔다.** react-query 의 무효화가 접두로 번지므로, 읽음
 * 쓰기가 이미 거는 `invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY })` 가 배지까지
 * 닿는다 — 「성공하면 배지가 줄고 실패하면 그대로」가 배선이 아니라 **구조**로 선다. 그 접두
 * 관계는 짝 test 가 잠근다.
 */
export function notificationsUnreadQueryKey(): readonly [string, string] {
  return [...NOTIFICATIONS_QUERY_KEY, 'unread'];
}

/**
 * 내 입찰 결과(M7/7E-3b) — 건별과 집계가 **한 응답**이라 키도 하나다.
 *
 * **인자가 없다**: 계약에 질의 인자가 없고 주체는 세션 계정이 정한다. 계정을 키에 섞으면
 * 화면이 주체를 아는 척하게 되고, 그 값이 틀려도 캐시는 조용히 갈린다.
 */
export const MY_BID_RESULTS_QUERY_KEY: readonly [string] = ['my-bid-results'];
