import type { components } from '../api/generated/operator-api';

import { VAT_TREATMENT_DISPLAY } from './moneyDisplay';
import type { StateDisplay } from './stateDisplay';

type Response = components['schemas']['BidPriceRequestResponse'];

/**
 * 하한 대비 여유(계약 13C · §6.2 C 4) — **율 축**이다(verifier H-1 · 팀장 판정 2026-11-18).
 *
 * 저장소 정본이 「**하한 제약은 율 축에서만 건다** — 게시 하한율은 예정가격 기준이고 투찰율은
 * 기초금액 기준이라 「하한율 × 기초금액」은 폐기된 basis 혼합이다」로 못박았다. 실격을 가르는
 * 낙찰하한가는 **추첨된 예정가격** 기준이라, 투찰 시점에 금액으로 「미달/초과」를 단정하면 추첨
 * 결과에 따라 **양쪽으로 틀린 판정**을 보이게 된다.
 *
 * 그래서 이 자리가 내는 것은 **임계 사정률** 하나다 — 커널 `criticalAssessmentRate` 와 같은
 * 정의(`임계 = 투찰율 ÷ 하한율`)이고, 하한 미달 ⟺ 실현 사정률 > 임계다. 커널을 **부를 수는 없고**
 * (화면은 Kotlin 을 지나지 않는다) 서버가 그 값을 내는 표면도 계약에 없다 — 그래서 정의와 규칙만
 * 그대로 가져온다: 하한율 0 은 임계가 정의되지 않고(커널도 같은 자리에서 측정 불가를 낸다),
 * 자리수와 절삭은 **선언된 정책**이지 지어낸 값이 아니다.
 *
 * 10진 정확 산술은 앞 판 그대로다 — `Number(rate)` 로 접으면 정확히 경계인 금액이 양쪽으로
 * 뒤집힌다(짝 test 의 두 표본).
 */
export type CriticalRateStanding = 'BELOW_ONE' | 'AT_ONE' | 'ABOVE_ONE';

export type FloorMarginReason =
  | 'GROUNDS_NOT_USABLE'
  | 'RATE_NOT_READABLE'
  | 'RATE_OUT_OF_RANGE'
  | 'BASE_AMOUNT_ABSENT'
  | 'BID_AMOUNT_ABSENT'
  | 'BID_AMOUNT_NOT_READABLE'
  | 'BID_VAT_NOT_CHOSEN'
  | 'VAT_AXIS_UNKNOWN'
  | 'VAT_AXIS_DIFFERS';

export type FloorMargin =
  | {
      readonly kind: 'measured';
      readonly standing: CriticalRateStanding;
      /** 투찰율 = 투찰 금액 ÷ 기초금액. 이 화면이 **금액에서 낸** 율이다. */
      readonly bidRateText: string;
      /** 게시 하한율 — 서버가 준 **원문 그대로**다(고쳐 적지 않는다). */
      readonly floorRateText: string;
      /** 임계 사정률 = 투찰율 ÷ 하한율. 실현 사정률이 이보다 크면 하한 아래가 된다. */
      readonly criticalRateText: string;
      /** 임계가 1 에서 떨어진 정도(%) — 「예정가격이 얼마나 높게 뽑혀도 되는가」다. */
      readonly marginPercentText: string;
    }
  | { readonly kind: 'unmeasurable'; readonly reason: FloorMarginReason };

/**
 * 자리 문면 — **실격을 단정하지 않는다**. 투찰 시점에 아는 것은 「추첨이 어디까지 가면 하한
 * 아래인가」이고 거기까지만 말한다(짝 test 가 단정 어휘 금지와 「예정가격」 언급을 잰다).
 */
export const CRITICAL_RATE_STANDING_DISPLAY = {
  BELOW_ONE: {
    label: '예정가격이 기초금액과 같아도 하한 아래',
    why: '낙찰하한가는 추첨된 예정가격에서 나온다 — 이 금액은 그 추첨이 기초금액과 같게 뽑혀도 하한에 못 미친다',
    tone: 'bad',
  },
  AT_ONE: {
    label: '예정가격이 기초금액과 같으면 하한과 같음',
    why: '추첨된 예정가격이 기초금액보다 조금이라도 높으면 하한 아래가 된다',
    tone: 'unjudged',
  },
  ABOVE_ONE: {
    label: '예정가격이 임계보다 높게 뽑히면 하한 아래',
    why: '낙찰하한가는 추첨된 예정가격에서 나온다 — 추첨이 아래 임계 사정률을 넘으면 하한에 못 미친다',
    tone: 'neutral',
  },
} as const satisfies Record<CriticalRateStanding, StateDisplay>;

/**
 * 재지 않는 사유 — **무엇이 없어서 못 재는지**를 말한다. 「0%」가 아니라 「재지 않았다」이고,
 * 사유마다 운영자가 할 일이 다르다(금액을 적는 것 · 과세를 고르는 것 · 아무것도 할 수 없는 것).
 */
