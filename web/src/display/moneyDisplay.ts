import type { components } from '../api/generated/operator-api';

import { displayOf, displayOrUnknown, type StateDisplay } from './stateDisplay';

/**
 * 금액 수식어의 표시(M7/7E-3b — r1 CR M-1 · v L-2, 경계는 r2 CR M-3 · v M-2 로 정정).
 *
 * **이 표가 덮는 경계는 둘이다**(측정된 경계를 적는다): ⓐ **화면 E 카드 안**의 과세 표시 네
 * 자리(`ResultCard.tsx` — 내 투찰가 · 확정 낙찰가 둘 · 잠정 1위) ⓑ **화면 C 의 입력 선택지**
 * (`VAT_CHOICES`). 그 둘이 같은 말을 쓰게 하는 것이 이 표의 일이다 — 운영자는 화면 C 에서
 * 「부가세 포함」을 고르는데 화면 E 가 `INCLUSIVE` 를 돌려주고 있었다.
 *
 * 키를 **요청 스키마의 enum** 에서 따온다: 응답 쪽 칸은 계약이 `string` 이라 `satisfies` 가
 * 서지 않지만, 어휘는 같은 축이고 요청 쪽에 전수가 선언돼 있다. 응답의 값을 읽을 때는
 * `displayOrUnknown` 으로 — 표 밖 값이 와도 **보이게** 낸다(조용한 빈 자리는 「서버가 안
 * 보냈다」로 읽힌다).
 *
 * **M9/screen-display 로 경계가 사라졌다**(D-3). 7E-3b 때 「그 밖은 아직 원문 토큰이다」로
 * 등재했던 축(`OPEN-7E3B-MONEY-WORDING`)을 아래 세 표가 받는다 — 금액 부속 토큰은 네 표
 * (basis · 과세 · provenance · 통화)로만 문면이 되고, 표 밖 값은 `displayOrUnknown` 이
 * **보이게** 낸다.
 */
export type VatTreatment = components['schemas']['RecordBidRequest']['vatTreatment'];

export const VAT_TREATMENT_DISPLAY = {
  INCLUSIVE: { label: '부가세 포함', tone: 'neutral' },
  EXCLUSIVE: { label: '부가세 별도', tone: 'neutral' },
  /** 상류가 과세 축을 선언하지 않은 값 — 「0%」가 아니라 **모른다**는 사실이다. */
  UNKNOWN: { label: '과세 구분 모름', tone: 'absent' },
} as const satisfies Record<VatTreatment, StateDisplay>;

/**
 * 응답의 과세 칸을 읽는 **한 자리**. 계약이 그 칸을 `string` 으로 선언하므로(요청 쪽만 enum)
 * 표 조회의 키 좁힘을 여기 한 번만 둔다 — 부르는 자리마다 넓히면 그 자리마다 폴백이 생긴다.
 *
 * 표 밖 값은 `displayOrUnknown` 이 **보이게** 낸다(계약이 늘었는데 번들이 아직 모르는 경우).
 */
export function vatTreatmentDisplayOf(token: string): StateDisplay {
  return displayOrUnknown<string>(VAT_TREATMENT_DISPLAY, token);
}

/**
 * 금액 basis 의 어휘(M9/screen-display D-3). **계약이 이 칸을 `string` 으로 선언하므로**
 * (`NoticeSummary.baseAmountBasis` 등) 생성 타입에서 유니온을 끌어올 수 없다 — 그래서 어휘를
 * 여기 선언하고 `satisfies` 로 전수를 잠근다. 출처는 서버가 이름을 내는 자리
 * (`shared-kernel` 의 `Basis` — `basis.name` 이 wire 값이다).
 *
 * 어휘가 늘면 **표가 아니라 이 유니온이 먼저** 자란다(그 순서가 전수 보증의 방향이다).
 */
export type AmountBasis = 'BASE_AMOUNT' | 'ESTIMATED' | 'YEGA' | 'BID' | 'ALLOCATED_BUDGET' | 'AWARD';

export const AMOUNT_BASIS_DISPLAY = {
  BASE_AMOUNT: { label: '기초금액', tone: 'neutral' },
  ESTIMATED: { label: '추정가격', tone: 'neutral' },
  YEGA: { label: '예정가격', tone: 'neutral' },
  BID: { label: '투찰금액', tone: 'neutral' },
  ALLOCATED_BUDGET: { label: '배정예산', tone: 'neutral' },
  AWARD: { label: '낙찰금액', tone: 'neutral' },
} as const satisfies Record<AmountBasis, StateDisplay>;

