import createClient from 'openapi-fetch';

import type { paths } from './generated/operator-api';

/**
 * 서버를 부르는 **유일한** 자리(ADR 0011 D-2·D-3).
 *
 * 타입 인자가 생성 타입(`paths`)이라, 계약에 없는 operation·경로·요청/응답 모양을 부르면
 * 컴파일되지 않는다. 경로 문자열을 손으로 적는 fetch 래퍼를 두지 않는다 — 그것이 legacy 가
 * 생성 타입 위에 수작성 미러 층을 쌓아 출처를 둘로 만든 자리다(조사 §2).
 *
 * 번들에는 `VITE_` 접두 공개 설정만 들어간다(ADR 0011 D-6). 인증은 7D 가 정한다 — 여기서
 * 자격증명을 싣지 않는다.
 */
export const apiClient = createClient<paths>({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/',
});
