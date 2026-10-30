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
