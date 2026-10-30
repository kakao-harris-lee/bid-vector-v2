import { describe, expect, it } from 'vitest';

import { ApiError } from './api/apiError';
import { queryClient, retryRead } from './queryClient';

/**
 * **세션이 없다는 답은 재시도로 풀리지 않는다**(M7/7E-3a r2 CR L-4). 화면들이 401 을 로그인
 * 얼굴로 가르는 규율과 네트워크 층이 어긋나 있었다 — 얼굴만 고치면 요청은 한 번 더 간다.
 */
describe('읽기 재시도 술어', () => {
  it('401 은 다시 때리지 않는다', () => {
    expect(retryRead(0, new ApiError(401, 'UNAUTHENTICATED'))).toBe(false);
  });

  it('그 밖의 오류는 한 번 다시 부른다 — 일시 장애는 재시도로 풀린다', () => {
    expect(retryRead(0, new ApiError(500, 'INTERNAL_ERROR'))).toBe(true);
  });

  it('한 번 다시 부른 뒤에는 멈춘다 — 영원히 때리지 않는다', () => {
    expect(retryRead(1, new ApiError(500, 'INTERNAL_ERROR'))).toBe(false);
  });

  it('ApiError 가 아닌 오류도 한 번만', () => {
    expect(retryRead(0, new Error('network'))).toBe(true);
    expect(retryRead(1, new Error('network'))).toBe(false);
  });
});

/**
 * **그 술어가 실제로 꽂혔는가**(M7/7E-3a r3 v M-2). 앞 판의 test 는 함수만 쟀다 — 설정을
 * `retry: 1` 로 되돌려도 전건 594 가 초록이었다(화면 test 는 저마다 `retry: false` 인 자기
 * `QueryClient` 를 쓴다). 배선을 재는 자리가 따로 있어야 한다.
 */
describe('재시도 술어가 공용 클라이언트에 꽂혀 있다', () => {
  it('기본 옵션의 retry 가 그 술어다', () => {
    expect(
      queryClient.getDefaultOptions().queries?.retry,
      'queryClient 의 retry 가 retryRead 가 아니다 — 술어를 고쳐도 wire 에 닿지 않는다',
    ).toBe(retryRead);
  });

  it('창 포커스로 다시 부르지 않는 기본도 그대로다', () => {
    expect(queryClient.getDefaultOptions().queries?.refetchOnWindowFocus).toBe(false);
  });
});
