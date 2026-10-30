import { describe, expect, it } from 'vitest';

import {
  sentValueView,
  EDIT_SESSION_CONFLICT_DISPLAY,
  EDIT_SESSION_STATE_DISPLAY,
  FIELD_VALUE_SLOT,
  SLOT_INPUT,
  STRATEGY_FIELD_DISPLAY,
  STRATEGY_FIELD_ORDER,
  STRATEGY_FIELD_VALUE,
  termsOf,
  valueBodyOf,
  type StrategyField,
} from './editSessionDisplay';
import { displayOrUnknown } from './stateDisplay';

/**
 * 계약 어휘를 **이 파일이 따로 적는다**(저장소 관례 — `caseDisplay.test.ts` 와 같다). 표를
 * 가져와 자기와 비교하면 등식이 공허하므로, 계약 문서의 목록을 손으로 적고 표가 그것과 같은지
 * 본다. 계약이 늘어난 것을 못 따라가는 자리는 원본의 `satisfies` 가 컴파일에서 멈춘다.
 */
const CONTRACT_FIELDS = [
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
] as const satisfies readonly StrategyField[];

describe('전략 칸 표 — 계약 enum 전수', () => {
  it('칸 열셋이 전부 문면 표에 있다', () => {
    expect(Object.keys(STRATEGY_FIELD_DISPLAY).sort()).toStrictEqual([...CONTRACT_FIELDS].sort());
  });

  it('칸 열셋이 전부 요청 칸 배정 표에 있다', () => {
    expect(Object.keys(FIELD_VALUE_SLOT).sort()).toStrictEqual([...CONTRACT_FIELDS].sort());
  });

  /** 계약 주석(L-3)이 적은 배정 그대로인지 — 네 칸의 묶음 수까지 본다. */
  it('요청 칸 배정이 계약의 묶음과 같다 — terms 다섯 · amountWon 둘 · number 넷 · count 둘', () => {
    const grouped = new Map<string, string[]>();
    for (const field of CONTRACT_FIELDS) {
      const slot = FIELD_VALUE_SLOT[field];
      grouped.set(slot, [...(grouped.get(slot) ?? []), field]);
    }

    expect(grouped.get('terms')).toStrictEqual([
      'FOCUS_CATEGORY',
      'FOCUS_REGION',
      'EXCLUDE_REGION',
      'REQUIRED_KEYWORD',
      'EXCLUDE_KEYWORD',
    ]);
    expect(grouped.get('amountWon')).toStrictEqual(['MIN_BUDGET', 'MAX_BUDGET']);
    expect(grouped.get('number')).toStrictEqual([
      'MINIMUM_MATCH_SCORE',
      'MINIMUM_PROBABILITY_SCORE',
      'BID_NOW_THRESHOLD',
      'REVIEW_THRESHOLD',
    ]);
    expect(grouped.get('count')).toStrictEqual(['CANDIDATE_LIMIT', 'MAX_ACTIVE_BIDS']);
  });

  it('네 요청 칸이 전부 입력 모양 표에 있다', () => {
    expect(Object.keys(SLOT_INPUT).sort()).toStrictEqual(['terms', 'amountWon', 'number', 'count'].sort());
  });

  /**
   * ML-03 · D-ML-4 — **값의 이름에 확률 어휘를 쓰지 않는다**. 부정하는 문장은 다르다: 화면 C 도
   * 「가격 적합도는 낙찰 확률이 아닙니다」라는 고지를 둔다. 그래서 라벨에서는 금하고 아래층
   * (`why`)에서는 **그 부정을 요구한다**.
   */
  it('칸 라벨에 확률 어휘가 없고 아래층이 그것을 부정한다', () => {
    for (const [field, display] of Object.entries(STRATEGY_FIELD_DISPLAY)) {
      expect(display.label, field).not.toContain('확률');
    }

    expect(STRATEGY_FIELD_DISPLAY.MINIMUM_PROBABILITY_SCORE.why ?? '').toContain('낙찰 확률이 아니다');
  });
});

