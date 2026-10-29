// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { appRoutes } from './AppRoot';
import { apiClient, hasCsrf, rememberCsrf } from './api/client';

/**
 * React 19 는 `act(...)` 를 쓰는 환경에 이 표지를 요구한다 — 없으면 상태 갱신이 flush 되지 않고
 * 제출 핸들러의 효과가 그대로 사라진다(실측: 요청 0건, stderr 에 「not configured to support
 * act」). 테스트 라이브러리를 들이지 않고 한 줄로 그 표지를 세운다(7A 의 하네스 관례).
 */
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

interface Sent {
  readonly method: string;
  readonly url: string;
  readonly body: string;
}

const sent: Sent[] = [];
const NO_CONTENT = 204;
let ejectStub: (() => void) | undefined;

/**
 * **미들웨어로 가로챈다.** 화면은 클라이언트를 자기가 부르므로 요청마다 `fetch` 를 넘길 자리가
 * 없고, **전역 `fetch` 교체는 닿지 않는다** — `createClient` 가 모듈 적재 시점에 그 참조를
 * 캡처하기 때문이다(실측: 전역을 바꿔도 요청이 실 네트워크로 나갔다). `onRequest` 가 `Response`
 * 를 돌려주면 그 자리에서 끝나므로 실 네트워크 호출이 0 이고, 클라이언트의 CSRF 미들웨어는
 * 그대로 먼저 돈다(이 가로채기가 그 층을 건너뛰지 않는다).
 */
function stubResponse(status: number, body: unknown): () => void {
  const middleware = {
    async onRequest({ request }: { request: Request }): Promise<Response> {
      sent.push({ method: request.method, url: request.url, body: await request.clone().text() });
      return new Response(status === NO_CONTENT ? null : JSON.stringify(body), {
        status,
        headers: { 'content-type': 'application/json' },
      });
    },
  };
  apiClient.use(middleware);
  return () => apiClient.eject(middleware);
}

/** 네트워크 실패를 흉내 낸다 — 미들웨어가 던지면 `apiClient.POST` 가 그대로 던진다. */
function stubThrow(): () => void {
  const middleware = {
    onRequest(): never {
      throw new TypeError('Failed to fetch');
    },
  };
  apiClient.use(middleware);
  return () => apiClient.eject(middleware);
}

/** shipped 라우트 트리를 그대로 `/login` 에서 띄운다 — 화면 자리의 배선까지 함께 잰다. */
async function renderLogin(container: HTMLElement): Promise<void> {
  const router = createMemoryRouter(appRoutes, { initialEntries: ['/login'] });
  await act(async () => {
    createRoot(container).render(<RouterProvider router={router} />);
  });
}

async function submit(container: HTMLElement, passphrase: string): Promise<void> {
  const form = container.querySelector('form');
  const loginName = container.querySelector<HTMLInputElement>('input[name="loginName"]');
  const secretInput = container.querySelector<HTMLInputElement>('input[name="passphrase"]');
  if (form === null || loginName === null || secretInput === null) {
    throw new Error('로그인 폼이 뜨지 않았다');
  }
  await act(async () => {
    setNativeValue(loginName, 'operator');
    setNativeValue(secretInput, passphrase);
  });
  await act(async () => {
    // `requestSubmit()` 이 표준 제출 경로다 — 합성 `submit` 이벤트를 직접 dispatch 하면
    // React 의 위임 리스너에 닿지 않는 환경이 있다(실측: 요청 0건).
    form.requestSubmit();
  });
}

/** React 가 제어하는 input 에 값을 넣는 표준 관용구(네이티브 setter + input 이벤트). */
function setNativeValue(element: HTMLInputElement, value: string): void {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  setter?.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

describe('로그인 화면 (7D)', () => {
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

  /**
   * **M7/7E-1 — 「요청이 하나」가 아니라 「로그인 POST 가 하나」를 잰다.** 성공하면 `/`(화면 B)로
   * 가고 그 화면은 자기 읽기를 건다 — 7A 때는 그 자리가 빈 자리라 요청 총수가 1 이었다. 재려는
   * 것은 **제출이 계약의 경로·본문으로 가는가**이고 그것은 그대로다.
   */
  it('폼이 뜨고 제출이 계약의 경로·본문으로 간다', async () => {
    ejectStub = stubResponse(201, { csrf: 'issued-stamp', expiresAt: '2026-10-16T12:00:00Z' });
    await renderLogin(container);

    await submit(container, 'right-passphrase-value');

    const posts = sent.filter((one) => one.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0]?.url).toContain('/api/session');
    expect(JSON.parse(posts[0]?.body ?? '{}')).toEqual({
      loginName: 'operator',
      passphrase: 'right-passphrase-value',
    });
  });

  it('성공하면 CSRF 값을 기억하고 비밀 문구를 입력칸에 남기지 않는다', async () => {
    ejectStub = stubResponse(201, { csrf: 'issued-stamp', expiresAt: '2026-10-16T12:00:00Z' });
    await renderLogin(container);

    await submit(container, 'right-passphrase-value');

    expect(hasCsrf()).toBe(true);
    const secretInput = container.querySelector<HTMLInputElement>('input[name="passphrase"]');
    expect(secretInput?.value ?? '').toBe('');
  });

  it('실패는 사유를 나누지 않는 문면 하나로 보인다 — 값은 기억하지 않는다', async () => {
    ejectStub = stubResponse(401, { code: 'UNAUTHENTICATED', message: '인증에 실패했다', correlationId: 'c' });
    await renderLogin(container);

    await submit(container, 'wrong-passphrase-value');

    const failure = container.querySelector('[data-testid="login-failure"]');
    expect(failure?.textContent ?? '').toContain('로그인에 실패했습니다');
    expect(hasCsrf()).toBe(false);
  });

  /**
   * code-review r2 L-2 — 네트워크 실패에서 `apiClient.POST` 는 **던진다**. 앞 판은 그 경로에서
   * `setPending(false)` 에 닿지 못해 버튼이 영구히 비활성으로 굳고 실패 문면도 뜨지 않았다.
   */
  it('네트워크 실패에서도 버튼이 풀리고 실패 문면 하나가 보인다', async () => {
    ejectStub = stubThrow();
    await renderLogin(container);

    await submit(container, 'right-passphrase-value');

    expect(container.querySelector('[data-testid="login-failure"]')).not.toBeNull();
    expect(container.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
    expect(hasCsrf()).toBe(false);
  });

  it('비밀 문구 입력칸은 password 타입이다 — 화면에 평문이 드러나지 않는다', async () => {
    ejectStub = stubResponse(201, { csrf: 'issued-stamp', expiresAt: '2026-10-16T12:00:00Z' });
    await renderLogin(container);

    const secretInput = container.querySelector<HTMLInputElement>('input[name="passphrase"]');
    expect(secretInput?.type).toBe('password');
  });
});
