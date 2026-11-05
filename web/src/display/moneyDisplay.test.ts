import { describe, expect, it } from 'vitest';

import { VAT_CHOICES } from '../screens/WorksheetRecord';

import {
  AMOUNT_BASIS_DISPLAY,
  AMOUNT_CURRENCY_DISPLAY,
  AMOUNT_PROVENANCE_DISPLAY,
  MONEY_TOKENS,
  VAT_TREATMENT_DISPLAY,
  amountBasisDisplayOf,
  amountProvenanceDisplayOf,
  amountQualifierTextOf,
  currencyDisplayOf,
  vatTreatmentDisplayOf,
  type AmountBasis,
  type AmountCurrency,
  type AmountProvenance,
  type VatTreatment,
} from './moneyDisplay';

/** 계약 enum 을 **축어로** 적는다 — 표에서 키를 뽑아 자기와 대조하면 등식이 공허하다. */
const CONTRACT_VAT: readonly VatTreatment[] = ['INCLUSIVE', 'EXCLUSIVE', 'UNKNOWN'];

/**
 * **라벨도 축어로** 적는다(r2 CR L-1). 앞 판은 표에서 뽑은 라벨을 입력 폼의 라벨과 맞댔는데,
 * 둘 다 같은 객체에서 나오므로 **문면이 바뀌어도 등식이 그대로 참**이었다 — 「같은 출처에서
 * 양쪽을 끌어오면 표가 틀려도 대조가 초록이다」의 실례가 이 파일 안에 있었다.
 */
const CONTRACT_VAT_LABEL: Readonly<Record<VatTreatment, string>> = {
  INCLUSIVE: '부가세 포함',
  EXCLUSIVE: '부가세 별도',
  UNKNOWN: '과세 구분 모름',
};


describe('과세 구분은 한 표에서만 나온다 (r1 CR M-1 · v L-2)', () => {
  it('표의 키 집합이 계약 전수와 같다', () => {
    expect(Object.keys(VAT_TREATMENT_DISPLAY).sort()).toStrictEqual([...CONTRACT_VAT].sort());
  });

  it('셋의 라벨이 서로 다르다', () => {
    const labels = CONTRACT_VAT.map((one) => VAT_TREATMENT_DISPLAY[one].label);

    expect(new Set(labels).size).toBe(labels.length);
  });

  /** 표의 문면이 바뀌면 **이 목록이 먼저 틀린다** — 값 쪽의 같은 주장이다(r2 CR L-1). */
  it('표의 라벨이 축어 목록과 같다', () => {
    for (const value of CONTRACT_VAT) {
      expect(VAT_TREATMENT_DISPLAY[value].label, `${value} 의 문면이 바뀌었다`).toBe(
        CONTRACT_VAT_LABEL[value],
      );
    }
  });

  /**
   * **이 slice 가 막는 핵심 불일치.** 운영자는 화면 C 의 고르는 자리에서 「부가세 포함」을
   * 고르는데 결과 화면이 `INCLUSIVE` 를 돌려주고 있었다.
   */
  it('입력 폼의 값·라벨이 표와 같다', () => {
    expect(VAT_CHOICES.map((one) => one.value)).toStrictEqual(Object.keys(VAT_TREATMENT_DISPLAY));
    for (const choice of VAT_CHOICES) {
      expect(choice.label, `${choice.value} 의 라벨이 표와 다르다`).toBe(CONTRACT_VAT_LABEL[choice.value]);
    }
  });

  /**
   * **「한 출처」는 값 대조로 설 수 없다**(r2 CR L-1 — 실측: 같은 문면을 손으로 적는 변이가
   * 667 passed). 같은 글자를 양쪽에 적으면 어떤 값 등식도 참이기 때문이다. 그 축은 원문을
   * 읽어야 서므로 **`scripts/vat-label-source.test.mjs`** 가 든다(여기는 TS 프로그램 안이라
   * node 내장 모듈을 쓸 수 없다 — ADR 0011 D-3 의 경계).
   */
  it('표의 라벨이 전부 비어 있지 않다', () => {
    for (const value of CONTRACT_VAT) {
      expect(VAT_TREATMENT_DISPLAY[value].label.length).toBeGreaterThan(0);
    }
  });

  /** 「모름」은 **부재**다 — 0% 로도 과세로도 읽히면 안 된다. */
  it('모름의 어조가 absent 다', () => {
    expect(VAT_TREATMENT_DISPLAY.UNKNOWN.tone).toBe('absent');
  });

  /**
   * 응답 칸이 계약상 `string` 이라 표 밖 값이 올 수 있다(계약이 늘었는데 번들이 아직 모르는
   * 경우). 그때 **조용한 빈 자리**는 「서버가 안 보냈다」로 읽히므로 그 값을 보이게 낸다.
   */
  it('표 밖 값은 그 값을 보이게 낸다 — 조용히 사라지지 않는다', () => {
    const display = vatTreatmentDisplayOf('ZERO_RATED');

    expect(display.label).toContain('ZERO_RATED');
    expect(display.tone).toBe('unjudged');
  });

  it('표 안의 값은 그 문면을 그대로 낸다', () => {
    expect(vatTreatmentDisplayOf('INCLUSIVE').label).toBe(VAT_TREATMENT_DISPLAY.INCLUSIVE.label);
  });
});

