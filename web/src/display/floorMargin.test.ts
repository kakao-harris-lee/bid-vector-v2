import { describe, expect, it } from 'vitest';

import {
  CRITICAL_RATE_STANDING_DISPLAY,
  FLOOR_MARGIN_REASON_DISPLAY,
  floorMarginOf,
  type CriticalRateStanding,
  type FloorMarginReason,
} from './floorMargin';
import type { StateDisplay } from './stateDisplay';

/**
 * 하한 대비 여유(계약 13C · §6.2 C 4) — **율 축**이다(verifier H-1 · 팀장 판정 2026-11-18).
 *
 * 저장소 정본이 「하한 제약은 **율 축에서만** 건다 — 게시 하한율은 예정가격 기준이고 투찰율은
 * 기초금액 기준이라 「하한율 × 기초금액」은 폐기된 basis 혼합이다」로 못박았다. 실격을 가르는
 * 낙찰하한가는 **추첨된 예정가격** 기준이라, 투찰 시점에 말할 수 있는 것은 금액 비교가 아니라
 * **임계 사정률**(= 투찰율 ÷ 하한율) 하나다 — 실현 사정률이 그보다 크면 하한 아래가 된다.
 *
 * 커널의 `criticalAssessmentRate` 와 **같은 정의**다(재사용 검토 결과는 evidence 에 있다).
 * 10진 정확 산술은 앞 판 그대로다.
 */
const USABLE = 'USABLE';

const measurable = {
  groundsState: USABLE,
  rateFraction: '0.8',
  baseAmountWon: 1_000_000_000,
  baseAmountVatTreatment: 'INCLUSIVE',
  bidAmountWon: 820_000_000,
  bidVatTreatment: 'INCLUSIVE',
} as const;

describe('재지 않는 자리 — 근거가 서지 않으면 빈 자리다', () => {
  it.each(['ABSENT', 'IMPLAUSIBLE', 'ORIGIN_NOT_PUBLISHED'])('%s 상태에서는 재지 않는다', (state) => {
    expect(floorMarginOf({ ...measurable, groundsState: state })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'GROUNDS_NOT_USABLE',
    });
  });

  it.each([
    ['없음', null],
    ['빈 글자', ''],
    ['퍼센트 꼴', '87%'],
    ['지수 꼴', '8.7e-1'],
    ['부호', '-0.87'],
    ['글자', '팔십칠'],
  ])('하한율이 %s 이면 읽지 못한 것으로 둔다', (_label, rate) => {
    expect(floorMarginOf({ ...measurable, rateFraction: rate })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'RATE_NOT_READABLE',
    });
  });

  /**
   * **비율 범위를 벗어난 값은 다른 답이다**(cr M-4 · L-2). `'87'` 은 표기로는 읽히지만 낙찰하한율이
   * 될 수 없는 수이고, 그대로 쓰면 모든 공고가 **가장 강한 거짓 경고**를 받는다. `'0'` 도 표기로는
   * 읽히지만 임계 자체가 정의되지 않는다(커널도 하한율 0 을 `Unmeasurable` 로 낸다).
   */
  it.each([
    ['1 초과 정수', '87'],
    ['1 초과 소수', '1.5'],
    ['0', '0'],
    ['0 소수 꼴', '0.0'],
  ])('하한율이 %s 이면 범위 밖으로 가른다', (_label, rate) => {
    expect(floorMarginOf({ ...measurable, rateFraction: rate })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'RATE_OUT_OF_RANGE',
    });
  });

  it('하한율 1 은 범위 안이다 — 경계값을 버리지 않는다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '1', bidAmountWon: 1_000_000_000 });

    expect(result.kind).toBe('measured');
  });

  it.each([
    ['없음', null],
    ['0', 0],
    ['음수', -1],
    ['정수가 아님', 1_000.5],
    ['안전 정수 밖', Number.MAX_SAFE_INTEGER + 2],
  ])('기초금액이 %s 이면 재지 않는다 — 투찰율을 낼 수 없다', (_label, base) => {
    expect(floorMarginOf({ ...measurable, baseAmountWon: base })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'BASE_AMOUNT_ABSENT',
    });
  });

  it('투찰 금액을 적지 않았으면 그 사실을 말한다', () => {
    expect(floorMarginOf({ ...measurable, bidAmountWon: null })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'BID_AMOUNT_ABSENT',
    });
  });

  it('투찰 금액이 안전 정수 밖이면 읽지 못한 것으로 둔다', () => {
    expect(floorMarginOf({ ...measurable, bidAmountWon: Number.MAX_SAFE_INTEGER + 2 })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'BID_AMOUNT_NOT_READABLE',
    });
  });

  /** **고르지 않은 것과 모르는 것은 다른 답이다**(cr L-6) — 운영자가 할 일이 다르다. */
  it('과세 구분을 아직 고르지 않았으면 그 사실을 말한다', () => {
    expect(floorMarginOf({ ...measurable, bidVatTreatment: '' })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'BID_VAT_NOT_CHOSEN',
    });
  });

  it.each([
    ['상류가 모름', 'UNKNOWN'],
    ['상류가 안 보냄', null],
    ['표 밖 값', 'NET_OF_SOMETHING'],
  ])('기초금액의 과세 축이 %s 이면 재지 않는다', (_label, baseVat) => {
    expect(floorMarginOf({ ...measurable, baseAmountVatTreatment: baseVat })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'VAT_AXIS_UNKNOWN',
    });
  });

  it('과세 축이 서로 다르면 사유가 다르다', () => {
    expect(floorMarginOf({ ...measurable, bidVatTreatment: 'EXCLUSIVE' })).toStrictEqual({
      kind: 'unmeasurable',
      reason: 'VAT_AXIS_DIFFERS',
    });
  });

  it('근거 상태가 서지 않으면 다른 축이 비어 있어도 그 사유를 말한다', () => {
    expect(
      floorMarginOf({
        ...measurable,
        groundsState: 'ABSENT',
        rateFraction: null,
        baseAmountWon: null,
        bidAmountWon: null,
      }),
    ).toStrictEqual({ kind: 'unmeasurable', reason: 'GROUNDS_NOT_USABLE' });
  });
});

