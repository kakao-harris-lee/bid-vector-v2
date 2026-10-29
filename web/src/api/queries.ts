import type { components, operations } from './generated/operator-api';

import { ApiError } from './apiError';
import { apiClient } from './client';

/**
 * 화면이 부르는 operation 의 **한 자리**(M7/7E-1). 하는 일은 셋이다: 생성 타입 클라이언트를
 * 부르고, 계약 헤더(`X-Total-Count`·`X-Result-State`)를 읽고, 비-2xx 를 [ApiError] 로 올린다.
 *
 * **손으로 적는 미러 층이 아니다**(ADR 0011 D-2) — 경로 문자열은 생성 타입(`paths`)이 검사하고
 * 요청·응답 모양을 다시 선언하지 않는다(전부 `components['schemas'][…]` 를 그대로 내보낸다).
 * 이 자리가 있는 이유는 ⓐ 헤더를 읽는 코드가 화면마다 흩어지지 않게 하고 ⓑ 화면 test 가
 * 네트워크 전역을 건드리지 않고 이 모듈만 바꿔치울 수 있게 하는 것이다.
 *
 * **이 slice 가 부르는 operation 은 계약의 목록 안뿐이다**(scope.md 「화면 B·C 가 쓰는
 * operation」). 목록 밖을 부르려면 이 파일에 줄이 생기므로 diff 에 드러난다.
 */
export type NoticeSummary = components['schemas']['NoticeSummary'];
export type SuggestedNotice = components['schemas']['SuggestedNotice'];
export type BidCase = components['schemas']['BidCase'];
export type BidTimelineEntry = components['schemas']['BidTimelineEntry'];
export type BidPriceRequestResponse = components['schemas']['BidPriceRequestResponse'];
export type SessionResponse = components['schemas']['SessionResponse'];

type NoticesResultState = operations['searchNotices']['responses']['200']['headers']['X-Result-State'];
type SuggestionsResultState = operations['listSuggestions']['responses']['200']['headers']['X-Result-State'];
export type NoticeSearchQuery = NonNullable<operations['searchNotices']['parameters']['query']>;

export { ApiError } from './apiError';

const HTTP_NOT_FOUND = 404;
const HTTP_UNAUTHORIZED = 401;

interface ErrorLike {
  readonly code?: string;
}

function raise(status: number, error: unknown): never {
  const code = typeof error === 'object' && error !== null ? ((error as ErrorLike).code ?? null) : null;
  throw new ApiError(status, code);
}

/**
 * 경로 칸의 건 id — **계약이 `type: string` 으로 선언한다**(`components.parameters.BidCaseId`)
 * 반면 본문의 `BidCase.caseId` 는 int64 다. 공개 인자는 본문 쪽(number)에 맞추고 경로에 넣는
 * 자리에서만 문자열로 적는다 — 호출자가 두 어휘를 알 필요가 없다.
 */
function pathCaseId(caseId: number): string {
  return String(caseId);
}

export interface Listed<Row, State> {
  readonly rows: readonly Row[];
  readonly total: number;
  readonly resultState: State;
}

function headerNumber(response: Response, name: string): number {
  const raw = response.headers.get(name);
  const parsed = raw === null ? Number.NaN : Number.parseInt(raw, 10);
  // 헤더가 없거나 숫자가 아니면 **0 으로 지어내지 않는다** — 계약이 required 로 선언한 값이
  // 없다는 것은 서버가 계약을 어겼다는 뜻이고, 화면이 그것을 덮으면 사라진다.
  if (Number.isNaN(parsed)) {
    throw new ApiError(response.status, 'CONTRACT_HEADER_MISSING');
  }
  return parsed;
}

function headerState<State extends string>(response: Response, name: string): State {
  const raw = response.headers.get(name);
  if (raw === null) {
    throw new ApiError(response.status, 'CONTRACT_HEADER_MISSING');
  }
  return raw as State;
}