/**
 * 금액 **부속 토큰 세 축**의 전수와 문면(M9/screen-display D-3). 앞 판은 이 셋이 화면에
 * `BASE_AMOUNT · INCLUSIVE · PUBLISHED` 로 그대로 나왔다(`OPEN-7E3B-MONEY-WORDING`) — 과세
 * 축만 표를 타고 있었다.
 *
 * **어휘를 축어로 적는다** — 표에서 키를 뽑아 자기와 대조하면 등식이 공허하다. 출처는 서버가
 * 이름을 내는 자리(`shared-kernel` 의 `Basis`·`Currency`, `procurement` 의 `ProvenanceKind`)다.
 */
const CONTRACT_BASIS: readonly AmountBasis[] = [
  'BASE_AMOUNT',
  'ESTIMATED',
  'YEGA',
  'BID',
  'ALLOCATED_BUDGET',
  'AWARD',
];

const CONTRACT_BASIS_LABEL: Readonly<Record<AmountBasis, string>> = {
  BASE_AMOUNT: '기초금액',
  ESTIMATED: '추정가격',
  YEGA: '예정가격',
  BID: '투찰금액',
  ALLOCATED_BUDGET: '배정예산',
  AWARD: '낙찰금액',
};

const CONTRACT_PROVENANCE: readonly AmountProvenance[] = [
  'PUBLISHED',
  'DERIVED_FROM_OPENING',
  'FILLED_FROM_BUDGET_KEY',
  'COPIED_FROM_BASE_AMOUNT',
  'OPERATOR_DECLARED',
  'UNDECLARED',
];

const CONTRACT_PROVENANCE_LABEL: Readonly<Record<AmountProvenance, string>> = {
  PUBLISHED: '공고 게시값',
  DERIVED_FROM_OPENING: '개찰 역산값',
  FILLED_FROM_BUDGET_KEY: '예산 항목에서 채운 값',
  COPIED_FROM_BASE_AMOUNT: '기초금액에서 복사한 값',
  OPERATOR_DECLARED: '운영자 선언값',
  UNDECLARED: '출처 미선언',
};

const CONTRACT_CURRENCY: readonly AmountCurrency[] = ['KRW'];

describe('금액 basis 는 한 표에서만 나온다 (D-3)', () => {
  it('표의 키 집합이 어휘 전수와 같다', () => {
    expect(Object.keys(AMOUNT_BASIS_DISPLAY).sort()).toStrictEqual([...CONTRACT_BASIS].sort());
  });

  it('표의 라벨이 축어 목록과 같다', () => {
    for (const value of CONTRACT_BASIS) {
      expect(AMOUNT_BASIS_DISPLAY[value].label, `${value} 의 문면이 바뀌었다`).toBe(CONTRACT_BASIS_LABEL[value]);
    }
  });

  it('여섯의 라벨이 서로 다르다', () => {
    const labels = CONTRACT_BASIS.map((one) => AMOUNT_BASIS_DISPLAY[one].label);

    expect(new Set(labels).size).toBe(labels.length);
  });

  /** 라벨이 원문 토큰을 품으면 토큰이 화면에 남는다 — 그러면 이 slice 가 아무것도 바꾸지 않았다. */
  it('라벨이 원문 토큰을 품지 않는다', () => {
    for (const value of CONTRACT_BASIS) {
      expect(AMOUNT_BASIS_DISPLAY[value].label).not.toContain(value);
    }
  });

  it('표 밖 값은 그 값을 보이게 낸다 — 조용히 사라지지 않는다', () => {
    const display = amountBasisDisplayOf('PURCHASE_COST');

    expect(display.label).toContain('PURCHASE_COST');
    expect(display.tone).toBe('unjudged');
  });
});