/** basis 칸을 읽는 **한 자리** — 계약이 `string` 이라 키 좁힘을 여기 한 번만 둔다. */
export function amountBasisDisplayOf(token: string): StateDisplay {
  return displayOrUnknown<string>(AMOUNT_BASIS_DISPLAY, token);
}

/**
 * 금액 출처의 어휘(D-3). 출처는 `procurement` 의 `ProvenanceKind` 이고 wire 값은
 * `provenanceLabel(…)` 이 내는 그 이름들이다.
 *
 * **어조가 갈린다**: 게시값·운영자 선언값은 사실의 진술(`neutral`)이고, 역산·복사·예산 항목
 * 채움은 **그 값이 그 금액 자신이 아니라는 사실**(`unjudged`)이다 — 미선언은 부재(`absent`).
 */
export type AmountProvenance =
  | 'PUBLISHED'
  | 'DERIVED_FROM_OPENING'
  | 'FILLED_FROM_BUDGET_KEY'
  | 'COPIED_FROM_BASE_AMOUNT'
  | 'OPERATOR_DECLARED'
  | 'UNDECLARED';

export const AMOUNT_PROVENANCE_DISPLAY = {
  PUBLISHED: { label: '공고 게시값', tone: 'neutral' },
  DERIVED_FROM_OPENING: { label: '개찰 역산값', why: '공고가 게시한 값이 아니라 개찰 결과에서 거꾸로 센 값이다', tone: 'unjudged' },
  FILLED_FROM_BUDGET_KEY: { label: '예산 항목에서 채운 값', why: '기초금액 칸이 비어 예산 항목으로 채웠다', tone: 'unjudged' },
  COPIED_FROM_BASE_AMOUNT: { label: '기초금액에서 복사한 값', tone: 'unjudged' },
  OPERATOR_DECLARED: { label: '운영자 선언값', tone: 'neutral' },
  UNDECLARED: { label: '출처 미선언', why: '출처가 「없음」이 아니라 상류가 적지 않았다', tone: 'absent' },
} as const satisfies Record<AmountProvenance, StateDisplay>;

export function amountProvenanceDisplayOf(token: string): StateDisplay {
  return displayOrUnknown<string>(AMOUNT_PROVENANCE_DISPLAY, token);
}

/**
 * 통화 — 어휘가 **하나**다(`shared-kernel` 의 `Currency`). 그래도 표로 둔다: 화면이 `KRW` 를
 * 그대로 그리던 자리가 있었고(`ResultCard` 의 금액 칸), 어휘가 늘 때 자리가 하나여야 한다.
 */
export type AmountCurrency = 'KRW';

export const AMOUNT_CURRENCY_DISPLAY = {
  KRW: { label: '원', tone: 'neutral' },
} as const satisfies Record<AmountCurrency, StateDisplay>;

export function currencyDisplayOf(token: string): StateDisplay {
  return displayOrUnknown<string>(AMOUNT_CURRENCY_DISPLAY, token);
}

/** 금액 글자에 붙는 **단위**와 그 단위를 이 화면이 아는지. */
export interface CurrencyUnit {
  readonly text: string;
  /** 표 안의 통화인가 — 아는 단위는 숫자에 바로 붙고, 모르는 코드는 한 칸 띄어 **보이게** 적는다. */
  readonly known: boolean;
}

/**
 * 금액 글자의 단위를 내는 **한 자리**(r1 v L-2 · cr L-4). 앞 판은 `noticeDisplay` 와
 * `editSessionDisplay` 가 각자 삼항으로 `'원'` 과 원문 코드를 갈랐다 — 통화 축을 표로 세운
 * slice 안에서 **같은 축의 두 번째 분기**가 남은 꼴이었다.
 *
 * 통화를 **선언하지 않은** 값은 원으로 본다: 이 도메인의 통화는 하나이고(`Currency.KRW`) 금액
 * 칸은 원 단위 정수다(계약). 표 밖 코드는 지어내지 않고 **그대로** 보인다.
 */
export function currencyUnitOf(token: string | null | undefined): CurrencyUnit {
  if (token === null || token === undefined) {
    return { text: AMOUNT_CURRENCY_DISPLAY.KRW.label, known: true };
  }
  const known = displayOf<string>(AMOUNT_CURRENCY_DISPLAY, token);
  return known === undefined ? { text: token, known: false } : { text: known.label, known: true };
}

