import { describe, expect, it } from 'vitest';

import {
  FLOOR_MARGIN_REASON_DISPLAY,
  FLOOR_STANDING_DISPLAY,
  floorMarginOf,
  type FloorMarginReason,
  type FloorStanding,
} from './floorMargin';
import type { StateDisplay } from './stateDisplay';

/**
 * 하한 대비 여유(계약 13C · §6.2 C 4). **화면 계산**이고 서버가 그 값을 내는 표면이 계약에 없다.
 * 그래서 이 파일이 재는 것은 둘이다 — **언제 재지 않는가**(근거가 서지 않는 자리는 빈 자리)와
 * **잴 때 정확한가**(10진 문자열을 부동소수로 접지 않는다).
 */
const USABLE = 'USABLE';

/** 재는 자리의 기본 입력 — 각 test 가 한 축만 바꾼다. */
const measurable = {
  groundsState: USABLE,
  rateFraction: '0.87',
  baseAmountWon: 1_000_000_000,
  baseAmountVatTreatment: 'INCLUSIVE',
  bidAmountWon: 900_000_000,
  bidVatTreatment: 'INCLUSIVE',
} as const;

describe('재지 않는 자리 — 근거가 서지 않으면 빈 자리다', () => {
  /**
   * 계약이 못박은 자리다: `USABLE` 이 아닌 셋은 **0% 나 대시로 접지 않는다**. `IMPLAUSIBLE` 은
   * 「값을 고치지 않는다」고 선언된 자리이므로 그 값으로 계산하는 것 자체가 위반이다.
   */
  it.each(['ABSENT', 'IMPLAUSIBLE', 'ORIGIN_NOT_PUBLISHED'])('%s 상태에서는 재지 않는다', (state) => {
    const result = floorMarginOf({ ...measurable, groundsState: state });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'GROUNDS_NOT_USABLE' });
  });

  /** 상태가 `USABLE` 인데 값이 없으면 **상태를 믿고 계산하지 않는다**(지어내지 않는다). */
  it.each([
    ['없음', null],
    ['빈 글자', ''],
    ['퍼센트 꼴', '87%'],
    ['지수 꼴', '8.7e-1'],
    ['부호', '-0.87'],
    ['0', '0'],
    ['글자', '팔십칠'],
  ])('하한율이 %s 이면 읽지 못한 것으로 둔다', (_label, rate) => {
    const result = floorMarginOf({ ...measurable, rateFraction: rate });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'RATE_NOT_READABLE' });
  });

  it.each([
    ['없음', null],
    ['0', 0],
    ['음수', -1],
    ['정수가 아님', 1_000.5],
    ['안전 정수 밖', Number.MAX_SAFE_INTEGER + 2],
  ])('기초금액이 %s 이면 재지 않는다', (_label, base) => {
    const result = floorMarginOf({ ...measurable, baseAmountWon: base });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'BASE_AMOUNT_ABSENT' });
  });

  it('투찰 금액을 적지 않았으면 그 사실을 말한다', () => {
    const result = floorMarginOf({ ...measurable, bidAmountWon: null });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'BID_AMOUNT_ABSENT' });
  });

  /**
   * **적었는데 다룰 수 없는 수**는 「적지 않았다」와 다른 답이다. 금액 입력의 상한은 M7 의 파서가
   * 정하고 이 slice 는 그 축을 바꾸지 않는다 — 그래서 이 자리는 **재지 않는 사유**로 선다.
   */
  it('투찰 금액이 안전 정수 밖이면 읽지 못한 것으로 둔다', () => {
    const result = floorMarginOf({ ...measurable, bidAmountWon: Number.MAX_SAFE_INTEGER + 2 });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'BID_AMOUNT_NOT_READABLE' });
  });

  /**
   * **과세 축이 비교의 전제다.** 기초금액이 부가세 포함인데 투찰 금액을 별도로 적으면 두 수는
   * 같은 기준이 아니다 — 그때 여유를 보이면 화면이 **틀린 안심**을 준다.
   */
  it.each([
    ['고르지 않음', 'INCLUSIVE', ''],
    ['상류가 모름', 'UNKNOWN', 'INCLUSIVE'],
    ['상류가 안 보냄', null, 'INCLUSIVE'],
    ['표 밖 값', 'NET_OF_SOMETHING', 'INCLUSIVE'],
    ['양쪽이 모름', 'UNKNOWN', 'UNKNOWN'],
  ])('과세 축이 %s 이면 재지 않는다', (_label, baseVat, bidVat) => {
    const result = floorMarginOf({
      ...measurable,
      baseAmountVatTreatment: baseVat,
      bidVatTreatment: bidVat,
    });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'VAT_AXIS_UNKNOWN' });
  });

  it('과세 축이 서로 다르면 사유가 다르다 — 「모른다」와 「다르다」는 다른 답이다', () => {
    const result = floorMarginOf({
      ...measurable,
      baseAmountVatTreatment: 'INCLUSIVE',
      bidVatTreatment: 'EXCLUSIVE',
    });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'VAT_AXIS_DIFFERS' });
  });

  /** 사유의 우선순위 — 여럿이 겹치면 **가장 상류의 사유**를 말한다(계산의 전제 순서다). */
  it('근거 상태가 서지 않으면 다른 축이 비어 있어도 그 사유를 말한다', () => {
    const result = floorMarginOf({
      ...measurable,
      groundsState: 'ABSENT',
      rateFraction: null,
      baseAmountWon: null,
      bidAmountWon: null,
    });

    expect(result).toStrictEqual({ kind: 'unmeasurable', reason: 'GROUNDS_NOT_USABLE' });
  });
});

