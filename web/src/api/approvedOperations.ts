/**
 * 화면 B·C 가 쓰도록 **승인된 operation 의 경로**(scope.md 「화면 B·C 가 쓰는 operation」).
 *
 * **이 목록이 한 자리다**(CR r2 M-1·M-2·M-7). 세 곳이 이것을 쓴다: ⓐ `client.ts` 가 생성 타입을
 * 이 경로들로 **좁혀** 목록 밖 호출이 `tsc` 에서 멈춘다 ⓑ 게이트 test 가 `queries.ts` 의 호출
 * 집합과 등식을 둔다 ⓒ 여정 e2e 가 런타임 경로를 대조한다.
 *
 * ⓐ 가 핵심이다 — 앞 판은 「클라이언트를 import 하는 자리」만 셌고, 재수출 한 줄·리터럴 동적
 * import·별칭·계산 접근 어느 꼴로든 그 객체를 들고 나가면 목록 밖 경로를 부를 수 있었다(실측
 * 다섯 꼴). 타입을 좁히면 **어느 꼴로 들고 나가도** 그 경로가 컴파일되지 않는다.
 *
 * 이 slice 가 쓰지 않는 operation 은 목록에 **두지 않는다**(M-7) — 쓰지 않는 능력을 목록이
 * 품으면 「목록 == 화면의 능력」이라는 등식이 공허해진다. 계약이 승인한 전체 집합은 scope.md 이고
 * 7E-2·3 이 자기 몫을 더할 때 이 목록에 줄을 더한다(건 단건 조회·건너뜀·나중에가 그 몫이다).
 */
export const APPROVED_OPERATION_PATHS = [
  '/api/session',
  '/api/notices',
  '/api/suggestions',
  '/api/bid-cases',
  '/api/bid-cases/{caseId}/timeline',
  '/api/bid-cases/{caseId}/draft',
  '/api/bid-cases/{caseId}/record',
  '/api/bid-cases/{caseId}/resume',
  '/api/bid-price-requests',
] as const;

export type ApprovedOperationPath = (typeof APPROVED_OPERATION_PATHS)[number];
