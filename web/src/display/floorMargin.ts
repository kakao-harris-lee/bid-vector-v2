import type { components } from '../api/generated/operator-api';

import { VAT_TREATMENT_DISPLAY } from './moneyDisplay';
import type { StateDisplay } from './stateDisplay';

type Response = components['schemas']['BidPriceRequestResponse'];

/**
 * 하한 대비 여유(계약 13C · §6.2 C 4). **이 자리는 화면이 계산한다** — 서버가 그 값을 내는
 * 표면이 계약에 없다(착수 실측). 그래서 두 가지를 구조로 지킨다.
 *
 * ⓐ **근거가 서지 않으면 재지 않는다.** 게시 하한율이 근거로 설 수 있을 때(`USABLE`)만 계산하고,
 *    나머지 셋은 **빈 자리 + 사유**다 — 0% 나 대시로 접지 않는다. `IMPLAUSIBLE` 은 계약이
 *    「값을 고치지 않는다」고 못박은 자리라 그 값으로 계산하는 것 자체가 위반이다.
 * ⓑ **10진 문자열을 부동소수로 접지 않는다.** 하한율은 fraction 의 10진 문자열이고 금액은 원 단위
 *    정수다. `Number(rate)` 로 재면 정확히 하한인 금액이 미달이나 초과로 **뒤집힌다**(짝 test 가
 *    그 두 자리를 든다). 그래서 비교와 비율을 모두 `BigInt` 정수 연산으로 한다.
 *
 * **과세 축이 비교의 전제다.** 기초금액이 부가세 포함인데 투찰 금액을 별도로 적으면 두 수는 같은
 * 기준이 아니고, 그때 여유를 보이면 화면이 **틀린 안심**을 준다.
 */
export type FloorStanding = 'BELOW' | 'AT' | 'ABOVE';

export type FloorMarginReason =
  | 'GROUNDS_NOT_USABLE'
  | 'RATE_NOT_READABLE'
  | 'BASE_AMOUNT_ABSENT'
  | 'BID_AMOUNT_ABSENT'
  | 'BID_AMOUNT_NOT_READABLE'
  | 'VAT_AXIS_UNKNOWN'
  | 'VAT_AXIS_DIFFERS';

export type FloorMargin =
  | { readonly kind: 'measured'; readonly standing: FloorStanding; readonly percentText: string }
  | { readonly kind: 'unmeasurable'; readonly reason: FloorMarginReason };

export const FLOOR_STANDING_DISPLAY = {
  BELOW: {
    label: '하한 미달',
    why: '게시 하한보다 낮은 금액이다 — 이대로 투찰하면 걸러진다',
    tone: 'bad',
  },
  AT: { label: '하한과 같음', why: '게시 하한과 정확히 같은 금액이다', tone: 'unjudged' },
  ABOVE: { label: '하한 초과', why: '게시 하한보다 높은 금액이다', tone: 'neutral' },
} as const satisfies Record<FloorStanding, StateDisplay>;

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
  BASE_AMOUNT_ABSENT: {
    label: '기초금액이 없어 재지 않습니다',
    why: '하한 금액은 기초금액에서 나오므로 그 값이 없으면 셀 것이 없다',
    tone: 'absent',
  },
  BID_AMOUNT_ABSENT: {
    label: '투찰 금액을 적으면 여유를 보여 드립니다',
    tone: 'absent',
  },
  BID_AMOUNT_NOT_READABLE: {
    label: '적힌 투찰 금액이 너무 커서 재지 않습니다',
    why: '원 단위 정수로 정확히 다룰 수 있는 범위를 넘었다',
    tone: 'absent',
  },
  VAT_AXIS_UNKNOWN: {
    label: '과세 구분을 모르는 쪽이 있어 재지 않습니다',
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
 * 값이고(그 표의 선언이 그렇다), 모르는 값끼리 또는 모르는 값과 아는 값을 견주면 비교가 거짓이
 * 된다. 어휘가 늘 때 이 집합이 함께 늘도록 손 목록을 두지 않는다.
 */
const COMPARABLE_VAT: ReadonlySet<string> = new Set(
  Object.entries(VAT_TREATMENT_DISPLAY)
    .filter(([, display]) => display.tone !== 'absent')
    .map(([token]) => token),
);

/** 비율의 10진 표기 — 부호·지수·퍼센트 기호를 받지 않는다(짐작하지 않는다). */
const DECIMAL_FRACTION = /^(\d+)(?:\.(\d+))?$/u;

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
  const numerator = BigInt(digits);
  if (numerator === 0n) {
    return null;
  }
  return { numerator, scale: 10n ** BigInt((matched[2] ?? '').length) };
}

const isWon = (value: number | null | undefined): value is number =>
  value !== null && value !== undefined && Number.isSafeInteger(value) && value >= 0;

/**
 * 퍼센트 글자 — **절삭 방향이 운영자에게 불리한 쪽**이다. 하한 미달은 즉시 걸러지는 자리이므로
 * 여유를 실제보다 **크게** 보이지 않는다: 초과는 내려서(여유를 적게), 미달은 올려서(모자람을
 * 크게) 접는다. 부호는 **정확한 비교**에서 온 자리에서 오므로, 글자가 `0.00` 이어도 「같음」과
 * 「아주 조금 초과」가 갈린다.
 */
function percentTextOf(hundredths: bigint, standing: FloorStanding): string {
  const magnitude = hundredths < 0n ? -hundredths : hundredths;
  const whole = magnitude / 100n;
  const fraction = magnitude % 100n;
  const sign = standing === 'AT' ? '' : standing === 'BELOW' ? '-' : '+';
  return `${sign}${whole}.${fraction.toString().padStart(2, '0')}`;
}

/**
 * 투찰 금액이 게시 하한에서 얼마나 떨어져 있는가. 하한 금액 = 기초금액 × 게시 하한율이고,
 * 여유 = (투찰 금액 − 하한 금액) ÷ 하한 금액 이다(「하한 대비」의 문면 그대로).
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
  const rate = input.rateFraction === null || input.rateFraction === undefined ? null : scaledFractionOf(input.rateFraction);
  if (rate === null) {
    return unmeasurable('RATE_NOT_READABLE');
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
  const baseVat = input.baseAmountVatTreatment ?? '';
  if (!COMPARABLE_VAT.has(baseVat) || !COMPARABLE_VAT.has(input.bidVatTreatment)) {
    return unmeasurable('VAT_AXIS_UNKNOWN');
  }
  if (baseVat !== input.bidVatTreatment) {
    return unmeasurable('VAT_AXIS_DIFFERS');
  }

  // 나누지 않고 견준다 — 하한 금액은 정수가 아닐 수 있고(소수 자리가 남는다) 그 자리를 버리면
  // 「정확히 하한」과 「1원 미달」이 같아진다.
  const floorScaled = BigInt(input.baseAmountWon) * rate.numerator;
  const bidScaled = BigInt(input.bidAmountWon) * rate.scale;
  const difference = bidScaled - floorScaled;
  const standing: FloorStanding = difference === 0n ? 'AT' : difference < 0n ? 'BELOW' : 'ABOVE';
  const quotient = (difference * 10_000n) / floorScaled;
  const remainder = (difference * 10_000n) % floorScaled;
  const hundredths = standing === 'BELOW' && remainder !== 0n ? quotient - 1n : quotient;

  return { kind: 'measured', standing, percentText: percentTextOf(hundredths, standing) };
}