describe('세션 상태와 거절 사유 표', () => {
  it('세션 상태 다섯이 전부 표에 있다', () => {
    expect(Object.keys(EDIT_SESSION_STATE_DISPLAY).sort()).toStrictEqual(
      ['WAITING_FOR_VALUE', 'WAITING_FOR_CONFIRMATION', 'APPLIED', 'CANCELLED', 'EXPIRED'].sort(),
    );
  });

  /**
   * **만료는 서버의 판정이다** — 화면이 `expiresAt` 을 지금 시각과 비교해 만드는 값이 아니다
   * (D-7E-4). 그 문면이 그 사실을 적는다.
   */
  it('만료 문면이 서버 판정임을 말한다', () => {
    expect(EDIT_SESSION_STATE_DISPLAY.EXPIRED.why).toContain('서버가');
  });

  it('409 사유 여덟이 전부 표에 있다', () => {
    expect(Object.keys(EDIT_SESSION_CONFLICT_DISPLAY).sort()).toStrictEqual(
      [
        'SESSION_EXPIRED',
        'IDEMPOTENCY_CONFLICT',
        'ACTOR_MISMATCH',
        'SYSTEM_ACTOR_NOT_PERMITTED',
        'STALE_REVISION',
        'INVALID_TRANSITION',
        'SESSION_ALREADY_ACTIVE',
        'EDIT_SESSION_CONFLICT',
      ].sort(),
    );
  });

  /**
   * 두 사유가 **다른 말**을 해야 한다 — 운영자가 할 일이 다르다. 조회는 화면과 **같은 자리**
   * (`displayOrUnknown`)를 지난다: 이 축은 키가 `string` 이라 직접 속성 접근이 `undefined` 를
   * 품고, 그 갈래를 test 가 비켜 가면 화면의 실제 경로를 재지 않는 것이 된다.
   */
  it('「같은 명령」과 「전략이 바뀌었다」가 다른 문면이다', () => {
    const idempotency = displayOrUnknown(EDIT_SESSION_CONFLICT_DISPLAY, 'IDEMPOTENCY_CONFLICT');
    const stale = displayOrUnknown(EDIT_SESSION_CONFLICT_DISPLAY, 'STALE_REVISION');

    expect(idempotency.label).not.toBe(stale.label);
    expect(stale.why).toContain('다시 읽고');
  });

  it('표 밖의 코드는 보이는 미지 상태다 — 문면을 지어내지 않는다', () => {
    const unknown = displayOrUnknown(EDIT_SESSION_CONFLICT_DISPLAY, 'NEW_CODE_FROM_SERVER');

    expect(unknown.label).toContain('NEW_CODE_FROM_SERVER');
    expect(unknown.tone).toBe('unjudged');
  });
});

