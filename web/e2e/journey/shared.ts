import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

import { expect, type Page, type Request } from '@playwright/test';

import { APPROVED_OPERATIONS } from '../../src/api/approvedOperations';

/**
 * 여정 e2e 의 **공통 자리**(M7/7E-2). 여정이 두 파일로 갈리면서 이것을 뽑았다 — 복제하면
 * 갈린다: 7E-1 이 승인 목록을 spec 에 복제했다가 r2 에서 세 쌍을 지웠을 때 **한 자리만 고쳐져**
 * 지운 쌍이 여전히 허용 목록에 있었다(실측). 그래서 요청 감시·승인 집합 파생·로그인·문면 표를
 * 한 모듈이 든다.
 *
 * `.spec.ts` 가 **아니다** — Playwright 의 `testMatch` 가 그 꼴만 수집하므로 이 파일은 test 로
 * 집히지 않는다.
 */
interface JourneyContext {
  readonly previewUrl: string;
  readonly loginName: string;
  readonly passphrase: string;
  /** 이 실행의 번들이 낸 자산 경로 전수 — 하네스가 빌드 직후 적는다(v r3 M-2). */
  readonly bundleAssets: readonly string[];
}

const CONTEXT_PATH = resolve(tmpdir(), 'bidvector-7e1-journey-context.json');
let cached: JourneyContext | undefined;

/**
 * **지연 읽기**다(OQ-2). 모듈 적재 때 읽으면 하네스가 뜨지 않은 상태에서 **수집 자체가**
 * 실패한다 — `--list` 로 「이 설정이 무엇을 수집하는가」를 세는 길이 막히고, 수집 단계의 실패와
 * test 의 실패가 같은 모양이 된다. 함수로 미루면 없는 파일은 **test 의 실패**다.
 */
export function journeyContext(): JourneyContext {
  cached ??= parseContext(readFileSync(CONTEXT_PATH, 'utf8'));
  return cached;
}

/**
 * 맥락 파일의 **모양을 한 번 본다**(CR r4 L-9). 앞 판은 검증 없이 캐스팅해서, 이 라운드 전의
 * 옛 꼴 파일이 남아 있으면 `bundleAssets` 가 `undefined` 이고 뒤에서 `TypeError` 로 터졌다 —
 * 멈추는 방향이라 안전하지만 **사유를 말하지 못한다**. 같은 파일의 다른 입구(`parseLockMarker`)와
 * 같은 꼴로 사유를 적는다.
 */
function parseContext(raw: string): JourneyContext {
  const parsed: unknown = JSON.parse(raw);
  if (typeof parsed !== 'object' || parsed === null) {
    throw new Error(`하네스 맥락이 객체가 아니다(${CONTEXT_PATH})`);
  }
  const candidate = parsed as Partial<JourneyContext>;
  const missing = (
    [
      ['previewUrl', typeof candidate.previewUrl === 'string'],
      ['loginName', typeof candidate.loginName === 'string'],
      ['passphrase', typeof candidate.passphrase === 'string'],
      ['bundleAssets', Array.isArray(candidate.bundleAssets)],
    ] as const
  )
    .filter(([, ok]) => !ok)
    .map(([key]) => key);
  if (missing.length > 0) {
    throw new Error(
      `하네스 맥락에 ${missing.join(', ')} 이(가) 없다(${CONTEXT_PATH}) — 옛 꼴 파일이 남았거나 ` +
        '하네스가 그 자리까지 가지 못했다. 그 파일을 지우고 다시 돌리십시오.',
    );
  }
  return candidate as JourneyContext;
}

export function sameOrigin(): string {
  return new URL(journeyContext().previewUrl).origin;
}

/** 화면의 「값 없음」 문면 — null 인 축은 이 문면과 대조한다. */
export const ABSENT = '값 없음';

export interface NoticeRow {
  readonly noticeNumber: string;
  readonly noticeRound: string;
  readonly status: string;
  readonly title: string | null;
  readonly demandAgencyName: string | null;
  readonly noticeAgencyName: string | null;
  readonly baseAmountWon: number | null;
  readonly baseAmountBasis: string | null;
  readonly baseAmountProvenance: string | null;
}

export interface BidCaseRow {
  readonly caseId: number;
  readonly noticeNumber: string;
  readonly noticeRound: string;
  readonly state: string;
  readonly caseVersion: number;
  readonly nextCheckpointKind: string;
  readonly nextCheckpointAt: string | null;
  readonly overdue: boolean;
}

