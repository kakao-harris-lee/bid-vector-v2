import { describe, expect, it } from 'vitest';

import {
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