describe('재는 자리 — 10진 문자열을 부동소수로 접지 않는다', () => {
  /**
   * **이 둘이 이 모듈의 존재 이유다.** `base × Number(rate)` 로 재면 두 자리에서 답이 **뒤집힌다**:
   * `100 × 0.07` 은 7 보다 **크게**(7.000000000000001) 나와 정확히 하한인 금액을 **미달**로 말하고,
   * `100 × 0.29` 는 29 보다 **작게** 나와 같은 금액을 **초과**로 말한다. 정확한 답은 둘 다
   * 「하한과 같음」이다.
   */
  it.each([
    ['부동소수가 미달이라고 말하는 자리', '0.07', 7],
    ['부동소수가 초과라고 말하는 자리', '0.29', 29],
  ])('%s — 정확히 하한과 같다', (_label, rate, bid) => {
    const result = floorMarginOf({
      ...measurable,
      rateFraction: rate,
      baseAmountWon: 100,
      bidAmountWon: bid,
    });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'AT', percentText: '0.00' });
  });

  it('하한보다 높으면 초과이고 여유가 양수로 선다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 820_000_000 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'ABOVE', percentText: '+2.50' });
  });

  it('하한보다 낮으면 미달이다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 780_000_000 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'BELOW', percentText: '-2.50' });
  });

  /**
   * **절삭 방향은 운영자에게 불리한 쪽이다.** 하한 미달은 즉시 실격이므로 여유를 실제보다 **크게**
   * 보이지 않는다 — 초과는 내림(여유를 적게), 미달은 올림(모자람을 크게)으로 접는다.
   */
  it('초과의 끝자리는 내려서 — 여유를 실제보다 크게 말하지 않는다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 800_299_999 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'ABOVE', percentText: '+0.03' });
  });

  it('미달의 끝자리는 올려서 — 모자람을 실제보다 작게 말하지 않는다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 799_700_001 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'BELOW', percentText: '-0.04' });
  });

  /**
   * **작은 양수가 「0.00」 하나로 접히지 않는다** — 퍼센트 글자는 절삭이라 0 처럼 보이지만
   * 상태는 정확한 비교에서 오므로 「하한과 같음」과 갈린다.
   */
  it('1원 초과도 상태로는 초과다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 800_000_001 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'ABOVE', percentText: '+0.00' });
  });

  it('1원 미달도 상태로는 미달이다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 799_999_999 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'BELOW', percentText: '-0.01' });
  });

  /**
   * 하한율의 소수 자리가 길어도 **자리 수를 잃지 않는다**(계약의 실제 꼴이 다섯 자리다).
   *
   * 정확한 하한은 1,070,925,916.1805 원이고 투찰 금액은 그보다 0.1805 원 **낮다**. 비율로는
   * 1e-8 % 쯤이지만 미달은 **올려서** 접으므로 글자가 `0.00` 이 되지 않는다 — 「미달인데 0.00%」가
   * 서지 않는 것이 이 자리의 요점이다.
   */
  it('소수 다섯 자리 하한율에서도 정확하고, 미달은 0.00 으로 보이지 않는다', () => {
    const result = floorMarginOf({
      ...measurable,
      rateFraction: '0.86745',
      baseAmountWon: 1_234_567_890,
      bidAmountWon: 1_070_925_916,
    });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'BELOW', percentText: '-0.01' });
  });

  it('정수 하한율(1)도 읽는다 — 소수점이 없는 꼴을 거부하지 않는다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '1', bidAmountWon: 1_000_000_000 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'AT', percentText: '0.00' });
  });

  it('투찰 금액 0 은 적은 값이다 — 재지 않는 자리가 아니다', () => {
    const result = floorMarginOf({ ...measurable, rateFraction: '0.80', bidAmountWon: 0 });

    expect(result).toStrictEqual({ kind: 'measured', standing: 'BELOW', percentText: '-100.00' });
  });
});

