import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

import { expect, type Locator, type Page, type Request, type Response } from '@playwright/test';

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
/**
 * 개찰 결과 갈래 하나의 설계(M9/opening-harness) — **하네스가 적고 spec 이 읽는다.** spec 에
 * 베껴 적으면 설계와 기대가 사본 둘이 되고 한쪽만 고쳐진다(이 파일 머리 주석의 승인 목록 선례).
 */
export interface OpeningAxisDesign {
  readonly noticeNumber: string;
  readonly expectedOpeningState: string;
  /** 잠정 1위 축의 기대(cr MEDIUM-1) — 그 축을 응답에서 읽어 되비추지 않으려는 자리다. */
  readonly expectedProvisionalState: string;
  /** 그 공고가 **왜** 그 갈래인가 — 붉어질 때 설계 의도를 읽는 사람에게 함께 준다. */
  readonly why: string;
}

interface JourneyContext {
  readonly previewUrl: string;
  readonly loginName: string;
  readonly passphrase: string;
  /** 이 실행의 번들이 낸 자산 경로 전수 — 하네스가 빌드 직후 적는다(v r3 M-2). */
  readonly bundleAssets: readonly string[];
  /** 개찰 결과 상태 아홉을 낳는 공고 설계표 — 하네스의 수집·승격 걸음이 그것을 세운다. */
  readonly openingAxis: readonly OpeningAxisDesign[];
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
      // **빈 목록도 없는 것으로 본다**: 설계표가 비면 아홉 갈래 등식이 「전부 통과」와 「아무것도
      // 재지 않았다」를 같은 값으로 낸다(여정 레인 판정의 ⓒ 축과 같은 사유).
      ['openingAxis', Array.isArray(candidate.openingAxis) && candidate.openingAxis.length > 0],
      // **원소 모양까지 본다**(cr LOW): 배열성만 보면 망가진 원소가 「설계: undefined」로 나타나고
      // 그 붉음은 원인을 말하지 않는다.
      [
        'openingAxis 원소(noticeNumber·expectedOpeningState·expectedProvisionalState·why)',
        Array.isArray(candidate.openingAxis) &&
          candidate.openingAxis.every(
            (one) =>
              typeof one?.noticeNumber === 'string' &&
              typeof one?.expectedOpeningState === 'string' &&
              typeof one?.expectedProvisionalState === 'string' &&
              typeof one?.why === 'string',
          ),
      ],
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

/**
 * 화면의 「값 없음」 문면 — null 인 축은 이 문면과 대조한다. **시각 칸도 같은 말이다**
 * (M9/screen-display D-1 갱신 2026-11-05) — 그래서 이름이 둘이 아니다.
 */
export const ABSENT = '값 없음';

/**
 * 화면이 보일 **KST 표기의 독립 기댓값**(M9 D-1 — `YYYY-MM-DD HH:mm`, 초 이하 절삭).
 *
 * **산출물의 함수를 부르지 않는다**: 양쪽을 같은 자리에서 끌어오면 그 자리가 틀려도 대조가
 * 초록이다(저장소 선례 — 7E-3b r2 CR L-1 의 「같은 문면을 손으로 적는 변이가 667 passed」).
 * 그래서 여기서는 **다른 기계**를 쓴다: 산출물은 고정 오프셋 산술이고 이 oracle 은 시간대
 * 자료(`Intl`)다. 둘이 같은 글자를 내는 것은 둘 다 맞을 때뿐이다.
 */
export function expectedKstText(iso: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(iso));
  const read = (type: Intl.DateTimeFormatPartTypes): string => {
    const found = parts.find((one) => one.type === type);
    if (found === undefined) {
      throw new Error(`Intl 이 ${type} 칸을 내지 않았다 — 러너의 시간대 자료를 확인하십시오.`);
    }
    return found.value;
  };
  return `${read('year')}-${read('month')}-${read('day')} ${read('hour')}:${read('minute')}`;
}

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

/**
 * 타임라인 한 줄 — spec 이 쓰는 두 칸.
 *
 * `caseVersion` 은 그 전이가 낳은 **건의 다음 버전**이고 목록은 그 값으로 오름차순이다(어댑터가
 * 전이 행에 `expectedVersion + 1` 을 적고 조회가 `ORDER BY case_version` 이다). 그래서 **마지막
 * 줄의 그 값이 그 쓰기 응답의 `caseVersion` 과 같다** — 늦은 응답을 집으면 그 등식이 깨진다.
 */