/**
 * 로그인 — **무인증으로 열리는 유일한 operation**(계약 선언). 실패의 **모양은 하나**다(계정
 * 부재·불일치·잠금을 가르지 않는다, 7D 규율) — 그래서 401 은 `null` 이고 호출자는 사유를
 * 알 수 없다. 이 자리가 있는 이유는 **화면이 생성 클라이언트를 직접 쥐지 않게** 하는 것이다
 * (v M-3 의 집합 등식).
 */
export async function signIn(loginName: string, passphrase: string): Promise<SessionResponse | null> {
  const { data, error, response } = await apiClient.POST('/api/session', { body: { loginName, passphrase } });
  if (data === undefined) {
    if (response.status === HTTP_UNAUTHORIZED) {
      return null;
    }
    raise(response.status, error);
  }
  return data;
}

/**
 * 지금 내 세션 — **새로고침 뒤 CSRF 값을 다시 받는 자리**다(7A 가 알려진 제한으로 남긴 cr r2 L-1).
 * 값은 메모리에만 있어 새로고침하면 사라지고, 이 읽기가 쥔 쿠키에서 서버가 다시 유도해 준다.
 * 세션이 없으면 404 이고 그때는 `null` 이다 — 화면은 로그인으로 보낸다(값을 지어내지 않는다).
 *
 * 쓰기 미들웨어 안에서 네트워크를 타지 않는 이유: 숨은 호출이 생기면 「어느 요청이 세션을
 * 조회하는가」가 호출자에게 보이지 않는다. 기동 때 한 번 부르는 쪽이 그 자리를 드러낸다.
 */
export async function readSession(): Promise<SessionResponse | null> {
  const { data, error, response } = await apiClient.GET('/api/session');
  if (data === undefined) {
    if (response.status === HTTP_NOT_FOUND) {
      return null;
    }
    raise(response.status, error);
  }
  return data;
}

export async function searchNotices(query: NoticeSearchQuery): Promise<Listed<NoticeSummary, NoticesResultState>> {
  const { data, error, response } = await apiClient.GET('/api/notices', { params: { query } });
  if (data === undefined) {
    raise(response.status, error);
  }
  return {
    rows: data,
    total: headerNumber(response, 'X-Total-Count'),
    resultState: headerState<NoticesResultState>(response, 'X-Result-State'),
  };
}

export async function listSuggestions(): Promise<Listed<SuggestedNotice, SuggestionsResultState>> {
  const { data, error, response } = await apiClient.GET('/api/suggestions');
  if (data === undefined) {
    raise(response.status, error);
  }
  return {
    rows: data,
    total: headerNumber(response, 'X-Total-Count'),
    resultState: headerState<SuggestionsResultState>(response, 'X-Result-State'),
  };
}

export async function listBidCases(kind: BidCase['bidKind']): Promise<readonly BidCase[]> {
  const { data, error, response } = await apiClient.GET('/api/bid-cases', { params: { query: { kind } } });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function addBidCase(body: components['schemas']['AddBidCaseRequest']): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function requestBidPrice(
  body: components['schemas']['BidPriceRequestBody'],
): Promise<BidPriceRequestResponse> {
  const { data, error, response } = await apiClient.POST('/api/bid-price-requests', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function readBidCase(caseId: number): Promise<BidCase> {
  const { data, error, response } = await apiClient.GET('/api/bid-cases/{caseId}', {
    params: { path: { caseId: pathCaseId(caseId) } },
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function readTimeline(caseId: number): Promise<readonly BidTimelineEntry[]> {
  const { data, error, response } = await apiClient.GET('/api/bid-cases/{caseId}/timeline', {
    params: { path: { caseId: pathCaseId(caseId) } },
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function draftBid(
  caseId: number,
  body: components['schemas']['DraftBidRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/draft', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function recordBid(
  caseId: number,
  body: components['schemas']['RecordBidRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/record', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function deferCase(
  caseId: number,
  body: components['schemas']['DeferRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/defer', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function resumeCase(caseId: number): Promise<BidCase> {
  // 되돌림 전이는 **본문이 없다**(계약에 requestBody 가 없다) — 빈 object 를 보내면 415·400 이다.
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/resume', {
    params: { path: { caseId: pathCaseId(caseId) } },
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function skipCase(
  caseId: number,
  body: components['schemas']['SkipRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/skip', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}
