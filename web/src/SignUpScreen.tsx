import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';

import { rememberCsrf } from './api/client';
import { signUp, type SignUpRefusal } from './api/queries';
import { Button } from './components/Button';
import { Card } from './components/Card';

/**
 * 가입 자리의 주소 — **여기가 한 자리**다. 라우트 표(`routes.tsx`)와 로그인 화면의 링크가 이 값을
 * 읽는다(라우트 표에 두면 화면 → 표 → 화면의 고리가 생긴다).
 */
export const SIGN_UP_PATH = '/signup';

/** 가입 뒤 가는 자리 — 계약의 「성공하면 곧장 시작하기」. */
const START_PATH = '/start';

/**
 * 거부 사유 → **그 문장이 설 칸**. 사유를 칸에 사상하는 자리가 한 곳이라, 사유가 늘면 여기서
 * 컴파일이 멈춘다(`Record` 전수).
 */
const REFUSAL_FIELD = {
  loginName: 'loginName',
  passphrase: 'passphrase',
  duplicate: 'loginName',
  rateLimited: 'form',
  sessionUnavailable: 'form',
  unknown: 'form',
} as const satisfies Record<SignUpRefusal, 'loginName' | 'passphrase' | 'form'>;

/**
 * 사유 → 문장. **서버의 고정 문구를 그대로 쓰지 않는다** — 그 문면은 운영자 로그의 어휘이고,
 * 여기는 처음 오는 사람이 읽는 자리다(무엇을 고쳐야 하는지 말한다).
 */
const REFUSAL_MESSAGE = {
  loginName: '아이디는 4~32자의 영문 소문자·숫자·마침표·밑줄·붙임표만 쓸 수 있습니다.',
  passphrase: '비밀번호는 10자 이상이어야 합니다(한글은 한 글자가 3바이트, 모두 72바이트까지).',
  duplicate: '이미 쓰이는 아이디입니다. 다른 아이디를 적어 주세요.',
  rateLimited: '가입 시도가 너무 잦습니다. 잠시 뒤에 다시 시도해 주세요.',
  sessionUnavailable: '계정은 만들어졌습니다. 로그인 화면에서 로그인해 주세요.',
  unknown: '가입하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.',
} as const satisfies Record<SignUpRefusal, string>;

/** 화면이 스스로 가르는 것 — 서버가 볼 수 없는 사실 하나(확인 칸이 본 칸과 다르다). */
const CONFIRM_MISMATCH = '비밀번호가 서로 다릅니다.';

const inputClass = 'min-h-11 rounded-md border border-control-line bg-card px-3 text-sm';

/** 칸 옆 문장 하나 — 자리마다 표지를 가른다(여정이 같은 이름을 둘 집지 않게). */
function FieldError({ testId, message }: { readonly testId: string; readonly message: string | null }) {
  if (message === null) {
    return null;
  }
  return (
    <span data-testid={testId} role="alert" className="text-sm text-bad-ink">
      {message}
    </span>
  );
}

/**
 * 가입 화면(M15/15B) — 아이디 · 비밀번호 · 비밀번호 확인 셋이다. 성공하면 **곧장 시작하기**로
 * 간다(계약): 가입 응답이 로그인과 같은 세션을 내므로 따로 로그인하지 않는다.
 *
 * **오류는 칸 옆에 선다.** 서버가 사유를 코드로 가르고(로그인과 반대인 것이 의도다 — 가입 규칙은
 * 공개된 값이다) 이 화면이 그 코드로 자리를 고른다. 「비밀번호 확인」만 화면이 스스로 가른다 —
 * 서버가 볼 수 없는 사실이라 보낼 이유가 없다.
 *
 * **셸·카드 문법은 M14 를 따른다**(`Card`·`Button`). 세션 전 자리라 셸의 전역 탐색은 뜨지 않는다.
 */
export function SignUpScreen() {
  const navigate = useNavigate();
  const [loginName, setLoginName] = useState('');
  const [passphrase, setPassphrase] = useState('');
  const [confirm, setConfirm] = useState('');
  const [refusal, setRefusal] = useState<SignUpRefusal | null>(null);
  const [mismatch, setMismatch] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setRefusal(null);
    setMismatch(false);
    if (passphrase !== confirm) {
      setMismatch(true);
      return;
    }
    setPending(true);
    // 비-2xx 는 던지지 않지만 **네트워크 실패와 5xx 는 던진다** — 그 경우에도 버튼이 영구히
    // 비활성으로 굳지 않게 `finally` 에서 푼다(로그인 화면이 같은 이유로 그렇게 한다).
    let granted: string | null = null;
    try {
      const result = await signUp(loginName, passphrase);
      if (result.kind === 'refused') {
        setRefusal(result.refusal);
      } else {
        granted = result.session.csrf;
      }
    } catch {
      setRefusal('unknown');
    } finally {
      setPending(false);
    }
    if (granted === null) {
      return;
    }
    rememberCsrf(granted);
    // 입력값을 상태에 남기지 않는다 — 다음 렌더에 그 값이 DOM 에 머물 이유가 없다.
    setPassphrase('');
    setConfirm('');
    await navigate(START_PATH);
  }

  const fieldOf = (field: 'loginName' | 'passphrase' | 'form'): string | null =>
    refusal !== null && REFUSAL_FIELD[refusal] === field ? REFUSAL_MESSAGE[refusal] : null;

  return (
    <section data-testid="screen-signup" className="p-6">
      <Card as="div" padding="lg">
        <h2 className="text-lg font-semibold">계정 만들기</h2>
        <p className="mt-1 text-sm text-ink-soft">아이디와 비밀번호만 있으면 바로 시작할 수 있습니다.</p>
        <form className="mt-4 flex max-w-sm flex-col gap-3" onSubmit={(event) => void submit(event)}>
          <label className="flex flex-col gap-1 text-sm">
            <span>아이디</span>
            <input
              name="loginName"
              autoComplete="username"
              value={loginName}
              onChange={(event) => setLoginName(event.target.value)}
              className={inputClass}
            />
            <FieldError testId="signup-error-login-name" message={fieldOf('loginName')} />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>비밀번호</span>
            <input
              name="passphrase"
              type="password"
              autoComplete="new-password"
              value={passphrase}
              onChange={(event) => setPassphrase(event.target.value)}
              className={inputClass}
            />
            <FieldError testId="signup-error-passphrase" message={fieldOf('passphrase')} />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>비밀번호 확인</span>
            <input
              name="passphraseConfirm"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              className={inputClass}
            />
            <FieldError testId="signup-error-confirm" message={mismatch ? CONFIRM_MISMATCH : null} />
          </label>
          <Button type="submit" tone="primary" disabled={pending} fullWidth testId="signup-submit">
            계정 만들기
          </Button>
          <FieldError testId="signup-failure" message={fieldOf('form')} />
        </form>
      </Card>
    </section>
  );
}
