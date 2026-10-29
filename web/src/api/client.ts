import createClient from 'openapi-fetch';

import type { ApprovedPaths } from './approvedOperations';

/**
 * 생성 타입 클라이언트를 만드는 **승인된 자리**(ADR 0011 D-2·D-3) — 게이트가 증명하는 것은
 * 「클라이언트 패키지를 끌어오는 자리 == 이 파일」까지다. 「서버를 부르는 경로가 이것 하나뿐」은
 * 보조 lint 와 리뷰 소관이다(7A 검토 r5).
 *
 * 타입 인자가 생성 타입(`paths`)이라, 계약에 없는 operation·경로·요청/응답 모양을 부르면
 * 컴파일되지 않는다. 경로 문자열을 손으로 적는 fetch 래퍼를 두지 않는다 — 그것이 legacy 가
 * 생성 타입 위에 수작성 미러 층을 쌓아 출처를 둘로 만든 자리다(조사 §2).
 *
 * 번들에는 `VITE_` 접두 공개 설정만 들어간다(ADR 0011 D-6).
 *
 * **인증은 7D 가 정했다 — 이 파일이 그 두 축을 든다.**
 *  · **쿠키**: `credentials: 'same-origin'`. 세션 쿠키는 `HttpOnly` 라 스크립트가 읽지 못하고
 *    브라우저가 싣는다. **이 값은 브라우저 기본값과 같다** — 그러니 이 줄이 하는 일은 「싣기를
 *    켜는 것」이 아니라 **그 기본값을 명시해 못박는 것**이다. `'include'` 를 쓰지 않는다: 이
 *    화면과 API 는 같은 출처에서 선다(7F 의 반입 형태)이고, 교차 출처로 쿠키를 보내는 설정은
 *    그 전제가 깨지는 날 조용히 자격을 밖으로 내보낸다(그 변이에서 test 가 RED 다).
 *  · **CSRF**: 상태를 바꾸는 요청에만 헤더를 붙인다. 값은 **메모리에만** 둔다 — `localStorage`
 *    에 두면 XSS 가 그 값을 영구히 읽을 수 있고, 세션 쿠키가 `HttpOnly` 인 이득이 줄어든다.
 *    **새로고침하면 그 값이 사라진다** — 그리고 지금 web 에는 그것을 다시 받는 호출이 **없다**
 *    (code-review r2 L-1). 서버 쪽은 서 있다: `GET /api/session` 이 쥔 쿠키에서 stamp 를 다시
 *    유도해 돌려주고 GET 은 CSRF 면제다. 붙이는 것은 **첫 쓰기 화면을 들이는 slice(7E)** 의
 *    일이다 — 지금 상태 변경을 하는 화면이 0 이라 깨지는 것이 없고, 그때가 되면 이 모듈에
 *    `ensureCsrf()`(없으면 조회 → 기억) 하나를 두고 `onRequest` 가 그것을 기다리면 된다.
 *    알려진 제한으로 등재돼 있다.
 */
/**
 * **타입이 승인 목록으로 좁혀져 있다**(CR r2 M-1 · v r3 M-1). 전체 `paths` 를 주면 계약의 어떤
 * operation 이든 부를 수 있어서, 이 객체를 재수출·동적 import·별칭 어느 꼴로 들고 나가도 목록
 * 밖 호출이 컴파일된다(실측 다섯 꼴). 좁히면 **그 키가 타입에 없으므로** 어느 꼴로 들고 나가도
 * `tsc` 가 멈춘다.
 *
 * 좁히는 단위는 **(메서드, 경로) 쌍**이다 — 경로만 좁히면 같은 경로의 미승인 메서드가 열린다
 * (실측: 읽기만 쓰는 `/api/session` 에 `DELETE` 가 지나갔다). 남는 것은 `as unknown` 캐스팅뿐이고
 * 그 자리는 여정의 런타임 대조 몫이다(알려진 제한).
 */
export const apiClient = createClient<ApprovedPaths>({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/',
  credentials: 'same-origin',
});

/** 서버의 면제 집합과 **같은 셋**이다(`BrowserSessionAuthenticator.CSRF_EXEMPT_METHODS`). */
const CSRF_EXEMPT_METHODS: ReadonlySet<string> = new Set(['GET', 'HEAD', 'OPTIONS']);

/** 서버가 요구하는 헤더 이름(`BrowserSessionAuthenticator.CSRF_HEADER`). */
const CSRF_HEADER = 'X-Operator-Csrf';

let csrfValue: string | null = null;

/**
 * 로그인·세션 조회가 받은 CSRF 값을 기억한다(로그아웃 뒤에는 `null`). 이 모듈 밖에 값을
 * 두지 않는 이유는 위 주석의 두 번째 축이다.
 */
export function rememberCsrf(value: string | null): void {
  csrfValue = value;
}

/** 화면·test 가 「지금 값이 있는가」를 볼 수 있게 한다 — 값 자체는 요청에만 실린다. */
export function hasCsrf(): boolean {
  return csrfValue !== null;
}

apiClient.use({
  onRequest({ request }) {
    if (CSRF_EXEMPT_METHODS.has(request.method)) {
      return request;
    }
    if (csrfValue === null) {
      // 값이 없으면 **붙이지 않는다** — 서버가 거부한다(fail-closed). 화면이 임의의 값을
      // 지어 붙이면 그 요청은 거부 대신 「틀린 값」이 되어 사유가 흐려진다.
      return request;
    }
    request.headers.set(CSRF_HEADER, csrfValue);
    return request;
  },
});
