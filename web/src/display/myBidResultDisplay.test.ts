import { describe, expect, it } from 'vitest';

import type { MyBidAggregate, MyBidCase } from '../api/queries';

import { confirmedAwardReading } from './openingResultDisplay';
import {
  AWARD_RATE_STATE_DISPLAY,
  GAP_STATE_DISPLAY,
  OUTCOME_AGREEMENT_DISPLAY,
  RECONCILED_OUTCOME_DISPLAY,
  RECONCILIATION_BLOCKER_DISPLAY,
  SAMPLE_NOTE,
  SUBMITTED_AMOUNT_ORIGIN_DISPLAY,
  awardGapReading,
  caseResultsById,
  rateAxesOf,
  type AwardRateState,
  type GapState,
  type OutcomeAgreement,
  type ReconciledOutcome,
  type ReconciliationBlocker,
  type SubmittedAmountOrigin,
} from './myBidResultDisplay';

/**
 * 계약 enum 을 **축어로** 적는다(`openingResultDisplay.test.ts` 와 같은 관례). 표에서 키를 뽑아
 * 자기 자신과 대조하면 등식이 공허하다 — 계약이 늘면 이 목록이 먼저 틀려야 한다. 타입 쪽은
 * 모듈의 `satisfies` 가 들고 이 목록은 **값 쪽**의 같은 주장이다.
 */
const CONTRACT_OUTCOMES: readonly ReconciledOutcome[] = [
  'WON',
  'LOST',
  'FAILED_BID',
  'REBID',
  'UNDECIDABLE',
];

/** 판정 불가 사유 **열**(D-7C5B-11) — 개찰 축 여섯 + 커널 축 넷. */
const CONTRACT_BLOCKERS: readonly ReconciliationBlocker[] = [
  'NOTICE_ABSENT',
  'NOT_OPENED_YET',
  'NOT_COLLECTED',
  'OBSERVED_NOT_PROMOTED',
  'PROGRESS_UNRECOGNIZED',
  'PROGRESS_ABSENT',
  'OPERATOR_NAME_NOT_DECLARED',
  'OPERATOR_NAME_NORMALIZES_TO_EMPTY',
  'WINNER_NAME_NOT_PUBLISHED',
  'WINNER_NAME_NORMALIZES_TO_EMPTY',
];

const CONTRACT_AGREEMENTS: readonly OutcomeAgreement[] = ['AGREE', 'DISAGREE', 'NOT_COMPARABLE'];
const CONTRACT_GAP_STATES: readonly GapState[] = ['COMPUTED', 'AWARD_ABSENT'];
const CONTRACT_RATE_STATES: readonly AwardRateState[] = [
  'MEASURED',
  'ABSENT_NO_RECORDED_BIDS',
  'ABSENT_NO_DECIDED_CASES',
];
const CONTRACT_ORIGINS: readonly SubmittedAmountOrigin[] = ['OPERATOR_ENTERED', 'PRICE_MENU'];

/**
 * 하네스 실측값이 기본값이다(착수 보고 ③ — 실 app.jar · fake KONEPS). 각 test 가 **자기가 재는
 * 칸만** 덮어쓴다.
 */
function bidResult(overrides: Partial<MyBidCase> = {}): MyBidCase {
  return {
    caseId: 51,
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    recordedState: 'WON',
    submittedAmountWon: 1_350_000_000,
    submittedAmountCurrency: 'KRW',
    submittedAmountVatTreatment: 'INCLUSIVE',
    submittedAmountOrigin: 'OPERATOR_ENTERED',
    chosenOptionRef: null,
    submittedAt: '2026-10-31T03:20:31.073Z',
    confirmedAt: '2026-10-31T03:20:31.082Z',
    openingState: 'NOT_OPENED_YET',
    confirmedAwardAmountWon: null,
    confirmedAwardAmountCurrency: null,
    confirmedAwardAmountBasis: null,
    confirmedAwardAmountVatTreatment: null,
    confirmedAwardAmountProvenance: null,
    reconciledOutcome: 'UNDECIDABLE',
    reconciliationBlocker: 'NOT_OPENED_YET',
    outcomeAgreement: 'NOT_COMPARABLE',
    gapState: 'AWARD_ABSENT',
    gapWon: null,
    gapCurrency: null,
    ...overrides,
  };
}

/**
 * 칸마다 **다른 수**를 둔다 — 두 축이 같은 수를 쓰면 접두를 바꿔 끼운 변이(`recorded*` ↔
 * `reconciled*`)가 조용히 지나간다.
 */
