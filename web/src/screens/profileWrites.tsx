import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query';

import { WriteFailureFace } from '../components/WriteFailure';

/**
 * 프로필 절의 질의 키와 쓰기 배선(M7/7E-2b, D-7E2b-8) — **화면 둘이 같은 자리를 쓴다**: 설정
 * 화면과 시작하기 마법사가 같은 필드를 저장하므로, 배선이 두 벌이면 한쪽만 고쳐져 갈린다.
 */
export const PROFILE_QUERY_KEY = ['profile'] as const;
export const CAPACITY_QUERY_KEY = ['construction-capacities'] as const;

/**
 * 성공·실패 **양쪽에서** 서버 상태를 다시 읽는 쓰기 — 화면이 보이는 것이 「내가 눌렀다고 믿는
 * 것」이 아니라 서버의 사실이게 한다(실패 얼굴의 문면이 그 사실을 적는다).
 *
 * [onApplied] 는 성공한 쓰기 뒤의 자리다(폼 초안을 버리고, 마법사라면 체크리스트를 다시 읽는다)
 * — 재시도가 성공해도 같은 자리를 지난다.
 */
export function useProfileWrite<Body, Result>(
  write: (body: Body) => Promise<Result>,
  queryKey: readonly unknown[],
  onApplied?: () => void,
): UseMutationResult<Result, Error, Body> {
  const queryClient = useQueryClient();
  return useMutation({
    // **본문 하나만** 넘긴다 — react-query 가 `mutationFn` 을 `(variables, context)` 로 부르므로
    // 호출을 그대로 넘기면 계약 함수에 둘째 인자(`client`·`meta`)가 실린다(실측).
    mutationFn: (body: Body) => write(body),
    onSuccess: async () => {
      onApplied?.();
      await queryClient.invalidateQueries({ queryKey });
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey });
    },
  });
}

/** 한 번 눌린 쓰기의 실패 축 — 재시도는 **그 쓰기가 보냈던 본문**을 다시 보낸다(멱등하지 않은 호출을 만들지 않는다). */
export interface WriteAttempt {
  readonly isError: boolean;
  readonly error: unknown;
  readonly submittedAt: number;
  readonly retry: () => void;
}

export function attemptOf<Body, Result>(mutation: UseMutationResult<Result, Error, Body>): WriteAttempt {
  return {
    isError: mutation.isError,
    error: mutation.error,
    submittedAt: mutation.submittedAt,
    retry: () => {
      if (mutation.variables !== undefined) {
        mutation.mutate(mutation.variables);
      }
    },
  };
}

/**
 * **운영자가 마지막으로 누른** 실패한 쓰기 하나(CR r1 L-3). 앞 판은 고정 우선순위의 첫 실패를
 * 다시 보냈고, 둘이 함께 실패해 있으면 **누르지 않은 쪽**이 갔다. `submittedAt` 은 react-query 가
 * 그 mutation 에 찍은 마지막 시각이라 가장 최근이 운영자가 누른 것이다.
 */
export function ProfileWriteFailure({ attempts }: { readonly attempts: readonly WriteAttempt[] }) {
  const failed = [...attempts].filter((one) => one.isError).sort((a, b) => b.submittedAt - a.submittedAt)[0];
  if (failed === undefined) {
    return null;
  }
  return <WriteFailureFace what="프로필을 저장하지" error={failed.error} onRetry={failed.retry} />;
}