describe('값 요청 본문 — field 가 이름하는 칸 하나만', () => {
  it('어휘 칸은 terms 하나만 싣는다', () => {
    const body = valueBodyOf('REQUIRED_KEYWORD', 'cmd-1', '합성, 공사');

    expect(body).toStrictEqual({ commandId: 'cmd-1', field: 'REQUIRED_KEYWORD', terms: ['합성', '공사'] });
  });

  it('예산은 amountWon · 임계는 number · 상한은 count 하나만 싣는다', () => {
    expect(valueBodyOf('MIN_BUDGET', 'c', '1200000')).toStrictEqual({
      commandId: 'c',
      field: 'MIN_BUDGET',
      amountWon: 1_200_000,
    });
    expect(valueBodyOf('REVIEW_THRESHOLD', 'c', '0.42')).toStrictEqual({
      commandId: 'c',
      field: 'REVIEW_THRESHOLD',
      number: 0.42,
    });
    expect(valueBodyOf('MAX_ACTIVE_BIDS', 'c', '5')).toStrictEqual({
      commandId: 'c',
      field: 'MAX_ACTIVE_BIDS',
      count: 5,
    });
  });

  /** 다른 칸이 함께 가면 **400** 이다(계약) — 본문의 키가 정확히 셋임을 센다. */
  it('본문의 키가 commandId · field · 그 칸 하나뿐이다', () => {
    for (const field of CONTRACT_FIELDS) {
      const body = valueBodyOf(field, 'c', field === 'REQUIRED_KEYWORD' ? 'a' : '1');

      expect(body, field).not.toBeNull();
      expect(Object.keys(body!).sort(), field).toStrictEqual(['commandId', 'field', FIELD_VALUE_SLOT[field]].sort());
    }
  });

  /** 수로 읽히지 않으면 **보내지 않는다** — 0 으로 지어내지 않는다. */
  it('숫자 칸에 수가 아닌 값이 오면 null 이다', () => {
    expect(valueBodyOf('MAX_ACTIVE_BIDS', 'c', '')).toBeNull();
    expect(valueBodyOf('MIN_BUDGET', 'c', '없음')).toBeNull();
    expect(valueBodyOf('REVIEW_THRESHOLD', 'c', '')).toBeNull();
  });

  /**
   * **CR H-2 · v H-1 의 회귀 test.** 앞자리만 읽는 파서는 「읽히기는 하지만 운영자가 적은 것과
   * 다른 수」를 통과시켰다 — `parseInt('1,200,000,000')` 은 1 이고 `Number.isFinite` 를 지난다.
   * 실 서버 실측에서 예산 상한 `50,000,000` 이 **50** 으로 저장됐다. 그래서 **문자열 전체가
   * 그 모양일 때만** 받는다.
   */
  it.each([
    ['자리 구분 쉼표', 'MIN_BUDGET', '1,000,000'],
    ['단위가 붙은 금액', 'MAX_BUDGET', '50,000,000원'],
    ['지수 표기', 'MIN_BUDGET', '1e6'],
    ['꼬리 문자', 'MIN_BUDGET', '12abc'],
    ['금액의 소수점', 'MIN_BUDGET', '1200.5'],
    ['금액의 음수', 'MIN_BUDGET', '-1'],
    ['건수의 소수점', 'CANDIDATE_LIMIT', '2.9'],
    ['건수의 음수', 'MAX_ACTIVE_BIDS', '-3'],
    ['소수 칸의 쉼표', 'REVIEW_THRESHOLD', '0,5'],
    ['소수 칸의 지수', 'REVIEW_THRESHOLD', '1e-3'],
    // v r2 L-1 · CR r2 L2R-3 — 모양은 지나지만 `Number` 가 접는 자리다(실 서버에서 접힌 값이 저장됐다).
    ['2^53 을 넘는 금액', 'MIN_BUDGET', '9007199254740993'],
    ['2^53 을 넘는 건수', 'MAX_ACTIVE_BIDS', '9007199254740993'],
  ] as const)('%s 은 거부한다 — 잘린 수를 보내지 않는다', (_label, field, raw) => {
    expect(valueBodyOf(field, 'c', raw)).toBeNull();
  });

  it('안전 정수 경계는 그대로 받는다 — 거부가 지나치지 않다', () => {
    expect(valueBodyOf('MIN_BUDGET', 'c', String(Number.MAX_SAFE_INTEGER))?.amountWon).toBe(
      Number.MAX_SAFE_INTEGER,
    );
  });

  it('앞뒤 공백만 정규화한다 — 그 결과는 확인 화면이 본문 값으로 보인다', () => {
    expect(valueBodyOf('MAX_ACTIVE_BIDS', 'c', '  5  ')).toStrictEqual({
      commandId: 'c',
      field: 'MAX_ACTIVE_BIDS',
      count: 5,
    });
    expect(valueBodyOf('REVIEW_THRESHOLD', 'c', ' 0.42 ')?.number).toBe(0.42);
  });

  it('받는 모양은 그대로 받는다 — 거부가 지나치지 않다', () => {
    expect(valueBodyOf('MIN_BUDGET', 'c', '1200000000')?.amountWon).toBe(1_200_000_000);
    expect(valueBodyOf('MIN_BUDGET', 'c', '0')?.amountWon).toBe(0);
    expect(valueBodyOf('REVIEW_THRESHOLD', 'c', '0')?.number).toBe(0);
    expect(valueBodyOf('REVIEW_THRESHOLD', 'c', '-0.5')?.number).toBe(-0.5);
    expect(valueBodyOf('CANDIDATE_LIMIT', 'c', '100')?.count).toBe(100);
  });
});

