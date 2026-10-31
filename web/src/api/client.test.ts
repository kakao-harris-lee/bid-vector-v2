import { beforeEach, describe, expect, it } from 'vitest';

import { apiClient, hasCsrf, rememberCsrf } from './client';

const CSRF_HEADER = 'x-operator-csrf';
const NO_CONTENT = 204;

interface Capture {
  readonly method: string;
  readonly credentials: RequestCredentials;
  readonly csrf: string | null;
}

const captured: Capture[] = [];

/**
 * **전역을 바꿔치우지 않는다** — 요청마다 넘기는 `fetch` 옵션으로 가로챈다. 전역 대입은
 * 네트워크 표면 금지 lint 의 대상이고(그 층의 목적이 실수로 쓴 직접 호출을 잡는 것이다),
 * 이 자리에는 그 금지를 끌 이유가 없다. 미들웨어는 이 경로에서도 그대로 돈다.
 */
function stub(status: number, body: unknown): typeof globalThis.fetch {
  return (async (input: Request | string | URL) => {
    const request = input as Request;
    captured.push({
      method: request.method,
      credentials: request.credentials,
      csrf: request.headers.get(CSRF_HEADER),
    });
    // **204 는 본문을 가질 수 없다**(`Response` 생성자가 거부한다 — 실측). 상태 코드가 본문
    // 없음을 뜻하면 본문을 주지 않는다.
    return new Response(status === NO_CONTENT ? null : JSON.stringify(body), {
      status,
      headers: { 'content-type': 'application/json' },
    });
  }) as unknown as typeof globalThis.fetch;
}

const SESSION_BODY = { csrf: 'value', expiresAt: '2026-10-16T12:00:00Z' };

describe('생성 타입 클라이언트의 인증 설정 (7D)', () => {
  beforeEach(() => {
    captured.length = 0;
    rememberCsrf(null);
  });

  /**
   * **이 단언이 잠그는 것은 「include 가 아니다」다.** `same-origin` 은 브라우저와 Node 의
   * fetch 기본값이기도 하므로, 설정을 지우는 변이는 같은 값을 낸다(거동이 같다 — 쿠키는
   * 그래도 실린다). 위험한 방향은 그 반대다: `include` 로 바꾸면 교차 출처 요청에도 쿠키가
   * 실리고, 그 변이에서 이 test 가 RED 다(변이 실측 표 M12).
   */
  it('쿠키를 교차 출처로 보내지 않는다 — include 가 아니다', async () => {
    await apiClient.GET('/api/session', { fetch: stub(200, SESSION_BODY) });

    expect(captured).toHaveLength(1);
    expect(captured[0]?.credentials).toBe('same-origin');
  });

  it('읽기 메서드에는 CSRF 헤더를 붙이지 않는다 — 면제 집합', async () => {
    rememberCsrf('remembered-stamp');

    await apiClient.GET('/api/session', { fetch: stub(200, SESSION_BODY) });

    expect(captured[0]?.csrf).toBeNull();
  });

  /**
   * **M7/7E-1 r3 — 상태를 바꾸는 요청을 `POST` 로 잰다.** 앞 판은 `DELETE /api/session`(로그아웃)을
   * 썼는데, 클라이언트 타입이 **(메서드, 경로) 승인 쌍**으로 좁혀진 뒤 그 호출은 컴파일되지
   * 않는다(이 slice 의 화면이 로그아웃을 부르지 않는다 — 7E-2·3 이 그 쌍을 더할 때 되살린다).
   * 재는 성질은 그대로다: **면제 집합 밖 메서드에는 기억한 값이 붙는다.**
   */
  it('상태를 바꾸는 요청에는 기억한 CSRF 값을 붙인다', async () => {
    rememberCsrf('remembered-stamp');

    await apiClient.POST('/api/session', {
      body: { loginName: 'operator', passphrase: 'x' },
      fetch: stub(201, SESSION_BODY),
    });

    expect(captured[0]?.method).toBe('POST');
    expect(captured[0]?.csrf).toBe('remembered-stamp');
  });

  it('값이 없으면 지어 붙이지 않는다 — 서버가 거부한다(fail-closed)', async () => {
    expect(hasCsrf()).toBe(false);

    await apiClient.POST('/api/session', {
      body: { loginName: 'operator', passphrase: 'x' },
      fetch: stub(401, { code: 'UNAUTHENTICATED', message: '인증에 실패했다', correlationId: 'c' }),
    });

    expect(captured[0]?.csrf).toBeNull();
  });

  it('로그아웃 뒤에는 값을 잊는다', () => {
    rememberCsrf('remembered-stamp');
    expect(hasCsrf()).toBe(true);

    rememberCsrf(null);

    expect(hasCsrf()).toBe(false);
  });
});
