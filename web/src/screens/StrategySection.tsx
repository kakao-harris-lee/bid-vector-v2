import { useQuery } from '@tanstack/react-query';

import { readStrategy, type StrategyReadResponse } from '../api/queries';
import { ErrorFace, Skeleton } from '../components/ScreenFaces';
import { isUnauthorized } from '../components/WriteFailure';
import { STRATEGY_FIELD_ORDER } from '../display/editSessionDisplay';

import {
  STRATEGY_QUERY_KEY,
  StrategyEditPanel,
  StrategyFieldRow,
  StrategyWriteFailure,
  useStrategyEditSession,
} from './strategyEdit';

/**
 * 전략 설정 — 칸 열셋의 지금 값과 「고치기」. 쓰기의 상태 기계·값 패널·칸 행은 `strategyEdit` 이
 * 들고(M7/7E-2b, D-7E2b-8 — 마법사가 같은 자리를 쓴다) 이 화면은 **표를 조립한다**.
 *
 * 확인 화면이 보이는 것은 둘뿐이다: ⓐ `GET /api/strategy` 가 준 **지금 값** ⓑ 운영자가 방금
 * 적은 값. 세션 응답에 초안이 없으므로(계약) 그 밖의 값을 지어낼 자리가 없다.
 */
export function StrategySection() {
  const strategy = useQuery({ queryKey: STRATEGY_QUERY_KEY, queryFn: readStrategy });

  if (strategy.isPending) {
    return <Skeleton testId="strategy-loading" />;
  }
  if (strategy.isError) {
    return isUnauthorized(strategy.error) ? null : (
      <ErrorFace testId="strategy-error" onRetry={() => void strategy.refetch()} />
    );
  }

  return <StrategyFields strategy={strategy.data} />;
}

/**
 * 편집 세션 훅은 **전략을 읽은 뒤에** 선다 — 확인이 「운영자가 본 개정」을 실어야 하기 때문이다.
 * 그래서 읽기 얼굴과 표가 다른 컴포넌트로 갈린다(훅을 조건부로 부르지 않는다).
 */
function StrategyFields({ strategy }: { readonly strategy: StrategyReadResponse }) {
  const session = useStrategyEditSession(strategy);

  return (
    <section data-testid="settings-strategy" className="flex flex-col gap-3">
      <h3 className="text-base font-semibold">감시 조건과 임계치</h3>
      <p className="text-xs text-ink-soft">
        값은 편집 세션을 지나 바뀝니다 — 고치기 → 값 → 확인. 개정 {strategy.revision}
      </p>

      <StrategyWriteFailure session={session} />
      <StrategyEditPanel session={session} strategy={strategy} />

      <dl className="grid gap-3 sm:grid-cols-2">
        {STRATEGY_FIELD_ORDER.map((field) => (
          <StrategyFieldRow
            key={field}
            field={field}
            strategy={strategy}
            // 세션은 한 번에 하나다 — **진행 중이면** 다른 칸의 「고치기」가 닫힌다.
            // 끝난 세션(만료·취소·적용)은 막지 않는다 — 그 자리는 패널의 「닫기」로 치운다.
            disabled={session.inProgress}
            onBegin={() => session.begin(field)}
          />
        ))}
      </dl>
    </section>
  );
}