/** 숫자와 단위를 **한 글자**로 — 아는 단위는 붙이고 모르는 코드는 띄운다. */
export function amountTextOf(won: number, token: string | null | undefined): string {
  const unit = currencyUnitOf(token);
  const formatted = won.toLocaleString('ko-KR');
  return unit.known ? `${formatted}${unit.text}` : `${formatted} ${unit.text}`;
}

/**
 * 한글 금액의 자리 단위 — 큰 자리부터다. 자리 이름은 **이 표 한 자리**에만 있다.
 *
 * **조에서 끝난다**(cr L-1). 이 함수는 안전 정수 밖을 먼저 `null` 로 돌려보내므로 조 위의 자리는
 * **입력이 존재하지 않는다** — 닿지 않는 행을 표에 두면 다음 사람이 그것을 살아 있는 분기로 읽는다.
 * 짝 test 가 「조가 최대」를 주장으로 든다.
 */
const KOREAN_UNITS: readonly { readonly unit: number; readonly name: string }[] = [
  { unit: 1_000_000_000_000, name: '조' },
  { unit: 100_000_000, name: '억' },
  { unit: 10_000, name: '만' },
];

/** 자리 이름 전수 — 짝 test 가 이 목록으로 「조가 최대」를 잰다(사본을 만들지 않는다). */
export const KOREAN_UNIT_NAMES: readonly string[] = KOREAN_UNITS.map((one) => one.name);

/**
 * 금액을 **한글 자리로 끊어 읽은 글자**(M13/13C · §6.2 C 4 「원 단위 정수 + 한글 금액 병기」).
 *
 * 나라장터 실수 목록의 첫 줄이 「0 을 하나 더/덜」이고, 그 실수는 숫자의 자리 수를 눈으로 세는
 * 것으로는 잡히지 않는다 — 억·만으로 끊어 읽는 글자가 잡는다. 그래서 이 글자는 **같은 수의 다른
 * 읽기**이고(짝 test 가 자리의 합을 원래 수와 대조한다) 비어 있는 자리는 **빠진다**(「0만」을
 * 적지 않는다).
 *
 * **읽을 수 없는 값에는 글자를 지어내지 않는다** — 원 단위 정수가 아니면 `null` 이다(그때
 * 「0원」이라고 적으면 화면이 금액을 지어내는 것이다).
 */
export function koreanAmountTextOf(won: number): string | null {
  if (!Number.isSafeInteger(won) || won < 0) {
    return null;
  }
  const parts: string[] = [];
  let left = won;
  for (const { unit, name } of KOREAN_UNITS) {
    const count = Math.floor(left / unit);
    if (count > 0) {
      parts.push(`${count}${name}`);
      left -= count * unit;
    }
  }
  if (left > 0 || parts.length === 0) {
    parts.push(`${left}`);
  }
  return `${parts.join(' ')}${AMOUNT_CURRENCY_DISPLAY.KRW.label}`;
}

/**
 * 압축 꼴이 남기는 소수 자리 — 두 자리다. 상수 하나로 두는 까닭은 **셈이 전부 이 값에 매여
 * 있기** 때문이다(내림의 단위 · 소수 자릿수 · 정확 판정이 같은 수를 쓴다).
 */
const COMPACT_FRACTION_SCALE = 100;

/**
 * 버린 자리가 있다는 **표지**. 압축 꼴의 핵심이 이 글자다 — 이 꼴은 자리를 일부러 버리므로,
 * 버렸다는 사실을 말하지 않으면 운영자가 그 수를 금액 그 자체로 읽는다.
 */
const APPROXIMATE_MARK = '약 ';

