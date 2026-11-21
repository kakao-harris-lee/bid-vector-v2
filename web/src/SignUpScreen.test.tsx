// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { appRoutes } from './AppRoot';
import { apiClient, hasCsrf, rememberCsrf } from './api/client';
import { SIGN_UP_PATH } from './SignUpScreen';

/** React 19 는 `act(...)` 를 쓰는 환경에 이 표지를 요구한다(로그인 화면 test 와 같은 자리). */
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

interface Sent {
  readonly method: string;
  readonly url: string;
  readonly body: string;
}

const sent: Sent[] = [];
let ejectStub: (() => void) | undefined;

/** 미들웨어로 가로챈다 — 전역 `fetch` 교체는 닿지 않는다(로그인 화면 test 의 그 사유). */
function stubResponse(status: number, body: unknown): () => void {
  const middleware = {
    async onRequest({ request }: { request: Request }): Promise<Response> {
      sent.push({ method: request.method, url: request.url, body: await request.clone().text() });
      return new Response(JSON.stringify(body), {
        status,
        headers: { 'content-type': 'application/json' },
      });
    },
  };
  apiClient.use(middleware);
  return () => apiClient.eject(middleware);
}

function stubThrow(): () => void {
  const middleware = {
    onRequest(): never {
      throw new TypeError('Failed to fetch');
    },
  };
  apiClient.use(middleware);
  return () => apiClient.eject(middleware);
}

/** shipped 라우트 트리를 그대로 가입 자리에서 띄운다 — 자리의 배선까지 함께 잰다. */
async function renderSignUp(container: HTMLElement): Promise<ReturnType<typeof createMemoryRouter>> {
  const router = createMemoryRouter(appRoutes, { initialEntries: [SIGN_UP_PATH] });
  await act(async () => {
    createRoot(container).render(<RouterProvider router={router} />);
  });
  return router;
}

