import type { components } from '../api/generated/operator-api';

import type { StateDisplay } from './stateDisplay';

type EditSessionResponse = components['schemas']['EditSessionResponse'];
type ValueRequest = components['schemas']['EditSessionValueRequest'];

/** 편집 세션이 다루는 전략 칸 — **계약 enum 전수**(`EditSessionBeginRequest.field`). */
export type StrategyField = NonNullable<components['schemas']['EditSessionBeginRequest']['field']>;

export type EditSessionState = EditSessionResponse['state'];

/**
 * 설정 화면의 세 표(M7/7E-2, D-7E2-2).
 *
 * 전략을 **직접 쓰는 operation 이 계약에 없다** — 모든 쓰기가 편집 세션(begin → value → confirm)을
 * 지난다. 그래서 이 파일이 드는 것은 ⓐ 칸의 문면 ⓑ 그 칸이 값을 싣는 **요청 칸의 이름** ⓒ 세션
 * 상태와 거절 사유의 문면이다. 셋 다 `satisfies Record<계약 enum, …>` 라 계약이 어휘를 늘리면
 * `tsc` 가 그 자리에서 멈춘다.
 */
export const STRATEGY_FIELD_DISPLAY: Record<StrategyField, StateDisplay> = {
  FOCUS_CATEGORY: { label: '관심 업종', why: '분류 코드 완전일치', tone: 'neutral' },
  FOCUS_REGION: { label: '관심 지역어', why: '공고문 지역 표기의 부분문자열', tone: 'neutral' },
  EXCLUDE_REGION: { label: '제외 지역어', why: '하나라도 맞으면 제외가 이긴다', tone: 'neutral' },
  REQUIRED_KEYWORD: { label: '필수 키워드', why: '모두 맞아야 통과한다', tone: 'neutral' },
  EXCLUDE_KEYWORD: { label: '제외 키워드', why: '하나라도 맞으면 제외가 이긴다', tone: 'neutral' },
  MIN_BUDGET: { label: '예산 하한', why: '기초금액 기준(원)', tone: 'neutral' },
  MAX_BUDGET: { label: '예산 상한', why: '기초금액 기준(원)', tone: 'neutral' },
  MINIMUM_MATCH_SCORE: { label: '최소 적합도', tone: 'neutral' },
  // ML-03 · D-ML-4 — **확률 어휘를 쓰지 않는다**. 이 값은 「적격(하한 이상)」 쪽 점수이고
  // 낙찰 확률이 아니다. 라벨이 길어도 그 구분을 지우지 않는다.
  MINIMUM_PROBABILITY_SCORE: {
    label: '최소 적격 점수',
    why: '낙찰 확률이 아니다 — 하한 이상에 들 가능성 쪽 점수다',
    tone: 'neutral',
  },
  BID_NOW_THRESHOLD: { label: '즉시 투찰 임계', tone: 'neutral' },
  REVIEW_THRESHOLD: { label: '검토 임계', tone: 'neutral' },
  CANDIDATE_LIMIT: { label: '후보 상한', why: '목록에 보이는 수의 상한이다', tone: 'neutral' },
  MAX_ACTIVE_BIDS: { label: '활성 투찰 상한', why: '이 값이 없으면 투찰가 요청이 거부된다', tone: 'neutral' },
};

/** 값이 실리는 요청 칸의 이름 — 계약이 네 칸만 두고 `field` 가 그 하나를 고른다. */
export type ValueSlot = 'terms' | 'amountWon' | 'number' | 'count';

/**
 * 칸 13 → 요청 칸 4. **계약 주석(L-3)이 적은 배정 그대로**다: 업종·지역·키워드 다섯은 `terms`,
 * 예산 둘은 `amountWon`, 임계 넷은 `number`, 후보 상한·여력 상한은 `count`.
 *
 * 표인 이유: 계약은 「`field` 가 이름하는 칸 **하나**가 필수」이고 다른 칸이 함께 오면 400 이다.
 * 분기로 적으면 칸 하나를 빠뜨린 요청이 런타임에만 드러난다.
 */