export interface PriceBody {
  readonly currentActiveBids: number;
  readonly maxActiveBids: number;
  readonly bidPriceMenuState: string;
  readonly floorRateGroundsState: string;
  readonly publishedFloorRateFraction: string | null;
  readonly licenseVerdict: string;
  readonly licenseValidity: string;
  readonly unjudgedAxes: readonly string[];
  readonly disclosures: readonly string[];
}

/**
 * 계약의 상태 → 화면 문면. **spec 이 문면을 지어내지 않고** 표에서 찾는다 — 화면의 표와 같은
 * 어휘이고, 화면이 그 문면을 바꾸면 이 대조가 붉어진다.
 */
export const NOTICE_STATUS_LABEL: Readonly<Record<string, string>> = {
  Open: '입찰 중',
  Renoticed: '재공고',
  Closed: '마감',
  Awarded: '낙찰자 결정',
  Failed: '유찰',
  Cancelled: '취소',
};
export const CASE_STATE_LABEL: Readonly<Record<string, string>> = {
  WATCHING: '관심',
  PRICE_RECEIVED: '투찰가 받음',
  BID_PLANNED: '투찰 예정',
  BID_RECORDED: '투찰 기록됨',
  AWAITING_OPENING: '개찰 대기',
};

/**
 * 상태 → **화면 D 의 탭 문면**(M7/7E-2). 화면의 표를 import 하지 않고 spec 이 자기 몫을
 * 적는다 — 같은 출처에서 양쪽을 끌어오면 표가 틀려도 대조가 초록이다(`CASE_STATE_LABEL` 과
 * 같은 이유).
 */
export const CASE_TAB_LABEL: Readonly<Record<string, string>> = {
  WATCHING: '관심',
  DEFERRED: '관심',
  PRICE_RECEIVED: '투찰가 받음',
  BID_PLANNED: '투찰가 받음',
  BID_RECORDED: '투찰함',
  AWAITING_OPENING: '개찰 대기',
  OPENED_PROVISIONAL_FIRST: '심사 중',
  OPENED_OUT_OF_RANK: '심사 중',
  UNDER_QUALIFICATION_REVIEW: '심사 중',
  WON: '종료',
  LOST: '종료',
  FAILED_OR_REBID: '종료',
  SKIPPED: '종료',
  DEADLINE_PASSED_UNRECORDED: '종료',
};
export const CHECKPOINT_LABEL: Readonly<Record<string, string>> = {
  DEADLINE: '투찰 마감',
  OPENING: '개찰',
  NONE: '없음',
};
export const UNJUDGED_LABEL: Readonly<Record<string, string>> = {
  QUAL_08_REGION: '지역',
  QUAL_10_PERFORMANCE: '수행실적',
  QUAL_11_CONSTRUCTION_CAPACITY: '시공능력평가금액',
};

/**
 * 같은 출처의 **비 API 요청**도 아는 종류만 받는다(v r2 M-2). 앞 판은 `/api/` 밖 요청을 아예
 * 기록하지 않았고, 그래서 화면이 `new URL('./rows.txt', import.meta.url)` 로 내보낸 자산을
 * 읽어 가도 「V2 API 밖」 감시가 그것을 보지 못했다(실측).
 */
/** 라우터가 **문서**로 받는 경로 — 화면의 주소다(자산이 아니다). */
export const ALLOWED_ROUTES = [
  /^\/$/u,
  /^\/login$/u,
  /^\/notices\//u,
  /^\/bids$/u,
  /^\/settings$/u,
  /** 화면 A 시작하기(M7/7E-2b). */
  /^\/start$/u,
];

/**
 * 자산은 **이 빌드가 실제로 낸 파일 이름만** 받는다(v r3 M-2 · CR r3 M-4).
 *
 * **이 층이 사는 값을 정확히 적는다**(CR r4 L-5): 허용 집합은 하네스가 그 빌드에서 읽은 목록이라
 * 자산이 늘면 양쪽이 함께 는다 — 「번들이 커지면 먼저 깨진다」는 **이 단언의 성질이 아니다**.
 * 그 축은 하네스가 진다(산출물 모양을 {js 1 · css 1} 로 못박고, 늘면 거기서 멈춘다). 여기서
 * 잡는 것은 **이 빌드가 내지 않은** 같은 출처 요청이다: `public/` 복사물 · 손으로 적은 경로 ·
 * 묵은 자산. 번들 안으로 들어온 데이터는 자산 술어(`importedCss`)와 `import.meta` 금지가 막는다.
 */
