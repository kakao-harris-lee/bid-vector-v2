import { describe, expect, it } from 'vitest';

import { VAT_CHOICES } from '../screens/WorksheetRecord';

import { VAT_TREATMENT_DISPLAY, vatTreatmentDisplayOf, type VatTreatment } from './moneyDisplay';

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