export const FIELD_VALUE_SLOT = {
  FOCUS_CATEGORY: 'terms',
  FOCUS_REGION: 'terms',
  EXCLUDE_REGION: 'terms',
  REQUIRED_KEYWORD: 'terms',
  EXCLUDE_KEYWORD: 'terms',
  MIN_BUDGET: 'amountWon',
  MAX_BUDGET: 'amountWon',
  MINIMUM_MATCH_SCORE: 'number',
  MINIMUM_PROBABILITY_SCORE: 'number',
  BID_NOW_THRESHOLD: 'number',
  REVIEW_THRESHOLD: 'number',
  CANDIDATE_LIMIT: 'count',
  MAX_ACTIVE_BIDS: 'count',
} as const satisfies Record<StrategyField, ValueSlot>;

/** 세션 상태 다섯 전수 — 종단 셋과 진행 둘이 어조로 갈린다. */
export const EDIT_SESSION_STATE_DISPLAY = {
  WAITING_FOR_VALUE: { label: '값을 기다린다', tone: 'neutral' },
  WAITING_FOR_CONFIRMATION: { label: '확인을 기다린다', tone: 'neutral' },
  APPLIED: { label: '적용됐다', tone: 'good' },
  CANCELLED: { label: '취소됐다', why: '재개는 새 세션이다', tone: 'absent' },
  EXPIRED: { label: '만료됐다', why: '서버가 접근 시점에 접은 판정이다', tone: 'absent' },
} as const satisfies Record<EditSessionState, StateDisplay>;

/**
 * 편집 세션의 409 거절 사유 — 계약이 `value` 아래에 적은 **여덟 코드 전수**이고 나머지 쓰기
 * 셋이 같은 목록을 가리킨다.
 *
 * 문면이 사유를 **가른다**: 「같은 명령을 다시 보냈다」와 「전략이 그동안 바뀌었다」는 운영자가
 * 할 일이 다르다. 모르는 코드는 `displayOrUnknown` 이 보이는 미지 상태로 낸다(이 표에 폴백을
 * 두지 않는다).
 *
 * **이 축만 키가 `string` 이다**: 계약의 `ErrorBody.code` 가 `type: string` 이라 `tsc` 가 전수를
 * 보장할 자리가 없다(7E-1 알려진 제한 5 와 같은 꼴 — `NOTICE_STATUS_DISPLAY` 가 선례다). 여덟
 * 코드를 적어 둔 것이 사실인지는 test 의 목록이 잠그고, 서버가 새 코드를 보내면 **그 코드를 그대로
 * 보인다**(문면을 지어내지 않는다). 세션 `state` 는 계약 enum 이므로 그쪽은 `satisfies` 가 든다.
 */
export const EDIT_SESSION_CONFLICT_DISPLAY: Readonly<Record<string, StateDisplay>> = {
  SESSION_EXPIRED: { label: '세션이 만료됐습니다', why: '새로 시작하십시오', tone: 'absent' },
  IDEMPOTENCY_CONFLICT: {
    label: '같은 명령에 다른 값이 왔습니다',
    why: '앞서 보낸 명령과 값이 다릅니다',
    tone: 'bad',
  },
  ACTOR_MISMATCH: { label: '세션을 연 주체가 다릅니다', tone: 'bad' },
  SYSTEM_ACTOR_NOT_PERMITTED: { label: '시스템 주체로는 할 수 없습니다', tone: 'bad' },
  STALE_REVISION: {
    label: '그동안 전략이 바뀌었습니다',
    why: '지금 값을 다시 읽고 처음부터 고치십시오',
    tone: 'bad',
  },
  INVALID_TRANSITION: { label: '지금 단계에서 할 수 없습니다', tone: 'bad' },
  SESSION_ALREADY_ACTIVE: { label: '이미 진행 중인 세션이 있습니다', tone: 'bad' },
  EDIT_SESSION_CONFLICT: { label: '동시 편집이 부딪쳤습니다', why: '다시 시도하십시오', tone: 'bad' },
};

/**
 * 값 입력이 운영자에게 묻는 꼴 — 요청 칸에서 나온다(칸마다 `if` 를 두지 않는다).
 *
 * `shape` 는 **받아들이는 문자열 전체의 모양**이다. 앞자리만 읽는 파서를 쓰지 않는 이유는
 * CR H-2 · v H-1 이다: `parseInt('1,200,000,000')` 은 **1** 이고 `Number.isFinite` 를 통과하므로
 * 「수로 읽히지 않으면 보내지 않는다」 가드를 **지나간다**. 그 결과 운영자가 적은 값과 다른 값이
 * 저장됐다(실 서버 실측: 예산 상한 `50,000,000` 입력 → 저장 `50`).
 *
 * 그래서 **문자열 전체가 그 모양일 때만** 받는다. 쉼표·단위·지수·꼬리 문자는 거부이고, 앞뒤
 * 공백만 정규화한다(그 정규화의 결과는 확인 화면이 **본문의 값**으로 보여 준다).
 */