export function bundleAssetPaths(): Set<string> {
  const names = journeyContext().bundleAssets;
  expect(names.length, '번들 산출물 목록이 비면 아래 단언이 공허해진다').toBeGreaterThan(0);
  return new Set(names);
}

/** 브라우저가 스스로 거는 요청 — 화면 코드가 부르는 것이 아니다(오늘 404 다). */
export const BROWSER_INITIATED = ['/favicon.ico'];

/** 화면이 **V2 API 밖**을 부르지 않는지, **목록 안**만 부르는지, 자산도 아는 종류인지 센다. */
export function watchRequests(page: Page): {
  readonly outside: string[];
  readonly apiPaths: Set<string>;
  readonly unexpectedSameOrigin: string[];
} {
  const allowedAssets = bundleAssetPaths();
  const outside: string[] = [];
  const apiPaths = new Set<string>();
  const unexpectedSameOrigin: string[] = [];
  page.on('request', (request: Request) => {
    const url = new URL(request.url());
    if (url.origin !== sameOrigin()) {
      outside.push(request.url());
      return;
    }
    if (url.pathname.startsWith('/api/')) {
      apiPaths.add(`${request.method()} ${url.pathname}`);
      return;
    }
    const known =
      ALLOWED_ROUTES.some((allowed) => allowed.test(url.pathname)) ||
      allowedAssets.has(url.pathname) ||
      BROWSER_INITIATED.includes(url.pathname);
    if (!known) {
      unexpectedSameOrigin.push(url.pathname);
    }
  });
  return { outside, apiPaths, unexpectedSameOrigin };
}

/**
 * 런타임 대조 집합을 **승인 목록에서 파생**한다(v r3 M-1). 앞 판은 같은 목록을 이 파일에
 * 복제했고, r2 에서 세 쌍을 지웠을 때 **여기만 남아** 두 자리가 갈렸다(실측: 지운 셋이 여전히
 * 허용 목록에 있었다). 한 자리에서 파생하면 그 갈림이 생기지 않는다.
 */
export const ALLOWED_API = new Set(APPROVED_OPERATIONS.map((one) => `${one.method} ${one.path}`));

/**
 * 런타임 경로를 계약의 템플릿 꼴로 접는다. **세션 id 도 접는다**(M7/7E-2): 편집 세션의 id 는
 * 서버가 만든 UUID 라 접지 않으면 그 요청이 늘 「목록 밖」으로 세어진다.
 */
export function templated(methodAndPath: string): string {
  return methodAndPath
    .replace(/\/api\/bid-cases\/\d+/u, '/api/bid-cases/{caseId}')
    .replace(/\/api\/strategy\/edit-sessions\/[^/]+/u, '/api/strategy/edit-sessions/{sessionId}');
}

export function assertOnlyAllowedOperations(apiPaths: Set<string>): void {
  const outside = [...apiPaths].map(templated).filter((one) => !ALLOWED_API.has(one));
  expect(outside, '화면이 계약 목록 밖의 operation 을 불렀다').toStrictEqual([]);
}

export async function signIn(page: Page): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name="loginName"]', journeyContext().loginName);
  await page.fill('input[name="passphrase"]', journeyContext().passphrase);
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/api/session') && response.request().method() === 'POST',
    ),
    page.getByRole('button', { name: '로그인' }).click(),
  ]);
  await expect(page.getByTestId('screen-home')).toBeVisible();
}

/**
 * 전략의 어휘 칸 하나를 **비운다** — 여정이 남긴 전역 상태를 되돌리는 자리다(M7/7E-2b).
 *
 * 화면이 아니라 **출하 API 로** 되돌린다: 실패 뒤 페이지가 어디에 있는지 알 수 없고, 되돌림은
 * 화면의 능력이 아니라 **운영자 설정 행위**다(하네스가 전략을 심는 D-7E-8 과 같은 자리). 브라우저
 * 세션 쿠키를 그대로 쓰므로 자격 값을 spec 이 들고 있지 않아도 된다.
 *
 * **멱등하다**: 이미 비어 있으면 아무것도 쓰지 않는다. 결과는 **세 갈래**다(CR r1 LOW) —
 * 되돌렸다 · 되돌릴 세션이 없다 · 되돌리지 못했다. 셋을 한 값으로 접으면 teardown 이
 * **원래 실패를 자기 실패로 덮는다**: `signIn` 전에 test 가 죽으면 이 함수의 첫 읽기가 401 인데,
 * 그것은 「되돌리지 못했다」가 아니라 「되돌릴 것이 없다」다.
 */
