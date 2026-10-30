import { Link } from 'react-router';

import { ApiError } from '../api/apiError';

const HTTP_UNAUTHORIZED = 401;

/**
 * 쓰기 전이가 실패했을 때의 얼굴(CR H-2 · M-2). 읽기만 세 얼굴을 갖추고 쓰기가 비어 있으면,
 * 실패가 **아무 표시 없이** 지나가고 운영자는 「기록이 남았는지」를 알 길이 없다 — 이 제품에서
 * 가장 비싼 침묵이다.
 *
 * **401 은 다른 답이다**: 일시 장애는 재시도로 풀리지만 세션 만료는 영원히 실패한다. 그래서
 * 재시도 버튼 대신 로그인으로 가는 길을 낸다(`ApiError.status` 가 이미 그 재료를 든다).
 *
 * 서버 메시지·코드는 **그대로 내보이지 않는다**(D-7E-6) — 운영자가 할 수 있는 일은 다시 시도하거나
 * 다시 로그인하는 것이고, 메시지에는 행 값이 실릴 수 있다.
 */
export function WriteFailureFace({
  what,
  error,
  onRetry,
}: {
  /** 실패한 일의 이름 — 「담지」·「기록하지」처럼 뒤에 「못했습니다」가 붙는 꼴. */
  readonly what: string;
  readonly error: unknown;
  readonly onRetry: () => void;
}) {
  const unauthorized = error instanceof ApiError && error.status === HTTP_UNAUTHORIZED;
  return (
    <div
      data-testid="write-failure"
      role="alert"
      className="rounded-lg border border-bad-ink/40 bg-bad-skin p-4 text-bad-ink"
    >
      {unauthorized ? (
        <>
          <p className="text-sm font-medium">로그인이 필요합니다.</p>
          <p className="mt-1 text-xs">세션이 끝났습니다 — 다시 로그인한 뒤 같은 자리에서 이어 하십시오.</p>
          <Link
            to="/login"
            className="mt-3 inline-block rounded-md border border-bad-ink/40 px-3 py-1 text-xs underline"
          >
            로그인 화면으로
          </Link>
        </>
      ) : (
        <>
          <p className="text-sm font-medium">{what} 못했습니다.</p>
          {/* 실패 뒤 서버 상태를 다시 읽었다는 사실을 함께 적는다 — 화면이 보이는 상태가
              「내가 눌렀다고 믿는 것」이 아니라 서버의 사실이라는 뜻이다. */}
          <p className="mt-1 text-xs">서버에 남은 상태를 다시 읽었습니다. 아래 상태를 확인한 뒤 다시 시도하십시오.</p>
          <button
            type="button"
            onClick={onRetry}
            className="mt-3 rounded-md border border-bad-ink/40 px-3 py-1 text-xs"
          >
            다시 시도
          </button>
        </>
      )}
    </div>
  );
}

/**
 * 세션이 없다는 **서버의 답**을 소비하는 얼굴(CR M-2). 읽기 401 과 「세션 자원이 없다」(404)는
 * 재시도로 풀리지 않는다 — 앞 판은 그것을 일시 장애와 같은 얼굴로 내고 영원히 재시도하게 했다.
 */
export function SignedOutFace() {
  return (
    <div data-testid="signed-out" role="alert" className="rounded-lg border border-line p-6 text-center">
      <p className="text-sm font-medium">로그인이 필요합니다.</p>
      <p className="mt-1 text-xs text-ink-soft">세션이 없거나 끝났습니다.</p>
      <Link to="/login" className="mt-3 inline-block rounded-md border border-line px-3 py-1 text-sm underline">
        로그인 화면으로
      </Link>
    </div>
  );
}

/**
 * 재수출 — 판단은 `api/apiError.ts` 한 자리에 산다(M7/7E-3a r3 CR L-4). 부르는 자리를 전부
 * 고치는 대신 여기서 잇는다: 화면들이 「얼굴을 고르는 곳」에서 이것을 쓰는 것이 자연스럽고,
 * 네트워크 설정(`queryClient.ts`)은 순수 자리에서 직접 가져간다.
 */
export { isUnauthorized } from '../api/apiError';
