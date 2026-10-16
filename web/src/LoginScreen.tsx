import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';

import { apiClient, rememberCsrf } from './api/client';

/**
 * 실패 문면은 **하나**다(M7/7D) — 계정 부재·비밀 문구 불일치·연속 실패 잠금을 화면이
 * 구분하면, 서버가 401 하나로 숨긴 사실을 화면이 되돌려 말하는 것이 된다.
 */
const FAILURE_MESSAGE = '로그인에 실패했습니다. 입력값을 확인하세요.';

/**
 * 로그인 화면(M7/7D 산출물 6) — 폼 하나와 실패 표시 하나다. 화면 다섯(A~E)은 7E 다.
 *
 * 성공하면 받은 CSRF 값을 클라이언트에 기억시키고(`rememberCsrf` — 메모리에만) 홈으로
 * 보낸다. **세션 쿠키는 이 코드가 만지지 않는다**: `HttpOnly` 라 스크립트가 읽을 수도 없고,
 * 브라우저가 `Set-Cookie` 를 보관해 다음 요청에 싣는다.
 */
export function LoginScreen() {
  const navigate = useNavigate();
  const [loginName, setLoginName] = useState('');
  const [passphrase, setPassphrase] = useState('');
  const [failed, setFailed] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setPending(true);
    setFailed(false);
    const { data } = await apiClient.POST('/api/session', { body: { loginName, passphrase } });
    setPending(false);
    if (data === undefined) {
      setFailed(true);
      return;
    }
    rememberCsrf(data.csrf);
    // 입력값을 상태에 남기지 않는다 — 다음 렌더에 그 값이 DOM 에 머물 이유가 없다.
    setPassphrase('');
    await navigate('/');
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
            className="rounded border border-gray-300 px-2 py-1"
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
            className="rounded border border-gray-300 px-2 py-1"
          />
        </label>
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-gray-900 px-3 py-1 text-sm text-white disabled:opacity-50"
        >
          로그인
        </button>
      </form>
      {failed ? (
        <p data-testid="login-failure" role="alert" className="mt-3 text-sm text-red-700">
          {FAILURE_MESSAGE}
        </p>
      ) : null}
    </section>
  );
}
