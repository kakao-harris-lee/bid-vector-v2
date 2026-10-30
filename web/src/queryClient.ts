import { QueryClient } from '@tanstack/react-query';

import { isUnauthorized } from './components/WriteFailure';

/**
 * 서버 상태를 드는 클라이언트 하나(ADR 0011 D-1). **셸이 이것을 제공한다** — 라우트 트리가
 * 그 provider 를 품으므로 라우트를 렌더하는 test 가 provider 를 따로 세우지 않아도 되고,
 * 「화면이 질의를 걸 수 있는 자리」와 「셸이 뜨는 자리」가 같아진다.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 운영자 화면이고 데이터가 분 단위로 바뀌지 않는다 — 창을 다시 보는 것만으로 다시
      // 부르지 않는다(§6.5 의 즉시 반응은 쓰기 뒤 무효화로 만든다).
      refetchOnWindowFocus: false,
      /**
       * **세션이 없다는 답은 다시 때리지 않는다**(M7/7E-3a r2 CR L-4). 그 자리는 재시도로
       * 풀리지 않는데 기본 재시도가 요청을 한 번 더 보냈다 — 화면들이 401 을 로그인 얼굴로
       * 가르는 규율과 네트워크 층이 어긋나 있었다.
       *
       * 전역 기본에 두는 이유: 같은 어긋남이 모든 읽기에 있고, 질의마다 적으면 새 질의가
       * 그것을 잊는다. test 가 `retry: false` 로 덮는 길은 그대로 남는다(기본을 덮는 쪽이다).
       */
      retry: (failureCount, error) => !isUnauthorized(error) && failureCount < 1,
    },
  },
});
