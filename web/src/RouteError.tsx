import { useRouteError } from 'react-router';

/**
 * 라우터 경계의 오류 자리(code-review r1 F-10). 화면 구현은 7E 지만, 「셸이 뜬다」를 알 수 없는
 * URL·던져진 오류에서도 참으로 유지하는 자리 하나는 골격의 일이다 — 없으면 react-router 의
 * 기본 오류 화면이 셸 밖에 그려진다.
 */
export function RouteError() {
  const error = useRouteError();
  const detail = error instanceof Error ? error.message : String(error);

  return (
    <section data-testid="route-error" className="p-6">
      <h2 className="text-lg font-semibold">화면을 열지 못했다</h2>
      <p className="mt-2 text-sm text-gray-600">{detail}</p>
    </section>
  );
}
