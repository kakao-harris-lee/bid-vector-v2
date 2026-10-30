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
export type StrategyReadResponse = components['schemas']['StrategyReadResponse'];
export type EditSessionResponse = components['schemas']['EditSessionResponse'];
export type Profile = components['schemas']['Profile'];
export type ConstructionCapacity = components['schemas']['ConstructionCapacity'];
export type OnboardingChecklist = components['schemas']['OnboardingChecklist'];
export type OpeningResultView = components['schemas']['OpeningResultView'];
export type NotificationItem = components['schemas']['NotificationItem'];
export type MarkReadResult = components['schemas']['MarkNotificationReadResponse'];

export type NotificationQuery = NonNullable<operations['listNotifications']['parameters']['query']>;

/**
 * 알림함 한 페이지 — 본문 행과 **두 계약 헤더**다. `Listed` 를 쓰지 않는 이유는 이 operation
 * 에 `X-Result-State` 가 없고 대신 `X-Unread-Count` 가 있기 때문이다(모양이 다른 것을 같은
 * 타입에 밀어 넣으면 없는 헤더를 읽는 자리가 생긴다).
 */
export interface NotificationPage {
  readonly rows: readonly NotificationItem[];
  /** 필터를 지난 전체 건수 — 페이지 길이와 다른 값이다. */
  readonly total: number;
  /** 계정의 안읽음 **전수** — 목록 필터와 무관하다(배지가 읽는 값). */
  readonly unread: number;
}

type NoticesResultState = operations['searchNotices']['responses']['200']['headers']['X-Result-State'];
type SuggestionsResultState = operations['listSuggestions']['responses']['200']['headers']['X-Result-State'];
export type NoticeSearchQuery = NonNullable<operations['searchNotices']['parameters']['query']>;

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

/* ── 화면 D 내 입찰함의 생애주기 전이 넷(M7/7E-2) ──────────────────────────────
   넷 다 응답이 `BidCase` 하나다 — 전이 뒤 상태를 **서버가 준 값으로** 받으므로 화면이
   「이제 이 상태일 것이다」를 지어내지 않는다. 어느 상태에서 어느 전이가 열리는지는 계약
   전이표의 몫이고 화면은 그 표와 같은 모양의 다음 행동 표를 든다. */