function setNativeValue(element: HTMLInputElement, value: string): void {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  setter?.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

async function submit(container: HTMLElement, passphrase: string, confirm = passphrase): Promise<void> {
  const form = container.querySelector('form');
  const loginName = container.querySelector<HTMLInputElement>('input[name="loginName"]');
  const secretInput = container.querySelector<HTMLInputElement>('input[name="passphrase"]');
  const confirmInput = container.querySelector<HTMLInputElement>('input[name="passphraseConfirm"]');
  if (form === null || loginName === null || secretInput === null || confirmInput === null) {
    throw new Error('가입 폼이 뜨지 않았다');
  }
  await act(async () => {
    setNativeValue(loginName, 'new-operator');
    setNativeValue(secretInput, passphrase);
    setNativeValue(confirmInput, confirm);
  });
  await act(async () => {
    form.requestSubmit();
  });
}

const CREATED = 201;
const BAD_REQUEST = 400;
const CONFLICT = 409;
const TOO_MANY = 429;
const SESSION_GONE = 503;

function textOf(container: HTMLElement, testId: string): string {
  return container.querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';
}

describe('가입 화면 (M15/15B)', () => {
  let container: HTMLElement;

  beforeEach(() => {
    rememberCsrf(null);
    sent.length = 0;
    container = document.createElement('div');
    document.body.append(container);
  });

  afterEach(() => {
    ejectStub?.();
    ejectStub = undefined;
    container.remove();
  });

  it('제출이 계약의 경로·본문으로 간다 — 확인 칸은 보내지 않는다', async () => {
    ejectStub = stubResponse(CREATED, { csrf: 'issued-stamp', expiresAt: '2026-11-21T12:00:00Z' });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    const posts = sent.filter((one) => one.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0]?.url).toContain('/api/accounts');
    expect(JSON.parse(posts[0]?.body ?? '{}')).toEqual({
      loginName: 'new-operator',
      passphrase: 'a-long-enough-passphrase',
    });
  });

  it('성공하면 CSRF 값을 기억하고 곧장 시작하기로 간다', async () => {
    ejectStub = stubResponse(CREATED, { csrf: 'issued-stamp', expiresAt: '2026-11-21T12:00:00Z' });
    const router = await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    expect(hasCsrf()).toBe(true);
    expect(router.state.location.pathname).toBe('/start');
  });

  it('성공해도 비밀번호를 입력칸에 남기지 않는다', async () => {
    ejectStub = stubResponse(CREATED, { csrf: 'issued-stamp', expiresAt: '2026-11-21T12:00:00Z' });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    expect(container.querySelector<HTMLInputElement>('input[name="passphrase"]')?.value ?? '').toBe('');
  });

  /** 서버가 코드로 사유를 가르고 화면이 그 코드로 **칸**을 고른다 — 한 문장으로 접지 않는다. */
  it('중복은 아이디 칸 옆에 선다', async () => {
    ejectStub = stubResponse(CONFLICT, { code: 'ACCOUNT_EXISTS', message: '이미 쓰이는 아이디다', correlationId: 'c' });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    expect(textOf(container, 'signup-error-login-name')).toContain('이미 쓰이는 아이디');
    expect(container.querySelector('[data-testid="signup-error-passphrase"]')).toBeNull();
    expect(hasCsrf()).toBe(false);
  });

  it('아이디 형식 오류와 비밀번호 형식 오류가 서로 다른 칸에 선다', async () => {
    ejectStub = stubResponse(BAD_REQUEST, { code: 'LOGIN_NAME_INVALID', message: 'x', correlationId: 'c' });
    await renderSignUp(container);
    await submit(container, 'a-long-enough-passphrase');
    expect(textOf(container, 'signup-error-login-name')).toContain('아이디는');

    ejectStub();
    ejectStub = stubResponse(BAD_REQUEST, { code: 'PASSPHRASE_INVALID', message: 'x', correlationId: 'c' });
    await submit(container, 'short');
    expect(textOf(container, 'signup-error-passphrase')).toContain('10자 이상');
    // 바이트 문면은 사라졌다 — 이 구현의 해시는 긴 입력을 접지 자르지 않는다(cr r1 M-1).
    expect(textOf(container, 'signup-error-passphrase')).not.toContain('바이트');
    expect(container.querySelector('[data-testid="signup-error-login-name"]')).toBeNull();
  });

  it('상한 초과는 폼 아래 한 줄로 선다', async () => {
    ejectStub = stubResponse(TOO_MANY, { code: 'SIGN_UP_RATE_LIMITED', message: 'x', correlationId: 'c' });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    expect(textOf(container, 'signup-failure')).toContain('너무 잦습니다');
  });

  /** 계정은 섰는데 세션이 서지 않은 자리 — 「다시 가입」이 아니라 **로그인**으로 안내한다. */
  it('세션을 열지 못한 경우는 로그인으로 안내한다', async () => {
    ejectStub = stubResponse(SESSION_GONE, {
      code: 'SIGN_UP_SESSION_UNAVAILABLE',
      message: 'x',
      correlationId: 'c',
    });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    expect(textOf(container, 'signup-failure')).toContain('로그인으로');
  });

  /** 확인 칸은 **서버가 볼 수 없는 사실**이라 보내지 않는다 — 화면이 스스로 가른다. */
  it('비밀번호 확인이 다르면 요청을 보내지 않고 그 칸 옆에 문장을 둔다', async () => {
    ejectStub = stubResponse(CREATED, { csrf: 'issued-stamp', expiresAt: '2026-11-21T12:00:00Z' });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase', 'a-different-passphrase');

    expect(sent.filter((one) => one.method === 'POST')).toHaveLength(0);
    expect(textOf(container, 'signup-error-confirm')).toContain('서로 다릅니다');
  });

  it('네트워크 실패에서도 버튼이 풀리고 문장 하나가 보인다', async () => {
    ejectStub = stubThrow();
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    expect(textOf(container, 'signup-failure')).not.toBe('');
    expect(container.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
    expect(hasCsrf()).toBe(false);
  });

  it('비밀번호 칸 둘 다 password 타입이다 — 화면에 평문이 드러나지 않는다', async () => {
    await renderSignUp(container);

    expect(container.querySelector<HTMLInputElement>('input[name="passphrase"]')?.type).toBe('password');
    expect(container.querySelector<HTMLInputElement>('input[name="passphraseConfirm"]')?.type).toBe('password');
  });

  /** 세션 전 자리라 전역 탐색이 뜨지 않는다 — 로그인 자리와 **같은 규칙**이다. */
  it('가입 자리에는 전역 탐색이 뜨지 않는다', async () => {
    await renderSignUp(container);

    expect(container.querySelector('[data-testid="screen-signup"]')).not.toBeNull();
    expect(container.querySelectorAll('nav[aria-label="화면"]')).toHaveLength(0);
  });

  /** code-review r1 M-2 — 오류 문장이 `<label>` 밖이라 입력칸의 **접근 이름**을 오염시키지 않는다. */
  it('오류 문장이 입력칸의 접근 이름에 섞이지 않고 aria 로 묶인다', async () => {
    ejectStub = stubResponse(CONFLICT, { code: 'ACCOUNT_EXISTS', message: 'x', correlationId: 'c' });
    await renderSignUp(container);

    await submit(container, 'a-long-enough-passphrase');

    const input = container.querySelector<HTMLInputElement>('input[name="loginName"]');
    const label = container.querySelector<HTMLLabelElement>('label[for="signup-login-name"]');
    expect(label?.textContent ?? '').toBe('아이디');
    expect(input?.getAttribute('aria-invalid')).toBe('true');
    expect(input?.getAttribute('aria-describedby')).toBe('signup-error-login-name');
    // 오류 노드는 label 의 자손이 **아니다** — 안에 있으면 그 글자가 접근 이름이 된다.
    expect(label?.querySelector('[data-testid="signup-error-login-name"]')).toBeNull();
  });

  it('거부가 없으면 aria-invalid 가 서지 않는다 — 대조', async () => {
    await renderSignUp(container);

    const input = container.querySelector<HTMLInputElement>('input[name="loginName"]');
    expect(input?.getAttribute('aria-invalid')).toBe('false');
    expect(input?.getAttribute('aria-describedby')).toBeNull();
  });

  /** code-review r1 M-3 — 세션 전 자리라 셸 탐색이 없다. 이 링크가 유일한 길이다. */
  it('가입 화면에서 로그인으로 돌아가는 링크가 있다', async () => {
    await renderSignUp(container);

    const link = container.querySelector<HTMLAnchorElement>('[data-testid="go-login"]');
    expect(link?.getAttribute('href')).toBe('/login');
  });

  it('로그인 화면에서 가입 자리로 가는 링크가 있다', async () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/login'] });
    await act(async () => {
      createRoot(container).render(<RouterProvider router={router} />);
    });

    const link = container.querySelector<HTMLAnchorElement>('[data-testid="go-signup"]');
    expect(link?.getAttribute('href')).toBe(SIGN_UP_PATH);
  });
});
