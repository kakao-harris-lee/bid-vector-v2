import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { rememberCsrf } from './api/client';
import { signIn } from './api/queries';
import { Button } from './components/Button';

/**
 * 실패 문면은 **하나**다(M7/7D) — 계정 부재·비밀 문구 불일치·연속 실패 잠금을 화면이
 * 구분하면, 서버가 401 하나로 숨긴 사실을 화면이 되돌려 말하는 것이 된다.
 */
const FAILURE_MESSAGE = '로그인에 실패했습니다. 입력값을 확인하세요.';

const HOME_PATH = '/';
const LOGIN_PATH = '/login';

/**
 * 로그인 뒤 **돌아갈 자리**(M13/13B). 세션이 끊겨 떠밀려 온 운영자를 홈이 아니라 하던 자리로
 * 돌려보낸다 — 그 자리를 아는 것은 보낸 쪽(`WriteFailure` 의 두 길)이고 `state` 로 싣는다.
 *
 * **앱 안의 경로만 받는다**(팀장 착수 판정 ⓑ 조건). 이 값은 화면이 지어낸 것이 아니라 라우터
 * 상태를 거쳐 온 것이고, 그런 값으로 `navigate` 를 부르면 **열린 리다이렉트**가 된다:
 *  · `https://…` 처럼 `/` 로 시작하지 않는 것 — 바깥 주소다.
 *  · `//evil.example` — 프로토콜 상대 주소라 브라우저가 바깥으로 읽는다(「/ 로 시작」을
 *    지나가므로 따로 막는다).
 *  · `/\evil.example` — 특수 스킴에서 URL 파서가 `\` 를 `/` 로 읽는다. 그러면 교차 출처가 되어
 *    `history.pushState` 가 SecurityError 를 던진다 — 로그인은 됐는데 아무 데도 못 가고 그
 *    거부를 잡는 자리도 없다(cr M-3).
 *  · `/login` 자신 — 돌아올 자리가 자기면 고리가 된다. **조각과 끝 `/` 를 떼고** 본다.
 * 전부 홈으로 떨어뜨린다.
 */
export function safeReturnPath(raw: unknown): string {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) {
    return HOME_PATH;
  }
  // **조각(`#`)과 끝 `/` 까지 떼고** 비교한다(cr M-3). 질의만 떼던 앞 판은 `/login#x` 와
  // `/login/` 을 「로그인 자신이 아니다」로 읽어 고리를 만들었다 — react-router 는 끝 `/` 를 떼고
  // 매칭하고, 조각은 어느 화면을 여는지에 영향이 없다.
  const head = raw.split(/[?#]/u)[0]?.replace(/\/+$/u, '') ?? '';
  return head === '' || head === LOGIN_PATH ? HOME_PATH : raw;
}

/**
 * 로그인 화면(M7/7D 산출물 6) — 폼 하나와 실패 표시 하나다. 화면 다섯(A~E)은 7E 다.
 *
 * 성공하면 받은 CSRF 값을 클라이언트에 기억시키고(`rememberCsrf` — 메모리에만) 홈으로
 * 보낸다. **세션 쿠키는 이 코드가 만지지 않는다**: `HttpOnly` 라 스크립트가 읽을 수도 없고,
 * 브라우저가 `Set-Cookie` 를 보관해 다음 요청에 싣는다.
 */
export function LoginScreen() {
  const navigate = useNavigate();
  const location = useLocation();
  const [loginName, setLoginName] = useState('');
  const [passphrase, setPassphrase] = useState('');
  const [failed, setFailed] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setPending(true);
    setFailed(false);
    // 비-2xx 는 던지지 않지만 **네트워크 실패는 던진다**(code-review r2 L-2). 앞 판은 그 경우
    // `setPending(false)` 에 닿지 못해 버튼이 영구히 비활성으로 굳고 실패 문면도 뜨지 않았다.
    // 실패의 **모양은 하나**로 둔다 — 사유를 나누지 않는 규율은 서버와 같다.
    let csrf: string | null;
    try {
      csrf = (await signIn(loginName, passphrase))?.csrf ?? null;
    } catch {
      csrf = null;
    } finally {
      setPending(false);
    }
    if (csrf === null) {
      setFailed(true);
      return;
    }
    rememberCsrf(csrf);
    // 입력값을 상태에 남기지 않는다 — 다음 렌더에 그 값이 DOM 에 머물 이유가 없다.
    setPassphrase('');
    await navigate(safeReturnPath((location.state as { readonly from?: unknown } | null)?.from));
  }

  return (
    <section data-testid="screen-login" className="p-6">
      <h2 className="text-lg font-semibold">로그인</h2>
      <form className="mt-4 flex max-w-sm flex-col gap-3" onSubmit={submit}>
        <label className="flex flex-col gap-1 text-sm">
          <span>로그인 식별자</span>
          <input
            name="loginName"
            autoComplete="username"
            value={loginName}
            onChange={(event) => setLoginName(event.target.value)}
            className="min-h-11 rounded-md border border-line bg-card px-3 text-sm"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>비밀 문구</span>
          <input
            name="passphrase"
            type="password"
            autoComplete="current-password"
            value={passphrase}
            onChange={(event) => setPassphrase(event.target.value)}
            className="min-h-11 rounded-md border border-line bg-card px-3 text-sm"
          />
        </label>
        <Button type="submit" tone="primary" disabled={pending} fullWidth>
          로그인
        </Button>
      </form>
      {failed ? (
        <p data-testid="login-failure" role="alert" className="mt-3 text-sm text-bad-ink">
          {FAILURE_MESSAGE}
        </p>
      ) : null}
    </section>
  );
}