function aggregate(overrides: Partial<MyBidAggregate> = {}): MyBidAggregate {
  return {
    sampleSize: 30,
    recordedRateState: 'MEASURED',
    recordedAwardRate: '0.5',
    recordedWonCount: 1,
    recordedLostCount: 2,
    recordedDenominator: 3,
    recordedFailedOrRebidCount: 4,
    recordedUndecidedCount: 5,
    reconciledRateState: 'ABSENT_NO_DECIDED_CASES',
    reconciledAwardRate: null,
    reconciledWonCount: 11,
    reconciledLostCount: 12,
    reconciledDenominator: 13,
    reconciledFailedOrRebidCount: 14,
    reconciledUndecidedCount: 15,
    reconciliationBlockers: ['NOT_OPENED_YET'],
    ...overrides,
  };
}

describe('대사 어휘 여섯은 계약 전수이고 접히지 않는다', () => {
  it('표마다 키 집합이 계약 전수와 같다', () => {
    expect(Object.keys(RECONCILED_OUTCOME_DISPLAY).sort()).toStrictEqual([...CONTRACT_OUTCOMES].sort());
    expect(Object.keys(RECONCILIATION_BLOCKER_DISPLAY).sort()).toStrictEqual([...CONTRACT_BLOCKERS].sort());
    expect(Object.keys(OUTCOME_AGREEMENT_DISPLAY).sort()).toStrictEqual([...CONTRACT_AGREEMENTS].sort());
    expect(Object.keys(GAP_STATE_DISPLAY).sort()).toStrictEqual([...CONTRACT_GAP_STATES].sort());
    expect(Object.keys(AWARD_RATE_STATE_DISPLAY).sort()).toStrictEqual([...CONTRACT_RATE_STATES].sort());
    expect(Object.keys(SUBMITTED_AMOUNT_ORIGIN_DISPLAY).sort()).toStrictEqual([...CONTRACT_ORIGINS].sort());
  });

  /** 라벨이 겹치면 계약이 가른 갈래가 **화면에서** 한 갈래가 된다 — 키 등식으로는 안 보인다. */
  it('표 안의 라벨이 서로 다르다', () => {
    const tables = [
      ['대사 결말', CONTRACT_OUTCOMES.map((one) => RECONCILED_OUTCOME_DISPLAY[one].label)],
      ['판정 불가 사유', CONTRACT_BLOCKERS.map((one) => RECONCILIATION_BLOCKER_DISPLAY[one].label)],
      ['일치', CONTRACT_AGREEMENTS.map((one) => OUTCOME_AGREEMENT_DISPLAY[one].label)],
      ['차이 상태', CONTRACT_GAP_STATES.map((one) => GAP_STATE_DISPLAY[one].label)],
      ['비율 상태', CONTRACT_RATE_STATES.map((one) => AWARD_RATE_STATE_DISPLAY[one].label)],
      ['투찰가 출처', CONTRACT_ORIGINS.map((one) => SUBMITTED_AMOUNT_ORIGIN_DISPLAY[one].label)],
    ] as const;

    for (const [name, labels] of tables) {
      expect(new Set(labels).size, `${name} 에 접힌 라벨이 있다: ${labels.join(' · ')}`).toBe(labels.length);
    }
  });

  /**
   * **판정 불가는 패찰이 아니다**(계약 문면). 라벨이 다른 것만으로는 모자라다 — 어조가 `bad` 면
   * 운영자가 그 배지를 나쁜 결과로 읽는다. 「견줄 것이 아직 없다」는 아직 판정하지 않은 것이다.
   */
  it('판정 불가가 패찰과 다른 얼굴이고 어조가 bad 가 아니다', () => {
    expect(RECONCILED_OUTCOME_DISPLAY.UNDECIDABLE.label).not.toBe(RECONCILED_OUTCOME_DISPLAY.LOST.label);
    expect(RECONCILED_OUTCOME_DISPLAY.UNDECIDABLE.tone).toBe('unjudged');
    expect(RECONCILED_OUTCOME_DISPLAY.LOST.tone).not.toBe('unjudged');
  });

  /** 어긋남은 **사실**이지 판정이 아니다 — 어느 쪽이 틀렸다고 읽히면 안 된다(D-7C5B-3). */
  it('어긋남 표지의 어조가 bad 가 아니다', () => {
    expect(OUTCOME_AGREEMENT_DISPLAY.DISAGREE.tone).not.toBe('bad');
    expect(OUTCOME_AGREEMENT_DISPLAY.NOT_COMPARABLE.tone).toBe('unjudged');
  });

  /**
   * **분모 0 은 0% 가 아니다.** 두 부재 사유가 서로 다른 얼굴이어야 「셀 것이 없다」와 「셌는데
   * 분모에 드는 것이 없다」가 갈린다. 어느 문면에도 `0%`·`0 %` 가 없어야 한다.
   */
  it('비율 부재 둘이 다른 얼굴이고 0% 로 읽히지 않는다', () => {
    const absent = [
      AWARD_RATE_STATE_DISPLAY.ABSENT_NO_RECORDED_BIDS,
      AWARD_RATE_STATE_DISPLAY.ABSENT_NO_DECIDED_CASES,
    ];

    expect(absent[0]!.label).not.toBe(absent[1]!.label);
    for (const one of absent) {
      expect(one.tone).toBe('absent');
      expect(`${one.label} ${one.why ?? ''}`).not.toMatch(/0\s*%/u);
    }
  });

  /** 사유 열에 **각자의 문면**이 있다 — 「판정 불가」 하나로 뭉치면 열을 둔 계약이 사라진다. */
  it('사유 열 전수에 사유 문면이 있다', () => {
    for (const blocker of CONTRACT_BLOCKERS) {
      const display = RECONCILIATION_BLOCKER_DISPLAY[blocker];
      expect(display.label.length, `${blocker} 에 라벨이 없다`).toBeGreaterThan(0);
      expect((display.why ?? '').length, `${blocker} 에 사유가 없다`).toBeGreaterThan(0);
    }
  });

  /** 「고른 안」은 빈칸이 아니라 **상태**다(D-7C5B-10 ①) — 오늘의 답은 「메뉴에서 고른 것이 아님」. */
  it('투찰가 출처 둘이 다른 얼굴이다', () => {
    expect(SUBMITTED_AMOUNT_ORIGIN_DISPLAY.OPERATOR_ENTERED.label).not.toBe(
      SUBMITTED_AMOUNT_ORIGIN_DISPLAY.PRICE_MENU.label,
    );
  });
});

