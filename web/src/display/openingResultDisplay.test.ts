import { describe, expect, it } from 'vitest';

import type { OpeningResultView } from '../api/queries';

import {
  OPENING_RESULT_STATE_DISPLAY,
  PROVISIONAL_RANK_ONE_DISPLAY,
  confirmedAwardReading,
  provisionalRankOneReading,
  type OpeningResultState,
  type ProvisionalRankOneState,
} from './openingResultDisplay';

/**
 * 계약 enum 을 **축어로** 적는다(`worksheetDisplay.test.ts` 와 같은 관례). 표에서 키를 뽑아
 * 자기 자신과 대조하면 등식이 공허하다 — 계약이 늘면 이 목록이 먼저 틀려야 한다. 타입 쪽은
 * 모듈의 `satisfies` 가 들고(`tsc` 가 CI 게이트다) 이 목록은 **값 쪽**의 같은 주장이다.
 */
const CONTRACT_STATES: readonly OpeningResultState[] = [
  'NOTICE_ABSENT',
  'NOT_OPENED_YET',
  'NOT_COLLECTED',
  'OBSERVED_NOT_PROMOTED',
  'OPENED',
  'FAILED_BID',
  'REBID',
  'PROGRESS_UNRECOGNIZED',
  'PROGRESS_ABSENT',
];

const CONTRACT_PROVISIONAL: readonly ProvisionalRankOneState[] = [
  'NOT_OBSERVED',
  'RANK_MISSING',
  'RANK_DUPLICATED',
  'DETERMINED',
];

/** 아무 칸도 없는 답 — 각 test 가 **자기가 재는 칸만** 채운다. */
function view(overrides: Partial<OpeningResultView> = {}): OpeningResultView {
  return {
    noticeNumber: 'SYN-7E3-RESULT',
    noticeRound: '000',
    state: 'NOT_OPENED_YET',
    participantCount: null,
    actualOpeningAt: null,
    observedAt: null,
    confirmedAwardAmountWon: null,
    confirmedAwardAmountCurrency: null,
    confirmedAwardAmountBasis: null,
    confirmedAwardAmountVatTreatment: null,
    confirmedAwardAmountProvenance: null,
    provisionalRankOneState: 'NOT_OBSERVED',
    provisionalRankOneDuplicateCount: null,
    provisionalRankOneBidAmountWon: null,
    provisionalRankOneBidAmountCurrency: null,
    provisionalRankOneBidAmountVatTreatment: null,
    provisionalRankOneBidRateFraction: null,
    provisionalRankOneObservedAt: null,
    ...overrides,
  };
}

const CONFIRMED_FIELDS = {
  confirmedAwardAmountWon: 1_350_000_000,
  confirmedAwardAmountCurrency: 'KRW',
  confirmedAwardAmountBasis: 'BASE_AMOUNT',
  confirmedAwardAmountVatTreatment: 'INCLUSIVE',
  confirmedAwardAmountProvenance: 'PUBLISHED',
} as const;

const PROVISIONAL_FIELDS = {
  provisionalRankOneState: 'DETERMINED',
  provisionalRankOneBidAmountWon: 1_310_000_000,
  provisionalRankOneBidAmountCurrency: 'KRW',
  provisionalRankOneBidAmountVatTreatment: 'INCLUSIVE',
  provisionalRankOneBidRateFraction: '0.87745',
  provisionalRankOneObservedAt: '2026-11-02T01:00:00Z',
} as const;

describe('개찰 결과의 아홉 갈래 — 접지 않는다', () => {
  it('표의 키 집합이 계약 전수 아홉과 같다', () => {
    expect(Object.keys(OPENING_RESULT_STATE_DISPLAY).sort()).toStrictEqual([...CONTRACT_STATES].sort());
  });

  /**
   * **아홉이 서로 다른 얼굴이어야 한다**(D-7E3a-7). 「데이터가 없다 · 결과가 없다 · 아직 개찰
   * 전이다」를 같은 문면으로 적으면 아홉 갈래를 둔 계약이 화면에서 한 갈래로 접힌다 — 그 접힘은
   * 키 집합 등식으로는 보이지 않는다.
   */
  it('아홉의 라벨이 서로 다르다 — 한 얼굴로 접히지 않는다', () => {
    const labels = CONTRACT_STATES.map((state) => OPENING_RESULT_STATE_DISPLAY[state].label);

    expect(new Set(labels).size, `접힌 라벨이 있다: ${labels.join(' · ')}`).toBe(CONTRACT_STATES.length);
  });

  /** 「모른다」 갈래는 **통과로도 미달로도** 읽히면 안 된다 — 어조가 그 사실을 나른다. */
  it('모름·미승격 갈래의 어조가 good/bad 가 아니다', () => {
    for (const state of ['NOTICE_ABSENT', 'NOT_COLLECTED', 'OBSERVED_NOT_PROMOTED', 'PROGRESS_UNRECOGNIZED', 'PROGRESS_ABSENT'] as const) {
      expect(['absent', 'unjudged', 'neutral'], `${state} 의 어조`).toContain(
        OPENING_RESULT_STATE_DISPLAY[state].tone,
      );
    }
  });

  it('잠정 1위 축의 표도 계약 전수 넷이고 라벨이 서로 다르다', () => {
    expect(Object.keys(PROVISIONAL_RANK_ONE_DISPLAY).sort()).toStrictEqual([...CONTRACT_PROVISIONAL].sort());

    const labels = CONTRACT_PROVISIONAL.map((one) => PROVISIONAL_RANK_ONE_DISPLAY[one].label);
    expect(new Set(labels).size).toBe(CONTRACT_PROVISIONAL.length);
  });
});