export const SLOT_INPUT = {
  terms: { kind: 'TERMS', hint: '쉼표로 나눕니다. 비우면 그 조건을 지웁니다.', shape: null, accepts: '쉼표로 나눈 어휘' },
  amountWon: { kind: 'INTEGER', hint: '원 단위 정수', shape: /^\d+$/u, accepts: '자리 구분 없는 원 단위 정수(예: 1200000000)' },
  number: { kind: 'DECIMAL', hint: '0 과 1 사이의 소수', shape: /^-?\d+(\.\d+)?$/u, accepts: '소수(예: 0.42)' },
  count: { kind: 'INTEGER', hint: '건수', shape: /^\d+$/u, accepts: '정수 건수(예: 5)' },
} as const satisfies Record<
  ValueSlot,
  {
    readonly kind: 'TERMS' | 'INTEGER' | 'DECIMAL';
    readonly hint: string;
    /** `null` 은 어휘 칸(모양 검사가 없다). */
    readonly shape: RegExp | null;
    /** 거부 얼굴이 운영자에게 말하는 「받는 모양」. */
    readonly accepts: string;
  }
>;

/**
 * 쉼표로 적은 어휘를 배열로 — **빈 원소를 보내지 않는다**(계약이 공백뿐인 원소를 400 으로
 * 거부한다). 반환이 `readonly` 가 아닌 이유는 그대로 요청 본문의 칸에 들어가기 때문이다
 * (생성 타입의 그 칸이 가변 배열이다).
 */
export function termsOf(raw: string): string[] {
  return raw
    .split(',')
    .map((one) => one.trim())
    .filter((one) => one !== '');
}

/**
 * `value` 요청 본문 — **`field` 가 이름하는 칸 하나만** 싣는다. 다른 칸이 함께 가면 400 이고,
 * 그 선택을 표가 한다.
 *
 * **모양에 맞지 않으면 `null`** 이다 — 호출자가 보내지 않고 무엇을 받는지 말한다. 앞자리만
 * 읽어 「읽히기는 하지만 다른 수」를 보내는 일이 없다(CR H-2 · v H-1).
 */
export function valueBodyOf(field: StrategyField, commandId: string, raw: string): ValueRequest | null {
  const slot = FIELD_VALUE_SLOT[field];
  if (slot === 'terms') {
    return { commandId, field, terms: termsOf(raw) };
  }
  const normalized = raw.trim();
  const { shape } = SLOT_INPUT[slot];
  if (shape === null || !shape.test(normalized)) {
    return null;
  }
  const parsed = Number(normalized);
  // 모양을 지났어도 수가 아니거나 유한하지 않으면 보내지 않는다(아주 긴 자리 수 등).
  if (!Number.isFinite(parsed)) {
    return null;
  }
  if (slot === 'amountWon') {
    return { commandId, field, amountWon: parsed };
  }
  if (slot === 'count') {
    // 건수는 정수다 — 모양이 이미 정수를 요구하므로 여기서 깎이는 값이 없다.
    return { commandId, field, count: parsed };
  }
  return { commandId, field, number: parsed };
}

/**
 * 확인 화면의 「보낸 값」 — **본문에 실린 값**에서 그린다(원문이 아니다).
 *
 * 왜 본문에서 그리는가(CR H-2 ②): 원문을 보이면 운영자는 자기가 적은 것을 확인하고 「적용」을
 * 누르는데 서버로 간 값은 다를 수 있었다. 금액은 **지금 값과 같은 포맷터**를 쓰므로 둘이 다르면
 * `1,200,000,000원` ↔ `1원` 처럼 눈에 보인다.
 */
export function sentValueView(field: StrategyField, body: ValueRequest): FieldValueView {
  const slot = FIELD_VALUE_SLOT[field];
  if (slot === 'terms') {
    return termsView(body.terms ?? []);
  }
  if (slot === 'amountWon') {
    return { text: `${(body.amountWon ?? 0).toLocaleString('ko-KR')}원` };
  }
  if (slot === 'count') {
    return numberView(body.count);
  }
  return numberView(body.number);
}