describe('차이 읽기 — 상태가 답일 때 숫자를 그리지 않는다', () => {
  it('확정 낙찰가가 없으면 상태가 답이다 — 0 을 내지 않는다', () => {
    const reading = awardGapReading(bidResult());

    expect(reading).toStrictEqual({ kind: 'ABSENT', state: 'AWARD_ABSENT' });
  });

  it('COMPUTED 이면 차이와 통화를 함께 낸다', () => {
    const reading = awardGapReading(
      bidResult({ gapState: 'COMPUTED', gapWon: -40_000_000, gapCurrency: 'KRW' }),
    );

    expect(reading).toStrictEqual({ kind: 'COMPUTED', differenceWon: -40_000_000, currency: 'KRW' });
  });

  /**
   * 차이 0 은 **값이다** — 「확정 낙찰가가 없다」와 다르다(계약: 「차이가 0 이다」와 `AWARD_ABSENT`
   * 가 다른 값). `gapWon` 을 참/거짓으로 보면 0 이 부재로 접힌다.
   */
  it('차이 0 이 부재로 접히지 않는다', () => {
    const reading = awardGapReading(bidResult({ gapState: 'COMPUTED', gapWon: 0, gapCurrency: 'KRW' }));

    expect(reading).toStrictEqual({ kind: 'COMPUTED', differenceWon: 0, currency: 'KRW' });
  });

  /** 서버가 COMPUTED 라면서 칸을 빠뜨린 것은 **계약 위반**이고 진단이 되어야 한다. */
  it('COMPUTED 인데 칸이 비면 INCOMPLETE 이고 빠진 칸을 말한다', () => {
    const reading = awardGapReading(bidResult({ gapState: 'COMPUTED', gapWon: -1, gapCurrency: null }));

    expect(reading.kind).toBe('INCOMPLETE');
    expect(reading.kind === 'INCOMPLETE' ? reading.missing : []).toStrictEqual(['gapCurrency']);
  });
});

describe('확정 낙찰가 읽기는 두 operation 이 같은 자리를 쓴다', () => {
  /**
   * `readOpeningResult` 와 `readMyBidResults` 가 **같은 칸 이름**으로 확정 낙찰가를 낸다. 읽기를
   * 복제하면 한쪽만 고쳐지는 길이 생기므로 한 함수가 둘을 읽는다(구조적 타입) — 그래도 두 값은
   * 화면에서 **따로** 그려진다(D-7E3b-9: 어긋나면 둘 다 보인다).
   */
  it('건 응답의 다섯 칸이 비면 ABSENT 다', () => {
    expect(confirmedAwardReading(bidResult()).kind).toBe('ABSENT');
  });

  it('건 응답의 다섯 칸이 차면 PRESENT 이고 값이 그대로다', () => {
    const reading = confirmedAwardReading(
      bidResult({
        confirmedAwardAmountWon: 1_310_000_000,
        confirmedAwardAmountCurrency: 'KRW',
        confirmedAwardAmountBasis: 'BASE_AMOUNT',
        confirmedAwardAmountVatTreatment: 'UNKNOWN',
        confirmedAwardAmountProvenance: 'PUBLISHED',
      }),
    );

    expect(reading).toStrictEqual({
      kind: 'PRESENT',
      value: {
        amountWon: 1_310_000_000,
        currency: 'KRW',
        basis: 'BASE_AMOUNT',
        vatTreatment: 'UNKNOWN',
        provenance: 'PUBLISHED',
      },
    });
  });
});

