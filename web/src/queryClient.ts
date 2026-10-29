import { QueryClient } from '@tanstack/react-query';

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
      retry: 1,
    },
  },
});