export const FLOOR_MARGIN_REASON_DISPLAY = {
  GROUNDS_NOT_USABLE: {
    label: '게시 하한율을 근거로 쓸 수 없어 재지 않습니다',
    why: '위 근거의 게시 낙찰하한율 자리가 그 사유를 말한다',
    tone: 'absent',
  },
  RATE_NOT_READABLE: {
    label: '게시 하한율을 읽지 못해 재지 않습니다',
    why: '값이 비율의 10진 표기가 아니라 짐작하지 않는다',
    tone: 'absent',
  },
  RATE_OUT_OF_RANGE: {
    label: '게시 하한율이 비율 범위를 벗어나 재지 않습니다',
    why: '0 보다 크고 1 이하가 아닌 값은 낙찰하한율이 될 수 없다 — 그 값으로 재면 모든 공고에 틀린 경고가 선다',
    tone: 'absent',
  },
  BASE_AMOUNT_ABSENT: {
    label: '기초금액이 없어 재지 않습니다',
    why: '투찰율은 기초금액에서 나오므로 그 값이 없으면 셀 것이 없다',
    tone: 'absent',
  },
  BID_AMOUNT_ABSENT: { label: '투찰 금액을 적으면 여유를 보여 드립니다', tone: 'absent' },
  BID_AMOUNT_NOT_READABLE: {
    label: '적힌 투찰 금액이 너무 커서 재지 않습니다',
    why: '원 단위 정수로 정확히 다룰 수 있는 범위를 넘었다',
    tone: 'absent',
  },
  BID_VAT_NOT_CHOSEN: {
    label: '과세 구분을 고르면 여유를 보여 드립니다',
    why: '기초금액과 같은 과세 기준인지 확인해야 비교가 뜻을 갖는다',
    tone: 'absent',
  },
  VAT_AXIS_UNKNOWN: {
    label: '기초금액의 과세 구분을 몰라 재지 않습니다',
    why: '두 금액이 같은 과세 기준인지 확인할 수 없으면 비교가 뜻을 잃는다',
    tone: 'absent',
  },
  VAT_AXIS_DIFFERS: {
    label: '두 금액의 과세 구분이 서로 달라 재지 않습니다',
    why: '부가세 포함과 별도를 바로 견주면 그 차이만큼 틀린 여유가 나온다',
    tone: 'absent',
  },
} as const satisfies Record<FloorMarginReason, StateDisplay>;

/**
 * 근거로 쓸 수 있는 상태 — **계약 enum 에 묶는다.** 상태 이름이 바뀌면 이 선언이 컴파일에서
 * 멈춘다(글자를 조건식에 흩뿌리지 않는다).
 */
const GROUNDS_USABLE: Response['floorRateGroundsState'] = 'USABLE';

/**
 * 견줄 수 있는 과세 축 — **표에서 유도한다.** 부재 어조(`absent`)인 값은 「모른다」를 말하는
 * 값이고, 모르는 값끼리 또는 모르는 값과 아는 값을 견주면 비교가 거짓이 된다.
 */
const COMPARABLE_VAT: ReadonlySet<string> = new Set(
  Object.entries(VAT_TREATMENT_DISPLAY)
    .filter(([, display]) => display.tone !== 'absent')
    .map(([token]) => token),
);

/** 비율의 10진 표기 — 부호·지수·퍼센트 기호를 받지 않는다(짐작하지 않는다). */
const DECIMAL_FRACTION = /^(\d+)(?:\.(\d+))?$/u;

/**
 * 보이는 자리수 — **선언된 정책**이다(커널이 자리수·반올림을 호출부 정책으로 받는 것과 같은
 * 규율). 율은 네 자리, 퍼센트는 두 자리이고 **절삭은 언제나 내림**이다: 임계가 작게 보이는 쪽이
 * 운영자에게 불리한 쪽이라, 추첨이 견디는 폭을 실제보다 크게 말하지 않는다.
 */
const RATE_DIGITS = 4n;
const PERCENT_DIGITS = 2n;

interface Scaled {
  readonly numerator: bigint;
  readonly scale: bigint;
}

/** `0.86745` → 분자 86745 · 자리 10^5. 읽을 수 없으면 `null`. */
function scaledFractionOf(raw: string): Scaled | null {
  const matched = DECIMAL_FRACTION.exec(raw);
  if (matched === null) {
    return null;
  }
  const digits = `${matched[1]}${matched[2] ?? ''}`;
  return { numerator: BigInt(digits), scale: 10n ** BigInt((matched[2] ?? '').length) };
}

const isWon = (value: number | null | undefined): value is number =>
  value !== null && value !== undefined && Number.isSafeInteger(value) && value >= 0;

/** 내림 나눗셈 — `BigInt` 의 `/` 는 0 쪽으로 자르므로 음수에서 방향이 갈린다. */
function floorDiv(numerator: bigint, denominator: bigint): bigint {
  const quotient = numerator / denominator;
  const negative = numerator < 0n !== denominator < 0n;
  return numerator % denominator !== 0n && negative ? quotient - 1n : quotient;
}

