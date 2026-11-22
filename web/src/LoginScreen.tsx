import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { rememberCsrf } from './api/client';
import { signIn } from './api/queries';
import { Button, LinkButton } from './components/Button';
import { LOGIN_PATH, SIGN_UP_PATH } from './paths';

/**
 * 실패 문면은 **하나**다(M7/7D) — 계정 부재·비밀 문구 불일치·연속 실패 잠금을 화면이
 * 구분하면, 서버가 401 하나로 숨긴 사실을 화면이 되돌려 말하는 것이 된다.
 */
const FAILURE_MESSAGE = '로그인에 실패했습니다. 입력값을 확인하세요.';

const HOME_PATH = '/';

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
/**
 * 제어 문자 — 브라우저 URL 파서는 탭·개행·복귀를 **떼고** 해석한다. 그래서 `/<TAB>/evil.example`
 * 은 `//evil.example` 이 되어 바깥 주소다(v M-1). 떼기 전 글자로 판정하면 그 변형을 놓친다.
 */
function hasControlCharacter(raw: string): boolean {
  // 정규식으로 적으면 lint 가 제어 문자 리터럴을 막는다(그 규칙의 사유는 「오타로 들어온 제어
  // 문자」이고 여기서는 **의도한 집합**이다). 부호 값으로 세면 의도가 더 분명하다.
  return [...raw].some((one) => {
    const code = one.codePointAt(0) ?? 0;
    return code < 0x20 || code === 0x7f;
  });
}

/**
 * 그 주소가 **로그인 자신으로 돌아오는가**. 비교 전에 **넷**을 접는다(v L-1 · v low):
 *  · 질의와 조각 — 어느 화면을 여는지에 영향이 없다.
 *  · 퍼센트 인코딩 — 라우터는 디코딩한 값으로 화면을 고른다.
 *  · 점 구간(`.`·`..`)과 겹친 `/` — 브라우저와 history 가 접어서 해석한다.
 *  · 대소문자 — react-router 의 경로 매칭이 기본적으로 대소문자를 가리지 않는다.
 *
 * **일부러 규칙보다 넓게 접는다**(cr D-1): 디코딩이 `%2f` 도 `/` 로 풀고 아래 칸 나누기가 그것을
 * 구분자로 세는데, URL 규칙에서 `%2f` 는 칸을 가르지 않는다. 그래서 이 술어가 「로그인」으로 읽는
 * 집합이 규칙보다 **크다** — 생기는 것은 과잉 차단(→ 홈)뿐이라 안전쪽이고, 좁히려면 디코딩
 * 기계를 둘로 두어야 한다.
 */
function returnsToLogin(raw: string): boolean | null {
  /**
   * **판정 전에 퍼센트 디코딩한다**(v low). 라우터는 디코딩한 값으로 화면을 고르므로
   * `/%6Cogin` 이 로그인 화면을 열어 고리가 된다 — 글자 그대로 비교하면 그것을 놓친다.
   * `%2e` 가 `.` 과 같아지는 자리(cr O-A)도 같은 디코딩이 함께 덮는다.
   *
   * 망가진 열(`/%zz`)은 디코딩이 던진다 — 그때는 **무엇인지 증명할 수 없으므로** `null` 을 내고
   * 부르는 쪽이 홈으로 떨어뜨린다.
   */
  let head: string;
  try {
    head = decodeURIComponent(raw.split(/[?#]/u)[0] ?? '');
  } catch {
    return null;
  }
  const segments: string[] = [];
  for (const part of head.split('/')) {
    if (part === '' || part === '.') {
      continue;
    }
    if (part === '..') {
      segments.pop();
      continue;
    }
    segments.push(part);
  }
  return `/${segments.join('/')}`.toLowerCase() === LOGIN_PATH;
}

export function safeReturnPath(raw: unknown): string {
  if (
    typeof raw !== 'string' ||
    !raw.startsWith('/') ||
    raw.startsWith('//') ||
    raw.includes('\\') ||
    hasControlCharacter(raw)
  ) {
    return HOME_PATH;
  }
  // `null` 은 「디코딩이 실패해 무엇인지 모른다」다 — 모르면 홈이다.
  return returnsToLogin(raw) !== false ? HOME_PATH : raw;
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
            className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
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
            className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
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
      {/*
        계정 만들기(M15/15B) — **로그인 아래 한 자리**다. 초대 코드도 운영자 승인도 없으므로 이
        링크가 가입의 유일한 입구이고, 그 자리는 폼 **뒤**다(돌아온 사람이 먼저 읽는 것은 로그인이다).
      */}
      <div className="mt-6 flex max-w-sm flex-col gap-2 text-sm text-ink-soft">
        <span>아직 계정이 없으신가요?</span>
        <LinkButton to={SIGN_UP_PATH} tone="secondary" fullWidth testId="go-signup">
          계정 만들기
        </LinkButton>
      </div>
    </section>
  );
}
