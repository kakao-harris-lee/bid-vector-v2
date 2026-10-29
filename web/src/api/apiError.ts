/**
 * 서버 오류의 정제된 표현 — **서버 메시지를 담지 않는다**(D-7E-6). 코드와 상태만 나른다:
 * 메시지에는 행 값이 실릴 수 있고 그것이 콘솔·trace 로 나가는 경로를 만들지 않는다.
 *
 * **왜 `queries.ts` 와 다른 모듈인가**(CR H-2 수정 중 실측): 화면 test 는 `../api/queries` 를
 * 모듈째 바꿔치우므로, 그 모듈에서 내보낸 클래스는 test 쪽과 화면 쪽이 **다른 정체**가 된다 —
 * `instanceof` 가 거짓이 되어 401 갈래가 조용히 서지 않았다(실측: 401 test 가 일반 실패 문면을
 * 받았다). 오류 타입을 질의 모듈에서 떼어 두면 두 쪽이 같은 클래스를 본다.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
  ) {
    super(`api status=${status} code=${code ?? 'unknown'}`);
    this.name = 'ApiError';
  }
}
