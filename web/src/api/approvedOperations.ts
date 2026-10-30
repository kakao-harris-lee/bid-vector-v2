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

  // 화면 D 내 입찰함(M7/7E-2, D-7E2-8) — 「제출함」 뒤의 생애주기 넷. 계약 전이표
  // (`BID_TRANSITIONS`)가 허락하는 쌍만이고 D 의 다음 행동 표가 그 넷을 고른다.
  // **담기 뒤 전이(`/defer`·`/skip`)와 결과 정정(`/outcome-correction`)은 두지 않는다** —
  // 요구표의 D 행에 없고, 쓰지 않는 능력을 목록이 품으면 「목록 == 화면의 능력」이 공허해진다.
  { method: 'POST', path: '/api/bid-cases/{caseId}/await-opening' },
  { method: 'POST', path: '/api/bid-cases/{caseId}/opening' },
  { method: 'POST', path: '/api/bid-cases/{caseId}/qualification-review' },
  { method: 'POST', path: '/api/bid-cases/{caseId}/outcome' },

  // 설정 — 전략(D-7E2-2). 쓰기는 **편집 세션을 지나는 길 하나**다(계약에 전략을 직접 쓰는
  // operation 이 없다). 세션 조회(GET)를 쓰는 이유: 만료를 `expiresAt < now` 로 화면이
  // 판정하면 상태를 클라이언트가 추론하는 것이다 — 계약이 「접근 시점에 만료를 먼저 접는다」로
  // 두었으므로 그 operation 이 준 `state` 를 보인다.
  { method: 'GET', path: '/api/strategy' },
  { method: 'POST', path: '/api/strategy/edit-sessions' },
  { method: 'GET', path: '/api/strategy/edit-sessions/{sessionId}' },
  { method: 'POST', path: '/api/strategy/edit-sessions/{sessionId}/value' },
  { method: 'POST', path: '/api/strategy/edit-sessions/{sessionId}/confirm' },
  { method: 'POST', path: '/api/strategy/edit-sessions/{sessionId}/edit' },
  { method: 'POST', path: '/api/strategy/edit-sessions/{sessionId}/cancel' },

  // 설정 — 프로필(D-7E2-3). 자격 사실·사업 정보는 **절 단위 교체**이고 시평액은 행 단위다.
  // 사업자번호는 계약에 칸이 없어 입력칸을 만들지 않는다(D-7E2-7) — 그래서 그 축의
  // operation 도 없다.
  { method: 'GET', path: '/api/profile' },
  { method: 'POST', path: '/api/profile/qualification-facts' },
  { method: 'POST', path: '/api/profile/business-details' },
  { method: 'GET', path: '/api/profile/construction-capacities' },
  { method: 'POST', path: '/api/profile/construction-capacities' },
  { method: 'POST', path: '/api/profile/construction-capacities/remove' },

  // 화면 A 시작하기(M7/7E-2b) — **이 slice 가 목록에 더하는 것은 이것 하나뿐이다**(착수 실측).
  // 마법사의 쓰기는 위의 설정 operation 을 그대로 쓴다(같은 컴포넌트가 저장한다).
  { method: 'GET', path: '/api/onboarding-checklist' },

  // 화면 E 결과 확인 · 알림함(M7/7E-3a, D-7E3a-8) — **증분은 정확히 넷이다**(착수 실측).
  // 결과 단계 전이 넷(`await-opening`·`opening`·`qualification-review`·`outcome`)은 이미 위에
  // 있지만 **E 는 그것을 부르지 않는다**: E 는 읽기 화면이고 전이는 D 하나가 든다(전이표의
  // 사본이 둘이 되면 갈리고, 여정 ④⑤ 가 같은 건을 밀게 된다).
  { method: 'GET', path: '/api/opening-results' },
  { method: 'GET', path: '/api/notifications' },
  { method: 'POST', path: '/api/notifications/read' },
  { method: 'POST', path: '/api/notifications/read-notice' },
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
 * **키 존재로는 볼 수 없다**(실측): 생성기는 없는 메서드도 키로 적는다. 그래서 교집합
 * (`& keyof paths[P]`)은 오타를 통과시켰고 `{ method: 'PUT', path: '/api/notices' }` 가
 * **tsc 0** 이었다.
 *
 * 그 자리를 보는 방법도 한 번 틀렸다: 생성기가 적는 것은 `put?: never` 이고 **optional** 이라
 * 그 속성의 타입은 `never` 가 아니라 `undefined` 다(probe 로 확인). 그래서 `undefined` 를
 * 걷어 낸 뒤 비었는지 본다 — 그것이 「계약에 그 메서드가 없다」의 모양이다.
 */
type UndeclaredMethodsFor<P extends ApprovedOperationPath> = {
  [M in ApprovedMethodsOf<P>]: [Exclude<paths[P][M], undefined>] extends [never] ? M : never;
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
