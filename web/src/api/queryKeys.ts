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
 * 내 입찰 결과(M7/7E-3b) — 건별과 집계가 **한 응답**이라 키도 하나다.
 *
 * **인자가 없다**: 계약에 질의 인자가 없고 주체는 세션 계정이 정한다. 계정을 키에 섞으면
 * 화면이 주체를 아는 척하게 되고, 그 값이 틀려도 캐시는 조용히 갈린다.
 */
export const MY_BID_RESULTS_QUERY_KEY: readonly [string] = ['my-bid-results'];
