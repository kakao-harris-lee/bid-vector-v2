import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';

import { rememberCsrf } from './api/client';
import { signUp, type SignUpRefusal } from './api/queries';
import { Button, LinkButton } from './components/Button';
import { Card } from './components/Card';
import { LOGIN_PATH } from './LoginScreen';

/**
 * 가입 자리의 주소 — **여기가 한 자리**다. 라우트 표(`routes.tsx`)와 로그인 화면의 링크가 이 값을
 * 읽는다(라우트 표에 두면 화면 → 표 → 화면의 고리가 생긴다).
 */
export const SIGN_UP_PATH = '/signup';

/** 가입 뒤 가는 자리 — 계약의 「성공하면 곧장 시작하기」. */
const START_PATH = '/start';

/** 칸 셋의 이름 — 표지·`id`·`aria-describedby` 가 같은 값을 읽는다(사본을 만들지 않는다). */
const FIELDS = {
  loginName: 'signup-error-login-name',
  passphrase: 'signup-error-passphrase',
  confirm: 'signup-error-confirm',
  form: 'signup-failure',
} as const;

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
 *
 * 비밀번호 문장이 **바이트를 말하지 않는다**(code-review r1 M-1): 서버의 해시는 긴 입력을 접고
 * 자르지 않으므로 한글도 영문과 같은 글자 수 규칙 아래 선다.
 */
const REFUSAL_MESSAGE = {
  loginName: '아이디는 4~32자의 영문 소문자·숫자·마침표·밑줄·붙임표만 쓸 수 있습니다.',
  passphrase: '비밀번호는 10자 이상이어야 합니다.',
  duplicate: '이미 쓰이는 아이디입니다. 다른 아이디를 적어 주세요.',
  rateLimited: '가입 시도가 너무 잦습니다. 잠시 뒤에 다시 시도해 주세요.',
  sessionUnavailable: '계정은 만들어졌습니다. 아래 「로그인으로」에서 로그인해 주세요.',
  unknown: '가입하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.',
} as const satisfies Record<SignUpRefusal, string>;

/** 화면이 스스로 가르는 것 — 서버가 볼 수 없는 사실 하나(확인 칸이 본 칸과 다르다). */
const CONFIRM_MISMATCH = '비밀번호가 서로 다릅니다.';

const inputClass = 'min-h-11 rounded-md border border-control-line bg-card px-3 text-sm';

/**
 * 칸 옆 문장 하나 — **`<label>` 밖**에 선다(code-review r1 M-2). 안에 두면 암묵 label 의 텍스트가
 * 전부 입력칸의 접근 이름이 되어, 스크린리더 사용자가 칸으로 옮길 때마다 긴 오류를 **이름으로**
 * 다시 듣는다(`role="alert"` 로 이미 한 번 읽히므로 중복이기도 하다). 로그인 화면이 실패 문장을
 * 폼 밖에 두는 것과 같은 규율이다.
 */
function FieldError({ testId, message }: { readonly testId: string; readonly message: string | null }) {
  if (message === null) {
    return null;
  }
  return (
    <p id={testId} data-testid={testId} role="alert" className="text-sm text-bad-ink">
      {message}
    </p>
  );
}