/**
 * 확인 화면이 보이는 「보낸 값」은 **본문의 값**이다(CR H-2 ②) — 원문이 아니다. 금액은 지금 값과
 * **같은 포맷터**를 쓰므로 둘이 다르면 눈에 보인다.
 */
describe('보낸 값의 문면은 본문에서 나온다', () => {
  it('금액은 지금 값과 같은 꼴로 보인다', () => {
    const body = valueBodyOf('MIN_BUDGET', 'c', '1200000000')!;

    expect(sentValueView('MIN_BUDGET', body).text).toBe('1,200,000,000원');
  });

  it('본문이 1 이면 「1원」이라고 보인다 — 원문을 되풀이하지 않는다', () => {
    expect(sentValueView('MIN_BUDGET', { commandId: 'c', field: 'MIN_BUDGET', amountWon: 1 }).text).toBe('1원');
  });

  /** 짝이 어긋나 칸이 비면 **「0원」을 지어내지 않는다**(CR r2 L2R-4). */
  it('금액 칸이 비면 값이 없다고 말한다 — 0 원을 지어내지 않는다', () => {
    expect(sentValueView('MIN_BUDGET', { commandId: 'c', field: 'MIN_BUDGET' }).text).toBeNull();
  });

  it('건수·소수는 본문 값 그대로', () => {
    expect(sentValueView('MAX_ACTIVE_BIDS', valueBodyOf('MAX_ACTIVE_BIDS', 'c', '5')!).text).toBe('5');
    expect(sentValueView('REVIEW_THRESHOLD', valueBodyOf('REVIEW_THRESHOLD', 'c', '0.42')!).text).toBe('0.42');
  });

  it('어휘는 쉼표로 이어지고 빈 어휘는 null 이다 — 「비움」 얼굴이 그 자리를 받는다', () => {
    expect(sentValueView('REQUIRED_KEYWORD', valueBodyOf('REQUIRED_KEYWORD', 'c', '합성, 공사')!).text).toBe(
      '합성, 공사',
    );
    expect(sentValueView('REQUIRED_KEYWORD', valueBodyOf('REQUIRED_KEYWORD', 'c', '')!).text).toBeNull();
  });

  /**
   * **중복은 접는다**(CR r1 L-4 처방 · r2 L2R-5 가 잠금 부재를 잡았다). 수는 **거부하고 받는
   * 모양을 말하는데** 어휘 중복은 **조용히 고쳐 보내는** 차이가 있다 — 그 이유는 둘이 다른 종류의
   * 입력이기 때문이다: `'1,000'` 은 **운영자가 뜻한 수를 알 수 없다**(1 인가 1000 인가), 반면
   * `'건축, 건축'` 의 뜻은 하나뿐이고(업종 「건축」) 계약이 그 중복만 400 으로 거부한다. 뜻이
   * 하나인 입력을 거부해 다시 적게 하는 것은 운영자에게 값을 주지 않는다.
   */
  it('중복 어휘를 접는다 — 계약이 중복을 400 으로 거부하는 자리다', () => {
    expect(termsOf('건축, 건축')).toStrictEqual(['건축']);
    expect(termsOf('건축, 토목, 건축')).toStrictEqual(['건축', '토목']);
    expect(valueBodyOf('FOCUS_CATEGORY', 'c', '가, 가, 나')?.terms).toStrictEqual(['가', '나']);
  });

  it('빈 어휘는 빈 배열이다 — 그 조건을 지우는 길이고 공백 원소를 보내지 않는다', () => {
    expect(termsOf('  ,  , ')).toStrictEqual([]);
    expect(termsOf('a, , b')).toStrictEqual(['a', 'b']);
    expect(valueBodyOf('FOCUS_CATEGORY', 'c', '')).toStrictEqual({
      commandId: 'c',
      field: 'FOCUS_CATEGORY',
      terms: [],
    });
  });
});