describe('표 — 전수와 문면', () => {
  /** 상태 셋과 사유 여섯이 **표에 전수**로 있다(폴백 0). */
  it('상태 표의 키가 상태 전수다', () => {
    const standings: readonly FloorStanding[] = ['BELOW', 'AT', 'ABOVE'];

    expect(Object.keys(FLOOR_STANDING_DISPLAY).sort()).toStrictEqual([...standings].sort());
  });

  it('사유 표의 키가 사유 전수다', () => {
    const reasons: readonly FloorMarginReason[] = [
      'GROUNDS_NOT_USABLE',
      'RATE_NOT_READABLE',
      'BASE_AMOUNT_ABSENT',
      'BID_AMOUNT_ABSENT',
      'BID_AMOUNT_NOT_READABLE',
      'VAT_AXIS_UNKNOWN',
      'VAT_AXIS_DIFFERS',
    ];

    expect(Object.keys(FLOOR_MARGIN_REASON_DISPLAY).sort()).toStrictEqual([...reasons].sort());
  });

  /** **부재는 0% 가 아니다**(저장소 선례). 어느 사유 문면도 0% 로 읽히지 않는다. */
  it('사유 문면이 0% 나 대시로 읽히지 않는다', () => {
    for (const reason of Object.keys(FLOOR_MARGIN_REASON_DISPLAY) as FloorMarginReason[]) {
      const shown: StateDisplay = FLOOR_MARGIN_REASON_DISPLAY[reason];

      expect(`${shown.label} ${shown.why ?? ''}`).not.toMatch(/0\s*%/u);
      expect(shown.label).not.toMatch(/^[-—–]$/u);
    }
  });

  /** 미달은 **어조가 다르다** — 색만으로 말하지 않지만, 색이 틀리면 그것도 거짓말이다. */
  it('미달의 어조가 나머지와 다르다', () => {
    expect(FLOOR_STANDING_DISPLAY.BELOW.tone).toBe('bad');
    expect(FLOOR_STANDING_DISPLAY.AT.tone).not.toBe('bad');
    expect(FLOOR_STANDING_DISPLAY.ABOVE.tone).not.toBe('bad');
  });
});