describe('재는 자리 — 율 축으로 말한다 (verifier H-1)', () => {
  /**
   * **금액 비교를 내지 않는다.** 결과가 나르는 것은 투찰율 · 하한율 · 임계 사정률이고, 하한
   * **금액**(기초금액 × 하한율)은 어디에도 없다 — 그 곱이 폐기된 basis 혼합이다.
   */
  it('결과가 율 축 셋을 나르고 하한 금액을 나르지 않는다', () => {
    const result = floorMarginOf(measurable);

    expect(result).toStrictEqual({
      kind: 'measured',
      standing: 'ABOVE_ONE',
      bidRateText: '0.8200',
      floorRateText: '0.8',
      criticalRateText: '1.0250',
      marginPercentText: '+2.50',
    });
  });

  /** 임계 사정률 = 투찰율 ÷ 하한율(커널 `criticalAssessmentRate` 와 같은 정의). */
  it.each([
    ['임계가 1 보다 크다', 820_000_000, 'ABOVE_ONE', '1.0250', '+2.50'],
    ['임계가 정확히 1 이다', 800_000_000, 'AT_ONE', '1.0000', '0.00'],
    ['임계가 1 보다 작다', 780_000_000, 'BELOW_ONE', '0.9750', '-2.50'],
  ])('%s', (_label, bid, standing, critical, percent) => {
    expect(floorMarginOf({ ...measurable, bidAmountWon: bid })).toMatchObject({
      kind: 'measured',
      standing,
      criticalRateText: critical,
      marginPercentText: percent,
    });
  });

  /**
   * **부동소수로 접으면 답이 뒤집히는 자리**(앞 판에서 이어받은 대조). `100 × 0.07` 은 7 보다 크게,
   * `100 × 0.29` 는 29 보다 작게 나온다 — 정확한 답은 둘 다 임계 1 이다.
   */
  it.each([
    ['부동소수가 미달이라고 말하는 자리', '0.07', 7],
    ['부동소수가 초과라고 말하는 자리', '0.29', 29],
  ])('%s — 임계가 정확히 1 이다', (_label, rate, bid) => {
    const result = floorMarginOf({ ...measurable, rateFraction: rate, baseAmountWon: 100, bidAmountWon: bid });

    expect(result).toMatchObject({ kind: 'measured', standing: 'AT_ONE', criticalRateText: '1.0000' });
  });

  it('소수 다섯 자리 하한율에서도 정확하다 — 아주 작은 차이가 0 으로 접히지 않는다', () => {
    const result = floorMarginOf({
      ...measurable,
      rateFraction: '0.86745',
      baseAmountWon: 1_234_567_890,
      bidAmountWon: 1_070_925_916,
    });

    expect(result).toMatchObject({ kind: 'measured', standing: 'BELOW_ONE', marginPercentText: '-0.01' });
  });

  /** 절삭 방향은 **운영자에게 불리한 쪽**이다 — 여유는 내리고 모자람은 올린다. */
  it('1 원 차이도 상태로는 갈린다', () => {
    expect(floorMarginOf({ ...measurable, bidAmountWon: 800_000_001 })).toMatchObject({
      standing: 'ABOVE_ONE',
      marginPercentText: '+0.00',
    });
    expect(floorMarginOf({ ...measurable, bidAmountWon: 799_999_999 })).toMatchObject({
      standing: 'BELOW_ONE',
      marginPercentText: '-0.01',
    });
  });

  /** 투찰율은 **금액에서 나온 율**이고 하한율은 **서버가 준 원문 그대로**다(고쳐 적지 않는다). */
  it('하한율은 원문 그대로 나르고 투찰율은 자리를 맞춰 낸다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.86745' });

    expect(result).toMatchObject({ floorRateText: '0.86745', bidRateText: '0.8200' });
  });
});