/** 전략 응답의 **빈 한 벌** — 각 test 가 자기가 재는 칸만 채운다. */
const EMPTY_STRATEGY = {
  revision: 0,
  focusCategories: [],
  focusRegionTerms: [],
  excludeRegionTerms: [],
  requiredKeywordTerms: [],
  excludeKeywordTerms: [],
};

describe('지금 값을 읽는 표', () => {
  it('칸 열셋이 전부 표에 있고 그리는 순서도 열셋이다', () => {
    expect(Object.keys(STRATEGY_FIELD_VALUE).sort()).toStrictEqual([...CONTRACT_FIELDS].sort());
    expect([...STRATEGY_FIELD_ORDER].sort()).toStrictEqual([...CONTRACT_FIELDS].sort());
  });

  /** 미설정은 **`null`** 이다 — 0 도 빈 문자열도 아니다(가짜 기본값 금지). */
  it('값이 없으면 전부 null 이다 — 0 으로 지어내지 않는다', () => {
    for (const field of CONTRACT_FIELDS) {
      expect(STRATEGY_FIELD_VALUE[field](EMPTY_STRATEGY).text, field).toBeNull();
    }
  });

  it('어휘는 쉼표로 이어 보인다', () => {
    const view = STRATEGY_FIELD_VALUE.REQUIRED_KEYWORD({ ...EMPTY_STRATEGY, requiredKeywordTerms: ['합성', '공사'] });

    expect(view.text).toBe('합성, 공사');
  });

  /** R-BASIS-06 — 금액은 **단위·과세·출처와 함께** 보인다. 그 셋이 없으면 그 사실을 적는다. */
  it('예산은 금액과 함께 통화·과세·출처를 보인다', () => {
    const view = STRATEGY_FIELD_VALUE.MIN_BUDGET({
      ...EMPTY_STRATEGY,
      minBudgetWon: 1_200_000_000,
      minBudgetCurrency: 'KRW',
      minBudgetVatTreatment: 'INCLUSIVE',
      minBudgetProvenance: 'OPERATOR_DECLARED',
    });

    expect(view.text).toBe('1,200,000,000원');
    expect(view.qualifier).toContain('KRW');
    expect(view.qualifier).toContain('INCLUSIVE');
    expect(view.qualifier).toContain('OPERATOR_DECLARED');
  });

  it('금액만 있고 단위·과세·출처가 없으면 그 사실을 적는다', () => {
    const view = STRATEGY_FIELD_VALUE.MAX_BUDGET({ ...EMPTY_STRATEGY, maxBudgetWon: 500 });

    expect(view.text).toBe('500원');
    expect(view.qualifier).toContain('미기재');
  });

  /** 0 은 **값이다** — 미설정과 같은 얼굴로 접으면 설정한 0 이 사라진다. */
  it('0 은 미설정이 아니다', () => {
    expect(STRATEGY_FIELD_VALUE.CANDIDATE_LIMIT({ ...EMPTY_STRATEGY, candidateLimit: 0 }).text).toBe('0');
    expect(STRATEGY_FIELD_VALUE.REVIEW_THRESHOLD({ ...EMPTY_STRATEGY, reviewThreshold: 0 }).text).toBe('0');
  });
});