describe('카드와 건의 대응은 caseId 로', () => {
  /**
   * **순서로 짝지으면 어긋난다**(착수 실측): `cases[]` 는 확인 시각 내림차순이고 화면 카드는
   * `listBidCases` 의 차례다. 두 목록의 길이도 다르다 — `cases[]` 는 「투찰 기록이 있는 실투찰
   * 건 전부」이고 카드는 「결과 단계 일곱」이다.
   */
  it('목록 순서와 무관하게 caseId 로 찾는다', () => {
    const map = caseResultsById([bidResult({ caseId: 3 }), bidResult({ caseId: 1 })]);

    expect(map.get(1)?.caseId).toBe(1);
    expect(map.get(3)?.caseId).toBe(3);
  });

  /** 카드에 짝이 없을 수 있다 — 그때 `undefined` 는 **답**이고 화면이 사유를 적는다(D-7E3b-7 ⒞). */
  it('짝이 없는 caseId 는 undefined 다', () => {
    expect(caseResultsById([bidResult({ caseId: 1 })]).get(99)).toBeUndefined();
  });

  it('빈 목록에서도 조회가 선다', () => {
    expect(caseResultsById([]).size).toBe(0);
  });
});

describe('집계 두 축은 자기 접두 칸만 본다', () => {
  /**
   * 접두를 바꿔 끼운 변이(`recorded*` ↔ `reconciled*`)를 잡으려면 **칸마다 다른 수**여야 한다.
   * 두 축을 한 JSX 로 그리므로 이 함수가 접두를 아는 **유일한 자리**다.
   */
  it('운영자 기록 축이 recorded* 일곱 칸을 그대로 싣는다', () => {
    const [recorded] = rateAxesOf(aggregate());

    expect(recorded.id).toBe('recorded');
    expect(recorded.state).toBe('MEASURED');
    expect(recorded.rate).toBe('0.5');
    expect(recorded.wonCount).toBe(1);
    expect(recorded.lostCount).toBe(2);
    expect(recorded.denominator).toBe(3);
    expect(recorded.failedOrRebidCount).toBe(4);
    expect(recorded.undecidedCount).toBe(5);
  });

  it('대사 축이 reconciled* 일곱 칸을 그대로 싣는다', () => {
    const [, reconciled] = rateAxesOf(aggregate());

    expect(reconciled.id).toBe('reconciled');
    expect(reconciled.state).toBe('ABSENT_NO_DECIDED_CASES');
    expect(reconciled.rate).toBeNull();
    expect(reconciled.wonCount).toBe(11);
    expect(reconciled.lostCount).toBe(12);
    expect(reconciled.denominator).toBe(13);
    expect(reconciled.failedOrRebidCount).toBe(14);
    expect(reconciled.undecidedCount).toBe(15);
  });

  /** 축이 **둘**이다 — 하나를 지우는 변이가 여기서 멈춘다. */
  it('언제나 두 축이고 라벨이 서로 다르다', () => {
    const axes = rateAxesOf(aggregate());

    expect(axes).toHaveLength(2);
    expect(axes[0].label).not.toBe(axes[1].label);
  });

  /**
   * **비율 문자열을 그대로 나른다**(D-7E3b-8). 서버가 뒤 0 을 떼어 `"1"`·`"0.5"` 로 보낸다(실측) —
   * 화면이 `Number()` 로 접으면 배정밀도 보호가 사라지고, 퍼센트로 바꾸면 화면이 계산한 것이다.
   */
  it('비율은 서버 문자열 그대로다 — 숫자로 바꾸거나 퍼센트로 환산하지 않는다', () => {
    const [recorded] = rateAxesOf(aggregate({ recordedAwardRate: '1' }));

    expect(recorded.rate).toBe('1');
    expect(typeof recorded.rate).toBe('string');
  });

  /** 분모 0 이면 비율 값이 **없다** — 0 을 지어내지 않는다. */
  it('부재 축은 비율이 null 이다', () => {
    const [recorded] = rateAxesOf(
      aggregate({ recordedRateState: 'ABSENT_NO_RECORDED_BIDS', recordedAwardRate: null }),
    );

    expect(recorded.rate).toBeNull();
  });
});

describe('표본 정의 문면 (D-7E3b-7 ⒟)', () => {
  /**
   * 집계의 표본은 **화면의 카드 수가 아니다**(실측: 표본 3 · 카드 2). 그 문면이 없으면 운영자가
   * 카드를 세어 검산하다 어긋난다.
   */
  it('표본이 카드 수가 아님을 말한다', () => {
    expect(SAMPLE_NOTE.length).toBeGreaterThan(0);
    expect(SAMPLE_NOTE).toContain('카드');
  });
});
