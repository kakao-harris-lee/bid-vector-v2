import type { StateDisplay } from '../display/stateDisplay';

/**
 * 빈·로딩·오류의 **세 얼굴**(§6.5). 세 얼굴을 한 자리에 모아 두는 이유는 화면마다 다른
 * 모양으로 흩어지는 것을 막는 것이다.
 */
export function Skeleton({ testId, rows = 3 }: { readonly testId: string; readonly rows?: number }) {
  return (
    <div data-testid={testId} aria-busy="true" aria-live="polite" className="flex flex-col gap-2">
      <span className="sr-only">불러오는 중</span>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-16 animate-pulse rounded-lg bg-neutral-skin" />
      ))}
    </div>
  );
}

/**
 * **이유 있는 빈 상태**(§6.5) — 문면은 API 상태에서 온 [StateDisplay] 다. 화면이 「결과가
 * 없습니다」로 접지 않는다(빈 결과의 뜻이 여럿이고 그 구분이 서버에 있다).
 */
export function EmptyFace({
  display,
  testId,
  action,
}: {
  readonly display: StateDisplay;
  readonly testId: string;
  readonly action?: React.ReactNode;
}) {
  return (
    <div data-testid={testId} className="rounded-lg border border-line p-6 text-center">
      <p className="text-sm font-medium">{display.label}</p>
      {display.why === undefined ? null : <p className="mt-1 text-xs text-ink-soft">{display.why}</p>}
      {action === undefined ? null : <div className="mt-3">{action}</div>}
    </div>
  );
}

/**
 * 오류 얼굴 — **재시도가 있다**(§6.5). 서버 메시지를 그대로 보이지 않는다(D-7E-6): 메시지에
 * 행 값이 실릴 수 있고, 운영자가 할 수 있는 일은 다시 시도하거나 조건을 바꾸는 것뿐이다.
 */
export function ErrorFace({ testId, onRetry }: { readonly testId: string; readonly onRetry: () => void }) {
  return (
    <div data-testid={testId} role="alert" className="rounded-lg border border-line p-6 text-center">
      <p className="text-sm font-medium">불러오지 못했습니다.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded-md border border-line px-3 py-1 text-sm hover:bg-neutral-skin"
      >
        다시 시도
      </button>
    </div>
  );
}
