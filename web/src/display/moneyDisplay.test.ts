import { describe, expect, it } from 'vitest';

import { VAT_CHOICES } from '../screens/WorksheetRecord';

import { VAT_TREATMENT_DISPLAY, vatTreatmentDisplayOf, type VatTreatment } from './moneyDisplay';

/** 계약 enum 을 **축어로** 적는다 — 표에서 키를 뽑아 자기와 대조하면 등식이 공허하다. */
const CONTRACT_VAT: readonly VatTreatment[] = ['INCLUSIVE', 'EXCLUSIVE', 'UNKNOWN'];

describe('과세 구분은 한 표에서만 나온다 (r1 CR M-1 · v L-2)', () => {
  it('표의 키 집합이 계약 전수와 같다', () => {
    expect(Object.keys(VAT_TREATMENT_DISPLAY).sort()).toStrictEqual([...CONTRACT_VAT].sort());
  });

  it('셋의 라벨이 서로 다르다', () => {
    const labels = CONTRACT_VAT.map((one) => VAT_TREATMENT_DISPLAY[one].label);

    expect(new Set(labels).size).toBe(labels.length);
  });

  /**
   * **이 slice 가 막는 핵심 불일치.** 운영자는 화면 C 의 고르는 자리에서 「부가세 포함」을
   * 고르는데 결과 화면이 `INCLUSIVE` 를 돌려주고 있었다. 두 자리가 같은 표를 읽는 것이 그
   * 불일치를 **구조로** 막는 자리다 — 한쪽에 라벨을 적는 순간 이 등식이 깨진다.
   */
  it('입력 폼의 라벨이 표의 라벨과 같다 — 한 값에 두 말이 없다', () => {
    expect(VAT_CHOICES.map((one) => one.value)).toStrictEqual(Object.keys(VAT_TREATMENT_DISPLAY));
    for (const choice of VAT_CHOICES) {
      expect(choice.label, `${choice.value} 의 라벨이 표와 다르다`).toBe(
        VAT_TREATMENT_DISPLAY[choice.value].label,
      );
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