/** 자리를 올린 정수를 10진 글자로 — 부호는 호출자가 정한다. */
function decimalTextOf(scaled: bigint, digits: bigint): string {
  const unit = 10n ** digits;
  const magnitude = scaled < 0n ? -scaled : scaled;
  const sign = scaled < 0n ? '-' : '';
  return `${sign}${magnitude / unit}.${(magnitude % unit).toString().padStart(Number(digits), '0')}`;
}

/** 퍼센트 글자 — 자리는 **정확한 비교**에서 오므로 글자가 `0.00` 이어도 자리가 갈린다. */
function percentTextOf(hundredths: bigint, standing: CriticalRateStanding): string {
  const magnitude = decimalTextOf(hundredths < 0n ? -hundredths : hundredths, PERCENT_DIGITS);
  if (standing === 'AT_ONE') {
    return magnitude;
  }
  return `${standing === 'BELOW_ONE' ? '-' : '+'}${magnitude}`;
}

/**
 * 투찰 금액이 **추첨을 얼마나 견디는가**. 임계 사정률 = (투찰 금액 ÷ 기초금액) ÷ 게시 하한율이고,
 * 실현 사정률이 그보다 크면 투찰가가 낙찰하한가 아래가 된다.
 *
 * 사유의 순서는 **계산의 전제 순서**다 — 근거 상태 → 하한율 → 기초금액 → 투찰 금액 → 과세 축.
 */
export function floorMarginOf(input: {
  readonly groundsState: string;
  readonly rateFraction: string | null | undefined;
  readonly baseAmountWon: number | null | undefined;
  readonly baseAmountVatTreatment: string | null | undefined;
  readonly bidAmountWon: number | null;
  readonly bidVatTreatment: string;
}): FloorMargin {
  const unmeasurable = (reason: FloorMarginReason): FloorMargin => ({ kind: 'unmeasurable', reason });

  if (input.groundsState !== GROUNDS_USABLE) {
    return unmeasurable('GROUNDS_NOT_USABLE');
  }
  const rate =
    input.rateFraction === null || input.rateFraction === undefined ? null : scaledFractionOf(input.rateFraction);
  if (rate === null) {
    return unmeasurable('RATE_NOT_READABLE');
  }
  // **비율 범위 밖은 읽기 실패와 다른 답이다**(cr M-4 · L-2): 표기로는 읽혔지만 낙찰하한율이 될 수
  // 없는 수다. 0 은 임계 자체가 정의되지 않는다(커널도 같은 자리에서 측정 불가를 낸다).
  if (rate.numerator === 0n || rate.numerator > rate.scale) {
    return unmeasurable('RATE_OUT_OF_RANGE');
  }
  if (!isWon(input.baseAmountWon) || input.baseAmountWon === 0) {
    return unmeasurable('BASE_AMOUNT_ABSENT');
  }
  if (input.bidAmountWon === null) {
    return unmeasurable('BID_AMOUNT_ABSENT');
  }
  if (!isWon(input.bidAmountWon)) {
    return unmeasurable('BID_AMOUNT_NOT_READABLE');
  }
  // **고르지 않은 것과 모르는 것은 다른 답이다**(cr L-6) — 앞은 운영자가 할 일이 있고 뒤는 없다.
  if (input.bidVatTreatment === '') {
    return unmeasurable('BID_VAT_NOT_CHOSEN');
  }
  const baseVat = input.baseAmountVatTreatment ?? '';
  if (!COMPARABLE_VAT.has(baseVat) || !COMPARABLE_VAT.has(input.bidVatTreatment)) {
    return unmeasurable('VAT_AXIS_UNKNOWN');
  }
  if (baseVat !== input.bidVatTreatment) {
    return unmeasurable('VAT_AXIS_DIFFERS');
  }

  /**
   * **금액을 곱하지 않는다.** 두 율을 같은 자리로 올려 견준다 — 임계 ⋛ 1 은
   * `투찰금액 × 하한율자리 ⋛ 기초금액 × 하한율분자` 와 같은 비교이고, 어느 쪽도 하한 **금액**을
   * 짓지 않는다(그 곱이 폐기된 basis 혼합이다).
   */
  const bid = BigInt(input.bidAmountWon);
  const base = BigInt(input.baseAmountWon);
  const bidScaled = bid * rate.scale;
  const floorScaled = base * rate.numerator;
  const difference = bidScaled - floorScaled;
  const standing: CriticalRateStanding =
    difference === 0n ? 'AT_ONE' : difference < 0n ? 'BELOW_ONE' : 'ABOVE_ONE';

  const rateUnit = 10n ** RATE_DIGITS;
  const percentUnit = 10n ** PERCENT_DIGITS;
  return {
    kind: 'measured',
    standing,
    bidRateText: decimalTextOf(floorDiv(bid * rateUnit, base), RATE_DIGITS),
    floorRateText: input.rateFraction ?? '',
    criticalRateText: decimalTextOf(floorDiv(bidScaled * rateUnit, floorScaled), RATE_DIGITS),
    marginPercentText: percentTextOf(floorDiv(difference * 100n * percentUnit, floorScaled), standing),
  };
}