describe('금액 provenance 는 한 표에서만 나온다 (D-3)', () => {
  it('표의 키 집합이 어휘 전수와 같다', () => {
    expect(Object.keys(AMOUNT_PROVENANCE_DISPLAY).sort()).toStrictEqual([...CONTRACT_PROVENANCE].sort());
  });

  it('표의 라벨이 축어 목록과 같다', () => {
    for (const value of CONTRACT_PROVENANCE) {
      expect(AMOUNT_PROVENANCE_DISPLAY[value].label, `${value} 의 문면이 바뀌었다`).toBe(
        CONTRACT_PROVENANCE_LABEL[value],
      );
    }
  });

  it('여섯의 라벨이 서로 다르다', () => {
    const labels = CONTRACT_PROVENANCE.map((one) => AMOUNT_PROVENANCE_DISPLAY[one].label);

    expect(new Set(labels).size).toBe(labels.length);
  });

  it('라벨이 원문 토큰을 품지 않는다', () => {
    for (const value of CONTRACT_PROVENANCE) {
      expect(AMOUNT_PROVENANCE_DISPLAY[value].label).not.toContain(value);
    }
  });

  /** 「미선언」은 **부재**다 — 출처가 있는 값으로 읽히면 안 된다. */
  it('미선언의 어조가 absent 다', () => {
    expect(AMOUNT_PROVENANCE_DISPLAY.UNDECLARED.tone).toBe('absent');
  });

  it('표 밖 값은 그 값을 보이게 낸다', () => {
    expect(amountProvenanceDisplayOf('GUESSED').label).toContain('GUESSED');
  });
});

describe('통화는 단위 문면으로 보인다 (D-3)', () => {
  it('표의 키 집합이 어휘 전수와 같다', () => {
    expect(Object.keys(AMOUNT_CURRENCY_DISPLAY).sort()).toStrictEqual([...CONTRACT_CURRENCY].sort());
  });

  it('원화는 단위로 보인다', () => {
    expect(currencyDisplayOf('KRW').label).toBe('원');
  });

  it('표 밖 통화는 그 코드를 보이게 낸다 — 단위를 아는 척하지 않는다', () => {
    expect(currencyDisplayOf('USD').label).toContain('USD');
  });
});

/**
 * **토큰 전수의 한 자리**. D-4 의 「DOM 에 금액 토큰 원문 0」 게이트가 이 집합을 금지 목록으로
 * 쓴다 — 표에서 유도하므로 어휘가 늘면 금지 목록도 함께 는다.
 */
describe('금지 토큰 집합은 네 표의 키 전수다 (D-4)', () => {
  it('네 표의 키가 전부 들어 있다', () => {
    const expected = [
      ...CONTRACT_BASIS,
      ...CONTRACT_PROVENANCE,
      ...CONTRACT_CURRENCY,
      ...CONTRACT_VAT,
    ].sort();

    expect([...MONEY_TOKENS].sort()).toStrictEqual(expected);
  });

  /** basis 여섯 · provenance 여섯 · 통화 하나 · 과세 셋. */
  it('토큰이 열여섯이다 — 표 한 행이 빠지면 여기가 먼저 틀린다', () => {
    expect(MONEY_TOKENS.length).toBe(16);
  });
});

describe('부속 세 축은 한 문면으로 조립된다 — 화면 B·C·설정이 같은 말을 쓴다 (D-3)', () => {
  it('네 축이 모두 있으면 순서대로 잇는다', () => {
    expect(
      amountQualifierTextOf({
        currency: 'KRW',
        basis: 'BASE_AMOUNT',
        vatTreatment: 'INCLUSIVE',
        provenance: 'PUBLISHED',
      }),
    ).toBe('원 · 기초금액 · 부가세 포함 · 공고 게시값');
  });

  it('없는 축은 빠지고 빈 자리를 만들지 않는다', () => {
    expect(amountQualifierTextOf({ basis: 'AWARD', vatTreatment: null, provenance: undefined })).toBe('낙찰금액');
  });

  it('축이 하나도 없으면 자리가 없다', () => {
    expect(amountQualifierTextOf({})).toBeNull();
    expect(amountQualifierTextOf({ basis: null, provenance: null })).toBeNull();
  });

  /** **이 slice 가 막는 핵심 불일치** — 머리와 입력 선택지가 같은 과세 문면을 쓴다. */
  it('조립된 문면에 원문 토큰이 남지 않는다', () => {
    const text =
      amountQualifierTextOf({
        currency: 'KRW',
        basis: 'BASE_AMOUNT',
        vatTreatment: 'INCLUSIVE',
        provenance: 'PUBLISHED',
      }) ?? '';

    for (const token of MONEY_TOKENS) {
      expect(text, `${token} 이 문면에 남았다`).not.toContain(token);
    }
  });
});
