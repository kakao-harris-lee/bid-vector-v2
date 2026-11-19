import { useLocation, useNavigate } from 'react-router';

import { Button } from './components/Button';

/**
 * 라우터 경계의 오류 자리. 「셸이 뜬다」를 알 수 없는 URL·던져진 오류에서도 참으로 유지하는
 * 자리다 — 없으면 react-router 의 기본 오류 화면이 셸 밖에 그려진다.
 *
 * **던진 값의 원문을 보이지 않는다**(D-7E-6 과 같은 축). 앞 판은 `error.message` 를 그대로
 * 그렸다 — 그 글자에는 행 값이 실릴 수 있고, 운영자가 그것으로 할 수 있는 일이 없다. 그래서
 * 이 자리는 **읽을 수 없는 사실을 옮기지 않고** 할 수 있는 일 하나를 준다.
 *
 * 그 하나가 **다시 시도**다. 화면이 서지 못한 사유 대부분은 한 번의 렌더에 묶여 있어서 같은
 * 자리를 다시 열면 풀린다. 그래서 **지금 자리로 다시 이동한다** — 그 이동이 경계의 오류를 함께
 * 지우고 라우트를 다시 그린다. `history.go(0)` 꼴(문서 새로고침)을 쓰지 않는 이유는 둘이다:
 * 쥔 서버 상태를 모두 버리고, 그 거동이 **측정되지 않는다**(jsdom 은 새로고침을 구현하지 않아
 * 짝 test 가 설 자리가 없다). 지금 꼴은 측정된다 — 짝 test 가 던지기를 끈 뒤 누르고, 화면이
 * 서는 것을 본다. `replace` 라 「다시 시도」가 뒤로 가기 이력을 쌓지 않는다.
 */
export function RouteError() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <section data-testid="route-error" className="p-6">
      <h2 className="text-lg font-semibold">화면을 열지 못했습니다</h2>
      <p className="mt-2 text-sm text-ink-soft">일시적인 문제일 수 있습니다. 다시 시도해 주십시오.</p>
      <div className="mt-3">
        <Button
          onClick={() => {
            void navigate(`${location.pathname}${location.search}`, { replace: true });
          }}
        >
          다시 시도
        </Button>
      </div>
    </section>
  );
}