describe('잠정과 확정은 다른 칸이다 (SET-02)', () => {
  /**
   * **이 slice 가 막는 핵심 오표시**: 잠정 1위 금액이 확정 낙찰가 자리에 서는 것. 두 읽기가
   * 각각 **자기 접두의 칸만** 보므로 한쪽 값이 다른 쪽의 대체값이 될 길이 구조적으로 없다.
   */
  it('잠정만 관측된 답에서 확정 낙찰가는 ABSENT 다 — 잠정으로 메우지 않는다', () => {
    const reading = confirmedAwardReading(view(PROVISIONAL_FIELDS));

    expect(reading.kind).toBe('ABSENT');
  });

  it('확정만 있는 답에서 잠정 1위 금액은 ABSENT 다 — 확정을 잠정으로 보이지 않는다', () => {
    const reading = provisionalRankOneReading(view(CONFIRMED_FIELDS));

    expect(reading.kind).toBe('ABSENT');
  });

  it('둘 다 있으면 둘 다 자기 값을 낸다 — 서로의 값을 섞지 않는다', () => {
    const both = view({ ...CONFIRMED_FIELDS, ...PROVISIONAL_FIELDS, state: 'OPENED' });

    const confirmed = confirmedAwardReading(both);
    const provisional = provisionalRankOneReading(both);

    expect(confirmed).toStrictEqual({
      kind: 'PRESENT',
      value: {
        amountWon: 1_350_000_000,
        currency: 'KRW',
        basis: 'BASE_AMOUNT',
        vatTreatment: 'INCLUSIVE',
        provenance: 'PUBLISHED',
      },
    });
    expect(provisional.kind).toBe('PRESENT');
    expect(provisional.kind === 'PRESENT' ? provisional.value.amountWon : null).toBe(1_310_000_000);
  });
});

describe('금액은 값만 싣지 않는다', () => {
  /**
   * 통화·basis·과세·provenance 가 없는 숫자는 **해석할 수 없는 값**이다(v2-지침서 §4). 화면이
   * 그것을 「1,350,000,000원」으로 그리면 basis 와 과세를 운영자가 짐작하게 된다.
   */
  it('확정 낙찰가의 다섯 칸 중 하나라도 비면 INCOMPLETE 이고 빠진 칸을 말한다', () => {
    const reading = confirmedAwardReading(
      view({ ...CONFIRMED_FIELDS, confirmedAwardAmountProvenance: null }),
    );

    expect(reading.kind).toBe('INCOMPLETE');
    expect(reading.kind === 'INCOMPLETE' ? reading.missing : []).toStrictEqual([
      'confirmedAwardAmountProvenance',
    ]);
  });

  it('잠정 1위 금액의 세 칸 중 하나라도 비면 INCOMPLETE 다', () => {
    const reading = provisionalRankOneReading(
      view({ ...PROVISIONAL_FIELDS, provisionalRankOneBidAmountVatTreatment: null }),
    );

    expect(reading.kind).toBe('INCOMPLETE');
    expect(reading.kind === 'INCOMPLETE' ? reading.missing : []).toStrictEqual([
      'provisionalRankOneBidAmountVatTreatment',
    ]);
  });

  /** 세 갈래가 **서로 다른 답**이다 — 「아직 안 왔다」와 「반쯤 왔다」를 한 얼굴로 접지 않는다. */
  it('아무 칸도 없으면 ABSENT — INCOMPLETE 와 다른 답이다', () => {
    expect(confirmedAwardReading(view()).kind).toBe('ABSENT');
    expect(provisionalRankOneReading(view()).kind).toBe('ABSENT');
  });

  /**
   * 투찰율은 **표기 그대로의 문자열**이다(계약) — JSON 수치의 배정밀도로 소수 자리를 잃지
   * 않으려고 서버가 문자열로 싣는다. 화면이 `Number()` 로 접으면 그 보호가 사라진다.
   */
  it('투찰율은 문자열 그대로 흐른다 — 숫자로 바꾸지 않는다', () => {
    const reading = provisionalRankOneReading(view(PROVISIONAL_FIELDS));

    expect(reading.kind === 'PRESENT' ? reading.value.bidRateFraction : null).toBe('0.87745');
  });

  /** 금액이 없어도 투찰율만 있을 수 있다 — 금액 축의 판정이 그 칸을 삼키지 않는다. */
  it('금액이 없고 투찰율만 있으면 금액은 ABSENT 다', () => {
    const reading = provisionalRankOneReading(view({ provisionalRankOneBidRateFraction: '0.9' }));

    expect(reading.kind).toBe('ABSENT');
  });
});