export type RevertOutcome =
  | { readonly kind: 'reverted' }
  /** 세션이 없다(로그인 전에 죽었다) — 전역 상태를 바꿀 기회도 없었다. */
  | { readonly kind: 'no-session'; readonly why: string }
  | { readonly kind: 'blocked'; readonly why: string };

const HTTP_UNAUTHORIZED_STATUS = 401;

export async function clearStrategyTerms(
  page: Page,
  field: string,
  currentTermsOf: (strategy: Record<string, unknown>) => readonly string[],
): Promise<RevertOutcome> {
  const read = await page.request.get('/api/strategy');
  if (read.status() === HTTP_UNAUTHORIZED_STATUS) {
    return { kind: 'no-session', why: '세션이 없어 되돌릴 것도 없다(GET /api/strategy 401)' };
  }
  if (!read.ok()) {
    return { kind: 'blocked', why: `전략을 읽지 못해 되돌릴 수 없었다(GET /api/strategy ${read.status()})` };
  }
  const strategy = (await read.json()) as Record<string, unknown> & { readonly revision: number };
  if (currentTermsOf(strategy).length === 0) {
    return { kind: 'reverted' };
  }
  const session = (await (await page.request.get('/api/session')).json()) as { readonly csrf: string };
  const headers = { 'X-Operator-Csrf': session.csrf };
  const begin = await page.request.post('/api/strategy/edit-sessions', { headers, data: { field } });
  if (!begin.ok()) {
    return { kind: 'blocked', why: `편집 세션을 열지 못해 되돌릴 수 없었다(POST edit-sessions ${begin.status()})` };
  }
  const { sessionId } = (await begin.json()) as { readonly sessionId?: string };
  if (sessionId === undefined) {
    return { kind: 'blocked', why: '편집 세션 응답에 sessionId 가 없어 되돌릴 수 없었다' };
  }
  const stamp = Date.now();
  const close = async (): Promise<void> => {
    // 적용이 안 됐으면 **세션을 닫아** 다음 실행의 `begin` 을 막지 않는다.
    await page.request.post(`/api/strategy/edit-sessions/${sessionId}/cancel`, {
      headers,
      data: { commandId: `teardown-cancel-${stamp}` },
    });
  };
  // **값 단계의 응답을 본다**(CR r1 LOW): 안 보면 그 거절이 다음 단계의
  // `INVALID_TRANSITION` 으로 나타나 「어느 단계가 막혔는가」가 한 칸 밀린다.
  const valued = await page.request.post(`/api/strategy/edit-sessions/${sessionId}/value`, {
    headers,
    data: { commandId: `teardown-value-${stamp}`, field, terms: [] },
  });
  if (!valued.ok()) {
    await close();
    return { kind: 'blocked', why: `되돌림 값이 거부됐다(value ${valued.status()})` };
  }
  const confirmed = await page.request.post(`/api/strategy/edit-sessions/${sessionId}/confirm`, {
    headers,
    data: { commandId: `teardown-confirm-${stamp}`, seenRevision: strategy.revision },
  });
  if (!confirmed.ok()) {
    await close();
    return { kind: 'blocked', why: `되돌림 적용이 거부됐다(confirm ${confirmed.status()})` };
  }
  return { kind: 'reverted' };
}

/** `datetime-local` 이 받는 꼴(`YYYY-MM-DDTHH:mm`, **지역 시각**) — 시각을 조작하지 않고 지금에서 센다. */
export function localDateTimeInput(daysFromNow: number): string {
  const when = new Date(Date.now() + daysFromNow * 86_400_000);
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${when.getFullYear()}-${pad(when.getMonth() + 1)}-${pad(when.getDate())}T${pad(when.getHours())}:${pad(
    when.getMinutes(),
  )}`;
}