/**
 * 가입 화면(M15/15B) — 아이디 · 비밀번호 · 비밀번호 확인 셋이다. 성공하면 **곧장 시작하기**로
 * 간다(계약): 가입 응답이 로그인과 같은 세션을 내므로 따로 로그인하지 않는다.
 *
 * **가입은 늘 시작하기로 간다.** 로그인 화면의 `from` 복귀를 따르지 않는다 — 계약이 그 자리를
 * 적었고, 처음 온 계정에는 「하던 자리」가 없다.
 *
 * **오류는 칸 옆에 선다.** 서버가 사유를 코드로 가르고(로그인과 반대인 것이 의도다 — 가입 규칙은
 * 공개된 값이다) 이 화면이 그 코드로 자리를 고른다. 「비밀번호 확인」만 화면이 스스로 가른다 —
 * 서버가 볼 수 없는 사실이라 보낼 이유가 없다.
 *
 * **셸·카드 문법은 M14 를 따른다**(`Card`·`Button`). 세션 전 자리라 셸의 전역 탐색은 뜨지 않고,
 * 그래서 **로그인으로 돌아가는 링크를 이 화면이 직접 든다**(code-review r1 M-3).
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

  const messageFor = (field: 'loginName' | 'passphrase' | 'form'): string | null =>
    refusal !== null && REFUSAL_FIELD[refusal] === field ? REFUSAL_MESSAGE[refusal] : null;

  const loginNameError = messageFor('loginName');
  const passphraseError = messageFor('passphrase');
  const confirmError = mismatch ? CONFIRM_MISMATCH : null;

  /**
   * 칸 하나의 `aria-describedby` — 오류가 없으면 **속성을 두지 않는다**(빈 문자열은 「없는 id 를
   * 가리킨다」가 된다).
   *
   * **펼친 속성(`{...}`)으로 묶지 않는다**(터치 타깃 게이트의 이름 덮어쓰기 축): 펼치면 그 자리에
   * `aria-label` 이 있는지 **증명할 수 없고**, 게이트는 증명할 수 없는 자리를 「레이블의 이름을
   * 덮는다」로 센다. 그래서 두 속성을 입력마다 **글자로** 적는다.
   */
  const describedBy = (testId: string, message: string | null): string | undefined =>
    message === null ? undefined : testId;

  return (
    <section data-testid="screen-signup" className="p-6">
      <Card as="div" padding="lg">
        <h2 className="text-lg font-semibold">계정 만들기</h2>
        <p className="mt-1 text-sm text-ink-soft">아이디와 비밀번호만 있으면 바로 시작할 수 있습니다.</p>
        <form className="mt-4 flex max-w-sm flex-col gap-3" onSubmit={(event) => void submit(event)}>
          <div className="flex flex-col gap-1">
            <label className="flex flex-col gap-1 text-sm" htmlFor="signup-login-name">
              <span>아이디</span>
            </label>
            <input
              id="signup-login-name"
              name="loginName"
              // **타입을 적는다** — 적지 않으면 터치 타깃 게이트가 「증명할 수 없는 컨트롤」로
              // 보고 체크박스·라디오와 같은 의무(감싼 레이블)를 지운다. 그 의무는 이 칸의
              // 접근 이름을 다시 오염시키므로, 타입을 밝혀 그 모집단 밖에 둔다.
              type="text"
              autoComplete="username"
              value={loginName}
              onChange={(event) => setLoginName(event.target.value)}
              className={inputClass}
              aria-invalid={loginNameError !== null}
              aria-describedby={describedBy(FIELDS.loginName, loginNameError)}
            />
            <FieldError testId={FIELDS.loginName} message={loginNameError} />
          </div>
          <div className="flex flex-col gap-1">
            <label className="flex flex-col gap-1 text-sm" htmlFor="signup-passphrase">
              <span>비밀번호</span>
            </label>
            <input
              id="signup-passphrase"
              name="passphrase"
              type="password"
              autoComplete="new-password"
              value={passphrase}
              onChange={(event) => setPassphrase(event.target.value)}
              className={inputClass}
              aria-invalid={passphraseError !== null}
              aria-describedby={describedBy(FIELDS.passphrase, passphraseError)}
            />
            <FieldError testId={FIELDS.passphrase} message={passphraseError} />
          </div>
          <div className="flex flex-col gap-1">
            <label className="flex flex-col gap-1 text-sm" htmlFor="signup-passphrase-confirm">
              <span>비밀번호 확인</span>
            </label>
            <input
              id="signup-passphrase-confirm"
              name="passphraseConfirm"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              className={inputClass}
              aria-invalid={confirmError !== null}
              aria-describedby={describedBy(FIELDS.confirm, confirmError)}
            />
            <FieldError testId={FIELDS.confirm} message={confirmError} />
          </div>
          <Button type="submit" tone="primary" disabled={pending} fullWidth testId="signup-submit">
            계정 만들기
          </Button>
          <FieldError testId={FIELDS.form} message={messageFor('form')} />
        </form>
        {/*
          로그인으로(M15/15B cr r1 M-3) — 세션 전 자리라 셸의 전역 탐색이 없다. 이 링크가 없으면
          `SIGN_UP_SESSION_UNAVAILABLE` 안내가 **갈 수 없는 곳**을 가리키고, 이미 계정이 있는
          사람이 이 주소를 직접 열었을 때도 뒤로 가기 말고는 길이 없다.
        */}
        <p className="mt-6 flex max-w-sm flex-col gap-2 text-sm text-ink-soft">
          <span>이미 계정이 있으신가요?</span>
          <LinkButton to={LOGIN_PATH} tone="secondary" fullWidth testId="go-login">
            로그인으로
          </LinkButton>
        </p>
      </Card>
    </section>
  );
}