export async function awaitOpening(
  caseId: number,
  body: components['schemas']['AwaitOpeningRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/await-opening', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function recordOpening(
  caseId: number,
  body: components['schemas']['RecordOpeningRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/opening', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function enterQualificationReview(
  caseId: number,
  body: components['schemas']['QualificationReviewRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/qualification-review', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function recordOutcome(
  caseId: number,
  body: components['schemas']['RecordOutcomeRequest'],
): Promise<BidCase> {
  const { data, error, response } = await apiClient.POST('/api/bid-cases/{caseId}/outcome', {
    params: { path: { caseId: pathCaseId(caseId) } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/* ── 설정: 전략과 편집 세션(M7/7E-2) ──────────────────────────────────────────
   전략을 **직접 쓰는 operation 이 계약에 없다** — 모든 쓰기가 편집 세션을 지난다. 그래서
   이 묶음은 「현재 값 읽기 하나 + 세션 여섯」이고, 확인 화면이 보이는 값은 읽기가 준 현재
   전략과 운영자가 방금 적은 값 둘뿐이다(세션 응답에 초안 값이 없다 — 계약 주석). */

export async function readStrategy(): Promise<StrategyReadResponse> {
  const { data, error, response } = await apiClient.GET('/api/strategy');
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function beginEditSession(
  body: components['schemas']['EditSessionBeginRequest'],
): Promise<EditSessionResponse> {
  // 세션 id 는 **서버가 만든다**(계약 C-3) — 화면이 id 를 고르는 자리가 없다.
  const { data, error, response } = await apiClient.POST('/api/strategy/edit-sessions', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/**
 * 세션 하나의 지금 상태 — **만료를 서버가 접는다**(계약 D-6A2b-11). 화면이 `expiresAt` 을
 * 지금 시각과 비교해 「만료됐다」고 판정하면 그것은 상태를 클라이언트가 추론하는 것이다
 * (D-7E-4). 그래서 확인 화면으로 돌아오는 자리가 이 읽기를 지난다.
 */
export async function readEditSession(sessionId: string): Promise<EditSessionResponse> {
  const { data, error, response } = await apiClient.GET('/api/strategy/edit-sessions/{sessionId}', {
    params: { path: { sessionId } },
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function provideEditSessionValue(
  sessionId: string,
  body: components['schemas']['EditSessionValueRequest'],
): Promise<EditSessionResponse> {
  const { data, error, response } = await apiClient.POST('/api/strategy/edit-sessions/{sessionId}/value', {
    params: { path: { sessionId } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function confirmEditSession(
  sessionId: string,
  body: components['schemas']['EditSessionConfirmRequest'],
): Promise<EditSessionResponse> {
  const { data, error, response } = await apiClient.POST('/api/strategy/edit-sessions/{sessionId}/confirm', {
    params: { path: { sessionId } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function requestEditSessionEdit(
  sessionId: string,
  body: components['schemas']['EditSessionEditRequest'],
): Promise<EditSessionResponse> {
  const { data, error, response } = await apiClient.POST('/api/strategy/edit-sessions/{sessionId}/edit', {
    params: { path: { sessionId } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function cancelEditSession(
  sessionId: string,
  body: components['schemas']['EditSessionCancelRequest'],
): Promise<EditSessionResponse> {
  const { data, error, response } = await apiClient.POST('/api/strategy/edit-sessions/{sessionId}/cancel', {
    params: { path: { sessionId } },
    body,
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/* ── 설정: 프로필(M7/7E-2) ────────────────────────────────────────────────────
   자격 사실·사업 정보는 **절 단위 교체**라 응답이 프로필 한 벌이고, 시평액은 행 단위라
   응답이 **다시 읽은 목록**이다(계약). 둘 다 서버가 준 값을 그대로 화면에 싣는다. */

export async function readProfile(): Promise<Profile> {
  const { data, error, response } = await apiClient.GET('/api/profile');
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function replaceQualificationFacts(
  body: components['schemas']['QualificationFactsRequest'],
): Promise<Profile> {
  const { data, error, response } = await apiClient.POST('/api/profile/qualification-facts', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function replaceBusinessDetails(
  body: components['schemas']['BusinessDetailsRequest'],
): Promise<Profile> {
  const { data, error, response } = await apiClient.POST('/api/profile/business-details', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function listConstructionCapacities(): Promise<readonly ConstructionCapacity[]> {
  const { data, error, response } = await apiClient.GET('/api/profile/construction-capacities');
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function upsertConstructionCapacity(
  body: components['schemas']['ConstructionCapacityRequest'],
): Promise<readonly ConstructionCapacity[]> {
  const { data, error, response } = await apiClient.POST('/api/profile/construction-capacities', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

export async function removeConstructionCapacity(
  body: components['schemas']['RemoveConstructionCapacityRequest'],
): Promise<readonly ConstructionCapacity[]> {
  const { data, error, response } = await apiClient.POST('/api/profile/construction-capacities/remove', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/**
 * 시작하기 체크리스트(M7/7E-2b) — **항목별 진행 상태를 서버가 판정한다**(7C④-3). 응답은 세
 * 목록이고 「해당 없음」은 그 여집합이다. 화면은 프로필·전략 값에서 완료를 추론하지 않는다.
 */
export async function readOnboardingChecklist(): Promise<OnboardingChecklist> {
  const { data, error, response } = await apiClient.GET('/api/onboarding-checklist');
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/* ── 화면 E 결과 확인 · 알림함(M7/7E-3a) ──────────────────────────────────────
   넷 다 읽기이거나 읽음 전이다 — E 는 결과 단계 전이를 부르지 않는다(D-7E3a-8). */

/**
 * 공고 하나의 개찰 결과(SET-02 표시 절반). **답은 `state` 가 가르는 아홉 갈래 중 하나**이고
 * 「모른다」도 200 이다 — 공고 부재가 404 가 아니라 `NOTICE_ABSENT` 상태다(계약 D-7C5-9).
 * 그래서 이 자리에 부재 처리가 없다.
 *
 * 번호·차수가 **둘 다 필수**다 — 차수를 기본값으로 접지 않는다.
 */
export async function readOpeningResult(
  noticeNumber: string,
  noticeRound: string,
): Promise<OpeningResultView> {
  const { data, error, response } = await apiClient.GET('/api/opening-results', {
    params: { query: { noticeNumber, noticeRound } },
  });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/**
 * 알림함 한 페이지. **안읽음 전수는 본문이 아니라 헤더에 있다**(D-7E3a-6) — `X-Unread-Count`
 * 는 `unreadOnly` 필터와 무관한 계정 전수라 배지가 목록 필터에 흔들리지 않는다. 본문 행을
 * 세어 배지를 만들면 그 보증이 사라진다(한 페이지만 보고 전체를 말하게 된다).
 *
 * 두 헤더가 없으면 `headerNumber` 가 `CONTRACT_HEADER_MISSING` 으로 올린다 — 0 으로 지어내지
 * 않는다(계약이 required 로 선언한 값이 없다는 것은 서버가 계약을 어겼다는 뜻이다).
 */
export async function listNotifications(query: NotificationQuery = {}): Promise<NotificationPage> {
  const { data, error, response } = await apiClient.GET('/api/notifications', { params: { query } });
  if (data === undefined) {
    raise(response.status, error);
  }
  return {
    rows: data,
    total: headerNumber(response, 'X-Total-Count'),
    unread: headerNumber(response, 'X-Unread-Count'),
  };
}

/**
 * 항목 하나를 읽음으로. **신원은 종류와 공고 쌍**이다 — 합성 id 가 없다(계약). 멱등이고,
 * 이미 읽었거나 없거나 남의 것이면 `markedCount` 가 0 이다(셋을 가르지 않는다).
 */
export async function markNotificationRead(
  body: components['schemas']['MarkNotificationReadRequest'],
): Promise<MarkReadResult> {
  const { data, error, response } = await apiClient.POST('/api/notifications/read', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}

/** 한 공고 묶음의 **안읽음 전부**를 읽음으로. 단건 경로와 갈라 둔 것이 계약의 모양이다. */
export async function markNoticeNotificationsRead(
  body: components['schemas']['MarkNoticeNotificationsReadRequest'],
): Promise<MarkReadResult> {
  const { data, error, response } = await apiClient.POST('/api/notifications/read-notice', { body });
  if (data === undefined) {
    raise(response.status, error);
  }
  return data;
}