describe('표 — 전수와 문면', () => {
  it('자리 표의 키가 전수다', () => {
    const standings: readonly CriticalRateStanding[] = ['BELOW_ONE', 'AT_ONE', 'ABOVE_ONE'];

    expect(Object.keys(CRITICAL_RATE_STANDING_DISPLAY).sort()).toStrictEqual([...standings].sort());
  });

  it('사유 표의 키가 전수다', () => {
    const reasons: readonly FloorMarginReason[] = [
      'GROUNDS_NOT_USABLE',
      'RATE_NOT_READABLE',
      'RATE_OUT_OF_RANGE',
      'BASE_AMOUNT_ABSENT',
      'BID_AMOUNT_ABSENT',
      'BID_AMOUNT_NOT_READABLE',
      'BID_VAT_NOT_CHOSEN',
      'VAT_AXIS_UNKNOWN',
      'VAT_AXIS_DIFFERS',
    ];

    expect(Object.keys(FLOOR_MARGIN_REASON_DISPLAY).sort()).toStrictEqual([...reasons].sort());
  });

  /**
   * **단정하지 않는다**(verifier H-1). 실격을 가르는 하한가는 **추첨된 예정가격** 기준이라,
   * 투찰 시점에 「걸러진다」·「실격」을 말하면 양쪽으로 틀린 판정을 보이게 된다.
   */
  it('자리 문면이 실격을 단정하지 않는다', () => {
    for (const standing of Object.keys(CRITICAL_RATE_STANDING_DISPLAY) as CriticalRateStanding[]) {
      const shown: StateDisplay = CRITICAL_RATE_STANDING_DISPLAY[standing];
      const text = `${shown.label} ${shown.why ?? ''}`;

      expect(text, `${standing} 가 실격을 단정한다`).not.toMatch(/걸러진|실격|탈락/u);
    }
  });

  /** 그 대신 **무엇이 결정하는지**를 말한다 — 추첨된 예정가격이다. */
  it('자리 문면이 예정가격 추첨을 말한다', () => {
    const texts = (Object.keys(CRITICAL_RATE_STANDING_DISPLAY) as CriticalRateStanding[]).map(
      (one) => `${CRITICAL_RATE_STANDING_DISPLAY[one].label} ${CRITICAL_RATE_STANDING_DISPLAY[one].why ?? ''}`,
    );

    expect(texts.every((one) => /예정가격/u.test(one))).toBe(true);
  });

  it('사유 문면이 0% 나 대시로 읽히지 않는다', () => {
    for (const reason of Object.keys(FLOOR_MARGIN_REASON_DISPLAY) as FloorMarginReason[]) {
      const shown: StateDisplay = FLOOR_MARGIN_REASON_DISPLAY[reason];

      expect(`${shown.label} ${shown.why ?? ''}`).not.toMatch(/0\s*%/u);
      expect(shown.label).not.toMatch(/^[-—–]$/u);
    }
  });
});