/**
 * 금액의 **압축 읽기**(M14/14B · 와이어프레임 모바일 카드의 「기초 4.2억」).
 *
 * [koreanAmountTextOf] 와 **다른 꼴**이다. 그쪽은 같은 수의 **무손실** 다른 읽기라 끊긴 자리를
 * 버리지 않고, 이쪽은 좁은 카드에 들어가려고 일부러 버린다. 재사용이 아니라 새 꼴인 이유가
 * 그것이고, 그래서 **버렸다는 사실 자체가 문면의 일부**다.
 *
 * 규율 둘.
 *  · **내림이다.** 올리면 화면이 없는 돈을 보이고, 그 수로 금액대를 가늠한 운영자가 자기
 *    상한을 넘은 공고를 담는다. 셈은 **정수 연산만** 지난다 — 나눗셈을 부동소수로 하면 경계
 *    값에서 한 자리가 위로 튄다(그 튐이 곧 올림이다).
 *  · **버린 자리가 있을 때만 「약」**이다. 늘 붙이면 정확히 떨어지는 금액까지 「대충 그쯤」으로
 *    읽히고, 한 번도 안 붙이면 버린 자리를 숨긴다.
 *
 * 자리 이름은 [KOREAN_UNITS] **그 표**에서 온다 — 두 꼴이 자기 어휘를 따로 가지면 같은 금액이
 * 화면에서 두 말로 읽힌다. 금액 부속 토큰 표 넷에는 **키를 더하지 않는다**: 이 꼴은 어휘가
 * 아니라 서식이고, [MONEY_TOKENS] 는 그 넷의 키에서 유도되므로 움직이지 않는다.
 *
 * 만 미만은 **끊을 자리가 없다** — 그때는 정확 금액 그대로이고 표지도 서지 않는다(근사가
 * 아니므로). 읽을 수 없는 값에는 글자를 지어내지 않는다.
 */
export function compactAmountTextOf(won: number, token: string | null | undefined): string | null {
  if (!Number.isSafeInteger(won) || won < 0) {
    return null;
  }
  const unit = currencyUnitOf(token);
  const withCurrency = (numeral: string): string =>
    unit.known ? `${numeral}${unit.text}` : `${numeral} ${unit.text}`;

  const place = KOREAN_UNITS.find((one) => won >= one.unit);
  if (place === undefined) {
    return withCurrency(won.toLocaleString('ko-KR'));
  }

  // 내림을 **나머지로** 한다: `floor(won / step)` 은 경계에서 위로 튀지만, 나머지를 빼면 남는
  // 수가 step 의 배수라 그 나눗셈이 정확하다(둘 다 안전 정수 안에 있다).
  const step = place.unit / COMPACT_FRACTION_SCALE;
  const kept = (won - (won % step)) / step;
  const whole = Math.floor(kept / COMPACT_FRACTION_SCALE);
  const fraction = kept % COMPACT_FRACTION_SCALE;
  const decimals = fraction === 0 ? '' : `.${String(fraction).padStart(2, '0').replace(/0$/u, '')}`;
  const numeral = `${whole.toLocaleString('ko-KR')}${decimals}${place.name}`;

  return `${kept * step === won ? '' : APPROXIMATE_MARK}${withCurrency(numeral)}`;
}

/**
 * 금액 부속 토큰의 **전수 집합**(D-4). 「DOM 에 원문 토큰 0」 게이트가 이 목록을 금지 목록으로
 * 쓴다 — 네 표의 키에서 **유도하므로** 어휘가 늘면 금지 목록도 함께 는다(손으로 적은 목록은
 * 어휘가 늘 때 조용히 뒤처진다).
 */
export const MONEY_TOKENS: readonly string[] = [
  ...Object.keys(AMOUNT_BASIS_DISPLAY),
  ...Object.keys(AMOUNT_PROVENANCE_DISPLAY),
  ...Object.keys(AMOUNT_CURRENCY_DISPLAY),
  ...Object.keys(VAT_TREATMENT_DISPLAY),
];

/**
 * 금액 **부속 칸들을 한 문면으로** 조립하는 자리(D-3). 화면 B 카드 · 화면 C 머리 · 설정의
 * 예산 칸이 이 함수를 지나므로 같은 축이 같은 말로 보인다 — 앞 판은 같은 과세 축이 화면 C
 * 안에서 두 말(`UNKNOWN` 과 「과세 구분 모름」)로 보였다.
 *
 * 없는 축은 **빠진다**(빈 자리를 만들지 않는다). 하나도 없으면 자리 자체가 없다(`null`).
 */
export function amountQualifierTextOf(fields: {
  readonly currency?: string | null;
  readonly basis?: string | null;
  readonly vatTreatment?: string | null;
  readonly provenance?: string | null;
}): string | null {
  const parts = [
    fields.currency === null || fields.currency === undefined ? null : currencyDisplayOf(fields.currency).label,
    fields.basis === null || fields.basis === undefined ? null : amountBasisDisplayOf(fields.basis).label,
    fields.vatTreatment === null || fields.vatTreatment === undefined
      ? null
      : vatTreatmentDisplayOf(fields.vatTreatment).label,
    fields.provenance === null || fields.provenance === undefined
      ? null
      : amountProvenanceDisplayOf(fields.provenance).label,
  ].filter((one): one is string => one !== null);
  return parts.length === 0 ? null : parts.join(' · ');
}
