import type { paths } from './generated/operator-api';

/**
 * 화면 B·C 가 쓰도록 승인된 **(메서드, 경로) 쌍**(scope.md 「화면 B·C 가 쓰는 operation」).
 *
 * **이 목록이 한 자리다.** 셋이 이것을 쓴다: ⓐ `client.ts` 가 생성 타입을 이 쌍들로 **좁혀**
 * 목록 밖 호출이 `tsc` 에서 멈춘다 ⓑ 게이트 test 가 `queries.ts` 의 호출 집합과 등식을 둔다
 * ⓒ 여정 e2e 가 이 목록에서 런타임 대조 집합을 **파생**한다(복제하지 않는다).
 *
 * **왜 경로가 아니라 쌍인가**(v r3 M-1): 경로만 좁히면 **같은 경로의 미승인 메서드**가 열린다 —
 * 실측에서 `apiClient.DELETE('/api/session')`(로그아웃)이 다섯 층을 모두 지났다. 읽기만 승인한
 * 경로에 쓰기를 보내는 것이 그 꼴이다.
 *
 * 이 slice 가 쓰지 않는 operation 은 **두지 않는다** — 쓰지 않는 능력을 목록이 품으면
 * 「목록 == 화면의 능력」 등식이 공허해진다. 7E-2·3 이 자기 몫을 더할 때 줄을 더한다.
 */
export const APPROVED_OPERATIONS = [
  { method: 'GET', path: '/api/session' },
  { method: 'POST', path: '/api/session' },
  { method: 'GET', path: '/api/notices' },
  { method: 'GET', path: '/api/suggestions' },
  { method: 'GET', path: '/api/bid-cases' },
  { method: 'POST', path: '/api/bid-cases' },
  { method: 'POST', path: '/api/bid-price-requests' },
  { method: 'GET', path: '/api/bid-cases/{caseId}/timeline' },
  { method: 'POST', path: '/api/bid-cases/{caseId}/draft' },
  { method: 'POST', path: '/api/bid-cases/{caseId}/record' },
  { method: 'POST', path: '/api/bid-cases/{caseId}/resume' },
] as const;

export type ApprovedOperation = (typeof APPROVED_OPERATIONS)[number];
export type ApprovedOperationPath = ApprovedOperation['path'];

/** 그 경로에 승인된 메서드만 — openapi-fetch 의 경로 항목은 메서드 키가 소문자다. */
/**
 * 그 경로에 승인된 메서드 — **계약에 있는 메서드로 제약한다**(CR r4 L-6). 앞 판은 교집합
 * (`& keyof paths[P]`)으로 걸렀고, 그러면 계약에 없는 메서드가 **말없이 떨어져** 목록의 오타가
 * 타입 층에서 조용했다(실측: `{ method: 'PUT', path: '/api/notices' }` → tsc 0). 경로 축은
 * `Pick` 의 키라서 이미 컴파일로 서므로(오타는 `TS2536`), 메서드 축도 같은 자리로 돌린다.
 */
type ApprovedMethodsOf<P extends ApprovedOperationPath> = Lowercase<
  Extract<ApprovedOperation, { path: P }>['method']
> &
  keyof paths[P];

/**
 * 목록의 메서드 가운데 **그 경로의 계약에 없는 것**. 하나라도 있으면 아래 단언이 컴파일에서
 * 멈춘다(CR r4 L-6).
 *
 * **키 존재로는 볼 수 없다**(실측): 생성기는 없는 메서드도 키로 적고 타입을 `never` 로 둔다
 * (`/api/notices` 아래에 `put: never` 가 있다). 그래서 교집합(`& keyof paths[P]`)은 오타를
 * 통과시켰고 `{ method: 'PUT', path: '/api/notices' }` 가 **tsc 0** 이었다. 키가 아니라 **그
 * 자리의 타입이 `never` 인가**를 본다.
 */
type UndeclaredMethodsFor<P extends ApprovedOperationPath> = {
  [M in ApprovedMethodsOf<P>]: [paths[P][M]] extends [never] ? M : never;
}[ApprovedMethodsOf<P>];

type MethodsNotInContract = {
  [P in ApprovedOperationPath]: UndeclaredMethodsFor<P>;
}[ApprovedOperationPath];

/** 그 집합이 **비어 있다**는 컴파일 단언 — 비어 있지 않으면 `TS2344` 로 멈춘다. */
type AssertEmpty<T extends never> = T;
export type ApprovedMethodAxisCheck = AssertEmpty<MethodsNotInContract>;

/**
 * 승인된 쌍만 담은 생성 타입. 경로를 고르고(`Pick`) 그 안에서 **메서드까지** 고른다 — 미승인
 * 메서드는 타입에 **키가 없으므로** 그 호출이 컴파일되지 않는다.
 */
export type ApprovedPaths = {
  [P in ApprovedOperationPath]: Pick<paths[P], ApprovedMethodsOf<P>>;
};