type Strategy = components['schemas']['StrategyReadResponse'];

export interface FieldValueView {
  /** 지금 값의 문면. `null` 은 **미설정**이고 화면은 빈 자리를 보인다(0 으로 지어내지 않는다). */
  readonly text: string | null;
  /** 금액의 basis·과세·provenance 처럼 값만으로 뜻이 서지 않는 축(R-BASIS-06). */
  readonly qualifier?: string;
}

function termsView(terms: readonly string[]): FieldValueView {
  return { text: terms.length === 0 ? null : terms.join(', ') };
}

/**
 * 금액은 **값만 싣지 않는다** — basis·통화·과세·provenance 를 함께 보인다(R-BASIS-06). 네 칸
 * 가운데 하나라도 없으면 그 사실을 적는다(짐작하지 않는다).
 */
function budgetView(
  won: number | null | undefined,
  currency: string | null | undefined,
  vat: string | null | undefined,
  provenance: string | null | undefined,
): FieldValueView {
  if (won === null || won === undefined) {
    return { text: null };
  }
  const parts = [currency, vat, provenance].filter((one): one is string => one !== null && one !== undefined);
  return {
    text: `${won.toLocaleString('ko-KR')}원`,
    qualifier: parts.length === 0 ? '단위·과세·출처 미기재' : parts.join(' · '),
  };
}

function numberView(value: number | null | undefined): FieldValueView {
  return { text: value === null || value === undefined ? null : String(value) };
}

/**
 * 칸 13 → **지금 값을 읽는 자리**. 전략 응답의 칸 이름이 편집 세션의 `field` 토큰과 다르므로
 * 그 대응을 표로 둔다 — 화면이 이름을 추측하지 않는다.
 *
 * 읽기만 한다: 값을 만들지 않고, 없으면 `null` 을 그대로 올린다.
 */
export const STRATEGY_FIELD_VALUE: Record<StrategyField, (strategy: Strategy) => FieldValueView> = {
  FOCUS_CATEGORY: (one) => termsView(one.focusCategories),
  FOCUS_REGION: (one) => termsView(one.focusRegionTerms),
  EXCLUDE_REGION: (one) => termsView(one.excludeRegionTerms),
  REQUIRED_KEYWORD: (one) => termsView(one.requiredKeywordTerms),
  EXCLUDE_KEYWORD: (one) => termsView(one.excludeKeywordTerms),
  MIN_BUDGET: (one) =>
    budgetView(one.minBudgetWon, one.minBudgetCurrency, one.minBudgetVatTreatment, one.minBudgetProvenance),
  MAX_BUDGET: (one) =>
    budgetView(one.maxBudgetWon, one.maxBudgetCurrency, one.maxBudgetVatTreatment, one.maxBudgetProvenance),
  MINIMUM_MATCH_SCORE: (one) => numberView(one.minimumMatchScore),
  MINIMUM_PROBABILITY_SCORE: (one) => numberView(one.minimumProbabilityScore),
  BID_NOW_THRESHOLD: (one) => numberView(one.bidNowThreshold),
  REVIEW_THRESHOLD: (one) => numberView(one.reviewThreshold),
  CANDIDATE_LIMIT: (one) => numberView(one.candidateLimit),
  MAX_ACTIVE_BIDS: (one) => numberView(one.maxActiveBids),
};

/** 화면이 칸을 그리는 순서 — 감시 조건 다섯 · 예산 둘 · 임계 넷 · 상한 둘(계약 enum 순서다). */
export const STRATEGY_FIELD_ORDER: readonly StrategyField[] = [
  'FOCUS_CATEGORY',
  'FOCUS_REGION',
  'EXCLUDE_REGION',
  'REQUIRED_KEYWORD',
  'EXCLUDE_KEYWORD',
  'MIN_BUDGET',
  'MAX_BUDGET',
  'MINIMUM_MATCH_SCORE',
  'MINIMUM_PROBABILITY_SCORE',
  'BID_NOW_THRESHOLD',
  'REVIEW_THRESHOLD',
  'CANDIDATE_LIMIT',
  'MAX_ACTIVE_BIDS',
];