export interface TimelineRow {
  readonly toState: string;
  readonly caseVersion: number;
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
  // 결과 단계 여섯(M7/7E-3a) — 여정 ⑤ 가 이 문면으로 화면 E·D 를 대조한다.
  OPENED_PROVISIONAL_FIRST: '잠정 1위',
  OPENED_OUT_OF_RANK: '순위 밖',
  UNDER_QUALIFICATION_REVIEW: '적격심사 중',
  WON: '낙찰',
  LOST: '패찰',
  FAILED_OR_REBID: '유찰·재공고',
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

/**
 * 금액 부속 토큰의 문면(M9/screen-display D-3) — 앞 판은 여정이 **원문 토큰**을 카드에서
 * 찾았다(`BASE_AMOUNT`·`PUBLISHED`). 다른 라벨 표와 같은 관례로 **축어로** 적는다: 산출물의
 * 표에서 끌어오면 그 표가 틀려도 대조가 초록이다.
 */
export const AMOUNT_BASIS_LABEL: Readonly<Record<string, string>> = {
  BASE_AMOUNT: '기초금액',
  ESTIMATED: '추정가격',
  YEGA: '예정가격',
  BID: '투찰금액',
  ALLOCATED_BUDGET: '배정예산',
  AWARD: '낙찰금액',
};
/** 통화의 단위 문면 — 금액 글자에 붙는다(M9 D-3 · r1 H-1). */
export const AMOUNT_CURRENCY_LABEL: Readonly<Record<string, string>> = {
  KRW: '원',
};
export const AMOUNT_PROVENANCE_LABEL: Readonly<Record<string, string>> = {
  PUBLISHED: '공고 게시값',
  DERIVED_FROM_OPENING: '개찰 역산값',
  FILLED_FROM_BUDGET_KEY: '예산 항목에서 채운 값',
  COPIED_FROM_BASE_AMOUNT: '기초금액에서 복사한 값',
  OPERATOR_DECLARED: '운영자 선언값',
  UNDECLARED: '출처 미선언',
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
  /** 화면 E 결과 확인 · 알림함(M7/7E-3a). */
  /^\/results$/u,
  /^\/inbox$/u,
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

/** 쓰기 하나와 **그 쓰기 뒤에 출발한** 다시 읽기. 쓰기 상태는 [readsAfterWrite] 가 단언했다. */
export interface WriteThenRead {
  readonly write: Promise<Response>;
  readonly read: Promise<Response>;
}

export interface ReadsAfterWrite {
  /**
   * 그 쓰기의 응답 **뒤에 출발한** 첫 200 읽기. 다시 읽기가 **없으면** 이 기다림은 닫히지 않고
   * 타임아웃이다 — 낡은 값으로 초록이 되는 것보다 낫다(PR #118 CR r1 L-2). 그때 볼 자리는
   * 그 쓰기의 `invalidateQueries` 다.
   */
  readonly afterWrite: (
    isWrite: (one: Request) => boolean,
    label: string,
    /** 그 쓰기가 성공으로 받는 상태 — 담기는 생성이라 201 도 성공이다(계약). */
    okStatuses?: readonly number[],
  ) => WriteThenRead;
  /** 요청 장부를 걷는다 — 한 여정이 이 자리를 여러 번 세울 때 청취자를 쌓지 않으려는 것이다. */
  readonly stop: () => void;
}

/**
 * **읽기의 출발 장부** — 「쓰기 뒤 다시 읽기」를 기다리는 자리가 이것으로 **그 쓰기 응답 뒤에
 * 출발한** 읽기만 받는다.
 *
 * 「경로 + 메서드 + 200」 포착으로는 닫히지 않는다(PR #118 v r1 H-1 · PR #157 첫 CI). 화면은 쓰기가
 * 성공한 뒤에만 다시 읽지만(`invalidateQueries`), **쓰기 전에 출발한** 읽기의 응답이 그 뒤에 닿을
 * 수 있다 — 한 화면이 질의 둘을 순차로 다시 읽으면 앞 질의의 단언이 서는 시점에 뒤 질의의 앞
 * 라운드 응답이 아직 날아오는 중이다. 그 늦은 응답이 포착되면 단언은 쓰기 **전** 상태를 기준으로
 * 화면을 보고, 화면은 이미 쓰기 뒤를 그려 어긋난다(실측 서명: 화면 3 ↔ 응답 2).
 *
 * 취소로는 막을 수 없다 — 질의 함수가 `signal` 을 쓰지 않으므로 react-query 의 취소가 **전선의
 * 응답을 막지 않는다**(M12/12D 실측). 그래서 거를 수 있는 축은 **요청 출발 순서**다.
 *
 * 화면 단언을 먼저 두는 것으로도 「늦은 응답이 아직 날아오는 중이 아니다」는 서지 않는다.
 */
export function readsAfterWrite(page: Page, isRead: (one: Request) => boolean): ReadsAfterWrite {
  const started = new Set<Request>();
  const ledger = (one: Request) => {
    if (isRead(one)) {
      started.add(one);
    }
  };
  page.on('request', ledger);
  return {
    afterWrite(
      isWrite: (one: Request) => boolean,
      label: string,
      okStatuses: readonly number[] = [200],
    ): WriteThenRead {
      // 봉인은 **응답 핸들러 안에서** 한다 — 그러면 봉인과 다시 읽기의 출발 순서가 **이벤트
      // 디스패치로** 보장된다. `await` 뒤로 미룬 판은 순서가 런타임의 처리 순서에 달린다
      // (PR #118 v r1 L-1 변이 M3 GREEN).
      let stale: ReadonlySet<Request> | null = null;
      const read = page.waitForResponse(
        (one) =>
          stale !== null &&
          isRead(one.request()) &&
          one.status() === 200 &&
          !stale.has(one.request()),
      );
      const written = page.waitForResponse((one) => {
        if (!isWrite(one.request())) {
          return false;
        }
        stale = new Set(started);
        return true;
      });
      // 쓰기 단언이 터지면 `read` 는 관측되지 않은 채 남아 뒤늦게 reject 된다 — 이미 실패한
      // test 에 노이즈만 붙으므로 삼킨다(PR #118 CR r1 L-3).
      void read.catch(() => {});
      const write = (async () => {
        const response = await written;
        expect(okStatuses, `${label} 쓰기 응답`).toContain(response.status());
        return response;
      })();
      // 읽기는 **쓰기 단언을 지난 뒤** 돌려준다 — 쓰기가 터진 자리에서 읽기 타임아웃이 겹치면
      // 어느 쪽이 원인인지 문면이 가리지 못한다.
      return {
        write,
        read: (async () => {
          await write;
          return read;
        })(),
      };
    },
    stop(): void {
      page.off('request', ledger);
    },
  };
}

/** 건 목록 조회인가 — 포착 술어가 한 자리다(요청 장부와 응답 포착이 같은 것을 센다). */
export function isBidCaseListRead(one: Request): boolean {
  return one.url().includes('/api/bid-cases?') && one.method() === 'GET';
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


/* ── 여정이 공유하는 걸음(M7/7E-3a) ─────────────────────────────────────────
   셋을 `journey.spec.ts` 에서 이 자리로 옮겼다 — 여정 ⑤ 가 같은 걸음을 걷는데 복제하면
   갈린다(이 파일 머리 주석의 승인 목록 선례와 같은 사유). **거동은 바뀌지 않았다**: 본문은
   그대로이고 옮긴 것은 자리뿐이다. */

/**
 * 검색 — 술어에 **질의까지** 넣는다(CR M-4). 경로·상태만 보면 화면 B 가 마운트 때 거는 빈 폼
 * 조회를 잡을 수 있고, 그러면 검색 경로를 **재지 않으면서 초록**이 된다.
 */
export async function search(page: Page, query: string): Promise<NoticeRow[]> {
  const response = page.waitForResponse(
    (one) =>
      one.url().includes('/api/notices') &&
      new URL(one.url()).searchParams.get('q') === query &&
      one.status() === 200,
  );
  await page.fill('input[name="q"]', query);
  await page.getByRole('button', { name: '검색' }).click();
  return (await (await response).json()) as NoticeRow[];
}

/**
 * 담기 — **멱등**이다(이미 담긴 건은 200). 조건 대기 뒤 버튼 유무로 가른다(v M-1 · CR M-5).
 * 반환은 「이 실행이 실제로 담았는가」다 — 반복 실행에서 토스트 단언을 가르는 데 쓴다(CR r2 L-12).
 */
export async function ensureInBidBox(page: Page, card: Locator): Promise<boolean> {
  await expect(card).toBeVisible();
  const addButton = card.getByRole('button', { name: /담기/u });
  if ((await addButton.count()) === 0) {
    return false;
  }
  // 담기도 쓰기이고, 오늘의 공고 화면이 그 뒤에 건 목록을 다시 읽는다(`invalidateQueries`).
  // 그 다시 읽기를 **떠나기 전에 거둔다** — 거두지 않으면 부르는 쪽이 워크시트로 가며 거는
  // 「내가 연 화면의 건 목록」 포착이 **이 담기가 낳은 늦은 응답**을 집는다(실측: 클릭보다
  // 1.6ms 앞서 출발한 읽기가 걸렸다).
  const caseListReads = readsAfterWrite(page, isBidCaseListRead);
  const added = caseListReads.afterWrite(
    (one) => one.method() === 'POST' && one.url().endsWith('/api/bid-cases'),
    '담기',
    [200, 201],
  );
  await addButton.click();
  await added.write;
  await added.read;
  caseListReads.stop();
  return true;
}

/**
 * 건 하나를 **「제출함」까지** 민다 — 여정 ③ 이 그 각 단계를 자세히 재므로 여기서는 같은
 * 클릭만 하고 단언은 결과 상태 하나다. **여정 ④ 와 ⑤ 가 각각 자기 공고로 부른다.**
 *
 * `null` 은 「그 건이 `WATCHING` 이 아니다」이고 부르는 쪽이 그것을 어떻게 받을지 정한다 —
 * 여정 ④ 는 나머지 단언을 계속 돌리고(`if`), 여정 ⑤ 는 **붉게 멈춘다**(r1 H-1: 하네스가
 * 실행마다 버릴 DB 라 그 조건은 회귀나 오염일 때만 선다).
 *
 * 여정마다 **다른 공고**를 쓰는 이유는 fixture 의 `why_third_notice`·`why_fourth_notice` 에
 * 있다 — 한 하네스를 공유하므로 건의 상태가 전역이고, 같은 건을 쓰면 여정이 서로의 전이를 본다.
 */
export async function driveToRecorded(page: Page, noticeNumber: string): Promise<BidCaseRow | null> {
  // 이 함수가 내는 쓰기들이 건 목록 다시 읽기를 낳는다 — **떠날 때 진행 중인 읽기가 없도록**
  // 마지막 쓰기의 다시 읽기를 여기서 거둔다(아래 「거둔다」 주석). 그러지 않으면 부르는 쪽의
  // 「내가 연 화면의 응답」 포착이 **이 함수가 낳은 늦은 응답**을 집는다.
  const caseListReads = readsAfterWrite(page, isBidCaseListRead);
  const rows = await search(page, noticeNumber);
  // 없으면 **사유를 말하고** 멈춘다 — `!` 로 두면 `TypeError` 가 되어 「fixture 가 안 들어왔다」와
  // 「화면이 안 그렸다」를 구별할 수 없다(CR r1 L-11).
  expect(rows.map((row) => row.noticeNumber), `검색이 ${noticeNumber} 를 내지 않았다`).toContain(noticeNumber);
  const target = rows.find((row) => row.noticeNumber === noticeNumber)!;
  const card = page.getByTestId(`notice-card-${target.noticeNumber}`);
  await ensureInBidBox(page, card);

  const cases = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  await card.getByTestId('notice-title').click();
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  const caseRows = (await (await cases).json()) as BidCaseRow[];
  expect(caseRows.map((row) => row.noticeNumber), `입찰함에 ${noticeNumber} 건이 없다`).toContain(noticeNumber);
  const opened = caseRows.find((row) => row.noticeNumber === noticeNumber)!;

  // 이미 제출 뒤면(같은 DB 에 두 번 미는 지역 실행) 밀지 않는다 — CI 는 버릴 DB 라 늘 여기서
  // `WATCHING` 이고 아래 세 단계를 탄다.
  if (opened.state !== 'WATCHING') {
    caseListReads.stop();
    return null;
  }

  await page.getByTestId('active-bids').fill('1');
  const priced = page.waitForResponse(
    (one) => one.url().includes('/api/bid-price-requests') && one.request().method() === 'POST',
  );
  await page.getByTestId('worksheet-primary').click();
  expect((await priced).status()).toBe(200);

  await page.getByTestId('record-amount').fill('1350000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  const drafted = page.waitForResponse((one) => one.url().includes('/draft') && one.request().method() === 'POST');
  await page.getByTestId('worksheet-primary').click();
  expect((await drafted).status()).toBe(200);

  await page.getByTestId('record-amount').fill('1350000000');
  await page.getByTestId('record-vat').selectOption('INCLUSIVE');
  const recorded = caseListReads.afterWrite(
    (one) => one.method() === 'POST' && one.url().endsWith('/record'),
    '제출함',
  );
  await page.getByTestId('worksheet-primary').click();
  const response = await recorded.write;
  // **거둔다** — 그 기록이 낳은 건 목록 다시 읽기를 여기서 기다려, 이 함수를 떠날 때 진행 중인
  // 건 목록 읽기가 없게 한다. 그래서 부르는 쪽(여정 ④·⑤)의 포착이 **그 화면의** 응답을 집는다.
  await recorded.read;
  caseListReads.stop();
  return (await response.json()) as BidCaseRow;
}
